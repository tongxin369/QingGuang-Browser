package com.qingguang.browser.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qingguang.browser.App
import com.qingguang.browser.data.LocalPage
import com.qingguang.browser.util.LocalSiteManager
import com.qingguang.browser.web.BrowserController
import kotlinx.coroutines.launch

@Composable
fun HomePage(
    controller: BrowserController,
    onOpenOverlay: (OverlayPage) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as App
    val scope = rememberCoroutineScope()
    val localPages by app.database.localPageDao()
        .observeAll()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    var query by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<LocalPage?>(null) }

    val openFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val name = queryDisplayName(context, uri) ?: "本地网页"
                    val page = app.localSites.importSingleHtml(uri, name)
                    app.database.localPageDao().upsert(page)
                    controller.loadUrl(page.entryUrl)
                }.onFailure { toast(context, it.message ?: "导入失败") }
            }
        }
    }

    val openTreeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val name = DocumentFile.fromTreeUri(context, uri)?.name ?: "本地网页目录"
                    val page = app.localSites.importDirectory(uri, name)
                    app.database.localPageDao().upsert(page)
                    controller.loadUrl(page.entryUrl)
                }.onFailure { toast(context, it.message ?: "导入失败") }
            }
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 28.dp)
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "轻光浏览器",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            "网页数据留在设备上，随时可查、可备份",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { onOpenOverlay(OverlayPage.SETTINGS) }) {
                        Icon(Icons.Filled.Settings, contentDescription = "设置")
                    }
                }

                Spacer(Modifier.height(24.dp))

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            if (query.isNotBlank()) {
                                controller.submitInput(query)
                                query = ""
                            }
                        }),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 18.dp),
                        decorationBox = { inner ->
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                                if (query.isBlank()) {
                                    Text(
                                        "搜索或输入网址",
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                                inner()
                            }
                        }
                    )
                }

                Spacer(Modifier.height(20.dp))

                Row(Modifier.fillMaxWidth()) {
                    QuickEntry(
                        modifier = Modifier.weight(1f),
                        label = "书签",
                        icon = Icons.Filled.BookmarkBorder,
                        onClick = { onOpenOverlay(OverlayPage.BOOKMARKS) }
                    )
                    QuickEntry(
                        modifier = Modifier.weight(1f),
                        label = "历史",
                        icon = Icons.Filled.History,
                        onClick = { onOpenOverlay(OverlayPage.HISTORY) }
                    )
                    QuickEntry(
                        modifier = Modifier.weight(1f),
                        label = "下载",
                        icon = Icons.Filled.Download,
                        onClick = { onOpenOverlay(OverlayPage.DOWNLOADS) }
                    )
                    QuickEntry(
                        modifier = Modifier.weight(1f),
                        label = "备份",
                        icon = Icons.Filled.Backup,
                        onClick = { onOpenOverlay(OverlayPage.BACKUPS) }
                    )
                }

                Spacer(Modifier.height(24.dp))

                Text(
                    "本地网页",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(8.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { openFileLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier.weight(1f)
                    ) { Text("打开 HTML 文件") }
                    OutlinedButton(
                        onClick = { openTreeLauncher.launch(null) },
                        modifier = Modifier.weight(1f)
                    ) { Text("打开文件夹") }
                }

                Text(
                    "提示：单个 HTML 文件只复制文件本身；如果页面还用到 CSS/JS/图片，请选择整个文件夹导入，相对路径才能正常加载。",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )

                TextButton(
                    onClick = {
                        controller.loadUrl(
                            LocalSiteManager.ASSET_HOST + "/assets/sample/index.html"
                        )
                    }
                ) { Text("打开内置示例网页（可测试数据保存与定位）") }

                Spacer(Modifier.height(8.dp))
            }

            if (localPages.isEmpty()) {
                item {
                    Text(
                        "还没有导入本地网页。导入后地址会被记住，下次直接从下面打开。",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            } else {
                items(localPages, key = { it.siteId }) { page ->
                    ListItem(
                        headlineContent = {
                            Text(
                                page.displayName,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        supportingContent = {
                            Text(
                                page.entryUrl,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 11.sp
                            )
                        },
                        leadingContent = {
                            Box(
                                Modifier.size(36.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.BookmarkBorder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        trailingContent = {
                            IconButton(onClick = { pendingDelete = page }) {
                                Icon(Icons.Filled.Close, contentDescription = "删除记录")
                            }
                        },
                        modifier = Modifier.clickable {
                            scope.launch {
                                app.database.localPageDao().touch(page.siteId, System.currentTimeMillis())
                                controller.loadUrl(page.entryUrl)
                            }
                        }
                    )
                }
            }
        }
    }

    pendingDelete?.let { page ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除本地网页？") },
            text = {
                Text("将删除应用内保存的「${page.displayName}」文件与打开记录。该网页产生的浏览数据不会自动删除，建议先在备份页面备份。")
            },
            confirmButton = {
                TextButton(onClick = {
                    app.localSites.deleteSite(page.siteId)
                    scope.launch { app.database.localPageDao().delete(page.siteId) }
                    pendingDelete = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun QuickEntry(
    modifier: Modifier,
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 12.sp)
    }
}

private fun queryDisplayName(context: Context, uri: Uri): String? {
    var name: String? = null
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) name = cursor.getString(index)
        }
    }
    return name
}

private fun toast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}
