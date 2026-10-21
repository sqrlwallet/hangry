package com.kevan.hangry

import com.kevan.hangry.data.local.dao.HeartRateDao
import com.kevan.hangry.data.local.dao.HeartRateRecordSpan
import com.kevan.hangry.data.local.dao.RestingHeartRateDao
import com.kevan.hangry.data.local.entity.HeartRateSampleEntity
import com.kevan.hangry.data.local.entity.RestingHeartRateEntity
import com.kevan.hangry.data.repository.DefaultHeartRateRepository
import com.kevan.hangry.domain.calculation.HeartRateZones
import com.kevan.hangry.domain.model.HeartRateZoneDistribution
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class DefaultHeartRateRepositoryTest {

    private val start = Instant.parse("2026-10-20T06:00:00Z")
    private val end = Instant.parse("2026-10-20T07:00:00Z")
    private val zones = HeartRateZones(maxHr = 190.0, restingHr = 60.0)
    private val distribution = HeartRateZoneDistribution(zone1Count = 3, zone2Count = 5, zone5Count = 1)

    private class FakeHrDao(private val distribution: HeartRateZoneDistribution) : HeartRateDao {
        var asked: List<Any>? = null
        var cleared = false

        override fun getZoneDistribution(start: Instant, end: Instant, z1: Double, z2: Double, z3: Double, z4: Double, z5: Double): Flow<HeartRateZoneDistribution> {
            asked = listOf(start, end, z1, z2, z3, z4, z5)
            return flowOf(distribution)
        }
        override suspend fun getZoneDistributionSync(start: Instant, end: Instant, z1: Double, z2: Double, z3: Double, z4: Double, z5: Double): HeartRateZoneDistribution {
            asked = listOf(start, end, z1, z2, z3, z4, z5)
            return distribution
        }
        override suspend fun deleteAll() { cleared = true }

        override suspend fun insertOrIgnore(samples: List<HeartRateSampleEntity>): List<Long> = TODO()
        override fun getSamplesBetween(start: Instant, end: Instant): Flow<List<HeartRateSampleEntity>> = TODO()
        override suspend fun getSamplesBetweenList(start: Instant, end: Instant): List<HeartRateSampleEntity> = TODO()
        override suspend fun getAverageBpmBetween(start: Instant, end: Instant): Double? = TODO()
        override suspend fun getMinBpmBetween(start: Instant, end: Instant): Double? = TODO()
        override suspend fun getMaxBpmBetween(start: Instant, end: Instant): Double? = TODO()
        override suspend fun getCount(): Int = TODO()
        override suspend fun getImportedRecordSpansBetween(start: Instant, end: Instant): List<HeartRateRecordSpan> = TODO()
        override suspend fun getFingerprintsForRecords(recordIds: List<String>): List<String> = TODO()
        override suspend fun deleteByRecordIds(recordIds: List<String>) = TODO()
        override suspend fun deleteByFingerprints(fingerprints: List<String>) = TODO()
    }

    private class FakeRhrDao : RestingHeartRateDao {
        var cleared = false
        override suspend fun deleteAll() { cleared = true }

        override suspend fun insertOrIgnore(records: List<RestingHeartRateEntity>): List<Long> = TODO()
        override suspend fun getForDate(date: LocalDate): RestingHeartRateEntity? = TODO()
        override fun getBetween(start: LocalDate, end: LocalDate): Flow<List<RestingHeartRateEntity>> = TODO()
        override suspend fun getBetweenList(start: LocalDate, end: LocalDate): List<RestingHeartRateEntity> = TODO()
        override suspend fun getAverageBetween(start: LocalDate, end: LocalDate): Double? = TODO()
        override suspend fun getCount(): Int = TODO()
        override suspend fun getImportedFingerprintsBetween(start: Instant, end: Instant): List<String> = TODO()
        override suspend fun deleteByFingerprints(fingerprints: List<String>) = TODO()
    }

    @Test
    fun zoneDistributionPassesTheFiveLowerBoundsInOrder() = runTest {
        val hr = FakeHrDao(distribution)
        val repo = DefaultHeartRateRepository(FakeRhrDao(), hr)
        // 60 resting, 190 max: zones start at 50/60/70/80/90% of the 130 bpm reserve.
        val expected = listOf(start, end, 125.0, 138.0, 151.0, 164.0, 177.0)

        assertEquals(distribution, repo.getZoneDistribution(start, end, zones).first())
        assertEquals(expected, hr.asked)

        hr.asked = null
        assertEquals(distribution, repo.getZoneDistributionSync(start, end, zones))
        assertEquals(expected, hr.asked)
    }

    @Test
    fun deleteAllClearsBothTables() = runTest {
        val rhr = FakeRhrDao()
        val hr = FakeHrDao(distribution)
        DefaultHeartRateRepository(rhr, hr).deleteAll()
        assertTrue(rhr.cleared && hr.cleared)
    }
}
