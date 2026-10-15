package com.kevan.hangry.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kevan.hangry.R
import com.kevan.hangry.domain.model.RecoveryState
import com.kevan.hangry.domain.model.ScoreConfidence
import com.kevan.hangry.ui.theme.HangryMotion
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens
import com.kevan.hangry.ui.theme.SurfaceDark

@Composable
fun HangryStatusBadge(
    state: RecoveryState,
    modifier: Modifier = Modifier
) {
    val tokens = LocalHangryTokens.current
    val isDark = tokens.cardBackground == SurfaceDark
    val (targetBgColor, targetTextColor, label) = when (state) {
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

    val animBgColor by animateColorAsState(targetValue = targetBgColor, animationSpec = tween(HangryMotion.slow), label = "statusBadgeBg")
    val animTextColor by animateColorAsState(targetValue = targetTextColor, animationSpec = tween(HangryMotion.slow), label = "statusBadgeText")
    val borderColor = animTextColor.copy(alpha = if (isDark) 0.32f else 0.22f)
    val pillShape = RoundedCornerShape(HangryTokens.CornerRadii.pill)

    Box(
        modifier = modifier
            .background(animBgColor, pillShape)
            .border(BorderStroke(1.dp, borderColor), pillShape)
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            color = animTextColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun HangryConfidenceBadge(
    confidence: ScoreConfidence,
    modifier: Modifier = Modifier
) {
    val tokens = LocalHangryTokens.current
    val isDark = tokens.cardBackground == SurfaceDark
    val (targetColor, text) = when (confidence) {
        ScoreConfidence.HIGH -> Pair(tokens.scoreColors.primed, stringResource(R.string.metrics_components_high_confidence))
        ScoreConfidence.MEDIUM -> Pair(tokens.scoreColors.balanced, stringResource(R.string.metrics_components_medium_confidence))
        ScoreConfidence.LOW -> Pair(tokens.scoreColors.buildingBaseline, stringResource(R.string.metrics_components_calibrating))
    }

    val animColor by animateColorAsState(targetValue = targetColor, animationSpec = tween(HangryMotion.slow), label = "confidenceBadgeColor")
    val pillShape = RoundedCornerShape(HangryTokens.CornerRadii.pill)
    val bgTint = animColor.copy(alpha = if (isDark) 0.14f else 0.08f)
    val borderTint = animColor.copy(alpha = if (isDark) 0.30f else 0.20f)

    Box(
        modifier = modifier
            .background(bgTint, pillShape)
            .border(BorderStroke(1.dp, borderTint), pillShape)
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            color = animColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium
        )
    }
}
