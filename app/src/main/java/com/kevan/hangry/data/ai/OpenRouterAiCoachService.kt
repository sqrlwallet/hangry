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
            You are Hangry's AI Coach, an expert personal health, fitness, and nutrition coach.
            You give actionable, empathetic, scientifically grounded advice personalized to the user.

            Below is the user's health profile, their last 7 days of health metrics (sleep, recovery, strain, workouts, nutrition), and their personal journal of past problems, health notes, and injuries:
            ---
            $context7Days
            ---

            GUIDELINES FOR COACHING:
            1. Ground your answers directly in their data from the last 7 days (e.g. low recovery score, high strain, sleep deficit, calorie deficit/surplus).
            2. Maintain continuity: reference past problems or journal notes when relevant (e.g. adjust exercise recommendations if they have an injury, or consider dietary restrictions when talking about nutrition).
            3. If they ask a general question, synthesize their recent trends to give them specific, tailored recommendations.

            PERSONAL INFORMATION & PROBLEM DETECTION:
            Pay close attention to what the user shares. If the user mentions any personal problem, symptom, injury, health issue, dietary reaction/allergy, sleep struggle, lifestyle habit, workout difficulty, or personal goal (for example: "I have lower back pain", "I am lactose intolerant", "I have trouble falling asleep before 1am", "I feel bloated after whey protein", "I'm training for a marathon"):
            You MUST extract that into a new journal entry so it can be saved in their personal journal and remembered for future days!

            RESPONSE FORMAT:
            You must respond with ONLY a single valid JSON object (no commentary before or after, no markdown fences outside the JSON) matching this schema:
            {
              "reply": "Your coaching answer directly to the user (can use markdown like bullet points, bold text, etc.)",
              "journalEntry": {
                "category": "PROBLEM" | "DIET" | "INJURY" | "HABIT" | "GOAL" | "HEALTH" | "NOTE",
                "summary": "Short title (3-6 words, e.g. 'Lower back pain on squats', 'Lactose intolerance')",
                "content": "Clear, objective description of the problem or personal detail to remember (1-2 sentences)"
              }
            }
            If the user did not share any personal problems, symptoms, health details, dietary restrictions, injuries, or personal notes in their message, set 'journalEntry' to null:
            {
              "reply": "Your coaching answer directly to the user...",
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
            try {
                json.decodeFromString(CoachResponse.serializer(), extractJsonPayload(raw))
            } catch (e: Exception) {
                // Fallback: If model returned conversational text instead of JSON, display it gracefully
                CoachResponse(
                    reply = raw.trim(),
                    journalEntry = null
                )
            }
        }.recoverCatching { e ->
            if (e is OpenRouterException) throw e
            throw OpenRouterException.MalformedResponse(e)
        }
    }
}
