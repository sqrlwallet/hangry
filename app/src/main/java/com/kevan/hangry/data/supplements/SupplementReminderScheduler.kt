package com.kevan.hangry.data.supplements

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.kevan.hangry.domain.model.Supplement
import com.kevan.hangry.domain.model.SupplementTimes
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * One daily alarm per dose time (all supplements due at 08:00 share one reminder). Alarms are
 * one-shot and re-armed for the next day each time they fire, which keeps them right across
 * DST and time-zone changes. Exact when Android allows it, otherwise within a few minutes.
 */
class SupplementReminderScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val prefs = context.getSharedPreferences("supplement_reminders", Context.MODE_PRIVATE)

    fun reschedule(supplements: List<Supplement>) {
        val wanted = supplements
            .filter { it.active && it.remindersEnabled }
            .flatMap { it.times }
            .map(SupplementTimes::key)
            .toSet()
        val previous = prefs.getStringSet(KEY_SCHEDULED, emptySet()).orEmpty()
        (previous - wanted).forEach { cancel(it) }
        wanted.forEach { schedule(it) }
        prefs.edit().putStringSet(KEY_SCHEDULED, wanted).apply()
    }

    private fun schedule(key: String) {
        val time = runCatching { LocalTime.parse(key) }.getOrNull() ?: return
        val zone = ZoneId.systemDefault()
        var trigger = LocalDate.now(zone).atTime(time).atZone(zone)
        if (!trigger.toInstant().isAfter(Instant.now())) trigger = trigger.plusDays(1)
        val at = trigger.toInstant().toEpochMilli()
        val pi = pendingIntent(key)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (canExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun cancel(key: String) = alarmManager.cancel(pendingIntent(key))

    private fun pendingIntent(key: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        requestCode(key),
        Intent(context, SupplementReminderReceiver::class.java)
            .setAction(SupplementReminderReceiver.ACTION_REMIND)
            .putExtra(SupplementReminderReceiver.EXTRA_TIME, key),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    companion object {
        private const val KEY_SCHEDULED = "scheduled_times"

        /** Stable per time slot (minutes since midnight), offset clear of other request codes. */
        fun requestCode(key: String): Int {
            val t = runCatching { LocalTime.parse(key) }.getOrNull() ?: LocalTime.MIDNIGHT
            return 20_000 + t.hour * 60 + t.minute
        }
    }
}
