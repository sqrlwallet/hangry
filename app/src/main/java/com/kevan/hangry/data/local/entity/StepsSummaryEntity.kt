package com.kevan.hangry.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

@Entity(
    tableName = "steps_summaries",
    indices = [
        Index(value = ["recordFingerprint"], unique = true),
        Index(value = ["recordDate"]),
        Index(value = ["sourceRecordId"]),
        Index(value = ["sourcePackageName"])
    ]
)
data class StepsSummaryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sourceRecordId: String? = null,
    val sourcePackageName: String? = null,
    val recordFingerprint: String,
    val recordDate: LocalDate,
    val stepCount: Long,
    val distanceMeters: Double? = null,
    val activeCalories: Double? = null,
    val dataQualityState: String = "VALID",
    val importTimestamp: Instant = Instant.now()
)
