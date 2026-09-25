package com.kevan.hangry.data.local.entity

import androidx.room.Entity
import java.time.LocalDate

/** A longevity pillar ticked off by hand for a day (balance drills, a quick stretch). */
@Entity(tableName = "longevity_check_ins", primaryKeys = ["date", "pillar"])
data class LongevityCheckInEntity(
    val date: LocalDate,
    /** LongevityPillar name. */
    val pillar: String
)
