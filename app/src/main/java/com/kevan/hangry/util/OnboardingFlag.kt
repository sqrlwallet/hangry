package com.kevan.hangry.util

import android.content.Context

/**
 * Whether onboarding is finished, cached outside the database so app start can decide where to
 * go instantly instead of waiting on Room (including any migration) on the main thread.
 */
object OnboardingFlag {
    private const val FILE = "hangry_app_state"
    private const val KEY = "onboarding_completed"

    /** Null until it has been recorded once (first launch after this was added). */
    fun get(context: Context): Boolean? {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        return if (prefs.contains(KEY)) prefs.getBoolean(KEY, false) else null
    }

    fun set(context: Context, completed: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY, completed).apply()
    }
}
