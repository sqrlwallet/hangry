package com.kevan.hangry.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

/** One lab/vital reading (see MarkerType). Values are stored in the marker's canonical unit. */
@Entity(
    tableName = "health_markers",
    indices = [
        Index(value = ["type", "measuredAt"]),
        Index(value = ["sourceRecordId"], unique = true)
    ]
)
data class HealthMarkerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: String,
    val value: Double,
    val secondaryValue: Double? = null,
    val measuredAt: Instant,
    val date: LocalDate,
    val glucoseContext: String? = null,
    val source: String,
    /** Health Connect / medical-record id, so re-imports don't duplicate. Null for manual entries. */
    val sourceRecordId: String? = null,
    val note: String? = null
)

@Entity(tableName = "marker_goals")
data class MarkerGoalEntity(
    @PrimaryKey
    val type: String,
    val targetValue: Double,
    val targetSecondary: Double? = null,
    val direction: String,
    val startValue: Double? = null,
    val startDate: LocalDate,
    val targetDate: LocalDate? = null,
    val createdAt: Instant = Instant.now()
)

/** An allergy or condition, entered by the user or imported from medical records. */
@Entity(
    tableName = "health_profile_items",
    indices = [Index(value = ["sourceRecordId"], unique = true)]
)
data class HealthProfileItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val kind: String,
    val name: String,
    val note: String? = null,
    val source: String,
    val sourceRecordId: String? = null,
    val createdAt: Instant = Instant.now()
)

@Entity(
    tableName = "menstrual_periods",
    indices = [Index(value = ["startDate"]), Index(value = ["sourceRecordId"], unique = true)]
)
data class MenstrualPeriodEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val source: String,
    val sourceRecordId: String? = null
)
