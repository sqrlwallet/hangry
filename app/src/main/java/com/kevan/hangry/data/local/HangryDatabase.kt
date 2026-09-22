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
        DailyHealthSummaryEntity::class,
        RecoveryScoreEntity::class,
        JournalEntryEntity::class,
        SyncStateEntity::class,
        CalculationMetadataEntity::class,
        DataSourceEntity::class,
        FoodLogEntity::class,
        MealPlanEntity::class,
        PostureScanEntity::class
    ],
    version = 7,
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
    abstract fun dailyHealthSummaryDao(): DailyHealthSummaryDao
    abstract fun recoveryScoreDao(): RecoveryScoreDao
    abstract fun journalDao(): JournalDao
    abstract fun syncStateDao(): SyncStateDao
    abstract fun calculationMetadataDao(): CalculationMetadataDao
    abstract fun dataSourceDao(): DataSourceDao
    abstract fun foodLogDao(): FoodLogDao
    abstract fun mealPlanDao(): MealPlanDao
    abstract fun postureScanDao(): PostureScanDao

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

        fun getDatabase(context: Context): HangryDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    HangryDatabase::class.java,
                    "hangry.db"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
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
