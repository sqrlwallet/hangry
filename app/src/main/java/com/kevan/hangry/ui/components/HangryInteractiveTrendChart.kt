package com.kevan.hangry.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.geometry.Size
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens
import com.kevan.hangry.ui.theme.SurfaceDark
import kotlin.math.sqrt
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import androidx.compose.ui.res.stringResource
import com.kevan.hangry.R

data class TrendPoint(
    val date: LocalDate,
    val value: Double?
)

/**
 * Premium interactive wellness trend line chart.
 *
 * Supports touch-and-drag scrubbing to inspect past data points with date, exact value,
 * and deviation from the period average. Features gradient area fill, dashed baseline average,
 * and high/low indicators.
 *
 * With [trend] (one value per point, e.g. a smoothed weight), the line and fill follow the trend
 * and the raw [points] are drawn as dots around it.
 */
@Composable
fun HangryInteractiveTrendChart(
    points: List<TrendPoint>,
    color: Color,
    unit: String,
    modifier: Modifier = Modifier,
    formatValue: (Double) -> String = { String.format(Locale.US, "%.1f", it) },
    onPointClicked: ((LocalDate) -> Unit)? = null,
    trend: List<Double?>? = null
) {
    val tokens = LocalHangryTokens.current
    val haptic = LocalHapticFeedback.current

    val validPoints = remember(points) { points.filter { it.value != null } }
    if (points.size < 2 || validPoints.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(140.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.metrics_components_chart_insufficient_data),
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textMuted
            )
        }
        return
    }

    val validValues = remember(validPoints) { validPoints.mapNotNull { it.value } }
    val minValue = remember(validValues) { validValues.minOrNull() ?: 0.0 }
    val maxValue = remember(validValues) { validValues.maxOrNull() ?: 100.0 }
    // The line: the trend when given (index-aligned with points), otherwise the points themselves.
    val hasTrend = trend != null && trend.size == points.size
    val lineValues = remember(points, trend) { if (hasTrend) trend!! else points.map { it.value } }
    val plotMin = remember(lineValues, minValue) { minOf(minValue, lineValues.filterNotNull().minOrNull() ?: minValue) }
    val plotMax = remember(lineValues, maxValue) { maxOf(maxValue, lineValues.filterNotNull().maxOrNull() ?: maxValue) }
    val avgValue = remember(validValues) { if (validValues.isNotEmpty()) validValues.average() else 0.0 }
    val stdDev = remember(validValues, avgValue) {
        if (validValues.size >= 2) {
            val variance = validValues.map { (it - avgValue) * (it - avgValue) }.average()
            sqrt(variance)
        } else 0.0
    }
    val isDark = tokens.cardBackground == SurfaceDark

    // Add 10% vertical padding so chart lines don't clamp strictly at canvas edges
    val rawRange = plotMax - plotMin
    val padding = if (rawRange <= 0.0) 1.0 else rawRange * 0.12
    val yMin = plotMin - padding
    val yMax = plotMax + padding
    val yRange = (yMax - yMin).let { if (it <= 0.0) 1.0 else it }

    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    val activePoint = selectedIndex?.let { idx -> points.getOrNull(idx) }
    val targetScrubberIdx = selectedIndex?.toFloat() ?: 0f
    val animatedScrubberIdx by animateFloatAsState(
        targetValue = targetScrubberIdx,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy),
        label = "scrubberIndexSpring"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        // Scrubber Tooltip / Metric Overview Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (activePoint != null && activePoint.value != null) {
                val deltaFromAvg = activePoint.value - avgValue
                Surface(
                    color = tokens.glassSurface,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = if (isDark) 0.5f else 0.35f)),
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = activePoint.date.format(DateTimeFormatter.ofPattern("EEEE, MMM d")),
                                style = MaterialTheme.typography.labelSmall,
                                color = tokens.textSecondary
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${formatValue(activePoint.value)} $unit".trim(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = color
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                val deltaText = when {
                                    abs(deltaFromAvg) < 0.05 -> stringResource(R.string.metrics_components_chart_on_baseline)
                                    deltaFromAvg > 0 -> stringResource(R.string.metrics_components_chart_vs_avg_positive, formatValue(deltaFromAvg))
                                    else -> stringResource(R.string.metrics_components_chart_vs_avg, formatValue(deltaFromAvg))
                                }
                                val trendValue = if (hasTrend) lineValues.getOrNull(selectedIndex ?: -1) else null
                                Text(
                                    text = trendValue?.let { stringResource(R.string.metrics_components_chart_trend, formatValue(it)) } ?: deltaText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (trendValue != null) tokens.textSecondary
                                    else if (deltaFromAvg >= 0) tokens.scoreColors.primed else tokens.scoreColors.rebuild
                                )
                            }
                        }

                        if (onPointClicked != null) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = color.copy(alpha = 0.15f),
                                modifier = Modifier.padding(start = 8.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.metrics_components_chart_tap_inspect),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = color,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.metrics_components_chart_avg, formatValue(avgValue), unit).trim(),
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.textSecondary
                        )
                        Text(
                            text = stringResource(R.string.metrics_components_chart_high, formatValue(maxValue)),
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.scoreColors.primed
                        )
                        Text(
                            text = stringResource(R.string.metrics_components_chart_low, formatValue(minValue)),
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.textMuted
                        )
                    }
                    Text(
                        text = stringResource(R.string.metrics_components_chart_touch_scrub),
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.textMuted.copy(alpha = 0.6f)
                    )
                }
            }
        }

        // Canvas Chart
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .pointerInput(points) {
                    detectTapGestures(
                        onPress = { offset ->
                            val stepX = size.width / (points.size - 1).coerceAtLeast(1)
                            val idx = (offset.x / stepX).roundToInt().coerceIn(0, points.size - 1)
                            selectedIndex = idx
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            tryAwaitRelease()
                            // If tapped and released, notify onPointClicked
                            onPointClicked?.invoke(points[idx].date)
                        }
                    )
                }
                .pointerInput(points) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val stepX = size.width / (points.size - 1).coerceAtLeast(1)
                            val idx = (offset.x / stepX).roundToInt().coerceIn(0, points.size - 1)
                            selectedIndex = idx
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        onDragEnd = {
                            // keep last selected point briefly or let user tap elsewhere
                        },
                        onDragCancel = {
                            selectedIndex = null
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val stepX = size.width / (points.size - 1).coerceAtLeast(1)
                            val idx = (change.position.x / stepX).roundToInt().coerceIn(0, points.size - 1)
                            if (idx != selectedIndex) {
                                selectedIndex = idx
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                val count = points.size
                val stepX = width / (count - 1).coerceAtLeast(1)

                fun yFor(v: Double): Float =
                    (height - ((v - yMin) / yRange * height)).toFloat()

                // Draw dashed average baseline
                val avgY = yFor(avgValue)
                drawLine(
                    color = tokens.textMuted.copy(alpha = if (isDark) 0.35f else 0.40f),
                    start = Offset(0f, avgY),
                    end = Offset(width, avgY),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )

                // Draw baseline normal-range corridor (mean ± 1.0 sigma)
                if (stdDev > 0.0) {
                    val topVal = minOf(plotMax, avgValue + stdDev)
                    val bottomVal = maxOf(plotMin, avgValue - stdDev)
                    val topY = yFor(topVal)
                    val bottomY = yFor(bottomVal)
                    drawRect(
                        color = color.copy(alpha = if (isDark) 0.07f else 0.045f),
                        topLeft = Offset(0f, topY),
                        size = Size(width, (bottomY - topY).coerceAtLeast(1f))
                    )
                }

                // Build line path & area path for gradient
                val linePath = Path()
                val areaPath = Path()

                var isPathStarted = false
                var lastValidX = 0f
                var firstValidX = 0f

                for (i in 0 until count) {
                    val lineValue = lineValues[i]
                    val x = i * stepX
                    if (lineValue != null) {
                        val y = yFor(lineValue)
                        if (!isPathStarted) {
                            linePath.moveTo(x, y)
                            areaPath.moveTo(x, height)
                            areaPath.lineTo(x, y)
                            isPathStarted = true
                            firstValidX = x
                        } else {
                            linePath.lineTo(x, y)
                            areaPath.lineTo(x, y)
                        }
                        lastValidX = x
                    } else if (isPathStarted) {
                        // gap in data: close current area path segment
                        areaPath.lineTo(lastValidX, height)
                        areaPath.close()
                        isPathStarted = false
                    }
                }

                if (isPathStarted) {
                    areaPath.lineTo(lastValidX, height)
                    areaPath.close()
                }

                // Draw luminous under-chart gradient
                drawPath(
                    path = areaPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            color.copy(alpha = 0.25f),
                            color.copy(alpha = 0.04f),
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = height
                    )
                )

                // Draw primary trend line
                drawPath(
                    path = linePath,
                    color = color,
                    style = Stroke(
                        width = 2.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                )

                // Draw data point circles (small; a little larger as dots around a trend line)
                val pointRadius = if (hasTrend) 2.5.dp.toPx() else 2.dp.toPx()
                points.forEachIndexed { i, pt ->
                    if (pt.value != null) {
                        val cx = i * stepX
                        val cy = yFor(pt.value)
                        drawCircle(
                            color = color.copy(alpha = if (hasTrend) 0.45f else 0.7f),
                            radius = pointRadius,
                            center = Offset(cx, cy)
                        )
                    }
                }

                // Draw scrubber cursor if active
                val sIdx = selectedIndex
                if (sIdx != null && sIdx in 0 until count) {
                    val sPoint = points[sIdx]
                    val sx = animatedScrubberIdx * stepX
                    // Vertical guideline
                    drawLine(
                        color = tokens.textMuted.copy(alpha = if (isDark) 0.55f else 0.40f),
                        start = Offset(sx, 0f),
                        end = Offset(sx, height),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                    )

                    if (sPoint.value != null) {
                        val sy = yFor(sPoint.value)
                        // Glowing outer halo
                        drawCircle(
                            color = color.copy(alpha = if (isDark) 0.35f else 0.22f),
                            radius = 9.dp.toPx(),
                            center = Offset(sx, sy)
                        )
                        // Inner prominent circle
                        drawCircle(
                            color = tokens.cardBackground,
                            radius = 4.5.dp.toPx(),
                            center = Offset(sx, sy)
                        )
                        drawCircle(
                            color = color,
                            radius = 3.dp.toPx(),
                            center = Offset(sx, sy)
                        )
                    }
                }
            }
        }

        // X-axis Milestone Dates (Start, Mid, End)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val startDate = points.firstOrNull()?.date
            val endDate = points.lastOrNull()?.date
            val midDate = points.getOrNull(points.size / 2)?.date

            Text(
                text = startDate?.format(DateTimeFormatter.ofPattern("MMM d")) ?: "",
                style = MaterialTheme.typography.labelSmall,
                color = tokens.textMuted
            )
            if (points.size >= 7 && midDate != null) {
                Text(
                    text = midDate.format(DateTimeFormatter.ofPattern("MMM d")),
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.textMuted
                )
            }
            Text(
                text = endDate?.format(DateTimeFormatter.ofPattern("MMM d")) ?: "",
                style = MaterialTheme.typography.labelSmall,
                color = tokens.textMuted
            )
        }
    }
}
