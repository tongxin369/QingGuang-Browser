package com.qingguang.browser.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Bookmark::class, BookmarkFolder::class, HomeItem::class, HistoryItem::class,
        LocalPage::class, DownloadRecord::class
    ],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun bookmarkFolderDao(): BookmarkFolderDao
    abstract fun homeItemDao(): HomeItemDao
    abstract fun historyDao(): HistoryDao
    abstract fun localPageDao(): LocalPageDao
    abstract fun downloadDao(): DownloadDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "qingguang.db"
                ).addMigrations(
                    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                    MIGRATION_5_6, MIGRATION_6_7
                ).build().also { instance = it }
            }

        /** v7：本地网页记录原始文件/目录，用于源码编辑后写回手机原文件夹 */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE local_pages ADD COLUMN sourceUri TEXT")
                db.execSQL("ALTER TABLE local_pages ADD COLUMN sourceKind TEXT")
            }
        }

        /** v6：首页快捷入口支持自由摆放（页内格子位置） */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE home_items ADD COLUMN slot INTEGER NOT NULL DEFAULT -1")
                db.execSQL(
                    "UPDATE home_items SET slot = (" +
                        "SELECT COUNT(*) FROM home_items h2 " +
                        "WHERE h2.parentId = home_items.parentId AND h2.page = home_items.page " +
                        "AND (h2.sortOrder < home_items.sortOrder " +
                        "OR (h2.sortOrder = home_items.sortOrder AND h2.id < home_items.id))" +
                        ") WHERE parentId = 0"
                )
            }
        }

        /** v5：首页快捷入口支持分页 */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE home_items ADD COLUMN page INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v4：书签与首页快捷入口支持自定义图标颜色/文字 */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE bookmarks ADD COLUMN iconColor INTEGER")
                db.execSQL("ALTER TABLE bookmarks ADD COLUMN iconText TEXT")
                db.execSQL("ALTER TABLE home_items ADD COLUMN iconColor INTEGER")
                db.execSQL("ALTER TABLE home_items ADD COLUMN iconText TEXT")
            }
        }

        /** v3：文件夹支持父子层级；新增首页快捷入口表 */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE bookmark_folders ADD COLUMN parentId INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `home_items` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`url` TEXT NOT NULL, " +
                        "`parentId` INTEGER NOT NULL, " +
                        "`isFolder` INTEGER NOT NULL, " +
                        "`sortOrder` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
            }
        }

        /** v2：书签新增所属文件夹字段，并新增文件夹表 */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE bookmarks ADD COLUMN folderId INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `bookmark_folders` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
            }
        }
    }
}
