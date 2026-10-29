package com.qingguang.browser

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.qingguang.browser.data.AppDatabase
import com.qingguang.browser.util.BackupManager
import com.qingguang.browser.util.GlobalBackupManager
import com.qingguang.browser.util.LocalSiteManager
import com.qingguang.browser.util.SettingsStore
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class App : Application() {

    val database: AppDatabase by lazy {
        val begin = android.os.SystemClock.uptimeMillis()
        val db = AppDatabase.get(this)
        StartupProfile.record("数据库首次打开", android.os.SystemClock.uptimeMillis() - begin)
        db
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

    /** 上次异常退出留下的崩溃栈；非空时界面提示用户 */
    var crashReport by mutableStateOf<String?>(null)
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        createdAtUptime = android.os.SystemClock.uptimeMillis()
        if (android.os.Build.VERSION.SDK_INT >= 24) {
            StartupProfile.processStartUptime = android.os.Process.getStartUptimeMillis()
            StartupProfile.record(
                "系统进程启动 → Application.onCreate",
                createdAtUptime - StartupProfile.processStartUptime
            )
        }
        installCrashHandler()
        loadCrashReport()
        // 启动二期：在后台线程提前打开数据库并预热首页依赖的查询，
        // 让首帧组合时不再等待 Room 冷启动，首屏更快出现内容。
        startupScope.launch {
            runCatching {
                val begin = android.os.SystemClock.uptimeMillis()
                database.openHelper.writableDatabase
                // 首页第一屏会读这两张表，提前查一次让查询计划/游标缓存就绪
                runCatching { database.homeItemDao().all() }
                StartupProfile.record(
                    "数据库预热（后台）",
                    android.os.SystemClock.uptimeMillis() - begin
                )
            }
        }
    }

    /** 冷启动首帧由 Activity 记录，方便在设置里查看分段耗时 */
    fun markFirstFrame() {
        if (StartupProfile.firstFrameDone) return
        StartupProfile.firstFrameDone = true
        StartupProfile.record(
            "Application.onCreate → 首帧",
            android.os.SystemClock.uptimeMillis() - createdAtUptime
        )
    }

    // ---------------- 崩溃栈留存 ----------------

    private fun crashDir(): File = File(filesDir, "crash").apply { mkdirs() }

    private fun crashFile(): File = File(crashDir(), "last_crash.txt")

    /** 上次崩溃记录文件（存在时才可读） */
    fun lastCrashFile(): File = crashFile()

    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    .format(Date())
                val text = buildString {
                    append("时间：").append(time).append('\n')
                    append("线程：").append(thread.name).append('\n')
                    append("版本：").append(BuildConfig.VERSION_NAME).append("\n\n")
                    append(throwable.stackTraceToString())
                }
                crashFile().writeText(text, Charsets.UTF_8)
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun loadCrashReport() {
        crashReport = runCatching {
            val file = crashFile()
            if (file.isFile) file.readText(Charsets.UTF_8).takeIf { it.isNotBlank() } else null
        }.getOrNull()
    }

    fun dismissCrashReport() {
        crashReport = null
    }

    fun clearCrashLog() {
        crashReport = null
        runCatching { crashFile().delete() }
    }

    companion object {
        lateinit var instance: App
            private set

        /** 冷启动埋点：Application.onCreate 时刻（uptimeMillis），供首帧日志换算耗时 */
        var createdAtUptime: Long = 0L
            private set
    }
}

/** 冷启动分段耗时（仅在本次进程内有效） */
object StartupProfile {
    var processStartUptime: Long = 0L
    var firstFrameDone: Boolean = false

    private val entryList = mutableListOf<Pair<String, Long>>()

    val entries: List<Pair<String, Long>> get() = entryList.toList()

    fun record(label: String, ms: Long) {
        if (entryList.any { it.first == label }) return
        entryList.add(label to ms)
    }

    fun report(): String = buildString {
        append("冷启动耗时（本次运行）\n")
        entryList.forEach { (label, ms) ->
            append("· ").append(label).append("：").append(ms).append(" ms\n")
        }
        append("· 版本：").append(com.qingguang.browser.BuildConfig.VERSION_NAME)
    }
}
