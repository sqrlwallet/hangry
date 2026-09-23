package com.kevan.hangry.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

@Entity(
    tableName = "breathing_sessions",
    indices = [
        Index(value = ["date"]),
        Index(value = ["startTime"])
    ]
)
data class BreathingSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: LocalDate,
    val startTime: Instant,
    val endTime: Instant,
    val patternId: String,
    val durationSeconds: Int,
    val cyclesCompleted: Int,
    /** False when the user ended the session before the planned duration. */
    val completed: Boolean,
    val healthConnectSynced: Boolean = false
)
