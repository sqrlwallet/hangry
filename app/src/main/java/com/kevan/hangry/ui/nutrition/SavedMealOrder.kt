package com.kevan.hangry.ui.nutrition

import com.kevan.hangry.data.local.entity.MealPlanEntity
import java.time.Instant

/**
 * Saved meals split into the foods logged most (pinned at the top) and the rest in the order
 * they came in (most recently used first). A food needs [MIN_USES] logs to count as a favourite,
 * so a long list of one-offs doesn't pin anything.
 */
internal object SavedMealOrder {

    const val PINNED_COUNT = 5
    const val MIN_USES = 2

    data class Sections(val mostLogged: List<MealPlanEntity>, val rest: List<MealPlanEntity>)

    fun split(meals: List<MealPlanEntity>, pinnedCount: Int = PINNED_COUNT): Sections {
        val pinned = meals
            .filter { it.useCount >= MIN_USES }
            .sortedWith(
                compareByDescending<MealPlanEntity> { it.useCount }
                    .thenByDescending { it.lastUsedAt ?: Instant.EPOCH }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            )
            .take(pinnedCount)
        val pinnedIds = pinned.mapTo(HashSet()) { it.id }
        return Sections(pinned, meals.filter { it.id !in pinnedIds })
    }
}
