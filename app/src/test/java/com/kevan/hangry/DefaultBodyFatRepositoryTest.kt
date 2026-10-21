package com.kevan.hangry

import com.kevan.hangry.data.local.dao.BodyFatScanDao
import com.kevan.hangry.data.local.entity.BodyFatScanEntity
import com.kevan.hangry.data.repository.DefaultBodyFatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class DefaultBodyFatRepositoryTest {

    private val scan = BodyFatScanEntity(id = 7, date = LocalDate.of(2026, 10, 1), bodyFatPercentage = 18.5, category = "Fit", method = "NAVY_CIRCUMFERENCE")

    private class FakeDao(private val scan: BodyFatScanEntity) : BodyFatScanDao {
        val inserted = mutableListOf<BodyFatScanEntity>()
        val deleted = mutableListOf<Long>()
        var range: Pair<LocalDate, LocalDate>? = null

        override suspend fun insert(scan: BodyFatScanEntity): Long { inserted += scan; return 99 }
        override fun getLatestScan(): Flow<BodyFatScanEntity?> = flowOf(scan)
        override suspend fun getLatestScanSync(): BodyFatScanEntity? = scan
        override fun getAllScans(): Flow<List<BodyFatScanEntity>> = flowOf(listOf(scan))
        override fun getScansBetween(startDate: LocalDate, endDate: LocalDate): Flow<List<BodyFatScanEntity>> {
            range = startDate to endDate
            return flowOf(listOf(scan))
        }
        override suspend fun deleteById(id: Long) { deleted += id }
        override suspend fun deleteAll() = Unit
    }

    @Test
    fun readsComeStraightFromTheDao() = runTest {
        val repo = DefaultBodyFatRepository(FakeDao(scan))
        assertEquals(scan, repo.getLatestScan().first())
        assertEquals(scan, repo.getLatestScanSync())
        assertEquals(listOf(scan), repo.getAllScans().first())
    }

    @Test
    fun passesTheDateRangeThrough() = runTest {
        val dao = FakeDao(scan)
        val start = LocalDate.of(2026, 9, 1)
        val end = LocalDate.of(2026, 9, 30)
        assertEquals(listOf(scan), DefaultBodyFatRepository(dao).getScansBetween(start, end).first())
        assertEquals(start to end, dao.range)
    }

    @Test
    fun savesAndDeletesThroughTheDao() = runTest {
        val dao = FakeDao(scan)
        val repo = DefaultBodyFatRepository(dao)
        assertEquals(99L, repo.saveScan(scan))
        repo.deleteScan(7)
        assertEquals(listOf(scan), dao.inserted)
        assertEquals(listOf(7L), dao.deleted)
    }
}
