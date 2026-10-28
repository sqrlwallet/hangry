package com.kevan.hangry.domain.calculation

import com.kevan.hangry.domain.model.BiologicalSex
import java.time.LocalDate

/**
 * Cardio fitness from VO2 max, like Apple's cardio fitness levels: where your VO2 max sits for
 * your age and sex, and alerts when it's low or falling - one of the strongest predictors of
 * long-term health (Kodama et al., JAMA 2009: each 1 MET, 3.5 ml/kg/min, is about 13% lower
 * all-cause mortality).
 *
 * The typical value is [BodyAgeCalculator.vo2Norm] (roughly the 50th percentile). In the FRIEND
 * registry (Kaminsky et al., Mayo Clin Proc 2015) the 25th and 75th percentiles sit about 17%
 * either side of the median at every age, for men and women, so the levels are quartiles:
 * below 83% of typical is Low, above 117% is High.
 */
object CardioFitness {

    enum class Level { LOW, BELOW_AVERAGE, ABOVE_AVERAGE, HIGH }

    const val LOW_FRACTION = 0.83
    const val HIGH_FRACTION = 1.17

    /** One MET: a fall this big is worth knowing about. */
    const val MEANINGFUL_DROP = 3.5

    /** How far back the trend compares against. */
    const val TREND_DAYS = 90L

    /** Low, or the fall, must hold this long before an alert, so one off estimate doesn't trigger it. */
    const val SUSTAINED_DAYS = 14L

    /** At most one alert of each kind this often. */
    const val ALERT_COOLDOWN_DAYS = 90L

    /** Edges of the levels in ml/kg/min for an age and sex. */
    data class Ranges(val lowBelow: Double, val typical: Double, val highFrom: Double)

    fun ranges(age: Double, sex: BiologicalSex?): Ranges {
        val typical = BodyAgeCalculator.vo2Norm(age, sex)
        return Ranges(typical * LOW_FRACTION, typical, typical * HIGH_FRACTION)
    }

    fun level(vo2Max: Double, age: Double, sex: BiologicalSex?): Level {
        val r = ranges(age, sex)
        return when {
            vo2Max < r.lowBelow -> Level.LOW
            vo2Max < r.typical -> Level.BELOW_AVERAGE
            vo2Max < r.highFrom -> Level.ABOVE_AVERAGE
            else -> Level.HIGH
        }
    }

    /**
     * VO2 max as of [day]: that day's value or the closest one in the week before. Daily summaries
     * carry the last reading forward, so this is "the latest estimate then".
     */
    fun valueOn(readings: Map<LocalDate, Double>, day: LocalDate): Double? =
        (0L..6L).firstNotNullOfOrNull { readings[day.minusDays(it)] }

    /** Now against [TREND_DAYS] ago, in ml/kg/min; null without both. */
    fun change(readings: Map<LocalDate, Double>, today: LocalDate): Double? {
        val now = valueOn(readings, today) ?: return null
        val before = valueOn(readings, today.minusDays(TREND_DAYS)) ?: return null
        return now - before
    }

    enum class Alert { LOW, DECLINING }

    /**
     * Which alert is due today, if any. [lastAlerts] is when each kind was last sent. [age] null
     * (no birthday yet) means only a decline can be judged.
     */
    fun alertDue(
        readings: Map<LocalDate, Double>,
        today: LocalDate,
        age: Double?,
        sex: BiologicalSex?,
        lastAlerts: Map<Alert, LocalDate> = emptyMap()
    ): Alert? {
        fun coolingDown(alert: Alert) = lastAlerts[alert]?.let { today.isBefore(it.plusDays(ALERT_COOLDOWN_DAYS)) } == true
        val now = valueOn(readings, today) ?: return null
        if (age != null && !coolingDown(Alert.LOW)) {
            val earlier = valueOn(readings, today.minusDays(SUSTAINED_DAYS))
            if (level(now, age, sex) == Level.LOW && earlier != null && level(earlier, age - SUSTAINED_DAYS / 365.25, sex) == Level.LOW) {
                return Alert.LOW
            }
        }
        fun fallen(day: LocalDate) = change(readings, day)?.let { it <= -MEANINGFUL_DROP } == true
        if (fallen(today) && fallen(today.minusDays(SUSTAINED_DAYS)) && !coolingDown(Alert.DECLINING)) return Alert.DECLINING
        return null
    }
}
