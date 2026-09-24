package com.kevan.hangry.ui.nutrition

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kevan.hangry.R
import com.kevan.hangry.data.local.entity.MealPlanEntity
import com.kevan.hangry.data.local.entity.PORTIONS
import com.kevan.hangry.data.local.entity.portionLabel
import com.kevan.hangry.data.local.entity.portionedName
import com.kevan.hangry.domain.model.CommonFood
import com.kevan.hangry.domain.model.CommonFoodCategory
import com.kevan.hangry.domain.model.CommonFoods
import com.kevan.hangry.ui.components.DashEmptyScene
import com.kevan.hangry.ui.components.DashEmptyState
import com.kevan.hangry.ui.components.HangryCard
import com.kevan.hangry.ui.components.dayLabel
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens
import kotlin.math.roundToInt

/**
 * Every food the user has logged before, plus everyday basics, one tap from being logged again.
 * Stays open after a tap so a whole meal (eggs, toast, coffee) can go in back to back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedMealsScreen(
    viewModel: NutritionViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalHangryTokens.current
    val haptic = LocalHapticFeedback.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<CommonFoodCategory?>(null) }
    // Sticks between taps so e.g. a double helping of several foods is quick; every row shows
    // the numbers for the chosen portion, so it's never a surprise.
    var portion by rememberSaveable { mutableDoubleStateOf(1.0) }
    var editing by remember { mutableStateOf<MealPlanEntity?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var deleted by remember { mutableStateOf<MealPlanEntity?>(null) }

    val q = query.trim().lowercase()
    val saved = uiState.mealPlans.filter { q.isEmpty() || it.name.lowercase().contains(q) }
    val savedKeys = remember(uiState.mealPlans) { uiState.mealPlans.map { it.nameKey }.toSet() }
    // A basic food the user has logged already shows once, under their own meals.
    val common = CommonFoods.all.filter {
        it.key !in savedKeys &&
            (category == null || it.category == category) &&
            (q.isEmpty() || it.name.lowercase().contains(q))
    }

    LaunchedEffect(uiState.lastSavedEntry) {
        val entry = uiState.lastSavedEntry ?: return@LaunchedEffect
        viewModel.clearLastSaved()
        val result = snackbarHostState.showSnackbar(
            message = "Logged ${entry.foodName} · ${entry.calories} kcal",
            actionLabel = "Undo",
            duration = SnackbarDuration.Short
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.deleteEntry(entry)
    }

    LaunchedEffect(deleted) {
        val meal = deleted ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = "Removed ${meal.name}",
            actionLabel = "Undo",
            duration = SnackbarDuration.Short
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.restoreSavedMeal(meal)
        deleted = null
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.title_meal_plan))
                        Text(
                            text = "Tap to log to ${dayLabel(uiState.selectedDate)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.textMuted
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Add a meal")
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
                .padding(innerPadding)
                .padding(horizontal = HangryTokens.Spacing.m),
            contentPadding = PaddingValues(vertical = HangryTokens.Spacing.s),
            verticalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.s)
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search foods") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                PortionPicker(portion = portion, onPortionChange = { portion = it })
            }

            item {
                SectionHeader(
                    title = "Your meals",
                    trailing = if (uiState.mealPlans.isNotEmpty()) "${uiState.mealPlans.size} saved" else null
                )
            }
            if (uiState.mealPlans.isEmpty()) {
                item {
                    HangryCard {
                        DashEmptyState(
                            scene = DashEmptyScene.MEALS,
                            title = "Nothing saved yet",
                            body = "Every food you log is saved here automatically, ready to log again in one tap.",
                            modifier = Modifier.padding(vertical = HangryTokens.Spacing.s)
                        )
                    }
                }
            } else if (saved.isEmpty()) {
                item { NoMatch("None of your meals match \"${query.trim()}\".") }
            } else {
                items(saved, key = { "saved_${it.id}" }) { meal ->
                    SavedMealRow(
                        meal = meal,
                        portion = portion,
                        onLog = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.logFromMealPlan(meal, portion)
                        },
                        onEdit = { editing = meal },
                        onDelete = {
                            viewModel.deleteSavedMeal(meal)
                            deleted = meal
                        }
                    )
                }
            }

            item {
                SectionHeader(title = "Common foods", trailing = "Typical values", modifier = Modifier.padding(top = HangryTokens.Spacing.s))
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(selected = category == null, onClick = { category = null }, label = { Text("All") })
                    }
                    items(CommonFoodCategory.entries) { c ->
                        FilterChip(
                            selected = category == c,
                            onClick = { category = if (category == c) null else c },
                            label = { Text(c.title) }
                        )
                    }
                }
            }
            if (common.isEmpty()) {
                item { NoMatch("No common foods match. Tap + to add your own.") }
            } else {
                items(common, key = { "common_${it.name}" }) { food ->
                    CommonFoodRow(food = food, portion = portion, onLog = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.logCommonFood(food, portion)
                    })
                }
            }
        }
    }

    if (showAddDialog || editing != null) {
        SavedMealDialog(
            initial = editing,
            initialName = if (editing == null) query.trim() else "",
            onDismiss = {
                showAddDialog = false
                editing = null
            },
            onSave = { meal ->
                viewModel.saveMeal(meal, replacing = editing)
                showAddDialog = false
                editing = null
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String, trailing: String?, modifier: Modifier = Modifier) {
    val tokens = LocalHangryTokens.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
        trailing?.let { Text(text = it, style = MaterialTheme.typography.labelSmall, color = tokens.textMuted) }
    }
}

@Composable
private fun NoMatch(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = LocalHangryTokens.current.textMuted,
        modifier = Modifier.padding(vertical = HangryTokens.Spacing.xs)
    )
}

private fun macroLine(calories: Int, protein: Double, carbs: Double, fat: Double, portion: Double = 1.0): String =
    "${(calories * portion).roundToInt()} kcal · " +
        "P${(protein * portion).roundToInt()} C${(carbs * portion).roundToInt()} F${(fat * portion).roundToInt()}"

@Composable
private fun PortionPicker(portion: Double, onPortionChange: (Double) -> Unit) {
    val tokens = LocalHangryTokens.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = "Portion", style = MaterialTheme.typography.labelLarge, color = tokens.textSecondary)
        PORTIONS.forEach { p ->
            FilterChip(
                selected = portion == p,
                onClick = { onPortionChange(p) },
                label = { Text("${portionLabel(p)}×") },
                modifier = Modifier.semantics { contentDescription = "${portionLabel(p)} portion" }
            )
        }
    }
}

@Composable
private fun SavedMealRow(meal: MealPlanEntity, portion: Double, onLog: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val tokens = LocalHangryTokens.current
    var menuOpen by remember { mutableStateOf(false) }
    HangryCard(
        modifier = Modifier.clickable(onClickLabel = "Log ${portionedName(meal.name, portion)}", onClick = onLog),
        contentPadding = HangryTokens.Spacing.s
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.AddCircleOutline,
                contentDescription = null,
                tint = tokens.chartColors.activeCalories,
                modifier = Modifier.padding(horizontal = HangryTokens.Spacing.xs)
            )
            Column(modifier = Modifier.weight(1f).padding(start = HangryTokens.Spacing.xs)) {
                Text(
                    text = meal.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = tokens.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val times = when (meal.useCount) {
                    0 -> ""
                    1 -> " · logged once"
                    else -> " · logged ${meal.useCount}×"
                }
                Text(
                    text = portionPrefix(portion) + macroLine(meal.calories, meal.proteinG, meal.carbsG, meal.fatG, portion) + times,
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.textMuted
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options for ${meal.name}", tint = tokens.textMuted)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onEdit()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Remove") },
                        leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CommonFoodRow(food: CommonFood, portion: Double, onLog: () -> Unit) {
    val tokens = LocalHangryTokens.current
    HangryCard(
        modifier = Modifier.clickable(onClickLabel = "Log ${portionedName(food.name, portion)}", onClick = onLog),
        contentPadding = HangryTokens.Spacing.s
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.AddCircleOutline,
                contentDescription = null,
                tint = tokens.chartColors.activeCalories,
                modifier = Modifier.padding(horizontal = HangryTokens.Spacing.xs)
            )
            Column(modifier = Modifier.weight(1f).padding(start = HangryTokens.Spacing.xs)) {
                Text(text = food.name, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                Text(
                    text = "${portionPrefix(portion)}${food.serving} · ${macroLine(food.calories, food.proteinG, food.carbsG, food.fatG, portion)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.textMuted
                )
            }
        }
    }
}

private fun portionPrefix(portion: Double): String = if (portion == 1.0) "" else "${portionLabel(portion)}× "

/** Add a saved meal by hand ([initial] null), or edit one. */
@Composable
private fun SavedMealDialog(
    initial: MealPlanEntity?,
    initialName: String,
    onDismiss: () -> Unit,
    onSave: (MealPlanEntity) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: initialName) }
    var calories by remember { mutableStateOf(initial?.calories?.toString() ?: "") }
    var protein by remember { mutableStateOf(initial?.proteinG?.toInt()?.toString() ?: "") }
    var carbs by remember { mutableStateOf(initial?.carbsG?.toInt()?.toString() ?: "") }
    var fat by remember { mutableStateOf(initial?.fatG?.toInt()?.toString() ?: "") }
    val number = KeyboardOptions(keyboardType = KeyboardType.Number)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add a meal" else "Edit meal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = calories,
                    onValueChange = { if (it.all(Char::isDigit)) calories = it },
                    label = { Text("Calories") },
                    keyboardOptions = number,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = protein, onValueChange = { if (it.all(Char::isDigit)) protein = it }, label = { Text("Protein g") }, keyboardOptions = number, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = carbs, onValueChange = { if (it.all(Char::isDigit)) carbs = it }, label = { Text("Carbs g") }, keyboardOptions = number, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = fat, onValueChange = { if (it.all(Char::isDigit)) fat = it }, label = { Text("Fat g") }, keyboardOptions = number, singleLine = true, modifier = Modifier.weight(1f))
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && calories.toIntOrNull() != null,
                onClick = {
                    val cal = calories.toIntOrNull() ?: 0
                    val p = protein.toDoubleOrNull() ?: 0.0
                    val c = carbs.toDoubleOrNull() ?: 0.0
                    val f = fat.toDoubleOrNull() ?: 0.0
                    onSave(
                        initial?.copy(name = name.trim(), calories = cal, proteinG = p, carbsG = c, fatG = f)
                            ?: MealPlanEntity(name = name.trim(), calories = cal, proteinG = p, carbsG = c, fatG = f)
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
