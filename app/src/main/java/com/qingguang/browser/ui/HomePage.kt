@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.qingguang.browser.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qingguang.browser.App
import com.qingguang.browser.R
import com.qingguang.browser.data.HomeItem
import com.qingguang.browser.util.LocalSiteManager
import com.qingguang.browser.web.BrowserController
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 第一页最多显示 3 行 × 4 列 = 12 个图标，超出的新图标自动放到第二页 */
internal const val HOME_PAGE_CAPACITY = 12

/** 首页：第 1 页是 Via 风格常驻主页，第 2 页是本地网页模块 + 快捷入口第二页 */
@Composable
fun HomePage(
    controller: BrowserController,
    onOpenOverlay: (OverlayPage) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as App
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 2 })

    val homeItems by app.database.homeItemDao()
        .observeAll()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val rootItems = homeItems.filter { it.parentId == 0L }.sortedBy { it.sortOrder }
    val firstPageItems = rootItems.filter { it.page <= 0 }
    val secondPageItems = rootItems.filter { it.page > 0 }

    var actionTarget by remember { mutableStateOf<HomeItem?>(null) }
    var openFolder by remember { mutableStateOf<HomeItem?>(null) }
    var renameTarget by remember { mutableStateOf<HomeItem?>(null) }
    var renameName by remember { mutableStateOf("") }
    var iconTarget by remember { mutableStateOf<HomeItem?>(null) }

    fun mergeItems(dragged: HomeItem, target: HomeItem, parentId: Long, page: Int) {
        scope.launch {
            val dao = app.database.homeItemDao()
            if (target.isFolder) {
                dao.moveToParent(dragged.id, target.id, 0)
            } else {
                val folderId = dao.insert(
                    HomeItem(
                        title = "分组",
                        isFolder = true,
                        parentId = parentId,
                        page = page,
                        sortOrder = minOf(target.sortOrder, dragged.sortOrder)
                    )
                )
                dao.moveToParent(target.id, folderId, 0)
                dao.moveToParent(dragged.id, folderId, 1)
            }
            toast(context, "已合并为分组")
        }
    }

    Box(Modifier.fillMaxSize()) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            if (page == 0) {
                HomeMainPage(
                    controller = controller,
                    onOpenOverlay = onOpenOverlay,
                    onOpenLocalPages = { scope.launch { pagerState.animateScrollToPage(1) } },
                    items = firstPageItems,
                    onOpenItem = { item ->
                        if (item.isFolder) openFolder = item else controller.loadUrl(item.url)
                    },
                    onLongPress = { actionTarget = it },
                    onMerge = { dragged, target -> mergeItems(dragged, target, 0L, 0) }
                )
            } else {
                HomeSecondPage(
                    controller = controller,
                    items = secondPageItems,
                    onOpenItem = { item ->
                        if (item.isFolder) openFolder = item else controller.loadUrl(item.url)
                    },
                    onLongPress = { actionTarget = it },
                    onMerge = { dragged, target -> mergeItems(dragged, target, 0L, 1) }
                )
            }
        }
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            repeat(2) { index ->
                Box(
                    Modifier
                        .size(if (index == pagerState.currentPage) 6.dp else 5.dp)
                        .background(
                            if (index == pagerState.currentPage) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                            CircleShape
                        )
                )
            }
        }
    }

    actionTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { actionTarget = null },
            title = { Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column {
                    HomeActionRow("打开") {
                        actionTarget = null
                        if (item.isFolder) openFolder = item else controller.loadUrl(item.url)
                    }
                    HomeActionRow("重命名") {
                        actionTarget = null
                        renameTarget = item
                        renameName = item.title
                    }
                    if (item.parentId == 0L) {
                        HomeActionRow(if (item.page == 0) "移到第二页" else "移到第一页") {
                            actionTarget = null
                            val targetPage = if (item.page == 0) 1 else 0
                            scope.launch {
                                val dao = app.database.homeItemDao()
                                dao.moveToPage(
                                    item.id,
                                    targetPage,
                                    dao.countRootOnPage(targetPage)
                                )
                            }
                        }
                    }
                    if (item.isFolder) {
                        HomeActionRow("解散分组") {
                            actionTarget = null
                            scope.launch {
                                val dao = app.database.homeItemDao()
                                homeItems.filter { it.parentId == item.id }.forEach { child ->
                                    dao.moveToParent(child.id, 0L, child.sortOrder)
                                    dao.moveToPage(child.id, item.page, child.sortOrder)
                                }
                                dao.delete(item.id)
                            }
                        }
                    } else {
                        HomeActionRow("自定义图标") {
                            actionTarget = null
                            iconTarget = item
                        }
                        if (item.parentId != 0L) {
                            HomeActionRow("移出分组") {
                                actionTarget = null
                                scope.launch {
                                    val dao = app.database.homeItemDao()
                                    dao.moveToParent(item.id, 0L, item.sortOrder)
                                    val page = if (dao.countRootOnPage(0) < HOME_PAGE_CAPACITY) 0 else 1
                                    dao.moveToPage(item.id, page, item.sortOrder)
                                }
                            }
                        } else {
                            HomeActionRow("移出首页") {
                                actionTarget = null
                                scope.launch { app.database.homeItemDao().delete(item.id) }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { actionTarget = null }) { Text("关闭") }
            }
        )
    }

    openFolder?.let { folder ->
        val children = homeItems.filter { it.parentId == folder.id }.sortedBy { it.sortOrder }
        AlertDialog(
            onDismissRequest = { openFolder = null },
            title = { Text(folder.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                if (children.isEmpty()) {
                    Text("这个分组里还没有图标。", fontSize = 13.sp)
                } else {
                    // 分组内容也使用快捷入口的图标网格样式；面板宽度不变，一行放 3 个
                    Column(
                        Modifier
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        HomeShortcutGrid(
                            items = children,
                            columns = 3,
                            onOpen = { child ->
                                openFolder = null
                                if (child.isFolder) openFolder = child
                                else controller.loadUrl(child.url)
                            },
                            onLongPress = { child ->
                                openFolder = null
                                actionTarget = child
                            },
                            onMerge = { dragged, target -> mergeItems(dragged, target, folder.id, folder.page) }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { openFolder = null }) { Text("关闭") }
            }
        )
    }

    renameTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重命名快捷入口") },
            text = {
                OutlinedTextField(
                    value = renameName,
                    onValueChange = { renameName = it },
                    label = { Text("名称") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            app.database.homeItemDao().rename(item.id, renameName.trim())
                        }
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
    iconTarget?.let { item ->
        IconStyleDialog(
            title = item.title,
            initialColor = item.iconColor,
            initialText = item.iconText,
            onDismiss = { iconTarget = null },
            onSave = { color, text ->
                iconTarget = null
                scope.launch {
                    val homeDao = app.database.homeItemDao()
                    homeDao.updateIcon(item.id, color, text)
                    if (item.url.isNotBlank()) {
                        // 与书签页、历史记录同步同一 URL 的图标样式
                        homeDao.updateIconByUrl(item.url, color, text)
                        app.database.bookmarkDao().updateIconByUrl(item.url, color, text)
                    }
                }
            }
        )
    }
}

@Composable
private fun HomeMainPage(
    controller: BrowserController,
    onOpenOverlay: (OverlayPage) -> Unit,
    onOpenLocalPages: () -> Unit,
    items: List<HomeItem>,
    onOpenItem: (HomeItem) -> Unit,
    onLongPress: (HomeItem) -> Unit,
    onMerge: (HomeItem, HomeItem) -> Unit
) {
    var query by remember { mutableStateOf("") }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "轻光",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { onOpenOverlay(OverlayPage.SETTINGS) }) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = "设置",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        item {
            Spacer(Modifier.height(28.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                // 图标前景自带透明留白，直接放大显示；不额外加白底和描边
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(104.dp)
                )
            }
            Spacer(Modifier.height(24.dp))
        }

        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(Modifier.size(10.dp))
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.secondary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            if (query.isNotBlank()) {
                                controller.submitInput(query)
                                query = ""
                            }
                        }),
                        modifier = Modifier.fillMaxSize(),
                        decorationBox = { inner ->
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                                if (query.isBlank()) {
                                    Text(
                                        "搜索或输入网址",
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                                inner()
                            }
                        }
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        item {
            // 快捷入口：仿桌面图标样式
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                AppIconEntry("书签", Icons.Filled.BookmarkBorder, Color(0xFF5B8C5A)) {
                    onOpenOverlay(OverlayPage.BOOKMARKS)
                }
                AppIconEntry("历史", Icons.Filled.History, Color(0xFF8E5A6E)) {
                    onOpenOverlay(OverlayPage.HISTORY)
                }
                AppIconEntry("下载", Icons.Filled.Download, Color(0xFF4A6FA5)) {
                    onOpenOverlay(OverlayPage.DOWNLOADS)
                }
                AppIconEntry("备份", Icons.Filled.Backup, Color(0xFF6C4BD8)) {
                    onOpenOverlay(OverlayPage.DATA_BACKUPS)
                }
                AppIconEntry("本地网页", Icons.Filled.FolderOpen, Color(0xFFC7A34A), onClick = onOpenLocalPages)
            }
            Spacer(Modifier.height(26.dp))
        }

        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "快捷入口",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { onOpenOverlay(OverlayPage.BOOKMARKS) }) {
                    Text("书签", fontSize = 13.sp)
                }
            }
            Text(
                "长按图标后拖到另一个图标上，可以合并成一个分组；长按菜单里可以把图标移到第二页。",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 10.dp)
            )
        }

        if (items.isEmpty()) {
            item {
                Text(
                    "还没有首页快捷入口。添加书签时勾选「添加到首页」，或在书签长按菜单里选「添加到首页」。",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
        } else {
            item {
                HomeShortcutGrid(
                    items = items,
                    onOpen = onOpenItem,
                    onLongPress = onLongPress,
                    onMerge = onMerge
                )
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

/** 首页第二页：上方是带边框的「本地网页」模块，下方是快捷入口第二页 */
@Composable
private fun HomeSecondPage(
    controller: BrowserController,
    items: List<HomeItem>,
    onOpenItem: (HomeItem) -> Unit,
    onLongPress: (HomeItem) -> Unit,
    onMerge: (HomeItem, HomeItem) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as App
    val scope = rememberCoroutineScope()

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

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp)
    ) {
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "本地网页",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        "单个 HTML 只复制文件本身；若页面用到 CSS/JS/图片，请导入整个文件夹，相对路径才能正常加载。",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    Row(
                        Modifier.padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ActionChip("打开文件") { openFileLauncher.launch(arrayOf("*/*")) }
                        ActionChip("打开文件夹") { openTreeLauncher.launch(null) }
                    }
                    TextButton(
                        onClick = {
                            controller.loadUrl(LocalSiteManager.ASSET_HOST + "/assets/sample/index.html")
                        },
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.padding(top = 6.dp)
                    ) { Text("打开内置示例网页", fontSize = 13.sp) }
                }
            }
            Spacer(Modifier.height(22.dp))
        }

        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "快捷入口",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "第 2 页",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "第一页放满 12 个图标后，新添加的快捷入口会自动排到这里；长按图标也可以在菜单里手动移到另一页。",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 10.dp)
            )
        }

        if (items.isEmpty()) {
            item {
                Text(
                    "第二页还是空的。第一页放满后，新添加的快捷入口会自动出现在这里。",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
        } else {
            item {
                HomeShortcutGrid(
                    items = items,
                    onOpen = onOpenItem,
                    onLongPress = onLongPress,
                    onMerge = onMerge
                )
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun AppIconEntry(
    label: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(color),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(23.dp)
            )
        }
        Spacer(Modifier.height(7.dp))
        Text(
            label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun HomeShortcutGrid(
    items: List<HomeItem>,
    columns: Int = 4,
    onOpen: (HomeItem) -> Unit,
    onLongPress: (HomeItem) -> Unit,
    onMerge: (HomeItem, HomeItem) -> Unit
) {
    var gridSize by remember { mutableStateOf(IntSize.Zero) }
    var dragging by remember { mutableStateOf<HomeItem?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var dragStart by remember { mutableStateOf(Offset.Zero) }
    var hoverId by remember { mutableStateOf<Long?>(null) }
    val density = LocalDensity.current
    val gap = 4.dp
    val rowHeight = 82.dp

    fun targetAt(pos: Offset): HomeItem? {
        if (gridSize.width <= 0) return null
        val gapPx = with(density) { gap.toPx() }
        val rowPx = with(density) { rowHeight.toPx() }
        val cellWidth = (gridSize.width - gapPx * (columns - 1)) / columns
        if (cellWidth <= 0f) return null
        val column = (pos.x / (cellWidth + gapPx)).toInt().coerceIn(0, columns - 1)
        val row = (pos.y / rowPx).toInt().coerceAtLeast(0)
        return items.getOrNull(row * columns + column)
    }

    Box(
        Modifier
            .fillMaxWidth()
            .onSizeChanged { gridSize = it }
            .pointerInput(items, columns) {
                val tapSlop = with(density) { 12.dp.toPx() }
                detectDragGesturesAfterLongPress(
                    onDragStart = { start ->
                        val target = targetAt(start)
                        if (target != null) {
                            dragging = target
                            dragStart = start
                            dragOffset = Offset.Zero
                            hoverId = null
                        }
                    },
                    onDrag = { change, amount ->
                        if (dragging == null) {
                            change.consume()
                            return@detectDragGesturesAfterLongPress
                        }
                        change.consume()
                        dragOffset += amount
                        hoverId = targetAt(dragStart + dragOffset)?.id
                    },
                    onDragEnd = {
                        val dragged = dragging
                        val target = hoverId?.let { id -> items.firstOrNull { it.id == id } }
                        if (dragged != null && target != null && target.id != dragged.id) {
                            onMerge(dragged, target)
                        } else if (dragged != null && dragOffset.getDistance() < tapSlop) {
                            onLongPress(dragged)
                        }
                        dragging = null
                        dragOffset = Offset.Zero
                        hoverId = null
                    },
                    onDragCancel = {
                        dragging = null
                        dragOffset = Offset.Zero
                        hoverId = null
                    }
                )
            }
    ) {
        Column(Modifier.fillMaxWidth()) {
            items.chunked(columns).forEach { rowItems ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(rowHeight),
                    horizontalArrangement = Arrangement.spacedBy(gap)
                ) {
                    rowItems.forEach { item ->
                        val isDragging = dragging?.id == item.id
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .zIndex(if (isDragging) 1f else 0f)
                        ) {
                            HomeShortcutIcon(
                                item = item,
                                highlight = !isDragging && hoverId == item.id,
                                clickEnabled = dragging == null,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .offset {
                                        if (isDragging) {
                                            IntOffset(
                                                dragOffset.x.roundToInt(),
                                                dragOffset.y.roundToInt()
                                            )
                                        } else {
                                            IntOffset.Zero
                                        }
                                    },
                                onClick = { onOpen(item) }
                            )
                        }
                    }
                    repeat(columns - rowItems.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun HomeShortcutIcon(
    item: HomeItem,
    highlight: Boolean,
    clickEnabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier.clickable(enabled = clickEnabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (item.isFolder) {
            Box(
                Modifier
                    .size(52.dp)
                    .then(
                        if (highlight) {
                            Modifier.border(2.dp, MaterialTheme.colorScheme.secondary, RoundedCornerShape(16.dp))
                        } else {
                            Modifier
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                FolderGlyph(Modifier.size(52.dp))
            }
        } else {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .background(
                        item.iconColor?.let { Color(it) }
                            ?: siteIconColor(item.title.ifBlank { item.url })
                    )
                    .then(
                        if (highlight) {
                            Modifier.border(2.dp, MaterialTheme.colorScheme.secondary, RoundedCornerShape(17.dp))
                        } else {
                            Modifier
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    item.iconText?.trim()?.take(2)?.takeIf { it.isNotBlank() }
                        ?: siteIconText(item.title, item.url),
                    fontSize = 20.sp,
                    color = Color.White
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            item.title,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun HomeActionRow(label: String, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 15.sp,
        color = if (label.startsWith("移出") || label.startsWith("解散")) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp)
    )
}

@Composable
private fun ActionChip(label: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .height(34.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
    ) {
        Box(Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
        }
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
