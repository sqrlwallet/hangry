package com.kevan.hangry

import com.kevan.hangry.data.local.entity.ExerciseSessionEntity
import com.kevan.hangry.data.local.entity.HeartRateSampleEntity
import com.kevan.hangry.data.local.entity.HrvMeasurementEntity
import com.kevan.hangry.data.local.entity.SleepSessionEntity
import com.kevan.hangry.domain.calculation.DayMetrics
import com.kevan.hangry.domain.calculation.HangryRecoveryCalculator
import com.kevan.hangry.domain.calculation.HangrySleepCalculator
import com.kevan.hangry.domain.calculation.HangryStrainCalculator
import com.kevan.hangry.domain.calculation.HangryTrainingLoadCalculator
import com.kevan.hangry.domain.calculation.HeartRateZones
import com.kevan.hangry.domain.calculation.NightlyVitals
import com.kevan.hangry.domain.calculation.RecoveryConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** The specific inaccuracies fixed in scoring v5 / summaries v3. */
class ScoreAccuracyTest {

    private val zone = ZoneOffset.UTC
    private val dayStart = Instant.parse("2026-09-20T00:00:00Z")
    private val strain = HangryStrainCalculator(HangryTrainingLoadCalculator())
    private val sleep = HangrySleepCalculator(zone)
    private val recovery = HangryRecoveryCalculator()

    private fun samples(from: Instant, minutes: Int, bpm: (Int) -> Double) =
        (0 until minutes).map { HeartRateSampleEntity(recordFingerprint = "hr-$from-$it", timestamp = from.plusSeconds(it * 60L), bpm = bpm(it)) }

    private fun night(wakeDay: Int, bed: String, wake: String, asleep: Int, inBed: Int? = null, id: String = "n$wakeDay"): SleepSessionEntity {
        val wakeAt = LocalDate.of(2026, 9, wakeDay).atTime(LocalTime.parse(wake)).toInstant(zone)
        val bedTime = LocalTime.parse(bed)
        val bedDay = if (bedTime.isAfter(LocalTime.parse(wake))) LocalDate.of(2026, 9, wakeDay - 1) else LocalDate.of(2026, 9, wakeDay)
        return SleepSessionEntity(
            recordFingerprint = id, startTime = bedDay.atTime(bedTime).toInstant(zone), endTime = wakeAt,
            durationMinutes = asleep, timeInBedMinutes = inBed
        )
    }

    // region Strain

    @Test
    fun `a day of just wearing the watch is low strain, not near maximum`() {
        // 16 hours awake at 70 bpm, one reading a minute. Used to score ~20/21.
        val result = strain.calculateDayStrain(samples(dayStart.plusSeconds(7 * 3600), 16 * 60) { 70.0 }, emptyList(), HeartRateZones.forUser(35, null, 60.0))
        assertTrue("resting day strain ${result.dayStrain}", result.dayStrain < 1.0)
    }

    @Test
    fun `heart rate while asleep earns no strain`() {
        val night = SleepSessionEntity(recordFingerprint = "s", startTime = dayStart, endTime = dayStart.plusSeconds(8 * 3600), durationMinutes = 480)
        // Pretend the sleep trace is high (a fever, or a bad sensor): it still isn't activity.
        val result = strain.calculateDayStrain(samples(dayStart, 8 * 60) { 130.0 }, emptyList(), HeartRateZones.DEFAULT, listOf(night))
        assertEquals(0.0, result.dayStrain, 0.001)
    }

    @Test
    fun `an hour of hard running lands in the upper range`() {
        val zones = HeartRateZones.forUser(30, null, 55.0) // max 187
        val run = strain.calculateDayStrain(samples(dayStart.plusSeconds(8 * 3600), 60) { 165.0 }, emptyList(), zones)
        val easyWalk = strain.calculateDayStrain(samples(dayStart.plusSeconds(8 * 3600), 60) { 115.0 }, emptyList(), zones)
        assertTrue("hard hour ${run.dayStrain}", run.dayStrain in 14.0..19.0)
        assertTrue("easy hour ${easyWalk.dayStrain}", easyWalk.dayStrain in 3.0..10.0)
    }

    @Test
    fun `the same heart rate is harder work for an older person`() {
        val trace = samples(dayStart.plusSeconds(8 * 3600), 45) { 140.0 }
        val young = strain.calculateDayStrain(trace, emptyList(), HeartRateZones.forUser(20, null, 60.0))
        val older = strain.calculateDayStrain(trace, emptyList(), HeartRateZones.forUser(60, null, 60.0))
        assertTrue(older.dayStrain > young.dayStrain)
    }

    @Test
    fun `a workout recorded without the watch still adds strain`() {
        val trace = samples(dayStart.plusSeconds(7 * 3600), 60) { 70.0 }
        val phoneRun = ExerciseSessionEntity(
            recordFingerprint = "run", exerciseType = "RUNNING", startTime = dayStart.plusSeconds(18 * 3600),
            endTime = dayStart.plusSeconds(18 * 3600 + 45 * 60), durationMinutes = 45, activeCalories = 450.0
        )
        val without = strain.calculateDayStrain(trace, emptyList())
        val with = strain.calculateDayStrain(trace, listOf(phoneRun))
        assertTrue(with.dayStrain > without.dayStrain + 5)
    }

    @Test
    fun `max heart rate comes from the setting, else from age`() {
        assertEquals(180.0, HeartRateZones.estimateMaxHr(40), 0.01)
        assertEquals(195.0, HeartRateZones.forUser(40, 195, 60.0).maxHr, 0.01)
        // Zone 1 starts at 50% of heart-rate reserve: 60 + 0.5 x 120 = 120.
        assertEquals(120.0, HeartRateZones.forUser(40, null, 60.0).lowerBounds[0], 0.01)
        assertEquals(-1, HeartRateZones.forUser(40, null, 60.0).zoneIndex(100.0))
    }

    // endregion

    // region Overnight vitals

    @Test
    fun `resting heart rate is the lowest sustained overnight value, not one glitchy reading`() {
        val start = dayStart
        val trace = samples(start, 8 * 60) { i -> if (i == 200) 38.0 else if (i in 240..300) 52.0 else 58.0 }
        val rhr = NightlyVitals.lowestSustainedBpm(trace, start, start.plusSeconds(8 * 3600))!!
        assertTrue("rhr $rhr", rhr in 51.0..53.0)
    }

    @Test
    fun `overnight HRV beats a later daytime spot reading`() {
        val sleepStart = dayStart
        val sleepEnd = dayStart.plusSeconds(8 * 3600)
        fun reading(at: Instant, v: Double, id: String) = HrvMeasurementEntity(recordFingerprint = id, recordDate = LocalDate.of(2026, 9, 20), timestamp = at, rmssd = v)
        val readings = listOf(
            reading(sleepStart.plusSeconds(3600), 60.0, "a"),
            reading(sleepStart.plusSeconds(5 * 3600), 70.0, "b"),
            reading(sleepEnd.plusSeconds(6 * 3600), 25.0, "stressful-afternoon")
        )
        assertEquals(65.0, NightlyVitals.nightlyHrv(readings, sleepStart, sleepEnd)!!, 0.001)
    }

    // endregion

    // region Sleep

    @Test
    fun `time awake in bed isn't counted as sleep need met`() {
        // The reported case: 7h 6m in bed, 6h 6m of sleep stages, an 8h goal.
        val last = night(25, "23:00", "06:06", asleep = 366, inBed = 426).copy(deepSleepMinutes = 31, remSleepMinutes = 108, lightSleepMinutes = 227, awakeMinutes = 20)
        val history = (18..24).map { night(it, "23:00", "06:30", asleep = 420, inBed = 450) }
        val a = sleep.analyzeSleep(last, history.reversed(), targetDurationMinutes = 480)
        assertTrue("need ${a.sleepNeedMinutes}", a.sleepNeedMinutes >= 480)
        assertTrue("performance ${a.sleepPerformancePercentage}", a.sleepPerformancePercentage!! < 80)
        assertEquals(86, a.efficiencyPercentage)
        assertNotNull(a.sleepScore)
    }

    @Test
    fun `short nights build debt that raises tonight's need`() {
        val rested = (18..24).map { night(it, "23:00", "07:00", asleep = 480) }.reversed()
        val short = (18..24).map { night(it, "01:00", "06:00", asleep = 300) }.reversed()
        val a = sleep.analyzeSleep(null, rested, targetDurationMinutes = 480)
        val b = sleep.analyzeSleep(null, short, targetDurationMinutes = 480)
        assertEquals(480, a.sleepNeedMinutes)
        // 7 nights x 3h short = 21h of debt, repaid over 14 nights = 90 min, capped at an hour.
        assertEquals(540, b.sleepNeedMinutes)
    }

    @Test
    fun `sleep debt is repaid over two weeks, not in one night`() {
        // Three nights an hour short: 3h of debt / 14 nights = ~13 extra minutes, not 3 hours.
        val nights = (22..24).map { night(it, "00:00", "07:00", asleep = 420) }.reversed()
        val a = sleep.analyzeSleep(null, nights, targetDurationMinutes = 480)
        assertEquals(493, a.sleepNeedMinutes)
    }

    @Test
    fun `a long night pays back earlier debt`() {
        val short = night(22, "00:00", "06:00", asleep = 360)          // 2h short
        val long = night(23, "22:00", "08:00", asleep = 600)           // 2h over
        val a = sleep.analyzeSleep(null, listOf(long, short), targetDurationMinutes = 480)
        assertEquals(480, a.sleepNeedMinutes)
    }

    @Test
    fun `naps don't count as extra nights`() {
        val nights = (18..24).map { night(it, "23:00", "07:00", asleep = 480) }
        val naps = (18..24).map { night(it, "14:00", "14:30", asleep = 30, id = "nap$it") }
        val a = sleep.analyzeSleep(null, (nights + naps).sortedByDescending { it.startTime }, targetDurationMinutes = 480)
        assertEquals(480, a.sevenDayAverageMinutes)
    }

    @Test
    fun `wake times around midnight average to midnight, not noon`() {
        val lateNights = listOf(
            night(24, "16:00", "23:50", asleep = 470),
            night(23, "16:10", "00:10", asleep = 470),
            night(22, "16:00", "23:50", asleep = 470)
        )
        val a = sleep.analyzeSleep(null, lateNights, targetDurationMinutes = 480)
        // Wake ~00:00 minus 8h need = ~16:00, not ~04:00 from a noon average.
        val bedtime = a.recommendedBedtime!!
        assertTrue("bedtime $bedtime", bedtime.hour in 15..16)
    }

    @Test
    fun `consistency measures regular timing, not similar lengths`() {
        val regular = (18..24).map { night(it, "23:00", "07:00", asleep = 480) }.reversed()
        val shifting = (18..24).map { d -> if (d % 2 == 0) night(d, "22:00", "06:00", asleep = 480) else night(d, "02:00", "10:00", asleep = 480) }.reversed()
        val a = sleep.analyzeSleep(null, regular, targetDurationMinutes = 480)
        val b = sleep.analyzeSleep(null, shifting, targetDurationMinutes = 480)
        assertTrue(a.consistencyPercentage!! >= 95)
        assertTrue("shifting ${b.consistencyPercentage}", b.consistencyPercentage!! < 40)
    }

    // endregion

    // region Recovery

    private fun history(days: Int, hrv: (Int) -> Double?, rhr: (Int) -> Double? = { 55.0 }, sleepMin: Int = 450, resp: Double? = null) =
        (1..days).map { DayMetrics(LocalDate.of(2026, 9, 20).minusDays(it.toLong()), sleepMin, rhr(it), hrv(it), respiratoryRate = resp) }

    @Test
    fun `sleeping more than you needed scores sleep highly`() {
        val today = DayMetrics(LocalDate.of(2026, 9, 20), 426, 55.0, 60.0, sleepNeedMinutes = 399)
        // History slept longer than today, which used to drag the sleep part to ~62.
        val result = recovery.calculateRecovery(LocalDate.of(2026, 9, 20), today, history(14, { 60.0 + it % 3 }, sleepMin = 460), RecoveryConfig())
        assertTrue("sleep part ${result.sleepScore}", result.sleepScore!! >= 90)
    }

    @Test
    fun `a drop only matters relative to how much your HRV usually varies`() {
        val steady = history(30, { 60.0 + (it % 2) })          // +/- 1 ms
        val volatile = history(30, { if (it % 2 == 0) 45.0 else 75.0 })
        val today = DayMetrics(LocalDate.of(2026, 9, 20), 450, 55.0, 52.0)
        val onSteady = recovery.calculateRecovery(today.date, today, steady, RecoveryConfig()).hrvScore!!
        val onVolatile = recovery.calculateRecovery(today.date, today, volatile, RecoveryConfig()).hrvScore!!
        assertTrue("steady $onSteady vs volatile $onVolatile", onSteady < onVolatile - 10)
    }

    @Test
    fun `at your normal HRV scores 65`() {
        val today = DayMetrics(LocalDate.of(2026, 9, 20), 450, 55.0, 60.0)
        val r = recovery.calculateRecovery(today.date, today, history(30, { 60.0 }), RecoveryConfig())
        assertEquals(65.0, r.hrvScore!!, 0.5)
    }

    @Test
    fun `resting heart rate loses 2 points per bpm above the monthly average`() {
        val base = history(30, { 60.0 }, rhr = { 55.0 })
        fun rhrScore(today: Double) =
            recovery.calculateRecovery(LocalDate.of(2026, 9, 20), DayMetrics(LocalDate.of(2026, 9, 20), 450, today, 60.0), base, RecoveryConfig()).rhrScore!!
        assertEquals(100.0, rhrScore(55.0), 0.01)
        assertEquals(100.0, rhrScore(52.0), 0.01)
        assertEquals(98.0, rhrScore(56.0), 0.01)
        assertEquals(96.0, rhrScore(57.0), 0.01)
        assertEquals(94.0, rhrScore(58.0), 0.01)
    }

    @Test
    fun `strain over yesterday's target lowers recovery, in range or lighter doesn't`() {
        val base = history(14, { 60.0 })
        fun result(strain: Double) = recovery.calculateRecovery(
            LocalDate.of(2026, 9, 20),
            DayMetrics(LocalDate.of(2026, 9, 20), 450, 55.0, 60.0, previousDayStrain = strain, previousDayStrainTarget = 8.0..12.0),
            base, RecoveryConfig()
        )
        assertEquals(100.0, result(10.0).trainingLoadScore!!, 0.01)
        assertEquals(100.0, result(4.0).trainingLoadScore!!, 0.01)
        assertTrue(result(4.0).positiveContributors.any { it.contains("room to push") })
        assertEquals(64.0, result(15.0).trainingLoadScore!!, 0.01)
        assertTrue(result(15.0).score!! < result(10.0).score!!)
        assertTrue(result(15.0).negativeContributors.any { it.contains("went over") })
    }

    @Test
    fun `a raised breathing rate lowers recovery`() {
        val base = history(14, { 60.0 }, resp = 14.0)
        val normal = DayMetrics(LocalDate.of(2026, 9, 20), 450, 55.0, 60.0, respiratoryRate = 14.2)
        val raised = normal.copy(respiratoryRate = 16.5)
        val a = recovery.calculateRecovery(normal.date, normal, base, RecoveryConfig()).score!!
        val b = recovery.calculateRecovery(raised.date, raised, base, RecoveryConfig())
        assertTrue(b.score!! <= a - 8)
        assertTrue(b.negativeContributors.any { it.contains("Breathing rate") })
    }

    // endregion
}
