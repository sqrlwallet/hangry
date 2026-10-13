package com.kevan.hangry.ui.widget

import kotlin.math.roundToInt

/** Pure number logic behind the Activity, Water, Strain and Bedtime widgets, kept apart from RemoteViews so it's testable. */
internal object DayWidgetText {

    /** Sleep debt below this isn't worth a chip on the Bedtime widget. */
    const val DEBT_WORTH_SHOWING_MINUTES = 15

    /** Whole percent of [goal], uncapped; null without a goal. */
    fun percent(value: Double, goal: Double?): Int? =
        if (goal == null || goal <= 0) null else (value / goal * 100).roundToInt().coerceAtLeast(0)

    /** 0..100 for a progress bar. */
    fun progress(value: Double, goal: Double?): Int = percent(value, goal)?.coerceAtMost(100) ?: 0

    enum class StrainStatus { BELOW, ON_TARGET, ABOVE }

    /** Where today's strain sits against the recommended range. */
    fun strainStatus(strain: Double, low: Double, high: Double): StrainStatus = when {
        strain > high -> StrainStatus.ABOVE
        strain >= low -> StrainStatus.ON_TARGET
        else -> StrainStatus.BELOW
    }

    /** Hours and minutes of a duration in minutes, e.g. 452 -> (7, 32). */
    fun hoursMinutes(minutes: Int): Pair<Int, Int> = minutes.coerceAtLeast(0).let { it / 60 to it % 60 }
}
