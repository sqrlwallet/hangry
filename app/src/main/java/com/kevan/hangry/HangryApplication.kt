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
        // Hourly background sync (skipped until Health Connect grants background reads - see
        // BackgroundAccess); idempotent, so safe on every process start, even before onboarding.
        HealthSyncWorker.schedule(this)
        // Morning readiness brief + bedtime reminder (both can be switched off in Settings).
        DailyNudgeWorker.schedule(this)
        BedtimeReminder.reschedule(this)
        com.kevan.hangry.ui.widget.HangryWidgetUpdater.updateAllWidgets(this)
        // Alarms are cleared by app updates and force-stops; re-arm supplement and fasting reminders.
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            runCatching { container.supplementRepository.rescheduleReminders() }
            runCatching { container.fastingRepository.rescheduleReminder() }
            runCatching { container.userProfileRepository.refreshAgeFromBirthday() }
        }
    }
}
