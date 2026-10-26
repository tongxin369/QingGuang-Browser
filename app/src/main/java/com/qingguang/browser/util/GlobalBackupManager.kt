package com.qingguang.browser.util

import com.qingguang.browser.data.AppDatabase
import com.qingguang.browser.data.Bookmark
import com.qingguang.browser.data.BookmarkFolder
import com.qingguang.browser.data.HistoryItem
import com.qingguang.browser.data.HomeItem
import com.qingguang.browser.data.LocalPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * 全局数据备份：设置 + 书签/文件夹 + 首页快捷入口 + 历史 + 本地网页记录。
 * 与站点备份共用同一个备份目录（默认应用私有目录，可切换为 SAF 目录）。
 */
class GlobalBackupManager(
    private val db: AppDatabase,
    private val settings: SettingsStore,
    private val backups: BackupManager
) {

    suspend fun list(): List<BackupInfo> = backups.listBackups(global = true)

    suspend fun backupNow(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val now = System.currentTimeMillis()
            val fileName = "${BackupManager.GLOBAL_PREFIX}$now.json"
            if (!backups.writeText(fileName, buildPayload(now).toString())) {
                error("无法写入备份目录")
            }
            pruneOld()
            "全局数据已备份（${BackupManager.formatTime(now)}）"
        }
    }

    /** 切后台/退出时按最小间隔自动备份 */
    suspend fun autoBackupIfNeeded(minIntervalMs: Long = 10 * 60 * 1000L): Result<String>? {
        if (!settings.autoBackupGlobal) return null
        val now = System.currentTimeMillis()
        if (now - settings.lastGlobalBackupAt < minIntervalMs) return null
        val result = backupNow()
        if (result.isSuccess) settings.lastGlobalBackupAt = now
        return result
    }

    suspend fun restore(info: BackupInfo): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val root = runCatching { JSONObject(backups.readText(info)) }
                .getOrElse { error("备份文件无法读取") }
            if (root.optString("type") != TYPE) error("这不是全局数据备份")

            db.bookmarkDao().clearAll()
            db.bookmarkFolderDao().clearAll()
            db.homeItemDao().clearAll()
            db.historyDao().clear()
            db.localPageDao().clearAll()

            root.optJSONArray("folders")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    db.bookmarkFolderDao().insert(
                        BookmarkFolder(
                            id = o.optLong("id"),
                            name = o.optString("name"),
                            parentId = o.optLong("parentId"),
                            createdAt = o.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
            }
            root.optJSONArray("bookmarks")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    db.bookmarkDao().insert(
                        Bookmark(
                            id = o.optLong("id"),
                            title = o.optString("title"),
                            url = o.optString("url"),
                            folderId = o.optLong("folderId"),
                            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                            iconColor = o.optNullableInt("iconColor"),
                            iconText = o.optNullableString("iconText")
                        )
                    )
                }
            }
            root.optJSONArray("homeItems")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    db.homeItemDao().insert(
                        HomeItem(
                            id = o.optLong("id"),
                            title = o.optString("title"),
                            url = o.optString("url"),
                            parentId = o.optLong("parentId"),
                            isFolder = o.optBoolean("isFolder"),
                            sortOrder = o.optInt("sortOrder"),
                            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                            iconColor = o.optNullableInt("iconColor"),
                            iconText = o.optNullableString("iconText"),
                            page = o.optInt("page", 0),
                            slot = o.optInt("slot", -1)
                        )
                    )
                }
            }
            root.optJSONArray("history")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    db.historyDao().insert(
                        HistoryItem(
                            id = o.optLong("id"),
                            title = o.optString("title"),
                            url = o.optString("url"),
                            visitedAt = o.optLong("visitedAt", System.currentTimeMillis())
                        )
                    )
                }
            }
            root.optJSONArray("localPages")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    db.localPageDao().upsert(
                        LocalPage(
                            siteId = o.optString("siteId"),
                            displayName = o.optString("displayName"),
                            entryUrl = o.optString("entryUrl"),
                            lastOpenedAt = o.optLong("lastOpenedAt", System.currentTimeMillis())
                        )
                    )
                }
            }

            root.optJSONObject("settings")?.let { applySettings(it) }
            "已恢复全局数据（恢复时间 ${BackupManager.formatTime(System.currentTimeMillis())}）"
        }
    }

    suspend fun delete(info: BackupInfo): Boolean = withContext(Dispatchers.IO) {
        backups.delete(info)
    }

    private suspend fun buildPayload(now: Long): JSONObject {
        val bookmarkArray = JSONArray().apply {
            db.bookmarkDao().all().forEach { put(it.toJson()) }
        }
        val folderArray = JSONArray().apply {
            db.bookmarkFolderDao().all().forEach { put(it.toJson()) }
        }
        val homeArray = JSONArray().apply {
            db.homeItemDao().all().forEach { put(it.toJson()) }
        }
        val historyArray = JSONArray().apply {
            db.historyDao().all().forEach { put(it.toJson()) }
        }
        val localArray = JSONArray().apply {
            db.localPageDao().all().forEach { put(it.toJson()) }
        }
        return JSONObject().apply {
            put("version", 1)
            put("type", TYPE)
            put("createdAt", now)
            put("settings", settings.toJson())
            put("bookmarks", bookmarkArray)
            put("folders", folderArray)
            put("homeItems", homeArray)
            put("history", historyArray)
            put("localPages", localArray)
        }
    }

    private suspend fun pruneOld() {
        val keep = settings.globalBackupKeep
        backups.listBackups(global = true).drop(keep).forEach { backups.delete(it) }
    }

    private fun applySettings(o: JSONObject) {
        if (o.has("searchEngine")) {
            settings.searchEngine = o.optString("searchEngine", settings.searchEngine)
        }
        settings.textZoom = o.optInt("textZoom", settings.textZoom).coerceIn(50, 200)
        settings.desktopMode = o.optBoolean("desktopMode", settings.desktopMode)
        settings.darkMode = o.optInt("darkMode", settings.darkMode)
        settings.appTheme = o.optInt("appTheme", settings.appTheme)
        settings.followPageTheme = o.optBoolean("followPageTheme", settings.followPageTheme)
        settings.menuRows = o.optInt("menuRows", settings.menuRows).coerceIn(2, 5)
        settings.menuOrder = if (!o.has("menuOrder") || o.isNull("menuOrder")) {
            null
        } else {
            o.optString("menuOrder").takeIf { it.isNotBlank() }
        }
        if (o.has("autoBackup")) settings.autoBackup = o.optBoolean("autoBackup", settings.autoBackup)
        if (o.has("autoBackupGlobal")) {
            settings.autoBackupGlobal = o.optBoolean("autoBackupGlobal", settings.autoBackupGlobal)
        }
        settings.globalBackupKeep = o.optInt("globalBackupKeep", settings.globalBackupKeep)
        settings.homePageCount = o.optInt("homePageCount", settings.homePageCount)
        settings.homeAutoCompact = o.optBoolean("homeAutoCompact", settings.homeAutoCompact)
    }

    companion object {
        private const val TYPE = "global"
    }
}

private fun Bookmark.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("title", title)
    put("url", url)
    put("folderId", folderId)
    put("createdAt", createdAt)
    put("iconColor", iconColor)
    put("iconText", iconText)
}

private fun BookmarkFolder.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("name", name)
    put("parentId", parentId)
    put("createdAt", createdAt)
}

private fun HomeItem.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("title", title)
    put("url", url)
    put("parentId", parentId)
    put("isFolder", isFolder)
    put("sortOrder", sortOrder)
    put("createdAt", createdAt)
    put("iconColor", iconColor)
    put("iconText", iconText)
    put("page", page)
    put("slot", slot)
}

private fun HistoryItem.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("title", title)
    put("url", url)
    put("visitedAt", visitedAt)
}

private fun LocalPage.toJson(): JSONObject = JSONObject().apply {
    put("siteId", siteId)
    put("displayName", displayName)
    put("entryUrl", entryUrl)
    put("lastOpenedAt", lastOpenedAt)
}

private fun SettingsStore.toJson(): JSONObject = JSONObject().apply {
    put("searchEngine", searchEngine)
    put("textZoom", textZoom)
    put("desktopMode", desktopMode)
    put("darkMode", darkMode)
    put("appTheme", appTheme)
    put("followPageTheme", followPageTheme)
    put("menuRows", menuRows)
    put("menuOrder", menuOrder)
    put("autoBackup", autoBackup)
    put("autoBackupGlobal", autoBackupGlobal)
    put("globalBackupKeep", globalBackupKeep)
    put("homePageCount", homePageCount)
    put("homeAutoCompact", homeAutoCompact)
}

private fun JSONObject.optNullableInt(key: String): Int? =
    if (has(key) && !isNull(key)) optInt(key) else null

private fun JSONObject.optNullableString(key: String): String? =
    if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotBlank() } else null
