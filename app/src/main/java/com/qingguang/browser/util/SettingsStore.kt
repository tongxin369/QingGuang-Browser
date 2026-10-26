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

    companion object {
        private const val KEY_SEARCH_ENGINE = "search_engine"
        private const val KEY_AUTO_BACKUP = "auto_backup"
        private const val KEY_AUTO_BACKUP_GLOBAL = "auto_backup_global"
        private const val KEY_LAST_GLOBAL_BACKUP = "last_global_backup"
        private const val KEY_TEXT_ZOOM = "text_zoom"
        private const val KEY_DESKTOP_MODE = "desktop_mode"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_APP_THEME = "app_theme"
        private const val KEY_FOLLOW_PAGE_THEME = "follow_page_theme"
        private const val KEY_BACKUP_TREE_URI = "backup_tree_uri"
        private const val KEY_MENU_ORDER = "menu_order"
        private const val KEY_MENU_ROWS = "menu_rows"

        const val DEFAULT_SEARCH_ENGINE = "https://www.bing.com/search?q=%s"
        const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }
}
