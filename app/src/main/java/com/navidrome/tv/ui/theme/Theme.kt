package com.navidrome.tv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun NavidromeTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            primary = Accent,
            onPrimary = AccentOnDark,
            background = Bg,
            onBackground = TextPrimary,
            surface = Bg,
            onSurface = TextPrimary,
            surfaceVariant = BgPanel,
            onSurfaceVariant = TextSecondary,
        ),
        typography = TvTypography(),
        content = content,
    )
}
