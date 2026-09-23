package com.kevan.hangry.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kevan.hangry.data.local.entity.DailyHealthSummaryEntity
import com.kevan.hangry.data.local.entity.ExerciseSessionEntity
import com.kevan.hangry.data.local.entity.FoodLogEntity
import com.kevan.hangry.data.local.entity.RecoveryScoreEntity
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Full Day Inspection Sheet.
 *
 * Provides a comprehensive deep dive into any historical day's biometric telemetry,
 * workouts, sleep architecture, and nutrition logs, with 1-tap navigation to the Dashboard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDetailSheet(
    date: LocalDate,
    summary: DailyHealthSummaryEntity?,
    recoveryScore: RecoveryScoreEntity?,
    workouts: List<ExerciseSessionEntity> = emptyList(),
    foodLogs: List<FoodLogEntity> = emptyList(),
    onDismiss: () -> Unit,
    onNavigateToDashboard: (LocalDate) -> Unit,
    onNavigateToNutrition: ((LocalDate) -> Unit)? = null
) {
    val tokens = LocalHangryTokens.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = tokens.cardBackground,
        tonalElevation = 12.dp,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = HangryTokens.Spacing.m)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.m)
        ) {
            // Header: Date & Primary CTA
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = date.format(DateTimeFormatter.ofPattern("EEEE")),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = tokens.textPrimary
                    )
                    Text(
                        text = date.format(DateTimeFormatter.ofPattern("MMMM d, yyyy")),
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.textSecondary
                    )
                }

                Button(
                    onClick = {
                        onDismiss()
                        onNavigateToDashboard(date)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Dashboard,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Open on Dashboard",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            // 1. Recovery Telemetry Card
            val scoreVal = recoveryScore?.score
            val recoveryColor = when {
                scoreVal == null -> tokens.textMuted
                scoreVal >= 67 -> tokens.scoreColors.primed
                scoreVal >= 34 -> tokens.scoreColors.balanced
                else -> tokens.scoreColors.rebuild
            }

            Surface(
                color = tokens.cardBorder.copy(alpha = 0.4f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recovery Score",
                            style = MaterialTheme.typography.titleMedium,
                            color = tokens.textPrimary
                        )
                        Surface(
                            color = recoveryColor.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = recoveryScore?.state ?: "BASELINE CALIBRATING",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = recoveryColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = if (scoreVal != null) "$scoreVal%" else "—",
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold,
                            color = recoveryColor
                        )

                        Text(
                            text = "Confidence: ${recoveryScore?.confidence ?: "CALIBRATING"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.textMuted
                        )
                    }

                    if (!recoveryScore?.supportiveAdvice.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = recoveryScore!!.supportiveAdvice,
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.textSecondary
                        )
                    }
                }
            }

            // 2. Activity, Strain & Calories Grid
            Text(
                text = "Activity & Strain",
                style = MaterialTheme.typography.titleSmall,
                color = tokens.textSecondary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricMiniCard(
                    title = "Day Strain",
                    value = summary?.dayStrain?.let { String.format(Locale.US, "%.1f", it) } ?: "—",
                    color = tokens.chartColors.trainingLoad,
                    modifier = Modifier.weight(1f)
                )
                MetricMiniCard(
                    title = "Steps",
                    value = summary?.steps?.let { String.format(Locale.US, "%,d", it) } ?: "0",
                    color = tokens.chartColors.steps,
                    modifier = Modifier.weight(1f)
                )
                MetricMiniCard(
                    title = "Active Burn",
                    value = summary?.activeCalories?.let { "${it.toInt()} kcal" } ?: "—",
                    color = tokens.chartColors.activeCalories,
                    modifier = Modifier.weight(1f)
                )
            }

            // 3. Sleep Breakdown Card
            Surface(
                color = tokens.cardBorder.copy(alpha = 0.4f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Bedtime,
                                contentDescription = null,
                                tint = tokens.chartColors.sleep,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Sleep Duration",
                                style = MaterialTheme.typography.titleMedium,
                                color = tokens.textPrimary
                            )
                        }

                        val sleepMins = summary?.sleepDurationMinutes
                        Text(
                            text = if (sleepMins != null) "${sleepMins / 60}h ${sleepMins % 60}m" else "No record",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = tokens.chartColors.sleep
                        )
                    }

                    if (summary?.sleepConsistencyScore != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Sleep Consistency: ${(summary.sleepConsistencyScore * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.textSecondary
                        )
                    }
                }
            }

            // 4. Cardiovascular & Autonomic Vitals Card
            Surface(
                color = tokens.cardBorder.copy(alpha = 0.4f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Cardiovascular & Vitals",
                        style = MaterialTheme.typography.titleMedium,
                        color = tokens.textPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        VitalColumn(
                            label = "Resting HR",
                            value = summary?.restingHeartRate?.let { "${it.toInt()} bpm" } ?: "—",
                            color = tokens.chartColors.restingHeartRate
                        )
                        VitalColumn(
                            label = "HRV RMSSD",
                            value = summary?.hrvRmssd?.let { "${it.toInt()} ms" } ?: "—",
                            color = tokens.chartColors.hrv
                        )
                        VitalColumn(
                            label = "Avg Heart Rate",
                            value = summary?.averageHeartRate?.let { "${it.toInt()} bpm" } ?: "—",
                            color = tokens.textSecondary
                        )
                    }

                    val hasExtraVitals = summary?.spo2Percentage != null ||
                        summary?.bloodPressureSystolic != null ||
                        summary?.respiratoryRate != null ||
                        summary?.hydrationLiters != null ||
                        summary?.bodyFatPercentage != null

                    if (hasExtraVitals) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            if (summary?.spo2Percentage != null) {
                                VitalColumn(
                                    label = "Blood Oxygen (SpO2)",
                                    value = "${summary.spo2Percentage.toInt()}%",
                                    color = tokens.textSecondary
                                )
                            }
                            if (summary?.bloodPressureSystolic != null && summary.bloodPressureDiastolic != null) {
                                VitalColumn(
                                    label = "Blood Pressure",
                                    value = "${summary.bloodPressureSystolic.toInt()}/${summary.bloodPressureDiastolic.toInt()} mmHg",
                                    color = tokens.textSecondary
                                )
                            }
                            if (summary?.respiratoryRate != null) {
                                VitalColumn(
                                    label = "Respiratory Rate",
                                    value = "${summary.respiratoryRate.toInt()} rpm",
                                    color = tokens.textSecondary
                                )
                            }
                        }

                        if (summary?.hydrationLiters != null || summary?.bodyFatPercentage != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                if (summary.hydrationLiters != null) {
                                    VitalColumn(
                                        label = "Hydration Intake",
                                        value = "%.1f L".format(summary.hydrationLiters),
                                        color = tokens.chartColors.sleep
                                    )
                                }
                                if (summary.bodyFatPercentage != null) {
                                    VitalColumn(
                                        label = "Body Fat",
                                        value = "%.1f%%".format(summary.bodyFatPercentage),
                                        color = tokens.textSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 5. Workouts logged on this day
            if (workouts.isNotEmpty()) {
                Surface(
                    color = tokens.cardBorder.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Workouts (${workouts.size})",
                                style = MaterialTheme.typography.titleMedium,
                                color = tokens.textPrimary
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.DirectionsRun,
                                contentDescription = null,
                                tint = tokens.chartColors.trainingLoad,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        workouts.forEach { session ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = session.title ?: session.exerciseType,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = tokens.textPrimary
                                    )
                                    Text(
                                        text = "${session.durationMinutes} minutes",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = tokens.textMuted
                                    )
                                }
                                Text(
                                    text = session.activeCalories?.let { "${it.toInt()} kcal" } ?: "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = tokens.chartColors.activeCalories
                                )
                            }
                        }
                    }
                }
            }

            // 6. Nutrition logged on this day
            if (foodLogs.isNotEmpty()) {
                val totalCal = foodLogs.sumOf { it.calories }
                val totalProtein = foodLogs.sumOf { it.proteinG }
                val totalCarbs = foodLogs.sumOf { it.carbsG }
                val totalFat = foodLogs.sumOf { it.fatG }

                Surface(
                    color = tokens.cardBorder.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Nutrition ($totalCal kcal)",
                                style = MaterialTheme.typography.titleMedium,
                                color = tokens.textPrimary
                            )
                            if (onNavigateToNutrition != null) {
                                TextButton(
                                    onClick = {
                                        onDismiss()
                                        onNavigateToNutrition(date)
                                    },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("Open in Nutrition", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                                }
                            }
                        }

                        Text(
                            text = "Macros: ${totalProtein.toInt()}g P · ${totalCarbs.toInt()}g C · ${totalFat.toInt()}g F",
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.textSecondary
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        foodLogs.forEach { meal ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = meal.foodName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = tokens.textPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "${meal.calories} kcal",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = tokens.textMuted
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricMiniCard(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val tokens = LocalHangryTokens.current
    Surface(
        color = tokens.cardBorder.copy(alpha = 0.4f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = tokens.textMuted
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
private fun VitalColumn(
    label: String,
    value: String,
    color: Color
) {
    val tokens = LocalHangryTokens.current
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tokens.textMuted
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}
