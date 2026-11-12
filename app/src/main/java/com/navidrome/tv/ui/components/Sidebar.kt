package com.navidrome.tv.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import com.navidrome.tv.ui.theme.*

enum class NavItem(val label: String, val icon: ImageVector) {
    SONGS("所有歌曲", Icons.Filled.MusicNote),
    ARTISTS("歌手", Icons.Filled.Person),
    ALBUMS("专辑", Icons.Filled.Album),
    PLAYLISTS("歌单", Icons.Filled.QueueMusic),
    FAVORITES("收藏", Icons.Filled.Favorite),
    NOW_PLAYING("正在播放", Icons.Filled.PlayArrow),
    QUEUE("播放队列", Icons.Filled.QueueMusic),
    SETTINGS("设定", Icons.Filled.Settings),
}

@Composable
fun AppSidebar(selected: NavItem, onSelect: (NavItem) -> Unit, modifier: Modifier = Modifier) {
    val requester = remember { FocusRequester() }
    DisposableEffect(selected) {
        FocusBus.sidebar = requester
        onDispose { if (FocusBus.sidebar === requester) FocusBus.sidebar = null }
    }
    Column(modifier.width(196.dp).fillMaxHeight().background(Color(0xFF12171D))
        .padding(horizontal = 12.dp, vertical = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.MusicNote, null, tint = Accent, modifier = Modifier.size(24.dp))
            Text("Navidrome TV", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))
        NavItem.entries.forEach { item ->
            FocusBox(onClick = { onSelect(item) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                    .then(if (selected == item) Modifier.focusRequester(requester) else Modifier)
                    .onPreviewKeyEvent { e ->
                        if (e.key == Key.DirectionRight && e.type == KeyEventType.KeyDown) FocusBus.requestContent()
                        else false
                    },
                unfocusedBg = if (selected == item) SidebarSelected else Color.Transparent,
                scaleOnFocus = false) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(item.icon, null, tint = if (selected == item) Accent else TextSecondary, modifier = Modifier.size(22.dp))
                    Text(item.label, color = TextPrimary, fontSize = 17.sp, maxLines = 1)
                }
            }
        }
    }
}
