package com.qingguang.browser.util

import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebView
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BackupInfo(
    val file: File,
    val host: String,
    val createdAt: Long,
    val sizeBytes: Long
)

/**
 * 站点数据备份。
 * MVP 备份内容：Cookie + localStorage（IndexedDB 体积大且导出复杂，后续版本补充）。
 * 备份文件保存在应用私有目录，路径可直接查看；同一站点只保留最新一份，旧文件自动删除。
 */
class BackupManager(private val context: Context) {

    private val gson = Gson()

    val backupDir: File
        get() = File(context.filesDir, "backups").apply { mkdirs() }

    suspend fun listBackups(): List<BackupInfo> = withContext(Dispatchers.IO) {
        backupDir.listFiles()
            ?.filter { it.isFile && it.extension == "json" }
            ?.map {
                val parts = it.nameWithoutExtension.split("@")
                BackupInfo(
                    file = it,
                    host = parts.firstOrNull() ?: it.nameWithoutExtension,
                    createdAt = parts.getOrNull(1)?.toLongOrNull() ?: it.lastModified(),
                    sizeBytes = it.length()
                )
            }
            ?.sortedByDescending { it.createdAt }
            ?: emptyList()
    }

    /** 导出当前页面的 Cookie 与 localStorage。回调在主线程。 */
    fun backupSite(
        webView: WebView,
        url: String,
        title: String,
        onResult: (success: Boolean, message: String) -> Unit
    ) {
        webView.evaluateJavascript(EXPORT_LOCAL_STORAGE_JS) { raw ->
            val localStorageJson = decodeJsString(raw) ?: "{}"
            val host = Uri.parse(url).host ?: "local"
            val cookies = CookieManager.getInstance().getCookie(url) ?: ""
            val payload = JsonObject().apply {
                addProperty("version", 1)
                addProperty("url", url)
                addProperty("host", host)
                addProperty("title", title)
                addProperty("createdAt", System.currentTimeMillis())
                addProperty("cookies", cookies)
                add("localStorage", runCatching { JsonParser.parseString(localStorageJson) }.getOrElse { JsonObject() })
            }
            val safeHost = host.replace(Regex("[^A-Za-z0-9.-]"), "_")
            val fileName = "${safeHost}@${System.currentTimeMillis()}.json"
            runCatching {
                File(backupDir, fileName).writeText(gson.toJson(payload), Charsets.UTF_8)
                // 同一站点只保留最新一份
                backupDir.listFiles()?.filter {
                    it.isFile && it.name.endsWith(".json") &&
                        (it.nameWithoutExtension.split("@").firstOrNull() == safeHost) &&
                        it.name != fileName
                }?.forEach { it.delete() }
            }.onSuccess {
                onResult(true, "已备份到 ${backupDir.absolutePath}/$fileName")
            }.onFailure {
                onResult(false, "备份失败：${it.message}")
            }
        }
    }

    /** 从备份文件恢复 Cookie 与 localStorage 到指定 WebView。回调在主线程。 */
    fun restore(webView: WebView, info: BackupInfo, onResult: (Boolean, String) -> Unit) {
        val payload = runCatching {
            JsonParser.parseString(info.file.readText(Charsets.UTF_8)).asJsonObject
        }.getOrNull()
        if (payload == null) {
            onResult(false, "备份文件已损坏")
            return
        }
        val url = payload.get("url")?.asString ?: ""
        val cookies = payload.get("cookies")?.asString ?: ""
        val localStorage = payload.get("localStorage")?.toString() ?: "{}"

        if (cookies.isNotBlank()) {
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookies.split(";").map { it.trim() }.filter { it.isNotEmpty() }.forEach {
                cookieManager.setCookie(url, it)
            }
            cookieManager.flush()
        }

        val script = RESTORE_LOCAL_STORAGE_JS.replace("__DATA__", gson.toJson(localStorage))
        webView.evaluateJavascript(script) { result ->
            webView.reload()
            onResult(true, if (result.contains("ok")) "已恢复并刷新页面" else "已写入数据并刷新页面")
        }
    }

    fun delete(info: BackupInfo): Boolean = info.file.delete()

    /** evaluateJavascript 返回的是被 JSON 编码过的字符串，这里解出原始字符串 */
    private fun decodeJsString(raw: String?): String? {
        if (raw == null || raw == "null") return null
        return runCatching { gson.fromJson(raw, String::class.java) }.getOrNull()
    }

    companion object {
        private const val EXPORT_LOCAL_STORAGE_JS =
            "(function(){var o={};try{for(var i=0;i<localStorage.length;i++){var k=localStorage.key(i);" +
                "o[k]=localStorage.getItem(k);}}catch(e){}return JSON.stringify(o);})()"

        private const val RESTORE_LOCAL_STORAGE_JS =
            "(function(d){try{var o=JSON.parse(d);localStorage.clear();" +
                "for(var k in o){localStorage.setItem(k,o[k]);}return 'ok';}catch(e){return 'err:'+e.message;}})('__DATA__')"

        fun formatTime(time: Long): String =
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(time))

        fun formatSize(bytes: Long): String = when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0)
            else -> String.format(Locale.getDefault(), "%.1f MB", bytes / 1024.0 / 1024.0)
        }
    }
}
