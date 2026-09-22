package com.kevan.hangry.domain.model

enum class StressLevel {
    LOW,
    MODERATE,
    ELEVATED,
    HIGH,
    BUILDING_BASELINE
}

data class StressResult(
    val score: Int, // 0-100 score calculated from data
    val level: StressLevel,
    val hrvDeviationRatio: Double?, // today / 7d baseline
    val rhrDeviationBpm: Double?, // today - 7d baseline
    val summaryText: String,
    val supportiveAdvice: String,
    val confidence: String // "LOW", "MEDIUM", "HIGH"
)
