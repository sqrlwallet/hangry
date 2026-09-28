package com.kevan.hangry.data.ai

import com.kevan.hangry.data.security.SecureKeyStore
import com.kevan.hangry.domain.ai.AiDefaults
import com.kevan.hangry.domain.ai.FoodAnalyzer
import com.kevan.hangry.domain.ai.extractJsonPayload
import com.kevan.hangry.domain.model.FoodAnalysisResult
import com.kevan.hangry.domain.repository.UserProfileRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val SYSTEM_PROMPT = """You are a certified nutrition specialist and dietary estimation assistant inside a personal health app. Given a photo of food, a meal plate, a beverage, packaged food, or a text description of a meal/snack, estimate the nutrition of EXACTLY the amount shown or described - not a typical serving of that dish.

METHOD - work component by component, never from a stock recipe:
1. Identify every separate component on the plate: each food, side, sauce, dressing, topping, garnish and drink. Also list cooking fat as its own component whenever the food is fried, sauteed, roasted or glossy (e.g. "Olive oil (absorbed)", ~5-15g), plus butter, cheese or sauce that is visible or clearly implied.
2. Establish scale from reference objects before judging size: a standard dinner plate is ~26-28cm across, a side plate ~20cm, a cereal bowl ~15cm, a fork ~19cm, a tablespoon ~4cm across the bowl, a 330ml can is 12cm tall, an adult palm is ~9cm wide, a phone ~15cm long. Say which reference you used.
3. Estimate each component's weight in grams from what is actually visible: its footprint (the fraction of the plate it covers), its height/thickness, and its density; or count discrete pieces (e.g. 3 chicken nuggets, 2 slices of bread, 12 fries) and multiply by a typical piece weight. Use the cooked, as-served weight. If the plate is partly eaten, only count what is left. Do not round the portion up or down to a "standard serving" - a small scoop of rice is a small scoop of rice.
4. For each component give nutrition per 100g for that food as prepared (e.g. cooked white rice ~130 kcal/100g, grilled chicken breast ~165 kcal/100g). The app multiplies these by your gram estimate, so the per-100g values must describe the food itself, independent of portion size.
5. Quantities the user states (e.g. "200g chicken", "half portion", "2 eggs", "I ate half") override your visual estimate. Packaged food with a visible label: use the label's values.
6. foodName: a specific, clear name for the whole meal highlighting key items and preparation (e.g. "Pan-Seared Salmon with Jasmine Rice & Steamed Broccoli").
7. portionNote: one short sentence on the scale reference and key assumptions (e.g. "Scaled from a 27cm dinner plate; rice mound ~2cm deep; assumed pan-fried in oil").

OUTPUT FORMAT:
Respond with ONLY a single JSON object. No markdown code fences, no introductory or concluding text:
{"foodName": string, "items": [{"name": string, "grams": number, "per100g": {"calories": number, "proteinG": number, "carbsG": number, "fatG": number, "fiberG": number, "sugarG": number, "sodiumMg": number}}], "portionNote": string}"""

@Serializable
internal data class FoodNutrientsJson(
    val calories: Double = 0.0,
    val proteinG: Double = 0.0,
    val carbsG: Double = 0.0,
    val fatG: Double = 0.0,
    val fiberG: Double = 0.0,
    val sugarG: Double = 0.0,
    val sodiumMg: Double = 0.0
)

@Serializable
internal data class FoodItemJson(
    val name: String,
    val grams: Double,
    val per100g: FoodNutrientsJson = FoodNutrientsJson()
)

@Serializable
internal data class FoodAnalysisJson(
    val foodName: String,
    val items: List<FoodItemJson> = emptyList(),
    val portionNote: String = "",
    // Whole-meal totals: only used when the model skips the itemized breakdown.
    val calories: Int = 0,
    val proteinG: Double = 0.0,
    val carbsG: Double = 0.0,
    val fatG: Double = 0.0,
    val fiberG: Double = 0.0,
    val sugarG: Double = 0.0,
    val sodiumMg: Double = 0.0,
    val confidenceNote: String = "",
    val allergenWarnings: List<String> = emptyList()
)

/**
 * Totals come from summing each component's grams x per-100g values, so the logged numbers scale
 * with the portion actually on the plate rather than whatever the model pictures as "a serving".
 */
internal fun FoodAnalysisJson.toResult(checkAllergens: Boolean): FoodAnalysisResult {
    val warnings = if (checkAllergens) allergenWarnings.filter { it.isNotBlank() } else emptyList()
    val weighed = items.filter { it.grams > 0 }
    if (weighed.isEmpty()) {
        return FoodAnalysisResult(
            foodName = foodName, calories = calories, proteinG = proteinG, carbsG = carbsG, fatG = fatG,
            fiberG = fiberG, sugarG = sugarG, sodiumMg = sodiumMg,
            confidenceNote = confidenceNote.ifBlank { portionNote }, allergenWarnings = warnings
        )
    }
    fun total(nutrient: (FoodNutrientsJson) -> Double) = weighed.sumOf { it.grams / 100.0 * nutrient(it.per100g) }
    fun Double.round1() = Math.round(this * 10) / 10.0
    val breakdown = weighed.joinToString(", ") { "~${Math.round(it.grams)}g ${it.name}" }
    return FoodAnalysisResult(
        foodName = foodName,
        calories = Math.round(total { it.calories }).toInt(),
        proteinG = total { it.proteinG }.round1(),
        carbsG = total { it.carbsG }.round1(),
        fatG = total { it.fatG }.round1(),
        fiberG = total { it.fiberG }.round1(),
        sugarG = total { it.sugarG }.round1(),
        sodiumMg = Math.round(total { it.sodiumMg }).toDouble(),
        confidenceNote = listOf("Estimated ~${Math.round(weighed.sumOf { it.grams })}g total: $breakdown.", portionNote)
            .filter { it.isNotBlank() }.joinToString(" "),
        allergenWarnings = warnings
    )
}

internal fun allergenInstructions(allergies: List<String>): String = """

ALLERGEN CHECK:
The user has these allergies: ${allergies.joinToString(", ")}.
Add "allergenWarnings": a list of short warnings, one per allergy this food likely or possibly contains, naming the allergen and where it's likely hiding (e.g. "Peanuts - satay sauce usually contains peanuts", "Shellfish - possible shrimp in the fried rice"). Include hidden and cross-contamination risks common for the dish. Use an empty list if none apply. Never list allergies that aren't in the user's list."""

class OpenRouterFoodAnalyzer(
    private val client: OpenRouterClient,
    private val keyStore: SecureKeyStore,
    private val userProfileRepository: UserProfileRepository
) : FoodAnalyzer {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun analyzePhoto(imageBase64: String, note: String?, allergies: List<String>): Result<FoodAnalysisResult> {
        val userText = note?.takeIf { it.isNotBlank() }
            ?.let { "Estimate the nutrition of exactly the food in this photo, weighing each component. Additional context from the user: $it" }
            ?: "Estimate the nutrition of exactly the food in this photo, weighing each component."
        return runAnalysis(userText, allergies, imagesBase64 = listOf(imageBase64))
    }

    override suspend fun analyzeDescription(text: String, allergies: List<String>): Result<FoodAnalysisResult> {
        return runAnalysis("Estimate the nutrition of this food: $text", allergies)
    }

    private suspend fun runAnalysis(userText: String, allergies: List<String>, imagesBase64: List<String> = emptyList()): Result<FoodAnalysisResult> {
        val apiKey = keyStore.getApiKey() ?: return Result.failure(OpenRouterException.InvalidApiKey())
        val model = userProfileRepository.getProfileSync()?.preferredAiModel?.takeIf { it.isNotBlank() }
            ?: AiDefaults.DEFAULT_MODEL

        // The allergen check is only asked for when the user has allergies on record.
        val systemPrompt = if (allergies.isEmpty()) SYSTEM_PROMPT else SYSTEM_PROMPT + allergenInstructions(allergies)
        return client.chatCompletion(apiKey, model, systemPrompt, userText, imagesBase64).mapCatching { raw ->
            json.decodeFromString(FoodAnalysisJson.serializer(), extractJsonPayload(raw))
                .toResult(checkAllergens = allergies.isNotEmpty())
        }.recoverCatching { e ->
            if (e is OpenRouterException) throw e
            throw OpenRouterException.MalformedResponse(e)
        }
    }
}
