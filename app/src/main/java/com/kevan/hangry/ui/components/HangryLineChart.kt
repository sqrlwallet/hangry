package com.kevan.hangry.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp

/**
 * A small trend line chart drawn on a Canvas (no third-party charting dependency, matching
 * the rest of the app's hand-rolled visualizations). Values are chronological (oldest first);
 * a null entry represents a day with no data and simply breaks the connecting line rather
 * than being coerced to zero.
 */
@Composable
fun HangryLineChart(
    values: List<Double?>,
    color: Color,
    modifier: Modifier = Modifier
) {
    val validValues = values.filterNotNull()

    if (validValues.isEmpty() || values.size < 2) {
        Box(modifier = modifier)
        return
    }

    val minValue = validValues.min()
    val maxValue = validValues.max()
    val range = (maxValue - minValue).let { if (it <= 0.0) 1.0 else it }

    Canvas(modifier = modifier.fillMaxSize()) {
        val stepX = size.width / (values.size - 1)
        val pointRadius = 2.5.dp.toPx()
        val strokeWidth = 2.5.dp.toPx()

        fun yFor(value: Double): Float =
            size.height - ((value - minValue) / range * size.height).toFloat()

        for (i in 0 until values.size - 1) {
            val start = values[i]
            val end = values[i + 1]
            if (start != null && end != null) {
                drawLine(
                    color = color,
                    start = Offset(i * stepX, yFor(start)),
                    end = Offset((i + 1) * stepX, yFor(end)),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }
        }

        values.forEachIndexed { index, value ->
            if (value != null) {
                drawCircle(color = color, radius = pointRadius, center = Offset(index * stepX, yFor(value)))
            }
        }
    }
}
