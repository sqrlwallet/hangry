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
    val sleepConsistencyPercentage: Int? = null,
    /** What the night needed (goal + recent debt + a hard day); sleep is scored against it. */
    val sleepNeedMinutes: Int? = null,
    /** How well you slept (efficiency, deep + REM, regular timing), 0-100. */
    val sleepQualityScore: Int? = null,
    /** Breaths per minute, usually measured overnight. */
    val respiratoryRate: Double? = null,
    /** Yesterday's final strain and the target band recommended for it. */
    val previousDayStrain: Double? = null,
    val previousDayStrainTarget: ClosedFloatingPointRange<Double>? = null
)

data class RecoveryConfig(
    // Bump whenever scoring changes - stored scores from older versions are recomputed on launch.
    // 4: no score for days with no readings; unknown consistency no longer assumed.
    // 5: 30-day baselines scored by how unusual today is (z-scores, HRV on a log scale),
    //    overnight RHR/HRV, sleep against sleep need, breathing-rate warning.
    // 6: RHR -2 points per bpm above the monthly average; yesterday's strain vs its target
    //    replaces sleep consistency; sleep debt repaid over two weeks.
    val algorithmVersion: Int = 6,
    val minDaysForBaseline: Int = 3,
    val optimalBaselineDays: Int = 7,
    /** Days of history the baseline is built from. */
    val baselineWindowDays: Int = 30,
    val hrvWeight: Double = 0.35,
    val rhrWeight: Double = 0.25,
    val sleepWeight: Double = 0.25,
    /** Strain balance: yesterday's strain against the target recommended for it. */
    val loadWeight: Double = 0.15,
    val defaultTargetSleepMinutes: Int = 480, // 8 hours
    // Many wearables never report HRV to Health Connect. Rather than letting RHR and sleep carry
    // the whole score, a missing HRV reading is treated as an excellent day by default - the user
    // can lower it per day with how they actually feel (HrvFeeling).
    val assumedHrvScore: Double = HrvFeeling.DEFAULT.score
)
