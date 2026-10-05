package com.kevan.hangry.data.ai

import com.kevan.hangry.data.security.SecureKeyStore
import com.kevan.hangry.domain.ai.AiDefaults
import com.kevan.hangry.domain.ai.NutritionReviewer
import com.kevan.hangry.domain.ai.extractJsonPayload
import com.kevan.hangry.domain.model.NutritionReview
import com.kevan.hangry.domain.repository.UserProfileRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val SYSTEM_PROMPT = """You are a registered dietitian reviewing one week of a person's food log inside a personal health app. You get their daily targets, per-day totals and the meals they logged.

GOAL: give specific, practical advice they can act on next week - what to add, what to swap, and how to fit it into the meals they already eat. Reference their actual foods by name ("your usual chicken wrap", "the 9pm noodles").

RULES:
1. Ground every point in the data. Don't invent meals they didn't log. If few days are logged, say the picture is partial.
2. Prefer adding and swapping over restricting. Keep advice realistic for their existing habits and cuisine.
3. Fiber matters: always say how to close any fiber gap with specific foods and portions, building up gradually with more water.
4. Never suggest a food containing one of their listed allergies.
5. No diagnoses, no supplements or medication advice, no extreme diets. Keep the tone warm and direct, no lecturing.
6. Keep it tight: headline under 70 characters; overview 2-3 sentences; 1-3 wins; 3-5 recommendations; 3-6 foods to add.

OUTPUT FORMAT:
Respond with ONLY a single JSON object, no markdown fences or extra text:
{"headline": string, "overview": string, "wins": [string], "recommendations": [{"title": string, "why": string, "how": string}], "foodsToAdd": [{"food": string, "why": string}], "fiberPlan": string}
- recommendations.how: concrete steps (which meal, what food, roughly how much).
- fiberPlan: 1-2 sentences on how they specifically reach their fiber target, given what they eat."""

@Serializable
internal data class NutritionReviewJson(
    val headline: String = "",
    val overview: String = "",
    val wins: List<String> = emptyList(),
    val recommendations: List<RecommendationJson> = emptyList(),
    val foodsToAdd: List<FoodToAddJson> = emptyList(),
    val fiberPlan: String = ""
) {
    @Serializable
    data class RecommendationJson(val title: String = "", val why: String = "", val how: String = "")

    @Serializable
    data class FoodToAddJson(val food: String = "", val why: String = "")

    fun toReview() = NutritionReview(
        headline = headline.trim(),
        overview = overview.trim(),
        wins = wins.map { it.trim() }.filter { it.isNotBlank() },
        recommendations = recommendations.filter { it.title.isNotBlank() }
            .map { NutritionReview.Recommendation(it.title.trim(), it.why.trim(), it.how.trim()) },
        foodsToAdd = foodsToAdd.filter { it.food.isNotBlank() }
            .map { NutritionReview.FoodToAdd(it.food.trim(), it.why.trim()) },
        fiberPlan = fiberPlan.trim()
    )
}

class OpenRouterNutritionReviewer(
    private val client: OpenRouterClient,
    private val keyStore: SecureKeyStore,
    private val userProfileRepository: UserProfileRepository
) : NutritionReviewer {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    override suspend fun review(weekText: String): Result<NutritionReview> {
        val apiKey = keyStore.getApiKey() ?: return Result.failure(OpenRouterException.InvalidApiKey())
        val model = AiDefaults.analysisModel(userProfileRepository.getProfileSync()?.preferredAiModel)
        return client.chatCompletion(apiKey, model, SYSTEM_PROMPT, "Review my last week of eating.\n\n$weekText").mapCatching { raw ->
            json.decodeFromString(NutritionReviewJson.serializer(), extractJsonPayload(raw)).toReview()
                .also { if (it.headline.isBlank() && it.recommendations.isEmpty()) throw IllegalStateException("Empty review") }
        }.recoverCatching { e ->
            if (e is OpenRouterException) throw e
            throw OpenRouterException.MalformedResponse(e)
        }
    }
}
