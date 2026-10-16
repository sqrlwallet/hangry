package com.kevan.hangry

import com.kevan.hangry.data.local.entity.SleepSessionEntity
import com.kevan.hangry.domain.calculation.SleepSessions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.Instant

class SleepSessionsTest {

    private fun session(
        id: String,
        startEpoch: Long,
        minutes: Int,
        inBed: Int? = null,
        deep: Int? = null,
        rem: Int? = null,
        light: Int? = null,
        awake: Int? = null
    ) = SleepSessionEntity(
        recordFingerprint = id,
        startTime = Instant.ofEpochSecond(startEpoch),
        endTime = Instant.ofEpochSecond(startEpoch + minutes * 60L),
        durationMinutes = minutes,
        timeInBedMinutes = inBed,
        deepSleepMinutes = deep,
        remSleepMinutes = rem,
        lightSleepMinutes = light,
        awakeMinutes = awake,
        stageSegments = "$id-segments"
    )

    @Test
    fun combine_empty_returnsNull() {
        assertNull(SleepSessions.combine(emptyList()))
    }

    @Test
    fun combine_single_returnsSameSession() {
        val night = session("night", 1_000_000, 480, inBed = 500)
        assertSame(night, SleepSessions.combine(listOf(night)))
    }

    @Test
    fun combine_nightAndNap_addsDurationStagesAndTimeInBed() {
        val night = session("night", 1_000_000, 420, inBed = 450, deep = 80, rem = 90, light = 250, awake = 15)
        val nap = session("nap", 1_050_000, 60, inBed = 65, deep = 10, rem = 5, light = 45, awake = 0)

        val combined = SleepSessions.combine(listOf(night, nap))!!

        assertEquals(480, combined.durationMinutes)
        assertEquals(515, combined.timeInBedMinutes)
        assertEquals(90, combined.deepSleepMinutes)
        assertEquals(95, combined.remSleepMinutes)
        assertEquals(295, combined.lightSleepMinutes)
        assertEquals(15, combined.awakeMinutes)
        // Bed and wake times stay the main (longest) session's.
        assertEquals(night.startTime, combined.startTime)
        assertEquals(night.endTime, combined.endTime)
        assertEquals(night.stageSegments, combined.stageSegments)
    }

    @Test
    fun combine_stageMinutesStayNullWhenNoneRecorded() {
        val night = session("night", 1_000_000, 420)
        val nap = session("nap", 1_050_000, 30)

        val combined = SleepSessions.combine(listOf(night, nap))!!

        assertEquals(450, combined.durationMinutes)
        assertNull(combined.deepSleepMinutes)
        assertNull(combined.remSleepMinutes)
        assertNull(combined.timeInBedMinutes)
    }

    @Test
    fun combine_timeInBedFallsBackToDurationForSessionsWithoutIt() {
        val night = session("night", 1_000_000, 420, inBed = 450)
        val nap = session("nap", 1_050_000, 30, inBed = null)

        val combined = SleepSessions.combine(listOf(night, nap))!!

        assertEquals(480, combined.timeInBedMinutes)
    }
}
