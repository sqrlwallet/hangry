package com.kevan.hangry.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Milliseconds until the next local midnight (plus a second, so the date has definitely changed). */
fun millisUntilNextMidnight(zone: ZoneId = ZoneId.systemDefault()): Long {
    val now = ZonedDateTime.now(zone)
    val midnight = now.toLocalDate().plusDays(1).atStartOfDay(zone)
    return Duration.between(now, midnight).toMillis() + 1_000
}

/**
 * Today's date, emitted again just after each local midnight - so a flow keyed on "today" (via
 * flatMapLatest) moves to the new day when the app is left open overnight.
 */
fun todayFlow(zone: ZoneId = ZoneId.systemDefault()): Flow<LocalDate> = flow {
    while (true) {
        emit(LocalDate.now(zone))
        delay(millisUntilNextMidnight(zone))
    }
}
