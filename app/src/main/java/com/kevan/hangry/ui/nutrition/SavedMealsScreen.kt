package com.kevan.hangry.ui.nutrition

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.LocalContext
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
import com.kevan.hangry.ui.components.DashEmptyScene
import com.kevan.hangry.ui.components.DashEmptyState
import com.kevan.hangry.ui.components.HangryCard
import com.kevan.hangry.ui.components.dayLabel
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens
import kotlin.math.roundToInt

/**
 * Every food the user has logged before, one tap from being logged again.
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
    val context = LocalContext.current

    var query by remember { mutableStateOf("") }
    // Sticks between taps so e.g. a double helping of several foods is quick; every row shows
    // the numbers for the chosen portion, so it's never a surprise.
    var portion by rememberSaveable { mutableDoubleStateOf(1.0) }
    var editing by remember { mutableStateOf<MealPlanEntity?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var deleted by remember { mutableStateOf<MealPlanEntity?>(null) }

    val q = query.trim().lowercase()
    val saved = uiState.mealPlans.filter { q.isEmpty() || it.name.lowercase().contains(q) }

    LaunchedEffect(uiState.lastSavedEntry) {
        val entry = uiState.lastSavedEntry ?: return@LaunchedEffect
        viewModel.clearLastSaved()
        val result = snackbarHostState.showSnackbar(
            message = context.getString(R.string.nutrition_saved_logged_snackbar, entry.foodName, entry.calories),
            actionLabel = context.getString(R.string.nutrition_undo),
            duration = SnackbarDuration.Short
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.deleteEntry(entry)
    }

    LaunchedEffect(deleted) {
        val meal = deleted ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = context.getString(R.string.nutrition_saved_removed_snackbar, meal.name),
            actionLabel = context.getString(R.string.nutrition_undo),
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
                            text = stringResource(R.string.nutrition_saved_tap_to_log, dayLabel(uiState.selectedDate)),
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.textMuted
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nutrition_cd_back))
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = stringResource(R.string.nutrition_saved_add_meal))
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
                    placeholder = { Text(stringResource(R.string.nutrition_saved_search)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.nutrition_saved_clear_search))
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
                    title = stringResource(R.string.nutrition_saved_your_meals),
                    trailing = if (uiState.mealPlans.isNotEmpty()) stringResource(R.string.nutrition_saved_count, uiState.mealPlans.size) else null
                )
            }
            if (uiState.mealPlans.isEmpty()) {
                item {
                    HangryCard {
                        DashEmptyState(
                            scene = DashEmptyScene.MEALS,
                            title = stringResource(R.string.nutrition_saved_empty_title),
                            body = stringResource(R.string.nutrition_saved_empty_body),
                            modifier = Modifier.padding(vertical = HangryTokens.Spacing.s)
                        )
                    }
                }
            } else if (saved.isEmpty()) {
                item { NoMatch(stringResource(R.string.nutrition_saved_no_match, query.trim())) }
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

@Composable
private fun macroLine(calories: Int, protein: Double, carbs: Double, fat: Double, portion: Double = 1.0): String =
    stringResource(
        R.string.nutrition_entry_macros,
        (calories * portion).roundToInt(),
        (protein * portion).roundToInt(),
        (carbs * portion).roundToInt(),
        (fat * portion).roundToInt()
    )

@Composable
private fun PortionPicker(portion: Double, onPortionChange: (Double) -> Unit) {
    val tokens = LocalHangryTokens.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = stringResource(R.string.nutrition_saved_portion), style = MaterialTheme.typography.labelLarge, color = tokens.textSecondary)
        PORTIONS.forEach { p ->
            val portionDescription = stringResource(R.string.nutrition_saved_portion_cd, portionLabel(p))
            FilterChip(
                selected = portion == p,
                onClick = { onPortionChange(p) },
                label = { Text(stringResource(R.string.nutrition_portion_times, portionLabel(p))) },
                modifier = Modifier.semantics { contentDescription = portionDescription }
            )
        }
    }
}

@Composable
private fun SavedMealRow(meal: MealPlanEntity, portion: Double, onLog: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val tokens = LocalHangryTokens.current
    var menuOpen by remember { mutableStateOf(false) }
    HangryCard(
        modifier = Modifier.clickable(onClickLabel = stringResource(R.string.nutrition_log_item_label, portionedName(meal.name, portion)), onClick = onLog),
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
                    1 -> stringResource(R.string.nutrition_saved_logged_once)
                    else -> stringResource(R.string.nutrition_saved_logged_times, meal.useCount)
                }
                Text(
                    text = portionPrefix(portion) + macroLine(meal.calories, meal.proteinG, meal.carbsG, meal.fatG, portion) + times,
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.textMuted
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.nutrition_saved_options_for, meal.name), tint = tokens.textMuted)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.nutrition_edit)) },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onEdit()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.nutrition_remove)) },
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
private fun portionPrefix(portion: Double): String =
    if (portion == 1.0) "" else stringResource(R.string.nutrition_portion_prefix, portionLabel(portion))

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
        title = { Text(if (initial == null) stringResource(R.string.nutrition_saved_add_meal) else stringResource(R.string.nutrition_saved_edit_meal)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.nutrition_field_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = calories,
                    onValueChange = { if (it.all(Char::isDigit)) calories = it },
                    label = { Text(stringResource(R.string.nutrition_field_calories)) },
                    keyboardOptions = number,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = protein, onValueChange = { if (it.all(Char::isDigit)) protein = it }, label = { Text(stringResource(R.string.nutrition_field_protein_g)) }, keyboardOptions = number, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = carbs, onValueChange = { if (it.all(Char::isDigit)) carbs = it }, label = { Text(stringResource(R.string.nutrition_field_carbs_g)) }, keyboardOptions = number, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = fat, onValueChange = { if (it.all(Char::isDigit)) fat = it }, label = { Text(stringResource(R.string.nutrition_field_fat_g)) }, keyboardOptions = number, singleLine = true, modifier = Modifier.weight(1f))
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
            ) { Text(stringResource(R.string.nutrition_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
