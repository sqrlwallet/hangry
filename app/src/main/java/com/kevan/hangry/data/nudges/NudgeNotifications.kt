package com.kevan.hangry.data.nudges

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.kevan.hangry.MainActivity

/** Shared plumbing for the morning brief and bedtime reminder. */
internal object NudgeNotifications {
    const val CHANNEL_ID = "daily_nudges"
    const val MORNING_ID = 41_001
    const val BEDTIME_ID = 41_002

    fun canNotify(context: Context) = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Daily briefings & bedtime", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Your morning recovery briefing and a nudge before bedtime"
            }
        )
    }

    /** Dash in the right mood as the notification's picture, scaled to notification size. */
    fun dashPicture(context: Context, @DrawableRes res: Int): Bitmap? = runCatching {
        val full = BitmapFactory.decodeResource(context.resources, res)
        Bitmap.createScaledBitmap(full, 192, 192, true).also { if (it !== full) full.recycle() }
    }.getOrNull()

    fun openApp(context: Context, requestCode: Int, destination: String? = null): PendingIntent = PendingIntent.getActivity(
        context, requestCode,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .apply { destination?.let { putExtra("destination", it) } },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    fun notify(context: Context, id: Int, builder: NotificationCompat.Builder) {
        if (!canNotify(context)) return
        ensureChannel(context)
        context.getSystemService(NotificationManager::class.java).notify(id, builder.build())
    }
}
