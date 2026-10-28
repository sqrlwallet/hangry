package com.kevan.hangry.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.widget.RemoteViews
import com.kevan.hangry.MainActivity
import com.kevan.hangry.R
import com.kevan.hangry.data.local.entity.DailyHealthSummaryEntity
import com.kevan.hangry.data.nudges.BedtimeReminder
import com.kevan.hangry.di.AppContainer
import com.kevan.hangry.domain.calculation.HangryStrainCalculator
import com.kevan.hangry.domain.calculation.WaterIntake
import com.kevan.hangry.domain.model.RecoveryState
import com.kevan.hangry.ui.navigation.Screen
import com.kevan.hangry.ui.nutrition.WaterFormat
import com.kevan.hangry.ui.nutrition.waterText
import kotlinx.coroutines.flow.firstOrNull
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Water (2 × 2): today's drinks against the target, with buttons that log a glass or a bottle.
 * Strain (2 × 2): today's strain so far against the range today's recovery suggests.
 * Bedtime (2 × 2): tonight's suggested bedtime and how last night went.
 * Data is only loaded for widgets that are actually on the home screen.
 */
internal object DayWidgets {

    private const val RC_WATER_OPEN = 330
    private const val RC_WATER_GLASS = 331
    private const val RC_WATER_BOTTLE = 332
    private const val RC_STRAIN = 333
    private const val RC_BEDTIME = 334

    private const val COLOR_STRAIN = 0xFF00A4FF.toInt()
    private const val COLOR_STRAIN_TARGET = 0x5500A4FF

    suspend fun updateAll(
        context: Context, manager: AppWidgetManager, container: AppContainer, today: LocalDate,
        summary: DailyHealthSummaryEntity?, recoveryState: String?, hide: Boolean
    ) {
        fun ids(provider: Class<*>) = manager.getAppWidgetIds(ComponentName(context, provider))
        ids(WaterWidgetProvider::class.java).takeIf { it.isNotEmpty() }?.let { updateWater(context, manager, it, container, today, summary, hide) }
        ids(StrainWidgetProvider::class.java).takeIf { it.isNotEmpty() }?.let { updateStrain(context, manager, it, container, today, summary, recoveryState, hide) }
        ids(BedtimeWidgetProvider::class.java).takeIf { it.isNotEmpty() }?.let { updateBedtime(context, manager, it, summary) }
    }

    private fun open(context: Context, route: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("destination", route)
        }
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun duration(context: Context, minutes: Int): String {
        val (h, m) = DayWidgetText.hoursMinutes(minutes)
        return if (h > 0) context.getString(R.string.dashboard_widget_hours_minutes, h, m) else context.getString(R.string.dashboard_widget_minutes_short, m)
    }

    // region Water

    private suspend fun updateWater(
        context: Context, manager: AppWidgetManager, ids: IntArray, container: AppContainer,
        today: LocalDate, summary: DailyHealthSummaryEntity?, hide: Boolean
    ) {
        // The same sum as the Nutrition tab: other apps' drinks from the sync plus what's logged in Hangry.
        val intake = WaterIntake.totalMl(summary?.hydrationLiters, container.waterLogRepository.loggedMl(today))
        val target = WaterIntake.targetMl(container.userProfileRepository.getProfileSync()?.currentWeightKg?.takeIf { it > 0 })
        val res = context.resources
        val targetText = res.waterText(WaterFormat.amount(target))
        val percent = DayWidgetText.percent(intake.toDouble(), target.toDouble())
        val subtitle = when {
            hide -> WidgetText.HIDDEN_SUBTITLE
            intake == 0 -> context.getString(R.string.dashboard_widget_water_aim, targetText)
            intake >= target -> context.getString(R.string.dashboard_widget_water_reached, targetText)
            else -> context.getString(R.string.dashboard_widget_water_of_target, targetText)
        }
        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_water)
            views.setTextViewText(R.id.tv_water_value, if (hide) WidgetText.HIDDEN_VALUE else res.waterText(WaterFormat.amount(intake)))
            views.setTextViewText(R.id.tv_water_subtitle, subtitle)
            val chip = if (hide || percent == null || intake == 0) "" else context.getString(R.string.dashboard_widget_percent, percent)
            views.setTextViewText(R.id.tv_water_percent, chip)
            views.setViewVisibility(R.id.tv_water_percent, if (chip.isEmpty()) View.GONE else View.VISIBLE)
            views.setProgressBar(R.id.pb_water, 100, if (hide) 0 else DayWidgetText.progress(intake.toDouble(), target.toDouble()), false)
            bindWaterButton(context, views, R.id.btn_water_glass, WaterIntake.GLASS_ML, RC_WATER_GLASS)
            bindWaterButton(context, views, R.id.btn_water_bottle, WaterIntake.BOTTLE_ML, RC_WATER_BOTTLE)
            views.setOnClickPendingIntent(R.id.widget_water_root, open(context, Screen.Nutrition.route, RC_WATER_OPEN))
            manager.updateAppWidget(id, views)
        }
    }

    private fun bindWaterButton(context: Context, views: RemoteViews, buttonId: Int, ml: Int, requestCode: Int) {
        val amount = context.resources.waterText(WaterFormat.amount(ml))
        views.setTextViewText(buttonId, context.getString(R.string.nutrition_water_widget_add, amount))
        views.setContentDescription(buttonId, context.getString(R.string.dashboard_widget_water_log, amount))
        views.setOnClickPendingIntent(
            buttonId,
            PendingIntent.getBroadcast(
                context, requestCode, NutritionWidgetReceiver.logWaterIntent(context, ml),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
    }

    // endregion

    // region Strain

    private suspend fun updateStrain(
        context: Context, manager: AppWidgetManager, ids: IntArray, container: AppContainer,
        today: LocalDate, summary: DailyHealthSummaryEntity?, recoveryState: String?, hide: Boolean
    ) {
        val max = HangryStrainCalculator.MAX_STRAIN
        // A synced day with no heart rate or workout yet is 0 so far; no day at all is unknown.
        val strain = summary?.let { (it.dayStrain ?: 0.0).coerceIn(0.0, max) }
        val week = container.dailySummaryRepository.getSummariesBetween(today.minusDays(7), today.minusDays(1)).firstOrNull().orEmpty()
        val yesterday = week.firstOrNull { it.date == today.minusDays(1) }?.dayStrain?.coerceIn(0.0, max)
        // The same target as Today and the morning brief: today's recovery against the past week.
        val state = recoveryState?.let { runCatching { RecoveryState.valueOf(it.uppercase()) }.getOrNull() }
        val recent = week.mapNotNull { it.dayStrain }
        val resting = container.restModeRepository.state.value.isRestDay(today)
        val target = if (state != null && recent.isNotEmpty()) container.strainCalculator.recommendStrainTarget(state, recent, resting) else null

        val subtitle = when {
            hide -> WidgetText.HIDDEN_SUBTITLE
            target != null -> context.getString(R.string.dashboard_widget_strain_target, one(target.targetLow), one(target.targetHigh))
            yesterday != null -> context.getString(R.string.dashboard_widget_strain_yesterday, one(yesterday))
            else -> context.getString(R.string.dashboard_widget_strain_so_far)
        }
        val status = if (strain != null && target != null) DayWidgetText.strainStatus(strain, target.targetLow, target.targetHigh) else null
        val (chip, chipColor) = when (status) {
            DayWidgetText.StrainStatus.ON_TARGET -> context.getString(R.string.dashboard_widget_strain_on_target) to R.color.widget_green
            DayWidgetText.StrainStatus.ABOVE -> context.getString(R.string.dashboard_widget_strain_above_target) to R.color.widget_amber
            else -> "" to R.color.widget_azure
        }
        val workouts = summary?.exerciseCount ?: 0
        val workoutText = when {
            hide || summary == null -> ""
            workouts == 0 -> context.getString(R.string.dashboard_widget_strain_no_workouts)
            else -> context.resources.getQuantityString(
                R.plurals.dashboard_widget_strain_workouts, workouts, workouts, duration(context, summary?.exerciseDurationMinutes ?: 0)
            )
        }
        val bar = if (hide) null else drawStrainBar(strain, target?.targetLow, target?.targetHigh)

        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_strain)
            views.setTextViewText(R.id.tv_strain_value, when {
                strain == null -> "—"
                hide -> WidgetText.HIDDEN_VALUE
                else -> one(strain)
            })
            views.setViewVisibility(R.id.tv_strain_out_of, if (strain == null || hide) View.GONE else View.VISIBLE)
            val shownChip = if (hide) "" else chip
            views.setTextViewText(R.id.tv_strain_target, shownChip)
            WidgetColors.setTextColor(context, views, R.id.tv_strain_target, chipColor)
            views.setViewVisibility(R.id.tv_strain_target, if (shownChip.isEmpty()) View.GONE else View.VISIBLE)
            if (bar != null) {
                views.setImageViewBitmap(R.id.iv_strain_bar, bar)
                views.setViewVisibility(R.id.iv_strain_bar, View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.iv_strain_bar, View.INVISIBLE)
            }
            views.setTextViewText(R.id.tv_strain_subtitle, subtitle)
            views.setTextViewText(R.id.tv_strain_workouts, workoutText)
            views.setOnClickPendingIntent(R.id.widget_strain_root, open(context, Screen.Training.route, RC_STRAIN))
            manager.updateAppWidget(id, views)
        }
    }

    private fun one(value: Double) = String.format(Locale.US, "%.1f", value)

    /** The 0-21 scale as a bar: the recommended range shaded, today's strain filled in. */
    private fun drawStrainBar(strain: Double?, low: Double?, high: Double?): Bitmap {
        val width = 480
        val height = 36
        val max = HangryStrainCalculator.MAX_STRAIN.toFloat()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val radius = height / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), radius, radius, paint.apply { color = WidgetColors.TRACK })
        if (low != null && high != null && high > low) {
            val left = width * (low.toFloat() / max)
            val right = width * (high.toFloat() / max)
            canvas.drawRoundRect(RectF(left, 0f, right, height.toFloat()), radius, radius, paint.apply { color = COLOR_STRAIN_TARGET })
        }
        if (strain != null && strain > 0) {
            val right = (width * (strain.toFloat() / max)).coerceAtLeast(height.toFloat())
            canvas.drawRoundRect(RectF(0f, 0f, right, height.toFloat()), radius, radius, paint.apply { color = COLOR_STRAIN })
        }
        return bitmap
    }

    // endregion

    // region Bedtime

    private suspend fun updateBedtime(context: Context, manager: AppWidgetManager, ids: IntArray, summary: DailyHealthSummaryEntity?) {
        val plan = BedtimeReminder.tonightsPlan(context)
        val timeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
        val bedtime: LocalTime? = plan?.recommendedBedtime
        val subtitle = if (plan == null || bedtime == null) {
            context.getString(R.string.dashboard_widget_bedtime_need_sleep)
        } else {
            context.getString(
                R.string.dashboard_widget_bedtime_wind_down,
                bedtime.minusMinutes(BedtimeReminder.MINUTES_AHEAD.toLong()).format(timeFormat),
                duration(context, plan.tonightsNeedMinutes)
            )
        }
        val debt = plan?.sleepDebtMinutes?.takeIf { bedtime != null && it >= DayWidgetText.DEBT_WORTH_SHOWING_MINUTES }
        val chip = debt?.let { context.getString(R.string.dashboard_widget_bedtime_debt, duration(context, it)) }.orEmpty()

        // Last night is today's summary: sleep counts on the day it ends.
        val slept = summary?.sleepDurationMinutes?.takeIf { it > 0 }
        val ofNeed = slept?.let { s -> summary?.sleepNeedMinutes?.takeIf { it > 0 }?.let { DayWidgetText.percent(s.toDouble(), it.toDouble()) } }
        val lastNight = when {
            slept == null -> context.getString(R.string.dashboard_widget_bedtime_no_sleep_yet)
            ofNeed != null -> context.getString(R.string.dashboard_widget_bedtime_last_night_need, duration(context, slept), ofNeed)
            else -> context.getString(R.string.dashboard_widget_bedtime_last_night, duration(context, slept))
        }

        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_bedtime)
            views.setTextViewText(R.id.tv_bedtime_time, bedtime?.format(timeFormat) ?: "—")
            views.setTextViewText(R.id.tv_bedtime_subtitle, subtitle)
            views.setTextViewText(R.id.tv_bedtime_goal, chip)
            views.setViewVisibility(R.id.tv_bedtime_goal, if (chip.isEmpty()) View.GONE else View.VISIBLE)
            views.setTextViewText(R.id.tv_bedtime_last_night, lastNight)
            views.setOnClickPendingIntent(R.id.widget_bedtime_root, open(context, Screen.Sleep.route, RC_BEDTIME))
            manager.updateAppWidget(id, views)
        }
    }

    // endregion
}
