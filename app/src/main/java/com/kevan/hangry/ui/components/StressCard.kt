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
import androidx.compose.ui.res.stringResource
import com.kevan.hangry.R

@Composable
fun StressCard(
    stressResult: StressResult,
    modifier: Modifier = Modifier
) {
    val tokens = LocalHangryTokens.current

    val level = stressResult.level
    val score = stressResult.score

    val (badgeColor, containerColor, badgeLabel) = when (level) {
        StressLevel.LOW -> Triple(tokens.scoreColors.primed, tokens.scoreColors.primedContainer, stringResource(R.string.metrics_components_stress_low))
        StressLevel.MODERATE -> Triple(tokens.scoreColors.balanced, tokens.scoreColors.balancedContainer, stringResource(R.string.metrics_components_stress_moderate))
        StressLevel.ELEVATED -> Triple(tokens.scoreColors.rebuild, tokens.scoreColors.rebuildContainer, stringResource(R.string.metrics_components_stress_elevated))
        StressLevel.HIGH, StressLevel.BUILDING_BASELINE -> Triple(tokens.scoreColors.rebuild, tokens.scoreColors.rebuildContainer, stringResource(R.string.metrics_components_stress_high))
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
                        text = stringResource(R.string.metrics_components_autonomic_stress),
                        style = MaterialTheme.typography.titleMedium,
                        color = tokens.textPrimary
                    )
                    HangryInfoTip(
                        title = stringResource(R.string.metrics_components_autonomic_stress),
                        body = stressResult.supportiveAdvice
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
                        val text = if (pct >= 0) stringResource(R.string.metrics_components_vs_baseline_positive, pct) else stringResource(R.string.metrics_components_vs_baseline_negative, pct)
                        val col = if (pct >= 0) tokens.scoreColors.primed else tokens.scoreColors.rebuild
                        MarkerIndicator(label = stringResource(R.string.metrics_components_hrv_balance), value = text, valueColor = col)
                    }

                    stressResult.rhrDeviationBpm?.let { diff ->
                        val text = if (diff >= 0) stringResource(R.string.metrics_components_bpm_signed_positive, diff.roundToInt()) else stringResource(R.string.metrics_components_bpm_value, diff.roundToInt())
                        val col = if (diff <= 1) tokens.scoreColors.primed else tokens.scoreColors.rebuild
                        MarkerIndicator(label = stringResource(R.string.metrics_components_resting_pulse), value = text, valueColor = col)
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
