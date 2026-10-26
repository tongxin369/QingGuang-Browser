@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.qingguang.browser.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
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

/** 第一页 4 列 × 3 行 = 12 个；第二页 4 列 × 5 行 = 20 个；第三页起 4 列 × 8 行 = 32 个 */
internal const val HOME_PAGE_CAPACITY = 12
private const val SECOND_PAGE_CAPACITY = 20
private const val EXTRA_PAGE_CAPACITY = 32
private const val HOME_COLUMNS = 4

/** 指定页面的图标容量：第一页 12 个，第二页 20 个，后续页面 32 个 */
internal fun homePageCapacity(page: Int): Int = when {
    page <= 0 -> HOME_PAGE_CAPACITY
    page == 1 -> SECOND_PAGE_CAPACITY
    else -> EXTRA_PAGE_CAPACITY
}

private val pageNames = arrayOf("第一页", "第二页", "第三页", "第四页", "第五页", "第六页")

private fun pageName(index: Int): String = pageNames.getOrElse(index) { "第 ${index + 1} 页" }

private const val SHORTCUT_HINT =
    "· 长按图标：重命名、自定义图标、移入其他页、移出首页\n" +
        "· 拖到另一个图标上：合并为分组\n" +
        "· 拖到空白格子：调整位置（关闭自动补位后可保留空位）\n" +
        "· 点击分组标题：重命名分组；分组内拖到空格可排序\n" +
        "· 第一、二页各 12 个图标，第三页起每页 32 个，页面放满后新图标自动进入下一页"

/** 首页：支持 2~99 页，第 1 页常驻主页，第 2 页本地网页模块，其余为快捷入口页 */
@Composable
fun HomePage(
    controller: BrowserController,
    onOpenOverlay: (OverlayPage) -> Unit,
    pageCount: Int,
    autoCompact: Boolean
) {
    val context = LocalContext.current
    val app = context.applicationContext as App
    val scope = rememberCoroutineScope()
    val totalPages = pageCount.coerceIn(2, 99)
    val pagerState = rememberPagerState(pageCount = { totalPages })

    val homeItems by app.database.homeItemDao()
        .observeAll()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val rootItems = homeItems.filter { it.parentId == 0L }
    val itemsByPage = (0 until totalPages).map { page ->
        rootItems.filter { it.page == page }
    }

    var actionTarget by remember { mutableStateOf<HomeItem?>(null) }
    var openFolder by remember { mutableStateOf<HomeItem?>(null) }
    var renameTarget by remember { mutableStateOf<HomeItem?>(null) }
    var renameName by remember { mutableStateOf("") }
    var iconTarget by remember { mutableStateOf<HomeItem?>(null) }
    var moveTarget by remember { mutableStateOf<HomeItem?>(null) }
    var showHint by remember { mutableStateOf(false) }

    suspend fun resequencePage(page: Int) {
        val dao = app.database.homeItemDao()
        dao.all()
            .filter { it.parentId == 0L && it.page == page }
            .sortedWith(
                compareBy(
                    { if (it.slot < 0) Int.MAX_VALUE else it.slot },
                    { it.sortOrder },
                    { it.id }
                )
            )
            .forEachIndexed { index, item -> dao.updateSlot(item.id, index) }
    }

    suspend fun compactPage(page: Int) {
        if (autoCompact) resequencePage(page)
    }

    suspend fun firstEmptySlot(page: Int): Int? {
        val used = app.database.homeItemDao().slotsOnPage(page).toSet()
        return (0 until homePageCapacity(page)).firstOrNull { it !in used }
    }

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
                        slot = minOf(
                            if (target.slot < 0) 0 else target.slot,
                            if (dragged.slot < 0) 0 else dragged.slot
                        ),
                        sortOrder = minOf(target.sortOrder, dragged.sortOrder)
                    )
                )
                dao.moveToParent(target.id, folderId, 0)
                dao.moveToParent(dragged.id, folderId, 1)
            }
            if (parentId == 0L) compactPage(page)
            toast(context, "已合并为分组")
        }
    }

    Box(Modifier.fillMaxSize()) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val pageItems = itemsByPage[page]
            val openItem: (HomeItem) -> Unit = { item ->
                if (item.isFolder) openFolder = item else controller.loadUrl(item.url)
            }
            val longPress: (HomeItem) -> Unit = { actionTarget = it }
            when (page) {
                0 -> HomeMainPage(
                    controller = controller,
                    onOpenOverlay = onOpenOverlay,
                    onOpenLocalPages = { scope.launch { pagerState.animateScrollToPage(1) } },
                    items = pageItems,
                    allItems = homeItems,
                    autoCompact = autoCompact,
                    onShowHint = { showHint = true },
                    onOpenItem = openItem,
                    onLongPress = longPress,
                    onMerge = { dragged, target -> mergeItems(dragged, target, 0L, 0) },
                    onMoveToSlot = { item, slot ->
                        scope.launch {
                            app.database.homeItemDao().updateSlot(item.id, slot)
                            compactPage(item.page)
                        }
                    }
                )
                1 -> HomeSecondPage(
                    controller = controller,
                    items = pageItems,
                    allItems = homeItems,
                    autoCompact = autoCompact,
                    onShowHint = { showHint = true },
                    onOpenItem = openItem,
                    onLongPress = longPress,
                    onMerge = { dragged, target -> mergeItems(dragged, target, 0L, 1) },
                    onMoveToSlot = { item, slot ->
                        scope.launch {
                            app.database.homeItemDao().updateSlot(item.id, slot)
                            compactPage(item.page)
                        }
                    }
                )
                else -> HomeExtraPage(
                    pageIndex = page,
                    items = pageItems,
                    allItems = homeItems,
                    autoCompact = autoCompact,
                    onShowHint = { showHint = true },
                    onOpenItem = openItem,
                    onLongPress = longPress,
                    onMerge = { dragged, target -> mergeItems(dragged, target, 0L, page) },
                    onMoveToSlot = { item, slot ->
                        scope.launch {
                            app.database.homeItemDao().updateSlot(item.id, slot)
                            compactPage(item.page)
                        }
                    }
                )
            }
        }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            if (totalPages <= 8) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    repeat(totalPages) { index ->
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
            } else {
                Text(
                    "${pagerState.currentPage + 1} / $totalPages",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showHint) {
        AlertDialog(
            onDismissRequest = { showHint = false },
            title = { Text("快捷入口说明") },
            text = { Text(SHORTCUT_HINT, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = { showHint = false }) { Text("知道了") }
            }
        )
    }

    moveTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { moveTarget = null },
            title = { Text("移入其他页") },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    (0 until totalPages).forEach { page ->
                        val capacity = homePageCapacity(page)
                        val count = itemsByPage[page].size
                        val isCurrent = page == item.page
                        val full = count >= capacity
                        val label = buildString {
                            append(pageName(page))
                            append("（")
                            append(count)
                            append("/")
                            append(capacity)
                            append("）")
                            if (isCurrent) append(" · 当前")
                            else if (full) append(" · 已满")
                        }
                        Text(
                            label,
                            fontSize = 15.sp,
                            color = if (isCurrent || full) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isCurrent) {
                                    if (full) {
                                        toast(context, "该页已满，请先增加页数或清理图标")
                                    } else {
                                        moveTarget = null
                                        scope.launch {
                                            val dao = app.database.homeItemDao()
                                            val sourcePage = item.page
                                            val slot = firstEmptySlot(page)
                                            if (slot == null) {
                                                toast(context, "该页已满")
                                            } else {
                                                dao.place(item.id, page, slot, item.sortOrder)
                                                compactPage(sourcePage)
                                                compactPage(page)
                                            }
                                        }
                                    }
                                }
                                .padding(vertical = 10.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { moveTarget = null }) { Text("取消") }
            }
        )
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
                        HomeActionRow("移入其他页") {
                            actionTarget = null
                            moveTarget = item
                        }
                    }
                    if (item.isFolder) {
                        HomeActionRow("解散分组") {
                            actionTarget = null
                            scope.launch {
                                val dao = app.database.homeItemDao()
                                val children = homeItems
                                    .filter { it.parentId == item.id }
                                    .sortedBy { it.sortOrder }
                                var nextSlot = (dao.slotsOnPage(item.page).maxOrNull() ?: -1) + 1
                                children.forEachIndexed { index, child ->
                                    dao.moveToParent(child.id, 0L, index)
                                    dao.place(child.id, item.page, nextSlot++, index)
                                }
                                dao.delete(item.id)
                                compactPage(item.page)
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
                                    var page = 0
                                    while (page < totalPages - 1 &&
                                        dao.countRootOnPage(page) >= homePageCapacity(page)
                                    ) {
                                        page++
                                    }
                                    val slot = (dao.slotsOnPage(page).maxOrNull() ?: -1) + 1
                                    dao.moveToParent(item.id, 0L, item.sortOrder)
                                    dao.place(item.id, page, slot, item.sortOrder)
                                    compactPage(page)
                                }
                            }
                        } else {
                            HomeActionRow("移出首页") {
                                actionTarget = null
                                scope.launch {
                                    val dao = app.database.homeItemDao()
                                    val page = item.page
                                    dao.delete(item.id)
                                    compactPage(page)
                                }
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
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        openFolder = null
                        renameTarget = folder
                        renameName = folder.title
                    }
                ) {
                    Text(
                        folder.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "重命名分组",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                }
            },
            text = {
                if (children.isEmpty()) {
                    Text("这个分组里还没有图标。", fontSize = 13.sp)
                } else {
                    val cells = children.map<HomeItem, HomeItem?> { it }.toMutableList()
                    while (cells.size % 3 != 0) cells.add(null)
                    Column(
                        Modifier
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        HomeShortcutGrid(
                            cells = cells,
                            columns = 3,
                            allItems = homeItems,
                            onOpen = { child ->
                                openFolder = null
                                if (child.isFolder) openFolder = child
                                else controller.loadUrl(child.url)
                            },
                            onLongPress = { child ->
                                openFolder = null
                                actionTarget = child
                            },
                            onMerge = { dragged, target ->
                                mergeItems(dragged, target, folder.id, folder.page)
                            },
                            onMoveToSlot = { child, slot ->
                                scope.launch {
                                    val dao = app.database.homeItemDao()
                                    val reordered = children.toMutableList()
                                    val fromIndex = reordered.indexOfFirst { it.id == child.id }
                                    if (fromIndex >= 0) {
                                        val moved = reordered.removeAt(fromIndex)
                                        val toIndex = slot.coerceIn(0, reordered.size)
                                        reordered.add(toIndex, moved)
                                        reordered.forEachIndexed { index, c ->
                                            dao.updateSortOrder(c.id, index)
                                        }
                                    }
                                }
                            }
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
            title = { Text(if (item.isFolder) "重命名分组" else "重命名快捷入口") },
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
    allItems: List<HomeItem>,
    autoCompact: Boolean,
    onShowHint: () -> Unit,
    onOpenItem: (HomeItem) -> Unit,
    onLongPress: (HomeItem) -> Unit,
    onMerge: (HomeItem, HomeItem) -> Unit,
    onMoveToSlot: (HomeItem, Int) -> Unit
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
            ShortcutHeader(pageLabel = null, onShowHint = onShowHint, onOpenBookmarks = {
                onOpenOverlay(OverlayPage.BOOKMARKS)
            })
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
                    cells = buildHomeCells(items, autoCompact, HOME_PAGE_CAPACITY),
                    columns = HOME_COLUMNS,
                    allItems = allItems,
                    onOpen = onOpenItem,
                    onLongPress = onLongPress,
                    onMerge = onMerge,
                    onMoveToSlot = onMoveToSlot
                )
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

/** 首页第二页：带边框的「本地网页」模块 + 快捷入口第二页 */
@Composable
private fun HomeSecondPage(
    controller: BrowserController,
    items: List<HomeItem>,
    allItems: List<HomeItem>,
    autoCompact: Boolean,
    onShowHint: () -> Unit,
    onOpenItem: (HomeItem) -> Unit,
    onLongPress: (HomeItem) -> Unit,
    onMerge: (HomeItem, HomeItem) -> Unit,
    onMoveToSlot: (HomeItem, Int) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as App
    val scope = rememberCoroutineScope()

    val openFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null) {
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
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null) {
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
                        ActionChip("打开文件") {
                            // 显式申请写权限，源码编辑器才能同步修改手机原文件
                            openFileLauncher.launch(
                                Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                    addCategory(Intent.CATEGORY_OPENABLE)
                                    type = "*/*"
                                    addFlags(
                                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                                            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                                    )
                                }
                            )
                        }
                        ActionChip("打开文件夹") {
                            openTreeLauncher.launch(
                                Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                                    addFlags(
                                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                                            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                                    )
                                }
                            )
                        }
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
            ShortcutHeader(pageLabel = "第 2 页", onShowHint = onShowHint)
        }

        if (items.isEmpty()) {
            item {
                Text(
                    "第二页还是空的。第一页放满后，新添加的快捷入口会自动出现在这里；也可以长按图标手动移入。",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
        } else {
            item {
                HomeShortcutGrid(
                    cells = buildHomeCells(items, autoCompact, homePageCapacity(1)),
                    columns = HOME_COLUMNS,
                    allItems = allItems,
                    onOpen = onOpenItem,
                    onLongPress = onLongPress,
                    onMerge = onMerge,
                    onMoveToSlot = onMoveToSlot
                )
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun HomeExtraPage(
    pageIndex: Int,
    items: List<HomeItem>,
    allItems: List<HomeItem>,
    autoCompact: Boolean,
    onShowHint: () -> Unit,
    onOpenItem: (HomeItem) -> Unit,
    onLongPress: (HomeItem) -> Unit,
    onMerge: (HomeItem, HomeItem) -> Unit,
    onMoveToSlot: (HomeItem, Int) -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp)
    ) {
        item {
            ShortcutHeader(pageLabel = pageName(pageIndex), onShowHint = onShowHint)
            Spacer(Modifier.height(14.dp))
        }
        if (items.isEmpty()) {
            item {
                Text(
                    "这一页还没有快捷入口。长按第一页的图标，选择「移入其他页」即可放到这里。",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
        } else {
            item {
                HomeShortcutGrid(
                    cells = buildHomeCells(items, autoCompact, homePageCapacity(pageIndex)),
                    columns = HOME_COLUMNS,
                    allItems = allItems,
                    onOpen = onOpenItem,
                    onLongPress = onLongPress,
                    onMerge = onMerge,
                    onMoveToSlot = onMoveToSlot
                )
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun ShortcutHeader(
    pageLabel: String?,
    onShowHint: () -> Unit,
    onOpenBookmarks: (() -> Unit)? = null
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "快捷入口",
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.width(4.dp))
        Box(
            Modifier
                .size(24.dp)
                .clickable(onClick = onShowHint),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Info,
                contentDescription = "快捷入口说明",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(Modifier.weight(1f))
        if (pageLabel != null) {
            Text(
                pageLabel,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (onOpenBookmarks != null) {
            TextButton(onClick = onOpenBookmarks) {
                Text("书签", fontSize = 13.sp)
            }
        }
    }
}

/** 把图标排进 4 列的格子；自动补位时顺序挤拢，否则按已保存的格子位置摆放 */
private fun buildHomeCells(
    items: List<HomeItem>,
    autoCompact: Boolean,
    capacity: Int
): List<HomeItem?> {
    val ordered = items.sortedWith(
        compareBy(
            { if (it.slot < 0) Int.MAX_VALUE else it.slot },
            { it.sortOrder },
            { it.id }
        )
    )
    val cells = MutableList<HomeItem?>(capacity) { null }
    if (autoCompact) {
        ordered.forEachIndexed { index, item ->
            if (index < cells.size) cells[index] = item else cells.add(item)
        }
        return cells
    }
    val pending = mutableListOf<HomeItem>()
    ordered.forEach { item ->
        val slot = item.slot
        if (slot in cells.indices && cells[slot] == null) {
            cells[slot] = item
        } else {
            pending.add(item)
        }
    }
    var cursor = 0
    pending.forEach { item ->
        while (cursor < cells.size && cells[cursor] != null) cursor++
        if (cursor < cells.size) cells[cursor++] = item else cells.add(item)
    }
    return cells
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
    cells: List<HomeItem?>,
    columns: Int,
    allItems: List<HomeItem>,
    onOpen: (HomeItem) -> Unit,
    onLongPress: (HomeItem) -> Unit,
    onMerge: (HomeItem, HomeItem) -> Unit,
    onMoveToSlot: (HomeItem, Int) -> Unit
) {
    var gridSize by remember { mutableStateOf(IntSize.Zero) }
    var dragging by remember { mutableStateOf<HomeItem?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var dragStart by remember { mutableStateOf(Offset.Zero) }
    var hoverIndex by remember { mutableStateOf(-1) }
    val density = LocalDensity.current
    val gap = 4.dp
    val rowHeight = 82.dp

    fun slotAt(pos: Offset): Int {
        if (gridSize.width <= 0) return -1
        val gapPx = with(density) { gap.toPx() }
        val rowPx = with(density) { rowHeight.toPx() }
        val cellWidth = (gridSize.width - gapPx * (columns - 1)) / columns
        if (cellWidth <= 0f) return -1
        val column = (pos.x / (cellWidth + gapPx)).toInt().coerceIn(0, columns - 1)
        val row = (pos.y / rowPx).toInt().coerceAtLeast(0)
        val index = row * columns + column
        return if (index in cells.indices) index else -1
    }

    Box(
        Modifier
            .fillMaxWidth()
            .onSizeChanged { gridSize = it }
            .pointerInput(cells, columns) {
                val tapSlop = with(density) { 12.dp.toPx() }
                detectDragGesturesAfterLongPress(
                    onDragStart = { start ->
                        val index = slotAt(start)
                        val target = cells.getOrNull(index)
                        if (target != null) {
                            dragging = target
                            dragStart = start
                            dragOffset = Offset.Zero
                            hoverIndex = -1
                        }
                    },
                    onDrag = { change, amount ->
                        if (dragging == null) {
                            change.consume()
                            return@detectDragGesturesAfterLongPress
                        }
                        change.consume()
                        dragOffset += amount
                        hoverIndex = slotAt(dragStart + dragOffset)
                    },
                    onDragEnd = {
                        val dragged = dragging
                        if (dragged != null) {
                            if (dragOffset.getDistance() < tapSlop) {
                                onLongPress(dragged)
                            } else if (hoverIndex >= 0) {
                                val target = cells.getOrNull(hoverIndex)
                                if (target != null && target.id != dragged.id) {
                                    onMerge(dragged, target)
                                } else if (target == null) {
                                    onMoveToSlot(dragged, hoverIndex)
                                }
                            }
                        }
                        dragging = null
                        dragOffset = Offset.Zero
                        hoverIndex = -1
                    },
                    onDragCancel = {
                        dragging = null
                        dragOffset = Offset.Zero
                        hoverIndex = -1
                    }
                )
            }
    ) {
        Column(Modifier.fillMaxWidth()) {
            cells.chunked(columns).forEachIndexed { rowIndex, rowCells ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(rowHeight),
                    horizontalArrangement = Arrangement.spacedBy(gap)
                ) {
                    rowCells.forEachIndexed { columnIndex, cell ->
                        val absoluteIndex = rowIndex * columns + columnIndex
                        val isDragging = cell != null && dragging?.id == cell.id
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(
                                    if (!isDragging && hoverIndex == absoluteIndex) {
                                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f)
                                    } else {
                                        Color.Transparent
                                    },
                                    RoundedCornerShape(16.dp)
                                )
                                .zIndex(if (isDragging) 1f else 0f)
                        ) {
                            if (cell != null) {
                                HomeShortcutIcon(
                                    item = cell,
                                    children = if (cell.isFolder) {
                                        allItems.filter { it.parentId == cell.id }
                                            .sortedBy { it.sortOrder }
                                    } else {
                                        emptyList()
                                    },
                                    highlight = !isDragging && hoverIndex == absoluteIndex,
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
                                    onClick = { onOpen(cell) }
                                )
                            }
                        }
                    }
                    repeat(columns - rowCells.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** 透明式分组图标：半透明圆角底 + 组内前 4 个图标的小预览 */
@Composable
private fun FolderPreview(children: List<HomeItem>, highlight: Boolean) {
    val shape = RoundedCornerShape(17.dp)
    Box(
        Modifier
            .size(52.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.30f), shape)
            .then(
                if (highlight) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.secondary, shape)
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (children.isEmpty()) {
            FolderGlyph(Modifier.size(26.dp))
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                children.take(4).chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        row.forEach { child -> MiniShortcutIcon(child) }
                        if (row.size == 1) Spacer(Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniShortcutIcon(item: HomeItem) {
    if (item.isFolder) {
        Box(
            Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)),
            contentAlignment = Alignment.Center
        ) {
            FolderGlyph(Modifier.size(13.dp))
        }
    } else {
        Box(
            Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(
                    item.iconColor?.let { Color(it) }
                        ?: siteIconColor(item.title.ifBlank { item.url })
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                item.iconText?.trim()?.take(1)?.takeIf { it.isNotBlank() }
                    ?: siteIconText(item.title, item.url).take(1),
                fontSize = 9.sp,
                lineHeight = 9.sp,
                color = Color.White,
                maxLines = 1,
                textAlign = TextAlign.Center,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
            )
        }
    }
}

@Composable
private fun HomeShortcutIcon(
    item: HomeItem,
    children: List<HomeItem> = emptyList(),
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
            FolderPreview(children = children, highlight = highlight)
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
