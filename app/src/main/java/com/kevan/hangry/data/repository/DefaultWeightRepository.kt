package com.kevan.hangry.data.repository

import com.kevan.hangry.data.local.dao.WeightDao
import com.kevan.hangry.data.local.entity.WeightMeasurementEntity
import com.kevan.hangry.domain.repository.WeightRepository
import kotlinx.coroutines.flow.Flow
import java.time.Instant

class DefaultWeightRepository(
    private val weightDao: WeightDao
) : WeightRepository {
    override suspend fun insertOrIgnore(records: List<WeightMeasurementEntity>): List<Long> =
        weightDao.insertOrIgnore(records)

    override fun getLatestWeight(): Flow<WeightMeasurementEntity?> =
        weightDao.getLatestWeight()

    override suspend fun getLatestWeightSync(): WeightMeasurementEntity? =
        weightDao.getLatestWeightSync()

    override fun getAllWeights(): Flow<List<WeightMeasurementEntity>> =
        weightDao.getAllWeights()

    override fun getRollingAverageWeight(): Flow<Double?> =
        weightDao.getRollingAverageWeight()

    override suspend fun getCount(): Int =
        weightDao.getCount()

    override suspend fun getImportedFingerprintsBetween(start: Instant, end: Instant): List<String> =
        weightDao.getImportedFingerprintsBetween(start, end)

    override suspend fun deleteByFingerprints(fingerprints: List<String>) =
        weightDao.deleteByFingerprints(fingerprints)

    override suspend fun deleteAll() =
        weightDao.deleteAll()
}
