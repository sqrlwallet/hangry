package com.kevan.hangry.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.kevan.hangry.HangryApplication
import com.kevan.hangry.MainActivity
import com.kevan.hangry.R
import com.kevan.hangry.ui.navigation.Screen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

object HangryWidgetUpdater {

    const val ACTION_QUICK_LOG_MEAL = "com.kevan.hangry.ACTION_QUICK_LOG_MEAL"
    private const val REQUEST_CODE_ACTIVITY = 101
    private const val REQUEST_CODE_QUICK_LOG = 102
    private const val REQUEST_CODE_SLEEP = 103
    private const val REQUEST_CODE_RECOVERY = 104
    private const val REQUEST_CODE_OVERVIEW = 105

    fun updateAllWidgets(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                updateAllWidgetsInternal(context.applicationContext)
            } catch (_: Exception) {
                // Ignore widget update failures to never crash background tasks
            }
        }
    }

    suspend fun updateAllWidgetsInternal(context: Context) {
        val app = context.applicationContext as? HangryApplication ?: return
        val container = app.container
        val appWidgetManager = AppWidgetManager.getInstance(context) ?: return

        val today = LocalDate.now(ZoneId.systemDefault())
        val summary = container.dailySummaryRepository.getSummaryForDateSync(today)
            ?: container.dailySummaryRepository.getLatestSummary().firstOrNull()

        val recovery = container.dailySummaryRepository.getRecoveryScoreForDateSync(today)
            ?: container.dailySummaryRepository.getLatestRecoveryScore().firstOrNull()

        val latestSleep = container.sleepRepository.getLatestSession().firstOrNull()

        // 1. Update Activity Widgets
        updateActivityWidgets(context, appWidgetManager, summary)

        // 2. Update Quick Log Widgets
        updateQuickLogWidgets(context, appWidgetManager)

        // 3. Update Sleep Widgets
        updateSleepWidgets(context, appWidgetManager, summary, latestSleep)

        // 4. Update Recovery Widgets
        updateRecoveryWidgets(context, appWidgetManager, recovery)

        // 5. Update Overview Widgets
        updateOverviewWidgets(context, appWidgetManager, summary, recovery, latestSleep)
    }

    private fun updateActivityWidgets(
        context: Context,
        manager: AppWidgetManager,
        summary: com.kevan.hangry.data.local.entity.DailyHealthSummaryEntity?
    ) {
        val component = ComponentName(context, ActivityWidgetProvider::class.java)
        val ids = manager.getAppWidgetIds(component)
        if (ids.isEmpty()) return

        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_activity)

            val stepsStr = summary?.steps?.let { String.format("%,d", it) } ?: "—"
            val caloriesStr = summary?.activeCalories?.let { "${it.roundToInt()}" } ?: "—"

            views.setTextViewText(R.id.tv_steps_value, stepsStr)
            views.setTextViewText(R.id.tv_calories_value, caloriesStr)

            val clickIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("destination", Screen.Dashboard.route)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                REQUEST_CODE_ACTIVITY,
                clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_activity_root, pendingIntent)

            manager.updateAppWidget(id, views)
        }
    }

    private fun updateQuickLogWidgets(context: Context, manager: AppWidgetManager) {
        val component = ComponentName(context, QuickLogWidgetProvider::class.java)
        val ids = manager.getAppWidgetIds(component)
        if (ids.isEmpty()) return

        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_quick_log)

            val clickIntent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_QUICK_LOG_MEAL
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("destination", Screen.Dashboard.route)
                putExtra("action", "quick_log_meal")
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                REQUEST_CODE_QUICK_LOG,
                clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_widget_log_meal, pendingIntent)
            views.setOnClickPendingIntent(R.id.widget_quick_log_root, pendingIntent)

            manager.updateAppWidget(id, views)
        }
    }

    private fun updateSleepWidgets(
        context: Context,
        manager: AppWidgetManager,
        summary: com.kevan.hangry.data.local.entity.DailyHealthSummaryEntity?,
        latestSleep: com.kevan.hangry.data.local.entity.SleepSessionEntity?
    ) {
        val component = ComponentName(context, SleepWidgetProvider::class.java)
        val ids = manager.getAppWidgetIds(component)
        if (ids.isEmpty()) return

        val durationMinutes = latestSleep?.durationMinutes ?: summary?.sleepDurationMinutes
        val durationStr = if (durationMinutes != null && durationMinutes > 0) {
            val hours = durationMinutes / 60
            val mins = durationMinutes % 60
            if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
        } else {
            "—"
        }

        val subtitleStr = if (durationMinutes != null && durationMinutes > 0) {
            "Restorative sleep session"
        } else {
            "No sleep recorded yet"
        }

        val scoreStr = if (durationMinutes != null && durationMinutes > 0) {
            "${summary?.sleepConsistencyScore?.roundToInt() ?: 85}%"
        } else {
            "—"
        }

        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_sleep)
            views.setTextViewText(R.id.tv_sleep_duration, durationStr)
            views.setTextViewText(R.id.tv_sleep_subtitle, subtitleStr)
            views.setTextViewText(R.id.tv_sleep_score, scoreStr)

            val clickIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("destination", Screen.Sleep.route)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                REQUEST_CODE_SLEEP,
                clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_sleep_root, pendingIntent)

            manager.updateAppWidget(id, views)
        }
    }

    private fun updateRecoveryWidgets(
        context: Context,
        manager: AppWidgetManager,
        recovery: com.kevan.hangry.data.local.entity.RecoveryScoreEntity?
    ) {
        val component = ComponentName(context, RecoveryWidgetProvider::class.java)
        val ids = manager.getAppWidgetIds(component)
        if (ids.isEmpty()) return

        val scoreStr = recovery?.score?.let { "$it%" } ?: "—"
        val badgeStr = when (recovery?.state?.uppercase()) {
            "PRIMED" -> "PRIMED"
            "BALANCED" -> "STEADY"
            "REBUILD" -> "RECOVERING"
            else -> "CALIBRATING"
        }
        val adviceStr = recovery?.supportiveAdvice ?: "Calibrating your physiological baseline"

        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_recovery)
            views.setTextViewText(R.id.tv_recovery_score, scoreStr)
            views.setTextViewText(R.id.tv_recovery_badge, badgeStr)
            views.setTextViewText(R.id.tv_recovery_subtitle, adviceStr)

            val clickIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("destination", Screen.RecoveryDetails.route)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                REQUEST_CODE_RECOVERY,
                clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_recovery_root, pendingIntent)

            manager.updateAppWidget(id, views)
        }
    }

    private fun updateOverviewWidgets(
        context: Context,
        manager: AppWidgetManager,
        summary: com.kevan.hangry.data.local.entity.DailyHealthSummaryEntity?,
        recovery: com.kevan.hangry.data.local.entity.RecoveryScoreEntity?,
        latestSleep: com.kevan.hangry.data.local.entity.SleepSessionEntity?
    ) {
        val component = ComponentName(context, OverviewWidgetProvider::class.java)
        val ids = manager.getAppWidgetIds(component)
        if (ids.isEmpty()) return

        val recoveryScoreStr = recovery?.score?.let { "$it%" } ?: "—"
        val recoveryBadgeStr = when (recovery?.state?.uppercase()) {
            "PRIMED" -> "PRIMED"
            "BALANCED" -> "STEADY"
            "REBUILD" -> "RECOVERING"
            else -> "CALIBRATING"
        }

        val stepsStr = summary?.steps?.let { "${String.format("%,d", it)} steps" } ?: "— steps"
        val caloriesStr = summary?.activeCalories?.let { "${it.roundToInt()} kcal" } ?: "— kcal"

        val durationMinutes = latestSleep?.durationMinutes ?: summary?.sleepDurationMinutes
        val sleepStr = if (durationMinutes != null && durationMinutes > 0) {
            val h = durationMinutes / 60
            val m = durationMinutes % 60
            "Sleep: ${h}h ${m}m"
        } else {
            "Sleep: —"
        }

        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_overview)

            views.setTextViewText(R.id.tv_overview_recovery_score, recoveryScoreStr)
            views.setTextViewText(R.id.tv_overview_recovery_badge, recoveryBadgeStr)
            views.setTextViewText(R.id.tv_overview_steps, stepsStr)
            views.setTextViewText(R.id.tv_overview_calories, caloriesStr)
            views.setTextViewText(R.id.tv_overview_sleep, sleepStr)

            // Log meal button click
            val logIntent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_QUICK_LOG_MEAL
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("destination", Screen.Dashboard.route)
                putExtra("action", "quick_log_meal")
            }
            val logPendingIntent = PendingIntent.getActivity(
                context,
                REQUEST_CODE_QUICK_LOG,
                logIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_overview_log_meal, logPendingIntent)

            // Recovery section click
            val recIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("destination", Screen.RecoveryDetails.route)
            }
            val recPendingIntent = PendingIntent.getActivity(
                context,
                REQUEST_CODE_RECOVERY,
                recIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_overview_recovery, recPendingIntent)

            // Activity section click
            val actIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("destination", Screen.Dashboard.route)
            }
            val actPendingIntent = PendingIntent.getActivity(
                context,
                REQUEST_CODE_ACTIVITY,
                actIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_overview_activity, actPendingIntent)

            manager.updateAppWidget(id, views)
        }
    }
}
