package com.navidrome.tv.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navidrome.tv.pairing.PairingSession
import com.navidrome.tv.pairing.QrGenerator
import com.navidrome.tv.ui.AppViewModel
import com.navidrome.tv.ui.collectAsStateValue
import com.navidrome.tv.ui.components.PillButton
import com.navidrome.tv.ui.theme.Accent
import com.navidrome.tv.ui.theme.BgPanel
import com.navidrome.tv.ui.theme.TextPrimary
import com.navidrome.tv.ui.theme.TextSecondary

/** 掃碼登入（依設計圖 12）與連線中（依設計圖 17）。全程不在電視輸入文字。 */
@Composable
fun LoginScreen(vm: AppViewModel) {
    val phase = vm.ui.collectAsStateValue().loginPhase
    when (phase) {
        AppViewModel.LoginPhase.CONNECTING -> ConnectingContent(vm)
        else -> QrContent(vm)
    }
}

@Composable
private fun QrContent(vm: AppViewModel) {
    val ui = vm.ui.collectAsStateValue()
    var showHelp by remember { mutableStateOf(false) }
    val first = remember { androidx.compose.ui.focus.FocusRequester() }
    com.navidrome.tv.ui.components.ContentFocus(first)

    Row(modifier = Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 24.dp)) {
        Column(Modifier.weight(1.1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.MusicNote, contentDescription = null, tint = Accent, modifier = Modifier.size(40.dp))
                Column {
                    Text("Navidrome TV", color = TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Text("音乐，从这里开始。", color = TextSecondary, fontSize = 18.sp)
                }
            }
            Spacer(Modifier.height(20.dp))
            Text("用手机扫码设定", color = TextPrimary, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))

            StepRow(1, Icons.Filled.Wifi, "手机连上家中 Wi-Fi")
            Spacer(Modifier.height(8.dp))
            StepRow(2, Icons.Filled.SettingsInputAntenna, "扫描右侧二维码")
            Spacer(Modifier.height(8.dp))
            StepRow(3, Icons.Filled.MusicNote, "在手机填写音乐服务器信息")

            Spacer(Modifier.height(16.dp))
            Text("电视不需要输入任何文字。", color = TextPrimary, fontSize = 20.sp)
            Spacer(Modifier.height(10.dp))
            Text("电视有线连接也可以。", color = TextSecondary, fontSize = 18.sp)

            ui.loginError?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = Color(0xFFF2A4A4), fontSize = 16.sp)
            }

            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PillButton("重新生成", Icons.Filled.Refresh, filled = false, modifier = Modifier.focusRequester(first)) { vm.showQr() }
                PillButton("连接说明", Icons.Filled.Info, filled = false) { showHelp = true }
            }
            if (ui.isReconfiguring) {
                Spacer(Modifier.height(8.dp))
                PillButton("取消，返回设定", null, filled = false) { vm.cancelReconfigure() }
            }
        }

        Column(Modifier.weight(0.9f), horizontalAlignment = Alignment.CenterHorizontally) {
            val qr = ui.qrContent
            if (qr != null) {
                val bitmap = remember(qr) { QrGenerator.bitmap(qr, 560) }
                Box(
                    Modifier
                        .background(Color.White, RoundedCornerShape(20.dp))
                        .padding(16.dp),
                ) {
                    Image(bitmap.asImageBitmap(), contentDescription = "设定二维码", modifier = Modifier.size(260.dp))
                }
                Spacer(Modifier.height(16.dp))
                val mm = ui.qrCountdownSec / 60
                val ss = ui.qrCountdownSec % 60
                Text("配对码將于 %d:%02d 后更新".format(mm, ss), color = TextSecondary, fontSize = 18.sp)
                Spacer(Modifier.height(8.dp))
                Text(ui.qrUrlText ?: "", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            } else {
                CircularProgressIndicator(color = Accent)
            }
        }
    }

    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showHelp = false }) { Text("知道了", color = Accent) }
            },
            title = { Text("连接说明", color = TextPrimary) },
            text = {
                Text(
                    "1. 手机与电视需连上同一个区域网络（电视可走有线）。\n" +
                        "2. 用手机相机或浏览器扫描二维码，开启设定頁。\n" +
                        "3. 若扫码不通，可在手机浏览器直接输入画面上的地址。\n" +
                        "4. 手机用行動网络或訪客 Wi-Fi 时，可能無法连到电视，请改用家中 Wi-Fi。",
                    color = TextSecondary,
                    fontSize = 17.sp,
                )
            },
            containerColor = BgPanel,
        )
    }
}

@Composable
private fun StepRow(number: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .background(BgPanel, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(28.dp).background(Accent, CircleShape),
        ) {
            Text(number.toString(), color = Color(0xFF122019), fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
        Icon(icon, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** 連線中（依設計圖 17）：三步驟指示＋取消連接。 */
@Composable
private fun ConnectingContent(vm: AppViewModel) {
    val step = vm.ui.collectAsStateValue().connectingStep
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().padding(60.dp),
    ) {
        CircularProgressIndicator(color = Accent, strokeWidth = 8.dp, modifier = Modifier.size(120.dp))
        Spacer(Modifier.height(32.dp))
        Text("正在连接音乐服务器", color = TextPrimary, fontSize = 40.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text("已收到手机设定，请稍候。", color = TextSecondary, fontSize = 20.sp)
        Spacer(Modifier.height(44.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            StepDot(0, "手机已连接", step)
            StepLine(true)
            StepDot(1, "正在验证", step)
            StepLine(step >= 3)
            StepDot(2, "开启歌曲清单", step)
        }
        Spacer(Modifier.height(48.dp))
        PillButton("取消连接", null, filled = false) { vm.cancelConnecting() }
    }
}

@Composable
private fun StepLine(active: Boolean) {
    Box(
        Modifier
            .width(140.dp)
            .height(4.dp)
            .background(if (active) Accent else Color(0xFF2A333C), RoundedCornerShape(2.dp)),
    )
}

@Composable
private fun StepDot(index: Int, label: String, currentStep: Int) {
    val done = currentStep > index + 1 || (index == 0 && currentStep >= 1)
    val active = currentStep == index + 1
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(44.dp),
        ) {
            when {
                done -> Box(Modifier.size(44.dp).background(Accent, CircleShape), contentAlignment = Alignment.Center) {
                    Text("✔", color = Color(0xFF122019), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                active -> CircularProgressIndicator(color = Accent, strokeWidth = 4.dp, modifier = Modifier.size(44.dp))
                else -> Box(
                    Modifier.size(36.dp).background(Color.Transparent, CircleShape)
                        .border(3.dp, Color(0xFF5E6B74), CircleShape),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            label,
            color = if (done || active) TextPrimary else TextSecondary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
