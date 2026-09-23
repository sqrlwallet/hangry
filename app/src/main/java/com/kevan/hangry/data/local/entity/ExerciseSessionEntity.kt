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
    val importTimestamp: Instant = Instant.now()
)
