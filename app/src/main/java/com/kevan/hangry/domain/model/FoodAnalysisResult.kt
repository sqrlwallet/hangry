package com.kevan.hangry.domain.model

/** The AI's estimate of a food photo or description - shown to the user for review/edit before saving. */
data class FoodAnalysisResult(
    val foodName: String,
    val calories: Int,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val fiberG: Double,
    val sugarG: Double,
    val sodiumMg: Double,
    val confidenceNote: String
)
