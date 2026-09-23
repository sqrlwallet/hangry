package com.kevan.hangry.data.nudges

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kevan.hangry.HangryApplication
import kotlinx.coroutines.flow.lastOrNull
import java.util.concurrent.TimeUnit

/**
 * Hourly: in the morning, sync so last night's sleep lands and send the readiness brief once;
 * any time, keep tonight's bedtime reminder planned. Cheap outside the morning window.
 */
class DailyNudgeWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val app = context as? HangryApplication ?: return Result.success()
        runCatching {
            if (NudgePrefs.morningEnabled(context) && MorningReadiness.inWindow() && !MorningReadiness.alreadySentToday(context)) {
                app.container.healthSyncManager.syncRecent().lastOrNull()
                MorningReadiness.maybeNotify(context)
            }
            BedtimeReminder.rescheduleNow(context)
            // A birthday may have passed since the app last ran.
            app.container.userProfileRepository.refreshAgeFromBirthday()
        }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "hangry_daily_nudges"

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<DailyNudgeWorker>(1, TimeUnit.HOURS).build()
            )
        }
    }
}
