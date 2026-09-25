package com.kevan.hangry.data.nudges

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.kevan.hangry.HangryApplication
import com.kevan.hangry.R
import com.kevan.hangry.domain.model.NudgeText
import com.kevan.hangry.ui.coach.DashMood
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * A nudge [MINUTES_AHEAD] minutes before tonight's suggested bedtime (from recent wake times and
 * sleep need - the same suggestion the Sleep screen shows). The alarm is re-planned whenever the
 * suggestion may have changed: hourly by the nudge worker, on app start, after reboot.
 */
object BedtimeReminder {
    const val MINUTES_AHEAD = 30
    private const val PREFS = "hangry_nudges"
    private const val KEY_BEDTIME = "planned_bedtime"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun reschedule(context: Context) {
        val appContext = context.applicationContext
        scope.launch { runCatching { rescheduleNow(appContext) } }
    }

    suspend fun rescheduleNow(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val pi = pendingIntent(context)
        alarms.cancel(pi)
        if (!NudgePrefs.bedtimeEnabled(context)) return
        val bedtime = suggestedBedtime(context) ?: return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_BEDTIME, bedtime.toString()).apply()
        val zone = ZoneId.systemDefault()
        var trigger = LocalDate.now(zone).atTime(bedtime.minusMinutes(MINUTES_AHEAD.toLong())).atZone(zone)
        if (!trigger.toInstant().isAfter(Instant.now())) trigger = trigger.plusDays(1)
        // Inexact is fine for a wind-down nudge, and needs no exact-alarm permission.
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger.toInstant().toEpochMilli(), pi)
    }

    private suspend fun suggestedBedtime(context: Context): LocalTime? {
        val container = (context.applicationContext as? HangryApplication)?.container ?: return null
        val now = Instant.now()
        val recent = container.database.sleepSessionDao()
            .getSessionsBetweenList(now.minusSeconds(14L * 24 * 3600), now)
            .sortedByDescending { it.startTime }
        if (recent.isEmpty()) return null
        val goal = container.userProfileRepository.getProfileSync()?.sleepGoalMinutes ?: 480
        return container.sleepCalculator.analyzeSleep(currentSession = null, recentSessions = recent, targetDurationMinutes = goal).recommendedBedtime
    }

    internal fun plannedBedtime(context: Context): LocalTime? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_BEDTIME, null)
            ?.let { runCatching { LocalTime.parse(it) }.getOrNull() }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, NudgeNotifications.BEDTIME_ID,
        Intent(context, BedtimeReminderReceiver::class.java).setAction(BedtimeReminderReceiver.ACTION_REMIND),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
}

/** Fires the bedtime nudge, then plans tomorrow's; also re-plans after reboot or a clock change. */
class BedtimeReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REMIND && NudgePrefs.bedtimeEnabled(context)) {
            BedtimeReminder.plannedBedtime(context)?.let { bedtime ->
                val message = NudgeText.bedtime(bedtime, BedtimeReminder.MINUTES_AHEAD)
                NudgeNotifications.notify(
                    context, NudgeNotifications.BEDTIME_ID,
                    NotificationCompat.Builder(context, NudgeNotifications.CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_notification_bedtime)
                        .setLargeIcon(NudgeNotifications.dashPicture(context, DashMood.BEDTIME.imageRes))
                        .setContentTitle(message.title)
                        .setContentText(message.body)
                        .setStyle(NotificationCompat.BigTextStyle().bigText(message.body))
                        .setCategory(NotificationCompat.CATEGORY_REMINDER)
                        .setAutoCancel(true)
                        .setContentIntent(NudgeNotifications.openApp(context, NudgeNotifications.BEDTIME_ID, "sleep"))
                )
            }
        }
        BedtimeReminder.reschedule(context)
    }

    companion object {
        const val ACTION_REMIND = "com.kevan.hangry.ACTION_BEDTIME_REMINDER"
    }
}
