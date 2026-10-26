package com.qingguang.browser.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingguang.browser.web.PendingDownload

/** 参考 Via 的下载确认弹窗：确认后才下载，文件名可直接修改 */
@Composable
fun DownloadConfirmDialog(
    request: PendingDownload,
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    var fileName by remember(request.url) { mutableStateOf(request.suggestedName) }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("你想要下载此文件吗？") },
        text = {
            Column {
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    singleLine = true,
                    label = { Text("文件名") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "文件大小：" + formatDownloadSize(request),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (request.mimeType.isNotBlank()) {
                    Text(
                        "类型：${request.mimeType}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = fileName.isNotBlank(),
                onClick = { onConfirm(fileName) }
            ) { Text("确定") }
        },
        dismissButton = {
            Row {
                if (!request.isBlobOrData) {
                    TextButton(onClick = {
                        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        manager.setPrimaryClip(ClipData.newPlainText("下载链接", request.url))
                        Toast.makeText(context, "链接已复制", Toast.LENGTH_SHORT).show()
                    }) { Text("复制链接") }
                }
                TextButton(onClick = onCancel) { Text("取消") }
            }
        }
    )
}

private fun formatDownloadSize(request: PendingDownload): String {
    if (!request.sizeChecked) return "获取中…"
    val bytes = request.sizeBytes ?: return "未知"
    return when {
        bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / 1024.0 / 1024.0)
        bytes >= 1024 -> String.format("%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
