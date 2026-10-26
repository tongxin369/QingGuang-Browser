package com.qingguang.browser.ui

import android.annotation.SuppressLint
import android.content.Context
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
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

private fun relativePath(root: File, file: File): String =
    file.relativeTo(root).path.replace(File.separatorChar, '/')

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
    var pendingOpen by remember { mutableStateOf<File?>(null) }

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
            val content = withContext(Dispatchers.IO) {
                runCatching { file.readText() }.getOrDefault("")
            }
            text = content
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
                    runCatching { file.writeText(code); true }.getOrDefault(false)
                }
                if (!localOk) {
                    toast("保存失败：无法写入应用内文件")
                    return@launch
                }
                text = code
                val sync = if (pageNow != null) {
                    withContext(Dispatchers.IO) {
                        app.localSites.writeBackText(pageNow, rel, code)
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

    fun rename() {
        val file = current ?: return
        val root = target?.root ?: return
        if (dirty) {
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
            current = dest
            showRename = false
            toast("已重命名为：$name")
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
                text = withContext(Dispatchers.IO) {
                    runCatching { file.readText() }.getOrDefault("")
                }
                current = file
            }
            files = withContext(Dispatchers.IO) { textFiles(target.root) }
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
                    TextButton(
                        enabled = current != null,
                        onClick = {
                            renameName = current?.name ?: ""
                            showRename = true
                        }
                    ) {
                        Text("重命名", fontSize = 13.sp)
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
            Box(Modifier.weight(1f)) {
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
                            items(files, key = { it.absolutePath }) { file ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { openFile(file) }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        relativePath(target.root, file),
                                        fontSize = 13.sp,
                                        color = if (file == current) {
                                            MaterialTheme.colorScheme.secondary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "${file.length()} B",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
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
                        }
                    }
                }
            }
        }
    }

    if (showRename) {
        AlertDialog(
            onDismissRequest = { showRename = false },
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
                    onClick = { rename() },
                    enabled = renameName.isNotBlank()
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) { Text("取消") }
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
