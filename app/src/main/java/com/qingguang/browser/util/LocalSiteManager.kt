package com.qingguang.browser.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.qingguang.browser.data.LocalPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.charset.Charset

/** 源码写回原文件的结果 */
data class SourceSyncResult(
    val synced: Boolean,
    val message: String,
    val newUri: String? = null
)

/** 旧站点重新关联手机原文件后的结果 */
data class SourceAttachResult(
    val page: LocalPage,
    val message: String,
    val newEntryUrl: String? = null
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

    /** 导入 zip 压缩包（包含 html/css/js/图片的整站） */
    suspend fun importZip(
        uri: Uri,
        displayName: String
    ): LocalPage = withContext(Dispatchers.IO) {
        val siteId = stableId(displayName)
        val dir = siteDir(siteId).apply { deleteRecursively(); mkdirs() }
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                java.util.zip.ZipInputStream(input.buffered()).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val name = entry.name.replace('\\', '/')
                        // 防目录穿越：忽略绝对路径和 ..
                        if (name.isNotBlank() && !name.startsWith("/") && !name.contains("..")) {
                            val target = File(dir, name)
                            if (entry.isDirectory) {
                                target.mkdirs()
                            } else {
                                target.parentFile?.mkdirs()
                                target.outputStream().use { output -> zip.copyTo(output) }
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            } ?: throw IOException("无法读取所选压缩包")
        } catch (e: Exception) {
            dir.deleteRecursively()
            throw IOException("压缩包导入失败：${e.message}")
        }
        // 压缩包有时会多一层根目录，自动向下找入口文件
        var entryDir = dir
        var prefix = ""
        var entry = findEntry(entryDir)
        while (entry == null && entryDir.listFiles()?.size == 1) {
            val child = entryDir.listFiles()!!.first()
            if (!child.isDirectory) break
            entryDir = child
            prefix = if (prefix.isEmpty()) child.name else "$prefix/${child.name}"
            entry = findEntry(entryDir)
        }
        val html = entry ?: throw IOException("压缩包内没有找到 .html 文件")
        val entryPath = if (prefix.isEmpty()) html else "$prefix/$html"
        LocalPage(
            siteId = siteId,
            displayName = displayName,
            entryUrl = urlFor(siteId, entryPath),
            sourceUri = null,
            sourceKind = null
        )
    }

    /** 把源码编辑器里的内容写回手机原文件（未关联原文件时只保留应用内副本） */
    suspend fun writeBackText(
        page: LocalPage,
        relativePath: String,
        text: String,
        charset: Charset = Charsets.UTF_8
    ): SourceSyncResult = withContext(Dispatchers.IO) {
        val uriText = page.sourceUri
            ?: return@withContext SourceSyncResult(false, "该网页未关联手机原文件，只写入了应用内副本")
        runCatching {
            val uri = Uri.parse(uriText)
            val target = if (page.sourceKind == "tree") findDocument(uri, relativePath) else uri
            context.contentResolver.openOutputStream(target, "wt")?.use { output ->
                output.write(text.toByteArray(charset))
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

    /** 旧版本导入、尚未关联原文件的站点：重新关联单个原文件，并同步应用内文件名 */
    suspend fun attachFileSource(
        page: LocalPage,
        uri: Uri,
        currentRelativePath: String
    ): SourceAttachResult = withContext(Dispatchers.IO) {
        persistAccess(uri)
        val originalName = queryDisplayName(uri)
        val safeName = sanitizeFileName(originalName).ifBlank { "index.html" }
        val current = File(siteDir(page.siteId), currentRelativePath)
        val parentRel = currentRelativePath.substringBeforeLast('/', "")
        val newRel = if (parentRel.isBlank()) safeName else "$parentRel/$safeName"
        val dest = File(siteDir(page.siteId), newRel)
        var newEntry: String? = null
        if (current.isFile && current.absolutePath != dest.absolutePath) {
            if (dest.exists()) dest.delete()
            if (!current.renameTo(dest)) {
                return@withContext SourceAttachResult(
                    page.copy(sourceUri = uri.toString(), sourceKind = "file"),
                    "已关联原文件，但应用内文件名未能同步为「$originalName」"
                )
            }
        } else if (!dest.exists()) {
            return@withContext SourceAttachResult(page, "找不到应用内副本文件")
        }
        val entryUrl = if (page.entryUrl.endsWith(currentRelativePath)) {
            urlFor(page.siteId, newRel).also { newEntry = it }
        } else {
            page.entryUrl
        }
        SourceAttachResult(
            page.copy(sourceUri = uri.toString(), sourceKind = "file", entryUrl = entryUrl),
            "已关联手机原文件：$originalName",
            newEntry
        )
    }

    /** 旧版本导入、尚未关联原文件的站点：重新关联整个原文件夹 */
    suspend fun attachTreeSource(page: LocalPage, treeUri: Uri): SourceAttachResult =
        withContext(Dispatchers.IO) {
            persistAccess(treeUri)
            val tree = DocumentFile.fromTreeUri(context, treeUri)
            val name = tree?.name ?: "所选文件夹"
            SourceAttachResult(
                page.copy(sourceUri = treeUri.toString(), sourceKind = "tree"),
                "已关联手机原文件夹：$name"
            )
        }

    /** 在手机原文件夹中新建文本文件；单文件站点只保留应用内副本 */
    suspend fun createTextInSource(
        page: LocalPage,
        relativePath: String,
        text: String
    ): SourceSyncResult = withContext(Dispatchers.IO) {
        val uriText = page.sourceUri
            ?: return@withContext SourceSyncResult(false, "仅创建了应用内副本")
        if (page.sourceKind != "tree") {
            return@withContext SourceSyncResult(false, "单文件站点仅创建了应用内副本")
        }
        runCatching {
            var dir = DocumentFile.fromTreeUri(context, Uri.parse(uriText))
                ?: error("无法访问原文件夹")
            val segments = relativePath.split('/').filter { it.isNotEmpty() }
            segments.dropLast(1).forEach { segment ->
                dir = dir?.findFile(segment) ?: dir?.createDirectory(segment)
                    ?: error("原文件夹中无法创建目录 $segment")
            }
            val name = segments.last()
            val created = dir?.createFile(mimeFor(name), name) ?: error("无法创建原文件")
            context.contentResolver.openOutputStream(created.uri, "wt")?.use { output ->
                output.write(text.toByteArray(Charsets.UTF_8))
            } ?: error("无法写入原文件")
            SourceSyncResult(true, "已在手机原文件夹中创建 $name")
        }.getOrElse {
            SourceSyncResult(false, "同步新建原文件失败：${it.message}")
        }
    }

    /** 在手机原文件夹中新建目录；单文件站点只创建应用内目录 */
    suspend fun createFolderInSource(
        page: LocalPage,
        relativePath: String
    ): SourceSyncResult = withContext(Dispatchers.IO) {
        val uriText = page.sourceUri
            ?: return@withContext SourceSyncResult(false, "仅创建了应用内文件夹")
        if (page.sourceKind != "tree") {
            return@withContext SourceSyncResult(false, "单文件站点仅创建了应用内文件夹")
        }
        runCatching {
            var dir = DocumentFile.fromTreeUri(context, Uri.parse(uriText))
                ?: error("无法访问原文件夹")
            for (segment in relativePath.split('/').filter { it.isNotEmpty() }) {
                dir = dir?.findFile(segment) ?: dir?.createDirectory(segment)
                    ?: error("无法创建目录 $segment")
            }
            SourceSyncResult(true, "已在手机原文件夹中创建目录")
        }.getOrElse {
            SourceSyncResult(false, "同步新建目录失败：${it.message}")
        }
    }

    /** 删除手机原文件夹中的文件；单文件站点只删除应用内副本 */
    suspend fun deleteInSource(
        page: LocalPage,
        relativePath: String
    ): SourceSyncResult = withContext(Dispatchers.IO) {
        val uriText = page.sourceUri
            ?: return@withContext SourceSyncResult(false, "仅删除了应用内副本")
        if (page.sourceKind != "tree") {
            return@withContext SourceSyncResult(false, "单文件站点不支持删除原文件")
        }
        runCatching {
            var doc = DocumentFile.fromTreeUri(context, Uri.parse(uriText))
                ?: error("无法访问原文件夹")
            for (segment in relativePath.split('/').filter { it.isNotEmpty() }) {
                doc = doc?.findFile(segment) ?: error("原文件夹中找不到 $relativePath")
            }
            if (!doc!!.delete()) error("删除原文件失败")
            SourceSyncResult(true, "已同步删除手机原文件")
        }.getOrElse {
            SourceSyncResult(false, "同步删除原文件失败：${it.message}")
        }
    }

    private fun mimeFor(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "html", "htm" -> "text/html"
        "css" -> "text/css"
        "js", "mjs" -> "application/javascript"
        "json" -> "application/json"
        "xml", "svg" -> "text/xml"
        "md" -> "text/markdown"
        else -> "text/plain"
    }

    private fun queryDisplayName(uri: Uri): String {
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) return cursor.getString(index)
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "index.html"
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

    /** 列出站点内所有 HTML 文件（相对路径），供用户选择入口，避免几十个网页只能打开第一个 */
    fun htmlFiles(page: LocalPage): List<String> {
        val dir = siteDir(page.siteId)
        if (!dir.isDirectory) return emptyList()
        return dir.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in setOf("html", "htm") }
            .map { it.relativeTo(dir).invariantSeparatorsPath }
            .sortedBy { it.lowercase() }
            .toList()
    }

    fun entryUrlFor(page: LocalPage, relativePath: String): String =
        urlFor(page.siteId, relativePath)

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
