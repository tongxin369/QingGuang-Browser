package com.qingguang.browser.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.qingguang.browser.App
import com.qingguang.browser.BuildConfig
import com.qingguang.browser.ui.theme.ThemeController
import com.qingguang.browser.util.SettingsStore
import com.qingguang.browser.web.BrowserController

/** 设置目录分组 */
private enum class SettingsSection(
    val title: String,
    val subtitle: String,
    val icon: ImageVector
) {
    APPEARANCE("界面外观", "主题、工具栏配色、网页深浅色", Icons.Filled.Palette),
    BROWSING("浏览设置", "搜索引擎、字体大小、电脑模式", Icons.Filled.Language),
    MENU("菜单定制", "功能菜单排序与每页行数", Icons.Filled.Apps),
    HOME("首页", "快捷入口页数与摆放方式", Icons.Filled.Home),
    DATA("数据与备份", "自动备份、备份目录、清理数据", Icons.Filled.CloudUpload),
    PERMISSION("权限", "定位等系统权限", Icons.Filled.LocationOn),
    ABOUT("关于", "版本与产品信息", Icons.Filled.Info)
}

@Composable
fun SettingsPage(controller: BrowserController) {
    var section by remember { mutableStateOf<SettingsSection?>(null) }
    val current = section
    if (current == null) {
        SettingsDirectory(onOpen = { section = it })
    } else {
        BackHandler { section = null }
        SettingsDetail(current, controller, onBack = { section = null })
    }
}

@Composable
private fun SettingsDirectory(onOpen: (SettingsSection) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SettingsGroup("外观与浏览")
        SettingsSectionRow(SettingsSection.APPEARANCE, onOpen)
        SettingsSectionRow(SettingsSection.BROWSING, onOpen)
        SettingsGroup("功能与数据")
        SettingsSectionRow(SettingsSection.MENU, onOpen)
        SettingsSectionRow(SettingsSection.HOME, onOpen)
        SettingsSectionRow(SettingsSection.DATA, onOpen)
        SettingsGroup("系统")
        SettingsSectionRow(SettingsSection.PERMISSION, onOpen)
        SettingsSectionRow(SettingsSection.ABOUT, onOpen)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SettingsSectionRow(section: SettingsSection, onOpen: (SettingsSection) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onOpen(section) }
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(36.dp)
                .background(
                    MaterialTheme.colorScheme.secondaryContainer,
                    RoundedCornerShape(11.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                section.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(19.dp)
            )
        }
        Spacer(Modifier.size(13.dp))
        Column(Modifier.weight(1f)) {
            Text(
                section.title,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                section.subtitle,
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

@Composable
private fun SettingsDetail(
    section: SettingsSection,
    controller: BrowserController,
    onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回目录")
            }
            Text(
                section.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 28.dp)
        ) {
            when (section) {
                SettingsSection.APPEARANCE -> AppearanceSettings(controller)
                SettingsSection.BROWSING -> BrowsingSettings(controller)
                SettingsSection.MENU -> MenuSettings()
                SettingsSection.HOME -> HomeSettings()
                SettingsSection.DATA -> DataSettings(controller)
                SettingsSection.PERMISSION -> PermissionSettings()
                SettingsSection.ABOUT -> AboutSettings()
            }
        }
    }
}

@Composable
private fun SettingsGroup(title: String) {
    Text(
        title,
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 18.dp, top = 18.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsSwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SettingsRadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(
            label,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}

@Composable
private fun AppearanceSettings(controller: BrowserController) {
    val context = LocalContext.current
    val app = context.applicationContext as App
    var appTheme by remember { mutableStateOf(app.settings.appTheme) }
    var follow by remember { mutableStateOf(app.settings.followPageTheme) }

    SettingsGroup("日间 / 夜间模式（应用与网页统一）")
    SettingsRadioRow("跟随系统", appTheme == 0) {
        appTheme = 0
        ThemeController.set(context, 0)
        controller.onThemeModeChanged()
    }
    SettingsRadioRow("日间模式", appTheme == 1) {
        appTheme = 1
        ThemeController.set(context, 1)
        controller.onThemeModeChanged()
    }
    SettingsRadioRow("夜间模式（网页自动调色）", appTheme == 2) {
        appTheme = 2
        ThemeController.set(context, 2)
        controller.onThemeModeChanged()
    }
    Text(
        "夜间模式会同时切换应用配色与菜单图标，并给网页注入深色调色；无需再单独设置网页深浅色。",
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
    )

    SettingsGroup("工具栏")
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    SettingsSwitchRow("顶/底栏跟随网页主题色", follow) { value ->
        follow = value
        app.settings.followPageTheme = value
        controller.applyFollowPageTheme(value)
    }
}

@Composable
private fun BrowsingSettings(controller: BrowserController) {
    val context = LocalContext.current
    val app = context.applicationContext as App
    val engines = listOf(
        "必应" to "https://www.bing.com/search?q=%s",
        "百度" to "https://www.baidu.com/s?wd=%s",
        "Google" to "https://www.google.com/search?q=%s"
    )
    var engine by remember { mutableStateOf(app.settings.searchEngine) }
    var zoom by remember { mutableStateOf(app.settings.textZoom) }
    var desktop by remember { mutableStateOf(app.settings.desktopMode) }
    var orientation by remember { mutableStateOf(app.settings.screenOrientation) }
    var showTabSearch by remember { mutableStateOf(app.settings.showTabSearch) }
    var showTabRestore by remember { mutableStateOf(app.settings.showTabRestore) }
    var pullRefresh by remember { mutableStateOf(app.settings.pullRefresh) }
    var swipeNav by remember { mutableStateOf(app.settings.swipeNavigation) }
    var incognito by remember { mutableStateOf(app.settings.incognito) }
    var adBlock by remember { mutableStateOf(app.settings.adBlock) }
    var showCustomEngine by remember { mutableStateOf(false) }
    var customEngine by remember { mutableStateOf(app.settings.searchEngine) }
    var readerSize by remember { mutableStateOf(app.settings.readerFontSize) }
    var readerLine by remember { mutableStateOf(app.settings.readerLinePercent) }
    var adExtra by remember { mutableStateOf(app.settings.adBlockExtraHosts) }

    SettingsGroup("搜索引擎")
    engines.forEach { (name, url) ->
        SettingsRadioRow(name, engine == url) {
            engine = url
            app.settings.searchEngine = url
        }
    }
    if (engine !in engines.map { it.second }) {
        SettingsRadioRow("自定义：$engine", true) {}
    }
    TextButton(
        onClick = {
            customEngine = app.settings.searchEngine
            showCustomEngine = true
        },
        modifier = Modifier.padding(start = 10.dp)
    ) { Text("自定义搜索引擎…", fontSize = 13.sp) }

    SettingsGroup("网页字体大小")
    Text(
        "当前 ${zoom}%",
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.padding(horizontal = 18.dp)
    )
    Slider(
        value = zoom.toFloat(),
        onValueChange = {
            zoom = it.toInt()
            app.settings.textZoom = zoom
        },
        onValueChangeFinished = { controller.applySettings() },
        valueRange = 50f..200f,
        steps = 29,
        modifier = Modifier.padding(horizontal = 18.dp)
    )

    SettingsGroup("阅读模式排版")
    Text(
        "正文字号 ${readerSize}sp · 行距 ${"%.1f".format(readerLine / 100f)} 倍",
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.padding(horizontal = 18.dp)
    )
    Slider(
        value = readerSize.toFloat(),
        onValueChange = {
            readerSize = it.toInt()
            app.settings.readerFontSize = readerSize
        },
        valueRange = 14f..30f,
        steps = 15,
        modifier = Modifier.padding(horizontal = 18.dp)
    )
    Slider(
        value = readerLine.toFloat(),
        onValueChange = {
            readerLine = it.toInt()
            app.settings.readerLinePercent = readerLine
        },
        valueRange = 120f..260f,
        steps = 13,
        modifier = Modifier.padding(horizontal = 18.dp)
    )

    SettingsGroup("显示模式")
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    SettingsSwitchRow("电脑模式（桌面版网页）", desktop) { value ->
        desktop = value
        if (app.settings.desktopMode != value) controller.toggleDesktopMode()
    }

    SettingsGroup("屏幕方向")
    Row(
        Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        listOf("跟随系统", "自动", "竖屏", "横屏").forEachIndexed { index, label ->
            FilterChip(
                selected = orientation == index,
                onClick = {
                    orientation = index
                    app.settings.screenOrientation = index
                    (context as? Activity)?.requestedOrientation = when (index) {
                        1 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
                        2 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        3 -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    }
                },
                label = { Text(label, fontSize = 12.sp) }
            )
        }
    }

    SettingsGroup("标签页")
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    SettingsSwitchRow("标签面板显示搜索框", showTabSearch) { value ->
        showTabSearch = value
        app.settings.showTabSearch = value
    }
    SettingsSwitchRow("显示「恢复刚关闭的标签页」", showTabRestore) { value ->
        showTabRestore = value
        app.settings.showTabRestore = value
    }

    SettingsGroup("手势")
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    SettingsSwitchRow("下拉刷新网页", pullRefresh) { value ->
        pullRefresh = value
        app.settings.pullRefresh = value
    }
    SettingsSwitchRow("边缘侧滑前进 / 后退", swipeNav) { value ->
        swipeNav = value
        app.settings.swipeNavigation = value
    }

    SettingsGroup("隐私与拦截")
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    SettingsSwitchRow("无痕模式（不记录历史）", incognito) { value ->
        incognito = value
        app.settings.incognito = value
        if (!value) controller.clearIncognitoData()
        Toast.makeText(
            context,
            if (value) "已开启无痕模式，浏览历史不再记录" else "已关闭无痕模式，浏览数据已清理",
            Toast.LENGTH_SHORT
        ).show()
    }
    SettingsSwitchRow("拦截广告与弹窗", adBlock) { value ->
        adBlock = value
        app.settings.adBlock = value
        controller.applySettings()
    }
    OutlinedTextField(
        value = adExtra,
        onValueChange = {
            adExtra = it
            app.settings.adBlockExtraHosts = it
        },
        label = { Text("自定义拦截域名（每行一个，可留空）") },
        placeholder = { Text("ads.example.com") },
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 6.dp)
    )

    if (showCustomEngine) {
        AlertDialog(
            onDismissRequest = { showCustomEngine = false },
            title = { Text("自定义搜索引擎") },
            text = {
                Column {
                    OutlinedTextField(
                        value = customEngine,
                        onValueChange = { customEngine = it },
                        singleLine = true,
                        label = { Text("搜索地址") },
                        placeholder = { Text("https://example.com/search?q=%s") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "用 %s 代表搜索关键词。",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val url = customEngine.trim()
                        if (!url.contains("%s")) {
                            Toast.makeText(context, "地址中需要包含 %s", Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }
                        app.settings.searchEngine = url
                        engine = url
                        showCustomEngine = false
                    }
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showCustomEngine = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun MenuSettings() {
    val context = LocalContext.current
    val app = context.applicationContext as App
    var rows by remember { mutableStateOf(MenuConfig.rows(app.settings)) }
    val order = remember { mutableStateListOf<MenuItem>() }
    remember(app.settings.menuOrder) {
        order.clear()
        order.addAll(MenuConfig.currentOrder(app.settings))
    }

    SettingsGroup("每页行数（每行 5 个，超出左右滑动）")
    Row(
        Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        (2..5).forEach { value ->
            FilterChip(
                selected = rows == value,
                onClick = {
                    rows = value
                    app.settings.menuRows = value
                },
                label = { Text("$value 行") }
            )
        }
    }

    SettingsGroup("菜单排序")
    order.forEachIndexed { index, item ->
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                item.label,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = {
                    if (index > 0) {
                        order.removeAt(index)
                        order.add(index - 1, item)
                        MenuConfig.save(app.settings, order)
                    }
                },
                enabled = index > 0,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    Icons.Filled.KeyboardArrowUp,
                    contentDescription = "上移",
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(
                onClick = {
                    if (index < order.lastIndex) {
                        order.removeAt(index)
                        order.add(index + 1, item)
                        MenuConfig.save(app.settings, order)
                    }
                },
                enabled = index < order.lastIndex,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = "下移",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
    TextButton(
        onClick = {
            app.settings.menuOrder = null
            order.clear()
            order.addAll(MenuItem.entries)
        },
        modifier = Modifier.padding(start = 10.dp)
    ) { Text("恢复默认排序") }
}

@Composable
private fun HomeSettings() {
    val context = LocalContext.current
    val app = context.applicationContext as App
    var pages by remember { mutableStateOf(app.settings.homePageCount) }
    var compact by remember { mutableStateOf(app.settings.homeAutoCompact) }
    var wallpaper by remember { mutableStateOf(app.settings.homeWallpaper) }
    val wallpaperPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            app.settings.homeWallpaper = uri.toString()
            wallpaper = uri.toString()
            Toast.makeText(context, "主页壁纸已更新", Toast.LENGTH_SHORT).show()
        }
    }

    SettingsGroup("快捷入口页数（可自由扩展）")
    Row(
        Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = {
                if (pages > 2) {
                    pages--
                    app.settings.homePageCount = pages
                }
            },
            enabled = pages > 2
        ) {
            Icon(Icons.Filled.Remove, contentDescription = "减少页数", modifier = Modifier.size(20.dp))
        }
        Text("$pages 页", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        IconButton(
            onClick = {
                if (pages < 99) {
                    pages++
                    app.settings.homePageCount = pages
                }
            },
            enabled = pages < 99
        ) {
            Icon(Icons.Filled.Add, contentDescription = "增加页数", modifier = Modifier.size(20.dp))
        }
    }
    Text(
        "最少 2 页，可一直增加到 99 页；每页统一 4 列 × 5 行 = 20 个图标。减少页数不会删除图标，超出页数的图标会暂时隐藏。",
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)
    )

    SettingsGroup("摆放方式")
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    SettingsSwitchRow("自动补位", compact) { value ->
        compact = value
        app.settings.homeAutoCompact = value
    }
    Text(
        "开启：删除或移动图标后自动挤拢排列；关闭：可以拖动到任意空格，保留空位。",
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)
    )

    SettingsGroup("主页壁纸")
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (wallpaper == null) "纯色背景" else "已设置自定义壁纸",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = { wallpaperPicker.launch(arrayOf("image/*")) }) {
            Text(if (wallpaper == null) "选择图片" else "更换")
        }
        if (wallpaper != null) {
            TextButton(onClick = {
                app.settings.homeWallpaper = null
                wallpaper = null
            }) { Text("清除") }
        }
    }
}

@Composable
private fun DataSettings(controller: BrowserController) {
    val context = LocalContext.current
    val app = context.applicationContext as App
    var autoBackup by remember { mutableStateOf(app.settings.autoBackup) }
    var treeUri by remember { mutableStateOf(app.settings.backupTreeUri) }

    val dirPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            app.settings.backupTreeUri = uri.toString()
            treeUri = uri.toString()
            Toast.makeText(context, "备份目录已更新", Toast.LENGTH_SHORT).show()
        }
    }

    SettingsGroup("自动备份")
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    SettingsSwitchRow("切换后台/退出时自动备份", autoBackup) { value ->
        autoBackup = value
        app.settings.autoBackup = value
    }

    SettingsGroup("备份目录")
    Text(
        treeUri?.let { Uri.parse(it).lastPathSegment ?: it } ?: "应用私有目录（默认）",
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)
    )
    Row(Modifier.padding(horizontal = 10.dp)) {
        TextButton(onClick = { dirPicker.launch(null) }) { Text("选择目录") }
        if (treeUri != null) {
            TextButton(
                onClick = {
                    app.settings.backupTreeUri = null
                    treeUri = null
                }
            ) { Text("恢复默认") }
        }
    }

    SettingsGroup("数据清理")
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Row(Modifier.padding(horizontal = 10.dp)) {
        TextButton(onClick = {
            controller.clearCacheOnly()
            Toast.makeText(context, "缓存已清理", Toast.LENGTH_SHORT).show()
        }) { Text("清除缓存") }
        TextButton(onClick = {
            controller.clearSiteData()
            Toast.makeText(context, "Cookie 与站点数据已清除", Toast.LENGTH_SHORT).show()
        }) { Text("清除站点数据") }
    }

    SettingsGroup("立即备份")
    TextButton(
        onClick = {
            controller.backupCurrent { _, msg ->
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        },
        modifier = Modifier.padding(horizontal = 10.dp)
    ) { Text("备份当前页") }
}

@Composable
private fun PermissionSettings() {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasLocationPermission(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { result ->
        granted = result
        if (!result) {
            Toast.makeText(context, "未授予定位权限，网页无法获取位置", Toast.LENGTH_SHORT).show()
        }
    }

    SettingsGroup("定位权限")
    Text(
        if (granted) "已授予" else "未授予",
        fontSize = 13.sp,
        color = if (granted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
    )
    Row(Modifier.padding(horizontal = 10.dp)) {
        TextButton(onClick = {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }) { Text("申请定位权限") }
        TextButton(onClick = {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)
            )
            context.startActivity(intent)
        }) { Text("系统权限设置") }
    }

    SettingsGroup("说明")
    Text(
        "网页请求定位时，应用会使用系统定位权限。若不希望网页获取位置，保持未授予即可。",
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 18.dp)
    )
}

@Composable
private fun AboutSettings() {
    SettingsGroup("轻光浏览器")
    Text(
        "版本 ${BuildConfig.VERSION_NAME}",
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
    )
    Text(
        "本地网页数据使用应用内部固定地址加载，保证跨重启不丢失；切换后台时自动备份站点数据。",
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
    )
}

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
