package com.kevan.hangry.domain.ai

import com.kevan.hangry.domain.model.NutritionReview

interface NutritionReviewer {
    /** [weekText] is the week's summary and meal list; the result is shown as the weekly review. */
    suspend fun review(weekText: String): Result<NutritionReview>
}
