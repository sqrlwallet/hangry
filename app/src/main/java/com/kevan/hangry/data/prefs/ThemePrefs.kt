package com.kevan.hangry.data.prefs

import android.content.Context
import androidx.compose.runtime.mutableStateOf

enum class ThemeMode {
    SYSTEM,
    DARK,
    LIGHT
}

/**
 * App theme preferences: System default, Dark (Obsidian), or Light (Cream).
 * Uses snapshot state so changing the theme updates the entire UI instantly.
 */
object ThemePrefs {
    private const val FILE = "hangry_theme"
    private const val KEY_THEME_MODE = "theme_mode"

    private val themeModeState = mutableStateOf<ThemeMode?>(null)

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun themeMode(context: Context): ThemeMode = themeModeState.value ?: run {
        val stored = prefs(context).getString(KEY_THEME_MODE, null)
            ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
        themeModeState.value = stored
        stored
    }

    fun setThemeMode(context: Context, mode: ThemeMode) {
        prefs(context).edit().putString(KEY_THEME_MODE, mode.name).apply()
        themeModeState.value = mode
    }
}
