package com.kevan.hangry.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kevan.hangry.ui.theme.LocalHangryTokens
import com.kevan.hangry.ui.theme.SurfaceDark

/**
 * Lightweight, high-performance micro-sparkline for dashboard metric tiles and cards.
 *
 * Smoothly interpolates [points] across the canvas with a luminous vertical gradient
 * fill and an optional terminal pulse dot representing the latest reading.
 */
@Composable
fun HangrySparkline(
    points: List<Double?>,
    color: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 2.dp,
    showDot: Boolean = true
) {
    val tokens = LocalHangryTokens.current
    val isDark = tokens.cardBackground == SurfaceDark

    val animatedProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "sparklineProgress"
    )

    val validPoints = remember(points) { points.mapIndexedNotNull { idx, v -> v?.let { idx to it } } }

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        if (validPoints.size < 2 || width <= 0f || height <= 0f) {
            // Insufficient data: draw a subtle muted dashed baseline
            val midY = height / 2f
            drawLine(
                color = tokens.textMuted.copy(alpha = if (isDark) 0.25f else 0.35f),
                start = Offset(0f, midY),
                end = Offset(width, midY),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
            )
            return@Canvas
        }

        val values = validPoints.map { it.second }
        val minVal = values.minOrNull() ?: 0.0
        val maxVal = values.maxOrNull() ?: 1.0
        val range = (maxVal - minVal).let { if (it <= 0.0) 1.0 else it }

        // Vertical padding so peaks don't clip at top/bottom edges
        val topPadding = height * 0.12f
        val bottomPadding = height * 0.12f
        val usableHeight = (height - topPadding - bottomPadding).coerceAtLeast(1f)

        fun xForIndex(idx: Int): Float =
            (idx.toFloat() / (points.size - 1).coerceAtLeast(1)) * width

        fun yForValue(v: Double): Float =
            (height - bottomPadding - ((v - minVal) / range * usableHeight)).toFloat()

        val linePath = Path()
        val areaPath = Path()

        var firstPoint = true
        var lastPoint = Offset.Zero

        for ((idx, value) in validPoints) {
            val pt = Offset(xForIndex(idx), yForValue(value))
            if (firstPoint) {
                linePath.moveTo(pt.x, pt.y)
                areaPath.moveTo(pt.x, height)
                areaPath.lineTo(pt.x, pt.y)
                firstPoint = false
            } else {
                // Smooth connection
                val prevPt = lastPoint
                val controlX = (prevPt.x + pt.x) / 2f
                linePath.cubicTo(controlX, prevPt.y, controlX, pt.y, pt.x, pt.y)
                areaPath.cubicTo(controlX, prevPt.y, controlX, pt.y, pt.x, pt.y)
            }
            lastPoint = pt
        }

        areaPath.lineTo(lastPoint.x, height)
        areaPath.close()

        val fillTopAlpha = if (isDark) 0.28f else 0.16f
        val fillMidAlpha = if (isDark) 0.08f else 0.04f

        // Draw gradient area fill
        drawPath(
            path = areaPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    color.copy(alpha = fillTopAlpha * animatedProgress),
                    color.copy(alpha = fillMidAlpha * animatedProgress),
                    Color.Transparent
                ),
                startY = 0f,
                endY = height
            )
        )

        // Draw primary line
        drawPath(
            path = linePath,
            color = color.copy(alpha = animatedProgress),
            style = Stroke(
                width = strokeWidth.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        // Draw latest reading pulse dot
        if (showDot && !firstPoint) {
            val dotRadius = 3.dp.toPx()
            // Ambient outer glow for the dot
            drawCircle(
                color = color.copy(alpha = if (isDark) 0.35f else 0.20f),
                radius = dotRadius * 2f,
                center = lastPoint
            )
            // Solid center dot
            drawCircle(
                color = color,
                radius = dotRadius,
                center = lastPoint
            )
        }
    }
}
