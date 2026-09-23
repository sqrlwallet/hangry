package com.kevan.hangry.domain.calculation

import java.time.Duration
import java.time.Instant
import kotlin.math.abs

/**
 * Health Connect keeps every app's copy of the same activity: a watch and its phone app, or a
 * workout that Strava, Samsung Health and Fit all write. Summing them double-counts. These rules
 * pick one copy of each real-world event. See CALCULATIONS.md §11.
 */
object HealthDedup {

    /** Copies from different apps overlapping at least this much of the shorter one are the same event. */
    const val CROSS_APP_OVERLAP = 0.5
    /** The same app writing the same session twice (a re-sync, an edit saved as new) starts and ends within this. */
    val SAME_APP_TOLERANCE: Duration = Duration.ofMinutes(2)

    /** A timed event from one app, with how complete its data is (higher wins). */
    data class Span<T>(val item: T, val start: Instant, val end: Instant, val source: String, val quality: Double)

    /**
     * Keeps one copy of every event: the most complete first, then the longest, then the
     * earliest-starting (so the result doesn't depend on read order). Genuinely separate events -
     * a nap and the night's sleep, two workouts back to back - don't overlap and are all kept.
     */
    fun <T> keepOnePerEvent(spans: List<Span<T>>): List<T> {
        val ranked = spans.sortedWith(
            compareByDescending<Span<T>> { it.quality }
                .thenByDescending { Duration.between(it.start, it.end) }
                .thenBy { it.start }
                .thenBy { it.source }
        )
        val kept = mutableListOf<Span<T>>()
        for (candidate in ranked) {
            if (kept.none { isSameEvent(it, candidate) }) kept += candidate
        }
        return kept.sortedBy { it.start }.map { it.item }
    }

    fun isSameEvent(a: Span<*>, b: Span<*>): Boolean {
        if (a.source == b.source) {
            return abs(Duration.between(a.start, b.start).toMillis()) <= SAME_APP_TOLERANCE.toMillis() &&
                abs(Duration.between(a.end, b.end).toMillis()) <= SAME_APP_TOLERANCE.toMillis()
        }
        val shorter = minOf(Duration.between(a.start, a.end), Duration.between(b.start, b.end))
        if (shorter <= Duration.ZERO) return a.start == b.start
        val overlap = Duration.between(maxOf(a.start, b.start), minOf(a.end, b.end))
        return overlap.toMillis() >= shorter.toMillis() * CROSS_APP_OVERLAP
    }

    /**
     * Calories, steps or distance during one workout, from a single app: the app that recorded
     * the workout if it wrote any, otherwise the app with the most. Several apps' records for the
     * same minutes are copies of each other, never meant to be added up.
     */
    fun <R> fromOneSource(records: List<R>, workoutSource: String, source: (R) -> String, amount: (R) -> Double): List<R> {
        if (records.isEmpty()) return records
        val bySource = records.groupBy(source)
        bySource[workoutSource]?.let { return it }
        return bySource.values.maxBy { group -> group.sumOf(amount) }
    }

    /** A point reading (weigh-in, meal) from one app. */
    data class Point<T>(val item: T, val time: Instant, val source: String, val key: String)

    /**
     * Drops readings another app already reported: same [Point.key] (e.g. the weight to 0.1 kg,
     * or a meal's name and calories) within [window]. Entries from the same app are all kept -
     * two identical snacks logged together are two snacks.
     */
    fun <T> dropCrossAppCopies(points: List<Point<T>>, window: Duration = SAME_APP_TOLERANCE): List<T> {
        val kept = mutableListOf<Point<T>>()
        for (p in points.sortedWith(compareBy<Point<T>> { it.time }.thenBy { it.source })) {
            val copy = kept.any {
                it.source != p.source && it.key == p.key &&
                    abs(Duration.between(it.time, p.time).toMillis()) <= window.toMillis()
            }
            if (!copy) kept += p
        }
        return kept.map { it.item }
    }
}
