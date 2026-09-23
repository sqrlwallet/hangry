package com.kevan.hangry

import com.kevan.hangry.domain.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class DashboardWidgetSerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testDefaultWidgets_containExpectedDefaults() {
        val defaults = DashboardWidget.createDefaultWidgets()
        assertTrue(defaults.any { it.type == WidgetType.RECOVERY_HERO })
        assertTrue(defaults.any { it.type == WidgetType.STRESS_MONITOR })
        assertTrue(defaults.any { it.type == WidgetType.VITALS_CARD })
        // Recovery, sleep, strain and activity share the overview card; Ask Dash, Nutrition and
        // Log Meal live in the tab bar and button - none of those cards come back by default.
        assertTrue(defaults.none { it.type in DashboardWidget.RETIRED_TYPES })
        assertEquals(WidgetType.RECOVERY_HERO, defaults.minBy { it.order }.type)
    }

    @Test
    fun testSerializationAndDeserialization_customWidget() {
        val original = DashboardWidget(
            id = "custom_steps_1",
            type = WidgetType.CUSTOM_METRIC,
            title = "My Daily Steps",
            isVisible = true,
            order = 3,
            metricType = MetricType.STEPS,
            targetGoal = 12000.0,
            unit = "steps",
            displayStyle = WidgetDisplayStyle.RING
        )

        val encoded = json.encodeToString(listOf(original))
        val decoded = json.decodeFromString<List<DashboardWidget>>(encoded)

        assertEquals(1, decoded.size)
        val item = decoded[0]
        assertEquals("custom_steps_1", item.id)
        assertEquals(WidgetType.CUSTOM_METRIC, item.type)
        assertEquals("My Daily Steps", item.title)
        assertTrue(item.isVisible)
        assertEquals(3, item.order)
        assertEquals(MetricType.STEPS, item.metricType)
        assertEquals(12000.0, item.targetGoal!!, 0.01)
        assertEquals("steps", item.unit)
        assertEquals(WidgetDisplayStyle.RING, item.displayStyle)
    }

    @Test
    fun testMergeMissingDefaults_preservesCustomListAndAppendsNewWidgets() {
        val existingSavedList = listOf(
            DashboardWidget(
                id = "recovery_hero",
                type = WidgetType.RECOVERY_HERO,
                title = "Hangry Recovery",
                isVisible = true,
                order = 0
            ),
            DashboardWidget(
                id = "custom_steps_1",
                type = WidgetType.CUSTOM_METRIC,
                title = "Custom Steps",
                isVisible = true,
                order = 1
            )
        )

        val defaultWidgets = DashboardWidget.createDefaultWidgets()
        val existingIds = existingSavedList.map { it.id }.toSet()
        val missingDefaults = defaultWidgets.filterNot { it.id in existingIds }

        val merged = existingSavedList + missingDefaults.mapIndexed { idx, w ->
            w.copy(order = existingSavedList.size + idx)
        }

        assertTrue(merged.any { it.id == "heart_metrics" })
        assertTrue(merged.any { it.id == "stress_monitor" })
        assertTrue(merged.any { it.id == "custom_steps_1" })
        assertEquals(0, merged.first { it.id == "recovery_hero" }.order)
        assertEquals(1, merged.first { it.id == "custom_steps_1" }.order)
    }

    @Test
    fun testSerializationAndDeserialization_vitalsMetrics() {
        val vitalsWidgets = listOf(
            DashboardWidget(
                id = "custom_vo2_1",
                type = WidgetType.CUSTOM_METRIC,
                title = "Cardio VO2 Max",
                isVisible = true,
                order = 0,
                metricType = MetricType.VO2_MAX,
                targetGoal = 50.0,
                unit = "mL/kg/min",
                displayStyle = WidgetDisplayStyle.STAT_CARD
            ),
            DashboardWidget(
                id = "custom_spo2_1",
                type = WidgetType.CUSTOM_METRIC,
                title = "Blood Oxygen",
                isVisible = true,
                order = 1,
                metricType = MetricType.SPO2,
                targetGoal = 98.0,
                unit = "%",
                displayStyle = WidgetDisplayStyle.RING
            ),
            DashboardWidget(
                id = "custom_rhr_1",
                type = WidgetType.CUSTOM_METRIC,
                title = "Resting Heart Rate",
                isVisible = true,
                order = 2,
                metricType = MetricType.RHR,
                targetGoal = 55.0,
                unit = "bpm",
                displayStyle = WidgetDisplayStyle.STAT_CARD
            )
        )

        val encoded = json.encodeToString(vitalsWidgets)
        val decoded = json.decodeFromString<List<DashboardWidget>>(encoded)

        assertEquals(3, decoded.size)
        assertEquals(MetricType.VO2_MAX, decoded[0].metricType)
        assertEquals(MetricType.SPO2, decoded[1].metricType)
        assertEquals(MetricType.RHR, decoded[2].metricType)
    }
}
