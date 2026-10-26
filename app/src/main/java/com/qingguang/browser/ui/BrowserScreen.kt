@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.qingguang.browser.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.qingguang.browser.App
import com.qingguang.browser.ui.theme.ThemeController
import com.qingguang.browser.web.BrowserController
import com.qingguang.browser.web.TabSummary
import kotlinx.coroutines.launch

enum class OverlayPage(val title: String) {
    BOOKMARKS("书签"),
    HISTORY("历史"),
    DOWNLOADS("下载"),
    DATA_BACKUPS("数据备份"),
    SETTINGS("设置")
}

/** 顶部/底部栏配色：跟随网页主题色时使用网页色，否则使用应用 surface */
private data class BarColors(val container: Color, val content: Color)

@Composable
private fun rememberBarColors(controller: BrowserController): BarColors {
    val fallbackContainer = MaterialTheme.colorScheme.surface
    val fallbackContent = MaterialTheme.colorScheme.onSurface
    val page = controller.pageThemeColor
    if (!controller.followPageTheme || page == null) {
        return BarColors(fallbackContainer, fallbackContent)
    }
    val container = Color(page)
    val content = if (container.luminance() > 0.5f) Color(0xFF1B1D21) else Color.White
    return BarColors(container, content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(controller: BrowserController) {
    val focusManager = LocalFocusManager.current
    val app = LocalContext.current.applicationContext as App

    var address by remember { mutableStateOf("") }
    var overlay by remember { mutableStateOf<OverlayPage?>(null) }
    var showTabs by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showAddBookmark by remember { mutableStateOf(false) }
    var showSource by remember { mutableStateOf(false) }

    LaunchedEffect(controller.currentUrl) {
        if (!controller.showHome) address = controller.currentUrl
    }
    LaunchedEffect(controller.showHome) {
        if (controller.showHome) address = ""
    }

    BackHandler(
        enabled = overlay != null || showTabs || showMenu || !controller.showHome
    ) {
        when {
            overlay != null -> overlay = null
            showTabs -> showTabs = false
            showMenu -> showMenu = false
            !controller.showHome -> {
                if (controller.canGoBack) controller.goBack() else controller.goHome()
            }
        }
    }

    val bars = rememberBarColors(controller)

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (!controller.showHome) {
            AddressBar(
                address = address,
                onAddressChange = { address = it },
                onSubmit = {
                    controller.submitInput(address)
                    focusManager.clearFocus()
                },
                onReload = { controller.reload() },
                progress = controller.progress,
                colors = bars
            )
        }
        Box(Modifier.weight(1f)) {
            WebViewHost(controller)
            if (controller.showHome) {
                HomePage(
                    controller = controller,
                    onOpenOverlay = { overlay = it },
                    pageCount = app.settings.homePageCount,
                    autoCompact = app.settings.homeAutoCompact
                )
            }
            // Via 风格：标签列表与功能菜单都贴在底部导航栏上方展开
            if (showTabs) {
                TabPanel(
                    controller = controller,
                    colors = bars,
                    onDismiss = { showTabs = false }
                )
            }
            if (showMenu) {
                MenuPanel(
                    controller = controller,
                    onDismiss = { showMenu = false },
                    onOpenOverlay = {
                        showMenu = false
                        overlay = it
                    },
                    onAddBookmark = { showAddBookmark = true },
                    onViewSource = { showSource = true }
                )
            }
        }
        BottomBar(
            controller = controller,
            onShowTabs = {
                showTabs = !showTabs
                if (showTabs) showMenu = false
            },
            onMenu = {
                showMenu = !showMenu
                if (showMenu) showTabs = false
            },
            colors = bars
        )
    }
    if (showAddBookmark) {
        AddBookmarkDialog(controller = controller, onDismiss = { showAddBookmark = false })
    }
    overlay?.let { page ->
        OverlayPageHost(
            page = page,
            controller = controller,
            onClose = { overlay = null }
        )
    }
    if (showSource) {
        SourceEditorOverlay(controller = controller, onClose = { showSource = false })
    }
    controller.pendingDownload?.let { pending ->
        DownloadConfirmDialog(
            request = pending,
            onConfirm = { controller.confirmDownload(it) },
            onCancel = { controller.cancelDownload() }
        )
    }
}

@Composable
private fun WebViewHost(controller: BrowserController) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            FrameLayout(ctx).also { controller.attachContainer(it) }
        }
    )
}

@Composable
private fun AddressBar(
    address: String,
    onAddressChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onReload: () -> Unit,
    progress: Float,
    colors: BarColors
) {
    Column(Modifier.background(colors.container)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp),
                shape = RoundedCornerShape(19.dp),
                color = colors.content.copy(alpha = 0.08f)
            ) {
                BasicTextField(
                    value = address,
                    onValueChange = onAddressChange,
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 13.sp,
                        color = colors.content
                    ),
                    cursorBrush = SolidColor(colors.content),
                    keyboardOptions = KeyboardOptions(
                        imeAction = androidx.compose.ui.text.input.ImeAction.Go
                    ),
                    keyboardActions = KeyboardActions(onGo = { onSubmit() }),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    decorationBox = { inner ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                            if (address.isBlank()) {
                                Text(
                                    "搜索或输入网址",
                                    fontSize = 13.sp,
                                    color = colors.content.copy(alpha = 0.55f)
                                )
                            }
                            inner()
                        }
                    }
                )
            }
            IconButton(onClick = onReload, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Filled.Refresh, contentDescription = "刷新", tint = colors.content)
            }
        }
        if (progress > 0.01f && progress < 0.99f) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = MaterialTheme.colorScheme.secondary,
                trackColor = Color.Transparent
            )
        }
    }
}

@Composable
private fun BottomBar(
    controller: BrowserController,
    onShowTabs: () -> Unit,
    onMenu: () -> Unit,
    colors: BarColors
) {
    Surface(color = colors.container) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(54.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BarIcon(
                icon = Icons.Outlined.ChevronLeft,
                desc = "后退",
                enabled = controller.canGoBack,
                content = colors.content
            ) { controller.goBack() }
            BarIcon(
                icon = Icons.Outlined.ChevronRight,
                desc = "前进",
                enabled = controller.canGoForward,
                content = colors.content
            ) { controller.goForward() }
            BarIcon(
                icon = Icons.Outlined.Menu,
                desc = "菜单",
                enabled = true,
                content = colors.content
            ) { onMenu() }
            IconButton(onClick = onShowTabs) {
                // X 浏览器风格：圆角方框 + 内部数字表示标签数量
                Box(
                    Modifier
                        .size(20.dp)
                        .border(1.4.dp, colors.content, RoundedCornerShape(5.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = controller.tabCount.toString(),
                        fontSize = 10.sp,
                        color = colors.content
                    )
                }
            }
            BarIcon(
                icon = Icons.Outlined.Home,
                desc = "主页",
                enabled = true,
                content = colors.content
            ) { controller.goHome() }
        }
    }
}

@Composable
private fun BarIcon(
    icon: ImageVector,
    desc: String,
    enabled: Boolean,
    content: Color,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(
            icon,
            contentDescription = desc,
            tint = if (enabled) content else content.copy(alpha = 0.3f)
        )
    }
}

/** Via 风格标签页：贴在底部导航栏上方展开，底部与「+」新建空白页在同一块 */
@Composable
private fun TabPanel(
    controller: BrowserController,
    colors: BarColors,
    onDismiss: () -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        // 点击面板上方空白处关闭
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.12f))
                .pointerInput(onDismiss) {
                    detectTapGestures { onDismiss() }
                }
        )
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                // 面板空白区域不触发“点空白关闭”
                .pointerInput(Unit) { detectTapGestures { } },
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
            color = colors.container,
            shadowElevation = 12.dp
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 370.dp)
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "标签页",
                        fontSize = 16.sp,
                        color = colors.content,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "关闭",
                            tint = colors.content.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                LazyColumn(Modifier.weight(1f, fill = false)) {
                    items(controller.tabList, key = { it.id }) { tab ->
                        TabRow(
                            tab = tab,
                            onClick = {
                                controller.switchTab(tab.id)
                                onDismiss()
                            },
                            onClose = { controller.closeTab(tab.id) }
                        )
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .clickable {
                            controller.newTab()
                            onDismiss()
                        },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "新建标签页",
                        tint = colors.content,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TabRow(tab: TabSummary, onClick: () -> Unit, onClose: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(34.dp)
                .background(
                    color = tabCircleColor(tab.title),
                    shape = RoundedCornerShape(10.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = tabInitial(tab.title, tab.url),
                fontSize = 14.sp,
                color = Color.White
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                tab.title,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (tab.isActive) MaterialTheme.colorScheme.secondary
                else MaterialTheme.colorScheme.onSurface
            )
            if (tab.url.isNotBlank()) {
                Text(
                    tab.url,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        IconButton(onClick = onClose, modifier = Modifier.size(34.dp)) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "关闭标签",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

private val circlePalette = listOf(
    Color(0xFF5B8C5A), Color(0xFFC7A34A), Color(0xFF8E5A6E),
    Color(0xFF6C4BD8), Color(0xFF4A6FA5), Color(0xFFB4543F)
)

internal fun tabCircleColor(seed: String): Color {
    if (seed.isBlank()) return Color(0xFF9AA1AC)
    return circlePalette[Math.abs(seed.hashCode()) % circlePalette.size]
}

internal fun tabInitial(title: String, url: String): String {
    val source = title.trim().ifBlank { url.trim() }
    if (source.isBlank()) return "新"
    return source.take(1).uppercase()
}

private fun menuIcon(item: MenuItem, isDark: Boolean, isSpeaking: Boolean): ImageVector = when (item) {
    MenuItem.ADD_BOOKMARK -> Icons.Filled.BookmarkAdd
    MenuItem.BACKUP_PAGE -> Icons.Filled.CloudUpload
    MenuItem.BOOKMARKS -> Icons.Filled.BookmarkBorder
    MenuItem.HISTORY -> Icons.Filled.History
    MenuItem.DOWNLOADS -> Icons.Filled.Download
    MenuItem.DATA_BACKUP -> Icons.Filled.Backup
    MenuItem.SETTINGS -> Icons.Filled.Settings
    MenuItem.REFRESH -> Icons.Filled.Refresh
    MenuItem.VIEW_SOURCE -> Icons.Filled.Code
    MenuItem.PRINT -> Icons.Filled.Print
    MenuItem.READ_ALOUD -> if (isSpeaking) Icons.Filled.StopCircle else Icons.Filled.RecordVoiceOver
    MenuItem.COPY_URL -> Icons.Filled.ContentCopy
    MenuItem.SHARE -> Icons.Filled.Share
    MenuItem.DARK_TOGGLE -> if (isDark) Icons.Filled.LightMode else Icons.Filled.DarkMode
    MenuItem.DESKTOP_MODE -> Icons.Filled.DesktopWindows
    MenuItem.CLEAR_CACHE -> Icons.Filled.Delete
    MenuItem.HOME -> Icons.Filled.Home
    MenuItem.CLOSE_TAB -> Icons.Filled.Close
}

private fun menuLabel(item: MenuItem, isDark: Boolean, isSpeaking: Boolean): String = when {
    item == MenuItem.DARK_TOGGLE && isDark -> "日间模式"
    item == MenuItem.READ_ALOUD && isSpeaking -> "停止朗读"
    else -> item.label
}

@Composable
private fun MenuPanel(
    controller: BrowserController,
    onDismiss: () -> Unit,
    onOpenOverlay: (OverlayPage) -> Unit,
    onAddBookmark: () -> Unit,
    onViewSource: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as App
    val systemDark = isSystemInDarkTheme()
    val isDark = when (ThemeController.mode) {
        1 -> false
        2 -> true
        else -> systemDark
    }

    val order = remember(app.settings.menuOrder) { MenuConfig.currentOrder(app.settings) }
    val rows = MenuConfig.rows(app.settings)
    val pageSize = MenuConfig.pageSize(app.settings)
    val pages = remember(order, pageSize) {
        order.chunked(pageSize).ifEmpty { listOf(emptyList()) }
    }
    val pagerState = rememberPagerState(pageCount = { pages.size })

    fun run(item: MenuItem) {
        onDismiss()
        when (item) {
            MenuItem.ADD_BOOKMARK -> onAddBookmark()
            MenuItem.BACKUP_PAGE -> controller.backupCurrent { _, msg ->
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
            MenuItem.BOOKMARKS -> onOpenOverlay(OverlayPage.BOOKMARKS)
            MenuItem.HISTORY -> onOpenOverlay(OverlayPage.HISTORY)
            MenuItem.DOWNLOADS -> onOpenOverlay(OverlayPage.DOWNLOADS)
            MenuItem.DATA_BACKUP -> onOpenOverlay(OverlayPage.DATA_BACKUPS)
            MenuItem.SETTINGS -> onOpenOverlay(OverlayPage.SETTINGS)
            MenuItem.REFRESH -> controller.reload()
            MenuItem.VIEW_SOURCE -> onViewSource()
            MenuItem.PRINT -> controller.printPage()
            MenuItem.READ_ALOUD -> controller.toggleReadAloud()
            MenuItem.COPY_URL -> copyToClipboard(context, controller.currentUrl)
            MenuItem.SHARE -> shareUrl(context, controller.currentUrl)
            MenuItem.DARK_TOGGLE -> ThemeController.set(context, if (isDark) 1 else 2)
            MenuItem.DESKTOP_MODE -> controller.toggleDesktopMode()
            MenuItem.CLEAR_CACHE -> controller.clearCacheOnly()
            MenuItem.HOME -> controller.goHome()
            MenuItem.CLOSE_TAB -> controller.tabList.firstOrNull { it.isActive }
                ?.let { controller.closeTab(it.id) }
        }
    }

    val rowHeight = 72.dp
    Box(Modifier.fillMaxSize()) {
        // 点击面板上方空白处关闭
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.12f))
                .pointerInput(onDismiss) {
                    detectTapGestures { onDismiss() }
                }
        )
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                // 面板空白区域不触发“点空白关闭”
                .pointerInput(Unit) { detectTapGestures { } },
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 12.dp
        ) {
            Column(Modifier.fillMaxWidth()) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.padding(top = 10.dp)
                ) { pageIndex ->
                    val rowsInPage = pages[pageIndex].chunked(MenuConfig.COLUMNS)
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .height(rowHeight * rows)
                    ) {
                        rowsInPage.forEach { row ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .height(rowHeight)
                            ) {
                                row.forEach { item ->
                                    Column(
                                        Modifier
                                            .weight(1f)
                                            .clickable { run(item) }
                                            .padding(vertical = 4.dp, horizontal = 2.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            menuIcon(item, isDark, controller.isSpeaking),
                                            contentDescription = menuLabel(item, isDark, controller.isSpeaking),
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(Modifier.height(5.dp))
                                        Text(
                                            menuLabel(item, isDark, controller.isSpeaking),
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            textAlign = TextAlign.Center,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                                if (row.size < MenuConfig.COLUMNS) {
                                    repeat(MenuConfig.COLUMNS - row.size) {
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                        // 选项不足整页时补空行，保持菜单面板高度固定，不缩小
                        repeat((rows - rowsInPage.size).coerceAtLeast(0)) {
                            Spacer(Modifier.height(rowHeight))
                        }
                    }
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (pages.size > 1) {
                        Row(horizontalArrangement = Arrangement.Center) {
                            repeat(pages.size) { index ->
                                Box(
                                    Modifier
                                        .padding(horizontal = 3.dp)
                                        .size(if (index == pagerState.currentPage) 7.dp else 5.dp)
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
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    if (text.isBlank()) return
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    manager.setPrimaryClip(ClipData.newPlainText("链接", text))
}

private fun shareUrl(context: Context, url: String) {
    if (url.isBlank()) return
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, url)
    }
    context.startActivity(Intent.createChooser(intent, "分享链接"))
}
