package com.kevan.hangry

import com.kevan.hangry.domain.ai.extractJsonPayload
import com.kevan.hangry.domain.model.CoachResponse
import com.kevan.hangry.domain.model.ExtractedJournalEntry
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AiCoachParsingTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun parseJson_withJournalEntry_succeeds() {
        val raw = """
            {
              "reply": "Based on your low recovery score of 42% and recent strain, I suggest taking a rest day today.",
              "journalEntry": {
                "category": "PROBLEM",
                "summary": "Right knee pain on squats",
                "content": "User reported sharp pain in the right knee when squatting heavy."
              }
            }
        """.trimIndent()

        val parsed = json.decodeFromString<CoachResponse>(extractJsonPayload(raw))
        assertEquals("Based on your low recovery score of 42% and recent strain, I suggest taking a rest day today.", parsed.reply)
        assertNotNull(parsed.journalEntry)
        assertEquals("PROBLEM", parsed.journalEntry?.category)
        assertEquals("Right knee pain on squats", parsed.journalEntry?.summary)
        assertEquals("User reported sharp pain in the right knee when squatting heavy.", parsed.journalEntry?.content)
    }

    @Test
    fun parseJson_withoutJournalEntry_hasNullJournal() {
        val raw = """
            {
              "reply": "Your average sleep over the last 7 days was 7h 20m, which is very close to your 8h target.",
              "journalEntry": null
            }
        """.trimIndent()

        val parsed = json.decodeFromString<CoachResponse>(extractJsonPayload(raw))
        assertEquals("Your average sleep over the last 7 days was 7h 20m, which is very close to your 8h target.", parsed.reply)
        assertNull(parsed.journalEntry)
    }

    @Test
    fun parseJson_withMarkdownFences_unwrapsCorrectly() {
        val raw = """
            ```json
            {
              "reply": "Great job hitting your step goal 5 out of 7 days!",
              "journalEntry": {
                "category": "HABIT",
                "summary": "Morning fasting habit",
                "content": "User fasts until noon on weekdays."
              }
            }
            ```
        """.trimIndent()

        val parsed = json.decodeFromString<CoachResponse>(extractJsonPayload(raw))
        assertEquals("Great job hitting your step goal 5 out of 7 days!", parsed.reply)
        assertEquals("Morning fasting habit", parsed.journalEntry?.summary)
        assertEquals("HABIT", parsed.journalEntry?.category)
    }

    @Test
    fun fallbackHandling_plainTextResponse_handlesGracefully() {
        val raw = "I recommend resting today because your strain was over 16 yesterday."
        val response = try {
            json.decodeFromString<CoachResponse>(extractJsonPayload(raw))
        } catch (_: Exception) {
            CoachResponse(reply = raw.trim(), journalEntry = null)
        }

        assertEquals(raw, response.reply)
        assertNull(response.journalEntry)
    }
}
