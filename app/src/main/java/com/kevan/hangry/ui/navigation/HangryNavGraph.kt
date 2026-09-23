package com.kevan.hangry.ui.navigation

import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.kevan.hangry.data.repository.BodyAgeLoader
import com.kevan.hangry.data.repository.StreaksLoader
import com.kevan.hangry.ui.bodyage.BodyAgeScreen
import com.kevan.hangry.ui.components.millisUntilNextMidnight
import com.kevan.hangry.ui.theme.HangryTheme
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.kevan.hangry.data.breathing.BreathingSessionState
import com.kevan.hangry.ui.more.MoreScreen
import com.kevan.hangry.util.OnboardingFlag
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.kevan.hangry.data.local.entity.UserProfileEntity
import com.kevan.hangry.di.AppContainer
import com.kevan.hangry.ui.bodyfat.BodyFatCalculatorScreen
import com.kevan.hangry.ui.bodyfat.BodyFatCalculatorViewModel
import com.kevan.hangry.domain.model.BreathingPattern
import com.kevan.hangry.domain.model.BreathingStats
import com.kevan.hangry.ui.bodymetrics.BodyMetricsScreen
import com.kevan.hangry.ui.bodymetrics.BodyMetricsViewModel
import com.kevan.hangry.domain.model.HealthRecordsSnapshot
import com.kevan.hangry.domain.model.SupplementsSnapshot
import com.kevan.hangry.ui.supplements.SupplementsScreen
import com.kevan.hangry.ui.supplements.SupplementsViewModel
import com.kevan.hangry.ui.breathing.BreathingScreen
import com.kevan.hangry.ui.healthrecords.HealthRecordsScreen
import com.kevan.hangry.ui.healthrecords.HealthRecordsViewModel
import com.kevan.hangry.ui.breathing.BreathingViewModel
import com.kevan.hangry.domain.ai.DashInsights
import com.kevan.hangry.ui.coach.AiCoachScreen
import com.kevan.hangry.ui.coach.AiCoachViewModel
import com.kevan.hangry.ui.dashboard.DashboardScreen
import com.kevan.hangry.ui.dashboard.DashboardViewModel
import com.kevan.hangry.ui.heart.HeartMetricsScreen
import com.kevan.hangry.ui.nutrition.MealPlanScreen
import com.kevan.hangry.ui.nutrition.NutritionScreen
import com.kevan.hangry.ui.nutrition.NutritionViewModel
import com.kevan.hangry.ui.onboarding.PermissionSetupScreen
import com.kevan.hangry.ui.onboarding.WelcomeScreen
import com.kevan.hangry.ui.posture.PostureCaptureScreen
import com.kevan.hangry.ui.posture.PostureScanDetailScreen
import com.kevan.hangry.ui.posture.PostureScreen
import com.kevan.hangry.ui.posture.PostureViewModel
import com.kevan.hangry.ui.recovery.RecoveryDetailsScreen
import com.kevan.hangry.ui.settings.DataSourcesScreen
import com.kevan.hangry.ui.settings.SettingsScreen
import com.kevan.hangry.ui.sleep.SleepScreen
import com.kevan.hangry.ui.privacy.PrivacyPolicyScreen
import com.kevan.hangry.ui.sync.HistoricalSyncSetupScreen
import com.kevan.hangry.ui.sync.SyncProgressScreen
import com.kevan.hangry.ui.training.TrainingScreen
import com.kevan.hangry.ui.trends.TrendsScreen
import com.kevan.hangry.ui.widget.HomeScreenWidgetsScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.kevan.hangry.data.local.entity.WeightMeasurementEntity
import com.kevan.hangry.domain.model.BiologicalSex
import com.kevan.hangry.domain.model.HealthProfileKind
import com.kevan.hangry.ui.onboarding.AboutYouScreen
import com.kevan.hangry.ui.onboarding.ProfileExtrasScreen
import java.time.Instant

sealed class Screen(val route: String) {
    data object Welcome : Screen("welcome")
    data object AboutYou : Screen("about_you")
    data object ProfileExtras : Screen("profile_extras")
    data object PermissionSetup : Screen("permission_setup")
    data object HistoricalSyncSetup : Screen("historical_sync_setup")
    data object SyncProgress : Screen("sync_progress/{rangeDays}") {
        fun createRoute(rangeDays: Int) = "sync_progress/$rangeDays"
    }
    data object Dashboard : Screen("dashboard")
    data object RecoveryDetails : Screen("recovery_details")
    data object Sleep : Screen("sleep")
    data object Training : Screen("training")
    data object HeartMetrics : Screen("heart_metrics")
    data object Trends : Screen("trends")
    data object DataSources : Screen("data_sources")
    data object Settings : Screen("settings")
    data object PrivacyPolicy : Screen("privacy_policy")
    data object Nutrition : Screen("nutrition")
    data object MealPlan : Screen("meal_plan")
    data object Posture : Screen("posture")
    data object PostureCapture : Screen("posture_capture")
    data object PostureScanDetail : Screen("posture_scan_detail/{scanId}") {
        fun createRoute(scanId: Long) = "posture_scan_detail/$scanId"
    }
    data object AiCoach : Screen("ai_coach")
    data object HomeScreenWidgets : Screen("home_screen_widgets")
    data object BodyFatCalculator : Screen("body_fat_calculator")
    data object BodyMetrics : Screen("body_metrics")
    data object HealthRecords : Screen("health_records")
    data object Supplements : Screen("supplements")
    data object More : Screen("more")
    data object BodyAge : Screen("body_age")
    data object Breathing : Screen("breathing?pattern={pattern}&start={start}") {
        /**
         * A null pattern opens the screen as-is, e.g. to return to a running session.
         * [start] begins a session straight away (used by the home screen widget).
         */
        fun createRoute(patternId: String? = null, start: Boolean = false) = when {
            patternId == null -> "breathing"
            start -> "breathing?pattern=$patternId&start=true"
            else -> "breathing?pattern=$patternId"
        }
    }
}

@Composable
fun HangryNavGraph(
    navController: NavHostController,
    appContainer: AppContainer,
    modifier: Modifier = Modifier,
    startDestination: String = Screen.Dashboard.route,
    quickLogTrigger: Boolean = false,
    onQuickLogTriggerHandled: () -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    val finalStart = startDestination

    // HistoricalSyncSetup/SyncProgress are shared between first-run onboarding and a later
    // "Historical Sync Range" re-trigger from Settings - the step indicator (Step 2/3 of 3)
    // only makes sense in the former. Set true the moment Welcome's "Get Started" is tapped
    // (the only entry point into this flow, whether fresh install or a Reset Application
    // re-entry), false for good the moment onboarding actually completes.
    var onboardingInProgress by remember { mutableStateOf(false) }
    // cm/kg or ft/lb, chosen on "About you" and carried to the extras step.
    var onboardingImperial by rememberSaveable { mutableStateOf(false) }

    val dashboardViewModel: DashboardViewModel = viewModel(
        factory = DashboardViewModel.provideFactory(
            dailySummaryRepository = appContainer.dailySummaryRepository,
            sleepRepository = appContainer.sleepRepository,
            workoutRepository = appContainer.workoutRepository,
            healthSyncManager = appContainer.healthSyncManager,
            userProfileRepository = appContainer.userProfileRepository,
            weightDao = appContainer.database.weightDao(),
            sleepCalculator = appContainer.sleepCalculator,
            trainingLoadCalculator = appContainer.trainingLoadCalculator,
            strainCalculator = appContainer.strainCalculator,
            calorieCalculator = appContainer.calorieCalculator,
            stressCalculator = appContainer.stressCalculator,
            dashboardWidgetRepository = appContainer.dashboardWidgetRepository,
            bodyFatRepository = appContainer.bodyFatRepository,
            bodyMetricsRepository = appContainer.bodyMetricsRepository,
            streaksLoader = StreaksLoader(appContainer.database),
            bodyAgeLoader = BodyAgeLoader(appContainer.database)
        )
    )

    val postureContext = LocalContext.current
    val postureViewModel: PostureViewModel = viewModel(
        factory = PostureViewModel.provideFactory(
            context = postureContext,
            postureScanRepository = appContainer.postureScanRepository,
            postureAnalyzer = appContainer.postureAnalyzer,
            userProfileRepository = appContainer.userProfileRepository
        )
    )

    val nutritionViewModel: NutritionViewModel = viewModel(
        factory = NutritionViewModel.provideFactory(
            context = postureContext,
            foodLogRepository = appContainer.foodLogRepository,
            mealPlanRepository = appContainer.mealPlanRepository,
            foodAnalyzer = appContainer.foodAnalyzer,
            healthConnectDataSource = appContainer.healthConnectDataSource,
            userProfileRepository = appContainer.userProfileRepository,
            healthRecordsRepository = appContainer.healthRecordsRepository
        )
    )

    val aiCoachViewModel: AiCoachViewModel = viewModel(
        factory = AiCoachViewModel.provideFactory(
            coachRepository = appContainer.coachRepository,
            userProfileRepository = appContainer.userProfileRepository,
            secureKeyStore = appContainer.secureKeyStore,
            suggestionSource = {
                DashInsights.suggestions(
                    supplements = appContainer.supplementRepository.current(),
                    records = appContainer.healthRecordsRepository.current(),
                    bodyMetrics = appContainer.bodyMetricsRepository.current()
                )
            }
        )
    )

    val appContext = LocalContext.current.applicationContext

    // Keep "today" and the data fresh: on every return to the app, and at midnight if it's open.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                dashboardViewModel.onAppResumed()
                nutritionViewModel.onDayMaybeChanged()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(millisUntilNextMidnight())
            dashboardViewModel.onDayMaybeChanged()
            nutritionViewModel.onDayMaybeChanged()
        }
    }

    NavHost(
        navController = navController,
        startDestination = finalStart,
        modifier = modifier,
        enterTransition = { fadeIn(animationSpec = tween(220)) },
        exitTransition = { fadeOut(animationSpec = tween(220)) },
        popEnterTransition = { fadeIn(animationSpec = tween(220)) },
        popExitTransition = { fadeOut(animationSpec = tween(220)) }
    ) {
        // Onboarding Flow
        composable(Screen.Welcome.route) {
            // Immersive photo-backed onboarding keeps the warm dark treatment.
            HangryTheme(darkTheme = true) {
                WelcomeScreen(
                    onGetStarted = {
                        // Welcome only ever appears as onboarding's first step - a fresh install,
                        // or "Reset Application" re-entering it mid-session - so this is always the
                        // correct moment to (re)assert that the step indicator should show.
                        onboardingInProgress = true
                        navController.navigate(Screen.AboutYou.route)
                    }
                )
            }
        }

        composable(Screen.AboutYou.route) {
            HangryTheme(darkTheme = true) {
                // Loaded once before showing the form, so answers already given (going back, or
                // after a reset) prefill it.
                val stored by produceState<Pair<UserProfileEntity?, Double?>?>(null) {
                    value = appContainer.userProfileRepository.getProfileSync() to
                        appContainer.database.weightDao().getLatestWeightSync()?.weightKg
                }
                val (profile, latestWeight) = stored ?: return@HangryTheme
                AboutYouScreen(
                    profile = profile,
                    initialWeightKg = latestWeight ?: profile?.currentWeightKg,
                    imperial = onboardingImperial,
                    onImperialChange = { onboardingImperial = it },
                    onNavigateBack = { navController.popBackStack() },
                    onContinue = { basics ->
                        coroutineScope.launch {
                            val repo = appContainer.userProfileRepository
                            val existing = repo.getProfileSync() ?: UserProfileEntity()
                            repo.saveProfile(
                                existing.copy(
                                    dateOfBirth = basics.dateOfBirth,
                                    biologicalSex = basics.sex.name,
                                    heightCm = basics.heightCm,
                                    currentWeightKg = basics.weightKg,
                                    updatedAt = Instant.now()
                                )
                            )
                            // A weigh-in today, so calorie targets and the weight trend have a
                            // starting point before anything syncs.
                            if (latestWeight == null || kotlin.math.abs(latestWeight - basics.weightKg) >= 0.05) {
                                appContainer.database.weightDao().insertOrIgnore(
                                    listOf(
                                        WeightMeasurementEntity(
                                            recordFingerprint = "manual_entry_${Instant.now().toEpochMilli()}",
                                            timestamp = Instant.now(),
                                            weightKg = basics.weightKg,
                                            sourcePackageName = "com.kevan.hangry.manual"
                                        )
                                    )
                                )
                            }
                            navController.navigate(Screen.ProfileExtras.route)
                        }
                    },
                    onSkip = { navController.navigate(Screen.ProfileExtras.route) }
                )
            }
        }

        composable(Screen.ProfileExtras.route) {
            HangryTheme(darkTheme = true) {
                val loaded by produceState<UserProfileEntity?>(null) {
                    value = appContainer.userProfileRepository.getProfileSync() ?: UserProfileEntity()
                }
                val profile = loaded ?: return@HangryTheme
                ProfileExtrasScreen(
                    profile = profile,
                    sex = profile.biologicalSex?.let { runCatching { BiologicalSex.valueOf(it) }.getOrNull() },
                    currentWeightKg = profile.currentWeightKg,
                    imperial = onboardingImperial,
                    onNavigateBack = { navController.popBackStack() },
                    onDone = { extras ->
                        coroutineScope.launch {
                            val repo = appContainer.userProfileRepository
                            val existing = repo.getProfileSync() ?: UserProfileEntity()
                            repo.saveProfile(
                                existing.copy(
                                    weightGoalKg = extras.weightGoalKg ?: existing.weightGoalKg,
                                    goalTargetDate = extras.goalTargetDate ?: existing.goalTargetDate,
                                    sleepGoalMinutes = extras.sleepGoalMinutes ?: existing.sleepGoalMinutes,
                                    dailyStepGoal = extras.dailyStepGoal ?: existing.dailyStepGoal,
                                    neckCircumferenceCm = extras.neckCm ?: existing.neckCircumferenceCm,
                                    waistCircumferenceCm = extras.waistCm ?: existing.waistCircumferenceCm,
                                    hipCircumferenceCm = extras.hipCm ?: existing.hipCircumferenceCm,
                                    updatedAt = Instant.now()
                                )
                            )
                            val records = appContainer.healthRecordsRepository
                            val known = records.current().profileItems.map { it.kind to it.name.lowercase() }.toSet()
                            extras.allergies.filter { (HealthProfileKind.ALLERGY to it.lowercase()) !in known }
                                .forEach { records.addProfileItem(HealthProfileKind.ALLERGY, it, null) }
                            extras.conditions.filter { (HealthProfileKind.CONDITION to it.lowercase()) !in known }
                                .forEach { records.addProfileItem(HealthProfileKind.CONDITION, it, null) }
                            navController.navigate(Screen.PermissionSetup.route)
                        }
                    },
                    onSkip = { navController.navigate(Screen.PermissionSetup.route) }
                )
            }
        }

        composable(Screen.PermissionSetup.route) {
            // Immersive photo-backed onboarding keeps the warm dark treatment.
            HangryTheme(darkTheme = true) {
                PermissionSetupScreen(
                    dataSource = appContainer.healthConnectDataSource,
                    providerStatus = appContainer.healthConnectProviderStatus,
                    onProceedToHistoricalSync = {
                        navController.navigate(Screen.HistoricalSyncSetup.route)
                    }
                )
            }
        }

        composable(Screen.HistoricalSyncSetup.route) {
            // Immersive photo-backed onboarding keeps the warm dark treatment.
            HangryTheme(darkTheme = true) {
                HistoricalSyncSetupScreen(
                    onStartSync = { days ->
                        navController.navigate(Screen.SyncProgress.createRoute(days))
                    },
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    dataSource = appContainer.healthConnectDataSource,
                    showStepIndicator = onboardingInProgress
                )
            }
        }

        composable(
            route = Screen.SyncProgress.route,
            arguments = listOf(
                navArgument("rangeDays") {
                    type = NavType.IntType
                    defaultValue = 30
                }
            )
        ) { backStackEntry ->
            val rangeDays = backStackEntry.arguments?.getInt("rangeDays") ?: 30
            // Immersive photo-backed onboarding keeps the warm dark treatment.
            HangryTheme(darkTheme = true) {
                SyncProgressScreen(
                    rangeDays = rangeDays,
                    syncManager = appContainer.healthSyncManager,
                    onComplete = {
                        coroutineScope.launch {
                            val existing = appContainer.userProfileRepository.getProfileSync() ?: UserProfileEntity()
                            appContainer.userProfileRepository.saveProfile(existing.copy(onboardingCompleted = true))
                            OnboardingFlag.set(appContext, true)
                            onboardingInProgress = false
                            navController.navigate(Screen.Dashboard.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    },
                    showStepIndicator = onboardingInProgress
                )
            }
        }

        // Main Dashboard
        composable(Screen.Dashboard.route) {
            val breathingStats by appContainer.breathingRepository.observeStats()
                .collectAsState(initial = BreathingStats())
            val breathingSession by appContainer.breathingSessionController.state.collectAsState()
            val healthRecords by appContainer.healthRecordsRepository.observe()
                .collectAsState(initial = HealthRecordsSnapshot())
            val supplements by appContainer.supplementRepository.observe()
                .collectAsState(initial = SupplementsSnapshot())
            DashboardScreen(
                onNavigateToBodyAge = { navController.navigate(Screen.BodyAge.route) },
                viewModel = dashboardViewModel,
                nutritionViewModel = nutritionViewModel,
                onNavigateToRecoveryDetails = {
                    navController.navigate(Screen.RecoveryDetails.route)
                },
                onNavigateToSleep = {
                    navController.navigate(Screen.Sleep.route)
                },
                onNavigateToTraining = {
                    navController.navigateToTab(Screen.Training.route)
                },
                onNavigateToHeartMetrics = {
                    navController.navigate(Screen.HeartMetrics.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onNavigateToNutrition = {
                    navController.navigateToTab(Screen.Nutrition.route)
                },
                onNavigateToBodyFatCalculator = {
                    navController.navigate(Screen.BodyFatCalculator.route)
                },
                onNavigateToBodyMetrics = {
                    navController.navigate(Screen.BodyMetrics.route)
                },
                breathingStats = breathingStats,
                breathingSession = breathingSession,
                healthRecords = healthRecords,
                supplements = supplements,
                onToggleSupplementDose = { id, time, taken ->
                    coroutineScope.launch { appContainer.supplementRepository.setTaken(id, time, taken) }
                },
                onNavigateToSupplements = { navController.navigate(Screen.Supplements.route) },
                onNavigateToHealthRecords = { navController.navigate(Screen.HealthRecords.route) },
                onNavigateToBreathing = { pattern ->
                    navController.navigate(Screen.Breathing.createRoute(pattern?.id)) {
                        launchSingleTop = true
                    }
                },
                autoOpenQuickLog = quickLogTrigger,
                onAutoOpenQuickLogHandled = onQuickLogTriggerHandled
            )
        }

        // Detail Screens
        composable(Screen.RecoveryDetails.route) {
            RecoveryDetailsScreen(
                viewModel = dashboardViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Sleep.route) {
            SleepScreen(
                viewModel = dashboardViewModel,
                sleepRepository = appContainer.sleepRepository,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Training.route) {
            TrainingScreen(
                viewModel = dashboardViewModel,
                workoutRepository = appContainer.workoutRepository,
                heartRateRepository = appContainer.heartRateRepository
            )
        }

        composable(Screen.HeartMetrics.route) {
            HeartMetricsScreen(
                viewModel = dashboardViewModel,
                heartRateRepository = appContainer.heartRateRepository,
                hrvRepository = appContainer.hrvRepository,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Trends.route) {
            TrendsScreen(
                dailySummaryRepository = appContainer.dailySummaryRepository,
                weightDao = appContainer.database.weightDao(),
                workoutRepository = appContainer.workoutRepository,
                foodLogRepository = appContainer.foodLogRepository,
                bodyFatRepository = appContainer.bodyFatRepository,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToDashboardForDate = { date ->
                    dashboardViewModel.selectDate(date)
                    navController.navigateToTab(Screen.Dashboard.route)
                },
                onNavigateToNutritionForDate = { date ->
                    nutritionViewModel.selectDate(date)
                    navController.navigateToTab(Screen.Nutrition.route)
                },
                onNavigateToBodyFatCalculator = {
                    navController.navigate(Screen.BodyFatCalculator.route)
                }
            )
        }

        // Settings & Management
        composable(Screen.DataSources.route) {
            DataSourcesScreen(
                syncManager = appContainer.healthSyncManager,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Settings.route) { backStackEntry ->
            val expandAi = remember { backStackEntry.savedStateHandle.remove<Boolean>(EXPAND_AI_SETTINGS) == true }
            SettingsScreen(
                expandAiInitially = expandAi,
                syncManager = appContainer.healthSyncManager,
                exportManager = appContainer.localExportManager,
                localStorageManager = appContainer.localStorageManager,
                userProfileRepository = appContainer.userProfileRepository,
                weightDao = appContainer.database.weightDao(),
                heightDao = appContainer.database.heightDao(),
                calorieCalculator = appContainer.calorieCalculator,
                secureKeyStore = appContainer.secureKeyStore,
                openRouterClient = appContainer.openRouterClient,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToHistoricalSync = {
                    navController.navigate(Screen.HistoricalSyncSetup.route)
                },
                onNavigateToDataSources = {
                    navController.navigate(Screen.DataSources.route)
                },
                onNavigateToPrivacyPolicy = {
                    navController.navigate(Screen.PrivacyPolicy.route)
                },
                onResetToWelcome = {
                    coroutineScope.launch {
                        val existing = appContainer.userProfileRepository.getProfileSync() ?: UserProfileEntity()
                        appContainer.userProfileRepository.saveProfile(existing.copy(onboardingCompleted = false))
                        OnboardingFlag.set(appContext, false)
                        navController.navigate(Screen.Welcome.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }
            )
        }

        composable(Screen.HomeScreenWidgets.route) {
            HomeScreenWidgetsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // Nutrition
        composable(Screen.Nutrition.route) {
            NutritionScreen(
                viewModel = nutritionViewModel,
                dashboardViewModel = dashboardViewModel,
                onNavigateToMealPlan = { navController.navigate(Screen.MealPlan.route) },
                onNavigateToAiSettings = { navController.openAiSettings() }
            )
        }

        composable(Screen.MealPlan.route) {
            MealPlanScreen(
                mealPlanRepository = appContainer.mealPlanRepository,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.BodyAge.route) {
            BodyAgeScreen(
                viewModel = dashboardViewModel,
                onNavigateBack = { navController.popBackStack() },
                onSaveBirthday = { dob ->
                    coroutineScope.launch {
                        val profile = appContainer.userProfileRepository.getProfileSync() ?: UserProfileEntity()
                        appContainer.userProfileRepository.saveProfile(profile.copy(dateOfBirth = dob))
                        dashboardViewModel.refreshHabits()
                    }
                }
            )
        }

        // Everything that isn't its own tab
        composable(Screen.More.route) {
            MoreScreen(
                onOpenBodyAge = { navController.navigate(Screen.BodyAge.route) },
                onOpenTrends = { navController.navigate(Screen.Trends.route) },
                onOpenPosture = { navController.navigate(Screen.Posture.route) },
                onOpenBodyFat = { navController.navigate(Screen.BodyFatCalculator.route) },
                onOpenBodyMetrics = { navController.navigate(Screen.BodyMetrics.route) },
                onOpenHealthRecords = { navController.navigate(Screen.HealthRecords.route) },
                onOpenSupplements = { navController.navigate(Screen.Supplements.route) },
                onOpenBreathing = { navController.navigate(Screen.Breathing.createRoute()) },
                onOpenWidgets = { navController.navigate(Screen.HomeScreenWidgets.route) },
                onCustomizeToday = {
                    // Open Today with its customize sheet already up.
                    dashboardViewModel.setCustomizeSheetVisible(true)
                    navController.navigateToTab(Screen.Dashboard.route)
                },
                onOpenSettings = { navController.navigate(Screen.Settings.route) }
            )
        }

        // Posture correction (AI feature - opt-in, see Settings > AI Features)
        composable(Screen.Posture.route) {
            PostureScreen(
                viewModel = postureViewModel,
                onNavigateBack = { navController.popBackStack() },
                onStartNewScan = { navController.navigate(Screen.PostureCapture.route) },
                onOpenScan = { scanId -> navController.navigate(Screen.PostureScanDetail.createRoute(scanId)) },
                onNavigateToAiSettings = { navController.openAiSettings() },
                onNavigateToAiCoach = { navController.navigateToTab(Screen.AiCoach.route) }
            )
        }

        composable(Screen.PostureCapture.route) {
            PostureCaptureScreen(
                viewModel = postureViewModel,
                onNavigateBack = { navController.popBackStack() },
                onDone = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.PostureScanDetail.route,
            arguments = listOf(navArgument("scanId") { type = NavType.LongType })
        ) { backStackEntry ->
            val scanId = backStackEntry.arguments?.getLong("scanId") ?: 0L
            PostureScanDetailScreen(
                scanId = scanId,
                postureScanRepository = appContainer.postureScanRepository,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // AI Coach
        composable(Screen.AiCoach.route) {
            AiCoachScreen(
                viewModel = aiCoachViewModel,
                onNavigateToAiSettings = { navController.openAiSettings() },
                onOpenScreen = { screen, pattern ->
                    val route = when (screen) {
                        "breathing" -> Screen.Breathing.createRoute(pattern)
                        "supplements" -> Screen.Supplements.route
                        "health_records" -> Screen.HealthRecords.route
                        "body_metrics" -> Screen.BodyMetrics.route
                        "body_fat" -> Screen.BodyFatCalculator.route
                        "nutrition" -> Screen.Nutrition.route
                        "sleep" -> Screen.Sleep.route
                        "recovery" -> Screen.RecoveryDetails.route
                        "heart" -> Screen.HeartMetrics.route
                        "training" -> Screen.Training.route
                        "trends" -> Screen.Trends.route
                        "posture" -> Screen.Posture.route
                        "settings" -> Screen.Settings.route
                        else -> null
                    }
                    when {
                        route == null -> Unit
                        // Tabs (Nutrition, Workouts) switch tabs like the bottom bar, rather than
                        // stacking a second copy of the tab on top of the chat.
                        route in BottomNavDestination.routeSet -> navController.navigateToTab(route)
                        else -> navController.navigate(route)
                    }
                }
            )
        }

        // Privacy Policy & Health Connect Audit
        composable(Screen.PrivacyPolicy.route) {
            PrivacyPolicyScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // Daily supplements: photo-first add, schedule, reminders
        composable(Screen.Supplements.route) {
            val context = LocalContext.current
            val supplementsViewModel: SupplementsViewModel = viewModel(
                factory = SupplementsViewModel.provideFactory(
                    context = context,
                    repository = appContainer.supplementRepository,
                    userProfileRepository = appContainer.userProfileRepository
                )
            )
            SupplementsScreen(
                viewModel = supplementsViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Labs, vitals, goals, allergies, conditions, pregnancy & cycle - tracking only
        composable(Screen.HealthRecords.route) {
            val healthRecordsViewModel: HealthRecordsViewModel = viewModel(
                factory = HealthRecordsViewModel.provideFactory(
                    repository = appContainer.healthRecordsRepository,
                    healthConnect = appContainer.healthConnectDataSource
                )
            )
            HealthRecordsScreen(
                viewModel = healthRecordsViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Guided breathing exercises
        composable(
            route = Screen.Breathing.route,
            arguments = listOf(
                navArgument("pattern") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("start") {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) { backStackEntry ->
            val patternId = backStackEntry.arguments?.getString("pattern")
            val autoStart = backStackEntry.arguments?.getBoolean("start") == true
            val breathingViewModel: BreathingViewModel = viewModel(
                factory = BreathingViewModel.provideFactory(
                    initialPattern = BreathingPattern.fromId(patternId),
                    controller = appContainer.breathingSessionController,
                    repository = appContainer.breathingRepository,
                    healthConnectDataSource = appContainer.healthConnectDataSource
                )
            )
            // Started from the widget: begin once, and never over a session that's already running.
            var autoStartHandled by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                if (autoStart && !autoStartHandled) {
                    autoStartHandled = true
                    if (appContainer.breathingSessionController.state.value is BreathingSessionState.Idle) {
                        breathingViewModel.start()
                    }
                }
            }
            BreathingScreen(
                viewModel = breathingViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Body Fat & Composition Calculator
        composable(Screen.BodyFatCalculator.route) {
            val bodyFatViewModel: BodyFatCalculatorViewModel = viewModel(
                factory = BodyFatCalculatorViewModel.provideFactory(
                    context = LocalContext.current,
                    userProfileRepository = appContainer.userProfileRepository,
                    weightDao = appContainer.database.weightDao(),
                    heightDao = appContainer.database.heightDao(),
                    bodyFatCalculator = appContainer.bodyFatCalculator,
                    bodyFatAnalyzer = appContainer.bodyFatAnalyzer,
                    bodyFatRepository = appContainer.bodyFatRepository,
                    secureKeyStore = appContainer.secureKeyStore
                )
            )
            BodyFatCalculatorScreen(
                viewModel = bodyFatViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onNavigateToBodyMetrics = {
                    // Coming from Body Metrics' "Update measurements", just go back to it.
                    if (!navController.popBackStack(Screen.BodyMetrics.route, inclusive = false)) {
                        navController.navigate(Screen.BodyMetrics.route)
                    }
                }
            )
        }

        // Every derived body metric (BMI, FFMI, WHtR, BRI...) with an explainer for each
        composable(Screen.BodyMetrics.route) {
            val bodyMetricsViewModel: BodyMetricsViewModel = viewModel(
                factory = BodyMetricsViewModel.provideFactory(appContainer.bodyMetricsRepository)
            )
            BodyMetricsScreen(
                viewModel = bodyMetricsViewModel,
                onNavigateBack = { navController.popBackStack() },
                onUpdateMeasurements = { navController.navigate(Screen.BodyFatCalculator.route) }
            )
        }
    }
}

private const val EXPAND_AI_SETTINGS = "expand_ai_settings"

/** Opens Settings with the (normally collapsed) AI Features section expanded and scrolled into view. */
private fun NavHostController.openAiSettings() {
    navigate(Screen.Settings.route)
    currentBackStackEntry?.savedStateHandle?.set(EXPAND_AI_SETTINGS, true)
}

/**
 * Opens a tab the way the bottom bar does - switching to it and keeping its state - rather than
 * stacking a second copy of the tab on top of the current screen.
 */
internal fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
