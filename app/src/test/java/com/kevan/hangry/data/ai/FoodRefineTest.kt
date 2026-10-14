package com.kevan.hangry.data.ai

import com.kevan.hangry.util.foodPhotoFileName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FoodRefineTest {

    private val previous = "Chicken Caesar Salad: 520 kcal, 38 g protein, 14 g carbs, 34 g fat, 3 g fiber"

    @Test
    fun `the prompt carries the earlier estimate and the user's comment`() {
        val prompt = refinePrompt(hasPhoto = true, previous, "  No croutons, and half the dressing  ")

        assertTrue(prompt.contains(previous))
        assertTrue(prompt.contains("\"No croutons, and half the dressing\""))
        assertTrue(prompt.contains("the food in this photo"))
        assertTrue(prompt.contains("authoritative"))
    }

    @Test
    fun `without a photo it doesn't mention one`() {
        val prompt = refinePrompt(hasPhoto = false, previous, "It was a large bowl")

        assertFalse(prompt.contains("photo"))
        assertTrue(prompt.contains("this meal"))
    }

    @Test
    fun `saved photos are named after the meal and day`() {
        assertEquals(
            "Hangry_Chicken_Caesar_Salad_2026-10-14.jpg",
            foodPhotoFileName("Chicken Caesar Salad!", LocalDate.of(2026, 10, 14))
        )
        assertEquals("Hangry_Meal_2026-10-14.jpg", foodPhotoFileName("  ", LocalDate.of(2026, 10, 14)))
    }
}
