package com.kevan.hangry.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import com.kevan.hangry.MainActivity
import com.kevan.hangry.R
import com.kevan.hangry.data.fasting.FastingReceiver
import com.kevan.hangry.domain.model.FastingMath
import com.kevan.hangry.domain.model.FastingSnapshot
import com.kevan.hangry.domain.model.FastingStage
import com.kevan.hangry.domain.repository.FastingRepository
import com.kevan.hangry.ui.navigation.Screen
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Fasting timer: a live Chronometer while fasting, with Start/End that work without opening the
 * app. When fasting is off it just offers to set it up.
 */
internal object FastingWidget {

    private const val RC_OPEN = 220
    private const val RC_ACTION = 221
    private val timeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

    suspend fun update(context: Context, manager: AppWidgetManager, repository: FastingRepository) {
        val ids = manager.getAppWidgetIds(ComponentName(context, FastingWidgetProvider::class.java))
        if (ids.isEmpty()) return
        val snapshot = repository.current()
        for (id in ids) manager.updateAppWidget(id, views(context, snapshot))
    }

    private fun views(context: Context, s: FastingSnapshot): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_fasting)
        views.setOnClickPendingIntent(R.id.widget_fasting_root, open(context))
        val active = s.active
        when {
            !s.enabled -> {
                views.setTextViewText(R.id.tv_fasting_label, context.getString(R.string.dashboard_widget_fasting_label))
                views.setTextViewText(R.id.tv_fasting_streak, context.getString(R.string.dashboard_widget_fasting_off))
                showHeadline(views, context.getString(R.string.dashboard_widget_fasting_is_off))
                views.setTextViewText(R.id.tv_fasting_subtitle, context.getString(R.string.dashboard_widget_fasting_turn_on))
                views.setViewVisibility(R.id.pb_fasting, View.GONE)
                views.setTextViewText(R.id.btn_fasting_action, context.getString(R.string.dashboard_widget_fasting_set_up))
                views.setOnClickPendingIntent(R.id.btn_fasting_action, open(context))
            }
            active == null -> {
                views.setTextViewText(R.id.tv_fasting_label, context.getString(R.string.dashboard_widget_fasting_eating_window))
                views.setTextViewText(R.id.tv_fasting_streak, streakChip(s))
                showHeadline(views, context.getString(R.string.dashboard_widget_fasting_not_fasting))
                val last = s.lastFinished
                views.setTextViewText(
                    R.id.tv_fasting_subtitle,
                    last?.let {
                        context.getString(
                            if (it.reachedGoal()) R.string.dashboard_widget_fasting_last_fast_reached else R.string.dashboard_widget_fasting_last_fast,
                            FastingMath.formatDuration(it.elapsed())
                        )
                    } ?: context.getString(R.string.dashboard_widget_fasting_start_when_done, s.plan.label)
                )
                views.setViewVisibility(R.id.pb_fasting, View.GONE)
                views.setTextViewText(R.id.btn_fasting_action, context.getString(R.string.dashboard_widget_fasting_start))
                views.setOnClickPendingIntent(R.id.btn_fasting_action, broadcast(context, FastingReceiver.ACTION_START))
            }
            else -> {
                val now = Instant.now()
                val elapsed = active.elapsed(now)
                views.setTextViewText(R.id.tv_fasting_label, context.getString(R.string.dashboard_widget_fasting_stage_label, FastingStage.at(elapsed).title.uppercase(Locale.getDefault())))
                views.setTextViewText(R.id.tv_fasting_streak, streakChip(s))
                views.setViewVisibility(R.id.tv_fasting_headline, View.GONE)
                views.setViewVisibility(R.id.ch_fasting_elapsed, View.VISIBLE)
                views.setChronometer(R.id.ch_fasting_elapsed, SystemClock.elapsedRealtime() - elapsed.toMillis(), null, true)
                val subtitle = if (active.reachedGoal(now)) {
                    context.getString(R.string.dashboard_widget_fasting_goal_reached, active.targetMinutes / 60)
                } else {
                    val left = Duration.between(now, active.goalAt())
                    context.getString(R.string.dashboard_widget_fasting_time_left, FastingMath.formatDuration(left), active.goalAt().atZone(ZoneId.systemDefault()).format(timeFormat))
                }
                views.setTextViewText(R.id.tv_fasting_subtitle, subtitle)
                views.setViewVisibility(R.id.pb_fasting, View.VISIBLE)
                views.setProgressBar(R.id.pb_fasting, 100, (active.progress(now) * 100).toInt(), false)
                views.setTextViewText(R.id.btn_fasting_action, context.getString(R.string.dashboard_widget_fasting_end))
                views.setOnClickPendingIntent(R.id.btn_fasting_action, broadcast(context, FastingReceiver.ACTION_END))
            }
        }
        return views
    }

    private fun showHeadline(views: RemoteViews, text: String) {
        views.setViewVisibility(R.id.ch_fasting_elapsed, View.GONE)
        views.setChronometer(R.id.ch_fasting_elapsed, SystemClock.elapsedRealtime(), null, false)
        views.setViewVisibility(R.id.tv_fasting_headline, View.VISIBLE)
        views.setTextViewText(R.id.tv_fasting_headline, text)
    }

    private fun streakChip(s: FastingSnapshot) = if (s.streak > 0) "🔥 ${s.streak}" else s.plan.label

    private fun open(context: Context): PendingIntent = PendingIntent.getActivity(
        context, RC_OPEN,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra("destination", Screen.Fasting.route),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun broadcast(context: Context, action: String): PendingIntent = PendingIntent.getBroadcast(
        context, RC_ACTION,
        Intent(context, FastingReceiver::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
