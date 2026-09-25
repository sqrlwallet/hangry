package com.kevan.hangry.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

@Entity(tableName = "daily_health_summaries")
data class DailyHealthSummaryEntity(
    @PrimaryKey
    val date: LocalDate,
    val sleepDurationMinutes: Int? = null,
    val sleepStartTime: Instant? = null,
    val sleepEndTime: Instant? = null,
    val sleepConsistencyScore: Double? = null,
    /** What the night needed: sleep goal + recent debt + a hard day's extra. */
    val sleepNeedMinutes: Int? = null,
    /** 0-100 Sleep Score (need met + how well you slept), see HangrySleepCalculator. */
    val sleepScore: Int? = null,
    /** 0-100: efficiency, deep + REM and regular timing, regardless of length. */
    val sleepQualityScore: Int? = null,
    val steps: Long? = null,
    val distanceMeters: Double? = null,
    val activeCalories: Double? = null, // workouts in full + every step's cost, see ActiveActivityCalculator
    val totalCalories: Double? = null,
    val bmrCalories: Double? = null, // resting metabolic rate contribution to totalCalories, see CALCULATIONS.md §8
    val exerciseDurationMinutes: Int? = null,
    /** Whole workouts plus 1 minute per 150 steps taken outside them. */
    val activeMinutes: Int? = null,
    val exerciseCount: Int = 0,
    val dailyTrainingLoad: Double? = null,
    val dayStrain: Double? = null, // 0-21 day strain; null when there was no heart rate or workout to go on
    /** Overnight resting heart rate when there's sleep heart rate, else a daytime estimate. */
    val restingHeartRate: Double? = null,
    val averageHeartRate: Double? = null,
    val hrvRmssd: Double? = null,
    val vo2Max: Double? = null,
    val spo2Percentage: Double? = null,
    val respiratoryRate: Double? = null,
    val bloodPressureSystolic: Double? = null,
    val bloodPressureDiastolic: Double? = null,
    val hydrationLiters: Double? = null,
    val bodyFatPercentage: Double? = null,
    val dataCompletenessRatio: Double = 0.0,
    val dataQualityState: String = "COMPLETE",
    val calculationVersion: Int = SUMMARY_CALCULATION_VERSION,
    val lastCalculatedTimestamp: Instant = Instant.now()
)

/**
 * Bump when the way a day's summary is calculated changes, so stored days get rebuilt.
 * 2: active calories/minutes count whole workouts and every step (ActiveActivityCalculator).
 * 3: overnight RHR and HRV, personal strain zones (sleep excluded), sleep need from the goal
 *    and a week of debt, time asleep instead of time in bed.
 */
const val SUMMARY_CALCULATION_VERSION = 3
