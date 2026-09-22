package com.kevan.hangry.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun DailyActivityRingsCard(
    currentSteps: Long,
    stepGoal: Long,
    currentCalories: Double,
    caloriesGoal: Int,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onSaveGoals: (stepGoal: Long, caloriesGoal: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalHangryTokens.current
    var showGoalDialog by remember { mutableStateOf(false) }

    val calorieColor = tokens.chartColors.trainingLoad
    val stepsColor = tokens.chartColors.steps

    val safeCalories = currentCalories.coerceAtLeast(0.0)
    val safeSteps = currentSteps.coerceAtLeast(0L)

    val calorieProgress = (safeCalories / caloriesGoal.coerceAtLeast(1)).toFloat().coerceIn(0f, 1f)
    val stepsProgress = (safeSteps.toFloat() / stepGoal.coerceAtLeast(1)).coerceIn(0f, 1f)

    val caloriePercentage = ((safeCalories / caloriesGoal.coerceAtLeast(1)) * 100).roundToInt()
    val stepsPercentage = ((safeSteps.toDouble() / stepGoal.coerceAtLeast(1)) * 100).roundToInt()

    HangryCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onToggleExpand() }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Daily Activity",
                        style = MaterialTheme.typography.titleMedium,
                        color = tokens.textPrimary
                    )
                    Text(
                        text = if (isExpanded) "Tap to hide details" else "Tap rings to reveal numbers",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.textMuted
                    )
                }

                IconButton(
                    onClick = { showGoalDialog = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Activity Goals",
                        tint = tokens.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(HangryTokens.Spacing.m))

            // Concentric 2-Ring Visual (Calories Burned & Steps)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                contentAlignment = Alignment.Center
            ) {
                ConcentricActivityRings(
                    outerProgress = calorieProgress,
                    outerColor = calorieColor,
                    innerProgress = stepsProgress,
                    innerColor = stepsColor,
                    modifier = Modifier.size(150.dp)
                )
            }

            // Quick legend under the rings (minimalist by default)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = HangryTokens.Spacing.s),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                RingLegendItem(color = calorieColor, label = "Calories Burned", icon = Icons.Default.LocalFireDepartment)
                RingLegendItem(color = stepsColor, label = "Steps", icon = Icons.AutoMirrored.Filled.DirectionsRun)
            }

            // Detailed numbers & percentages (Click-to-reveal)
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = HangryTokens.Spacing.m),
                    verticalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.s)
                ) {
                    HorizontalDivider(color = tokens.cardBorder)

                    ActivityMetricRow(
                        icon = Icons.AutoMirrored.Filled.DirectionsRun,
                        label = "Steps",
                        current = String.format(Locale.US, "%,d", safeSteps),
                        goal = String.format(Locale.US, "%,d", stepGoal),
                        unit = "steps",
                        percentage = stepsPercentage,
                        color = stepsColor
                    )

                    ActivityMetricRow(
                        icon = Icons.Default.LocalFireDepartment,
                        label = "Active Calories",
                        current = String.format(Locale.US, "%,d", safeCalories.roundToInt()),
                        goal = String.format(Locale.US, "%,d", caloriesGoal),
                        unit = "kcal",
                        percentage = caloriePercentage,
                        color = calorieColor
                    )
                }
            }
        }
    }

    if (showGoalDialog) {
        EditActivityGoalsDialog(
            initialStepGoal = stepGoal,
            initialCaloriesGoal = caloriesGoal,
            onDismiss = { showGoalDialog = false },
            onSave = { s, c ->
                onSaveGoals(s, c)
                showGoalDialog = false
            }
        )
    }
}

@Composable
private fun ConcentricActivityRings(
    outerProgress: Float,
    outerColor: Color,
    innerProgress: Float,
    innerColor: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 12.dp,
    gap: Dp = 6.dp
) {
    val trackAlpha = 0.18f

    val animOuter by animateFloatAsState(targetValue = outerProgress, animationSpec = tween(700), label = "outer")
    val animInner by animateFloatAsState(targetValue = innerProgress, animationSpec = tween(700), label = "inner")

    Canvas(modifier = modifier) {
        val strokePx = strokeWidth.toPx()
        val gapPx = gap.toPx()
        val startAngle = -90f
        val fullSweep = 360f

        // Outer Ring (Calories Burned)
        val outerRadius = (size.minDimension - strokePx) / 2f
        val outerTopLeft = Offset(size.width / 2f - outerRadius, size.height / 2f - outerRadius)
        val outerSize = Size(outerRadius * 2, outerRadius * 2)

        drawArc(
            color = outerColor.copy(alpha = trackAlpha),
            startAngle = startAngle,
            sweepAngle = fullSweep,
            useCenter = false,
            topLeft = outerTopLeft,
            size = outerSize,
            style = Stroke(width = strokePx, cap = StrokeCap.Round)
        )
        if (animOuter > 0f) {
            drawArc(
                color = outerColor,
                startAngle = startAngle,
                sweepAngle = fullSweep * animOuter,
                useCenter = false,
                topLeft = outerTopLeft,
                size = outerSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
        }

        // Inner Ring (Steps)
        val innerRadius = outerRadius - strokePx - gapPx
        val innerTopLeft = Offset(size.width / 2f - innerRadius, size.height / 2f - innerRadius)
        val innerSize = Size(innerRadius * 2, innerRadius * 2)

        drawArc(
            color = innerColor.copy(alpha = trackAlpha),
            startAngle = startAngle,
            sweepAngle = fullSweep,
            useCenter = false,
            topLeft = innerTopLeft,
            size = innerSize,
            style = Stroke(width = strokePx, cap = StrokeCap.Round)
        )
        if (animInner > 0f) {
            drawArc(
                color = innerColor,
                startAngle = startAngle,
                sweepAngle = fullSweep * animInner,
                useCenter = false,
                topLeft = innerTopLeft,
                size = innerSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
private fun RingLegendItem(
    color: Color,
    label: String,
    icon: ImageVector
) {
    val tokens = LocalHangryTokens.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tokens.textSecondary
        )
    }
}

@Composable
private fun ActivityMetricRow(
    icon: ImageVector,
    label: String,
    current: String,
    goal: String,
    unit: String,
    percentage: Int,
    color: Color
) {
    val tokens = LocalHangryTokens.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(HangryTokens.Spacing.s))
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.textPrimary
                )
                Text(
                    text = "$current / $goal $unit",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.textMuted
                )
            }
        }

        Surface(
            color = color.copy(alpha = 0.15f),
            shape = MaterialTheme.shapes.small
        ) {
            Text(
                text = "$percentage%",
                style = MaterialTheme.typography.labelMedium,
                color = color,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
fun EditActivityGoalsDialog(
    initialStepGoal: Long,
    initialCaloriesGoal: Int,
    onDismiss: () -> Unit,
    onSave: (stepGoal: Long, caloriesGoal: Int) -> Unit
) {
    var stepText by remember { mutableStateOf(initialStepGoal.toString()) }
    var caloriesText by remember { mutableStateOf(initialCaloriesGoal.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Daily Activity Goals") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Personalize your daily targets. Progress resets every day at midnight.",
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalHangryTokens.current.textSecondary
                )

                OutlinedTextField(
                    value = stepText,
                    onValueChange = { stepText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Daily Steps") },
                    placeholder = { Text("Default: 6000") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = caloriesText,
                    onValueChange = { caloriesText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Active Calories (kcal)") },
                    placeholder = { Text("Default: 500") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val s = (stepText.toLongOrNull() ?: 6000L).coerceAtLeast(100L)
                    val c = (caloriesText.toIntOrNull() ?: 500).coerceAtLeast(50)
                    onSave(s, c)
                }
            ) {
                Text("Save Goals")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
