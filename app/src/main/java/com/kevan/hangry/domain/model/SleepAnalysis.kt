package com.kevan.hangry.domain.model

import java.time.LocalTime

data class SleepAnalysis(
    val durationMinutes: Int,
    val timeInBedMinutes: Int? = null,
    val deepSleepMinutes: Int = 0,
    val remSleepMinutes: Int = 0,
    val lightSleepMinutes: Int = 0,
    val awakeMinutes: Int = 0,
    val restorativePercentage: Int = 0,
    val sleepNeedMinutes: Int = 480,
    val sleepPerformancePercentage: Int = 100,
    val sleepQualityScore: Int = 70,
    val recommendedBedtime: LocalTime? = null,
    val consistencyPercentage: Int = 85,
    val sevenDayAverageMinutes: Int = 480,
    val thirtyDayAverageMinutes: Int = 480,
    val sleepDebtMinutes: Int = 0,
    val baselineRatio: Double = 1.0,
    val supportiveNote: String
)
