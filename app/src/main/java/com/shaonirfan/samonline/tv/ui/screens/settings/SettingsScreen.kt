package com.shaonirfan.samonline.tv.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.window.Dialog
import com.shaonirfan.samonline.tv.ui.components.PrimaryActionButton
import com.shaonirfan.samonline.tv.ui.components.SecondaryActionButton
import kotlinx.coroutines.launch
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
    var showAccount by remember { mutableStateOf(false) }
    var accountUser by remember { mutableStateOf(container.settings.openSubtitlesUser) }

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
            val os = container.onlineSubtitles
            ActionRow(
                "Subtitles account (OpenSubtitles)",
                when {
                    !os.isConfigured -> "Online subtitles are not available in this build"
                    accountUser.isBlank() -> "Not logged in: 5 online subtitle downloads a day. Log in for 20."
                    else -> "Logged in as $accountUser"
                },
                Modifier.rememberFocus(focus, "osaccount"),
            ) { if (os.isConfigured) showAccount = true }
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

    if (showAccount) {
        AccountDialog(
            initialUser = container.settings.openSubtitlesUser,
            onLogin = { user, pass ->
                val allowed = container.onlineSubtitles.testLogin(user, pass)
                container.settings.openSubtitlesUser = user
                container.settings.openSubtitlesPassword = pass
                accountUser = user
                allowed
            },
            onLogout = {
                container.settings.openSubtitlesUser = ""
                container.settings.openSubtitlesPassword = ""
                container.onlineSubtitles.logout()
                accountUser = ""
            },
            onDismiss = { showAccount = false },
        )
    }
}

/** Username/password form; the TV's on-screen keyboard opens when a field is selected. */
@Composable
private fun AccountDialog(
    initialUser: String,
    onLogin: suspend (String, String) -> Int,
    onLogout: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var user by remember { mutableStateOf(initialUser) }
    var pass by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val userFocus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(520.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(SamColors.Surface)
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("OpenSubtitles account", style = MaterialTheme.typography.headlineSmall, color = SamColors.TextPrimary)
            Text(
                "Free account at opensubtitles.com. Logged in you get 20 subtitle downloads a day (5 without).",
                style = MaterialTheme.typography.bodyMedium, color = SamColors.TextSecondary,
            )
            TvTextField(user, { user = it }, "Username", Modifier.focusRequester(userFocus))
            TvTextField(pass, { pass = it }, "Password", password = true)
            if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodyMedium, color = SamColors.AccentSoft)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PrimaryActionButton(if (busy) "Checking…" else "Log in", Icons.AutoMirrored.Filled.Login, {
                    if (busy || user.isBlank() || pass.isBlank()) return@PrimaryActionButton
                    busy = true
                    status = ""
                    scope.launch {
                        runCatching { onLogin(user.trim(), pass) }
                            .onSuccess { allowed ->
                                Toast.makeText(context, "Logged in: $allowed downloads a day", Toast.LENGTH_LONG).show()
                                onDismiss()
                            }
                            .onFailure { status = it.message ?: "Login failed" }
                        busy = false
                    }
                })
                if (initialUser.isNotBlank()) {
                    SecondaryActionButton("Log out", Icons.AutoMirrored.Filled.Logout, { onLogout(); onDismiss() })
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { userFocus.requestFocus() } }
}

@Composable
private fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    password: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = SamColors.TextMuted)
        Spacer(Modifier.height(6.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (password) KeyboardType.Password else KeyboardType.Text,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Next,
            ),
            textStyle = MaterialTheme.typography.titleMedium.copy(color = SamColors.TextPrimary),
            cursorBrush = SolidColor(SamColors.Accent),
            modifier = modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused }
                .clip(RoundedCornerShape(8.dp))
                .background(SamColors.SurfaceRaised)
                .border(2.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(8.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        )
    }
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
