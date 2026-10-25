package com.qingguang.browser.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF0E7C86),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB8EDE8),
    onPrimaryContainer = Color(0xFF00201D),
    secondary = Color(0xFFB07B00),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE6A8),
    onSecondaryContainer = Color(0xFF3A2A00),
    background = Color(0xFFF7FAF9),
    onBackground = Color(0xFF14211F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF14211F),
    surfaceVariant = Color(0xFFE3ECE9),
    onSurfaceVariant = Color(0xFF44524F)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF35D0BA),
    onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF005047),
    onPrimaryContainer = Color(0xFFB8EDE8),
    secondary = Color(0xFFFFD166),
    onSecondary = Color(0xFF3A2A00),
    secondaryContainer = Color(0xFF5C4300),
    onSecondaryContainer = Color(0xFFFFE6A8),
    background = Color(0xFF0B1518),
    onBackground = Color(0xFFDDE7E4),
    surface = Color(0xFF122125),
    onSurface = Color(0xFFDDE7E4),
    surfaceVariant = Color(0xFF1D3036),
    onSurfaceVariant = Color(0xFFA9BDB8)
)

@Composable
fun QingGuangTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
