package com.kevan.hangry

import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import com.kevan.hangry.data.datasource.ResolvedOnce
import com.kevan.hangry.data.local.dao.HeartRateRecordSpan
import com.kevan.hangry.data.local.entity.DailyHealthSummaryEntity
import com.kevan.hangry.data.local.entity.SleepSessionEntity
import com.kevan.hangry.data.repository.HealthSyncPlan
import com.kevan.hangry.data.repository.SyncedType
import com.kevan.hangry.data.repository.newestValue
import com.kevan.hangry.domain.calculation.HangrySleepCalculator
import com.kevan.hangry.domain.model.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/** Sync safeguards: nothing stored is dropped because of a read that didn't happen or failed. */
class SyncIntegrityTest {

    // --- Health Connect client: unavailable at first, available later -------------------------

    @Test
    fun clientNotAvailableAtFirst_isLookedUpAgainLater() {
        var available = false
        var lookups = 0
        val holder = ResolvedOnce { lookups++; if (available) "client" else null }

        assertNull(holder.get())
        assertNull(holder.get())
        available = true
        assertEquals("client", holder.get())
        assertEquals(3, lookups)
    }

    @Test
    fun clientOnceFound_isKept() {
        var lookups = 0
        val holder = ResolvedOnce { lookups++; Any() }
        val first = holder.get()
        assertSame(first, holder.get())
        assertEquals(1, lookups)
    }

    // --- Only granted types are read --------------------------------------------------------

    @Test
    fun stepsOnlyGrant_readsStepsOnly() {
        val granted = setOf(HealthPermission.getReadPermission(StepsRecord::class))
        assertEquals(setOf(SyncedType.STEPS), HealthSyncPlan.readableTypes(granted))
    }

    @Test
    fun partialGrant_skipsTheRest() {
        val granted = setOf(
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(HeartRateRecord::class),
            "android.permission.health.READ_HEALTH_DATA_HISTORY"
        )
        val readable = HealthSyncPlan.readableTypes(granted)
        assertEquals(setOf(SyncedType.SLEEP, SyncedType.HEART_RATE), readable)
        assertFalse(SyncedType.RESTING_HEART_RATE in readable)
        assertFalse(SyncedType.WEIGHT in readable)
    }

    @Test
    fun noGrant_readsNothing() {
        assertTrue(HealthSyncPlan.readableTypes(emptySet()).isEmpty())
    }

    @Test
    fun everyGrant_readsEverything() {
        val granted = SyncedType.entries.map { it.readPermission }.toSet()
        assertEquals(SyncedType.entries.toSet(), HealthSyncPlan.readableTypes(granted))
    }

    // --- Stale removal ----------------------------------------------------------------------

    @Test
    fun stale_isWhatTheReadNoLongerHas() {
        val stored = listOf("a", "b", "c")
        assertEquals(listOf("b"), HealthSyncPlan.stale(stored, listOf("a", "c", "d")))
        assertEquals(emptyList<String>(), HealthSyncPlan.stale(stored, setOf("a", "b", "c")))
    }

    private val from = Instant.parse("2026-10-01T00:00:00Z")
    private val to = Instant.parse("2026-10-15T00:00:00Z")

    @Test
    fun heartRateRecord_goneFromHealthConnect_isStale() {
        val stored = listOf(HeartRateRecordSpan("gone", from.plusSeconds(3600), from.plusSeconds(7200)))
        assertEquals(listOf("gone"), HealthSyncPlan.staleHeartRateRecords(stored, emptySet(), from, to))
    }

    @Test
    fun heartRateRecord_stillThere_isKept() {
        val stored = listOf(HeartRateRecordSpan("kept", from.plusSeconds(3600), from.plusSeconds(7200)))
        assertTrue(HealthSyncPlan.staleHeartRateRecords(stored, setOf("kept"), from, to).isEmpty())
    }

    @Test
    fun heartRateRecord_reachingPastTheWindow_isKept() {
        val stored = listOf(
            HeartRateRecordSpan("startsBefore", from.minusSeconds(600), from.plusSeconds(600)),
            HeartRateRecordSpan("endsAfter", to.minusSeconds(600), to.plusSeconds(600))
        )
        assertTrue(HealthSyncPlan.staleHeartRateRecords(stored, emptySet(), from, to).isEmpty())
    }

    // --- One-time re-import ------------------------------------------------------------------

    @Test
    fun reimport_onlyCountsWhenTheSyncSucceeded() {
        assertTrue(HealthSyncPlan.reimportSucceeded(SyncStatus.SUCCESS))
        assertFalse(HealthSyncPlan.reimportSucceeded(SyncStatus.FAILED))
        assertFalse(HealthSyncPlan.reimportSucceeded(SyncStatus.IN_PROGRESS))
        assertFalse(HealthSyncPlan.reimportSucceeded(null))
    }

    // --- Body Age picks the newest reading ----------------------------------------------------

    @Test
    fun bodyAge_usesNewestVo2MaxAndBodyFat_fromNewestFirstList() {
        val today = LocalDate.of(2026, 10, 12)
        // Same order the DAO returns: date DESC.
        val summaries = listOf(
            DailyHealthSummaryEntity(date = today, vo2Max = null, bodyFatPercentage = null),
            DailyHealthSummaryEntity(date = today.minusDays(1), vo2Max = 48.0, bodyFatPercentage = 18.0),
            DailyHealthSummaryEntity(date = today.minusDays(20), vo2Max = 41.0, bodyFatPercentage = 24.0)
        )
        assertEquals(48.0, newestValue(summaries) { it.vo2Max })
        assertEquals(18.0, newestValue(summaries) { it.bodyFatPercentage })
        assertEquals(48.0, newestValue(summaries.reversed()) { it.vo2Max })
        assertNull(newestValue(summaries) { it.hydrationLiters })
    }

    // --- Sleep belongs to the day it ends on --------------------------------------------------

    private val utc = ZoneOffset.UTC

    private fun sleep(id: String, start: String, end: String, minutes: Int) = SleepSessionEntity(
        recordFingerprint = id,
        startTime = LocalDateTime.parse(start).toInstant(utc),
        endTime = LocalDateTime.parse(end).toInstant(utc),
        durationMinutes = minutes
    )

    @Test
    fun eveningNap_countsForOneDayOnly() {
        val nap = sleep("nap", "2026-10-10T19:00", "2026-10-10T23:30", 270)
        val day10 = LocalDate.of(2026, 10, 10)
        val day11 = LocalDate.of(2026, 10, 11)
        assertEquals(nap, HealthSyncPlan.primarySleep(day10, listOf(nap), utc))
        assertNull(HealthSyncPlan.primarySleep(day11, listOf(nap), utc))
    }

    @Test
    fun sleepStartingBeforeSixPmTheDayBefore_isStillAttributed() {
        // A long sleep (e.g. after a night shift) starting mid-afternoon.
        val long = sleep("long", "2026-10-10T15:00", "2026-10-11T07:00", 900)
        assertEquals(long, HealthSyncPlan.primarySleep(LocalDate.of(2026, 10, 11), listOf(long), utc))
    }

    @Test
    fun primarySleep_isTheLongestEndingThatDay() {
        val night = sleep("night", "2026-10-10T23:00", "2026-10-11T07:00", 450)
        val nap = sleep("nap", "2026-10-11T13:00", "2026-10-11T13:40", 40)
        val other = sleep("other", "2026-10-11T22:00", "2026-10-12T06:00", 470)
        assertEquals(night, HealthSyncPlan.primarySleep(LocalDate.of(2026, 10, 11), listOf(nap, night, other), utc))
    }

    @Test
    fun sleepDay_followsTheZone() {
        val s = sleep("s", "2026-10-10T20:00", "2026-10-10T23:30", 210)
        assertEquals(LocalDate.of(2026, 10, 10), HealthSyncPlan.sleepDate(s, utc))
        assertEquals(LocalDate.of(2026, 10, 11), HealthSyncPlan.sleepDate(s, ZoneId.of("Asia/Tokyo")))
    }

    // --- Sleep calculator reads the zone each time ------------------------------------------

    @Test
    fun sleepCalculator_followsTimeZoneChange() {
        var zone: ZoneId = utc
        val calculator = HangrySleepCalculator { zone }
        // Ends 23:30 UTC = 08:30 next day in Tokyo.
        val a = sleep("a", "2026-10-10T15:00", "2026-10-10T23:30", 500)
        val b = sleep("b", "2026-10-11T01:00", "2026-10-11T02:00", 60)

        // UTC: different wake-up days, two nights.
        assertEquals(2, calculator.mainSleeps(listOf(a, b)).size)
        zone = ZoneId.of("Asia/Tokyo")
        // Tokyo: both end on Oct 11, one night with the two sessions added together.
        val tokyoNight = calculator.mainSleeps(listOf(a, b)).single()
        assertEquals(560, tokyoNight.durationMinutes)
        assertEquals(a.startTime, tokyoNight.startTime)
        assertEquals(a.endTime, tokyoNight.endTime)

        // Bedtime advice is in local clock time too.
        val nights = (1..7).map { i ->
            sleep("n$i", "2026-10-0${i}T23:00", "2026-10-0${i + 1}T07:00", 480)
        }
        zone = utc
        val utcBedtime = calculator.analyzeSleep(null, nights.reversed()).recommendedBedtime
        zone = ZoneId.of("Asia/Tokyo")
        val tokyoBedtime = calculator.analyzeSleep(null, nights.reversed()).recommendedBedtime
        assertEquals(utcBedtime!!.plusHours(9), tokyoBedtime)
    }
}
