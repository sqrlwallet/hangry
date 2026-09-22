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

private const val SYSTEM_PROMPT = """You are an expert biomechanics, physical therapy, and postural assessment specialist. The user submits 1 to 5 photos (which may show front, side, back, or three-quarter views, standing or sitting) for posture evaluation.

GUARDRAILS & VALIDATION:
1. Normal clothing (t-shirts, tank tops, gym clothes, sports bras, shorts, pants, leggings, casual wear) is completely acceptable and expected. Assess posture through the body's natural silhouette, alignment of head/neck, shoulders, spine curvature, pelvis, and limbs.
2. Do NOT reject photos for wearing clothes, ordinary indoor lighting, or informal poses.
3. Only return "valid": false if NO person is visible in any of the photos (e.g. an accidental picture of a blank wall, floor, pet, car, or completely dark image). If at least one photo shows a person, perform the analysis and return "valid": true.

ANALYSIS GUIDELINES:
- Score (0-100):
  * 90-100: Near-ideal posture with excellent vertical alignment and balance.
  * 75-89: Good posture with minor everyday muscular imbalances (e.g., mild forward head or slight shoulder rounding).
  * 55-74: Moderate postural deviations (e.g., noticeable forward head carriage, rounded shoulders/thoracic kyphosis, anterior pelvic tilt, uneven shoulder height).
  * Below 55: Significant postural imbalances or asymmetrical compensatory patterns.
- Findings: Provide 2 to 5 concise, constructive, actionable observations on cranio-cervical alignment, shoulder and scapular position, thoracic/lumbar spine curves, pelvic tilt, or left/right symmetry.
- Exercises: Provide 3 to 5 targeted, highly effective corrective exercises or stretches tailored specifically to the findings:
  * name: Standard exercise name (e.g., "Chin Tucks", "Doorway Chest Stretch", "Prone Y-T-W Raises", "Glute Bridges with Pelvic Tilt", "Thoracic Spine Foam Rolling", "Deadbugs").
  * description: Clear, practical form cues and coaching instructions on how to perform it safely.
  * targetArea: Muscle group or joint focus (e.g., "Deep Neck Flexors", "Chest & Anterior Deltoids", "Rhomboids & Lower Trapezius", "Core & Hip Flexors").
  * sets: Integer (e.g. 2 or 3).
  * reps: Prescription string (e.g. "10-12 reps", "30-second hold", "8 reps each side").

OUTPUT FORMAT:
Respond with ONLY a single JSON object. No markdown code fences, no commentary:
{"valid": true, "score": 82, "findings": ["...", "..."], "exercises": [{"name": "...", "description": "...", "targetArea": "...", "sets": 3, "reps": "..."}]}

If and only if no person is visible in the photos:
{"valid": false, "invalidPhotoIndices": [0], "reason": "No person detected in the photos"}"""

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
        val userText = if (imagesBase64.size == 1) {
            "Here is 1 posture photo for evaluation."
        } else {
            "Here are ${imagesBase64.size} posture photos, indexed 0 to ${imagesBase64.size - 1} in the order given, for evaluation."
        }

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
