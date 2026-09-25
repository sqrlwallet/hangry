package com.kevan.hangry.ui.dashboard

import com.kevan.hangry.data.local.entity.BodyFatScanEntity
import com.kevan.hangry.data.local.entity.DailyHealthSummaryEntity
import com.kevan.hangry.data.local.entity.RecoveryScoreEntity
import com.kevan.hangry.data.repository.BodyAgeSnapshot
import com.kevan.hangry.domain.calculation.Streak
import com.kevan.hangry.domain.model.CalorieBurnResult
import com.kevan.hangry.domain.model.CalorieGoalRecommendation
import com.kevan.hangry.domain.model.DashboardWidget
import com.kevan.hangry.domain.model.HrvFeeling
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
    /** The day before's final strain; Today leads with it. */
    val previousDayStrain: Double? = null,
    /** Personal heart-rate zones (max HR from Settings or age, usual resting HR). */
    val heartRateZones: com.kevan.hangry.domain.calculation.HeartRateZones = com.kevan.hangry.domain.calculation.HeartRateZones.DEFAULT,
    val sleepAnalysis: SleepAnalysis? = null,
    val trainingAnalysis: TrainingLoadAnalysis? = null,
    val strainRecommendation: StrainRecommendation? = null,
    val calorieBurn: CalorieBurnResult? = null,
    val calorieGoal: CalorieGoalRecommendation? = null,
    val stressResult: StressResult? = null,
    val dailyStepGoal: Long = 6000L,
    val dailyActivityMinutesGoal: Int = 90,
    val sleepGoalMinutes: Int = 480,
    /** Habits with data, e.g. step goal and meals logged; empty until loaded. */
    val streaks: List<Streak> = emptyList(),
    /** Null until loaded. */
    val bodyAge: BodyAgeSnapshot? = null,
    val dailyActiveCaloriesGoal: Int = 500,
    val todayActiveMinutes: Int = 0,
    val todayActiveCalories: Double = 0.0,
    val widgets: List<DashboardWidget> = emptyList(),
    val showCustomizeSheet: Boolean = false,
    val latestWeightKg: Double? = null,
    val latestBodyFatScan: BodyFatScanEntity? = null,
    val syncStatusMessage: String? = null,
    val lastSyncFormatted: String? = null,
    val errorMessage: String? = null,
    val aiFeaturesEnabled: Boolean = false,
    // True from midnight until today's sleep is recorded (synced or logged manually) - while
    // true, Strain/Recovery/Sleep Score are withheld from the UI and shown as "Pending" instead
    // of a zero or a stale carried-over value.
    val isPendingSleepData: Boolean = true,
    /** Stand-in for HRV on the selected day when no reading exists (default Excellent). */
    val hrvFeeling: HrvFeeling = HrvFeeling.DEFAULT
) {
    val isViewingToday: Boolean get() = selectedDate == LocalDate.now()
}
