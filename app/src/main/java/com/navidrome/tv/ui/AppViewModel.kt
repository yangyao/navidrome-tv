package com.navidrome.tv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.navidrome.tv.TvApp
import com.navidrome.tv.data.api.AuthException
import com.navidrome.tv.data.api.NetworkException
import com.navidrome.tv.data.local.ConnectionSettings
import com.navidrome.tv.data.local.CredentialCipher
import com.navidrome.tv.model.QueueSource
import com.navidrome.tv.model.Song
import com.navidrome.tv.pairing.PairingResult
import com.navidrome.tv.playback.PlaybackAccess
import com.navidrome.tv.playback.PlaybackState
import com.navidrome.tv.ui.components.NavItem
import com.navidrome.tv.ui.nav.Screen
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** App 層狀態：登入流程、畫面堆疊、歌曲操作覆蓋層。 */
class AppViewModel(private val app: TvApp) : ViewModel() {

    enum class LoginPhase { CHECKING, NEED_SETUP, SHOWING_QR, CONNECTING, ERROR }

    data class ConnectingStep(val done: Int) // 0..3

    data class UiState(
        val loginPhase: LoginPhase = LoginPhase.CHECKING,
        val loginError: String? = null,
        val qrContent: String? = null,
        val qrUrlText: String? = null,
        val qrCountdownSec: Int = 300,
        val screen: Screen = Screen.Songs(),
        val songActions: Song? = null,
        val songActionsSource: List<Song>? = null,
        val songActionsIndex: Int = -1,
        val songActionsQueueSource: QueueSource = QueueSource.AllSongs(),
        val showResumePrompt: Boolean = false,
        val connectingStep: Int = 0,
        val isReconfiguring: Boolean = false,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui

    val playback: StateFlow<PlaybackState> get() = com.navidrome.tv.playback.PlaybackService.playbackState

    private val backStack = mutableListOf<Screen>()
    private val screenCache = com.navidrome.tv.util.ScreenCache()
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> cached(key: String, create: () -> T): T = screenCache.getOrPut(key, create)
    private var actionFocusJob: Job? = null
    private var countdownJob: Job? = null
    private val pairingLock = Any()
    private var pairingGeneration = 0
    private var reconfigureReturn: Screen? = null


    val appContext: TvApp get() = app

    init {
        checkLogin()
    }

    fun checkLogin() {
        _ui.value = _ui.value.copy(loginPhase = LoginPhase.CHECKING)
        viewModelScope.launch(Dispatchers.IO) {
            val conn = app.settings.connection()
            if (conn == null) {
                _ui.value = _ui.value.copy(loginPhase = LoginPhase.NEED_SETUP, screen = Screen.Login)
                showQr()
                return@launch
            }
            // 開啟時驗證既有連線
            val ok = runCatching { app.client.ping() }
            _ui.value = if (ok.isSuccess) {
                // 已登入：進首頁（所有歌曲），並提供「繼續播放」選擇
                viewModelScope.launch {
                    PlaybackAccess.withService(app) { }
                    showResumePrompt()
                }
                UiState(loginPhase = LoginPhase.CHECKING, screen = Screen.Songs())
            } else {
                val e = ok.exceptionOrNull()
                UiState(loginPhase = LoginPhase.NEED_SETUP, loginError = e?.message, screen = Screen.Login)
            }
            if (_ui.value.screen is Screen.Login) showQr()
        }
    }

    // ---- 掃碼配對 ----

    private val pairingServer by lazy {
        com.navidrome.tv.pairing.PairingServer(
            onSubmission = { submission -> verifyAndSave(submission) },
            discoveredServers = { discovery?.servers?.value ?: emptyList() },
        )
    }

    private var discovery: com.navidrome.tv.pairing.ServerDiscovery? = null

    fun showQr() {
        _ui.value = _ui.value.copy(loginPhase = LoginPhase.SHOWING_QR, loginError = null)
        synchronized(pairingLock) { pairingGeneration++ }
        countdownJob?.cancel()
        pairingServer.stop()
        startPairingSession()
        countdownJob = viewModelScope.launch { countdownLoop() }
    }

    private fun startPairingSession() {
        if (discovery == null) {
            discovery = com.navidrome.tv.pairing.ServerDiscovery(app).also { it.start(viewModelScope) }
        }
        val running = pairingServer.start()
        if (running != null) {
            val ip = com.navidrome.tv.pairing.LanAddress.primaryIp() ?: "0.0.0.0"
            val url = "http://$ip:${running.port}/setup"
            _ui.value = _ui.value.copy(qrContent = "$url#${running.session.token}", qrUrlText = url, qrCountdownSec = 300)
        } else {
            _ui.value = _ui.value.copy(loginError = "無法启動区网设定服务")
        }
    }

    private suspend fun countdownLoop() {
        while (_ui.value.loginPhase == LoginPhase.SHOWING_QR) {
            delay(1000)
            val left = _ui.value.qrCountdownSec - 1
            if (left <= 0) {
                startPairingSession()
            } else {
                _ui.value = _ui.value.copy(qrCountdownSec = left)
            }
        }
    }

    fun stopPairing() {
        countdownJob?.cancel()
        pairingServer.stop()
        discovery?.stop()
    }

    /** 電視驗證：由電視呼叫 Navidrome ping，避免手機連得上、電視連不上的假成功。 */
    private fun verifyAndSave(submission: com.navidrome.tv.pairing.SetupSubmission): PairingResult {
        val generation = synchronized(pairingLock) { pairingGeneration }
        _ui.value = _ui.value.copy(loginPhase = LoginPhase.CONNECTING, connectingStep = 1)
        return try {
            val test = com.navidrome.tv.data.api.SubsonicClient {
                ConnectionSettings(submission.serverUrl, submission.username, CredentialCipher.encrypt(submission.password))
            }
            kotlinx.coroutines.runBlocking { test.ping() }
            synchronized(pairingLock) {
                check(generation == pairingGeneration && _ui.value.screen == Screen.Login) { "Pairing cancelled" }
                app.settings.saveConnection(submission.serverUrl, submission.username, submission.password)
                screenCache.clear()
                app.repository.clearCaches()
                PlaybackAccess.stop(app)
                backStack.clear()
                reconfigureReturn = null
                _ui.value = UiState(screen = Screen.Songs(), loginPhase = LoginPhase.CHECKING)
            }
            countdownJob?.cancel()
            discovery?.stop()
            // Allow the HTTP response to leave before shutting down the pairing listener.
            viewModelScope.launch { delay(400); pairingServer.stop() }
            PairingResult.Success
        } catch (e: Exception) {
            if (generation == pairingGeneration && _ui.value.screen == Screen.Login) {
                _ui.value = _ui.value.copy(loginPhase = LoginPhase.SHOWING_QR)
                countdownJob?.cancel()
                countdownJob = viewModelScope.launch { countdownLoop() }
            }
            val kind = when (e) {
                is AuthException -> PairingResult.Failure.Kind.CREDENTIALS
                is javax.net.ssl.SSLException -> PairingResult.Failure.Kind.TLS
                else -> PairingResult.Failure.Kind.REACHABILITY
            }
            PairingResult.Failure(kind)
        }
    }

    fun beginReconfigure() {
        reconfigureReturn = _ui.value.screen
        _ui.value = _ui.value.copy(screen = Screen.Login, isReconfiguring = true)
        showQr()
    }

    fun cancelReconfigure() {
        val previous = reconfigureReturn ?: return
        synchronized(pairingLock) {
            pairingGeneration++
            _ui.value = _ui.value.copy(screen = previous, isReconfiguring = false, loginError = null)
            reconfigureReturn = null
        }
        stopPairing()
    }

    fun cancelConnecting() {
        if (reconfigureReturn != null) cancelReconfigure() else showQr()
    }

    // ---- 導覽 ----

    fun navigate(screen: Screen) {
        if (_ui.value.screen == screen) { closeSongActions(); return }
        actionFocusJob?.cancel()
        backStack.add(_ui.value.screen)
        _ui.value = _ui.value.withoutSongActions().copy(screen = screen)
    }

    fun switchNav(item: NavItem) {
        if (item == NavItem.NOW_PLAYING || item == NavItem.QUEUE) {
            navigate(if (item == NavItem.NOW_PLAYING) Screen.NowPlaying else Screen.Queue)
            return
        }
        val screen = when (item) {
            NavItem.SONGS -> Screen.Songs()
            NavItem.ARTISTS -> Screen.Artists
            NavItem.ALBUMS -> Screen.Albums
            NavItem.PLAYLISTS -> Screen.Playlists
            NavItem.FAVORITES -> Screen.Favorites
            NavItem.SETTINGS -> Screen.Settings
            NavItem.NOW_PLAYING -> Screen.NowPlaying
            NavItem.QUEUE -> Screen.Queue
        }
        actionFocusJob?.cancel()
        backStack.clear()
        _ui.value = _ui.value.withoutSongActions().copy(screen = screen)
    }

    /** Back：先關閉覆蓋層，再返回上頁。 */
    fun back() {
        if (_ui.value.isReconfiguring) { cancelReconfigure(); return }
        if (_ui.value.songActions != null) {
            closeSongActions()
            return
        }
        val current = _ui.value.screen
        if (current is Screen.NowPlaying || current is Screen.Queue) {
            // 播放頁／佇列返回清單
            val prev = backStack.removeLastOrNull() ?: Screen.Songs()
            _ui.value = _ui.value.copy(screen = prev)
            return
        }
        val prev = backStack.removeLastOrNull()
        _ui.value = _ui.value.copy(screen = prev ?: Screen.Songs())
    }

    val canGoBack: Boolean get() = _ui.value.isReconfiguring || _ui.value.songActions != null || backStack.isNotEmpty()

    fun navItemFor(screen: Screen): NavItem = when (screen) {
        is Screen.Songs -> NavItem.SONGS
        is Screen.NowPlaying -> NavItem.NOW_PLAYING
        is Screen.Queue -> NavItem.QUEUE
        is Screen.Artists, is Screen.ArtistDetail -> NavItem.ARTISTS
        is Screen.Albums, is Screen.AlbumDetail -> NavItem.ALBUMS
        is Screen.Playlists, is Screen.PlaylistDetail -> NavItem.PLAYLISTS
        is Screen.Favorites -> NavItem.FAVORITES
        is Screen.Settings -> NavItem.SETTINGS
        is Screen.Login -> NavItem.SONGS
    }

    // ---- 歌曲操作 ----

    fun openSongActions(song: Song, source: List<Song>, index: Int) {
        val queueSource = when (val screen = _ui.value.screen) {
            is Screen.ArtistDetail -> QueueSource.ArtistSongs(screen.artistId, screen.name)
            is Screen.AlbumDetail -> QueueSource.AlbumSongs(screen.albumId, song.album)
            is Screen.PlaylistDetail -> QueueSource.PlaylistSongs(screen.playlistId, screen.name)
            Screen.Favorites -> QueueSource.Starred
            else -> QueueSource.AllSongs()
        }
        _ui.value = _ui.value.copy(songActions = song, songActionsSource = source, songActionsIndex = index,
            songActionsQueueSource = queueSource)
    }

    private fun UiState.withoutSongActions() = copy(songActions = null, songActionsSource = null,
        songActionsIndex = -1, songActionsQueueSource = QueueSource.AllSongs())

    fun closeSongActions() {
        if (_ui.value.songActions == null) return
        val origin = _ui.value.screen
        _ui.value = _ui.value.withoutSongActions()
        actionFocusJob?.cancel()
        actionFocusJob = viewModelScope.launch {
            delay(80)
            if (_ui.value.screen == origin && _ui.value.songActions == null)
                com.navidrome.tv.ui.components.FocusBus.requestContent()
        }
    }

    fun performSongPrimaryAction() {
        val state = _ui.value
        val song = state.songActions ?: return
        if (playback.value.currentSong?.id == song.id) {
            PlaybackAccess.togglePause(app)
        } else {
            val source = state.songActionsSource ?: listOf(song)
            val index = state.songActionsIndex.takeIf { it in source.indices } ?: 0
            PlaybackAccess.playFrom(app, source, index, state.songActionsQueueSource)
        }
        closeSongActions()
    }

    fun showResumePrompt() {
        if (app.settings.queueSnapshot != null && !playback.value.hasQueue) {
            _ui.value = _ui.value.copy(showResumePrompt = true)
        }
    }

    fun resumePlayback() {
        _ui.value = _ui.value.copy(showResumePrompt = false)
        PlaybackAccess.restore(app)
    }

    fun dismissResumePrompt() {
        _ui.value = _ui.value.copy(showResumePrompt = false)
    }

    // ---- 播放捷徑 ----

    fun playSongs(context: android.content.Context, songs: List<Song>, startIndex: Int, source: QueueSource) {
        PlaybackAccess.playFrom(context, songs, startIndex, source)
    }

    fun shuffleAll(context: android.content.Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val songs = app.repository.randomSongs(50).getOrDefault(emptyList())
            if (songs.isNotEmpty()) {
                PlaybackAccess.playFrom(context, songs, 0, QueueSource.Random())
            }
        }
    }

    fun logout() {
        screenCache.clear()
        app.repository.clearCaches()
        reconfigureReturn = null
        synchronized(pairingLock) { pairingGeneration++ }
        app.settings.logout()
        PlaybackAccess.stop(app)
        discovery?.stop()
        discovery = null
        pairingServer.stop()
        backStack.clear()
        _ui.value = UiState(loginPhase = LoginPhase.NEED_SETUP, screen = Screen.Login)
        showQr()
    }
}
