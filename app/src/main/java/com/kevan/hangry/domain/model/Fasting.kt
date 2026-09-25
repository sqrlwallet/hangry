package com.kevan.hangry.domain.model

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The fasting schedules popular apps offer, as fasting hours : eating hours. */
enum class FastingPlan(val id: String, val label: String, val fastHours: Int, val blurb: String) {
    CIRCADIAN("13_11", "13:11", 13, "Gentle start - roughly dinner to breakfast"),
    FOURTEEN("14_10", "14:10", 14, "A step up, still easy most days"),
    SIXTEEN("16_8", "16:8", 16, "The classic - skip breakfast or a late snack"),
    EIGHTEEN("18_6", "18:6", 18, "A 6-hour eating window"),
    TWENTY("20_4", "20:4", 20, "Warrior style - one big meal and a snack"),
    OMAD("23_1", "OMAD", 23, "One meal a day"),
    CUSTOM("custom", "Custom", 16, "Pick your own length");

    companion object {
        val DEFAULT = SIXTEEN
        fun fromId(id: String?): FastingPlan = entries.firstOrNull { it.id == id } ?: DEFAULT
        const val MIN_CUSTOM_HOURS = 12
        const val MAX_CUSTOM_HOURS = 72
    }
}

/** Rough body stages by hours fasted - what fasting apps show. They vary a lot between people. */
enum class FastingStage(val fromHours: Int, val title: String, val detail: String) {
    FED(0, "Digesting", "Your body is using energy from your last meal."),
    SETTLING(4, "Blood sugar settling", "Insulin falls as digestion winds down."),
    FAT_BURNING(12, "Fat burning", "Stored glycogen runs low and fat use ramps up."),
    KETOSIS(16, "Ketosis starting", "Your liver makes more ketones for fuel."),
    DEEP(24, "Deep fast", "Ketones keep rising. Longer fasts are best planned with a doctor.");

    companion object {
        fun at(elapsed: Duration): FastingStage {
            val hours = elapsed.toMinutes() / 60.0
            return entries.last { hours >= it.fromHours }
        }
    }
}

data class Fast(
    val id: Long,
    val startAt: Instant,
    /** Null while the fast is running. */
    val endAt: Instant?,
    val targetMinutes: Int,
    val planId: String
) {
    val isActive: Boolean get() = endAt == null
    fun elapsed(now: Instant = Instant.now()): Duration = Duration.between(startAt, endAt ?: now).coerceAtLeast(Duration.ZERO)
    fun goalAt(): Instant = startAt.plus(Duration.ofMinutes(targetMinutes.toLong()))
    fun reachedGoal(now: Instant = Instant.now()): Boolean = elapsed(now).toMinutes() >= targetMinutes
    fun progress(now: Instant = Instant.now()): Float =
        if (targetMinutes <= 0) 0f else (elapsed(now).toMinutes().toFloat() / targetMinutes).coerceIn(0f, 1f)
}

data class FastingSnapshot(
    val enabled: Boolean = false,
    val plan: FastingPlan = FastingPlan.DEFAULT,
    /** Target for new fasts in hours (the plan's, or the custom length). */
    val targetHours: Int = FastingPlan.DEFAULT.fastHours,
    val goalReminder: Boolean = true,
    val active: Fast? = null,
    /** Finished fasts, newest first. */
    val history: List<Fast> = emptyList(),
    val streak: Int = 0,
    val bestStreak: Int = 0
) {
    val lastFinished: Fast? get() = history.firstOrNull()
}

object FastingMath {

    /** Fasts shorter than this are treated as a mis-tap and not saved. */
    const val MIN_SAVED_MINUTES = 5

    /**
     * Days that count for the streak: the day each fast reached its goal. A running fast counts
     * as soon as it gets there; ending early doesn't count.
     */
    fun goalDays(fasts: List<Fast>, zone: ZoneId, now: Instant = Instant.now()): Set<LocalDate> =
        fasts.filter { it.reachedGoal(now) }.map { it.goalAt().atZone(zone).toLocalDate() }.toSet()

    /** "16h 5m", or "45m" under an hour. */
    fun formatDuration(d: Duration): String {
        val total = d.toMinutes().coerceAtLeast(0)
        val h = total / 60
        val m = total % 60
        return if (h == 0L) "${m}m" else "${h}h ${m}m"
    }

    /** Fasts finished in the last 7 days (by end time). */
    fun lastWeek(history: List<Fast>, now: Instant = Instant.now()): List<Fast> {
        val from = now.minus(Duration.ofDays(7))
        return history.filter { it.endAt != null && it.endAt.isAfter(from) }
    }
}
