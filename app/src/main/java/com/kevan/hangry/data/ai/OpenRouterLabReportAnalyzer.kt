package com.kevan.hangry.data.ai

import com.kevan.hangry.data.security.SecureKeyStore
import com.kevan.hangry.domain.ai.AiDefaults
import com.kevan.hangry.domain.ai.LabReportAnalyzer
import com.kevan.hangry.domain.ai.LabReportParser
import com.kevan.hangry.domain.ai.LabReportScan
import com.kevan.hangry.domain.model.MarkerType
import com.kevan.hangry.domain.repository.UserProfileRepository

internal fun labReportSystemPrompt(markers: List<MarkerType>): String = """You read photos of lab / medical test reports for a personal health-tracking app. Several photos are pages of the same report.

EXTRACTION RULES:
1. List EVERY test result printed, including ones not in the marker list below. Never invent a result or a value you can't read clearly; skip it instead.
2. marker: the matching key from this list, or null when the test isn't one of them. Only map an exact match - e.g. "VLDL", "Non-HDL", ratios, free testosterone and urine glucose are null.
${LabReportParser.markerCatalog(markers)}
3. name: the test name exactly as printed. value: the result exactly as printed, in the printed unit - do NOT convert units. unit: the printed unit, or null.
4. Blood pressure: value is systolic and secondaryValue diastolic. Otherwise secondaryValue is null.
5. fasting: true for a fasting glucose, false for post-meal/random, null if not stated.
6. referenceRange: the printed reference / normal range as text, or null.
7. reportDate: the sample collection date (else the report date) as YYYY-MM-DD, or null. Read day/month order from the report's locale.
8. If the photos aren't a lab or medical report, return an empty results list.

OUTPUT FORMAT:
Respond with ONLY a single JSON object, no markdown fences or extra text:
{"reportDate": string|null, "results": [{"marker": string|null, "name": string, "value": number|string, "secondaryValue": number|null, "unit": string|null, "fasting": boolean|null, "referenceRange": string|null}]}"""

class OpenRouterLabReportAnalyzer(
    private val client: OpenRouterClient,
    private val keyStore: SecureKeyStore,
    private val userProfileRepository: UserProfileRepository
) : LabReportAnalyzer {

    override suspend fun readReport(imagesBase64: List<String>, markers: List<MarkerType>): Result<LabReportScan> {
        val apiKey = keyStore.getApiKey() ?: return Result.failure(OpenRouterException.InvalidApiKey())
        val model = AiDefaults.analysisModel(userProfileRepository.getProfileSync()?.preferredAiModel)
        val userText = "Read every test result from this report (${imagesBase64.size} page${if (imagesBase64.size == 1) "" else "s"})."
        return client.chatCompletion(apiKey, model, labReportSystemPrompt(markers), userText, imagesBase64).mapCatching { raw ->
            LabReportParser.parse(raw, markers.toSet())
        }.recoverCatching { e ->
            if (e is OpenRouterException) throw e
            throw OpenRouterException.MalformedResponse(e)
        }
    }
}
