package com.navidrome.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navidrome.tv.ui.AppViewModel
import com.navidrome.tv.ui.collectAsStateValue
import com.navidrome.tv.ui.components.*
import com.navidrome.tv.ui.nav.Screen
import com.navidrome.tv.ui.theme.*

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun SongActionsOverlay(vm: AppViewModel) {
    val ui = vm.ui.collectAsStateValue()
    val song = ui.songActions ?: return
    val context = androidx.compose.ui.platform.LocalContext.current
    val first = remember { FocusRequester() }
    LaunchedEffect(song.id) { withFrameNanos { }; first.requestFocus() }
    Column(Modifier.widthIn(max = 390.dp).fillMaxHeight().background(BgPanel)
        .focusProperties { left = FocusRequester.Cancel; right = FocusRequester.Cancel }
        .focusGroup().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("歌曲操作", color = TextPrimary, fontSize = 26.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CoverImage(song.coverArt, 56.dp)
            Column(Modifier.weight(1f)) { PrimaryText(song.title, fontSize = 20); SecondaryText(song.artist, fontSize = 15) }
        }
        ActionRow(Icons.Filled.PlayArrow, "立即播放", Modifier.focusRequester(first).focusProperties { up = FocusRequester.Cancel }) {
            ui.songActionsSource?.let { if (ui.songActionsIndex >= 0) vm.playSongs(context, it, ui.songActionsIndex, ui.songActionsQueueSource) }
            vm.closeSongActions()
        }
        ActionRow(Icons.Filled.SkipNext, "下一首播放") {
            com.navidrome.tv.playback.PlaybackAccess.playNext(context, song); vm.closeSongActions()
        }
        ActionRow(Icons.Filled.AddToQueue, "加入队列") {
            com.navidrome.tv.playback.PlaybackAccess.addToQueue(context, song); vm.closeSongActions()
        }
        song.artistId?.let { id -> ActionRow(Icons.Filled.Person, "查看歌手") {
            vm.closeSongActions(); vm.navigate(Screen.ArtistDetail(id, song.artist))
        } }
        song.albumId?.let { id -> ActionRow(Icons.Filled.Album, "查看专辑") {
            vm.closeSongActions(); vm.navigate(Screen.AlbumDetail(id))
        } }
        ActionRow(Icons.Filled.Close, "关闭", Modifier.focusProperties { down = FocusRequester.Cancel }) { vm.closeSongActions() }
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
