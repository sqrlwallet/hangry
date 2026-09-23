package com.kevan.hangry.ui.dashboard

import com.kevan.hangry.data.local.entity.DailyHealthSummaryEntity
import com.kevan.hangry.data.local.entity.RecoveryScoreEntity
import com.kevan.hangry.domain.model.CalorieBurnResult
import com.kevan.hangry.domain.model.CalorieGoalRecommendation
import com.kevan.hangry.domain.model.DashboardWidget
import com.kevan.hangry.domain.model.SleepAnalysis
import com.kevan.hangry.domain.model.StrainRecommendation
import com.kevan.hangry.domain.model.StressResult
import com.kevan.hangry.domain.model.TrainingLoadAnalysis
import java.time.LocalDate

data class DashboardUiState(
    val isLoading: Boolean = true,
    val isSyncing: Boolean = false,
    val selectedDate: LocalDate = LocalDate.now(),
    val dailySummary: DailyHealthSummaryEntity? = null,
    val recoveryScore: RecoveryScoreEntity? = null,
    val sleepAnalysis: SleepAnalysis? = null,
    val trainingAnalysis: TrainingLoadAnalysis? = null,
    val strainRecommendation: StrainRecommendation? = null,
    val calorieBurn: CalorieBurnResult? = null,
    val calorieGoal: CalorieGoalRecommendation? = null,
    val stressResult: StressResult? = null,
    val dailyStepGoal: Long = 6000L,
    val dailyActivityMinutesGoal: Int = 90,
    val dailyActiveCaloriesGoal: Int = 500,
    val todayActiveMinutes: Int = 0,
    val todayActiveCalories: Double = 0.0,
    val isActivityExpanded: Boolean = false,
    val widgets: List<DashboardWidget> = emptyList(),
    val showCustomizeSheet: Boolean = false,
    val latestWeightKg: Double? = null,
    val syncStatusMessage: String? = null,
    val lastSyncFormatted: String? = null,
    val isOfflineMode: Boolean = false,
    val errorMessage: String? = null,
    val aiFeaturesEnabled: Boolean = false,
    // True from midnight until today's sleep is recorded (synced or logged manually) - while
    // true, Strain/Recovery/Sleep Score are withheld from the UI and shown as "Pending" instead
    // of a zero or a stale carried-over value.
    val isPendingSleepData: Boolean = true
) {
    val isViewingToday: Boolean get() = selectedDate == LocalDate.now()
}
