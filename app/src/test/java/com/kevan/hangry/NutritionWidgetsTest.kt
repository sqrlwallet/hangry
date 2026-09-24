package com.kevan.hangry

import com.kevan.hangry.data.local.entity.FoodLogSource
import com.kevan.hangry.data.local.entity.MealPlanEntity
import com.kevan.hangry.data.local.entity.toLogEntry
import com.kevan.hangry.domain.calculation.NutritionTargets
import com.kevan.hangry.domain.model.CommonFoods
import com.kevan.hangry.ui.widget.NutritionWidgetText
import com.kevan.hangry.ui.widget.WidgetText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class NutritionWidgetsTest {

    @Test
    fun `calories left against the target`() {
        val t = NutritionWidgetText.calories(consumed = 1240, itemCount = 3, target = 2000, hide = false)
        assertEquals("1,240", t.value)
        assertEquals("62%", t.percent)
        assertEquals("760 left of 2,000", t.subtitle)
        assertEquals(0.62f, t.fraction!!, 0.001f)
        assertFalse(t.over)
    }

    @Test
    fun `over the target fills the ring and says by how much`() {
        val t = NutritionWidgetText.calories(consumed = 2240, itemCount = 5, target = 2000, hide = false)
        assertEquals("240 over 2,000", t.subtitle)
        assertEquals("112%", t.percent)
        assertEquals(1f, t.fraction!!, 0f)
        assertTrue(t.over)
    }

    @Test
    fun `no target is never made up`() {
        val empty = NutritionWidgetText.calories(consumed = 0, itemCount = 0, target = null, hide = false)
        assertEquals("Nothing logged yet", empty.subtitle)
        assertNull(empty.percent)
        assertNull(empty.fraction)
        val some = NutritionWidgetText.calories(consumed = 350, itemCount = 1, target = null, hide = false)
        assertEquals("1 item · no target yet", some.subtitle)
        assertEquals("To go", "2,000 to go today", NutritionWidgetText.calories(0, 0, 2000, hide = false).subtitle)
    }

    @Test
    fun `hidden values show no numbers anywhere`() {
        val t = NutritionWidgetText.calories(consumed = 1240, itemCount = 3, target = 2000, hide = true)
        assertEquals(WidgetText.HIDDEN_VALUE, t.value)
        assertEquals(WidgetText.HIDDEN_SUBTITLE, t.subtitle)
        assertNull(t.percent)
        assertNull(t.fraction)
        assertEquals(WidgetText.HIDDEN_VALUE, NutritionWidgetText.macro(82.0, 125.0, hide = true))
        assertEquals(0, NutritionWidgetText.macroPercent(82.0, 125.0, hide = true))
        assertEquals("+ Banana", NutritionWidgetText.quickAddLabel("Banana", 105, hide = true))
    }

    @Test
    fun `macro rows and quick add labels`() {
        assertEquals("82 / 125 g", NutritionWidgetText.macro(82.4, 125.0, hide = false))
        assertEquals("82 g", NutritionWidgetText.macro(82.4, null, hide = false))
        assertEquals(66, NutritionWidgetText.macroPercent(82.4, 125.0, hide = false))
        assertEquals(100, NutritionWidgetText.macroPercent(300.0, 125.0, hide = false))
        assertEquals("+ Banana · 105", NutritionWidgetText.quickAddLabel("Banana", 105, hide = false))
        assertEquals("+ Chicken ri… · 620", NutritionWidgetText.quickAddLabel("Chicken rice bowl", 620, hide = false))
    }

    @Test
    fun `targets match the Nutrition screen split`() {
        val m = NutritionTargets.macros(2000)!!
        assertEquals(125.0, m.proteinG, 0.0)
        assertEquals(250.0, m.carbsG, 0.0)
        assertEquals(55.6, m.fatG, 0.1)
        assertEquals(50.0, NutritionTargets.macros(600)!!.proteinG, 0.0)
        assertNull(NutritionTargets.macros(null))
        assertNull(NutritionTargets.dailyCalories(null))
    }

    @Test
    fun `widget quick add logs the same entry as the app`() {
        val day = LocalDate.of(2026, 9, 24)
        val meal = MealPlanEntity(id = 7, name = "Oats", calories = 166, proteinG = 5.9, fiberG = 4.0)
        val entry = meal.toLogEntry(day)
        assertEquals("Oats", entry.foodName)
        assertEquals(166, entry.calories)
        assertEquals(4.0, entry.fiberG, 0.0)
        assertEquals(FoodLogSource.MEAL_PLAN, entry.source)
        val banana = CommonFoods.all.first { it.name == "Banana" }.toLogEntry(day, portion = 2.0)
        assertEquals("Banana (×2)", banana.foodName)
        assertEquals(210, banana.calories)
        assertEquals(FoodLogSource.MANUAL, banana.source)
    }
}
