package com.kevan.hangry.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.graphics.Brush
import com.kevan.hangry.ui.theme.LocalHangryTokens
import com.kevan.hangry.ui.theme.SurfaceDark

/**
 * A circular progress gauge: track + progress arc swept over 270 degrees, with a centered
 * content slot for the score/value. Features theme-aware ambient radial glow and smooth
 * animations for both progress and color transitions.
 */
@Composable
fun HangryRingGauge(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    trackColor: Color = LocalHangryTokens.current.cardBorder,
    strokeWidth: Dp = 10.dp,
    showAmbientGlow: Boolean = true,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val tokens = LocalHangryTokens.current
    val isDark = tokens.cardBackground == SurfaceDark

    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "ringGaugeProgress"
    )

    val animatedColor by animateColorAsState(
        targetValue = color,
        animationSpec = tween(durationMillis = 400),
        label = "ringGaugeColor"
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = strokeWidth.toPx()
            val startAngle = 135f
            val sweepAngle = 270f
            val diameter = size.minDimension - strokePx
            val center = Offset(size.width / 2f, size.height / 2f)
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)

            // Ambient radial glow behind the gauge
            if (showAmbientGlow && animatedProgress > 0f) {
                val glowRadius = diameter * 0.65f
                val centerAlpha = if (isDark) 0.22f else 0.12f
                val outerAlpha = if (isDark) 0.06f else 0.03f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            animatedColor.copy(alpha = centerAlpha),
                            animatedColor.copy(alpha = outerAlpha),
                            Color.Transparent
                        ),
                        center = center,
                        radius = glowRadius
                    ),
                    radius = glowRadius,
                    center = center
                )
            }

            // Background track
            drawArc(
                color = trackColor.copy(alpha = if (isDark) 0.40f else 0.60f),
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )

            // Progress arc
            if (animatedProgress > 0f) {
                drawArc(
                    color = animatedColor,
                    startAngle = startAngle,
                    sweepAngle = sweepAngle * animatedProgress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }
        }
        content()
    }
}
