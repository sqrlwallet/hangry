package com.kevan.hangry.domain.calculation

import com.kevan.hangry.domain.model.HrvFeeling
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
    /** Null until there are enough nights to judge; never assumed. */
    val sleepConsistencyPercentage: Int? = null
)

data class RecoveryConfig(
    // Bump whenever scoring changes - stored scores from older versions are recomputed on launch.
    // 4: no score for days with no readings; unknown consistency no longer assumed.
    val algorithmVersion: Int = 4,
    val minDaysForBaseline: Int = 3,
    val optimalBaselineDays: Int = 7,
    val hrvWeight: Double = 0.35,
    val rhrWeight: Double = 0.25,
    val sleepWeight: Double = 0.25,
    val loadWeight: Double = 0.15,
    val defaultTargetSleepMinutes: Int = 480, // 8 hours
    // Many wearables never report HRV to Health Connect. Rather than letting RHR and sleep carry
    // the whole score, a missing HRV reading is treated as an excellent day by default - the user
    // can lower it per day with how they actually feel (HrvFeeling).
    val assumedHrvScore: Double = HrvFeeling.DEFAULT.score
)
