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
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Base64
import android.view.View
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
import com.qingguang.browser.data.AuditLog
import com.qingguang.browser.data.DownloadRecord
import com.qingguang.browser.data.HistoryItem
import com.qingguang.browser.data.OfflinePage
import com.qingguang.browser.util.BackupInfo
import com.qingguang.browser.util.DownloadNotifier
import com.qingguang.browser.util.SettingsStore
import com.qingguang.browser.util.SitePermission
import com.qingguang.browser.util.SitePermissionStore
import com.qingguang.browser.util.hostOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONTokener
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class BrowserTab(val id: String, val webView: WebView) {
    var locked by mutableStateOf(false)
}

/** 长按命中结果，用于网页元素菜单 */
data class LongPressTarget(
    val pageUrl: String = "",
    val linkUrl: String = "",
    val imageUrl: String = "",
    val text: String = ""
) {
    val hasLink: Boolean get() = linkUrl.isNotBlank()
    val hasImage: Boolean get() = imageUrl.isNotBlank()
}

/** 支持长按命中检测的 WebView；普通文本长按仍走系统选择 */
private class HitTestWebView(context: Context) : WebView(context) {
    var onHitTestLongPress: ((WebView.HitTestResult) -> Boolean)? = null

    override fun performLongClick(): Boolean {
        val result = hitTestResult
        if (onHitTestLongPress?.invoke(result) == true) return true
        return super.performLongClick()
    }
}

data class TabSummary(
    val id: String,
    val title: String,
    val url: String,
    val isActive: Boolean,
    val locked: Boolean = false
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
    private val closedTabs = ArrayDeque<Pair<String, String>>()
    private var activeIndex = -1
    private var container: FrameLayout? = null
    var canRestoreTab by mutableStateOf(false)
        private set

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

    /** 网页长按元素菜单 */
    var longPressTarget by mutableStateOf<LongPressTarget?>(null)

    /** 是否处于阅读模式（页面已被重排，退出时刷新恢复） */
    var readerMode by mutableStateOf(false)
        private set

    var findQuery by mutableStateOf("")
    var findMatches by mutableStateOf(0)
    var findOrdinal by mutableStateOf(0)
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
    private var ttsReady = false
    private var pendingReadText: String? = null
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
        val tab = tabs[index]
        if (tab.locked) {
            toast("标签已锁定，解锁后再关闭")
            return
        }
        val url = tab.webView.url
        val title = tab.webView.title
        if (!url.isNullOrBlank()) {
            closedTabs.addLast(url to (title ?: url))
            while (closedTabs.size > 20) closedTabs.removeFirst()
        }
        canRestoreTab = closedTabs.isNotEmpty()
        tabs.removeAt(index)
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

    /** 恢复最近关闭的标签页 */
    fun restoreClosedTab() {
        val entry = closedTabs.removeLastOrNull()
        if (entry == null) {
            toast("没有可恢复的标签页")
            return
        }
        canRestoreTab = closedTabs.isNotEmpty()
        val tab = newTabInternal()
        showHome = false
        tab.webView.loadUrl(entry.first)
        refresh()
    }

    fun toggleTabLock(id: String) {
        val tab = tabs.firstOrNull { it.id == id } ?: return
        tab.locked = !tab.locked
        refresh()
        toast(if (tab.locked) "已锁定标签页" else "已解锁标签页")
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
        // 从网页返回首页：关闭当前网页标签，避免标签越积越多
        val tab = currentTab()
        val url = tab?.webView?.url
        val hasPage = !url.isNullOrBlank() && !url.startsWith("about:")
        if (tab != null && hasPage && !tab.locked) {
            closeTab(tab.id)
        }
        if (tabs.isEmpty()) newTabInternal()
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
            // 首页覆盖在容器之上，WebView 必须一起隐藏，否则首页背景会透出网页残影
            tab.webView.visibility = if (showHome) View.INVISIBLE else View.VISIBLE
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
        applySitePolicy(tab.webView, url)
        tab.webView.loadUrl(url)
        refresh()
    }

    /** 本次会话临时授权的站点（关闭应用即失效），优先级高于持久配置 */
    private val sessionGrants = mutableSetOf<String>()

    fun sessionGrantedHosts(): Set<String> = sessionGrants.toSet()

    /** 仅本次会话允许该站点全部权限，并立即重新加载 */
    fun grantSessionPermissions(host: String) {
        val key = host.trim().lowercase()
        if (key.isBlank()) return
        sessionGrants.add(key)
        val tab = currentTab() ?: return
        val url = tab.webView.url ?: return
        applySitePolicy(tab.webView, url)
        tab.webView.reload()
        audit("权限", "临时放行", key, "本次会话全部允许")
    }

    fun revokeSessionPermissions(host: String) {
        sessionGrants.remove(host.trim().lowercase())
        val tab = currentTab() ?: return
        tab.webView.reload()
    }

    private fun policyForHost(host: String): SitePermission {
        val key = host.trim().lowercase()
        return if (key in sessionGrants) SitePermission(host = key)
        else SitePermissionStore.policyFor(app.settings, key)
    }

    /** 按站点权限矩阵设置脚本/图片开关（未配置的站点保持默认允许） */
    private fun applySitePolicy(webView: WebView, url: String) {
        if (!url.startsWith("http")) return
        val host = hostOf(url)
        if (host.isBlank()) return
        val policy = policyForHost(host)
        webView.settings.javaScriptEnabled = policy.allowJs
        webView.settings.blockNetworkImage = !policy.allowImages
        if (!policy.allowJs) audit("脚本", "禁用", host, url)
        if (!policy.allowImages) audit("图片", "拦截", host, url)
    }

    /** 写入一条操作审计日志（无痕模式不记录） */
    internal fun audit(action: String, result: String, host: String = "", detail: String = "") {
        if (app.settings.incognito) return
        scope.launch {
            val dao = app.database.auditLogDao()
            dao.insert(AuditLog(action = action, host = host, result = result, detail = detail))
            dao.prune()
        }
    }

    /** 新标签页打开（长按菜单、链接菜单共用） */
    fun openInNewTab(url: String) {
        if (url.isBlank()) return
        longPressTarget = null
        newTabInternal()
        showHome = false
        currentTab()?.webView?.loadUrl(url)
        refresh()
    }

    fun dismissLongPress() {
        longPressTarget = null
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
        val webView = HitTestWebView(activity)
        webView.onHitTestLongPress = { result ->
            val pageUrl = result.extra ?: ""
            when (result.type) {
                WebView.HitTestResult.IMAGE_TYPE,
                WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                    longPressTarget = LongPressTarget(imageUrl = pageUrl)
                    true
                }
                WebView.HitTestResult.SRC_ANCHOR_TYPE -> {
                    longPressTarget = LongPressTarget(linkUrl = pageUrl)
                    true
                }
                WebView.HitTestResult.IMAGE_ANCHOR_TYPE -> {
                    longPressTarget = LongPressTarget(linkUrl = pageUrl)
                    true
                }
                else -> false
            }
        }
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
        webView.setFindListener { ordinal, matches, _ ->
            if (webView === currentTab()?.webView) {
                findMatches = matches
                findOrdinal = if (matches > 0) ordinal else 0
            }
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                if (app.settings.adBlock && isAdHost(request.url.host ?: "")) {
                    return WebResourceResponse(
                        "text/plain",
                        "utf-8",
                        java.io.ByteArrayInputStream(ByteArray(0))
                    )
                }
                val response = assetLoader.shouldInterceptRequest(request.url)
                if (response != null && response.data != null) {
                    // 本地网页用 <script type="module"> 时，WebView 严格校验 MIME，
                    // 若被当成 text/plain 会拒绝执行，导致展开列表等交互失效
                    val mime = response.mimeType
                    if (mime == null || mime == "text/plain" || mime == "application/octet-stream") {
                        val fixed = mimeForPath(request.url.path.orEmpty())
                        if (fixed != null) {
                            return WebResourceResponse(fixed, response.encoding ?: "utf-8", response.data)
                        }
                    }
                }
                return response
            }

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
                readerMode = false
                longPressTarget = null
                // 提前接管 URL.createObjectURL，缓存网页生成的 Blob，避免导出下载时 fetch 被 CSP 拦截
                view.evaluateJavascript(BLOB_STORE_JS, null)
                // 夜间调色尽早注入，减少首屏白闪与延迟感
                applyDarkMode(view)
                injectNightCss(view)
                refresh()
            }

            override fun onPageCommitVisible(view: WebView, url: String?) {
                // 页面内容即将可见时再注入一次：比 onPageFinished 更早，明显减少暗色延迟
                if (view === currentTab()?.webView) injectNightCss(view)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                if (view === currentTab()?.webView) {
                    progress = 1f
                    recordHistory(view)
                    refresh()
                    updatePageTheme(view)
                    // Android WebView 不实现 File System Access API，注入兼容实现，网页可多选文件
                    view.evaluateJavascript(PICKER_POLYFILL_JS, null)
                    // 手势与广告元素隐藏
                    gestureScript()?.let { view.evaluateJavascript(it, null) }
                    if (app.settings.adBlock) {
                        view.evaluateJavascript(AD_HIDE_JS, null)
                    }
                    if (nightInvertEnabled()) {
                        injectNightCss(view)
                    }
                    // 页面加载完成后自动备份一次，降低"交互后直接退出"导致的数据丢失风险
                    if (app.settings.autoBackup && !app.settings.incognito &&
                        url != null && !url.startsWith("about:")
                    ) {
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

            override fun onCreateWindow(
                view: WebView,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message
            ): Boolean {
                val host = hostOf(view.url ?: "")
                val policy = policyForHost(host)
                if (app.settings.adBlock || !policy.allowPopup) {
                    toast("已拦截网页弹窗")
                    audit("弹窗", "拦截", host, view.url ?: "")
                    return false
                }
                return super.onCreateWindow(view, isDialog, isUserGesture, resultMsg)
            }

            override fun onPermissionRequest(request: android.webkit.PermissionRequest) {
                val host = hostOf(request.origin.toString())
                val policy = policyForHost(host)
                if (policy.allowCamera) {
                    request.grant(request.resources)
                    audit("摄像头/麦克风", "允许", host, request.origin.toString())
                } else {
                    request.deny()
                    audit("摄像头/麦克风", "拒绝", host, request.origin.toString())
                }
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String,
                callback: GeolocationPermissions.Callback
            ) {
                val host = hostOf(origin)
                if (!policyForHost(host).allowGeo) {
                    callback.invoke(origin, false, true)
                    audit("定位", "拒绝", host, origin)
                    return
                }
                val requester = locationRequester
                if (requester == null) {
                    callback.invoke(origin, false, true)
                    return
                }
                requester { granted ->
                    callback.invoke(origin, granted, true)
                    audit("定位", if (granted) "允许" else "拒绝", host, origin)
                }
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
        webView.addJavascriptInterface(GestureBridge(), "QingGuangGestures")
        webView.addJavascriptInterface(BackupBridge(), "QingGuangBackup")

        return webView
    }

    private fun applyDarkMode(webView: WebView) {
        val night = nightEnabledFor(webView.url)
        webView.setBackgroundColor(if (night) 0xFF101214.toInt() else 0x00000000)
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) return
        // 与应用主题合并：浅色=不暗化，深色/跟随系统=暗化；夜间同时注入调色 CSS
        WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.settings, night)
    }

    /** 统一主题：夜间 = 应用深色 + 网页注入深色调色（跟随系统时按系统深色判断） */
    fun nightInvertEnabled(): Boolean = when (app.settings.appTheme) {
        1 -> false
        else -> isSystemDark()
    }

    /** 单个站点可覆盖全局夜间调色：1 强制开、0 强制关、-1 跟随全局 */
    private fun nightEnabledFor(url: String?): Boolean {
        val global = nightInvertEnabled()
        val host = hostOf(url ?: "")
        if (host.isBlank()) return global
        val policy = SitePermissionStore.find(app.settings, host) ?: return global
        return when (policy.nightMode) {
            1 -> true
            0 -> false
            else -> global
        }
    }

    /** 夜间调色：先铺深色底，再尽早注入样式，减少白闪与延迟 */
    private fun injectNightCss(view: WebView) {
        if (nightEnabledFor(view.url)) {
            view.setBackgroundColor(0xFF101214.toInt())
            view.evaluateJavascript(NIGHT_CSS_JS, null)
        } else {
            view.setBackgroundColor(0x00000000)
        }
    }

    /** 主题模式切换后重新应用并刷新网页 */
    fun onThemeModeChanged() {
        tabs.forEach { applyDarkMode(it.webView) }
        reload()
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
            tab.webView.url?.let { applySitePolicy(tab.webView, it) }
            applyDarkMode(tab.webView)
        }
        currentTab()?.webView?.reload()
    }

    // ---------------- 下载 ----------------

    /** 阅读模式：提取正文并重排页面；再次调用则刷新退出 */
    fun toggleReaderMode() {
        val webView = currentTab()?.webView ?: return
        if (readerMode) {
            readerMode = false
            webView.reload()
            toast("已退出阅读模式")
            return
        }
        val fontSize = app.settings.readerFontSize.coerceIn(14, 30)
        val lineHeight = app.settings.readerLinePercent.coerceIn(120, 260) / 100f
        val readerJs = READER_JS.replace(
            "font-size:19px;line-height:1.9;",
            "font-size:${fontSize}px;line-height:${lineHeight};"
        )
        webView.evaluateJavascript(readerJs) { result ->
            if (result == "true") {
                readerMode = true
                toast("已进入阅读模式，可在菜单中退出")
            } else {
                toast("这个页面没有可提取的正文")
            }
        }
    }

    /** 保存当前网页为离线包：HTML + 图片/CSS/JS 一并下载，断网也能打开 */
    fun saveCurrentPageOffline() {
        val title = currentTitle.ifBlank { "网页" }
        val url = currentUrl
        if (!url.startsWith("http")) {
            toast("只能保存在线网页")
            return
        }
        toast("正在保存网页与资源…")
        fetchPageSource { html ->
            if (html.isBlank()) {
                toast("无法获取网页内容")
                return@fetchPageSource
            }
            scope.launch {
                val saved = withContext(Dispatchers.IO) { buildOfflinePack(url, html) }
                if (saved == null) {
                    toast("离线保存失败")
                } else {
                    val page = OfflinePage(
                        title = title,
                        url = url,
                        dirName = saved,
                        totalBytes = offlinePackSize(saved)
                    )
                    app.database.offlinePageDao().insert(page)
                    toast("已离线保存：${offlinePackSize(saved) / 1024} KB，可在菜单「离线网页」打开")
                }
            }
        }
    }

    /** 下载 HTML 引用的图片/CSS/JS 到本地并改写路径，返回目录名 */
    private fun buildOfflinePack(url: String, html: String): String? = runCatching {
        val root = File(activity.filesDir, "offline")
        val dirName = "p_" + System.currentTimeMillis()
        val dir = File(root, dirName)
        val assets = File(dir, "_assets")
        if (!assets.mkdirs()) error("无法创建离线目录")
        var rewritten = html
        val refRegex = Regex("""(?:src|href|poster)=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        val refs = refRegex.findAll(html).map { it.groupValues[1] }.distinct().take(120).toList()
        val base = URL(url)
        var index = 0
        var total = html.toByteArray(Charsets.UTF_8).size.toLong()
        for (ref in refs) {
            val lower = ref.lowercase()
            if (lower.startsWith("data:") || lower.startsWith("#") || lower.startsWith("javascript:") ||
                lower.startsWith("mailto:") || lower.startsWith("blob:")
            ) continue
            val ext = ref.substringBefore('?').substringBefore('#').substringAfterLast('.', "").lowercase()
            if (ext !in OFFLINE_ASSET_EXTS) continue
            val abs = runCatching { URL(base, ref).toString() }.getOrNull() ?: continue
            if (!abs.startsWith("http")) continue
            val bytes = fetchAssetBytes(abs) ?: continue
            if (bytes.size > 8 * 1024 * 1024) continue
            val name = "a${index++}.$ext"
            File(assets, name).writeBytes(bytes)
            total += bytes.size
            rewritten = rewritten
                .replace("\"$ref\"", "\"_assets/$name\"")
                .replace("'$ref'", "'_assets/$name'")
            if (total > 80L * 1024 * 1024) break
        }
        File(dir, "index.html").writeText(rewritten, Charsets.UTF_8)
        dirName
    }.getOrNull()

    private fun offlinePackSize(dirName: String): Long {
        val dir = File(File(activity.filesDir, "offline"), dirName)
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    private fun fetchAssetBytes(url: String): ByteArray? = runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 12000
        conn.readTimeout = 25000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", System.getProperty("http.agent") ?: "Mozilla/5.0")
        CookieManager.getInstance().getCookie(url)?.takeIf { it.isNotBlank() }?.let {
            conn.setRequestProperty("Cookie", it)
        }
        conn.inputStream.use { it.readBytes() }
    }.getOrNull()

    private fun mimeForPath(path: String): String? =
        when (path.substringAfterLast('.', "").lowercase()) {
            "js", "mjs" -> "text/javascript"
            "css" -> "text/css"
            "json" -> "application/json"
            "html", "htm" -> "text/html"
            "svg" -> "image/svg+xml"
            "wasm" -> "application/wasm"
            else -> null
        }

    fun openOfflinePage(page: OfflinePage) {
        loadUrl("https://appassets.androidplatform.net/files/offline/${page.dirName}/index.html")
    }

    fun deleteOfflinePage(page: OfflinePage) {
        scope.launch {
            withContext(Dispatchers.IO) {
                File(File(activity.filesDir, "offline"), page.dirName).deleteRecursively()
                app.database.offlinePageDao().delete(page.id)
            }
            toast("已删除离线网页")
        }
    }

    /** 批量保存当前网页里的图片（最多 60 张） */
    fun downloadAllImages() {
        val webView = currentTab()?.webView
        if (webView == null) {
            toast("当前没有打开的页面")
            return
        }
        val js = "(function(){var seen={};var out=[];var imgs=document.images||[];" +
            "for(var i=0;i<imgs.length;i++){var s=imgs[i].currentSrc||imgs[i].src;" +
            "if(!s||s.indexOf('data:')===0)continue;if(seen[s])continue;seen[s]=1;" +
            "out.push(s);if(out.length>=60)break;}return JSON.stringify(out);})()"
        webView.evaluateJavascript(js) { raw ->
            val text = runCatching { JSONTokener(raw).nextValue() as? String }.getOrNull()
            val array = runCatching { org.json.JSONArray(text ?: "[]") }.getOrNull()
            val urls = mutableListOf<String>()
            if (array != null) {
                for (i in 0 until array.length()) {
                    val item = array.optString(i)
                    if (item.isNotBlank()) urls.add(item)
                }
            }
            if (urls.isEmpty()) {
                toast("本页没有可下载的图片")
                return@evaluateJavascript
            }
            activity.runOnUiThread {
                urls.forEachIndexed { index, url -> saveImageFromUrl(url, imageName(url, index)) }
                toast("已开始保存 ${urls.size} 张图片")
            }
        }
    }

    private fun imageName(url: String, index: Int): String {
        val raw = url.substringBefore('?').substringAfterLast('/').substringBefore('#')
        val decoded = runCatching {
            java.net.URLDecoder.decode(raw, "UTF-8")
        }.getOrDefault(raw)
        return if (decoded.contains('.')) "${index + 1}-$decoded" else "图片-${index + 1}.jpg"
    }

    private fun gestureScript(): String? {
        if (!app.settings.pullRefresh && !app.settings.swipeNavigation) return null
        val pull = app.settings.pullRefresh
        val swipe = app.settings.swipeNavigation
        return "(function(){if(window.__qgGesture)return;window.__qgGesture=true;var sx=0,sy=0;" +
            "document.addEventListener('touchstart',function(e){" +
            "if(e.touches.length!==1)return;sx=e.touches[0].clientX;sy=e.touches[0].clientY;},{passive:true});" +
            "document.addEventListener('touchend',function(e){" +
            "if(e.changedTouches.length!==1)return;" +
            "var x=e.changedTouches[0].clientX,y=e.changedTouches[0].clientY,dx=x-sx,dy=y-sy;" +
            (if (swipe) "if(Math.abs(dx)>100&&Math.abs(dy)<60){if(sx<40&&dx>0)QingGuangGestures.navigate(-1);" +
                "else if(sx>window.innerWidth-40&&dx<0)QingGuangGestures.navigate(1);}" else "") +
            (if (pull) "if(window.scrollY<=2&&dy>130&&Math.abs(dx)<80)QingGuangGestures.reload();" else "") +
            "},{passive:true});})()"
    }

    private fun isAdHost(host: String): Boolean {
        if (host.isBlank()) return false
        val h = host.lowercase()
        if (AD_HOSTS.any { h == it || h.endsWith(".$it") }) return true
        val extra = app.settings.adBlockExtraHosts
            .split('\n', ',', '，')
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
        if (extra.any { h == it || h.endsWith(".$it") }) return true
        return h.startsWith("ads.") || h.startsWith("ad.") || h.contains("adserver")
    }

    // ---------------- 下载 ----------------

    /** 收到下载链接：先弹确认框，由用户确认（可改名）后再下载 */
    private fun requestDownload(
        url: String,
        userAgent: String,
        contentDisposition: String,
        mimetype: String
    ) {
        val host = if (url.startsWith("http")) hostOf(url) else currentUrl
        val policy = policyForHost(host)
        if (!policy.allowDownload) {
            toast("该站点下载权限已被关闭，可在「权限中心」开启")
            audit("下载", "拒绝", host, url)
            return
        }
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
        audit("下载", "开始", hostOf(pending.url.ifBlank { currentUrl }), finalName)
        if (pending.isBlobOrData) {
            downloadBlobOrData(pending.url, pending.contentDisposition, pending.mimeType, finalName)
        } else {
            enqueueDownload(pending, finalName)
        }
    }

    private fun enqueueDownload(pending: PendingDownload, fileName: String) {
        runCatching {
            val subDir = app.settings.downloadSubDir
            val relativePath = if (subDir.isBlank()) fileName else "$subDir/$fileName"
            val request = DownloadManager.Request(Uri.parse(pending.url))
                .setMimeType(pending.mimeType)
                .setTitle(fileName)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, relativePath)
            if (pending.userAgent.isNotBlank()) {
                request.addRequestHeader("User-Agent", pending.userAgent)
            }
            val id = downloadManager.enqueue(request)
            scope.launch {
                app.database.downloadDao().insert(
                    DownloadRecord(
                        fileName = fileName,
                        url = pending.url,
                        localPath = "${Environment.DIRECTORY_DOWNLOADS}/$relativePath",
                        status = "等待下载",
                        managerId = id,
                        totalBytes = pending.sizeBytes ?: -1L,
                        mimeType = pending.mimeType
                    )
                )
            }
            toast("已加入下载：$fileName")
        }.onFailure { toast("下载失败：${it.message}") }
    }

    /** 下载中心「重试」：按原地址重新加入下载任务 */
    fun retryDownload(url: String, fileName: String, mimeType: String) {
        if (url.isBlank() || url.startsWith("data:") || url.startsWith("blob:")) {
            toast("该下载无法自动重试，请回到网页重新下载")
            return
        }
        enqueueDownload(
            PendingDownload(
                url = url,
                userAgent = defaultUserAgent,
                contentDisposition = "",
                mimeType = mimeType,
                suggestedName = fileName,
                sizeChecked = true
            ),
            fileName
        )
    }

    /** 长按图片：保存图片（使用网页 Cookie 抓取，避免防盗链失败） */
    fun saveImageFromUrl(url: String, suggestedName: String) {
        if (url.isBlank()) {
            toast("图片地址无效")
            return
        }
        val name = sanitizeDownloadName(suggestedName).ifBlank { fallbackFileName("", "image/*") }
        val mime = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
            ?: "image/*"
        downloadBlobOrData(url, "", mime, name)
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
              var cached = window.__qgBlobStore ? window.__qgBlobStore['$safeUrl'] : null;
              function deliver(b) {
                var fr = new FileReader();
                fr.onload = function(){
                  var s = fr.result;
                  var i = s.indexOf(',');
                  QingGuangDownload.saveBase64('$safeName', b.type || '$safeMime', s.substring(i + 1));
                };
                fr.onerror = function(){ QingGuangDownload.fail('读取数据失败'); };
                fr.readAsDataURL(b);
              }
              if (cached) { deliver(cached); return; }
              fetch('$safeUrl', { credentials: 'include' }).then(function(r){return r.blob();})
                .then(deliver)
                .catch(function(e){
                  var msg = (e && e.message) ? e.message : 'Failed to fetch';
                  if ('$safeUrl'.indexOf('blob:') === 0) {
                    msg = '网页限制了脚本读取导出文件（' + msg + '），请尝试网页内的其他导出方式';
                  }
                  QingGuangDownload.fail(msg);
                });
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
            val subDir = app.settings.downloadSubDir
            val relativePath = if (subDir.isBlank()) {
                Environment.DIRECTORY_DOWNLOADS
            } else {
                "${Environment.DIRECTORY_DOWNLOADS}/$subDir"
            }
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mime.ifBlank { "application/octet-stream" })
                put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
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
                        localPath = "$relativePath/$fileName",
                        status = "下载完成",
                        totalBytes = bytes.size.toLong(),
                        mimeType = mime,
                        localUri = uri.toString()
                    )
                )
            }
            DownloadNotifier.notifyComplete(app, fileName)
            toast("下载完成：$fileName")
        }.onFailure { toast("下载失败：${it.message}") }
    }

    /** 供网页 JS 调用的手势桥接 */
    inner class GestureBridge {
        @JavascriptInterface
        fun navigate(delta: Int) {
            activity.runOnUiThread {
                if (delta < 0) goBack() else goForward()
            }
        }

        @JavascriptInterface
        fun reload() {
            activity.runOnUiThread {
                currentTab()?.webView?.reload()
            }
        }
    }

    /** 供网页 JS 调用的备份桥接：回报 IndexedDB 导出/恢复结果 */
    inner class BackupBridge {
        @JavascriptInterface
        fun onIndexedDb(json: String) {
            app.backups.deliverIndexedDb(json)
        }

        @JavascriptInterface
        fun onIndexedDbRestored() {
            app.backups.deliverIndexedDbRestored()
        }
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
        fun saveText(name: String?, mime: String?, text: String?) {
            if (text.isNullOrBlank()) return
            val fileName = name?.takeIf { it.isNotBlank() } ?: "站点数据.json"
            val bytes = text.toByteArray(Charsets.UTF_8)
            activity.runOnUiThread {
                saveDownloadedBytes(fileName, mime ?: "application/json", bytes, "site-data")
            }
        }

        @JavascriptInterface
        fun fail(message: String?) {
            activity.runOnUiThread { toast("下载失败：${message ?: "读取数据失败"}") }
        }
    }

    // ---------------- 历史与备份 ----------------

    private fun recordHistory(webView: WebView) {
        if (app.settings.incognito) return
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

    /** 从 JSON 文件恢复当前站点的 Cookie、localStorage 与 IndexedDB */
    fun restoreSiteData(json: String, callback: (Boolean, String) -> Unit) {
        val tab = currentTab()
        if (tab == null) {
            callback(false, "当前没有打开的页面")
            return
        }
        val url = tab.webView.url
        if (url.isNullOrBlank() || url.startsWith("about:")) {
            callback(false, "请先打开要恢复数据的网页")
            return
        }
        audit("站点数据", "恢复", hostOf(url), "从 JSON 文件恢复")
        app.backups.restoreFromText(tab.webView, json, callback)
    }

    /** 导出当前站点的 localStorage 与 IndexedDB 为 JSON，保存到下载目录 */
    fun exportSiteData() {
        val tab = currentTab() ?: run {
            toast("当前没有打开的页面")
            return
        }
        val url = tab.webView.url
        if (url.isNullOrBlank() || url.startsWith("about:") || !url.startsWith("http")) {
            toast("当前页面不支持导出站点数据")
            return
        }
        toast("正在导出站点数据…")
        audit("站点数据", "导出", hostOf(url), url)
        tab.webView.evaluateJavascript(SITE_DATA_EXPORT_JS, null)
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

    /** 退出无痕模式时清理浏览痕迹（Cookie、网页存储、表单、缓存），不动书签/下载等本地数据 */
    fun clearIncognitoData() {
        runCatching {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            WebStorage.getInstance().deleteAllData()
            tabs.forEach {
                it.webView.clearCache(true)
                it.webView.clearFormData()
                it.webView.clearHistory()
            }
        }
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
            if (ttsReady) {
                speakChunks(splitForTts(pageText))
            } else {
                // 语音引擎初始化是异步的，先缓存文本，等 onInit 成功后再朗读
                pendingReadText = pageText
                ensureTts()
            }
        }
    }

    fun stopReadAloud() {
        pendingReadText = null
        tts?.stop()
        isSpeaking = false
    }

    // ---------------- 页内查找 ----------------

    fun startFind(text: String) {
        findQuery = text
        val view = currentTab()?.webView ?: return
        if (text.isBlank()) {
            view.clearMatches()
            findMatches = 0
            findOrdinal = 0
            return
        }
        view.findAllAsync(text)
    }

    fun findNext(forward: Boolean = true) {
        currentTab()?.webView?.findNext(forward)
    }

    fun stopFind() {
        currentTab()?.webView?.clearMatches()
        findQuery = ""
        findMatches = 0
        findOrdinal = 0
    }

    private fun speakChunks(chunks: List<String>) {
        if (chunks.isEmpty()) return
        val engine = tts ?: return
        engine.stop()
        if (!applyTtsLanguage(engine)) {
            isSpeaking = false
            toast("语音引擎缺少中文语音数据，请在系统设置中下载语音包")
            return
        }
        ttsLastUtterance = "qingguang-${chunks.size - 1}"
        val first = chunks.firstOrNull() ?: return
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "qingguang-0")
        }
        val started = engine.speak(
            first,
            TextToSpeech.QUEUE_FLUSH,
            params,
            "qingguang-0"
        ) == TextToSpeech.SUCCESS
        if (!started) {
            isSpeaking = false
            toast("朗读启动失败，请检查系统语音引擎")
            return
        }
        chunks.drop(1).forEachIndexed { offset, chunk ->
            val index = offset + 1
            val queueParams = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "qingguang-$index")
            }
            engine.speak(chunk, TextToSpeech.QUEUE_ADD, queueParams, "qingguang-$index")
        }
    }

    private var ttsInitAttempt = 0

    private fun ensureTts() {
        if (tts != null) {
            if (ttsReady) {
                val pending = pendingReadText
                pendingReadText = null
                if (!pending.isNullOrBlank()) speakChunks(splitForTts(pending))
            }
            return
        }
        ttsInitAttempt = 0
        createTts()
    }

    private fun createTts() {
        val listener = TextToSpeech.OnInitListener { status ->
            if (status != TextToSpeech.SUCCESS) {
                tts?.shutdown()
                tts = null
                ttsReady = false
                if (ttsInitAttempt < 1) {
                    // 部分机型的语音引擎首次连接会瞬时失败，稍后重试一次
                    ttsInitAttempt++
                    android.os.Handler(android.os.Looper.getMainLooper())
                        .postDelayed({ createTts() }, 900)
                } else {
                    pendingReadText = null
                    isSpeaking = false
                    toast(
                        "语音引擎初始化失败。红米/MIUI 请在 设置 → 无障碍 → 文字转语音 中选择「小米语音引擎」，" +
                            "或安装「Google 文字转语音」后重试"
                    )
                }
                return@OnInitListener
            }
            val engine = tts ?: return@OnInitListener
            if (!applyTtsLanguage(engine)) {
                ttsReady = false
                engine.shutdown()
                tts = null
                pendingReadText = null
                isSpeaking = false
                toast("语音引擎缺少中文语音数据，请在 设置 → 无障碍 → 文字转语音 中下载中文语音包")
                return@OnInitListener
            }
            runCatching {
                engine.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
            }
            ttsReady = true
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    activity.runOnUiThread { isSpeaking = true }
                }

                override fun onDone(utteranceId: String?) {
                    if (utteranceId == ttsLastUtterance) {
                        activity.runOnUiThread { isSpeaking = false }
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    activity.runOnUiThread {
                        isSpeaking = false
                        toast("朗读中断，请检查系统语音引擎")
                    }
                }
            })
            val pending = pendingReadText
            pendingReadText = null
            if (!pending.isNullOrBlank()) {
                activity.runOnUiThread { speakChunks(splitForTts(pending)) }
            }
        }
        tts = TextToSpeech(activity, listener)
    }

    /** 依次尝试系统语言、简中、中文，避免部分引擎对 Locale 要求不同而误判不支持 */
    private fun applyTtsLanguage(engine: TextToSpeech): Boolean {
        val candidates = listOf(
            Locale.getDefault(),
            Locale.SIMPLIFIED_CHINESE,
            Locale.CHINA,
            Locale.CHINESE,
            Locale.US
        ).distinct()
        return candidates.any { locale ->
            val result = engine.setLanguage(locale)
            result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
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
                isActive = it === tab,
                locked = it.locked
            )
        }
    }

    fun destroy() {
        stopReadAloud()
        tts?.shutdown()
        tts = null
        ttsReady = false
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

        /** 缓存网页通过 URL.createObjectURL 生成的 Blob；部分网站 CORS/CSP 会阻止再次 fetch(blob:) */
        private const val BLOB_STORE_JS =
            "(function(){if(window.__qgBlobHooked)return;window.__qgBlobHooked=true;" +
                "window.__qgBlobStore=window.__qgBlobStore||{};" +
                "try{var oc=URL.createObjectURL.bind(URL);" +
                "URL.createObjectURL=function(o){var u=oc(o);try{window.__qgBlobStore[u]=o;}catch(e){}return u;};" +
                "var orv=URL.revokeObjectURL.bind(URL);" +
                "URL.revokeObjectURL=function(u){setTimeout(function(){" +
                "try{orv(u);}catch(e){}try{delete window.__qgBlobStore[u];}catch(e){};},120000);};" +
                "}catch(e){}})()"

        /** 常见广告域名；开启拦截后直接返回空响应 */
        private val AD_HOSTS = listOf(
            "doubleclick.net",
            "googlesyndication.com",
            "googleadservices.com",
            "adservice.google.com",
            "amazon-adsystem.com",
            "cpro.baidu.com",
            "pos.baidu.com",
            "tanx.com",
            "mmstat.com",
            "umeng.com",
            "cnzz.com",
            "adcolony.com",
            "applovin.com",
            "unityads.unity3d.com"
        )

        /** 离线保存时一并下载的资源类型 */
        private val OFFLINE_ASSET_EXTS = setOf(
            "png", "jpg", "jpeg", "gif", "webp", "svg", "ico", "bmp", "avif",
            "css", "js", "woff", "woff2", "ttf", "otf"
        )

        /** 导出站点数据：localStorage + 全部 IndexedDB（含库结构、索引与记录） */
        private val SITE_DATA_EXPORT_JS = """
            (function(){
              var result={url:location.href,host:location.hostname,time:Date.now(),localStorage:{},indexedDB:[]};
              try{
                for(var i=0;i<localStorage.length;i++){var k=localStorage.key(i);result.localStorage[k]=localStorage.getItem(k);}
              }catch(e){}
              function readStore(db,storeName){
                return new Promise(function(resolve){
                  var info={name:storeName,keyPath:null,autoIncrement:false,indexes:[],records:[]};
                  try{
                    var tx=db.transaction(storeName,'readonly');
                    var store=tx.objectStore(storeName);
                    info.keyPath=store.keyPath||null;
                    info.autoIncrement=!!store.autoIncrement;
                    for(var i=0;i<store.indexNames.length;i++){
                      var ix=store.index(store.indexNames[i]);
                      info.indexes.push({name:ix.name,keyPath:ix.keyPath||null,unique:!!ix.unique,multiEntry:!!ix.multiEntry});
                    }
                    var req=store.getAll();
                    req.onsuccess=function(){info.records=req.result||[];resolve(info);};
                    req.onerror=function(){resolve(info);};
                  }catch(e){resolve(null);}
                });
              }
              function readDB(item){
                return new Promise(function(resolve){
                  var req;
                  try{req=indexedDB.open(item.name);}catch(e){resolve(null);return;}
                  req.onerror=function(){resolve(null);};
                  req.onsuccess=function(){
                    var db=req.result;
                    var stores=Array.prototype.slice.call(db.objectStoreNames);
                    var out={name:db.name,version:db.version,stores:[]};
                    if(!stores.length){db.close();resolve(out);return;}
                    Promise.all(stores.map(function(s){return readStore(db,s);})).then(function(list){
                      out.stores=list.filter(Boolean);
                      db.close();
                      resolve(out);
                    }).catch(function(){db.close();resolve(out);});
                  };
                });
              }
              var dbs=(indexedDB.databases?indexedDB.databases():Promise.resolve([]));
              dbs.then(function(list){
                return Promise.all((list||[]).map(readDB));
              }).then(function(all){
                result.indexedDB=(all||[]).filter(Boolean);
                var stamp=new Date().toISOString().replace(/[:.]/g,'-');
                var host=location.hostname||'site';
                QingGuangDownload.saveText('站点数据-'+host+'-'+stamp+'.json','application/json',JSON.stringify(result));
              }).catch(function(e){
                QingGuangDownload.fail(String(e));
              });
            })();
        """.trimIndent()

        private val NIGHT_CSS_JS = """
            (function(){
              if (document.getElementById('qg-night')) return;
              try {
                var root = document.documentElement;
                var body = document.body || root;
                var bg = getComputedStyle(body).backgroundColor || '';
                var m = bg.match(/rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)/i);
                var light = true;
                if (m) {
                  var lum = (0.299 * (+m[1]) + 0.587 * (+m[2]) + 0.114 * (+m[3])) / 255;
                  light = lum > 0.55;
                }
                var s = document.createElement('style');
                s.id = 'qg-night';
                s.textContent = light
                  ? 'html{filter:invert(1) hue-rotate(180deg);background:#101214!important}' +
                    'img,video,canvas,svg,picture{filter:invert(1) hue-rotate(180deg)!important}'
                  : 'html{color-scheme:dark;background:#101214!important}';
                root.appendChild(s);
              } catch (e) {}
            })();
        """.trimIndent()

        private const val AD_HIDE_JS =
            "(function(){if(document.getElementById('qg-adblock'))return;" +
                "var s=document.createElement('style');s.id='qg-adblock';" +
                "s.textContent='ins.adsbygoogle,.adsbygoogle,[id^=\"google_ads\"],[id^=\"div-gpt-ad\"]," +
                ".ad-banner,.ad-container,.ad-wrapper,[class*=\"advertisement\"]," +
                "[id*=\"ad-banner\"],[class*=\"adsbygoogle\"]{display:none!important;}';" +
                "(document.head||document.documentElement).appendChild(s);})()"

        /** 阅读模式：挑选正文容器并重排，尽量保留图片 */
        private const val READER_JS =
            "(function(){" +
                "if(window.__qgReader)return false;" +
                "var cands=[].slice.call(document.querySelectorAll('article,main,[role=\"main\"]," +
                ".article,.post,.content,#content,.markdown-body'));" +
                "var best=null,score=0;" +
                "cands.forEach(function(el){var t=el.innerText||'';if(t.length>score){score=t.length;best=el;}});" +
                "if(!best||score<300)best=document.body;" +
                "if(!best)return false;" +
                "var dark=window.matchMedia&&window.matchMedia('(prefers-color-scheme: dark)').matches;" +
                "var html=best.innerHTML,title=document.title;" +
                "document.body.innerHTML='';" +
                "document.body.style.cssText='margin:0;padding:0;background:'+(dark?'#121212':'#ffffff')+';';" +
                "var holder=document.createElement('div');" +
                "holder.style.cssText='max-width:760px;margin:0 auto;padding:28px 20px 60px;" +
                "font-size:19px;line-height:1.9;font-family:Georgia,\"Noto Serif SC\",serif;color:'+(dark?'#e6e6e6':'#1b1b1b')+';';" +
                "holder.innerHTML=html;" +
                "holder.querySelectorAll('script,style,nav,footer,aside,form,iframe,button,video').forEach(" +
                "function(n){try{n.remove();}catch(e){}});" +
                "document.body.appendChild(holder);" +
                "window.__qgReader=true;document.title=title;return true;})()"
    }
}
