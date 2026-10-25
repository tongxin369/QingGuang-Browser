package com.qingguang.browser

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.webkit.WebChromeClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.qingguang.browser.ui.BrowserScreen
import com.qingguang.browser.ui.theme.QingGuangTheme
import com.qingguang.browser.ui.theme.ThemeController
import com.qingguang.browser.web.BrowserController
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var controller: BrowserController
    private var pendingLocationCallback: ((Boolean) -> Unit)? = null
    private var pendingFileCallback: ((Array<Uri>?) -> Unit)? = null

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        pendingLocationCallback?.invoke(granted)
        pendingLocationCallback = null
    }

    /** 网页 <input type="file"> 选文件结果回传 */
    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = pendingFileCallback
        pendingFileCallback = null
        val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        callback?.invoke(uris)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeController.init(this)
        controller = BrowserController(this)
        controller.locationRequester = { callback ->
            if (hasLocationPermission()) {
                callback(true)
            } else {
                pendingLocationCallback = callback
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }
        controller.fileChooserRequester = { params, callback ->
            pendingFileCallback = callback
            val launched = runCatching { fileChooserLauncher.launch(params.createIntent()) }.isSuccess
            if (!launched) {
                pendingFileCallback = null
                callback(null)
            }
        }
        setContent {
            val systemDark = isSystemInDarkTheme()
            val dark = when (ThemeController.mode) {
                1 -> false
                2 -> true
                else -> systemDark
            }
            QingGuangTheme(darkTheme = dark) {
                BrowserScreen(controller)
            }
        }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** 处理从其他应用「打开方式」或「分享」进来的链接与 HTML 文件 */
    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_VIEW -> {
                val data = intent.data ?: return
                if (data.scheme == "http" || data.scheme == "https") {
                    controller.loadUrl(data.toString())
                } else {
                    openLocalHtml(data)
                }
            }
            Intent.ACTION_SEND -> {
                @Suppress("DEPRECATION")
                val stream = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                if (stream != null) openLocalHtml(stream)
            }
        }
    }

    private fun openLocalHtml(uri: Uri) {
        lifecycleScope.launch {
            runCatching {
                val app = applicationContext as App
                val name = queryDisplayName(uri) ?: "本地网页"
                val page = app.localSites.importSingleHtml(uri, name)
                app.database.localPageDao().upsert(page)
                controller.loadUrl(page.entryUrl)
            }.onFailure {
                Toast.makeText(this@MainActivity, "打开失败：${it.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        var name: String? = null
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) name = cursor.getString(index)
            }
        }
        return name
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    override fun onStop() {
        super.onStop()
        controller.autoBackupCurrent()
    }

    override fun onDestroy() {
        controller.destroy()
        super.onDestroy()
    }
}
