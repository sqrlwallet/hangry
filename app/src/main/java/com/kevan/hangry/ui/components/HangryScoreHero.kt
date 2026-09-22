package com.kevan.hangry.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kevan.hangry.R
import com.kevan.hangry.data.local.entity.RecoveryScoreEntity
import com.kevan.hangry.domain.model.RecoveryState
import com.kevan.hangry.domain.model.ScoreConfidence
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens

/** Max positive+negative driver bullets shown inline before collapsing the rest into a count. */
private const val MAX_DRIVERS_SHOWN = 3

@Composable
fun HangryScoreHero(
    scoreEntity: RecoveryScoreEntity?,
    isPending: Boolean = false,
    modifier: Modifier = Modifier
) {
    val tokens = LocalHangryTokens.current

    if (isPending) {
        PendingRecoveryHero(modifier = modifier)
        return
    }

    val state = try {
        scoreEntity?.state?.let { RecoveryState.valueOf(it) } ?: RecoveryState.BUILDING_BASELINE
    } catch (_: Exception) {
        RecoveryState.BUILDING_BASELINE
    }

    val confidence = try {
        scoreEntity?.confidence?.let { ScoreConfidence.valueOf(it) } ?: ScoreConfidence.LOW
    } catch (_: Exception) {
        ScoreConfidence.LOW
    }

    val scoreDisplay = scoreEntity?.score?.toString() ?: "—"
    val adviceText = scoreEntity?.supportiveAdvice
        ?: stringResource(R.string.recovery_advice_building_baseline)

    val positiveDrivers = scoreEntity?.positiveContributors?.split("|")?.filter { it.isNotBlank() } ?: emptyList()
    val negativeDrivers = scoreEntity?.negativeContributors?.split("|")?.filter { it.isNotBlank() } ?: emptyList()

    val scoreColor = when (state) {
        RecoveryState.PRIMED -> tokens.scoreColors.primed
        RecoveryState.BALANCED -> tokens.scoreColors.balanced
        RecoveryState.REBUILD -> tokens.scoreColors.rebuild
        RecoveryState.BUILDING_BASELINE -> tokens.scoreColors.buildingBaseline
    }

    HangryCard(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = HangryTokens.CornerRadii.large,
        contentPadding = HangryTokens.Spacing.l
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.recovery_score_label),
                style = MaterialTheme.typography.titleLarge,
                color = tokens.textPrimary
            )
            HangryStatusBadge(state = state)
        }

        Spacer(modifier = Modifier.height(HangryTokens.Spacing.m))

        Row(verticalAlignment = Alignment.CenterVertically) {
            HangryRingGauge(
                progress = (scoreEntity?.score ?: 0) / 100f,
                color = scoreColor,
                modifier = Modifier.size(104.dp),
                strokeWidth = 10.dp
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = scoreDisplay,
                        style = MaterialTheme.typography.headlineLarge,
                        color = scoreColor
                    )
                    if (scoreEntity?.score != null) {
                        Text(
                            text = "%",
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.textMuted
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(HangryTokens.Spacing.m))
            HangryConfidenceBadge(confidence = confidence)
        }

        Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))

        Text(
            text = adviceText,
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.textPrimary
        )

        val allDrivers = positiveDrivers.map { true to it } + negativeDrivers.map { false to it }
        if (allDrivers.isNotEmpty()) {
            val shownDrivers = allDrivers.take(MAX_DRIVERS_SHOWN)
            val hiddenCount = allDrivers.size - shownDrivers.size

            Spacer(modifier = Modifier.height(HangryTokens.Spacing.m))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                shownDrivers.forEach { (isPositive, driver) ->
                    Text(
                        text = "• $driver",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isPositive) tokens.scoreColors.primed else tokens.scoreColors.rebuild
                    )
                }
                if (hiddenCount > 0) {
                    Text(
                        text = "+$hiddenCount more factor${if (hiddenCount > 1) "s" else ""}",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.textMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun PendingRecoveryHero(modifier: Modifier = Modifier) {
    val tokens = LocalHangryTokens.current

    HangryCard(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = HangryTokens.CornerRadii.large,
        contentPadding = HangryTokens.Spacing.l
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.recovery_score_label),
                style = MaterialTheme.typography.titleLarge,
                color = tokens.textPrimary
            )
            Surface(
                color = tokens.scoreColors.buildingBaselineContainer,
                shape = RoundedCornerShape(HangryTokens.CornerRadii.pill)
            ) {
                Text(
                    text = "PENDING",
                    color = tokens.scoreColors.buildingBaseline,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(HangryTokens.Spacing.m))

        HangryRingGauge(
            progress = 0f,
            color = tokens.scoreColors.buildingBaseline,
            modifier = Modifier.size(104.dp),
            strokeWidth = 10.dp
        ) {
            Text(
                text = "--",
                style = MaterialTheme.typography.headlineLarge,
                color = tokens.scoreColors.buildingBaseline
            )
        }

        Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))

        Text(
            text = "Log or sync last night's sleep to unlock today's recovery.",
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.textPrimary
        )
    }
}
