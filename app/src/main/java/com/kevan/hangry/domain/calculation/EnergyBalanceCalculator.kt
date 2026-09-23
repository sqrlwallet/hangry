package com.kevan.hangry.domain.calculation

import com.kevan.hangry.data.local.entity.DailyHealthSummaryEntity
import com.kevan.hangry.data.local.entity.ExerciseSessionEntity
import com.kevan.hangry.domain.model.BiologicalSex
import com.kevan.hangry.domain.model.EnergyBalanceEstimate
import com.kevan.hangry.domain.model.EnergyBalanceResult
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToLong

/**
 * Estimates maintenance calories from real behaviour over the 7 full days before today,
 * counting activity exactly as Daily Activity does (see [ActiveActivityCalculator]):
 *
 *   maintenance = (resting burn + step calories + workout calories) × 1.10
 *
 * - Resting burn is BMR for the minutes you weren't active; active minutes already include
 *   their own resting burn inside the step and workout calories, so it isn't counted twice.
 * - BMR uses Katch-McArdle (from lean mass) when there's a measured body-fat scan, since it's
 *   more accurate for lean or muscular people; otherwise Mifflin-St Jeor.
 * - Every step outside a workout counts in full, and every workout counts in full.
 * - The extra 10% is the thermic effect of food - energy spent digesting - which neither BMR
 *   formula includes.
 * - Averages are over days that actually have step data; days with no data are skipped rather
 *   than treated as zero-activity days.
 *
 * Nothing is guessed when inputs are missing: the result lists what's needed instead.
 */
class EnergyBalanceCalculator(private val calorieCalculator: CalorieCalculator) {

    fun estimate(
        weightKg: Double?,
        heightCm: Double?,
        age: Int?,
        sex: BiologicalSex?,
        summaries: List<DailyHealthSummaryEntity>,
        workouts: List<ExerciseSessionEntity>,
        goalWeightKg: Double?,
        goalDate: LocalDate?,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
        /** From a recent tape or photo scan only - not an estimate derived from BMI. */
        measuredBodyFatPercent: Double? = null
    ): EnergyBalanceResult {
        val windowStart = today.minusDays(WINDOW_DAYS.toLong())
        val windowEnd = today.minusDays(1)
        val stepsByDay = summaries
            .filter { it.date in windowStart..windowEnd }
            .mapNotNull { summary -> summary.steps?.let { summary.date to it } }
            .toMap()

        val missing = buildList {
            if (weightKg == null) add("Weight")
            if (heightCm == null) add("Height")
            if (age == null) add("Age")
            if (sex == null) add("Sex")
            if (stepsByDay.size < MIN_DAYS_WITH_DATA) add("Step data for at least $MIN_DAYS_WITH_DATA of the last 7 days")
        }
        if (missing.isNotEmpty() || weightKg == null || heightCm == null || age == null || sex == null) {
            return EnergyBalanceResult(estimate = null, missing = missing)
        }

        val leanKg = measuredBodyFatPercent?.takeIf { it in 3.0..65.0 }?.let { weightKg * (1 - it / 100.0) }
        val bmr = leanKg?.let { 370 + 21.6 * it } ?: calorieCalculator.calculateBmr(weightKg, heightCm, age, sex)
        val bmrMethod = if (leanKg != null) "Katch-McArdle" else "Mifflin-St Jeor"
        val workoutsByDay = workouts.groupBy { it.startTime.atZone(zone).toLocalDate() }

        var totalSteps = 0L
        var countedSteps = 0L
        var restingKcalSum = 0.0
        var stepKcalSum = 0.0
        var workoutKcalSum = 0.0
        var activeMinutesSum = 0
        var workoutsCounted = 0
        var workoutsWithoutCalories = 0
        val kcalPerStep = ActiveActivityCalculator.kcalPerStep(weightKg, heightCm, bmr)

        stepsByDay.forEach { (day, daySteps) ->
            val dayWorkouts = workoutsByDay[day].orEmpty()
            val active = ActiveActivityCalculator.calculate(daySteps, dayWorkouts, weightKg, heightCm, bmr)
            dayWorkouts.forEach { if (ActiveActivityCalculator.workoutCalories(it, bmr) == null) workoutsWithoutCalories++ else workoutsCounted++ }
            val minutes = active.activeMinutes ?: 0
            totalSteps += daySteps
            countedSteps += ((active.stepCalories ?: 0.0) / kcalPerStep).roundToLong()
            activeMinutesSum += minutes
            restingKcalSum += bmr * (MINUTES_PER_DAY - minutes).coerceIn(0.0, MINUTES_PER_DAY) / MINUTES_PER_DAY
            stepKcalSum += active.stepCalories ?: 0.0
            workoutKcalSum += active.workoutCalories
        }

        val days = stepsByDay.size.toDouble()
        val restingKcal = restingKcalSum / days
        val stepKcal = stepKcalSum / days
        val avgWorkoutKcal = workoutKcalSum / days
        val beforeFood = restingKcal + stepKcal + avgWorkoutKcal
        val tefKcal = beforeFood * TEF_FRACTION
        val maintenance = beforeFood + tefKcal

        val goal = if (goalWeightKg != null && goalDate != null) {
            calorieCalculator.recommendDailyCalorieGoal(
                tdee = maintenance,
                currentWeightKg = weightKg,
                weightGoalKg = goalWeightKg,
                targetDate = goalDate,
                today = today
            )
        } else null

        return EnergyBalanceResult(
            estimate = EnergyBalanceEstimate(
                windowStart = windowStart,
                windowEnd = windowEnd,
                daysWithData = stepsByDay.size,
                bmrKcal = bmr,
                bmrMethod = bmrMethod,
                restingKcal = restingKcal,
                avgActiveMinutes = activeMinutesSum / days,
                avgTotalSteps = totalSteps / days,
                avgCountedSteps = countedSteps / days,
                kcalPerStep = kcalPerStep,
                stepKcal = stepKcal,
                avgWorkoutKcal = avgWorkoutKcal,
                workoutsCounted = workoutsCounted,
                workoutsWithoutCalories = workoutsWithoutCalories,
                tefKcal = tefKcal,
                maintenanceKcal = maintenance,
                goalWeightKg = goalWeightKg,
                goalDate = goalDate,
                goal = goal
            ),
            missing = emptyList()
        )
    }

    companion object {
        const val WINDOW_DAYS = 7
        const val MIN_DAYS_WITH_DATA = 5
        /** Thermic effect of food - ~10% of daily energy goes to digesting what you eat. */
        const val TEF_FRACTION = 0.10
        private const val MINUTES_PER_DAY = 1440.0

        /**
         * Net energy cost of walking is ~0.5 kcal per kg per km, and stride length is ~41.5%
         * of height, so the cost of one step scales with weight and height: about 0.03 kcal
         * per step (1 kcal per ~33 steps) for an 80 kg, 180 cm adult.
         */
        fun kcalPerStep(weightKg: Double, heightCm: Double): Double {
            val strideKm = heightCm * 0.415 / 100_000.0
            return 0.5 * weightKg * strideKm
        }
    }
}
