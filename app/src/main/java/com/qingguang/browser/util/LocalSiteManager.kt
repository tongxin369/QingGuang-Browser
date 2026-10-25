package com.qingguang.browser.util

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.qingguang.browser.data.LocalPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * 本地网页管理。
 *
 * 关键设计：所有本地网页都复制到 filesDir/sites/<siteId>/ 下，
 * 并通过 WebViewAssetLoader 以 https://appassets.androidplatform.net 的固定地址加载。
 * 这样每个站点都有稳定 origin，localStorage / IndexedDB / Cookie 不会因为重开浏览器而丢失，
 * 同时满足"记住本地地址""本地网页也能定位"等需求。
 */
class LocalSiteManager(private val context: Context) {

    private val rootDir: File = File(context.filesDir, "sites").apply { mkdirs() }

    suspend fun importSingleHtml(uri: Uri, displayName: String): LocalPage = withContext(Dispatchers.IO) {
        val siteId = stableId(displayName)
        val dir = siteDir(siteId).apply { deleteRecursively(); mkdirs() }
        context.contentResolver.openInputStream(uri)?.use { input ->
            File(dir, "index.html").outputStream().use { output -> input.copyTo(output) }
        } ?: throw IOException("无法读取所选文件")
        LocalPage(siteId = siteId, displayName = displayName, entryUrl = urlFor(siteId, "index.html"))
    }

    suspend fun importDirectory(treeUri: Uri, displayName: String): LocalPage = withContext(Dispatchers.IO) {
        val tree = DocumentFile.fromTreeUri(context, treeUri) ?: throw IOException("无法访问所选目录")
        val siteId = stableId(displayName)
        val dir = siteDir(siteId).apply { deleteRecursively(); mkdirs() }
        copyDocument(tree, dir)
        val entry = findEntry(dir) ?: throw IOException("该目录下没有找到 .html 文件")
        LocalPage(siteId = siteId, displayName = displayName, entryUrl = urlFor(siteId, entry))
    }

    fun siteDir(siteId: String): File = File(rootDir, siteId)

    fun deleteSite(siteId: String) {
        siteDir(siteId).deleteRecursively()
    }

    fun directorySize(siteId: String): Long = siteDir(siteId).walkTopDown().filter { it.isFile }.sumOf { it.length() }

    private fun copyDocument(doc: DocumentFile, dest: File) {
        if (doc.isDirectory) {
            dest.mkdirs()
            doc.listFiles().forEach { child ->
                val name = sanitizeFileName(child.name ?: "unnamed")
                copyDocument(child, File(dest, name))
            }
        } else {
            context.contentResolver.openInputStream(doc.uri)?.use { input ->
                dest.parentFile?.mkdirs()
                dest.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }

    private fun findEntry(dir: File): String? {
        val all = dir.walkTopDown().filter { it.isFile && it.extension.lowercase() in setOf("html", "htm") }.toList()
        if (all.isEmpty()) return null
        val preferred = all.firstOrNull { it.name.equals("index.html", ignoreCase = true) && it.parentFile == dir }
            ?: all.firstOrNull { it.name.equals("index.html", ignoreCase = true) }
            ?: all.first()
        return preferred.relativeTo(dir).path.replace(File.separatorChar, '/')
    }

    private fun sanitizeFileName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_")

    companion object {
        const val ASSET_HOST = "https://appassets.androidplatform.net"

        fun urlFor(siteId: String, entryPath: String): String {
            val encodedEntry = entryPath.split('/').joinToString("/") { Uri.encode(it) }
            return "$ASSET_HOST/files/sites/${Uri.encode(siteId)}/$encodedEntry"
        }
    }

    private fun stableId(name: String): String {
        val safe = name.replace(Regex("[^A-Za-z0-9_-]"), "_").take(24).ifBlank { "site" }
        return "$safe-${Integer.toHexString(name.hashCode())}"
    }
}
