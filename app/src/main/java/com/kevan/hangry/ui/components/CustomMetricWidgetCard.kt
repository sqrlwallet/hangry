package com.kevan.hangry.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kevan.hangry.data.local.entity.DailyHealthSummaryEntity
import com.kevan.hangry.domain.model.DashboardWidget
import com.kevan.hangry.domain.model.MetricType
import com.kevan.hangry.domain.model.StressLevel
import com.kevan.hangry.domain.model.StressResult
import com.kevan.hangry.domain.model.WidgetDisplayStyle
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun CustomMetricWidgetCard(
    widget: DashboardWidget,
    dailySummary: DailyHealthSummaryEntity?,
    stressResult: StressResult?,
    latestWeightKg: Double?,
    onRemoveWidget: () -> Unit,
    modifier: Modifier = Modifier,
    activeCalories: Double = dailySummary?.activeCalories ?: 0.0
) {
    val tokens = LocalHangryTokens.current

    val (currentValue: Double?, displayUnit: String, color: Color) = when (widget.metricType) {
        MetricType.STEPS -> Triple(
            dailySummary?.steps?.toDouble(),
            widget.unit ?: "steps",
            tokens.chartColors.steps
        )
        MetricType.ACTIVE_CALORIES -> Triple(
            if (dailySummary != null || activeCalories > 0) activeCalories else null,
            widget.unit ?: "kcal",
            tokens.chartColors.trainingLoad
        )
        MetricType.RHR -> Triple(
            dailySummary?.restingHeartRate,
            widget.unit ?: "bpm",
            tokens.chartColors.restingHeartRate
        )
        MetricType.HRV -> Triple(
            dailySummary?.hrvRmssd,
            widget.unit ?: "ms",
            tokens.chartColors.hrv
        )
        MetricType.VO2_MAX -> Triple(
            dailySummary?.vo2Max,
            widget.unit ?: "mL/kg/min",
            tokens.scoreColors.primed
        )
        MetricType.SPO2 -> Triple(
            dailySummary?.spo2Percentage,
            widget.unit ?: "%",
            tokens.scoreColors.primed
        )
        MetricType.SLEEP_DURATION -> Triple(
            dailySummary?.sleepDurationMinutes?.toDouble(),
            widget.unit ?: "min",
            tokens.chartColors.sleep
        )
        MetricType.DAY_STRAIN -> Triple(
            dailySummary?.dayStrain,
            widget.unit ?: "",
            tokens.chartColors.trainingLoad
        )
        MetricType.STRESS -> {
            val stressColor = when (stressResult?.level) {
                StressLevel.LOW -> tokens.scoreColors.primed
                StressLevel.MODERATE -> tokens.scoreColors.balanced
                StressLevel.ELEVATED, StressLevel.HIGH -> tokens.scoreColors.rebuild
                else -> tokens.scoreColors.buildingBaseline
            }
            Triple(
                stressResult?.score?.toDouble(),
                widget.unit ?: "%",
                stressColor
            )
        }
        MetricType.WEIGHT -> Triple(
            latestWeightKg,
            widget.unit ?: "kg",
            tokens.chartColors.restingHeartRate
        )
        null -> Triple(null, "", tokens.textPrimary)
    }

    val goal = widget.targetGoal
    val progress = if (currentValue != null && goal != null && goal > 0) {
        (currentValue / goal).toFloat().coerceIn(0f, 1f)
    } else 0f
    val truePercentage = if (currentValue != null && goal != null && goal > 0) {
        ((currentValue / goal) * 100).roundToInt()
    } else null

    val formattedVal = when {
        currentValue == null -> "—"
        widget.metricType == MetricType.STEPS -> String.format(Locale.US, "%,d", currentValue.toLong())
        widget.metricType == MetricType.SLEEP_DURATION && (widget.unit == null || widget.unit == "min") -> {
            val totalMins = currentValue.roundToInt()
            "${totalMins / 60}h ${totalMins % 60}m"
        }
        currentValue % 1.0 == 0.0 -> "${currentValue.toLong()}"
        else -> String.format(Locale.US, "%.1f", currentValue)
    }

    val displayUnitWithSpace = if (displayUnit.isNotBlank() && !displayUnit.startsWith("%")) " $displayUnit" else displayUnit

    val targetSubtitle = when {
        currentValue == null && goal != null -> "Target: ${goal.toInt()}$displayUnitWithSpace (No data today)"
        currentValue == null -> "No data recorded today"
        goal != null && truePercentage != null -> "Target: ${goal.toInt()}$displayUnitWithSpace ($truePercentage%)"
        else -> null
    }

    HangryCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Insights,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(HangryTokens.Spacing.xs))
                    Text(
                        text = widget.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = tokens.textPrimary
                    )
                }

                IconButton(
                    onClick = onRemoveWidget,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove Widget",
                        tint = tokens.textMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))

            when (widget.displayStyle) {
                WidgetDisplayStyle.RING -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            val valueText = if (currentValue != null) "$formattedVal$displayUnitWithSpace" else formattedVal
                            Text(
                                text = valueText,
                                style = MaterialTheme.typography.headlineMedium,
                                color = color
                            )
                            if (targetSubtitle != null) {
                                Text(
                                    text = targetSubtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = tokens.textMuted
                                )
                            }
                        }

                        HangryRingGauge(
                            progress = progress,
                            color = color,
                            modifier = Modifier.size(54.dp),
                            strokeWidth = 6.dp
                        ) {
                            Text(
                                text = if (truePercentage != null) "$truePercentage%" else if (currentValue != null) "—" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = color
                            )
                        }
                    }
                }

                WidgetDisplayStyle.STAT_CARD -> {
                    Column {
                        val valueText = if (currentValue != null) "$formattedVal$displayUnitWithSpace" else formattedVal
                        Text(
                            text = valueText,
                            style = MaterialTheme.typography.displaySmall,
                            color = color
                        )
                        if (targetSubtitle != null) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = targetSubtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = tokens.textMuted
                            )
                        }
                    }
                }

                WidgetDisplayStyle.PROGRESS_BAR -> {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val valueText = if (currentValue != null) "$formattedVal$displayUnitWithSpace" else formattedVal
                            Text(
                                text = valueText,
                                style = MaterialTheme.typography.titleLarge,
                                color = color
                            )
                            if (truePercentage != null) {
                                Text(
                                    text = "$truePercentage%",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = color
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(HangryTokens.Spacing.xs))

                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp),
                            color = color,
                            trackColor = color.copy(alpha = 0.2f),
                        )

                        if (targetSubtitle != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = targetSubtitle,
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
