package com.kevan.hangry.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

/** The user's own "how do you feel" pick for a day with no HRV reading - see HrvFeeling. */
@Entity(tableName = "hrv_feelings")
data class HrvFeelingEntity(
    @PrimaryKey
    val date: LocalDate,
    val feeling: String,
    val updatedAt: Instant = Instant.now()
)
