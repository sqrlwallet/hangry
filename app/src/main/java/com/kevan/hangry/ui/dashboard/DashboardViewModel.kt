package com.kevan.hangry.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kevan.hangry.data.local.dao.WeightDao
import com.kevan.hangry.data.local.entity.BodyFatScanEntity
import com.kevan.hangry.data.local.entity.DailyHealthSummaryEntity
import com.kevan.hangry.data.local.entity.ExerciseSessionEntity
import com.kevan.hangry.data.local.entity.RecoveryScoreEntity
import com.kevan.hangry.data.local.entity.SleepSessionEntity
import com.kevan.hangry.data.local.entity.UserProfileEntity
import com.kevan.hangry.data.local.entity.WeightMeasurementEntity
import com.kevan.hangry.data.repository.BodyAgeLoader
import com.kevan.hangry.data.repository.StreaksLoader
import com.kevan.hangry.domain.calculation.ActiveActivityCalculator
import com.kevan.hangry.domain.calculation.CalorieCalculator
import com.kevan.hangry.domain.calculation.SleepCalculator
import com.kevan.hangry.domain.calculation.StrainCalculator
import com.kevan.hangry.domain.calculation.StressCalculator
import com.kevan.hangry.domain.calculation.TrainingLoadCalculator
import com.kevan.hangry.domain.model.CalorieGoalRecommendation
import com.kevan.hangry.domain.model.DashboardWidget
import com.kevan.hangry.domain.model.EnergyBalanceEstimate
import com.kevan.hangry.domain.model.HrvFeeling
import com.kevan.hangry.domain.model.RecoveryState
import com.kevan.hangry.domain.model.SyncStatus
import com.kevan.hangry.domain.repository.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.withContext

class DashboardViewModel(
    private val dailySummaryRepository: DailySummaryRepository,
    private val sleepRepository: SleepRepository,
    private val workoutRepository: WorkoutRepository,
    private val healthSyncManager: HealthSyncManager,
    private val userProfileRepository: UserProfileRepository,
    private val weightDao: WeightDao,
    private val sleepCalculator: SleepCalculator,
    private val trainingLoadCalculator: TrainingLoadCalculator,
    private val strainCalculator: StrainCalculator,
    private val calorieCalculator: CalorieCalculator,
    private val stressCalculator: StressCalculator,
    private val dashboardWidgetRepository: DashboardWidgetRepository,
    private val bodyFatRepository: BodyFatRepository,
    /** Source of the 7-day maintenance/goal calorie estimate; null leaves the calorie goal blank. */
    private val bodyMetricsRepository: BodyMetricsRepository? = null,
    private val streaksLoader: StreaksLoader? = null,
    private val bodyAgeLoader: BodyAgeLoader? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    // "Today" is the device's local day everywhere in this pipeline - see DefaultHealthSyncManager,
    // which uses the same zone so day boundaries agree end to end.
    private val zone = ZoneId.systemDefault()
    private val _selectedDate = MutableStateFlow(LocalDate.now(zone))
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private var lastKnownToday: LocalDate = LocalDate.now(zone)

    /**
     * The app came back to the foreground, or the clock passed midnight. If the screen was
     * following "today", it moves to the new today - otherwise a meal logged after midnight
     * would land on yesterday. A day the user picked on purpose stays as it is.
     */
    fun onDayMaybeChanged() {
        val today = LocalDate.now(zone)
        if (today == lastKnownToday) return
        if (_selectedDate.value == lastKnownToday) _selectedDate.value = today
        lastKnownToday = today
    }

    private var lastSyncCompletedAt: Instant? = null

    /**
     * The app came back to the foreground: roll over to the new day if needed, and catch up
     * with Health Connect if the last sync was a while ago - the startup sync only runs when
     * the app process starts, not every time you switch back to it.
     */
    fun onAppResumed() {
        onDayMaybeChanged()
        refreshHabits()
        val last = lastSyncCompletedAt ?: return
        if (!_uiState.value.isSyncing && java.time.Duration.between(last, Instant.now()) > RESUME_SYNC_AFTER) {
            syncNow(days = 3)
        }
    }

    // Guards against re-triggering a sync every time this flow recombines while waiting for it.
    private var autoResyncedForDate: LocalDate? = null

    init {
        loadDashboardData()
        observeHrvFeeling()
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun observeHrvFeeling() {
        viewModelScope.launch {
            _selectedDate.flatMapLatest { healthSyncManager.observeHrvFeeling(it) }.collect { feeling ->
                _uiState.update { it.copy(hrvFeeling = feeling ?: HrvFeeling.DEFAULT) }
            }
        }
    }

    /** Only offered when the day has no HRV reading; the stored recovery score updates via its flow. */
    fun setHrvFeeling(feeling: HrvFeeling) {
        val date = _selectedDate.value
        _uiState.update { it.copy(hrvFeeling = feeling) }
        viewModelScope.launch { healthSyncManager.setHrvFeeling(date, feeling) }
    }

    private data class DashboardSources(
        val targetDate: LocalDate,
        val summary: DailyHealthSummaryEntity?,
        val score: RecoveryScoreEntity?,
        val sleepSessions: List<SleepSessionEntity>,
        val allWorkouts: List<ExerciseSessionEntity>,
        val recentSummaries: List<DailyHealthSummaryEntity>
    )

    private data class SecondarySources(
        val profile: UserProfileEntity?,
        val latestWeight: WeightMeasurementEntity?,
        val widgets: List<DashboardWidget>,
        val latestBodyFatScan: BodyFatScanEntity?,
        val energy: EnergyBalanceEstimate?
    )

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun loadDashboardData() {
        viewModelScope.launch {
            _selectedDate.flatMapLatest { date ->
                val sleepHistoryStart = date.minusDays(31).atStartOfDay(zone).toInstant()
                val sleepHistoryEnd = date.plusDays(1).atStartOfDay(zone).toInstant()

                val coreFlow = combine(
                    dailySummaryRepository.getSummaryForDate(date),
                    dailySummaryRepository.getRecoveryScoreForDate(date),
                    sleepRepository.getSessionsBetween(sleepHistoryStart, sleepHistoryEnd),
                    workoutRepository.getAllSessions(),
                    dailySummaryRepository.getSummariesBetween(date.minusDays(29), date.minusDays(1))
                ) { summary, score, sleepSessions, allWorkouts, recentSummaries ->
                    DashboardSources(date, summary, score, sleepSessions, allWorkouts, recentSummaries)
                }

                val energyFlow = bodyMetricsRepository?.observe()?.map { it.input.energy?.estimate } ?: flowOf(null)
                val secondaryFlow = combine(
                    userProfileRepository.getProfile(),
                    weightDao.getLatestWeight(),
                    dashboardWidgetRepository.getWidgets(),
                    bodyFatRepository.getLatestScan(),
                    energyFlow
                ) { profile, latestWeight, widgets, latestBodyFatScan, energy ->
                    SecondarySources(profile, latestWeight, widgets, latestBodyFatScan, energy)
                }

                combine(coreFlow, secondaryFlow) { core, secondary ->
                    Pair(core, secondary)
                }
            }.collect { (core, secondary) ->
                val (date, summary, score, sleepSessions, allWorkouts, recentSummaries) = core
                val (profile, latestWeight, widgets, latestBodyFatScan, energy) = secondary

                // Sorted newest-first: the most recent session is "current", the rest is real
                // baseline history (fixes the previous bug of always passing an empty history).
                // The selected day's night is the one that ended on it; only earlier nights are
                // history. A day with no sleep of its own shows none, not an older night.
                val sortedSleep = sleepSessions.sortedByDescending { it.startTime }
                val latestSleep = sortedSleep.firstOrNull { it.endTime.atZone(zone).toLocalDate() == date }
                val sleepBaselineHistory = sortedSleep.filter { it.endTime.atZone(zone).toLocalDate().isBefore(date) }
                val sleepGoal = profile?.sleepGoalMinutes ?: 480

                // Strain, recovery, and sleep score should only surface once today's sleep is in -
                // before that (i.e. from midnight until a session is logged/synced) they read as
                // "pending" rather than showing a zero or yesterday's stale value. For past days,
                // sleep is never "pending".
                val isToday = date == LocalDate.now(zone)
                val sleepRecordedForDate = sortedSleep.any { session ->
                    session.endTime.atZone(zone).toLocalDate() == date
                }

                val previousDaySummary = recentSummaries.firstOrNull { it.date == date.minusDays(1) }
                // Same 7 days as the sync uses, so tonight's need matches the stored one.
                val rollingAverageStrain = recentSummaries.filter { !it.date.isBefore(date.minusDays(7)) }
                    .mapNotNull { it.dayStrain }
                    .let { if (it.isNotEmpty()) it.average() else null }

                val sleepAnalysis = sleepCalculator.analyzeSleep(
                    currentSession = latestSleep,
                    recentSessions = sleepBaselineHistory,
                    targetDurationMinutes = sleepGoal,
                    previousDayStrain = previousDaySummary?.dayStrain,
                    rollingAverageStrain = rollingAverageStrain
                )
                val todaysWorkouts = allWorkouts.filter {
                    it.startTime.atZone(zone).toLocalDate() == date
                }
                val trailingSevenDayWorkouts = allWorkouts.filter {
                    val wDate = it.startTime.atZone(zone).toLocalDate()
                    wDate >= date.minusDays(7) && wDate < date
                }
                val trainingAnalysis = trainingLoadCalculator.calculateDailyLoad(
                    workoutsToday = todaysWorkouts,
                    recentWorkoutsHistory = trailingSevenDayWorkouts
                )

                val recoveryState = try {
                    RecoveryState.valueOf(score?.state ?: RecoveryState.BUILDING_BASELINE.name)
                } catch (_: IllegalArgumentException) {
                    RecoveryState.BUILDING_BASELINE
                }
                // The target is a band around your own recent strain; with no history yet there's
                // nothing personal to base it on, so no target rather than a generic one.
                // The week before, like the sync uses to judge yesterday's strain against its band.
                val recentStrain = recentSummaries.filter { !it.date.isBefore(date.minusDays(7)) }.mapNotNull { it.dayStrain }
                val strainRecommendation = recentStrain.takeIf { it.isNotEmpty() }?.let {
                    strainCalculator.recommendStrainTarget(recoveryState = recoveryState, recentDailyStrain = it)
                }

                // Workouts count in full - every calorie burned during them, not just the extra.
                // The stored summary adds every step on top (see ActiveActivityCalculator); before
                // today's first sync lands, fall back to the workouts alone.
                val exerciseCalories = todaysWorkouts.sumOf { ActiveActivityCalculator.workoutCalories(it, summary?.bmrCalories) ?: 0.0 }
                val effectiveActiveCalories = summary?.activeCalories ?: if (exerciseCalories > 0.0) exerciseCalories else null
                val activeMinutes = summary?.activeMinutes
                    ?: max(summary?.exerciseDurationMinutes ?: 0, todaysWorkouts.sumOf { it.durationMinutes })
                val activeCalories = effectiveActiveCalories ?: 0.0

                val calorieBurn = calorieCalculator.calculateDailyBurn(
                    bmr = summary?.bmrCalories,
                    totalActiveCalories = effectiveActiveCalories,
                    exerciseCalories = exerciseCalories,
                    activeMinutes = activeMinutes
                )
                // Built from the last 7 full days of steps and workouts rather than today's still
                // accumulating burn, so the target doesn't swing through the day. With no goal
                // set, the target is simply maintenance; with too little data, it stays blank.
                val calorieGoal = energy?.let { e ->
                    e.goal ?: CalorieGoalRecommendation(
                        dailyCalorieTarget = e.maintenanceKcal.roundToInt(),
                        weeklyPaceKg = 0.0,
                        isPaceAdjustedForSafety = false,
                        guidance = "Eat about this much to stay at your current weight, based on your last 7 days of steps and workouts."
                    )
                }

                // Autonomic stress calculation based on rolling 7d baseline of HRV & RHR
                val trailing7Summaries = recentSummaries.take(7)
                val hrvVals = trailing7Summaries.mapNotNull { it.hrvRmssd }
                val rhrVals = trailing7Summaries.mapNotNull { it.restingHeartRate }
                val hrvBaseline7d = if (hrvVals.isNotEmpty()) hrvVals.average() else null
                val rhrBaseline7d = if (rhrVals.isNotEmpty()) rhrVals.average() else null
                val baselineDaysCount = trailing7Summaries.count { it.hrvRmssd != null || it.restingHeartRate != null }

                val stressResult = stressCalculator.calculateStress(
                    hrvToday = summary?.hrvRmssd,
                    hrvBaseline7d = hrvBaseline7d,
                    rhrToday = summary?.restingHeartRate,
                    rhrBaseline7d = rhrBaseline7d,
                    dayStrain = summary?.dayStrain,
                    baselineDaysCount = baselineDaysCount
                )

                val stepGoal = profile?.dailyStepGoal ?: 6000L
                val minutesGoal = profile?.dailyActivityMinutesGoal ?: 90
                val caloriesGoal = profile?.dailyActiveCaloriesGoal ?: 500

                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        selectedDate = date,
                        dailySummary = summary,
                        recoveryScore = score,
                        sleepAnalysis = sleepAnalysis,
                        previousDayStrain = previousDaySummary?.dayStrain,
                        heartRateZones = com.kevan.hangry.domain.calculation.HeartRateZones.forUser(
                            profile?.age, profile?.maxHeartRate,
                            recentSummaries.mapNotNull { it.restingHeartRate }.takeIf { it.isNotEmpty() }?.average()
                        ),
                        trainingAnalysis = trainingAnalysis,
                        strainRecommendation = strainRecommendation,
                        calorieBurn = calorieBurn,
                        calorieGoal = calorieGoal,
                        stressResult = stressResult,
                        dailyStepGoal = stepGoal,
                        dailyActivityMinutesGoal = minutesGoal,
                        sleepGoalMinutes = sleepGoal,
                        dailyActiveCaloriesGoal = caloriesGoal,
                        todayActiveMinutes = activeMinutes,
                        todayActiveCalories = activeCalories,
                        widgets = widgets,
                        latestWeightKg = latestWeight?.weightKg,
                        latestBodyFatScan = latestBodyFatScan,
                        aiFeaturesEnabled = profile?.aiFeaturesEnabled ?: false,
                        isPendingSleepData = isToday && !sleepRecordedForDate
                    )
                }

                // A sleep session just landed but the persisted summary/recovery row for today
                // predates it (still stuck at the last sync) - refresh just today so the newly
                // unlocked strain/recovery/sleep numbers are accurate, not the pre-sleep values.
                val summaryStaleRelativeToSleep = isToday && sleepRecordedForDate && summary?.sleepDurationMinutes == null
                if (summaryStaleRelativeToSleep && autoResyncedForDate != date && !_uiState.value.isSyncing) {
                    autoResyncedForDate = date
                    syncNow(days = 1)
                }
            }
        }

        // Refresh from Health Connect when the app starts, not just on the hourly background
        // sync - a full historical import if the database is still empty (first run), otherwise a
        // quick recent-days catch-up. Coming back to a running app is handled by onAppResumed().
        viewModelScope.launch {
            // e.g. missing HRV now scores as excellent - refresh scores saved under the old rules.
            healthSyncManager.recalculateIfScoringChanged()
            val existing = dailySummaryRepository.getSummaryForDateSync(_selectedDate.value)
            if (existing == null) {
                syncNow()
            } else {
                syncNow(days = 3)
            }
        }
    }

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
    }

    fun goToPreviousDay() {
        _selectedDate.value = _selectedDate.value.minusDays(1)
    }

    fun goToNextDay() {
        val current = _selectedDate.value
        if (current < LocalDate.now(zone)) {
            _selectedDate.value = current.plusDays(1)
        }
    }

    fun goToToday() {
        _selectedDate.value = LocalDate.now(zone)
    }

    /** Recomputes the habit streaks (step goal, meals, sleep goal, supplements) and Body Age. */
    fun refreshHabits() {
        bodyAgeLoader?.let { loader ->
            viewModelScope.launch {
                val snapshot = runCatching { withContext(Dispatchers.IO) { loader.load(LocalDate.now(zone), zone) } }.getOrNull()
                _uiState.update { it.copy(bodyAge = snapshot) }
            }
        }
        val loader = streaksLoader ?: return
        viewModelScope.launch {
            val profile = userProfileRepository.getProfileSync()
            val streaks = runCatching {
                withContext(Dispatchers.IO) {
                    loader.load(LocalDate.now(zone), profile?.dailyStepGoal ?: 6000L, profile?.sleepGoalMinutes ?: 480, zone)
                }
            }.getOrDefault(emptyList())
            _uiState.update { it.copy(streaks = streaks) }
        }
    }

    fun updateActivityGoals(stepGoal: Long, caloriesGoal: Int, activeMinutesGoal: Int? = null) {
        viewModelScope.launch {
            val currentProfile = userProfileRepository.getProfileSync() ?: UserProfileEntity()
            val minutesGoal = activeMinutesGoal ?: currentProfile.dailyActivityMinutesGoal
            userProfileRepository.saveProfile(
                currentProfile.copy(
                    dailyStepGoal = stepGoal,
                    dailyActiveCaloriesGoal = caloriesGoal,
                    dailyActivityMinutesGoal = minutesGoal
                )
            )
            _uiState.update {
                it.copy(
                    dailyStepGoal = stepGoal,
                    dailyActiveCaloriesGoal = caloriesGoal,
                    dailyActivityMinutesGoal = minutesGoal
                )
            }
            refreshHabits()
        }
    }

    fun setCustomizeSheetVisible(visible: Boolean) {
        _uiState.update { it.copy(showCustomizeSheet = visible) }
    }

    fun toggleWidgetVisibility(widgetId: String, isVisible: Boolean) {
        viewModelScope.launch {
            dashboardWidgetRepository.toggleWidgetVisibility(widgetId, isVisible)
        }
    }

    fun reorderWidgets(orderedIds: List<String>) {
        viewModelScope.launch {
            dashboardWidgetRepository.reorderWidgets(orderedIds)
        }
    }

    fun addCustomWidget(widget: DashboardWidget) {
        viewModelScope.launch {
            dashboardWidgetRepository.addCustomWidget(widget)
        }
    }

    fun removeWidget(widgetId: String) {
        viewModelScope.launch {
            dashboardWidgetRepository.removeWidget(widgetId)
        }
    }

    fun resetWidgetLayout() {
        viewModelScope.launch {
            dashboardWidgetRepository.resetToDefault()
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun syncNow(days: Int = 30) {
        viewModelScope.launch {
            // A new attempt clears the last sync error card.
            _uiState.update { it.copy(isSyncing = true, syncStatusMessage = "Syncing health data…", errorMessage = null) }
            healthSyncManager.syncHistorical(days = days).collect { progress ->
                when (progress.status) {
                    SyncStatus.IN_PROGRESS -> {
                        _uiState.update {
                            it.copy(
                                isSyncing = true,
                                syncStatusMessage = "Reading ${progress.currentDataType.lowercase()}…"
                            )
                        }
                    }
                    SyncStatus.SUCCESS -> {
                        _uiState.update {
                            it.copy(
                                isSyncing = false,
                                syncStatusMessage = null,
                                lastSyncFormatted = formatTime(Instant.now().also { lastSyncCompletedAt = it })
                            )
                        }
                        refreshHabits()
                    }
                    SyncStatus.FAILED -> {
                        _uiState.update {
                            it.copy(
                                isSyncing = false,
                                syncStatusMessage = null,
                                errorMessage = progress.errorMessage
                            )
                        }
                    }
                    SyncStatus.IDLE -> Unit
                }
            }
        }
    }

    private fun formatTime(instant: Instant): String {
        return DateTimeFormatter.ofPattern("h:mm a")
            .withZone(zone)
            .format(instant)
    }

    companion object {
        fun provideFactory(
            dailySummaryRepository: DailySummaryRepository,
            sleepRepository: SleepRepository,
            workoutRepository: WorkoutRepository,
            healthSyncManager: HealthSyncManager,
            userProfileRepository: UserProfileRepository,
            weightDao: WeightDao,
            sleepCalculator: SleepCalculator,
            trainingLoadCalculator: TrainingLoadCalculator,
            strainCalculator: StrainCalculator,
            calorieCalculator: CalorieCalculator,
            stressCalculator: StressCalculator,
            dashboardWidgetRepository: DashboardWidgetRepository,
            bodyFatRepository: BodyFatRepository,
            bodyMetricsRepository: BodyMetricsRepository? = null,
            streaksLoader: StreaksLoader? = null,
            bodyAgeLoader: BodyAgeLoader? = null
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return DashboardViewModel(
                    dailySummaryRepository = dailySummaryRepository,
                    sleepRepository = sleepRepository,
                    workoutRepository = workoutRepository,
                    healthSyncManager = healthSyncManager,
                    userProfileRepository = userProfileRepository,
                    weightDao = weightDao,
                    sleepCalculator = sleepCalculator,
                    trainingLoadCalculator = trainingLoadCalculator,
                    strainCalculator = strainCalculator,
                    calorieCalculator = calorieCalculator,
                    stressCalculator = stressCalculator,
                    dashboardWidgetRepository = dashboardWidgetRepository,
                    bodyFatRepository = bodyFatRepository,
                    bodyMetricsRepository = bodyMetricsRepository,
                    streaksLoader = streaksLoader,
                    bodyAgeLoader = bodyAgeLoader
                ) as T
            }
        }
    }
}

/** Coming back to the app after this long triggers a catch-up sync. */
private val RESUME_SYNC_AFTER: java.time.Duration = java.time.Duration.ofMinutes(15)
