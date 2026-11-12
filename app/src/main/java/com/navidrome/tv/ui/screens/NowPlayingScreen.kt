package com.navidrome.tv.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navidrome.tv.playback.PlaybackMode
import androidx.compose.material3.AlertDialog
import com.navidrome.tv.playback.PlaybackAccess
import com.navidrome.tv.ui.AppViewModel
import com.navidrome.tv.ui.collectAsStateValue
import com.navidrome.tv.ui.components.*
import com.navidrome.tv.ui.nav.Screen
import com.navidrome.tv.ui.theme.*

@Composable
fun NowPlayingScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val state = vm.playback.collectAsStateValue()
    val song = state.currentSong
    var choosingMode by remember { mutableStateOf(false) }
    val modeFocus = remember { FocusRequester() }
    val mode = PlaybackMode.from(state)
    val pause = remember { FocusRequester() }
    ContentFocus(pause, song != null)
    if (song == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(Icons.Filled.MusicNote, "没有正在播放的歌曲", "从歌曲、歌手或专辑选一首开始播放。", "返回", { vm.back() })
        }
        return
    }
    Column(Modifier.fillMaxSize().padding(24.dp)) {
      PageHeader("正在播放") { vm.back() }
      BoxWithConstraints(Modifier.weight(1f)) {
        val coverSize = minOf(maxHeight - 24.dp, maxWidth * .38f, 340.dp).coerceAtLeast(140.dp)
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            CoverImage(song.coverArt, coverSize)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(song.title, color = TextPrimary, fontSize = 32.sp, fontWeight = FontWeight.Bold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(song.artist, color = TextPrimary, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                SecondaryText(song.album, fontSize = 16)
                PlayerProgress(Modifier.fillMaxWidth(), interactive = true)
                TransportControls(state, false, { PlaybackAccess.previous(context) },
                    { PlaybackAccess.togglePause(context) }, { PlaybackAccess.next(context) }, pauseFocusRequester = pause)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PillButton(mode.label, when (mode) {
                        PlaybackMode.SHUFFLE -> Icons.Filled.Shuffle
                        PlaybackMode.REPEAT_ONE -> Icons.Filled.RepeatOne
                        else -> Icons.Filled.Repeat
                    }, false, Modifier.focusRequester(modeFocus)) { choosingMode = true }
                    PillButton("播放队列", Icons.Filled.QueueMusic, false) { vm.navigate(Screen.Queue) }
                }
            }
        }
    }
    }
    if (choosingMode) {
        val selected = remember { FocusRequester() }
        LaunchedEffect(Unit) { selected.requestFocus() }
        AlertDialog(onDismissRequest = { choosingMode = false; modeFocus.requestFocus() },
            containerColor = BgPanel,
            title = { Text("播放模式", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlaybackMode.entries.forEach { option ->
                        PillButton(option.label, if (option == mode) Icons.Filled.Check else null,
                            option == mode, Modifier.fillMaxWidth().then(if (option == mode) Modifier.focusRequester(selected) else Modifier)) {
                            PlaybackAccess.setPlaybackMode(context, option)
                            choosingMode = false
                            modeFocus.requestFocus()
                        }
                    }
                }
            }, confirmButton = {})
    }
}

@Composable
fun QueueScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val state = vm.playback.collectAsStateValue()
    val requester = remember { FocusRequester() }
    val listState = remember { vm.cached("queue:scroll") { androidx.compose.foundation.lazy.LazyListState() } }
    var focusedIndex by remember { vm.cached("queue:focus") { mutableStateOf(0) } }
    if (focusedIndex > state.queue.lastIndex && state.queue.isNotEmpty()) focusedIndex = state.queue.lastIndex
    ContentFocus(requester, state.queue.isNotEmpty())
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        PageHeader("播放队列 · ${state.queue.size} 首") { vm.back() }
        if (state.queue.isEmpty()) {
            EmptyState(Icons.Filled.QueueMusic, "队列是空的", "在歌曲上按右，选择加入队列。", "所有歌曲", { vm.switchNav(NavItem.SONGS) })
        } else LazyColumn(state = listState, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(state.queue) { index, song ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FocusBox({ if (index == state.currentIndex) PlaybackAccess.togglePause(context) else PlaybackAccess.jumpTo(context, index) },
                        modifier = Modifier.weight(1f)
                            .then(if (index == focusedIndex) Modifier.focusRequester(requester) else Modifier)
                            .onFocusChanged { if (it.isFocused) focusedIndex = index },
                        unfocusedBg = if (index == state.currentIndex) BgRow else BgPanel, scaleOnFocus = false) {
                        Row(Modifier.fillMaxWidth().height(66.dp).padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text((index + 1).toString(), color = TextSecondary, fontSize = 14.sp, modifier = Modifier.width(26.dp))
                            Box(Modifier.width(22.dp)) { if (index == state.currentIndex) PlayingIndicator() }
                            CoverImage(song.coverArt, 40.dp)
                            Column(Modifier.weight(1f)) {
                                PrimaryText(song.title, fontSize = 17)
                                SecondaryText(song.artist, fontSize = 14)
                            }
                            Text(formatDuration(song.durationSec), color = TextSecondary, fontSize = 14.sp)
                        }
                    }
                    FocusBox({ PlaybackAccess.removeAt(context, index) }, modifier = Modifier.size(44.dp), scaleOnFocus = false) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Close, "从队列移除 ${song.title}", tint = TextPrimary, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    }
}
