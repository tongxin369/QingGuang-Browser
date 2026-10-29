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
import com.qingguang.browser.web.StorageEntry
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.detectReorderAfterLongPress
import org.burnoutcrew.reorderable.reorderable
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
                OverlayPage.PAGE_DIAGNOSTICS -> PageDiagnosticsPage(controller)
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
    var moveSelectionOpen by remember { mutableStateOf(false) }
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
    val bookmarkListState = androidx.compose.foundation.lazy.rememberLazyListState()
    // 整理模式下的本地顺序（拖动时先改本地顺序，松手后再落库）
    val sortItems = remember { mutableStateListOf<com.qingguang.browser.data.Bookmark>() }
    val reorderState = org.burnoutcrew.reorderable.rememberReorderableLazyListState(
        onMove = { from, to ->
            val fromKey = from.key as? String
            val toKey = to.key as? String
            if (fromKey != null && toKey != null) {
                val fromIndex = sortItems.indexOfFirst { "b-${it.id}" == fromKey }
                val toIndex = sortItems.indexOfFirst { "b-${it.id}" == toKey }
                if (fromIndex >= 0 && toIndex >= 0) {
                    sortItems.add(toIndex, sortItems.removeAt(fromIndex))
                }
            }
        }
    )

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

    // 整理模式：把当前文件夹的顺序同步到本地列表；松手后按本地顺序落库
    val orderKey = visibleBookmarks.joinToString(",") { it.id.toString() }
    LaunchedEffect(sortMode, orderKey) {
        if (sortMode && reorderState.draggingItemIndex == null) {
            sortItems.clear()
            sortItems.addAll(visibleBookmarks)
        }
    }
    LaunchedEffect(reorderState.draggingItemIndex) {
        if (sortMode && reorderState.draggingItemIndex == null) {
            // 先拷成不可变快照：写库是挂起操作，期间拖动/重排会修改 sortItems，
            // 直接迭代会抛 ConcurrentModificationException（拖到屏幕顶部触发自动滚动时必现）
            val ids = sortItems.map { it.id }
            ids.forEachIndexed { i, id ->
                app.database.bookmarkDao().updateSortOrder(id, i + 1)
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
                TextButton(onClick = {
                    if (selectedIds.isNotEmpty()) moveSelectionOpen = true
                }) { Text("移动") }
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
                    modifier = if (sortMode) {
                        Modifier
                            .fillMaxSize()
                            .reorderable(reorderState)
                    } else Modifier.fillMaxSize(),
                    state = if (sortMode) reorderState.listState else bookmarkListState
                ) {
                    items(shownFolders, key = { "f-${it.id}" }) { folder ->
                        BookmarkFolderRow(
                            folder = folder,
                            count = bookmarks.count { it.folderId == folder.id },
                            onClick = { openFolder(folder.id) },
                            onLongClick = { folderAction = folder }
                        )
                    }
                    itemsIndexed(
                        if (sortMode) sortItems else visibleBookmarks,
                        key = { _, it -> "b-${it.id}" }
                    ) { _, bookmark ->
                        val rowContent: @Composable (Modifier) -> Unit = { extra ->
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
                                trailingHint = if (sortMode) "拖动" else null,
                                modifier = extra
                            )
                        }
                        if (sortMode) {
                            ReorderableItem(reorderState, key = "b-${bookmark.id}") { isDragging ->
                                Box(
                                    Modifier.graphicsLayer {
                                        if (isDragging) {
                                            scaleX = 1.02f
                                            scaleY = 1.02f
                                        }
                                    }
                                ) {
                                    rowContent(
                                        Modifier
                                            .background(MaterialTheme.colorScheme.surface)
                                            .detectReorderAfterLongPress(reorderState)
                                    )
                                }
                            }
                        } else {
                            rowContent(Modifier)
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

    if (moveSelectionOpen) {
        val flatFolders = flattenFolders(folders)
        fun moveSelectedTo(folderId: Long) {
            val ids = selectedIds.toList()
            scope.launch {
                ids.forEach { app.database.bookmarkDao().moveToFolder(it, folderId) }
                message = "已移动 ${ids.size} 个书签"
            }
            selectedIds.clear()
            selecting = false
            moveSelectionOpen = false
        }
        AlertDialog(
            onDismissRequest = { moveSelectionOpen = false },
            title = { Text("移动 ${selectedIds.size} 个书签到") },
            text = {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    item {
                        FolderChoiceRow("未分类", selected = false) { moveSelectedTo(0L) }
                    }
                    items(flatFolders, key = { it.first.id }) { (folder, depth) ->
                        FolderChoiceRow("    ".repeat(depth) + folder.name, selected = false) {
                            moveSelectedTo(folder.id)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { moveSelectionOpen = false }) { Text("取消") }
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
    trailingHint: String? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .fillMaxWidth()
            .pointerInput(bookmark.id, selectionMode, trailingHint) {
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
    var failedHint by remember { mutableStateOf<com.qingguang.browser.data.DownloadRecord?>(null) }
    var confirmClearFinished by remember { mutableStateOf(false) }
    var subDir by remember { mutableStateOf(app.settings.downloadSubDir) }
    // 多选删除：进入选择模式后点按行勾选，统一删除
    var selectionMode by remember { mutableStateOf(false) }
    val selectedIds = remember { androidx.compose.runtime.mutableStateListOf<Long>() }
    var typeFilter by remember { mutableStateOf("全部") }

    fun categoryOf(record: com.qingguang.browser.data.DownloadRecord): String {
        val ext = record.fileName.substringAfterLast('.', "").lowercase()
        val mime = record.mimeType.lowercase()
        return when {
            ext in setOf("pdf", "doc", "docx", "txt", "md", "epub", "xls", "xlsx", "ppt", "pptx", "csv") ||
                mime.startsWith("text/") -> "文档"
            ext in setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg") ||
                mime.startsWith("image/") -> "图片"
            ext in setOf("mp3", "wav", "m4a", "aac", "ogg", "flac", "mp4", "mkv", "webm", "mov", "3gp", "avi") ||
                mime.startsWith("audio/") || mime.startsWith("video/") -> "音视频"
            ext in setOf("zip", "rar", "7z", "tar", "gz", "apk") -> "压缩包"
            else -> "其他"
        }
    }

    val visible = remember(downloads, typeFilter) {
        if (typeFilter == "全部") downloads else downloads.filter { categoryOf(it) == typeFilter }
    }

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
        // 让用户自选打开方式（浏览器 / 阅读器 / 播放器 / 文件管理器）
        runCatching { context.startActivity(Intent.createChooser(intent, "选择打开方式")) }
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
            if (downloads.any { it.status == "下载完成" }) {
                TextButton(onClick = { confirmClearFinished = true }) {
                    Text("清除已完成", fontSize = 12.sp)
                }
            }
            TextButton(onClick = {
                selectionMode = !selectionMode
                if (!selectionMode) selectedIds.clear()
            }) { Text(if (selectionMode) "完成" else "选择", fontSize = 12.sp) }
        }
        if (selectionMode) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "已选 ${selectedIds.size} 项",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = {
                    if (selectedIds.size == visible.size) selectedIds.clear()
                    else {
                        selectedIds.clear()
                        selectedIds.addAll(visible.map { it.id })
                    }
                }) { Text(if (selectedIds.size == visible.size) "取消全选" else "全选", fontSize = 12.sp) }
                TextButton(
                    enabled = selectedIds.isNotEmpty(),
                    onClick = {
                        val ids = selectedIds.toList()
                        ids.forEach { id ->
                            val record = downloads.firstOrNull { it.id == id } ?: return@forEach
                            if (record.managerId > 0 && record.status != "下载完成") {
                                runCatching { manager.remove(record.managerId) }
                            }
                            scope.launch { app.database.downloadDao().delete(id) }
                        }
                        selectedIds.clear()
                        selectionMode = false
                        Toast.makeText(context, "已删除 ${ids.size} 条记录", Toast.LENGTH_SHORT)
                            .show()
                    }
                ) { Text("删除所选", fontSize = 12.sp) }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            "支持暂停 / 继续 / 重试；点击文件可直接打开。下载由系统下载管理器完成。",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        // 按类型筛选：文档 / 图片 / 音视频 / 压缩包 / 其他
        if (downloads.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("全部", "文档", "图片", "音视频", "压缩包", "其他").forEach { label ->
                    FilterChip(
                        selected = typeFilter == label,
                        onClick = { typeFilter = label },
                        label = { Text(label, fontSize = 11.sp) }
                    )
                }
            }
        }
        if (visible.isEmpty()) {
            EmptyHint(if (downloads.isEmpty()) "还没有下载记录。" else "当前分类下没有下载记录。")
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(visible, key = { it.id }) { record ->
                    val progress = progressMap[record.id]
                    val running = progress?.status == DownloadManager.STATUS_RUNNING
                    val paused = progress?.status == DownloadManager.STATUS_PAUSED
                    val finished = record.status == "下载完成"
                    val failed = record.status == "下载失败"
                    val total = progress?.total?.takeIf { it > 0 } ?: record.totalBytes
                    val bytes = progress?.bytes ?: if (finished) total else 0L
                    Column(Modifier.fillMaxWidth()) {
                        ListItem(
                            leadingContent = if (selectionMode) {
                                {
                                    androidx.compose.material3.Checkbox(
                                        checked = selectedIds.contains(record.id),
                                        onCheckedChange = { checked ->
                                            if (checked) selectedIds.add(record.id)
                                            else selectedIds.remove(record.id)
                                        }
                                    )
                                }
                            } else null,
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
                                if (selectionMode) {
                                    if (selectedIds.contains(record.id)) selectedIds.remove(record.id)
                                    else selectedIds.add(record.id)
                                } else when {
                                    finished || record.localUri != null -> openRecord(record)
                                    failed -> failedHint = record
                                }
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

    failedHint?.let { record ->
        AlertDialog(
            onDismissRequest = { failedHint = null },
            title = { Text("下载失败") },
            text = {
                Column {
                    Text(
                        record.fileName,
                        fontSize = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "常见原因：\n· 链接已过期或需要登录（重新进入网页后再下载）\n" +
                            "· 存储空间不足\n· 网络中断或站点拒绝访问\n\n" +
                            "可以点「重试」重新入队，或回到网页刷新后重新下载。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    controller.retryDownload(record.url, record.fileName, record.mimeType)
                    scope.launch { app.database.downloadDao().delete(record.id) }
                    failedHint = null
                }) { Text("重试") }
            },
            dismissButton = {
                TextButton(onClick = { failedHint = null }) { Text("关闭") }
            }
        )
    }

    if (confirmClearFinished) {
        AlertDialog(
            onDismissRequest = { confirmClearFinished = false },
            title = { Text("清除已完成的下载记录？") },
            text = { Text("只清理下载中心里的记录，已下载的文件仍保留在下载目录。") },
            confirmButton = {
                TextButton(onClick = {
                    val finishedIds = downloads
                        .filter { it.status == "下载完成" }
                        .map { it.id }
                    scope.launch {
                        finishedIds.forEach { app.database.downloadDao().delete(it) }
                    }
                    confirmClearFinished = false
                }) { Text("清除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearFinished = false }) { Text("取消") }
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
        // 本次会话内已放行的站点：全部权限临时放行 + 「每次询问」逐次确认的记录，均可单独撤销
        val sessionGrants = controller.sessionGrantedHosts()
        val mediaGrants = controller.mediaGrantedHosts()
        if (sessionGrants.isNotEmpty() || mediaGrants.isNotEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 4.dp)
            ) {
                Text(
                    "本次会话已授权（重启应用后清空）",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                sessionGrants.forEach { host ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(host, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "全部权限临时放行",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = {
                            controller.revokeSessionPermissions(host)
                            refreshKey++
                        }) { Text("撤销", fontSize = 12.sp) }
                    }
                }
                mediaGrants.filter { it !in sessionGrants }.forEach { host ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(host, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "麦克风/摄像头「每次询问」已放行",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = {
                            controller.revokeMediaSessionGrant(host)
                            refreshKey++
                        }) { Text("撤销", fontSize = 12.sp) }
                    }
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
                            if (permission.expiresAt > 0) {
                                Text(
                                    if (permission.isExpired()) "已过期（当前按默认策略）"
                                    else "有效期至 " + SimpleDateFormat(
                                        "yyyy-MM-dd HH:mm",
                                        Locale.getDefault()
                                    ).format(Date(permission.expiresAt)),
                                    fontSize = 11.sp,
                                    color = if (permission.isExpired())
                                        MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                        TextButton(onClick = {
                            val before = SitePermissionStore.load(app.settings)
                                .firstOrNull { it.host == permission.host }?.summary().orEmpty()
                            SitePermissionStore.remove(app.settings, permission.host)
                            controller.auditPermissionChange(permission.host, before, "")
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
                            val added = SitePermission(host = host)
                            SitePermissionStore.upsert(app.settings, added)
                            controller.auditPermissionChange(host, "", added.summary())
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
        var history by remember(origin.host, refreshKey) {
            mutableStateOf<List<com.qingguang.browser.data.AuditLog>>(emptyList())
        }
        LaunchedEffect(origin.host, refreshKey) {
            history = app.database.auditLogDao().byHost(origin.host)
        }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(origin.host, fontSize = 16.sp) },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    PermissionSwitch("定位", draft.allowGeo) { draft = draft.copy(allowGeo = it) }
                    PermissionSwitch("摄像头", draft.allowCamera) {
                        draft = draft.copy(allowCamera = it)
                    }
                    PermissionSwitch("麦克风", draft.allowMic) {
                        draft = draft.copy(allowMic = it)
                    }
                    PermissionSwitch("麦克风/摄像头每次询问", draft.askEachTime) {
                        draft = draft.copy(askEachTime = it)
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
                        "授权有效期",
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    val dayMs = 24L * 60 * 60 * 1000
                    val validityOptions = listOf(
                        0L to "永久",
                        7L * dayMs to "7 天",
                        30L * dayMs to "30 天"
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        validityOptions.forEach { (duration, label) ->
                            val selected = if (duration == 0L) {
                                draft.expiresAt == 0L
                            } else {
                                draft.expiresAt > System.currentTimeMillis() &&
                                    draft.expiresAt - System.currentTimeMillis() <= duration + dayMs
                            }
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    draft = draft.copy(
                                        expiresAt = if (duration == 0L) 0L
                                        else System.currentTimeMillis() + duration
                                    )
                                },
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                    }
                    Text(
                        when {
                            draft.expiresAt == 0L -> "永久生效，直到手动移除或恢复默认"
                            draft.isExpired() -> "已过期：该站点已回退为默认策略，保存后会重新生效"
                            else -> "有效期至 " +
                                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                                    .format(Date(draft.expiresAt)) +
                                "，到期自动回退为默认策略"
                        },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                    // 该站点的权限变更时间线，只展示最近 8 条
                    if (history.isNotEmpty()) {
                        HorizontalDivider(Modifier.padding(vertical = 10.dp))
                        Text(
                            "该站点的变更记录",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        val fmt = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                        history.take(8).forEach { log ->
                            Text(
                                "${fmt.format(Date(log.createdAt))}  ${log.action}·${log.result}  ${log.detail}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val before = SitePermissionStore.load(app.settings)
                        .firstOrNull { it.host == origin.host }?.summary().orEmpty()
                    SitePermissionStore.upsert(app.settings, draft)
                    controller.auditPermissionChange(origin.host, before, draft.summary())
                    refreshKey++
                    editing = null
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = {
                    val before = SitePermissionStore.load(app.settings)
                        .firstOrNull { it.host == origin.host }?.summary().orEmpty()
                    SitePermissionStore.remove(app.settings, origin.host)
                    controller.auditPermissionChange(origin.host, before, "")
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

/**
 * 页面诊断：把页面脚本报错、资源加载失败、本地存储键都摊开在应用里，
 * 用户不需要会抓 logcat 也能定位「点了没反应」是哪一步断的。
 */
@Composable
private fun PageDiagnosticsPage(controller: BrowserController) {
    val context = LocalContext.current
    var keys by remember { mutableStateOf<List<StorageEntry>>(emptyList()) }
    var refreshKey by remember { mutableStateOf(0) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    var confirmRepair by remember { mutableStateOf(false) }

    LaunchedEffect(refreshKey) {
        controller.readLocalStorage { entries -> keys = entries }
    }

    val logs = controller.diagnostics
    Column(Modifier.fillMaxSize()) {
        Text(
            if (controller.currentUrl.isBlank()) "当前没有打开网页"
            else "当前页面：${controller.currentUrl}",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "页面错误 ${logs.size} 条",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = {
                val text = logs.joinToString("\n")
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                    as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("页面诊断", text))
                Toast.makeText(context, "已复制诊断信息", Toast.LENGTH_SHORT).show()
            }) { Text("复制", fontSize = 12.sp) }
            TextButton(onClick = { controller.clearDiagnostics() }) {
                Text("清空", fontSize = 12.sp)
            }
            TextButton(onClick = { controller.reload() }) { Text("重新加载", fontSize = 12.sp) }
            TextButton(onClick = { confirmRepair = true }) { Text("修复异常值", fontSize = 12.sp) }
        }
        if (logs.isEmpty()) {
            Text(
                "暂无脚本错误或加载失败。若某个按钮点了没反应，先点一次那个按钮，再回到这里查看。",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
            )
        } else {
            LazyColumn(Modifier.heightIn(max = 260.dp)) {
                items(logs) { line ->
                    Text(
                        line,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("站点本地存储", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text(
                    "网页的数据大多保存在这里；本地存储本身不记录写入时间，下面显示的是本次会话首次看到它的时间。",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = { refreshKey++ }) { Text("刷新", fontSize = 12.sp) }
        }
        if (keys.isEmpty()) {
            Text(
                "没有读取到本地存储键（页面未打开或站点未使用 localStorage）。",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(keys, key = { it.key }) { entry ->
                    val key = entry.key
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                key,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "${BackupManager.formatSize(entry.sizeChars.toLong())}（字符数） · " +
                                    "首次发现 " + SimpleDateFormat(
                                        "MM-dd HH:mm:ss",
                                        Locale.getDefault()
                                    ).format(Date(entry.firstSeenMs)),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { pendingDelete = key }) {
                            Text("删除", fontSize = 12.sp)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }

    if (confirmRepair) {
        AlertDialog(
            onDismissRequest = { confirmRepair = false },
            title = { Text("修复异常的本地存储值？") },
            text = {
                Text(
                    "有些网页会把 null / undefined 这类无效值存进本地存储，页面读取时就会报错、" +
                        "点了没反应。这里只会删除值为 null/undefined/NaN 的键，其他数据不动，然后重新加载页面。",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRepair = false
                    controller.repairCorruptedStorage { count ->
                        Toast.makeText(
                            context,
                            if (count > 0) "已修复 $count 个异常值，正在重新加载" else "没有发现异常值",
                            Toast.LENGTH_SHORT
                        ).show()
                        if (count > 0) {
                            controller.reload()
                            refreshKey++
                        }
                    }
                }) { Text("修复并重载") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRepair = false }) { Text("取消") }
            }
        )
    }

    pendingDelete?.let { key ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除这个存储键？") },
            text = {
                Column {
                    Text(key, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "只会删除这一个键，页面会重新加载。若这是网页的全部数据，相关自定义内容可能丢失。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = key
                    pendingDelete = null
                    controller.deleteLocalStorageKey(target) {
                        refreshKey++
                    }
                }) { Text("删除并重载") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
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
    var timeFilter by remember { mutableStateOf("全部") }
    var keyword by remember { mutableStateOf("") }
    var exportHint by remember { mutableStateOf<String?>(null) }

    val now = System.currentTimeMillis()
    val todayStart = remember(now / 60_000) {
        java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    val filtered = logs.filter { log ->
        val typeOk = when (typeFilter) {
            "权限" -> log.action in setOf("权限", "定位", "摄像头/麦克风", "麦克风/摄像头", "弹窗", "脚本", "图片")
            "下载" -> log.action == "下载"
            "站点数据" -> log.action == "站点数据"
            else -> true
        }
        val timeOk = when (timeFilter) {
            "今天" -> log.createdAt >= todayStart
            "近7天" -> log.createdAt >= todayStart - 6 * 24 * 60 * 60 * 1000L
            "近30天" -> log.createdAt >= todayStart - 29 * 24 * 60 * 60 * 1000L
            else -> true
        }
        val key = keyword.trim()
        val keyOk = key.isBlank() || log.host.contains(key, ignoreCase = true) ||
            log.detail.contains(key, ignoreCase = true)
        typeOk && timeOk && keyOk
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
            listOf("全部", "今天", "近7天", "近30天").forEach { range ->
                FilterChip(
                    selected = timeFilter == range,
                    onClick = { timeFilter = range },
                    label = { Text(range, fontSize = 12.sp) }
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
                val dayKeyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val dayLabelFormat = SimpleDateFormat("M月d日 EEEE", Locale.getDefault())
                itemsIndexed(filtered, key = { _, log -> log.id }) { index, log ->
                    // 按天分组显示，长时间线的记录更容易定位
                    val day = dayKeyFormat.format(Date(log.createdAt))
                    val prevDay = filtered.getOrNull(index - 1)
                        ?.let { dayKeyFormat.format(Date(it.createdAt)) }
                    if (day != prevDay) {
                        Text(
                            dayLabelFormat.format(Date(log.createdAt)),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                        )
                    }
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
