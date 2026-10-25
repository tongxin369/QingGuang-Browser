package com.qingguang.browser.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.qingguang.browser.App

// 参考 Via：大面积留白 + 细边框 + 少量彩色点缀，去掉厚重色块
private val LightColors = lightColorScheme(
    primary = Color(0xFF1F2328),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF0F1F4),
    onPrimaryContainer = Color(0xFF1F2328),
    secondary = Color(0xFF2E7CF6),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8F0FE),
    onSecondaryContainer = Color(0xFF0B3E8F),
    background = Color(0xFFF5F6F8),
    onBackground = Color(0xFF181A1F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF181A1F),
    surfaceVariant = Color(0xFFF2F3F5),
    onSurfaceVariant = Color(0xFF6B7280),
    outline = Color(0xFFE4E6EB),
    outlineVariant = Color(0xFFEDEFF2)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF2F3F5),
    onPrimary = Color(0xFF14161A),
    primaryContainer = Color(0xFF262A31),
    onPrimaryContainer = Color(0xFFF2F3F5),
    secondary = Color(0xFF6FA8FF),
    onSecondary = Color(0xFF0B1020),
    secondaryContainer = Color(0xFF1E2F4D),
    onSecondaryContainer = Color(0xFFCFE0FF),
    background = Color(0xFF0E1014),
    onBackground = Color(0xFFE9EAEE),
    surface = Color(0xFF14171C),
    onSurface = Color(0xFFE9EAEE),
    surfaceVariant = Color(0xFF1C2027),
    onSurfaceVariant = Color(0xFF9AA1AC),
    outline = Color(0xFF262A32),
    outlineVariant = Color(0xFF1E2229)
)

/**
 * 界面外观：0 跟随系统，1 浅色，2 深色。
 * mode 是 Compose 状态，切换后整棵树会重组。
 */
object ThemeController {
    var mode by mutableStateOf(0)
        private set

    fun init(context: Context) {
        mode = (context.applicationContext as App).settings.appTheme
    }

    fun set(context: Context, value: Int) {
        mode = value
        (context.applicationContext as App).settings.appTheme = value
    }
}

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
