package com.kevan.hangry.domain.model

import java.util.Locale

/** Temperature changes (skin temperature deltas) in the user's unit. A change of 1 °C is 1.8 °F. */
object TemperatureText {
    fun deltaValue(deltaC: Double, fahrenheit: Boolean): Double = if (fahrenheit) deltaC * 1.8 else deltaC

    /** e.g. "+0.6 °C" or "+1.1 °F". */
    fun delta(deltaC: Double, fahrenheit: Boolean): String =
        String.format(Locale.US, "%+.1f %s", deltaValue(deltaC, fahrenheit), if (fahrenheit) "°F" else "°C")
}
