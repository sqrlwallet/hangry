package com.kevan.hangry.data.local.entity

import com.kevan.hangry.domain.model.CommonFood
import java.time.Instant
import java.time.LocalDate
import kotlin.math.roundToInt

/** [portion] helpings of a saved meal, as a log entry for [date]. */
fun MealPlanEntity.toLogEntry(date: LocalDate, portion: Double = 1.0, timestamp: Instant = Instant.now()) = FoodLogEntity(
    date = date,
    timestamp = timestamp,
    source = FoodLogSource.MEAL_PLAN,
    foodName = portionedName(name, portion),
    calories = (calories * portion).roundToInt(),
    proteinG = proteinG * portion,
    carbsG = carbsG * portion,
    fatG = fatG * portion,
    fiberG = fiberG * portion,
    sugarG = sugarG * portion,
    sodiumMg = sodiumMg * portion
)

/** [portion] standard servings of a built-in food, as a log entry for [date]. */
fun CommonFood.toLogEntry(date: LocalDate, portion: Double = 1.0, timestamp: Instant = Instant.now()) = FoodLogEntity(
    date = date,
    timestamp = timestamp,
    source = FoodLogSource.MANUAL,
    foodName = portionedName(name, portion),
    calories = (calories * portion).roundToInt(),
    proteinG = proteinG * portion,
    carbsG = carbsG * portion,
    fatG = fatG * portion,
    fiberG = fiberG * portion,
    sugarG = sugarG * portion,
    sodiumMg = sodiumMg * portion
)
