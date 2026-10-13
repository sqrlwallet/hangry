package com.kevan.hangry.ui.trends

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kevan.hangry.R
import com.kevan.hangry.data.local.entity.BodyFatScanEntity
import com.kevan.hangry.data.local.entity.DailyHealthSummaryEntity
import com.kevan.hangry.data.local.entity.ExerciseSessionEntity
import com.kevan.hangry.data.local.entity.FoodLogEntity
import com.kevan.hangry.data.local.entity.RecoveryScoreEntity
import com.kevan.hangry.data.local.entity.WeightMeasurementEntity
import com.kevan.hangry.domain.repository.BodyFatRepository
import com.kevan.hangry.domain.repository.DailySummaryRepository
import com.kevan.hangry.domain.repository.FoodLogRepository
import com.kevan.hangry.domain.repository.WeightRepository
import com.kevan.hangry.domain.repository.WorkoutRepository
import com.kevan.hangry.util.AppDispatchers
import com.kevan.hangry.util.DefaultAppDispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class HistoryFilter(@StringRes val labelRes: Int) {
    ALL(R.string.body_trends_filter_all_days),
    WORKOUTS(R.string.body_trends_filter_workouts),
    PRIMED(R.string.body_trends_filter_primed),
    REBUILD(R.string.body_trends_filter_rebuild),
    NUTRITION(R.string.body_trends_filter_has_meals),
    BODY_SIGNALS(R.string.body_trends_filter_body_signals)
}

data class TrendsUiState(
    val selectedTimeframe: TrendTimeframe = TrendTimeframe.DAYS_30,
    val selectedMetric: TrendMetricCategory = TrendMetricCategory.RECOVERY,
    val selectedDomain: TrendDomain = TrendDomain.ALL,
    val historyFilter: HistoryFilter = HistoryFilter.ALL,
    val inspectingDate: LocalDate? = null,
    val scores: List<RecoveryScoreEntity> = emptyList(),
    val summaries: List<DailyHealthSummaryEntity> = emptyList(),
    val foodLogs: List<FoodLogEntity> = emptyList(),
    val allWorkouts: List<ExerciseSessionEntity> = emptyList(),
    val allWeights: List<WeightMeasurementEntity> = emptyList(),
    val allBodyFatScans: List<BodyFatScanEntity> = emptyList(),
    val latestWeight: WeightMeasurementEntity? = null,
    val thisWeekScores: List<RecoveryScoreEntity> = emptyList(),
    val lastWeekScores: List<RecoveryScoreEntity> = emptyList(),
    val thisWeekSummaries: List<DailyHealthSummaryEntity> = emptyList(),
    val lastWeekSummaries: List<DailyHealthSummaryEntity> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
class TrendsViewModel(
    private val dailySummaryRepository: DailySummaryRepository,
    private val weightRepository: WeightRepository,
    private val workoutRepository: WorkoutRepository,
    private val foodLogRepository: FoodLogRepository,
    private val bodyFatRepository: BodyFatRepository? = null,
    private val dispatchers: AppDispatchers = DefaultAppDispatchers(),
    private val zoneProvider: () -> ZoneId = { ZoneId.systemDefault() }
) : ViewModel() {

    private val zone get() = zoneProvider()
    private val today get() = LocalDate.now(zone)

    private val _selectedTimeframe = MutableStateFlow(TrendTimeframe.DAYS_30)
    private val _selectedMetric = MutableStateFlow(TrendMetricCategory.RECOVERY)
    private val _selectedDomain = MutableStateFlow(TrendDomain.ALL)
    private val _historyFilter = MutableStateFlow(HistoryFilter.ALL)
    private val _inspectingDate = MutableStateFlow<LocalDate?>(null)

    // Windowed data based on selected timeframe
    private val timeframeData = _selectedTimeframe.flatMapLatest { timeframe ->
        val queryStartDate = timeframe.days?.let { today.minusDays(it) } ?: LocalDate.of(2020, 1, 1)
        combine(
            dailySummaryRepository.getScoresBetween(queryStartDate, today),
            dailySummaryRepository.getSummariesBetween(queryStartDate, today),
            foodLogRepository.getBetween(queryStartDate, today)
        ) { scores, summaries, foodLogs ->
            Triple(scores, summaries, foodLogs)
        }
    }.flowOn(dispatchers.io)

    // Weekly comparison baseline
    private val weeklyRecapData = flow {
        val t = today
        val thisWkScores = dailySummaryRepository.getScoresBetween(t.minusDays(6), t)
        val lastWkScores = dailySummaryRepository.getScoresBetween(t.minusDays(13), t.minusDays(7))
        val thisWkSummaries = dailySummaryRepository.getSummariesBetween(t.minusDays(6), t)
        val lastWkSummaries = dailySummaryRepository.getSummariesBetween(t.minusDays(13), t.minusDays(7))

        emitAll(
            combine(thisWkScores, lastWkScores, thisWkSummaries, lastWkSummaries) { tws, lws, twsum, lwsum ->
                WeeklyRecapData(tws, lws, twsum, lwsum)
            }
        )
    }.flowOn(dispatchers.io)

    private data class WeeklyRecapData(
        val thisWeekScores: List<RecoveryScoreEntity>,
        val lastWeekScores: List<RecoveryScoreEntity>,
        val thisWeekSummaries: List<DailyHealthSummaryEntity>,
        val lastWeekSummaries: List<DailyHealthSummaryEntity>
    )

    private val allScansFlow = bodyFatRepository?.getAllScans() ?: flowOf(emptyList())

    private data class FilterSelection(
        val timeframe: TrendTimeframe,
        val metric: TrendMetricCategory,
        val domain: TrendDomain,
        val filter: HistoryFilter,
        val inspectingDate: LocalDate?
    )

    private data class HistoricalMetrics(
        val workouts: List<ExerciseSessionEntity>,
        val weights: List<WeightMeasurementEntity>,
        val scans: List<BodyFatScanEntity>,
        val latestWeight: WeightMeasurementEntity?
    )

    private val filterSelectionFlow = combine(
        _selectedTimeframe,
        _selectedMetric,
        _selectedDomain,
        _historyFilter,
        _inspectingDate
    ) { tf, metric, domain, filter, inspecting ->
        FilterSelection(tf, metric, domain, filter, inspecting)
    }

    private val historicalMetricsFlow = combine(
        workoutRepository.getAllSessions(),
        weightRepository.getAllWeights(),
        allScansFlow,
        weightRepository.getLatestWeight()
    ) { workouts, weights, scans, latestWeight ->
        HistoricalMetrics(workouts, weights, scans, latestWeight)
    }

    val uiState: StateFlow<TrendsUiState> = combine(
        filterSelectionFlow,
        timeframeData,
        weeklyRecapData,
        historicalMetricsFlow
    ) { sel, (scores, summaries, foodLogs), weekly, hist ->
        TrendsUiState(
            selectedTimeframe = sel.timeframe,
            selectedMetric = sel.metric,
            selectedDomain = sel.domain,
            historyFilter = sel.filter,
            inspectingDate = sel.inspectingDate,
            scores = scores,
            summaries = summaries,
            foodLogs = foodLogs,
            allWorkouts = hist.workouts,
            allWeights = hist.weights,
            allBodyFatScans = hist.scans,
            latestWeight = hist.latestWeight,
            thisWeekScores = weekly.thisWeekScores,
            lastWeekScores = weekly.lastWeekScores,
            thisWeekSummaries = weekly.thisWeekSummaries,
            lastWeekSummaries = weekly.lastWeekSummaries
        )
    }.flowOn(dispatchers.default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = TrendsUiState()
        )

    fun setTimeframe(timeframe: TrendTimeframe) {
        _selectedTimeframe.value = timeframe
    }

    fun setMetric(metric: TrendMetricCategory) {
        _selectedMetric.value = metric
    }

    fun setDomain(domain: TrendDomain) {
        _selectedDomain.value = domain
    }

    fun setHistoryFilter(filter: HistoryFilter) {
        _historyFilter.value = filter
    }

    fun setInspectingDate(date: LocalDate?) {
        _inspectingDate.value = date
    }

    fun logWeight(kg: Double, takenAt: Instant = Instant.now()) {
        viewModelScope.launch(dispatchers.io) {
            weightRepository.insertOrIgnore(
                listOf(
                    WeightMeasurementEntity(
                        recordFingerprint = "manual_entry_${Instant.now().toEpochMilli()}",
                        timestamp = takenAt,
                        weightKg = kg,
                        sourcePackageName = "com.kevan.hangry.manual"
                    )
                )
            )
        }
    }

    companion object {
        fun provideFactory(
            dailySummaryRepository: DailySummaryRepository,
            weightRepository: WeightRepository,
            workoutRepository: WorkoutRepository,
            foodLogRepository: FoodLogRepository,
            bodyFatRepository: BodyFatRepository? = null,
            dispatchers: AppDispatchers = DefaultAppDispatchers()
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return TrendsViewModel(
                    dailySummaryRepository = dailySummaryRepository,
                    weightRepository = weightRepository,
                    workoutRepository = workoutRepository,
                    foodLogRepository = foodLogRepository,
                    bodyFatRepository = bodyFatRepository,
                    dispatchers = dispatchers
                ) as T
            }
        }
    }
}
