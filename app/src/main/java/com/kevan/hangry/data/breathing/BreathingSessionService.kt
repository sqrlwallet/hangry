package com.kevan.hangry.data.breathing

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.kevan.hangry.HangryApplication
import com.kevan.hangry.MainActivity
import com.kevan.hangry.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps a breathing session alive while the screen is off or the app is in the background -
 * the whole point is to follow the cues with headphones in and the phone in a pocket.
 * It owns no session logic: it mirrors [BreathingSessionController.state] into an ongoing
 * notification, holds a partial wake lock so cue timing isn't stalled by CPU sleep, and stops
 * itself as soon as the session is no longer active.
 */
class BreathingSessionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var wakeLock: PowerManager.WakeLock? = null

    private val controller: BreathingSessionController
        get() = (application as HangryApplication).container.breathingSessionController

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification("Breathing session", "Starting…"),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Hangry:BreathingSession")
            .apply {
                setReferenceCounted(false)
                acquire(MAX_SESSION_MS)
            }

        scope.launch {
            controller.state
                .map { state ->
                    (state as? BreathingSessionState.Active)?.let {
                        val phase = if (it.isPaused) "Paused" else it.position.phase.type.label
                        val remaining = "%d:%02d left".format(it.remainingSeconds / 60, it.remainingSeconds % 60)
                        it.pattern.title to "$phase · $remaining"
                    }
                }
                // Remaining time ticks every second; that's as often as the notification needs.
                .distinctUntilChanged()
                .collect { content ->
                    if (content == null) {
                        stopSelf()
                    } else if (canPostNotifications()) {
                        getSystemService(NotificationManager::class.java)
                            .notify(NOTIFICATION_ID, buildNotification(content.first, content.second))
                    }
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_END_SESSION) controller.stop()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        super.onDestroy()
    }

    private fun buildNotification(title: String, text: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_breathing)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    // Resume the existing task exactly like the launcher does, on the session screen.
                    Intent(this, MainActivity::class.java)
                        .setAction(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_LAUNCHER)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .addAction(
                0,
                "End session",
                PendingIntent.getService(
                    this,
                    1,
                    Intent(this, BreathingSessionService::class.java).setAction(ACTION_END_SESSION),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .build()

    /** Without it the session still runs; Android just hides the notification. */
    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Breathing sessions", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shown while a guided breathing session is running"
                setShowBadge(false)
            }
        )
    }

    companion object {
        private const val CHANNEL_ID = "breathing_session"
        private const val NOTIFICATION_ID = 4107
        private const val ACTION_END_SESSION = "com.kevan.hangry.action.END_BREATHING_SESSION"
        /** Longest selectable session is 20 min; the wake lock is a safety net, not the limit. */
        private const val MAX_SESSION_MS = 60 * 60 * 1000L

        /** Stops by itself once the controller leaves the Active state. */
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, BreathingSessionService::class.java))
        }
    }
}
