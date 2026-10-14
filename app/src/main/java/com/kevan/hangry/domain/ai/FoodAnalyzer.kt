package com.kevan.hangry.domain.ai

import com.kevan.hangry.domain.model.FoodAnalysisResult

interface FoodAnalyzer {
    /** [allergies]: the user's recorded allergies; empty means no allergen check at all. */
    suspend fun analyzePhoto(imageBase64: String, note: String?, allergies: List<String>): Result<FoodAnalysisResult>
    suspend fun analyzeDescription(text: String, allergies: List<String>): Result<FoodAnalysisResult>

    /**
     * Re-estimates a meal that's already logged, from its photo (when it has one) plus the user's
     * [comment] correcting it. [previous] describes what was logged, so the model knows what it's
     * correcting.
     */
    suspend fun refine(imageBase64: String?, previous: String, comment: String, allergies: List<String>): Result<FoodAnalysisResult>
}
