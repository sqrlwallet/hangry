package com.kevan.hangry.data.repository

import android.content.Context
import com.kevan.hangry.data.datasource.HealthConnectDataSource
import com.kevan.hangry.data.local.dao.WaterLogDao
import com.kevan.hangry.data.local.entity.WaterLogEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Water logged in Hangry. Saved locally first - so logging works without Health Connect or its
 * permission - then copied to Health Connect as a HydrationRecord when Hangry may write there.
 * The sync never reads those copies back (see WaterIntake.externalDailyLiters).
 */
class WaterLogRepository(
    private val context: Context,
    private val dao: WaterLogDao,
    private val healthConnect: HealthConnectDataSource
) {
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val prefs get() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun observeLoggedMl(date: LocalDate): Flow<Int> = dao.observeTotalMl(date)

    suspend fun loggedMl(date: LocalDate): Int = dao.totalMl(date)

    /** Logs [ml] on [date] - now for today, the current time of day on a past date. */
    suspend fun log(date: LocalDate, ml: Int): WaterLogEntity {
        val now = Instant.now()
        val timestamp = if (date == LocalDate.now(zone)) now
        else date.atTime(LocalTime.now(zone)).atZone(zone).toInstant().coerceAtMost(now)
        val entry = WaterLogEntity(date = date, timestamp = timestamp, volumeMl = ml)
        val saved = entry.copy(id = dao.insert(entry))
        syncPending()
        return saved
    }

    /** Removes [entry] here and its Health Connect copy - the snackbar's Undo. */
    suspend fun remove(entry: WaterLogEntity) {
        dao.delete(entry.id)
        if (entry.healthConnectSynced || healthConnect.hasHydrationWritePermission()) healthConnect.deleteHydrationRecord(entry)
    }

    /** Copies anything not yet in Health Connect there, e.g. after write access is granted. */
    suspend fun syncPending() {
        if (!healthConnect.hasHydrationWritePermission()) return
        dao.getUnsynced().forEach { if (healthConnect.writeHydrationRecord(it)) dao.markSynced(it.id) }
    }

    /**
     * True once per install, the first time water is logged without write access: the moment
     * to ask for it. Asking again after a "no" would nag, so this only fires once.
     */
    suspend fun shouldAskForWriteAccess(): Boolean {
        if (prefs.getBoolean(KEY_ASKED, false)) return false
        if (healthConnect.hydrationWritePermissions().isEmpty() || !healthConnect.isAvailable()) return false
        if (healthConnect.hasHydrationWritePermission()) return false
        prefs.edit().putBoolean(KEY_ASKED, true).apply()
        return true
    }

    private fun Instant.coerceAtMost(max: Instant): Instant = if (isAfter(max)) max else this

    private companion object {
        const val PREFS = "hangry_water"
        const val KEY_ASKED = "hc_write_asked"
    }
}
