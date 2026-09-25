package com.kevan.hangry.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kevan.hangry.R
import com.kevan.hangry.domain.calculation.HangryStrainCalculator
import com.kevan.hangry.domain.model.RecoveryState
import com.kevan.hangry.ui.coach.DashExpression
import com.kevan.hangry.ui.coach.DashMood
import com.kevan.hangry.ui.coach.dashMood
import com.kevan.hangry.ui.components.EditActivityGoalsDialog
import com.kevan.hangry.ui.components.HangryCard
import com.kevan.hangry.ui.components.HangryRingGauge
import com.kevan.hangry.ui.components.HangryStatusBadge
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens
import java.util.Locale
import kotlin.math.roundToInt

private val DashboardUiState.recoveryState: RecoveryState
    get() = recoveryScore?.state?.let { runCatching { RecoveryState.valueOf(it) }.getOrNull() }
        ?: RecoveryState.BUILDING_BASELINE

/**
 * The top of Today in one card: recovery score, last night's sleep and today's strain, with
 * activity (active calories, steps, active time) underneath.
 */
@Composable
fun TodayOverviewCard(
    uiState: DashboardUiState,
    onOpenRecovery: () -> Unit,
    onOpenSleep: () -> Unit,
    onOpenWorkouts: () -> Unit,
    onSaveActivityGoals: (steps: Long, calories: Int, activeMinutes: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalHangryTokens.current
    val isPending = uiState.isPendingSleepData
    val state = uiState.recoveryState
    val score = uiState.recoveryScore?.score
    val scoreColor = when {
        isPending -> tokens.scoreColors.buildingBaseline
        state == RecoveryState.PRIMED -> tokens.scoreColors.primed
        state == RecoveryState.BALANCED -> tokens.scoreColors.balanced
        state == RecoveryState.REBUILD -> tokens.scoreColors.rebuild
        else -> tokens.scoreColors.buildingBaseline
    }
    var showGoalDialog by remember { mutableStateOf(false) }

    HangryCard(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = HangryTokens.CornerRadii.large,
        contentPadding = HangryTokens.Spacing.l
    ) {
        // Recovery, sleep and strain
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClickLabel = "Open recovery details") { onOpenRecovery() },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                HangryRingGauge(
                    progress = if (isPending) 0f else (score ?: 0) / 100f,
                    color = scoreColor,
                    modifier = Modifier.size(108.dp),
                    strokeWidth = 10.dp
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (isPending || score == null) "--" else "$score",
                            style = MaterialTheme.typography.headlineLarge,
                            color = scoreColor
                        )
                        Text(
                            text = stringResource(R.string.recovery_score_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.textMuted
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                if (isPending) {
                    Text("PENDING", style = MaterialTheme.typography.labelSmall, color = tokens.scoreColors.buildingBaseline)
                } else {
                    HangryStatusBadge(state = state)
                }
            }

            Spacer(modifier = Modifier.width(HangryTokens.Spacing.m))

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val sleepMin = uiState.dailySummary?.sleepDurationMinutes?.takeIf { it > 0 }
                val sleepScore = uiState.sleepAnalysis?.sleepScore ?: uiState.dailySummary?.sleepScore
                OverviewStat(
                    label = "Sleep score",
                    value = when {
                        isPending -> "Pending"
                        sleepScore != null && sleepMin != null -> "$sleepScore/100"
                        sleepMin != null -> "${sleepMin / 60}h ${sleepMin % 60}m"
                        else -> "—"
                    },
                    detail = when {
                        isPending -> "Log last night's sleep"
                        sleepMin != null -> "${sleepMin / 60}h ${sleepMin % 60}m asleep" +
                            (uiState.sleepAnalysis?.sleepPerformancePercentage?.let { " · $it% of need" } ?: "")
                        else -> null
                    },
                    color = tokens.chartColors.sleep,
                    onClick = onOpenSleep
                )
                val strain = uiState.dailySummary?.dayStrain?.coerceIn(0.0, HangryStrainCalculator.MAX_STRAIN)
                val target = uiState.strainRecommendation
                // Today's strain keeps building until midnight; earlier days are final.
                val isToday = uiState.selectedDate == java.time.LocalDate.now()
                OverviewStat(
                    label = if (isToday) "Strain so far" else "Strain",
                    value = strain?.let { String.format(Locale.US, "%.1f", it) } ?: if (isToday) "0.0" else "—",
                    detail = when {
                        !isToday -> if (strain != null) "Final for the day" else null
                        target != null -> String.format(Locale.US, "Target %.1f–%.1f · live", target.targetLow, target.targetHigh)
                        else -> "Live - builds through the day"
                    },
                    color = tokens.chartColors.trainingLoad,
                    onClick = onOpenWorkouts
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = HangryTokens.Spacing.m), color = tokens.cardBorder)

        // Activity
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Activity",
                style = MaterialTheme.typography.titleSmall,
                color = tokens.textPrimary,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { showGoalDialog = true }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Edit, contentDescription = "Edit activity goals", tint = tokens.textMuted, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ActivityMini(
                label = "Active kcal",
                value = uiState.todayActiveCalories.roundToInt(),
                goal = uiState.dailyActiveCaloriesGoal,
                color = tokens.chartColors.activeCalories,
                modifier = Modifier.weight(1f)
            )
            ActivityMini(
                label = "Steps",
                value = (uiState.dailySummary?.steps ?: 0L).toInt(),
                goal = uiState.dailyStepGoal.toInt(),
                color = tokens.chartColors.steps,
                modifier = Modifier.weight(1f)
            )
            ActivityMini(
                label = "Active min",
                value = uiState.todayActiveMinutes,
                goal = uiState.dailyActivityMinutesGoal,
                color = tokens.chartColors.hrv,
                modifier = Modifier.weight(1f)
            )
        }
    }

    if (showGoalDialog) {
        EditActivityGoalsDialog(
            initialStepGoal = uiState.dailyStepGoal,
            initialCaloriesGoal = uiState.dailyActiveCaloriesGoal,
            initialActiveMinutesGoal = uiState.dailyActivityMinutesGoal,
            onDismiss = { showGoalDialog = false },
            onSave = { s, c, m ->
                onSaveActivityGoals(s, c, m)
                showGoalDialog = false
            }
        )
    }
}

@Composable
private fun OverviewStat(label: String, value: String, detail: String?, color: Color, onClick: () -> Unit) {
    val tokens = LocalHangryTokens.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClickLabel = "Open $label") { onClick() }
            .padding(vertical = 2.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = tokens.textMuted)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = color)
        detail?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = tokens.textSecondary) }
    }
}

@Composable
private fun ActivityMini(label: String, value: Int, goal: Int, color: Color, modifier: Modifier = Modifier) {
    val tokens = LocalHangryTokens.current
    val progress = (value.toFloat() / goal.coerceAtLeast(1)).coerceIn(0f, 1f)
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        HangryRingGauge(progress = progress, color = color, modifier = Modifier.size(56.dp), strokeWidth = 6.dp) {
            Text(
                text = "${(progress * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = color
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = String.format(Locale.US, "%,d", value),
            style = MaterialTheme.typography.titleSmall,
            color = tokens.textPrimary
        )
        Text(
            text = "$label · ${String.format(Locale.US, "%,d", goal)}",
            style = MaterialTheme.typography.labelSmall,
            color = tokens.textMuted
        )
    }
}

/**
 * Today's advice, from the recovery score: Dash's read on the day, why the score is what it
 * is, and how hard to push.
 */
@Composable
fun DailyBriefingCard(uiState: DashboardUiState, modifier: Modifier = Modifier) {
    val tokens = LocalHangryTokens.current
    val isPending = uiState.isPendingSleepData
    val state = uiState.recoveryState
    val scoreEntity = uiState.recoveryScore

    val headline = when {
        isPending -> "Waiting for last night's sleep"
        state == RecoveryState.PRIMED -> "Primed for a big day"
        state == RecoveryState.BALANCED -> "Balanced and steady"
        state == RecoveryState.REBUILD -> "Rebuild and restore"
        else -> "Getting to know you"
    }
    val advice = when {
        isPending -> "Log or sync last night's sleep to unlock today's recovery and advice."
        else -> scoreEntity?.supportiveAdvice ?: stringResource(R.string.recovery_advice_building_baseline)
    }
    val positive = scoreEntity?.positiveContributors?.split("|")?.filter { it.isNotBlank() }.orEmpty()
    val negative = scoreEntity?.negativeContributors?.split("|")?.filter { it.isNotBlank() }.orEmpty()
    val drivers = if (isPending) emptyList() else positive.map { true to it } + negative.map { false to it }
    val target = uiState.strainRecommendation

    HangryCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            DashExpression(
                mood = if (isPending) DashMood.SLEEPY else state.dashMood(),
                size = 64.dp,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(HangryTokens.Spacing.m))
            Column(modifier = Modifier.weight(1f)) {
                Text("Daily Briefing", style = MaterialTheme.typography.labelMedium, color = tokens.textMuted)
                Text(headline, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = tokens.textPrimary)
                Spacer(modifier = Modifier.height(4.dp))
                Text(advice, style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary)
            }
        }
        if (drivers.isNotEmpty()) {
            Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))
            val shown = drivers.take(MAX_BRIEFING_DRIVERS)
            shown.forEach { (good, text) ->
                Text(
                    text = "• $text",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (good) tokens.scoreColors.primed else tokens.scoreColors.rebuild
                )
            }
            val hidden = drivers.size - shown.size
            if (hidden > 0) {
                Text("+$hidden more factor${if (hidden > 1) "s" else ""}", style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
            }
        }
        if (!isPending && target != null) {
            Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))
            Text(
                text = String.format(Locale.US, "Aim for a strain of %.1f–%.1f today.", target.targetLow, target.targetHigh),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = tokens.chartColors.trainingLoad
            )
        }
    }
}

private const val MAX_BRIEFING_DRIVERS = 3
