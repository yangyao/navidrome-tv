package com.navidrome.tv.playback

import android.content.Intent
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.navidrome.tv.TvApp
import com.navidrome.tv.model.QueueSource
import com.navidrome.tv.model.Song
import com.navidrome.tv.util.mergeDistinct
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Low-frequency state: progress is deliberately kept out of library/root composition. */
data class PlaybackState(
    val hasQueue: Boolean = false,
    val currentSong: Song? = null,
    val playing: Boolean = false,
    val queue: List<Song> = emptyList(),
    val currentIndex: Int = -1,
    val sourceName: String = "",
    val sourceKey: String = "",
    val shuffle: Boolean = false,
    val repeatAll: Boolean = false,
    val repeatOne: Boolean = false,
)
data class PlaybackProgress(val positionMs: Long = 0, val durationMs: Long = 0)

class PlaybackService : MediaSessionService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val app get() = applicationContext as TvApp
    private var player: ExoPlayer? = null
    private var session: MediaSession? = null
    private var songs: List<Song> = emptyList()
    private var currentIndex = -1
    private var source: QueueSource = QueueSource.AllSongs()
    private var windowStart = 0
    private var remoteOffset = 0
    private var exhausted = false
    private var generation = 0
    private var sourceGeneration = 0
    private val edits = Mutex()
    private var preparing: Job? = null
    private var maintenance: Job? = null
    private var restoring: Job? = null
    private var positionTicker: Job? = null
    private var editingWindow = false
    private var retryAfter = 0L
    private var lastSnapshotAt = 0L
    private var listenStartAt = 0L
    private var lastScrobbledId: String? = null

    companion object {
        @Volatile private var instance: PlaybackService? = null
        fun get(): PlaybackService? = instance
        private val _state = MutableStateFlow(PlaybackState())
        val playbackState: StateFlow<PlaybackState> = _state
        private val _progress = MutableStateFlow(PlaybackProgress())
        val progress: StateFlow<PlaybackProgress> = _progress
        const val SUBMISSION_MS = 240_000L
        private const val PAGE = 100
    }

    override fun onCreate() {
        super.onCreate()
        val p = ExoPlayer.Builder(this).build()
        player = p
        p.addListener(playerListener)
        session = MediaSession.Builder(this, p).build()
        instance = this
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (player?.playWhenReady != true || player?.mediaItemCount == 0) stopSelf()
    }
    override fun onDestroy() {
        saveSnapshot()
        generation++
        scope.cancel()
        player?.removeListener(playerListener)
        session?.release()
        player?.release()
        session = null; player = null
        if (instance === this) {
            instance = null
            _state.value = PlaybackState()
            _progress.value = PlaybackProgress()
        }
        super.onDestroy()
    }

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (editingWindow) return
            submitPreviousListen()
            currentIndex = windowStart + (player?.currentMediaItemIndex ?: 0)
            startListen()
            publish()
            saveSnapshot()
            scheduleMaintenance()
        }
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            publish()
            positionTicker?.cancel()
            if (isPlaying) positionTicker = scope.launch {
                while (isActive) {
                    updateProgress()
                    scheduleMaintenance()
                    if (SystemClock.elapsedRealtime() - lastSnapshotAt > 15_000) saveSnapshot()
                    delay(500)
                }
            } else saveSnapshot()
        }
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (editingWindow) return
            publish()
            if (playbackState == Player.STATE_ENDED) advancePastWindow()
        }
        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo, reason: Int) { updateProgress() }
        override fun onShuffleModeEnabledChanged(enabled: Boolean) {
            if (!editingWindow) _state.value = _state.value.copy(shuffle = enabled)
        }
        // Repeat mode is owned by the logical queue: a bounded player window must not loop itself.
    }

    private fun invalidateWork() {
        generation++
        preparing?.cancel(); preparing = null
        maintenance?.cancel(); maintenance = null
        restoring?.cancel(); restoring = null
        retryAfter = 0
    }

    fun playFrom(songs: List<Song>, startIndex: Int, source: QueueSource) {
        if (songs.isEmpty()) return
        sourceGeneration++
        invalidateWork()
        submitPreviousListen()
        this.songs = songs
        this.source = source
        remoteOffset = songs.size
        exhausted = source !is QueueSource.AllSongs && source !is QueueSource.Random
        currentIndex = startIndex.coerceIn(songs.indices)
        loadWindow(currentIndex, autoplay = true)
    }

    /** All URL/authentication work runs on IO; only player mutations run on its main looper. */
    private suspend fun mediaItems(batch: List<Song>): List<MediaItem> = withContext(Dispatchers.IO) {
        val compat = app.settings.audioQuality == com.navidrome.tv.data.local.AudioQuality.COMPATIBLE
        batch.map { song ->
            MediaItem.Builder().setMediaId(song.id).setUri(app.repository.streamUrl(song.id, compat))
                .setMediaMetadata(MediaMetadata.Builder().setTitle(song.title).setArtist(song.artist)
                    .setAlbumTitle(song.album).build()).build()
        }
    }

    private fun loadWindow(index: Int, autoplay: Boolean, positionMs: Long = 0) {
        preparing?.cancel()
        maintenance?.cancel(); maintenance = null
        val epoch = generation
        val window = QueueWindow.around(index, songs.size)
        val batch = songs.subList(window.start, window.endExclusive).toList()
        player?.pause()
        preparing = scope.launch {
            try {
                val items = mediaItems(batch)
                if (epoch != generation) return@launch
                val p = player ?: return@launch
                editingWindow = true
                try {
                    windowStart = window.start
                    currentIndex = index
                    p.setMediaItems(items, index - window.start, positionMs)
                    applyNativeMode()
                    p.prepare()
                    p.playWhenReady = autoplay
                } finally { editingWindow = false }
                startListen()
                publish()
                saveSnapshot()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { android.util.Log.w("PlaybackService", "Cannot prepare playback", e) }
            finally {
                if (epoch == generation) {
                    preparing = null
                    scheduleMaintenance()
                }
            }
        }
    }

    fun jumpTo(index: Int) {
        val p = player ?: return
        if (index !in songs.indices) return
        if (preparing?.isActive == true || index !in windowStart until windowStart + p.mediaItemCount) {
            invalidateWork()
            loadWindow(index, true)
        } else {
            p.seekTo(index - windowStart, 0)
            p.play()
        }
    }
    fun next() {
        val p = player ?: return
        if (p.hasNextMediaItem()) p.seekToNextMediaItem()
        else if (currentIndex < songs.lastIndex) jumpTo(currentIndex + 1)
        else if (_state.value.repeatAll && songs.isNotEmpty()) jumpTo(0)
        else scheduleMaintenance()
    }
    fun previous() {
        val p = player ?: return
        if (p.currentPosition > 3_000) p.seekTo(0)
        else if (p.hasPreviousMediaItem()) p.seekToPreviousMediaItem()
        else if (currentIndex > 0) jumpTo(currentIndex - 1)
        else if (_state.value.repeatAll && songs.isNotEmpty()) jumpTo(songs.lastIndex)
    }
    fun togglePause() { player?.let { if (it.playWhenReady) it.pause() else it.play() } }
    fun seekTo(ms: Long) { player?.seekTo(ms); updateProgress() }
    fun setShuffle(enabled: Boolean) = setPlaybackMode(if (enabled) PlaybackMode.SHUFFLE else PlaybackMode.ORDERED)
    fun setRepeatAll(enabled: Boolean) = setPlaybackMode(if (enabled) PlaybackMode.REPEAT_ALL else PlaybackMode.ORDERED)
    fun setPlaybackMode(mode: PlaybackMode) {
        _state.value = _state.value.copy(shuffle = mode == PlaybackMode.SHUFFLE,
            repeatAll = mode == PlaybackMode.REPEAT_ALL, repeatOne = mode == PlaybackMode.REPEAT_ONE)
        editingWindow = true
        try { applyNativeMode() } finally { editingWindow = false }
    }
    private fun applyNativeMode() {
        val p = player ?: return
        p.shuffleModeEnabled = _state.value.shuffle
        p.repeatMode = when {
            _state.value.repeatOne -> Player.REPEAT_MODE_ONE
            _state.value.repeatAll && windowStart == 0 && p.mediaItemCount == songs.size -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }
    }

    /** Serial main-thread bookkeeping; stale IO responses cannot alter a different source. */
    private fun scheduleMaintenance() {
        if (songs.isEmpty() || preparing?.isActive == true || maintenance?.isActive == true) return
        val p = player ?: return
        val end = windowStart + p.mediaItemCount
        val needRemote = QueueWindow.needsRemote(currentIndex, songs.size, exhausted)
        val needWindow = end - currentIndex <= QueueWindow.PREFETCH_REMAINING + 1 && end < songs.size
        if (!needWindow && (!needRemote || SystemClock.elapsedRealtime() < retryAfter)) return
        val epoch = generation
        maintenance = scope.launch {
            try {
                if (needRemote && SystemClock.elapsedRealtime() >= retryAfter) {
                    val result = when (source) {
                        is QueueSource.AllSongs -> app.repository.songsPage(remoteOffset, PAGE)
                        is QueueSource.Random -> app.repository.randomSongs(50)
                        else -> null
                    }
                    if (epoch != generation) return@launch
                    result?.onSuccess { page ->
                        if (source is QueueSource.AllSongs) {
                            remoteOffset += page.size
                            exhausted = page.size < PAGE
                        }
                        val merged = withContext(Dispatchers.Default) { mergeDistinct(songs, page) { it.id } }
                        if (epoch != generation) return@launch
                        if (source is QueueSource.Random && merged.size == songs.size) exhausted = true
                        songs = merged
                        retryAfter = SystemClock.elapsedRealtime() + 5_000
                        publish()
                    }?.onFailure { retryAfter = SystemClock.elapsedRealtime() + 5_000 }
                }
                extendWindow(epoch)
                if (epoch == generation && player?.playbackState == Player.STATE_ENDED) advancePastWindow()
            } catch (e: CancellationException) { throw e }
            finally { if (epoch == generation) maintenance = null }
        }
    }

    private suspend fun extendWindow(epoch: Int) {
        val p = player ?: return
        val oldEnd = windowStart + p.mediaItemCount
        if (oldEnd >= songs.size) return
        val target = QueueWindow.around(currentIndex, songs.size)
        // Never trim a track the user sought back to while IO was in flight.
        val newStart = maxOf(windowStart, target.start)
        val newEnd = target.endExclusive
        if (newEnd <= oldEnd) return
        val items = mediaItems(songs.subList(oldEnd, newEnd).toList())
        if (epoch != generation || currentIndex < newStart) return
        val remove = newStart - windowStart
        editingWindow = true
        try {
            windowStart = newStart
            if (remove > 0) p.removeMediaItems(0, remove)
            p.addMediaItems(items)
            applyNativeMode()
        } finally { editingWindow = false }
        publish()
    }

    private fun advancePastWindow() {
        if (preparing?.isActive == true) return
        if (currentIndex < songs.lastIndex) jumpTo(currentIndex + 1)
        else if (_state.value.repeatAll && songs.isNotEmpty()) jumpTo(0)
        else scheduleMaintenance()
    }

    fun playNext(song: Song) = insertSong(song, next = true)
    fun addToQueue(song: Song) = insertSong(song, next = false)
    private fun insertSong(song: Song, next: Boolean) {
        if (songs.isEmpty()) { playFrom(listOf(song), 0, QueueSource.Manual(listOf(song))); return }
        val sourceEpoch = sourceGeneration
        scope.launch {
            edits.withLock {
                preparing?.join()
                if (sourceEpoch != sourceGeneration) return@withLock
                val item = mediaItems(listOf(song)).single()
                if (sourceEpoch != sourceGeneration) return@withLock
                invalidateWork()
                val p = player ?: return@withLock
                val at = if (next) currentIndex + 1 else songs.size
                songs = songs.toMutableList().apply { add(at, song) }
                if (p.mediaItemCount == 0) { loadWindow(currentIndex.coerceAtLeast(0), true); return@withLock }
                editingWindow = true
                try {
                    val nativeIndex = at - windowStart
                    if (nativeIndex in 0..p.mediaItemCount && nativeIndex < QueueWindow.MAX_ITEMS) {
                        p.addMediaItem(nativeIndex, item)
                        if (p.mediaItemCount > QueueWindow.MAX_ITEMS) p.removeMediaItem(p.mediaItemCount - 1)
                    }
                    applyNativeMode()
                } finally { editingWindow = false }
                publish()
                scheduleMaintenance()
            }
        }
    }

    fun removeAt(index: Int) {
        if (index !in songs.indices || songs.size == 1) return
        val wasPreparing = preparing?.isActive == true
        invalidateWork()
        val p = player ?: return
        val nativeIndex = index - windowStart
        val removedCurrent = index == currentIndex
        if (removedCurrent) submitPreviousListen()
        songs = songs.toMutableList().apply { removeAt(index) }
        if (index < currentIndex) currentIndex--
        currentIndex = currentIndex.coerceAtMost(songs.lastIndex)
        if (wasPreparing) { loadWindow(currentIndex, true); return }
        editingWindow = true
        try {
            if (index < windowStart) windowStart--
            else if (nativeIndex in 0 until p.mediaItemCount) p.removeMediaItem(nativeIndex)
            applyNativeMode()
        } finally { editingWindow = false }
        if (p.mediaItemCount == 0 || currentIndex !in windowStart until windowStart + p.mediaItemCount) {
            loadWindow(currentIndex, p.playWhenReady)
        } else {
            if (removedCurrent) startListen()
            publish(); saveSnapshot(); scheduleMaintenance()
        }
    }

    fun stopPlayback() {
        sourceGeneration++
        invalidateWork()
        positionTicker?.cancel()
        editingWindow = true
        try { player?.stop(); player?.clearMediaItems() } finally { editingWindow = false }
        songs = emptyList(); currentIndex = -1; windowStart = 0; exhausted = true
        lastScrobbledId = null
        app.settings.queueSnapshot = null
        _state.value = PlaybackState(); _progress.value = PlaybackProgress()
    }
    fun restoreFromSnapshot() {
        val snap = app.settings.queueSnapshot ?: return
        sourceGeneration++
        invalidateWork()
        val epoch = generation
        restoring = scope.launch {
            val src = QueueSource.fromKey(snap.sourceKey, snap.sourceName)
            val restored = when (src) {
                is QueueSource.AllSongs -> fetchPageRange(snap.songIds.size)
                is QueueSource.AlbumSongs -> app.repository.albumSongs(src.albumId).getOrNull()
                is QueueSource.PlaylistSongs -> app.repository.playlistSongs(src.playlistId).getOrNull()
                is QueueSource.ArtistSongs -> app.repository.artistSongs(src.artistId).getOrNull()
                is QueueSource.Starred -> app.repository.starred().getOrNull()?.first
                else -> null
            } ?: return@launch
            val ordered = withContext(Dispatchers.Default) {
                val byId = restored.associateBy { it.id }
                snap.songIds.mapNotNull { byId[it] }
            }
            if (epoch != generation || ordered.isEmpty()) return@launch
            songs = ordered; source = src; remoteOffset = restored.size
            exhausted = src !is QueueSource.AllSongs && src !is QueueSource.Random
            loadWindow(snap.currentIndex.coerceIn(ordered.indices), true, snap.positionMs)
        }
    }
    private suspend fun fetchPageRange(total: Int): List<Song> {
        val out = mutableListOf<Song>()
        var offset = 0
        while (out.size < total) {
            val page = app.repository.songsPage(offset, PAGE).getOrNull() ?: break
            if (page.isEmpty()) break
            out += page; offset += page.size
        }
        return out
    }

    private fun startListen() {
        lastScrobbledId = songs.getOrNull(currentIndex)?.id
        listenStartAt = SystemClock.elapsedRealtime()
        lastScrobbledId?.let { id -> scope.launch { app.repository.scrobble(id, false) } }
    }
    private fun submitPreviousListen() {
        val id = lastScrobbledId ?: return
        val listened = SystemClock.elapsedRealtime() - listenStartAt
        val duration = _progress.value.durationMs
        if (listened >= SUBMISSION_MS || duration > 0 && listened >= duration / 2) {
            scope.launch { app.repository.scrobble(id, true) }
        }
        lastScrobbledId = null
    }
    private fun saveSnapshot() {
        if (songs.isEmpty()) return
        lastSnapshotAt = SystemClock.elapsedRealtime()
        app.settings.queueSnapshot = com.navidrome.tv.data.local.QueueSnapshot(source.key, source.displayName,
            songs.map { it.id }, currentIndex.coerceAtLeast(0), player?.currentPosition ?: 0)
    }
    fun publishSnapshotNow() = saveSnapshot()
    private fun updateProgress() {
        val p = player ?: return
        _progress.value = PlaybackProgress(p.currentPosition.coerceAtLeast(0), p.duration.coerceAtLeast(0))
    }
    private fun publish() {
        val p = player
        _state.value = _state.value.copy(hasQueue = songs.isNotEmpty(), currentSong = songs.getOrNull(currentIndex),
            playing = p?.isPlaying == true, queue = songs, currentIndex = currentIndex,
            sourceName = source.displayName, sourceKey = source.key)
        updateProgress()
    }
}
