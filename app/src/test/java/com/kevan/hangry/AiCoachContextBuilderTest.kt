package com.kevan.hangry

import com.kevan.hangry.data.local.dao.*
import com.kevan.hangry.data.local.entity.*
import com.kevan.hangry.domain.ai.AiCoachContextBuilder
import com.kevan.hangry.domain.repository.UserProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class AiCoachContextBuilderTest {

    private class FakeUserProfileRepository(var profile: UserProfileEntity? = null) : UserProfileRepository {
        override fun getProfile(): Flow<UserProfileEntity?> = flowOf(profile)
        override suspend fun getProfileSync(): UserProfileEntity? = profile
        override suspend fun saveProfile(profile: UserProfileEntity) {
            this.profile = profile
        }
    }

    private class FakeWeightDao(var latest: WeightMeasurementEntity? = null) : WeightDao {
        override suspend fun insertOrIgnore(records: List<WeightMeasurementEntity>): List<Long> = emptyList()
        override fun getLatestWeight(): Flow<WeightMeasurementEntity?> = flowOf(latest)
        override suspend fun getLatestWeightSync(): WeightMeasurementEntity? = latest
        override fun getAllWeights(): Flow<List<WeightMeasurementEntity>> = flowOf(emptyList())
        override fun getRollingAverageWeight(): Flow<Double?> = flowOf(null)
        override suspend fun getCount(): Int = 0
        override suspend fun deleteAll() {}
    }

    private class FakeDailyHealthSummaryDao(var summaries: List<DailyHealthSummaryEntity> = emptyList()) : DailyHealthSummaryDao {
        override suspend fun insertOrReplace(summary: DailyHealthSummaryEntity) {}
        override suspend fun insertOrReplaceAll(summaries: List<DailyHealthSummaryEntity>) {}
        override fun getSummaryForDate(date: LocalDate): Flow<DailyHealthSummaryEntity?> = flowOf(null)
        override suspend fun getSummaryForDateSync(date: LocalDate): DailyHealthSummaryEntity? = null
        override fun getSummariesBetween(start: LocalDate, end: LocalDate): Flow<List<DailyHealthSummaryEntity>> = flowOf(summaries)
        override fun getAllSummaries(): Flow<List<DailyHealthSummaryEntity>> = flowOf(summaries)
        override suspend fun getSummariesBetweenList(start: LocalDate, end: LocalDate): List<DailyHealthSummaryEntity> = summaries
        override fun getLatestSummary(): Flow<DailyHealthSummaryEntity?> = flowOf(null)
        override suspend fun getOldestSummary(): DailyHealthSummaryEntity? = null
        override suspend fun getCount(): Int = summaries.size
        override suspend fun deleteForDate(date: LocalDate) {}
        override suspend fun deleteAll() {}
    }

    private class FakeRecoveryScoreDao(var scores: List<RecoveryScoreEntity> = emptyList()) : RecoveryScoreDao {
        override suspend fun insertOrReplace(score: RecoveryScoreEntity) {}
        override suspend fun insertOrReplaceAll(scores: List<RecoveryScoreEntity>) {}
        override fun getScoreForDate(date: LocalDate): Flow<RecoveryScoreEntity?> = flowOf(null)
        override suspend fun getScoreForDateSync(date: LocalDate): RecoveryScoreEntity? = null
        override fun getLatestScore(): Flow<RecoveryScoreEntity?> = flowOf(null)
        override suspend fun getLatestScoreSync(): RecoveryScoreEntity? = null
        override fun getScoresBetween(start: LocalDate, end: LocalDate): Flow<List<RecoveryScoreEntity>> = flowOf(scores)
        override fun getAllScores(): Flow<List<RecoveryScoreEntity>> = flowOf(scores)
        override suspend fun getScoresBetweenList(start: LocalDate, end: LocalDate): List<RecoveryScoreEntity> = scores
        override suspend fun deleteForDate(date: LocalDate) {}
        override suspend fun deleteAll() {}
    }

    private class FakeExerciseSessionDao(var workouts: List<ExerciseSessionEntity> = emptyList()) : ExerciseSessionDao {
        override suspend fun insertOrIgnore(sessions: List<ExerciseSessionEntity>): List<Long> = emptyList()
        override suspend fun backfillSteps(fingerprint: String, steps: Long) {}
        override fun getSessionsBetween(start: Instant, end: Instant): Flow<List<ExerciseSessionEntity>> = flowOf(workouts)
        override suspend fun getSessionsBetweenList(start: Instant, end: Instant): List<ExerciseSessionEntity> = workouts
        override fun getAllSessions(): Flow<List<ExerciseSessionEntity>> = flowOf(workouts)
        override suspend fun getCount(): Int = workouts.size
        override suspend fun getOldestSession(): ExerciseSessionEntity? = null
        override suspend fun deleteAll() {}
    }

    private class FakeSleepSessionDao(var sleepSessions: List<SleepSessionEntity> = emptyList()) : SleepSessionDao {
        override suspend fun insertOrIgnore(sessions: List<SleepSessionEntity>): List<Long> = emptyList()
        override fun getSessionsBetween(start: Instant, end: Instant): Flow<List<SleepSessionEntity>> = flowOf(sleepSessions)
        override suspend fun getSessionsBetweenList(start: Instant, end: Instant): List<SleepSessionEntity> = sleepSessions
        override fun getLatestSession(): Flow<SleepSessionEntity?> = flowOf(null)
        override suspend fun getOldestSession(): SleepSessionEntity? = null
        override suspend fun getCount(): Int = sleepSessions.size
        override suspend fun deleteAll() {}
    }

    private class FakeFoodLogDao(var foodLogs: List<FoodLogEntity> = emptyList()) : FoodLogDao {
        override suspend fun insert(entry: FoodLogEntity): Long = 1L
        override suspend fun insertOrIgnore(entries: List<FoodLogEntity>): List<Long> = entries.map { 1L }
        override suspend fun update(entry: FoodLogEntity) {}
        override suspend fun delete(entry: FoodLogEntity) {}
        override fun getForDate(date: LocalDate): Flow<List<FoodLogEntity>> = flowOf(foodLogs)
        override fun getBetween(start: LocalDate, end: LocalDate): Flow<List<FoodLogEntity>> = flowOf(foodLogs)
        override suspend fun getBetweenList(start: LocalDate, end: LocalDate): List<FoodLogEntity> = foodLogs
        override fun getTotalCaloriesForDate(date: LocalDate): Flow<Int> = flowOf(0)
        override suspend fun markSyncedToHealthConnect(id: Long) {}
        override suspend fun getAllPhotoPaths(): List<String> = foodLogs.mapNotNull { it.photoPath }
        override suspend fun deleteBySource(source: String) {}
        override suspend fun deleteAll() {}
    }

    private class FakePostureScanDao(var latest: PostureScanEntity? = null) : PostureScanDao {
        override suspend fun insert(scan: PostureScanEntity): Long = 1L
        override suspend fun delete(scan: PostureScanEntity) {}
        override fun getAll(): Flow<List<PostureScanEntity>> = flowOf(emptyList())
        override fun getLatest(): Flow<PostureScanEntity?> = flowOf(latest)
        override suspend fun getLatestSync(): PostureScanEntity? = latest
        override suspend fun getById(id: Long): PostureScanEntity? = latest
        override suspend fun getAllPhotoPathsJson(): List<String> = listOfNotNull(latest?.photoPathsJson)
        override suspend fun deleteAll() {}
    }

    private class FakeCoachJournalDao(var entries: List<CoachJournalEntity> = emptyList()) : CoachJournalDao {
        override suspend fun insert(entry: CoachJournalEntity): Long = 1L
        override suspend fun delete(id: Long) {}
        override fun getAll(): Flow<List<CoachJournalEntity>> = flowOf(entries)
        override suspend fun getAllSync(): List<CoachJournalEntity> = entries
        override suspend fun deleteAll() {}
    }

    @Test
    fun build7DayContext_withEmptyData_buildsGracefully() = runBlocking {
        val builder = AiCoachContextBuilder(
            userProfileRepository = FakeUserProfileRepository(),
            weightDao = FakeWeightDao(),
            dailyHealthSummaryDao = FakeDailyHealthSummaryDao(),
            recoveryScoreDao = FakeRecoveryScoreDao(),
            exerciseSessionDao = FakeExerciseSessionDao(),
            sleepSessionDao = FakeSleepSessionDao(),
            foodLogDao = FakeFoodLogDao(),
            postureScanDao = FakePostureScanDao(),
            coachJournalDao = FakeCoachJournalDao()
        )

        val context = builder.build7DayContext()
        assertTrue(context.contains("=== USER PROFILE ==="))
        assertTrue(context.contains("=== USER'S PERSONAL JOURNAL & KNOWN PROBLEMS / MEMORIES ==="))
        assertTrue(context.contains("No past journal entries recorded yet."))
        assertTrue(context.contains("=== 7-DAY DAILY HEALTH SUMMARIES"))
        assertTrue(context.contains("=== 7-DAY WORKOUTS & EXERCISE SESSIONS ==="))
        assertTrue(context.contains("=== 7-DAY NUTRITION & FOOD LOGS ==="))
    }

    @Test
    fun build7DayContext_withFullData_includesAllKeyDetails() = runBlocking {
        val today = LocalDate.now()
        val userProfile = UserProfileEntity(
            biologicalSex = "MALE",
            age = 30,
            heightCm = 182.0,
            dailyStepGoal = 10000,
            dailyActivityMinutesGoal = 60,
            dailyActiveCaloriesGoal = 600
        )
        val weight = WeightMeasurementEntity(
            recordFingerprint = "w1",
            timestamp = Instant.now(),
            weightKg = 81.5
        )
        val summary = DailyHealthSummaryEntity(
            date = today,
            steps = 9500L,
            activeCalories = 550.0,
            dayStrain = 14.2,
            sleepDurationMinutes = 450,
            restingHeartRate = 54.0,
            hrvRmssd = 65.0
        )
        val recovery = RecoveryScoreEntity(
            date = today,
            score = 85,
            confidence = "HIGH",
            state = "PRIMED",
            supportiveAdvice = "Well recovered"
        )
        val workout = ExerciseSessionEntity(
            recordFingerprint = "ex1",
            exerciseType = "RUNNING",
            title = "Morning Trail Run",
            startTime = Instant.now().minusSeconds(3600),
            endTime = Instant.now(),
            durationMinutes = 45,
            activeCalories = 420.0,
            estimatedTrainingLoad = 55.0
        )
        val food = FoodLogEntity(
            date = today,
            timestamp = Instant.now(),
            source = FoodLogSource.MANUAL,
            foodName = "Grilled Salmon and Quinoa",
            calories = 650,
            proteinG = 45.0,
            carbsG = 50.0,
            fatG = 22.0,
            fiberG = 6.0,
            sugarG = 2.0,
            sodiumMg = 350.0,
            healthConnectSynced = false
        )
        val journal = CoachJournalEntity(
            date = today.minusDays(2),
            category = "PROBLEM",
            summary = "Left knee soreness on lunges",
            content = "User reported mild pain in left patellar tendon."
        )

        val builder = AiCoachContextBuilder(
            userProfileRepository = FakeUserProfileRepository(userProfile),
            weightDao = FakeWeightDao(weight),
            dailyHealthSummaryDao = FakeDailyHealthSummaryDao(listOf(summary)),
            recoveryScoreDao = FakeRecoveryScoreDao(listOf(recovery)),
            exerciseSessionDao = FakeExerciseSessionDao(listOf(workout)),
            sleepSessionDao = FakeSleepSessionDao(),
            foodLogDao = FakeFoodLogDao(listOf(food)),
            postureScanDao = FakePostureScanDao(),
            coachJournalDao = FakeCoachJournalDao(listOf(journal))
        )

        val context = builder.build7DayContext()
        assertTrue("Contains profile data", context.contains("81.5 kg"))
        assertTrue("Contains steps goal", context.contains("10000"))
        assertTrue("Contains journal summary", context.contains("Left knee soreness on lunges"))
        assertTrue("Contains recovery score", context.contains("85%"))
        assertTrue("Contains workout title", context.contains("Morning Trail Run"))
        assertTrue("Contains food name", context.contains("Grilled Salmon and Quinoa"))
        assertTrue("Contains calorie total", context.contains("650 kcal"))
    }
}
