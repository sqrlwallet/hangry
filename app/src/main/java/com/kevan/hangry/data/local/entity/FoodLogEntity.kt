package com.kevan.hangry.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

/** How a [FoodLogEntity] row was created - not synced from Health Connect, so no dedup fingerprint is needed. */
object FoodLogSource {
    const val PHOTO = "PHOTO"
    const val MANUAL = "MANUAL"
    const val MEAL_PLAN = "MEAL_PLAN"
}

@Entity(
    tableName = "food_log",
    indices = [Index(value = ["date"])]
)
data class FoodLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: LocalDate,
    val timestamp: Instant = Instant.now(),
    val source: String,
    val foodName: String,
    val calories: Int,
    val proteinG: Double = 0.0,
    val carbsG: Double = 0.0,
    val fatG: Double = 0.0,
    val fiberG: Double = 0.0,
    val sugarG: Double = 0.0,
    val sodiumMg: Double = 0.0,
    /** Local file path (app-private storage), only set when [source] is PHOTO. */
    val photoPath: String? = null,
    val healthConnectSynced: Boolean = false
)
