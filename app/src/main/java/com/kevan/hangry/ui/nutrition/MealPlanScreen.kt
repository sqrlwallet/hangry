package com.kevan.hangry.ui.nutrition

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kevan.hangry.R
import com.kevan.hangry.data.local.entity.MealPlanEntity
import com.kevan.hangry.domain.repository.MealPlanRepository
import com.kevan.hangry.ui.components.HangryCard
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealPlanScreen(
    mealPlanRepository: MealPlanRepository,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalHangryTokens.current
    val coroutineScope = rememberCoroutineScope()
    val plans by mealPlanRepository.getAll().collectAsState(initial = emptyList())
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_meal_plan)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Add Meal")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (plans.isEmpty()) {
            Column(
                modifier = modifier.fillMaxSize().padding(innerPadding).padding(HangryTokens.Spacing.m)
            ) {
                HangryCard {
                    Text(
                        text = "No saved meals yet. Add one to quick-log it without the AI each time.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.textSecondary
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = HangryTokens.Spacing.m, vertical = HangryTokens.Spacing.s),
                verticalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.m)
            ) {
                items(plans, key = { it.id }) { plan ->
                    HangryCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = plan.name, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                                Text(
                                    text = "${plan.calories} kcal · P${plan.proteinG.toInt()} C${plan.carbsG.toInt()} F${plan.fatG.toInt()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = tokens.textMuted
                                )
                            }
                            IconButton(onClick = { coroutineScope.launch { mealPlanRepository.delete(plan) } }) {
                                Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = "Delete", tint = tokens.textMuted)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddMealPlanDialog(
            onDismiss = { showAddDialog = false },
            onSave = { entry ->
                coroutineScope.launch { mealPlanRepository.upsert(entry) }
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun AddMealPlanDialog(onDismiss: () -> Unit, onSave: (MealPlanEntity) -> Unit) {
    var name by remember { mutableStateOf("") }
    var calories by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("0") }
    var carbs by remember { mutableStateOf("0") }
    var fat by remember { mutableStateOf("0") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Meal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
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
                enabled = name.isNotBlank() && calories.toIntOrNull() != null,
                onClick = {
                    onSave(
                        MealPlanEntity(
                            name = name,
                            calories = calories.toIntOrNull() ?: 0,
                            proteinG = protein.toDoubleOrNull() ?: 0.0,
                            carbsG = carbs.toDoubleOrNull() ?: 0.0,
                            fatG = fat.toDoubleOrNull() ?: 0.0
                        )
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
