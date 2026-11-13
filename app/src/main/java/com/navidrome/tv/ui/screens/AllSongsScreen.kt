package com.navidrome.tv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navidrome.tv.R
import com.navidrome.tv.TvApp
import com.navidrome.tv.ui.collectAsStateValue
import com.navidrome.tv.model.QueueSource
import com.navidrome.tv.playback.PlaybackAccess
import com.navidrome.tv.ui.AppViewModel
import com.navidrome.tv.ui.components.ErrorState
import com.navidrome.tv.ui.components.LoadingState
import com.navidrome.tv.ui.components.PillButton
import com.navidrome.tv.ui.components.ScreenHeader
import com.navidrome.tv.ui.components.SongRow
import com.navidrome.tv.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/**
 * 所有歌曲（依設計圖 01）：預設首頁。search3 空查詢分頁，首批 100 首，
 * 瀏覽靠近尾端時預取下一頁；確認立即播放並留在原清單。
 */
@Composable
fun AllSongsScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val app = context.applicationContext as TvApp
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    var songs by remember { vm.cached("songs:data") { mutableStateOf<List<com.navidrome.tv.model.Song>>(emptyList()) } }
    var loading by remember { mutableStateOf(songs.isEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loadingMore by remember { mutableStateOf(false) }

    val playback = vm.playback.collectAsStateValue()
    val listState = remember { vm.cached("songs:scroll") { androidx.compose.foundation.lazy.LazyListState() } }
    var focusedIndex by remember { vm.cached("songs:focus") { mutableStateOf(0) } }
    var exhausted by remember { vm.cached("songs:exhausted") { mutableStateOf(false) } }
    val focusRequester = remember { FocusRequester() }

    suspend fun loadFirst() {
        loading = true; error = null; exhausted = false
        val result = app.repository.songsPage(0, 100)
        result.onSuccess { songs = it; loading = false }
            .onFailure { error = it.message ?: context.getString(R.string.err_load_failed); loading = false }
    }

    LaunchedEffect(Unit) { if (songs.isEmpty()) loadFirst() }
    com.navidrome.tv.ui.components.ContentFocus(focusRequester, songs.isNotEmpty() && !loading)

    // 靠近已載入末尾時預取下一頁
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            songs.isNotEmpty() && last >= songs.size - 15
        }
    }
    LaunchedEffect(nearEnd, songs.size) {
        if (nearEnd && !loadingMore && !exhausted) {
            loadingMore = true
            app.repository.songsPage(songs.size, 100).onSuccess { more ->
                exhausted = more.size < 100
                if (more.isNotEmpty()) songs = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { com.navidrome.tv.util.mergeDistinct(songs, more) { it.id } }
            }
            loadingMore = false
        }
    }

    Column(Modifier.fillMaxSize().padding(start = 40.dp, end = 40.dp, top = 28.dp)) {
        ScreenHeader(
            title = stringResource(R.string.nav_songs),
            subtitle = if (songs.isNotEmpty()) stringResource(R.string.songs_loaded, songs.size) else null,
            trailing = {
                PillButton(stringResource(R.string.shuffle_play), Icons.Filled.Shuffle, filled = true) { vm.shuffleAll(context) }
            },
        )
        when {
            loading -> LoadingState(Modifier.weight(1f))
            error != null -> ErrorState(
                title = stringResource(R.string.error_server_title),
                body = error ?: "",
                retryText = stringResource(R.string.retry_connect),
                onRetry = { scope.launch { loadFirst() } },
                reSetupText = stringResource(R.string.settings_relogin),
                onReSetup = { vm.beginReconfigure() },
                modifier = Modifier.weight(1f),
            )
            songs.isEmpty() -> Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                androidx.compose.material3.Text(stringResource(R.string.empty_all_songs), color = TextSecondary)
            }
            else -> {
                LazyColumn(state = listState, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    itemsIndexed(songs, key = { _, s -> s.id }) { index, song ->
                        val isCurrent = playback.currentSong?.id == song.id
                        SongRow(
                            song = song,
                            index = null,
                            isCurrent = isCurrent,
                            showArtist = true,
                            showAlbum = true,
                            onClick = {
                                PlaybackAccess.playFrom(context, songs.toList(), index, QueueSource.AllSongs())
                            },
                            onMore = { vm.openSongActions(song, songs.toList(), index) },
                            onLeft = { com.navidrome.tv.ui.components.FocusBus.requestSidebar() },
                            onDownEdge = if (index == songs.lastIndex) {
                                { com.navidrome.tv.ui.components.FocusBus.requestMiniBar() }
                            } else null,
                            onFocused = { focusedIndex = index },
                            modifier = if (index == focusedIndex) Modifier.focusRequester(focusRequester) else Modifier,
                        )
                    }
                }
            }
        }
    }
}
