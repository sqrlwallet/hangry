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
