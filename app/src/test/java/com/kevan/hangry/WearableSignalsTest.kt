package com.kevan.hangry

import com.kevan.hangry.data.local.entity.DailyHealthSummaryEntity
import com.kevan.hangry.data.local.entity.HeartRateSampleEntity
import com.kevan.hangry.domain.calculation.BodySignals
import com.kevan.hangry.domain.calculation.CycleTemperature
import com.kevan.hangry.domain.calculation.DayMetrics
import com.kevan.hangry.domain.calculation.HangryRecoveryCalculator
import com.kevan.hangry.domain.calculation.HealthMarkerCalculator
import com.kevan.hangry.domain.calculation.NightlyVitals
import com.kevan.hangry.domain.calculation.RecoveryConfig
import com.kevan.hangry.domain.model.NudgeText
import com.kevan.hangry.domain.model.RecoveryState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** Overnight vitals, the illness warning and cycle phase from wearable skin temperature. */
class WearableSignalsTest {

    private val today = LocalDate.of(2026, 10, 12)

    private fun day(date: LocalDate, rhr: Double? = 55.0, hrv: Double? = 60.0, resp: Double? = 14.0, temp: Double? = 0.0) =
        DailyHealthSummaryEntity(date = date, restingHeartRate = rhr, hrvRmssd = hrv, respiratoryRate = resp, skinTempDeltaCelsius = temp)

    private val normalHistory = (1..14).map { day(today.minusDays(it.toLong()), hrv = 58.0 + it % 5, temp = 0.05 * (it % 3)) }

    // region Body signals

    @Test
    fun `a normal night raises no signals`() {
        val result = BodySignals.evaluate(day(today, rhr = 56.0, hrv = 59.0, resp = 14.3, temp = 0.1), normalHistory)
        assertTrue(result.signals.isEmpty())
        assertFalse(result.flagged)
    }

    @Test
    fun `one reading off on its own is not flagged`() {
        val result = BodySignals.evaluate(day(today, temp = 0.9), normalHistory)
        assertEquals(listOf(BodySignals.Kind.SKIN_TEMP), result.signals.map { it.kind })
        assertFalse(result.flagged)
    }

    @Test
    fun `several readings off together are flagged`() {
        val result = BodySignals.evaluate(day(today, rhr = 60.0, hrv = 42.0, resp = 15.6, temp = 0.8), normalHistory)
        assertEquals(4, result.signals.size)
        assertTrue(result.flagged)
        val hrv = result.signals.first { it.kind == BodySignals.Kind.HRV }
        assertTrue("HRV change ${hrv.change}", hrv.change < -20)
    }

    @Test
    fun `too little history raises nothing`() {
        val result = BodySignals.evaluate(day(today, rhr = 70.0, temp = 2.0), normalHistory.take(3))
        assertTrue(result.signals.isEmpty())
    }

    @Test
    fun `the warmer half of the cycle is not taken for illness`() {
        val warm = day(today, temp = 0.6)
        assertTrue(BodySignals.evaluate(warm, normalHistory).signals.any { it.kind == BodySignals.Kind.SKIN_TEMP })
        assertTrue(BodySignals.evaluate(warm, normalHistory, lutealPhase = true).signals.none { it.kind == BodySignals.Kind.SKIN_TEMP })
    }

    @Test
    fun `signals survive storage`() {
        val signals = listOf(BodySignals.Signal(BodySignals.Kind.SKIN_TEMP, 0.7), BodySignals.Signal(BodySignals.Kind.RESTING_HR, 4.0))
        val decoded = BodySignals.decode(BodySignals.encode(signals))
        assertEquals(signals.map { it.kind }, decoded.map { it.kind })
        assertEquals(0.7, decoded[0].change, 0.001)
        assertNull(BodySignals.encode(emptyList()))
        assertTrue(BodySignals.decode(null).isEmpty())
        assertTrue(BodySignals.decode("NOT_A_KIND:1.0,garbage").isEmpty())
    }

    @Test
    fun `the morning brief leads with a body signals warning`() {
        val message = NudgeText.morning(48, RecoveryState.BALANCED, 420, null, null, listOf("resting heart rate +4 bpm", "skin temperature +0.7°C"))
        assertTrue(message.body.startsWith("Your body may be fighting something"))
        val quiet = NudgeText.morning(48, RecoveryState.BALANCED, 420, null, null, listOf("resting heart rate +4 bpm"))
        assertFalse(quiet.body.contains("fighting"))
    }

    // endregion

    // region Overnight vitals

    private val sleepStart = Instant.parse("2026-10-11T22:30:00Z")
    private val sleepEnd = Instant.parse("2026-10-12T06:30:00Z")
    private val dayStart = Instant.parse("2026-10-12T00:00:00Z")
    private val dayEnd = Instant.parse("2026-10-13T00:00:00Z")

    @Test
    fun `SpO2 uses readings taken during sleep, not daytime spot checks`() {
        val readings = listOf(
            NightlyVitals.Reading(sleepStart.plus(Duration.ofHours(2)), 95.0),
            NightlyVitals.Reading(sleepStart.plus(Duration.ofHours(4)), 97.0),
            NightlyVitals.Reading(Instant.parse("2026-10-12T14:00:00Z"), 99.0)
        )
        assertEquals(96.0, NightlyVitals.overnightAverage(readings, sleepStart, sleepEnd, dayStart, dayEnd)!!, 0.001)
    }

    @Test
    fun `without sleep the day's readings are averaged`() {
        val readings = listOf(NightlyVitals.Reading(Instant.parse("2026-10-12T14:00:00Z"), 15.0), NightlyVitals.Reading(Instant.parse("2026-10-12T15:00:00Z"), 17.0))
        assertEquals(16.0, NightlyVitals.overnightAverage(readings, null, null, dayStart, dayEnd)!!, 0.001)
        assertNull(NightlyVitals.overnightAverage(emptyList(), sleepStart, sleepEnd, dayStart, dayEnd))
    }

    @Test
    fun `sleeping heart rate averages samples during sleep`() {
        val samples = (0 until 60).map {
            HeartRateSampleEntity(recordFingerprint = "s$it", timestamp = sleepStart.plus(Duration.ofMinutes(it * 8L)), bpm = if (it % 2 == 0) 50.0 else 60.0)
        }
        assertEquals(55.0, NightlyVitals.averageSleepingBpm(samples, sleepStart, sleepEnd)!!, 0.5)
        assertNull(NightlyVitals.averageSleepingBpm(samples.take(3), sleepStart, sleepEnd))
    }

    // endregion

    // region Cycle

    private val periodStart = LocalDate.of(2026, 9, 20)

    /** Nights from the period start: low until [shiftDay], then 0.35 °C warmer. */
    private fun temps(nights: Int, shiftDay: Int?) = (0 until nights).associate { i ->
        periodStart.plusDays(i.toLong()) to (if (shiftDay != null && i + 1 >= shiftDay) 0.25 else -0.1) + 0.02 * (i % 2)
    }

    private fun stats(asOf: LocalDate) = HealthMarkerCalculator.cycleStats(
        listOf(LocalDate.of(2026, 8, 23) to LocalDate.of(2026, 8, 27), periodStart to LocalDate.of(2026, 9, 24)), asOf
    )

    @Test
    fun `a sustained temperature rise confirms the luteal phase`() {
        val asOf = periodStart.plusDays(19) // cycle day 20
        val insight = CycleTemperature.insight(stats(asOf), temps(20, shiftDay = 15), asOf)
        assertNotNull(insight)
        assertEquals(CycleTemperature.Phase.LUTEAL, insight!!.phase)
        assertEquals(periodStart.plusDays(14), insight.shiftDetectedOn)
        assertTrue(insight.shiftSizeC!! >= CycleTemperature.MIN_SHIFT_C)
    }

    @Test
    fun `no rise yet stays out of the luteal phase`() {
        val early = periodStart.plusDays(6) // cycle day 7, period over
        assertEquals(CycleTemperature.Phase.FOLLICULAR, CycleTemperature.insight(stats(early), temps(7, null), early)!!.phase)
        // Day 22 of a 28-day cycle would be luteal by the calendar, but temperature hasn't risen.
        val late = periodStart.plusDays(21)
        val insight = CycleTemperature.insight(stats(late), temps(22, shiftDay = null), late)!!
        assertFalse(insight.confirmedByTemperature)
        assertEquals(CycleTemperature.Phase.FOLLICULAR, insight.phase)
        assertFalse(CycleTemperature.isLuteal(listOf(periodStart), 28.0, temps(22, null), late))
    }

    @Test
    fun `a one-night spike is not a shift`() {
        val spiky = temps(20, shiftDay = null).toMutableMap().apply { put(periodStart.plusDays(12), 0.6) }
        val asOf = periodStart.plusDays(19)
        assertFalse(CycleTemperature.isLuteal(listOf(periodStart), 28.0, spiky, asOf))
    }

    @Test
    fun `without temperature the calendar estimate is used`() {
        assertTrue(CycleTemperature.isLuteal(listOf(periodStart), 28.0, emptyMap(), periodStart.plusDays(20)))
        assertFalse(CycleTemperature.isLuteal(listOf(periodStart), 28.0, emptyMap(), periodStart.plusDays(5)))
    }

    @Test
    fun `recovery allows for the luteal rise`() {
        val recovery = HangryRecoveryCalculator()
        val base = (1..14).map { DayMetrics(today.minusDays(it.toLong()), 450, 55.0, 60.0, skinTempDeltaCelsius = 0.0) }
        val warm = DayMetrics(today, 450, 55.0, 60.0, skinTempDeltaCelsius = 0.6)
        assertTrue(recovery.calculateRecovery(today, warm, base, RecoveryConfig()).negativeContributors.any { it.contains("Skin temperature") })
        assertTrue(recovery.calculateRecovery(today, warm.copy(lutealPhase = true), base, RecoveryConfig()).negativeContributors.none { it.contains("Skin temperature") })
    }

    // endregion
}
