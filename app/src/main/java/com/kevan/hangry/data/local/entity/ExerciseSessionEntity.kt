package com.kevan.hangry.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "exercise_sessions",
    indices = [
        Index(value = ["recordFingerprint"], unique = true),
        Index(value = ["startTime", "endTime"]),
        Index(value = ["sourceRecordId"]),
        Index(value = ["sourcePackageName"])
    ]
)
data class ExerciseSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sourceRecordId: String? = null,
    val sourcePackageName: String? = null,
    val recordFingerprint: String,
    /** Health Connect's exercise type name, e.g. "WEIGHTLIFTING" - see WorkoutType. */
    val exerciseType: String,
    val title: String? = null,
    val startTime: Instant,
    val endTime: Instant,
    val durationMinutes: Int,
    val activeCalories: Double? = null,
    val totalCalories: Double? = null,
    /** Steps recorded during the session - subtracted from daily steps so they aren't also counted as NEAT. */
    val steps: Long? = null,
    val estimatedTrainingLoad: Double? = null,
    val dataQualityState: String = "VALID",
    val importTimestamp: Instant = Instant.now(),
    val notes: String? = null,
    val distanceMeters: Double? = null,
    val elevationGainMeters: Double? = null,
    val avgHeartRate: Double? = null,
    val maxHeartRate: Double? = null,
    val avgPowerWatts: Double? = null,
    /** Strength sets (exercise segments, not counting rests). */
    val setCount: Int? = null,
    val repCount: Int? = null,
    /** e.g. "Bench press 3×10 · Squat 4×8". */
    val segmentSummary: String? = null,
    val lapCount: Int? = null,
    /** Which version of the Health Connect import filled this row - older rows get re-read. */
    val detailVersion: Int = 0
)

/**
 * Bump when the workout import reads more from Health Connect, so saved workouts are re-read.
 * 1: every exercise type, plus distance, elevation, heart rate, power, sets/reps, laps and notes.
 */
const val WORKOUT_DETAIL_VERSION = 1
