package com.kevan.hangry.domain.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** The five kinds of exercise most linked with healthy ageing, with weekly targets. */
enum class LongevityPillar(
    val label: String,
    val goal: Int,
    val unit: String,
    /** True when the goal counts days (daily practice) rather than sessions or minutes. */
    val daily: Boolean,
    val why: String
) {
    STRENGTH("Strength", 3, "sessions", false, "Push, pull and squat 3 times a week. Protects muscle, bone and independence as you age."),
    ZONE2("Zone 2 cardio", 150, "min", false, "150 minutes a week at a pace you can still talk at. Builds your aerobic base and metabolic health."),
    HIGH_INTENSITY("Zone 4–5 cardio", 75, "min", false, "75 minutes a week of hard efforts to raise VO2 max, one of the strongest predictors of longevity. Try the Norwegian 4×4: four 4-minute hard intervals with 3 minutes easy between."),
    MOBILITY("Mobility", 7, "days", true, "5–10 minutes a day for hips, spine, ankles and shoulders, to move well and avoid injury."),
    BALANCE("Balance", 7, "days", true, "A few minutes a day of stability work, like standing on one leg (eyes closed when it gets easy). Helps prevent falls.");

    companion object {
        fun fromName(name: String?): LongevityPillar? = entries.firstOrNull { it.name == name }
    }
}

data class PillarProgress(
    val pillar: LongevityPillar,
    val value: Int,
    /** Days of the week this pillar was done (daily pillars, and strength sessions). */
    val days: Set<LocalDate> = emptySet(),
    /** Days ticked off by hand, which can be un-ticked. */
    val checkedDays: Set<LocalDate> = emptySet(),
    /** False when there was no data that could count toward it (e.g. no heart rate for zones). */
    val measurable: Boolean = true
) {
    val fraction: Float get() = (value.toFloat() / pillar.goal).coerceIn(0f, 1f)
    val met: Boolean get() = value >= pillar.goal
}

data class LongevityWeek(
    /** Monday of the week. */
    val start: LocalDate,
    val pillars: List<PillarProgress>
) {
    val end: LocalDate get() = start.plusDays(6)
    val days: List<LocalDate> get() = (0L..6L).map { start.plusDays(it) }
    val pillarsMet: Int get() = pillars.count { it.met }
    fun of(pillar: LongevityPillar): PillarProgress = pillars.first { it.pillar == pillar }

    companion object {
        fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }
}
