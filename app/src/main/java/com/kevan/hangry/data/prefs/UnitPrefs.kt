package com.kevan.hangry.data.prefs

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import java.util.Locale

/**
 * Display units for temperature. Hangry stores and calculates in °C; this only changes how
 * readings are shown. Defaults to °F where that's the everyday unit (US and a few others).
 */
object UnitPrefs {
    private const val FILE = "hangry_units"
    private const val KEY_FAHRENHEIT = "temperature_fahrenheit"
    private val FAHRENHEIT_COUNTRIES = setOf("US", "LR", "MM", "BS", "BZ", "KY", "PW", "FM", "MH")

    /** Snapshot state, so screens showing temperatures update the moment the setting changes. */
    private val fahrenheitState = mutableStateOf<Boolean?>(null)

    fun fahrenheit(context: Context): Boolean = fahrenheitState.value ?: run {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val stored = if (prefs.contains(KEY_FAHRENHEIT)) prefs.getBoolean(KEY_FAHRENHEIT, false)
        else Locale.getDefault().country in FAHRENHEIT_COUNTRIES
        fahrenheitState.value = stored
        stored
    }

    fun setFahrenheit(context: Context, fahrenheit: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY_FAHRENHEIT, fahrenheit).apply()
        fahrenheitState.value = fahrenheit
    }
}
