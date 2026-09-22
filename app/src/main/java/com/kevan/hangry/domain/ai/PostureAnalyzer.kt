package com.kevan.hangry.domain.ai

import com.kevan.hangry.domain.model.PostureAnalysisResult

interface PostureAnalyzer {
    /** One call analyzes the whole photo set - validates each photo before ever producing a score. */
    suspend fun analyzeScan(imagesBase64: List<String>): Result<PostureAnalysisResult>
}
