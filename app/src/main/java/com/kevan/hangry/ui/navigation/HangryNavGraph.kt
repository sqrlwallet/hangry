package com.kevan.hangry.ui.navigation

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
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.kevan.hangry.data.local.entity.UserProfileEntity
import com.kevan.hangry.di.AppContainer
import com.kevan.hangry.ui.dashboard.DashboardScreen
import com.kevan.hangry.ui.dashboard.DashboardViewModel
import com.kevan.hangry.ui.heart.HeartMetricsScreen
import com.kevan.hangry.ui.nutrition.MealPlanScreen
import com.kevan.hangry.ui.nutrition.NutritionScreen
import com.kevan.hangry.ui.nutrition.NutritionViewModel
import com.kevan.hangry.ui.onboarding.HealthConnectExplanationScreen
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
import com.kevan.hangry.ui.theme_preview.ThemePreviewScreen
import com.kevan.hangry.ui.training.TrainingScreen
import com.kevan.hangry.ui.trends.TrendsScreen
import kotlinx.coroutines.launch

sealed class Screen(val route: String) {
    data object Welcome : Screen("welcome")
    data object HealthConnectExplanation : Screen("health_connect_explanation")
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
    data object ThemePreview : Screen("theme_preview")
    data object PrivacyPolicy : Screen("privacy_policy")
    data object Nutrition : Screen("nutrition")
    data object MealPlan : Screen("meal_plan")
    data object Posture : Screen("posture")
    data object PostureCapture : Screen("posture_capture")
    data object PostureScanDetail : Screen("posture_scan_detail/{scanId}") {
        fun createRoute(scanId: Long) = "posture_scan_detail/$scanId"
    }
}

@Composable
fun HangryNavGraph(
    navController: NavHostController,
    appContainer: AppContainer,
    modifier: Modifier = Modifier,
    startDestination: String = Screen.Dashboard.route
) {
    val coroutineScope = rememberCoroutineScope()
    val finalStart = startDestination

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
            journalRepository = appContainer.journalRepository,
            dashboardWidgetRepository = appContainer.dashboardWidgetRepository
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

    NavHost(
        navController = navController,
        startDestination = finalStart,
        modifier = modifier
    ) {
        // Onboarding Flow
        composable(Screen.Welcome.route) {
            WelcomeScreen(
                onGetStarted = {
                    navController.navigate(Screen.HealthConnectExplanation.route)
                }
            )
        }

        composable(Screen.HealthConnectExplanation.route) {
            HealthConnectExplanationScreen(
                onContinue = {
                    navController.navigate(Screen.PermissionSetup.route)
                }
            )
        }

        composable(Screen.PermissionSetup.route) {
            PermissionSetupScreen(
                dataSource = appContainer.healthConnectDataSource,
                providerStatus = appContainer.healthConnectProviderStatus,
                onProceedToHistoricalSync = {
                    navController.navigate(Screen.HistoricalSyncSetup.route)
                }
            )
        }

        composable(Screen.HistoricalSyncSetup.route) {
            HistoricalSyncSetupScreen(
                onStartSync = { days ->
                    navController.navigate(Screen.SyncProgress.createRoute(days))
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
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
            SyncProgressScreen(
                rangeDays = rangeDays,
                syncManager = appContainer.healthSyncManager,
                onComplete = {
                    coroutineScope.launch {
                        val existing = appContainer.userProfileRepository.getProfileSync() ?: UserProfileEntity()
                        appContainer.userProfileRepository.saveProfile(existing.copy(onboardingCompleted = true))
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }
            )
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
                }
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
                onNavigateBack = {
                    navController.popBackStack()
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
                journalRepository = appContainer.journalRepository,
                userProfileRepository = appContainer.userProfileRepository,
                weightDao = appContainer.database.weightDao(),
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
                onNavigateToThemePreview = {
                    navController.navigate(Screen.ThemePreview.route)
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
                onNavigateToAiSettings = { navController.navigate(Screen.Settings.route) }
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

        // Theme Preview
        composable(Screen.ThemePreview.route) {
            ThemePreviewScreen(
                onNavigateBack = {
                    navController.popBackStack()
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
    }
}
