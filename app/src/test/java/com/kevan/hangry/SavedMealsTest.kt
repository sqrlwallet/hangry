package com.kevan.hangry

import com.kevan.hangry.data.local.dao.MealPlanDao
import com.kevan.hangry.data.local.entity.FoodLogEntity
import com.kevan.hangry.data.local.entity.FoodLogSource
import com.kevan.hangry.data.local.entity.MealPlanEntity
import com.kevan.hangry.data.local.entity.parsePortionedName
import com.kevan.hangry.data.local.entity.portionedName
import com.kevan.hangry.data.local.entity.savedMealKey
import com.kevan.hangry.data.repository.DefaultMealPlanRepository
import com.kevan.hangry.domain.model.CommonFoods
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.math.abs

class SavedMealsTest {

    private class FakeMealPlanDao : MealPlanDao {
        val rows = MutableStateFlow<List<MealPlanEntity>>(emptyList())
        private var nextId = 1L
        override suspend fun upsert(entry: MealPlanEntity): Long {
            val id = if (entry.id == 0L) nextId++ else entry.id
            rows.value = rows.value.filter { it.id != id && it.nameKey != entry.nameKey } + entry.copy(id = id)
            return id
        }
        override suspend fun update(entry: MealPlanEntity) {
            rows.value = rows.value.map { if (it.id == entry.id) entry else it }
        }
        override suspend fun delete(entry: MealPlanEntity) {
            rows.value = rows.value.filter { it.id != entry.id }
        }
        override fun getAll(): Flow<List<MealPlanEntity>> = rows
        override suspend fun getByKey(nameKey: String) = rows.value.firstOrNull { it.nameKey == nameKey }
        override suspend fun deleteAll() { rows.value = emptyList() }
    }

    private fun log(name: String, calories: Int, source: String = FoodLogSource.MANUAL, fiber: Double = 0.0) =
        FoodLogEntity(date = LocalDate.of(2026, 9, 24), source = source, foodName = name, calories = calories, fiberG = fiber)

    @Test
    fun `logging a food saves it once and counts repeats`() = runTest {
        val dao = FakeMealPlanDao()
        val repo = DefaultMealPlanRepository(dao)
        repo.rememberFromLog(log("Chicken rice bowl", 620))
        repo.rememberFromLog(log("  chicken RICE bowl ", 640))
        val rows = dao.rows.value
        assertEquals(1, rows.size)
        assertEquals("Chicken rice bowl", rows[0].name)
        assertEquals(640, rows[0].calories)
        assertEquals(2, rows[0].useCount)
    }

    @Test
    fun `corrections update numbers without counting as another meal`() = runTest {
        val dao = FakeMealPlanDao()
        val repo = DefaultMealPlanRepository(dao)
        repo.rememberFromLog(log("Oat latte", 150))
        repo.rememberFromLog(log("Oat latte", 190), countAsUse = false)
        assertEquals(190, dao.rows.value.single().calories)
        assertEquals(1, dao.rows.value.single().useCount)
    }

    @Test
    fun `placeholders and Health Connect imports are not saved`() = runTest {
        val dao = FakeMealPlanDao()
        val repo = DefaultMealPlanRepository(dao)
        repo.rememberFromLog(log("Meal", 400))
        repo.rememberFromLog(log("Lunch", 400))
        repo.rememberFromLog(log("   ", 400))
        repo.rememberFromLog(log("Burrito", 700, source = FoodLogSource.HEALTH_CONNECT))
        assertTrue(dao.rows.value.isEmpty())
    }

    @Test
    fun `re-logging from the manual form keeps known fiber`() = runTest {
        val dao = FakeMealPlanDao()
        val repo = DefaultMealPlanRepository(dao)
        repo.rememberFromLog(log("Banana", 105, fiber = 3.1))
        repo.rememberFromLog(log("Banana", 105))
        assertEquals(3.1, dao.rows.value.single().fiberG, 0.0)
    }

    @Test
    fun `adding a meal by hand with a known name replaces it`() = runTest {
        val dao = FakeMealPlanDao()
        val repo = DefaultMealPlanRepository(dao)
        repo.rememberFromLog(log("Protein shake", 120))
        repo.upsert(MealPlanEntity(name = "protein shake", calories = 160))
        val row = dao.rows.value.single()
        assertEquals(160, row.calories)
        assertEquals(1, row.useCount)
    }

    @Test
    fun `common foods have unique names and calories that match their macros`() {
        val keys = CommonFoods.all.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        CommonFoods.all.forEach { f ->
            val fromMacros = 4 * f.proteinG + 4 * f.carbsG + 9 * f.fatG
            // Atwater factors are approximate (fiber, alcohol), so allow a generous margin -
            // this catches typos like a dropped digit, not rounding.
            if (!f.name.contains("Beer") && !f.name.contains("wine")) {
                assertTrue("${f.name}: ${f.calories} vs $fromMacros", abs(f.calories - fromMacros) <= maxOf(20.0, f.calories * 0.2))
            }
        }
        assertEquals(CommonFoods.starters.size, 8)
        assertEquals("banana", savedMealKey(" Banana "))
    }

    @Test
    fun `portion names round-trip`() {
        assertEquals("Banana", portionedName("Banana", 1.0))
        assertEquals("Banana (×½)", portionedName("Banana", 0.5))
        assertEquals("Rice (×1½)", portionedName("Rice", 1.5))
        assertEquals("Egg (×2)", portionedName("Egg", 2.0))
        listOf(0.5, 1.5, 2.0).forEach { p ->
            assertEquals("Chicken (x) bowl" to p, parsePortionedName(portionedName("Chicken (x) bowl", p)))
        }
        assertEquals("Pad thai (large)" to 1.0, parsePortionedName("Pad thai (large)"))
    }

    @Test
    fun `a double helping counts toward the saved meal without changing one portion`() = runTest {
        val dao = FakeMealPlanDao()
        val repo = DefaultMealPlanRepository(dao)
        repo.rememberFromLog(log("Banana", 105, fiber = 3.1))
        repo.rememberFromLog(log(portionedName("Banana", 2.0), 210, fiber = 6.2))
        val row = dao.rows.value.single()
        assertEquals("Banana", row.name)
        assertEquals(105, row.calories)
        assertEquals(2, row.useCount)
    }

    @Test
    fun `a first log at half a portion saves one full portion`() = runTest {
        val dao = FakeMealPlanDao()
        val repo = DefaultMealPlanRepository(dao)
        repo.rememberFromLog(log(portionedName("Bagel", 0.5), 135))
        val row = dao.rows.value.single()
        assertEquals("Bagel", row.name)
        assertEquals(270, row.calories)
    }
}
