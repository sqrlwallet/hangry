package com.kevan.hangry.ui.navigation

import com.kevan.hangry.ui.theme.HangryTheme
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
import kotlinx.coroutines.launch

sealed class Screen(val route: String) {
    data object Welcome : Screen("welcome")
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
            bodyFatRepository = appContainer.bodyFatRepository
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
            userProfileRepository = appContainer.userProfileRepository
        )
    )

    val aiCoachViewModel: AiCoachViewModel = viewModel(
        factory = AiCoachViewModel.provideFactory(
            coachRepository = appContainer.coachRepository,
            userProfileRepository = appContainer.userProfileRepository,
            secureKeyStore = appContainer.secureKeyStore
        )
    )

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
                        navController.navigate(Screen.PermissionSetup.route)
                    }
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
            DashboardScreen(
                viewModel = dashboardViewModel,
                nutritionViewModel = nutritionViewModel,
                onNavigateToRecoveryDetails = {
                    navController.navigate(Screen.RecoveryDetails.route)
                },
                onNavigateToSleep = {
                    navController.navigate(Screen.Sleep.route)
                },
                onNavigateToTraining = {
                    navController.navigate(Screen.Training.route)
                },
                onNavigateToHeartMetrics = {
                    navController.navigate(Screen.HeartMetrics.route)
                },
                onNavigateToTrends = {
                    navController.navigate(Screen.Trends.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onNavigateToNutrition = {
                    navController.navigate(Screen.Nutrition.route)
                },
                onNavigateToPosture = {
                    navController.navigate(Screen.Posture.route)
                },
                onNavigateToAiCoach = {
                    navController.navigate(Screen.AiCoach.route)
                },
                onNavigateToBodyFatCalculator = {
                    navController.navigate(Screen.BodyFatCalculator.route)
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
                heartRateRepository = appContainer.heartRateRepository,
                onNavigateBack = {
                    navController.popBackStack()
                }
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
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onNavigateToNutritionForDate = { date ->
                    nutritionViewModel.selectDate(date)
                    navController.navigate(Screen.Nutrition.route) {
                        launchSingleTop = true
                    }
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

        composable(Screen.Settings.route) {
            SettingsScreen(
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
                onNavigateToHomeScreenWidgets = {
                    navController.navigate(Screen.HomeScreenWidgets.route)
                },
                onNavigateToBodyFatCalculator = {
                    navController.navigate(Screen.BodyFatCalculator.route)
                },
                onResetToWelcome = {
                    coroutineScope.launch {
                        val existing = appContainer.userProfileRepository.getProfileSync() ?: UserProfileEntity()
                        appContainer.userProfileRepository.saveProfile(existing.copy(onboardingCompleted = false))
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
                onNavigateBack = { navController.popBackStack() },
                onNavigateToMealPlan = { navController.navigate(Screen.MealPlan.route) },
                onNavigateToAiSettings = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(Screen.MealPlan.route) {
            MealPlanScreen(
                mealPlanRepository = appContainer.mealPlanRepository,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Posture correction (AI feature - opt-in, see Settings > AI Features)
        composable(Screen.Posture.route) {
            PostureScreen(
                viewModel = postureViewModel,
                onNavigateBack = { navController.popBackStack() },
                onStartNewScan = { navController.navigate(Screen.PostureCapture.route) },
                onOpenScan = { scanId -> navController.navigate(Screen.PostureScanDetail.createRoute(scanId)) },
                onNavigateToAiSettings = { navController.navigate(Screen.Settings.route) },
                onNavigateToAiCoach = { navController.navigate(Screen.AiCoach.route) }
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
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAiSettings = { navController.navigate(Screen.Settings.route) }
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
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
            )
        }
    }
}
