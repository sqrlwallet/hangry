package com.kevan.hangry.domain.calculation

import com.kevan.hangry.data.local.entity.DailyHealthSummaryEntity
import com.kevan.hangry.data.local.entity.ExerciseSessionEntity
import com.kevan.hangry.data.local.entity.FoodLogEntity
import com.kevan.hangry.data.local.entity.WeightMeasurementEntity
import com.kevan.hangry.domain.model.AdaptiveMaintenance
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
 * Today's target is then worked out from today itself (see [DailyCalorieTargetCalculator]):
 * summaries and workouts may include today, which the 7-day averages leave out.
 *
 * Then, with 14+ well-logged days and a weight trend over the last 28, it learns from what
 * actually happened (see [adaptive]) and blends that in - more of it the more days are logged.
 * Formulas can be 10-20% off for any one person; intake vs. weight change isn't.
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
        measuredBodyFatPercent: Double? = null,
        /** The last [ADAPTIVE_WINDOW_DAYS] full days of food logs, for [adaptive]. */
        foodLogs: List<FoodLogEntity> = emptyList(),
        /** Weigh-ins over the same window, for [adaptive]. */
        weights: List<WeightMeasurementEntity> = emptyList()
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
        var stepMinutesSum = 0
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
            stepMinutesSum += active.stepMinutes
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
        val formulaMaintenance = beforeFood + tefKcal
        val (adaptive, adaptiveMissing) = adaptive(foodLogs, weights, formulaMaintenance, today, zone)
        val maintenance = adaptive?.let { formulaMaintenance * (1 - it.weight) + it.maintenanceKcal * it.weight } ?: formulaMaintenance

        // A goal weight is enough; a target date sets the pace when there is one.
        val goal = if (goalWeightKg != null) {
            calorieCalculator.recommendDailyCalorieGoal(
                tdee = maintenance,
                currentWeightKg = weightKg,
                weightGoalKg = goalWeightKg,
                targetDate = goalDate,
                today = today,
                bmr = bmr,
                sex = sex
            )
        } else null

        val estimate = EnergyBalanceEstimate(
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
            formulaMaintenanceKcal = formulaMaintenance,
            adaptive = adaptive,
            adaptiveMissing = adaptiveMissing,
            maintenanceKcal = maintenance,
            goalWeightKg = goalWeightKg,
            goalDate = goalDate,
            goal = goal,
            weightKg = weightKg,
            heightCm = heightCm,
            sex = sex,
            avgStepMinutes = stepMinutesSum / days
        )
        // Today's own target: today's steps (or a usual day's, if more) and today's workouts.
        val todayTarget = DailyCalorieTargetCalculator.calculate(
            estimate = estimate,
            date = today,
            steps = summaries.firstOrNull { it.date == today }?.steps,
            workouts = workoutsByDay[today].orEmpty(),
            calorieCalculator = calorieCalculator
        )
        return EnergyBalanceResult(estimate = estimate.copy(today = todayTarget), missing = emptyList())
    }

    /**
     * Maintenance from the last [ADAPTIVE_WINDOW_DAYS] full days: average intake on well-logged
     * days, minus the weight trend's slope × 7,700 kcal/kg. Returns why not when it can't.
     */
    private fun adaptive(
        foodLogs: List<FoodLogEntity>,
        weights: List<WeightMeasurementEntity>,
        formulaMaintenance: Double,
        today: LocalDate,
        zone: ZoneId
    ): Pair<AdaptiveMaintenance?, String?> {
        val start = today.minusDays(ADAPTIVE_WINDOW_DAYS.toLong())
        val end = today.minusDays(1)
        // A day logging under half of maintenance is almost always a partly-logged day; counting
        // it would make maintenance look lower than it is.
        val intakeByDay = foodLogs.filter { it.date in start..end }
            .groupBy { it.date }
            .mapValues { (_, entries) -> entries.sumOf { it.calories }.toDouble() }
            .filterValues { it >= formulaMaintenance * MIN_LOGGED_FRACTION }
        // Same daily averaging and slope as the Trends weight card (see [WeightTrend]).
        val weightByDay = WeightTrend.dailyWeights(weights, zone)
            .filter { (day, _) -> day in start..end }
            .toMap()
        val span = weightByDay.keys.let { days -> if (days.isEmpty()) 0L else java.time.temporal.ChronoUnit.DAYS.between(days.min(), days.max()) }

        val missing = buildList {
            if (intakeByDay.size < MIN_LOGGED_DAYS) add("${MIN_LOGGED_DAYS - intakeByDay.size} more fully-logged day(s) of food")
            if (weightByDay.size < MIN_WEIGH_INS || span < MIN_WEIGH_IN_SPAN_DAYS) add("weigh-ins on at least $MIN_WEIGH_INS days spread over $MIN_WEIGH_IN_SPAN_DAYS+ days")
        }
        if (missing.isNotEmpty()) return null to missing.joinToString(" and ")

        val slopePerDay = WeightTrend.slopePerDay(weightByDay.toList(), start, end) ?: 0.0
        val avgIntake = intakeByDay.values.average()
        val learnt = avgIntake - slopePerDay * HangryCalorieCalculator.KCAL_PER_KG
        // Far from the formula usually means meals going unlogged or a scale glitch, not a
        // metabolism 40% off average - don't let that steer the target.
        if (learnt !in formulaMaintenance * ADAPTIVE_MIN_RATIO..formulaMaintenance * ADAPTIVE_MAX_RATIO) {
            return null to "intake and weight change don't add up yet - check that every meal is logged"
        }
        val weight = (intakeByDay.size.toDouble() / ADAPTIVE_WINDOW_DAYS).coerceIn(ADAPTIVE_MIN_WEIGHT, ADAPTIVE_MAX_WEIGHT)
        return AdaptiveMaintenance(
            windowStart = start,
            windowEnd = end,
            loggedDays = intakeByDay.size,
            avgIntakeKcal = avgIntake,
            weighIns = weightByDay.size,
            weeklyWeightChangeKg = slopePerDay * 7,
            maintenanceKcal = learnt,
            weight = weight
        ) to null
    }

    companion object {
        const val WINDOW_DAYS = 7
        const val ADAPTIVE_WINDOW_DAYS = 28
        const val MIN_LOGGED_DAYS = 14
        const val MIN_WEIGH_INS = 4
        const val MIN_WEIGH_IN_SPAN_DAYS = 14
        private const val MIN_LOGGED_FRACTION = 0.5
        private const val ADAPTIVE_MIN_RATIO = 0.7
        private const val ADAPTIVE_MAX_RATIO = 1.4
        /** At 14 logged days it's half the answer; by ~24 days it's most of it. */
        private const val ADAPTIVE_MIN_WEIGHT = 0.5
        private const val ADAPTIVE_MAX_WEIGHT = 0.85
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
