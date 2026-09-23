package com.kevan.hangry.domain.calculation

import com.kevan.hangry.data.local.entity.DailyHealthSummaryEntity
import com.kevan.hangry.data.local.entity.ExerciseSessionEntity
import com.kevan.hangry.domain.model.BiologicalSex
import com.kevan.hangry.domain.model.EnergyBalanceEstimate
import com.kevan.hangry.domain.model.EnergyBalanceResult
import java.time.LocalDate
import java.time.ZoneId

/**
 * Estimates maintenance calories from real behaviour over the 7 full days before today:
 *
 *   maintenance = BMR + NEAT + average workout calories
 *
 * - NEAT (non-exercise activity) is the calorie cost of one third of your average daily steps.
 *   Counting only a third is a deliberate rough allowance for steps already covered by workout
 *   calories (and for how much of a step count is low-effort shuffling) - a placeholder rule
 *   until workout steps are handled more precisely.
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
        zone: ZoneId = ZoneId.systemDefault()
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

        val bmr = calorieCalculator.calculateBmr(weightKg, heightCm, age, sex)
        val workoutsByDay = workouts.groupBy { it.startTime.atZone(zone).toLocalDate() }

        var totalSteps = 0L
        var totalWorkoutKcal = 0.0
        var workoutsCounted = 0
        var workoutsWithoutCalories = 0

        stepsByDay.forEach { (day, daySteps) ->
            totalSteps += daySteps
            workoutsByDay[day].orEmpty().forEach { workout ->
                val kcal = workoutCalories(workout, bmr)
                if (kcal == null) {
                    workoutsWithoutCalories++
                } else {
                    workoutsCounted++
                    totalWorkoutKcal += kcal
                }
            }
        }

        val days = stepsByDay.size.toDouble()
        val avgTotalSteps = totalSteps / days
        val avgNeatSteps = avgTotalSteps / NEAT_STEP_DIVISOR
        val kcalPerStep = kcalPerStep(weightKg, heightCm)
        val neatKcal = avgNeatSteps * kcalPerStep
        val avgWorkoutKcal = totalWorkoutKcal / days
        val maintenance = bmr + neatKcal + avgWorkoutKcal

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
                avgTotalSteps = avgTotalSteps,
                avgNeatSteps = avgNeatSteps,
                kcalPerStep = kcalPerStep,
                neatKcal = neatKcal,
                avgWorkoutKcal = avgWorkoutKcal,
                workoutsCounted = workoutsCounted,
                workoutsWithoutCalories = workoutsWithoutCalories,
                maintenanceKcal = maintenance,
                goalWeightKg = goalWeightKg,
                goalDate = goalDate,
                goal = goal
            ),
            missing = emptyList()
        )
    }

    /** Active calories if recorded, else total calories minus the resting share of that time. */
    private fun workoutCalories(workout: ExerciseSessionEntity, bmr: Double): Double? {
        workout.activeCalories?.takeIf { it > 0 }?.let { return it }
        val total = workout.totalCalories?.takeIf { it > 0 } ?: return null
        val restingShare = bmr * workout.durationMinutes / MINUTES_PER_DAY
        return (total - restingShare).coerceAtLeast(0.0)
    }

    companion object {
        const val WINDOW_DAYS = 7
        const val MIN_DAYS_WITH_DATA = 3
        /** Only a third of average daily steps counts toward NEAT - see the class docs. */
        const val NEAT_STEP_DIVISOR = 3.0
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
