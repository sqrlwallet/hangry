package com.kevan.hangry.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class WidgetType {
    RECOVERY_HERO,
    DAILY_ACTIVITY_RINGS,
    LOG_MEAL,
    STRESS_MONITOR,
    SLEEP_STRAIN_RINGS,
    HEART_METRICS,
    VITALS_CARD,
    BODY_FAT_COMPOSITION,
    SLEEP_SUMMARY,
    CALORIE_BURN,
    AI_SHORTCUTS,
    BREATHING,
    HEALTH_RECORDS,
    SUPPLEMENTS,
    STREAKS,
    BODY_AGE,
    FASTING,
    CUSTOM_METRIC
}

@Serializable
enum class MetricType {
    STEPS,
    ACTIVE_CALORIES,
    RHR,
    HRV,
    VO2_MAX,
    SPO2,
    SLEEP_DURATION,
    DAY_STRAIN,
    STRESS,
    WEIGHT
}

@Serializable
enum class WidgetDisplayStyle {
    RING,
    STAT_CARD,
    PROGRESS_BAR
}

@Serializable
data class DashboardWidget(
    val id: String,
    val type: WidgetType,
    val title: String,
    val isVisible: Boolean = true,
    val order: Int = 0,
    val metricType: MetricType? = null,
    val targetGoal: Double? = null,
    val unit: String? = null,
    val displayStyle: WidgetDisplayStyle = WidgetDisplayStyle.STAT_CARD
) {
    companion object {
        /**
         * Cards no longer shown on Today: recovery, sleep, strain and activity now share the
         * overview card, and Ask Dash, Nutrition and Log Meal live in the tab bar and button.
         * Kept in [WidgetType] so layouts saved by older versions still load.
         */
        val RETIRED_TYPES = setOf(
            WidgetType.DAILY_ACTIVITY_RINGS,
            WidgetType.LOG_MEAL,
            WidgetType.SLEEP_STRAIN_RINGS,
            WidgetType.SLEEP_SUMMARY,
            WidgetType.AI_SHORTCUTS
        )

        fun createDefaultWidgets(): List<DashboardWidget> = listOf(
            DashboardWidget(
                id = "recovery_hero",
                type = WidgetType.RECOVERY_HERO,
                title = "Recovery, Sleep & Activity",
                isVisible = true,
                order = 0
            ),
            DashboardWidget(
                id = "streaks",
                type = WidgetType.STREAKS,
                title = "Streaks",
                isVisible = true,
                order = 1
            ),
            DashboardWidget(
                // Only drawn once the user turns fasting on.
                id = "fasting",
                type = WidgetType.FASTING,
                title = "Fasting",
                isVisible = true,
                order = 1
            ),
            DashboardWidget(
                id = "body_age",
                type = WidgetType.BODY_AGE,
                title = "Body Age",
                isVisible = true,
                order = 2
            ),
            DashboardWidget(
                id = "stress_monitor",
                type = WidgetType.STRESS_MONITOR,
                title = "Autonomic Stress",
                isVisible = false, // disabled by default since Health Connect lacks native stress, enabled if user has wearable data
                order = 3
            ),
            DashboardWidget(
                id = "heart_metrics",
                type = WidgetType.HEART_METRICS,
                title = "Heart Rate & HRV",
                isVisible = true,
                order = 4
            ),
            DashboardWidget(
                id = "vitals_card",
                type = WidgetType.VITALS_CARD,
                title = "Key Vitals",
                isVisible = true,
                order = 5
            ),
            DashboardWidget(
                id = "body_fat_composition",
                type = WidgetType.BODY_FAT_COMPOSITION,
                title = "Body Fat & Composition",
                isVisible = true,
                order = 6
            ),
            DashboardWidget(
                id = "supplements",
                type = WidgetType.SUPPLEMENTS,
                title = "Supplements",
                isVisible = true,
                order = 7
            ),
            DashboardWidget(
                id = "health_records",
                type = WidgetType.HEALTH_RECORDS,
                title = "Health Records",
                isVisible = true,
                order = 8
            ),
            DashboardWidget(
                id = "breathing_exercises",
                type = WidgetType.BREATHING,
                title = "Breathing Exercises",
                isVisible = true,
                order = 9
            ),
            DashboardWidget(
                id = "calorie_burn",
                type = WidgetType.CALORIE_BURN,
                title = "Calorie Expenditure Breakdown",
                isVisible = false, // disabled by default since DailyActivityRings shows active calories, user can enable
                order = 10
            )
        )
    }
}
