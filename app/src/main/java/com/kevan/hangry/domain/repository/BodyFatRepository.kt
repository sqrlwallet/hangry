package com.kevan.hangry.domain.repository

import com.kevan.hangry.data.local.entity.BodyFatScanEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface BodyFatRepository {
    fun getLatestScan(): Flow<BodyFatScanEntity?>
    suspend fun getLatestScanSync(): BodyFatScanEntity?
    fun getAllScans(): Flow<List<BodyFatScanEntity>>
    fun getScansBetween(startDate: LocalDate, endDate: LocalDate): Flow<List<BodyFatScanEntity>>
    suspend fun saveScan(scan: BodyFatScanEntity): Long
    suspend fun deleteScan(id: Long)
}
