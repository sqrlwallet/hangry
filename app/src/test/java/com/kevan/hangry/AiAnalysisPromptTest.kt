package com.kevan.hangry

import com.kevan.hangry.domain.ai.extractJsonPayload
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@Serializable
private data class TestPostureJson(
    val valid: Boolean,
    val invalidPhotoIndices: List<Int> = emptyList(),
    val reason: String? = null,
    val score: Int? = null,
    val findings: List<String> = emptyList(),
    val exercises: List<TestExerciseJson> = emptyList()
) {
    @Serializable
    data class TestExerciseJson(
        val name: String,
        val description: String,
        val targetArea: String,
        val sets: Int,
        val reps: String
    )
}

@Serializable
private data class TestFoodJson(
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

class AiAnalysisPromptTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun postureResponse_validWithClothedSubject_parsesCorrectly() {
        val payload = """
            {
              "valid": true,
              "score": 84,
              "findings": [
                "Mild forward head posture with chin jutting forward approximately 10 degrees",
                "Slight bilateral shoulder rounding (internal rotation)",
                "Good neutral thoracic alignment and pelvis positioning"
              ],
              "exercises": [
                {
                  "name": "Chin Tucks",
                  "description": "Retract your chin straight backward as if making a double chin, holding for 3 seconds.",
                  "targetArea": "Deep Neck Flexors",
                  "sets": 3,
                  "reps": "10-12 reps"
                },
                {
                  "name": "Doorway Chest Stretch",
                  "description": "Place elbows on doorframe at 90 degrees and gently step forward to open the chest.",
                  "targetArea": "Pectorals & Anterior Deltoids",
                  "sets": 3,
                  "reps": "30-second hold"
                }
              ]
            }
        """.trimIndent()

        val parsed = json.decodeFromString(TestPostureJson.serializer(), extractJsonPayload(payload))
        assertTrue(parsed.valid)
        assertEquals(84, parsed.score)
        assertEquals(3, parsed.findings.size)
        assertEquals(2, parsed.exercises.size)
        assertEquals("Chin Tucks", parsed.exercises[0].name)
        assertEquals("Deep Neck Flexors", parsed.exercises[0].targetArea)
    }

    @Test
    fun postureResponse_invalid_parsesRejectionReason() {
        val payload = """
            {
              "valid": false,
              "invalidPhotoIndices": [0],
              "reason": "No person detected in the photos"
            }
        """.trimIndent()

        val parsed = json.decodeFromString(TestPostureJson.serializer(), extractJsonPayload(payload))
        assertFalse(parsed.valid)
        assertEquals(listOf(0), parsed.invalidPhotoIndices)
        assertEquals("No person detected in the photos", parsed.reason)
    }

    @Test
    fun foodResponse_withPortionBreakdown_parsesCorrectly() {
        val payload = """
            ```json
            {
              "foodName": "Pan-Seared Salmon with Jasmine Rice & Steamed Broccoli",
              "calories": 620,
              "proteinG": 42.5,
              "carbsG": 55.0,
              "fatG": 24.0,
              "fiberG": 4.5,
              "sugarG": 2.0,
              "sodiumMg": 480.0,
              "confidenceNote": "Estimated ~450g total: ~180g salmon fillet, 1 cup cooked rice (~195g), 1 cup broccoli with ~1 tsp olive oil"
            }
            ```
        """.trimIndent()

        val parsed = json.decodeFromString(TestFoodJson.serializer(), extractJsonPayload(payload))
        assertEquals("Pan-Seared Salmon with Jasmine Rice & Steamed Broccoli", parsed.foodName)
        assertEquals(620, parsed.calories)
        assertEquals(42.5, parsed.proteinG, 0.01)
        assertEquals(55.0, parsed.carbsG, 0.01)
        assertEquals(24.0, parsed.fatG, 0.01)
        assertEquals(4.5, parsed.fiberG, 0.01)
        assertNotNull(parsed.confidenceNote)
        assertTrue(parsed.confidenceNote.contains("olive oil"))
    }
}
