package com.kevan.hangry.ui.heart

import com.kevan.hangry.data.local.entity.DailyHealthSummaryEntity
import java.time.LocalDate

/** One day on the Heart screen: the day's resting heart rate and overnight HRV. */
data class HeartDailyReading(val date: LocalDate, val restingHr: Double?, val hrv: Double?)

object HeartDailyReadings {
    /** Days with a resting HR or HRV, oldest first, one row per date whatever order [summaries] come in. */
    fun from(summaries: List<DailyHealthSummaryEntity>): List<HeartDailyReading> =
        summaries
            .filter { it.restingHeartRate != null || it.hrvRmssd != null }
            .distinctBy { it.date }
            .sortedBy { it.date }
            .map { HeartDailyReading(it.date, it.restingHeartRate, it.hrvRmssd) }
}
