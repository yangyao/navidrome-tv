package com.navidrome.tv.playback

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper

/** UI → 服務的橋樑：服務是唯一播放器，UI 不建立第二個播放器。 */
object PlaybackAccess {

    fun withService(context: Context, block: (PlaybackService) -> Unit) {
        val app = context.applicationContext
        val main = Handler(Looper.getMainLooper())
        fun dispatch(attempt: Int = 0) {
            val service = PlaybackService.get()
            if (service != null) { block(service); return }
            if (attempt == 0) app.startService(Intent(app, PlaybackService::class.java))
            if (attempt < 100) main.postDelayed({ dispatch(attempt + 1) }, 25)
        }
        if (Looper.myLooper() == Looper.getMainLooper()) dispatch() else main.post { dispatch() }
    }

    fun playFrom(context: Context, songs: List<com.navidrome.tv.model.Song>, startIndex: Int, source: com.navidrome.tv.model.QueueSource) =
        withService(context) { it.playFrom(songs, startIndex, source) }

    fun playNext(context: Context, song: com.navidrome.tv.model.Song) =
        withService(context) { it.playNext(song) }

    fun addToQueue(context: Context, song: com.navidrome.tv.model.Song) =
        withService(context) { it.addToQueue(song) }

    fun removeAt(context: Context, index: Int) = withService(context) { it.removeAt(index) }

    fun jumpTo(context: Context, index: Int) = withService(context) { it.jumpTo(index) }

    fun togglePause(context: Context) = withService(context) { it.togglePause() }

    fun next(context: Context) = withService(context) { it.next() }

    fun previous(context: Context) = withService(context) { it.previous() }

    fun setPlaybackMode(context: Context, mode: PlaybackMode) = withService(context) { it.setPlaybackMode(mode) }

    fun setShuffle(context: Context, enabled: Boolean) = withService(context) { it.setShuffle(enabled) }

    fun setRepeatAll(context: Context, enabled: Boolean) = withService(context) { it.setRepeatAll(enabled) }

    fun seekTo(context: Context, ms: Long) = withService(context) { it.seekTo(ms) }

    fun stop(context: Context) = withService(context) { it.stopPlayback() }

    fun restore(context: Context) = withService(context) { it.restoreFromSnapshot() }
}
