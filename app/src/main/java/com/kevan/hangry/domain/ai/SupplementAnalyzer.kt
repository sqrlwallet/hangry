package com.kevan.hangry.domain.ai

import com.kevan.hangry.domain.model.SupplementAnalysisResult

interface SupplementAnalyzer {
    /**
     * Reads a supplement from 1-3 photos (front of the bottle, supplement-facts label).
     * [userContext] (pregnancy, conditions, allergies, current supplements) lets the model flag
     * relevant cautions; it never changes the label values.
     */
    suspend fun analyzePhotos(imagesBase64: List<String>, note: String?, userContext: String?): Result<SupplementAnalysisResult>
}
