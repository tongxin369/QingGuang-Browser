package com.qingguang.browser.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import com.qingguang.browser.App
import com.qingguang.browser.data.LocalPage
import com.qingguang.browser.ui.theme.ThemeController
import com.qingguang.browser.util.LocalSiteManager
import com.qingguang.browser.web.BrowserController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.nio.charset.Charset

/** 可在编辑器中打开的文本文件后缀 */
private val TEXT_EXTENSIONS = setOf(
    "html", "htm", "css", "js", "mjs", "json", "xml", "svg", "txt", "md", "csv", "ts", "vue"
)

private data class LocalTarget(val siteId: String, val root: File, val file: File)

/** 解析本地站点 URL；在线网页返回 null */
private fun resolveLocalTarget(context: Context, url: String): LocalTarget? {
    val prefix = LocalSiteManager.ASSET_HOST + "/files/sites/"
    if (!url.startsWith(prefix)) return null
    val rest = url.removePrefix(prefix).substringBefore('?').substringBefore('#')
    val segments = rest.split('/').filter { it.isNotEmpty() }
    if (segments.isEmpty()) return null
    val siteId = Uri.decode(segments[0])
    val root = LocalSiteManager(context).siteDir(siteId)
    if (!root.isDirectory) return null
    val relative = segments.drop(1).joinToString("/") { Uri.decode(it) }
    var file = if (relative.isBlank()) root else File(root, relative)
    if (file.isDirectory) file = File(file, "index.html")
    if (!file.isFile) {
        file = root.walkTopDown()
            .firstOrNull { it.isFile && it.extension.lowercase() in TEXT_EXTENSIONS }
            ?: root
    }
    return LocalTarget(siteId, root, file)
}

private fun textFiles(root: File): List<File> = root.walkTopDown()
    .filter { it.isFile && it.extension.lowercase() in TEXT_EXTENSIONS }
    .sortedBy { it.relativeTo(root).path }
    .toList()

private fun subDirs(root: File): List<File> = root.walkTopDown()
    .filter { it.isDirectory && it != root }
    .sortedBy { it.relativeTo(root).path }
    .toList()

private fun relativePath(root: File, file: File): String =
    file.relativeTo(root).path.replace(File.separatorChar, '/')

/** 读取文本，优先 UTF-8，检测到乱码时回退 GBK，并返回所用编码 */
private fun readTextSmart(file: File): Pair<String, Charset> {
    val bytes = runCatching { file.readBytes() }.getOrDefault(ByteArray(0))
    val utf8 = bytes.toString(Charsets.UTF_8)
    if (utf8.contains('\uFFFD')) {
        val gbk = runCatching { bytes.toString(Charset.forName("GBK")) }.getOrNull()
        if (gbk != null) return gbk to Charset.forName("GBK")
    }
    return utf8 to Charsets.UTF_8
}

/** 文件列表按目录层级缩进，形成简单的树状视图 */
private fun treeIndent(relative: String): String =
    "  ".repeat(relative.count { it == '/' })

/** 按文件后缀选择 CodeMirror 语法模式 */
private fun editorMode(name: String?): String = when (name?.substringAfterLast('.', "")?.lowercase()) {
    "html", "htm" -> "htmlmixed"
    "css" -> "css"
    "js", "mjs", "ts", "json" -> "javascript"
    "xml", "svg" -> "xml"
    "md", "markdown" -> "markdown"
    else -> "text/plain"
}

/**
 * 网页源码查看 / 本地网页代码编辑。
 * 使用内置 CodeMirror（本地资源，离线可用）显示行号与语法高亮；
 * 保存时写入应用内副本，并尝试同步回手机原文件夹。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SourceEditorOverlay(controller: BrowserController, onClose: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as App
    val scope = rememberCoroutineScope()
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val url = controller.currentUrl
    val target = remember(url) { resolveLocalTarget(context, url) }
    val systemDark = isSystemInDarkTheme()
    val dark = when (ThemeController.mode) {
        1 -> false
        2 -> true
        else -> systemDark
    }

    var page by remember(url) { mutableStateOf<LocalPage?>(null) }
    var files by remember(url) { mutableStateOf(target?.let { textFiles(it.root) } ?: emptyList()) }
    var current by remember(url) { mutableStateOf<File?>(null) }
    var text by remember(url) { mutableStateOf("") }
    var remote by remember(url) { mutableStateOf("") }
    var loading by remember(url) { mutableStateOf(true) }
    var showList by remember(url) { mutableStateOf(target?.file?.isFile != true) }
    var dirty by remember(url) { mutableStateOf(false) }
    var editorReady by remember(url) { mutableStateOf(false) }
    var editorWebView by remember(url) { mutableStateOf<WebView?>(null) }
    var showRename by remember { mutableStateOf(false) }
    var renameName by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<File?>(null) }
    var pendingOpen by remember { mutableStateOf<File?>(null) }
    var charset by remember(url) { mutableStateOf(Charsets.UTF_8) }
    var showFind by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var findCount by remember { mutableStateOf(-1) }
    var showNewFile by remember { mutableStateOf(false) }
    var newFileName by remember { mutableStateOf("") }
    var showNewFolder by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var dirs by remember(url) { mutableStateOf(target?.let { subDirs(it.root) } ?: emptyList()) }
    var deleteTarget by remember { mutableStateOf<File?>(null) }
    var showRelink by remember { mutableStateOf(false) }
    var scrollTop by remember { mutableStateOf(0f) }
    var scrollTotal by remember { mutableStateOf(0f) }
    var scrollViewport by remember { mutableStateOf(0f) }
    var railHeight by remember { mutableStateOf(0f) }

    val assetLoader = remember {
        WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
            .build()
    }
    val bridge = remember(url) {
        object {
            @JavascriptInterface
            fun onDirty(value: Boolean) {
                mainHandler.post { dirty = value }
            }

            @JavascriptInterface
            fun onScroll(top: Float, total: Float, viewport: Float) {
                mainHandler.post {
                    scrollTop = top
                    scrollTotal = total
                    scrollViewport = viewport
                }
            }
        }
    }

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun openFile(file: File) {
        if (dirty) {
            pendingOpen = file
            return
        }
        scope.launch {
            loading = true
            val loaded = withContext(Dispatchers.IO) {
                runCatching { readTextSmart(file) }.getOrNull()
            }
            text = loaded?.first ?: ""
            charset = loaded?.second ?: Charsets.UTF_8
            current = file
            showList = false
            loading = false
        }
    }

    fun save() {
        val file = current ?: return
        val view = editorWebView ?: return
        val root = target?.root ?: return
        val pageNow = page
        val rel = relativePath(root, file)
        view.evaluateJavascript("window.getCode()") { raw ->
            val code = runCatching { JSONTokener(raw).nextValue() as? String }.getOrNull()
            if (code == null) {
                toast("读取编辑器内容失败")
                return@evaluateJavascript
            }
            scope.launch {
                val localOk = withContext(Dispatchers.IO) {
                    runCatching { file.writeText(code, charset); true }.getOrDefault(false)
                }
                if (!localOk) {
                    toast("保存失败：无法写入应用内文件")
                    return@launch
                }
                text = code
                val sync = if (pageNow != null) {
                    withContext(Dispatchers.IO) {
                        app.localSites.writeBackText(pageNow, rel, code, charset)
                    }
                } else null
                view.evaluateJavascript("window.markClean()", null)
                dirty = false
                toast(
                    when {
                        sync == null -> "已保存到应用内副本"
                        sync.synced -> "已保存并同步到手机原文件"
                        else -> "已保存到应用内，${sync.message}"
                    }
                )
                controller.reload()
            }
        }
    }

    fun rename(file: File) {
        val root = target?.root ?: return
        if (dirty && file == current) {
            toast("请先保存当前修改，再重命名文件")
            return
        }
        val name = renameName.trim()
        if (name.isBlank() || name.contains('/') || name.contains('\\')) {
            toast("文件名不合法")
            return
        }
        if (name == file.name) {
            showRename = false
            return
        }
        val dest = File(file.parentFile, name)
        if (dest.exists()) {
            toast("同名文件已存在")
            return
        }
        val siteId = target.siteId
        val oldRel = relativePath(root, file)
        val newRel = relativePath(root, dest)
        scope.launch {
            val pageNow = page
            var newSourceUri: String? = null
            if (pageNow != null && pageNow.sourceUri != null) {
                val sync = withContext(Dispatchers.IO) {
                    app.localSites.renameInSource(pageNow, oldRel, name)
                }
                if (!sync.synced) {
                    showRename = false
                    toast("未重命名：${sync.message}")
                    return@launch
                }
                newSourceUri = sync.newUri
            }
            if (!file.renameTo(dest)) {
                toast("应用内重命名失败")
                return@launch
            }
            if (pageNow != null) {
                val newEntry = if (pageNow.entryUrl == LocalSiteManager.urlFor(siteId, oldRel)) {
                    LocalSiteManager.urlFor(siteId, newRel)
                } else {
                    pageNow.entryUrl
                }
                val updated = pageNow.copy(
                    sourceUri = newSourceUri ?: pageNow.sourceUri,
                    entryUrl = newEntry
                )
                app.database.localPageDao().upsert(updated)
                page = updated
                if (newEntry != pageNow.entryUrl) {
                    controller.loadUrl(newEntry)
                } else {
                    controller.reload()
                }
            } else {
                controller.reload()
            }
            files = textFiles(root)
            dirs = subDirs(root)
            current = dest
            showRename = false
            toast("已重命名为：$name")
        }
    }

    fun attachSource(uri: Uri, isTree: Boolean) {
        val pageNow = page ?: return
        val rootDir = target?.root ?: return
        val file = current ?: return
        val rel = relativePath(rootDir, file)
        scope.launch {
            loading = true
            val result = withContext(Dispatchers.IO) {
                if (isTree) {
                    app.localSites.attachTreeSource(pageNow, uri)
                } else {
                    app.localSites.attachFileSource(pageNow, uri, rel)
                }
            }
            app.database.localPageDao().upsert(result.page)
            page = result.page
            result.newEntryUrl?.let { newUrl ->
                current = resolveLocalTarget(context, newUrl)?.file
                controller.loadUrl(newUrl)
            }
            files = withContext(Dispatchers.IO) { textFiles(rootDir) }
            dirs = withContext(Dispatchers.IO) { subDirs(rootDir) }
            showRelink = false
            loading = false
            toast(result.message)
        }
    }

    val relinkFilePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { attachSource(it, false) }
        }
    }
    val relinkTreePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { attachSource(it, true) }
        }
    }

    fun openRelinkPicker(isTree: Boolean) {
        val intent = if (isTree) {
            Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        } else {
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
        }.apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
        }
        if (isTree) relinkTreePicker.launch(intent) else relinkFilePicker.launch(intent)
    }

    fun createFile() {
        val rootDir = target?.root ?: return
        val name = newFileName.trim().replace('\\', '/').trim('/')
        if (name.isBlank() || name.split('/').any { it.isBlank() || it == "." || it == ".." }) {
            toast("文件名不合法")
            return
        }
        val file = File(rootDir, name)
        if (file.exists()) {
            toast("同名文件已存在")
            return
        }
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    file.parentFile?.mkdirs()
                    file.writeText("", Charsets.UTF_8)
                    true
                }.getOrDefault(false)
            }
            if (!ok) {
                toast("新建文件失败")
                return@launch
            }
            val pageNow = page
            if (pageNow != null) {
                val sync = withContext(Dispatchers.IO) {
                    app.localSites.createTextInSource(pageNow, name, "")
                }
                toast(sync.message)
            }
            files = textFiles(rootDir)
            dirs = subDirs(rootDir)
            showNewFile = false
            newFileName = ""
            openFile(file)
        }
    }

    fun createFolder() {
        val rootDir = target?.root ?: return
        val name = newFolderName.trim().replace('\\', '/').trim('/')
        if (name.isBlank() || name.split('/').any { it.isBlank() || it == "." || it == ".." }) {
            toast("文件夹名不合法")
            return
        }
        if (File(rootDir, name).exists()) {
            toast("同名文件或文件夹已存在")
            return
        }
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { File(rootDir, name).mkdirs() }.getOrDefault(false)
            }
            if (!ok) {
                toast("新建文件夹失败")
                return@launch
            }
            val pageNow = page
            if (pageNow != null) {
                val sync = withContext(Dispatchers.IO) {
                    app.localSites.createFolderInSource(pageNow, name)
                }
                toast(sync.message)
            }
            files = textFiles(rootDir)
            dirs = subDirs(rootDir)
            showNewFolder = false
            newFolderName = ""
        }
    }

    fun deleteFile(file: File) {
        val rootDir = target?.root ?: return
        scope.launch {
            val pageNow = page
            if (pageNow?.sourceKind == "file") {
                toast("单文件站点不支持删除，请改为导入整个文件夹")
                deleteTarget = null
                return@launch
            }
            if (pageNow != null && pageNow.sourceUri != null) {
                val rel = relativePath(rootDir, file)
                val sync = withContext(Dispatchers.IO) {
                    app.localSites.deleteInSource(pageNow, rel)
                }
                if (!sync.synced) {
                    toast("未删除：${sync.message}")
                    deleteTarget = null
                    return@launch
                }
            }
            if (!file.delete()) {
                toast("删除应用内文件失败")
                deleteTarget = null
                return@launch
            }
            files = textFiles(rootDir)
            dirs = subDirs(rootDir)
            if (current?.absolutePath == file.absolutePath) {
                val next = files.firstOrNull()
                if (next != null) {
                    current = null
                    openFile(next)
                } else {
                    current = null
                    showList = true
                }
            }
            deleteTarget = null
            toast("已删除")
        }
    }

    LaunchedEffect(url) {
        loading = true
        if (target != null) {
            val siteId = target.siteId
            page = withContext(Dispatchers.IO) {
                app.database.localPageDao().all().firstOrNull { it.siteId == siteId }
            }
            val file = target.file.takeIf { it.isFile }
            if (file != null) {
                val loaded = withContext(Dispatchers.IO) {
                    runCatching { readTextSmart(file) }.getOrNull()
                }
                text = loaded?.first ?: ""
                charset = loaded?.second ?: Charsets.UTF_8
                current = file
            }
            files = withContext(Dispatchers.IO) { textFiles(target.root) }
            dirs = withContext(Dispatchers.IO) { subDirs(target.root) }
            showList = file == null
            loading = false
        } else {
            controller.fetchPageSource { source ->
                remote = source
                loading = false
            }
        }
    }

    // 切换文件 / 首次加载时把内容送入编辑器
    LaunchedEffect(editorReady, current, remote, loading) {
        val view = editorWebView ?: return@LaunchedEffect
        if (!editorReady || loading) return@LaunchedEffect
        if (current == null && remote.isBlank()) return@LaunchedEffect
        val content = if (current != null) text else remote
        view.evaluateJavascript(
            "window.setEditor(" +
                JSONObject.quote(content) + "," +
                JSONObject.quote(editorMode(current?.name)) + "," +
                (current == null) + "," + dark + ")",
            null
        )
        dirty = false
    }

    LaunchedEffect(dark) {
        editorWebView?.evaluateJavascript("window.setDark($dark)", null)
    }

    BackHandler { onClose() }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Column(Modifier.weight(1f)) {
                    Text("网页源码", fontSize = 17.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                    Text(
                        if (target != null) {
                            val rel = current?.let { relativePath(target.root, it) } ?: "本地网页"
                            "$rel · " + when {
                                page?.sourceUri == null -> "仅应用内副本"
                                page?.sourceKind == "tree" -> "已关联手机原文件夹"
                                else -> "已关联手机原文件"
                            }
                        } else {
                            "在线网页（只读）"
                        },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (target != null) {
                    TextButton(onClick = {
                        if (dirty) {
                            toast("请先保存当前修改，再切换文件")
                        } else {
                            showList = !showList
                        }
                    }) {
                        Text("文件", fontSize = 13.sp)
                    }
                    if (page?.sourceUri == null) {
                        TextButton(onClick = { showRelink = true }) {
                            Text("关联", fontSize = 13.sp)
                        }
                    }
                    TextButton(
                        enabled = current != null,
                        onClick = {
                            showFind = !showFind
                            if (!showFind) findCount = -1
                        }
                    ) {
                        Text("查找", fontSize = 13.sp)
                    }
                    TextButton(
                        enabled = current != null && dirty,
                        onClick = { save() }
                    ) {
                        Text("保存", fontSize = 13.sp)
                    }
                }
            }
            HorizontalDivider()
            if (showFind && current != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = findQuery,
                        onValueChange = {
                            findQuery = it
                            findCount = -1
                        },
                        singleLine = true,
                        placeholder = { Text("查找内容", fontSize = 13.sp) },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        enabled = findQuery.isNotBlank(),
                        onClick = {
                            editorWebView?.evaluateJavascript(
                                "window.findNext(${JSONObject.quote(findQuery)}, false)"
                            ) { raw ->
                                findCount = raw?.trim()?.toIntOrNull() ?: -1
                            }
                        }
                    ) { Text("下一个") }
                    if (findCount >= 0) {
                        Text("$findCount 处", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Box(
                Modifier
                    .weight(1f)
                    .onSizeChanged { railHeight = it.height.toFloat() }
            ) {
                if (loading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = false
                                settings.allowFileAccess = false
                                settings.allowContentAccess = false
                                settings.builtInZoomControls = true
                                settings.displayZoomControls = false
                                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                addJavascriptInterface(bridge, "QingGuangEditor")
                                webViewClient = object : WebViewClient() {
                                    override fun shouldInterceptRequest(
                                        view: WebView,
                                        request: WebResourceRequest
                                    ): WebResourceResponse? =
                                        assetLoader.shouldInterceptRequest(request.url)

                                    override fun onPageFinished(view: WebView, url: String?) {
                                        mainHandler.post { editorReady = true }
                                    }
                                }
                                editorReady = false
                                loadUrl(
                                    LocalSiteManager.ASSET_HOST + "/assets/codeeditor/editor.html"
                                )
                                editorWebView = this
                            }
                        }
                    )
                }
                if (target != null && showList) {
                    Surface(
                        Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        LazyColumn(Modifier.fillMaxSize()) {
                            item {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "文件列表",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = {
                                        newFolderName = ""
                                        showNewFolder = true
                                    }) { Text("新建文件夹", fontSize = 13.sp) }
                                    TextButton(onClick = {
                                        newFileName = ""
                                        showNewFile = true
                                    }) { Text("新建", fontSize = 13.sp) }
                                }
                            }
                            items(dirs, key = { "d-" + it.absolutePath }) { dir ->
                                val rel = relativePath(target.root, dir)
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Filled.Folder,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        treeIndent(rel) + dir.name,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        "文件夹",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                }
                            }
                            items(files, key = { it.absolutePath }) { file ->
                                val rel = relativePath(target.root, file)
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { openFile(file) }
                                        .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            treeIndent(rel) + file.name,
                                            fontSize = 13.sp,
                                            color = if (file == current) {
                                                MaterialTheme.colorScheme.secondary
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            },
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val parent = rel.substringBeforeLast('/', "")
                                        if (parent.isNotBlank()) {
                                            Text(
                                                parent,
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Text(
                                        "${file.length()} B",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    IconButton(onClick = {
                                        renameTarget = file
                                        renameName = file.name
                                        showRename = true
                                    }) {
                                        Icon(
                                            Icons.Filled.Edit,
                                            contentDescription = "重命名",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(onClick = { deleteTarget = file }) {
                                        Icon(
                                            Icons.Filled.Delete,
                                            contentDescription = "删除",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                            if (files.isEmpty()) {
                                item {
                                    Text(
                                        "该站点下没有可编辑的文本文件",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                            }
                            if (files.size <= 1 && page?.sourceKind != "tree") {
                                item {
                                    Text(
                                        "该网页是单文件导入，只能编辑入口文件；子网页源码需要把整个网站以「文件夹」方式导入后才能查看和修改。",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                if (!showList && !loading && current != null &&
                    scrollTotal > scrollViewport + 1f && railHeight > 0f
                ) {
                    val density = LocalDensity.current
                    val thumbPx = (railHeight * scrollViewport / scrollTotal)
                        .coerceIn(with(density) { 28.dp.toPx() }, railHeight)
                    val maxTravel = (railHeight - thumbPx).coerceAtLeast(1f)
                    val ratio = (scrollTop / (scrollTotal - scrollViewport).coerceAtLeast(1f))
                        .coerceIn(0f, 1f)
                    fun seek(y: Float) {
                        val seekRatio = ((y - thumbPx / 2f) / maxTravel).coerceIn(0f, 1f)
                        editorWebView?.evaluateJavascript("window.scrollToRatio($seekRatio)", null)
                    }
                    Box(
                        Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .width(18.dp)
                            .onSizeChanged { railHeight = it.height.toFloat() }
                            .pointerInput(scrollTotal, scrollViewport, railHeight) {
                                detectTapGestures { offset -> seek(offset.y) }
                            }
                            .pointerInput(scrollTotal, scrollViewport, railHeight) {
                                detectVerticalDragGestures { change, _ -> seek(change.position.y) }
                            }
                    ) {
                        Box(
                            Modifier
                                .align(Alignment.CenterEnd)
                                .width(3.dp)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f))
                        )
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .offset { IntOffset(0, (ratio * maxTravel).toInt()) }
                                .width(7.dp)
                                .height(with(density) { thumbPx.toDp() })
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
                        )
                    }
                }
            }
        }
    }

    if (showRename) {
        AlertDialog(
            onDismissRequest = {
                showRename = false
                renameTarget = null
            },
            title = { Text("重命名文件") },
            text = {
                Column {
                    OutlinedTextField(
                        value = renameName,
                        onValueChange = { renameName = it },
                        singleLine = true,
                        label = { Text("文件名") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "会同时修改手机原文件夹中的文件名（已关联时）",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { renameTarget?.let { rename(it) } },
                    enabled = renameName.isNotBlank() && renameTarget != null
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRename = false
                    renameTarget = null
                }) { Text("取消") }
            }
        )
    }

    if (showNewFolder) {
        AlertDialog(
            onDismissRequest = { showNewFolder = false },
            title = { Text("新建文件夹") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        singleLine = true,
                        label = { Text("文件夹名") },
                        placeholder = { Text("例如 assets/images") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "已关联原文件夹时，会同时在手机原文件夹中创建",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { createFolder() },
                    enabled = newFolderName.isNotBlank()
                ) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolder = false }) { Text("取消") }
            }
        )
    }

    if (showNewFile) {
        AlertDialog(
            onDismissRequest = { showNewFile = false },
            title = { Text("新建文件") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newFileName,
                        onValueChange = { newFileName = it },
                        singleLine = true,
                        label = { Text("文件名") },
                        placeholder = { Text("例如 style.css") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "已关联原文件夹时，会同时在手机原文件夹中创建",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { createFile() },
                    enabled = newFileName.isNotBlank()
                ) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = { showNewFile = false }) { Text("取消") }
            }
        )
    }

    deleteTarget?.let { file ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除文件？") },
            text = { Text("将删除「${file.name}」；已关联原文件夹时会同步删除手机原文件。") },
            confirmButton = {
                TextButton(onClick = { deleteFile(file) }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            }
        )
    }

    if (showRelink) {
        AlertDialog(
            onDismissRequest = { showRelink = false },
            title = { Text("关联手机原文件") },
            text = {
                Column {
                    Text(
                        "此站点是在同步功能上线前导入的，目前只有应用内副本。重新选择原文件或原文件夹后，保存和重命名会同步到手机原文件。",
                        fontSize = 13.sp
                    )
                    TextButton(
                        onClick = { openRelinkPicker(false) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("选择原始网页文件", fontSize = 14.sp) }
                    TextButton(
                        onClick = { openRelinkPicker(true) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("选择原始文件夹", fontSize = 14.sp) }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showRelink = false }) { Text("取消") }
            }
        )
    }

    pendingOpen?.let { file ->
        AlertDialog(
            onDismissRequest = { pendingOpen = null },
            title = { Text("放弃未保存的修改？") },
            text = { Text("当前文件还有未保存的修改，切换后会丢失。") },
            confirmButton = {
                TextButton(onClick = {
                    pendingOpen = null
                    dirty = false
                    openFile(file)
                }) { Text("放弃并切换") }
            },
            dismissButton = {
                TextButton(onClick = { pendingOpen = null }) { Text("取消") }
            }
        )
    }
}
