package com.kevan.hangry.domain.model

enum class BodyFatCategory(val displayName: String, val description: String) {
    ESSENTIAL_FAT("Essential Fat", "Minimum fat required for basic physiological and hormonal functions."),
    ATHLETIC("Athletic", "Lean physique typical of competitive athletes and bodybuilders."),
    FITNESS("Fitness", "Healthy, active physique with visible muscle tone."),
    AVERAGE("Average", "Typical healthy range for general population."),
    ABOVE_AVERAGE("Above Average", "Higher adiposity; focus on progressive recomposition and heart health.")
}

data class BodyFatCalculationResult(
    val bodyFatPercentage: Double,
    val category: BodyFatCategory,
    val fatMassKg: Double?,
    val leanMassKg: Double?,
    val waistToHipRatio: Double?,
    val waistToHeightRatio: Double?,
    val chestToWaistRatio: Double?,
    val methodDescription: String
)

data class BodyFatAnalysisResult(
    val isValid: Boolean,
    val rejectionReason: String? = null,
    val bodyFatPercentage: Double? = null,
    val confidenceRangeMin: Double? = null,
    val confidenceRangeMax: Double? = null,
    val category: BodyFatCategory? = null,
    val leanMassKg: Double? = null,
    val fatMassKg: Double? = null,
    val visualObservations: List<String> = emptyList(),
    val healthInsights: List<String> = emptyList(),
    val circumferenceConsistencyNote: String? = null
)
