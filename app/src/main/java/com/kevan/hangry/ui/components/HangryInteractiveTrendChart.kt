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
import androidx.compose.ui.unit.sp
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

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
 */
@Composable
fun HangryInteractiveTrendChart(
    points: List<TrendPoint>,
    color: Color,
    unit: String,
    modifier: Modifier = Modifier,
    formatValue: (Double) -> String = { String.format(Locale.US, "%.1f", it) },
    onPointClicked: ((LocalDate) -> Unit)? = null
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
                text = "Insufficient data points for this timeframe",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textMuted
            )
        }
        return
    }

    val validValues = remember(validPoints) { validPoints.mapNotNull { it.value } }
    val minValue = remember(validValues) { validValues.minOrNull() ?: 0.0 }
    val maxValue = remember(validValues) { validValues.maxOrNull() ?: 100.0 }
    val avgValue = remember(validValues) { if (validValues.isNotEmpty()) validValues.average() else 0.0 }

    // Add 10% vertical padding so chart lines don't clamp strictly at canvas edges
    val rawRange = maxValue - minValue
    val padding = if (rawRange <= 0.0) 1.0 else rawRange * 0.12
    val yMin = minValue - padding
    val yMax = maxValue + padding
    val yRange = (yMax - yMin).let { if (it <= 0.0) 1.0 else it }

    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    val activePoint = selectedIndex?.let { idx -> points.getOrNull(idx) }

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
                    color = tokens.cardBorder.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.5f)),
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
                                    abs(deltaFromAvg) < 0.05 -> "on baseline"
                                    deltaFromAvg > 0 -> "+${formatValue(deltaFromAvg)} vs avg"
                                    else -> "${formatValue(deltaFromAvg)} vs avg"
                                }
                                Text(
                                    text = deltaText,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                                    color = if (deltaFromAvg >= 0) tokens.scoreColors.primed else tokens.scoreColors.rebuild
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
                                    text = "Tap to inspect day",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold
                                    ),
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
                            text = "Avg: ${formatValue(avgValue)} $unit".trim(),
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.textSecondary
                        )
                        Text(
                            text = "High: ${formatValue(maxValue)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.scoreColors.primed
                        )
                        Text(
                            text = "Low: ${formatValue(minValue)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.textMuted
                        )
                    }
                    Text(
                        text = "Touch to scrub",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
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
                    color = tokens.textMuted.copy(alpha = 0.28f),
                    start = Offset(0f, avgY),
                    end = Offset(width, avgY),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )

                // Build line path & area path for gradient
                val linePath = Path()
                val areaPath = Path()

                var isPathStarted = false
                var lastValidX = 0f
                var firstValidX = 0f

                for (i in 0 until count) {
                    val pt = points[i]
                    val x = i * stepX
                    if (pt.value != null) {
                        val y = yFor(pt.value)
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

                // Draw data point circles (small)
                val pointRadius = 2.dp.toPx()
                points.forEachIndexed { i, pt ->
                    if (pt.value != null) {
                        val cx = i * stepX
                        val cy = yFor(pt.value)
                        drawCircle(
                            color = color.copy(alpha = 0.7f),
                            radius = pointRadius,
                            center = Offset(cx, cy)
                        )
                    }
                }

                // Draw scrubber cursor if active
                val sIdx = selectedIndex
                if (sIdx != null && sIdx in 0 until count) {
                    val sPoint = points[sIdx]
                    val sx = sIdx * stepX
                    // Vertical guideline
                    drawLine(
                        color = Color.White.copy(alpha = 0.35f),
                        start = Offset(sx, 0f),
                        end = Offset(sx, height),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                    )

                    if (sPoint.value != null) {
                        val sy = yFor(sPoint.value)
                        // Glowing outer halo
                        drawCircle(
                            color = color.copy(alpha = 0.30f),
                            radius = 9.dp.toPx(),
                            center = Offset(sx, sy)
                        )
                        // Inner prominent circle
                        drawCircle(
                            color = Color.White,
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
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = tokens.textMuted
            )
            if (points.size >= 7 && midDate != null) {
                Text(
                    text = midDate.format(DateTimeFormatter.ofPattern("MMM d")),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = tokens.textMuted
                )
            }
            Text(
                text = endDate?.format(DateTimeFormatter.ofPattern("MMM d")) ?: "",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = tokens.textMuted
            )
        }
    }
}
