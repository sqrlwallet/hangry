package com.kevan.hangry.data.ai

import com.kevan.hangry.data.security.SecureKeyStore
import com.kevan.hangry.domain.ai.AiDefaults
import com.kevan.hangry.domain.ai.SupplementAnalyzer
import com.kevan.hangry.domain.ai.extractJsonPayload
import com.kevan.hangry.domain.model.SupplementAnalysisResult
import com.kevan.hangry.domain.repository.UserProfileRepository
import kotlinx.serialization.json.Json

private const val SYSTEM_PROMPT = """You read dietary supplement packaging for a personal health-tracking app. You get 1-3 photos: usually the front of the bottle/box and the "Supplement Facts" / nutrition label.

EXTRACTION RULES:
1. Read values exactly as printed. Never invent an ingredient or amount you can't see; omit what you can't read.
2. name: the product name as the user would say it (e.g. "Vitamin D3 + K2", "Magnesium Glycinate", "Omega-3 Fish Oil"). brand: the manufacturer if visible.
3. form: one of "capsule", "tablet", "softgel", "gummy", "powder", "liquid", "other".
4. servingAmount / servingUnit: the label's serving size (e.g. 2 and "capsules"; 1 and "scoop"; 5 and "ml").
5. ingredients: every active ingredient per serving with amount, unit (mg, mcg, IU, g, CFU...) and %DV when printed. Skip "other ingredients" fillers.
6. suggestedTiming: when it's commonly best taken, briefly (e.g. "Morning with a meal containing fat", "Evening, 1 hour before bed"), using the label's directions when present.
7. notes: the label's directions and anything useful to know, in 1-2 short sentences.
8. cautions: short, factual flags ONLY when relevant - e.g. a single ingredient well above typical upper limits, duplicates of nutrients the user already takes, or conflicts with pregnancy, allergies or conditions in the user context. Suggest checking with a doctor or pharmacist; never diagnose or tell them to stop a medication. Empty list if nothing applies.
9. If the photos aren't a supplement, return name "Unknown" and put the reason in notes.

OUTPUT FORMAT:
Respond with ONLY a single JSON object, no markdown fences or extra text:
{"name": string, "brand": string|null, "form": string|null, "servingAmount": number|null, "servingUnit": string|null, "ingredients": [{"name": string, "amount": number|null, "unit": string|null, "dailyValuePercent": number|null}], "suggestedTiming": string|null, "notes": string|null, "cautions": [string]}"""

class OpenRouterSupplementAnalyzer(
    private val client: OpenRouterClient,
    private val keyStore: SecureKeyStore,
    private val userProfileRepository: UserProfileRepository
) : SupplementAnalyzer {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    override suspend fun analyzePhotos(imagesBase64: List<String>, note: String?, userContext: String?): Result<SupplementAnalysisResult> {
        val apiKey = keyStore.getApiKey() ?: return Result.failure(OpenRouterException.InvalidApiKey())
        val model = userProfileRepository.getProfileSync()?.preferredAiModel?.takeIf { it.isNotBlank() }
            ?: AiDefaults.DEFAULT_MODEL
        val userText = buildString {
            append("Read this supplement from the photos.")
            note?.takeIf { it.isNotBlank() }?.let { append(" Note from the user: $it") }
            userContext?.takeIf { it.isNotBlank() }?.let { append("\n\nUser context for cautions only:\n$it") }
        }
        return client.chatCompletion(apiKey, model, SYSTEM_PROMPT, userText, imagesBase64).mapCatching { raw ->
            json.decodeFromString(SupplementAnalysisResult.serializer(), extractJsonPayload(raw))
        }.recoverCatching { e ->
            if (e is OpenRouterException) throw e
            throw OpenRouterException.MalformedResponse(e)
        }
    }
}
