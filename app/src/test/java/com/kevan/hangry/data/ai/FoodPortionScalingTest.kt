package com.kevan.hangry.data.ai

import com.kevan.hangry.domain.ai.extractJsonPayload
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodPortionScalingTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun parse(payload: String, checkAllergens: Boolean = false) =
        json.decodeFromString(FoodAnalysisJson.serializer(), extractJsonPayload(payload)).toResult(checkAllergens)

    @Test
    fun itemizedResponse_totalsScaleWithEachComponentsGrams() {
        val result = parse(
            """
            {"foodName": "Grilled Chicken with Rice",
             "items": [
               {"name": "grilled chicken breast", "grams": 150, "per100g": {"calories": 165, "proteinG": 31, "carbsG": 0, "fatG": 3.6, "sodiumMg": 74}},
               {"name": "white rice", "grams": 90, "per100g": {"calories": 130, "proteinG": 2.7, "carbsG": 28, "fatG": 0.3, "fiberG": 0.4, "sodiumMg": 1}},
               {"name": "olive oil (absorbed)", "grams": 5, "per100g": {"calories": 884, "fatG": 100}}
             ],
             "portionNote": "Scaled from a 27cm dinner plate."}
            """
        )
        // 247.5 + 117 + 44.2
        assertEquals(409, result.calories)
        assertEquals(48.9, result.proteinG, 0.01)
        assertEquals(25.2, result.carbsG, 0.01)
        assertEquals(10.7, result.fatG, 0.01)
        assertEquals(112.0, result.sodiumMg, 0.01)
        assertTrue(result.confidenceNote.startsWith("Estimated ~245g total: ~150g grilled chicken breast, ~90g white rice"))
        assertTrue(result.confidenceNote.endsWith("Scaled from a 27cm dinner plate."))
    }

    @Test
    fun halfThePortion_halvesTheNutrition() {
        fun rice(grams: Int) = parse(
            """{"foodName": "Rice", "items": [{"name": "rice", "grams": $grams, "per100g": {"calories": 130, "carbsG": 28}}]}"""
        )
        assertEquals(rice(200).calories, rice(100).calories * 2)
        assertEquals(rice(200).carbsG, rice(100).carbsG * 2, 0.01)
    }

    @Test
    fun responseWithoutItems_fallsBackToWholeMealTotals() {
        val result = parse(
            """{"foodName": "Protein Bar", "calories": 210, "proteinG": 20, "carbsG": 22, "fatG": 7, "confidenceNote": "From label"}"""
        )
        assertEquals(210, result.calories)
        assertEquals(20.0, result.proteinG, 0.01)
        assertEquals("From label", result.confidenceNote)
    }

    @Test
    fun allergenWarnings_onlyKeptWhenChecked() {
        val payload = """{"foodName": "Satay", "items": [{"name": "satay", "grams": 100, "per100g": {"calories": 200}}], "allergenWarnings": ["Peanuts - satay sauce", ""]}"""
        assertEquals(listOf("Peanuts - satay sauce"), parse(payload, checkAllergens = true).allergenWarnings)
        assertTrue(parse(payload, checkAllergens = false).allergenWarnings.isEmpty())
    }
}
