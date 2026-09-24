package com.kevan.hangry.data.repository

import com.kevan.hangry.data.local.dao.MealPlanDao
import com.kevan.hangry.data.local.entity.FoodLogEntity
import com.kevan.hangry.data.local.entity.FoodLogSource
import com.kevan.hangry.data.local.entity.MealPlanEntity
import com.kevan.hangry.data.local.entity.parsePortionedName
import com.kevan.hangry.data.local.entity.savedMealKey
import com.kevan.hangry.domain.repository.MealPlanRepository
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import kotlin.math.roundToInt

class DefaultMealPlanRepository(
    private val dao: MealPlanDao
) : MealPlanRepository {
    override fun getAll(): Flow<List<MealPlanEntity>> = dao.getAll()

    override suspend fun upsert(entry: MealPlanEntity): Long {
        val key = savedMealKey(entry.name)
        val existing = dao.getByKey(key)
        return dao.upsert(
            entry.copy(
                id = existing?.id ?: entry.id,
                name = entry.name.trim(),
                nameKey = key,
                useCount = maxOf(entry.useCount, existing?.useCount ?: 0),
                lastUsedAt = entry.lastUsedAt ?: existing?.lastUsedAt,
                createdAt = existing?.createdAt ?: entry.createdAt
            )
        )
    }

    override suspend fun delete(entry: MealPlanEntity) = dao.delete(entry)

    override suspend fun deleteAll() = dao.deleteAll()

    override suspend fun rememberFromLog(entry: FoodLogEntity, countAsUse: Boolean) {
        if (!isWorthRemembering(entry)) return
        // "Banana (×2)" is two portions of the saved "Banana", not a new food.
        val (baseName, portion) = parsePortionedName(entry.foodName)
        val key = savedMealKey(baseName)
        val now = Instant.now()
        val existing = dao.getByKey(key)
        if (existing == null) {
            dao.upsert(
                MealPlanEntity(
                    name = baseName.trim(),
                    calories = (entry.calories / portion).roundToInt(),
                    proteinG = entry.proteinG / portion,
                    carbsG = entry.carbsG / portion,
                    fatG = entry.fatG / portion,
                    fiberG = entry.fiberG / portion,
                    sugarG = entry.sugarG / portion,
                    sodiumMg = entry.sodiumMg / portion,
                    nameKey = key,
                    useCount = 1,
                    lastUsedAt = now
                )
            )
        } else if (portion != 1.0) {
            // A bigger or smaller helping of a known meal says nothing new about one portion:
            // count it, but keep the saved numbers (scaling back would also drift by rounding).
            if (countAsUse) dao.update(existing.copy(useCount = existing.useCount + 1, lastUsedAt = now))
        } else {
            // The manual form has no fiber/sugar/sodium fields: re-logging the same food through
            // it shouldn't wipe what the saved meal already knows about them.
            val keepMicros = entry.calories == existing.calories &&
                entry.fiberG == 0.0 && entry.sugarG == 0.0 && entry.sodiumMg == 0.0
            dao.update(
                existing.copy(
                    calories = entry.calories,
                    proteinG = entry.proteinG,
                    carbsG = entry.carbsG,
                    fatG = entry.fatG,
                    fiberG = if (keepMicros) existing.fiberG else entry.fiberG,
                    sugarG = if (keepMicros) existing.sugarG else entry.sugarG,
                    sodiumMg = if (keepMicros) existing.sodiumMg else entry.sodiumMg,
                    useCount = existing.useCount + if (countAsUse) 1 else 0,
                    lastUsedAt = if (countAsUse) now else existing.lastUsedAt ?: now
                )
            )
        }
    }

    companion object {
        /** Placeholder names from the quick-log chips - they say nothing about what was eaten. */
        private val GENERIC_NAMES = setOf("meal", "breakfast", "lunch", "dinner", "snack", "snacks")

        fun isWorthRemembering(entry: FoodLogEntity): Boolean =
            entry.source != FoodLogSource.HEALTH_CONNECT &&
                savedMealKey(entry.foodName).let { it.isNotEmpty() && it !in GENERIC_NAMES }
    }
}
