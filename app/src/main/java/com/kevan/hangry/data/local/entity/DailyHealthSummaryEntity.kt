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
    val steps: Long? = null,
    val distanceMeters: Double? = null,
    val activeCalories: Double? = null,
    val totalCalories: Double? = null,
    val bmrCalories: Double? = null, // resting metabolic rate contribution to totalCalories, see CALCULATIONS.md §8
    val exerciseDurationMinutes: Int? = null,
    val exerciseCount: Int = 0,
    val dailyTrainingLoad: Double? = null,
    val dayStrain: Double? = null, // 0-21 bounded day-strain, see CALCULATIONS.md §6
    val restingHeartRate: Double? = null,
    val averageHeartRate: Double? = null,
    val hrvRmssd: Double? = null,
    val vo2Max: Double? = null,
    val spo2Percentage: Double? = null,
    val respiratoryRate: Double? = null,
    val bloodPressureSystolic: Double? = null,
    val bloodPressureDiastolic: Double? = null,
    val dataCompletenessRatio: Double = 0.0,
    val dataQualityState: String = "COMPLETE",
    val calculationVersion: Int = 1,
    val lastCalculatedTimestamp: Instant = Instant.now()
)
