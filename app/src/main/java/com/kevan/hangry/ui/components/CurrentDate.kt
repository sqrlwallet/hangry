package com.kevan.hangry.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import com.kevan.hangry.ui.theme.LocalHangryTokens
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

/** Milliseconds until the next local midnight (plus a second, so the date has definitely changed). */
fun millisUntilNextMidnight(zone: ZoneId = ZoneId.systemDefault()): Long {
    val now = ZonedDateTime.now(zone)
    val midnight = now.toLocalDate().plusDays(1).atStartOfDay(zone)
    return Duration.between(now, midnight).toMillis() + 1_000
}

/**
 * Today's date that updates itself at midnight, for labels like "Today" that must not go stale
 * when the app is left open overnight.
 */
@Composable
fun rememberToday(zone: ZoneId = ZoneId.systemDefault()): LocalDate {
    val today by produceState(LocalDate.now(zone)) {
        while (true) {
            delay(millisUntilNextMidnight(zone))
            value = LocalDate.now(zone)
        }
    }
    return today
}

/** "Today", "Yesterday", or e.g. "Mon, Sep 22" - for headings that follow the selected date. */
fun dayLabel(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> date.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()))
}

/** "Today's Workouts" on today, "Workouts · Mon, Sep 22" on any other day. */
fun dayHeading(noun: String, date: LocalDate, today: LocalDate = LocalDate.now()): String =
    if (date == today) "Today's $noun" else "$noun · ${dayLabel(date, today)}"

/**
 * A small note shown when a detail screen is displaying a past day (it follows the date picked
 * on Today), so the numbers aren't mistaken for today's. Shows nothing on today.
 */
@Composable
fun PastDayNote(date: LocalDate, modifier: Modifier = Modifier) {
    val today = rememberToday()
    if (date == today) return
    Text(
        text = "Showing ${dayLabel(date, today)}",
        style = MaterialTheme.typography.labelLarge,
        color = LocalHangryTokens.current.textSecondary,
        modifier = modifier
    )
}
