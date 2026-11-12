package com.navidrome.tv.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navidrome.tv.TvApp
import com.navidrome.tv.model.*
import com.navidrome.tv.playback.PlaybackAccess
import com.navidrome.tv.ui.AppViewModel
import com.navidrome.tv.ui.collectAsStateValue
import com.navidrome.tv.ui.components.*
import com.navidrome.tv.ui.nav.Screen
import com.navidrome.tv.ui.theme.*

@Composable
fun ArtistsScreen(vm: AppViewModel) {
    val app = LocalContext.current.applicationContext as TvApp
    var artists by remember { vm.cached("artists:data") { mutableStateOf<List<Artist>>(emptyList()) } }
    var loading by remember { mutableStateOf(artists.isEmpty()) }
    var focusedId by remember { vm.cached("artists:focus") { mutableStateOf("") } }
    val grid = remember { vm.cached("artists:scroll") { LazyGridState() } }
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (artists.isEmpty()) app.repository.artists().onSuccess { artists = it }
        if (focusedId.isEmpty()) focusedId = artists.firstOrNull()?.id ?: ""
        loading = false
    }
    ContentFocus(requester, !loading && artists.isNotEmpty())
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        ScreenHeader("歌手", "${artists.size} 位歌手")
        if (loading) LoadingState(Modifier.weight(1f)) else LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp), state = grid, modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(artists, key = { it.id }) { artist ->
                FocusBox({ vm.navigate(Screen.ArtistDetail(artist.id, artist.name)) },
                    modifier = Modifier.fillMaxWidth()
                        .then(if (artist.id == focusedId) Modifier.focusRequester(requester) else Modifier)
                        .onFocusChanged { if (it.isFocused) focusedId = artist.id }, scaleOnFocus = false) {
                    Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        CoverImage(artist.coverArt, 160.dp, circle = true, adaptive = true)
                        PrimaryText(artist.name, Modifier.padding(top = 8.dp), 18)
                        SecondaryText("${artist.albumCount} 张专辑", fontSize = 14)
                    }
                }
            }
        }
    }
}

@Composable
fun AlbumsScreen(vm: AppViewModel) {
    val app = LocalContext.current.applicationContext as TvApp
    var albums by remember { vm.cached("albums:data") { mutableStateOf<List<Album>>(emptyList()) } }
    var loading by remember { mutableStateOf(albums.isEmpty()) }
    var loadingMore by remember { mutableStateOf(false) }
    var exhausted by remember { vm.cached("albums:exhausted") { mutableStateOf(false) } }
    var focusedId by remember { vm.cached("albums:focus") { mutableStateOf("") } }
    val grid = remember { vm.cached("albums:scroll") { LazyGridState() } }
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (albums.isEmpty()) app.repository.albumsAlphabetical(0).onSuccess { albums = it }
        if (focusedId.isEmpty()) focusedId = albums.firstOrNull()?.id ?: ""
        loading = false
    }
    ContentFocus(requester, !loading && albums.isNotEmpty())
    val nearEnd by remember { derivedStateOf {
        albums.isNotEmpty() && (grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= albums.size - 8
    } }
    LaunchedEffect(nearEnd, albums.size) {
        if (nearEnd && !loadingMore && !exhausted) {
            loadingMore = true
            app.repository.albumsAlphabetical(albums.size).onSuccess { more ->
                exhausted = more.isEmpty()
                albums = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { com.navidrome.tv.util.mergeDistinct(albums, more) { it.id } }
            }
            loadingMore = false
        }
    }
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        ScreenHeader("专辑", "已载入 ${albums.size} 张")
        if (loading) LoadingState(Modifier.weight(1f)) else LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp), state = grid, modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(albums, key = { it.id }) { album ->
                AlbumCard(album, if (album.id == focusedId) requester else null,
                    onFocused = { focusedId = album.id }) { vm.navigate(Screen.AlbumDetail(album.id)) }
            }
        }
    }
}

@Composable
fun AlbumCard(album: Album, focusRequester: FocusRequester? = null, onFocused: (() -> Unit)? = null, onClick: () -> Unit) {
    FocusBox(onClick, modifier = Modifier.fillMaxWidth()
        .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
        .onFocusChanged { if (it.isFocused) onFocused?.invoke() }, scaleOnFocus = false) {
        Column(Modifier.padding(10.dp)) {
            CoverImage(album.coverArt, 180.dp, adaptive = true)
            Text(album.name, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 2, minLines = 2, lineHeight = 21.sp, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
            SecondaryText(album.artist, Modifier.padding(top = 2.dp), 14)
        }
    }
}

@Composable
fun ArtistDetailScreen(vm: AppViewModel, artistId: String, name: String) {
    val context = LocalContext.current
    val app = context.applicationContext as TvApp
    val key = "artist:$artistId"
    var albums by remember { vm.cached("$key:albums") { mutableStateOf<List<Album>>(emptyList()) } }
    var songs by remember { vm.cached("$key:songs") { mutableStateOf<List<Song>>(emptyList()) } }
    var loaded by remember { vm.cached("$key:loaded") { mutableStateOf(false) } }
    var tab by remember { vm.cached("$key:tab") { mutableStateOf(0) } }
    var focusedIndex by remember { vm.cached("$key:focus") { mutableStateOf(0) } }
    var focusedAlbum by remember { vm.cached("$key:albumFocus") { mutableStateOf("") } }
    val list = remember { vm.cached("$key:scroll") { LazyListState() } }
    val grid = remember { vm.cached("$key:grid") { LazyGridState() } }
    val requester = remember(tab) { FocusRequester() }
    val playback = vm.playback.collectAsStateValue()
    val source = QueueSource.ArtistSongs(artistId, name)
    LaunchedEffect(artistId) {
        if (!loaded) {
            app.repository.artistAlbums(artistId).onSuccess { albums = it }
            focusedAlbum = albums.firstOrNull()?.id ?: ""
            loaded = true
        }
    }
    var songsLoaded by remember { vm.cached("$key:songsLoaded") { mutableStateOf(false) } }
    var songError by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableStateOf(0) }
    LaunchedEffect(artistId, loaded, tab, retry) {
        if (loaded && tab == 0 && !songsLoaded) {
            songError = null
            app.repository.songsForAlbums(albums).onSuccess { songs = it; songsLoaded = true }
                .onFailure { songError = it.message ?: "无法载入歌曲" }
        }
    }
    ContentFocus(requester, loaded && if (tab == 0) songsLoaded && songs.isNotEmpty() else albums.isNotEmpty())
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        PageHeader("歌手") { vm.back() }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CoverImage(albums.firstOrNull()?.coverArt, 64.dp, circle = true)
            Column(Modifier.weight(1f)) {
                PrimaryText(name, fontSize = 28)
                SecondaryText("${albums.size} 张专辑 · ${songs.size} 首歌曲", fontSize = 15)
            }
        }
        TabRow(listOf("歌曲", "专辑"), tab) { tab = it }
        if (!loaded) LoadingState(Modifier.weight(1f)) else if (tab == 0 && songError != null) {
            EmptyState(Icons.Filled.CloudOff, "歌曲载入失败", songError!!, "重试", { retry++ }, Modifier.weight(1f))
        } else if (tab == 0 && !songsLoaded) LoadingState(Modifier.weight(1f)) else if (tab == 0) {
            if (songs.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                PillButton("播放全部", Icons.Filled.PlayArrow, true) { PlaybackAccess.playFrom(context, songs, 0, source) }
                PillButton("随机播放", Icons.Filled.Shuffle, false) { PlaybackAccess.playFrom(context, songs.shuffled(), 0, source) }
            }
            LazyColumn(state = list, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(songs, key = { _, s -> s.id }) { index, song ->
                    SongRow(song, index, playback.currentSong?.id == song.id, false, true,
                        { PlaybackAccess.playFrom(context, songs, index, source) }, { vm.openSongActions(song, songs, index) },
                        modifier = if (index == focusedIndex) Modifier.focusRequester(requester) else Modifier,
                        onFocused = { focusedIndex = index }, onLeft = { FocusBus.requestSidebar() })
                }
            }
        } else LazyVerticalGrid(columns = GridCells.Adaptive(150.dp), state = grid, modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(albums, key = { it.id }) { album ->
                AlbumCard(album, if (album.id == focusedAlbum) requester else null, { focusedAlbum = album.id }) {
                    vm.navigate(Screen.AlbumDetail(album.id))
                }
            }
        }
    }
}

@Composable
fun AlbumDetailScreen(vm: AppViewModel, albumId: String) {
    val context = LocalContext.current
    val app = context.applicationContext as TvApp
    val key = "album:$albumId"
    var album by remember { vm.cached("$key:data") { mutableStateOf<Album?>(null) } }
    var songs by remember { vm.cached("$key:songs") { mutableStateOf<List<Song>>(emptyList()) } }
    var loaded by remember { vm.cached("$key:loaded") { mutableStateOf(false) } }
    var focusedIndex by remember { vm.cached("$key:focus") { mutableStateOf(0) } }
    val list = remember { vm.cached("$key:scroll") { LazyListState() } }
    val playback = vm.playback.collectAsStateValue()
    val requester = remember { FocusRequester() }
    LaunchedEffect(albumId) {
        if (!loaded) {
            app.repository.albumDetails(albumId).onSuccess { (details, tracks) -> album = details; songs = tracks }
            loaded = true
        }
    }
    ContentFocus(requester, loaded && songs.isNotEmpty())
    if (!loaded) { LoadingState(Modifier.fillMaxSize()); return }
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        PageHeader("专辑") { vm.back() }
        Spacer(Modifier.height(12.dp))
        BoxWithConstraints(Modifier.weight(1f)) {
            val coverSize = minOf(maxHeight * .42f, 190.dp)
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.width(210.dp).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CoverImage(album?.coverArt, coverSize)
                    Text(album?.name ?: "", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    SecondaryText(album?.artist ?: "", fontSize = 16)
                    SecondaryText("${album?.year?.let { "$it · " } ?: ""}${songs.size} 首歌曲", fontSize = 14)
                    if (songs.isNotEmpty()) {
                        PillButton("播放全部", Icons.Filled.PlayArrow, true) {
                            PlaybackAccess.playFrom(context, songs, 0, QueueSource.AlbumSongs(albumId, album?.name ?: "专辑"))
                        }
                        PillButton("随机播放", Icons.Filled.Shuffle, false) {
                            PlaybackAccess.playFrom(context, songs.shuffled(), 0, QueueSource.AlbumSongs(albumId, album?.name ?: "专辑"))
                        }
                    }
                }
                LazyColumn(state = list, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    itemsIndexed(songs, key = { _, s -> s.id }) { index, song ->
                        SongRow(song, index, playback.currentSong?.id == song.id, false, false,
                            { PlaybackAccess.playFrom(context, songs, index, QueueSource.AlbumSongs(albumId, album?.name ?: "专辑")) },
                            { vm.openSongActions(song, songs, index) },
                            modifier = if (index == focusedIndex) Modifier.focusRequester(requester) else Modifier,
                            onFocused = { focusedIndex = index })
                    }
                }
            }
        }
    }
}
