package com.kevan.hangry.data.repository

import com.kevan.hangry.data.local.dao.HeartRateDao
import com.kevan.hangry.data.local.dao.RestingHeartRateDao
import com.kevan.hangry.data.local.entity.HeartRateSampleEntity
import com.kevan.hangry.data.local.entity.RestingHeartRateEntity
import com.kevan.hangry.domain.repository.HeartRateRepository
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

class DefaultHeartRateRepository(
    private val rhrDao: RestingHeartRateDao,
    private val hrDao: HeartRateDao
) : HeartRateRepository {
    override fun getRestingHeartRates(start: LocalDate, end: LocalDate): Flow<List<RestingHeartRateEntity>> =
        rhrDao.getBetween(start, end)

    override suspend fun getRestingHeartRateForDate(date: LocalDate): RestingHeartRateEntity? =
        rhrDao.getForDate(date)

    override suspend fun getAverageRestingHeartRate(start: LocalDate, end: LocalDate): Double? =
        rhrDao.getAverageBetween(start, end)

    override suspend fun insertRestingHeartRates(records: List<RestingHeartRateEntity>): List<Long> =
        rhrDao.insertOrIgnore(records)

    override fun getSamplesBetween(start: Instant, end: Instant): Flow<List<HeartRateSampleEntity>> =
        hrDao.getSamplesBetween(start, end)

    override fun getZoneDistribution(start: Instant, end: Instant): Flow<com.kevan.hangry.domain.model.HeartRateZoneDistribution> =
        hrDao.getZoneDistribution(start, end)

    override suspend fun getZoneDistributionSync(start: Instant, end: Instant): com.kevan.hangry.domain.model.HeartRateZoneDistribution =
        hrDao.getZoneDistributionSync(start, end)

    override suspend fun insertSamples(samples: List<HeartRateSampleEntity>): List<Long> =
        hrDao.insertOrIgnore(samples)

    override suspend fun deleteAll() {
        rhrDao.deleteAll()
        hrDao.deleteAll()
    }
}
