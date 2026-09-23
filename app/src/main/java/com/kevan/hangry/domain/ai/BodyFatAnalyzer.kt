package com.kevan.hangry.domain.ai

import com.kevan.hangry.domain.model.BiologicalSex
import com.kevan.hangry.domain.model.BodyFatAnalysisResult

interface BodyFatAnalyzer {
    suspend fun analyzeBodyFat(
        imagesBase64: List<String>,
        heightCm: Double?,
        weightKg: Double?,
        age: Int?,
        biologicalSex: BiologicalSex?,
        neckCm: Double?,
        chestCm: Double?,
        waistCm: Double?,
        hipCm: Double?,
        calculatedNavyBf: Double?
    ): Result<BodyFatAnalysisResult>
}
