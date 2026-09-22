package com.kevan.hangry.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "sleep_sessions",
    indices = [
        Index(value = ["recordFingerprint"], unique = true),
        Index(value = ["startTime", "endTime"]),
        Index(value = ["sourceRecordId"]),
        Index(value = ["sourcePackageName"])
    ]
)
data class SleepSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sourceRecordId: String? = null,
    val sourcePackageName: String? = null,
    val recordFingerprint: String,
    val startTime: Instant,
    val endTime: Instant,
    val durationMinutes: Int,
    val timeInBedMinutes: Int? = null,
    val deepSleepMinutes: Int? = null,
    val remSleepMinutes: Int? = null,
    val lightSleepMinutes: Int? = null,
    val awakeMinutes: Int? = null,
    val sleepQualityScore: Int? = null,
    val isManualEntry: Boolean = false,
    val timeZoneOffset: String? = null,
    val dataQualityState: String = "VALID",
    val importTimestamp: Instant = Instant.now()
)
