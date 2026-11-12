package com.navidrome.tv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navidrome.tv.ui.theme.Accent
import com.navidrome.tv.ui.theme.TextPrimary
import com.navidrome.tv.ui.theme.TextSecondary

/** 空狀態（依設計圖 16：空收藏）。必須有可取得焦點的出口。 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    actionText: String?,
    onAction: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.padding(20.dp),
    ) {
        Icon(icon, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text(title, color = TextPrimary, fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(body, color = TextSecondary, fontSize = 16.sp, lineHeight = 22.sp)
        if (actionText != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            val first = remember { FocusRequester() }
            ContentFocus(first)
            PillButton(actionText, null, filled = false, modifier = Modifier.focusRequester(first), onClick = onAction)
        }
    }
}

/** 載入中。 */
@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier,
    ) {
        CircularProgressIndicator(color = Accent, strokeWidth = 5.dp, modifier = Modifier.size(72.dp))
    }
}

/** 錯誤狀態（依設計圖 15：連線失敗）。提供重新連線與重新設定兩個出口。 */
@Composable
fun ErrorState(
    title: String,
    body: String,
    retryText: String,
    onRetry: () -> Unit,
    reSetupText: String,
    onReSetup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.padding(20.dp),
    ) {
        Icon(Icons.Filled.CloudOff, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text(title, color = TextPrimary, fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(body, color = TextSecondary, fontSize = 16.sp, lineHeight = 22.sp)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            PillButton(retryText, null, filled = false, onClick = onRetry)
            PillButton(reSetupText, null, filled = false, onClick = onReSetup)
        }
    }
}
