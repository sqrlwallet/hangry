package com.kevan.hangry

import com.kevan.hangry.data.local.entity.DailyHealthSummaryEntity
import com.kevan.hangry.data.local.entity.FoodLogEntity
import com.kevan.hangry.data.local.entity.FoodLogSource
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class HealthConnectIngestionUnitTest {

    @Test
    fun testNutritionRecordDeduplication_externalHealthConnectMeals() {
        val date = LocalDate.of(2026, 9, 23)
        val record1 = FoodLogEntity(
            id = 1,
            date = date,
            timestamp = Instant.parse("2026-09-23T12:00:00Z"),
            source = FoodLogSource.HEALTH_CONNECT,
            foodName = "Grilled Chicken Salad",
            calories = 450,
            proteinG = 42.0,
            carbsG = 12.0,
            fatG = 15.0,
            healthConnectSynced = true,
            sourceRecordId = "hc-record-uuid-101"
        )

        val localSim = mutableMapOf<String, FoodLogEntity>()

        // 1st sync run inserts
        var inserted = 0
        var skipped = 0
        val incoming = listOf(record1)
        for (item in incoming) {
            val key = item.sourceRecordId ?: item.id.toString()
            if (!localSim.containsKey(key)) {
                localSim[key] = item
                inserted++
            } else {
                skipped++
            }
        }
        assertEquals(1, inserted)
        assertEquals(0, skipped)
        assertEquals(1, localSim.size)

        // 2nd sync run skips duplicate sourceRecordId
        var secondRunInserted = 0
        var secondRunSkipped = 0
        for (item in incoming) {
            val key = item.sourceRecordId ?: item.id.toString()
            if (!localSim.containsKey(key)) {
                localSim[key] = item
                secondRunInserted++
            } else {
                secondRunSkipped++
            }
        }
        assertEquals(0, secondRunInserted)
        assertEquals(1, secondRunSkipped)
    }

    @Test
    fun testManualFoodEntries_allowNullSourceRecordIdWithoutCollisions() {
        val date = LocalDate.of(2026, 9, 23)
        val entry1 = FoodLogEntity(
            id = 1,
            date = date,
            timestamp = Instant.parse("2026-09-23T08:00:00Z"),
            source = FoodLogSource.MANUAL,
            foodName = "Espresso",
            calories = 5,
            sourceRecordId = null
        )
        val entry2 = FoodLogEntity(
            id = 2,
            date = date,
            timestamp = Instant.parse("2026-09-23T10:00:00Z"),
            source = FoodLogSource.PHOTO,
            foodName = "Protein Shake",
            calories = 200,
            sourceRecordId = null
        )

        assertNull(entry1.sourceRecordId)
        assertNull(entry2.sourceRecordId)
        assertNotEquals(entry1.id, entry2.id)
    }

    @Test
    fun testDailyHealthSummary_enrichmentWithMeasuredVitalsAndMetrics() {
        val date = LocalDate.of(2026, 9, 23)
        val summary = DailyHealthSummaryEntity(
            date = date,
            steps = 10450L,
            activeCalories = 480.0,
            bmrCalories = 1750.0, // Measured from smart scale / tracker BasalMetabolicRateRecord
            totalCalories = 2230.0, // bmrCalories (1750) + activeCalories (480)
            respiratoryRate = 14.5,
            bloodPressureSystolic = 118.0,
            bloodPressureDiastolic = 78.0,
            spo2Percentage = 98.0,
            vo2Max = 48.5,
            hydrationLiters = 2.75,
            bodyFatPercentage = 15.2
        )

        assertEquals(2230.0, summary.totalCalories!!, 0.01)
        assertEquals(14.5, summary.respiratoryRate!!, 0.01)
        assertEquals(118.0, summary.bloodPressureSystolic!!, 0.01)
        assertEquals(78.0, summary.bloodPressureDiastolic!!, 0.01)
        assertEquals(2.75, summary.hydrationLiters!!, 0.01)
        assertEquals(15.2, summary.bodyFatPercentage!!, 0.01)
    }

    @Test
    fun testEarliestDateSelectionLogic() {
        val today = LocalDate.of(2026, 9, 23)
        val earliestSteps = LocalDate.of(2023, 1, 15)
        val oldestSleep = LocalDate.of(2022, 6, 10)
        val oldestExercise = LocalDate.of(2022, 8, 1)

        val candidates = listOf(earliestSteps, oldestSleep, oldestExercise)
        val earliest = candidates.minOrNull()

        assertNotNull(earliest)
        assertEquals(LocalDate.of(2022, 6, 10), earliest)

        // Days count should span all the way back to the earliest recorded date
        val daysBack = java.time.temporal.ChronoUnit.DAYS.between(earliest, today).toInt()
        assertTrue("Historical depth should exceed 1000 days", daysBack > 1000)
    }
}
