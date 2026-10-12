package com.kevan.hangry.data.ai

import com.kevan.hangry.domain.ai.AiJson
import com.kevan.hangry.domain.ai.extractJsonPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real model answers drift from the schema; one odd value mustn't throw the whole meal away. */
class FoodAnalysisParsingTest {

    private fun parse(raw: String, checkAllergens: Boolean = false) = parseFoodAnalysis(raw).toResult(checkAllergens)

    @Test
    fun fractionalTopLevelCalories_areRoundedNotRejected() {
        val result = parse("""{"foodName": "Pad Thai", "calories": 512.4, "proteinG": 18.2, "carbsG": 70}""")
        assertEquals("Pad Thai", result.foodName)
        assertEquals(512, result.calories)
        assertEquals(18.2, result.proteinG, 0.01)
    }

    @Test
    fun chatterAroundTheJson_isIgnored() {
        val raw = """Sure! Here's my estimate for your meal:

            {"foodName": "Banana", "items": [{"name": "banana", "grams": 120, "per100g": {"calories": 89, "carbsG": 23, "fiberG": 2.6}}]}

            Let me know if you'd like me to adjust anything {or not}."""
        val result = parse(raw)
        assertEquals("Banana", result.foodName)
        assertEquals(107, result.calories)
    }

    @Test
    fun fenceAfterIntroText_isUnwrapped() {
        val raw = "Here is the JSON you asked for:\n```json\n{\"foodName\": \"Toast\", \"calories\": 160}\n```\nEnjoy!"
        assertEquals(160, parse(raw).calories)
    }

    @Test
    fun missingNamesGramsAndNulls_fallBackToDefaults() {
        val raw = """
            {"items": [
               {"name": "grilled salmon", "grams": 140, "per100g": {"calories": 208, "proteinG": 20, "fatG": null}},
               {"name": "garnish", "per100g": {"calories": 20}},
               {"grams": null, "per100g": null}
             ],
             "portionNote": null, "allergenWarnings": null, "extra": {"confidence": "high"}}
        """
        val result = parse(raw)
        // Only the weighed component counts; the meal is named after it.
        assertEquals("grilled salmon", result.foodName)
        assertEquals(291, result.calories)
        assertEquals(0.0, result.fatG, 0.0)
    }

    @Test
    fun quotedNumbers_areRead() {
        val result = parse("""{"foodName": "Latte", "items": [{"name": "latte", "grams": "350", "per100g": {"calories": "54", "proteinG": "3.4"}}]}""")
        assertEquals(189, result.calories)
        assertEquals(11.9, result.proteinG, 0.01)
    }

    @Test
    fun allergenWarnings_surviveMessyOutput() {
        val raw = "```\n{\"foodName\": \"Satay Chicken\", \"calories\": 450.6, \"allergenWarnings\": [\"Peanuts - satay sauce\", \"\"]}\n```"
        val result = parse(raw, checkAllergens = true)
        assertEquals(451, result.calories)
        assertEquals(listOf("Peanuts - satay sauce"), result.allergenWarnings)
    }

    @Test(expected = Exception::class)
    fun answerWithNoFood_fails() {
        parse("""{"notes": "I can't see any food in this photo."}""")
    }

    @Test(expected = Exception::class)
    fun plainTextRefusal_fails() {
        parse("I'm sorry, I can't identify food in this image.")
    }

    @Test
    fun sharedJson_readsOtherAnalyzersLeniently() {
        val review = AiJson.decodeFromString(
            NutritionReviewJson.serializer(),
            extractJsonPayload("Review:\n```json\n{\"headline\": \"Solid week\", \"wins\": null, \"fiberPlan\": \"Add oats\"}\n```")
        ).toReview()
        assertEquals("Solid week", review.headline)
        assertTrue(review.wins.isEmpty())
    }
}
