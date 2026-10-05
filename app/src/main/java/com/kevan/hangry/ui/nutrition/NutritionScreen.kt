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
import com.kevan.hangry.ui.components.HangryInfoIconButton
import com.kevan.hangry.ui.components.HangryInfoSection
import com.kevan.hangry.ui.components.HangryInfoTip
import com.kevan.hangry.ui.components.HangryPendingNotice
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
import com.kevan.hangry.util.rememberPhotoCaptureLauncher
import java.io.File
import java.time.Instant
import java.time.ZoneId

private enum class MealBucket(@StringRes val titleRes: Int) {
    BREAKFAST(R.string.nutrition_meal_breakfast),
    LUNCH(R.string.nutrition_meal_lunch),
    DINNER(R.string.nutrition_meal_dinner),
    SNACKS(R.string.nutrition_meal_snacks)
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

@OptIn(ExperimentalMaterial3Api::class)
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
    val totalProtein = uiState.todayEntries.sumOf { it.proteinG }
    val totalCarbs = uiState.todayEntries.sumOf { it.carbsG }
    val totalFat = uiState.todayEntries.sumOf { it.fatG }
    val totalFiber = uiState.todayEntries.sumOf { it.fiberG }
    val macroGoals = NutritionTargets.macros(calorieTarget)
    val proteinGoal = macroGoals?.proteinG
    val carbsGoal = macroGoals?.carbsG
    val fatGoal = macroGoals?.fatG

    var showDescribeDialog by remember { mutableStateOf(false) }
    var showQuickLogSheet by remember { mutableStateOf(false) }

    var showOverflowMenu by remember { mutableStateOf(false) }

    val photoLauncher = rememberPhotoCaptureLauncher { uri: Uri -> viewModel.logMealFromPhoto(uri) }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    // Auto-save confirmation: brief, dismissible, with a one-tap correction path instead of a
    // blocking review dialog on every single entry.
    LaunchedEffect(uiState.lastSavedEntry) {
        val saved = uiState.lastSavedEntry ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = context.getString(R.string.nutrition_logged_snackbar, saved.foodName, saved.calories),
            actionLabel = context.getString(R.string.nutrition_edit),
            duration = SnackbarDuration.Short
        )
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.startEdit(saved)
        } else {
            viewModel.clearLastSaved()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState, modifier = Modifier.padding(bottom = LocalDockInset.current)) },
        topBar = {
            TopAppBar(
                // A tab, so no back arrow.
                title = { Text(stringResource(R.string.title_nutrition)) },
                actions = {
                    HangryInfoIconButton(title = stringResource(R.string.nutrition_about_title), sections = nutritionInfoSections())
                    IconButton(onClick = onNavigateToMealPlan) {
                        Icon(imageVector = Icons.Default.RestaurantMenu, contentDescription = stringResource(R.string.nutrition_cd_saved_meals))
                    }
                    // Typing a meal in by hand is deliberately tucked away here: the photo is the
                    // default path everywhere, and AI fills in the details.
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(imageVector = Icons.Default.MoreVert, contentDescription = stringResource(R.string.nutrition_cd_more_options))
                        }
                        DropdownMenu(expanded = showOverflowMenu, onDismissRequest = { showOverflowMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.nutrition_enter_manually)) },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    showQuickLogSheet = true
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .padding(horizontal = HangryTokens.Spacing.m),
            // Clear of the floating tab bar, which the list scrolls behind.
            contentPadding = PaddingValues(top = HangryTokens.Spacing.s, bottom = 16.dp + LocalDockInset.current),
            verticalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.m)
        ) {
            item {
                DateNavigatorBar(
                    selectedDate = uiState.selectedDate,
                    onDateSelected = { date -> viewModel.selectDate(date) }
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
                    fiberGoalG = NutritionTargets.fiberG(calorieTarget)
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.s)
                ) {
                    LogActionButton(
                        icon = Icons.Default.CameraAlt,
                        label = stringResource(R.string.nutrition_action_take_photo),
                        enabled = !uiState.isAnalyzing,
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
                    LogActionButton(
                        icon = Icons.Default.Bookmarks,
                        label = stringResource(R.string.nutrition_action_saved),
                        enabled = true,
                        onClick = onNavigateToMealPlan,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                WeeklyReviewEntryCard(onClick = { viewModel.openWeeklyReview(calorieTarget) })
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

            if (uiState.mealPlans.isNotEmpty()) {
                item {
                    QuickAddRow(
                        savedMeals = uiState.mealPlans,
                        onLogSaved = { meal, portion ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.logFromMealPlan(meal, portion)
                        },
                        onSeeAll = onNavigateToMealPlan
                    )
                }
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
                val groupedEntries = run {
                    val zone = ZoneId.systemDefault()
                    val map = linkedMapOf<MealBucket, MutableList<FoodLogEntity>>()
                    for (bucket in MealBucket.entries) {
                        map[bucket] = mutableListOf()
                    }
                    for (entry in uiState.todayEntries) {
                        val bucket = getMealBucket(entry.timestamp, zone)
                        map[bucket]?.add(entry)
                    }
                    map.filter { it.value.isNotEmpty() }
                }

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
                                viewModel.deleteEntry(entry)
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
                viewModel.deleteEntry(entry)
                viewModel.cancelEdit()
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

/** Opens the look back over the last 7 days of eating. */
@Composable
private fun WeeklyReviewEntryCard(onClick: () -> Unit) {
    val tokens = LocalHangryTokens.current
    HangryCard(modifier = Modifier.fillMaxWidth().clickable(onClickLabel = stringResource(R.string.nutrition_weekly_open_label), onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(tokens.brandAccentContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Insights, contentDescription = null, tint = tokens.brandAccent)
            }
            Spacer(modifier = Modifier.width(HangryTokens.Spacing.s))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.nutrition_info_weekly_title), style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                Text(
                    stringResource(R.string.nutrition_weekly_entry_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textMuted
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = tokens.textMuted)
        }
    }
}

/** One-tap logging for what the user eats most: their most recent saved meals. */
@Composable
private fun QuickAddRow(
    savedMeals: List<MealPlanEntity>,
    onLogSaved: (MealPlanEntity, Double) -> Unit,
    onSeeAll: () -> Unit
) {
    val tokens = LocalHangryTokens.current
    val recent = savedMeals.take(QUICK_ADD_COUNT)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = stringResource(R.string.nutrition_quick_add), style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
                Text(text = stringResource(R.string.nutrition_quick_add_hint), style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
            }
            TextButton(onClick = onSeeAll) { Text(stringResource(R.string.nutrition_all_saved_meals)) }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(recent, key = { "saved_${it.id}" }) { meal ->
                QuickAddChip(name = meal.name, calories = meal.calories, onLog = { portion -> onLogSaved(meal, portion) })
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
    modifier: Modifier = Modifier
) {
    val tokens = LocalHangryTokens.current
    HangryCard(
        modifier = modifier.clickable(enabled = enabled, onClick = onClick)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Icon(imageVector = icon, contentDescription = null, tint = if (enabled) tokens.chartColors.activeCalories else tokens.textMuted)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = tokens.textSecondary)
        }
    }
}

@Composable
private fun FoodLogRow(entry: FoodLogEntity, onClick: () -> Unit, onDelete: () -> Unit) {
    val tokens = LocalHangryTokens.current
    HangryCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (entry.photoPath != null && File(entry.photoPath).exists()) {
                AsyncImage(
                    model = entry.photoPath,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
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

@Composable
private fun DescribeFoodDialog(onDismiss: () -> Unit, onAnalyze: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
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
    var foodName by remember { mutableStateOf(entry.foodName) }
    var calories by remember { mutableStateOf(entry.calories.toString()) }
    val shownProtein = entry.proteinG.toInt().toString()
    val shownCarbs = entry.carbsG.toInt().toString()
    val shownFat = entry.fatG.toInt().toString()
    val shownFiber = entry.fiberG.toInt().toString()
    var protein by remember { mutableStateOf(shownProtein) }
    var carbs by remember { mutableStateOf(shownCarbs) }
    var fat by remember { mutableStateOf(shownFat) }
    var fiber by remember { mutableStateOf(shownFiber) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.nutrition_edit_entry_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (entry.photoPath != null && File(entry.photoPath).exists()) {
                    AsyncImage(
                        model = entry.photoPath,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
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
