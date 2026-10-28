package com.qingguang.browser

import android.app.Application
import com.qingguang.browser.data.AppDatabase
import com.qingguang.browser.util.BackupManager
import com.qingguang.browser.util.GlobalBackupManager
import com.qingguang.browser.util.LocalSiteManager
import com.qingguang.browser.util.SettingsStore

class App : Application() {

    val database: AppDatabase by lazy { AppDatabase.get(this) }
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
    }

    companion object {
        lateinit var instance: App
            private set

        /** 冷启动埋点：Application.onCreate 时刻（uptimeMillis），供首帧日志换算耗时 */
        var createdAtUptime: Long = 0L
            private set
    }
}
