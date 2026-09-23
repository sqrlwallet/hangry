package com.kevan.hangry.ui.nutrition

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.kevan.hangry.data.local.entity.MealPlanEntity
import com.kevan.hangry.domain.model.FoodAnalysisResult
import com.kevan.hangry.ui.coach.DashSpinner
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickMealLogSheet(
    initialPhotoUri: Uri? = null,
    /** Shown above the form, e.g. why a photo couldn't be analyzed automatically. */
    notice: String? = null,
    aiEnabled: Boolean = false,
    mealPlans: List<MealPlanEntity> = emptyList(),
    onDismiss: () -> Unit,
    onLogMeal: (name: String, calories: Int, photoUri: Uri?, protein: Double, carbs: Double, fat: Double) -> Unit,
    onEstimateWithAi: (suspend (Uri?, String) -> Result<FoodAnalysisResult>)? = null,
    onLogMealPlan: ((MealPlanEntity) -> Unit)? = null
) {
    val tokens = LocalHangryTokens.current
    val coroutineScope = rememberCoroutineScope()

    var photoUri by remember { mutableStateOf(initialPhotoUri) }
    var foodName by remember { mutableStateOf("") }
    var caloriesText by remember { mutableStateOf("") }
    var proteinText by remember { mutableStateOf("") }
    var carbsText by remember { mutableStateOf("") }
    var fatText by remember { mutableStateOf("") }
    var showMacros by remember { mutableStateOf(false) }

    var isEstimating by remember { mutableStateOf(false) }
    var estimateError by remember { mutableStateOf<String?>(null) }
    var allergenWarnings by remember { mutableStateOf<List<String>>(emptyList()) }

    val quickMealTypes = listOf("Breakfast", "Lunch", "Dinner", "Snack")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = HangryTokens.Spacing.m)
                .padding(bottom = HangryTokens.Spacing.xl)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.m)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Meal details",
                    style = MaterialTheme.typography.titleLarge,
                    color = tokens.textPrimary
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = tokens.textMuted)
                }
            }

            notice?.let { text ->
                Surface(
                    color = tokens.brandAccentContainer,
                    shape = RoundedCornerShape(HangryTokens.CornerRadii.small),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = tokens.brandAccent, modifier = Modifier.size(18.dp))
                        Text(text = text, style = MaterialTheme.typography.bodySmall, color = tokens.textPrimary)
                    }
                }
            }

            // Photo Preview if available
            photoUri?.let { uri ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(HangryTokens.CornerRadii.medium))
                        .background(tokens.cardBackground)
                ) {
                    AsyncImage(
                        model = uri,
                        contentDescription = "Captured Meal",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    IconButton(
                        onClick = { photoUri = null },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(32.dp)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove Photo",
                            tint = tokens.textPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Quick Meal Plan Chips
            if (mealPlans.isNotEmpty() && onLogMealPlan != null) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "From your Meal Plan",
                        style = MaterialTheme.typography.labelMedium,
                        color = tokens.textMuted
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        mealPlans.forEach { plan ->
                            SuggestionChip(
                                onClick = {
                                    onLogMealPlan(plan)
                                    onDismiss()
                                },
                                label = { Text("${plan.name} (${plan.calories} kcal)") },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = tokens.cardBackground,
                                    labelColor = tokens.textPrimary
                                )
                            )
                        }
                    }
                }
            }

            // Quick Category Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                quickMealTypes.forEach { type ->
                    FilterChip(
                        selected = foodName.startsWith(type),
                        onClick = {
                            foodName = if (foodName.isBlank() || quickMealTypes.any { foodName == it }) {
                                type
                            } else {
                                "$type: $foodName"
                            }
                        },
                        label = { Text(type) }
                    )
                }
            }

            // Food Name Field
            OutlinedTextField(
                value = foodName,
                onValueChange = { foodName = it },
                label = { Text("Meal / Food Name") },
                placeholder = { Text("e.g. Oatmeal with blueberries") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Calories Field with quick add buttons
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = caloriesText,
                    onValueChange = { input ->
                        if (input.all { it.isDigit() }) caloriesText = input
                    },
                    label = { Text("Calories (kcal)") },
                    placeholder = { Text("500") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(100, 250, 500).forEach { bump ->
                        AssistChip(
                            onClick = {
                                val current = caloriesText.toIntOrNull() ?: 0
                                caloriesText = (current + bump).toString()
                            },
                            label = { Text("+$bump kcal") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // AI Estimation Button (if AI enabled and estimator available)
            if (aiEnabled && onEstimateWithAi != null && (photoUri != null || foodName.isNotBlank())) {
                OutlinedButton(
                    onClick = {
                        coroutineScope.launch {
                            isEstimating = true
                            estimateError = null
                            val res = onEstimateWithAi(photoUri, foodName)
                            res.fold(
                                onSuccess = { result ->
                                    if (foodName.isBlank() || quickMealTypes.contains(foodName)) {
                                        foodName = result.foodName
                                    }
                                    caloriesText = result.calories.toString()
                                    proteinText = result.proteinG.toInt().toString()
                                    carbsText = result.carbsG.toInt().toString()
                                    fatText = result.fatG.toInt().toString()
                                    allergenWarnings = result.allergenWarnings
                                    showMacros = true
                                },
                                onFailure = { err ->
                                    estimateError = err.message ?: "Failed to estimate food."
                                }
                            )
                            isEstimating = false
                        }
                    },
                    enabled = !isEstimating,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isEstimating) {
                        DashSpinner(size = 26.dp, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Estimating with AI...")
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Auto-Estimate with AI")
                    }
                }

                if (allergenWarnings.isNotEmpty()) {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(HangryTokens.CornerRadii.small), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Possible allergen", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onErrorContainer)
                            allergenWarnings.forEach {
                                Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                }

                estimateError?.let { err ->
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.scoreColors.rebuild
                    )
                }
            }

            // Macro details expander
            TextButton(
                onClick = { showMacros = !showMacros },
                modifier = Modifier.align(Alignment.Start)
            ) {
                Icon(
                    imageVector = if (showMacros) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (showMacros) "Hide optional macros" else "Add macros (optional)")
            }

            AnimatedVisibility(visible = showMacros) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = proteinText,
                        onValueChange = { if (it.all { c -> c.isDigit() }) proteinText = it },
                        label = { Text("Protein (g)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = carbsText,
                        onValueChange = { if (it.all { c -> c.isDigit() }) carbsText = it },
                        label = { Text("Carbs (g)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = fatText,
                        onValueChange = { if (it.all { c -> c.isDigit() }) fatText = it },
                        label = { Text("Fat (g)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))

            // Primary Log Button
            Button(
                onClick = {
                    val finalName = foodName.ifBlank { "Meal" }
                    val finalCalories = caloriesText.toIntOrNull() ?: 0
                    val protein = proteinText.toDoubleOrNull() ?: 0.0
                    val carbs = carbsText.toDoubleOrNull() ?: 0.0
                    val fat = fatText.toDoubleOrNull() ?: 0.0

                    onLogMeal(finalName, finalCalories, photoUri, protein, carbs, fat)
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(HangryTokens.CornerRadii.medium),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Log Meal",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
