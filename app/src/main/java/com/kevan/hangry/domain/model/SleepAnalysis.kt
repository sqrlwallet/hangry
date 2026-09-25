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
    /** Time asleep / time in bed. */
    val efficiencyPercentage: Int? = null,
    /** What this night needed: the sleep goal plus recent sleep debt and a hard day's extra. */
    val sleepNeedMinutes: Int = 480,
    /** What tonight needs, including anything this night fell short by. */
    val tonightsNeedMinutes: Int = sleepNeedMinutes,
    val sleepPerformancePercentage: Int? = null,
    /** How well you slept (efficiency, deep + REM, regular timing), regardless of length. */
    val sleepQualityScore: Int? = null,
    /** 0-100: how much of your need you got (half the score) plus how well you slept. */
    val sleepScore: Int? = null,
    /** The Sleep Score's parts, each 0-100; null when not measured. */
    val efficiencyScore: Int? = null,
    val restorativeScore: Int? = null,
    val recommendedBedtime: LocalTime? = null,
    /** How regular bed and wake times are. Needs at least 3 nights. */
    val consistencyPercentage: Int? = null,
    val sevenDayAverageMinutes: Int? = null,
    val thirtyDayAverageMinutes: Int? = null,
    val sleepDebtMinutes: Int = 0,
    val baselineRatio: Double = 1.0,
    val supportiveNote: String
)
