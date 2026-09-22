package com.kevan.hangry.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

@Entity(tableName = "recovery_scores")
data class RecoveryScoreEntity(
    @PrimaryKey
    val date: LocalDate,
    val score: Int?, // 0-100, null if building baseline (<3 days)
    val confidence: String, // "LOW", "MEDIUM", "HIGH"
    val state: String, // "PRIMED", "BALANCED", "REBUILD", "BUILDING_BASELINE"
    val algorithmVersion: Int = 1,
    val baselineWindowDays: Int = 7,
    val hrvComponentScore: Double? = null,
    val rhrComponentScore: Double? = null,
    val sleepComponentScore: Double? = null,
    val trainingLoadComponentScore: Double? = null,
    val positiveContributors: String = "[]", // Serialized JSON list
    val negativeContributors: String = "[]", // Serialized JSON list
    val supportiveAdvice: String,
    val calculatedAt: Instant = Instant.now()
)
