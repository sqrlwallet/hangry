package com.kevan.hangry.data.ai

import com.kevan.hangry.data.security.SecureKeyStore
import com.kevan.hangry.domain.ai.AiDefaults
import com.kevan.hangry.domain.ai.PostureAnalyzer
import com.kevan.hangry.domain.ai.extractJsonPayload
import com.kevan.hangry.domain.model.PostureAnalysisResult
import com.kevan.hangry.domain.model.PostureExercise
import com.kevan.hangry.domain.repository.UserProfileRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val SYSTEM_PROMPT = """You are a posture assessment assistant inside a personal fitness app. The user submits 3-5 photos of themselves, shirtless and wearing shorts, standing naturally, for a standing-posture analysis.

First, validate EACH photo in the order given (0-indexed): it must clearly show a person's body, shirtless with the bare torso visible, wearing shorts or similar, standing, and clear/well-lit enough to assess posture. If ANY photo fails this check, respond with ONLY this JSON and nothing else:
{"valid": false, "invalidPhotoIndices": [0-based indices that failed], "reason": "short explanation of what's wrong"}

If ALL photos pass validation, analyze standing posture across the full set: head position, shoulder alignment/rounding, spinal curvature, pelvic tilt, and any visible asymmetry. Respond with ONLY this JSON and nothing else:
{"valid": true, "score": integer 0-100 (100 = ideal posture), "findings": [short strings, each one specific observed issue or strength], "exercises": [{"name": string, "description": string, "targetArea": string, "sets": integer, "reps": string}, 3 to 6 corrective exercises tailored to the findings]}

Never include markdown fences or commentary outside the JSON object."""

@Serializable
private data class PostureResponseJson(
    val valid: Boolean,
    val invalidPhotoIndices: List<Int> = emptyList(),
    val reason: String? = null,
    val score: Int? = null,
    val findings: List<String> = emptyList(),
    val exercises: List<ExerciseJson> = emptyList()
) {
    @Serializable
    data class ExerciseJson(
        val name: String,
        val description: String,
        val targetArea: String,
        val sets: Int,
        val reps: String
    )
}

class OpenRouterPostureAnalyzer(
    private val client: OpenRouterClient,
    private val keyStore: SecureKeyStore,
    private val userProfileRepository: UserProfileRepository
) : PostureAnalyzer {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun analyzeScan(imagesBase64: List<String>): Result<PostureAnalysisResult> {
        val apiKey = keyStore.getApiKey() ?: return Result.failure(OpenRouterException.InvalidApiKey())
        val model = userProfileRepository.getProfileSync()?.preferredAiModel?.takeIf { it.isNotBlank() }
            ?: AiDefaults.DEFAULT_MODEL
        val userText = "Here are ${imagesBase64.size} posture photos, indexed 0 to ${imagesBase64.size - 1} in the order given."

        return client.chatCompletion(apiKey, model, SYSTEM_PROMPT, userText, imagesBase64).mapCatching { raw ->
            val parsed = json.decodeFromString(PostureResponseJson.serializer(), extractJsonPayload(raw))
            PostureAnalysisResult(
                isValid = parsed.valid,
                invalidPhotoIndices = parsed.invalidPhotoIndices,
                rejectionReason = parsed.reason,
                score = parsed.score,
                findings = parsed.findings,
                exercises = parsed.exercises.map {
                    PostureExercise(
                        name = it.name,
                        description = it.description,
                        targetArea = it.targetArea,
                        sets = it.sets,
                        reps = it.reps
                    )
                }
            )
        }.recoverCatching { e ->
            if (e is OpenRouterException) throw e
            throw OpenRouterException.MalformedResponse(e)
        }
    }
}
