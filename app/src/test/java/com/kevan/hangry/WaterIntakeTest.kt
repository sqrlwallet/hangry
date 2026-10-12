package com.kevan.hangry

import com.kevan.hangry.domain.calculation.BodyMetricsCalculator
import com.kevan.hangry.domain.calculation.HangryCalorieCalculator
import com.kevan.hangry.domain.calculation.WaterIntake
import com.kevan.hangry.domain.calculation.WaterIntake.Drink
import com.kevan.hangry.domain.model.BiologicalSex
import com.kevan.hangry.domain.model.BodyMetricsInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class WaterIntakeTest {

    private val own = "com.kevan.hangry"
    private val zone: ZoneId = ZoneOffset.UTC
    private val day = LocalDate.of(2026, 10, 12)
    private fun at(hour: Int, minute: Int = 0) = day.atTime(hour, minute).toInstant(ZoneOffset.UTC)

    // --- Target and intake ---

    @Test
    fun `target is the 30-35 ml per kg midpoint`() {
        assertEquals(2600, WaterIntake.targetMl(80.0))
        assertEquals(1950, WaterIntake.targetMl(60.0))
    }

    @Test
    fun `no weight falls back to the default target`() {
        assertEquals(WaterIntake.DEFAULT_TARGET_ML, WaterIntake.targetMl(null))
        assertEquals(WaterIntake.DEFAULT_TARGET_ML, WaterIntake.targetMl(0.0))
    }

    @Test
    fun `target matches the Body Metrics water target`() {
        val input = BodyMetricsInput(heightCm = 180.0, weightKg = 80.0, age = 30, sex = BiologicalSex.MALE)
        val metric = BodyMetricsCalculator(HangryCalorieCalculator()).calculate(input)
            .flatMap { it.metrics }.first { it.id == "water" }
        assertEquals(metric.value!! * 1000, WaterIntake.targetMl(80.0).toDouble(), 1.0)
    }

    @Test
    fun `intake adds other apps' liters to the local log`() {
        assertEquals(1450, WaterIntake.totalMl(1.2, 250))
        assertEquals(500, WaterIntake.totalMl(null, 500))
        assertEquals(0, WaterIntake.totalMl(null, 0))
    }

    @Test
    fun `progress is clamped to 0-1`() {
        assertEquals(0.5f, WaterIntake.progress(1000, 2000), 0.0001f)
        assertEquals(1f, WaterIntake.progress(5000, 2000), 0.0001f)
        assertEquals(0f, WaterIntake.progress(500, 0), 0.0001f)
    }

    @Test
    fun `custom amounts must be a sensible single drink`() {
        assertEquals(330, WaterIntake.parseCustomMl(" 330 "))
        assertNull(WaterIntake.parseCustomMl("0"))
        assertNull(WaterIntake.parseCustomMl("25000"))
        assertNull(WaterIntake.parseCustomMl("abc"))
    }

    // --- Dedupe of Health Connect records ---

    @Test
    fun `own records are recognised by package or client id`() {
        assertTrue(WaterIntake.isOwn(Drink(at(9), own, 250.0, null), own))
        assertTrue(WaterIntake.isOwn(Drink(at(9), "other.app", 250.0, WaterIntake.clientId(7)), own))
        assertFalse(WaterIntake.isOwn(Drink(at(9), "other.app", 250.0, "their-id"), own))
    }

    @Test
    fun `hangry's own writes are never counted again`() {
        val drinks = listOf(
            Drink(at(9), own, 250.0, WaterIntake.clientId(1)),
            Drink(at(12), own, 500.0, WaterIntake.clientId(2)),
            Drink(at(15), "com.fitbit", 300.0, null)
        )
        assertEquals(mapOf(day to 0.3), WaterIntake.externalDailyLiters(drinks, own, zone, day, day))
    }

    @Test
    fun `another app's mirror of our drink is dropped`() {
        val drinks = listOf(
            Drink(at(9), own, 250.0, WaterIntake.clientId(1)),
            Drink(at(9, 1), "com.sec.android.app.shealth", 250.0, null)
        )
        assertTrue(WaterIntake.externalDailyLiters(drinks, own, zone, day, day).isEmpty())
    }

    @Test
    fun `a drink mirrored between two other apps counts once`() {
        val drinks = listOf(
            Drink(at(10), "com.fitbit", 400.0, null),
            Drink(at(10, 1), "com.google.android.apps.fitness", 400.0, null)
        )
        assertEquals(0.4, WaterIntake.externalDailyLiters(drinks, own, zone, day, day)[day]!!, 1e-9)
    }

    @Test
    fun `two identical glasses from one app are both kept`() {
        val drinks = listOf(
            Drink(at(10), "com.fitbit", 250.0, null),
            Drink(at(10), "com.fitbit", 250.0, null)
        )
        assertEquals(0.5, WaterIntake.externalDailyLiters(drinks, own, zone, day, day)[day]!!, 1e-9)
    }

    @Test
    fun `drinks are grouped by local day and kept to the range`() {
        val drinks = listOf(
            Drink(at(23), "com.fitbit", 200.0, null),
            Drink(day.plusDays(1).atTime(1, 0).toInstant(ZoneOffset.UTC), "com.fitbit", 300.0, null),
            Drink(day.minusDays(1).atTime(12, 0).toInstant(ZoneOffset.UTC), "com.fitbit", 900.0, null)
        )
        val result = WaterIntake.externalDailyLiters(drinks, own, zone, day, day.plusDays(1))
        assertEquals(mapOf(day to 0.2, day.plusDays(1) to 0.3), result)
    }
}
