package com.kevan.hangry

import com.kevan.hangry.data.local.entity.MealPlanEntity
import com.kevan.hangry.ui.nutrition.SavedMealOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SavedMealOrderTest {

    private fun meal(id: Long, name: String, uses: Int, lastUsedSecondsAgo: Long? = null) = MealPlanEntity(
        id = id, name = name, calories = 100, useCount = uses,
        lastUsedAt = lastUsedSecondsAgo?.let { Instant.parse("2026-10-13T12:00:00Z").minusSeconds(it) }
    )

    @Test
    fun `most logged foods are pinned above the rest, by how often`() {
        // Input arrives most recent first, as the DAO returns it.
        val meals = listOf(
            meal(1, "Latte", uses = 2, lastUsedSecondsAgo = 10),
            meal(2, "Salad", uses = 1, lastUsedSecondsAgo = 20),
            meal(3, "Oats", uses = 9, lastUsedSecondsAgo = 30),
            meal(4, "Banana", uses = 4, lastUsedSecondsAgo = 40)
        )
        val sections = SavedMealOrder.split(meals)
        assertEquals(listOf("Oats", "Banana", "Latte"), sections.mostLogged.map { it.name })
        assertEquals(listOf("Salad"), sections.rest.map { it.name })
    }

    @Test
    fun `the rest keep their recent-first order`() {
        val meals = (1L..8L).map { meal(it, "Food $it", uses = 10 - it.toInt(), lastUsedSecondsAgo = it) }
        val sections = SavedMealOrder.split(meals, pinnedCount = 3)
        assertEquals(listOf(1L, 2L, 3L), sections.mostLogged.map { it.id })
        assertEquals(listOf(4L, 5L, 6L, 7L, 8L), sections.rest.map { it.id })
    }

    @Test
    fun `a food logged once is never pinned`() {
        val meals = listOf(meal(1, "Toast", uses = 1, lastUsedSecondsAgo = 5), meal(2, "Added by hand", uses = 0))
        val sections = SavedMealOrder.split(meals)
        assertTrue(sections.mostLogged.isEmpty())
        assertEquals(meals, sections.rest)
    }

    @Test
    fun `ties go to the one logged more recently`() {
        val meals = listOf(meal(1, "Eggs", uses = 3, lastUsedSecondsAgo = 100), meal(2, "Rice", uses = 3, lastUsedSecondsAgo = 5))
        assertEquals(listOf("Rice", "Eggs"), SavedMealOrder.split(meals).mostLogged.map { it.name })
    }
}
