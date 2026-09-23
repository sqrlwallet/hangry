package com.kevan.hangry

import android.app.Application
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
        // Activates the documented 6-hour periodic sync (SYNC_DESIGN.md §4); enqueueUniquePeriodicWork
        // with KEEP is idempotent, so this is safe to call on every process start, including before
        // onboarding completes (an unauthorized sync is simply a harmless no-op).
        HealthSyncWorker.schedule(this)
        com.kevan.hangry.ui.widget.HangryWidgetUpdater.updateAllWidgets(this)
        // Alarms are cleared by app updates and force-stops; re-arm supplement reminders.
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            runCatching { container.supplementRepository.rescheduleReminders() }
        }
    }
}
