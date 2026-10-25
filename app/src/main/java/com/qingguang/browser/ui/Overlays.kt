package com.qingguang.browser.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qingguang.browser.App
import com.qingguang.browser.BuildConfig
import com.qingguang.browser.ui.theme.ThemeController
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
                OverlayPage.DATA_BACKUPS -> BackupsPage(controller)
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
            if (subFolders.isEmpty() && itemsHere.isEmpty()) {
                EmptyHint(
                    if (chain.isEmpty()) "还没有书签。打开网页后，从菜单里点「添加书签」。"
                    else "这个文件夹还是空的，点右上角可以新建子文件夹。"
                )
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(subFolders, key = { "f-${it.id}" }) { folder ->
                        BookmarkFolderRow(
                            folder = folder,
                            count = bookmarks.count { it.folderId == folder.id },
                            onClick = { openFolder(folder.id) },
                            onLongClick = { folderAction = folder }
                        )
                    }
                    items(itemsHere, key = { "b-${it.id}" }) { bookmark ->
                        BookmarkListRow(
                            bookmark = bookmark,
                            onClick = {
                                controller.loadUrl(bookmark.url)
                                onClose()
                            },
                            onLongClick = { bookmarkAction = bookmark },
                            onDelete = { deleteBookmark = bookmark }
                        )
                    }
                    item { Spacer(Modifier.height(12.dp)) }
                }
            }
        }
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
                        scope.launch {
                            app.database.bookmarkFolderDao().insert(
                                com.qingguang.browser.data.BookmarkFolder(
                                    name = newFolderName.trim(),
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
                            dao.insert(
                                com.qingguang.browser.data.HomeItem(
                                    title = bookmark.title,
                                    url = bookmark.url
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
        Box(
            Modifier
                .size(34.dp)
                .background(Color(0xFFE8F0FE), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Folder,
                contentDescription = null,
                tint = Color(0xFF2E7CF6),
                modifier = Modifier.size(19.dp)
            )
        }
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
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .pointerInput(bookmark.id) {
                detectTapGestures(onTap = { onClick() }, onLongPress = { onLongClick() })
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(34.dp)
                .background(tabCircleColor(bookmark.title.ifBlank { bookmark.url }), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = tabInitial(bookmark.title, bookmark.url),
                fontSize = 14.sp,
                color = Color.White
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
        IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "删除书签",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
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

// 设置页已拆分到 SettingsPages.kt（目录 + 子页）

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
