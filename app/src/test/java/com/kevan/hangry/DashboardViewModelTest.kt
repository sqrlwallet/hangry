package com.kevan.hangry

import com.kevan.hangry.data.local.dao.WeightDao
import com.kevan.hangry.data.local.entity.*
import com.kevan.hangry.domain.calculation.HangryCalorieCalculator
import com.kevan.hangry.domain.calculation.HangrySleepCalculator
import com.kevan.hangry.domain.calculation.HangryStrainCalculator
import com.kevan.hangry.domain.calculation.HangryStressCalculator
import com.kevan.hangry.domain.calculation.HangryTrainingLoadCalculator
import com.kevan.hangry.domain.model.DashboardWidget
import com.kevan.hangry.domain.model.SyncProgress
import com.kevan.hangry.domain.model.SyncStatus
import com.kevan.hangry.domain.repository.*
import com.kevan.hangry.ui.dashboard.DashboardViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testViewModel_initialStateAndSyncNowTrigger() = runTest(testDispatcher) {
        val today = LocalDate.now()
        val mockSummary = DailyHealthSummaryEntity(
            date = today,
            sleepDurationMinutes = 480,
            steps = 8200,
            restingHeartRate = 56.0,
            hrvRmssd = 64.0
        )
        val mockScore = RecoveryScoreEntity(
            date = today,
            score = 78,
            confidence = "HIGH",
            state = "PRIMED",
            supportiveAdvice = "Great energy today"
        )

        val dailySummaryRepo = object : DailySummaryRepository {
            override fun getLatestSummary(): Flow<DailyHealthSummaryEntity?> = flowOf(mockSummary)
            override fun getSummaryForDate(date: LocalDate): Flow<DailyHealthSummaryEntity?> = flowOf(mockSummary)
            override suspend fun getSummaryForDateSync(date: LocalDate): DailyHealthSummaryEntity? = mockSummary
            override fun getSummariesBetween(start: LocalDate, end: LocalDate): Flow<List<DailyHealthSummaryEntity>> = flowOf(listOf(mockSummary))
            override fun getAllSummaries(): Flow<List<DailyHealthSummaryEntity>> = flowOf(listOf(mockSummary))
            override suspend fun saveSummary(summary: DailyHealthSummaryEntity) {}
            override fun getLatestRecoveryScore(): Flow<RecoveryScoreEntity?> = flowOf(mockScore)
            override fun getRecoveryScoreForDate(date: LocalDate): Flow<RecoveryScoreEntity?> = flowOf(mockScore)
            override suspend fun getRecoveryScoreForDateSync(date: LocalDate): RecoveryScoreEntity? = mockScore
            override fun getScoresBetween(start: LocalDate, end: LocalDate): Flow<List<RecoveryScoreEntity>> = flowOf(listOf(mockScore))
            override fun getAllScores(): Flow<List<RecoveryScoreEntity>> = flowOf(listOf(mockScore))
            override suspend fun saveRecoveryScore(score: RecoveryScoreEntity) {}
            override suspend fun deleteAll() {}
        }

        val sleepRepo = object : SleepRepository {
            override fun getLatestSession(): Flow<SleepSessionEntity?> = flowOf(null)
            override fun getSessionsBetween(start: Instant, end: Instant): Flow<List<SleepSessionEntity>> = flowOf(emptyList())
            override suspend fun getSessionsBetweenList(start: Instant, end: Instant): List<SleepSessionEntity> = emptyList()
            override suspend fun insertSessions(sessions: List<SleepSessionEntity>): List<Long> = emptyList()
            override suspend fun deleteAll() {}
        }

        val workoutRepo = object : WorkoutRepository {
            override fun getAllSessions(): Flow<List<ExerciseSessionEntity>> = flowOf(emptyList())
            override fun getSessionsBetween(start: Instant, end: Instant): Flow<List<ExerciseSessionEntity>> = flowOf(emptyList())
            override suspend fun getSessionsBetweenList(start: Instant, end: Instant): List<ExerciseSessionEntity> = emptyList()
            override suspend fun insertSessions(sessions: List<ExerciseSessionEntity>): List<Long> = emptyList()
            override suspend fun deleteAll() {}
        }

        var syncCalled = false
        val syncManager = object : HealthSyncManager {
            override fun syncHistorical(days: Int): Flow<SyncProgress> = flow {
                syncCalled = true
                emit(SyncProgress(status = SyncStatus.IN_PROGRESS, currentDataType = "SLEEP"))
                emit(SyncProgress(status = SyncStatus.SUCCESS, currentDataType = "DONE"))
            }
            override fun syncRecent(): Flow<SyncProgress> = syncHistorical(3)
            override fun recalculateAllBaselines(): Flow<SyncProgress> = flowOf(SyncProgress(status = SyncStatus.SUCCESS, currentDataType = "DONE"))
            override fun getSyncStates(): Flow<List<SyncStateEntity>> = flowOf(emptyList())
            override suspend fun clearAllData() {}
        }

        var savedProfile: UserProfileEntity? = null
        val userProfileRepo = object : UserProfileRepository {
            override fun getProfile(): Flow<UserProfileEntity?> = flowOf(UserProfileEntity(dailyStepGoal = 8000L, dailyActivityMinutesGoal = 60, dailyActiveCaloriesGoal = 400))
            override suspend fun getProfileSync(): UserProfileEntity? = savedProfile ?: UserProfileEntity()
            override suspend fun saveProfile(profile: UserProfileEntity) {
                savedProfile = profile
            }
        }

        val weightDao = object : WeightDao {
            override suspend fun insertOrIgnore(records: List<WeightMeasurementEntity>): List<Long> = emptyList()
            override fun getLatestWeight(): Flow<WeightMeasurementEntity?> = flowOf(null)
            override suspend fun getLatestWeightSync(): WeightMeasurementEntity? = null
            override fun getAllWeights(): Flow<List<WeightMeasurementEntity>> = flowOf(emptyList())
            override fun getRollingAverageWeight(): Flow<Double?> = flowOf(null)
            override suspend fun getCount(): Int = 0
            override suspend fun deleteAll() {}
        }

        val widgetFlow = MutableStateFlow(DashboardWidget.createDefaultWidgets())
        val widgetRepo = object : DashboardWidgetRepository {
            override fun getWidgets(): Flow<List<DashboardWidget>> = widgetFlow
            override suspend fun toggleWidgetVisibility(widgetId: String, isVisible: Boolean) {
                widgetFlow.value = widgetFlow.value.map {
                    if (it.id == widgetId) it.copy(isVisible = isVisible) else it
                }
            }
            override suspend fun reorderWidgets(orderedIds: List<String>) {}
            override suspend fun addCustomWidget(widget: DashboardWidget) {
                widgetFlow.value = widgetFlow.value + widget
            }
            override suspend fun removeWidget(widgetId: String) {
                widgetFlow.value = widgetFlow.value.filterNot { it.id == widgetId }
            }
            override suspend fun resetToDefault() {
                widgetFlow.value = DashboardWidget.createDefaultWidgets()
            }
        }

        val trainingLoadCalculator = HangryTrainingLoadCalculator()
        val viewModel = DashboardViewModel(
            dailySummaryRepository = dailySummaryRepo,
            sleepRepository = sleepRepo,
            workoutRepository = workoutRepo,
            healthSyncManager = syncManager,
            userProfileRepository = userProfileRepo,
            weightDao = weightDao,
            sleepCalculator = HangrySleepCalculator(),
            trainingLoadCalculator = trainingLoadCalculator,
            strainCalculator = HangryStrainCalculator(trainingLoadCalculator),
            calorieCalculator = HangryCalorieCalculator(),
            stressCalculator = HangryStressCalculator(),
            dashboardWidgetRepository = widgetRepo
        )

        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(78, state.recoveryScore?.score)
        assertEquals(480, state.dailySummary?.sleepDurationMinutes)
        assertEquals(8000L, state.dailyStepGoal)
        assertEquals(60, state.dailyActivityMinutesGoal)
        assertEquals(400, state.dailyActiveCaloriesGoal)
        assertTrue(state.widgets.isNotEmpty())

        // Test activity goal updates
        viewModel.updateActivityGoals(10000L, 550)
        testScheduler.advanceUntilIdle()
        assertEquals(10000L, viewModel.uiState.value.dailyStepGoal)
        assertEquals(550, viewModel.uiState.value.dailyActiveCaloriesGoal)

        // Test widget toggle
        viewModel.toggleWidgetVisibility("daily_activity_rings", false)
        testScheduler.advanceUntilIdle()
        val toggled = widgetFlow.value.find { it.id == "daily_activity_rings" }
        assertFalse(toggled?.isVisible == true)

        // Trigger manual sync
        viewModel.syncNow()
        testScheduler.advanceUntilIdle()

        assertTrue(syncCalled)
        assertFalse(viewModel.uiState.value.isSyncing)
    }
}
