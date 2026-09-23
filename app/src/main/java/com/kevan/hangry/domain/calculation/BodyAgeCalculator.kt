package com.kevan.hangry.domain.calculation

import com.kevan.hangry.domain.model.BiologicalSex
import kotlin.math.abs
import kotlin.math.roundToInt

/** A 30-day picture of the habits and markers Body Age is built from. Null = no data. */
data class BodyAgeInputs(
    val age: Int,
    val sex: BiologicalSex?,
    val daysWithData: Int,
    val vo2Max: Double?,
    val restingHeartRate: Double?,
    val hrvRmssd: Double?,
    val averageSteps: Double?,
    val weeklyExerciseMinutes: Double?,
    val weeklyStrengthSessions: Double?,
    val averageSleepMinutes: Double?,
    val sleepConsistency: Double?,
    /** From a body-fat scan if there is one... */
    val bodyFatPercent: Double?,
    /** ...otherwise BMI. */
    val bmi: Double?
)

/** One factor's effect: negative years make Body Age younger. */
data class BodyAgeFactor(
    val name: String,
    val value: String,
    val years: Double,
    val tip: String
)

data class BodyAgeResult(
    val chronologicalAge: Int,
    val bodyAge: Double,
    val factors: List<BodyAgeFactor>,
    /** Factors that had no data, so they didn't count. */
    val missing: List<String>
) {
    /** Negative = younger than your real age. */
    val difference: Double get() = bodyAge - chronologicalAge
}

/**
 * Body Age: an estimate of how old your body "acts", from nine habits and fitness markers
 * compared with typical values for your age and sex. Each factor adds or removes a capped number
 * of years; missing data is left out, never assumed. For motivation, not a medical measurement -
 * see CALCULATIONS.md §10.
 */
object BodyAgeCalculator {

    const val MIN_FACTORS = 4
    const val MIN_DAYS = 7
    private const val MAX_TOTAL_YEARS = 12.0

    /** Null when there isn't enough to say anything honest (see [MIN_FACTORS], [MIN_DAYS]). */
    fun calculate(i: BodyAgeInputs): BodyAgeResult? {
        if (i.daysWithData < MIN_DAYS) return null
        val male = i.sex == BiologicalSex.MALE
        val factors = mutableListOf<BodyAgeFactor>()
        val missing = mutableListOf<String>()

        fun add(name: String, value: Any?, build: () -> BodyAgeFactor) {
            if (value == null) missing += name else factors += build()
        }

        // Cardio fitness: VO2 max against the typical value for your age and sex. Each 3.5
        // ml/kg/min (one MET) above or below the norm is worth 2 years (fitness-age research).
        add("Cardio fitness", i.vo2Max) {
            val norm = vo2Norm(i.age, i.sex)
            val years = cap(-(i.vo2Max!! - norm) / 3.5 * 2.0, 5.0)
            BodyAgeFactor("Cardio fitness", "VO₂ max ${fmt(i.vo2Max, 1)} (typical ${fmt(norm, 0)})", years,
                "Hard efforts that leave you breathless - intervals, hills, fast cycling - raise VO₂ max fastest.")
        }
        // Resting heart rate: 60 bpm is neutral; every 5 bpm lower or higher is a year.
        add("Resting heart rate", i.restingHeartRate) {
            val years = cap((i.restingHeartRate!! - 60.0) / 5.0, 3.0)
            BodyAgeFactor("Resting heart rate", "${i.restingHeartRate.roundToInt()} bpm", years,
                "Regular cardio, good sleep and less alcohol all bring resting heart rate down.")
        }
        // HRV against a typical value that falls with age; small weight, since devices differ.
        add("Heart rate variability", i.hrvRmssd) {
            val norm = (62.0 - 0.6 * (i.age - 20)).coerceAtLeast(20.0)
            val years = cap(-(i.hrvRmssd!! - norm) / norm * 3.0, 1.5)
            BodyAgeFactor("Heart rate variability", "${i.hrvRmssd.roundToInt()} ms (typical ${norm.roundToInt()})", years,
                "Consistent sleep, recovery days and managing stress lift HRV over time.")
        }
        add("Daily steps", i.averageSteps) {
            val s = i.averageSteps!!
            val years = when {
                s >= 12_000 -> -2.0
                s >= 10_000 -> -1.5
                s >= 7_500 -> -1.0
                s >= 5_000 -> 0.0
                s >= 3_000 -> 1.0
                else -> 2.0
            }
            BodyAgeFactor("Daily steps", "${fmt(s, 0)} a day", years, "Aim for 8,000-10,000 steps; a walk after meals adds up fast.")
        }
        // Moderate-to-vigorous exercise against the 150 min/week guideline.
        add("Weekly exercise", i.weeklyExerciseMinutes) {
            val m = i.weeklyExerciseMinutes!!
            val years = when {
                m >= 300 -> -2.0
                m >= 150 -> -1.0
                m >= 75 -> 0.0
                m >= 30 -> 1.0
                else -> 2.0
            }
            BodyAgeFactor("Weekly exercise", "${m.roundToInt()} min a week", years, "Health guidelines suggest at least 150 minutes a week; 300 is even better.")
        }
        add("Strength training", i.weeklyStrengthSessions) {
            val n = i.weeklyStrengthSessions!!
            val years = when {
                n >= 2.0 -> -1.5
                n >= 1.0 -> -0.5
                else -> 1.0
            }
            BodyAgeFactor("Strength training", "${fmt(n, 1)} sessions a week", years, "Two strength sessions a week protect muscle and bone as you age.")
        }
        add("Sleep", i.averageSleepMinutes) {
            val h = i.averageSleepMinutes!! / 60.0
            val years = when {
                h in 7.0..9.0 -> -1.0
                h in 6.0..7.0 -> 0.5
                h > 9.0 -> 0.5
                else -> 2.0
            }
            BodyAgeFactor("Sleep", "${fmt(h, 1)} h a night", years, "7-9 hours a night is the range linked with the best health.")
        }
        add("Sleep consistency", i.sleepConsistency) {
            val c = i.sleepConsistency!!
            val years = when {
                c >= 85 -> -1.0
                c >= 70 -> 0.0
                else -> 1.0
            }
            BodyAgeFactor("Sleep consistency", "${c.roundToInt()}%", years, "Going to bed and waking at similar times - weekends too - matters as much as hours.")
        }
        val bodyValue = i.bodyFatPercent ?: i.bmi
        add("Body composition", bodyValue) {
            if (i.bodyFatPercent != null) {
                val bf = i.bodyFatPercent
                val (healthyTop, high) = if (male) 20.0 to 25.0 else if (i.sex == BiologicalSex.FEMALE) 30.0 to 35.0 else 25.0 to 30.0
                val years = when {
                    bf <= healthyTop -> -1.0
                    bf <= high -> 1.0
                    else -> 2.5
                }
                BodyAgeFactor("Body composition", "${fmt(bf, 1)}% body fat", years, "Strength training and enough protein shift body composition toward more muscle.")
            } else {
                val b = i.bmi!!
                val years = when {
                    b < 18.5 -> 1.0
                    b < 25.0 -> -0.5
                    b < 30.0 -> 1.0
                    else -> 2.5
                }
                BodyAgeFactor("Body composition", "BMI ${fmt(b, 1)}", years, "A body-fat scan in the app gives a more accurate picture than BMI.")
            }
        }

        if (factors.size < MIN_FACTORS) return null
        val total = cap(factors.sumOf { it.years }, MAX_TOTAL_YEARS)
        return BodyAgeResult(
            chronologicalAge = i.age,
            bodyAge = ((i.age + total) * 10).roundToInt() / 10.0,
            factors = factors.sortedByDescending { abs(it.years) },
            missing = missing
        )
    }

    /**
     * Typical VO2 max by age and sex (roughly the 50th percentile in population fitness norms):
     * falls about 0.4 ml/kg/min a year from age 20.
     */
    fun vo2Norm(age: Int, sex: BiologicalSex?): Double {
        val at20 = when (sex) {
            BiologicalSex.MALE -> 47.0
            BiologicalSex.FEMALE -> 40.0
            else -> 43.5
        }
        return (at20 - 0.4 * (age - 20).coerceAtLeast(0)).coerceAtLeast(20.0)
    }

    private fun cap(value: Double, limit: Double) = value.coerceIn(-limit, limit)
    private fun fmt(value: Double, decimals: Int) =
        if (decimals == 0) String.format(java.util.Locale.US, "%,.0f", value) else String.format(java.util.Locale.US, "%.${decimals}f", value)
}
