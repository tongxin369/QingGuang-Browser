package com.qingguang.browser.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Bookmark>>

    @Insert
    suspend fun insert(bookmark: Bookmark): Long

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM bookmarks WHERE url = :url")
    suspend fun countByUrl(url: String): Int

    @Query("UPDATE bookmarks SET folderId = :folderId WHERE id = :id")
    suspend fun moveToFolder(id: Long, folderId: Long)

    @Query("UPDATE bookmarks SET title = :title, url = :url, folderId = :folderId WHERE id = :id")
    suspend fun update(id: Long, title: String, url: String, folderId: Long)

    @Query("UPDATE bookmarks SET iconColor = :color, iconText = :text WHERE id = :id")
    suspend fun updateIcon(id: Long, color: Int?, text: String?)

    /** 同一网址的所有书签一起更新，保证书签/首页/历史图标一致 */
    @Query("UPDATE bookmarks SET iconColor = :color, iconText = :text WHERE url = :url")
    suspend fun updateIconByUrl(url: String, color: Int?, text: String?)

    @Query("SELECT * FROM bookmarks")
    suspend fun all(): List<Bookmark>

    @Query("DELETE FROM bookmarks")
    suspend fun clearAll()
}

@Dao
interface BookmarkFolderDao {
    @Query("SELECT * FROM bookmark_folders ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<BookmarkFolder>>

    @Insert
    suspend fun insert(folder: BookmarkFolder): Long

    @Query("UPDATE bookmark_folders SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("UPDATE bookmark_folders SET parentId = :parentId WHERE id = :id")
    suspend fun moveToParent(id: Long, parentId: Long)

    @Query("DELETE FROM bookmark_folders WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM bookmark_folders")
    suspend fun all(): List<BookmarkFolder>

    @Query("DELETE FROM bookmark_folders")
    suspend fun clearAll()
}

@Dao
interface HomeItemDao {
    @Query("SELECT * FROM home_items ORDER BY sortOrder ASC, createdAt ASC")
    fun observeAll(): Flow<List<HomeItem>>

    @Insert
    suspend fun insert(item: HomeItem): Long

    @Query("UPDATE home_items SET parentId = :parentId, sortOrder = :sortOrder WHERE id = :id")
    suspend fun moveToParent(id: Long, parentId: Long, sortOrder: Int)

    @Query("UPDATE home_items SET title = :title WHERE id = :id")
    suspend fun rename(id: Long, title: String)

    @Query("DELETE FROM home_items WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM home_items WHERE url = :url AND parentId = 0")
    suspend fun countOnHome(url: String): Int

    /** 某一首页分页上的根图标数量，用于判断第一页是否已满 */
    @Query("SELECT COUNT(*) FROM home_items WHERE parentId = 0 AND page = :page")
    suspend fun countRootOnPage(page: Int): Int

    @Query("UPDATE home_items SET page = :page, sortOrder = :sortOrder WHERE id = :id")
    suspend fun moveToPage(id: Long, page: Int, sortOrder: Int)

    @Query("UPDATE home_items SET iconColor = :color, iconText = :text WHERE id = :id")
    suspend fun updateIcon(id: Long, color: Int?, text: String?)

    /** 同一网址的快捷入口一起更新，与书签保持一致 */
    @Query("UPDATE home_items SET iconColor = :color, iconText = :text WHERE url = :url AND isFolder = 0")
    suspend fun updateIconByUrl(url: String, color: Int?, text: String?)

    @Query("SELECT * FROM home_items ORDER BY sortOrder ASC, createdAt ASC")
    suspend fun all(): List<HomeItem>

    @Query("DELETE FROM home_items")
    suspend fun clearAll()
}

@Dao
interface HistoryDao {
    /** 按地址聚合，只保留最近一次访问 */
    @Query(
        "SELECT id, title, url, MAX(visitedAt) AS visitedAt FROM history " +
            "GROUP BY url ORDER BY visitedAt DESC LIMIT 500"
    )
    fun observeRecent(): Flow<List<HistoryItem>>

    @Insert
    suspend fun insert(item: HistoryItem)

    @Query("DELETE FROM history WHERE url = :url")
    suspend fun deleteByUrl(url: String)

    @Query("DELETE FROM history")
    suspend fun clear()

    @Query("SELECT * FROM history ORDER BY visitedAt DESC")
    suspend fun all(): List<HistoryItem>
}

@Dao
interface LocalPageDao {
    @Query("SELECT * FROM local_pages ORDER BY lastOpenedAt DESC")
    fun observeAll(): Flow<List<LocalPage>>

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun upsert(page: LocalPage)

    @Query("UPDATE local_pages SET lastOpenedAt = :time WHERE siteId = :siteId")
    suspend fun touch(siteId: String, time: Long)

    @Query("DELETE FROM local_pages WHERE siteId = :siteId")
    suspend fun delete(siteId: String)

    @Query("SELECT * FROM local_pages ORDER BY lastOpenedAt DESC")
    suspend fun all(): List<LocalPage>

    @Query("DELETE FROM local_pages")
    suspend fun clearAll()
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC LIMIT 200")
    fun observeAll(): Flow<List<DownloadRecord>>

    @Insert
    suspend fun insert(record: DownloadRecord)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun delete(id: Long)
}
