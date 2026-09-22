package com.kevan.hangry.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kevan.hangry.domain.model.StressLevel
import com.kevan.hangry.domain.model.StressResult
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens
import kotlin.math.roundToInt

@Composable
fun StressCard(
    stressResult: StressResult,
    modifier: Modifier = Modifier
) {
    val tokens = LocalHangryTokens.current

    val level = stressResult.level
    val score = stressResult.score

    val (badgeColor, containerColor, badgeLabel) = when (level) {
        StressLevel.LOW -> Triple(tokens.scoreColors.primed, tokens.scoreColors.primedContainer, "LOW STRESS")
        StressLevel.MODERATE -> Triple(tokens.scoreColors.balanced, tokens.scoreColors.balancedContainer, "MODERATE")
        StressLevel.ELEVATED -> Triple(tokens.scoreColors.rebuild, tokens.scoreColors.rebuildContainer, "ELEVATED")
        StressLevel.HIGH, StressLevel.BUILDING_BASELINE -> Triple(tokens.scoreColors.rebuild, tokens.scoreColors.rebuildContainer, "HIGH STRESS")
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
                        imageVector = Icons.Default.Spa,
                        contentDescription = null,
                        tint = badgeColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(HangryTokens.Spacing.xs))
                    Text(
                        text = "Autonomic Stress",
                        style = MaterialTheme.typography.titleMedium,
                        color = tokens.textPrimary
                    )
                }

                Surface(
                    color = containerColor,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = badgeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = badgeColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(HangryTokens.Spacing.m))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Ring gauge showing 0-100 stress score
                HangryRingGauge(
                    progress = (score / 100f).coerceIn(0f, 1f),
                    color = badgeColor,
                    modifier = Modifier.size(72.dp),
                    strokeWidth = 7.dp
                ) {
                    Text(
                        text = "$score",
                        style = MaterialTheme.typography.titleMedium,
                        color = badgeColor
                    )
                }

                Spacer(modifier = Modifier.width(HangryTokens.Spacing.m))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stressResult.summaryText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.textPrimary
                    )
                    Spacer(modifier = Modifier.height(HangryTokens.Spacing.xxs))
                    Text(
                        text = stressResult.supportiveAdvice,
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.textSecondary
                    )
                }
            }

            // Physiological markers breakdown
            if (stressResult.hrvDeviationRatio != null || stressResult.rhrDeviationBpm != null) {
                Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))
                HorizontalDivider(color = tokens.cardBorder)
                Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    stressResult.hrvDeviationRatio?.let { ratio ->
                        val pct = ((ratio - 1.0) * 100).roundToInt()
                        val text = if (pct >= 0) "+$pct% vs baseline" else "$pct% vs baseline"
                        val col = if (pct >= 0) tokens.scoreColors.primed else tokens.scoreColors.rebuild
                        MarkerIndicator(label = "HRV Balance", value = text, valueColor = col)
                    }

                    stressResult.rhrDeviationBpm?.let { diff ->
                        val text = if (diff >= 0) "+${diff.roundToInt()} bpm" else "${diff.roundToInt()} bpm"
                        val col = if (diff <= 1) tokens.scoreColors.primed else tokens.scoreColors.rebuild
                        MarkerIndicator(label = "Resting Pulse", value = text, valueColor = col)
                    }
                }
            }
        }
    }
}

@Composable
private fun MarkerIndicator(label: String, value: String, valueColor: Color) {
    val tokens = LocalHangryTokens.current
    Column {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
        Text(text = value, style = MaterialTheme.typography.bodySmall, color = valueColor)
    }
}
