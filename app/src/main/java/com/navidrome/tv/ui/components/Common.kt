package com.navidrome.tv.ui.components

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import coil.compose.AsyncImage
import com.navidrome.tv.R
import com.navidrome.tv.TvApp
import com.navidrome.tv.ui.theme.Accent
import com.navidrome.tv.ui.theme.AccentOnDark
import com.navidrome.tv.ui.theme.BgPanel
import com.navidrome.tv.ui.theme.BgRowFocused
import com.navidrome.tv.ui.theme.CardShape
import com.navidrome.tv.ui.theme.TextPrimary
import com.navidrome.tv.ui.theme.TextTertiary

fun formatDuration(sec: Int): String {
    val m = sec / 60
    val s = sec % 60
    return "%d:%02d".format(m, s)
}

fun formatMs(ms: Long): String {
    val total = ms / 1000
    return "%d:%02d".format(total / 60, total % 60)
}

/** 封面：依顯示尺寸向伺服器請求，走 Coil 記憶體／磁碟快取。 */
@Composable
fun CoverImage(coverArt: String?, size: Dp, modifier: Modifier = Modifier, circle: Boolean = false, adaptive: Boolean = false) {
    val context = LocalContext.current
    val app = context.applicationContext as TvApp
    val connection = app.connection()
    val density = LocalDensity.current
    val shape: Shape = if (circle) CircleShape else CardShape
    BoxWithConstraints(
        modifier = modifier
            .then(if (adaptive) Modifier.fillMaxWidth().aspectRatio(1f) else Modifier.size(size))
            .clip(shape).background(BgPanel),
        contentAlignment = Alignment.Center,
    ) {
        val pixels = if (adaptive) constraints.maxWidth.coerceAtLeast(1)
            else with(density) { size.roundToPx() }.coerceAtLeast(1)
        val request by produceState<ImageRequest?>(null, coverArt, pixels, connection) {
            value = null
            if (coverArt != null && connection != null) {
                try { value = app.repository.artworkRequest(context, coverArt, pixels) }
                catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (_: Exception) { value = null }
            }
        }
        if (request != null) {
            AsyncImage(model = request, contentDescription = null,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(Icons.Filled.MusicNote, contentDescription = null, tint = TextTertiary,
                modifier = Modifier.size(size / 2))
        }
    }
}

/** 「正在播放」的綠色音柱指示（依設計圖 01）。 */
@Composable
fun PlayingIndicator(modifier: Modifier = Modifier) {
    Icon(
        Icons.Filled.GraphicEq,
        contentDescription = stringResource(R.string.playing_indicator_desc),
        tint = Accent,
        modifier = modifier.size(22.dp),
    )
}

/** 可取得焦點、以確認鍵觸發的容器：描邊＋底色＋小幅放大。 */
@Composable
fun FocusBox(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(10.dp),
    unfocusedBg: Color = Color.Transparent,
    focusedBg: Color = BgRowFocused,
    scaleOnFocus: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (focused) focusedBg else unfocusedBg, shape)
            .then(if (focused) Modifier.border(2.dp, Accent, shape) else Modifier)
            .then(if (scaleOnFocus && focused) Modifier.scale(1.02f) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.CenterStart,
        content = content,
    )
}

/** 實心／描邊圓角按鈕（播放全部、隨機播放等）。 */
@Composable
fun PillButton(
    text: String,
    icon: ImageVector?,
    filled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    var focused by remember { mutableStateOf(false) }
    val bg = if (filled) Accent else if (focused) Color(0xFF22352C) else Color(0x14161C22).copy(alpha = 0.6f)
    val fg = if (filled) AccentOnDark else if (focused) Accent else TextPrimary
    Box(
        modifier = modifier
            .clip(shape)
            .background(bg, shape)
            .border(
                when {
                    filled -> 2.dp
                    focused -> 2.dp
                    else -> 1.dp
                },
                if (focused) TextPrimary else if (filled) Accent else Color(0xFF3A4750),
                shape,
            )
            .then(if (focused) Modifier.scale(1.04f) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            icon?.let { Icon(it, contentDescription = null, tint = fg, modifier = Modifier.size(22.dp)) }
            Text(text, color = fg, fontSize = 17.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** 文字列（歌名等主資訊）。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PrimaryText(text: String, modifier: Modifier = Modifier, fontSize: Int = 18, marquee: Boolean = false) {
    Text(
        text,
        modifier = modifier.then(if (marquee) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier),
        color = TextPrimary,
        fontSize = fontSize.sp,
        lineHeight = (fontSize + 4).sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun SecondaryText(text: String, modifier: Modifier = Modifier, fontSize: Int = 16) {
    Text(
        text,
        modifier = modifier,
        color = com.navidrome.tv.ui.theme.TextSecondary,
        fontSize = fontSize.sp,
        lineHeight = (fontSize + 4).sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
