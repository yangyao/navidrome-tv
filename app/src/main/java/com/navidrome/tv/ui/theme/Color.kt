package com.navidrome.tv.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
// 色彩：深灰底、白字、薄荷綠焦點（依 design/v1）
val Bg = Color(0xFF0E1216)
val BgPanel = Color(0xFF161C22)
val BgRow = Color(0xFF1A222A)
val BgRowFocused = Color(0xFF22352C)
val SidebarSelected = Color(0xFF24382E)
val Divider = Color(0xFF2A333C)
val TextPrimary = Color(0xFFF2F6F4)
val TextSecondary = Color(0xFF98A4AC)
val TextTertiary = Color(0xFF5E6B74)
val Accent = Color(0xFFA8E8C8)
val AccentDim = Color(0xFF7FCFA9)
val AccentOnDark = Color(0xFF122019)
val Heart = Color(0xFF7FE0B4)

val FocusShape = RoundedCornerShape(10.dp)
val CardShape = RoundedCornerShape(12.dp)
val PillShape = RoundedCornerShape(24.dp)

@Composable
fun TvTypography(): Typography = Typography(
    displayMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 48.sp, color = TextPrimary),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 38.sp, color = TextPrimary),
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 32.sp, color = TextPrimary),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 30.sp, color = TextPrimary),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 26.sp, color = TextPrimary),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 24.sp, color = TextPrimary),
    bodyLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 19.sp, lineHeight = 26.sp, color = TextPrimary),
    bodyMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 17.sp, lineHeight = 24.sp, color = TextSecondary),
    bodySmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp, color = TextTertiary),
)
