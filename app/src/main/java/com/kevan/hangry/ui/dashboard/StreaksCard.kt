package com.kevan.hangry.ui.dashboard

import com.kevan.hangry.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kevan.hangry.domain.calculation.Streak
import com.kevan.hangry.domain.calculation.StreakType
import com.kevan.hangry.ui.components.HangryCard
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens

/** Consecutive days of the habits the user has data for, two per row. */
@Composable
fun StreaksCard(streaks: List<Streak>, modifier: Modifier = Modifier) {
    val tokens = LocalHangryTokens.current
    HangryCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = tokens.chartColors.trainingLoad, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.dashboard_streaks_title), style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
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
        streaks.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.s), modifier = Modifier.padding(bottom = HangryTokens.Spacing.s)) {
                row.forEach { StreakTile(it, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StreakTile(streak: Streak, modifier: Modifier = Modifier) {
    val tokens = LocalHangryTokens.current
    val (icon, color) = streak.type.look()
    val status = when {
        streak.doneToday -> stringResource(R.string.dashboard_streak_done_today)
        streak.current > 0 -> stringResource(R.string.dashboard_streak_keep_going)
        else -> stringResource(R.string.dashboard_streak_start_today)
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(HangryTokens.CornerRadii.medium))
            .background(color.copy(alpha = 0.10f))
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(streak.type.label, style = MaterialTheme.typography.labelMedium, color = tokens.textSecondary)
        }
        Spacer(modifier = Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${streak.current}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = tokens.textPrimary)
            Text(
                if (streak.current == 1) stringResource(R.string.dashboard_streak_day_suffix) else stringResource(R.string.dashboard_streak_days_suffix),
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textSecondary,
                modifier = Modifier.padding(bottom = 3.dp)
            )
        }
        Text(
            if (streak.best > streak.current) stringResource(R.string.dashboard_streak_status_with_best, status, streak.best) else status,
            style = MaterialTheme.typography.labelSmall,
            color = if (streak.doneToday) tokens.scoreColors.primed else tokens.textMuted
        )
    }
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
