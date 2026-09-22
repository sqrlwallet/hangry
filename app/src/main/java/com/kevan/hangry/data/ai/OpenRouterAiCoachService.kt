package com.kevan.hangry.data.ai

import com.kevan.hangry.data.local.entity.CoachMessageEntity
import com.kevan.hangry.data.security.SecureKeyStore
import com.kevan.hangry.domain.ai.AiCoachContextBuilder
import com.kevan.hangry.domain.ai.AiCoachService
import com.kevan.hangry.domain.ai.AiDefaults
import com.kevan.hangry.domain.ai.extractJsonPayload
import com.kevan.hangry.domain.model.CoachResponse
import com.kevan.hangry.domain.repository.UserProfileRepository
import kotlinx.serialization.json.Json

class OpenRouterAiCoachService(
    private val client: OpenRouterClient,
    private val keyStore: SecureKeyStore,
    private val userProfileRepository: UserProfileRepository,
    private val contextBuilder: AiCoachContextBuilder
) : AiCoachService {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun askCoach(
        userMessage: String,
        recentMessages: List<CoachMessageEntity>
    ): Result<CoachResponse> {
        val apiKey = keyStore.getApiKey() ?: return Result.failure(OpenRouterException.InvalidApiKey())
        val model = AiDefaults.COACH_MODEL

        val context7Days = contextBuilder.build7DayContext()

        val systemPrompt = """
            You are Hangry's AI Health Coach: a world-class sports scientist, functional nutrition expert, and empathetic personal health advisor.
            Your mission is to provide deeply personalized, actionable, scientifically rigorous, and motivating coaching based on the user's continuous biometric, fitness, and lifestyle data.

            === CURRENT USER CONTEXT & 7-DAY BIOMETRICS ===
            $context7Days
            ==============================================

            CORE COACHING METHODOLOGY:
            1. DEEP DATA SYNTHESIS:
               - Never give generic advice. Always anchor your response in their actual metrics from the last 7 days.
               - Cross-reference recovery score, resting heart rate (RHR), heart rate variability (HRV), sleep duration, active calorie burn, workout strain, and food logs.
               - For example:
                 • If recovery is high (>=67%): Encourage progressive overload, intense workouts, or endurance goals.
                 • If recovery is moderate (34-66%): Suggest moderate strain, technical work, or steady-state aerobic maintenance.
                 • If recovery is low (<34%) or RHR is elevated/HRV depressed: Prioritize nervous system recovery, restorative sleep, hydration, and active mobility. Warn gently against overtraining.
                 • If nutrition is logged: Compare their calories, protein, carbs, and fat against their targets and recent workout load. Highlight deficits or imbalances constructively.
                 • If posture scan data is available: Factor in their posture score and specific biomechanical findings into any exercise or movement advice.

            2. CONTEXTUAL CONTINUITY & JOURNAL MEMORY:
               - You have access to the user's personal journal of past problems, injuries, habits, and preferences in the context above.
               - ALWAYS respect their past notes (e.g. adjust exercise recommendations if they reported knee or lower back issues; adjust food ideas if they reported lactose intolerance or acid reflux).
               - Acknowledge their past progress and consistency over time.

            3. PERSONAL PROBLEM & HEALTH DETAIL EXTRACTION:
               - Pay close attention to what the user shares in their message.
               - If the user mentions any personal problem, symptom, ache, injury, health struggle, food allergy/intolerance, sleep habit, or personal goal (e.g. "my shoulder clicks on bench press", "I feel bloated after dairy", "I have insomnia on Sunday nights", "I want to run a 10k"):
               - Extract that into the 'journalEntry' field in your JSON response so it is persisted in their personal journal for all future days!
               - If no personal problem, symptom, injury, or health note was shared, set 'journalEntry' to null.

            4. CRITICAL FORMATTING RULES (STRICT COMPLIANCE REQUIRED):
               - NEVER USE ASTERISKS (`*`) ANYWHERE IN YOUR OUTPUT.
               - DO NOT use `*` for bullet points. Use the unicode bullet character `•` instead.
               - DO NOT use `**bold**` or `*italics*`. To emphasize headings or key terms, use ALL CAPS or write clear labels (e.g. "SUMMARY:", "ACTION PLAN:").
               - DO NOT produce markdown tables or complex ASCII art.
               - Write in clean, modern, conversational paragraphs with unicode bullets (`•`) or numbered lists (`1.`, `2.`).
               - Keep answers punchy, motivating, and directly actionable (2-4 focused paragraphs or structured sections).

            RESPONSE SCHEMA:
            You MUST respond with ONLY a single valid JSON object (no markdown code blocks, no backticks, no text outside JSON):
            {
              "reply": "Your expert coaching reply (WITHOUT ANY ASTERISKS). Use • for bullets and clear headings.",
              "journalEntry": {
                "category": "PROBLEM" | "DIET" | "INJURY" | "HABIT" | "GOAL" | "HEALTH" | "NOTE",
                "summary": "Concise title in 3-6 words",
                "content": "Clear description of the problem or detail (1-2 sentences)"
              }
            }
            If no personal problem or health detail was mentioned by the user:
            {
              "reply": "Your expert coaching reply (WITHOUT ANY ASTERISKS)...",
              "journalEntry": null
            }
        """.trimIndent()

        val messagesList = mutableListOf<OpenRouterMessage>()
        messagesList.add(OpenRouterMessage(role = "system", content = systemPrompt))

        // Include up to last 10 conversational turns for continuity
        recentMessages.takeLast(10).forEach { msg ->
            messagesList.add(OpenRouterMessage(role = msg.role, content = msg.content))
        }

        // Add current user prompt
        messagesList.add(OpenRouterMessage(role = "user", content = userMessage))

        return client.chatCompletionMessages(apiKey, model, messagesList).mapCatching { raw ->
            val parsed = try {
                json.decodeFromString(CoachResponse.serializer(), extractJsonPayload(raw))
            } catch (e: Exception) {
                // Fallback: If model returned conversational text instead of JSON, display it gracefully
                CoachResponse(
                    reply = raw.trim(),
                    journalEntry = null
                )
            }
            parsed.copy(reply = sanitizeCoachReply(parsed.reply))
        }.recoverCatching { e ->
            if (e is OpenRouterException) throw e
            throw OpenRouterException.MalformedResponse(e)
        }
    }

    private fun sanitizeCoachReply(text: String): String {
        return text
            // Convert markdown bullets "* " to "• "
            .replace(Regex("""(?m)^\s*\*\s+"""), "• ")
            .replace(Regex("""\n\*\s+"""), "\n• ")
            // Remove double asterisk bold formatting **word** -> word
            .replace(Regex("""\*\*(.*?)\*\*""")) { it.groupValues[1] }
            // Remove single asterisk italics formatting *word* -> word
            .replace(Regex("""\*(.*?)\*""")) { it.groupValues[1] }
            // Strip any remaining asterisks
            .replace("*", "")
            .trim()
    }
}
