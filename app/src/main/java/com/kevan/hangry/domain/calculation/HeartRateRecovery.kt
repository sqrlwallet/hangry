package com.kevan.hangry.domain.calculation

import java.time.Duration
import java.time.Instant
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Heart-rate recovery, like Apple's Cardio Recovery: how far your heart rate falls in the first
 * one and two minutes after a workout ends. A fitter heart drops faster, so it tends to climb as
 * fitness improves and dips with fatigue or illness.
 *
 * Ranges follow the exercise-test research: a 1-minute drop of 12 bpm or less (Cole et al., NEJM
 * 1999) and a 2-minute drop of 22 bpm or less (Shetler et al., JACC 2001) are low; trained people
 * commonly drop 25+ bpm in the first minute. Those studies used a hard treadmill test, so a
 * reading only counts when the workout ended at a real effort (see [MIN_END_RESERVE_FRACTION]),
 * and a cool-down that lowers your heart rate before you stop makes the drop look smaller.
 */
object HeartRateRecovery {

    enum class Rating { LOW, TYPICAL, STRONG }

    data class Result(
        /** Heart rate as the workout ended (the highest in its last 30 seconds). */
        val endBpm: Int,
        /** Drop after 1 minute; null without a reading near then. */
        val oneMinuteDrop: Int?,
        /** Drop after 2 minutes; null without a reading near then. */
        val twoMinuteDrop: Int?
    ) {
        /** From the 1-minute drop when there is one, else the 2-minute drop. */
        val rating: Rating
            get() = oneMinuteDrop?.let { rateOneMinute(it) } ?: rateTwoMinutes(twoMinuteDrop ?: 0)
    }

    /** At or below: low (Cole 1999). */
    const val ONE_MINUTE_LOW = 12
    /** At or above: strong. */
    const val ONE_MINUTE_STRONG = 25
    /** At or below: low (Shetler 2001). */
    const val TWO_MINUTE_LOW = 22
    /** At or above: strong - about twice the 1-minute mark, as the drop slows after the first minute. */
    const val TWO_MINUTE_STRONG = 45

    /**
     * The workout must end at 70% of heart-rate reserve or more (zone 3): after an easy effort
     * there's little to recover from, and the drop says nothing about fitness.
     */
    const val MIN_END_RESERVE_FRACTION = 0.7

    private val END_WINDOW: Duration = Duration.ofSeconds(30)
    /** How far from the 1- and 2-minute marks a reading may be. Watches sample every few seconds to a minute. */
    private val MARK_TOLERANCE: Duration = Duration.ofSeconds(15)
    private val ONE_MINUTE: Duration = Duration.ofSeconds(60)
    private val TWO_MINUTES: Duration = Duration.ofSeconds(120)

    /** How much heart rate to load around a workout's end. */
    val WINDOW_BEFORE_END: Duration = END_WINDOW
    val WINDOW_AFTER_END: Duration = TWO_MINUTES + MARK_TOLERANCE

    fun rateOneMinute(drop: Int): Rating = when {
        drop <= ONE_MINUTE_LOW -> Rating.LOW
        drop >= ONE_MINUTE_STRONG -> Rating.STRONG
        else -> Rating.TYPICAL
    }

    fun rateTwoMinutes(drop: Int): Rating = when {
        drop <= TWO_MINUTE_LOW -> Rating.LOW
        drop >= TWO_MINUTE_STRONG -> Rating.STRONG
        else -> Rating.TYPICAL
    }

    /**
     * [samples] are (time, bpm) around [workoutEnd], from [WINDOW_BEFORE_END] before it to
     * [WINDOW_AFTER_END] after. Null when the workout didn't end hard enough or there's no
     * reading at the end or near either mark.
     */
    fun calculate(workoutEnd: Instant, samples: List<Pair<Instant, Double>>, zones: HeartRateZones): Result? {
        val endBpm = samples
            .filter { (t, _) -> !t.isBefore(workoutEnd - END_WINDOW) && !t.isAfter(workoutEnd) }
            .maxOfOrNull { it.second } ?: return null
        val hardEnough = zones.restingHr + MIN_END_RESERVE_FRACTION * (zones.maxHr - zones.restingHr)
        if (endBpm < hardEnough) return null

        fun dropAt(mark: Duration): Int? {
            val target = workoutEnd + mark
            val reading = samples
                .filter { (t, _) -> abs(Duration.between(target, t).seconds) <= MARK_TOLERANCE.seconds }
                .minByOrNull { (t, _) -> abs(Duration.between(target, t).seconds) }
                ?: return null
            return (endBpm - reading.second).roundToInt().coerceAtLeast(0)
        }
        val one = dropAt(ONE_MINUTE)
        val two = dropAt(TWO_MINUTES)
        if (one == null && two == null) return null
        return Result(endBpm.roundToInt(), one, two)
    }
}
