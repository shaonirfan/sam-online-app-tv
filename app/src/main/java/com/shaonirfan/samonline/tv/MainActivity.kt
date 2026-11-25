package com.shaonirfan.samonline.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.compose.rememberNavController
import com.shaonirfan.samonline.tv.ui.components.LocalAppContainer
import com.shaonirfan.samonline.tv.ui.navigation.SamNavHost
import com.shaonirfan.samonline.tv.ui.theme.SamColors
import com.shaonirfan.samonline.tv.ui.theme.SamOnlineTheme

class MainActivity : ComponentActivity() {

    private val container get() = (application as SamOnlineApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent {
            CompositionLocalProvider(LocalAppContainer provides container) {
                SamOnlineTheme {
                    Box(Modifier.fillMaxSize().background(SamColors.Background)) {
                        SamNavHost(rememberNavController())
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // BeeGear behaviour: pick up queued / connection-aborted downloads when the app returns.
        container.downloads.onAppResumed()
    }
}
