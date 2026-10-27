package com.qingguang.browser.ui

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.Settings
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilterChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qingguang.browser.App
import com.qingguang.browser.BuildConfig
import com.qingguang.browser.data.AuditLog
import com.qingguang.browser.data.HomeItem
import com.qingguang.browser.ui.theme.ThemeController
import com.qingguang.browser.util.BackupInfo
import com.qingguang.browser.util.BackupManager
import com.qingguang.browser.util.SitePermission
import com.qingguang.browser.util.SitePermissionStore
import com.qingguang.browser.web.BrowserController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
                OverlayPage.DOWNLOADS -> DownloadsPage(controller)
                OverlayPage.OFFLINE_PAGES -> OfflinePagesPage(controller, onClose)
                OverlayPage.DATA_BACKUPS -> BackupsPage(controller)
                OverlayPage.SETTINGS -> SettingsPage(controller)
                OverlayPage.PERMISSION_CENTER -> PermissionCenterPage(controller)
                OverlayPage.AUDIT_LOG -> AuditLogPage()
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
    val folders by app.database.bookmarkFolderDao()
        .observeAll()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    /** 当前浏览路径（不含根），最后一项为当前文件夹；支持左右滑动与面包屑跳转 */
    val path = remember { mutableStateListOf<Long>() }
    var forwardPath by remember { mutableStateOf<List<Long>>(emptyList()) }
    val currentId = path.lastOrNull() ?: 0L

    var showNewFolder by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var newFolderParent by remember { mutableStateOf(0L) }
    var folderAction by remember { mutableStateOf<com.qingguang.browser.data.BookmarkFolder?>(null) }
    var bookmarkAction by remember { mutableStateOf<com.qingguang.browser.data.Bookmark?>(null) }
    var editTarget by remember { mutableStateOf<com.qingguang.browser.data.Bookmark?>(null) }
    var editTitle by remember { mutableStateOf("") }
    var editUrl by remember { mutableStateOf("") }
    var moveTarget by remember { mutableStateOf<com.qingguang.browser.data.Bookmark?>(null) }
    var renameTarget by remember { mutableStateOf<com.qingguang.browser.data.BookmarkFolder?>(null) }
    var renameName by remember { mutableStateOf("") }
    var deleteFolder by remember { mutableStateOf<com.qingguang.browser.data.BookmarkFolder?>(null) }
    var deleteBookmark by remember { mutableStateOf<com.qingguang.browser.data.Bookmark?>(null) }
    var iconTarget by remember { mutableStateOf<com.qingguang.browser.data.Bookmark?>(null) }
    var query by remember { mutableStateOf("") }
    var selecting by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<Long>() }
    var showMenu by remember { mutableStateOf(false) }
    var confirmBatchDelete by remember { mutableStateOf(false) }
    var batchMove by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var sortMode by remember { mutableStateOf(false) }
    var dragId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    var autoScroll by remember { mutableStateOf(0f) }
    val bookmarkListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val rowHeightPx = with(density) { 66.dp.toPx() }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/html")
    ) { uri ->
        if (uri != null) {
            val html = buildBookmarkHtml(bookmarks, folders)
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(html.toByteArray(Charsets.UTF_8))
                } ?: error("无法写入")
            }.onSuccess {
                message = "已导出 ${bookmarks.size} 个书签到 HTML"
            }.onFailure {
                message = "导出失败：${it.message}"
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val text = runCatching {
                    context.contentResolver.openInputStream(uri)
                        ?.bufferedReader(Charsets.UTF_8)
                        ?.use { it.readText() }
                }.getOrNull()
                if (text.isNullOrBlank()) {
                    message = "导入失败：无法读取文件"
                    return@launch
                }
                val imported = parseBookmarkHtml(text)
                if (imported.isEmpty()) {
                    message = "文件中没有找到书签"
                    return@launch
                }
                val knowFolders = folders.toMutableList()
                var created = 0
                imported.forEach { entry ->
                    var parentId = 0L
                    entry.folderPath.forEach { name ->
                        val existing = knowFolders.firstOrNull {
                            it.parentId == parentId && it.name == name
                        }
                        parentId = if (existing != null) {
                            existing.id
                        } else {
                            val id = app.database.bookmarkFolderDao().insert(
                                com.qingguang.browser.data.BookmarkFolder(
                                    name = name,
                                    parentId = parentId
                                )
                            )
                            knowFolders.add(
                                com.qingguang.browser.data.BookmarkFolder(
                                    id = id, name = name, parentId = parentId
                                )
                            )
                            id
                        }
                    }
                    app.database.bookmarkDao().insert(
                        com.qingguang.browser.data.Bookmark(
                            title = entry.title,
                            url = entry.url,
                            folderId = currentId.takeIf { entry.folderPath.isEmpty() } ?: parentId
                        )
                    )
                    created++
                }
                message = "已导入 $created 个书签"
            }
        }
    }

    fun openFolder(id: Long) {
        path.add(id)
        forwardPath = emptyList()
    }
    fun goUp() {
        if (path.isNotEmpty()) {
            forwardPath = listOf(path.removeAt(path.lastIndex)) + forwardPath
        }
    }
    fun goForwardLevel() {
        if (forwardPath.isNotEmpty()) {
            path.add(forwardPath.first())
            forwardPath = forwardPath.drop(1)
        }
    }
    fun jumpTo(index: Int) {
        forwardPath = path.drop(index + 1) + forwardPath
        while (path.size > index + 1) path.removeAt(path.lastIndex)
    }

    // 当前文件夹被删除时自动回到上一级
    LaunchedEffect(folders) {
        if (path.isNotEmpty() && folders.none { it.id == path.last() }) {
            path.removeAt(path.lastIndex)
        }
    }

    BackHandler(enabled = path.isNotEmpty()) { goUp() }

    val chain = folderChain(folders, currentId)
    val subFolders = folders.filter { it.parentId == currentId }.sortedBy { it.createdAt }
    val itemsHere = bookmarks.filter { it.folderId == currentId }
    val keyword = query.trim()
    val visibleFolders = if (keyword.isEmpty()) subFolders
    else folders.filter { it.name.contains(keyword, ignoreCase = true) }
    val visibleBookmarks = if (keyword.isEmpty()) itemsHere
    else bookmarks.filter {
        it.title.contains(keyword, ignoreCase = true) ||
            it.url.contains(keyword, ignoreCase = true)
    }
    val shownFolders = if (selecting || sortMode) emptyList() else visibleFolders

    fun finishReorder(fromIndex: Int) {
        val moved = ((dragOffset + autoScroll) / rowHeightPx).roundToInt()
        dragId = null
        dragOffset = 0f
        autoScroll = 0f
        if (moved == 0) return
        val list = visibleBookmarks.toMutableList()
        if (fromIndex !in list.indices) return
        val target = (fromIndex + moved).coerceIn(0, list.lastIndex)
        if (target == fromIndex) return
        val item = list.removeAt(fromIndex)
        list.add(target, item)
        scope.launch {
            list.forEachIndexed { index, bookmark ->
                app.database.bookmarkDao().updateSortOrder(bookmark.id, index + 1)
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    BreadcrumbLabel("书签", chain.isEmpty()) { jumpTo(-1) }
                }
                items(chain, key = { it.id }) { folder ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(15.dp)
                        )
                        val index = chain.indexOfFirst { it.id == folder.id }
                        BreadcrumbLabel(folder.name, index == chain.lastIndex) { jumpTo(index) }
                    }
                }
            }
            IconButton(
                onClick = {
                    newFolderParent = currentId
                    newFolderName = ""
                    showNewFolder = true
                },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Filled.CreateNewFolder,
                    contentDescription = "新建文件夹",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = { showMenu = true }, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = "更多",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("搜索书签（名称或网址）", fontSize = 13.sp) },
                modifier = Modifier.weight(1f)
            )
            if (selecting) {
                TextButton(onClick = {
                    selecting = false
                    selectedIds.clear()
                    query = ""
                }) { Text("完成") }
            }
        }
        if (selecting) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "已选 ${selectedIds.size} 项",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = {
                    selectedIds.clear()
                    selectedIds.addAll(visibleBookmarks.map { it.id })
                }) { Text("全选") }
                TextButton(onClick = {
                    val targets = bookmarks.filter { it.id in selectedIds }
                    if (targets.isEmpty()) return@TextButton
                    scope.launch {
                        val dao = app.database.homeItemDao()
                        val all = dao.all()
                        val existing = all.filter { it.parentId == 0L }
                            .map { it.url }.toMutableSet()
                        val used = (0 until app.settings.homePageCount).associateWith { pg ->
                            all.filter { it.parentId == 0L && it.page == pg }
                                .map { it.slot }.toMutableSet()
                        }.toMutableMap()
                        var added = 0
                        targets.forEach { bookmark ->
                            if (bookmark.url in existing) return@forEach
                            var targetPage = -1
                            var targetSlot = -1
                            for (pg in 0 until app.settings.homePageCount) {
                                val slot = (0 until homePageCapacity(pg)).firstOrNull {
                                    it !in (used[pg] ?: mutableSetOf())
                                }
                                if (slot != null) {
                                    targetPage = pg
                                    targetSlot = slot
                                    break
                                }
                            }
                            if (targetPage >= 0) {
                                dao.insert(
                                    HomeItem(
                                        title = bookmark.title,
                                        url = bookmark.url,
                                        page = targetPage,
                                        slot = targetSlot
                                    )
                                )
                                used[targetPage]?.add(targetSlot)
                                existing.add(bookmark.url)
                                added++
                            }
                        }
                        message = if (added > 0) "已添加 $added 个书签到首页快捷入口"
                        else "首页快捷入口已满，或这些书签已在首页"
                    }
                }) { Text("添加到首页") }
            }
        }
        message?.let {
            Text(
                it,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp)
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(currentId, forwardPath) {
                    val threshold = 56.dp.toPx()
                    var drag = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { drag = 0f },
                        onDragEnd = {
                            if (drag > threshold) goUp() else if (drag < -threshold) goForwardLevel()
                        },
                        onHorizontalDrag = { _, amount -> drag += amount }
                    )
                }
        ) {
            if (shownFolders.isEmpty() && visibleBookmarks.isEmpty()) {
                EmptyHint(
                    if (keyword.isNotEmpty()) "没有找到匹配的书签。"
                    else if (chain.isEmpty()) "还没有书签。打开网页后，从菜单里点「添加书签」。"
                    else "这个文件夹还是空的，点右上角可以新建子文件夹。"
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = bookmarkListState,
                    // 拖动时禁用列表滚动，避免滚动手势把长按拖动“抢走”导致脱手
                    userScrollEnabled = dragId == null
                ) {
                    items(shownFolders, key = { "f-${it.id}" }) { folder ->
                        BookmarkFolderRow(
                            folder = folder,
                            count = bookmarks.count { it.folderId == folder.id },
                            onClick = { openFolder(folder.id) },
                            onLongClick = { folderAction = folder }
                        )
                    }
                    itemsIndexed(visibleBookmarks, key = { _, it -> "b-${it.id}" }) { index, bookmark ->
                        val dragging = dragId == bookmark.id
                        Box(
                            Modifier
                                .offset {
                                    IntOffset(
                                        0,
                                        if (dragging) (dragOffset + autoScroll).roundToInt() else 0
                                    )
                                }
                                .zIndex(if (dragging) 1f else 0f)
                                .graphicsLayer {
                                    if (dragging) {
                                        scaleX = 1.03f
                                        scaleY = 1.03f
                                        shadowElevation = 12f
                                    }
                                }
                                .then(
                                    if (sortMode) Modifier.pointerInput(bookmark.id, index) {
                                        awaitEachGesture {
                                            // 在 Initial 阶段消费事件，避免被 LazyColumn 的滚动/点击手势抢走
                                            val down = awaitFirstDown(
                                                requireUnconsumed = false,
                                                pass = PointerEventPass.Initial
                                            )
                                            val longPressed = awaitLongPressOrCancellation(down.id)
                                                ?: return@awaitEachGesture
                                            longPressed.consume()
                                            dragId = bookmark.id
                                            dragOffset = 0f
                                            autoScroll = 0f
                                            try {
                                                while (true) {
                                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                                    val change = event.changes
                                                        .firstOrNull { it.id == down.id } ?: break
                                                    if (change.changedToUpIgnoreConsumed()) {
                                                        change.consume()
                                                        finishReorder(index)
                                                        break
                                                    }
                                                    change.consume()
                                                    dragOffset += change.positionChange().y
                                                    // 靠近列表上下边缘时自动滚动，拖到很远也能继续移动
                                                    val viewport = bookmarkListState
                                                        .layoutInfo.viewportSize.height
                                                    if (viewport > 0) {
                                                        val edge = with(density) { 72.dp.toPx() }
                                                        val step = when {
                                                            change.position.y < edge -> -18f
                                                            change.position.y > viewport - edge -> 18f
                                                            else -> 0f
                                                        }
                                                        if (step != 0f) {
                                                            scope.launch {
                                                                autoScroll +=
                                                                    bookmarkListState.scrollBy(step)
                                                            }
                                                        }
                                                    }
                                                }
                                            } finally {
                                                // 手势被系统中断时复位，避免图标卡在浮起状态
                                                if (dragId == bookmark.id) {
                                                    dragId = null
                                                    dragOffset = 0f
                                                    autoScroll = 0f
                                                }
                                            }
                                        }
                                    } else Modifier
                                )
                        ) {
                            BookmarkListRow(
                                bookmark = bookmark,
                                selectionMode = selecting,
                                selected = bookmark.id in selectedIds,
                                onClick = {
                                    if (selecting) {
                                        if (bookmark.id in selectedIds) selectedIds.remove(bookmark.id)
                                        else selectedIds.add(bookmark.id)
                                    } else if (!sortMode) {
                                        controller.loadUrl(bookmark.url)
                                        onClose()
                                    }
                                },
                                onLongClick = {
                                    if (selecting) {
                                        if (bookmark.id in selectedIds) selectedIds.remove(bookmark.id)
                                        else selectedIds.add(bookmark.id)
                                    } else if (!sortMode) {
                                        bookmarkAction = bookmark
                                    }
                                },
                                onDelete = { deleteBookmark = bookmark },
                                trailingHint = if (sortMode) "拖动" else null
                            )
                        }
                    }
                    item { Spacer(Modifier.height(12.dp)) }
                }
            }
        }
        if (selecting) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { batchMove = true },
                    enabled = selectedIds.isNotEmpty()
                ) { Text("移动到文件夹") }
                TextButton(
                    onClick = { confirmBatchDelete = true },
                    enabled = selectedIds.isNotEmpty()
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (showMenu) {
        ActionDialog(
            title = "书签工具",
            onDismiss = { showMenu = false },
            actions = listOf(
                "批量选择" to {
                    showMenu = false
                    selecting = true
                    query = ""
                },
                (if (sortMode) "结束拖动排序" else "拖动排序书签") to {
                    showMenu = false
                    sortMode = !sortMode
                    selecting = false
                    query = ""
                    selectedIds.clear()
                },
                "导入书签 HTML" to {
                    showMenu = false
                    importLauncher.launch(arrayOf("text/html", "text/plain", "*/*"))
                },
                "导出书签 HTML" to {
                    showMenu = false
                    exportLauncher.launch("bookmarks.html")
                }
            )
        )
    }

    if (confirmBatchDelete) {
        AlertDialog(
            onDismissRequest = { confirmBatchDelete = false },
            title = { Text("删除所选书签？") },
            text = { Text("将删除 ${selectedIds.size} 个书签，此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    val ids = selectedIds.toList()
                    scope.launch {
                        ids.forEach { app.database.bookmarkDao().delete(it) }
                    }
                    selectedIds.clear()
                    confirmBatchDelete = false
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmBatchDelete = false }) { Text("取消") }
            }
        )
    }

    if (batchMove) {
        val flatFolders = flattenFolders(folders)
        AlertDialog(
            onDismissRequest = { batchMove = false },
            title = { Text("移动 ${selectedIds.size} 个书签到") },
            text = {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    item {
                        FolderChoiceRow("未分类", selected = false) {
                            val ids = selectedIds.toList()
                            scope.launch {
                                ids.forEach {
                                    app.database.bookmarkDao().moveToFolder(it, 0L)
                                }
                            }
                            selectedIds.clear()
                            batchMove = false
                        }
                    }
                    items(flatFolders, key = { it.first.id }) { (folder, depth) ->
                        FolderChoiceRow(
                            "    ".repeat(depth) + folder.name,
                            selected = false
                        ) {
                            val ids = selectedIds.toList()
                            scope.launch {
                                ids.forEach {
                                    app.database.bookmarkDao().moveToFolder(it, folder.id)
                                }
                            }
                            selectedIds.clear()
                            batchMove = false
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { batchMove = false }) { Text("取消") }
            }
        )
    }

    if (showNewFolder) {
        AlertDialog(
            onDismissRequest = { showNewFolder = false },
            title = { Text(if (newFolderParent == 0L) "新建文件夹" else "新建子文件夹") },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("文件夹名称") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val folderName = newFolderName.trim()
                        if (folderName.isBlank()) return@TextButton
                        scope.launch {
                            app.database.bookmarkFolderDao().insert(
                                com.qingguang.browser.data.BookmarkFolder(
                                    name = folderName,
                                    parentId = newFolderParent
                                )
                            )
                        }
                        newFolderName = ""
                        showNewFolder = false
                    },
                    enabled = newFolderName.isNotBlank()
                ) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolder = false }) { Text("取消") }
            }
        )
    }

    folderAction?.let { folder ->
        ActionDialog(
            title = folder.name,
            onDismiss = { folderAction = null },
            actions = listOf(
                "打开" to {
                    folderAction = null
                    openFolder(folder.id)
                },
                "新建子文件夹" to {
                    folderAction = null
                    newFolderParent = folder.id
                    newFolderName = ""
                    showNewFolder = true
                },
                "重命名" to {
                    folderAction = null
                    renameTarget = folder
                    renameName = folder.name
                },
                "移动到根目录" to {
                    folderAction = null
                    scope.launch { app.database.bookmarkFolderDao().moveToParent(folder.id, 0L) }
                },
                "删除文件夹" to {
                    folderAction = null
                    deleteFolder = folder
                }
            )
        )
    }

    bookmarkAction?.let { bookmark ->
        ActionDialog(
            title = bookmark.title,
            onDismiss = { bookmarkAction = null },
            actions = listOf(
                "打开" to {
                    bookmarkAction = null
                    controller.loadUrl(bookmark.url)
                    onClose()
                },
                "在新标签页打开" to {
                    bookmarkAction = null
                    controller.newTab()
                    controller.loadUrl(bookmark.url)
                    onClose()
                },
                "编辑" to {
                    bookmarkAction = null
                    editTarget = bookmark
                    editTitle = bookmark.title
                    editUrl = bookmark.url
                },
                "移动到文件夹" to {
                    bookmarkAction = null
                    moveTarget = bookmark
                },
                "添加到首页" to {
                    bookmarkAction = null
                    scope.launch {
                        val dao = app.database.homeItemDao()
                        if (dao.countOnHome(bookmark.url) == 0) {
                            var page = 0
                            val lastPage = (app.settings.homePageCount - 1).coerceAtLeast(0)
                            while (page < lastPage &&
                                dao.countRootOnPage(page) >= homePageCapacity(page)
                            ) {
                                page++
                            }
                            val used = dao.slotsOnPage(page).toSet()
                            val slot = (0 until homePageCapacity(page))
                                .firstOrNull { it !in used } ?: -1
                            dao.insert(
                                com.qingguang.browser.data.HomeItem(
                                    title = bookmark.title,
                                    url = bookmark.url,
                                    page = page,
                                    slot = slot
                                )
                            )
                            Toast.makeText(context, "已添加到首页", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "首页已有该快捷方式", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                "分享" to {
                    bookmarkAction = null
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, bookmark.url)
                    }
                    context.startActivity(Intent.createChooser(intent, "分享链接"))
                },
                "自定义图标" to {
                    bookmarkAction = null
                    iconTarget = bookmark
                },
                "删除" to {
                    bookmarkAction = null
                    deleteBookmark = bookmark
                }
            )
        )
    }

    renameTarget?.let { folder ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重命名文件夹") },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = renameName,
                    onValueChange = { renameName = it },
                    label = { Text("文件夹名称") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch { app.database.bookmarkFolderDao().rename(folder.id, renameName.trim()) }
                        renameTarget = null
                    },
                    enabled = renameName.isNotBlank()
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("取消") }
            }
        )
    }

    editTarget?.let { bookmark ->
        AlertDialog(
            onDismissRequest = { editTarget = null },
            title = { Text("编辑书签") },
            text = {
                Column {
                    androidx.compose.material3.OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = { Text("名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = editUrl,
                        onValueChange = { editUrl = it },
                        label = { Text("网址") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            app.database.bookmarkDao().update(
                                id = bookmark.id,
                                title = editTitle.trim().ifBlank { bookmark.title },
                                url = editUrl.trim(),
                                folderId = bookmark.folderId
                            )
                        }
                        editTarget = null
                    },
                    enabled = editUrl.isNotBlank()
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { editTarget = null }) { Text("取消") }
            }
        )
    }

    moveTarget?.let { bookmark ->
        val flatFolders = flattenFolders(folders)
        AlertDialog(
            onDismissRequest = { moveTarget = null },
            title = { Text("移动到文件夹") },
            text = {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    item {
                        FolderChoiceRow("未分类", selected = bookmark.folderId == 0L) {
                            scope.launch { app.database.bookmarkDao().moveToFolder(bookmark.id, 0L) }
                            moveTarget = null
                        }
                    }
                    items(flatFolders, key = { it.first.id }) { (folder, depth) ->
                        FolderChoiceRow(
                            "    ".repeat(depth) + folder.name,
                            selected = bookmark.folderId == folder.id
                        ) {
                            scope.launch {
                                app.database.bookmarkDao().moveToFolder(bookmark.id, folder.id)
                            }
                            moveTarget = null
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { moveTarget = null }) { Text("取消") }
            }
        )
    }

    deleteFolder?.let { folder ->
        val ids = collectFolderIds(folder.id, folders)
        AlertDialog(
            onDismissRequest = { deleteFolder = null },
            title = { Text("删除文件夹？") },
            text = { Text("文件夹「${folder.name}」及其子文件夹里的书签会移到「未分类」，不会被删除。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        bookmarks.filter { it.folderId in ids }.forEach {
                            app.database.bookmarkDao().moveToFolder(it.id, 0L)
                        }
                        ids.forEach { app.database.bookmarkFolderDao().delete(it) }
                    }
                    deleteFolder = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteFolder = null }) { Text("取消") }
            }
        )
    }

    iconTarget?.let { bookmark ->
        IconStyleDialog(
            title = bookmark.title,
            initialColor = bookmark.iconColor,
            initialText = bookmark.iconText,
            onDismiss = { iconTarget = null },
            onSave = { color, text ->
                iconTarget = null
                scope.launch {
                    // 按 URL 同步书签与首页入口，历史记录展示时也读取同一书签样式
                    app.database.bookmarkDao().updateIcon(bookmark.id, color, text)
                    app.database.bookmarkDao().updateIconByUrl(bookmark.url, color, text)
                    app.database.homeItemDao().updateIconByUrl(bookmark.url, color, text)
                }
            }
        )
    }

    deleteBookmark?.let { bookmark ->
        AlertDialog(
            onDismissRequest = { deleteBookmark = null },
            title = { Text("删除书签？") },
            text = { Text("将删除「${bookmark.title}」，此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { app.database.bookmarkDao().delete(bookmark.id) }
                    deleteBookmark = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteBookmark = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun BreadcrumbLabel(text: String, isCurrent: Boolean, onClick: () -> Unit) {
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
        color = if (isCurrent) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp)
    )
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun BookmarkFolderRow(
    folder: com.qingguang.browser.data.BookmarkFolder,
    count: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .pointerInput(folder.id) {
                detectTapGestures(onTap = { onClick() }, onLongPress = { onLongClick() })
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FolderGlyph(Modifier.size(34.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                folder.name,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "$count 项",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun BookmarkListRow(
    bookmark: com.qingguang.browser.data.Bookmark,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit,
    trailingHint: String? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .pointerInput(bookmark.id, selectionMode) {
                if (trailingHint == null) {
                    detectTapGestures(onTap = { onClick() }, onLongPress = { onLongClick() })
                }
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectionMode) {
            Checkbox(checked = selected, onCheckedChange = { onClick() })
            Spacer(Modifier.width(4.dp))
        }
        Box(
            Modifier.size(34.dp),
            contentAlignment = Alignment.Center
        ) {
            SiteIconBadge(
                title = bookmark.title,
                url = bookmark.url,
                iconColor = bookmark.iconColor,
                iconText = bookmark.iconText,
                size = 34.dp,
                corner = 10.dp,
                fontSize = 14.sp
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                bookmark.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                bookmark.url,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (trailingHint == null) {
            IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "删除书签",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        } else {
            Box(
                Modifier.size(34.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.DragHandle,
                    contentDescription = "拖动排序",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun ActionDialog(
    title: String,
    onDismiss: () -> Unit,
    actions: List<Pair<String, () -> Unit>>
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                actions.forEach { (label, action) ->
                    Text(
                        label,
                        fontSize = 15.sp,
                        color = if (label.startsWith("删除")) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onDismiss()
                                action()
                            }
                            .padding(vertical = 11.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

@Composable
private fun FolderChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 14.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        color = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp)
    )
}

/** 从指定文件夹向上回溯，得到根 → 当前 的完整路径 */
internal fun folderChain(
    folders: List<com.qingguang.browser.data.BookmarkFolder>,
    id: Long
): List<com.qingguang.browser.data.BookmarkFolder> {
    if (id == 0L) return emptyList()
    val chain = mutableListOf<com.qingguang.browser.data.BookmarkFolder>()
    var cursor = folders.firstOrNull { it.id == id }
    var guard = 0
    while (cursor != null && guard < 128) {
        chain.add(0, cursor)
        val parentId = cursor.parentId
        cursor = folders.firstOrNull { it.id == parentId }
        guard++
    }
    return chain
}

/** 按层级顺序展开文件夹，depth 用于缩进展示 */
internal fun flattenFolders(
    folders: List<com.qingguang.browser.data.BookmarkFolder>,
    parentId: Long = 0L,
    depth: Int = 0
): List<Pair<com.qingguang.browser.data.BookmarkFolder, Int>> {
    val children = folders.filter { it.parentId == parentId }.sortedBy { it.createdAt }
    return children.flatMap {
        listOf(it to depth) + flattenFolders(folders, it.id, depth + 1)
    }
}

/** 该文件夹自身及其全部后代文件夹 id */
private fun collectFolderIds(
    rootId: Long,
    folders: List<com.qingguang.browser.data.BookmarkFolder>
): List<Long> {
    val ids = mutableListOf(rootId)
    var index = 0
    while (index < ids.size) {
        val current = ids[index]
        folders.filter { it.parentId == current }.forEach {
            if (it.id !in ids) ids.add(it.id)
        }
        index++
    }
    return ids
}

@Composable
private fun HistoryPage(controller: BrowserController, onClose: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as App
    val scope = rememberCoroutineScope()
    val history by app.database.historyDao()
        .observeRecent()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    // 历史图标与书签/首页保持一致：同一 URL 复用自定义样式（书签优先）
    val bookmarks by app.database.bookmarkDao()
        .observeAll()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val homeItems by app.database.homeItemDao()
        .observeAll()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val iconStyles = remember(bookmarks, homeItems) {
        buildMap {
            homeItems.filter { it.url.isNotBlank() && !it.isFolder }.forEach {
                put(it.url, it.iconColor to it.iconText)
            }
            bookmarks.forEach { put(it.url, it.iconColor to it.iconText) }
        }
    }
    var confirmClear by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var selecting by remember { mutableStateOf(false) }
    val selectedUrls = remember { mutableStateListOf<String>() }
    var confirmDeleteSelected by remember { mutableStateOf(false) }
    val keyword = query.trim()
    val filtered = remember(history, keyword) {
        if (keyword.isEmpty()) history
        else history.filter {
            it.title.contains(keyword, ignoreCase = true) ||
                it.url.contains(keyword, ignoreCase = true)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("搜索历史（名称或网址）", fontSize = 13.sp) },
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = {
                selecting = !selecting
                if (!selecting) selectedUrls.clear()
            }) { Text(if (selecting) "完成" else "选择") }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selecting) {
                Text(
                    "已选 ${selectedUrls.size} 项",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = {
                    selectedUrls.clear()
                    selectedUrls.addAll(filtered.map { it.url })
                }) { Text("全选") }
            } else {
                Spacer(Modifier.weight(1f))
            }
            TextButton(onClick = { confirmClear = true }, enabled = history.isNotEmpty()) {
                Text("清空历史")
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (filtered.isEmpty()) {
            EmptyHint(if (keyword.isNotEmpty()) "没有匹配的历史记录。" else "还没有浏览历史。")
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(filtered, key = { it.id }) { item ->
                    ListItem(
                        leadingContent = {
                            if (selecting) {
                                Checkbox(
                                    checked = item.url in selectedUrls,
                                    onCheckedChange = {
                                        if (item.url in selectedUrls) selectedUrls.remove(item.url)
                                        else selectedUrls.add(item.url)
                                    }
                                )
                            } else {
                                val style = iconStyles[item.url]
                                SiteIconBadge(
                                    title = item.title,
                                    url = item.url,
                                    iconColor = style?.first,
                                    iconText = style?.second,
                                    size = 34.dp,
                                    corner = 10.dp
                                )
                            }
                        },
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
                            if (selecting) {
                                if (item.url in selectedUrls) selectedUrls.remove(item.url)
                                else selectedUrls.add(item.url)
                            } else {
                                controller.loadUrl(item.url)
                                onClose()
                            }
                        }
                    )
                }
            }
        }
        if (selecting) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = { confirmDeleteSelected = true },
                    enabled = selectedUrls.isNotEmpty()
                ) {
                    Text("删除所选", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (confirmDeleteSelected) {
        AlertDialog(
            onDismissRequest = { confirmDeleteSelected = false },
            title = { Text("删除所选历史？") },
            text = { Text("将删除 ${selectedUrls.size} 条浏览历史。") },
            confirmButton = {
                TextButton(onClick = {
                    val urls = selectedUrls.toList()
                    scope.launch { urls.forEach { app.database.historyDao().deleteByUrl(it) } }
                    selectedUrls.clear()
                    confirmDeleteSelected = false
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteSelected = false }) { Text("取消") }
            }
        )
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

private data class DownloadProgress(val status: Int, val bytes: Long, val total: Long)

@Composable
private fun DownloadsPage(controller: BrowserController) {
    val context = LocalContext.current
    val app = context.applicationContext as App
    val scope = rememberCoroutineScope()
    val downloads by app.database.downloadDao()
        .observeAll()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val manager = remember {
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    }
    var progressMap by remember { mutableStateOf<Map<Long, DownloadProgress>>(emptyMap()) }
    var showDirDialog by remember { mutableStateOf(false) }
    var dirText by remember { mutableStateOf(app.settings.downloadSubDir) }
    var pendingDelete by remember { mutableStateOf<com.qingguang.browser.data.DownloadRecord?>(null) }
    var subDir by remember { mutableStateOf(app.settings.downloadSubDir) }

    // 轮询系统下载管理器，刷新进度与状态
    LaunchedEffect(downloads) {
        val tracked = downloads.filter { it.managerId > 0 && it.status != "下载完成" && it.status != "已取消" }
        if (tracked.isEmpty()) {
            progressMap = emptyMap()
            return@LaunchedEffect
        }
        while (true) {
            val snapshot = mutableMapOf<Long, DownloadProgress>()
            tracked.forEach { record ->
                queryDownload(manager, record.managerId)?.let { snapshot[record.id] = it }
            }
            progressMap = snapshot
            snapshot.forEach { (recordId, progress) ->
                val record = tracked.firstOrNull { it.id == recordId } ?: return@forEach
                val label = when (progress.status) {
                    DownloadManager.STATUS_RUNNING -> "下载中"
                    DownloadManager.STATUS_PAUSED -> "已暂停"
                    DownloadManager.STATUS_SUCCESSFUL -> "下载完成"
                    DownloadManager.STATUS_FAILED -> "下载失败"
                    else -> "等待下载"
                }
                if (label != record.status) {
                    scope.launch { app.database.downloadDao().updateStatus(record.id, label) }
                }
            }
            delay(900)
        }
    }

    fun openRecord(record: com.qingguang.browser.data.DownloadRecord) {
        val uri = when {
            record.managerId > 0 -> manager.getUriForDownloadedFile(record.managerId)
            !record.localUri.isNullOrBlank() -> Uri.parse(record.localUri)
            else -> null
        }
        if (uri == null) {
            Toast.makeText(context, "文件尚未下载完成或已被删除", Toast.LENGTH_SHORT).show()
            return
        }
        val mime = record.mimeType.ifBlank {
            MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(record.fileName.substringAfterLast('.', "").lowercase())
                ?: "*/*"
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { context.startActivity(intent) }
            .onFailure { Toast.makeText(context, "没有可打开该文件的应用", Toast.LENGTH_SHORT).show() }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("保存位置", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text(
                    if (subDir.isBlank()) "下载" else "下载/$subDir",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = {
                dirText = subDir
                showDirDialog = true
            }) { Text("更改") }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            "支持暂停 / 继续 / 重试；点击文件可直接打开。下载由系统下载管理器完成。",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        if (downloads.isEmpty()) {
            EmptyHint("还没有下载记录。")
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(downloads, key = { it.id }) { record ->
                    val progress = progressMap[record.id]
                    val running = progress?.status == DownloadManager.STATUS_RUNNING
                    val paused = progress?.status == DownloadManager.STATUS_PAUSED
                    val finished = record.status == "下载完成"
                    val failed = record.status == "下载失败"
                    val total = progress?.total?.takeIf { it > 0 } ?: record.totalBytes
                    val bytes = progress?.bytes ?: if (finished) total else 0L
                    Column(Modifier.fillMaxWidth()) {
                        ListItem(
                            headlineContent = {
                                Text(record.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            supportingContent = {
                                Column {
                                    Text(
                                        buildString {
                                            append(record.status)
                                            if (total > 0) {
                                                append(" · ")
                                                append(BackupManager.formatSize(bytes.coerceAtLeast(0)))
                                                append(" / ")
                                                append(BackupManager.formatSize(total))
                                            }
                                            append(" · ")
                                            append(BackupManager.formatTime(record.createdAt))
                                        },
                                        fontSize = 11.sp
                                    )
                                    if (running || paused) {
                                        val ratio = if (total > 0) (bytes.toFloat() / total) else 0f
                                        LinearProgressIndicator(
                                            progress = { ratio.coerceIn(0f, 1f) },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 4.dp)
                                                .height(3.dp)
                                        )
                                    }
                                }
                            },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (record.managerId > 0 && !finished) {
                                        if (running) {
                                            IconButton(onClick = {
                                                if (pauseDownloadTask(manager, record.managerId)) {
                                                    scope.launch {
                                                        app.database.downloadDao()
                                                            .updateStatus(record.id, "已暂停")
                                                    }
                                                } else {
                                                    Toast.makeText(
                                                        context,
                                                        "当前系统不支持暂停下载",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            }) {
                                                Icon(
                                                    Icons.Filled.Pause,
                                                    contentDescription = "暂停下载"
                                                )
                                            }
                                        }
                                        if (paused || (!running && !finished && !failed)) {
                                            IconButton(onClick = {
                                                if (resumeDownloadTask(manager, record.managerId)) {
                                                    scope.launch {
                                                        app.database.downloadDao()
                                                            .updateStatus(record.id, "下载中")
                                                    }
                                                } else {
                                                    Toast.makeText(
                                                        context,
                                                        "当前系统不支持继续下载，请使用重试",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            }) {
                                                Icon(
                                                    Icons.Filled.PlayArrow,
                                                    contentDescription = "继续下载"
                                                )
                                            }
                                        }
                                    }
                                    if (failed) {
                                        TextButton(onClick = {
                                            controller.retryDownload(
                                                record.url,
                                                record.fileName,
                                                record.mimeType
                                            )
                                            scope.launch {
                                                app.database.downloadDao().delete(record.id)
                                            }
                                        }) { Text("重试", fontSize = 12.sp) }
                                    }
                                    if (finished || record.localUri != null) {
                                        TextButton(onClick = { openRecord(record) }) {
                                            Text("打开", fontSize = 12.sp)
                                        }
                                    }
                                    IconButton(onClick = { pendingDelete = record }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "删除记录")
                                    }
                                }
                            },
                            modifier = Modifier.clickable {
                                if (finished || record.localUri != null) openRecord(record)
                            }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }

    if (showDirDialog) {
        AlertDialog(
            onDismissRequest = { showDirDialog = false },
            title = { Text("下载保存位置") },
            text = {
                Column {
                    Text(
                        "文件会保存在系统「下载」目录下的指定子目录；留空则保存在下载根目录。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = dirText,
                        onValueChange = { dirText = it },
                        singleLine = true,
                        label = { Text("子目录名称") },
                        placeholder = { Text("例如：轻光浏览器") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val cleaned = dirText.trim().trim('/')
                    app.settings.downloadSubDir = cleaned
                    subDir = cleaned
                    showDirDialog = false
                }) { Text("保存") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        dirText = ""
                    }) { Text("用下载根目录") }
                    TextButton(onClick = { showDirDialog = false }) { Text("取消") }
                }
            }
        )
    }

    pendingDelete?.let { record ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除下载记录？") },
            text = { Text("只删除下载中心里的记录，已下载的文件会保留在下载目录。") },
            confirmButton = {
                TextButton(onClick = {
                    if (record.managerId > 0 && record.status != "下载完成") {
                        runCatching { manager.remove(record.managerId) }
                    }
                    scope.launch { app.database.downloadDao().delete(record.id) }
                    pendingDelete = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }
}

/** 系统 DownloadManager 的 pause/resume 在 SDK 中未公开，只能通过反射调用 */
private fun callDownloadManager(manager: DownloadManager, method: String, id: Long): Boolean =
    runCatching {
        val target = DownloadManager::class.java.getMethod(method, LongArray::class.java)
        target.invoke(manager, longArrayOf(id))
        true
    }.getOrDefault(false)

private fun pauseDownloadTask(manager: DownloadManager, id: Long): Boolean =
    callDownloadManager(manager, "pauseDownload", id)

private fun resumeDownloadTask(manager: DownloadManager, id: Long): Boolean =
    callDownloadManager(manager, "resumeDownload", id)

/** 查询系统下载任务当前状态；任务不存在时返回 null */
private fun queryDownload(manager: DownloadManager, id: Long): DownloadProgress? {
    if (id <= 0) return null
    var cursor: Cursor? = null
    return try {
        cursor = manager.query(DownloadManager.Query().setFilterById(id))
        if (cursor.moveToFirst()) {
            DownloadProgress(
                status = cursor.getInt(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                ),
                bytes = cursor.getLong(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                ),
                total = cursor.getLong(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                )
            )
        } else {
            null
        }
    } catch (_: Exception) {
        null
    } finally {
        cursor?.close()
    }
}

@Composable
private fun OfflinePagesPage(controller: BrowserController, onClose: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as App
    val pages by app.database.offlinePageDao()
        .observeAll()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var pendingDelete by remember {
        mutableStateOf<com.qingguang.browser.data.OfflinePage?>(null)
    }

    if (pages.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "还没有离线网页。\n在网页菜单里选「保存网页」，图片和样式会一起保存。",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(pages, key = { it.id }) { page ->
                ListItem(
                    leadingContent = {
                        Icon(
                            Icons.Filled.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    },
                    headlineContent = {
                        Text(page.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    supportingContent = {
                        Text(
                            "${page.totalBytes / 1024} KB · ${page.url}",
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = {
                                controller.openOfflinePage(page)
                                onClose()
                            }) { Text("打开") }
                            IconButton(onClick = { pendingDelete = page }) {
                                Icon(Icons.Filled.Delete, contentDescription = "删除")
                            }
                        }
                    }
                )
            }
        }
    }

    pendingDelete?.let { page ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除离线网页") },
            text = { Text("将删除「${page.title}」及其图片、样式文件，无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    controller.deleteOfflinePage(page)
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun BackupsPage(controller: BrowserController) {
    var mode by remember { mutableStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = mode == 0,
                onClick = { mode = 0 },
                label = { Text("站点数据") }
            )
            FilterChip(
                selected = mode == 1,
                onClick = { mode = 1 },
                label = { Text("全局数据") }
            )
        }
        HorizontalDivider()
        Box(Modifier.weight(1f)) {
            if (mode == 0) SiteBackupSection(controller) else GlobalBackupSection(controller)
        }
    }
}

/** 站点级 Cookie / localStorage 备份 */
@Composable
private fun SiteBackupSection(controller: BrowserController) {
    val context = LocalContext.current
    val app = context.applicationContext as App
    val scope = rememberCoroutineScope()

    var backups by remember { mutableStateOf<List<BackupInfo>>(emptyList()) }
    var pendingDelete by remember { mutableStateOf<BackupInfo?>(null) }
    var pendingRestore by remember { mutableStateOf<BackupInfo?>(null) }
    var showScopeInfo by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var autoBackup by remember { mutableStateOf(app.settings.autoBackup) }
    var locationLabel by remember { mutableStateOf(app.backups.locationLabel()) }
    var usesCustom by remember { mutableStateOf(app.backups.usesCustomDir) }

    fun refreshList() {
        scope.launch { backups = app.backups.listBackups() }
        locationLabel = app.backups.locationLabel()
        usesCustom = app.backups.usesCustomDir
    }

    val dirLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            message = if (app.backups.setCustomDir(uri)) "已切换备份目录" else "无法获取该目录写入权限"
            refreshList()
        }
    }

    LaunchedEffect(Unit) { backups = app.backups.listBackups() }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val text = runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)
                            ?.bufferedReader()?.use { it.readText() }
                    }
                }.getOrNull()
                if (text.isNullOrBlank()) {
                    message = "读取 JSON 文件失败"
                } else {
                    controller.restoreSiteData(text) { _, msg -> message = msg }
                }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text("备份目录", fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(
                locationLabel,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(Modifier.padding(top = 4.dp)) {
                OutlinedButton(
                    onClick = { dirLauncher.launch(null) },
                    modifier = Modifier.height(36.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp)
                ) { Text("选择目录", fontSize = 13.sp) }
                if (usesCustom) {
                    Spacer(Modifier.width(10.dp))
                    OutlinedButton(
                        onClick = {
                            app.backups.resetToDefault()
                            refreshList()
                            message = "已恢复应用私有备份目录"
                        },
                        modifier = Modifier.height(36.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp)
                    ) { Text("恢复默认", fontSize = 13.sp) }
                }
            }
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = {
                    controller.backupCurrent { success, msg ->
                        message = msg
                        if (success) refreshList()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("备份当前页面") }
            Spacer(Modifier.height(6.dp))
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("从 JSON 恢复当前站点数据") }
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
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "备份范围：Cookie、localStorage、IndexedDB",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { showScopeInfo = true },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = "备份范围说明",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        HorizontalDivider()
        if (backups.isEmpty()) {
            EmptyHint("还没有备份。点击上方按钮备份当前页面。")
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(backups, key = { it.name }) { info ->
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

    if (showScopeInfo) {
        AlertDialog(
            onDismissRequest = { showScopeInfo = false },
            title = { Text("备份范围说明") },
            text = {
                Column {
                    Text("备份包含以下站点数据：", fontWeight = FontWeight.Medium)
                    Text("· Cookie：登录态与会话")
                    Text("· localStorage：键值对缓存")
                    Text("· IndexedDB：库结构（objectStore、keyPath、自增、索引）与全部记录")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "同一站点只保留最新一份，旧备份自动删除。恢复时会覆盖当前站点对应的 Cookie、localStorage 与 IndexedDB，并刷新页面。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showScopeInfo = false }) { Text("知道了") }
            }
        )
    }

    pendingRestore?.let { info ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("恢复备份？") },
            text = {
                Text("会用「${info.host}」的备份覆盖当前页面站点的 Cookie、localStorage 与 IndexedDB，并刷新页面。请确认当前打开的是对应网站。")
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

/** 全局数据（设置、书签、首页快捷入口、历史、本地网页记录）备份 */
@Composable
private fun GlobalBackupSection(controller: BrowserController) {
    val context = LocalContext.current
    val app = context.applicationContext as App
    val scope = rememberCoroutineScope()

    var items by remember { mutableStateOf<List<BackupInfo>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var autoBackup by remember { mutableStateOf(app.settings.autoBackupGlobal) }
    var keep by remember { mutableStateOf(app.settings.globalBackupKeep) }
    var pendingDelete by remember { mutableStateOf<BackupInfo?>(null) }
    var pendingRestore by remember { mutableStateOf<BackupInfo?>(null) }

    LaunchedEffect(Unit) {
        items = app.globalBackups.list()
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
            Text("全局数据备份", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(
                "包含设置、书签与文件夹、首页快捷入口、浏览历史、本地网页记录；不含网页内部的 Cookie / 缓存数据。",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("退出或切后台时自动备份", fontSize = 13.sp)
                    Text(
                        "自动备份间隔至少 10 分钟",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = autoBackup,
                    onCheckedChange = {
                        autoBackup = it
                        app.settings.autoBackupGlobal = it
                    }
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("保留最新份数", fontSize = 13.sp)
                    Text(
                        "下次备份时清理超出的旧备份",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = {
                        if (keep > 1) {
                            keep -= 1
                            app.settings.globalBackupKeep = keep
                        }
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Filled.Remove,
                        contentDescription = "减少",
                        modifier = Modifier.size(18.dp)
                    )
                }
                Box(Modifier.width(30.dp), contentAlignment = Alignment.Center) {
                    Text("$keep", fontSize = 14.sp)
                }
                IconButton(
                    onClick = {
                        if (keep < 50) {
                            keep += 1
                            app.settings.globalBackupKeep = keep
                        }
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "增加",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Button(
                onClick = {
                    if (!busy) {
                        busy = true
                        scope.launch {
                            val result = app.globalBackups.backupNow()
                            busy = false
                            message = result.getOrElse { it.message ?: "备份失败" }
                            if (result.isSuccess) items = app.globalBackups.list()
                        }
                    }
                },
                enabled = !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Text(if (busy) "正在备份…" else "立即备份全局数据")
            }
            message?.let {
                Text(
                    it,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
        HorizontalDivider()
        if (items.isEmpty()) {
            EmptyHint("还没有全局数据备份。")
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(items, key = { it.name }) { info ->
                    ListItem(
                        headlineContent = {
                            Text(BackupManager.formatTime(info.createdAt), fontSize = 13.sp)
                        },
                        supportingContent = {
                            Text(
                                "全局数据 · ${BackupManager.formatSize(info.sizeBytes)}",
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
            title = { Text("删除这份全局备份？") },
            text = { Text(BackupManager.formatTime(info.createdAt)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    scope.launch {
                        val ok = app.globalBackups.delete(info)
                        items = app.globalBackups.list()
                        message = if (ok) "备份已删除" else "删除失败"
                    }
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
            title = { Text("恢复全局数据？") },
            text = {
                Text("当前的设置、书签与文件夹、首页快捷入口、历史记录都会被这份备份覆盖，此操作不可撤销。")
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    busy = true
                    scope.launch {
                        val result = app.globalBackups.restore(info)
                        busy = false
                        message = result.getOrElse { it.message ?: "恢复失败" }
                        if (result.isSuccess) {
                            ThemeController.set(context, app.settings.appTheme)
                            controller.applySettings()
                            controller.applyFollowPageTheme(app.settings.followPageTheme)
                            autoBackup = app.settings.autoBackupGlobal
                        }
                    }
                }) { Text("恢复") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestore = null }) { Text("取消") }
            }
        )
    }
}

// 设置页已拆分到 SettingsPages.kt（目录 + 子页）

private data class ImportedBookmark(
    val title: String,
    val url: String,
    val folderPath: List<String>
)

private fun htmlEscape(value: String): String = value
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")

private fun htmlUnescape(value: String): String = value
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&amp;", "&")

/** 生成 Netscape 书签 HTML，可被 Chrome / 系统浏览器导入 */
private fun buildBookmarkHtml(
    bookmarks: List<com.qingguang.browser.data.Bookmark>,
    folders: List<com.qingguang.browser.data.BookmarkFolder>
): String {
    val sb = StringBuilder()
    sb.append("<!DOCTYPE NETSCAPE-Bookmark-file-1>\n")
    sb.append("<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">\n")
    sb.append("<TITLE>Bookmarks</TITLE>\n<H1>Bookmarks</H1>\n<DL><p>\n")
    fun walk(parentId: Long, depth: Int) {
        val pad = "    ".repeat(depth)
        bookmarks.filter { it.folderId == parentId }.forEach { bookmark ->
            sb.append(pad).append("<DT><A HREF=\"")
                .append(htmlEscape(bookmark.url)).append("\" ADD_DATE=\"")
                .append(bookmark.createdAt / 1000).append("\">")
                .append(htmlEscape(bookmark.title)).append("</A>\n")
        }
        folders.filter { it.parentId == parentId }.sortedBy { it.createdAt }.forEach { folder ->
            sb.append(pad).append("<DT><H3>").append(htmlEscape(folder.name)).append("</H3>\n")
            sb.append(pad).append("<DL><p>\n")
            walk(folder.id, depth + 1)
            sb.append(pad).append("</DL><p>\n")
        }
    }
    walk(0L, 0)
    sb.append("</DL><p>\n")
    return sb.toString()
}

/** 解析 Netscape 书签 HTML，按 H3 / DL 层级还原文件夹 */
private fun parseBookmarkHtml(text: String): List<ImportedBookmark> {
    val result = mutableListOf<ImportedBookmark>()
    val stack = mutableListOf<String>()
    var pendingFolder: String? = null
    val h3Regex = Regex("<H3[^>]*>(.*?)</H3>", RegexOption.IGNORE_CASE)
    val anchorRegex = Regex(
        "<A\\s+[^>]*?HREF\\s*=\\s*[\"']([^\"']*)[\"'][^>]*>(.*?)</A>",
        RegexOption.IGNORE_CASE
    )
    val dlOpenRegex = Regex("<DL[^>]*>", RegexOption.IGNORE_CASE)
    val dlCloseRegex = Regex("</DL>", RegexOption.IGNORE_CASE)
    text.lineSequence().forEach { raw ->
        val line = raw.trim()
        if (line.isEmpty()) return@forEach
        val h3 = h3Regex.find(line)
        if (h3 != null) {
            val name = htmlUnescape(h3.groupValues[1]).trim()
            pendingFolder = name
            val rest = line.substring(h3.range.last + 1)
            if (dlOpenRegex.containsMatchIn(rest)) {
                if (name.isNotEmpty()) stack.add(name)
                pendingFolder = null
            }
            return@forEach
        }
        val dlOpen = dlOpenRegex.find(line)
        if (dlOpen != null) {
            pendingFolder?.let { if (it.isNotEmpty()) stack.add(it) }
            pendingFolder = null
            if (dlOpen.range.first > 0) return@forEach
        }
        if (dlCloseRegex.containsMatchIn(line)) {
            if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
            return@forEach
        }
        val anchor = anchorRegex.find(line) ?: return@forEach
        val url = htmlUnescape(anchor.groupValues[1]).trim()
        if (url.isEmpty()) return@forEach
        val title = htmlUnescape(anchor.groupValues[2]).trim().ifBlank { url }
        result.add(ImportedBookmark(title, url, stack.toList()))
    }
    return result
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

/** 权限中心：每个站点一份权限矩阵，浏览器不替用户做决定 */
@Composable
private fun PermissionCenterPage(controller: BrowserController) {
    val context = LocalContext.current
    val app = context.applicationContext as App
    var refreshKey by remember { mutableStateOf(0) }
    val permissions = remember(refreshKey) { SitePermissionStore.load(app.settings) }
    var editing by remember { mutableStateOf<SitePermission?>(null) }
    var showSiteLogs by remember { mutableStateOf(false) }
    val siteLogsAll by app.database.auditLogDao()
        .observeAll()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var showAdd by remember { mutableStateOf(false) }
    var newHost by remember { mutableStateOf("") }
    val currentHost = hostOfText(controller.currentUrl)
    val currentPolicy = remember(refreshKey, currentHost) {
        if (currentHost.isBlank()) null
        else SitePermissionStore.policyFor(app.settings, currentHost)
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            "新站点首次访问默认全部允许，不弹窗、不打扰；需要时可在下方逐站自定义。",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (currentHost.isBlank()) "当前没有打开网页" else "当前站点：$currentHost",
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                currentPolicy?.let {
                    Text(
                        it.summary(),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (currentPolicy != null) {
                OutlinedButton(
                    onClick = { editing = currentPolicy },
                    modifier = Modifier.height(36.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp)
                ) { Text("配置", fontSize = 13.sp) }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = {
                newHost = currentHost
                showAdd = true
            }) { Text("添加站点") }
            Spacer(Modifier.width(10.dp))
            if (currentHost.isNotBlank() && currentPolicy != null &&
                permissions.none { it.host == currentHost }
            ) {
                TextButton(onClick = {
                    SitePermissionStore.upsert(app.settings, currentPolicy)
                    refreshKey++
                }) { Text("保存当前站点", fontSize = 13.sp) }
            }
            if (currentHost.isNotBlank()) {
                TextButton(onClick = { showSiteLogs = true }) {
                    Text("最近操作", fontSize = 13.sp)
                }
            }
        }
        if (currentHost.isNotBlank()) {
            val sessionGranted = controller.sessionGrantedHosts().contains(currentHost.lowercase())
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (sessionGranted) "该站点本次会话已全部放行（重启应用后失效）"
                    else "临时放行只影响本次会话，不改动持久设置",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = {
                    if (sessionGranted) controller.revokeSessionPermissions(currentHost)
                    else controller.grantSessionPermissions(currentHost)
                    refreshKey++
                }) {
                    Text(
                        if (sessionGranted) "取消放行" else "本次会话全部允许",
                        fontSize = 12.sp
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (permissions.isEmpty()) {
            EmptyHint("还没有单独配置的站点。\n新增后，可分别关闭定位、摄像头、弹窗、下载、脚本或图片。")
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(permissions, key = { it.host }) { permission ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { editing = permission }
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                permission.host,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                permission.summary(),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = {
                            SitePermissionStore.remove(app.settings, permission.host)
                            refreshKey++
                        }) { Text("移除") }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }

    if (showSiteLogs) {
        val siteLogs = siteLogsAll.filter { it.host == currentHost }.take(60)
        AlertDialog(
            onDismissRequest = { showSiteLogs = false },
            title = { Text("$currentHost 的最近操作") },
            text = {
                if (siteLogs.isEmpty()) {
                    Text("该站点还没有操作记录。")
                } else {
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(siteLogs, key = { it.id }) { log ->
                            AuditLogRow(log)
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSiteLogs = false }) { Text("关闭") }
            }
        )
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("添加站点") },
            text = {
                Column {
                    Text("填写域名，例如 example.com", fontSize = 12.sp)
                    Text(
                        "支持通配：.example.com 覆盖其全部子域",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = newHost,
                        onValueChange = { newHost = it },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val host = hostOfText(newHost)
                        if (host.isBlank()) {
                            Toast.makeText(context, "请填写域名", Toast.LENGTH_SHORT).show()
                        } else {
                            SitePermissionStore.upsert(app.settings, SitePermission(host = host))
                            refreshKey++
                            showAdd = false
                        }
                    }
                ) { Text("添加") }
            },
            dismissButton = {
                TextButton(onClick = { showAdd = false }) { Text("取消") }
            }
        )
    }

    editing?.let { origin ->
        var draft by remember(origin) { mutableStateOf(origin) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(origin.host, fontSize = 16.sp) },
            text = {
                Column {
                    PermissionSwitch("定位", draft.allowGeo) { draft = draft.copy(allowGeo = it) }
                    PermissionSwitch("摄像头 / 麦克风", draft.allowCamera) {
                        draft = draft.copy(allowCamera = it)
                    }
                    PermissionSwitch("网页弹窗", draft.allowPopup) { draft = draft.copy(allowPopup = it) }
                    PermissionSwitch("下载文件", draft.allowDownload) {
                        draft = draft.copy(allowDownload = it)
                    }
                    PermissionSwitch("JavaScript", draft.allowJs) { draft = draft.copy(allowJs = it) }
                    PermissionSwitch("加载图片", draft.allowImages) {
                        draft = draft.copy(allowImages = it)
                    }
                    Text(
                        "夜间网页调色",
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(-1 to "跟随全局", 0 to "关闭", 1 to "开启").forEach { (value, label) ->
                            FilterChip(
                                selected = draft.nightMode == value,
                                onClick = { draft = draft.copy(nightMode = value) },
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    SitePermissionStore.upsert(app.settings, draft)
                    refreshKey++
                    editing = null
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = {
                    SitePermissionStore.remove(app.settings, origin.host)
                    refreshKey++
                    editing = null
                }) { Text("恢复默认") }
            }
        )
    }
}

@Composable
private fun PermissionSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** 操作记录：权限放行/拦截、下载等关键动作的审计流水 */
@Composable
private fun AuditLogPage() {
    val context = LocalContext.current
    val app = context.applicationContext as App
    val scope = rememberCoroutineScope()
    val logs by app.database.auditLogDao()
        .observeAll()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var typeFilter by remember { mutableStateOf("全部") }
    var keyword by remember { mutableStateOf("") }
    var exportHint by remember { mutableStateOf<String?>(null) }

    val filtered = logs.filter { log ->
        val typeOk = when (typeFilter) {
            "权限" -> log.action in setOf("定位", "摄像头/麦克风", "弹窗", "脚本", "图片")
            "下载" -> log.action == "下载"
            "站点数据" -> log.action == "站点数据"
            else -> true
        }
        val key = keyword.trim()
        val keyOk = key.isBlank() || log.host.contains(key, ignoreCase = true) ||
            log.detail.contains(key, ignoreCase = true)
        typeOk && keyOk
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val csv = buildString {
                append('\uFEFF')
                append("时间,类型,结果,站点,详情\r\n")
                filtered.forEach { log ->
                    append(csvCell(timeFormat.format(Date(log.createdAt))))
                    append(',').append(csvCell(log.action))
                    append(',').append(csvCell(log.result))
                    append(',').append(csvCell(log.host))
                    append(',').append(csvCell(log.detail))
                    append("\r\n")
                }
            }
            exportHint = runCatching {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(csv.toByteArray(Charsets.UTF_8))
                } ?: error("无法写入")
            }.fold(
                onSuccess = { "已导出 ${filtered.size} 条操作记录" },
                onFailure = { "导出失败：${it.message}" }
            )
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "共 ${filtered.size} 条；无痕模式下的操作不记录",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = {
                    exportLauncher.launch(
                        "操作记录-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault())
                            .format(Date()) + ".csv"
                    )
                }
            ) { Text("导出") }
            TextButton(onClick = {
                scope.launch { app.database.auditLogDao().clearAll() }
            }) { Text("清空") }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("全部", "权限", "下载", "站点数据").forEach { type ->
                FilterChip(
                    selected = typeFilter == type,
                    onClick = { typeFilter = type },
                    label = { Text(type, fontSize = 12.sp) }
                )
            }
        }
        OutlinedTextField(
            value = keyword,
            onValueChange = { keyword = it },
            placeholder = { Text("按站点或详情筛选", fontSize = 12.sp) },
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 4.dp)
        )
        exportHint?.let {
            Text(
                it,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp)
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (filtered.isEmpty()) {
            EmptyHint(if (logs.isEmpty()) "暂无操作记录。" else "没有符合筛选条件的记录。")
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(filtered, key = { it.id }) { log ->
                    AuditLogRow(log)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

private fun csvCell(value: String): String {
    val text = value.replace("\"", "\"\"")
    return "\"$text\""
}

@Composable
private fun AuditLogRow(log: AuditLog) {
    val time = remember(log.id) {
        SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date(log.createdAt))
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                log.action,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.width(8.dp))
            Text(
                log.result,
                fontSize = 11.sp,
                color = if (log.result == "允许" || log.result == "开始" || log.result == "完成") {
                    Color(0xFF3E8E52)
                } else {
                    MaterialTheme.colorScheme.error
                }
            )
            Spacer(Modifier.weight(1f))
            Text(
                time,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (log.host.isNotBlank()) {
            Text(
                log.host,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (log.detail.isNotBlank()) {
            Text(
                log.detail,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun hostOfText(url: String): String {
    val text = url.trim()
    if (text.isBlank()) return ""
    return runCatching {
        val withoutScheme = text.substringAfter("://", text)
        withoutScheme.substringBefore('/').substringBefore('?').substringBefore('#')
            .substringAfterLast('@').substringBefore(':').lowercase()
    }.getOrDefault("")
}

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
