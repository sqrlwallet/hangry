package com.kevan.hangry.data.worker

import android.content.Context
import androidx.work.*
import com.kevan.hangry.HangryApplication
import com.kevan.hangry.data.background.BackgroundAccess
import com.kevan.hangry.data.background.BackgroundReadStatus
import com.kevan.hangry.data.background.BackgroundSyncLog
import com.kevan.hangry.data.nudges.MorningReadiness
import com.kevan.hangry.domain.model.SyncStatus
import kotlinx.coroutines.flow.lastOrNull
import java.util.concurrent.TimeUnit

/**
 * Hourly background refresh: pull the last few days from Health Connect, recompute scores, then
 * bring everything that depends on them up to date - widgets, and the morning brief as soon as
 * last night's sleep lands. Needs Health Connect's background-read permission; without it the
 * app still syncs every time it's opened, so the run is skipped rather than retried.
 */
class HealthSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? HangryApplication ?: return Result.failure()
        if (BackgroundAccess.healthRead(app) != BackgroundReadStatus.GRANTED) {
            BackgroundSyncLog.record(app, BackgroundSyncLog.SKIPPED_NO_PERMISSION)
            return Result.success()
        }

        return try {
            val progress = app.container.healthSyncManager.syncRecent().lastOrNull()
            if (progress?.status == SyncStatus.FAILED) {
                BackgroundSyncLog.record(app, BackgroundSyncLog.FAILED)
                // A few quick retries, then wait for the next hourly run.
                if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.success()
            } else {
                BackgroundSyncLog.record(app, BackgroundSyncLog.SYNCED)
                com.kevan.hangry.ui.widget.HangryWidgetUpdater.updateAllWidgets(app)
                runCatching { MorningReadiness.maybeNotify(app) }
                Result.success()
            }
        } catch (_: Exception) {
            BackgroundSyncLog.record(app, BackgroundSyncLog.FAILED)
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.success()
        }
    }

    companion object {
        private const val WORK_NAME = "hangry_periodic_health_sync"
        private const val MAX_RETRIES = 3

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            val syncRequest = PeriodicWorkRequestBuilder<HealthSyncWorker>(
                repeatInterval = 1,
                repeatIntervalTimeUnit = TimeUnit.HOURS,
                // Any time in the last 20 minutes of each hour, so Android can batch it with other work.
                flexTimeInterval = 20,
                flexTimeIntervalUnit = TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                .build()

            // UPDATE (not KEEP) so installs that scheduled the old 6-hour sync pick up the new
            // interval; re-enqueueing an unchanged request on every start is a no-op.
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                syncRequest
            )
        }

        /** A one-off sync right after background access is granted, so it takes effect now. */
        fun syncSoon(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                "$WORK_NAME-now",
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<HealthSyncWorker>().build()
            )
        }
    }
}
