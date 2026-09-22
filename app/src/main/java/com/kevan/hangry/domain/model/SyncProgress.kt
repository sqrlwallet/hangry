package com.kevan.hangry.domain.model

data class SyncProgress(
    val status: SyncStatus = SyncStatus.IDLE,
    val currentDataType: String = "",
    val recordsRead: Int = 0,
    val recordsInserted: Int = 0,
    val recordsUpdated: Int = 0,
    val recordsSkipped: Int = 0,
    val errorMessage: String? = null
)
