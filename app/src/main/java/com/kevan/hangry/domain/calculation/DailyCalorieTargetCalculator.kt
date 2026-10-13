package com.kevan.hangry.domain.calculation

import com.kevan.hangry.data.local.entity.ExerciseSessionEntity
import com.kevan.hangry.domain.model.DailyCalorieTarget
import com.kevan.hangry.domain.model.EnergyBalanceEstimate
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * A day's calorie target from that day's own burn: BMR + NEAT + workouts + digestion, corrected
 * by what your logs have shown, ± your goal. See [DailyCalorieTarget] and CALCULATIONS.md §8.5.
 */
object DailyCalorieTargetCalculator {

    private const val MINUTES_PER_DAY = 1440.0

    /**
     * @param estimate supplies BMR, the 7-day typical NEAT, the learnt correction and the goal.
     * @param steps the day's steps so far (null before any sync).
     * @param workouts the day's workouts so far.
     */
    fun calculate(
        estimate: EnergyBalanceEstimate,
        date: LocalDate,
        steps: Long?,
        workouts: List<ExerciseSessionEntity>,
        calorieCalculator: CalorieCalculator
    ): DailyCalorieTarget {
        val bmr = estimate.bmrKcal
        val day = ActiveActivityCalculator.calculate(steps, workouts, estimate.weightKg, estimate.heightCm, bmr)

        // NEAT: the day's steps once they beat your usual day; until then, your usual day.
        val dayNeat = day.stepCalories ?: 0.0
        val neatFromDay = dayNeat > estimate.stepKcal
        val neatKcal = if (neatFromDay) dayNeat else estimate.stepKcal
        val neatMinutes = if (neatFromDay) day.stepMinutes.toDouble() else estimate.avgStepMinutes
        val neatSteps = if (neatFromDay) dayNeat / estimate.kcalPerStep else estimate.avgCountedSteps

        val workoutKcal = day.workoutCalories
        val withoutCalories = workouts.count { ActiveActivityCalculator.workoutCalories(it, bmr) == null }
        val activeMinutes = neatMinutes + day.workoutMinutes
        val restingKcal = bmr * (MINUTES_PER_DAY - activeMinutes).coerceIn(0.0, MINUTES_PER_DAY) / MINUTES_PER_DAY

        val beforeFood = restingKcal + neatKcal + workoutKcal
        val tefKcal = beforeFood * EnergyBalanceCalculator.TEF_FRACTION
        val adaptiveFactor = if (estimate.adaptive != null && estimate.formulaMaintenanceKcal > 0) {
            estimate.maintenanceKcal / estimate.formulaMaintenanceKcal
        } else 1.0
        val burnKcal = (beforeFood + tefKcal) * adaptiveFactor

        val goal = estimate.goalWeightKg?.let { goalKg ->
            calorieCalculator.recommendDailyCalorieGoal(
                tdee = burnKcal,
                currentWeightKg = estimate.weightKg,
                weightGoalKg = goalKg,
                targetDate = estimate.goalDate,
                today = date,
                bmr = bmr,
                sex = estimate.sex
            )
        }
        return DailyCalorieTarget(
            date = date,
            bmrKcal = bmr,
            restingKcal = restingKcal,
            neatKcal = neatKcal,
            neatSteps = neatSteps,
            neatFromDay = neatFromDay,
            workoutKcal = workoutKcal,
            workoutsCounted = workouts.size - withoutCalories,
            workoutsWithoutCalories = withoutCalories,
            tefKcal = tefKcal,
            adaptiveFactor = adaptiveFactor,
            burnKcal = burnKcal,
            goal = goal,
            // A goal whose date has passed has no target; the day's burn stands in.
            targetKcal = goal?.dailyCalorieTarget ?: burnKcal.roundToInt()
        )
    }
}
