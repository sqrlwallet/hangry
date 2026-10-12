package com.kevan.hangry.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

/** One drink logged in Hangry. Also written to Health Connect as a HydrationRecord when allowed. */
@Entity(
    tableName = "water_log",
    indices = [Index(value = ["date"])]
)
data class WaterLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: LocalDate,
    val timestamp: Instant,
    val volumeMl: Int,
    val healthConnectSynced: Boolean = false
)
