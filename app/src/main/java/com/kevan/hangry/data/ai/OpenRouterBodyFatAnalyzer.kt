package com.kevan.hangry.data.ai

import com.kevan.hangry.data.security.SecureKeyStore
import com.kevan.hangry.domain.ai.AiDefaults
import com.kevan.hangry.domain.ai.BodyFatAnalyzer
import com.kevan.hangry.domain.ai.extractJsonPayload
import com.kevan.hangry.domain.model.BiologicalSex
import com.kevan.hangry.domain.model.BodyFatAnalysisResult
import com.kevan.hangry.domain.model.BodyFatCategory
import com.kevan.hangry.domain.repository.UserProfileRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val SYSTEM_PROMPT = """You are an expert exercise physiologist, kinanthropometry specialist, and body composition scientist. The user submits 1 to 3 physique photos along with their biometric measurements (height, current weight, age, biological sex, and tape circumferences: neck, chest, waist, hips) to estimate body fat percentage and body composition.

The app guides the user through up to three standard poses (not every photo will be present, and order may vary):
- Front static view (anterior): relaxed, arms at sides - assess overall contour, waist-to-hip shape, abdominal definition, and limb fat.
- Side profile view (lateral): rotated 90 degrees - assess abdominal depth and protrusion, lower-back and glute contour, and front-to-back proportions.
- Back static view (posterior): arms slightly away from the body - assess fat distribution across the upper back, flanks, and lower back, plus muscular definition.
Identify which view each photo shows and combine evidence across all views.

GUARDRAILS & VALIDATION:
1. Normal attire (fitted gym wear, shorts, tank tops, sports bras, swimwear, athletic wear) is completely acceptable and standard for physique assessment. Evaluate subcutaneous fat distribution, muscle definition, and body silhouette.
2. Do NOT reject photos for wearing clothes, ordinary lighting, or informal poses.
3. Only return "valid": false if NO person/physique is visible in any of the photos (e.g. blank wall, pet, furniture, document, or dark blur). If a person is visible, analyze their body composition and return "valid": true.

EVALUATION METHODOLOGY:
- Cross-reference visual indicators with provided measurements:
  * Visual muscle definition (abdominals, serratus, deltoids, back musculature, vascularity, and subcutaneous fat folds).
  * High chest-to-waist ratio (V-taper) and low waist-to-height ratio indicate athletic muscular hypertrophy rather than excess adiposity.
  * Compare visual assessment with the U.S. Navy circumference estimate when provided.
- Category classification (ACE standards):
  * Male: ESSENTIAL_FAT (<6%), ATHLETIC (6-13.9%), FITNESS (14-17.9%), AVERAGE (18-24.9%), ABOVE_AVERAGE (>=25%)
  * Female: ESSENTIAL_FAT (<14%), ATHLETIC (14-20.9%), FITNESS (21-24.9%), AVERAGE (25-31.9%), ABOVE_AVERAGE (>=32%)
- Confidence Range: Provide a realistic +- 1.5% to 3.0% confidence interval (confidenceRangeMin and confidenceRangeMax).
- Actionable Insights: Provide 2-4 visual observations, 2-3 body recomposition/health tips, and a circumferenceConsistencyNote reconciling the visual findings with the tape measurements.

OUTPUT FORMAT:
Respond with ONLY a single JSON object. No markdown code fences, no commentary:
{
  "valid": true,
  "bodyFatPercentage": 14.8,
  "confidenceRangeMin": 13.5,
  "confidenceRangeMax": 16.2,
  "category": "FITNESS",
  "visualObservations": ["Moderate abdominal definition visible", "Well-developed shoulder and chest taper"],
  "healthInsights": ["Current body composition is well within the healthy athletic range", "Slight caloric deficit with resistance training can enhance lower core definition"],
  "circumferenceConsistencyNote": "Visual indicators align closely with the waist-to-neck ratio."
}

If and only if no person/physique is visible:
{"valid": false, "reason": "No person or physique detected in the provided photos"}"""

@Serializable
private data class BodyFatResponseJson(
    val valid: Boolean,
    val reason: String? = null,
    val bodyFatPercentage: Double? = null,
    val confidenceRangeMin: Double? = null,
    val confidenceRangeMax: Double? = null,
    val category: String? = null,
    val visualObservations: List<String> = emptyList(),
    val healthInsights: List<String> = emptyList(),
    val circumferenceConsistencyNote: String? = null
)

class OpenRouterBodyFatAnalyzer(
    private val client: OpenRouterClient,
    private val keyStore: SecureKeyStore,
    private val userProfileRepository: UserProfileRepository
) : BodyFatAnalyzer {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun analyzeBodyFat(
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
    ): Result<BodyFatAnalysisResult> {
        val apiKey = keyStore.getApiKey() ?: return Result.failure(OpenRouterException.InvalidApiKey())
        val model = userProfileRepository.getProfileSync()?.preferredAiModel?.takeIf { it.isNotBlank() }
            ?: AiDefaults.DEFAULT_MODEL

        val biometricsBuilder = StringBuilder("User Biometrics & Circumference Data:\n")
        heightCm?.let { biometricsBuilder.append("- Height: ${it.roundToInt()} cm\n") }
        weightKg?.let { biometricsBuilder.append("- Current Weight: $it kg\n") }
        age?.let { biometricsBuilder.append("- Age: $it years old\n") }
        biologicalSex?.let { biometricsBuilder.append("- Biological Sex: ${it.name}\n") }
        neckCm?.let { biometricsBuilder.append("- Neck Circumference: $it cm\n") }
        chestCm?.let { biometricsBuilder.append("- Chest Circumference: $it cm\n") }
        waistCm?.let { biometricsBuilder.append("- Waist Circumference: $it cm\n") }
        hipCm?.let { biometricsBuilder.append("- Hip Circumference: $it cm\n") }
        calculatedNavyBf?.let { biometricsBuilder.append("- US Navy Circumference Body Fat Estimate: $it%\n") }
        biometricsBuilder.append("\nPlease analyze the ${imagesBase64.size} attached physique photo(s) in conjunction with these metrics.")

        val userText = biometricsBuilder.toString()

        return client.chatCompletion(apiKey, model, SYSTEM_PROMPT, userText, imagesBase64).mapCatching { raw ->
            val parsed = json.decodeFromString(BodyFatResponseJson.serializer(), extractJsonPayload(raw))
            if (!parsed.valid) {
                BodyFatAnalysisResult(
                    isValid = false,
                    rejectionReason = parsed.reason ?: "Unable to analyze body composition from the provided photos."
                )
            } else {
                val bf = parsed.bodyFatPercentage?.let { min(65.0, max(3.0, roundToOneDecimal(it))) }
                val minRange = parsed.confidenceRangeMin?.let { roundToOneDecimal(it) }
                val maxRange = parsed.confidenceRangeMax?.let { roundToOneDecimal(it) }

                val category = parsed.category?.uppercase(Locale.US)?.let { catStr ->
                    runCatching { BodyFatCategory.valueOf(catStr) }.getOrNull()
                } ?: bf?.let { percentage ->
                    // Fallback to algorithmic category if AI returned custom string
                    when (biologicalSex ?: BiologicalSex.OTHER) {
                        BiologicalSex.MALE -> when {
                            percentage < 6.0 -> BodyFatCategory.ESSENTIAL_FAT
                            percentage < 14.0 -> BodyFatCategory.ATHLETIC
                            percentage < 18.0 -> BodyFatCategory.FITNESS
                            percentage < 25.0 -> BodyFatCategory.AVERAGE
                            else -> BodyFatCategory.ABOVE_AVERAGE
                        }
                        BiologicalSex.FEMALE -> when {
                            percentage < 14.0 -> BodyFatCategory.ESSENTIAL_FAT
                            percentage < 21.0 -> BodyFatCategory.ATHLETIC
                            percentage < 25.0 -> BodyFatCategory.FITNESS
                            percentage < 32.0 -> BodyFatCategory.AVERAGE
                            else -> BodyFatCategory.ABOVE_AVERAGE
                        }
                        BiologicalSex.OTHER -> when {
                            percentage < 10.0 -> BodyFatCategory.ESSENTIAL_FAT
                            percentage < 17.5 -> BodyFatCategory.ATHLETIC
                            percentage < 21.5 -> BodyFatCategory.FITNESS
                            percentage < 28.5 -> BodyFatCategory.AVERAGE
                            else -> BodyFatCategory.ABOVE_AVERAGE
                        }
                    }
                }

                val fatMass = if (weightKg != null && bf != null) {
                    roundToOneDecimal(weightKg * (bf / 100.0))
                } else null

                val leanMass = if (weightKg != null && fatMass != null) {
                    roundToOneDecimal(max(0.0, weightKg - fatMass))
                } else null

                BodyFatAnalysisResult(
                    isValid = true,
                    bodyFatPercentage = bf,
                    confidenceRangeMin = minRange,
                    confidenceRangeMax = maxRange,
                    category = category,
                    leanMassKg = leanMass,
                    fatMassKg = fatMass,
                    visualObservations = parsed.visualObservations,
                    healthInsights = parsed.healthInsights,
                    circumferenceConsistencyNote = parsed.circumferenceConsistencyNote
                )
            }
        }.recoverCatching { e ->
            if (e is OpenRouterException) throw e
            throw OpenRouterException.MalformedResponse(e)
        }
    }

    private fun roundToOneDecimal(value: Double): Double {
        return (value * 10.0).roundToInt() / 10.0
    }
}
