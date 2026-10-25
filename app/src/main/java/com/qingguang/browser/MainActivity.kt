package com.qingguang.browser

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.qingguang.browser.ui.BrowserScreen
import com.qingguang.browser.ui.theme.QingGuangTheme
import com.qingguang.browser.web.BrowserController

class MainActivity : ComponentActivity() {

    private lateinit var controller: BrowserController
    private var pendingLocationCallback: ((Boolean) -> Unit)? = null

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        pendingLocationCallback?.invoke(granted)
        pendingLocationCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        setContent {
            QingGuangTheme {
                BrowserScreen(controller)
            }
        }
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
