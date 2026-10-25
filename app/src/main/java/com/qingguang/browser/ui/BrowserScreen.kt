package com.qingguang.browser.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
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
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.qingguang.browser.App
import com.qingguang.browser.web.BrowserController
import kotlinx.coroutines.launch

enum class OverlayPage(val title: String) {
    BOOKMARKS("书签"),
    HISTORY("历史"),
    DOWNLOADS("下载"),
    BACKUPS("数据备份"),
    SETTINGS("设置")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(controller: BrowserController) {
    val context = LocalContext.current
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
                progress = controller.progress
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
            onMenu = { showMenu = true }
        )
    }

    if (showTabs) {
        TabSwitcherSheet(controller = controller, onDismiss = { showTabs = false })
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
    progress: Float
) {
    Surface(tonalElevation = 2.dp) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                    shape = RoundedCornerShape(21.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    BasicTextField(
                        value = address,
                        onValueChange = onAddressChange,
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { onSubmit() }),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp),
                        decorationBox = { inner ->
                            Box(
                                Modifier.fillMaxSize(),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (address.isBlank()) {
                                    Text(
                                        "搜索或输入网址",
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                                inner()
                            }
                        }
                    )
                }
                IconButton(onClick = onReload) {
                    Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                }
                IconButton(onClick = onMenu) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "菜单")
                }
            }
            if (progress > 0.01f && progress < 0.99f) {
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                )
            }
        }
    }
}

@Composable
private fun BottomBar(
    controller: BrowserController,
    onShowTabs: () -> Unit,
    onMenu: () -> Unit
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { controller.goBack() }, enabled = controller.canGoBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "后退")
            }
            IconButton(onClick = { controller.goForward() }, enabled = controller.canGoForward) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "前进")
            }
            IconButton(onClick = { controller.goHome() }) {
                Icon(Icons.Filled.Home, contentDescription = "主页")
            }
            IconButton(onClick = onShowTabs) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Tab, contentDescription = "标签页")
                    Text(
                        text = controller.tabCount.toString(),
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .padding(horizontal = 3.dp, vertical = 1.dp)
                    )
                }
            }
            IconButton(onClick = onMenu) {
                Icon(Icons.Filled.MoreVert, contentDescription = "菜单")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabSwitcherSheet(controller: BrowserController, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("标签页", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = {
                    controller.newTab()
                    onDismiss()
                }) { Text("新建标签页") }
            }
            if (controller.tabList.isEmpty()) {
                Text(
                    "还没有打开的标签页",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            }
            controller.tabList.forEach { tab ->
                ListItem(
                    headlineContent = {
                        Text(tab.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    supportingContent = {
                        Text(
                            tab.url.ifBlank { "空白标签页" },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingContent = { Icon(Icons.Filled.Tab, contentDescription = null) },
                    trailingContent = {
                        IconButton(onClick = { controller.closeTab(tab.id) }) {
                            Icon(Icons.Filled.Close, contentDescription = "关闭")
                        }
                    },
                    colors = if (tab.isActive) {
                        ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    } else {
                        ListItemDefaults.colors()
                    },
                    modifier = Modifier.clickable {
                        controller.switchTab(tab.id)
                        onDismiss()
                    }
                )
            }
        }
    }
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

    val actions = listOf(
        MenuAction("书签", Icons.Filled.BookmarkBorder) { onOpenOverlay(OverlayPage.BOOKMARKS) },
        MenuAction("历史", Icons.Filled.History) { onOpenOverlay(OverlayPage.HISTORY) },
        MenuAction("下载", Icons.Filled.Download) { onOpenOverlay(OverlayPage.DOWNLOADS) },
        MenuAction("备份", Icons.Filled.Backup) { onOpenOverlay(OverlayPage.BACKUPS) },
        MenuAction("设置", Icons.Filled.Settings) { onOpenOverlay(OverlayPage.SETTINGS) },
        MenuAction("刷新", Icons.Filled.Refresh) { controller.reload() },
        MenuAction("复制链接", Icons.Filled.ContentCopy) {
            copyToClipboard(context, controller.currentUrl)
        },
        MenuAction("分享", Icons.Filled.Share) {
            shareUrl(context, controller.currentUrl)
        }
    )

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Text("菜单", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
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
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                action.icon,
                                contentDescription = action.label,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(action.label, fontSize = 12.sp)
                        }
                    }
                    if (row.size < 4) {
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Row(Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = {
                        val url = controller.currentUrl
                        if (url.isBlank()) {
                            onDismiss()
                            return@TextButton
                        }
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
                        onDismiss()
                    }
                ) { Text("收藏当前页") }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { onOpenOverlay(OverlayPage.BACKUPS) }) {
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
