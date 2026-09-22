package com.kevan.hangry.data.worker

import android.content.Context
import androidx.work.*
import com.kevan.hangry.HangryApplication
import kotlinx.coroutines.flow.lastOrNull
import java.util.concurrent.TimeUnit

class HealthSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? HangryApplication ?: return Result.failure()
        val syncManager = app.container.healthSyncManager

        return try {
            val progress = syncManager.syncRecent().lastOrNull()
            if (progress?.status == com.kevan.hangry.domain.model.SyncStatus.FAILED) {
                Result.retry()
            } else {
                com.kevan.hangry.ui.widget.HangryWidgetUpdater.updateAllWidgets(applicationContext)
                Result.success()
            }
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "hangry_periodic_health_sync"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            val syncRequest = PeriodicWorkRequestBuilder<HealthSyncWorker>(
                repeatInterval = 6,
                repeatIntervalTimeUnit = TimeUnit.HOURS
            ).setConstraints(constraints).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
            )
        }
    }
}
