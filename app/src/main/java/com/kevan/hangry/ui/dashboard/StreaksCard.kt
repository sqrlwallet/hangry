package com.kevan.hangry.ui.dashboard

import com.kevan.hangry.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kevan.hangry.domain.calculation.Streak
import com.kevan.hangry.domain.calculation.StreakType
import com.kevan.hangry.ui.components.HangryCard
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens

/** Consecutive days of the habits the user has data for, side by side in one row. */
@Composable
fun StreaksCard(streaks: List<Streak>, modifier: Modifier = Modifier) {
    val tokens = LocalHangryTokens.current
    HangryCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = tokens.chartColors.trainingLoad, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(HangryTokens.Spacing.xs))
            Text(stringResource(R.string.dashboard_streaks_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = tokens.textPrimary)
            Spacer(modifier = Modifier.weight(1f))
            if (streaks.isNotEmpty()) {
                val done = streaks.count { it.doneToday }
                Text(
                    stringResource(R.string.dashboard_streaks_done_count, done, streaks.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (done == streaks.size) tokens.scoreColors.primed else tokens.textMuted
                )
            }
        }
        Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))
        if (streaks.isEmpty()) {
            Text(
                stringResource(R.string.dashboard_streaks_empty),
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textSecondary
            )
            return@HangryCard
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            streaks.forEach { StreakItem(it, Modifier.weight(1f)) }
        }
    }
}

/** A habit's icon (filled in once today is done), its run in days, and its name. */
@Composable
private fun StreakItem(streak: Streak, modifier: Modifier = Modifier) {
    val tokens = LocalHangryTokens.current
    val (icon, color) = streak.type.look()
    val status = when {
        streak.doneToday -> stringResource(R.string.dashboard_streak_done_today)
        streak.current > 0 -> stringResource(R.string.dashboard_streak_keep_going)
        else -> stringResource(R.string.dashboard_streak_start_today)
    }
    val description = stringResource(R.string.dashboard_streak_description, streak.type.label, streak.current, status, streak.best)
    Column(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (streak.doneToday) color else color.copy(alpha = 0.12f))
                .then(if (streak.doneToday) Modifier else Modifier.border(1.5.dp, color.copy(alpha = 0.45f), CircleShape)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = if (streak.doneToday) Color.White else color, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${streak.current}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = tokens.textPrimary)
            Text(
                stringResource(R.string.dashboard_streak_days_short),
                style = MaterialTheme.typography.labelSmall,
                color = tokens.textMuted,
                modifier = Modifier.padding(start = 1.dp, bottom = 3.dp)
            )
        }
        Text(
            stringResource(streak.type.shortLabel()),
            style = MaterialTheme.typography.labelSmall,
            color = tokens.textSecondary,
            maxLines = 1
        )
        if (streak.best > streak.current) {
            Text(
                stringResource(R.string.dashboard_streak_best, streak.best),
                style = MaterialTheme.typography.labelSmall,
                color = tokens.textMuted,
                maxLines = 1
            )
        }
    }
}

private fun StreakType.shortLabel(): Int = when (this) {
    StreakType.STEPS -> R.string.dashboard_streak_short_steps
    StreakType.MEALS -> R.string.dashboard_streak_short_meals
    StreakType.SLEEP -> R.string.dashboard_streak_short_sleep
    StreakType.SUPPLEMENTS -> R.string.dashboard_streak_short_supplements
    StreakType.FASTING -> R.string.dashboard_streak_short_fasting
}

@Composable
private fun StreakType.look(): Pair<ImageVector, Color> {
    val tokens = LocalHangryTokens.current
    return when (this) {
        StreakType.STEPS -> Icons.AutoMirrored.Filled.DirectionsWalk to tokens.chartColors.steps
        StreakType.MEALS -> Icons.Default.Restaurant to tokens.chartColors.activeCalories
        StreakType.SLEEP -> Icons.Default.Bedtime to tokens.chartColors.sleep
        StreakType.SUPPLEMENTS -> Icons.Default.Medication to tokens.chartColors.hrv
        StreakType.FASTING -> Icons.Default.Timer to com.kevan.hangry.ui.fasting.FastingColor
    }
}
