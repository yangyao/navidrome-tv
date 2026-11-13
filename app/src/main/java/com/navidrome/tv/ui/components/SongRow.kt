package com.navidrome.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navidrome.tv.R
import com.navidrome.tv.model.Song
import com.navidrome.tv.playback.PlaybackAccess
import com.navidrome.tv.ui.theme.*

@Composable
fun SongRow(song: Song, index: Int?, isCurrent: Boolean, showArtist: Boolean, showAlbum: Boolean,
    onClick: () -> Unit, onMore: () -> Unit, modifier: Modifier = Modifier,
    onLeft: (() -> Unit)? = null, onDownEdge: (() -> Unit)? = null,
    onFocused: (() -> Unit)? = null, trailing: @Composable (() -> Unit)? = null) {
    var focused by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val rowDesc = stringResource(R.string.song_row_desc, song.title, song.artist,
        stringResource(if (isCurrent) R.string.play_or_pause else R.string.play_inline))
    val shape = RoundedCornerShape(10.dp)
    Row(modifier.fillMaxWidth().height(64.dp).clip(shape)
        .background(if (focused) BgRowFocused else if (isCurrent) BgRow else Color.Transparent)
        .then(if (focused) Modifier.border(2.dp, Accent, shape) else Modifier)
        .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused?.invoke() }
        .onPreviewKeyEvent { e ->
            when {
                e.key == Key.DirectionRight -> { if (e.type == KeyEventType.KeyDown) onMore(); true }
                e.key == Key.DirectionLeft && onLeft != null -> { if (e.type == KeyEventType.KeyDown) onLeft(); true }
                e.key == Key.DirectionDown && onDownEdge != null -> { if (e.type == KeyEventType.KeyDown) onDownEdge(); true }
                else -> false
            }
        }
        .semantics { contentDescription = rowDesc }
        .clickable { if (isCurrent) PlaybackAccess.togglePause(context) else onClick() }
        .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (index != null) Text((index + 1).toString(), color = TextTertiary, fontSize = 14.sp, modifier = Modifier.width(22.dp))
        Box(Modifier.width(22.dp)) { if (isCurrent) PlayingIndicator() }
        CoverImage(song.coverArt, 40.dp)
        PrimaryText(song.title, Modifier.weight(2.4f), 17, marquee = focused)
        if (showArtist) SecondaryText(song.artist, Modifier.weight(1.2f), 15)
        if (showAlbum) SecondaryText(song.album, Modifier.weight(1.2f), 15)
        Text(formatDuration(song.durationSec), color = TextSecondary, fontSize = 14.sp, maxLines = 1, softWrap = false,
            modifier = Modifier.width(42.dp))
        trailing?.invoke()
    }
}
