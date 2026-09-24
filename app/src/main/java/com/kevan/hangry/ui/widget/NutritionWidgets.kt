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
import com.kevan.hangry.di.AppContainer
import com.kevan.hangry.domain.calculation.NutritionTargets
import com.kevan.hangry.domain.model.CommonFoods
import com.kevan.hangry.ui.navigation.Screen
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * Calories (2 × 2): what's been eaten today against the daily target, as a ring.
 * Nutrition (4 × 2): the same ring plus protein / carbs / fat, and quick-add buttons that log a
 * saved meal straight from the home screen without opening the app.
 */
internal object NutritionWidgets {

    private const val RC_CALORIES = 301
    private const val RC_NUTRITION = 302
    private const val RC_NUTRITION_CAMERA = 303
    /** Quick-add buttons use RC_QUICK_ADD + slot, so each keeps its own extras. */
    private const val RC_QUICK_ADD = 310

    private const val COLOR_TRACK = 0xFF2F2C33.toInt()
    private const val COLOR_CALORIES = 0xFFFF7E1D.toInt()
    private const val COLOR_OVER = 0xFFFF6B6B.toInt()
    private const val COLOR_MUTED = 0xFFB5AEB8.toInt()

    private val QUICK_ADD_SLOTS = listOf(R.id.btn_nutrition_add_1, R.id.btn_nutrition_add_2, R.id.btn_nutrition_add_3)

    suspend fun updateAll(context: Context, manager: AppWidgetManager, container: AppContainer, today: LocalDate, hide: Boolean) {
        val caloriesIds = manager.getAppWidgetIds(ComponentName(context, CaloriesWidgetProvider::class.java))
        val nutritionIds = manager.getAppWidgetIds(ComponentName(context, NutritionWidgetProvider::class.java))
        if (caloriesIds.isEmpty() && nutritionIds.isEmpty()) return

        val entries = container.foodLogRepository.getForDate(today).first()
        val consumed = entries.sumOf { it.calories }
        val target = NutritionTargets.dailyCalories(container.bodyMetricsRepository.current().input.energy?.estimate)
        val text = NutritionWidgetText.calories(consumed, entries.size, target, hide)
        val ring = drawRing(text.fraction, text.over)

        for (id in caloriesIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_calories)
            bindCalories(views, text, ring, R.id.iv_calories_ring, R.id.tv_calories_value, R.id.tv_calories_subtitle, R.id.tv_calories_percent)
            views.setOnClickPendingIntent(R.id.widget_calories_root, open(context, Screen.Nutrition.route, RC_CALORIES))
            manager.updateAppWidget(id, views)
        }

        if (nutritionIds.isEmpty()) return
        val goals = NutritionTargets.macros(target)
        val saved = container.mealPlanRepository.getAll().first()
        val savedKeys = saved.map { it.nameKey }.toSet()
        // Most recent saved meals first; basics fill the rest until there are enough saved.
        val slots = saved.take(QUICK_ADD_SLOTS.size).map {
            NutritionWidgetText.quickAddLabel(it.name, it.calories, hide) to NutritionWidgetReceiver.logSavedIntent(context, it.id)
        } + CommonFoods.starters.filter { it.key !in savedKeys }.map {
            NutritionWidgetText.quickAddLabel(it.name, it.calories, hide) to NutritionWidgetReceiver.logCommonIntent(context, it.name)
        }

        for (id in nutritionIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_nutrition)
            bindCalories(views, text, ring, R.id.iv_nutrition_ring, R.id.tv_nutrition_value, R.id.tv_nutrition_subtitle, R.id.tv_nutrition_percent)
            bindMacro(views, R.id.tv_nutrition_protein, R.id.pb_nutrition_protein, entries.sumOf { it.proteinG }, goals?.proteinG, hide)
            bindMacro(views, R.id.tv_nutrition_carbs, R.id.pb_nutrition_carbs, entries.sumOf { it.carbsG }, goals?.carbsG, hide)
            bindMacro(views, R.id.tv_nutrition_fat, R.id.pb_nutrition_fat, entries.sumOf { it.fatG }, goals?.fatG, hide)
            QUICK_ADD_SLOTS.forEachIndexed { i, buttonId ->
                val slot = slots.getOrNull(i)
                views.setViewVisibility(buttonId, if (slot == null) View.GONE else View.VISIBLE)
                if (slot != null) {
                    views.setTextViewText(buttonId, slot.first)
                    views.setOnClickPendingIntent(
                        buttonId,
                        PendingIntent.getBroadcast(context, RC_QUICK_ADD + i, slot.second, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    )
                }
            }
            views.setOnClickPendingIntent(R.id.widget_nutrition_root, open(context, Screen.Nutrition.route, RC_NUTRITION))
            views.setOnClickPendingIntent(R.id.btn_nutrition_camera, quickLogMeal(context))
            manager.updateAppWidget(id, views)
        }
    }

    private fun bindCalories(
        views: RemoteViews, text: NutritionWidgetText.CalorieText, ring: Bitmap,
        ringId: Int, valueId: Int, subtitleId: Int, percentId: Int
    ) {
        views.setImageViewBitmap(ringId, ring)
        views.setTextViewText(valueId, text.value)
        views.setTextViewText(subtitleId, text.subtitle)
        views.setTextViewText(percentId, text.percent.orEmpty())
        views.setTextColor(percentId, if (text.over) COLOR_OVER else COLOR_CALORIES)
        views.setViewVisibility(percentId, if (text.percent == null) View.GONE else View.VISIBLE)
    }

    private fun bindMacro(views: RemoteViews, textId: Int, barId: Int, grams: Double, goal: Double?, hide: Boolean) {
        views.setTextViewText(textId, NutritionWidgetText.macro(grams, goal, hide))
        views.setTextColor(textId, if (goal == null && !hide) COLOR_MUTED else 0xFFFFFFFF.toInt())
        views.setProgressBar(barId, 100, NutritionWidgetText.macroPercent(grams, goal, hide), false)
    }

    private fun open(context: Context, route: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("destination", route)
        }
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** The same camera shortcut as the Quick Log widget. */
    private fun quickLogMeal(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = HangryWidgetUpdater.ACTION_QUICK_LOG_MEAL
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("destination", Screen.Dashboard.route)
            putExtra("action", "quick_log_meal")
        }
        return PendingIntent.getActivity(context, RC_NUTRITION_CAMERA, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** A progress ring drawn to a bitmap, since widgets can't draw arcs. Full and red when over target. */
    private fun drawRing(fraction: Float?, over: Boolean): Bitmap {
        val size = 220
        val stroke = 22f
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bounds = RectF(stroke / 2, stroke / 2, size - stroke / 2, size - stroke / 2)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawArc(bounds, 0f, 360f, false, paint.apply { color = COLOR_TRACK })
        if (fraction != null && fraction > 0f) {
            val sweep = if (over) 360f else (360f * fraction).coerceAtLeast(4f)
            canvas.drawArc(bounds, -90f, sweep, false, paint.apply { color = if (over) COLOR_OVER else COLOR_CALORIES })
        }
        return bitmap
    }
}
