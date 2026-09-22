package com.kevan.hangry.di

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import com.kevan.hangry.data.ai.OpenRouterAiCoachService
import com.kevan.hangry.data.ai.OpenRouterClient
import com.kevan.hangry.data.ai.OpenRouterFoodAnalyzer
import com.kevan.hangry.data.ai.OpenRouterPostureAnalyzer
import com.kevan.hangry.data.datasource.HealthConnectDataSource
import com.kevan.hangry.data.datasource.RealHealthConnectDataSource
import com.kevan.hangry.data.local.HangryDatabase
import com.kevan.hangry.data.repository.*
import com.kevan.hangry.data.security.SecureKeyStore
import com.kevan.hangry.domain.ai.AiCoachContextBuilder
import com.kevan.hangry.domain.ai.AiCoachService
import com.kevan.hangry.domain.ai.FoodAnalyzer
import com.kevan.hangry.domain.ai.PostureAnalyzer
import com.kevan.hangry.domain.calculation.*
import com.kevan.hangry.domain.repository.*

interface AppContainer {
    val database: HangryDatabase
    val healthConnectDataSource: HealthConnectDataSource
    val healthConnectProviderStatus: Int
    val recoveryCalculator: RecoveryCalculator
    val sleepCalculator: SleepCalculator
    val trainingLoadCalculator: TrainingLoadCalculator
    val strainCalculator: StrainCalculator
    val calorieCalculator: CalorieCalculator
    val stressCalculator: StressCalculator
    val dashboardWidgetRepository: DashboardWidgetRepository

    val sleepRepository: SleepRepository
    val workoutRepository: WorkoutRepository
    val heartRateRepository: HeartRateRepository
    val hrvRepository: HRVRepository
    val activityRepository: ActivityRepository
    val dailySummaryRepository: DailySummaryRepository
    val healthSyncManager: HealthSyncManager
    val userProfileRepository: UserProfileRepository
    val localExportManager: LocalExportManager

    // AI features (opt-in): OpenRouter-backed calorie & posture analysis + AI Coach
    val secureKeyStore: SecureKeyStore
    val openRouterClient: OpenRouterClient
    val foodAnalyzer: FoodAnalyzer
    val postureAnalyzer: PostureAnalyzer
    val foodLogRepository: FoodLogRepository
    val mealPlanRepository: MealPlanRepository
    val postureScanRepository: PostureScanRepository
    val aiCoachService: AiCoachService
    val coachRepository: CoachRepository
}

class DefaultAppContainer(private val context: Context) : AppContainer {

    override val database: HangryDatabase by lazy {
        HangryDatabase.getDatabase(context)
    }

    override val healthConnectProviderStatus: Int by lazy {
        try {
            HealthConnectClient.getSdkStatus(context)
        } catch (_: Exception) {
            HealthConnectClient.SDK_UNAVAILABLE
        }
    }

    // Always the real source - it already degrades to empty results (not fabricated ones) when
    // Health Connect isn't installed/available, so there's no separate fake/demo data path here.
    override val healthConnectDataSource: HealthConnectDataSource by lazy {
        RealHealthConnectDataSource(context)
    }

    override val recoveryCalculator: RecoveryCalculator by lazy {
        HangryRecoveryCalculator()
    }

    override val sleepCalculator: SleepCalculator by lazy {
        HangrySleepCalculator()
    }

    override val trainingLoadCalculator: TrainingLoadCalculator by lazy {
        HangryTrainingLoadCalculator()
    }

    override val strainCalculator: StrainCalculator by lazy {
        HangryStrainCalculator(trainingLoadCalculator)
    }

    override val calorieCalculator: CalorieCalculator by lazy {
        HangryCalorieCalculator()
    }

    override val stressCalculator: StressCalculator by lazy {
        HangryStressCalculator()
    }

    override val dashboardWidgetRepository: DashboardWidgetRepository by lazy {
        DefaultDashboardWidgetRepository(context)
    }

    override val sleepRepository: SleepRepository by lazy {
        DefaultSleepRepository(database.sleepSessionDao())
    }

    override val workoutRepository: WorkoutRepository by lazy {
        DefaultWorkoutRepository(database.exerciseSessionDao())
    }

    override val heartRateRepository: HeartRateRepository by lazy {
        DefaultHeartRateRepository(database.restingHeartRateDao(), database.heartRateDao())
    }

    override val hrvRepository: HRVRepository by lazy {
        DefaultHrvRepository(database.hrvDao())
    }

    override val activityRepository: ActivityRepository by lazy {
        DefaultActivityRepository(database.stepsDao())
    }

    override val dailySummaryRepository: DailySummaryRepository by lazy {
        DefaultDailySummaryRepository(database.dailyHealthSummaryDao(), database.recoveryScoreDao())
    }

    override val healthSyncManager: HealthSyncManager by lazy {
        DefaultHealthSyncManager(
            database = database,
            dataSource = healthConnectDataSource,
            recoveryCalculator = recoveryCalculator,
            sleepCalculator = sleepCalculator,
            trainingLoadCalculator = trainingLoadCalculator,
            strainCalculator = strainCalculator,
            calorieCalculator = calorieCalculator
        )
    }

    override val userProfileRepository: UserProfileRepository by lazy {
        DefaultUserProfileRepository(database.userProfileDao())
    }

    override val localExportManager: LocalExportManager by lazy {
        DefaultLocalExportManager(dailySummaryRepository)
    }

    override val secureKeyStore: SecureKeyStore by lazy {
        SecureKeyStore(context)
    }

    override val openRouterClient: OpenRouterClient by lazy {
        OpenRouterClient()
    }

    override val foodAnalyzer: FoodAnalyzer by lazy {
        OpenRouterFoodAnalyzer(openRouterClient, secureKeyStore, userProfileRepository)
    }

    override val postureAnalyzer: PostureAnalyzer by lazy {
        OpenRouterPostureAnalyzer(openRouterClient, secureKeyStore, userProfileRepository)
    }

    override val foodLogRepository: FoodLogRepository by lazy {
        DefaultFoodLogRepository(database.foodLogDao())
    }

    override val mealPlanRepository: MealPlanRepository by lazy {
        DefaultMealPlanRepository(database.mealPlanDao())
    }

    override val postureScanRepository: PostureScanRepository by lazy {
        DefaultPostureScanRepository(database.postureScanDao())
    }

    private val aiCoachContextBuilder: AiCoachContextBuilder by lazy {
        AiCoachContextBuilder(
            userProfileRepository = userProfileRepository,
            weightDao = database.weightDao(),
            dailyHealthSummaryDao = database.dailyHealthSummaryDao(),
            recoveryScoreDao = database.recoveryScoreDao(),
            exerciseSessionDao = database.exerciseSessionDao(),
            sleepSessionDao = database.sleepSessionDao(),
            foodLogDao = database.foodLogDao(),
            postureScanDao = database.postureScanDao(),
            coachJournalDao = database.coachJournalDao()
        )
    }

    override val aiCoachService: AiCoachService by lazy {
        OpenRouterAiCoachService(
            client = openRouterClient,
            keyStore = secureKeyStore,
            userProfileRepository = userProfileRepository,
            contextBuilder = aiCoachContextBuilder
        )
    }

    override val coachRepository: CoachRepository by lazy {
        DefaultCoachRepository(
            coachJournalDao = database.coachJournalDao(),
            coachMessageDao = database.coachMessageDao(),
            aiCoachService = aiCoachService
        )
    }
}
