package com.kevan.hangry.data.repository

import com.kevan.hangry.domain.calculation.NightlyVitals
import java.time.LocalDate
import java.time.ZoneId

/**
 * Raw overnight readings (SpO2, breathing rate) a month at a time while days are rebuilt in
 * order. Recalculating years of history would otherwise hold every per-minute reading at once.
 * Each month is read with a day either side, so a night that crosses into it is complete.
 */
internal class ReadingsByMonth(
    private val zone: ZoneId,
    private val fetch: suspend (java.time.Instant, java.time.Instant) -> List<NightlyVitals.Reading>
) {
    private var month: LocalDate? = null
    private var cached: List<NightlyVitals.Reading> = emptyList()

    /** Readings around [date]: its whole month, plus the day before and after. Empty if the read fails. */
    suspend fun around(date: LocalDate): List<NightlyVitals.Reading> {
        val first = date.withDayOfMonth(1)
        if (first != month) {
            month = first
            cached = emptyList() // let the previous month go before reading the next
            cached = runCatching {
                fetch(
                    first.minusDays(1).atStartOfDay(zone).toInstant(),
                    first.plusMonths(1).plusDays(1).atStartOfDay(zone).toInstant()
                )
            }.getOrDefault(emptyList())
        }
        return cached
    }
}
