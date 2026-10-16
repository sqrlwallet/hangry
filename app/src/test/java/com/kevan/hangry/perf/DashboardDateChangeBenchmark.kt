package com.kevan.hangry.perf

import com.kevan.hangry.data.local.entity.*
import com.kevan.hangry.domain.calculation.HangryCalorieCalculator
import com.kevan.hangry.domain.calculation.HangrySleepCalculator
import com.kevan.hangry.domain.calculation.HangryStrainCalculator
import com.kevan.hangry.domain.calculation.HangryStressCalculator
import com.kevan.hangry.domain.calculation.HangryTrainingLoadCalculator
import com.kevan.hangry.domain.model.BodyMetricsInput
import com.kevan.hangry.domain.model.BodyMetricsSnapshot
import com.kevan.hangry.domain.model.DashboardWidget
import com.kevan.hangry.domain.model.SyncProgress
import com.kevan.hangry.domain.model.SyncStatus
import com.kevan.hangry.domain.repository.*
import com.kevan.hangry.ui.dashboard.DashboardViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.Executors

/**
 * Main-thread CPU spent by Today for each day the user steps back through, with two years of
 * history. Repositories deliver off the main thread, like Room does, so what's measured is the
 * work the ViewModel itself puts on the main thread.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardDateChangeBenchmark {

    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.now(zone)
    private val days = 730L

    private val queries = java.util.concurrent.atomic.AtomicInteger()
    private val rowsRead = java.util.concurrent.atomic.AtomicInteger()

    /** A Room-like Flow: the query runs on IO, and each run is counted (with the rows it returns). */
    private fun <T> room(block: () -> T): Flow<T> = flow {
        queries.incrementAndGet()
        val result = block()
        rowsRead.addAndGet(if (result is List<*>) result.size else 1)
        emit(result)
    }.flowOn(Dispatchers.IO)

    private val sleep = (0 until days).map { i ->
        val date = today.minusDays(i)
        val end = date.atTime(7, 0).atZone(zone).toInstant()
        val start = end.minusSeconds(7 * 3600)
        SleepSessionEntity(
            recordFingerprint = "s$i", startTime = start, endTime = end, durationMinutes = 420,
            timeInBedMinutes = 450, deepSleepMinutes = 80, remSleepMinutes = 90, lightSleepMinutes = 230, awakeMinutes = 30,
            stageSegments = "L:0-1800,D:1800-3600,R:3600-5400,L:5400-9000,D:9000-10800,R:10800-14400,L:14400-21600,A:21600-25200".repeat(4)
        )
    }
    private val workouts = (0 until days step 2).map { i ->
        val start = today.minusDays(i).atTime(18, 0).atZone(zone).toInstant()
        ExerciseSessionEntity(
            recordFingerprint = "w$i", exerciseType = "RUNNING", title = "Run", startTime = start, endTime = start.plusSeconds(3600),
            durationMinutes = 60, activeCalories = 500.0, totalCalories = 600.0, steps = 6000, avgHeartRate = 140.0, maxHeartRate = 170.0
        )
    }
    private val summaries = (0 until days).map { i ->
        DailyHealthSummaryEntity(
            date = today.minusDays(i), sleepDurationMinutes = 420, steps = 8000, restingHeartRate = 55.0, hrvRmssd = 45.0,
            dayStrain = 10.0, activeCalories = 400.0, bmrCalories = 1700.0, sleepingHeartRate = 52.0, respiratoryRate = 14.0,
            skinTempDeltaCelsius = 0.1, spo2Percentage = 97.0
        )
    }
    private val scores = (0 until days).map { i ->
        RecoveryScoreEntity(date = today.minusDays(i), score = 70, confidence = "HIGH", state = "PRIMED", supportiveAdvice = "x")
    }

    @Test
    fun dateChange_mainThreadCpu() {
        assumeTrue(PerfBench.enabled)
        val mainExecutor = Executors.newSingleThreadExecutor { r -> Thread(r, "fake-main") }
        val mainThread = mainExecutor.submit<Thread> { Thread.currentThread() }.get()
        Dispatchers.setMain(mainExecutor.asCoroutineDispatcher())
        try {
            val vm = newViewModel()
            runBlocking {
                withTimeout(30_000) { vm.uiState.first { !it.isLoading && it.selectedDate == today } }
                val samples = mutableListOf<Double>()
                var queriesTimed = 0
                var rowsTimed = 0
                // Step back a day at a time (with a warm-up pass), like swiping through history.
                for (pass in 0..1) {
                    for (i in 1..60) {
                        val date = today.minusDays(i.toLong() + pass * 60L)
                        val before = PerfBench.cpuMs(mainThread)
                        val q0 = queries.get(); val r0 = rowsRead.get()
                        vm.selectDate(date)
                        withTimeout(30_000) { vm.uiState.first { it.selectedDate == date && !it.isLoading } }
                        // Let anything still queued for this day finish on the main thread.
                        mainExecutor.submit {}.get()
                        if (pass == 1) {
                            samples += PerfBench.cpuMs(mainThread) - before
                            queriesTimed += queries.get() - q0; rowsTimed += rowsRead.get() - r0
                        }
                    }
                }
                PerfBench.report("Today: main-thread CPU per day change", samples)
                println("BENCH Today: queries per day change %.1f, rows read per day change %.1f".format(queriesTimed / 60.0, rowsTimed / 60.0))
            }
        } finally {
            Dispatchers.resetMain()
            mainExecutor.shutdownNow()
        }
    }

    private fun newViewModel(): DashboardViewModel {
        val dailySummaryRepo = object : DailySummaryRepository {
            override fun getLatestSummary(): Flow<DailyHealthSummaryEntity?> = room { summaries.first() }
            override fun getSummaryForDate(date: LocalDate): Flow<DailyHealthSummaryEntity?> = room { summaries.firstOrNull { it.date == date } }
            override suspend fun getSummaryForDateSync(date: LocalDate): DailyHealthSummaryEntity? = summaries.firstOrNull { it.date == date }
            override fun getSummariesBetween(start: LocalDate, end: LocalDate): Flow<List<DailyHealthSummaryEntity>> =
                room { summaries.filter { it.date in start..end }.sortedByDescending { it.date } }
            override fun getAllSummaries(): Flow<List<DailyHealthSummaryEntity>> = room { summaries }
            override suspend fun saveSummary(summary: DailyHealthSummaryEntity) {}
            override fun getLatestRecoveryScore(): Flow<RecoveryScoreEntity?> = room { scores.first() }
            override fun getRecoveryScoreForDate(date: LocalDate): Flow<RecoveryScoreEntity?> = room { scores.firstOrNull { it.date == date } }
            override suspend fun getRecoveryScoreForDateSync(date: LocalDate): RecoveryScoreEntity? = scores.firstOrNull { it.date == date }
            override fun getScoresBetween(start: LocalDate, end: LocalDate): Flow<List<RecoveryScoreEntity>> = room { scores.filter { it.date in start..end } }
            override fun getAllScores(): Flow<List<RecoveryScoreEntity>> = room { scores }
            override suspend fun saveRecoveryScore(score: RecoveryScoreEntity) {}
            override suspend fun deleteAll() {}
        }
        val sleepRepo = object : SleepRepository {
            override fun getLatestSession(): Flow<SleepSessionEntity?> = room { sleep.first() }
            override fun getSessionsBetween(start: Instant, end: Instant): Flow<List<SleepSessionEntity>> =
                room { sleep.filter { it.startTime >= start && it.endTime <= end } }
            override suspend fun getSessionsBetweenList(start: Instant, end: Instant): List<SleepSessionEntity> =
                sleep.filter { it.startTime >= start && it.endTime <= end }
            override suspend fun insertSessions(sessions: List<SleepSessionEntity>): List<Long> = emptyList()
            override suspend fun deleteAll() {}
        }
        val workoutRepo = object : WorkoutRepository {
            override fun getAllSessions(): Flow<List<ExerciseSessionEntity>> = room { workouts.map { it.copy() } }
            override fun getSessionsBetween(start: Instant, end: Instant): Flow<List<ExerciseSessionEntity>> =
                room { workouts.filter { it.startTime >= start && it.startTime <= end } }
            override suspend fun getSessionsBetweenList(start: Instant, end: Instant): List<ExerciseSessionEntity> =
                workouts.filter { it.startTime >= start && it.startTime <= end }
            override suspend fun insertSessions(sessions: List<ExerciseSessionEntity>): List<Long> = emptyList()
            override suspend fun deleteAll() {}
        }
        val syncManager = object : HealthSyncManager {
            override fun syncHistorical(days: Int): Flow<SyncProgress> = flowOf(SyncProgress(status = SyncStatus.SUCCESS, currentDataType = "DONE"))
            override fun syncRecent(): Flow<SyncProgress> = syncHistorical(3)
            override fun recalculateAllBaselines(): Flow<SyncProgress> = flowOf(SyncProgress(status = SyncStatus.SUCCESS, currentDataType = "DONE"))
            override fun getSyncStates(): Flow<List<SyncStateEntity>> = flowOf(emptyList())
            override suspend fun clearAllData() {}
        }
        val profile = UserProfileEntity(age = 35, dailyStepGoal = 8000L)
        val userProfileRepo = object : UserProfileRepository {
            override fun getProfile(): Flow<UserProfileEntity?> = room { profile }
            override suspend fun getProfileSync(): UserProfileEntity? = profile
            override suspend fun saveProfile(profile: UserProfileEntity) {}
        }
        val weightRepo = object : WeightRepository {
            override suspend fun insertOrIgnore(records: List<WeightMeasurementEntity>): List<Long> = emptyList()
            override fun getLatestWeight(): Flow<WeightMeasurementEntity?> = room { null }
            override suspend fun getLatestWeightSync(): WeightMeasurementEntity? = null
            override fun getAllWeights(): Flow<List<WeightMeasurementEntity>> = room { emptyList() }
            override fun getWeightsBetween(start: Instant, end: Instant): Flow<List<WeightMeasurementEntity>> = room { emptyList() }
            override suspend fun getWeightsBetweenList(start: Instant, end: Instant): List<WeightMeasurementEntity> = emptyList()
            override fun getRollingAverageWeight(): Flow<Double?> = flowOf(null)
            override suspend fun getCount(): Int = 0
            override suspend fun deleteAll() {}
            override suspend fun getImportedFingerprintsBetween(start: Instant, end: Instant): List<String> = emptyList()
            override suspend fun deleteByFingerprints(fingerprints: List<String>) {}
        }
        val widgetFlow = MutableStateFlow(DashboardWidget.createDefaultWidgets())
        val widgetRepo = object : DashboardWidgetRepository {
            override fun getWidgets(): Flow<List<DashboardWidget>> = widgetFlow
            override suspend fun toggleWidgetVisibility(widgetId: String, isVisible: Boolean) {}
            override suspend fun reorderWidgets(orderedIds: List<String>) {}
            override suspend fun addCustomWidget(widget: DashboardWidget) {}
            override suspend fun removeWidget(widgetId: String) {}
            override suspend fun resetToDefault() {}
        }
        val bodyFatRepo = object : BodyFatRepository {
            override fun getLatestScan(): Flow<BodyFatScanEntity?> = room { null }
            override suspend fun getLatestScanSync(): BodyFatScanEntity? = null
            override fun getAllScans(): Flow<List<BodyFatScanEntity>> = room { emptyList() }
            override fun getScansBetween(startDate: LocalDate, endDate: LocalDate): Flow<List<BodyFatScanEntity>> = room { emptyList() }
            override suspend fun saveScan(scan: BodyFatScanEntity): Long = -1L
            override suspend fun deleteScan(id: Long) {}
        }
        // The real one combines 9 queries (profile, latest weight/height/scan, 14 days of
        // summaries, 7 days of workouts, 28 days of meals, 28 days of weigh-ins); count them as such.
        val bodyMetricsRepo = object : BodyMetricsRepository {
            override fun observe(): Flow<BodyMetricsSnapshot> = room {
                queries.addAndGet(8)
                rowsRead.addAndGet(4 + 14 + 4 + 28 * 4 + 28)
                BodyMetricsSnapshot(BodyMetricsInput(), null, emptyList())
            }
            override suspend fun current(): BodyMetricsSnapshot = BodyMetricsSnapshot(BodyMetricsInput(), null, emptyList())
            override suspend fun logWeight(kg: Double, takenAt: Instant) {}
        }
        val trainingLoadCalculator = HangryTrainingLoadCalculator()
        return DashboardViewModel(
            dailySummaryRepository = dailySummaryRepo,
            sleepRepository = sleepRepo,
            workoutRepository = workoutRepo,
            healthSyncManager = syncManager,
            userProfileRepository = userProfileRepo,
            weightRepository = weightRepo,
            sleepCalculator = HangrySleepCalculator(),
            trainingLoadCalculator = trainingLoadCalculator,
            strainCalculator = HangryStrainCalculator(trainingLoadCalculator),
            calorieCalculator = HangryCalorieCalculator(),
            stressCalculator = HangryStressCalculator(),
            dashboardWidgetRepository = widgetRepo,
            bodyFatRepository = bodyFatRepo,
            bodyMetricsRepository = bodyMetricsRepo
        )
    }
}
