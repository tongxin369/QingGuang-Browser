package com.qingguang.browser.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 书签文件夹；parentId = 0 表示根目录，可无限嵌套 */
@Entity(tableName = "bookmark_folders")
data class BookmarkFolder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val parentId: Long = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "bookmarks")
data class Bookmark(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val folderId: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    /** 自定义图标颜色（ARGB）；null 表示按标题自动配色 */
    val iconColor: Int? = null,
    /** 自定义图标文字（1-2 字）；null 表示自动取标题首字 */
    val iconText: String? = null
)

/** 首页快捷入口：isFolder=false 为网页图标；为 true 时作为分组合并多个图标（parentId 指向它） */
@Entity(tableName = "home_items")
data class HomeItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String = "",
    val parentId: Long = 0,
    val isFolder: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val iconColor: Int? = null,
    val iconText: String? = null
)

@Entity(tableName = "history")
data class HistoryItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val visitedAt: Long = System.currentTimeMillis()
)

/** 导入到应用内部存储的本地网页站点，记录稳定入口地址，下次可直接打开 */
@Entity(tableName = "local_pages")
data class LocalPage(
    @PrimaryKey val siteId: String,
    val displayName: String,
    val entryUrl: String,
    val lastOpenedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "downloads")
data class DownloadRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val url: String,
    val localPath: String?,
    val status: String,
    val createdAt: Long = System.currentTimeMillis()
)
