package com.kevan.hangry.data.fasting

import android.content.Context
import com.kevan.hangry.domain.model.FastingPlan

/** Fasting settings. The feature is off until the user turns it on. */
class FastingPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("hangry_fasting", Context.MODE_PRIVATE)

    data class Settings(val enabled: Boolean, val plan: FastingPlan, val customHours: Int, val goalReminder: Boolean) {
        val targetHours: Int get() = if (plan == FastingPlan.CUSTOM) customHours else plan.fastHours
    }

    fun read() = Settings(
        enabled = prefs.getBoolean(KEY_ENABLED, false),
        plan = FastingPlan.fromId(prefs.getString(KEY_PLAN, null)),
        customHours = prefs.getInt(KEY_CUSTOM_HOURS, FastingPlan.DEFAULT.fastHours),
        goalReminder = prefs.getBoolean(KEY_GOAL_REMINDER, true)
    )

    fun write(settings: Settings) = prefs.edit()
        .putBoolean(KEY_ENABLED, settings.enabled)
        .putString(KEY_PLAN, settings.plan.id)
        .putInt(KEY_CUSTOM_HOURS, settings.customHours)
        .putBoolean(KEY_GOAL_REMINDER, settings.goalReminder)
        .apply()

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_PLAN = "plan"
        const val KEY_CUSTOM_HOURS = "custom_hours"
        const val KEY_GOAL_REMINDER = "goal_reminder"
    }
}
