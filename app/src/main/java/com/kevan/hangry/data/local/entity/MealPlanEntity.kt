package com.kevan.hangry.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * A saved meal the user can log again in one tap, without the AI or retyping. Rows are added by
 * hand, by the coach, and automatically every time the user logs a food - so the handful of meals
 * someone actually eats builds itself up. One row per food name ([nameKey]); logging the same
 * food again refreshes its numbers and bumps [useCount] / [lastUsedAt].
 */
@Entity(
    tableName = "meal_plan",
    indices = [
        Index(value = ["createdAt"]),
        Index(value = ["nameKey"], unique = true)
    ]
)
data class MealPlanEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val mealType: String = "OTHER", // BREAKFAST / LUNCH / DINNER / SNACK / OTHER
    val calories: Int,
    val proteinG: Double = 0.0,
    val carbsG: Double = 0.0,
    val fatG: Double = 0.0,
    val fiberG: Double = 0.0,
    val sugarG: Double = 0.0,
    val sodiumMg: Double = 0.0,
    val createdAt: Instant = Instant.now(),
    /** Case- and space-insensitive identity of [name], so "Banana" and " banana " are one meal. */
    val nameKey: String = savedMealKey(name),
    val useCount: Int = 0,
    val lastUsedAt: Instant? = null
)

fun savedMealKey(name: String): String = name.trim().replace(Regex("\\s+"), " ").lowercase()

/** Portion sizes offered when logging a saved or common food. */
val PORTIONS = listOf(0.5, 1.0, 1.5, 2.0)

/** "½", "1½", "2" - how a portion reads next to a food name. */
fun portionLabel(portion: Double): String {
    val whole = portion.toInt()
    val half = portion - whole == 0.5
    return when {
        half && whole == 0 -> "½"
        half -> "$whole½"
        portion == whole.toDouble() -> whole.toString()
        else -> portion.toString()
    }
}

/** "Banana" logged at 2 portions is "Banana (×2)"; one portion keeps the plain name. */
fun portionedName(name: String, portion: Double): String =
    if (portion == 1.0) name else "$name (×${portionLabel(portion)})"

private val PORTION_SUFFIX = Regex("""^(.*\S)\s*\(×(\d*½|\d+(?:\.\d+)?)\)$""")

/** Splits "Banana (×2)" back into ("Banana", 2.0); a name without a portion is 1 portion. */
fun parsePortionedName(name: String): Pair<String, Double> {
    val match = PORTION_SUFFIX.matchEntire(name.trim()) ?: return name to 1.0
    val label = match.groupValues[2]
    val portion = if (label.endsWith("½")) (label.dropLast(1).toIntOrNull() ?: 0) + 0.5 else label.toDoubleOrNull()
    return if (portion == null || portion <= 0.0) name to 1.0 else match.groupValues[1] to portion
}
