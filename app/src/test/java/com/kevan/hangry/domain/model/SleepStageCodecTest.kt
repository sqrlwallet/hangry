package com.kevan.hangry.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class SleepStageCodecTest {
    private val start = Instant.parse("2026-09-29T23:10:00Z")

    @Test
    fun roundTripsSegmentsInTimeOrder() {
        val segments = listOf(
            SleepStageSegment(SleepStage.DEEP, start.plusSeconds(1800), start.plusSeconds(3000)),
            SleepStageSegment(SleepStage.LIGHT, start, start.plusSeconds(1800)),
            SleepStageSegment(SleepStage.AWAKE, start.plusSeconds(3000), start.plusSeconds(3060))
        )
        val text = SleepStageCodec.encode(start, segments)
        assertEquals("L0,1800;D1800,3000;A3000,3060", text)
        assertEquals(segments.sortedBy { it.start }, SleepStageCodec.decode(start, text))
    }

    @Test
    fun emptyAndBadInputGiveNothing() {
        assertNull(SleepStageCodec.encode(start, emptyList()))
        assertEquals(emptyList<SleepStageSegment>(), SleepStageCodec.decode(start, null))
        assertEquals(1, SleepStageCodec.decode(start, "X1,2;R0,60;L5").size)
    }
}
