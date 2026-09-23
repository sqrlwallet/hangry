package com.kevan.hangry.data.background

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import com.kevan.hangry.data.datasource.RealHealthConnectDataSource
import com.kevan.hangry.data.nudges.NudgeNotifications
import java.time.Instant

/** Whether Health Connect lets Hangry read while it isn't on screen. */
enum class BackgroundReadStatus {
    GRANTED,
    NOT_GRANTED,
    /** Health Connect missing, or too old for background reads - sync happens only while the app is open. */
    UNSUPPORTED
}

/** A snapshot of everything that lets Hangry keep itself up to date in the background. */
data class BackgroundAccessState(
    val healthRead: BackgroundReadStatus,
    val batteryUnrestricted: Boolean,
    val notifications: Boolean
) {
    val backgroundSyncWorks: Boolean get() = healthRead == BackgroundReadStatus.GRANTED
}

/**
 * The three things background work depends on:
 * - Health Connect's background-read permission - without it, a sync the app isn't showing is refused;
 * - an exemption from battery optimisation, so syncs aren't held back for hours in Doze;
 * - notifications, for the morning brief and reminders.
 */
object BackgroundAccess {

    val BACKGROUND_PERMISSION = setOf(RealHealthConnectDataSource.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND)

    suspend fun state(context: Context) = BackgroundAccessState(
        healthRead = healthRead(context),
        batteryUnrestricted = batteryUnrestricted(context),
        notifications = NudgeNotifications.canNotify(context)
    )

    suspend fun healthRead(context: Context): BackgroundReadStatus = runCatching {
        if (HealthConnectClient.getSdkStatus(context) != HealthConnectClient.SDK_AVAILABLE) return BackgroundReadStatus.UNSUPPORTED
        val client = HealthConnectClient.getOrCreate(context)
        val supported = client.features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND) ==
            HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
        when {
            !supported -> BackgroundReadStatus.UNSUPPORTED
            client.permissionController.getGrantedPermissions().containsAll(BACKGROUND_PERMISSION) -> BackgroundReadStatus.GRANTED
            else -> BackgroundReadStatus.NOT_GRANTED
        }
    }.getOrDefault(BackgroundReadStatus.UNSUPPORTED)

    fun batteryUnrestricted(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

    /** The system "Let app always run in background?" dialog. */
    @SuppressLint("BatteryLife") // Periodic health sync, the morning brief and widgets are core to the app.
    fun batteryExemptionIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))

    /** Fallback when a device has no handler for the dialog: the full battery-optimisation list. */
    fun batterySettingsIntent(): Intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
}

/** When the last background sync finished, for Settings. */
object BackgroundSyncLog {
    private const val FILE = "hangry_background"
    private const val KEY_LAST_SYNC = "last_background_sync"
    private const val KEY_LAST_RESULT = "last_background_result"

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun record(context: Context, result: String, at: Instant = Instant.now()) =
        prefs(context).edit().putLong(KEY_LAST_SYNC, at.toEpochMilli()).putString(KEY_LAST_RESULT, result).apply()

    fun lastSync(context: Context): Instant? = prefs(context).getLong(KEY_LAST_SYNC, 0L).takeIf { it > 0 }?.let(Instant::ofEpochMilli)
    fun lastResult(context: Context): String? = prefs(context).getString(KEY_LAST_RESULT, null)

    const val SYNCED = "synced"
    const val SKIPPED_NO_PERMISSION = "skipped_no_permission"
    const val FAILED = "failed"
}
