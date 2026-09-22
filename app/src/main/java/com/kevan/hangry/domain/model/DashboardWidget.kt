package com.kevan.hangry.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class WidgetType {
    RECOVERY_HERO,
    DAILY_ACTIVITY_RINGS,
    STRESS_MONITOR,
    SLEEP_STRAIN_RINGS,
    HEART_METRICS,
    SLEEP_SUMMARY,
    TRAINING_LOAD,
    CALORIE_BURN,
    AI_SHORTCUTS,
    CUSTOM_METRIC
}

@Serializable
enum class MetricType {
    STEPS,
    ACTIVE_MINUTES,
    ACTIVE_CALORIES,
    RHR,
    HRV,
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
        fun createDefaultWidgets(): List<DashboardWidget> = listOf(
            DashboardWidget(
                id = "recovery_hero",
                type = WidgetType.RECOVERY_HERO,
                title = "Hangry Recovery",
                isVisible = true,
                order = 0
            ),
            DashboardWidget(
                id = "daily_activity_rings",
                type = WidgetType.DAILY_ACTIVITY_RINGS,
                title = "Daily Activity",
                isVisible = true,
                order = 1
            ),
            DashboardWidget(
                id = "stress_monitor",
                type = WidgetType.STRESS_MONITOR,
                title = "Autonomic Stress",
                isVisible = false, // disabled by default since Health Connect lacks native stress, enabled if user has wearable data
                order = 2
            ),
            DashboardWidget(
                id = "sleep_strain_rings",
                type = WidgetType.SLEEP_STRAIN_RINGS,
                title = "Sleep & Strain Rings",
                isVisible = true,
                order = 3
            ),
            DashboardWidget(
                id = "sleep_summary",
                type = WidgetType.SLEEP_SUMMARY,
                title = "Sleep Duration & Need",
                isVisible = true,
                order = 4
            ),
            DashboardWidget(
                id = "training_load",
                type = WidgetType.TRAINING_LOAD,
                title = "Cardiovascular Strain & Load",
                isVisible = true,
                order = 5
            ),
            DashboardWidget(
                id = "heart_metrics",
                type = WidgetType.HEART_METRICS,
                title = "Heart Rate & HRV",
                isVisible = true,
                order = 6
            ),
            DashboardWidget(
                id = "ai_shortcuts",
                type = WidgetType.AI_SHORTCUTS,
                title = "Nutrition & Posture",
                isVisible = true,
                order = 7
            ),
            DashboardWidget(
                id = "calorie_burn",
                type = WidgetType.CALORIE_BURN,
                title = "Calorie Expenditure Breakdown",
                isVisible = false, // disabled by default since DailyActivityRings shows active calories, user can enable
                order = 8
            )
        )
    }
}
