package com.kevan.hangry.domain.ai

import com.kevan.hangry.domain.model.FoodAnalysisResult

interface FoodAnalyzer {
    suspend fun analyzePhoto(imageBase64: String, note: String?): Result<FoodAnalysisResult>
    suspend fun analyzeDescription(text: String): Result<FoodAnalysisResult>
}
