package com.kevan.hangry.data.repository

import com.kevan.hangry.data.local.dao.BodyFatScanDao
import com.kevan.hangry.data.local.entity.BodyFatScanEntity
import com.kevan.hangry.domain.repository.BodyFatRepository
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class DefaultBodyFatRepository(
    private val bodyFatScanDao: BodyFatScanDao
) : BodyFatRepository {

    override fun getLatestScan(): Flow<BodyFatScanEntity?> {
        return bodyFatScanDao.getLatestScan()
    }

    override suspend fun getLatestScanSync(): BodyFatScanEntity? {
        return bodyFatScanDao.getLatestScanSync()
    }

    override fun getAllScans(): Flow<List<BodyFatScanEntity>> {
        return bodyFatScanDao.getAllScans()
    }

    override fun getScansBetween(startDate: LocalDate, endDate: LocalDate): Flow<List<BodyFatScanEntity>> {
        return bodyFatScanDao.getScansBetween(startDate, endDate)
    }

    override suspend fun saveScan(scan: BodyFatScanEntity): Long {
        return bodyFatScanDao.insert(scan)
    }

    override suspend fun deleteScan(id: Long) {
        bodyFatScanDao.deleteById(id)
    }
}
