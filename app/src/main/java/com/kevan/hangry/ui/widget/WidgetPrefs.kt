package com.kevan.hangry.ui.widget

import android.content.Context

/** Widget-only preferences. A home screen is visible to anyone holding the phone. */
object WidgetPrefs {
    private const val FILE = "hangry_widget_prefs"
    private const val KEY_HIDE_VALUES = "hide_health_values"

    /** When on, health widgets (heart, markers, goals, weight, posture, cycle) show no numbers. */
    fun hideValues(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_HIDE_VALUES, false)

    fun setHideValues(context: Context, hide: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY_HIDE_VALUES, hide).apply()
        HangryWidgetUpdater.updateAllWidgets(context)
    }
}
