package com.navidrome.tv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navidrome.tv.ui.theme.TextPrimary
import com.navidrome.tv.ui.theme.TextSecondary

/** 畫面標頭：大標題＋同行小字數量資訊＋右側動作（隨機播放等）。 */
@Composable
fun ScreenHeader(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(bottom = 18.dp),
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text(title, color = TextPrimary, fontSize = 38.sp, fontWeight = FontWeight.Bold)
            subtitle?.let {
                Text(it, color = TextSecondary, fontSize = 17.sp, modifier = Modifier.padding(bottom = 6.dp))
            }
        }
        trailing?.invoke()
    }
}

/** 所有次级页面共用同一个左上返回入口。 */
@Composable
fun PageHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FocusBox(onBack, modifier = Modifier.size(40.dp), scaleOnFocus = false) {
            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack,
                "返回", tint = TextPrimary, modifier = Modifier.align(Alignment.Center).size(24.dp))
        }
        Text(title, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Medium)
    }
}
