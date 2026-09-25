package com.kevan.hangry.di

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import com.kevan.hangry.data.ai.OpenRouterAiCoachService
import com.kevan.hangry.data.ai.OpenRouterBodyFatAnalyzer
import com.kevan.hangry.data.ai.OpenRouterClient
import com.kevan.hangry.data.ai.OpenRouterFoodAnalyzer
import com.kevan.hangry.data.ai.OpenRouterPostureAnalyzer
import com.kevan.hangry.data.ai.OpenRouterSupplementAnalyzer
import com.kevan.hangry.data.coach.CoachActionExecutor
import com.kevan.hangry.data.supplements.SupplementReminderScheduler
import com.kevan.hangry.domain.ai.SupplementAnalyzer
import com.kevan.hangry.data.breathing.BreathingSessionController
import com.kevan.hangry.data.datasource.HealthConnectDataSource
import com.kevan.hangry.data.datasource.RealHealthConnectDataSource
import com.kevan.hangry.data.local.HangryDatabase
import com.kevan.hangry.data.repository.*
import com.kevan.hangry.data.security.SecureKeyStore
import com.kevan.hangry.domain.ai.AiCoachContextBuilder
import com.kevan.hangry.domain.ai.AiCoachService
import com.kevan.hangry.domain.ai.BodyFatAnalyzer
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
    val bodyFatCalculator: BodyFatCalculator
    val bodyMetricsCalculator: BodyMetricsCalculator
    val energyBalanceCalculator: EnergyBalanceCalculator
    val bodyMetricsRepository: BodyMetricsRepository
    val healthRecordsRepository: HealthRecordsRepository
    val supplementAnalyzer: SupplementAnalyzer
    val supplementRepository: SupplementRepository
    val fastingRepository: com.kevan.hangry.domain.repository.FastingRepository
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
    val localStorageManager: LocalStorageManager
    val bodyFatRepository: BodyFatRepository
    val breathingRepository: BreathingRepository
    val breathingSessionController: BreathingSessionController

    // AI features (opt-in): OpenRouter-backed calorie & posture analysis + AI Coach
    val secureKeyStore: SecureKeyStore
    val openRouterClient: OpenRouterClient
    val foodAnalyzer: FoodAnalyzer
    val postureAnalyzer: PostureAnalyzer
    val bodyFatAnalyzer: BodyFatAnalyzer
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

    override val bodyFatCalculator: BodyFatCalculator by lazy {
        HangryBodyFatCalculator()
    }

    override val bodyMetricsCalculator: BodyMetricsCalculator by lazy {
        BodyMetricsCalculator(calorieCalculator)
    }

    override val energyBalanceCalculator: EnergyBalanceCalculator by lazy {
        EnergyBalanceCalculator(calorieCalculator)
    }

    override val bodyMetricsRepository: BodyMetricsRepository by lazy {
        DefaultBodyMetricsRepository(
            userProfileRepository = userProfileRepository,
            weightDao = database.weightDao(),
            heightDao = database.heightDao(),
            bodyFatRepository = bodyFatRepository,
            dailyHealthSummaryDao = database.dailyHealthSummaryDao(),
            exerciseSessionDao = database.exerciseSessionDao(),
            bodyMetricsCalculator = bodyMetricsCalculator,
            energyBalanceCalculator = energyBalanceCalculator
        )
    }

    override val healthRecordsRepository: HealthRecordsRepository by lazy {
        DefaultHealthRecordsRepository(database.healthRecordsDao(), userProfileRepository, healthConnectDataSource)
    }

    override val supplementAnalyzer: SupplementAnalyzer by lazy {
        OpenRouterSupplementAnalyzer(openRouterClient, secureKeyStore, userProfileRepository)
    }

    override val supplementRepository: SupplementRepository by lazy {
        DefaultSupplementRepository(
            context = context,
            dao = database.supplementDao(),
            analyzer = supplementAnalyzer,
            healthRecordsRepository = healthRecordsRepository,
            scheduler = SupplementReminderScheduler(context)
        )
    }

    override val fastingRepository: com.kevan.hangry.domain.repository.FastingRepository by lazy {
        com.kevan.hangry.data.repository.DefaultFastingRepository(
            context = context,
            dao = database.fastDao(),
            prefs = com.kevan.hangry.data.fasting.FastingPrefs(context),
            scheduler = com.kevan.hangry.data.fasting.FastingReminderScheduler(context)
        )
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
            calorieCalculator = calorieCalculator,
            prefs = context.getSharedPreferences("hangry_sync", android.content.Context.MODE_PRIVATE)
        )
    }

    override val userProfileRepository: UserProfileRepository by lazy {
        DefaultUserProfileRepository(database.userProfileDao())
    }

    override val localExportManager: LocalExportManager by lazy {
        DefaultLocalExportManager(dailySummaryRepository)
    }

    override val localStorageManager: LocalStorageManager by lazy {
        com.kevan.hangry.data.storage.DefaultLocalStorageManager(
            context = context,
            database = database,
            foodLogDao = database.foodLogDao(),
            postureScanDao = database.postureScanDao()
        )
    }

    override val bodyFatRepository: BodyFatRepository by lazy {
        DefaultBodyFatRepository(database.bodyFatScanDao())
    }

    override val breathingRepository: BreathingRepository by lazy {
        DefaultBreathingRepository(database.breathingSessionDao(), healthConnectDataSource)
    }

    override val breathingSessionController: BreathingSessionController by lazy {
        BreathingSessionController(context, breathingRepository)
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

    override val bodyFatAnalyzer: BodyFatAnalyzer by lazy {
        OpenRouterBodyFatAnalyzer(openRouterClient, secureKeyStore, userProfileRepository)
    }

    override val foodLogRepository: FoodLogRepository by lazy {
        DefaultFoodLogRepository(database.foodLogDao(), mealPlanRepository)
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
            coachJournalDao = database.coachJournalDao(),
            bodyMetricsRepository = bodyMetricsRepository,
            healthRecordsRepository = healthRecordsRepository,
            supplementRepository = supplementRepository,
            fastingRepository = fastingRepository,
            bodyAgeLoader = BodyAgeLoader(database),
            streaksLoader = StreaksLoader(database, fastingRepository),
            strainCalculator = strainCalculator
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
            aiCoachService = aiCoachService,
            context = context,
            actionExecutor = CoachActionExecutor(
                supplementRepository = supplementRepository,
                healthRecordsRepository = healthRecordsRepository,
                foodLogRepository = foodLogRepository,
                writeNutritionRecord = healthConnectDataSource::writeNutritionRecord,
                userProfileRepository = userProfileRepository,
                weightDao = database.weightDao(),
                mealPlanRepository = mealPlanRepository,
                sleepRepository = sleepRepository,
                bodyFatRepository = bodyFatRepository,
                healthSyncManager = healthSyncManager,
                exerciseSessionDao = database.exerciseSessionDao(),
                coachJournalDao = database.coachJournalDao(),
                context = context
            )
        )
    }
}
