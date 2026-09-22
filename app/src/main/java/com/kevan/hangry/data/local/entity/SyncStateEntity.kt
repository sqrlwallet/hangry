package com.kevan.hangry.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey
    val dataType: String,
    val lastSuccessfulSyncTimestamp: Instant? = null,
    val historicalImportStart: Instant? = null,
    val historicalImportEnd: Instant? = null,
    val currentCursor: String? = null,
    val syncStatus: String = "IDLE", // "IDLE", "IN_PROGRESS", "SUCCESS", "FAILED"
    val lastError: String? = null,
    val recordsRead: Int = 0,
    val recordsInserted: Int = 0,
    val recordsUpdated: Int = 0,
    val recordsSkipped: Int = 0,
    val syncTimestamp: Instant = Instant.now()
)
