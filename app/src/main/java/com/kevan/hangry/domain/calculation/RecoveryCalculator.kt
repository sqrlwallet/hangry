package com.kevan.hangry.domain.calculation

import com.kevan.hangry.domain.model.RecoveryResult
import java.time.LocalDate

interface RecoveryCalculator {
    fun calculateRecovery(
        date: LocalDate,
        currentDayMetrics: DayMetrics,
        baselineHistory: List<DayMetrics>,
        config: RecoveryConfig = RecoveryConfig()
    ): RecoveryResult
}

data class DayMetrics(
    val date: LocalDate,
    val sleepDurationMinutes: Int?,
    val restingHeartRate: Double?,
    val hrvRmssd: Double?,
    val trainingLoad: Double? = 0.0,
    val sleepConsistencyPercentage: Int? = 85
)

data class RecoveryConfig(
    val algorithmVersion: Int = 1,
    val minDaysForBaseline: Int = 3,
    val optimalBaselineDays: Int = 7,
    val hrvWeight: Double = 0.35,
    val rhrWeight: Double = 0.25,
    val sleepWeight: Double = 0.25,
    val loadWeight: Double = 0.15,
    val defaultTargetSleepMinutes: Int = 480 // 8 hours
)
