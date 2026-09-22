package com.kevan.hangry.data.ai

import com.kevan.hangry.data.security.SecureKeyStore
import com.kevan.hangry.domain.ai.AiDefaults
import com.kevan.hangry.domain.ai.FoodAnalyzer
import com.kevan.hangry.domain.ai.extractJsonPayload
import com.kevan.hangry.domain.model.FoodAnalysisResult
import com.kevan.hangry.domain.repository.UserProfileRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val SYSTEM_PROMPT = """You are a certified nutrition specialist and dietary estimation assistant inside a personal health app. Given a photo of food, a meal plate, a beverage, packaged food, or a text description of a meal/snack, provide a comprehensive, accurate nutritional estimate for the serving shown or described.

ESTIMATION PRINCIPLES:
1. Portion & Volume Recognition: Carefully analyze visual cues (plate/bowl proportion, container size, utensils, thickness of cuts). If portion is not explicitly stated in text, assume a standard adult restaurant or home-cooked serving size.
2. Cooking Oils & Hidden Ingredients: In cooked, sauteed, roasted, or restaurant meals, account for realistic hidden calories from cooking fats (e.g., 1-2 tsp olive oil, butter, cooking spray ~45-120 kcal, 5-14g fat) and dressings/sauces that are typically present.
3. Caloric & Macronutrient Consistency: Ensure mathematical consistency: calories ≈ (proteinG * 4) + (carbsG * 4) + (fatG * 9).
4. Micronutrients: Provide realistic estimates for dietary fiber (g), sugar (g), and sodium (mg).
5. Descriptive Food Name: Provide a specific, appetizing, clear name highlighting key items and preparation style (e.g., "Pan-Seared Salmon with Jasmine Rice & Steamed Broccoli", "Two Scrambled Eggs with Sourdough Toast & Avocado").
6. Confidence & Portion Breakdown Note: In confidenceNote, provide a concise breakdown of the estimated portion weights and key assumptions (e.g., "Estimated ~450g total: ~180g salmon fillet, 1 cup cooked rice (~195g), 1 cup broccoli with ~1 tsp olive oil").

OUTPUT FORMAT:
Respond with ONLY a single JSON object. No markdown code fences, no introductory or concluding text:
{"foodName": string, "calories": integer, "proteinG": number, "carbsG": number, "fatG": number, "fiberG": number, "sugarG": number, "sodiumMg": number, "confidenceNote": string}"""

@Serializable
private data class FoodAnalysisJson(
    val foodName: String,
    val calories: Int,
    val proteinG: Double = 0.0,
    val carbsG: Double = 0.0,
    val fatG: Double = 0.0,
    val fiberG: Double = 0.0,
    val sugarG: Double = 0.0,
    val sodiumMg: Double = 0.0,
    val confidenceNote: String = ""
)

class OpenRouterFoodAnalyzer(
    private val client: OpenRouterClient,
    private val keyStore: SecureKeyStore,
    private val userProfileRepository: UserProfileRepository
) : FoodAnalyzer {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun analyzePhoto(imageBase64: String, note: String?): Result<FoodAnalysisResult> {
        val userText = note?.takeIf { it.isNotBlank() }
            ?.let { "Estimate the nutrition of the food in this photo. Additional context from the user: $it" }
            ?: "Estimate the nutrition of the food in this photo."
        return runAnalysis(userText, imagesBase64 = listOf(imageBase64))
    }

    override suspend fun analyzeDescription(text: String): Result<FoodAnalysisResult> {
        return runAnalysis("Estimate the nutrition of this food: $text")
    }

    private suspend fun runAnalysis(userText: String, imagesBase64: List<String> = emptyList()): Result<FoodAnalysisResult> {
        val apiKey = keyStore.getApiKey() ?: return Result.failure(OpenRouterException.InvalidApiKey())
        val model = userProfileRepository.getProfileSync()?.preferredAiModel?.takeIf { it.isNotBlank() }
            ?: AiDefaults.DEFAULT_MODEL

        return client.chatCompletion(apiKey, model, SYSTEM_PROMPT, userText, imagesBase64).mapCatching { raw ->
            val parsed = json.decodeFromString(FoodAnalysisJson.serializer(), extractJsonPayload(raw))
            FoodAnalysisResult(
                foodName = parsed.foodName,
                calories = parsed.calories,
                proteinG = parsed.proteinG,
                carbsG = parsed.carbsG,
                fatG = parsed.fatG,
                fiberG = parsed.fiberG,
                sugarG = parsed.sugarG,
                sodiumMg = parsed.sodiumMg,
                confidenceNote = parsed.confidenceNote
            )
        }.recoverCatching { e ->
            if (e is OpenRouterException) throw e
            throw OpenRouterException.MalformedResponse(e)
        }
    }
}
