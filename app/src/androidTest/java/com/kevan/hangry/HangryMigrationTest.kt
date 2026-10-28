package com.kevan.hangry

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kevan.hangry.data.local.HangryDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the Room migrations against the exported schemas in app/schemas/.
 *
 * Schema export was only switched on at v33; 28, 30, 31 and 32 were regenerated from the
 * commits that shipped them, so older migrations can't be tested here. From now on, every
 * version bump commits its new N.json (the build writes it) - add a test below that creates
 * the database at N-1, inserts a row the migration must keep, and migrates to N.
 */
@RunWith(AndroidJUnit4::class)
class HangryMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        HangryDatabase::class.java
    )

    private val allMigrations = with(HangryDatabase) {
        arrayOf(
            MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8,
            MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13,
            MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18,
            MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23,
            MIGRATION_23_24, MIGRATION_24_25, MIGRATION_25_26, MIGRATION_26_27, MIGRATION_27_28,
            MIGRATION_28_29, MIGRATION_29_30, MIGRATION_30_31, MIGRATION_31_32, MIGRATION_32_33, MIGRATION_33_34, MIGRATION_34_35, MIGRATION_35_36
        )
    }

    @Test
    fun migrate28To30_addsLongevityAndPrograms_keepsFoodLog() {
        helper.createDatabase(DB, 28).apply {
            insertFood()
            close()
        }
        helper.runMigrationsAndValidate(DB, 30, true, HangryDatabase.MIGRATION_28_29, HangryDatabase.MIGRATION_29_30).use { db ->
            assertEquals(1, db.count("food_log"))
            assertEquals(0, db.count("program_sessions"))
            assertEquals(0, db.count("longevity_check_ins"))
        }
    }

    @Test
    fun migrate30To31_addsStageSegments_keepsSleep() {
        helper.createDatabase(DB, 30).apply {
            execSQL(
                "INSERT INTO sleep_sessions (recordFingerprint, startTime, endTime, durationMinutes, isManualEntry, dataQualityState, importTimestamp) " +
                    "VALUES ('fp', 0, 28800000, 480, 1, 'COMPLETE', 0)"
            )
            close()
        }
        helper.runMigrationsAndValidate(DB, 31, true, HangryDatabase.MIGRATION_30_31).use { db ->
            db.query("SELECT durationMinutes, stageSegments FROM sleep_sessions").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(480, c.getInt(0))
                assertTrue(c.isNull(1))
            }
        }
    }

    @Test
    fun migrate31To32_addsBodySignalColumns() {
        helper.createDatabase(DB, 31).close()
        helper.runMigrationsAndValidate(DB, 32, true, HangryDatabase.MIGRATION_31_32).close()
    }

    @Test
    fun migrate32To33_groupsOldMessagesIntoOneChat() {
        helper.createDatabase(DB, 32).apply {
            execSQL("INSERT INTO coach_messages (timestamp, role, content) VALUES (1000, 'user', 'How did I sleep?')")
            execSQL("INSERT INTO coach_messages (timestamp, role, content) VALUES (2000, 'assistant', 'Well.')")
            close()
        }
        helper.runMigrationsAndValidate(DB, 33, true, HangryDatabase.MIGRATION_32_33).use { db ->
            db.query("SELECT id, title, createdAt, updatedAt FROM coach_conversations").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("How did I sleep?", c.getString(1))
                assertEquals(1000L, c.getLong(2))
                assertEquals(2000L, c.getLong(3))
                val chatId = c.getLong(0)
                assertEquals(2, db.count("coach_messages WHERE conversationId = $chatId"))
            }
        }
    }

    @Test
    fun migrate32To33_withNoMessages_createsNoChat() {
        helper.createDatabase(DB, 32).close()
        helper.runMigrationsAndValidate(DB, 33, true, HangryDatabase.MIGRATION_32_33).use { db ->
            assertEquals(0, db.count("coach_conversations"))
        }
    }

    @Test
    fun migrate33To34_addsWaterLog_keepsFoodLog() {
        helper.createDatabase(DB, 33).apply {
            insertFood()
            close()
        }
        helper.runMigrationsAndValidate(DB, 34, true, HangryDatabase.MIGRATION_33_34).use { db ->
            assertEquals(1, db.count("food_log"))
            assertEquals(0, db.count("water_log"))
            db.execSQL("INSERT INTO water_log (date, timestamp, volumeMl, healthConnectSynced) VALUES (19000, 0, 250, 0)")
            assertEquals(1, db.count("water_log WHERE date = 19000"))
        }
    }

    @Test
    fun migrate34To35_indexesHrvTimestamp_keepsFoodLog() {
        helper.createDatabase(DB, 34).apply {
            insertFood()
            close()
        }
        helper.runMigrationsAndValidate(DB, 35, true, HangryDatabase.MIGRATION_34_35).use { db ->
            assertEquals(1, db.count("food_log"))
            assertEquals(1, db.count("sqlite_master WHERE type = 'index' AND name = 'index_hrv_measurements_timestamp'"))
        }
    }

    @Test
    fun migrate35To36_addsQueryIndexes_keepsFoodLog() {
        helper.createDatabase(DB, 35).apply {
            insertFood()
            close()
        }
        helper.runMigrationsAndValidate(DB, 36, true, HangryDatabase.MIGRATION_35_36).use { db ->
            assertEquals(1, db.count("food_log"))
            listOf(
                "index_heart_rate_samples_timestamp_bpm", "index_sleep_sessions_endTime",
                "index_food_log_source_timestamp", "index_resting_heart_rate_timestamp"
            ).forEach { assertEquals(it, 1, db.count("sqlite_master WHERE type = 'index' AND name = '$it'")) }
            listOf("index_heart_rate_samples_timestamp", "index_food_log_source")
                .forEach { assertEquals(it, 0, db.count("sqlite_master WHERE type = 'index' AND name = '$it'")) }
        }
    }

    /** The full chain from the oldest exported schema opens with Room and keeps the user's data. */
    @Test
    fun migrate28ToLatest_opensWithRoom() {
        helper.createDatabase(DB, 28).apply {
            insertFood()
            close()
        }
        val db = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), HangryDatabase::class.java, DB)
            .addMigrations(*allMigrations)
            .build()
        try {
            val sqlite = db.openHelper.writableDatabase
            assertEquals(36, sqlite.version)
            assertEquals(1, sqlite.count("food_log"))
        } finally {
            db.close()
        }
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.insertFood() {
        execSQL(
            "INSERT INTO food_log (date, timestamp, source, foodName, calories, proteinG, carbsG, fatG, fiberG, sugarG, sodiumMg, healthConnectSynced) " +
                "VALUES (19000, 0, 'MANUAL', 'Oats', 300, 10, 50, 5, 8, 1, 0, 0)"
        )
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.count(tableAndWhere: String): Int =
        query("SELECT COUNT(*) FROM $tableAndWhere").use { c -> c.moveToFirst(); c.getInt(0) }

    private companion object {
        const val DB = "migration-test.db"
    }
}
