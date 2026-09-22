package com.kevan.hangry.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "heart_rate_samples",
    indices = [
        Index(value = ["recordFingerprint"], unique = true),
        Index(value = ["timestamp"]),
        Index(value = ["sourceRecordId"]),
        Index(value = ["sourcePackageName"])
    ]
)
data class HeartRateSampleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sourceRecordId: String? = null,
    val sourcePackageName: String? = null,
    val recordFingerprint: String,
    val timestamp: Instant,
    val bpm: Double,
    val dataQualityState: String = "VALID",
    val importTimestamp: Instant = Instant.now()
)
