package com.kevan.hangry.ui.nutrition

import android.net.Uri
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
import com.kevan.hangry.data.local.entity.MealPlanEntity
import com.kevan.hangry.data.local.entity.PORTIONS
import com.kevan.hangry.data.local.entity.portionLabel
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt
import com.kevan.hangry.domain.calculation.NutritionTargets
import com.kevan.hangry.domain.model.CommonFood
import com.kevan.hangry.domain.model.CommonFoods
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
import androidx.compose.ui.platform.LocalHapticFeedback
import com.kevan.hangry.util.rememberPhotoCaptureLauncher
import java.io.File
import java.time.Instant
import java.time.ZoneId

private enum class MealBucket(val title: String) {
    BREAKFAST("Breakfast"),
    LUNCH("Lunch"),
    DINNER("Dinner"),
    SNACKS("Snacks")
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

private val NUTRITION_INFO_SECTIONS = listOf(
    HangryInfoSection(
        "How it works",
        "Take or choose a food photo, or describe a meal in text - the AI estimates calories and macros and logs it immediately. Got it wrong? Tap the entry to fix it."
    ),
    HangryInfoSection(
        "Saved Meals",
        "Every food you log is saved automatically. Log it again in one tap from Quick add or Saved Meals - no retyping, no AI call. Common foods like a banana or an egg are there from day one."
    ),
    HangryInfoSection(
        "Health Connect",
        "Entries are written to Health Connect as nutrition records alongside the local daily log."
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

    // No target until there's enough data for a real one - never an assumed 2000 kcal.
    val calorieTarget = dashboardState.calorieGoal?.dailyCalorieTarget
    val totalProtein = uiState.todayEntries.sumOf { it.proteinG }
    val totalCarbs = uiState.todayEntries.sumOf { it.carbsG }
    val totalFat = uiState.todayEntries.sumOf { it.fatG }
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
            message = "Logged: ${saved.foodName} · ${saved.calories} kcal",
            actionLabel = "Edit",
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
                    HangryInfoIconButton(title = "About Nutrition", sections = NUTRITION_INFO_SECTIONS)
                    IconButton(onClick = onNavigateToMealPlan) {
                        Icon(imageVector = Icons.Default.RestaurantMenu, contentDescription = "Saved meals")
                    }
                    // Typing a meal in by hand is deliberately tucked away here: the photo is the
                    // default path everywhere, and AI fills in the details.
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(imageVector = Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(expanded = showOverflowMenu, onDismissRequest = { showOverflowMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Enter meal manually") },
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
                                        text = "AI Auto-Estimation is Off",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = tokens.textPrimary
                                    )
                                    HangryInfoTip(
                                        title = "AI Auto-Estimation",
                                        body = "You can log meals quickly below. Tap here to configure AI in Settings for auto-detection from photos."
                                    )
                                }
                                Text(
                                    text = "Tap to set up in Settings",
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
                    fatGoalG = fatGoal
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.s)
                ) {
                    LogActionButton(
                        icon = Icons.Default.CameraAlt,
                        label = "Take Photo",
                        enabled = !uiState.isAnalyzing,
                        onClick = { photoLauncher.takePhoto() },
                        modifier = Modifier.weight(1f)
                    )
                    LogActionButton(
                        icon = Icons.Default.Image,
                        label = "Gallery",
                        enabled = !uiState.isAnalyzing,
                        onClick = { photoLauncher.pickFromGallery() },
                        modifier = Modifier.weight(1f)
                    )
                    if (uiState.aiFeaturesEnabled) {
                        LogActionButton(
                            icon = Icons.Default.Edit,
                            label = "Describe",
                            enabled = !uiState.isAnalyzing,
                            onClick = { showDescribeDialog = true },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    LogActionButton(
                        icon = Icons.Default.Bookmarks,
                        label = "Saved",
                        enabled = true,
                        onClick = onNavigateToMealPlan,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (uiState.isAnalyzing) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DashSpinner(size = 44.dp, contentDescription = null)
                        Spacer(modifier = Modifier.width(HangryTokens.Spacing.s))
                        Text("Analyzing and logging…", style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary)
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
                    onLogCommon = { food, portion ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.logCommonFood(food, portion)
                    },
                    onSeeAll = onNavigateToMealPlan
                )
            }

            item {
                Text(text = dayHeading("Log", uiState.selectedDate), style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
            }

            if (uiState.todayEntries.isEmpty()) {
                item {
                    HangryCard(modifier = Modifier.fillMaxWidth()) {
                        DashEmptyState(
                            scene = DashEmptyScene.MEALS,
                            title = "Nothing logged yet",
                            body = "Snap a photo, or tap a food in Quick add to log it in one go.",
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
                                text = bucket.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = tokens.textSecondary
                            )
                            Text(
                                text = "${bucketEntries.sumOf { it.calories }} kcal",
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
                    val itemText = if (count == 1) "1 item" else "$count items"
                    DashNote(
                        mood = DashMood.NUTRITION,
                        text = "Great fueling! $totalKcal kcal tracked across $itemText. Keep up the mindful nourishment.",
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
            onLogMeal = { name, calories, uri, p, c, f ->
                viewModel.quickLogMeal(name, calories, uri, p, c, f)
            },
            onEstimateWithAi = if (uiState.aiFeaturesEnabled) {
                { uri, note -> viewModel.estimateFood(uri, note) }
            } else null,
            onLogMealPlan = { plan -> viewModel.logFromMealPlan(plan) }
        )
    }
}

/**
 * One-tap logging for what the user eats most: their most recent saved meals first, topped up
 * with everyday basics so there's something to tap before anything has been saved.
 */
@Composable
private fun QuickAddRow(
    savedMeals: List<MealPlanEntity>,
    onLogSaved: (MealPlanEntity, Double) -> Unit,
    onLogCommon: (CommonFood, Double) -> Unit,
    onSeeAll: () -> Unit
) {
    val tokens = LocalHangryTokens.current
    val recent = savedMeals.take(QUICK_ADD_COUNT)
    val savedKeys = savedMeals.map { it.nameKey }.toSet()
    val starters = CommonFoods.starters.filter { it.key !in savedKeys }.take((QUICK_ADD_COUNT - recent.size).coerceAtLeast(0))
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = "Quick add", style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
                Text(text = "Hold for ½× or 2×", style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
            }
            TextButton(onClick = onSeeAll) { Text("All saved meals") }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(recent, key = { "saved_${it.id}" }) { meal ->
                QuickAddChip(name = meal.name, calories = meal.calories, onLog = { portion -> onLogSaved(meal, portion) })
            }
            items(starters, key = { "common_${it.name}" }) { food ->
                QuickAddChip(name = food.name, calories = food.calories, onLog = { portion -> onLogCommon(food, portion) })
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
                    onClickLabel = "Log $name",
                    onLongClickLabel = "Choose portion",
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
                    text = "$name · $calories",
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
                    text = { Text("${portionLabel(portion)}× · ${(calories * portion).roundToInt()} kcal") },
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
                    text = "${entry.calories} kcal · P${entry.proteinG.toInt()} C${entry.carbsG.toInt()} F${entry.fatG.toInt()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.textMuted
                )
            }
            IconButton(onClick = onDelete) {
                Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = "Delete", tint = tokens.textMuted)
            }
        }
    }
}

@Composable
private fun DescribeFoodDialog(onDismiss: () -> Unit, onAnalyze: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Describe Your Meal") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("e.g. Chicken sandwich & fries") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = { onAnalyze(text) }) { Text("Log It") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
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
    var protein by remember { mutableStateOf(entry.proteinG.toInt().toString()) }
    var carbs by remember { mutableStateOf(entry.carbsG.toInt().toString()) }
    var fat by remember { mutableStateOf(entry.fatG.toInt().toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Entry") },
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
                OutlinedTextField(value = foodName, onValueChange = { foodName = it }, label = { Text("Food") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = calories, onValueChange = { calories = it }, label = { Text("Calories") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = protein, onValueChange = { protein = it }, label = { Text("Protein g") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = carbs, onValueChange = { carbs = it }, label = { Text("Carbs g") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = fat, onValueChange = { fat = it }, label = { Text("Fat g") }, modifier = Modifier.weight(1f))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        entry.copy(
                            foodName = foodName,
                            calories = calories.toIntOrNull() ?: entry.calories,
                            proteinG = protein.toDoubleOrNull() ?: entry.proteinG,
                            carbsG = carbs.toDoubleOrNull() ?: entry.carbsG,
                            fatG = fat.toDoubleOrNull() ?: entry.fatG
                        )
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("Delete") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}
