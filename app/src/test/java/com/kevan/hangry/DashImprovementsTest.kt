package com.kevan.hangry

import com.kevan.hangry.data.coach.CoachActionExecutor
import com.kevan.hangry.data.local.dao.WeightDao
import com.kevan.hangry.data.local.entity.DailyHealthSummaryEntity
import com.kevan.hangry.data.local.entity.UserProfileEntity
import com.kevan.hangry.data.local.entity.WeightMeasurementEntity
import com.kevan.hangry.domain.ai.DashInsights
import com.kevan.hangry.domain.model.CoachAction
import com.kevan.hangry.domain.model.GoalsActionPayload
import com.kevan.hangry.domain.model.Supplement
import com.kevan.hangry.domain.model.SupplementDose
import com.kevan.hangry.domain.model.SupplementsSnapshot
import com.kevan.hangry.domain.model.WeightActionPayload
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
        val magnesium = Supplement(1, "Magnesium", null, null, 2.0, "capsules", listOf(LocalTime.of(8, 0)), emptyList(), true, null, null, true)
        val snapshot = SupplementsSnapshot(supplements = listOf(magnesium), todayDoses = listOf(SupplementDose(magnesium, LocalTime.of(8, 0), taken = false)))
        val chips = DashInsights.suggestions(snapshot, null, null, now = LocalTime.of(12, 0))
        assertTrue(chips.first().contains("Magnesium"))
        assertEquals(5, chips.size)
    }

    private val profiles = FakeProfiles()
    private val weights = FakeWeightDao()
    private val executor = CoachActionExecutor(
        supplementRepository = FakeSupplementRepository(),
        healthRecordsRepository = FakeHealthRecordsRepository(),
        foodLogRepository = FakeFoodLogRepository(),
        writeNutritionRecord = { true },
        userProfileRepository = profiles,
        weightDao = weights
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
    }
}
