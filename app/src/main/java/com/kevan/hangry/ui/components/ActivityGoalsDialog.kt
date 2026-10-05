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
import androidx.compose.material.icons.filled.Timer
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens
import java.util.Locale
import kotlin.math.roundToInt
import androidx.compose.ui.res.stringResource
import com.kevan.hangry.R

@Composable
fun EditActivityGoalsDialog(
    initialStepGoal: Long,
    initialCaloriesGoal: Int,
    initialActiveMinutesGoal: Int,
    onDismiss: () -> Unit,
    onSave: (stepGoal: Long, caloriesGoal: Int, activeMinutesGoal: Int) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var stepText by remember { mutableStateOf(initialStepGoal.toString()) }
    var caloriesText by remember { mutableStateOf(initialCaloriesGoal.toString()) }
    var minutesText by remember { mutableStateOf(initialActiveMinutesGoal.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.metrics_components_set_activity_goals)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.metrics_components_goals_reset_midnight),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalHangryTokens.current.textSecondary
                )

                OutlinedTextField(
                    value = stepText,
                    onValueChange = { stepText = it.filter { ch -> ch.isDigit() } },
                    label = { Text(stringResource(R.string.metrics_components_daily_steps)) },
                    placeholder = { Text(stringResource(R.string.metrics_components_default_6000)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = caloriesText,
                    onValueChange = { caloriesText = it.filter { ch -> ch.isDigit() } },
                    label = { Text(stringResource(R.string.metrics_components_active_calories_kcal)) },
                    placeholder = { Text(stringResource(R.string.metrics_components_default_500)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = minutesText,
                    onValueChange = { minutesText = it.filter { ch -> ch.isDigit() } },
                    label = { Text(stringResource(R.string.metrics_components_active_time_min)) },
                    placeholder = { Text(stringResource(R.string.metrics_components_default_90)) },
                    supportingText = { Text(stringResource(R.string.metrics_components_active_time_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val s = (stepText.toLongOrNull() ?: 6000L).coerceAtLeast(100L)
                    val c = (caloriesText.toIntOrNull() ?: 500).coerceAtLeast(50)
                    val m = (minutesText.toIntOrNull() ?: 90).coerceIn(5, 600)
                    onSave(s, c, m)
                }
            ) {
                Text(stringResource(R.string.metrics_components_save_goals))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
