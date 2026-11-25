package com.shaonirfan.samonline.tv.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Switch
import androidx.tv.material3.SwitchDefaults
import androidx.tv.material3.Text
import coil.imageLoader
import com.shaonirfan.samonline.tv.BuildConfig
import com.shaonirfan.samonline.tv.data.remote.DirectoryScraper
import com.shaonirfan.samonline.tv.ui.components.FocusRestoreEffect
import com.shaonirfan.samonline.tv.ui.components.LocalAppContainer
import com.shaonirfan.samonline.tv.ui.components.initialFocus
import com.shaonirfan.samonline.tv.ui.components.rememberFocus
import com.shaonirfan.samonline.tv.ui.components.rememberFocusMemory
import com.shaonirfan.samonline.tv.ui.theme.Dimens
import com.shaonirfan.samonline.tv.ui.theme.SamColors
import java.text.DateFormat
import java.util.Date

@Composable
fun SettingsScreen() {
    val container = LocalAppContainer.current
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    val index by container.search.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focus = rememberFocusMemory("settings")

    LazyColumn(
        contentPadding = PaddingValues(horizontal = Dimens.SafeHorizontal, vertical = Dimens.SafeVertical),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Text(
                "Settings", style = MaterialTheme.typography.headlineMedium, color = SamColors.TextPrimary,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        item {
            ToggleRow("Autoplay next episode", "Starts the next episode when one ends", settings.autoplayNext,
                Modifier.initialFocus(focus).rememberFocus(focus, "autoplay")) {
                container.settings.update { s -> s.copy(autoplayNext = !s.autoplayNext) }
            }
        }
        item {
            ToggleRow("Subtitles on by default", "Turns on auto-loaded .srt / .vtt subtitles", settings.subtitlesByDefault,
                Modifier.rememberFocus(focus, "subs")) {
                container.settings.update { s -> s.copy(subtitlesByDefault = !s.subtitlesByDefault) }
            }
        }
        item {
            ToggleRow("Rotate the home hero", "Auto-advance the featured carousel", settings.heroAutoRotate,
                Modifier.rememberFocus(focus, "hero")) {
                container.settings.update { s -> s.copy(heroAutoRotate = !s.heroAutoRotate) }
            }
        }
        item {
            ToggleRow("Allow downloads", "Show Download actions on titles", settings.downloadsEnabled,
                Modifier.rememberFocus(focus, "downloads")) {
                container.settings.update { s -> s.copy(downloadsEnabled = !s.downloadsEnabled) }
            }
        }
        item {
            val built = if (index.builtAt > 0) DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(index.builtAt)) else "never"
            ActionRow(
                "Rebuild search index",
                if (index.building) "Indexing… ${index.count} titles" else "${index.count} titles · last built $built",
                Modifier.rememberFocus(focus, "index"),
            ) {
                container.search.rebuild()
                Toast.makeText(context, "Rebuilding the search index in the background", Toast.LENGTH_SHORT).show()
            }
        }
        item {
            ActionRow(
                "Refresh library & posters",
                "Clears cached listings, posters and images (${container.posters.size} posters cached)",
                Modifier.rememberFocus(focus, "cache"),
            ) {
                DirectoryScraper.clearCache()
                container.posters.clear()
                context.imageLoader.memoryCache?.clear()
                context.imageLoader.diskCache?.clear()
                Toast.makeText(context, "Caches cleared", Toast.LENGTH_SHORT).show()
            }
        }
        item {
            Text(
                "SAM Online TV ${BuildConfig.VERSION_NAME} · Works on SAM Online / BDIX connections (DHAKA-FLIX servers)",
                style = MaterialTheme.typography.bodySmall, color = SamColors.TextMuted,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
    FocusRestoreEffect(focus)
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, modifier: Modifier, onToggle: () -> Unit) {
    ListItem(
        selected = false,
        onClick = onToggle,
        modifier = modifier.fillMaxWidth(),
        scale = ListItemDefaults.scale(focusedScale = 1.02f),
        colors = ListItemDefaults.colors(
            containerColor = SamColors.Surface,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
        ),
        headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
        supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodySmall) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(checkedTrackColor = SamColors.Accent),
            )
        },
    )
}

@Composable
private fun ActionRow(title: String, subtitle: String, modifier: Modifier, onClick: () -> Unit) {
    ListItem(
        selected = false,
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        scale = ListItemDefaults.scale(focusedScale = 1.02f),
        colors = ListItemDefaults.colors(
            containerColor = SamColors.Surface,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
        ),
        headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
        supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodySmall) },
    )
}
