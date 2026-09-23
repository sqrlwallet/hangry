package com.kevan.hangry

import com.kevan.hangry.domain.calculation.HealthDedup
import com.kevan.hangry.domain.calculation.HealthDedup.Point
import com.kevan.hangry.domain.calculation.HealthDedup.Span
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class HealthDedupTest {

    private val t0 = Instant.parse("2026-09-20T22:00:00Z")
    private fun at(minutes: Long) = t0.plusSeconds(minutes * 60)
    private fun span(id: String, from: Long, to: Long, source: String, quality: Double = 0.0) =
        Span(id, at(from), at(to), source, quality)

    @Test
    fun watchAndPhoneCopiesOfOneNightBecomeOne() {
        val kept = HealthDedup.keepOnePerEvent(listOf(
            span("phone", 30, 510, "com.phone"),                 // 22:30-06:30, no stages
            span("watch", 0, 480, "com.watch", quality = 1.0)    // 22:00-06:00, with stages
        ))
        assertEquals(listOf("watch"), kept)
    }

    @Test
    fun napAndNightSleepAreBothKept() {
        val kept = HealthDedup.keepOnePerEvent(listOf(
            span("night", 0, 480, "com.watch"),
            span("nap", 900, 960, "com.phone")
        ))
        assertEquals(listOf("night", "nap"), kept)
    }

    @Test
    fun sameWorkoutFromThreeAppsCountsOnce() {
        val kept = HealthDedup.keepOnePerEvent(listOf(
            span("fit", 1, 46, "com.google.fit", quality = 2.0),
            span("strava", 0, 45, "com.strava", quality = 5.0),
            span("samsung", 0, 44, "com.samsung.health", quality = 3.0)
        ))
        assertEquals(listOf("strava"), kept)
    }

    @Test
    fun backToBackWorkoutsAreSeparate() {
        val kept = HealthDedup.keepOnePerEvent(listOf(
            span("run", 0, 30, "com.watch"),
            span("weights", 32, 90, "com.gym")
        ))
        assertEquals(listOf("run", "weights"), kept)
    }

    @Test
    fun barelyOverlappingWorkoutsFromDifferentAppsAreSeparate() {
        // 10 minutes shared out of a 60-minute shorter session: not the same workout.
        val kept = HealthDedup.keepOnePerEvent(listOf(span("a", 0, 60, "x"), span("b", 50, 120, "y")))
        assertEquals(2, kept.size)
    }

    @Test
    fun sameAppOnlyMergesNearIdenticalCopies() {
        val kept = HealthDedup.keepOnePerEvent(listOf(
            span("copy1", 0, 45, "com.watch"),
            span("copy2", 1, 46, "com.watch"),
            span("later", 20, 80, "com.watch") // overlaps, but a different session from the same app
        ))
        assertEquals(listOf("copy1", "later"), kept)
    }

    @Test
    fun workoutCaloriesComeFromOneApp() {
        data class Cal(val source: String, val kcal: Double)
        val records = listOf(Cal("com.watch", 300.0), Cal("com.fit", 280.0), Cal("com.fit", 20.0))
        assertEquals(listOf(Cal("com.watch", 300.0)), HealthDedup.fromOneSource(records, "com.watch", { it.source }, { it.kcal }))
        // The workout's own app wrote none: the app with the most, never both added together.
        assertEquals(300.0, HealthDedup.fromOneSource(records.drop(1) + Cal("com.samsung", 250.0), "com.strava", { it.source }, { it.kcal }).sumOf { it.kcal }, 0.0)
    }

    @Test
    fun mirroredWeighInsAndMealsAreDropped() {
        val kept = HealthDedup.dropCrossAppCopies(listOf(
            Point("scale", at(0), "com.scale", "72.4"),
            Point("mirror", at(1), "com.fit", "72.4"),
            Point("snack1", at(5), "com.mfp", "apple|95"),
            Point("snack2", at(5), "com.mfp", "apple|95"),   // same app: two apples
            Point("tomorrow", at(1440), "com.fit", "72.4")
        ))
        assertEquals(listOf("scale", "snack1", "snack2", "tomorrow"), kept)
    }
}
