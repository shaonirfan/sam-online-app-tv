package com.shaonirfan.samonline.tv

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.shaonirfan.samonline.tv.data.remote.Servers
import com.shaonirfan.samonline.tv.ui.navigation.Routes
import com.shaonirfan.samonline.tv.ui.navigation.openItem
import com.shaonirfan.samonline.tv.ui.navigation.play
import com.shaonirfan.samonline.tv.ui.components.LocalAppContainer
import com.shaonirfan.samonline.tv.ui.navigation.SamNavHost
import com.shaonirfan.samonline.tv.ui.theme.SamColors
import com.shaonirfan.samonline.tv.ui.theme.SamOnlineTheme

class MainActivity : ComponentActivity() {

    private val container get() = (application as SamOnlineApp).container

    /** A pending `--es open <target>` test request (see TestHooks). */
    private val openRequest = mutableStateOf<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra("open")?.let { openRequest.value = it }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        openRequest.value = intent?.getStringExtra("open")
        setContent {
            val nav = rememberNavController()
            CompositionLocalProvider(LocalAppContainer provides container) {
                SamOnlineTheme {
                    Box(Modifier.fillMaxSize().background(SamColors.Background)) {
                        SamNavHost(nav)
                    }
                }
            }
            val target = openRequest.value
            LaunchedEffect(target) {
                if (target != null) {
                    openRequest.value = null
                    // Let the NavHost attach its graph before navigating.
                    withFrameNanos { }
                    handleOpen(target, nav)
                }
            }
        }
    }

    private suspend fun handleOpen(target: String, nav: NavHostController) {
        val parts = target.split(':')
        val kind = parts[0]
        val arg = parts.getOrNull(1).orEmpty()
        val index = parts.getOrNull(2)?.toIntOrNull() ?: 0
        // Every request starts from a clean Home back stack.
        nav.navigate(Routes.HOME) { popUpTo(nav.graph.id) { inclusive = true } }
        val ok: Boolean = when (kind) {
            "home" -> true
            "search" -> {
                TestHooks.pendingSearch = target.substringAfter(':', "")
                nav.navigate(Routes.SEARCH)
                true
            }
            "category" -> Servers.byId(arg)?.let { nav.navigate(Routes.folder(it.url, it.name, null)); true } ?: false
            "title", "play" -> {
                val category = Servers.byId(arg)
                val item = category?.let { container.catalog.categoryRow(it).getOrNull(index) }
                when {
                    item == null -> false
                    kind == "title" -> { nav.openItem(item); true }
                    else -> container.playback.resolve(item).getOrNull()
                        ?.let { nav.play(it.queue, it.index, it.positionMs); true } ?: false
                }
            }
            else -> false
        }
        TestLog.mark("open", "target" to target, "ok" to ok)
    }

    override fun onResume() {
        super.onResume()
        // BeeGear behaviour: pick up queued / connection-aborted downloads when the app returns.
        container.downloads.onAppResumed()
    }
}
