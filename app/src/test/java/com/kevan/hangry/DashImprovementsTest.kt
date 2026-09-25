package com.kevan.hangry

import com.kevan.hangry.data.coach.CoachActionExecutor
import com.kevan.hangry.data.local.dao.CoachJournalDao
import com.kevan.hangry.data.local.dao.ExerciseSessionDao
import com.kevan.hangry.data.local.dao.WeightDao
import com.kevan.hangry.data.local.entity.CoachJournalEntity
import com.kevan.hangry.data.local.entity.DailyHealthSummaryEntity
import com.kevan.hangry.data.local.entity.ExerciseSessionEntity
import com.kevan.hangry.data.local.entity.UserProfileEntity
import com.kevan.hangry.data.local.entity.WeightMeasurementEntity
import com.kevan.hangry.domain.ai.DashInsights
import com.kevan.hangry.domain.model.CoachAction
import com.kevan.hangry.domain.model.GoalsActionPayload
import com.kevan.hangry.domain.model.ResolveJournalActionPayload
import com.kevan.hangry.domain.model.Supplement
import com.kevan.hangry.domain.model.SupplementDose
import com.kevan.hangry.domain.model.SupplementsSnapshot
import com.kevan.hangry.domain.model.WeightActionPayload
import com.kevan.hangry.domain.model.WorkoutActionPayload
import com.kevan.hangry.domain.repository.UserProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

class DashImprovementsTest {

    private val today = LocalDate.of(2026, 9, 23)

    @Test
    fun `trends compare this week with the three weeks before`() {
        val summaries = (1L..28L).map { offset ->
            // Resting HR 60 in the baseline weeks, 64 this week.
            DailyHealthSummaryEntity(date = today.minusDays(offset), restingHeartRate = if (offset <= 7) 64.0 else 60.0)
        }
        val lines = DashInsights.trendLines(summaries, emptyMap(), emptyList(), today, ZoneOffset.UTC)
        val rhr = lines.single { it.contains("Resting HR") }
        assertTrue(rhr, rhr.contains("64 bpm this week vs 60 bpm") && rhr.contains("up (worse)"))
    }

    @Test
    fun `trends need data on both sides`() {
        val summaries = (1L..4L).map { DailyHealthSummaryEntity(date = today.minusDays(it), steps = 9000) }
        assertTrue(DashInsights.trendLines(summaries, emptyMap(), emptyList(), today).isEmpty())
    }

    @Test
    fun `weight change over the month is reported`() {
        val weights = listOf(
            WeightMeasurementEntity(recordFingerprint = "a", timestamp = today.minusDays(20).atStartOfDay().toInstant(ZoneOffset.UTC), weightKg = 82.0),
            WeightMeasurementEntity(recordFingerprint = "b", timestamp = today.minusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC), weightKg = 80.5)
        )
        val line = DashInsights.trendLines(emptyList(), emptyMap(), weights, today, ZoneOffset.UTC).single()
        assertTrue(line, line.contains("-1.5 kg"))
    }

    @Test
    fun `an overdue supplement dose becomes the first suggestion`() {
        val magnesium = Supplement(1, "Magnesium", null, null, 2.0, "capsules", listOf(LocalTime.of(8, 0)), emptyList(), true, true, null, null, true)
        val snapshot = SupplementsSnapshot(supplements = listOf(magnesium), todayDoses = listOf(SupplementDose(magnesium, LocalTime.of(8, 0), taken = false)))
        val chips = DashInsights.suggestions(snapshot, null, null, now = LocalTime.of(12, 0))
        assertTrue(chips.first().contains("Magnesium"))
        assertEquals(5, chips.size)
    }

    private val profiles = FakeProfiles()
    private val weights = FakeWeightDao()
    private val exerciseSessions = FakeExerciseSessionDao()
    private val coachJournals = FakeCoachJournalDao()
    private val executor = CoachActionExecutor(
        supplementRepository = FakeSupplementRepository(),
        healthRecordsRepository = FakeHealthRecordsRepository(),
        foodLogRepository = FakeFoodLogRepository(),
        writeNutritionRecord = { true },
        userProfileRepository = profiles,
        weightDao = weights,
        exerciseSessionDao = exerciseSessions,
        coachJournalDao = coachJournals
    )

    @Test
    fun `weight in pounds is logged in kilograms`() = runTest {
        val result = executor.execute(CoachAction(type = CoachAction.LOG_WEIGHT, weight = WeightActionPayload(176.0, "lb")))
        assertTrue(result.isSuccess)
        assertEquals(79.8, weights.inserted.single().weightKg, 0.1)
        assertEquals(79.8, profiles.flow.value!!.currentWeightKg!!, 0.1)
    }

    @Test
    fun `goal update only changes what was given and validates it`() = runTest {
        executor.execute(CoachAction(type = CoachAction.UPDATE_GOALS, goals = GoalsActionPayload(dailySteps = 10_000)))
        assertEquals(10_000L, profiles.flow.value!!.dailyStepGoal)
        assertEquals(500, profiles.flow.value!!.dailyActiveCaloriesGoal)

        val bad = executor.execute(CoachAction(type = CoachAction.UPDATE_GOALS, goals = GoalsActionPayload(sleepHours = 20.0)))
        assertTrue(bad.isFailure)
    }

    @Test
    fun `open screen only accepts known screens`() = runTest {
        assertTrue(executor.execute(CoachAction(type = CoachAction.OPEN_SCREEN, screen = "breathing")).isSuccess)
        assertTrue(executor.execute(CoachAction(type = CoachAction.OPEN_SCREEN, screen = "bank_details")).isFailure)
    }

    @Test
    fun `every action is documented for Dash and handled by the executor`() = runTest {
        val guide = com.kevan.hangry.data.ai.DASH_ACTION_GUIDE
        CoachAction.ALL.forEach { type ->
            assertTrue("$type missing from Dash's action guide", guide.contains("• $type:"))
            val result = executor.execute(CoachAction(type = type))
            assertTrue("$type isn't handled", result.exceptionOrNull()?.message != "Hangry can't do that yet.")
        }
    }

    @Test
    fun `pregnancy and periods are refused unless sex is female`() = runTest {
        profiles.flow.value = UserProfileEntity(biologicalSex = "MALE")
        val result = executor.execute(
            CoachAction(type = CoachAction.SET_PREGNANCY, pregnancy = com.kevan.hangry.domain.model.PregnancyActionPayload(true))
        )
        assertTrue(result.isFailure)
    }

    @Test
    fun `profile measurements in inches are stored in centimetres`() = runTest {
        executor.execute(
            CoachAction(type = CoachAction.UPDATE_PROFILE, profile = com.kevan.hangry.domain.model.ProfileActionPayload(waist = 34.0, lengthUnit = "in"))
        )
        assertEquals(86.4, profiles.flow.value!!.waistCircumferenceCm!!, 0.1)
    }

    @Test
    fun `dash moods cover workout and nutrition with valid resources`() {
        com.kevan.hangry.ui.coach.DashMood.entries.forEach { mood ->
            assertTrue(mood.name, mood.imageRes != 0)
            assertTrue(mood.description, mood.description.contains(com.kevan.hangry.ui.coach.MASCOT_NAME))
        }
        assertTrue(com.kevan.hangry.ui.coach.DashMood.WORKOUT.popsIn)
    }

    @Test
    fun `logging a workout saves session with type, duration, calories, distance and notes`() = runTest {
        val result = executor.execute(
            CoachAction(
                type = CoachAction.LOG_WORKOUT,
                workout = WorkoutActionPayload(
                    exerciseType = "RUNNING",
                    durationMinutes = 45,
                    calories = 420.0,
                    distanceKm = 5.2,
                    notes = "Morning tempo run"
                )
            )
        )
        assertTrue(result.isSuccess)
        val saved = exerciseSessions.inserted.single()
        assertEquals("RUNNING", saved.exerciseType)
        assertEquals(45, saved.durationMinutes)
        assertEquals(420.0, saved.totalCalories!!, 0.1)
        assertEquals(5200.0, saved.distanceMeters!!, 1.0)
        assertEquals("Morning tempo run", saved.notes)
    }

    @Test
    fun `resolving a journal entry removes matching memory`() = runTest {
        val entry = CoachJournalEntity(
            id = 42L,
            date = today,
            category = "INJURY",
            summary = "Left knee pain after squats",
            content = "User reported patellar tendon irritation."
        )
        coachJournals.entries.add(entry)
        val result = executor.execute(
            CoachAction(
                type = CoachAction.RESOLVE_JOURNAL_ENTRY,
                resolveJournal = ResolveJournalActionPayload(
                    entryId = 42L
                )
            )
        )
        assertTrue(result.isSuccess)
        assertTrue(coachJournals.entries.isEmpty())
    }

    private class FakeProfiles : UserProfileRepository {
        val flow = MutableStateFlow<UserProfileEntity?>(UserProfileEntity())
        override fun getProfile(): Flow<UserProfileEntity?> = flow
        override suspend fun getProfileSync(): UserProfileEntity? = flow.value
        override suspend fun saveProfile(profile: UserProfileEntity) { flow.value = profile }
    }

    private class FakeWeightDao : WeightDao {
        val inserted = mutableListOf<WeightMeasurementEntity>()
        override suspend fun insertOrIgnore(records: List<WeightMeasurementEntity>): List<Long> { inserted += records; return records.map { 1L } }
        override fun getLatestWeight(): Flow<WeightMeasurementEntity?> = flowOf(inserted.lastOrNull())
        override suspend fun getLatestWeightSync(): WeightMeasurementEntity? = inserted.lastOrNull()
        override fun getAllWeights(): Flow<List<WeightMeasurementEntity>> = flowOf(inserted)
        override fun getRollingAverageWeight(): Flow<Double?> = flowOf(null)
        override suspend fun getCount(): Int = inserted.size
        override suspend fun deleteAll() { inserted.clear() }
        override suspend fun getImportedFingerprintsBetween(start: java.time.Instant, end: java.time.Instant): List<String> = emptyList()
        override suspend fun deleteByFingerprints(fingerprints: List<String>) {}
    }

    private class FakeExerciseSessionDao : ExerciseSessionDao {
        val inserted = mutableListOf<ExerciseSessionEntity>()
        override suspend fun insertOrIgnore(sessions: List<ExerciseSessionEntity>): List<Long> { inserted += sessions; return sessions.map { 1L } }
        override suspend fun updateDetails(
            fingerprint: String, exerciseType: String, title: String?, notes: String?,
            activeCalories: Double?, totalCalories: Double?, steps: Long?,
            distanceMeters: Double?, elevationGainMeters: Double?, avgPowerWatts: Double?,
            setCount: Int?, repCount: Int?, segmentSummary: String?, lapCount: Int?, detailVersion: Int
        ) {}
        override suspend fun updateHeartRate(fingerprint: String, avg: Double?, max: Double?) {}
        override suspend fun markDetailVersion(version: Int) {}
        override suspend fun getOldestStartNeedingDetails(version: Int): Instant? = null
        override fun getSessionsBetween(start: Instant, end: Instant): Flow<List<ExerciseSessionEntity>> = flowOf(inserted)
        override suspend fun getSessionsBetweenList(start: Instant, end: Instant): List<ExerciseSessionEntity> = inserted
        override fun getAllSessions(): Flow<List<ExerciseSessionEntity>> = flowOf(inserted)
        override suspend fun getCount(): Int = inserted.size
        override suspend fun getOldestSession(): ExerciseSessionEntity? = null
        override suspend fun deleteAll() { inserted.clear() }
        override suspend fun getImportedFingerprintsStartingBetween(start: Instant, end: Instant): List<String> = emptyList()
        override suspend fun deleteByFingerprints(fingerprints: List<String>) {}
    }

    private class FakeCoachJournalDao : CoachJournalDao {
        val entries = mutableListOf<CoachJournalEntity>()
        override suspend fun insert(entry: CoachJournalEntity): Long {
            val id = if (entry.id == 0L) (entries.size + 1).toLong() else entry.id
            entries.add(entry.copy(id = id))
            return id
        }
        override suspend fun delete(id: Long) {
            entries.removeAll { it.id == id }
        }
        override fun getAll(): Flow<List<CoachJournalEntity>> = flowOf(entries)
        override suspend fun getAllSync(): List<CoachJournalEntity> = entries.toList()
        override suspend fun deleteAll() {
            entries.clear()
        }
    }
}
