package com.kevan.hangry.domain.calculation

import com.kevan.hangry.domain.model.CycleStats
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Cycle phase from period history, confirmed by skin temperature where the wearable records it.
 * After ovulation, progesterone lifts resting temperature by roughly 0.2-0.5 °C until the next
 * period - the "thermal shift" fertility-awareness methods look for. Spotting it tells us
 * ovulation has happened (only after the fact - this never predicts fertile days reliably).
 */
object CycleTemperature {

    enum class Phase { MENSTRUAL, FOLLICULAR, FERTILE_WINDOW, LUTEAL }

    data class Insight(
        val phase: Phase,
        val cycleDay: Int,
        /** First warm night of the shift, when temperature confirmed ovulation this cycle. */
        val shiftDetectedOn: LocalDate?,
        /** How much warmer the shift nights ran than the six before, °C. */
        val shiftSizeC: Double?,
        /** Next period from the temperature shift and your own luteal length; null unless confirmed. */
        val predictedNextStart: LocalDate? = null,
        /** Your luteal length learned from earlier cycles' shifts, when there were any. */
        val personalLutealDays: Int? = null
    ) {
        val confirmedByTemperature: Boolean get() = shiftDetectedOn != null
    }

    private const val DEFAULT_CYCLE_DAYS = 28
    /** Textbook luteal length, used until your own is known. */
    private const val LUTEAL_DAYS = 14
    /** The classic "three over six" rule: three nights warmer than all of the previous six... */
    private const val LOW_NIGHTS = 6
    private const val HIGH_NIGHTS = 3
    /** ...with the shift at least this big on average. */
    const val MIN_SHIFT_C = 0.2
    /** Ovulation is rarely this early - ignore warm spells right after a period. */
    private const val EARLIEST_SHIFT_CYCLE_DAY = 8

    /**
     * Today's phase, or null without a period on record or once the cycle has run so long
     * (over 60 days) that the last logged period clearly isn't the current one.
     */
    fun insight(stats: CycleStats, skinTempByDate: Map<LocalDate, Double>, today: LocalDate, periodStarts: List<LocalDate> = emptyList()): Insight? {
        val start = stats.lastPeriodStart ?: return null
        val cycleDay = ChronoUnit.DAYS.between(start, today).toInt() + 1
        if (cycleDay !in 1..60) return null
        val shift = findShift(start, today, skinTempByDate)
        val learned = personalLutealDays(periodStarts, skinTempByDate)
        val lutealDays = learned ?: LUTEAL_DAYS
        val cycleLength = stats.averageCycleDays?.let { Math.round(it).toInt() } ?: DEFAULT_CYCLE_DAYS
        val ovulationDay = cycleLength - lutealDays
        val tempTracked = skinTempByDate.keys.any { !it.isBefore(start) && !it.isAfter(today) }
        val phase = when {
            stats.inPeriodNow -> Phase.MENSTRUAL
            shift != null -> Phase.LUTEAL
            cycleDay in (ovulationDay - 5)..(ovulationDay + 1) -> Phase.FERTILE_WINDOW
            cycleDay < ovulationDay - 5 -> Phase.FOLLICULAR
            // Temperature is tracked but hasn't risen: ovulation is late, not past (as isLuteal).
            tempTracked -> Phase.FOLLICULAR
            else -> Phase.LUTEAL
        }
        // The shift's first warm night is about the day after ovulation.
        val predicted = shift?.first?.minusDays(1)?.plusDays(lutealDays.toLong())
        return Insight(phase, cycleDay, shift?.first, shift?.second, predicted, learned)
    }

    /**
     * Your own luteal length - ovulation to the next period - averaged over finished cycles
     * where temperature showed the shift. It varies far less from cycle to cycle than the days
     * before ovulation, so it predicts the next period well once the shift is seen.
     * Null without a measured cycle; implausible lengths (outside 8-18 days) are ignored.
     */
    fun personalLutealDays(periodStarts: List<LocalDate>, skinTempByDate: Map<LocalDate, Double>): Int? {
        val lengths = periodStarts.sorted().zipWithNext().mapNotNull { (start, next) ->
            val shift = findShift(start, next.minusDays(1), skinTempByDate)?.first ?: return@mapNotNull null
            ChronoUnit.DAYS.between(shift.minusDays(1), next).toInt().takeIf { it in 8..18 }
        }
        return lengths.takeIf { it.isNotEmpty() }?.let { Math.round(it.average()).toInt() }
    }

    /** True when [date] falls after a confirmed (or, without temperature data, expected) ovulation. */
    fun isLuteal(periodStarts: List<LocalDate>, averageCycleDays: Double?, skinTempByDate: Map<LocalDate, Double>, date: LocalDate): Boolean {
        val start = periodStarts.filter { !it.isAfter(date) }.maxOrNull() ?: return false
        val cycleDay = ChronoUnit.DAYS.between(start, date).toInt() + 1
        if (cycleDay !in 1..60) return false
        val shift = findShift(start, date, skinTempByDate)
        if (shift != null) return true
        // Temperature tracked this cycle but no shift yet: not luteal.
        if (skinTempByDate.keys.any { !it.isBefore(start) && !it.isAfter(date) }) return false
        val cycleLength = averageCycleDays?.let { Math.round(it).toInt() } ?: DEFAULT_CYCLE_DAYS
        val lutealDays = personalLutealDays(periodStarts.filter { !it.isAfter(date) }, skinTempByDate) ?: LUTEAL_DAYS
        return cycleDay > cycleLength - lutealDays + 1
    }

    /** First night of a sustained rise in [cycleStart]..[upTo], with its size. */
    private fun findShift(cycleStart: LocalDate, upTo: LocalDate, temps: Map<LocalDate, Double>): Pair<LocalDate, Double>? {
        val nights = temps.filterKeys { !it.isBefore(cycleStart) && !it.isAfter(upTo) }.toSortedMap().entries.toList()
        for (i in LOW_NIGHTS..nights.size - HIGH_NIGHTS) {
            val candidate = nights[i].key
            if (ChronoUnit.DAYS.between(cycleStart, candidate) + 1 < EARLIEST_SHIFT_CYCLE_DAY) continue
            val low = nights.subList(i - LOW_NIGHTS, i).map { it.value }
            val high = nights.subList(i, i + HIGH_NIGHTS).map { it.value }
            // Nights must be close together - a week-long gap isn't a sustained rise.
            if (ChronoUnit.DAYS.between(nights[i - LOW_NIGHTS].key, nights[i + HIGH_NIGHTS - 1].key) > LOW_NIGHTS + HIGH_NIGHTS + 2) continue
            val size = high.average() - low.average()
            if (high.all { it > low.max() } && size >= MIN_SHIFT_C) return candidate to size
        }
        return null
    }
}
