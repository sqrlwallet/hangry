package com.kevan.hangry

import com.kevan.hangry.data.ai.NutritionReviewJson
import com.kevan.hangry.data.local.entity.FoodLogEntity
import com.kevan.hangry.data.local.entity.FoodLogSource
import com.kevan.hangry.domain.ai.AiDefaults
import com.kevan.hangry.domain.ai.extractJsonPayload
import com.kevan.hangry.domain.calculation.NutritionTargets
import com.kevan.hangry.domain.model.NutritionWeekAnalyzer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

class NutritionWeeklyReviewTest {

    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 10, 5)

    private fun meal(
        date: LocalDate,
        name: String = "Chicken wrap",
        calories: Int = 600,
        protein: Double = 30.0,
        fiber: Double = 4.0,
        sodium: Double = 500.0,
        hour: Int = 13
    ) = FoodLogEntity(
        date = date,
        timestamp = date.atTime(LocalTime.of(hour, 0)).toInstant(zone),
        source = FoodLogSource.PHOTO,
        foodName = name,
        calories = calories,
        proteinG = protein,
        fiberG = fiber,
        sodiumMg = sodium
    )

    @Test
    fun window_isTheSevenFullDaysBeforeToday() {
        val entries = listOf(meal(today.minusDays(3)), meal(today))
        val summary = NutritionWeekAnalyzer.summarize(entries, today, zone)
        assertEquals(today.minusDays(7), summary.start)
        assertEquals(today.minusDays(1), summary.end)
        // Today's half-finished day is left out of the averages.
        assertEquals(1, summary.daysLogged)
    }

    @Test
    fun window_fallsBackToIncludeToday_whenOnlyTodayIsLogged() {
        val summary = NutritionWeekAnalyzer.summarize(listOf(meal(today)), today, zone)
        assertEquals(today, summary.end)
        assertEquals(1, summary.daysLogged)
    }

    @Test
    fun averages_areOverLoggedDaysOnly() {
        val entries = listOf(
            meal(today.minusDays(1), calories = 1000, fiber = 10.0),
            meal(today.minusDays(1), calories = 1000, fiber = 10.0),
            meal(today.minusDays(2), calories = 1000, fiber = 4.0)
        )
        val summary = NutritionWeekAnalyzer.summarize(entries, today, zone)
        assertEquals(2, summary.daysLogged)
        assertEquals(1500, summary.avgCalories)
        assertEquals(12.0, summary.avgFiberG, 0.001)
        assertEquals(listOf("Chicken wrap"), summary.frequentFoods.map { it.name })
        assertEquals(3, summary.frequentFoods.first().times)
    }

    @Test
    fun lateMeals_countMealsFrom9pm() {
        val entries = listOf(meal(today.minusDays(1), hour = 21), meal(today.minusDays(2), hour = 23), meal(today.minusDays(3), hour = 20))
        assertEquals(2, NutritionWeekAnalyzer.summarize(entries, today, zone).lateMeals)
    }

    @Test
    fun tips_flagLowFiberAndProtein() {
        val entries = (1..5L).map { meal(today.minusDays(it), name = "Meal $it", calories = 2000, protein = 40.0, fiber = 8.0) }
        val tips = NutritionWeekAnalyzer.tips(NutritionWeekAnalyzer.summarize(entries, today, zone), calorieTarget = 2000)
        assertTrue(tips.any { it.title.contains("fiber") })
        assertTrue(tips.any { it.title.contains("protein") })
        assertFalse(tips.any { it.title == "Solid week" })
    }

    @Test
    fun tips_saySolidWeek_whenEverythingIsOnTrack() {
        val entries = (1..7L).flatMap { d ->
            listOf("Oats", "Salmon bowl", "Lentil soup").map { meal(today.minusDays(d), name = it + d, calories = 667, protein = 45.0, fiber = 11.0, sodium = 600.0) }
        }
        val tips = NutritionWeekAnalyzer.tips(NutritionWeekAnalyzer.summarize(entries, today, zone), calorieTarget = 2000)
        assertEquals(listOf("Solid week"), tips.map { it.title })
    }

    @Test
    fun tips_areEmpty_withNothingLogged() {
        assertTrue(NutritionWeekAnalyzer.tips(NutritionWeekAnalyzer.summarize(emptyList(), today, zone), 2000).isEmpty())
    }

    @Test
    fun fiberTarget_is14gPer1000kcal_withinAdultRange() {
        assertEquals(28.0, NutritionTargets.fiberG(2000), 0.001)
        assertEquals(25.0, NutritionTargets.fiberG(1200), 0.001)
        assertEquals(38.0, NutritionTargets.fiberG(3500), 0.001)
        assertEquals(28.0, NutritionTargets.fiberG(null), 0.001)
    }

    @Test
    fun promptText_includesAllergiesAndTargets() {
        val summary = NutritionWeekAnalyzer.summarize(listOf(meal(today.minusDays(1))), today, zone)
        val text = NutritionWeekAnalyzer.promptText(summary, 2000, "34 years old", listOf("Peanuts"))
        assertTrue(text.contains("Peanuts"))
        assertTrue(text.contains("fiber 28g"))
        assertTrue(text.contains("34 years old"))
    }

    @Test
    fun analysisModel_movesRetiredDefaultToGemini38Flash() {
        assertEquals("google/gemini-3.8-flash", AiDefaults.DEFAULT_MODEL)
        assertEquals(AiDefaults.DEFAULT_MODEL, AiDefaults.analysisModel(null))
        assertEquals(AiDefaults.DEFAULT_MODEL, AiDefaults.analysisModel("  "))
        assertEquals(AiDefaults.DEFAULT_MODEL, AiDefaults.analysisModel("google/gemini-2.5-flash"))
        assertEquals("anthropic/claude-x", AiDefaults.analysisModel("anthropic/claude-x"))
    }

    @Test
    fun reviewJson_parsesAndDropsBlankItems() {
        val raw = """```json
            {"headline": "Good protein, light on fiber", "overview": "Solid week.",
             "wins": ["Protein at breakfast", " "],
             "recommendations": [{"title": "Add beans", "why": "Fiber is low", "how": "Half a cup at lunch"}, {"title": ""}],
             "foodsToAdd": [{"food": "Lentils", "why": "8g fiber per half cup"}],
             "fiberPlan": "Add oats with chia at breakfast."}
            ```"""
        val review = Json { ignoreUnknownKeys = true }
            .decodeFromString(NutritionReviewJson.serializer(), extractJsonPayload(raw)).toReview()
        assertEquals("Good protein, light on fiber", review.headline)
        assertEquals(1, review.wins.size)
        assertEquals(1, review.recommendations.size)
        assertEquals("Half a cup at lunch", review.recommendations.first().how)
        assertEquals("Lentils", review.foodsToAdd.single().food)
    }
}
