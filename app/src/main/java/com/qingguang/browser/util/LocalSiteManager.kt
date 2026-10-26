package com.qingguang.browser.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.qingguang.browser.data.LocalPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** 源码写回原文件的结果 */
data class SourceSyncResult(
    val synced: Boolean,
    val message: String,
    val newUri: String? = null
)

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

    suspend fun importSingleHtml(
        uri: Uri,
        displayName: String,
        registerSource: Boolean = true
    ): LocalPage = withContext(Dispatchers.IO) {
        val siteId = stableId(displayName)
        val dir = siteDir(siteId).apply { deleteRecursively(); mkdirs() }
        val fileName = sanitizeFileName(displayName).ifBlank { "index.html" }
            .let { if (it.substringAfterLast('.', "").isBlank()) "$it.html" else it }
        context.contentResolver.openInputStream(uri)?.use { input ->
            File(dir, fileName).outputStream().use { output -> input.copyTo(output) }
        } ?: throw IOException("无法读取所选文件")
        if (registerSource) persistAccess(uri)
        LocalPage(
            siteId = siteId,
            displayName = displayName,
            entryUrl = urlFor(siteId, fileName),
            sourceUri = uri.toString().takeIf { registerSource },
            sourceKind = "file".takeIf { registerSource }
        )
    }

    suspend fun importDirectory(
        treeUri: Uri,
        displayName: String,
        registerSource: Boolean = true
    ): LocalPage = withContext(Dispatchers.IO) {
        val tree = DocumentFile.fromTreeUri(context, treeUri) ?: throw IOException("无法访问所选目录")
        val siteId = stableId(displayName)
        val dir = siteDir(siteId).apply { deleteRecursively(); mkdirs() }
        copyDocument(tree, dir)
        val entry = findEntry(dir) ?: throw IOException("该目录下没有找到 .html 文件")
        if (registerSource) persistAccess(treeUri)
        LocalPage(
            siteId = siteId,
            displayName = displayName,
            entryUrl = urlFor(siteId, entry),
            sourceUri = treeUri.toString().takeIf { registerSource },
            sourceKind = "tree".takeIf { registerSource }
        )
    }

    /** 把源码编辑器里的内容写回手机原文件（未关联原文件时只保留应用内副本） */
    suspend fun writeBackText(
        page: LocalPage,
        relativePath: String,
        text: String
    ): SourceSyncResult = withContext(Dispatchers.IO) {
        val uriText = page.sourceUri
            ?: return@withContext SourceSyncResult(false, "该网页未关联手机原文件，只写入了应用内副本")
        runCatching {
            val uri = Uri.parse(uriText)
            val target = if (page.sourceKind == "tree") findDocument(uri, relativePath) else uri
            context.contentResolver.openOutputStream(target, "wt")?.use { output ->
                output.write(text.toByteArray(Charsets.UTF_8))
            } ?: error("无法打开原文件")
            SourceSyncResult(true, "已同步到手机原文件夹")
        }.getOrElse {
            SourceSyncResult(false, "写入手机原文件失败：${it.message}")
        }
    }

    /** 重命名时同步修改手机原文件名；成功时返回新的原始 URI */
    suspend fun renameInSource(
        page: LocalPage,
        relativePath: String,
        newName: String
    ): SourceSyncResult = withContext(Dispatchers.IO) {
        val uriText = page.sourceUri
            ?: return@withContext SourceSyncResult(false, "该网页未关联手机原文件，只修改了应用内文件名")
        runCatching {
            val uri = Uri.parse(uriText)
            if (page.sourceKind == "tree") {
                var doc = DocumentFile.fromTreeUri(context, uri) ?: error("无法访问原文件夹")
                for (segment in relativePath.split('/').filter { it.isNotEmpty() }) {
                    doc = doc.findFile(segment) ?: error("原文件夹中找不到 $relativePath")
                }
                if (!doc.renameTo(newName)) error("原文件重命名失败")
                SourceSyncResult(true, "已同步重命名手机原文件")
            } else {
                val newUri = DocumentsContract.renameDocument(context.contentResolver, uri, newName)
                    ?: error("原文件重命名失败")
                SourceSyncResult(true, "已同步重命名手机原文件", newUri.toString())
            }
        }.getOrElse {
            SourceSyncResult(
                false,
                "手机原文件重命名失败：${it.message}（该位置可能不支持重命名，可改为导入整个文件夹后再试）"
            )
        }
    }

    private fun persistAccess(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
    }

    private fun findDocument(treeUri: Uri, relativePath: String): Uri {
        var doc = DocumentFile.fromTreeUri(context, treeUri)
            ?: throw IOException("无法访问原文件夹")
        for (segment in relativePath.split('/').filter { it.isNotEmpty() }) {
            doc = doc.findFile(segment)
                ?: throw IOException("原文件夹中找不到 $relativePath")
        }
        return doc.uri
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
