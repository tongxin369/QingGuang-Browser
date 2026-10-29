package com.qingguang.browser.util

import android.content.Context

/** 简单设置存储（MVP 阶段用 SharedPreferences，后续设置项增多可迁移 DataStore） */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("browser_settings", Context.MODE_PRIVATE)

    /** 默认搜索引擎地址，%s 会被替换为关键词 */
    var searchEngine: String
        get() = prefs.getString(KEY_SEARCH_ENGINE, DEFAULT_SEARCH_ENGINE) ?: DEFAULT_SEARCH_ENGINE
        set(value) = prefs.edit().putString(KEY_SEARCH_ENGINE, value).apply()

    /** 退出/切后台时是否自动备份当前站点数据 */
    var autoBackup: Boolean
        get() = prefs.getBoolean(KEY_AUTO_BACKUP, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_BACKUP, value).apply()

    /** 退出/切后台时是否自动备份全局数据（设置、书签、快捷入口、历史等） */
    var autoBackupGlobal: Boolean
        get() = prefs.getBoolean(KEY_AUTO_BACKUP_GLOBAL, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_BACKUP_GLOBAL, value).apply()

    /** 最近一次全局自动备份时间，用于限制自动备份频率 */
    var lastGlobalBackupAt: Long
        get() = prefs.getLong(KEY_LAST_GLOBAL_BACKUP, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_GLOBAL_BACKUP, value).apply()

    /** 全局数据备份最多保留的份数 */
    var globalBackupKeep: Int
        get() = prefs.getInt(KEY_GLOBAL_BACKUP_KEEP, 10).coerceIn(1, 50)
        set(value) = prefs.edit().putInt(KEY_GLOBAL_BACKUP_KEEP, value.coerceIn(1, 50)).apply()

    /** 首页快捷入口页数（含第一页），默认 2 页，可按需扩展（上限 99 页） */
    var homePageCount: Int
        get() = prefs.getInt(KEY_HOME_PAGE_COUNT, 2).coerceIn(2, 99)
        set(value) = prefs.edit().putInt(KEY_HOME_PAGE_COUNT, value.coerceIn(2, 99)).apply()

    /** 快捷入口是否自动补位（关闭后拖动可以留空，位置不自动挤拢） */
    var homeAutoCompact: Boolean
        get() = prefs.getBoolean(KEY_HOME_AUTO_COMPACT, true)
        set(value) = prefs.edit().putBoolean(KEY_HOME_AUTO_COMPACT, value).apply()

    /** 字体缩放百分比，100 为基准 */
    var textZoom: Int
        get() = prefs.getInt(KEY_TEXT_ZOOM, 100)
        set(value) = prefs.edit().putInt(KEY_TEXT_ZOOM, value).apply()

    /** 电脑模式：改写 UA */
    var desktopMode: Boolean
        get() = prefs.getBoolean(KEY_DESKTOP_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_DESKTOP_MODE, value).apply()

    /** 0 跟随系统，1 强制深色网页，2 始终保持浅色网页 */
    var darkMode: Int
        get() = prefs.getInt(KEY_DARK_MODE, 0)
        set(value) = prefs.edit().putInt(KEY_DARK_MODE, value).apply()

    /** 应用界面外观：0 跟随系统，1 浅色，2 深色 */
    var appTheme: Int
        get() = prefs.getInt(KEY_APP_THEME, 0)
        set(value) = prefs.edit().putInt(KEY_APP_THEME, value).apply()

    /** 夜间网页亮度百分比（70–130，默认 100） */
    var nightBrightness: Int
        get() = prefs.getInt(KEY_NIGHT_BRIGHTNESS, 100).coerceIn(70, 130)
        set(value) = prefs.edit().putInt(KEY_NIGHT_BRIGHTNESS, value.coerceIn(70, 130)).apply()

    /** 夜间网页对比度百分比（70–130，默认 100） */
    var nightContrast: Int
        get() = prefs.getInt(KEY_NIGHT_CONTRAST, 100).coerceIn(70, 130)
        set(value) = prefs.edit().putInt(KEY_NIGHT_CONTRAST, value.coerceIn(70, 130)).apply()

    /** 标签页数量上限；0 表示不限制（默认不限制，保持自由度） */
    var tabLimit: Int
        get() = prefs.getInt(KEY_TAB_LIMIT, 0).coerceIn(0, 99)
        set(value) = prefs.edit().putInt(KEY_TAB_LIMIT, value.coerceIn(0, 99)).apply()

    /** 屏幕方向：0 跟随系统，1 自动旋转，2 竖屏，3 横屏 */
    var screenOrientation: Int
        get() = prefs.getInt(KEY_SCREEN_ORIENTATION, 0).coerceIn(0, 3)
        set(value) = prefs.edit().putInt(KEY_SCREEN_ORIENTATION, value.coerceIn(0, 3)).apply()

    /** 顶部地址栏与底部功能栏是否跟随网页主题色 */
    var followPageTheme: Boolean
        get() = prefs.getBoolean(KEY_FOLLOW_PAGE_THEME, true)
        set(value) = prefs.edit().putBoolean(KEY_FOLLOW_PAGE_THEME, value).apply()

    /** 自定义备份目录（SAF tree uri）；为空表示使用应用私有目录 */
    var backupTreeUri: String?
        get() = prefs.getString(KEY_BACKUP_TREE_URI, null)
        set(value) = prefs.edit().putString(KEY_BACKUP_TREE_URI, value).apply()

    /** 功能菜单顺序（逗号分隔的菜单项 key）；null 表示使用默认顺序 */
    var menuOrder: String?
        get() = prefs.getString(KEY_MENU_ORDER, null)
        set(value) = prefs.edit().putString(KEY_MENU_ORDER, value).apply()

    /** 功能菜单每页行数（每行 5 个），超出部分左右滑动分页 */
    var menuRows: Int
        get() = prefs.getInt(KEY_MENU_ROWS, 3)
        set(value) = prefs.edit().putInt(KEY_MENU_ROWS, value).apply()

    /** 下载文件在「下载」目录下的子目录；空字符串表示直接放在下载根目录 */
    var downloadSubDir: String
        get() = prefs.getString(KEY_DOWNLOAD_SUB_DIR, "") ?: ""
        set(value) = prefs.edit().putString(KEY_DOWNLOAD_SUB_DIR, value.trim().trim('/')).apply()

    /** 标签面板是否显示搜索框 */
    var showTabSearch: Boolean
        get() = prefs.getBoolean(KEY_SHOW_TAB_SEARCH, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_TAB_SEARCH, value).apply()

    /** 标签面板是否显示「恢复刚关闭的标签页」入口 */
    var showTabRestore: Boolean
        get() = prefs.getBoolean(KEY_SHOW_TAB_RESTORE, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_TAB_RESTORE, value).apply()

    /** 下拉刷新 */
    var pullRefresh: Boolean
        get() = prefs.getBoolean(KEY_PULL_REFRESH, true)
        set(value) = prefs.edit().putBoolean(KEY_PULL_REFRESH, value).apply()

    /** 左右边缘侧滑前进 / 后退 */
    var swipeNavigation: Boolean
        get() = prefs.getBoolean(KEY_SWIPE_NAV, false)
        set(value) = prefs.edit().putBoolean(KEY_SWIPE_NAV, value).apply()

    /** 广告与弹窗拦截 */
    var adBlock: Boolean
        get() = prefs.getBoolean(KEY_AD_BLOCK, false)
        set(value) = prefs.edit().putBoolean(KEY_AD_BLOCK, value).apply()

    /** 无痕模式：不记录历史、不参与自动备份 */
    var incognito: Boolean
        get() = prefs.getBoolean(KEY_INCOGNITO, false)
        set(value) = prefs.edit().putBoolean(KEY_INCOGNITO, value).apply()

    /** 主页壁纸（content uri）；空表示使用纯色背景 */
    var homeWallpaper: String?
        get() = prefs.getString(KEY_HOME_WALLPAPER, null)
        set(value) = prefs.edit().putString(KEY_HOME_WALLPAPER, value).apply()

    /** 标签面板最大高度（dp）。默认 370，可调到接近网页区域高度；面板本身会按可用高度再收敛 */
    var tabPanelHeight: Int
        get() = prefs.getInt(KEY_TAB_PANEL_HEIGHT, 370).coerceIn(220, 720)
        set(value) = prefs.edit().putInt(KEY_TAB_PANEL_HEIGHT, value.coerceIn(220, 720)).apply()

    /** 夜间强制深色网页（注入 CSS 反色） */
    var nightWeb: Boolean
        get() = prefs.getBoolean(KEY_NIGHT_WEB, false)
        set(value) = prefs.edit().putBoolean(KEY_NIGHT_WEB, value).apply()

    /** 夜间网页色温（0–100，默认 0 不偏色；数值越大越暖） */
    var nightWarmth: Int
        get() = prefs.getInt(KEY_NIGHT_WARMTH, 0).coerceIn(0, 100)
        set(value) = prefs.edit().putInt(KEY_NIGHT_WARMTH, value.coerceIn(0, 100)).apply()

    /** 站点记忆的夜间色温（JSON 对象：host -> 数值） */
    var siteNightWarmthJson: String
        get() = prefs.getString(KEY_SITE_NIGHT_WARMTH, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_SITE_NIGHT_WARMTH, value).apply()

    fun nightWarmthFor(host: String): Int {
        val key = host.trim().lowercase()
        if (key.isNotBlank()) {
            runCatching {
                val obj = org.json.JSONObject(siteNightWarmthJson)
                if (obj.has(key)) return obj.optInt(key, nightWarmth).coerceIn(0, 100)
            }
        }
        return nightWarmth
    }

    /** 记住该站点的夜间色温；value < 0 表示清除该站点记忆 */
    fun setNightWarmthFor(host: String, value: Int) {
        val key = host.trim().lowercase()
        if (key.isBlank()) {
            if (value >= 0) nightWarmth = value
            return
        }
        val obj = runCatching { org.json.JSONObject(siteNightWarmthJson) }
            .getOrDefault(org.json.JSONObject())
        if (value < 0) obj.remove(key) else obj.put(key, value.coerceIn(0, 100))
        siteNightWarmthJson = obj.toString()
    }

    /** 广告拦截自定义域名，换行/逗号分隔 */
    var adBlockExtraHosts: String
        get() = prefs.getString(KEY_AD_EXTRA_HOSTS, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_AD_EXTRA_HOSTS, value).apply()

    /** 阅读模式正文字号（sp） */
    var readerFontSize: Int
        get() = prefs.getInt(KEY_READER_FONT_SIZE, 19)
        set(value) = prefs.edit().putInt(KEY_READER_FONT_SIZE, value).apply()

    /** 阅读模式行高（百分比，190 表示 1.9 倍） */
    var readerLinePercent: Int
        get() = prefs.getInt(KEY_READER_LINE_PERCENT, 190)
        set(value) = prefs.edit().putInt(KEY_READER_LINE_PERCENT, value).apply()

    /** 站点级权限矩阵（JSON 数组字符串） */
    var sitePermissionsJson: String
        get() = prefs.getString(KEY_SITE_PERMISSIONS, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_SITE_PERMISSIONS, value).apply()

    /** 站点记忆的夜间亮度/对比度（JSON 对象：host -> "亮度,对比度"） */
    var siteNightTuningJson: String
        get() = prefs.getString(KEY_SITE_NIGHT_TUNING, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_SITE_NIGHT_TUNING, value).apply()

    /** 夜间网页里的图片亮度（%），与整页反色分开调整 */
    var nightImageBrightness: Int
        get() = prefs.getInt(KEY_NIGHT_IMAGE_BRIGHTNESS, 100)
        set(value) = prefs.edit()
            .putInt(KEY_NIGHT_IMAGE_BRIGHTNESS, value.coerceIn(50, 150))
            .apply()

    /** 夜间"不反色"站点列表（逗号分隔）：这些站点保留原配色，只做轻微调暗 */
    var nightNoInvertHosts: String
        get() = prefs.getString(KEY_NIGHT_NO_INVERT, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_NIGHT_NO_INVERT, value).apply()

    fun isNightNoInvert(host: String): Boolean {
        val key = host.trim().lowercase()
        if (key.isBlank()) return false
        return nightNoInvertHosts
            .split(',')
            .map { it.trim() }
            .any { it.isNotBlank() && it == key }
    }

    /** 「不反色」站点清单，供设置页批量管理 */
    fun nightNoInvertList(): List<String> = nightNoInvertHosts
        .split(',')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()

    fun clearNightNoInvertHosts() {
        nightNoInvertHosts = ""
    }

    fun setNightNoInvert(host: String, enabled: Boolean) {
        val key = host.trim().lowercase()
        if (key.isBlank()) return
        val set = nightNoInvertHosts
            .split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toMutableSet()
        if (enabled) set.add(key) else set.remove(key)
        nightNoInvertHosts = set.joinToString(",")
    }

    /** 取某站点的夜间调色参数；该站点没有单独记忆时回退到全局值 */
    fun nightTuningFor(host: String): Pair<Int, Int> {
        val key = host.trim().lowercase()
        if (key.isNotBlank()) {
            runCatching {
                val obj = org.json.JSONObject(siteNightTuningJson)
                val raw = obj.optString(key, "")
                if (raw.isNotBlank()) {
                    val parts = raw.split(',')
                    val b = parts.getOrNull(0)?.trim()?.toIntOrNull()
                    val c = parts.getOrNull(1)?.trim()?.toIntOrNull()
                    if (b != null && c != null) {
                        return b.coerceIn(70, 130) to c.coerceIn(70, 130)
                    }
                }
            }
        }
        return nightBrightness to nightContrast
    }

    /** 记住该站点的夜间调色；亮度传 0 表示清除该站点记忆 */
    fun setNightTuningFor(host: String, brightness: Int, contrast: Int) {
        val key = host.trim().lowercase()
        if (key.isBlank()) {
            if (brightness > 0) {
                nightBrightness = brightness
                nightContrast = contrast
            }
            return
        }
        val obj = runCatching { org.json.JSONObject(siteNightTuningJson) }
            .getOrDefault(org.json.JSONObject())
        if (brightness <= 0) obj.remove(key) else obj.put(key, "$brightness,$contrast")
        siteNightTuningJson = obj.toString()
    }

    /** 该站点是否单独记忆过夜间亮度或色温 */
    fun hasSiteNightTuning(host: String): Boolean {
        val key = host.trim().lowercase()
        if (key.isBlank()) return false
        val hasBrightness = runCatching {
            org.json.JSONObject(siteNightTuningJson).optString(key, "").isNotBlank()
        }.getOrDefault(false)
        if (hasBrightness) return true
        return runCatching {
            org.json.JSONObject(siteNightWarmthJson).has(key)
        }.getOrDefault(false)
    }

    companion object {
        private const val KEY_SEARCH_ENGINE = "search_engine"
        private const val KEY_AUTO_BACKUP = "auto_backup"
        private const val KEY_AUTO_BACKUP_GLOBAL = "auto_backup_global"
        private const val KEY_LAST_GLOBAL_BACKUP = "last_global_backup"
        private const val KEY_GLOBAL_BACKUP_KEEP = "global_backup_keep"
        private const val KEY_HOME_PAGE_COUNT = "home_page_count"
        private const val KEY_HOME_AUTO_COMPACT = "home_auto_compact"
        private const val KEY_TEXT_ZOOM = "text_zoom"
        private const val KEY_DESKTOP_MODE = "desktop_mode"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_APP_THEME = "app_theme"
        private const val KEY_NIGHT_BRIGHTNESS = "night_brightness"
        private const val KEY_NIGHT_CONTRAST = "night_contrast"
        private const val KEY_SCREEN_ORIENTATION = "screen_orientation"
        private const val KEY_TAB_LIMIT = "tab_limit"
        private const val KEY_FOLLOW_PAGE_THEME = "follow_page_theme"
        private const val KEY_BACKUP_TREE_URI = "backup_tree_uri"
        private const val KEY_MENU_ORDER = "menu_order"
        private const val KEY_MENU_ROWS = "menu_rows"
        private const val KEY_DOWNLOAD_SUB_DIR = "download_sub_dir"
        private const val KEY_SHOW_TAB_SEARCH = "show_tab_search"
        private const val KEY_SHOW_TAB_RESTORE = "show_tab_restore"
        private const val KEY_PULL_REFRESH = "pull_refresh"
        private const val KEY_SWIPE_NAV = "swipe_nav"
        private const val KEY_AD_BLOCK = "ad_block"
        private const val KEY_INCOGNITO = "incognito"
        private const val KEY_HOME_WALLPAPER = "home_wallpaper"
        private const val KEY_NIGHT_WEB = "night_web"
        private const val KEY_AD_EXTRA_HOSTS = "ad_extra_hosts"
        private const val KEY_READER_FONT_SIZE = "reader_font_size"
        private const val KEY_READER_LINE_PERCENT = "reader_line_percent"
        private const val KEY_SITE_PERMISSIONS = "site_permissions"
        private const val KEY_SITE_NIGHT_TUNING = "site_night_tuning"
        private const val KEY_NIGHT_IMAGE_BRIGHTNESS = "night_image_brightness"
        private const val KEY_NIGHT_NO_INVERT = "night_no_invert"
        private const val KEY_TAB_PANEL_HEIGHT = "tab_panel_height"
        private const val KEY_NIGHT_WARMTH = "night_warmth"
        private const val KEY_SITE_NIGHT_WARMTH = "site_night_warmth"

        const val DEFAULT_SEARCH_ENGINE = "https://www.bing.com/search?q=%s"
        const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }
}
