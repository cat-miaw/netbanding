package com.netbanding.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import com.netbanding.app.data.notify.PriceDropMonitor
import com.netbanding.app.ui.TemplateNavHost
import com.netbanding.app.ui.theme.TemplateTheme

/**
 * Single activity. Edge-to-edge is enabled once here so no other screen has to
 * think about insets.
 */
class MainActivity : ComponentActivity() {

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must be called before super.onCreate. On API 31+ it draws the system
        // splash for free; on older versions it emulates it from the theme.
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = false

        // Hold the splash only if there is genuinely blocking work. Everything
        // else should render immediately and fill in.
        //
        // splashScreen.setKeepOnScreenCondition { viewModel.isBlocking }

        PriceDropMonitor.ensureChannel(this)
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            TemplateTheme {
                TemplateNavHost(container = appContainer)
            }
        }
    }
}