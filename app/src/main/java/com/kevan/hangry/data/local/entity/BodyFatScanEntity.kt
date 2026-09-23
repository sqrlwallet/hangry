package com.kevan.hangry.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

@Entity(
    tableName = "body_fat_scans",
    indices = [
        Index(value = ["date"]),
        Index(value = ["timestamp"])
    ]
)
data class BodyFatScanEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: LocalDate,
    val timestamp: Instant = Instant.now(),
    val bodyFatPercentage: Double,
    val confidenceMin: Double? = null,
    val confidenceMax: Double? = null,
    val category: String,
    val method: String, // "NAVY_CIRCUMFERENCE" or "AI_MULTIMODAL"
    val weightKg: Double? = null,
    val neckCm: Double? = null,
    val chestCm: Double? = null,
    val waistCm: Double? = null,
    val hipCm: Double? = null,
    val leanMassKg: Double? = null,
    val fatMassKg: Double? = null,
    val visualObservationsJson: String = "[]",
    val healthInsightsJson: String = "[]",
    val consistencyNote: String? = null
)
