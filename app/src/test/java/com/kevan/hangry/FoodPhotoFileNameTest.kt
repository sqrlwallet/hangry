package com.kevan.hangry

import com.kevan.hangry.util.foodPhotoFileName
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class FoodPhotoFileNameTest {
    private val date = LocalDate.of(2026, 10, 14)

    @Test
    fun replacesSymbolsAndSpacesWithUnderscores() {
        assertEquals("Hangry_Chicken_Caesar_Salad_2026-10-14.jpg", foodPhotoFileName("Chicken Caesar Salad", date))
        assertEquals("Hangry_Mac_Cheese_2026-10-14.jpg", foodPhotoFileName("  Mac & Cheese! ", date))
    }

    @Test
    fun fallsBackToMealWhenNothingIsLeft() {
        assertEquals("Hangry_Meal_2026-10-14.jpg", foodPhotoFileName("🍕🍕", date))
        assertEquals("Hangry_Meal_2026-10-14.jpg", foodPhotoFileName("", date))
    }

    @Test
    fun capsTheNameAtFortyCharacters() {
        assertEquals("Hangry_${"a".repeat(40)}_2026-10-14.jpg", foodPhotoFileName("a".repeat(60), date))
    }
}
