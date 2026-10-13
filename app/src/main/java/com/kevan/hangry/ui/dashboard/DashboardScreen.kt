package com.kevan.hangry.ui.dashboard

import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import com.kevan.hangry.domain.calculation.BodySignals
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
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kevan.hangry.R
import com.kevan.hangry.data.breathing.BreathingSessionState
import com.kevan.hangry.domain.calculation.StreakCalculator
import com.kevan.hangry.domain.model.BreathingPattern
import com.kevan.hangry.domain.model.BreathingStats
import com.kevan.hangry.ui.bodyage.BodyAgeCard
import com.kevan.hangry.ui.breathing.BreathingExercisesCard
import com.kevan.hangry.domain.model.HealthRecordsSnapshot
import com.kevan.hangry.ui.coach.Celebrations
import com.kevan.hangry.ui.coach.DashAlertCard
import com.kevan.hangry.ui.coach.DashCelebration
import com.kevan.hangry.ui.coach.DashExpression
import com.kevan.hangry.ui.coach.DashMood
import com.kevan.hangry.ui.healthrecords.HealthRecordsCard
import com.kevan.hangry.domain.model.SupplementsSnapshot
import com.kevan.hangry.ui.navigation.LocalDockInset
import com.kevan.hangry.ui.supplements.SupplementsDashboardCard
import com.kevan.hangry.domain.calculation.HangryStrainCalculator
import com.kevan.hangry.domain.model.DashboardWidget
import com.kevan.hangry.domain.model.WidgetType
import com.kevan.hangry.ui.components.*
import com.kevan.hangry.ui.nutrition.AllergenAlertDialog
import com.kevan.hangry.ui.nutrition.NutritionViewModel
import com.kevan.hangry.ui.nutrition.QuickMealLogSheet
import com.kevan.hangry.ui.theme.CtaGradient
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens
import com.kevan.hangry.util.rememberPhotoCaptureLauncher
import java.time.LocalDate
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
    onNavigateToSettings: () -> Unit,
    onNavigateToBodyAge: () -> Unit = {},
    onNavigateToNutrition: () -> Unit,
    onNavigateToBodyFatCalculator: () -> Unit = {},
    onNavigateToBodyMetrics: () -> Unit = onNavigateToBodyFatCalculator,
    breathingStats: BreathingStats = BreathingStats(),
    breathingSession: BreathingSessionState = BreathingSessionState.Idle,
    onNavigateToBreathing: (BreathingPattern?) -> Unit = {},
    healthRecords: HealthRecordsSnapshot = HealthRecordsSnapshot(),
    supplements: SupplementsSnapshot = SupplementsSnapshot(),
    onToggleSupplementDose: (Long, java.time.LocalTime, Boolean) -> Unit = { _, _, _ -> },
    onNavigateToSupplements: () -> Unit = {},
    onNavigateToHealthRecords: () -> Unit = {},
    fasting: com.kevan.hangry.domain.model.FastingSnapshot = com.kevan.hangry.domain.model.FastingSnapshot(),
    onStartFast: () -> Unit = {},
    onEndFast: () -> Unit = {},
    onNavigateToFasting: () -> Unit = {},
    autoOpenQuickLog: Boolean = false,
    onAutoOpenQuickLogHandled: () -> Unit = {},
    onLogWeight: ((kg: Double, takenAt: java.time.Instant) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val nutritionUiState = nutritionViewModel?.uiState?.collectAsState()?.value
    val tokens = LocalHangryTokens.current
    val snackbarHostState = remember { SnackbarHostState() }
    val dockInset = LocalDockInset.current
    val scrollState = rememberScrollState()
    val tabReselectionBus = com.kevan.hangry.ui.navigation.LocalTabReselectionBus.current
    LaunchedEffect(tabReselectionBus) {
        tabReselectionBus.events.collect { route ->
            if (route == com.kevan.hangry.ui.navigation.Screen.Dashboard.route) {
                scrollState.animateScrollTo(0)
            }
        }
    }

    val isAnalyzingMeal = nutritionUiState?.isAnalyzing == true

    // Photo first, always: the shot goes straight to AI and is logged; the manual sheet only
    // appears (photo attached) when AI can't do it.
    val photoLauncher = rememberPhotoCaptureLauncher { uri ->
        nutritionViewModel?.logMealFromPhoto(uri)
    }

    // The home-screen "Log Meal" widget lands here - open the camera, not a form.
    LaunchedEffect(autoOpenQuickLog) {
        if (autoOpenQuickLog) {
            onAutoOpenQuickLogHandled()
            photoLauncher.takePhoto()
        }
    }

    // Goal celebrations: each activity goal gets one confetti moment the day it's reached.
    val context = LocalContext.current
    var celebration by remember { mutableStateOf<String?>(null) }
    var celebrationIsStreak by remember { mutableStateOf(false) }
    val isTodaySelected = uiState.selectedDate == LocalDate.now()
    LaunchedEffect(isTodaySelected, uiState.isLoading, uiState.todayActiveCalories, uiState.dailySummary?.steps, uiState.todayActiveMinutes) {
        if (!isTodaySelected || uiState.isLoading) return@LaunchedEffect
        val day = LocalDate.now()
        val reached = buildList {
            if (uiState.todayActiveCalories >= uiState.dailyActiveCaloriesGoal && Celebrations.claim(context, "activity_kcal_$day")) add(context.getString(R.string.dashboard_goal_active_calories))
            if ((uiState.dailySummary?.steps ?: 0L) >= uiState.dailyStepGoal && Celebrations.claim(context, "activity_steps_$day")) add(context.getString(R.string.dashboard_goal_steps))
            if (uiState.todayActiveMinutes >= uiState.dailyActivityMinutesGoal && Celebrations.claim(context, "activity_minutes_$day")) add(context.getString(R.string.dashboard_goal_active_time))
        }
        if (reached.isNotEmpty()) {
            val goals = if (reached.size == 1) reached[0] else context.getString(R.string.dashboard_goal_list_and, reached.dropLast(1).joinToString(context.getString(R.string.dashboard_list_separator)), reached.last())
            celebration = if (reached.size > 1) context.getString(R.string.dashboard_celebration_goals, goals) else context.getString(R.string.dashboard_celebration_goal, goals)
            celebrationIsStreak = false
        }
    }
    // Streak milestones (7, 14, 30... days): celebrated once per run, the highest one reached.
    LaunchedEffect(Unit) { viewModel.refreshHabits() }
    LaunchedEffect(uiState.streaks) {
        for (streak in uiState.streaks) {
            val start = streak.startedOn ?: continue
            val newlyReached = StreakCalculator.milestonesReached(streak)
                .filter { Celebrations.claim(context, "streak_${streak.type.name}_${start}_$it") }
            val top = newlyReached.maxOrNull() ?: continue
            celebration = context.getString(R.string.dashboard_celebration_streak, top, streak.type.label.lowercase())
            celebrationIsStreak = true
            break
        }
    }

    celebration?.let { message ->
        DashCelebration(
            title = if (celebrationIsStreak) stringResource(R.string.dashboard_celebration_streak_title) else stringResource(R.string.dashboard_celebration_goal_title),
            message = message,
            onDismiss = { celebration = null },
            mood = if (celebrationIsStreak) DashMood.STREAK else DashMood.CELEBRATE
        )
    }


    LaunchedEffect(nutritionUiState?.lastSavedEntry) {
        val saved = nutritionUiState?.lastSavedEntry ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = context.getString(R.string.dashboard_snackbar_logged, saved.foodName, saved.calories),
            actionLabel = context.getString(R.string.dashboard_snackbar_edit),
            duration = SnackbarDuration.Short
        )
        if (result == SnackbarResult.ActionPerformed) {
            // The Nutrition screen hosts the edit dialog and opens it for editingEntry.
            nutritionViewModel?.startEdit(saved)
            onNavigateToNutrition()
        } else {
            nutritionViewModel?.clearLastSaved()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState, modifier = Modifier.padding(bottom = dockInset)) },
        floatingActionButtonPosition = FabPosition.End,
        floatingActionButton = {
            val haptic = LocalHapticFeedback.current
            Surface(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    photoLauncher.takePhoto()
                },
                enabled = !isAnalyzingMeal,
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
                    // Sit just above the floating tab bar.
                    .padding(bottom = dockInset)
                    .height(48.dp)
                    .background(
                        brush = Brush.horizontalGradient(
                            CtaGradient
                        ),
                        shape = RoundedCornerShape(26.dp)
                    )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isAnalyzingMeal) {
                        // Dash thinks it over while the photo is read.
                        DashExpression(mood = DashMood.THINKING, size = 30.dp, contentDescription = null, interactive = false)
                    } else {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = stringResource(R.string.dashboard_log_meal),
                            tint = Color.White,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isAnalyzingMeal) stringResource(R.string.dashboard_reading_your_meal) else stringResource(R.string.dashboard_log_meal),
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
    ) { _ ->
        // Pull down to sync with Health Connect - replaces the old refresh button.
        val pullState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = uiState.isSyncing,
            onRefresh = { viewModel.syncNow() },
            state = pullState,
            modifier = modifier.fillMaxSize(),
            indicator = {
                DashPullIndicator(
                    state = pullState,
                    isRefreshing = uiState.isSyncing,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                )
            }
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .statusBarsPadding()
                .padding(horizontal = HangryTokens.Spacing.m)
                .padding(top = HangryTokens.Spacing.s)
                // Room for the Log Meal button and the floating tab bar below the last card.
                .padding(bottom = dockInset + 56.dp),
            verticalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.section)
        ) {
            // Date Navigator Bar: navigate between days, inspect past data, or pick a date
            DateNavigatorBar(
                selectedDate = uiState.selectedDate,
                onDateSelected = { date -> viewModel.selectDate(date) }
            )

            // A failed sync stays visible (with a worried Dash) until you retry or dismiss it.
            uiState.errorMessage?.let { error ->
                DashAlertCard(
                    mood = DashMood.SYNC_ERROR,
                    title = stringResource(R.string.dashboard_sync_failed_title),
                    message = stringResource(R.string.dashboard_sync_failed_message, error),
                    onDismiss = { viewModel.clearError() }
                )
            }

            // Several overnight readings off your normal together - an early sign of illness.
            val signals = BodySignals.decode(uiState.dailySummary?.bodySignals)
            if (signals.size >= BodySignals.MIN_SIGNALS) {
                val described = signals.map { s ->
                    when (s.kind) {
                        BodySignals.Kind.RESTING_HR -> stringResource(R.string.dashboard_signal_rhr, s.change)
                        BodySignals.Kind.HRV -> stringResource(R.string.dashboard_signal_hrv, s.change)
                        BodySignals.Kind.BREATHING -> stringResource(R.string.dashboard_signal_breathing, s.change)
                        BodySignals.Kind.SKIN_TEMP -> stringResource(R.string.dashboard_signal_skin_temp, skinTempText(s.change))
                    }
                }
                DashAlertCard(
                    mood = DashMood.CONCERNED,
                    title = stringResource(R.string.dashboard_body_signals_title),
                    message = stringResource(R.string.dashboard_body_signals_message, described.joinToString(", "))
                )
            }

            // Modular Widget Engine: Render active widgets ordered by user preference
            val activeWidgets = (if (uiState.widgets.isNotEmpty()) uiState.widgets else DashboardWidget.createDefaultWidgets())
                .filter { it.isVisible }
                .sortedWith(DashboardWidget.DISPLAY_ORDER)

            activeWidgets.forEach { widget ->
                when (widget.type) {
                    WidgetType.RECOVERY_HERO -> {
                        // Recovery, sleep, strain and activity in one card.
                        TodayOverviewCard(
                            uiState = uiState,
                            onOpenRecovery = onNavigateToRecoveryDetails,
                            onOpenSleep = onNavigateToSleep,
                            onOpenWorkouts = onNavigateToTraining,
                            onSaveActivityGoals = { s, c, m -> viewModel.updateActivityGoals(s, c, m) }
                        )
                    }

                    WidgetType.DAILY_BRIEFING -> DailyBriefingCard(uiState = uiState)

                    WidgetType.STREAKS -> StreaksCard(streaks = uiState.streaks)

                    WidgetType.BODY_AGE -> BodyAgeCard(snapshot = uiState.bodyAge, onClick = onNavigateToBodyAge)

                    WidgetType.FASTING -> com.kevan.hangry.ui.fasting.FastingDashboardCard(
                        snapshot = fasting,
                        onStart = onStartFast,
                        onEnd = onEndFast,
                        onOpen = onNavigateToFasting
                    )

                    // Folded into the overview, or reachable from the tab bar and Log Meal button.
                    WidgetType.DAILY_ACTIVITY_RINGS, WidgetType.LOG_MEAL, WidgetType.SLEEP_STRAIN_RINGS,
                    WidgetType.SLEEP_SUMMARY, WidgetType.AI_SHORTCUTS -> Unit


                    WidgetType.STRESS_MONITOR -> {
                        val stress = uiState.stressResult
                        if (stress != null) {
                            StressCard(stressResult = stress)
                        }
                    }



                    // Heart rate, HRV, body check and overnight readings, all in one card.
                    WidgetType.VITALS_CARD -> VitalsCard(
                        summary = uiState.dailySummary,
                        comparisons = uiState.bodyCheck,
                        onClick = onNavigateToHeartMetrics
                    )

                    // Folded into the Vitals card.
                    WidgetType.HEART_METRICS, WidgetType.BODY_CHECK, WidgetType.OVERNIGHT_VITALS -> Unit

                    WidgetType.BODY_FAT_COMPOSITION -> {
                        val scan = uiState.latestBodyFatScan
                        BodyFatCompositionWidget(
                            scan = scan,
                            onClick = onNavigateToBodyMetrics,
                            lastWeightKg = uiState.latestWeightKg,
                            onLogWeight = onLogWeight
                        )
                    }

                    WidgetType.SUPPLEMENTS -> {
                        SupplementsDashboardCard(
                            snapshot = supplements,
                            onToggle = onToggleSupplementDose,
                            onOpen = onNavigateToSupplements
                        )
                    }

                    WidgetType.CYCLE -> CycleCard(healthRecords, uiState.recentSkinTemps, onClick = onNavigateToHealthRecords)

                    WidgetType.HEALTH_RECORDS -> {
                        HealthRecordsCard(records = healthRecords, onClick = onNavigateToHealthRecords)
                    }

                    WidgetType.BREATHING -> {
                        BreathingExercisesCard(
                            stats = breathingStats,
                            session = breathingSession,
                            onOpenPattern = { pattern -> onNavigateToBreathing(pattern) },
                            onOpenSession = { onNavigateToBreathing(null) }
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

            // Sync status footer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = HangryTokens.Spacing.s),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val syncTime = uiState.lastSyncFormatted
                Text(
                    text = when {
                        uiState.isSyncing -> stringResource(R.string.dashboard_syncing)
                        syncTime != null -> stringResource(R.string.sync_success, syncTime)
                        else -> stringResource(R.string.dashboard_not_synced_yet)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.textMuted
                )
                Text(
                    text = stringResource(R.string.data_source_label, "Health Connect"),
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.textMuted
                )
            }

        }
        }
    }

    nutritionUiState?.allergenAlert?.let { alert ->
        AllergenAlertDialog(
            alert = alert,
            onDismiss = { nutritionViewModel?.dismissAllergenAlert() },
            onEdit = {
                nutritionViewModel?.dismissAllergenAlert()
                nutritionViewModel?.startEdit(alert.entry)
                onNavigateToNutrition()
            }
        )
    }

    val manualReviewPhoto = nutritionUiState?.manualReviewPhoto
    if (manualReviewPhoto != null) {
        QuickMealLogSheet(
            initialPhotoUri = manualReviewPhoto,
            notice = nutritionUiState.manualReviewNotice,
            aiEnabled = nutritionUiState.aiFeaturesEnabled,
            mealPlans = nutritionUiState.mealPlans,
            onDismiss = { nutritionViewModel.dismissManualReview() },
            onLogMeal = { name, calories, uri, p, c, f, fiber ->
                nutritionViewModel.quickLogMeal(name, calories, uri, p, c, f, fiber)
            },
            onEstimateWithAi = if (nutritionUiState?.aiFeaturesEnabled == true) {
                { uri, note -> nutritionViewModel.estimateFood(uri, note) }
            } else null,
            onLogMealPlan = { plan -> nutritionViewModel.logFromMealPlan(plan) }
        )
    }

    if (uiState.showCustomizeSheet) {
        CustomizeDashboardSheet(
            widgets = (if (uiState.widgets.isNotEmpty()) uiState.widgets else DashboardWidget.createDefaultWidgets()).sortedWith(DashboardWidget.DISPLAY_ORDER),
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.dashboard_calories_burned),
                    style = MaterialTheme.typography.titleMedium,
                    color = tokens.textPrimary
                )
                HangryInfoTip(
                    title = stringResource(R.string.dashboard_calories_burned),
                    body = stringResource(
                        R.string.dashboard_calories_burned_info,
                        burn?.supportiveNote ?: stringResource(R.string.dashboard_calories_burned_setup_profile)
                    )
                )
            }
            val goal = uiState.calorieGoal
            if (goal?.dailyCalorieTarget != null) {
                Text(
                    text = stringResource(R.string.dashboard_calorie_goal, goal.dailyCalorieTarget),
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.textMuted
                )
            }
        }
        Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))

        val total = burn?.totalBurnedCalories
        Text(
            text = if (total != null) stringResource(R.string.dashboard_kcal_value, total.toInt()) else "—",
            style = MaterialTheme.typography.headlineLarge,
            color = tokens.chartColors.trainingLoad
        )
        Spacer(modifier = Modifier.height(HangryTokens.Spacing.xs))

        if (burn?.bmrCalories != null || burn?.neatCalories != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.m)
            ) {
                CalorieComponentLabel(stringResource(R.string.dashboard_bmr), burn.bmrCalories, tokens.textSecondary)
                CalorieComponentLabel(stringResource(R.string.dashboard_neat), burn.neatCalories, tokens.textSecondary)
                CalorieComponentLabel(stringResource(R.string.dashboard_exercise), burn.exerciseCalories, tokens.textSecondary)
            }
            Spacer(modifier = Modifier.height(HangryTokens.Spacing.xs))
        }

        if (burn?.bmrCalories == null) {
            Text(
                text = stringResource(R.string.dashboard_calories_burned_add_profile),
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textSecondary
            )
        }
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
private fun BodyFatCompositionWidget(
    scan: com.kevan.hangry.data.local.entity.BodyFatScanEntity?,
    onClick: () -> Unit,
    lastWeightKg: Double?,
    onLogWeight: ((Double, java.time.Instant) -> Unit)?
) {
    val tokens = LocalHangryTokens.current
    val units = rememberBodyUnitFormatter()
    var loggingWeight by remember { mutableStateOf(false) }
    if (loggingWeight && onLogWeight != null) {
        LogWeightSheet(
            lastWeightKg = lastWeightKg,
            onDismiss = { loggingWeight = false },
            onSave = { kg, at ->
                onLogWeight(kg, at)
                loggingWeight = false
            }
        )
    }
    HangryCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = stringResource(R.string.dashboard_all_body_metrics)) { onClick() },
        contentPadding = HangryTokens.Spacing.s + 4.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.FitnessCenter,
                contentDescription = null,
                tint = tokens.scoreColors.primed,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(HangryTokens.Spacing.xs))
            Text(
                text = stringResource(R.string.dashboard_body_composition),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = tokens.textPrimary,
                modifier = Modifier.weight(1f)
            )
            scan?.category?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = tokens.scoreColors.primed, maxLines = 1)
            }
            if (onLogWeight != null) {
                TextButton(
                    onClick = { loggingWeight = true },
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(stringResource(R.string.body_log_weight_action), style = MaterialTheme.typography.labelMedium)
                }
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = tokens.textMuted)
        }
        Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))
        if (scan != null) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.s)) {
                BodyStat(
                    value = String.format(Locale.US, "%.1f%%", scan.bodyFatPercentage),
                    label = stringResource(R.string.dashboard_body_fat),
                    color = tokens.scoreColors.primed,
                    modifier = Modifier.weight(1f)
                )
                BodyStat(
                    value = scan.leanMassKg?.let(units::weight) ?: "—",
                    label = stringResource(R.string.dashboard_lean_mass),
                    color = tokens.textPrimary,
                    modifier = Modifier.weight(1f)
                )
                BodyStat(
                    value = scan.fatMassKg?.let(units::weight) ?: "—",
                    label = stringResource(R.string.dashboard_fat_mass),
                    color = tokens.textPrimary,
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            Text(
                text = stringResource(R.string.dashboard_no_scan_yet),
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textSecondary
            )
        }
    }
}

@Composable
private fun BodyStat(value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = LocalHangryTokens.current.textMuted, maxLines = 1)
    }
}
