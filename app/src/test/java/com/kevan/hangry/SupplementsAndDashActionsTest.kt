package com.kevan.hangry

import android.net.Uri
import com.kevan.hangry.data.ai.parseCoachResponse
import com.kevan.hangry.data.coach.CoachActionExecutor
import com.kevan.hangry.data.local.entity.FoodLogEntity
import com.kevan.hangry.domain.model.CoachAction
import com.kevan.hangry.domain.model.GlucoseContext
import com.kevan.hangry.domain.model.HealthProfileKind
import com.kevan.hangry.domain.model.HealthRecordsImportResult
import com.kevan.hangry.domain.model.HealthRecordsSnapshot
import com.kevan.hangry.domain.model.MarkerType
import com.kevan.hangry.domain.model.MealActionPayload
import com.kevan.hangry.domain.model.ReadingActionPayload
import com.kevan.hangry.domain.model.SupplementActionPayload
import com.kevan.hangry.domain.model.SupplementAnalysisResult
import com.kevan.hangry.domain.model.SupplementTimes
import com.kevan.hangry.domain.model.SupplementsSnapshot
import com.kevan.hangry.domain.repository.FoodLogRepository
import com.kevan.hangry.domain.repository.HealthRecordsRepository
import com.kevan.hangry.domain.repository.SupplementDraft
import com.kevan.hangry.domain.repository.SupplementRepository
import com.kevan.hangry.ui.supplements.SupplementsViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class SupplementsAndDashActionsTest {

    // region Supplement times

    @Test
    fun `dose times round trip, sorted and deduplicated`() {
        val times = SupplementTimes.parse("21:00, 08:00,08:00,bad")
        assertEquals(listOf(LocalTime.of(8, 0), LocalTime.of(21, 0)), times)
        assertEquals("08:00,21:00", SupplementTimes.format(times))
    }

    @Test
    fun `timing advice becomes a sensible reminder time`() {
        assertEquals(LocalTime.of(21, 0), SupplementsViewModel.defaultTimeFor("Evening, 1 hour before bed"))
        assertEquals(LocalTime.of(13, 0), SupplementsViewModel.defaultTimeFor("With lunch"))
        assertEquals(LocalTime.of(8, 0), SupplementsViewModel.defaultTimeFor(null))
    }

    // endregion

    // region Dash response parsing

    @Test
    fun `a malformed action is dropped without losing the reply`() {
        val raw = """
            {"reply":"Tap Add below.","journalEntry":null,"actions":[
              {"type":"ADD_SUPPLEMENT","title":"Add D3","supplement":{"name":"Vitamin D3","times":["08:00"]}},
              {"type":"LOG_MEAL","title":"Broken","meal":{"calories":"lots"}}
            ]}
        """.trimIndent()
        val parsed = parseCoachResponse(raw)
        assertEquals("Tap Add below.", parsed.reply)
        assertEquals(1, parsed.actions.size)
        assertEquals("Vitamin D3", parsed.actions.first().supplement!!.name)
    }

    @Test
    fun `plain text replies are shown as-is`() {
        val parsed = parseCoachResponse("Sure! Magnesium is best at night.")
        assertEquals("Sure! Magnesium is best at night.", parsed.reply)
        assertTrue(parsed.actions.isEmpty())
    }

    // endregion

    // region Action executor

    private val supplements = FakeSupplementRepository()
    private val records = FakeHealthRecordsRepository()
    private val foodLog = FakeFoodLogRepository()
    private val executor = CoachActionExecutor(supplements, records, foodLog, writeNutritionRecord = { true })

    @Test
    fun `lab reading in mmol per litre is saved in mg per dL`() = runTest {
        val result = executor.execute(
            CoachAction(type = CoachAction.ADD_READING, reading = ReadingActionPayload(marker = "ldl", value = 3.0, unit = "mmol/L", date = "2026-08-01"))
        )
        assertTrue(result.isSuccess)
        val saved = records.readings.single()
        assertEquals(MarkerType.LDL, saved.first)
        assertEquals(116.0, saved.second, 0.1)
    }

    @Test
    fun `blood pressure without diastolic is rejected`() = runTest {
        val result = executor.execute(
            CoachAction(type = CoachAction.ADD_READING, reading = ReadingActionPayload(marker = "blood_pressure", value = 130.0, unit = "mmHg"))
        )
        assertTrue(result.isFailure)
        assertTrue(records.readings.isEmpty())
    }

    @Test
    fun `unknown marker is rejected`() = runTest {
        val result = executor.execute(CoachAction(type = CoachAction.ADD_READING, reading = ReadingActionPayload(marker = "vitamin_q", value = 1.0)))
        assertTrue(result.isFailure)
    }

    @Test
    fun `supplement action saves dose and reminder times`() = runTest {
        executor.execute(
            CoachAction(
                type = CoachAction.ADD_SUPPLEMENT,
                supplement = SupplementActionPayload(name = "Magnesium Glycinate", doseAmount = 2.0, doseUnit = "capsules", times = listOf("21:00"))
            )
        )
        val draft = supplements.saved.single()
        assertEquals("Magnesium Glycinate", draft.name)
        assertEquals(2.0, draft.doseAmount, 0.0)
        assertEquals(listOf(LocalTime.of(21, 0)), draft.times)
        assertTrue(draft.remindersEnabled)
    }

    @Test
    fun `implausible meal is not logged`() = runTest {
        val result = executor.execute(CoachAction(type = CoachAction.LOG_MEAL, meal = MealActionPayload(foodName = "Pizza", calories = 50_000)))
        assertTrue(result.isFailure)
        assertTrue(foodLog.entries.isEmpty())
    }

    @Test
    fun `meal is logged`() = runTest {
        val result = executor.execute(CoachAction(type = CoachAction.LOG_MEAL, meal = MealActionPayload(foodName = "Chicken bowl", calories = 620, proteinG = 45.0)))
        assertTrue(result.isSuccess)
        assertEquals(620, foodLog.entries.single().calories)
    }

    // endregion
}
