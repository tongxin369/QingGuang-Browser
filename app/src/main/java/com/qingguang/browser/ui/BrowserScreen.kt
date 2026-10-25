package com.qingguang.browser.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
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

    var address by remember { mutableStateOf("") }
    var overlay by remember { mutableStateOf<OverlayPage?>(null) }
    var showTabs by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

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
                onMenu = { showMenu = true },
                progress = controller.progress,
                colors = bars
            )
        }
        Box(Modifier.weight(1f)) {
            WebViewHost(controller)
            if (controller.showHome) {
                HomePage(controller = controller, onOpenOverlay = { overlay = it })
            }
        }
        BottomBar(
            controller = controller,
            onShowTabs = { showTabs = true },
            onMenu = { showMenu = true },
            colors = bars
        )
    }

    if (showTabs) {
        TabSwitcherScreen(controller = controller, onDismiss = { showTabs = false })
    }
    if (showMenu) {
        MenuSheet(
            controller = controller,
            onDismiss = { showMenu = false },
            onOpenOverlay = {
                showMenu = false
                overlay = it
            }
        )
    }
    overlay?.let { page ->
        OverlayPageHost(
            page = page,
            controller = controller,
            onClose = { overlay = null }
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
    onMenu: () -> Unit,
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
            IconButton(onClick = onMenu, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Filled.MoreVert, contentDescription = "菜单", tint = colors.content)
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
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                desc = "后退",
                enabled = controller.canGoBack,
                content = colors.content
            ) { controller.goBack() }
            BarIcon(
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                desc = "前进",
                enabled = controller.canGoForward,
                content = colors.content
            ) { controller.goForward() }
            BarIcon(
                icon = Icons.Filled.Home,
                desc = "主页",
                enabled = true,
                content = colors.content
            ) { controller.goHome() }
            IconButton(onClick = onShowTabs) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Tab, contentDescription = "标签页", tint = colors.content)
                    Text(
                        text = controller.tabCount.toString(),
                        fontSize = 9.sp,
                        color = colors.container,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .background(colors.content, CircleShape)
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
            BarIcon(
                icon = Icons.Filled.MoreVert,
                desc = "菜单",
                enabled = true,
                content = colors.content
            ) { onMenu() }
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

/** Via 风格标签页：整屏列表，底部整行「+」新建空白页 */
@Composable
private fun TabSwitcherScreen(controller: BrowserController, onDismiss: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "标签页",
                    fontSize = 19.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "关闭", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            LazyColumn(Modifier.weight(1f)) {
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
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
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
                    tint = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(6.dp))
                Text("新建标签页", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
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

private fun tabCircleColor(seed: String): Color {
    if (seed.isBlank()) return Color(0xFF9AA1AC)
    return circlePalette[Math.abs(seed.hashCode()) % circlePalette.size]
}

private fun tabInitial(title: String, url: String): String {
    val source = title.trim().ifBlank { url.trim() }
    if (source.isBlank()) return "新"
    return source.take(1).uppercase()
}

private data class MenuAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MenuSheet(
    controller: BrowserController,
    onDismiss: () -> Unit,
    onOpenOverlay: (OverlayPage) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as App
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val systemDark = isSystemInDarkTheme()
    val isDark = when (ThemeController.mode) {
        1 -> false
        2 -> true
        else -> systemDark
    }

    val actions = listOf(
        MenuAction("书签", Icons.Filled.BookmarkBorder) { onOpenOverlay(OverlayPage.BOOKMARKS) },
        MenuAction("历史", Icons.Filled.History) { onOpenOverlay(OverlayPage.HISTORY) },
        MenuAction("下载", Icons.Filled.Download) { onOpenOverlay(OverlayPage.DOWNLOADS) },
        MenuAction("备份", Icons.Filled.Backup) { onOpenOverlay(OverlayPage.DATA_BACKUPS) },
        MenuAction("设置", Icons.Filled.Settings) { onOpenOverlay(OverlayPage.SETTINGS) },
        MenuAction("刷新", Icons.Filled.Refresh) { controller.reload() },
        MenuAction("复制链接", Icons.Filled.ContentCopy) {
            copyToClipboard(context, controller.currentUrl)
        },
        MenuAction("分享", Icons.Filled.Share) {
            shareUrl(context, controller.currentUrl)
        },
        MenuAction(if (isDark) "日间模式" else "夜间模式",
            if (isDark) Icons.Filled.LightMode else Icons.Filled.DarkMode
        ) { ThemeController.set(context, if (isDark) 1 else 2) }
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 20.dp)
        ) {
            Text(
                "菜单",
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
            )
            actions.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { action ->
                        Column(
                            Modifier
                                .weight(1f)
                                .clickable {
                                    action.onClick()
                                    onDismiss()
                                }
                                .padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                action.icon,
                                contentDescription = action.label,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                action.label,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (row.size < 4) {
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }

            HorizontalDivider(
                Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("顶/底栏跟随网页主题色", fontSize = 13.sp)
                    Text(
                        "取网页 theme-color 或背景色",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = controller.followPageTheme,
                    onCheckedChange = { controller.applyFollowPageTheme(it) }
                )
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        val url = controller.currentUrl
                        if (url.isNotBlank()) {
                            scope.launch {
                                if (app.database.bookmarkDao().countByUrl(url) == 0) {
                                    app.database.bookmarkDao().insert(
                                        com.qingguang.browser.data.Bookmark(
                                            title = controller.currentTitle,
                                            url = url
                                        )
                                    )
                                }
                            }
                        }
                        onDismiss()
                    }
                ) { Text("收藏当前页") }
                TextButton(onClick = { onOpenOverlay(OverlayPage.DATA_BACKUPS) }) {
                    Text("备份当前页")
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
