package com.qingguang.browser.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 书签文件夹；folderId = 0 表示未分类（根） */
@Entity(tableName = "bookmark_folders")
data class BookmarkFolder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "bookmarks")
data class Bookmark(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val folderId: Long = 0,
    val createdAt: Long = System.currentTimeMillis()
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
