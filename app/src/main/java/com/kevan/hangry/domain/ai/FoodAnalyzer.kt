package com.kevan.hangry.domain.ai

import com.kevan.hangry.domain.model.FoodAnalysisResult

interface FoodAnalyzer {
    /** [allergies]: the user's recorded allergies; empty means no allergen check at all. */
    suspend fun analyzePhoto(imageBase64: String, note: String?, allergies: List<String> = emptyList()): Result<FoodAnalysisResult>
    suspend fun analyzeDescription(text: String, allergies: List<String> = emptyList()): Result<FoodAnalysisResult>
}
