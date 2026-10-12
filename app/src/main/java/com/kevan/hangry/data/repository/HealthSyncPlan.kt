package com.kevan.hangry.data.repository

import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import com.kevan.hangry.data.local.dao.HeartRateRecordSpan
import com.kevan.hangry.data.local.entity.SleepSessionEntity
import com.kevan.hangry.domain.model.SyncStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The Health Connect data a sync imports, each read only when its own permission is granted. */
internal enum class SyncedType(val readPermission: String) {
    SLEEP(HealthPermission.getReadPermission(SleepSessionRecord::class)),
    WORKOUTS(HealthPermission.getReadPermission(ExerciseSessionRecord::class)),
    RESTING_HEART_RATE(HealthPermission.getReadPermission(RestingHeartRateRecord::class)),
    HRV(HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class)),
    STEPS(HealthPermission.getReadPermission(StepsRecord::class)),
    HEART_RATE(HealthPermission.getReadPermission(HeartRateRecord::class)),
    WEIGHT(HealthPermission.getReadPermission(WeightRecord::class)),
    HEIGHT(HealthPermission.getReadPermission(HeightRecord::class)),
    NUTRITION(HealthPermission.getReadPermission(NutritionRecord::class))
}

/**
 * The decisions a sync makes about what to read and what to drop, kept apart from the database
 * so they can be checked on their own.
 */
internal object HealthSyncPlan {

    /** Types the user let Hangry read. Ungranted ones are skipped, never treated as empty. */
    fun readableTypes(granted: Set<String>): Set<SyncedType> =
        SyncedType.entries.filterTo(mutableSetOf()) { it.readPermission in granted }

    /**
     * Stored rows from a window that the read of that same window didn't return - deleted or
     * edited at the source. Only meaningful when the read itself succeeded.
     */
    fun stale(stored: Collection<String>, fetched: Collection<String>): List<String> {
        val kept = fetched as? Set<String> ?: fetched.toHashSet()
        return stored.filter { it !in kept }.distinct()
    }

    /**
     * Heart rate records no longer in Health Connect. A record only counts as gone when every
     * stored sample of it lies inside the synced window - one reaching past either edge may
     * simply not have been part of this read.
     */
    fun staleHeartRateRecords(
        stored: List<HeartRateRecordSpan>,
        fetchedRecordIds: Set<String>,
        from: Instant,
        to: Instant
    ): List<String> = stored
        .filter { it.sourceRecordId !in fetchedRecordIds }
        .filter { !it.firstSample.isBefore(from) && it.lastSample.isBefore(to) }
        .map { it.sourceRecordId }

    /** The one-time re-import only counts as done when the sync behind it succeeded. */
    fun reimportSucceeded(status: SyncStatus?): Boolean = status == SyncStatus.SUCCESS

    /** A sleep belongs to the day it ends on (the wake-up day), in [zone]. */
    fun sleepDate(session: SleepSessionEntity, zone: ZoneId): LocalDate = session.endTime.atZone(zone).toLocalDate()

    /** The main sleep of [date]: the longest session ending that day. */
    fun primarySleep(date: LocalDate, sessions: List<SleepSessionEntity>, zone: ZoneId): SleepSessionEntity? =
        sessions.filter { sleepDate(it, zone) == date }.maxByOrNull { it.durationMinutes }
}
