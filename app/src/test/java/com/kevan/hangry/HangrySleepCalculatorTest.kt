package com.kevan.hangry

import com.kevan.hangry.data.local.entity.SleepSessionEntity
import com.kevan.hangry.domain.calculation.HangrySleepCalculator
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class HangrySleepCalculatorTest {

    private lateinit var calculator: HangrySleepCalculator

    @Before
    fun setup() {
        calculator = HangrySleepCalculator()
    }

    @Test
    fun testNullSession_returnsEmptyState() {
        val analysis = calculator.analyzeSleep(null, emptyList())
        assertEquals(0, analysis.durationMinutes)
        assertEquals(0, analysis.sleepDebtMinutes)
        assertTrue(analysis.supportiveNote.contains("No sleep recorded"))
    }

    @Test
    fun testMidnightCrossingSession_calculatesDurationAndDebt() {
        val bedTime = Instant.parse("2026-09-21T23:00:00Z")
        val wakeTime = Instant.parse("2026-09-22T06:30:00Z")
        val session = SleepSessionEntity(
            recordFingerprint = "test-sleep-1",
            startTime = bedTime,
            endTime = wakeTime,
            durationMinutes = 450, // 7.5 hours
            timeInBedMinutes = 470
        )

        val analysis = calculator.analyzeSleep(
            currentSession = session,
            recentSessions = emptyList(),
            targetDurationMinutes = 480 // 8h target
        )

        assertEquals(450, analysis.durationMinutes)
        assertEquals(470, analysis.timeInBedMinutes)
        assertEquals(30, analysis.sleepDebtMinutes) // 480 - 450 = 30 min debt
    }

    @Test
    fun testRollingAverages_reflectsRecentSessions() {
        val now = Instant.now()
        val current = SleepSessionEntity(
            recordFingerprint = "cur",
            startTime = now.minus(8, ChronoUnit.HOURS),
            endTime = now,
            durationMinutes = 490
        )
        val history = (1..10).map { i ->
            SleepSessionEntity(
                recordFingerprint = "hist-$i",
                startTime = now.minus((i * 24 + 8).toLong(), ChronoUnit.HOURS),
                endTime = now.minus((i * 24).toLong(), ChronoUnit.HOURS),
                durationMinutes = 480
            )
        }

        val analysis = calculator.analyzeSleep(current, history)
        assertEquals(480, analysis.sevenDayAverageMinutes)
        assertEquals(480, analysis.thirtyDayAverageMinutes)
        assertEquals(0, analysis.sleepDebtMinutes)
    }

    @Test
    fun testSleepNeed_risesWithRecentDebtAndStrain() {
        val now = Instant.now()
        val current = SleepSessionEntity(
            recordFingerprint = "need-test",
            startTime = now.minus(8, ChronoUnit.HOURS),
            endTime = now,
            durationMinutes = 480
        )
        val history = (1..7).map { i ->
            SleepSessionEntity(
                recordFingerprint = "need-hist-$i",
                startTime = now.minus((i * 24 + 8).toLong(), ChronoUnit.HOURS),
                endTime = now.minus((i * 24).toLong(), ChronoUnit.HOURS),
                durationMinutes = 480
            )
        }

        val shortNights = history.map { it.copy(durationMinutes = 360) }
        val baseline = calculator.analyzeSleep(current, history)
        val withDebtAndStrain = calculator.analyzeSleep(
            currentSession = current,
            recentSessions = shortNights,
            previousDayStrain = 18.0,
            rollingAverageStrain = 9.0
        )

        assertTrue(
            "Sleep need must increase after a debt-carrying, high-strain previous day",
            withDebtAndStrain.sleepNeedMinutes > baseline.sleepNeedMinutes
        )
        assertTrue(withDebtAndStrain.sleepPerformancePercentage!! <= baseline.sleepPerformancePercentage!!)
    }

    @Test
    fun testRecommendedBedtime_derivedFromRecentWakeTimes() {
        val now = Instant.now()
        val current = SleepSessionEntity(
            recordFingerprint = "bedtime-test",
            startTime = now.minus(8, ChronoUnit.HOURS),
            endTime = now,
            durationMinutes = 480
        )
        val history = (1..7).map { i ->
            SleepSessionEntity(
                recordFingerprint = "bedtime-hist-$i",
                startTime = now.minus((i * 24 + 8).toLong(), ChronoUnit.HOURS),
                endTime = now.minus((i * 24).toLong(), ChronoUnit.HOURS),
                durationMinutes = 480
            )
        }

        val analysis = calculator.analyzeSleep(current, history)
        assertNotNull("Bedtime should be derivable once there's recent sleep history", analysis.recommendedBedtime)
    }

    @Test
    fun testQualityScore_efficientRestorativeConsistentSleep_scoresHigherThanFragmentedSleep() {
        val now = Instant.now()
        val history = (1..7).map { i ->
            SleepSessionEntity(
                recordFingerprint = "quality-hist-$i",
                startTime = now.minus((i * 24 + 8).toLong(), ChronoUnit.HOURS),
                endTime = now.minus((i * 24).toLong(), ChronoUnit.HOURS),
                durationMinutes = 480
            )
        }

        val goodSession = SleepSessionEntity(
            recordFingerprint = "quality-good",
            startTime = now.minus(8, ChronoUnit.HOURS),
            endTime = now,
            durationMinutes = 470,
            timeInBedMinutes = 480, // high efficiency: 470/480
            deepSleepMinutes = 100,
            remSleepMinutes = 115, // (100+115)/470 ~= 45.7% restorative, near the ideal target
            lightSleepMinutes = 245,
            awakeMinutes = 10
        )
        val fragmentedSession = SleepSessionEntity(
            recordFingerprint = "quality-fragmented",
            startTime = now.minus(8, ChronoUnit.HOURS),
            endTime = now,
            durationMinutes = 350,
            timeInBedMinutes = 480, // low efficiency: 350/480
            deepSleepMinutes = 20,
            remSleepMinutes = 20, // ~11% restorative, well below the ideal target
            lightSleepMinutes = 290,
            awakeMinutes = 20
        )

        val goodAnalysis = calculator.analyzeSleep(goodSession, history)
        val fragmentedAnalysis = calculator.analyzeSleep(fragmentedSession, history)

        assertTrue(
            "Efficient, restorative sleep must score higher quality than fragmented sleep",
            goodAnalysis.sleepQualityScore!! > fragmentedAnalysis.sleepQualityScore!!
        )
        assertTrue(goodAnalysis.sleepQualityScore in 0..100)
        assertTrue(fragmentedAnalysis.sleepQualityScore in 0..100)
    }

    @Test
    fun testQualityScore_missingTimeInBed_stillComputesFromRemainingComponents() {
        val now = Instant.now()
        val session = SleepSessionEntity(
            recordFingerprint = "quality-no-tib",
            startTime = now.minus(8, ChronoUnit.HOURS),
            endTime = now,
            durationMinutes = 480,
            timeInBedMinutes = null,
            deepSleepMinutes = 90,
            remSleepMinutes = 110
        )
        val analysis = calculator.analyzeSleep(session, emptyList())
        assertTrue(analysis.sleepQualityScore in 0..100)
    }

    @Test
    fun testNothingMeasured_leavesStagesQualityAndAveragesUnknown() {
        val now = Instant.now()
        // A manually logged night: only a duration, no stages, no time in bed, no history.
        val session = SleepSessionEntity(
            recordFingerprint = "manual",
            startTime = now.minus(8, ChronoUnit.HOURS),
            endTime = now,
            durationMinutes = 450
        )
        val analysis = calculator.analyzeSleep(session, emptyList())
        assertNull(analysis.deepSleepMinutes)
        assertNull(analysis.remSleepMinutes)
        assertNull(analysis.restorativePercentage)
        assertNull(analysis.sleepQualityScore)
        assertNull(analysis.consistencyPercentage)
        assertNull(analysis.sevenDayAverageMinutes)
        assertNull(analysis.thirtyDayAverageMinutes)
        // Need still has a meaning without history: the target.
        assertEquals(480, analysis.sleepNeedMinutes)
    }

    @Test
    fun testSleepGoal_drivesNeedWithoutHistory() {
        val now = Instant.now()
        val session = SleepSessionEntity(recordFingerprint = "goal", startTime = now.minus(7, ChronoUnit.HOURS), endTime = now, durationMinutes = 420)
        val analysis = calculator.analyzeSleep(session, emptyList(), targetDurationMinutes = 420)
        assertEquals(420, analysis.sleepNeedMinutes)
        assertEquals(100, analysis.sleepPerformancePercentage)
    }

    @Test
    fun testShortSleep_providesSupportiveNonShamingMessage() {
        val now = Instant.now()
        val current = SleepSessionEntity(
            recordFingerprint = "short",
            startTime = now.minus(5, ChronoUnit.HOURS),
            endTime = now,
            durationMinutes = 300 // 5 hours
        )
        val history = (1..7).map { i ->
            SleepSessionEntity(
                recordFingerprint = "h-$i",
                startTime = now.minus((i * 24).toLong(), ChronoUnit.HOURS),
                endTime = now,
                durationMinutes = 480
            )
        }

        val analysis = calculator.analyzeSleep(current, history)
        assertTrue(analysis.supportiveNote.contains("shorter than the sleep you needed", ignoreCase = true))
        assertFalse(analysis.supportiveNote.contains("poor", ignoreCase = true))
    }
}
