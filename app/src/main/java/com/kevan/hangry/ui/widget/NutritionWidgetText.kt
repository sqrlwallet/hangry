package com.kevan.hangry.ui.widget

import java.util.Locale
import kotlin.math.roundToInt

/** Pure text logic behind the Calories and Nutrition widgets, kept apart from RemoteViews so it's testable. */
internal object NutritionWidgetText {

    data class CalorieText(
        val value: String,
        /** "62%" of the target, or null when there's no target (or values are hidden). */
        val percent: String?,
        val subtitle: String,
        /** How full the ring is, 0..1; null draws an empty track. */
        val fraction: Float?,
        val over: Boolean
    )

    fun calories(consumed: Int, itemCount: Int, target: Int?, hide: Boolean): CalorieText {
        if (hide) return CalorieText(WidgetText.HIDDEN_VALUE, null, WidgetText.HIDDEN_SUBTITLE, null, false)
        val items = if (itemCount == 1) "1 item" else "$itemCount items"
        if (target == null || target <= 0) {
            val subtitle = if (itemCount == 0) "Nothing logged yet" else "$items · no target yet"
            return CalorieText(kcal(consumed), null, subtitle, null, false)
        }
        val left = target - consumed
        val subtitle = when {
            itemCount == 0 -> "${kcal(target)} to go today"
            left >= 0 -> "${kcal(left)} left of ${kcal(target)}"
            else -> "${kcal(-left)} over ${kcal(target)}"
        }
        val percent = (consumed * 100.0 / target).roundToInt()
        return CalorieText(kcal(consumed), "$percent%", subtitle, (consumed.toFloat() / target).coerceIn(0f, 1f), left < 0)
    }

    /** "82 / 125 g", or "82 g" with no target. */
    fun macro(grams: Double, goal: Double?, hide: Boolean): String = when {
        hide -> WidgetText.HIDDEN_VALUE
        goal == null -> "${grams.roundToInt()} g"
        else -> "${grams.roundToInt()} / ${goal.roundToInt()} g"
    }

    fun macroPercent(grams: Double, goal: Double?, hide: Boolean): Int =
        if (hide || goal == null || goal <= 0) 0 else (grams / goal * 100).roundToInt().coerceIn(0, 100)

    /**
     * "+ Banana · 105" on a quick-add button - the name is cut short so the calories always
     * show. No calories when values are hidden.
     */
    fun quickAddLabel(name: String, calories: Int, hide: Boolean, maxName: Int = 11): String {
        val short = if (name.length <= maxName) name else name.take(maxName - 1).trimEnd() + "…"
        return if (hide) "+ $short" else "+ $short · $calories"
    }

    private fun kcal(value: Int): String = String.format(Locale.US, "%,d", value)
}
