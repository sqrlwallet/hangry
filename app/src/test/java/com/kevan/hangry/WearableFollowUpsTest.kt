package com.kevan.hangry

import com.kevan.hangry.data.datasource.RoutePoint
import com.kevan.hangry.data.local.entity.DailyHealthSummaryEntity
import com.kevan.hangry.data.repository.ReadingsByMonth
import com.kevan.hangry.domain.calculation.BodySignals
import com.kevan.hangry.domain.calculation.CycleTemperature
import com.kevan.hangry.domain.calculation.HealthMarkerCalculator
import com.kevan.hangry.domain.calculation.NightlyVitals
import com.kevan.hangry.domain.calculation.RouteStats
import com.kevan.hangry.domain.model.DashboardWidget
import com.kevan.hangry.domain.model.TemperatureText
import com.kevan.hangry.domain.model.WidgetType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** One source per night, month-sized reads, personal thresholds, cycle learning, routes, units. */
class WearableFollowUpsTest {

    private val sleepStart = Instant.parse("2026-10-11T22:30:00Z")
    private val sleepEnd = Instant.parse("2026-10-12T06:30:00Z")
    private val dayStart = Instant.parse("2026-10-12T00:00:00Z")
    private val dayEnd = Instant.parse("2026-10-13T00:00:00Z")

    @Test
    fun `two devices on one night - the one with more readings is used`() {
        val watch = (1..6).map { NightlyVitals.Reading(sleepStart.plus(Duration.ofHours(it.toLong())), 95.0, "watch") }
        val ring = (1..2).map { NightlyVitals.Reading(sleepStart.plus(Duration.ofHours(it.toLong())), 99.0, "ring") }
        assertEquals(95.0, NightlyVitals.overnightAverage(watch + ring, sleepStart, sleepEnd, dayStart, dayEnd)!!, 0.001)
    }

    @Test
    fun `readings are fetched once per month`() = runBlocking {
        val calls = mutableListOf<Pair<Instant, Instant>>()
        val byMonth = ReadingsByMonth(ZoneOffset.UTC) { from, to -> calls += from to to; emptyList() }
        byMonth.around(LocalDate.of(2026, 9, 1))
        byMonth.around(LocalDate.of(2026, 9, 30))
        byMonth.around(LocalDate.of(2026, 10, 1))
        assertEquals(2, calls.size)
        // A day either side, so nights crossing the month edge are whole.
        assertEquals(Instant.parse("2026-08-31T00:00:00Z"), calls[0].first)
        assertEquals(Instant.parse("2026-10-02T00:00:00Z"), calls[0].second)
    }

    @Test
    fun `a failed month read gives no readings instead of failing the sync`() = runBlocking {
        val byMonth = ReadingsByMonth(ZoneOffset.UTC) { _, _ -> throw IllegalStateException("revoked") }
        assertTrue(byMonth.around(LocalDate.of(2026, 9, 5)).isEmpty())
    }

    // region Personal thresholds

    private val today = LocalDate.of(2026, 10, 12)
    private fun history(rhr: (Int) -> Double) = (1..20).map { DailyHealthSummaryEntity(date = today.minusDays(it.toLong()), restingHeartRate = rhr(it)) }

    @Test
    fun `a steady person is flagged at the minimum`() {
        val steady = history { 55.0 + (it % 2) * 0.4 }
        val result = BodySignals.evaluate(DailyHealthSummaryEntity(date = today, restingHeartRate = 58.5), steady)
        assertTrue(result.signals.any { it.kind == BodySignals.Kind.RESTING_HR })
    }

    @Test
    fun `someone whose readings swing a lot needs a bigger move`() {
        val swingy = history { if (it % 2 == 0) 51.0 else 59.0 } // ±4 bpm night to night
        val result = BodySignals.evaluate(DailyHealthSummaryEntity(date = today, restingHeartRate = 58.5), swingy)
        assertTrue(result.signals.none { it.kind == BodySignals.Kind.RESTING_HR })
        assertTrue(BodySignals.evaluate(DailyHealthSummaryEntity(date = today, restingHeartRate = 63.0), swingy).signals.any { it.kind == BodySignals.Kind.RESTING_HR })
    }

    @Test
    fun `body check lists every reading with its usual`() {
        val past = (1..10).map { DailyHealthSummaryEntity(date = today.minusDays(it.toLong()), restingHeartRate = 55.0, hrvRmssd = 60.0 + it % 3) }
        val check = BodySignals.compare(DailyHealthSummaryEntity(date = today, restingHeartRate = 55.0, hrvRmssd = 40.0), past)
        assertEquals(listOf(BodySignals.Kind.RESTING_HR, BodySignals.Kind.HRV), check.map { it.kind })
        val hrv = check.first { it.kind == BodySignals.Kind.HRV }
        assertEquals(61.0, hrv.usual!!, 0.5)
        assertTrue(hrv.off)
        assertTrue(hrv.change!! < -30)
        assertFalse(check.first { it.kind == BodySignals.Kind.RESTING_HR }.off)
    }

    @Test
    fun `body check says when it is still learning`() {
        val check = BodySignals.compare(DailyHealthSummaryEntity(date = today, skinTempDeltaCelsius = 0.3), emptyList())
        assertNull(check.single().usual)
        assertFalse(check.single().off)
    }

    // endregion

    // region Cycle learning

    /** Three 28-day cycles, each with a clear shift on [shiftDay]. */
    private val starts = listOf(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 29), LocalDate.of(2026, 8, 26), LocalDate.of(2026, 9, 23))
    private fun temps(shiftDay: Int): Map<LocalDate, Double> = starts.dropLast(1).flatMap { start ->
        (0 until 28).map { i -> start.plusDays(i.toLong()) to (if (i + 1 >= shiftDay) 0.3 else -0.1) + 0.01 * (i % 3) }
    }.toMap()

    @Test
    fun `luteal length is learned from past cycles`() {
        // Shift on day 18 -> ovulation day 17 -> 28 - 17 + 1 = 12 days to the next period.
        assertEquals(12, CycleTemperature.personalLutealDays(starts, temps(18)))
        assertNull(CycleTemperature.personalLutealDays(starts, emptyMap()))
    }

    @Test
    fun `a confirmed shift predicts the next period from your own luteal length`() {
        // Three warm nights (days 18-20) are needed to call the shift.
        val all = temps(18) + (0 until 20).associate { i -> starts.last().plusDays(i.toLong()) to (if (i + 1 >= 18) 0.3 else -0.1) + 0.01 * (i % 3) }
        val asOf = starts.last().plusDays(19)
        val periods = starts.map { it to it.plusDays(4) }
        val insight = CycleTemperature.insight(HealthMarkerCalculator.cycleStats(periods, asOf), all, asOf, starts)!!
        assertEquals(12, insight.personalLutealDays)
        assertEquals(starts.last().plusDays(17).minusDays(1).plusDays(12), insight.predictedNextStart)
    }

    // endregion

    // region Routes

    /** Points heading north at ~1 m per 0.000009° latitude, 1 point per 10 m, 5 min/km. */
    private fun northRun(meters: Int, altitude: (Int) -> Double? = { null }): List<RoutePoint> {
        val start = Instant.parse("2026-10-12T07:00:00Z")
        return (0..meters / 10).map { i ->
            RoutePoint(start.plusSeconds(i * 3L), 51.5 + i * 10 / 111_195.0, -0.12, altitude(i))
        }
    }

    @Test
    fun `kilometre splits come out at the right pace`() {
        val splits = RouteStats.splits(northRun(2500))
        assertEquals(3, splits.size)
        assertEquals(300.0, splits[0].paceSecondsPerKm, 3.0)
        assertEquals(1000.0, splits[1].meters, 0.001)
        assertEquals(500.0, splits[2].meters, 5.0)
    }

    @Test
    fun `climb ignores GPS wobble`() {
        val wobbly = RouteStats.elevationProfile(northRun(1000) { i -> 10.0 + (i % 2) * 1.0 })
        assertEquals(0.0, RouteStats.climbMeters(wobbly), 0.001)
        val hill = RouteStats.elevationProfile(northRun(1000) { i -> 10.0 + i * 0.5 })
        assertEquals(50.0, RouteStats.climbMeters(hill), 1.0)
        assertTrue(RouteStats.elevationProfile(northRun(1000)).isEmpty())
    }

    // endregion

    @Test
    fun `temperature changes convert to Fahrenheit as a change`() {
        assertEquals("+0.5 °C", TemperatureText.delta(0.5, fahrenheit = false))
        assertEquals("+0.9 °F", TemperatureText.delta(0.5, fahrenheit = true))
        assertEquals("-0.2 °C", TemperatureText.delta(-0.2, fahrenheit = false))
    }

    @Test
    fun `new dashboard cards are in the defaults`() {
        val types = DashboardWidget.createDefaultWidgets().map { it.type }
        assertTrue(types.containsAll(listOf(WidgetType.BODY_CHECK, WidgetType.OVERNIGHT_VITALS, WidgetType.CYCLE)))
        assertEquals(types.size, DashboardWidget.createDefaultWidgets().map { it.id }.toSet().size)
    }
}
