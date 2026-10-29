package com.qingguang.browser

import android.app.Application
import com.qingguang.browser.data.AppDatabase
import com.qingguang.browser.util.BackupManager
import com.qingguang.browser.util.GlobalBackupManager
import com.qingguang.browser.util.LocalSiteManager
import com.qingguang.browser.util.SettingsStore
import kotlinx.coroutines.launch

class App : Application() {

    val database: AppDatabase by lazy {
        android.util.Log.i(
            "QGStart",
            "database open +${android.os.SystemClock.uptimeMillis() - createdAtUptime}ms"
        )
        AppDatabase.get(this)
    }
    /** 启动预热用的后台作用域（数据库打开与首页查询预热） */
    private val startupScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
    )

    val settings: SettingsStore by lazy { SettingsStore(this) }
    val localSites: LocalSiteManager by lazy { LocalSiteManager(this) }
    val backups: BackupManager by lazy { BackupManager(this, settings) }
    val globalBackups: GlobalBackupManager by lazy {
        GlobalBackupManager(database, settings, backups)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createdAtUptime = android.os.SystemClock.uptimeMillis()
        android.util.Log.i(
            "QGStart",
            "Application.onCreate +$createdAtUptime" +
                if (android.os.Build.VERSION.SDK_INT >= 24) {
                    " processStart=${android.os.Process.getStartUptimeMillis()}"
                } else ""
        )
        // 启动二期：在后台线程提前打开数据库并预热首页依赖的查询，
        // 让首帧组合时不再等待 Room 冷启动，首屏更快出现内容。
        startupScope.launch {
            runCatching {
                val begin = android.os.SystemClock.uptimeMillis()
                database.openHelper.writableDatabase
                // 首页第一屏会读这两张表，提前查一次让查询计划/游标缓存就绪
                runCatching { database.homeItemDao().all() }
                val cost = android.os.SystemClock.uptimeMillis() - begin
                android.util.Log.i("QGStart", "home prewarm +${cost}ms")
            }
        }
    }

    companion object {
        lateinit var instance: App
            private set

        /** 冷启动埋点：Application.onCreate 时刻（uptimeMillis），供首帧日志换算耗时 */
        var createdAtUptime: Long = 0L
            private set
    }
}
