package com.kevan.hangry.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "calculation_metadata")
data class CalculationMetadataEntity(
    @PrimaryKey
    val calculationType: String, // "RECOVERY", "SLEEP", "TRAINING_LOAD"
    val algorithmVersion: Int = 1,
    val lastRunTimestamp: Instant = Instant.now(),
    val parametersHash: String = ""
)
