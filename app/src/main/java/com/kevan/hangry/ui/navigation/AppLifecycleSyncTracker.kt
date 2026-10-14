package com.kevan.hangry.ui.navigation

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.kevan.hangry.domain.repository.HealthSyncManager
import com.kevan.hangry.util.millisUntilNextMidnight
import com.kevan.hangry.ui.dashboard.DashboardViewModel
import com.kevan.hangry.ui.nutrition.NutritionViewModel
import com.kevan.hangry.ui.widget.HangryWidgetUpdater
import kotlinx.coroutines.delay

/** How often today's strain refreshes while the app is open. */
private const val LIVE_REFRESH_MS = 5 * 60 * 1000L

/**
 * Handles application lifecycle events, background live strain polling, and midnight date rollover.
 * Decoupled from screen navigation routing.
 */
@Composable
fun AppLifecycleSyncTracker(
    dashboardViewModel: DashboardViewModel,
    nutritionViewModel: NutritionViewModel,
    syncManager: HealthSyncManager,
    context: Context
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                dashboardViewModel.onAppResumed()
                nutritionViewModel.onDayMaybeChanged()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    // Live strain: while the app is on screen, pull today's heart rate every few minutes so
    // strain builds up as the day goes on. The hourly background sync covers the rest, and the
    // first sync after midnight settles yesterday's final number.
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                if (syncManager.refreshToday()) {
                    dashboardViewModel.refreshHabits()
                    HangryWidgetUpdater.updateAllWidgets(context)
                }
                delay(LIVE_REFRESH_MS)
            }
        }
    }

    // Midnight rollover: update today's date and data when the day flips while the app is open.
    LaunchedEffect(Unit) {
        while (true) {
            delay(millisUntilNextMidnight())
            dashboardViewModel.onDayMaybeChanged()
            nutritionViewModel.onDayMaybeChanged()
        }
    }
}
