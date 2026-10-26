package com.qingguang.browser.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 书签 / 首页快捷入口 / 历史 共用的图标配色 */
internal val siteIconPalette = listOf(
    Color(0xFF2E7CF6), Color(0xFF4A6FA5), Color(0xFF3F7E8C), Color(0xFF5B8C5A),
    Color(0xFFC7A34A), Color(0xFFB4543F), Color(0xFF8E5A6E), Color(0xFF6C4BD8),
    Color(0xFFD16A9A), Color(0xFF7A6C57), Color(0xFF3A8E5C), Color(0xFF1F2D3D)
)

internal fun siteIconColor(seed: String): Color {
    if (seed.isBlank()) return Color(0xFF9AA1AC)
    return siteIconPalette[Math.abs(seed.hashCode()) % siteIconPalette.size]
}

internal fun siteIconText(title: String, url: String): String {
    val fromTitle = title.trim().take(1)
    if (fromTitle.isNotBlank()) return fromTitle.uppercase()
    val host = runCatching { java.net.URI(url.trim()).host }.getOrNull()
        ?: url.trim()
    val fromHost = host.removePrefix("www.").trim().take(1)
    return fromHost.uppercase().ifBlank { "网" }
}

/** 整个图标做成文件夹造型（蓝色渐变），用于书签文件夹与首页分组 */
@Composable
internal fun FolderGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        // 文件夹后页（标签）
        drawRoundRect(
            color = Color(0xFF7DB7FF),
            topLeft = Offset(w * 0.08f, h * 0.15f),
            size = Size(w * 0.46f, h * 0.32f),
            cornerRadius = CornerRadius(w * 0.09f)
        )
        // 文件夹主体
        drawRoundRect(
            brush = Brush.verticalGradient(
                listOf(Color(0xFF4D93FF), Color(0xFF1E6FE0))
            ),
            topLeft = Offset(w * 0.04f, h * 0.28f),
            size = Size(w * 0.92f, h * 0.58f),
            cornerRadius = CornerRadius(w * 0.13f)
        )
        // 顶部高光
        drawRoundRect(
            color = Color.White.copy(alpha = 0.18f),
            topLeft = Offset(w * 0.04f, h * 0.28f),
            size = Size(w * 0.92f, h * 0.13f),
            cornerRadius = CornerRadius(w * 0.13f)
        )
    }
}

/**
 * 统一站点图标：默认取标题首字 + 自动配色；支持自定义颜色与文字。
 * folder = true 时显示整块文件夹造型。
 */
@Composable
internal fun SiteIconBadge(
    title: String,
    url: String,
    iconColor: Int?,
    iconText: String?,
    folder: Boolean = false,
    size: Dp = 34.dp,
    corner: Dp = 10.dp,
    fontSize: TextUnit = 14.sp,
    modifier: Modifier = Modifier
) {
    if (folder) {
        FolderGlyph(modifier = modifier.size(size))
        return
    }
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(iconColor?.let { Color(it) } ?: siteIconColor(title.ifBlank { url })),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = iconText?.trim()?.take(2)?.takeIf { it.isNotBlank() }
                ?: siteIconText(title, url),
            fontSize = fontSize,
            color = Color.White
        )
    }
}

/** 自定义图标：1-2 个文字 + 背景色，保存后按 URL 同步到书签、首页与历史 */
@Composable
internal fun IconStyleDialog(
    title: String,
    initialColor: Int?,
    initialText: String?,
    onDismiss: () -> Unit,
    onSave: (color: Int?, text: String?) -> Unit
) {
    var selectedColor by remember { mutableStateOf(initialColor) }
    var text by remember { mutableStateOf(initialText.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自定义图标") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SiteIconBadge(
                        title = title,
                        url = "",
                        iconColor = selectedColor,
                        iconText = text.trim().ifBlank { null },
                        size = 46.dp,
                        corner = 14.dp,
                        fontSize = 19.sp
                    )
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(
                            title,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "图标会同步到书签、首页与历史记录",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(2) },
                    label = { Text("图标文字（1-2 字，留空自动）") },
                    singleLine = true
                )
                Spacer(Modifier.height(10.dp))
                Text("图标背景色", fontSize = 12.sp)
                siteIconPalette.chunked(6).forEach { row ->
                    Row(
                        Modifier.padding(top = 7.dp),
                        horizontalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        row.forEach { color ->
                            val argb = color.toArgb()
                            Box(
                                Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .then(
                                        if (selectedColor == argb) {
                                            Modifier.border(
                                                2.dp,
                                                MaterialTheme.colorScheme.onSurface,
                                                CircleShape
                                            )
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .clickable { selectedColor = argb }
                            )
                        }
                    }
                }
                TextButton(
                    onClick = {
                        selectedColor = null
                        text = ""
                    },
                    modifier = Modifier.padding(top = 4.dp)
                ) { Text("恢复自动配色") }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(selectedColor, text.trim().ifBlank { null }) }
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
