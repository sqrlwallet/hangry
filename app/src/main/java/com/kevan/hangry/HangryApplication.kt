package com.kevan.hangry

import android.app.Application
import com.kevan.hangry.data.nudges.BedtimeReminder
import com.kevan.hangry.data.nudges.DailyNudgeWorker
import com.kevan.hangry.data.worker.HealthSyncWorker
import com.kevan.hangry.di.AppContainer
import com.kevan.hangry.di.DefaultAppContainer
import kotlinx.coroutines.launch

class HangryApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
        // Offload WorkManager DB access and periodic work scheduling to IO dispatcher so
        // cold startup on the main thread is not blocked by SQLite disk operations.
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            runCatching { HealthSyncWorker.schedule(this@HangryApplication) }
            runCatching { DailyNudgeWorker.schedule(this@HangryApplication) }
            runCatching { container.supplementRepository.rescheduleReminders() }
            runCatching { container.fastingRepository.rescheduleReminder() }
            runCatching { container.userProfileRepository.refreshAgeFromBirthday() }
        }
        BedtimeReminder.reschedule(this)
        com.kevan.hangry.ui.widget.HangryWidgetUpdater.updateAllWidgets(this)
    }
}
