package com.kevan.hangry.domain.calculation

import kotlin.math.roundToInt

/**
 * Personal heart-rate zones from heart-rate reserve (Karvonen): zone n starts at
 * resting + (40 + 10n)% of (max - resting), so zone 1 is 50-60% of reserve and zone 5 is 90%+.
 * Below 50% isn't a training zone; 40-50% ([lightActivityLowerBound]) is everyday movement like
 * a brisk walk, which strain counts at half weight. Sitting and sleeping sit well below it.
 */
data class HeartRateZones(val maxHr: Double, val restingHr: Double) {

    /** Lower bpm edge of zones 1-5. */
    val lowerBounds: List<Double> = ZONE_FRACTIONS.map { restingHr + it * (maxHr - restingHr) }

    /** Start of light activity: 40% of heart-rate reserve. */
    val lightActivityLowerBound: Double = restingHr + LIGHT_ACTIVITY_FRACTION * (maxHr - restingHr)

    /** 0-4 for zones 1-5, or -1 below zone 1. */
    fun zoneIndex(bpm: Double): Int = lowerBounds.indexOfLast { bpm >= it }

    /** "124-136" style labels per zone, for screens. */
    fun labels(): List<String> = lowerBounds.mapIndexed { i, low ->
        val high = lowerBounds.getOrNull(i + 1)
        if (high == null) "${low.roundToInt()}+" else "${low.roundToInt()}-${high.roundToInt() - 1}"
    }

    companion object {
        val ZONE_FRACTIONS = listOf(0.5, 0.6, 0.7, 0.8, 0.9)
        const val LIGHT_ACTIVITY_FRACTION = 0.4
        const val DEFAULT_AGE = 35
        const val DEFAULT_RESTING_HR = 60.0

        /** Tanaka et al. (2001): 208 - 0.7 x age; more accurate than 220 - age past 40. */
        fun estimateMaxHr(age: Int?): Double = 208.0 - 0.7 * (age ?: DEFAULT_AGE).coerceIn(10, 100)

        /**
         * [maxHrOverride] (from Settings) wins over the age estimate. [restingHr] is the user's
         * usual resting heart rate; implausible values fall back to 60.
         */
        fun forUser(age: Int?, maxHrOverride: Int?, restingHr: Double?): HeartRateZones {
            val max = maxHrOverride?.takeIf { it in 120..230 }?.toDouble() ?: estimateMaxHr(age)
            val rest = restingHr?.takeIf { it in 30.0..100.0 && it < max - 40 } ?: DEFAULT_RESTING_HR
            return HeartRateZones(max, rest)
        }

        val DEFAULT = forUser(null, null, null)
    }
}
