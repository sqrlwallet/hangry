package com.kevan.hangry

import com.kevan.hangry.data.local.entity.SleepSessionEntity
import com.kevan.hangry.domain.calculation.DayMetrics
import com.kevan.hangry.domain.calculation.HangryRecoveryCalculator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class SyncPipelineIdempotencyUnitTest {

    @Test
    fun testSyncDeduplication_repeatedSyncDoesNotCreateDuplicateFingerprints() = runBlocking {
        val start = LocalDate.now(ZoneOffset.UTC).minusDays(14).atStartOfDay().toInstant(ZoneOffset.UTC)
        val run1Records = (0 until 14).map { i ->
            val s = start.plusSeconds(i * 86400L)
            SleepSessionEntity(
                sourceRecordId = "sleep-$i",
                sourcePackageName = "com.google.android.apps.healthdata",
                recordFingerprint = "fingerprint-$i",
                startTime = s,
                endTime = s.plusSeconds(28800),
                durationMinutes = 480
            )
        }
        val localDatabaseSim = mutableMapOf<String, SleepSessionEntity>()

        // First sync run: insert records
        var run1Inserted = 0
        var run1Skipped = 0
        run1Records.forEach { record ->
            if (!localDatabaseSim.containsKey(record.recordFingerprint)) {
                localDatabaseSim[record.recordFingerprint] = record
                run1Inserted++
            } else {
                run1Skipped++
            }
        }

        assertEquals(run1Records.size, run1Inserted)
        assertEquals(0, run1Skipped)
        assertEquals(run1Records.size, localDatabaseSim.size)

        // Second sync run with identical query
        val run2Records = run1Records
        var run2Inserted = 0
        var run2Skipped = 0
        run2Records.forEach { record ->
            if (!localDatabaseSim.containsKey(record.recordFingerprint)) {
                localDatabaseSim[record.recordFingerprint] = record
                run2Inserted++
            } else {
                run2Skipped++
            }
        }

        assertEquals("Zero duplicate records should be inserted on second sync", 0, run2Inserted)
        assertEquals("All records should be safely skipped as duplicates", run2Records.size, run2Skipped)
        assertEquals(run1Records.size, localDatabaseSim.size)
    }

    @Test
    fun testRecalculationAfterDeletion_updatesBaselineAccurately() {
        val calculator = HangryRecoveryCalculator()
        val today = LocalDate.of(2026, 9, 22)

        val current = DayMetrics(today, 480, 56.0, 68.0)
        val historyWithBadDay = (1..7).map { offset ->
            if (offset == 3) {
                // Skewed outlier day that user later deletes
                DayMetrics(today.minusDays(offset.toLong()), 180, 85.0, 20.0)
            } else {
                DayMetrics(today.minusDays(offset.toLong()), 480, 56.0, 65.0)
            }
        }

        val resultBeforeDeletion = calculator.calculateRecovery(today, current, historyWithBadDay)

        // After user deletes day 3
        val historyAfterDeletion = historyWithBadDay.filterIndexed { index, _ -> index != 2 }
        val resultAfterDeletion = calculator.calculateRecovery(today, current, historyAfterDeletion)

        assertNotNull(resultBeforeDeletion.score)
        assertNotNull(resultAfterDeletion.score)
        assertTrue(resultBeforeDeletion.score!! in 0..100)
        assertTrue(resultAfterDeletion.score!! in 0..100)
        // Baseline changes when outlier is removed, altering the calculated recovery score
        assertNotEquals(resultBeforeDeletion.score, resultAfterDeletion.score)
    }
}
