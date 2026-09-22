package com.kevan.hangry

import com.kevan.hangry.data.datasource.FakeHealthConnectDataSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class FakeHealthConnectDataSourceTest {

    @Test
    fun testFakeDataSource_generatesConsistentDeterministicRecords() = runBlocking {
        val dataSource = FakeHealthConnectDataSource(daysOfData = 14)

        val today = LocalDate.now(ZoneOffset.UTC)
        val startDate = today.minusDays(14)
        val startInstant = startDate.atStartOfDay().toInstant(ZoneOffset.UTC)
        val endInstant = Instant.now()

        val sleepSessions = dataSource.fetchSleepSessions(startInstant, endInstant)
        val workouts = dataSource.fetchExerciseSessions(startInstant, endInstant)
        val rhr = dataSource.fetchRestingHeartRates(startDate, today)
        val hrv = dataSource.fetchHrvMeasurements(startDate, today)
        val steps = dataSource.fetchStepsSummaries(startDate, today)

        assertTrue("Should have at least 10 sleep sessions", sleepSessions.size >= 10)
        assertTrue("Should have alternating workouts", workouts.isNotEmpty())
        assertTrue("Should have daily resting heart rate", rhr.size >= 10)
        assertTrue("Should have daily HRV records", hrv.size >= 10)
        assertTrue("Should have steps records", steps.size >= 10)

        // Verify fingerprints are valid non-empty hex strings
        sleepSessions.forEach { session ->
            assertEquals(64, session.recordFingerprint.length)
            assertTrue(session.durationMinutes > 0)
        }

        // Verify idempotency: repeated call returns identical fingerprints
        val repeatSleep = dataSource.fetchSleepSessions(startInstant, endInstant)
        assertEquals(sleepSessions.map { it.recordFingerprint }, repeatSleep.map { it.recordFingerprint })
    }
}
