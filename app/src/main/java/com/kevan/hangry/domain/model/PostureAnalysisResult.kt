package com.kevan.hangry.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class PostureExercise(
    val name: String,
    val description: String,
    val targetArea: String,
    val sets: Int,
    val reps: String
)

/**
 * The AI's response to a posture scan submission. [isValid] gates everything else - the model
 * is asked to validate each required photo (person visible, shirtless, shorts, standing, well
 * lit) before it ever produces a score, so a bad photo set never silently gets scored.
 */
data class PostureAnalysisResult(
    val isValid: Boolean,
    val invalidPhotoIndices: List<Int>,
    val rejectionReason: String?,
    val score: Int?,
    val findings: List<String>,
    val exercises: List<PostureExercise>
)
