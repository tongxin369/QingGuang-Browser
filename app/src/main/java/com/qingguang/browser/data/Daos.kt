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
