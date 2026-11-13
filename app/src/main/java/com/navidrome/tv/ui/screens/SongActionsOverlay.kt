package com.navidrome.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.navidrome.tv.R
import com.navidrome.tv.playback.PlaybackAccess
import com.navidrome.tv.playback.SongPlaybackAction
import com.navidrome.tv.ui.AppViewModel
import com.navidrome.tv.ui.collectAsStateValue
import com.navidrome.tv.ui.components.*
import com.navidrome.tv.ui.nav.Screen
import com.navidrome.tv.ui.theme.*

private data class SongMenuItem(val icon: ImageVector, val label: String, val action: () -> Unit)

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun SongActionsOverlay(vm: AppViewModel) {
    val ui = vm.ui.collectAsStateValue()
    val song = ui.songActions ?: return
    val playback = vm.playback.collectAsStateValue()
    val primary = SongPlaybackAction.forSong(song.id, playback)
    val context = androidx.compose.ui.platform.LocalContext.current
    val first = remember(song.id) { FocusRequester() }
    val close = remember { FocusRequester() }
    val items = buildList {
        add(SongMenuItem(if (primary == SongPlaybackAction.PAUSE) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            stringResource(primary.labelRes)) { vm.performSongPrimaryAction() })
        if (playback.currentSong?.id == song.id) add(SongMenuItem(Icons.Filled.OpenInFull, stringResource(R.string.now_playing)) {
            vm.navigate(Screen.NowPlaying)
        })
        add(SongMenuItem(Icons.Filled.SkipNext, stringResource(R.string.play_next)) {
            PlaybackAccess.playNext(context, song); vm.closeSongActions()
        })
        add(SongMenuItem(Icons.Filled.AddToQueue, stringResource(R.string.add_to_queue)) {
            PlaybackAccess.addToQueue(context, song); vm.closeSongActions()
        })
        song.artistId?.let { id -> add(SongMenuItem(Icons.Filled.Person, stringResource(R.string.view_artist)) {
            vm.navigate(Screen.ArtistDetail(id, song.artist))
        }) }
        song.albumId?.let { id -> add(SongMenuItem(Icons.Filled.Album, stringResource(R.string.view_album)) {
            vm.navigate(Screen.AlbumDetail(id))
        }) }
    }
    // A separate window keeps sidebar / mini-player focus outside the modal focus tree.
    Dialog(onDismissRequest = { vm.closeSongActions() },
        properties = DialogProperties(usePlatformDefaultWidth = false)) {
        LaunchedEffect(song.id) { withFrameNanos { }; first.requestFocus() }
        Box(Modifier.fillMaxSize().onPreviewKeyEvent { event ->
            if (event.key == Key.DirectionLeft) {
                if (event.type == KeyEventType.KeyDown) vm.closeSongActions()
                true
            } else false
        }) {
            Box(Modifier.matchParentSize().pointerInput(Unit) {
                detectTapGestures { vm.closeSongActions() }
            })
            Column(Modifier.align(Alignment.CenterEnd).width(390.dp).fillMaxHeight().background(BgPanel)
                .focusProperties { right = FocusRequester.Cancel }.focusGroup()
                .verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.song_actions), color = TextPrimary, fontSize = 26.sp, modifier = Modifier.weight(1f))
                    FocusBox({ vm.closeSongActions() }, Modifier.size(36.dp).focusRequester(close)
                        .focusProperties { up = FocusRequester.Cancel; down = first; right = FocusRequester.Cancel },
                        scaleOnFocus = false) {
                        Icon(Icons.Filled.Close, stringResource(R.string.close_song_actions), tint = TextPrimary,
                            modifier = Modifier.align(Alignment.Center).size(24.dp))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CoverImage(song.coverArt, 56.dp)
                    Column(Modifier.weight(1f)) {
                        PrimaryText(song.title, fontSize = 20)
                        SecondaryText(song.artist, fontSize = 15)
                    }
                }
                items.forEachIndexed { index, item ->
                    ActionRow(item.icon, item.label, Modifier
                        .then(if (index == 0) Modifier.focusRequester(first) else Modifier)
                        .focusProperties {
                            if (index == 0) up = close
                            if (index == items.lastIndex) down = FocusRequester.Cancel
                        }, item.action)
                }
            }
        }
    }
}

@Composable
private fun ActionRow(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FocusBox(onClick, modifier.fillMaxWidth(), unfocusedBg = BgRow, scaleOnFocus = false) {
        Row(Modifier.height(48.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, tint = TextPrimary, modifier = Modifier.size(24.dp))
            Text(label, color = TextPrimary, fontSize = 18.sp)
        }
    }
}
