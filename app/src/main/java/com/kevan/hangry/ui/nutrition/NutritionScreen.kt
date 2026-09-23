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
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kevan.hangry.R
import com.kevan.hangry.data.local.entity.FoodLogEntity
import com.kevan.hangry.ui.components.DateNavigatorBar
import com.kevan.hangry.ui.components.HangryCard
import com.kevan.hangry.ui.theme.EmberAccent
import com.kevan.hangry.ui.components.HangryInfoIconButton
import com.kevan.hangry.ui.components.HangryInfoSection
import com.kevan.hangry.ui.components.HangryPendingNotice
import com.kevan.hangry.ui.dashboard.DashboardViewModel
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
        "Meal Plan",
        "Save your usual meals once, then log them instantly without calling the AI each time."
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
    onNavigateBack: () -> Unit,
    onNavigateToMealPlan: () -> Unit,
    onNavigateToAiSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalHangryTokens.current
    val haptic = LocalHapticFeedback.current
    val uiState by viewModel.uiState.collectAsState()
    val dashboardState by dashboardViewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val calorieTarget = dashboardState.calorieGoal?.dailyCalorieTarget ?: 2000
    val totalProtein = uiState.todayEntries.sumOf { it.proteinG }
    val totalCarbs = uiState.todayEntries.sumOf { it.carbsG }
    val totalFat = uiState.todayEntries.sumOf { it.fatG }
    val proteinGoal = (calorieTarget * 0.25 / 4.0).coerceAtLeast(50.0)
    val carbsGoal = (calorieTarget * 0.50 / 4.0).coerceAtLeast(100.0)
    val fatGoal = (calorieTarget * 0.25 / 9.0).coerceAtLeast(30.0)

    var showDescribeDialog by remember { mutableStateOf(false) }
    var showQuickLogSheet by remember { mutableStateOf(false) }
    var selectedPhotoUri by remember { mutableStateOf<Uri?>(null) }

    val photoLauncher = rememberPhotoCaptureLauncher { uri: Uri ->
        if (uiState.aiFeaturesEnabled) {
            viewModel.analyzePhoto(uri, note = null)
        } else {
            selectedPhotoUri = uri
            showQuickLogSheet = true
        }
    }

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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_nutrition)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    HangryInfoIconButton(title = "About Nutrition", sections = NUTRITION_INFO_SECTIONS)
                    IconButton(onClick = onNavigateToMealPlan) {
                        Icon(imageVector = Icons.Default.RestaurantMenu, contentDescription = "Meal Plan")
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
            contentPadding = PaddingValues(top = HangryTokens.Spacing.s, bottom = 16.dp),
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
                                Text(
                                    text = "AI Auto-Estimation is Off",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = tokens.textPrimary
                                )
                                Text(
                                    text = "You can log meals quickly below. Tap here to configure AI in Settings for auto-detection from photos.",
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
                        icon = Icons.Default.Add,
                        label = "Quick Log",
                        enabled = !uiState.isAnalyzing,
                        onClick = {
                            selectedPhotoUri = null
                            showQuickLogSheet = true
                        },
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
                }
            }

            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Quick Add",
                        style = MaterialTheme.typography.labelMedium,
                        color = tokens.textSecondary,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.s)
                    ) {
                        item {
                            SuggestionChip(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.quickLogMeal("Snack (100 kcal)", 100, null, 3.0, 15.0, 3.0)
                                },
                                label = { Text("+100 kcal Snack") }
                            )
                        }
                        item {
                            SuggestionChip(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.quickLogMeal("Quick Meal (250 kcal)", 250, null, 15.0, 30.0, 8.0)
                                },
                                label = { Text("+250 kcal Meal") }
                            )
                        }
                        item {
                            SuggestionChip(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.quickLogMeal("Full Meal (500 kcal)", 500, null, 30.0, 55.0, 18.0)
                                },
                                label = { Text("+500 kcal Meal") }
                            )
                        }
                        item {
                            SuggestionChip(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.quickLogMeal("Water (500 ml)", 0, null, 0.0, 0.0, 0.0)
                                },
                                label = { Text("+500ml Water") }
                            )
                        }
                    }
                }
            }

            if (uiState.isAnalyzing) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(HangryTokens.Spacing.s))
                        Text("Analyzing and logging…", style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary)
                    }
                }
            }

            if (uiState.mealPlans.isNotEmpty()) {
                item {
                    Text(text = "Meal Plan", style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.s)
                    ) {
                        uiState.mealPlans.take(4).forEach { plan ->
                            AssistChip(
                                onClick = { viewModel.logFromMealPlan(plan) },
                                label = { Text(plan.name) }
                            )
                        }
                    }
                }
            }

            item {
                Text(text = "Today's Log", style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
            }

            if (uiState.todayEntries.isEmpty()) {
                item {
                    HangryCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Fueling & Recovery Strategy",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = tokens.textPrimary
                            )
                            Surface(
                                color = tokens.chartColors.activeCalories.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "$calorieTarget kcal Target",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = tokens.chartColors.activeCalories,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Tracking your daily meals provides the energy and macronutrient data needed to calibrate training volume with metabolic recovery. Prioritize lean protein and hydration to optimize your recovery baseline.",
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.textSecondary,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { photoLauncher.takePhoto() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Snap Photo", style = MaterialTheme.typography.labelMedium)
                            }
                            Button(
                                onClick = {
                                    selectedPhotoUri = null
                                    showQuickLogSheet = true
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = EmberAccent)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Quick Log", style = MaterialTheme.typography.labelMedium)
                            }
                        }
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

            item { Spacer(modifier = Modifier.height(HangryTokens.Spacing.m)) }
        }
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

    if (showQuickLogSheet) {
        QuickMealLogSheet(
            initialPhotoUri = selectedPhotoUri,
            aiEnabled = uiState.aiFeaturesEnabled,
            mealPlans = uiState.mealPlans,
            onDismiss = {
                showQuickLogSheet = false
                selectedPhotoUri = null
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
                placeholder = { Text("e.g. Grilled chicken sandwich with fries") },
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
