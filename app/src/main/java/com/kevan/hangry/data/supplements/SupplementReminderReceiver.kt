package com.kevan.hangry.data.supplements

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.kevan.hangry.HangryApplication
import com.kevan.hangry.MainActivity
import com.kevan.hangry.R
import com.kevan.hangry.domain.model.SupplementTimes
import com.kevan.hangry.ui.widget.HangryWidgetUpdater
import com.kevan.hangry.util.DashNotificationIcon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.util.Locale

/** Posts supplement reminders, handles "Mark taken", and re-arms alarms after boot/time changes. */
class SupplementReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? HangryApplication ?: return
        val repository = app.container.supplementRepository
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_REMIND -> {
                        intent.getStringExtra(EXTRA_TIME)?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
                            ?.let { remind(context, it) }
                        repository.rescheduleReminders()
                    }
                    ACTION_MARK_TAKEN -> {
                        val key = intent.getStringExtra(EXTRA_TIME)
                        key?.let { runCatching { LocalTime.parse(it) }.getOrNull() }?.let { repository.markSlotTaken(it) }
                        key?.let { context.getSystemService(NotificationManager::class.java).cancel(notificationId(it)) }
                    }
                    // Boot, time/zone change or app update: alarms don't survive these.
                    else -> repository.rescheduleReminders()
                }
                HangryWidgetUpdater.updateAllWidgetsInternal(context)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun remind(context: Context, time: LocalTime) {
        val app = context.applicationContext as HangryApplication
        val snapshot = app.container.supplementRepository.current()
        val due = snapshot.todayDoses.filter { it.time == time && !it.taken && it.supplement.remindersEnabled }
        if (due.isEmpty() || !canNotify(context)) return
        ensureChannel(context)
        val key = SupplementTimes.key(time)
        val lines = due.map { "${it.supplement.name} · ${formatDose(it.supplement.doseAmount)} ${it.supplement.doseUnit}" }
        val title = if (due.size == 1) "Time for ${due.first().supplement.name}" else "Time for ${due.size} supplements"
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_supplement)
            .setLargeIcon(DashNotificationIcon.get(context))
            .setContentTitle(title)
            .setContentText(lines.joinToString(", "))
            .setStyle(NotificationCompat.InboxStyle().also { style -> lines.forEach(style::addLine) })
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context, SupplementReminderScheduler.requestCode(key),
                    Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        .putExtra("destination", "supplements"),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .addAction(
                0, "Mark taken",
                PendingIntent.getBroadcast(
                    context, SupplementReminderScheduler.requestCode(key) + 2000,
                    Intent(context, SupplementReminderReceiver::class.java).setAction(ACTION_MARK_TAKEN).putExtra(EXTRA_TIME, key),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .build()
        context.getSystemService(NotificationManager::class.java).notify(notificationId(key), notification)
    }

    private fun canNotify(context: Context) = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Supplement reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Reminders to take your daily supplements"
            }
        )
    }

    companion object {
        const val ACTION_REMIND = "com.kevan.hangry.action.SUPPLEMENT_REMIND"
        const val ACTION_MARK_TAKEN = "com.kevan.hangry.action.SUPPLEMENT_MARK_TAKEN"
        const val EXTRA_TIME = "time"
        private const val CHANNEL_ID = "supplement_reminders"

        private fun notificationId(key: String) = SupplementReminderScheduler.requestCode(key)

        fun formatDose(amount: Double): String =
            if (amount % 1.0 == 0.0) amount.toInt().toString() else String.format(Locale.US, "%.1f", amount)
    }
}
