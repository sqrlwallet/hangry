package com.kevan.hangry.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kevan.hangry.domain.model.RecoveryState
import com.kevan.hangry.domain.model.ScoreConfidence
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens
import androidx.compose.ui.res.stringResource
import com.kevan.hangry.R

@Composable
fun HangryStatusBadge(
    state: RecoveryState,
    modifier: Modifier = Modifier
) {
    val tokens = LocalHangryTokens.current
    val (backgroundColor, textColor, label) = when (state) {
        RecoveryState.PRIMED -> Triple(
            tokens.scoreColors.primedContainer,
            tokens.scoreColors.primed,
            stringResource(R.string.metrics_components_state_primed)
        )
        RecoveryState.BALANCED -> Triple(
            tokens.scoreColors.balancedContainer,
            tokens.scoreColors.balanced,
            stringResource(R.string.metrics_components_state_balanced)
        )
        RecoveryState.REBUILD -> Triple(
            tokens.scoreColors.rebuildContainer,
            tokens.scoreColors.rebuild,
            stringResource(R.string.metrics_components_state_rebuild)
        )
        RecoveryState.BUILDING_BASELINE -> Triple(
            tokens.scoreColors.buildingBaselineContainer,
            tokens.scoreColors.buildingBaseline,
            stringResource(R.string.metrics_components_calibrating_caps)
        )
    }

    Box(
        modifier = modifier
            .background(backgroundColor, RoundedCornerShape(HangryTokens.CornerRadii.pill))
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            color = textColor,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
fun HangryConfidenceBadge(
    confidence: ScoreConfidence,
    modifier: Modifier = Modifier
) {
    val tokens = LocalHangryTokens.current
    val (color, text) = when (confidence) {
        ScoreConfidence.HIGH -> Pair(tokens.scoreColors.primed, stringResource(R.string.metrics_components_high_confidence))
        ScoreConfidence.MEDIUM -> Pair(tokens.scoreColors.balanced, stringResource(R.string.metrics_components_medium_confidence))
        ScoreConfidence.LOW -> Pair(tokens.scoreColors.buildingBaseline, stringResource(R.string.metrics_components_calibrating))
    }

    Box(
        modifier = modifier
            .background(tokens.cardBackground, RoundedCornerShape(HangryTokens.CornerRadii.pill))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelSmall
        )
    }
}
