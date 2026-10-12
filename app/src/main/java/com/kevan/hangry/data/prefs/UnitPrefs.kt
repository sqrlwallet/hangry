package com.kevan.hangry.data.prefs

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.kevan.hangry.domain.model.LengthUnit
import com.kevan.hangry.domain.model.WeightUnit
import java.util.Locale

/**
 * Display units. Hangry stores and calculates in °C, kg and cm; these only change how values are
 * shown and typed. Temperature defaults to °F where that's the everyday unit (US and a few
 * others). Weight and length default to metric - existing users keep what they've always seen -
 * and are set from the "cm · kg / ft · lb" choice in onboarding.
 */
object UnitPrefs {
    private const val FILE = "hangry_units"
    private const val KEY_FAHRENHEIT = "temperature_fahrenheit"
    private const val KEY_WEIGHT = "weight_unit"
    private const val KEY_LENGTH = "length_unit"
    private val FAHRENHEIT_COUNTRIES = setOf("US", "LR", "MM", "BS", "BZ", "KY", "PW", "FM", "MH")

    /** Snapshot state, so screens showing values update the moment a setting changes. */
    private val fahrenheitState = mutableStateOf<Boolean?>(null)
    private val weightState = mutableStateOf<WeightUnit?>(null)
    private val lengthState = mutableStateOf<LengthUnit?>(null)

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun fahrenheit(context: Context): Boolean = fahrenheitState.value ?: run {
        val prefs = prefs(context)
        val stored = if (prefs.contains(KEY_FAHRENHEIT)) prefs.getBoolean(KEY_FAHRENHEIT, false)
        else Locale.getDefault().country in FAHRENHEIT_COUNTRIES
        fahrenheitState.value = stored
        stored
    }

    fun setFahrenheit(context: Context, fahrenheit: Boolean) {
        prefs(context).edit().putBoolean(KEY_FAHRENHEIT, fahrenheit).apply()
        fahrenheitState.value = fahrenheit
    }

    fun weightUnit(context: Context): WeightUnit = weightState.value ?: run {
        val stored = prefs(context).getString(KEY_WEIGHT, null)
            ?.let { runCatching { WeightUnit.valueOf(it) }.getOrNull() } ?: WeightUnit.KG
        weightState.value = stored
        stored
    }

    fun setWeightUnit(context: Context, unit: WeightUnit) {
        prefs(context).edit().putString(KEY_WEIGHT, unit.name).apply()
        weightState.value = unit
    }

    fun lengthUnit(context: Context): LengthUnit = lengthState.value ?: run {
        val stored = prefs(context).getString(KEY_LENGTH, null)
            ?.let { runCatching { LengthUnit.valueOf(it) }.getOrNull() } ?: LengthUnit.CM
        lengthState.value = stored
        stored
    }

    fun setLengthUnit(context: Context, unit: LengthUnit) {
        prefs(context).edit().putString(KEY_LENGTH, unit.name).apply()
        lengthState.value = unit
    }

    /** Onboarding's single "cm · kg" / "ft · lb" switch. Keeps stones if already chosen. */
    fun isImperial(context: Context): Boolean = weightUnit(context) != WeightUnit.KG

    fun setImperial(context: Context, imperial: Boolean) {
        if (imperial) {
            if (weightUnit(context) == WeightUnit.KG) setWeightUnit(context, WeightUnit.LB)
            setLengthUnit(context, LengthUnit.IN)
        } else {
            setWeightUnit(context, WeightUnit.KG)
            setLengthUnit(context, LengthUnit.CM)
        }
    }
}
