package com.kevan.hangry.domain.model

/**
 * How the day's strain value was derived. Zone-based strain (from a continuous heart-rate
 * trace) is far more reliable than a workout-only estimate, so callers should use this to
 * label confidence in the UI rather than presenting both the same way.
 */
enum class StrainSource {
    HEART_RATE_ZONES,
    WORKOUT_ESTIMATE,
    NONE
}

data class StrainResult(
    val dayStrain: Double, // 0.0 to 21.0, saturating scale
    val confidence: ScoreConfidence,
    val source: StrainSource,
    val supportiveNote: String
)

data class StrainRecommendation(
    val targetLow: Double,
    val targetHigh: Double,
    val guidance: String
)
