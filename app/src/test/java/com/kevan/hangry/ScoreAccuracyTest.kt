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
import com.kevan.hangry.domain.calculation.SleepSessions
import com.kevan.hangry.domain.model.SleepStage
import com.kevan.hangry.domain.model.SleepStageCodec
import com.kevan.hangry.domain.model.SleepStageSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.math.roundToInt

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
    fun `naps add to each day's total, not as extra nights`() {
        val nights = (18..24).map { night(it, "23:00", "07:00", asleep = 480) }
        val naps = (18..24).map { night(it, "14:00", "14:30", asleep = 30, id = "nap$it") }
        val a = sleep.analyzeSleep(null, (nights + naps).sortedByDescending { it.startTime }, targetDurationMinutes = 480)
        // Seven nights of 8h, each with a 30-minute nap folded in - still seven nights, now 8.5h each.
        assertEquals(510, a.sevenDayAverageMinutes)
    }

    @Test
    fun `a nap after waking lowers tonight's need minute for minute`() {
        val rested = (18..24).map { night(it, "23:00", "07:00", asleep = 480) }.reversed()
        val lastNight = night(25, "23:00", "07:00", asleep = 480)
        val nap = night(25, "14:00", "14:45", asleep = 45, id = "nap25")
        val day = listOf(lastNight, nap)
        val a = sleep.analyzeSleep(SleepSessions.combine(day), rested, targetDurationMinutes = 480, currentDaySessions = day, date = LocalDate.of(2026, 9, 25))
        // Last night's need is untouched; the nap counts toward tonight, not as last night's sleep.
        assertEquals(480, a.sleepNeedMinutes)
        assertEquals(435, a.tonightsNeedMinutes)
    }

    @Test
    fun `yesterday afternoon's nap lowers last night's need`() {
        val rested = (18..24).map { night(it, "23:00", "07:00", asleep = 480) }
        val nap = night(24, "14:00", "15:00", asleep = 60, id = "nap24")
        val history = (rested + nap).sortedByDescending { it.startTime }
        val lastNight = night(25, "23:00", "06:00", asleep = 420)
        val a = sleep.analyzeSleep(lastNight, history, targetDurationMinutes = 480, date = LocalDate.of(2026, 9, 25))
        assertEquals(420, a.sleepNeedMinutes)
        assertEquals(100, a.sleepPerformancePercentage)
        // The nap paid for that night, so the shorter night didn't leave debt behind it either.
        assertEquals(480 + (60 / 14.0).roundToInt(), a.tonightsNeedMinutes)
    }

    @Test
    fun `a nap's credit is capped at 90 minutes`() {
        val rested = (18..24).map { night(it, "23:00", "07:00", asleep = 480) }.reversed()
        val day = listOf(night(25, "23:00", "07:00", asleep = 480), night(25, "13:00", "16:00", asleep = 180, id = "nap25"))
        val a = sleep.analyzeSleep(SleepSessions.combine(day), rested, targetDurationMinutes = 480, currentDaySessions = day)
        assertEquals(390, a.tonightsNeedMinutes)
    }

    @Test
    fun `consistency looks back two weeks, not one`() {
        // The last week is perfectly regular; the week before kept very different hours.
        val lastWeek = (18..24).map { night(it, "23:00", "07:00", asleep = 480) }
        val weekBefore = (11..17).map { night(it, "03:00", "11:00", asleep = 480) }
        val a = sleep.analyzeSleep(null, (lastWeek + weekBefore).sortedByDescending { it.startTime }, targetDurationMinutes = 480)
        assertTrue("consistency ${a.consistencyPercentage}", a.consistencyPercentage!! < 50)
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

    private fun staged(wakeDay: Int, bed: String, wake: String, awake: List<Pair<String, String>>, deep: Int = 90, rem: Int = 100): SleepSessionEntity {
        val base = night(wakeDay, bed, wake, asleep = 0)
        fun at(t: String): Instant {
            val time = LocalTime.parse(t)
            val day = if (time.isAfter(LocalTime.parse(wake))) LocalDate.of(2026, 9, wakeDay - 1) else LocalDate.of(2026, 9, wakeDay)
            return day.atTime(time).toInstant(zone)
        }
        // Light sleep throughout, with the given awake spells cut out of it.
        val cuts = awake.map { at(it.first) to at(it.second) }
        val segments = mutableListOf<SleepStageSegment>()
        var cursor = base.startTime
        cuts.forEach { (from, to) ->
            segments += SleepStageSegment(SleepStage.LIGHT, cursor, from)
            segments += SleepStageSegment(SleepStage.AWAKE, from, to)
            cursor = to
        }
        segments += SleepStageSegment(SleepStage.LIGHT, cursor, base.endTime)
        val awakeMin = cuts.sumOf { Duration.between(it.first, it.second).toMinutes() }.toInt()
        val inBed = Duration.between(base.startTime, base.endTime).toMinutes().toInt()
        return base.copy(
            durationMinutes = inBed - awakeMin, timeInBedMinutes = inBed, awakeMinutes = awakeMin,
            deepSleepMinutes = deep, remSleepMinutes = rem, lightSleepMinutes = inBed - awakeMin - deep - rem,
            stageSegments = SleepStageCodec.encode(base.startTime, segments)
        )
    }

    @Test
    fun `an unbroken night scores full marks for interruptions`() {
        val a = sleep.analyzeSleep(staged(25, "23:00", "07:00", awake = emptyList()), emptyList(), targetDurationMinutes = 480)
        assertEquals(100, a.interruptionsScore)
        assertEquals(0, a.awakenings)
        assertEquals(0, a.wakeAfterSleepOnsetMinutes)
    }

    @Test
    fun `waking up in the night costs interruption points`() {
        val broken = staged(25, "23:00", "07:30", awake = listOf("01:00" to "01:10", "03:00" to "03:10", "05:00" to "05:10"))
        val a = sleep.analyzeSleep(broken, emptyList(), targetDurationMinutes = 480)
        // 30 min awake: (90 - 30) / 70 = 86; 3 awakenings: 100 - 2 x 20 = 60; averaged = 73.
        assertEquals(30, a.wakeAfterSleepOnsetMinutes)
        assertEquals(3, a.awakenings)
        assertEquals(73, a.interruptionsScore)
        val unbroken = sleep.analyzeSleep(staged(25, "23:00", "07:30", awake = emptyList()), emptyList(), targetDurationMinutes = 480)
        assertTrue("broken ${a.sleepScore} vs unbroken ${unbroken.sleepScore}", a.sleepScore!! < unbroken.sleepScore!!)
    }

    @Test
    fun `brief stirrings are awake time but not awakenings, and touching spells are one`() {
        val night = staged(25, "23:00", "07:00", awake = listOf("01:00" to "01:03", "03:00" to "03:04"))
        val a = sleep.analyzeSleep(night, emptyList(), targetDurationMinutes = 480)
        assertEquals(7, a.wakeAfterSleepOnsetMinutes)
        assertEquals(0, a.awakenings)
        // Two awake segments a few seconds apart count as one 8-minute spell.
        val segments = listOf(
            SleepStageSegment(SleepStage.LIGHT, night.startTime, night.startTime.plusSeconds(3600)),
            SleepStageSegment(SleepStage.AWAKE, night.startTime.plusSeconds(3600), night.startTime.plusSeconds(3600 + 240)),
            SleepStageSegment(SleepStage.AWAKE, night.startTime.plusSeconds(3600 + 270), night.startTime.plusSeconds(3600 + 480)),
            SleepStageSegment(SleepStage.LIGHT, night.startTime.plusSeconds(3600 + 480), night.endTime)
        )
        val b = sleep.analyzeSleep(night.copy(stageSegments = SleepStageCodec.encode(night.startTime, segments)), emptyList(), targetDurationMinutes = 480)
        assertEquals(1, b.awakenings)
    }

    @Test
    fun `awake time before falling asleep and after the last sleep isn't an interruption`() {
        val base = staged(25, "23:00", "07:00", awake = emptyList())
        val segments = listOf(
            SleepStageSegment(SleepStage.AWAKE, base.startTime, base.startTime.plusSeconds(30 * 60)),
            SleepStageSegment(SleepStage.LIGHT, base.startTime.plusSeconds(30 * 60), base.endTime.minusSeconds(20 * 60)),
            SleepStageSegment(SleepStage.AWAKE, base.endTime.minusSeconds(20 * 60), base.endTime)
        )
        val a = sleep.analyzeSleep(base.copy(stageSegments = SleepStageCodec.encode(base.startTime, segments)), emptyList(), targetDurationMinutes = 480)
        assertEquals(100, a.interruptionsScore)
    }

    @Test
    fun `without a stage timeline interruptions are left out, not guessed`() {
        val a = sleep.analyzeSleep(night(25, "23:00", "07:00", asleep = 450, inBed = 480).copy(awakeMinutes = 30), emptyList(), targetDurationMinutes = 480)
        assertEquals(null, a.interruptionsScore)
        assertNotNull(a.sleepScore)
    }

    @Test
    fun `deep and REM are scored in minutes, so a short night can't max them`() {
        // A 5-hour night with a textbook mix (15% deep, 20% REM) used to score 100 here.
        val short = night(25, "02:00", "07:00", asleep = 300).copy(deepSleepMinutes = 45, remSleepMinutes = 60, lightSleepMinutes = 195)
        val full = night(25, "23:00", "07:00", asleep = 480).copy(deepSleepMinutes = 90, remSleepMinutes = 96, lightSleepMinutes = 294)
        val a = sleep.analyzeSleep(short, emptyList(), targetDurationMinutes = 480, age = 30)
        val b = sleep.analyzeSleep(full, emptyList(), targetDurationMinutes = 480, age = 30)
        // Deep 45/90 and REM 60/90, averaged.
        assertEquals(58, a.restorativeScore)
        assertEquals(100, b.restorativeScore)
    }

    @Test
    fun `less deep sleep is expected with age`() {
        assertEquals(90.0, HangrySleepCalculator.deepTargetMinutes(25), 0.0)
        assertEquals(90.0, HangrySleepCalculator.deepTargetMinutes(30), 0.0)
        assertEquals(75.0, HangrySleepCalculator.deepTargetMinutes(50), 0.0)
        assertEquals(60.0, HangrySleepCalculator.deepTargetMinutes(80), 0.0)
        assertEquals(80.0, HangrySleepCalculator.deepTargetMinutes(null), 0.0)
        val night = night(25, "23:00", "07:00", asleep = 480).copy(deepSleepMinutes = 60, remSleepMinutes = 90, lightSleepMinutes = 330)
        assertEquals(100, sleep.analyzeSleep(night, emptyList(), targetDurationMinutes = 480, age = 70).restorativeScore)
        assertTrue(sleep.analyzeSleep(night, emptyList(), targetDurationMinutes = 480, age = 25).restorativeScore!! < 100)
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
    fun `resting heart rate loses 7 points per SD above the monthly normal`() {
        // A perfectly steady 55 bpm: the SD floor of 1.5 bpm applies.
        val base = history(30, { 60.0 }, rhr = { 55.0 })
        fun rhrScore(today: Double) =
            recovery.calculateRecovery(LocalDate.of(2026, 9, 20), DayMetrics(LocalDate.of(2026, 9, 20), 450, today, 60.0), base, RecoveryConfig()).rhrScore!!
        assertEquals(100.0, rhrScore(55.0), 0.01)
        assertEquals(100.0, rhrScore(52.0), 0.01)
        assertEquals(100.0 - 7 / 1.5, rhrScore(56.0), 0.01)
        assertEquals(100.0 - 14 / 1.5, rhrScore(57.0), 0.01)
        assertEquals(86.0, rhrScore(58.0), 0.01)
    }

    @Test
    fun `the same rise counts for less when your resting heart rate usually wanders`() {
        fun rhrScore(history: List<DayMetrics>) = recovery.calculateRecovery(
            LocalDate.of(2026, 9, 20), DayMetrics(LocalDate.of(2026, 9, 20), 450, 58.0, 60.0), history, RecoveryConfig()
        ).rhrScore!!
        val steady = rhrScore(history(30, { 60.0 }, rhr = { 55.0 }))
        val wandering = rhrScore(history(30, { 60.0 }, rhr = { if (it % 2 == 0) 51.0 else 59.0 }))
        assertTrue("steady $steady vs wandering $wandering", wandering > steady + 5)
    }

    @Test
    fun `an unusually low resting heart rate costs a little, a modest dip doesn't`() {
        val base = history(30, { 60.0 }, rhr = { 55.0 })
        val result = { today: Double ->
            recovery.calculateRecovery(LocalDate.of(2026, 9, 20), DayMetrics(LocalDate.of(2026, 9, 20), 450, today, 60.0), base, RecoveryConfig())
        }
        // 3 bpm down is good news; 10 bpm down (over 2 SD and 8 bpm) is flagged and costs a few points.
        assertEquals(100.0, result(52.0).rhrScore!!, 0.01)
        assertTrue(result(52.0).positiveContributors.any { it.startsWith("Resting heart rate is 3 bpm below") })
        val veryLow = result(45.0)
        assertEquals(100.0 - 7 * (10 / 1.5 - 8 / 1.5), veryLow.rhrScore!!, 0.01)
        assertTrue(veryLow.negativeContributors.any { it.contains("unusually low") })
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

    @Test
    fun `skin temperature running warm lowers recovery`() {
        val base = history(14, { 60.0 }).map { it.copy(skinTempDeltaCelsius = 0.1) }
        val normal = DayMetrics(LocalDate.of(2026, 9, 20), 450, 55.0, 60.0, skinTempDeltaCelsius = 0.2)
        val warm = normal.copy(skinTempDeltaCelsius = 1.0)
        val a = recovery.calculateRecovery(normal.date, normal, base, RecoveryConfig())
        val b = recovery.calculateRecovery(warm.date, warm, base, RecoveryConfig())
        assertTrue(a.negativeContributors.none { it.contains("Skin temperature") })
        assertTrue(b.score!! <= a.score!! - 8)
        assertTrue(b.negativeContributors.any { it.contains("Skin temperature") })
    }

    @Test
    fun `skin temperature needs a few nights of history before it counts`() {
        val base = history(14, { 60.0 }).mapIndexed { i, d -> if (i < 3) d.copy(skinTempDeltaCelsius = 0.0) else d }
        val warm = DayMetrics(LocalDate.of(2026, 9, 20), 450, 55.0, 60.0, skinTempDeltaCelsius = 1.5)
        val result = recovery.calculateRecovery(warm.date, warm, base, RecoveryConfig())
        assertTrue(result.negativeContributors.none { it.contains("Skin temperature") })
    }

    /** 30 days of HRV wobbling ±8% around 60 ms, with the last [recent] days set to [recentHrv]. */
    private fun hrvHistory(recent: Int = 0, recentHrv: Double = 60.0, skipRecent: Boolean = false) =
        (1..37).mapNotNull { d ->
            if (skipRecent && d < 8) return@mapNotNull null
            val hrv = if (d <= recent) recentHrv else 60.0 * if (d % 2 == 0) 1.08 else 0.92
            DayMetrics(LocalDate.of(2026, 9, 20).minusDays(d.toLong()), 450, 55.0, hrv)
        }.filter { it.date.isAfter(LocalDate.of(2026, 9, 20).minusDays(if (skipRecent) 38 else 31)) }

    private fun hrvResult(today: Double, history: List<DayMetrics>) =
        recovery.calculateRecovery(LocalDate.of(2026, 9, 20), DayMetrics(LocalDate.of(2026, 9, 20), 450, 55.0, today), history, RecoveryConfig())

    @Test
    fun `a week of low HRV counts for more than one low night`() {
        val oneNight = hrvResult(48.0, hrvHistory())
        val wholeWeek = hrvResult(48.0, hrvHistory(recent = 6, recentHrv = 48.0))
        assertTrue("one night ${oneNight.hrvScore} vs week ${wholeWeek.hrvScore}", wholeWeek.hrvScore!! < oneNight.hrvScore!! - 10)
        assertTrue(wholeWeek.negativeContributors.any { it.contains("this week") })
    }

    @Test
    fun `one low night after a normal week is softened`() {
        // Same night, but with no recent nights to average it with: scored on its own.
        val alone = hrvResult(48.0, hrvHistory(skipRecent = true))
        val withWeek = hrvResult(48.0, hrvHistory())
        assertTrue("alone ${alone.hrvScore} vs with week ${withWeek.hrvScore}", withWeek.hrvScore!! > alone.hrvScore!!)
    }

    @Test
    fun `HRV well above normal all week stops earning points and is flagged`() {
        val high = hrvResult(90.0, hrvHistory(recent = 6, recentHrv = 90.0))
        // Last night alone would be 100; the week's part is capped at 1.5 SD (87.5).
        assertTrue("score ${high.hrvScore}", high.hrvScore!! < 95.0)
        assertTrue(high.negativeContributors.any { it.contains("overreaching") })
    }

    // endregion
}
