package com.kevan.hangry

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kevan.hangry.data.local.HangryDatabase
import com.kevan.hangry.data.local.entity.DailyHealthSummaryEntity
import com.kevan.hangry.data.local.entity.SleepSessionEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class HangryDatabaseInstrumentedTest {

    private lateinit var db: HangryDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = HangryDatabase.createInMemoryDatabase(context)
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun testSleepSession_insertAndDeduplicationByFingerprint() = runBlocking {
        val now = Instant.now()
        val session1 = SleepSessionEntity(
            recordFingerprint = "fingerprint-abc",
            sourceRecordId = "rec-1",
            sourcePackageName = "com.google.android.apps.fitness",
            startTime = now.minusSeconds(28800),
            endTime = now,
            durationMinutes = 480
        )

        val insertedFirst = db.sleepSessionDao().insertOrIgnore(listOf(session1))
        assertEquals(1, insertedFirst.count { it != -1L })

        // Attempting to insert identical record with same fingerprint must be ignored
        val sessionDuplicate = session1.copy(id = 0)
        val insertedSecond = db.sleepSessionDao().insertOrIgnore(listOf(sessionDuplicate))
        assertEquals("Duplicate fingerprint must be ignored", 0, insertedSecond.count { it != -1L })

        val totalCount = db.sleepSessionDao().getCount()
        assertEquals(1, totalCount)
    }

    @Test
    fun testSleepSession_updateDetails_refreshesAnAlreadySavedNight() = runBlocking {
        val now = Instant.now()
        val original = SleepSessionEntity(
            recordFingerprint = "fingerprint-refresh",
            sourceRecordId = "rec-2",
            sourcePackageName = "com.google.android.apps.fitness",
            startTime = now.minusSeconds(28800),
            endTime = now,
            durationMinutes = 420
        )
        db.sleepSessionDao().insertOrIgnore(listOf(original))

        // Health Connect later extends the same record and adds stages.
        val extended = original.copy(
            endTime = now.plusSeconds(3600),
            durationMinutes = 480,
            deepSleepMinutes = 80,
            stageSegments = "D0,4800"
        )
        db.sleepSessionDao().updateDetails(extended)

        val stored = db.sleepSessionDao().getSessionsBetweenList(now.minusSeconds(40000), now.plusSeconds(40000)).single()
        assertEquals(480, stored.durationMinutes)
        assertEquals(extended.endTime, stored.endTime)
        assertEquals(80, stored.deepSleepMinutes)
        assertEquals("D0,4800", stored.stageSegments)
        assertEquals(1, db.sleepSessionDao().getCount())
    }

    @Test
    fun testSleepSession_updateDetails_leavesManualEntriesAlone() = runBlocking {
        val now = Instant.now()
        val manual = SleepSessionEntity(
            recordFingerprint = "manual-abc",
            startTime = now.minusSeconds(28800),
            endTime = now,
            durationMinutes = 420,
            isManualEntry = true
        )
        db.sleepSessionDao().insertOrIgnore(listOf(manual))

        db.sleepSessionDao().updateDetails(manual.copy(durationMinutes = 10))

        val stored = db.sleepSessionDao().getSessionsBetweenList(now.minusSeconds(40000), now.plusSeconds(40000)).single()
        assertEquals(420, stored.durationMinutes)
    }

    @Test
    fun testDailySummary_insertAndWipeAll() = runBlocking {
        val today = LocalDate.now()
        val summary = DailyHealthSummaryEntity(
            date = today,
            sleepDurationMinutes = 490,
            steps = 9500,
            restingHeartRate = 54.0,
            hrvRmssd = 68.0
        )

        db.dailyHealthSummaryDao().insertOrReplace(summary)

        val fetched = db.dailyHealthSummaryDao().getSummaryForDateSync(today)
        assertNotNull(fetched)
        assertEquals(490, fetched?.sleepDurationMinutes)

        // Delete all
        db.dailyHealthSummaryDao().deleteAll()
        val afterDelete = db.dailyHealthSummaryDao().getSummaryForDateSync(today)
        assertNull("All summaries must be wiped after deleteAll()", afterDelete)
    }
}
