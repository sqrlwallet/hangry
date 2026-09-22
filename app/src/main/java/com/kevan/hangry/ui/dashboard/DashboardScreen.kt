package com.kevan.hangry.ui.dashboard

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kevan.hangry.R
import com.kevan.hangry.domain.calculation.HangryStrainCalculator
import com.kevan.hangry.domain.model.DashboardWidget
import com.kevan.hangry.domain.model.WidgetType
import com.kevan.hangry.ui.components.*
import com.kevan.hangry.ui.nutrition.NutritionViewModel
import com.kevan.hangry.ui.nutrition.QuickMealLogSheet
import com.kevan.hangry.ui.theme.EmberAccent
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
    onNavigateToAiCoach: () -> Unit = {},
    autoOpenQuickLog: Boolean = false,
    onAutoOpenQuickLogHandled: () -> Unit = {},
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

    LaunchedEffect(autoOpenQuickLog) {
        if (autoOpenQuickLog) {
            showQuickLogSheet = true
            onAutoOpenQuickLogHandled()
        }
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.setCustomizeSheetVisible(true) }) {
                    Icon(
                        imageVector = Icons.Default.DashboardCustomize,
                        contentDescription = "Customize Dashboard"
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
            }
        },
        floatingActionButtonPosition = FabPosition.End,
        floatingActionButton = {
            val haptic = LocalHapticFeedback.current
            Surface(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    photoLauncher.takePhoto()
                },
                shape = RoundedCornerShape(26.dp),
                color = Color.Transparent,
                shadowElevation = 14.dp,
                border = BorderStroke(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.35f),
                            Color.White.copy(alpha = 0.08f)
                        )
                    )
                ),
                modifier = Modifier
                    .height(48.dp)
                    .background(
                        brush = Brush.horizontalGradient(
                            listOf(Color(0xFFFF5722), Color(0xFFFF7043))
                        ),
                        shape = RoundedCornerShape(26.dp)
                    )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Log Meal",
                        tint = Color.White,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Log Meal",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.2.sp
                        ),
                        color = Color.White
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .verticalScroll(rememberScrollState())
                .padding(horizontal = HangryTokens.Spacing.m)
                .padding(bottom = 12.dp),
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
                            currentCalories = uiState.todayActiveCalories,
                            caloriesGoal = uiState.dailyActiveCaloriesGoal,
                            isExpanded = uiState.isActivityExpanded,
                            onToggleExpand = { viewModel.toggleActivityExpanded() },
                            onSaveGoals = { s, c -> viewModel.updateActivityGoals(s, c) }
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

                    WidgetType.HEART_METRICS -> {
                        HeartMetricsRow(
                            uiState = uiState,
                            onNavigateToHeartMetrics = onNavigateToHeartMetrics
                        )
                    }

                    WidgetType.VITALS_CARD -> {
                        VitalsCard(summary = uiState.dailySummary)
                    }

                    WidgetType.AI_SHORTCUTS -> {
                        AiShortcutsRow(
                            onNavigateToAiCoach = onNavigateToAiCoach,
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
                            activeCalories = uiState.todayActiveCalories,
                            onRemoveWidget = { viewModel.removeWidget(widget.id) }
                        )
                    }
                }
            }

            // Daily Coach Briefing & Recovery Overview
            DailyCoachBriefingCard(
                uiState = uiState,
                onNavigateToAiCoach = onNavigateToAiCoach,
                onNavigateToNutrition = onNavigateToNutrition
            )

            // Customize Dashboard Quick Button
            Surface(
                onClick = { viewModel.setCustomizeSheetVisible(true) },
                shape = RoundedCornerShape(16.dp),
                color = tokens.cardBackground,
                border = BorderStroke(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.10f),
                            Color.White.copy(alpha = 0.03f)
                        )
                    )
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.DashboardCustomize,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = tokens.textSecondary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Customize Dashboard Widgets",
                        style = MaterialTheme.typography.labelLarge,
                        color = tokens.textSecondary
                    )
                }
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

            // Bottom clearance
            Spacer(modifier = Modifier.height(8.dp))
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
    onNavigateToAiCoach: () -> Unit,
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
                .clickable { onNavigateToAiCoach() },
            contentPadding = 12.dp
        ) {
            Text(
                text = "AI Coach",
                style = MaterialTheme.typography.titleSmall,
                color = tokens.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(HangryTokens.Spacing.xs))
            Text(
                text = "Ask coach",
                style = MaterialTheme.typography.titleMedium,
                color = EmberAccent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
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
                style = MaterialTheme.typography.titleMedium,
                color = tokens.chartColors.trainingLoad,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
                style = MaterialTheme.typography.titleMedium,
                color = tokens.chartColors.hrv,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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

@Composable
private fun DailyCoachBriefingCard(
    uiState: DashboardUiState,
    onNavigateToAiCoach: () -> Unit,
    onNavigateToNutrition: () -> Unit
) {
    val tokens = LocalHangryTokens.current
    val haptic = LocalHapticFeedback.current

    val quality = uiState.sleepAnalysis?.sleepQualityScore ?: 70
    val isPending = uiState.isPendingSleepData

    val headline: String
    val recommendation: String

    when {
        isPending -> {
            headline = "Awaiting Sleep Data"
            recommendation = "Log or sync last night's sleep to calculate your recovery readiness, strain capacity, and personalized advice."
        }
        quality >= 80 -> {
            headline = "Primed for Peak Output"
            recommendation = "Sleep quality was high ($quality%). Autonomic nervous system is restored. You have capacity for high-strain training or demanding workouts today."
        }
        quality in 50..79 -> {
            headline = "Balanced Daily Capacity"
            recommendation = "Moderate sleep recovery ($quality%). A steady training session, zone 2 cardio, or maintenance routine will keep momentum without overload."
        }
        else -> {
            headline = "Rebuild & Restore Focus"
            recommendation = "Sleep recovery is in the rebuild zone ($quality%). Prioritize active recovery, hydration, mobility, and early wind-down tonight."
        }
    }

    HangryCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = EmberAccent,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Daily Coach Briefing",
                    style = MaterialTheme.typography.titleSmall,
                    color = tokens.textPrimary
                )
            }
            Surface(
                color = EmberAccent.copy(alpha = 0.14f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = if (isPending) "Calibrating" else if (quality >= 80) "Optimal" else if (quality in 50..79) "Balanced" else "Rebuild",
                    style = MaterialTheme.typography.labelSmall,
                    color = EmberAccent,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = headline,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = tokens.textPrimary
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = recommendation,
            style = MaterialTheme.typography.bodySmall,
            color = tokens.textSecondary,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SuggestionChip(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onNavigateToAiCoach()
                },
                label = { Text("Ask Coach", style = MaterialTheme.typography.labelSmall) },
                icon = {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = EmberAccent
                    )
                }
            )
            SuggestionChip(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onNavigateToNutrition()
                },
                label = { Text("Log Food", style = MaterialTheme.typography.labelSmall) },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Restaurant,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = tokens.chartColors.activeCalories
                    )
                }
            )
        }
    }
}

