package com.kevan.hangry.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kevan.hangry.ui.theme.HangryMotion
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens

import androidx.compose.ui.geometry.Offset
import com.kevan.hangry.ui.theme.SurfaceDark

/**
 * The app's card surface. Pass [onClick] to make the card interactive: it then presses in with a
 * quick spring scale (alongside the ripple) instead of only highlighting, so a tap registers
 * physically across the whole surface.
 */
@Composable
fun HangryCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = HangryTokens.CornerRadii.medium,
    contentPadding: Dp = HangryTokens.Spacing.card,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val tokens = LocalHangryTokens.current
    val isDark = tokens.cardBackground == SurfaceDark

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.975f else 1f,
        animationSpec = HangryMotion.snappy(),
        label = "cardPressScale"
    )
    val pressModifier = if (onClick != null) {
        Modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClickLabel = onClickLabel,
                onClick = onClick
            )
    } else {
        Modifier
    }

    Card(
        modifier = modifier.then(pressModifier),
        shape = RoundedCornerShape(cornerRadius),
        colors = CardDefaults.cardColors(
            containerColor = tokens.cardBackground
        ),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = if (isDark) {
                    listOf(
                        Color.White.copy(alpha = 0.14f),
                        Color.White.copy(alpha = 0.03f)
                    )
                } else {
                    listOf(
                        tokens.edgeHighlight.copy(alpha = (tokens.edgeHighlight.alpha * 1.5f).coerceAtMost(0.20f)),
                        tokens.cardBorder.copy(alpha = 0.6f)
                    )
                },
                start = Offset.Zero,
                end = Offset.Infinite
            )
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = HangryTokens.Elevations.card)
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}
