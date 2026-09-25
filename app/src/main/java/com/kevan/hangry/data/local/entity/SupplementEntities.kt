package com.kevan.hangry.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

@Entity(tableName = "supplements")
data class SupplementEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val brand: String? = null,
    val form: String? = null,
    val doseAmount: Double = 1.0,
    val doseUnit: String = "serving",
    /** Comma-separated "HH:mm" dose times - see SupplementTimes. */
    val times: String = "",
    /** JSON list of SupplementIngredient. */
    val ingredientsJson: String = "[]",
    val remindersEnabled: Boolean = false,
    /**
     * The user asked Hangry to help them take it: doses get check-offs, adherence and (optionally)
     * reminders. Otherwise it's context for Dash and assumed taken as usual.
     */
    val tracked: Boolean = false,
    val notes: String? = null,
    val photoPath: String? = null,
    val active: Boolean = true,
    val createdAt: Instant = Instant.now()
)

/** A dose marked taken. One row per supplement per scheduled time per day. */
@Entity(
    tableName = "supplement_intakes",
    indices = [
        Index(value = ["supplementId", "date", "scheduledTime"], unique = true),
        Index(value = ["date"])
    ]
)
data class SupplementIntakeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val supplementId: Long,
    val date: LocalDate,
    /** "HH:mm" of the dose this covers. */
    val scheduledTime: String,
    val takenAt: Instant = Instant.now()
)
