package com.qingguang.browser.web

import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.print.PrintAttributes
import android.print.PrintManager
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Base64
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewFeature
import com.qingguang.browser.App
import com.qingguang.browser.data.DownloadRecord
import com.qingguang.browser.data.HistoryItem
import com.qingguang.browser.util.BackupInfo
import com.qingguang.browser.util.DownloadNotifier
import com.qingguang.browser.util.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONTokener
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class BrowserTab(val id: String, val webView: WebView)

data class TabSummary(
    val id: String,
    val title: String,
    val url: String,
    val isActive: Boolean
)

/** 等待用户确认的下载请求 */
data class PendingDownload(
    val url: String,
    val userAgent: String,
    val contentDisposition: String,
    val mimeType: String,
    val suggestedName: String,
    val sizeBytes: Long? = null,
    val sizeChecked: Boolean = false
) {
    val isBlobOrData: Boolean get() = url.startsWith("blob:") || url.startsWith("data:")
}

/**
 * 浏览器核心：多标签管理 + WebView 配置 + 本地资源拦截 + 下载 + 位置权限路由。
 * 由 Activity 持有，生命周期与 Activity 一致。
 */
class BrowserController(private val activity: Activity) {

    private val app = activity.applicationContext as App
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val downloadManager = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val defaultUserAgent = WebSettings.getDefaultUserAgent(activity)

    private val assetLoader = WebViewAssetLoader.Builder()
        .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(activity.applicationContext))
        .addPathHandler(
            "/files/",
            WebViewAssetLoader.InternalStoragePathHandler(activity.applicationContext, activity.filesDir)
        )
        .build()

    private val tabs = mutableListOf<BrowserTab>()
    private var activeIndex = -1
    private var container: FrameLayout? = null

    var currentUrl by mutableStateOf("")
        private set
    var currentTitle by mutableStateOf("")
        private set
    var progress by mutableStateOf(0f)
        private set
    var canGoBack by mutableStateOf(false)
        private set
    var canGoForward by mutableStateOf(false)
        private set
    var tabCount by mutableStateOf(0)
        private set
    var tabList by mutableStateOf<List<TabSummary>>(emptyList())
        private set
    var showHome by mutableStateOf(true)
        private set

    /** 当前网页主题色（ARGB）；null 表示未取到，工具栏使用应用默认配色 */
    var pageThemeColor by mutableStateOf<Int?>(null)
        private set

    /** 待确认的下载（弹出确认框，确认后才真正下载） */
    var pendingDownload by mutableStateOf<PendingDownload?>(null)
        private set

    /** 顶/底栏是否跟随网页主题色 */
    var followPageTheme by mutableStateOf(app.settings.followPageTheme)
        private set

    /** 由 Activity 注入：申请系统定位权限，结果回传给 WebView */
    var locationRequester: ((callback: (Boolean) -> Unit) -> Unit)? = null

    /** 由 Activity 注入：处理网页内的文件选择（<input type="file">） */
    var fileChooserRequester:
        ((params: WebChromeClient.FileChooserParams, callback: (Array<Uri>?) -> Unit) -> Unit)? = null

    /** 由 Activity 注入：申请 Android 13+ 通知权限 */
    var notificationPermissionRequester: ((callback: (Boolean) -> Unit) -> Unit)? = null

    /** 是否正在朗读网页 */
    var isSpeaking by mutableStateOf(false)
        private set

    private var tts: TextToSpeech? = null
    private var ttsLastUtterance: String? = null

    // ---------------- 容器与标签 ----------------

    fun attachContainer(frame: FrameLayout) {
        container = frame
        // 首次进入至少保留一个空白标签，标签数字不会显示为 0
        if (tabs.isEmpty()) newTabInternal()
        renderContainer()
        refresh()
    }

    fun newTab(): BrowserTab {
        val tab = newTabInternal()
        showHome = true
        refresh()
        return tab
    }

    private fun newTabInternal(): BrowserTab {
        val tab = BrowserTab(UUID.randomUUID().toString(), createWebView())
        tabs.add(tab)
        activeIndex = tabs.size - 1
        renderContainer()
        return tab
    }

    fun closeTab(id: String) {
        val index = tabs.indexOfFirst { it.id == id }
        if (index < 0) return
        val tab = tabs.removeAt(index)
        container?.removeView(tab.webView)
        tab.webView.stopLoading()
        tab.webView.destroy()
        activeIndex = when {
            tabs.isEmpty() -> -1
            index >= tabs.size -> tabs.size - 1
            else -> index
        }
        if (tabs.isEmpty()) showHome = true
        renderContainer()
        refresh()
    }

    fun switchTab(id: String) {
        val index = tabs.indexOfFirst { it.id == id }
        if (index < 0) return
        activeIndex = index
        showHome = false
        pageThemeColor = null
        renderContainer()
        refresh()
        tabs[index].webView.let { updatePageTheme(it) }
    }

    fun goHome() {
        stopReadAloud()
        val empty = tabs.firstOrNull { it.webView.url.isNullOrEmpty() }
        if (empty != null) {
            activeIndex = tabs.indexOf(empty)
        } else {
            newTabInternal()
        }
        showHome = true
        pageThemeColor = null
        renderContainer()
        refresh()
    }

    private fun currentTab(): BrowserTab? = tabs.getOrNull(activeIndex)

    private fun renderContainer() {
        val frame = container ?: return
        frame.removeAllViews()
        currentTab()?.let { tab ->
            if (tab.webView.parent != frame) {
                (tab.webView.parent as? ViewGroup)?.removeView(tab.webView)
                frame.addView(
                    tab.webView,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
            }
        }
    }

    // ---------------- 导航 ----------------

    fun submitInput(input: String) {
        val url = normalizeInput(input)
        if (url.isBlank()) return
        loadUrl(url)
    }

    fun loadUrl(url: String) {
        stopReadAloud()
        val tab = currentTab() ?: newTabInternal()
        showHome = false
        tab.webView.loadUrl(url)
        refresh()
    }

    fun reload() {
        currentTab()?.webView?.let {
            if (it.url.isNullOrEmpty()) it.reload() else it.reload()
        }
    }

    /** 读取当前页面渲染后的 HTML 源码（在线页面只读查看用） */
    fun fetchPageSource(callback: (String) -> Unit) {
        val webView = currentTab()?.webView
        if (webView == null) {
            callback("")
            return
        }
        webView.evaluateJavascript(
            "(function(){return '<!DOCTYPE html>\\n' + document.documentElement.outerHTML;})()"
        ) { raw ->
            val text = runCatching {
                JSONTokener(raw).nextValue() as? String
            }.getOrNull()
            callback(text ?: "")
        }
    }

    fun goBack(): Boolean {
        val tab = currentTab() ?: return false
        stopReadAloud()
        return if (tab.webView.canGoBack()) {
            tab.webView.goBack()
            true
        } else false
    }

    fun goForward(): Boolean {
        val tab = currentTab() ?: return false
        stopReadAloud()
        return if (tab.webView.canGoForward()) {
            tab.webView.goForward()
            true
        } else false
    }

    fun goBackToHome() {
        showHome = true
    }

    fun hideHome() {
        showHome = false
    }

    // ---------------- WebView 配置 ----------------

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(): WebView {
        val webView = WebView(activity)
        val settings = webView.settings
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = true
            allowContentAccess = true
            javaScriptCanOpenWindowsAutomatically = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            setGeolocationEnabled(true)
            textZoom = app.settings.textZoom
            userAgentString = if (app.settings.desktopMode) SettingsStore.DESKTOP_UA else defaultUserAgent
        }
        applyDarkMode(webView)
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val url = request.url.toString()
                if (url.startsWith("file://")) {
                    toast("出于安全考虑，浏览器不直接打开 file:// 地址")
                    return true
                }
                return false
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                if (view !== currentTab()?.webView) return
                showHome = false
                progress = 0.02f
                pageThemeColor = null
                refresh()
            }

            override fun onPageFinished(view: WebView, url: String?) {
                if (view === currentTab()?.webView) {
                    progress = 1f
                    recordHistory(view)
                    refresh()
                    updatePageTheme(view)
                    // Android WebView 不实现 File System Access API，注入兼容实现，网页可多选文件
                    view.evaluateJavascript(PICKER_POLYFILL_JS, null)
                    // 页面加载完成后自动备份一次，降低"交互后直接退出"导致的数据丢失风险
                    if (app.settings.autoBackup && url != null && !url.startsWith("about:")) {
                        app.backups.backupSite(view, url, view.title ?: url) { _, _ -> }
                    }
                }
            }

        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                if (view === currentTab()?.webView) progress = newProgress / 100f
            }

            override fun onReceivedTitle(view: WebView, title: String?) {
                if (view === currentTab()?.webView) refresh()
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String,
                callback: GeolocationPermissions.Callback
            ) {
                val requester = locationRequester
                if (requester == null) {
                    callback.invoke(origin, false, true)
                    return
                }
                requester { granted -> callback.invoke(origin, granted, true) }
            }

            override fun onShowFileChooser(
                view: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                val requester = fileChooserRequester ?: return false
                requester(fileChooserParams) { uris -> filePathCallback.onReceiveValue(uris) }
                return true
            }
        }

        webView.setDownloadListener(DownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            requestDownload(url, userAgent, contentDisposition, mimetype)
        })
        webView.addJavascriptInterface(BlobDownloadBridge(), "QingGuangDownload")

        return webView
    }

    private fun applyDarkMode(webView: WebView) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) return
        val mode = app.settings.darkMode
        val allowed = when (mode) {
            1 -> true
            2 -> false
            else -> isSystemDark()
        }
        WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.settings, allowed)
    }

    private fun isSystemDark(): Boolean =
        (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    /** 读取网页主题色（meta theme-color 或页面背景色），用于顶/底栏跟随 */
    private fun updatePageTheme(view: WebView) {
        if (!followPageTheme) {
            pageThemeColor = null
            return
        }
        view.evaluateJavascript(THEME_COLOR_JS) { raw ->
            val hex = runCatching { JSONTokener(raw).nextValue() as? String }.getOrNull()
            pageThemeColor = hex?.takeIf { it.length == 7 && it.startsWith("#") }
                ?.let { runCatching { android.graphics.Color.parseColor(it) }.getOrNull() }
        }
    }

    /** 切换"顶/底栏跟随网页主题色" */
    fun applyFollowPageTheme(enabled: Boolean) {
        followPageTheme = enabled
        app.settings.followPageTheme = enabled
        if (!enabled) {
            pageThemeColor = null
        } else {
            currentTab()?.webView?.let { updatePageTheme(it) }
        }
    }

    /** 切换电脑模式（桌面版 UA） */
    fun toggleDesktopMode() {
        app.settings.desktopMode = !app.settings.desktopMode
        applySettings()
        toast(if (app.settings.desktopMode) "已切换到电脑模式" else "已切换回手机模式")
    }

    /** 设置变化后应用到所有标签 */
    fun applySettings() {
        tabs.forEach { tab ->
            tab.webView.settings.textZoom = app.settings.textZoom
            tab.webView.settings.userAgentString =
                if (app.settings.desktopMode) SettingsStore.DESKTOP_UA else defaultUserAgent
            applyDarkMode(tab.webView)
        }
        currentTab()?.webView?.reload()
    }

    // ---------------- 下载 ----------------

    /** 收到下载链接：先弹确认框，由用户确认（可改名）后再下载 */
    private fun requestDownload(
        url: String,
        userAgent: String,
        contentDisposition: String,
        mimetype: String
    ) {
        val fileName = if (url.startsWith("data:")) {
            fallbackFileName(contentDisposition, mimetype)
        } else {
            URLUtil.guessFileName(url, contentDisposition, mimetype)
        }
        val pending = PendingDownload(
            url = url,
            userAgent = userAgent,
            contentDisposition = contentDisposition,
            mimeType = mimetype,
            suggestedName = fileName
        )
        pendingDownload = pending
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            pendingDownload = pending.copy(sizeChecked = true)
            return
        }
        // 预取文件大小，供确认框显示（失败则显示未知）
        scope.launch {
            val size = withContext(Dispatchers.IO) {
                runCatching {
                    val conn = URL(url).openConnection() as HttpURLConnection
                    conn.requestMethod = "HEAD"
                    conn.connectTimeout = 6000
                    conn.readTimeout = 6000
                    if (userAgent.isNotBlank()) conn.setRequestProperty("User-Agent", userAgent)
                    conn.connect()
                    val length = conn.contentLengthLong
                    conn.disconnect()
                    length.takeIf { it > 0 }
                }.getOrNull()
            }
            pendingDownload = pendingDownload?.takeIf { it.url == url }
                ?.copy(sizeBytes = size, sizeChecked = true)
        }
    }

    fun cancelDownload() {
        pendingDownload = null
    }

    /** 用户确认下载后执行（fileName 为弹窗中最终的文件名） */
    fun confirmDownload(fileName: String) {
        val pending = pendingDownload ?: return
        pendingDownload = null
        // Android 13+ 首次下载时申请通知权限（拒绝也不影响下载本身）
        notificationPermissionRequester?.invoke { }
        val finalName = sanitizeDownloadName(fileName).ifBlank { pending.suggestedName }
        if (pending.isBlobOrData) {
            downloadBlobOrData(pending.url, pending.contentDisposition, pending.mimeType, finalName)
        } else {
            enqueueDownload(pending, finalName)
        }
    }

    private fun enqueueDownload(pending: PendingDownload, fileName: String) {
        runCatching {
            val request = DownloadManager.Request(Uri.parse(pending.url))
                .setMimeType(pending.mimeType)
                .setTitle(fileName)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            if (pending.userAgent.isNotBlank()) {
                request.addRequestHeader("User-Agent", pending.userAgent)
            }
            val id = downloadManager.enqueue(request)
            scope.launch {
                app.database.downloadDao().insert(
                    DownloadRecord(
                        fileName = fileName,
                        url = pending.url,
                        localPath = "系统下载管理 #$id",
                        status = "已提交下载"
                    )
                )
            }
            toast("已加入下载：$fileName")
        }.onFailure { toast("下载失败：${it.message}") }
    }

    private fun sanitizeDownloadName(name: String): String =
        name.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")

    /** blob/data 链接下载：data 直接解码，blob 通过 JS 读取后经桥接回传 */
    private fun downloadBlobOrData(
        url: String,
        contentDisposition: String,
        mimetype: String,
        nameOverride: String? = null
    ) {
        val fileName = nameOverride?.takeIf { it.isNotBlank() }
            ?: fallbackFileName(contentDisposition, mimetype)
        if (url.startsWith("data:")) {
            val comma = url.indexOf(',')
            if (comma <= 0) {
                toast("下载失败：链接格式不正确")
                return
            }
            val meta = url.substring(5, comma)
            val payload = url.substring(comma + 1)
            val bytes = runCatching {
                if (meta.contains("base64", ignoreCase = true)) {
                    Base64.decode(payload, Base64.DEFAULT)
                } else {
                    Uri.decode(payload).toByteArray(Charsets.UTF_8)
                }
            }.getOrNull()
            if (bytes == null) {
                toast("下载失败：数据解析失败")
                return
            }
            val mime = meta.substringBefore(';').ifBlank { mimetype }
            saveDownloadedBytes(fileName, mime, bytes, url)
            return
        }
        val webView = currentTab()?.webView
        if (webView == null) {
            toast("下载失败：当前没有页面")
            return
        }
        val safeUrl = url.replace("\\", "\\\\").replace("'", "\\'")
        val safeName = fileName.replace("\\", "\\\\").replace("'", "\\'")
        val safeMime = mimetype.replace("\\", "\\\\").replace("'", "\\'")
        val js = """
            (function(){
              fetch('$safeUrl').then(function(r){return r.blob();}).then(function(b){
                var fr = new FileReader();
                fr.onload = function(){
                  var s = fr.result;
                  var i = s.indexOf(',');
                  QingGuangDownload.saveBase64('$safeName', b.type || '$safeMime', s.substring(i + 1));
                };
                fr.onerror = function(){ QingGuangDownload.fail('读取数据失败'); };
                fr.readAsDataURL(b);
              }).catch(function(e){ QingGuangDownload.fail(e.message); });
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    private fun fallbackFileName(contentDisposition: String, mimetype: String): String {
        val guessed = runCatching {
            URLUtil.guessFileName("", contentDisposition, mimetype)
        }.getOrDefault("")
        if (guessed.isNotBlank() && !guessed.startsWith("downloadfile")) return guessed
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimetype) ?: "bin"
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())
        return "网页下载-$stamp.$ext"
    }

    private fun saveDownloadedBytes(fileName: String, mime: String, bytes: ByteArray, sourceUrl: String) {
        runCatching {
            val resolver = app.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mime.ifBlank { "application/octet-stream" })
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("无法创建文件")
            resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("写入失败")
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            scope.launch {
                app.database.downloadDao().insert(
                    DownloadRecord(
                        fileName = fileName,
                        url = sourceUrl,
                        localPath = "${Environment.DIRECTORY_DOWNLOADS}/$fileName",
                        status = "下载完成"
                    )
                )
            }
            DownloadNotifier.notifyComplete(app, fileName)
            toast("下载完成：$fileName")
        }.onFailure { toast("下载失败：${it.message}") }
    }

    /** 供网页 JS 调用的 blob 下载桥接 */
    inner class BlobDownloadBridge {
        @JavascriptInterface
        fun saveBase64(name: String?, mime: String?, base64: String?) {
            if (base64.isNullOrBlank()) return
            val bytes = runCatching { Base64.decode(base64, Base64.DEFAULT) }.getOrNull() ?: return
            val fileName = name?.takeIf { it.isNotBlank() } ?: fallbackFileName("", mime ?: "")
            activity.runOnUiThread {
                saveDownloadedBytes(fileName, mime ?: "", bytes, "blob")
            }
        }

        @JavascriptInterface
        fun fail(message: String?) {
            activity.runOnUiThread { toast("下载失败：${message ?: "读取数据失败"}") }
        }
    }

    // ---------------- 历史与备份 ----------------

    private fun recordHistory(webView: WebView) {
        val url = webView.url ?: return
        if (url.startsWith("about:") || url.startsWith("data:")) return
        val title = webView.title?.takeIf { it.isNotBlank() } ?: url
        scope.launch {
            app.database.historyDao().insert(HistoryItem(title = title, url = url))
        }
    }

    /** 手动备份当前页面 */
    fun backupCurrent(callback: (Boolean, String) -> Unit) {
        val tab = currentTab()
        if (tab == null) {
            callback(false, "当前没有打开的页面")
            return
        }
        val url = tab.webView.url
        if (url.isNullOrBlank() || url.startsWith("about:")) {
            callback(false, "当前页面不支持备份")
            return
        }
        app.backups.backupSite(tab.webView, url, tab.webView.title ?: url, callback)
    }

    /** 从备份恢复当前页面 */
    fun restoreBackup(info: BackupInfo, callback: (Boolean, String) -> Unit) {
        val tab = currentTab()
        if (tab == null) {
            callback(false, "请先打开需要恢复的网页")
            return
        }
        app.backups.restore(tab.webView, info, callback)
    }

    /** 只清缓存，不影响 Cookie 与网页数据 */
    fun clearCacheOnly() {
        tabs.forEach { it.webView.clearCache(true) }
        toast("已清除网页缓存（不影响网页数据）")
    }

    /** 清除 Cookie 与网页存储：会影响包含本地网页在内的数据，调用前必须确认 */
    fun clearSiteData() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
        tabs.forEach {
            it.webView.clearFormData()
            it.webView.clearHistory()
            it.webView.reload()
        }
        toast("已清除 Cookie 与网页存储")
    }

    /** 应用进入后台时调用：自动备份当前站点数据 */
    fun autoBackupCurrent() {
        if (!app.settings.autoBackup) return
        val tab = currentTab() ?: return
        val url = tab.webView.url ?: return
        if (url.startsWith("about:") || url.startsWith("data:")) return
        app.backups.backupSite(tab.webView, url, tab.webView.title ?: url) { _, _ -> }
    }

    // ---------------- 打印 / 朗读 ----------------

    /** 调用系统打印，可选择「另存为 PDF」 */
    fun printPage() {
        val webView = currentTab()?.webView ?: return
        runCatching {
            val manager = activity.getSystemService(Context.PRINT_SERVICE) as PrintManager
            val jobName = (currentTitle.ifBlank { "网页" }) + ".pdf"
            val adapter = webView.createPrintDocumentAdapter(jobName)
            manager.print(
                jobName,
                adapter,
                PrintAttributes.Builder()
                    .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                    .build()
            )
        }.onFailure { toast("无法调用打印：${it.message}") }
    }

    /** 朗读当前网页正文，再次点击停止 */
    fun toggleReadAloud() {
        if (isSpeaking) {
            stopReadAloud()
            return
        }
        val webView = currentTab()?.webView ?: return
        webView.evaluateJavascript(
            "(function(){try{return document.body?document.body.innerText:'';}catch(e){return '';}})()"
        ) { raw ->
            val pageText = runCatching {
                JSONTokener(raw).nextValue() as? String
            }.getOrNull().orEmpty().trim()
            if (pageText.isBlank()) {
                toast("当前页面没有可朗读的文字")
                return@evaluateJavascript
            }
            ensureTts()
            val engine = tts ?: return@evaluateJavascript
            val chunks = splitForTts(pageText)
            if (chunks.isEmpty()) return@evaluateJavascript
            engine.stop()
            engine.setLanguage(Locale.getDefault())
            ttsLastUtterance = "qingguang-${chunks.size - 1}"
            chunks.forEachIndexed { index, chunk ->
                val params = Bundle().apply {
                    putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "qingguang-$index")
                }
                engine.speak(
                    chunk,
                    if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
                    params,
                    "qingguang-$index"
                )
            }
            isSpeaking = true
        }
    }

    fun stopReadAloud() {
        tts?.stop()
        isSpeaking = false
    }

    private fun ensureTts() {
        if (tts != null) return
        tts = TextToSpeech(activity) { status ->
            if (status != TextToSpeech.SUCCESS) {
                toast("系统语音引擎不可用")
                tts = null
                return@TextToSpeech
            }
            tts?.setLanguage(Locale.getDefault())
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit

                override fun onDone(utteranceId: String?) {
                    if (utteranceId == ttsLastUtterance) {
                        activity.runOnUiThread { isSpeaking = false }
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    activity.runOnUiThread { isSpeaking = false }
                }
            })
        }
    }

    /** 按句子切分，避免超过系统 TTS 单次朗读长度 */
    private fun splitForTts(text: String): List<String> {
        val max = 3000
        val result = mutableListOf<String>()
        var remaining = text.replace(Regex("\\n{2,}"), "\n").trim()
        while (remaining.isNotEmpty()) {
            if (remaining.length <= max) {
                result.add(remaining)
                break
            }
            var cut = remaining.lastIndexOfAny(charArrayOf('。', '！', '？', '.', '!', '?', '\n'), max)
            if (cut < max / 2) cut = max
            result.add(remaining.substring(0, cut + 1).trim())
            remaining = remaining.substring(cut + 1).trim()
        }
        return result.filter { it.isNotBlank() }
    }

    // ---------------- 工具 ----------------

    fun normalizeInput(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return ""
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://") ||
            trimmed.startsWith("file://") || trimmed.startsWith("about:")
        ) {
            return trimmed
        }
        val looksLikeHost = Regex("^[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)+(/.*)?$").matches(trimmed) ||
            trimmed.startsWith("localhost")
        return if (looksLikeHost) {
            "https://$trimmed"
        } else {
            app.settings.searchEngine.replace("%s", URLEncoder.encode(trimmed, "UTF-8"))
        }
    }

    fun refresh() {
        val tab = currentTab()
        if (tab != null) {
            currentUrl = tab.webView.url ?: ""
            currentTitle = tab.webView.title?.takeIf { it.isNotBlank() }
                ?: if (currentUrl.isBlank()) "新标签页" else currentUrl
            canGoBack = tab.webView.canGoBack()
            canGoForward = tab.webView.canGoForward()
        } else {
            currentUrl = ""
            currentTitle = "新标签页"
            canGoBack = false
            canGoForward = false
            progress = 0f
        }
        tabCount = tabs.size
        tabList = tabs.map {
            TabSummary(
                id = it.id,
                title = it.webView.title?.takeIf { t -> t.isNotBlank() } ?: "新标签页",
                url = it.webView.url ?: "",
                isActive = it === tab
            )
        }
    }

    fun destroy() {
        stopReadAloud()
        tts?.shutdown()
        tts = null
        container?.removeAllViews()
        tabs.forEach {
            it.webView.stopLoading()
            it.webView.destroy()
        }
        tabs.clear()
        container = null
        scope.cancel()
    }

    private fun toast(message: String) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        /** 优先取 meta theme-color，其次取 body/html 背景色，统一转成 #RRGGBB */
        private const val THEME_COLOR_JS =
            "(function(){function h(c){if(!c)return '';c=(''+c).trim();" +
                "if(c.charAt(0)==='#'){if(c.length===4)return '#'+c.charAt(1)+c.charAt(1)+c.charAt(2)+c.charAt(2)+c.charAt(3)+c.charAt(3);" +
                "return c.substring(0,7);}" +
                "if(c.indexOf('rgb')===0){var m=c.match(/\\d+/g);if(!m)return '';" +
                "return '#'+((1<<24)+(+m[0]<<16)+(+m[1]<<8)+(+m[2])).toString(16).slice(1);}return '';}" +
                "try{var m=document.querySelector('meta[name=\"theme-color\"]');var c=m&&m.content?h(m.content):'';" +
                "if(!c&&document.body){c=h(getComputedStyle(document.body).backgroundColor);}" +
                "if(!c&&document.documentElement){c=h(getComputedStyle(document.documentElement).backgroundColor);}" +
                "return c;}catch(e){return '';}})()"

        /** Android WebView 没有 File System Access API，注入基于 input[type=file] 的兼容实现 */
        private const val PICKER_POLYFILL_JS =
            "(function(){if(window.showOpenFilePicker)return;" +
                "window.showOpenFilePicker=function(options){return new Promise(function(resolve){" +
                "var input=document.createElement('input');input.type='file';" +
                "input.multiple=!!(options&&options.multiple);input.style.display='none';" +
                "if(options&&options.types){var accepts=[];" +
                "try{options.types.forEach(function(t){if(t&&t.accept){Object.keys(t.accept).forEach(function(k){accepts.push(k);});}});}catch(e){}" +
                "if(accepts.length)input.accept=accepts.join(',');}" +
                "document.body.appendChild(input);" +
                "input.addEventListener('change',function(){" +
                "var handles=Array.prototype.map.call(input.files||[],function(f){return {kind:'file',name:f.name,getFile:function(){return Promise.resolve(f);}};});" +
                "input.remove();resolve(handles);});input.click();});};})()"
    }
}
