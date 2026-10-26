package com.qingguang.browser.ui

import com.qingguang.browser.util.SettingsStore

/** 功能菜单里可配置的菜单项 */
enum class MenuItem(val key: String, val label: String) {
    ADD_BOOKMARK("add_bookmark", "添加书签"),
    BACKUP_PAGE("backup_page", "备份此页"),
    BOOKMARKS("bookmarks", "书签"),
    HISTORY("history", "历史"),
    DOWNLOADS("downloads", "下载"),
    DATA_BACKUP("data_backup", "数据备份"),
    SETTINGS("settings", "设置"),
    REFRESH("refresh", "刷新"),
    FIND("find", "页内查找"),
    VIEW_SOURCE("view_source", "网页源码"),
    PRINT("print", "打印 / PDF"),
    READ_ALOUD("read_aloud", "朗读网页"),
    COPY_URL("copy_url", "复制链接"),
    SHARE("share", "分享"),
    DARK_TOGGLE("dark_toggle", "夜间模式"),
    DESKTOP_MODE("desktop_mode", "电脑模式"),
    SCREEN_ORIENTATION("screen_orientation", "屏幕方向"),
    READER_MODE("reader_mode", "阅读模式"),
    OFFLINE_SAVE("offline_save", "保存网页"),
    DOWNLOAD_IMAGES("download_images", "下载图片"),
    OFFLINE_LIST("offline_list", "离线网页"),
    AD_BLOCK("ad_block", "广告拦截"),
    INCOGNITO("incognito", "无痕模式"),
    CLEAR_CACHE("clear_cache", "清除缓存"),
    HOME("home", "主页"),
    CLOSE_TAB("close_tab", "关闭标签")
}

object MenuConfig {
    /** 菜单每行固定 5 个 */
    const val COLUMNS = 5

    /** 每页行数（默认 3，可在设置中调整），超出部分左右滑动分页 */
    fun rows(settings: SettingsStore): Int = settings.menuRows.coerceIn(2, 5)

    fun pageSize(settings: SettingsStore): Int = COLUMNS * rows(settings)

    fun currentOrder(settings: SettingsStore): List<MenuItem> {
        val raw = settings.menuOrder
        if (raw.isNullOrBlank()) return MenuItem.entries.toList()
        val saved = raw.split(',')
            .mapNotNull { key -> MenuItem.entries.firstOrNull { it.key == key } }
        // 旧版本保存的顺序可能缺少新增项，统一补在末尾
        return saved + MenuItem.entries.filter { it !in saved }
    }

    fun save(settings: SettingsStore, order: List<MenuItem>) {
        settings.menuOrder = order.joinToString(",") { it.key }
    }
}
