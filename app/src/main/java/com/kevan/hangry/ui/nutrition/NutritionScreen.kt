package com.kevan.hangry.ui.nutrition

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.kevan.hangry.R
import com.kevan.hangry.data.local.entity.FoodLogEntity
import com.kevan.hangry.data.local.entity.FoodLogSource
import com.kevan.hangry.data.local.entity.MealPlanEntity
import com.kevan.hangry.data.local.entity.PORTIONS
import com.kevan.hangry.data.local.entity.portionLabel
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt
import com.kevan.hangry.domain.calculation.NutritionTargets
import com.kevan.hangry.ui.coach.DashMood
import com.kevan.hangry.ui.coach.DashNote
import com.kevan.hangry.ui.coach.DashSpinner
import com.kevan.hangry.ui.components.DashEmptyScene
import com.kevan.hangry.ui.components.DashEmptyState
import com.kevan.hangry.ui.components.DateNavigatorBar
import com.kevan.hangry.ui.components.HangryCard
import com.kevan.hangry.ui.components.HangryInfoDialog
import com.kevan.hangry.ui.components.HangryInfoSection
import com.kevan.hangry.ui.components.HangryInfoTip
import com.kevan.hangry.ui.components.HangryPendingNotice
import com.kevan.hangry.ui.components.UndoDeleteEffect
import com.kevan.hangry.ui.components.dayHeading
import com.kevan.hangry.ui.dashboard.DashboardViewModel
import com.kevan.hangry.ui.navigation.LocalDockInset
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.annotation.StringRes
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.health.connect.client.PermissionController
import com.kevan.hangry.util.rememberPhotoCaptureLauncher
import java.time.Instant
import java.time.ZoneId

private enum class MealBucket(@StringRes val titleRes: Int) {
    BREAKFAST(R.string.nutrition_meal_breakfast),
    LUNCH(R.string.nutrition_meal_lunch),
    DINNER(R.string.nutrition_meal_dinner),
    SNACKS(R.string.nutrition_meal_snacks)
}

/** Today's entries under Breakfast / Lunch / Dinner / Snacks, empty meals left out. */
private fun groupByMeal(entries: List<FoodLogEntity>): Map<MealBucket, List<FoodLogEntity>> {
    val zone = ZoneId.systemDefault()
    return entries.groupBy { getMealBucket(it.timestamp, zone) }.toSortedMap(compareBy { it.ordinal })
}

/** The "Logged X" snackbar's visuals: Edit is its action, Undo a second button beside it. */
private class LoggedSnackbarVisuals(
    override val message: String,
    override val actionLabel: String,
    val undoLabel: String
) : SnackbarVisuals {
    override val withDismissAction: Boolean get() = false
    override val duration: SnackbarDuration get() = SnackbarDuration.Short
    /** Set when Undo closed the snackbar, which otherwise reads as a plain dismissal. */
    var undone = false
}

@Composable
private fun LoggedSnackbar(data: SnackbarData, visuals: LoggedSnackbarVisuals) {
    val actionColors = ButtonDefaults.textButtonColors(contentColor = SnackbarDefaults.actionColor)
    Snackbar(
        modifier = Modifier.padding(12.dp),
        action = {
            Row {
                TextButton(
                    onClick = {
                        visuals.undone = true
                        data.dismiss()
                    },
                    colors = actionColors
                ) { Text(visuals.undoLabel) }
                TextButton(onClick = { data.performAction() }, colors = actionColors) { Text(visuals.actionLabel) }
            }
        }
    ) {
        Text(visuals.message)
    }
}

private fun getMealBucket(timestamp: Instant, zone: ZoneId = ZoneId.systemDefault()): MealBucket {
    val hour = timestamp.atZone(zone).hour
    return when (hour) {
        in 4..10 -> MealBucket.BREAKFAST
        in 11..15 -> MealBucket.LUNCH
        in 16..20 -> MealBucket.DINNER
        else -> MealBucket.SNACKS
    }
}

@Composable
private fun nutritionInfoSections(): List<HangryInfoSection> = listOf(
    HangryInfoSection(
        stringResource(R.string.nutrition_info_how_title),
        stringResource(R.string.nutrition_info_how_body)
    ),
    HangryInfoSection(
        stringResource(R.string.nutrition_info_weekly_title),
        stringResource(R.string.nutrition_info_weekly_body)
    ),
    HangryInfoSection(
        stringResource(R.string.nutrition_info_saved_title),
        stringResource(R.string.nutrition_info_saved_body)
    ),
    HangryInfoSection(
        stringResource(R.string.nutrition_info_hc_title),
        stringResource(R.string.nutrition_info_hc_body)
    )
)

@Composable
fun NutritionScreen(
    viewModel: NutritionViewModel,
    dashboardViewModel: DashboardViewModel,
    onNavigateToMealPlan: () -> Unit,
    onNavigateToAiSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalHangryTokens.current
    val haptic = LocalHapticFeedback.current
    val uiState by viewModel.uiState.collectAsState()
    val dashboardState by dashboardViewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // No target until there's enough data for a real one - never an assumed 2000 kcal.
    val calorieTarget = dashboardState.calorieGoal?.dailyCalorieTarget
    val entries = uiState.todayEntries
    val totalProtein = remember(entries) { entries.sumOf { it.proteinG } }
    val totalCarbs = remember(entries) { entries.sumOf { it.carbsG } }
    val totalFat = remember(entries) { entries.sumOf { it.fatG } }
    val totalFiber = remember(entries) { entries.sumOf { it.fiberG } }
    val groupedEntries = remember(entries) { groupByMeal(entries) }
    val macroGoals = NutritionTargets.macros(calorieTarget)
    val proteinGoal = macroGoals?.proteinG
    val carbsGoal = macroGoals?.carbsG
    val fatGoal = macroGoals?.fatG

    var showDescribeDialog by rememberSaveable { mutableStateOf(false) }
    var showQuickLogSheet by rememberSaveable { mutableStateOf(false) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val tabReselectionBus = com.kevan.hangry.ui.navigation.LocalTabReselectionBus.current
    LaunchedEffect(tabReselectionBus) {
        tabReselectionBus.events.collect { route ->
            if (route == com.kevan.hangry.ui.navigation.Screen.Nutrition.route) {
                listState.animateScrollToItem(0)
            }
        }
    }
    // Hidden at once; the entry, its photo and its Health Connect record go when the snackbar closes.
    var pendingDelete by remember { mutableStateOf<FoodLogEntity?>(null) }
    val deleteEntry: (FoodLogEntity) -> Unit = { entry ->
        viewModel.hideEntry(entry)
        pendingDelete = entry
    }
    UndoDeleteEffect(
        pending = pendingDelete,
        snackbarHostState = snackbarHostState,
        message = { context.getString(R.string.nutrition_entry_deleted_snackbar, it.foodName) },
        onUndo = viewModel::undoDelete,
        onCommit = viewModel::commitDelete,
        onSettled = { pendingDelete = null }
    )

    val photoLauncher = rememberPhotoCaptureLauncher { uri: Uri -> viewModel.logMealFromPhoto(uri) }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    // Auto-save confirmation: brief, dismissible, with one-tap Edit and Undo instead of a
    // blocking review dialog on every single entry - a wrong AI guess is one tap from gone.
    LaunchedEffect(uiState.lastSavedEntry) {
        val saved = uiState.lastSavedEntry ?: return@LaunchedEffect
        val visuals = LoggedSnackbarVisuals(
            message = context.getString(R.string.nutrition_logged_snackbar, saved.foodName, saved.calories),
            actionLabel = context.getString(R.string.nutrition_edit),
            undoLabel = context.getString(R.string.nutrition_undo)
        )
        val result = snackbarHostState.showSnackbar(visuals)
        when {
            result == SnackbarResult.ActionPerformed -> viewModel.startEdit(saved)
            visuals.undone -> {
                viewModel.clearLastSaved()
                viewModel.deleteEntry(saved)
            }
            else -> viewModel.clearLastSaved()
        }
    }

    // Water: "Added 250 ml of water" with Undo. Asking for Health Connect write access (first
    // time only) happens alongside; the drink is already saved locally whatever the answer.
    val waterPermissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { viewModel.onWaterPermissionResult() }
    LaunchedEffect(uiState.waterPermissionRequest) {
        val request = uiState.waterPermissionRequest
        if (request.isEmpty()) return@LaunchedEffect
        viewModel.consumeWaterPermissionRequest()
        runCatching { waterPermissionLauncher.launch(request) }
    }
    LaunchedEffect(uiState.lastWaterEntry) {
        val added = uiState.lastWaterEntry ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = context.getString(
                R.string.nutrition_water_added_snackbar,
                context.resources.waterText(WaterFormat.amount(added.volumeMl))
            ),
            actionLabel = context.getString(R.string.nutrition_undo),
            duration = SnackbarDuration.Short
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.undoWater(added)
        viewModel.clearLastWater()
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState, modifier = Modifier.padding(bottom = LocalDockInset.current)) { data ->
                val logged = data.visuals as? LoggedSnackbarVisuals
                if (logged == null) Snackbar(data) else LoggedSnackbar(data, logged)
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .padding(horizontal = HangryTokens.Spacing.m),
            // Clear of the floating tab bar, which the list scrolls behind.
            contentPadding = PaddingValues(top = HangryTokens.Spacing.s, bottom = 16.dp + LocalDockInset.current),
            verticalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.section)
        ) {
            // A tab without a top bar: the date bar leads, with the screen's menu at its end.
            item {
                DateNavigatorBar(
                    selectedDate = uiState.selectedDate,
                    onDateSelected = { date -> viewModel.selectDate(date) },
                    actions = {
                        NutritionMenu(
                            onWeeklyReview = { viewModel.openWeeklyReview(calorieTarget) },
                            onSavedMeals = onNavigateToMealPlan,
                            onEnterManually = { showQuickLogSheet = true }
                        )
                    }
                )
            }

            if (!uiState.aiFeaturesEnabled) {
                item {
                    HangryCard(modifier = Modifier.clickable { onNavigateToAiSettings() }) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = stringResource(R.string.nutrition_ai_off_title),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = tokens.textPrimary
                                    )
                                    HangryInfoTip(
                                        title = stringResource(R.string.nutrition_ai_tip_title),
                                        body = stringResource(R.string.nutrition_ai_tip_body)
                                    )
                                }
                                Text(
                                    text = stringResource(R.string.nutrition_ai_off_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = tokens.textMuted
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = tokens.textMuted
                            )
                        }
                    }
                }
            }

            item {
                MacroProgressBar(
                    totalCalories = uiState.totalCaloriesToday,
                    targetCalories = calorieTarget,
                    proteinG = totalProtein,
                    proteinGoalG = proteinGoal,
                    carbsG = totalCarbs,
                    carbsGoalG = carbsGoal,
                    fatG = totalFat,
                    fatGoalG = fatGoal,
                    fiberG = totalFiber,
                    fiberGoalG = NutritionTargets.fiberG(calorieTarget),
                    water = uiState.water,
                    onAddWater = viewModel::logWater
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.s)
                ) {
                    LogActionButton(
                        icon = Icons.Default.CameraAlt,
                        label = stringResource(R.string.nutrition_action_photo),
                        enabled = !uiState.isAnalyzing,
                        primary = true,
                        onClick = { photoLauncher.takePhoto() },
                        modifier = Modifier.weight(1f)
                    )
                    LogActionButton(
                        icon = Icons.Default.Image,
                        label = stringResource(R.string.nutrition_action_gallery),
                        enabled = !uiState.isAnalyzing,
                        onClick = { photoLauncher.pickFromGallery() },
                        modifier = Modifier.weight(1f)
                    )
                    if (uiState.aiFeaturesEnabled) {
                        LogActionButton(
                            icon = Icons.Default.Edit,
                            label = stringResource(R.string.nutrition_action_describe),
                            enabled = !uiState.isAnalyzing,
                            onClick = { showDescribeDialog = true },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            if (uiState.isAnalyzing) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DashSpinner(size = 44.dp, contentDescription = null)
                        Spacer(modifier = Modifier.width(HangryTokens.Spacing.s))
                        Text(stringResource(R.string.nutrition_analyzing), style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary)
                    }
                }
            }

            item {
                QuickAddRow(
                    savedMeals = uiState.mealPlans,
                    onLogSaved = { meal, portion ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.logFromMealPlan(meal, portion)
                    },
                    onLogStaple = { staple ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.quickLogMeal(
                            foodName = staple.name,
                            calories = staple.calories,
                            proteinG = staple.proteinG,
                            carbsG = staple.carbsG,
                            fatG = staple.fatG,
                            fiberG = staple.fiberG
                        )
                    },
                    onSeeAll = onNavigateToMealPlan
                )
            }

            item {
                Text(text = dayHeading(stringResource(R.string.nutrition_log_heading), uiState.selectedDate), style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
            }

            if (uiState.todayEntries.isEmpty()) {
                item {
                    HangryCard(modifier = Modifier.fillMaxWidth()) {
                        DashEmptyState(
                            scene = DashEmptyScene.MEALS,
                            title = stringResource(R.string.nutrition_empty_title),
                            body = stringResource(R.string.nutrition_empty_body),
                            modifier = Modifier.padding(vertical = HangryTokens.Spacing.s)
                        )
                    }
                }
            } else {
                groupedEntries.forEach { (bucket, bucketEntries) ->
                    item(key = "header_${bucket.name}") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = HangryTokens.Spacing.xs),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(bucket.titleRes),
                                style = MaterialTheme.typography.titleSmall,
                                color = tokens.textSecondary
                            )
                            Text(
                                text = stringResource(R.string.nutrition_kcal, bucketEntries.sumOf { it.calories }),
                                style = MaterialTheme.typography.labelSmall,
                                color = tokens.chartColors.activeCalories
                            )
                        }
                    }

                    items(bucketEntries, key = { it.id }) { entry ->
                        FoodLogRow(
                            entry = entry,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.startEdit(entry)
                            },
                            onDelete = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                deleteEntry(entry)
                            }
                        )
                    }
                }
            }

            if (uiState.todayEntries.isNotEmpty()) {
                item {
                    val totalKcal = uiState.todayEntries.sumOf { it.calories }
                    val count = uiState.todayEntries.size
                    val itemText = if (count == 1) stringResource(R.string.nutrition_one_item) else stringResource(R.string.nutrition_n_items, count)
                    DashNote(
                        mood = DashMood.NUTRITION,
                        text = stringResource(R.string.nutrition_great_fueling, totalKcal, itemText),
                        modifier = Modifier.padding(top = HangryTokens.Spacing.xs)
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(HangryTokens.Spacing.m)) }
        }
    }

    uiState.allergenAlert?.let { alert ->
        AllergenAlertDialog(
            alert = alert,
            onDismiss = viewModel::dismissAllergenAlert,
            onEdit = {
                viewModel.dismissAllergenAlert()
                viewModel.startEdit(alert.entry)
            }
        )
    }

    uiState.weeklyReview?.let { review ->
        WeeklyReviewSheet(
            state = review,
            onDismiss = viewModel::closeWeeklyReview,
            onRefresh = viewModel::refreshWeeklyReview,
            onOpenAiSettings = {
                viewModel.closeWeeklyReview()
                onNavigateToAiSettings()
            }
        )
    }

    if (showDescribeDialog) {
        DescribeFoodDialog(
            onDismiss = { showDescribeDialog = false },
            onAnalyze = { text ->
                showDescribeDialog = false
                viewModel.analyzeDescription(text)
            }
        )
    }

    uiState.editingEntry?.let { entry ->
        EditFoodEntryDialog(
            entry = entry,
            onDismiss = viewModel::cancelEdit,
            onSave = viewModel::saveEdit,
            onDelete = {
                viewModel.cancelEdit()
                deleteEntry(entry)
            }
        )
    }

    if (showQuickLogSheet || uiState.manualReviewPhoto != null) {
        QuickMealLogSheet(
            initialPhotoUri = uiState.manualReviewPhoto,
            notice = uiState.manualReviewNotice,
            aiEnabled = uiState.aiFeaturesEnabled,
            mealPlans = uiState.mealPlans,
            onDismiss = {
                showQuickLogSheet = false
                viewModel.dismissManualReview()
            },
            onLogMeal = { name, calories, uri, p, c, f, fiber ->
                viewModel.quickLogMeal(name, calories, uri, p, c, f, fiber)
            },
            onEstimateWithAi = if (uiState.aiFeaturesEnabled) {
                { uri, note -> viewModel.estimateFood(uri, note) }
            } else null,
            onLogMealPlan = { plan -> viewModel.logFromMealPlan(plan) }
        )
    }
}

/**
 * Everything that used to sit in the top bar: the weekly review, saved meals, typing a meal in by
 * hand (deliberately tucked away - the photo is the default path) and what this screen does.
 */
@Composable
private fun NutritionMenu(onWeeklyReview: () -> Unit, onSavedMeals: () -> Unit, onEnterManually: () -> Unit) {
    val tokens = LocalHangryTokens.current
    var expanded by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.nutrition_cd_more_options), tint = tokens.textSecondary, modifier = Modifier.size(20.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.nutrition_info_weekly_title)) },
                leadingIcon = { Icon(Icons.Default.Insights, contentDescription = null) },
                onClick = { expanded = false; onWeeklyReview() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.nutrition_cd_saved_meals)) },
                leadingIcon = { Icon(Icons.Default.Bookmarks, contentDescription = null) },
                onClick = { expanded = false; onSavedMeals() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.nutrition_enter_manually)) },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                onClick = { expanded = false; onEnterManually() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.nutrition_about_title)) },
                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                onClick = { expanded = false; showAbout = true }
            )
        }
    }
    if (showAbout) {
        HangryInfoDialog(title = stringResource(R.string.nutrition_about_title), sections = nutritionInfoSections(), onDismiss = { showAbout = false })
    }
}

private data class StarterStaple(val name: String, val calories: Int, val proteinG: Double, val carbsG: Double, val fatG: Double, val fiberG: Double)
private val STARTER_STAPLES = listOf(
    StarterStaple("Protein Shake", 120, 24.0, 3.0, 1.5, 0.0),
    StarterStaple("Banana", 105, 1.3, 27.0, 0.3, 3.1),
    StarterStaple("2 Eggs", 140, 12.0, 1.0, 10.0, 0.0),
    StarterStaple("Greek Yogurt", 130, 15.0, 6.0, 4.0, 0.0),
    StarterStaple("Oatmeal", 150, 5.0, 27.0, 2.5, 4.0),
    StarterStaple("Apple", 95, 0.5, 25.0, 0.3, 4.4)
)

/** One-tap logging for what the user eats most: their most recent saved meals or starter healthy staples. */
@Composable
private fun QuickAddRow(
    savedMeals: List<MealPlanEntity>,
    onLogSaved: (MealPlanEntity, Double) -> Unit,
    onLogStaple: (StarterStaple) -> Unit,
    onSeeAll: () -> Unit
) {
    val tokens = LocalHangryTokens.current
    val recent = savedMeals.take(QUICK_ADD_COUNT)
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = stringResource(R.string.nutrition_quick_add), style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
            Text(
                text = stringResource(R.string.nutrition_quick_add_hint),
                style = MaterialTheme.typography.labelSmall,
                color = tokens.textMuted,
                modifier = Modifier.padding(start = HangryTokens.Spacing.s).weight(1f)
            )
            if (savedMeals.isNotEmpty()) {
                TextButton(onClick = onSeeAll, contentPadding = PaddingValues(horizontal = 8.dp)) { Text(stringResource(R.string.nutrition_see_all)) }
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (recent.isNotEmpty()) {
                items(recent, key = { "saved_${it.id}" }) { meal ->
                    QuickAddChip(name = meal.name, calories = meal.calories, onLog = { portion -> onLogSaved(meal, portion) })
                }
            } else {
                items(STARTER_STAPLES, key = { "staple_${it.name}" }) { staple ->
                    QuickAddChip(name = staple.name, calories = staple.calories, onLog = { onLogStaple(staple) })
                }
            }
        }
    }
}

private const val QUICK_ADD_COUNT = 10

/** Tap logs one portion; a long press offers the other portion sizes. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickAddChip(name: String, calories: Int, onLog: (portion: Double) -> Unit) {
    val tokens = LocalHangryTokens.current
    val haptic = LocalHapticFeedback.current
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color.Transparent,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .heightIn(min = 32.dp)
                .clip(RoundedCornerShape(8.dp))
                .combinedClickable(
                    onClickLabel = stringResource(R.string.nutrition_log_item_label, name),
                    onLongClickLabel = stringResource(R.string.nutrition_choose_portion),
                    onClick = { onLog(1.0) },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuOpen = true
                    }
                )
        ) {
            Row(
                modifier = Modifier.padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = tokens.textSecondary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.nutrition_name_dot_value, name, calories),
                    style = MaterialTheme.typography.labelLarge,
                    color = tokens.textPrimary,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 200.dp)
                )
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            PORTIONS.forEach { portion ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.nutrition_portion_kcal, portionLabel(portion), (calories * portion).roundToInt())) },
                    onClick = {
                        menuOpen = false
                        onLog(portion)
                    }
                )
            }
        }
    }
}

@Composable
private fun LogActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false
) {
    val padding = PaddingValues(horizontal = 8.dp)
    val content: @Composable RowScope.() -> Unit = {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = label, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
    if (primary) {
        Button(onClick = onClick, enabled = enabled, contentPadding = padding, modifier = modifier, content = content)
    } else {
        FilledTonalButton(onClick = onClick, enabled = enabled, contentPadding = padding, modifier = modifier, content = content)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FoodLogRow(entry: FoodLogEntity, onClick: () -> Unit, onDelete: () -> Unit) {
    val tokens = LocalHangryTokens.current
    // No file check in composition: a photo that's gone just fails to load and the slot drops out.
    var photoMissing by remember(entry.photoPath) { mutableStateOf(false) }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            val color = MaterialTheme.colorScheme.errorContainer
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(HangryTokens.CornerRadii.medium))
                    .background(color)
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = stringResource(R.string.nutrition_delete),
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    ) {
        HangryCard(modifier = Modifier.clickable(onClick = onClick)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (entry.photoPath != null && !photoMissing) {
                    AsyncImage(
                        model = entry.photoPath,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        onError = { photoMissing = true },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(modifier = Modifier.width(HangryTokens.Spacing.s))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = entry.foodName, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                    Text(
                        text = stringResource(R.string.nutrition_entry_macros, entry.calories, entry.proteinG.toInt(), entry.carbsG.toInt(), entry.fatG.toInt()) +
                            if (entry.fiberG >= 0.5) stringResource(R.string.nutrition_entry_fiber_suffix, entry.fiberG.roundToInt()) else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.textMuted
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = stringResource(R.string.nutrition_delete), tint = tokens.textMuted)
                }
            }
        }
    }
}

@Composable
private fun DescribeFoodDialog(onDismiss: () -> Unit, onAnalyze: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.nutrition_describe_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(stringResource(R.string.nutrition_describe_placeholder)) },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = { onAnalyze(text) }) { Text(stringResource(R.string.nutrition_log_it)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

/** Correction dialog for an already-saved entry - reached via the auto-save snackbar's Edit action or tapping a log row. */
@Composable
private fun EditFoodEntryDialog(
    entry: FoodLogEntity,
    onDismiss: () -> Unit,
    onSave: (FoodLogEntity) -> Unit,
    onDelete: () -> Unit
) {
    var photoMissing by remember(entry.photoPath) { mutableStateOf(false) }
    var foodName by rememberSaveable(entry.id) { mutableStateOf(entry.foodName) }
    var calories by rememberSaveable(entry.id) { mutableStateOf(entry.calories.toString()) }
    val shownProtein = entry.proteinG.toInt().toString()
    val shownCarbs = entry.carbsG.toInt().toString()
    val shownFat = entry.fatG.toInt().toString()
    val shownFiber = entry.fiberG.toInt().toString()
    var protein by rememberSaveable(entry.id) { mutableStateOf(shownProtein) }
    var carbs by rememberSaveable(entry.id) { mutableStateOf(shownCarbs) }
    var fat by rememberSaveable(entry.id) { mutableStateOf(shownFat) }
    var fiber by rememberSaveable(entry.id) { mutableStateOf(shownFiber) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.nutrition_edit_entry_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (entry.photoPath != null && !photoMissing) {
                    AsyncImage(
                        model = entry.photoPath,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        onError = { photoMissing = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                }
                if (entry.source == FoodLogSource.HEALTH_CONNECT) {
                    // Health Connect only lets the app that wrote a record change it.
                    Text(
                        stringResource(R.string.nutrition_edit_hc_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedTextField(value = foodName, onValueChange = { foodName = it }, label = { Text(stringResource(R.string.nutrition_field_food)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = calories, onValueChange = { calories = it }, label = { Text(stringResource(R.string.nutrition_field_calories)) }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = protein, onValueChange = { protein = it }, label = { Text(stringResource(R.string.nutrition_field_protein_g)) }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = carbs, onValueChange = { carbs = it }, label = { Text(stringResource(R.string.nutrition_field_carbs_g)) }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = fat, onValueChange = { fat = it }, label = { Text(stringResource(R.string.nutrition_field_fat_g)) }, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(value = fiber, onValueChange = { fiber = it }, label = { Text(stringResource(R.string.nutrition_field_fiber_g)) }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        entry.copy(
                            foodName = foodName,
                            calories = calories.toIntOrNull() ?: entry.calories,
                            // Untouched fields keep their decimals instead of the rounded value shown.
                            proteinG = protein.takeIf { it != shownProtein }?.toDoubleOrNull() ?: entry.proteinG,
                            carbsG = carbs.takeIf { it != shownCarbs }?.toDoubleOrNull() ?: entry.carbsG,
                            fatG = fat.takeIf { it != shownFat }?.toDoubleOrNull() ?: entry.fatG,
                            fiberG = fiber.takeIf { it != shownFiber }?.toDoubleOrNull() ?: entry.fiberG
                        )
                    )
                }
            ) { Text(stringResource(R.string.nutrition_save)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text(stringResource(R.string.nutrition_delete)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        }
    )
}
