package com.kevan.hangry.domain.repository

interface LocalExportManager {
    suspend fun exportDataAsJson(): String
    suspend fun exportDataAsCsv(): String
}
