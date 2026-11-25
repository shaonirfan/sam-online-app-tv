package com.shaonirfan.samonline.tv.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    val autoplayNext: Boolean = true,
    val subtitlesByDefault: Boolean = true,
    val downloadsEnabled: Boolean = true,
    val heroAutoRotate: Boolean = true,
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("sam_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(
        AppSettings(
            autoplayNext = prefs.getBoolean("autoplay_next", true),
            subtitlesByDefault = prefs.getBoolean("subs_default", true),
            downloadsEnabled = prefs.getBoolean("downloads_enabled", true),
            heroAutoRotate = prefs.getBoolean("hero_rotate", true),
        )
    )
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        _settings.value = next
        prefs.edit()
            .putBoolean("autoplay_next", next.autoplayNext)
            .putBoolean("subs_default", next.subtitlesByDefault)
            .putBoolean("downloads_enabled", next.downloadsEnabled)
            .putBoolean("hero_rotate", next.heroAutoRotate)
            .apply()
    }

    /** OpenSubtitles.com account (raises online subtitle downloads from 5 to 20 a day). */
    var openSubtitlesUser: String
        get() = prefs.getString("os_user", "").orEmpty()
        set(value) = prefs.edit().putString("os_user", value.trim()).apply()

    var openSubtitlesPassword: String
        get() = prefs.getString("os_pass", "").orEmpty()
        set(value) = prefs.edit().putString("os_pass", value).apply()

    /** Last chosen playback speed / subtitle language survive between sessions. */
    var playbackSpeed: Float
        get() = prefs.getFloat("speed", 1f)
        set(value) = prefs.edit().putFloat("speed", value).apply()
}
