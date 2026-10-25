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

    companion object {
        private const val KEY_SEARCH_ENGINE = "search_engine"
        private const val KEY_AUTO_BACKUP = "auto_backup"
        private const val KEY_TEXT_ZOOM = "text_zoom"
        private const val KEY_DESKTOP_MODE = "desktop_mode"
        private const val KEY_DARK_MODE = "dark_mode"

        const val DEFAULT_SEARCH_ENGINE = "https://www.bing.com/search?q=%s"
        const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }
}
