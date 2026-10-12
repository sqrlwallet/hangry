package com.kevan.hangry.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.kevan.hangry.data.prefs.UnitPrefs
import com.kevan.hangry.domain.model.TemperatureText

/** A skin temperature change in the unit chosen in Settings, e.g. "+0.6 °C". */
@Composable
fun skinTempText(deltaC: Double): String = TemperatureText.delta(deltaC, UnitPrefs.fahrenheit(LocalContext.current))
