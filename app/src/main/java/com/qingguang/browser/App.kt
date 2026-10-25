package com.qingguang.browser

import android.app.Application
import com.qingguang.browser.data.AppDatabase
import com.qingguang.browser.util.BackupManager
import com.qingguang.browser.util.LocalSiteManager
import com.qingguang.browser.util.SettingsStore

class App : Application() {

    val database: AppDatabase by lazy { AppDatabase.get(this) }
    val settings: SettingsStore by lazy { SettingsStore(this) }
    val localSites: LocalSiteManager by lazy { LocalSiteManager(this) }
    val backups: BackupManager by lazy { BackupManager(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: App
            private set
    }
}
