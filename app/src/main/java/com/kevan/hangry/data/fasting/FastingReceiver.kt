package com.kevan.hangry.data.fasting

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.kevan.hangry.HangryApplication
import com.kevan.hangry.R
import com.kevan.hangry.data.nudges.NudgeNotifications
import com.kevan.hangry.domain.model.FastingMath
import com.kevan.hangry.ui.coach.DashMood
import com.kevan.hangry.ui.widget.HangryWidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The goal-reached notification, Start/End from the home-screen widget and the notification's
 * End fast button, and re-arming the goal alarm after reboot or clock changes.
 */
class FastingReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? HangryApplication ?: return
        val repository = app.container.fastingRepository
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_GOAL -> notifyGoal(app)
                    ACTION_START -> {
                        repository.startFast()
                        val s = repository.current()
                        toast(app, if (s.active != null) "Fast started · goal ${s.targetHours}h" else "Turn on fasting in the app first")
                    }
                    ACTION_END -> {
                        val active = repository.current().active
                        if (repository.endFast()) {
                            toast(app, "Fast ended · ${active?.let { FastingMath.formatDuration(it.elapsed()) }.orEmpty()}")
                        }
                    }
                    // Boot, time/zone change or app update: alarms don't survive these.
                    else -> repository.rescheduleReminder()
                }
                HangryWidgetUpdater.updateAllWidgetsInternal(app)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun notifyGoal(context: Context) {
        val app = context.applicationContext as HangryApplication
        val snapshot = app.container.fastingRepository.current()
        val fast = snapshot.active ?: return
        if (!snapshot.enabled || !snapshot.goalReminder || !fast.reachedGoal() || !NudgeNotifications.canNotify(context)) return
        ensureChannel(context)
        val hours = fast.targetMinutes / 60
        val streakLine = if (snapshot.streak > 1) " That's ${snapshot.streak} days in a row." else ""
        val body = "You've fasted ${hours}h.$streakLine End it when you're ready to eat, or keep going."
        val endIntent = PendingIntent.getBroadcast(
            context, NOTIFICATION_ID,
            Intent(context, FastingReceiver::class.java).setAction(ACTION_END),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_fasting)
            .setLargeIcon(NudgeNotifications.dashPicture(context, DashMood.HAPPY.imageRes))
            .setContentTitle("Fasting goal reached")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(NudgeNotifications.openApp(context, NOTIFICATION_ID + 1, "fasting"))
            .addAction(0, "End fast", endIntent)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Fasting", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Tells you when your fast reaches its goal"
            }
        )
    }

    private suspend fun toast(context: Context, message: String) = withContext(Dispatchers.Main) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val ACTION_GOAL = "com.kevan.hangry.ACTION_FAST_GOAL"
        const val ACTION_START = "com.kevan.hangry.ACTION_FAST_START"
        const val ACTION_END = "com.kevan.hangry.ACTION_FAST_END"
        const val CHANNEL_ID = "fasting"
        const val NOTIFICATION_ID = 42_001
    }
}
