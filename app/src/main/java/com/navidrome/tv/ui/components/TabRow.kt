package com.navidrome.tv.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navidrome.tv.ui.theme.*

@Composable
fun TabRow(tabs: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        tabs.forEachIndexed { index, label ->
            FocusBox({ onSelect(index) }, unfocusedBg = if (selected == index) BgRowFocused else androidx.compose.ui.graphics.Color.Transparent,
                scaleOnFocus = false) {
                Text(label, color = if (selected == index) Accent else TextSecondary, fontSize = 18.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
        }
    }
}
