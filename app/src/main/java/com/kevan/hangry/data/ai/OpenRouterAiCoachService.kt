package com.kevan.hangry.data.ai

import com.kevan.hangry.data.local.entity.CoachMessageEntity
import com.kevan.hangry.data.security.SecureKeyStore
import com.kevan.hangry.domain.ai.AiCoachContextBuilder
import com.kevan.hangry.domain.ai.AiCoachService
import com.kevan.hangry.domain.ai.AiDefaults
import com.kevan.hangry.domain.ai.StreamingReply
import com.kevan.hangry.domain.ai.extractJsonPayload
import com.kevan.hangry.domain.model.CoachAction
import com.kevan.hangry.domain.model.CoachActionStatus
import com.kevan.hangry.domain.model.CoachResponse
import com.kevan.hangry.domain.model.ExtractedJournalEntry
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import com.kevan.hangry.domain.repository.UserProfileRepository
import kotlinx.serialization.json.Json

class OpenRouterAiCoachService(
    private val client: OpenRouterClient,
    private val keyStore: SecureKeyStore,
    private val userProfileRepository: UserProfileRepository,
    private val contextBuilder: AiCoachContextBuilder
) : AiCoachService {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    override suspend fun askCoach(
        userMessage: String,
        recentMessages: List<CoachMessageEntity>,
        imagesBase64: List<String>,
        onPartialReply: ((String) -> Unit)?
    ): Result<CoachResponse> {
        val apiKey = keyStore.getApiKey() ?: return Result.failure(OpenRouterException.InvalidApiKey())
        val model = userProfileRepository.getProfileSync()
            ?.preferredCoachModel?.takeIf { it.isNotBlank() }
            ?: AiDefaults.COACH_MODEL

        val context7Days = contextBuilder.build7DayContext()

        val systemPrompt = """
            You are Dash, the voice of Hangry's "Ask Dash" chat and the app's mascot: a cheerful little red fox in a blue-and-white striped scarf, with the brain of a world-class sports scientist, functional nutrition expert, and empathetic personal health advisor.
            Your mission is to provide deeply personalized, actionable, scientifically rigorous, and motivating coaching based on the user's continuous biometric, fitness, and lifestyle data.

            DASH'S PERSONALITY:
            - Speak in the first person as Dash. Refer to yourself only as Dash - never as an "AI coach" or "AI Health Coach". Warm, upbeat, encouraging, and a little playful, like a supportive friend who genuinely knows their stuff.
            - A light touch of fox charm is welcome (an occasional "tail-wagging" win or "let me sniff through your data"), but at most once per reply and never when the user is discussing pain, injury, illness, or anything serious. In those moments be calm, caring, and clear.
            - Personality never replaces substance: every reply must still be precise, data-driven, and actionable.

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

            4. HEALTH RECORDS, SAFETY & MEDICAL BOUNDARIES:
               - The context may include blood pressure, blood sugar, HbA1c, cholesterol, testosterone, allergies, conditions, pregnancy and menstrual cycle data. Use them to personalise advice and to track progress toward the user's own goals.
               - You are not a doctor and this is not medical advice. Never diagnose, never tell the user to start, stop or change medication, and never interpret a result as a definitive medical finding. Describe where a number sits against general reference ranges, what lifestyle factors commonly influence it, and when it's worth discussing with a clinician.
               - If a reading is in a crisis range (e.g. blood pressure above 180/120, very low or very high blood sugar) or the user describes urgent symptoms, calmly advise seeking medical care promptly before anything else.
               - ALWAYS respect allergies in any food suggestion, and conditions in any exercise or nutrition suggestion.
               - If the user is pregnant, keep all exercise, nutrition and supplement suggestions pregnancy-appropriate and suggest checking changes with their midwife or doctor; don't recommend calorie deficits for weight loss.
               - Hangry is an open-source app that keeps this data on the user's phone for their own tracking.

            5. PHOTOS & IN-APP ACTIONS:
               - The user may attach photos: supplement bottles/labels, meals, lab reports, blood pressure monitor screens, etc. Read them carefully and only use values you can actually see.
               - You can propose in-app actions in the "actions" array. The app shows each as a card and the user taps to confirm - you never change anything yourself. So say "Tap Add below to save it", never "I've added it".
               - Propose an action when the user asks you to add, log, save, track or remind them of something, or clearly implies it (e.g. sends a supplement photo saying "I take this every morning", or a lab report saying "save these"). Don't propose actions for general questions.
               - Supplements: adding one is context for you, not a request to be tracked. Untracked supplements are assumed taken as usual - never ask whether they took them or nag about them. Set "track": true only when the user asks for help tracking, remembering or reminders.
               - Fasting: only when the context shows INTERMITTENT FASTING, the user turned it on - time meal and workout advice around their eating window and cheer their streak. Never push fasting on anyone who hasn't turned it on, and if they're pregnant, under 18 or mention an eating disorder, gently suggest checking with their doctor rather than encouraging fasts.
               - One action per item: a lab report with LDL, HDL and triglycerides becomes three ADD_READING actions.
               - If something needed is missing (e.g. what time they take a supplement), make a sensible default, say what you assumed in the reply, and mention they can edit it later.
               - Action types and payloads (these cover everything a user can enter in Hangry - if they want to record or change something, there's an action for it):
$DASH_ACTION_GUIDE
               - Every action needs "type" and a short "title" for its card, e.g. "Add Vitamin D3 · 1 softgel at 8:00 AM".
               - Supplement cautions: if a new supplement overlaps with one they already take, exceeds common upper limits, or conflicts with pregnancy, allergies or conditions, say so plainly and suggest checking with a pharmacist or doctor.

            6. CRITICAL FORMATTING RULES (STRICT COMPLIANCE REQUIRED):
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
              },
              "actions": [ { "type": "ADD_SUPPLEMENT", "title": "...", "supplement": { ... } } ]
            }
            "actions" is an empty array when you aren't proposing anything.
            If no personal problem or health detail was mentioned by the user:
            {
              "reply": "Your expert coaching reply (WITHOUT ANY ASTERISKS)...",
              "journalEntry": null,
              "actions": []
            }
        """.trimIndent()

        val messagesList = mutableListOf<OpenRouterMessage>()
        messagesList.add(OpenRouterMessage(role = "system", content = systemPrompt))

        // Include up to last 10 conversational turns for continuity
        // Earlier photos aren't re-sent (cost); a note keeps the thread readable, and past
        // action outcomes let Dash know what the user actually saved.
        recentMessages.takeLast(10).forEach { msg ->
            val photoCount = msg.imagePaths?.split(',')?.count { it.isNotBlank() } ?: 0
            val actions = msg.actionsJson?.let { raw ->
                runCatching { json.decodeFromString(ListSerializer(CoachAction.serializer()), raw) }.getOrNull()
            }.orEmpty()
            val content = buildString {
                append(msg.content)
                if (photoCount > 0) append("\n[attached $photoCount photo(s)]")
                if (actions.isNotEmpty()) {
                    append("\n[proposed actions: " + actions.joinToString("; ") { "${it.title} - ${it.status.lowercase()}" } + "]")
                }
            }
            messagesList.add(OpenRouterMessage(role = msg.role, content = content))
        }

        // Add current user prompt, with any photos attached to it
        messagesList.add(OpenRouterMessage(role = "user", content = userMessage, imagesBase64 = imagesBase64))

        val completion = if (onPartialReply == null) {
            client.chatCompletionMessages(apiKey, model, messagesList)
        } else {
            val soFar = StringBuilder()
            var shown = ""
            client.streamChatCompletionMessages(apiKey, model, messagesList) { delta ->
                soFar.append(delta)
                val visible = StreamingReply.visibleText(soFar.toString())
                if (visible != shown) {
                    shown = visible
                    onPartialReply(visible)
                }
            }
        }
        return completion.mapCatching { raw ->
            val parsed = parseCoachResponse(raw)
            parsed.copy(
                reply = sanitizeCoachReply(parsed.reply),
                // Unknown types can't be executed, so they're dropped rather than shown.
                actions = parsed.actions.filter { it.type in CoachAction.ALL }.map { it.copy(status = CoachActionStatus.PENDING) }
            )
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

private val coachJson = Json { ignoreUnknownKeys = true; coerceInputValues = true }

/**
 * Field by field, so one malformed action or journal entry can't turn the whole reply into
 * raw JSON on screen. Plain-text replies (model ignored the schema) are shown as-is.
 */
internal fun parseCoachResponse(raw: String): CoachResponse {
    val obj = runCatching { coachJson.parseToJsonElement(extractJsonPayload(raw)).jsonObject }.getOrNull()
        ?: return CoachResponse(reply = raw.trim())
    val reply = (obj["reply"] as? JsonPrimitive)?.contentOrNull ?: return CoachResponse(reply = raw.trim())
    val journal = obj["journalEntry"]?.takeIf { it !is JsonNull }?.let {
        runCatching { coachJson.decodeFromJsonElement(ExtractedJournalEntry.serializer(), it) }.getOrNull()
    }
    val actions = (obj["actions"] as? JsonArray).orEmpty().mapNotNull {
        runCatching { coachJson.decodeFromJsonElement(CoachAction.serializer(), it) }.getOrNull()
    }
    return CoachResponse(reply = reply, journalEntry = journal, actions = actions)
}

/**
 * Every action Dash can propose, with its payload. One entry per CoachAction type - a unit test
 * checks nothing in CoachAction.ALL is missing, so new data-entry points can't be forgotten here.
 */
internal val DASH_ACTION_GUIDE = listOf(
    "ADD_SUPPLEMENT" to "\"supplement\": {\"name\", \"brand\", \"form\" (capsule|tablet|softgel|gummy|powder|liquid|other), \"doseAmount\" (units per dose), \"doseUnit\" (e.g. \"capsules\"), \"times\" ([\"HH:mm\"] 24-hour, when they usually take it), \"track\" (true ONLY if they ask you to help them track, remember or remind them; default false = context only, assumed taken), \"reminders\" (true/false, only used when tracked),\"ingredients\" ([{\"name\",\"amount\",\"unit\",\"dailyValuePercent\"}]), \"notes\"}",
    "UPDATE_SUPPLEMENT" to "\"supplementName\" (one they already take) + \"supplementUpdate\": {\"doseAmount\", \"doseUnit\", \"times\" ([\"HH:mm\"]), \"track\" (true when they ask for help tracking or reminders, false to stop), \"reminders\", \"active\" (false pauses it), \"notes\"} - only what changes",
    "MARK_SUPPLEMENT_TAKEN" to "\"supplementName\" (a tracked one - untracked supplements already count as taken)",
    "LOG_MEAL" to "\"meal\": {\"foodName\", \"calories\" (integer), \"proteinG\", \"carbsG\", \"fatG\", \"fiberG\", \"sugarG\", \"sodiumMg\"} - estimate like a nutrition specialist, including hidden oils",
    "UPDATE_MEAL" to "\"mealName\" (a meal logged in the last 3 days) + \"meal\": the full corrected values (same fields as LOG_MEAL)",
    "ADD_MEAL_PLAN" to "\"mealPlan\": {\"name\", \"mealType\" (BREAKFAST|LUNCH|DINNER|SNACK|OTHER), \"calories\", \"proteinG\", \"carbsG\", \"fatG\"} - a saved meal they eat often, for one-tap logging",
    "ADD_READING" to "\"reading\": {\"marker\" (blood_pressure|blood_glucose|hba1c|total_cholesterol|ldl|hdl|triglycerides|testosterone - testosterone only if their sex is male), \"value\" (systolic for blood pressure), \"secondaryValue\" (diastolic, blood pressure only), \"unit\" (mmHg|mg/dL|mmol/L|%|ng/dL|nmol/L), \"context\" (FASTING|BEFORE_MEAL|AFTER_MEAL|RANDOM, blood sugar only), \"date\" (\"YYYY-MM-DD\", from the report if shown)}",
    "SET_GOAL" to "\"goal\": {\"marker\", \"targetValue\", \"targetSecondary\" (diastolic, blood pressure only), \"unit\", \"targetDate\" (\"YYYY-MM-DD\" or null)}",
    "ADD_ALLERGY" to "\"item\": {\"name\", \"note\" (e.g. the reaction)}",
    "ADD_CONDITION" to "\"item\": {\"name\", \"note\"}",
    "SET_PREGNANCY" to "\"pregnancy\": {\"pregnant\" (true/false), \"dueDate\" (\"YYYY-MM-DD\")} - only if their sex is female",
    "LOG_PERIOD" to "\"period\": {\"startDate\" (\"YYYY-MM-DD\"), \"endDate\" (or null if ongoing)} - only if their sex is female",
    "UPDATE_GOALS" to "\"goals\": {\"weightGoalKg\", \"goalDate\" (\"YYYY-MM-DD\"), \"dailySteps\", \"dailyActiveCalories\", \"sleepHours\"} - only what changes",
    "UPDATE_PROFILE" to "\"profile\": {\"age\", \"sex\" (MALE|FEMALE|OTHER), \"height\", \"neck\", \"chest\", \"waist\", \"hip\", \"lengthUnit\" (cm|in)} - only what changes; tape measurements unlock more body metrics",
    "LOG_WEIGHT" to "\"weight\": {\"value\", \"unit\" (kg|lb)}",
    "LOG_BODY_FAT" to "\"bodyFat\": {\"percentage\", \"source\" (e.g. \"DEXA scan\", \"smart scale\")} - a measured body fat % they tell you or show you",
    "LOG_SLEEP" to "\"sleep\": {\"durationMinutes\", \"endTime\" (\"YYYY-MM-DDTHH:mm\" wake time, or null for now)} - when their device missed a night",
    "SET_HRV_FEELING" to "\"feeling\" (EXCELLENT|GOOD|OKAY|TIRED|DRAINED) - how they feel today; only changes recovery on days their device didn't report HRV",
    "OPEN_SCREEN" to "\"screen\" (breathing|supplements|fasting|programs|health_records|body_metrics|body_fat|nutrition|sleep|recovery|heart|training|trends|posture|settings), plus \"breathingPattern\" (box_4|bpm_6|bpm_5|bpm_3) for breathing - e.g. offer a 6 BPM session when they're stressed before bed",
    "LOG_WORKOUT" to "\"workout\": {\"exerciseType\" (e.g. \"RUNNING\", \"WEIGHTLIFTING\", \"WALKING\", \"SWIMMING\", \"CYCLING\", \"YOGA\"), \"title\", \"durationMinutes\", \"calories\" (kcal), \"distanceKm\", \"startTime\" (\"YYYY-MM-DDTHH:mm\" or null for ending now), \"notes\"} - log an activity they did without a wearable",
    "RESOLVE_JOURNAL_ENTRY" to "\"resolveJournal\": {\"summary\" (matching title/keyword of the problem or injury)} - resolve or forget an injury or past issue that is now healed",
    "UPDATE_REMINDERS" to "\"reminders\": {\"morningReadinessEnabled\" (true/false), \"bedtimeReminderEnabled\" (true/false)} - update nudge and reminder switches"
).joinToString("\n") { (type, payload) -> "                 • $type: $payload" }
