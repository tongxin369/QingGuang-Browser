package com.qingguang.browser.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 书签文件夹；parentId = 0 表示根目录，可无限嵌套 */
@Entity(tableName = "bookmark_folders")
data class BookmarkFolder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val parentId: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    /** 手动拖动排序序号；0 表示尚未排序（按创建时间升序） */
    val sortOrder: Int = 0
)

@Entity(tableName = "bookmarks")
data class Bookmark(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val folderId: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    /** 手动拖动排序序号；0 表示尚未排序（按创建时间倒序） */
    val sortOrder: Int = 0,
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
    val iconText: String? = null,
    /** 所在首页分页：0 第一页，1 第二页（仅对 parentId = 0 的根图标有效） */
    val page: Int = 0,
    /** 页内格子位置（4 列）：-1 表示未指定，仅对根图标有效 */
    val slot: Int = -1
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
    val lastOpenedAt: Long = System.currentTimeMillis(),
    /** 手机原始文件/目录的 SAF URI；null 表示未关联原文件（仅应用内副本） */
    val sourceUri: String? = null,
    /** "file" 单个文件；"tree" 整个目录 */
    val sourceKind: String? = null
)

/** 离线保存的网页包：HTML + 图片/CSS/JS 一起放在 filesDir/offline/<dirName> */
@Entity(tableName = "offline_pages")
data class OfflinePage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val dirName: String,
    val totalBytes: Long = 0,
    val createdAt: Long = System.currentTimeMillis()
)

/** 操作审计日志：权限放行/拦截、下载、弹窗等关键动作，全部记录便于回溯 */
@Entity(tableName = "audit_logs")
data class AuditLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 动作类型：定位 / 摄像头 / 弹窗 / 下载 / 脚本 / 图片 等 */
    val action: String,
    /** 站点域名；为空表示全局动作 */
    val host: String = "",
    /** 结果：允许 / 拒绝 / 拦截 / 完成 */
    val result: String,
    /** 说明文字 */
    val detail: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "downloads")
data class DownloadRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val url: String,
    val localPath: String?,
    val status: String,
    val createdAt: Long = System.currentTimeMillis(),
    /** 系统下载管理器任务 id；-1 表示非下载管理器任务（data / blob） */
    val managerId: Long = -1,
    /** 文件总大小（字节）；-1 表示未知 */
    val totalBytes: Long = -1,
    val mimeType: String = "",
    /** 应用自己写盘后的 content:// 地址，用于打开文件 */
    val localUri: String? = null
)
