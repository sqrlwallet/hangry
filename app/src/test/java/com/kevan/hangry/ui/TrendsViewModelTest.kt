package com.kevan.hangry.ui

import com.kevan.hangry.FakeFoodLogRepository
import com.kevan.hangry.data.local.entity.DailyHealthSummaryEntity
import com.kevan.hangry.data.local.entity.ExerciseSessionEntity
import com.kevan.hangry.data.local.entity.RecoveryScoreEntity
import com.kevan.hangry.data.local.entity.WeightMeasurementEntity
import com.kevan.hangry.domain.repository.DailySummaryRepository
import com.kevan.hangry.domain.repository.WeightRepository
import com.kevan.hangry.domain.repository.WorkoutRepository
import com.kevan.hangry.ui.trends.HistoryFilter
import com.kevan.hangry.ui.trends.TrendDomain
import com.kevan.hangry.ui.trends.TrendMetricCategory
import com.kevan.hangry.ui.trends.TrendTimeframe
import com.kevan.hangry.ui.trends.TrendsViewModel
import com.kevan.hangry.util.AppDispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class TrendsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val dispatchers = object : AppDispatchers {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
    }

    private class FakeDailySummaryRepository : DailySummaryRepository {
        val scores = mutableListOf<RecoveryScoreEntity>()
        val summaries = mutableListOf<DailyHealthSummaryEntity>()

        override fun getLatestSummary(): Flow<DailyHealthSummaryEntity?> = flowOf(summaries.lastOrNull())
        override fun getSummaryForDate(date: LocalDate): Flow<DailyHealthSummaryEntity?> = flowOf(summaries.find { it.date == date })
        override suspend fun getSummaryForDateSync(date: LocalDate): DailyHealthSummaryEntity? = summaries.find { it.date == date }
        override fun getSummariesBetween(start: LocalDate, end: LocalDate): Flow<List<DailyHealthSummaryEntity>> = flowOf(summaries)
        override fun getAllSummaries(): Flow<List<DailyHealthSummaryEntity>> = flowOf(summaries)
        override suspend fun saveSummary(summary: DailyHealthSummaryEntity) { summaries += summary }

        override fun getLatestRecoveryScore(): Flow<RecoveryScoreEntity?> = flowOf(scores.lastOrNull())
        override fun getRecoveryScoreForDate(date: LocalDate): Flow<RecoveryScoreEntity?> = flowOf(scores.find { it.date == date })
        override suspend fun getRecoveryScoreForDateSync(date: LocalDate): RecoveryScoreEntity? = scores.find { it.date == date }
        override fun getScoresBetween(start: LocalDate, end: LocalDate): Flow<List<RecoveryScoreEntity>> = flowOf(scores)
        override fun getAllScores(): Flow<List<RecoveryScoreEntity>> = flowOf(scores)
        override suspend fun saveRecoveryScore(score: RecoveryScoreEntity) { scores += score }

        override suspend fun deleteAll() { scores.clear(); summaries.clear() }
    }

    private class FakeWorkoutRepository : WorkoutRepository {
        val workouts = mutableListOf<ExerciseSessionEntity>()
        override fun getAllSessions(): Flow<List<ExerciseSessionEntity>> = flowOf(workouts)
        override fun getSessionsBetween(start: Instant, end: Instant): Flow<List<ExerciseSessionEntity>> =
            flowOf(workouts.filter { it.startTime >= start && it.startTime <= end })
        override suspend fun getSessionsBetweenList(start: Instant, end: Instant): List<ExerciseSessionEntity> =
            workouts.filter { it.startTime >= start && it.startTime <= end }
        override suspend fun insertSessions(sessions: List<ExerciseSessionEntity>): List<Long> {
            workouts += sessions
            return sessions.map { 1L }
        }
        override suspend fun deleteAll() { workouts.clear() }
    }

    private class FakeWeightRepo : WeightRepository {
        val list = mutableListOf<WeightMeasurementEntity>()
        private val _flow = MutableStateFlow<List<WeightMeasurementEntity>>(emptyList())

        override fun getAllWeights(): Flow<List<WeightMeasurementEntity>> = _flow.asStateFlow()
        override fun getWeightsBetween(start: Instant, end: Instant): Flow<List<WeightMeasurementEntity>> =
            flowOf(list.filter { it.timestamp >= start && it.timestamp < end })
        override suspend fun getWeightsBetweenList(start: Instant, end: Instant): List<WeightMeasurementEntity> =
            list.filter { it.timestamp >= start && it.timestamp < end }
        override fun getLatestWeight(): Flow<WeightMeasurementEntity?> = flowOf(list.lastOrNull())
        override suspend fun getLatestWeightSync(): WeightMeasurementEntity? = list.lastOrNull()
        override fun getRollingAverageWeight(): Flow<Double?> = flowOf(null)
        override suspend fun getCount(): Int = list.size
        override suspend fun getImportedFingerprintsBetween(start: Instant, end: Instant): List<String> = emptyList()
        override suspend fun deleteByFingerprints(fingerprints: List<String>) {}
        override suspend fun insertOrIgnore(records: List<WeightMeasurementEntity>): List<Long> {
            list.addAll(records)
            _flow.value = list.toList()
            return records.map { 1L }
        }
        override suspend fun deleteAll() { list.clear(); _flow.value = emptyList() }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_hasDefaults() = runTest(testDispatcher) {
        val dailyRepo = FakeDailySummaryRepository()
        val weightRepo = FakeWeightRepo()
        val workoutRepo = FakeWorkoutRepository()
        val foodRepo = FakeFoodLogRepository()

        val viewModel = TrendsViewModel(
            dailySummaryRepository = dailyRepo,
            weightRepository = weightRepo,
            workoutRepository = workoutRepo,
            foodLogRepository = foodRepo,
            dispatchers = dispatchers,
            todayDates = kotlinx.coroutines.flow.flowOf(java.time.LocalDate.now())
        )

        val job = launch { viewModel.uiState.collect { } }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(TrendTimeframe.DAYS_30, state.selectedTimeframe)
        assertEquals(TrendMetricCategory.RECOVERY, state.selectedMetric)
        assertEquals(TrendDomain.ALL, state.selectedDomain)
        assertEquals(HistoryFilter.ALL, state.historyFilter)
        job.cancel()
    }

    @Test
    fun setters_updateStateCorrectly() = runTest(testDispatcher) {
        val dailyRepo = FakeDailySummaryRepository()
        val weightRepo = FakeWeightRepo()
        val workoutRepo = FakeWorkoutRepository()
        val foodRepo = FakeFoodLogRepository()

        val viewModel = TrendsViewModel(
            dailySummaryRepository = dailyRepo,
            weightRepository = weightRepo,
            workoutRepository = workoutRepo,
            foodLogRepository = foodRepo,
            dispatchers = dispatchers,
            todayDates = kotlinx.coroutines.flow.flowOf(java.time.LocalDate.now())
        )

        val job = launch { viewModel.uiState.collect { } }
        advanceUntilIdle()

        viewModel.setTimeframe(TrendTimeframe.DAYS_90)
        viewModel.setMetric(TrendMetricCategory.HRV)
        viewModel.setDomain(TrendDomain.VITALS)
        viewModel.setHistoryFilter(HistoryFilter.WORKOUTS)
        val today = LocalDate.now()
        viewModel.setInspectingDate(today)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(TrendTimeframe.DAYS_90, state.selectedTimeframe)
        assertEquals(TrendMetricCategory.HRV, state.selectedMetric)
        assertEquals(TrendDomain.VITALS, state.selectedDomain)
        assertEquals(HistoryFilter.WORKOUTS, state.historyFilter)
        assertEquals(today, state.inspectingDate)
        job.cancel()
    }

    @Test
    fun logWeight_persistsToRepository() = runTest(testDispatcher) {
        val dailyRepo = FakeDailySummaryRepository()
        val weightRepo = FakeWeightRepo()
        val workoutRepo = FakeWorkoutRepository()
        val foodRepo = FakeFoodLogRepository()

        val viewModel = TrendsViewModel(
            dailySummaryRepository = dailyRepo,
            weightRepository = weightRepo,
            workoutRepository = workoutRepo,
            foodLogRepository = foodRepo,
            dispatchers = dispatchers,
            todayDates = kotlinx.coroutines.flow.flowOf(java.time.LocalDate.now())
        )

        advanceUntilIdle()

        viewModel.logWeight(75.5, Instant.now())
        advanceUntilIdle()

        assertEquals(1, weightRepo.list.size)
        assertEquals(75.5, weightRepo.list.first().weightKg, 0.001)
    }
}
