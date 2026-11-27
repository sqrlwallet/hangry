package com.kevan.hangry

import com.kevan.hangry.data.local.entity.DailyHealthSummaryEntity
import com.kevan.hangry.ui.heart.HeartDailyReadings
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class HeartDailyReadingsTest {

    private val today = LocalDate.of(2026, 11, 28)

    private fun day(daysAgo: Long, rhr: Double?, hrv: Double?) =
        DailyHealthSummaryEntity(date = today.minusDays(daysAgo), restingHeartRate = rhr, hrvRmssd = hrv)

    @Test
    fun oneRowPerDay_oldestFirst_skippingDaysWithNeither() {
        // The DAO hands summaries back newest first; trend lines need oldest first.
        val days = HeartDailyReadings.from(listOf(day(0, 58.0, 41.0), day(1, null, null), day(2, 61.0, null), day(3, null, 37.0)))
        assertEquals(listOf(today.minusDays(3), today.minusDays(2), today), days.map { it.date })
        assertEquals(listOf(null, 61.0, 58.0), days.map { it.restingHr })
        assertEquals(listOf(37.0, null, 41.0), days.map { it.hrv })
    }
}
