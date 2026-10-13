package com.kevan.hangry.data.repository

import com.kevan.hangry.data.local.dao.HeightDao
import com.kevan.hangry.data.local.entity.HeightMeasurementEntity
import com.kevan.hangry.domain.repository.HeightRepository
import kotlinx.coroutines.flow.Flow
import java.time.Instant

class DefaultHeightRepository(
    private val heightDao: HeightDao
) : HeightRepository {
    override suspend fun insertOrIgnore(records: List<HeightMeasurementEntity>): List<Long> =
        heightDao.insertOrIgnore(records)

    override fun getLatestHeight(): Flow<HeightMeasurementEntity?> =
        heightDao.getLatestHeight()

    override suspend fun getLatestHeightSync(): HeightMeasurementEntity? =
        heightDao.getLatestHeightSync()

    override suspend fun getCount(): Int =
        heightDao.getCount()

    override suspend fun getImportedFingerprintsBetween(start: Instant, end: Instant): List<String> =
        heightDao.getImportedFingerprintsBetween(start, end)

    override suspend fun deleteByFingerprints(fingerprints: List<String>) =
        heightDao.deleteByFingerprints(fingerprints)

    override suspend fun deleteAll() =
        heightDao.deleteAll()
}
