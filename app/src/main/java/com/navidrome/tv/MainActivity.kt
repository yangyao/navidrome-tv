package com.navidrome.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.navidrome.tv.playback.PlaybackAccess
import com.navidrome.tv.playback.PlaybackService
import com.navidrome.tv.ui.AppViewModel
import com.navidrome.tv.ui.collectAsStateValue
import com.navidrome.tv.ui.components.AppSidebar
import com.navidrome.tv.ui.components.MiniPlayerBar
import com.navidrome.tv.ui.components.TabRow
import com.navidrome.tv.ui.nav.Screen
import com.navidrome.tv.ui.screens.AlbumDetailScreen
import com.navidrome.tv.ui.screens.AlbumsScreen
import com.navidrome.tv.ui.screens.AllSongsScreen
import com.navidrome.tv.ui.screens.ArtistDetailScreen
import com.navidrome.tv.ui.screens.ArtistsScreen
import com.navidrome.tv.ui.screens.FavoritesScreen
import com.navidrome.tv.ui.screens.LoginScreen
import com.navidrome.tv.ui.screens.NowPlayingScreen
import com.navidrome.tv.ui.screens.PlaylistDetailScreen
import com.navidrome.tv.ui.screens.PlaylistsScreen
import com.navidrome.tv.ui.screens.QueueScreen
import com.navidrome.tv.ui.screens.SettingsScreen
import com.navidrome.tv.ui.screens.SongActionsOverlay
import com.navidrome.tv.ui.theme.Bg
import com.navidrome.tv.ui.theme.NavidromeTvTheme
import com.navidrome.tv.ui.theme.TextPrimary

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 播放服務完全惰性啟動：首次播放/恢復播放時由 PlaybackAccess 啟動
        //（冷啟動時 App 尚在背景狀態，此時 startService 會被系統拒絕）
        val app = application as TvApp
        setContent {
            NavidromeTvTheme {
                val vm: AppViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(app) as T
                    },
                )
                AppRoot(vm)
            }
        }
    }
}

@Composable
fun AppRoot(vm: AppViewModel) {
    val ui = vm.ui.collectAsStateValue()
    val playback = vm.playback.collectAsStateValue()
    val screen = ui.screen
    androidx.activity.compose.BackHandler(enabled = vm.canGoBack) { vm.back() }

    Row(
        Modifier
            .fillMaxSize()
            .background(Bg)

    ) {
        if (screen is Screen.Login) {
            LoginScreen(vm)
        } else {
            if (screen !is Screen.NowPlaying) Box(Modifier.fillMaxHeight()) {
                AppSidebar(
                    selected = vm.navItemFor(screen),
                    onSelect = { vm.switchNav(it) },
                )
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Box(Modifier.weight(1f).focusGroup()) {
                    when (screen) {
                        is Screen.Songs -> AllSongsScreen(vm)
                        Screen.Artists -> ArtistsScreen(vm)
                        is Screen.ArtistDetail -> ArtistDetailScreen(vm, screen.artistId, screen.name)
                        Screen.Albums -> AlbumsScreen(vm)
                        is Screen.AlbumDetail -> AlbumDetailScreen(vm, screen.albumId)
                        Screen.Playlists -> PlaylistsScreen(vm)
                        is Screen.PlaylistDetail -> PlaylistDetailScreen(vm, screen.playlistId, screen.name)
                        Screen.Favorites -> FavoritesScreen(vm)
                        Screen.Settings -> SettingsScreen(vm)
                        Screen.NowPlaying -> NowPlayingScreen(vm)
                        Screen.Queue -> QueueScreen(vm)
                        is Screen.Login -> {}
                    }
                    // 歌曲操作覆蓋層（右側面板）
                    if (ui.songActions != null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.CenterEnd) {
                            SongActionsOverlay(vm)
                        }
                    }
                }
                // 全域迷你播放列：全螢幕播放頁不顯示
                if (screen !is Screen.NowPlaying) {
                    MiniPlayerBar(
                        state = playback,
                        onOpenNowPlaying = { vm.navigate(Screen.NowPlaying) },
                    )
                }
            }
        }
    }

    // 重啟後讓使用者選擇继续播放
    if (ui.showResumePrompt) {
        val resumeFocus = androidx.compose.runtime.remember { androidx.compose.ui.focus.FocusRequester() }
        androidx.compose.runtime.LaunchedEffect(Unit) {
            androidx.compose.runtime.withFrameNanos { }
            resumeFocus.requestFocus()
        }
        AlertDialog(
            onDismissRequest = { vm.dismissResumePrompt() },
            title = { Text("继续播放？", color = TextPrimary) },
            text = { Text("上次播放到《${playback.sourceName.ifEmpty { "上次列表" }}》，可以从原位置继续。", color = com.navidrome.tv.ui.theme.TextSecondary) },
            confirmButton = {
                com.navidrome.tv.ui.components.PillButton("继续播放", null, false,
                    Modifier.focusRequester(resumeFocus)) { vm.resumePlayback() }
            },
            dismissButton = {
                com.navidrome.tv.ui.components.PillButton("稍后再听", null, false) { vm.dismissResumePrompt() }
            },
            containerColor = com.navidrome.tv.ui.theme.BgPanel,
        )
    }
}
