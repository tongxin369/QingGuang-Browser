package com.qingguang.browser.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.core.content.ContextCompat
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qingguang.browser.App
import com.qingguang.browser.BuildConfig
import com.qingguang.browser.util.BackupInfo
import com.qingguang.browser.util.BackupManager
import com.qingguang.browser.web.BrowserController
import kotlinx.coroutines.launch

@Composable
fun OverlayPageHost(
    page: OverlayPage,
    controller: BrowserController,
    onClose: () -> Unit
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Text(
                    page.title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
            when (page) {
                OverlayPage.BOOKMARKS -> BookmarksPage(controller, onClose)
                OverlayPage.HISTORY -> HistoryPage(controller, onClose)
                OverlayPage.DOWNLOADS -> DownloadsPage()
                OverlayPage.BACKUPS -> BackupsPage(controller)
                OverlayPage.SETTINGS -> SettingsPage(controller)
            }
        }
    }
}

@Composable
private fun BookmarksPage(controller: BrowserController, onClose: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as App
    val scope = rememberCoroutineScope()
    val bookmarks by app.database.bookmarkDao()
        .observeAll()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    if (bookmarks.isEmpty()) {
        EmptyHint("还没有书签。打开网页后，从菜单里点「收藏当前页」。")
        return
    }

    LazyColumn(Modifier.fillMaxSize()) {
        items(bookmarks, key = { it.id }) { bookmark ->
            ListItem(
                headlineContent = {
                    Text(bookmark.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                supportingContent = {
                    Text(bookmark.url, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp)
                },
                trailingContent = {
                    IconButton(onClick = { scope.launch { app.database.bookmarkDao().delete(bookmark.id) } }) {
                        Icon(Icons.Filled.Delete, contentDescription = "删除书签")
                    }
                },
                modifier = Modifier.clickable {
                    controller.loadUrl(bookmark.url)
                    onClose()
                }
            )
        }
    }
}

@Composable
private fun HistoryPage(controller: BrowserController, onClose: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as App
    val scope = rememberCoroutineScope()
    val history by app.database.historyDao()
        .observeRecent()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var confirmClear by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = { confirmClear = true }, enabled = history.isNotEmpty()) {
                Text("清空历史")
            }
        }
        if (history.isEmpty()) {
            EmptyHint("还没有浏览历史。")
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(history, key = { it.id }) { item ->
                    ListItem(
                        headlineContent = {
                            Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        supportingContent = {
                            Text(
                                item.url,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 11.sp
                            )
                        },
                        trailingContent = {
                            IconButton(onClick = {
                                scope.launch { app.database.historyDao().deleteByUrl(item.url) }
                            }) {
                                Icon(Icons.Filled.Delete, contentDescription = "删除")
                            }
                        },
                        modifier = Modifier.clickable {
                            controller.loadUrl(item.url)
                            onClose()
                        }
                    )
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空浏览历史？") },
            text = { Text("将删除全部浏览历史记录，不会影响网页数据与备份。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { app.database.historyDao().clear() }
                    confirmClear = false
                }) { Text("清空") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun DownloadsPage() {
    val context = LocalContext.current
    val app = context.applicationContext as App
    val scope = rememberCoroutineScope()
    val downloads by app.database.downloadDao()
        .observeAll()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    Column(Modifier.fillMaxSize()) {
        Text(
            "下载文件保存在系统「下载」目录，由系统下载管理器完成。此处仅记录下载历史。",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        if (downloads.isEmpty()) {
            EmptyHint("还没有下载记录。")
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(downloads, key = { it.id }) { record ->
                    ListItem(
                        headlineContent = {
                            Text(record.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        supportingContent = {
                            Text(
                                "${record.status} · ${BackupManager.formatTime(record.createdAt)}",
                                fontSize = 11.sp
                            )
                        },
                        trailingContent = {
                            IconButton(onClick = {
                                scope.launch { app.database.downloadDao().delete(record.id) }
                            }) {
                                Icon(Icons.Filled.Delete, contentDescription = "删除记录")
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun BackupsPage(controller: BrowserController) {
    val context = LocalContext.current
    val app = context.applicationContext as App
    val scope = rememberCoroutineScope()

    var backups by remember { mutableStateOf<List<BackupInfo>>(emptyList()) }
    var pendingDelete by remember { mutableStateOf<BackupInfo?>(null) }
    var pendingRestore by remember { mutableStateOf<BackupInfo?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var autoBackup by remember { mutableStateOf(app.settings.autoBackup) }

    fun refreshList() {
        scope.launch { backups = app.backups.listBackups() }
    }

    LaunchedEffect(Unit) { backups = app.backups.listBackups() }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text("备份目录", fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(
                app.backups.backupDir.absolutePath,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    controller.backupCurrent { success, msg ->
                        message = msg
                        if (success) refreshList()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("备份当前页面") }
            message?.let {
                Text(
                    it,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text("自动备份", fontSize = 14.sp)
                    Text(
                        "页面加载完成与切到后台时自动备份",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = autoBackup,
                    onCheckedChange = {
                        autoBackup = it
                        app.settings.autoBackup = it
                    }
                )
            }
            Text(
                "备份范围：Cookie + localStorage。同一站点只保留最新一份，旧备份自动删除。恢复会覆盖当前站点的对应数据并刷新页面。",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        HorizontalDivider()
        if (backups.isEmpty()) {
            EmptyHint("还没有备份。点击上方按钮备份当前页面。")
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(backups, key = { it.file.absolutePath }) { info ->
                    ListItem(
                        headlineContent = { Text(info.host, maxLines = 1) },
                        supportingContent = {
                            Text(
                                "${BackupManager.formatTime(info.createdAt)} · ${BackupManager.formatSize(info.sizeBytes)}",
                                fontSize = 11.sp
                            )
                        },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { pendingRestore = info }) { Text("恢复") }
                                IconButton(onClick = { pendingDelete = info }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "删除备份")
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    pendingDelete?.let { info ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除备份？") },
            text = {
                Text("将删除「${info.host}」的备份文件（${BackupManager.formatSize(info.sizeBytes)}）。此操作不可撤销。")
            },
            confirmButton = {
                TextButton(onClick = {
                    app.backups.delete(info)
                    pendingDelete = null
                    message = "已删除备份"
                    refreshList()
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }

    pendingRestore?.let { info ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("恢复备份？") },
            text = {
                Text("会用「${info.host}」的备份覆盖当前页面站点的 Cookie 与 localStorage，并刷新页面。请确认当前打开的是对应网站。")
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    controller.restoreBackup(info) { _, msg -> message = msg }
                }) { Text("恢复") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestore = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun SettingsPage(controller: BrowserController) {
    val context = LocalContext.current
    val app = context.applicationContext as App

    var searchEngine by remember { mutableStateOf(app.settings.searchEngine) }
    var locationGranted by remember { mutableStateOf(hasLocationPermission(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { locationGranted = hasLocationPermission(context) }
    var textZoom by remember { mutableStateOf(app.settings.textZoom.toFloat()) }
    var desktopMode by remember { mutableStateOf(app.settings.desktopMode) }
    var darkMode by remember { mutableStateOf(app.settings.darkMode) }
    var autoBackup by remember { mutableStateOf(app.settings.autoBackup) }
    var confirmClearData by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        SectionTitle("搜索引擎")
        listOf(
            "Bing" to "https://www.bing.com/search?q=%s",
            "百度" to "https://m.baidu.com/s?word=%s",
            "Google" to "https://www.google.com/search?q=%s"
        ).forEach { (name, url) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        searchEngine = url
                        app.settings.searchEngine = url
                    }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = searchEngine == url,
                    onClick = {
                        searchEngine = url
                        app.settings.searchEngine = url
                    }
                )
                Text(name, fontSize = 14.sp)
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        SectionTitle("网页字体大小")
        Text("${textZoom.toInt()}%", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(
            value = textZoom,
            onValueChange = { textZoom = it },
            valueRange = 100f..200f,
            onValueChangeFinished = {
                app.settings.textZoom = textZoom.toInt()
                controller.applySettings()
            }
        )

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        SettingSwitchRow(
            title = "电脑模式",
            subtitle = "使用桌面浏览器标识加载网页",
            checked = desktopMode,
            onCheckedChange = {
                desktopMode = it
                app.settings.desktopMode = it
                controller.applySettings()
            }
        )

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        SectionTitle("网页深色模式")
        listOf(
            0 to "跟随系统",
            1 to "强制深色网页",
            2 to "始终保持浅色网页"
        ).forEach { (value, label) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        darkMode = value
                        app.settings.darkMode = value
                        controller.applySettings()
                    }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = darkMode == value,
                    onClick = {
                        darkMode = value
                        app.settings.darkMode = value
                        controller.applySettings()
                    }
                )
                Text(label, fontSize = 14.sp)
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        SettingSwitchRow(
            title = "自动备份",
            subtitle = "页面加载完成与切到后台时自动备份",
            checked = autoBackup,
            onCheckedChange = {
                autoBackup = it
                app.settings.autoBackup = it
            }
        )

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        SectionTitle("权限")
        Row(
            Modifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("定位权限", fontSize = 14.sp)
                Text(
                    if (locationGranted) "已授予，网页可直接获取位置" else "未授予，网页请求定位会失败",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = {
                if (locationGranted) {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null)
                        )
                    )
                } else {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            }) { Text(if (locationGranted) "系统设置" else "授予") }
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        SectionTitle("数据管理")
        OutlinedButton(
            onClick = { controller.clearCacheOnly() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) { Text("清除网页缓存（不影响数据）") }
        OutlinedButton(
            onClick = { confirmClearData = true },
            modifier = Modifier.fillMaxWidth()
        ) { Text("清除 Cookie 与网页存储（会影响数据）") }

        Spacer(Modifier.height(16.dp))

        SectionTitle("备份目录")
        Text(
            app.backups.backupDir.absolutePath,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(16.dp))

        SectionTitle("关于")
        Text("轻光浏览器", fontSize = 14.sp)
        Text(
            "版本 ${BuildConfig.VERSION_NAME}",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(32.dp))
    }

    if (confirmClearData) {
        AlertDialog(
            onDismissRequest = { confirmClearData = false },
            title = { Text("清除网页存储？") },
            text = {
                Text("这会删除所有站点的 Cookie 与 localStorage，包括本地网页的数据，且不可撤销。建议先在「数据备份」里备份。")
            },
            confirmButton = {
                TextButton(onClick = {
                    controller.clearSiteData()
                    confirmClearData = false
                }) { Text("确认清除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearData = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 6.dp)
    )
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp)
            Text(
                subtitle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(32.dp)
        )
    }
}

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
