package com.kevan.hangry.domain.model

data class RecoveryResult(
    val score: Int?, // 0 to 100, null if baseline is building (<3 days)
    val confidence: ScoreConfidence,
    val state: RecoveryState,
    val hrvScore: Double? = null,
    val rhrScore: Double? = null,
    val sleepScore: Double? = null,
    val trainingLoadScore: Double? = null,
    val positiveContributors: List<String> = emptyList(),
    val negativeContributors: List<String> = emptyList(),
    val supportiveAdvice: String,
    val baselineDaysCount: Int,
    val algorithmVersion: Int = 1
)
