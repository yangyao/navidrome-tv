package com.navidrome.tv.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.imageLoader
import com.navidrome.tv.TvApp
import com.navidrome.tv.data.local.AudioQuality
import com.navidrome.tv.ui.AppViewModel
import com.navidrome.tv.ui.components.*
import com.navidrome.tv.ui.theme.*

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun SettingsScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val app = context.applicationContext as TvApp
    val connection = app.settings.connection()
    val version = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
    var quality by remember { mutableStateOf(app.settings.audioQuality) }
    var cacheCleared by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    val original = remember { FocusRequester() }
    val compatible = remember { FocusRequester() }
    val list = remember { vm.cached("settings:scroll") { androidx.compose.foundation.lazy.LazyListState() } }
    ContentFocus(first)
    LazyColumn(state = list, modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("设定", color = TextPrimary, fontSize = 30.sp) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("音乐服务器 · 已连接", color = Accent, fontSize = 18.sp)
                Text("用户：${connection?.username ?: ""}", color = TextSecondary, fontSize = 16.sp)
            }
        }
        item {
            PillButton("用手机重新设定", Icons.Filled.QrCode2, false, Modifier.focusRequester(first).focusProperties { down = original }) { vm.beginReconfigure() }
        }
        item { Text("播放音质", color = Accent, fontSize = 18.sp) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PillButton("原音", null, quality == AudioQuality.ORIGINAL, Modifier.focusRequester(original).focusProperties { right = compatible }) {
                    quality = AudioQuality.ORIGINAL; app.settings.audioQuality = quality
                }
                PillButton("兼容模式", null, quality == AudioQuality.COMPATIBLE, Modifier.focusRequester(compatible).focusProperties { left = original; right = FocusRequester.Cancel }) {
                    quality = AudioQuality.COMPATIBLE; app.settings.audioQuality = quality
                }
            }
        }
        item { Text("储存", color = Accent, fontSize = 18.sp) }
        item {
            PillButton(if (cacheCleared) "封面缓存已清除" else "清除封面缓存", Icons.Filled.Image, false) {
                context.imageLoader.diskCache?.clear(); context.imageLoader.memoryCache?.clear(); cacheCleared = true
            }
        }
        item {
            Text("关于 · Navidrome TV $version", color = TextPrimary, fontSize = 17.sp)
        }
        item { PillButton("登出", Icons.Filled.Logout, false) { confirmLogout = true } }
        item { Spacer(Modifier.height(8.dp)) }
    }
    if (confirmLogout) {
        val cancel = remember { FocusRequester() }
        LaunchedEffect(Unit) { withFrameNanos { }; cancel.requestFocus() }
        AlertDialog(onDismissRequest = { confirmLogout = false },
            title = { Text("确定登出？", color = TextPrimary) },
            text = { Text("将停止播放并清除本机登录信息。修改连接可使用「用手机重新设定」。", color = TextSecondary) },
            confirmButton = { PillButton("确定登出", null, false) { confirmLogout = false; vm.logout() } },
            dismissButton = { PillButton("取消", null, false, Modifier.focusRequester(cancel)) { confirmLogout = false } },
            containerColor = BgPanel)
    }
}
