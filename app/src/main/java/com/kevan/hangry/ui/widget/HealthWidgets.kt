package com.kevan.hangry.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.view.View
import android.widget.RemoteViews
import com.kevan.hangry.MainActivity
import com.kevan.hangry.R
import com.kevan.hangry.data.local.entity.WeightMeasurementEntity
import com.kevan.hangry.di.AppContainer
import com.kevan.hangry.domain.calculation.HealthMarkerCalculator
import com.kevan.hangry.domain.model.BreathingPattern
import com.kevan.hangry.domain.model.HealthRecordsSnapshot
import com.kevan.hangry.domain.model.MarkerType
import com.kevan.hangry.domain.model.MetricTone
import com.kevan.hangry.ui.navigation.Screen
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Health widgets: Breathe, Heart, Health Markers, Goals, Weight, Posture
 * and Cycle. Data is only loaded for widgets that are actually on the home screen.
 */
internal object HealthWidgets {

    private const val RC_BREATHE = 201
    private const val RC_BREATHE_A = 202
    private const val RC_BREATHE_B = 203
    private const val RC_HEART = 204
    private const val RC_MARKERS = 205
    private const val RC_MARKERS_ADD = 206
    private const val RC_GOALS = 207
    private const val RC_WEIGHT = 208
    private const val RC_POSTURE = 209
    private const val RC_POSTURE_NEW = 210
    private const val RC_CYCLE = 211

    private const val COLOR_MUTED = 0xFFB5AEB8.toInt()
    private const val COLOR_GOOD = 0xFF01A652.toInt()
    private const val COLOR_CAUTION = 0xFFF7931E.toInt()
    private const val COLOR_RISK = 0xFFFF6B6B.toInt()

    suspend fun updateAll(context: Context, manager: AppWidgetManager, container: AppContainer, today: LocalDate) {
        val hide = WidgetPrefs.hideValues(context)
        fun ids(provider: Class<*>) = manager.getAppWidgetIds(ComponentName(context, provider))

        // Health Markers, Goals and Cycle all read the same snapshot; load it once, on demand.
        var records: HealthRecordsSnapshot? = null
        suspend fun records() = records ?: container.healthRecordsRepository.current().also { records = it }
        var weights: List<WeightMeasurementEntity>? = null
        suspend fun weights() = weights ?: container.database.weightDao().getAllWeights().first().also { weights = it }

        ids(BreatheWidgetProvider::class.java).takeIf { it.isNotEmpty() }?.let { updateBreathe(context, manager, it, container) }
        ids(HeartWidgetProvider::class.java).takeIf { it.isNotEmpty() }?.let { updateHeart(context, manager, it, container, today, hide) }
        ids(HealthMarkersWidgetProvider::class.java).takeIf { it.isNotEmpty() }?.let { updateMarkers(context, manager, it, records(), hide) }
        ids(GoalsWidgetProvider::class.java).takeIf { it.isNotEmpty() }?.let {
            updateGoals(context, manager, it, records(), weights(), container.userProfileRepository.getProfileSync()?.weightGoalKg, hide)
        }
        ids(WeightWidgetProvider::class.java).takeIf { it.isNotEmpty() }?.let { updateWeight(context, manager, it, weights(), hide) }
        ids(PostureWidgetProvider::class.java).takeIf { it.isNotEmpty() }?.let { updatePosture(context, manager, it, container, today, hide) }
        ids(CycleWidgetProvider::class.java).takeIf { it.isNotEmpty() }?.let { updateCycle(context, manager, it, records(), today, hide) }
    }

    private fun open(context: Context, route: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("destination", route)
        }
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun toneColor(tone: MetricTone): Int = when (tone) {
        MetricTone.GOOD -> COLOR_GOOD
        MetricTone.CAUTION -> COLOR_CAUTION
        MetricTone.RISK -> COLOR_RISK
        MetricTone.NEUTRAL -> COLOR_MUTED
    }

    // region Breathe

    private suspend fun updateBreathe(context: Context, manager: AppWidgetManager, ids: IntArray, container: AppContainer) {
        val stats = container.breathingRepository.observeStats().first()
        val usual = BreathingPattern.fromId(container.breathingRepository.getRecentSessions(1).first().firstOrNull()?.patternId)
        val others = BreathingPattern.entries.filter { it != usual }.take(2)
        val subtitle = if (stats.sessionsToday == 0) {
            "Tap to start ${usual.shortLabel}"
        } else {
            "today · ${stats.sessionsToday} session${if (stats.sessionsToday == 1) "" else "s"}"
        }
        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_breathe)
            views.setTextViewText(R.id.tv_breathe_today, "${stats.minutesToday} min")
            views.setTextViewText(R.id.tv_breathe_subtitle, subtitle)
            views.setTextViewText(R.id.tv_breathe_week, "${stats.minutesThisWeek} min / 7d")
            views.setTextViewText(R.id.btn_breathe_a, others[0].shortLabel)
            views.setTextViewText(R.id.btn_breathe_b, others[1].shortLabel)
            views.setOnClickPendingIntent(R.id.widget_breathe_root, open(context, Screen.Breathing.createRoute(usual.id, start = true), RC_BREATHE))
            views.setOnClickPendingIntent(R.id.btn_breathe_a, open(context, Screen.Breathing.createRoute(others[0].id, start = true), RC_BREATHE_A))
            views.setOnClickPendingIntent(R.id.btn_breathe_b, open(context, Screen.Breathing.createRoute(others[1].id, start = true), RC_BREATHE_B))
            manager.updateAppWidget(id, views)
        }
    }

    // endregion

    // region Heart

    private suspend fun updateHeart(
        context: Context, manager: AppWidgetManager, ids: IntArray, container: AppContainer, today: LocalDate, hide: Boolean
    ) {
        val summaries = container.dailySummaryRepository.getSummariesBetween(today.minusDays(35), today).first()
        val rhr = WidgetText.heartReading(summaries, today) { it.restingHeartRate }
        val hrv = WidgetText.heartReading(summaries, today) { it.hrvRmssd }
        val subtitle = when {
            hide -> WidgetText.HIDDEN_SUBTITLE
            rhr == null && hrv == null -> "No heart data synced yet"
            rhr?.baseline == null && hrv?.baseline == null -> "Building your 4-week normal"
            else -> "vs your 4-week normal"
        }
        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_heart)
            bindHeartRow(views, R.id.tv_heart_rhr, R.id.tv_heart_rhr_delta, rhr, "bpm", higherIsBetter = false, hide)
            bindHeartRow(views, R.id.tv_heart_hrv, R.id.tv_heart_hrv_delta, hrv, "ms", higherIsBetter = true, hide)
            views.setTextViewText(R.id.tv_heart_subtitle, subtitle)
            views.setOnClickPendingIntent(R.id.widget_heart_root, open(context, Screen.HeartMetrics.route, RC_HEART))
            manager.updateAppWidget(id, views)
        }
    }

    private fun bindHeartRow(
        views: RemoteViews, valueId: Int, deltaId: Int, reading: WidgetText.HeartReading?,
        unit: String, higherIsBetter: Boolean, hide: Boolean
    ) {
        views.setTextViewText(valueId, when {
            reading == null -> "—"
            hide -> WidgetText.HIDDEN_VALUE
            else -> "${reading.value.roundToInt()} $unit"
        })
        val delta = reading?.delta
        views.setTextViewText(deltaId, if (hide) "" else WidgetText.deltaText(delta).orEmpty())
        views.setTextColor(deltaId, when (WidgetText.isImprovement(delta, higherIsBetter)) {
            true -> COLOR_GOOD
            false -> COLOR_CAUTION
            null -> COLOR_MUTED
        })
    }

    // endregion

    // region Health markers

    private fun updateMarkers(context: Context, manager: AppWidgetManager, ids: IntArray, records: HealthRecordsSnapshot, hide: Boolean) {
        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_markers)
            bindMarker(views, R.id.tv_markers_bp, R.id.tv_markers_bp_status, MarkerType.BLOOD_PRESSURE, records, hide)
            bindMarker(views, R.id.tv_markers_glucose, R.id.tv_markers_glucose_status, MarkerType.BLOOD_GLUCOSE, records, hide)
            views.setOnClickPendingIntent(R.id.widget_markers_root, open(context, Screen.HealthRecords.route, RC_MARKERS))
            views.setOnClickPendingIntent(R.id.btn_markers_add, open(context, Screen.HealthRecords.route, RC_MARKERS_ADD))
            manager.updateAppWidget(id, views)
        }
    }

    private fun bindMarker(views: RemoteViews, valueId: Int, statusId: Int, type: MarkerType, records: HealthRecordsSnapshot, hide: Boolean) {
        val latest = records.latest(type)
        val metric = HealthMarkerCalculator.describe(type, latest, records.sex)
        views.setTextViewText(valueId, when {
            latest == null -> "—"
            hide -> WidgetText.HIDDEN_VALUE
            else -> "${metric.displayValue} ${metric.unit}"
        })
        val status = if (hide || latest == null) "" else metric.status.orEmpty()
        views.setTextViewText(statusId, status)
        views.setViewVisibility(statusId, if (status.isEmpty()) View.GONE else View.VISIBLE)
        views.setTextColor(statusId, toneColor(metric.tone))
    }

    // endregion

    // region Goals

    private data class GoalRow(val label: String, val detail: String, val fraction: Double?, val reached: Boolean)

    private val GOAL_ROWS = listOf(
        Triple(R.id.row_goal_1, R.id.tv_goal_1_label, R.id.tv_goal_1_detail) to R.id.pb_goal_1,
        Triple(R.id.row_goal_2, R.id.tv_goal_2_label, R.id.tv_goal_2_detail) to R.id.pb_goal_2,
        Triple(R.id.row_goal_3, R.id.tv_goal_3_label, R.id.tv_goal_3_detail) to R.id.pb_goal_3
    )

    private fun updateGoals(
        context: Context, manager: AppWidgetManager, ids: IntArray, records: HealthRecordsSnapshot,
        weights: List<WeightMeasurementEntity>, weightGoalKg: Double?, hide: Boolean
    ) {
        val rows = buildList {
            weightGoalRow(weights, weightGoalKg)?.let(::add)
            records.goals.filter { it.type.isVisibleFor(records.sex) }.forEach { goal ->
                val latest = records.latest(goal.type)
                val progress = HealthMarkerCalculator.progress(goal, latest)
                val unit = goal.type.canonicalUnit
                val detail = when {
                    latest == null -> "No reading yet"
                    progress.reached -> "Reached"
                    else -> "${HealthMarkerCalculator.format(goal.type, latest.value, latest.secondaryValue)} → " +
                        "${HealthMarkerCalculator.format(goal.type, goal.targetValue, goal.targetSecondary)} $unit"
                }
                add(GoalRow(goal.type.label, detail, progress.fraction, progress.reached))
            }
        }
        val shown = rows.take(GOAL_ROWS.size)
        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_goals)
            GOAL_ROWS.forEachIndexed { i, (textIds, barId) ->
                val (rowId, labelId, detailId) = textIds
                val row = shown.getOrNull(i)
                views.setViewVisibility(rowId, if (row == null) View.GONE else View.VISIBLE)
                if (row != null) {
                    views.setTextViewText(labelId, row.label)
                    views.setTextViewText(detailId, if (hide) "" else row.detail)
                    views.setTextColor(detailId, if (row.reached) COLOR_GOOD else COLOR_MUTED)
                    val percent = ((row.fraction ?: if (row.reached) 1.0 else 0.0) * 100).roundToInt()
                    views.setProgressBar(barId, 100, percent, false)
                }
            }
            views.setViewVisibility(R.id.tv_goals_empty, if (rows.isEmpty()) View.VISIBLE else View.GONE)
            val reached = rows.count { it.reached }
            views.setTextViewText(R.id.tv_goals_count, if (rows.isEmpty()) "" else "$reached/${rows.size} reached")
            views.setViewVisibility(R.id.tv_goals_count, if (rows.isEmpty()) View.GONE else View.VISIBLE)
            views.setOnClickPendingIntent(R.id.widget_goals_root, open(context, Screen.HealthRecords.route, RC_GOALS))
            manager.updateAppWidget(id, views)
        }
    }

    /**
     * Progress is measured from the oldest weigh-in in the last 6 months - the app doesn't
     * store when the goal was set, and that's the closest honest starting point.
     */
    private fun weightGoalRow(weights: List<WeightMeasurementEntity>, goalKg: Double?): GoalRow? {
        goalKg ?: return null
        val latest = weights.firstOrNull()?.weightKg ?: return GoalRow("Weight", "No weigh-in yet", null, false)
        val start = weights.lastOrNull { Duration.between(it.timestamp, Instant.now()).toDays() <= 183 }?.weightKg
        val reached = when {
            start == null || abs(start - goalKg) < 0.05 -> abs(latest - goalKg) < 0.25
            start > goalKg -> latest <= goalKg
            else -> latest >= goalKg
        }
        val fraction = when {
            reached -> 1.0
            start == null || abs(start - goalKg) < 0.05 -> null
            else -> ((start - latest) / (start - goalKg)).coerceIn(0.0, 1.0)
        }
        val detail = if (reached) "Reached" else String.format(Locale.US, "%.1f → %.1f kg", latest, goalKg)
        return GoalRow("Weight", detail, fraction, reached)
    }

    // endregion

    // region Weight

    private fun updateWeight(context: Context, manager: AppWidgetManager, ids: IntArray, weights: List<WeightMeasurementEntity>, hide: Boolean) {
        val latest = weights.firstOrNull()
        val cutoff = Instant.now().minus(Duration.ofDays(30))
        val month = weights.filter { it.timestamp.isAfter(cutoff) }.sortedBy { it.timestamp }
        val change = if (month.size >= 2) month.last().weightKg - month.first().weightKg else null
        val chart = if (!hide && month.size >= 2) drawSparkline(month.map { it.weightKg }) else null
        val subtitle = when {
            hide -> WidgetText.HIDDEN_SUBTITLE
            latest == null -> "No weigh-ins yet"
            month.size < 2 -> "Log more weigh-ins to see a trend"
            else -> "Last 30 days"
        }
        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_weight)
            views.setTextViewText(R.id.tv_weight_value, when {
                latest == null -> "—"
                hide -> WidgetText.HIDDEN_VALUE
                else -> String.format(Locale.US, "%.1f kg", latest.weightKg)
            })
            val changeText = if (hide || change == null) "" else String.format(Locale.US, "%+.1f kg", change)
            views.setTextViewText(R.id.tv_weight_change, changeText)
            views.setViewVisibility(R.id.tv_weight_change, if (changeText.isEmpty()) View.GONE else View.VISIBLE)
            views.setTextViewText(R.id.tv_weight_subtitle, subtitle)
            if (chart != null) {
                views.setImageViewBitmap(R.id.iv_weight_chart, chart)
                views.setViewVisibility(R.id.iv_weight_chart, View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.iv_weight_chart, View.INVISIBLE)
            }
            views.setOnClickPendingIntent(R.id.widget_weight_root, open(context, Screen.Trends.route, RC_WEIGHT))
            manager.updateAppWidget(id, views)
        }
    }

    /** A small line chart with a soft fill, drawn to a bitmap since widgets can't draw charts. */
    private fun drawSparkline(values: List<Double>): Bitmap {
        val width = 480
        val height = 120
        val pad = 8f
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val min = values.min()
        val max = values.max()
        val range = (max - min).takeIf { it > 0.0 } ?: 1.0
        val points = values.mapIndexed { i, v ->
            val x = pad + (width - 2 * pad) * i / (values.size - 1).coerceAtLeast(1)
            val y = pad + (height - 2 * pad) * (1 - ((v - min) / range)).toFloat()
            x to y
        }
        val line = Path().apply {
            moveTo(points.first().first, points.first().second)
            points.drop(1).forEach { (x, y) -> lineTo(x, y) }
        }
        val fill = Path(line).apply {
            lineTo(points.last().first, height.toFloat())
            lineTo(points.first().first, height.toFloat())
            close()
        }
        val accent = 0xFF6AA8FF.toInt()
        canvas.drawPath(fill, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, 0f, height.toFloat(), 0x556AA8FF, 0x006AA8FF, Shader.TileMode.CLAMP)
        })
        canvas.drawPath(line, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accent
            style = Paint.Style.STROKE
            strokeWidth = 5f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        })
        val (lx, ly) = points.last()
        canvas.drawCircle(lx, ly, 7f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent })
        return bitmap
    }

    // endregion

    // region Posture

    private suspend fun updatePosture(
        context: Context, manager: AppWidgetManager, ids: IntArray, container: AppContainer, today: LocalDate, hide: Boolean
    ) {
        val latest = container.postureScanRepository.getLatest().first()
        val subtitle = when {
            latest == null -> "No checks yet"
            else -> {
                val days = ChronoUnit.DAYS.between(latest.date, today)
                val nudge = if (days >= WidgetText.POSTURE_RECHECK_DAYS) " · time for another" else ""
                "Checked ${WidgetText.daysAgoText(latest.date, today)}$nudge"
            }
        }
        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_posture)
            views.setTextViewText(R.id.tv_posture_score, when {
                latest == null -> "—"
                hide -> WidgetText.HIDDEN_VALUE
                else -> "${latest.score}"
            })
            views.setViewVisibility(R.id.tv_posture_out_of, if (latest == null || hide) View.GONE else View.VISIBLE)
            val badge = if (latest == null || hide) "" else WidgetText.postureBadge(latest.score)
            views.setTextViewText(R.id.tv_posture_badge, badge)
            views.setViewVisibility(R.id.tv_posture_badge, if (badge.isEmpty()) View.GONE else View.VISIBLE)
            views.setTextViewText(R.id.tv_posture_subtitle, subtitle)
            views.setOnClickPendingIntent(R.id.widget_posture_root, open(context, Screen.Posture.route, RC_POSTURE))
            views.setOnClickPendingIntent(R.id.btn_posture_new, open(context, Screen.PostureCapture.route, RC_POSTURE_NEW))
            manager.updateAppWidget(id, views)
        }
    }

    // endregion

    // region Cycle

    private fun updateCycle(context: Context, manager: AppWidgetManager, ids: IntArray, records: HealthRecordsSnapshot, today: LocalDate, hide: Boolean) {
        val text = when {
            !records.showsFemaleHealth -> WidgetText.CycleText("—", "Set your sex to female in Settings to track your cycle", "")
            hide -> WidgetText.CycleText("Cycle", WidgetText.HIDDEN_SUBTITLE, "")
            else -> WidgetText.cycleText(
                HealthMarkerCalculator.cycleStats(records.periods.map { it.startDate to it.endDate }, today),
                today
            )
        }
        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_cycle)
            views.setTextViewText(R.id.tv_cycle_day, text.headline)
            views.setTextViewText(R.id.tv_cycle_subtitle, text.subtitle)
            views.setTextViewText(R.id.tv_cycle_badge, text.badge)
            views.setViewVisibility(R.id.tv_cycle_badge, if (text.badge.isEmpty()) View.GONE else View.VISIBLE)
            views.setOnClickPendingIntent(R.id.widget_cycle_root, open(context, Screen.HealthRecords.route, RC_CYCLE))
            manager.updateAppWidget(id, views)
        }
    }

    // endregion
}
