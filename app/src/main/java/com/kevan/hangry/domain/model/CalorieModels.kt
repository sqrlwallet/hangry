package com.kevan.hangry.domain.model

/**
 * Mifflin-St Jeor only defines Male/Female coefficients; OTHER averages the two rather than
 * forcing a binary choice, documented as an approximation in CALCULATIONS.md §8.
 */
enum class BiologicalSex {
    MALE,
    FEMALE,
    OTHER
}

data class CalorieBurnResult(
    val bmrCalories: Double?,
    val neatCalories: Double?,
    val exerciseCalories: Double,
    val totalBurnedCalories: Double?,
    val confidence: ScoreConfidence,
    val supportiveNote: String
)

data class CalorieGoalRecommendation(
    val dailyCalorieTarget: Int?,
    val weeklyPaceKg: Double,
    val isPaceAdjustedForSafety: Boolean,
    val guidance: String
)
