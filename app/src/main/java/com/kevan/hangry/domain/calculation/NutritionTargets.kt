package com.kevan.hangry.domain.calculation

import com.kevan.hangry.domain.model.EnergyBalanceEstimate
import kotlin.math.roundToInt

/** Daily calorie and macro targets, shared by the Nutrition screen and the nutrition widgets. */
object NutritionTargets {

    data class Macros(val proteinG: Double, val carbsG: Double, val fatG: Double)

    /**
     * The goal's target when a goal weight and date are set, otherwise maintenance. Null until
     * there's enough real data for an estimate - never an assumed 2000 kcal.
     */
    fun dailyCalories(estimate: EnergyBalanceEstimate?): Int? =
        estimate?.let { it.goal?.dailyCalorieTarget ?: it.maintenanceKcal.roundToInt() }

    /** A 25 / 50 / 25 protein / carbs / fat split of [calories], with sensible floors. */
    fun macros(calories: Int?): Macros? = calories?.let {
        Macros(
            proteinG = (it * 0.25 / 4.0).coerceAtLeast(50.0),
            carbsG = (it * 0.50 / 4.0).coerceAtLeast(100.0),
            fatG = (it * 0.25 / 9.0).coerceAtLeast(30.0)
        )
    }
}
