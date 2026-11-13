package com.navidrome.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navidrome.tv.R
import com.navidrome.tv.TvApp
import com.navidrome.tv.model.Playlist
import com.navidrome.tv.model.QueueSource
import com.navidrome.tv.model.Song
import com.navidrome.tv.playback.PlaybackAccess
import com.navidrome.tv.ui.AppViewModel
import com.navidrome.tv.ui.collectAsStateValue
import com.navidrome.tv.ui.components.CoverImage
import com.navidrome.tv.ui.components.EmptyState
import com.navidrome.tv.ui.components.LoadingState
import com.navidrome.tv.ui.components.PillButton
import com.navidrome.tv.ui.components.PlayingIndicator
import com.navidrome.tv.ui.components.PrimaryText
import com.navidrome.tv.ui.components.ScreenHeader
import com.navidrome.tv.ui.components.SecondaryText
import com.navidrome.tv.ui.components.SongRow
import com.navidrome.tv.ui.components.TabRow
import com.navidrome.tv.ui.components.formatDuration
import com.navidrome.tv.ui.nav.Screen
import com.navidrome.tv.ui.theme.Accent
import com.navidrome.tv.ui.theme.BgPanel
import com.navidrome.tv.ui.theme.BgRowFocused
import com.navidrome.tv.ui.theme.TextPrimary
import com.navidrome.tv.ui.theme.TextSecondary
import java.time.Duration
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder

/** 歌單瀏覽（依設計圖 06）。 */
@Composable
fun PlaylistsScreen(vm: AppViewModel) {
    val app = LocalContext.current.applicationContext as TvApp
    var playlists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        app.repository.playlists().onSuccess { playlists = it }
        loading = false
    }
    com.navidrome.tv.ui.components.ContentFocus(focusRequester, playlists.isNotEmpty())

    Column(Modifier.fillMaxSize().padding(start = 40.dp, end = 40.dp, top = 28.dp)) {
        ScreenHeader(stringResource(R.string.nav_playlists), if (playlists.isNotEmpty()) "(${pluralStringResource(R.plurals.playlists_count, playlists.size, playlists.size)})" else null)
        if (loading) {
            LoadingState(Modifier.fillMaxSize())
        } else if (playlists.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(Icons.Filled.Shuffle, stringResource(R.string.empty_playlists_title), stringResource(R.string.empty_playlists_body), stringResource(R.string.nav_songs),
                    { vm.switchNav(com.navidrome.tv.ui.components.NavItem.SONGS) })
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(180.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                items(playlists, key = { it.id }) { playlist ->
                    PlaylistCard(playlist, if (playlists.first() == playlist) focusRequester else null) {
                        vm.navigate(Screen.PlaylistDetail(playlist.id, playlist.name))
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistCard(playlist: Playlist, focusRequester: FocusRequester?, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clip(RoundedCornerShape(14.dp))
            .background(if (focused) BgRowFocused else BgPanel)
            .then(if (focused) Modifier.border(2.dp, Accent, RoundedCornerShape(14.dp)) else Modifier)
            .then(if (focused) Modifier.scale(1.03f) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
    ) {
        CoverImage(playlist.coverArt, 200.dp, adaptive = true)
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            PrimaryText(playlist.name, fontSize = 21)
            SecondaryText(pluralStringResource(R.plurals.songs_count, playlist.songCount, playlist.songCount), Modifier.padding(top = 2.dp), 16)
        }
    }
}

/** 歌單詳情（依設計圖 07）：標頭＋播放全部／隨機播放＋編號歌曲列。 */
@Composable
fun PlaylistDetailScreen(vm: AppViewModel, playlistId: String, name: String) {
    val app = LocalContext.current.applicationContext as TvApp
    val context = LocalContext.current
    var playlist by remember { mutableStateOf<Playlist?>(null) }
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val playback = vm.playback.collectAsStateValue()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(playlistId) {
        app.repository.playlists().onSuccess { playlists -> playlist = playlists.find { it.id == playlistId } }
        app.repository.playlistSongs(playlistId).onSuccess { songs = it }
        loading = false
    }
    com.navidrome.tv.ui.components.ContentFocus(focusRequester, songs.isNotEmpty())

    Column(Modifier.fillMaxSize().padding(start = 40.dp, end = 40.dp, top = 20.dp)) {
        com.navidrome.tv.ui.components.PageHeader(stringResource(R.string.nav_playlists)) { vm.back() }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(top = 16.dp, bottom = 14.dp),
        ) {
            CoverImage(playlist?.coverArt, 64.dp)
            Column(Modifier.weight(1f)) {
                PrimaryText(name, fontSize = 34)
                val trackCount = playlist?.songCount ?: songs.size
                SecondaryText(pluralStringResource(R.plurals.songs_count, trackCount, trackCount), Modifier.padding(top = 4.dp), 18)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton(stringResource(R.string.play_all), Icons.Filled.PlayArrow, filled = true) {
                    if (songs.isNotEmpty()) PlaybackAccess.playFrom(context, songs, 0, QueueSource.PlaylistSongs(playlistId, name))
                }
                PillButton(stringResource(R.string.shuffle_play), Icons.Filled.Shuffle, filled = false) {
                    if (songs.isNotEmpty()) PlaybackAccess.playFrom(context, songs.shuffled(), 0, QueueSource.PlaylistSongs(playlistId, name))
                }
            }
        }
        if (loading) {
            LoadingState(Modifier.fillMaxSize())
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(songs, key = { _, s -> s.id }) { index, song ->
                    SongRow(
                        song = song,
                        index = index,
                        isCurrent = playback.currentSong?.id == song.id,
                        showArtist = true,
                        showAlbum = true,
                        onClick = { PlaybackAccess.playFrom(context, songs, index, QueueSource.PlaylistSongs(playlistId, name)) },
                        onMore = { vm.openSongActions(song, songs, index) },
                        onLeft = { com.navidrome.tv.ui.components.FocusBus.requestSidebar() },
                        onDownEdge = if (index == songs.lastIndex) {
                            { com.navidrome.tv.ui.components.FocusBus.requestMiniBar() }
                        } else null,
                        modifier = if (index == 0) Modifier.focusRequester(focusRequester) else Modifier,
                    )
                }
            }
        }
    }
}

/** 收藏（依設計圖 08/16）：歌曲／專輯檢視。 */
@Composable
fun FavoritesScreen(vm: AppViewModel) {
    val app = LocalContext.current.applicationContext as TvApp
    val context = LocalContext.current
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var albums by remember { mutableStateOf<List<com.navidrome.tv.model.Album>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var tab by remember { mutableIntStateOf(0) }
    val playback = vm.playback.collectAsStateValue()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        app.repository.starred().onSuccess { (s, a) -> songs = s; albums = a }
        loading = false
    }
    com.navidrome.tv.ui.components.ContentFocus(focusRequester, songs.isNotEmpty() && tab == 0)

    Column(Modifier.fillMaxSize().padding(start = 40.dp, end = 40.dp, top = 28.dp)) {
        ScreenHeader(
            stringResource(R.string.nav_favorites),
            if (songs.isNotEmpty()) pluralStringResource(R.plurals.songs_count, songs.size, songs.size) else null,
            trailing = {
                if (tab == 0 && songs.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    PillButton(stringResource(R.string.play_all), Icons.Filled.PlayArrow, filled = true) {
                        if (songs.isNotEmpty()) PlaybackAccess.playFrom(context, songs, 0, QueueSource.Starred)
                    }
                    PillButton(stringResource(R.string.shuffle_play), Icons.Filled.Shuffle, filled = false) {
                        if (songs.isNotEmpty()) PlaybackAccess.playFrom(context, songs.shuffled(), 0, QueueSource.Starred)
                    }
                }
            },
        )
        TabRow(listOf(stringResource(R.string.songs_tab), stringResource(R.string.albums_tab)), tab) { tab = it }
        when {
            loading -> LoadingState(Modifier.fillMaxSize())
            tab == 0 && songs.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Filled.FavoriteBorder,
                    title = stringResource(R.string.empty_favorites_title),
                    body = stringResource(R.string.empty_favorites_body),
                    actionText = stringResource(R.string.browse_all_songs),
                    onAction = { vm.switchNav(com.navidrome.tv.ui.components.NavItem.SONGS) },
                )
            }
            tab == 0 -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    itemsIndexed(songs, key = { _, s -> s.id }) { index, song ->
                        SongRow(
                            song = song,
                            index = null,
                            isCurrent = playback.currentSong?.id == song.id,
                            showArtist = true,
                            showAlbum = false,
                            onClick = { PlaybackAccess.playFrom(context, songs, index, QueueSource.Starred) },
                            onMore = { vm.openSongActions(song, songs, index) },
                            onLeft = { com.navidrome.tv.ui.components.FocusBus.requestSidebar() },
                            onDownEdge = if (index == songs.lastIndex) {
                                { com.navidrome.tv.ui.components.FocusBus.requestMiniBar() }
                            } else null,
                            modifier = if (index == 0) Modifier.focusRequester(focusRequester) else Modifier,
                            trailing = {
                                androidx.compose.material3.Icon(
                                    Icons.Filled.Favorite,
                                    contentDescription = stringResource(R.string.favorited_desc),
                                    tint = com.navidrome.tv.ui.theme.Heart,
                                    modifier = Modifier.padding(start = 8.dp).width(24.dp),
                                )
                            },
                        )
                    }
                }
            }
            tab == 1 && albums.isEmpty() -> Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                EmptyState(Icons.Filled.FavoriteBorder, stringResource(R.string.empty_fav_albums_title), stringResource(R.string.empty_fav_albums_body), stringResource(R.string.browse_albums),
                    { vm.switchNav(com.navidrome.tv.ui.components.NavItem.ALBUMS) })
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(150.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    items(albums, key = { it.id }) { album ->
                        com.navidrome.tv.ui.screens.AlbumCard(album) { vm.navigate(Screen.AlbumDetail(album.id)) }
                    }
                }
            }
        }
    }
}
