package com.kevan.hangry.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/** One fast. A null [endAt] is the fast running now - there's at most one. */
@Entity(tableName = "fasts", indices = [Index(value = ["startAt"])])
data class FastEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startAt: Instant,
    val endAt: Instant? = null,
    val targetMinutes: Int,
    /** FastingPlan id at the time. */
    val planId: String
)
