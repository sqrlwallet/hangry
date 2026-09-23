package com.kevan.hangry.domain.ai

import com.kevan.hangry.data.local.entity.DailyHealthSummaryEntity
import com.kevan.hangry.data.local.entity.WeightMeasurementEntity
import com.kevan.hangry.domain.calculation.HealthMarkerCalculator
import com.kevan.hangry.domain.model.BodyMetricsSnapshot
import com.kevan.hangry.domain.model.GoalDirection
import com.kevan.hangry.domain.model.HealthRecordsSnapshot
import com.kevan.hangry.domain.model.MarkerType
import com.kevan.hangry.domain.model.MetricTone
import com.kevan.hangry.domain.model.SupplementsSnapshot
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs

/** Pure helpers that turn stored data into what Dash reads (trends) and what it suggests (chips). */
object DashInsights {

    /**
     * Last 7 days vs the 3 weeks before, per metric - so Dash can say "your resting HR is up
     * 4 bpm this week" instead of guessing from one week of raw numbers. Metrics without
     * enough data on both sides are left out.
     */
    fun trendLines(
        summaries: List<DailyHealthSummaryEntity>,
        recoveryByDate: Map<LocalDate, Int?>,
        weights: List<WeightMeasurementEntity>,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<String> {
        val recentStart = today.minusDays(7)
        val baselineStart = today.minusDays(28)
        val recent = summaries.filter { it.date >= recentStart && it.date < today }
        val baseline = summaries.filter { it.date >= baselineStart && it.date < recentStart }

        fun line(label: String, unit: String, decimals: Int, higherIsBetter: Boolean?, pick: (DailyHealthSummaryEntity) -> Double?): String? {
            val r = recent.mapNotNull(pick)
            val b = baseline.mapNotNull(pick)
            if (r.size < 3 || b.size < 5) return null
            val rAvg = r.average()
            val bAvg = b.average()
            val diff = rAvg - bAvg
            val pct = if (bAvg != 0.0) diff / bAvg * 100 else 0.0
            val direction = when {
                abs(pct) < 3 -> "steady"
                higherIsBetter == null -> if (diff > 0) "up" else "down"
                (diff > 0) == higherIsBetter -> if (diff > 0) "up (better)" else "down (better)"
                else -> if (diff > 0) "up (worse)" else "down (worse)"
            }
            val fmt = "%.${decimals}f"
            return "• $label: ${fmt.format(Locale.US, rAvg)}$unit this week vs ${fmt.format(Locale.US, bAvg)}$unit the 3 weeks before - $direction"
        }

        val lines = listOfNotNull(
            line("Sleep", " h", 1, true) { s -> s.sleepDurationMinutes?.takeIf { it > 0 }?.div(60.0) },
            line("Resting HR", " bpm", 0, false) { it.restingHeartRate },
            line("HRV", " ms", 0, true) { it.hrvRmssd },
            line("Steps", "", 0, true) { s -> s.steps?.takeIf { it > 0 }?.toDouble() },
            line("Active calories", " kcal", 0, null) { s -> s.activeCalories?.takeIf { it > 0 } },
            line("Day strain", "/21", 1, null) { it.dayStrain },
            line("Recovery", "%", 0, true) { s -> recoveryByDate[s.date]?.toDouble() }
        ).toMutableList()

        val monthWeights = weights
            .filter { !it.timestamp.atZone(zone).toLocalDate().isBefore(today.minusDays(30)) }
            .sortedBy { it.timestamp }
        if (monthWeights.size >= 2) {
            val change = monthWeights.last().weightKg - monthWeights.first().weightKg
            val days = java.time.Duration.between(monthWeights.first().timestamp, monthWeights.last().timestamp).toDays().coerceAtLeast(1)
            lines += "• Weight: %.1f kg → %.1f kg over %d days (%+.1f kg)".format(
                Locale.US, monthWeights.first().weightKg, monthWeights.last().weightKg, days, change
            )
        }
        return lines
    }

    /**
     * Suggestion chips built from what's actually going on, so the first tap is relevant:
     * a dose that's due, a goal in progress, an out-of-range marker, today's calories...
     * Falls back to general starters when there's nothing specific.
     */
    fun suggestions(
        supplements: SupplementsSnapshot?,
        records: HealthRecordsSnapshot?,
        bodyMetrics: BodyMetricsSnapshot?,
        now: LocalTime = LocalTime.now(),
        max: Int = 5
    ): List<String> {
        val out = mutableListOf<String>()

        supplements?.let { s ->
            val overdue = s.todayDoses.firstOrNull { !it.taken && it.time.isBefore(now) }
            when {
                overdue != null -> out += "💊 I haven't taken my ${overdue.supplement.name} yet - still OK?"
                s.supplements.isEmpty() -> out += "💊 Add a supplement from a photo"
                s.supplements.size >= 2 -> out += "💊 Do any of my supplements overlap?"
            }
        }

        records?.let { r ->
            r.goals.firstOrNull { g -> !HealthMarkerCalculator.progress(g, r.latest(g.type)).reached }?.let { g ->
                val target = HealthMarkerCalculator.format(g.type, g.targetValue, g.targetSecondary)
                val verb = if (g.direction == GoalDirection.LOWER) "lower" else "raise"
                out += "🎯 How do I $verb my ${g.type.label.lowercase()} to $target?"
            }
            r.visibleMarkers.firstOrNull { type ->
                r.goal(type) == null && r.latest(type) != null &&
                    HealthMarkerCalculator.describe(type, r.latest(type), r.sex).tone.let { it == MetricTone.CAUTION || it == MetricTone.RISK }
            }?.let { out += "🩺 What can help my ${it.label.lowercase()}?" }
            if (r.isPregnant) out += "🤰 Is my training right for pregnancy?"
        }

        bodyMetrics?.input?.energy?.estimate?.let { e ->
            out += if (e.goal?.dailyCalorieTarget != null) "🍽️ What should I eat for the rest of today?" else "🍽️ How many calories should I eat to maintain?"
        }

        val fallback = listOf(
            "⚡ Am I ready to train today?",
            "💤 How did sleep affect my recovery?",
            "📈 How am I trending this month?",
            "🍗 Ideas for a high-protein dinner",
            "🧘 Stretches for my posture scan?"
        )
        fallback.forEach { if (out.size < max && it !in out) out += it }
        return out.take(max)
    }
}
