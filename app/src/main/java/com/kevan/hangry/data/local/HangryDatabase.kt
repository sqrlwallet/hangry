package com.kevan.hangry.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.kevan.hangry.data.local.converter.DateConverters
import com.kevan.hangry.data.local.dao.*
import com.kevan.hangry.data.local.entity.*

@Database(
    entities = [
        UserProfileEntity::class,
        SleepSessionEntity::class,
        ExerciseSessionEntity::class,
        HeartRateSampleEntity::class,
        RestingHeartRateEntity::class,
        HrvMeasurementEntity::class,
        StepsSummaryEntity::class,
        WeightMeasurementEntity::class,
        HeightMeasurementEntity::class,
        DailyHealthSummaryEntity::class,
        RecoveryScoreEntity::class,
        SyncStateEntity::class,
        CalculationMetadataEntity::class,
        DataSourceEntity::class,
        FoodLogEntity::class,
        MealPlanEntity::class,
        PostureScanEntity::class,
        CoachJournalEntity::class,
        CoachMessageEntity::class,
        BodyFatScanEntity::class,
        BreathingSessionEntity::class
    ],
    version = 14,
    exportSchema = false
)
@TypeConverters(DateConverters::class)
abstract class HangryDatabase : RoomDatabase() {

    abstract fun userProfileDao(): UserProfileDao
    abstract fun sleepSessionDao(): SleepSessionDao
    abstract fun exerciseSessionDao(): ExerciseSessionDao
    abstract fun heartRateDao(): HeartRateDao
    abstract fun restingHeartRateDao(): RestingHeartRateDao
    abstract fun hrvDao(): HrvDao
    abstract fun stepsDao(): StepsDao
    abstract fun weightDao(): WeightDao
    abstract fun heightDao(): HeightDao
    abstract fun dailyHealthSummaryDao(): DailyHealthSummaryDao
    abstract fun recoveryScoreDao(): RecoveryScoreDao
    abstract fun syncStateDao(): SyncStateDao
    abstract fun calculationMetadataDao(): CalculationMetadataDao
    abstract fun dataSourceDao(): DataSourceDao
    abstract fun foodLogDao(): FoodLogDao
    abstract fun mealPlanDao(): MealPlanDao
    abstract fun postureScanDao(): PostureScanDao
    abstract fun coachJournalDao(): CoachJournalDao
    abstract fun coachMessageDao(): CoachMessageDao
    abstract fun bodyFatScanDao(): BodyFatScanDao
    abstract fun breathingSessionDao(): BreathingSessionDao

    open fun checkpointAndOptimize() {
        openHelper.writableDatabase.let { db ->
            runCatching { db.query("PRAGMA wal_checkpoint(TRUNCATE)").close() }
            runCatching { db.query("PRAGMA incremental_vacuum").close() }
            runCatching { db.query("PRAGMA optimize").close() }
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: HangryDatabase? = null

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE daily_health_summaries ADD COLUMN dayStrain REAL")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE daily_health_summaries ADD COLUMN bmrCalories REAL")
                db.execSQL("ALTER TABLE user_profile ADD COLUMN age INTEGER")
                db.execSQL("ALTER TABLE user_profile ADD COLUMN biologicalSex TEXT")
                db.execSQL("ALTER TABLE user_profile ADD COLUMN weightGoalKg REAL")
                db.execSQL("ALTER TABLE user_profile ADD COLUMN goalTargetDate INTEGER")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // AI feature opt-in flag + preferred model - the API key itself lives outside
                // Room entirely, see data/security/SecureKeyStore.
                db.execSQL("ALTER TABLE user_profile ADD COLUMN aiFeaturesEnabled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE user_profile ADD COLUMN preferredAiModel TEXT")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS food_log (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        date INTEGER NOT NULL,
                        timestamp INTEGER NOT NULL,
                        source TEXT NOT NULL,
                        foodName TEXT NOT NULL,
                        calories INTEGER NOT NULL,
                        proteinG REAL NOT NULL,
                        carbsG REAL NOT NULL,
                        fatG REAL NOT NULL,
                        fiberG REAL NOT NULL,
                        sugarG REAL NOT NULL,
                        sodiumMg REAL NOT NULL,
                        photoPath TEXT,
                        healthConnectSynced INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_food_log_date ON food_log(date)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS meal_plan (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        mealType TEXT NOT NULL,
                        calories INTEGER NOT NULL,
                        proteinG REAL NOT NULL,
                        carbsG REAL NOT NULL,
                        fatG REAL NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS posture_scans (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        date INTEGER NOT NULL,
                        timestamp INTEGER NOT NULL,
                        score INTEGER NOT NULL,
                        findingsJson TEXT NOT NULL,
                        exercisesJson TEXT NOT NULL,
                        photoPathsJson TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_posture_scans_date ON posture_scans(date)")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE user_profile ADD COLUMN dailyStepGoal INTEGER NOT NULL DEFAULT 6000")
                db.execSQL("ALTER TABLE user_profile ADD COLUMN dailyActivityMinutesGoal INTEGER NOT NULL DEFAULT 90")
                db.execSQL("ALTER TABLE user_profile ADD COLUMN dailyActiveCaloriesGoal INTEGER NOT NULL DEFAULT 500")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE daily_health_summaries ADD COLUMN vo2Max REAL")
                db.execSQL("ALTER TABLE daily_health_summaries ADD COLUMN spo2Percentage REAL")
                db.execSQL("ALTER TABLE daily_health_summaries ADD COLUMN respiratoryRate REAL")
                db.execSQL("ALTER TABLE daily_health_summaries ADD COLUMN bloodPressureSystolic REAL")
                db.execSQL("ALTER TABLE daily_health_summaries ADD COLUMN bloodPressureDiastolic REAL")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Journal (subjective energy/soreness logging) was removed as a feature.
                db.execSQL("DROP TABLE IF EXISTS journal_entries")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS height_measurements (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        sourceRecordId TEXT,
                        sourcePackageName TEXT,
                        recordFingerprint TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        heightCm REAL NOT NULL,
                        dataQualityState TEXT NOT NULL,
                        importTimestamp INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_height_measurements_recordFingerprint ON height_measurements(recordFingerprint)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_height_measurements_timestamp ON height_measurements(timestamp)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_height_measurements_sourceRecordId ON height_measurements(sourceRecordId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_height_measurements_sourcePackageName ON height_measurements(sourcePackageName)")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS coach_journal_entries (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        date INTEGER NOT NULL,
                        timestamp INTEGER NOT NULL,
                        category TEXT NOT NULL,
                        summary TEXT NOT NULL,
                        content TEXT NOT NULL,
                        sourceMessage TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_coach_journal_entries_date ON coach_journal_entries(date)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS coach_messages (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        role TEXT NOT NULL,
                        content TEXT NOT NULL,
                        journalEntrySummary TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_coach_messages_timestamp ON coach_messages(timestamp)")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE food_log ADD COLUMN sourceRecordId TEXT")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_food_log_sourceRecordId ON food_log(sourceRecordId)")
                db.execSQL("ALTER TABLE daily_health_summaries ADD COLUMN hydrationLiters REAL")
                db.execSQL("ALTER TABLE daily_health_summaries ADD COLUMN bodyFatPercentage REAL")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE user_profile ADD COLUMN currentWeightKg REAL")
                db.execSQL("ALTER TABLE user_profile ADD COLUMN neckCircumferenceCm REAL")
                db.execSQL("ALTER TABLE user_profile ADD COLUMN chestCircumferenceCm REAL")
                db.execSQL("ALTER TABLE user_profile ADD COLUMN waistCircumferenceCm REAL")
                db.execSQL("ALTER TABLE user_profile ADD COLUMN hipCircumferenceCm REAL")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS body_fat_scans (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        date INTEGER NOT NULL,
                        timestamp INTEGER NOT NULL,
                        bodyFatPercentage REAL NOT NULL,
                        confidenceMin REAL,
                        confidenceMax REAL,
                        category TEXT NOT NULL,
                        method TEXT NOT NULL,
                        weightKg REAL,
                        neckCm REAL,
                        chestCm REAL,
                        waistCm REAL,
                        hipCm REAL,
                        leanMassKg REAL,
                        fatMassKg REAL,
                        visualObservationsJson TEXT NOT NULL,
                        healthInsightsJson TEXT NOT NULL,
                        consistencyNote TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_body_fat_scans_date ON body_fat_scans(date)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_body_fat_scans_timestamp ON body_fat_scans(timestamp)")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_food_log_date_timestamp ON food_log(date, timestamp)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_food_log_source ON food_log(source)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_meal_plan_createdAt ON meal_plan(createdAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_coach_journal_entries_date_timestamp ON coach_journal_entries(date, timestamp)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_coach_journal_entries_category ON coach_journal_entries(category)")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE user_profile ADD COLUMN preferredCoachModel TEXT")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS breathing_sessions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        date INTEGER NOT NULL,
                        startTime INTEGER NOT NULL,
                        endTime INTEGER NOT NULL,
                        patternId TEXT NOT NULL,
                        durationSeconds INTEGER NOT NULL,
                        cyclesCompleted INTEGER NOT NULL,
                        completed INTEGER NOT NULL,
                        healthConnectSynced INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_breathing_sessions_date ON breathing_sessions(date)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_breathing_sessions_startTime ON breathing_sessions(startTime)")
            }
        }

        fun getDatabase(context: Context): HangryDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    HangryDatabase::class.java,
                    "hangry.db"
                )
                    .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                    .addMigrations(
                        MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
                        MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10,
                        MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14
                    )
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            runCatching { db.query("PRAGMA synchronous = NORMAL").close() }
                            runCatching { db.query("PRAGMA busy_timeout = 6000").close() }
                            runCatching { db.query("PRAGMA foreign_keys = ON").close() }
                            runCatching { db.query("PRAGMA cache_size = -8000").close() }
                            runCatching { db.query("PRAGMA temp_store = MEMORY").close() }
                        }
                    })
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        fun createInMemoryDatabase(context: Context): HangryDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                HangryDatabase::class.java
            ).allowMainThreadQueries().build()
        }
    }
}
