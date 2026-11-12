package com.navidrome.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navidrome.tv.playback.PlaybackAccess
import com.navidrome.tv.playback.PlaybackState
import com.navidrome.tv.playback.PlaybackService
import com.navidrome.tv.ui.collectAsStateValue
import androidx.compose.ui.input.key.*
import com.navidrome.tv.ui.theme.*

@Composable
fun MiniPlayerBar(state: PlaybackState, onOpenNowPlaying: () -> Unit, modifier: Modifier = Modifier) {
    val song = state.currentSong ?: return
    val requester = remember { FocusRequester() }
    DisposableEffect(Unit) {
        FocusBus.miniBar = requester
        onDispose { if (FocusBus.miniBar === requester) FocusBus.miniBar = null }
    }
    Row(modifier.fillMaxWidth().height(80.dp).background(Color(0xFF131A20)).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        FocusBox(onClick = onOpenNowPlaying, modifier = Modifier.weight(1.3f), scaleOnFocus = false) {
            Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CoverImage(song.coverArt, 48.dp)
                Column(Modifier.weight(1f)) {
                    PrimaryText(song.title, fontSize = 17)
                    SecondaryText(song.artist, fontSize = 13)
                }
            }
        }
        val context = LocalContext.current
        TransportControls(state, true, { PlaybackAccess.previous(context) },
            { PlaybackAccess.togglePause(context) }, { PlaybackAccess.next(context) }, pauseFocusRequester = requester)
        PlayerProgress(modifier = Modifier.weight(.8f))
    }
}

@Composable
fun TransportControls(state: PlaybackState, compact: Boolean, onPrev: () -> Unit, onToggle: () -> Unit,
    onNext: () -> Unit, modifier: Modifier = Modifier, pauseFocusRequester: FocusRequester? = null) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 20.dp)) {
        TransportIcon(Icons.Filled.SkipPrevious, !compact, onPrev, "上一首")
        FocusBox(onToggle, modifier = Modifier.size(if (compact) 52.dp else 68.dp)
            .then(if (pauseFocusRequester != null) Modifier.focusRequester(pauseFocusRequester) else Modifier),
            shape = CircleShape, unfocusedBg = BgPanel, scaleOnFocus = false) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(if (state.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    if (state.playing) "暂停" else "播放", tint = Accent, modifier = Modifier.size(if (compact) 28.dp else 36.dp))
            }
        }
        TransportIcon(Icons.Filled.SkipNext, !compact, onNext, "下一首")
    }
}

@Composable
fun TransportIcon(icon: ImageVector, big: Boolean, onClick: () -> Unit, label: String = "播放控制") {
    FocusBox(onClick, modifier = Modifier.size(if (big) 56.dp else 40.dp), shape = CircleShape,
        unfocusedBg = BgPanel, scaleOnFocus = false) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = TextPrimary, modifier = Modifier.size(if (big) 30.dp else 24.dp))
        }
    }
}

@Composable
fun ProgressTrack(progress: Float, modifier: Modifier = Modifier, trackHeight: Int = 6) {
    Box(modifier.height(trackHeight.dp).clip(RoundedCornerShape(trackHeight.dp)).background(Color(0xFF2A333C))) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(Accent))
    }
}

/** Only this component subscribes to the 500ms position clock. */
@Composable
fun PlayerProgress(modifier: Modifier = Modifier, interactive: Boolean = false) {
    val progress = PlaybackService.progress.collectAsStateValue()
    val context = LocalContext.current
    val content: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().then(if (interactive) Modifier.padding(10.dp) else Modifier),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(formatMs(progress.positionMs), color = TextSecondary,
                fontSize = if (interactive) 15.sp else 13.sp, maxLines = 1, softWrap = false)
            ProgressTrack(if (progress.durationMs > 0) progress.positionMs.toFloat() / progress.durationMs else 0f,
                Modifier.weight(1f))
            Text(formatMs(progress.durationMs), color = TextSecondary,
                fontSize = if (interactive) 15.sp else 13.sp, maxLines = 1, softWrap = false)
        }
    }
    if (interactive) FocusBox(onClick = { PlaybackAccess.togglePause(context) }, scaleOnFocus = false,
        modifier = modifier.onPreviewKeyEvent { event ->
            if (event.key == Key.DirectionLeft || event.key == Key.DirectionRight) {
                if (event.type == KeyEventType.KeyDown) PlaybackAccess.seekTo(context,
                    (progress.positionMs + if (event.key == Key.DirectionLeft) -10_000 else 10_000)
                        .coerceIn(0, progress.durationMs))
                true
            } else false
        }) { content() }
    else Box(modifier) { content() }
}
