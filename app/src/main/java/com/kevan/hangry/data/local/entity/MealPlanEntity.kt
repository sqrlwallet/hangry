package com.kevan.hangry.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/** A saved meal template the user can quick-add from without invoking the AI each time. */
@Entity(
    tableName = "meal_plan",
    indices = [
        Index(value = ["createdAt"])
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
    val createdAt: Instant = Instant.now()
)
