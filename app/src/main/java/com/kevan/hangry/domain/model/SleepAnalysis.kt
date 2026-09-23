package com.kevan.hangry.domain.model

import java.time.LocalTime

/**
 * A night's sleep. Anything the device didn't record, or that needs more history than there
 * is, stays null - screens show "—" rather than a plausible-looking default.
 */
data class SleepAnalysis(
    val durationMinutes: Int,
    val timeInBedMinutes: Int? = null,
    /** Sleep stages - only when the device recorded them. */
    val deepSleepMinutes: Int? = null,
    val remSleepMinutes: Int? = null,
    val lightSleepMinutes: Int? = null,
    val awakeMinutes: Int? = null,
    val restorativePercentage: Int? = null,
    /** Personal baseline plus adjustments; the 8h target until there's history. */
    val sleepNeedMinutes: Int = 480,
    val sleepPerformancePercentage: Int? = null,
    val sleepQualityScore: Int? = null,
    val recommendedBedtime: LocalTime? = null,
    /** Needs at least 3 recent nights. */
    val consistencyPercentage: Int? = null,
    val sevenDayAverageMinutes: Int? = null,
    val thirtyDayAverageMinutes: Int? = null,
    val sleepDebtMinutes: Int = 0,
    val baselineRatio: Double = 1.0,
    val supportiveNote: String
)
