package com.kevan.hangry.ui.dashboard

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kevan.hangry.R
import com.kevan.hangry.domain.calculation.HangryStrainCalculator
import com.kevan.hangry.domain.model.DashboardWidget
import com.kevan.hangry.domain.model.WidgetType
import com.kevan.hangry.ui.components.*
import com.kevan.hangry.ui.nutrition.NutritionViewModel
import com.kevan.hangry.ui.nutrition.QuickMealLogSheet
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens
import com.kevan.hangry.util.rememberPhotoCaptureLauncher
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    nutritionViewModel: NutritionViewModel? = null,
    onNavigateToRecoveryDetails: () -> Unit,
    onNavigateToSleep: () -> Unit,
    onNavigateToTraining: () -> Unit,
    onNavigateToHeartMetrics: () -> Unit,
    onNavigateToTrends: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToNutrition: () -> Unit,
    onNavigateToPosture: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val nutritionUiState = nutritionViewModel?.uiState?.collectAsState()?.value
    val tokens = LocalHangryTokens.current
    val snackbarHostState = remember { SnackbarHostState() }

    var showQuickLogSheet by remember { mutableStateOf(false) }
    var capturedMealPhotoUri by remember { mutableStateOf<Uri?>(null) }

    val photoLauncher = rememberPhotoCaptureLauncher { uri ->
        capturedMealPhotoUri = uri
        showQuickLogSheet = true
    }

    LaunchedEffect(uiState.errorMessage) {
        val message = uiState.errorMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    LaunchedEffect(nutritionUiState?.lastSavedEntry) {
        val saved = nutritionUiState?.lastSavedEntry ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            message = "Logged: ${saved.foodName} · ${saved.calories} kcal",
            duration = SnackbarDuration.Short
        )
        nutritionViewModel?.clearLastSaved()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.hangry_logo),
                            contentDescription = stringResource(R.string.app_name),
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.setCustomizeSheetVisible(true) }) {
                        Icon(
                            imageVector = Icons.Default.DashboardCustomize,
                            contentDescription = "Customize Dashboard"
                        )
                    }
                    IconButton(onClick = onNavigateToTrends) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                            contentDescription = "Trends"
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                    IconButton(
                        onClick = { viewModel.syncNow() },
                        enabled = !uiState.isSyncing
                    ) {
                        if (uiState.isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = tokens.scoreColors.primed
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.sync_now)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = HangryTokens.Spacing.m, vertical = HangryTokens.Spacing.s),
            verticalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.m)
        ) {
            // Modular Widget Engine: Render active widgets ordered by user preference
            val activeWidgets = (if (uiState.widgets.isNotEmpty()) uiState.widgets else DashboardWidget.createDefaultWidgets())
                .filter { it.isVisible }
                .sortedBy { it.order }

            activeWidgets.forEach { widget ->
                when (widget.type) {
                    WidgetType.RECOVERY_HERO -> {
                        HangryScoreHero(
                            scoreEntity = uiState.recoveryScore,
                            isPending = uiState.isPendingSleepData,
                            modifier = Modifier.clickable { onNavigateToRecoveryDetails() }
                        )
                    }

                    WidgetType.DAILY_ACTIVITY_RINGS -> {
                        DailyActivityRingsCard(
                            currentSteps = uiState.dailySummary?.steps ?: 0L,
                            stepGoal = uiState.dailyStepGoal,
                            currentMinutes = uiState.todayActiveMinutes,
                            minutesGoal = uiState.dailyActivityMinutesGoal,
                            currentCalories = uiState.todayActiveCalories,
                            caloriesGoal = uiState.dailyActiveCaloriesGoal,
                            isExpanded = uiState.isActivityExpanded,
                            onToggleExpand = { viewModel.toggleActivityExpanded() },
                            onSaveGoals = { s, m, c -> viewModel.updateActivityGoals(s, m, c) }
                        )
                    }

                    WidgetType.LOG_MEAL -> {
                        LogMealWidgetCard(
                            totalCaloriesToday = nutritionUiState?.totalCaloriesToday ?: 0,
                            calorieGoal = uiState.calorieGoal?.dailyCalorieTarget,
                            recentEntries = nutritionUiState?.todayEntries ?: emptyList(),
                            onTakePhoto = { photoLauncher.takePhoto() },
                            onQuickAdd = {
                                capturedMealPhotoUri = null
                                showQuickLogSheet = true
                            },
                            onOpenNutrition = onNavigateToNutrition
                        )
                    }

                    WidgetType.STRESS_MONITOR -> {
                        val stress = uiState.stressResult
                        if (stress != null) {
                            StressCard(stressResult = stress)
                        }
                    }

                    WidgetType.SLEEP_STRAIN_RINGS -> {
                        SleepStrainRingsRow(
                            uiState = uiState,
                            onNavigateToSleep = onNavigateToSleep,
                            onNavigateToTraining = onNavigateToTraining
                        )
                    }

                    WidgetType.SLEEP_SUMMARY -> {
                        SleepSummaryCard(
                            uiState = uiState,
                            onNavigateToSleep = onNavigateToSleep
                        )
                    }

                    WidgetType.TRAINING_LOAD -> {
                        TrainingLoadCard(
                            uiState = uiState,
                            onNavigateToTraining = onNavigateToTraining
                        )
                    }

                    WidgetType.HEART_METRICS -> {
                        HeartMetricsRow(
                            uiState = uiState,
                            onNavigateToHeartMetrics = onNavigateToHeartMetrics
                        )
                    }

                    WidgetType.AI_SHORTCUTS -> {
                        AiShortcutsRow(
                            onNavigateToNutrition = onNavigateToNutrition,
                            onNavigateToPosture = onNavigateToPosture
                        )
                    }

                    WidgetType.CALORIE_BURN -> {
                        CalorieBurnCard(
                            uiState = uiState,
                            onNavigateToSettings = onNavigateToSettings
                        )
                    }

                    WidgetType.CUSTOM_METRIC -> {
                        CustomMetricWidgetCard(
                            widget = widget,
                            dailySummary = uiState.dailySummary,
                            stressResult = uiState.stressResult,
                            latestWeightKg = uiState.latestWeightKg,
                            activeMinutes = uiState.todayActiveMinutes,
                            activeCalories = uiState.todayActiveCalories,
                            onRemoveWidget = { viewModel.removeWidget(widget.id) }
                        )
                    }
                }
            }

            // Customize Dashboard Quick Button
            OutlinedButton(
                onClick = { viewModel.setCustomizeSheetVisible(true) },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(
                    imageVector = Icons.Default.DashboardCustomize,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Customize Dashboard Widgets")
            }

            // Sync status footer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = HangryTokens.Spacing.s),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val syncTime = uiState.lastSyncFormatted ?: "Just now"
                Text(
                    text = stringResource(R.string.sync_success, syncTime),
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.textMuted
                )
                Text(
                    text = stringResource(R.string.data_source_label, "Health Connect"),
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.textMuted
                )
            }

            Spacer(modifier = Modifier.height(HangryTokens.Spacing.m))
        }
    }

    if (showQuickLogSheet && nutritionViewModel != null) {
        QuickMealLogSheet(
            initialPhotoUri = capturedMealPhotoUri,
            aiEnabled = nutritionUiState?.aiFeaturesEnabled ?: false,
            mealPlans = nutritionUiState?.mealPlans ?: emptyList(),
            onDismiss = {
                showQuickLogSheet = false
                capturedMealPhotoUri = null
            },
            onLogMeal = { name, calories, uri, p, c, f ->
                nutritionViewModel.quickLogMeal(name, calories, uri, p, c, f)
            },
            onEstimateWithAi = if (nutritionUiState?.aiFeaturesEnabled == true) {
                { uri, note -> nutritionViewModel.estimateFood(uri, note) }
            } else null,
            onLogMealPlan = { plan -> nutritionViewModel.logFromMealPlan(plan) }
        )
    }

    if (uiState.showCustomizeSheet) {
        CustomizeDashboardSheet(
            widgets = (if (uiState.widgets.isNotEmpty()) uiState.widgets else DashboardWidget.createDefaultWidgets()).sortedBy { it.order },
            onToggleVisibility = { id, vis -> viewModel.toggleWidgetVisibility(id, vis) },
            onReorderWidgets = { ids -> viewModel.reorderWidgets(ids) },
            onAddCustomWidget = { w -> viewModel.addCustomWidget(w) },
            onRemoveWidget = { id -> viewModel.removeWidget(id) },
            onResetDefaults = { viewModel.resetWidgetLayout() },
            onDismiss = { viewModel.setCustomizeSheetVisible(false) }
        )
    }
}

@Composable
private fun SleepStrainRingsRow(
    uiState: DashboardUiState,
    onNavigateToSleep: () -> Unit,
    onNavigateToTraining: () -> Unit
) {
    val tokens = LocalHangryTokens.current
    val isPending = uiState.isPendingSleepData

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HangryCard(
            modifier = Modifier
                .weight(1f)
                .clickable { onNavigateToSleep() },
            contentPadding = 12.dp
        ) {
            val quality = uiState.sleepAnalysis?.sleepQualityScore ?: 70
            val sleepScoreColor = when {
                isPending -> tokens.textMuted
                quality >= 85 -> tokens.scoreColors.primed
                quality >= 65 -> tokens.scoreColors.balanced
                else -> tokens.scoreColors.rebuild
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                HangryRingGauge(
                    progress = if (isPending) 0f else (quality / 100f).coerceIn(0f, 1f),
                    color = sleepScoreColor,
                    modifier = Modifier.size(52.dp),
                    strokeWidth = 5.dp
                ) {
                    Text(
                        text = if (isPending) "--" else "$quality",
                        style = MaterialTheme.typography.titleSmall,
                        color = sleepScoreColor
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = "Sleep Score",
                        style = MaterialTheme.typography.titleSmall,
                        color = tokens.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (isPending) "Pending" else "Quality",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        HangryCard(
            modifier = Modifier
                .weight(1f)
                .clickable { onNavigateToTraining() },
            contentPadding = 12.dp
        ) {
            val strain = (uiState.dailySummary?.dayStrain ?: 0.0).coerceIn(0.0, HangryStrainCalculator.MAX_STRAIN)
            val strainColor = if (isPending) tokens.textMuted else tokens.chartColors.trainingLoad
            Row(verticalAlignment = Alignment.CenterVertically) {
                HangryRingGauge(
                    progress = if (isPending) 0f else (strain / HangryStrainCalculator.MAX_STRAIN).toFloat(),
                    color = strainColor,
                    modifier = Modifier.size(52.dp),
                    strokeWidth = 5.dp
                ) {
                    Text(
                        text = if (isPending) "--" else String.format(Locale.US, "%.1f", strain),
                        style = MaterialTheme.typography.titleSmall,
                        color = strainColor
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = "Strain",
                        style = MaterialTheme.typography.titleSmall,
                        color = tokens.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val recommendation = uiState.strainRecommendation
                    val targetText = when {
                        isPending -> "Pending"
                        recommendation != null -> String.format(Locale.US, "%.1f–%.1f", recommendation.targetLow, recommendation.targetHigh)
                        else -> "Calibrating"
                    }
                    Text(
                        text = targetText,
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun SleepSummaryCard(
    uiState: DashboardUiState,
    onNavigateToSleep: () -> Unit
) {
    val tokens = LocalHangryTokens.current
    HangryCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onNavigateToSleep() }
    ) {
        Text(
            text = stringResource(R.string.sleep_summary_title),
            style = MaterialTheme.typography.titleSmall,
            color = tokens.textSecondary
        )
        Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))

        val sleepMin = uiState.dailySummary?.sleepDurationMinutes
        val sleepDisplay = when {
            uiState.isPendingSleepData -> "Pending"
            sleepMin != null && sleepMin > 0 -> "${sleepMin / 60}h ${sleepMin % 60}m"
            else -> stringResource(R.string.not_available)
        }

        Text(
            text = sleepDisplay,
            style = MaterialTheme.typography.headlineMedium,
            color = if (uiState.isPendingSleepData) tokens.textMuted else tokens.chartColors.sleep
        )
        Spacer(modifier = Modifier.height(HangryTokens.Spacing.xs))
        val performance = uiState.sleepAnalysis?.sleepPerformancePercentage
        val sleepSubtitle = when {
            uiState.isPendingSleepData -> "Log last night's sleep to unlock"
            performance != null -> "$performance% of sleep need"
            else -> stringResource(R.string.sleep_aligned_with_pattern)
        }
        Text(
            text = sleepSubtitle,
            style = MaterialTheme.typography.labelSmall,
            color = tokens.textMuted
        )
    }
}

@Composable
private fun TrainingLoadCard(
    uiState: DashboardUiState,
    onNavigateToTraining: () -> Unit
) {
    val tokens = LocalHangryTokens.current
    HangryCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onNavigateToTraining() }
    ) {
        Text(
            text = stringResource(R.string.training_load_title),
            style = MaterialTheme.typography.titleSmall,
            color = tokens.textSecondary
        )
        Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))

        val load = uiState.dailySummary?.dailyTrainingLoad ?: 0.0
        Text(
            text = String.format(Locale.US, "%.1f", load),
            style = MaterialTheme.typography.headlineMedium,
            color = tokens.chartColors.trainingLoad
        )
        Spacer(modifier = Modifier.height(HangryTokens.Spacing.xs))
        val count = uiState.dailySummary?.exerciseCount ?: 0
        Text(
            text = stringResource(R.string.training_load_workouts_count, count),
            style = MaterialTheme.typography.labelSmall,
            color = tokens.textMuted
        )
    }
}

@Composable
private fun HeartMetricsRow(
    uiState: DashboardUiState,
    onNavigateToHeartMetrics: () -> Unit
) {
    val tokens = LocalHangryTokens.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HangryCard(
            modifier = Modifier
                .weight(1f)
                .clickable { onNavigateToHeartMetrics() },
            contentPadding = 12.dp
        ) {
            Text(
                text = stringResource(R.string.resting_hr_label),
                style = MaterialTheme.typography.titleSmall,
                color = tokens.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(HangryTokens.Spacing.xs))
            val rhr = uiState.dailySummary?.restingHeartRate
            val rhrText = if (rhr != null) "${rhr.toInt()} bpm" else "—"
            Text(
                text = rhrText,
                style = MaterialTheme.typography.headlineMedium,
                color = tokens.chartColors.restingHeartRate
            )
        }

        HangryCard(
            modifier = Modifier
                .weight(1f)
                .clickable { onNavigateToHeartMetrics() },
            contentPadding = 12.dp
        ) {
            Text(
                text = stringResource(R.string.hrv_rmssd_label),
                style = MaterialTheme.typography.titleSmall,
                color = tokens.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(HangryTokens.Spacing.xs))
            val hrv = uiState.dailySummary?.hrvRmssd
            val hrvText = if (hrv != null) "${hrv.toInt()} ms" else "—"
            Text(
                text = hrvText,
                style = MaterialTheme.typography.headlineMedium,
                color = tokens.chartColors.hrv
            )
        }
    }
}

@Composable
private fun AiShortcutsRow(
    onNavigateToNutrition: () -> Unit,
    onNavigateToPosture: () -> Unit
) {
    val tokens = LocalHangryTokens.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HangryCard(
            modifier = Modifier
                .weight(1f)
                .clickable { onNavigateToNutrition() },
            contentPadding = 12.dp
        ) {
            Text(
                text = "Nutrition",
                style = MaterialTheme.typography.titleSmall,
                color = tokens.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(HangryTokens.Spacing.xs))
            Text(
                text = "Log food",
                style = MaterialTheme.typography.headlineSmall,
                color = tokens.chartColors.trainingLoad
            )
        }
        HangryCard(
            modifier = Modifier
                .weight(1f)
                .clickable { onNavigateToPosture() },
            contentPadding = 12.dp
        ) {
            Text(
                text = "Posture",
                style = MaterialTheme.typography.titleSmall,
                color = tokens.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(HangryTokens.Spacing.xs))
            Text(
                text = "Check posture",
                style = MaterialTheme.typography.headlineSmall,
                color = tokens.chartColors.hrv
            )
        }
    }
}

@Composable
private fun CalorieBurnCard(
    uiState: DashboardUiState,
    onNavigateToSettings: () -> Unit
) {
    val tokens = LocalHangryTokens.current
    val burn = uiState.calorieBurn

    HangryCard(modifier = Modifier.fillMaxWidth().clickable { onNavigateToSettings() }) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Calories Burned",
                style = MaterialTheme.typography.titleMedium,
                color = tokens.textPrimary
            )
            val goal = uiState.calorieGoal
            if (goal?.dailyCalorieTarget != null) {
                Text(
                    text = "Goal: ${goal.dailyCalorieTarget} kcal",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.textMuted
                )
            }
        }
        Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))

        val total = burn?.totalBurnedCalories
        Text(
            text = if (total != null) "${total.toInt()} kcal" else "—",
            style = MaterialTheme.typography.headlineLarge,
            color = tokens.chartColors.trainingLoad
        )
        Spacer(modifier = Modifier.height(HangryTokens.Spacing.xs))

        if (burn?.bmrCalories != null || burn?.neatCalories != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.m)
            ) {
                CalorieComponentLabel("BMR", burn.bmrCalories, tokens.textSecondary)
                CalorieComponentLabel("NEAT", burn.neatCalories, tokens.textSecondary)
                CalorieComponentLabel("Exercise", burn.exerciseCalories, tokens.textSecondary)
            }
            Spacer(modifier = Modifier.height(HangryTokens.Spacing.xs))
        }

        Text(
            text = burn?.supportiveNote ?: "Set up your profile in Settings to estimate calorie burn.",
            style = MaterialTheme.typography.bodySmall,
            color = tokens.textSecondary
        )
    }
}

@Composable
private fun CalorieComponentLabel(label: String, value: Double?, labelColor: androidx.compose.ui.graphics.Color) {
    Column {
        Text(
            text = if (value != null) "${value.toInt()}" else "—",
            style = MaterialTheme.typography.titleSmall,
            color = labelColor
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = LocalHangryTokens.current.textMuted
        )
    }
}
