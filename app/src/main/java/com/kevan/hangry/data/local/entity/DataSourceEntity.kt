package com.kevan.hangry.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "data_sources")
data class DataSourceEntity(
    @PrimaryKey
    val packageName: String,
    val displayName: String,
    val lastSeenTimestamp: Instant = Instant.now(),
    val totalRecordsProvided: Int = 0,
    val isEnabled: Boolean = true
)
