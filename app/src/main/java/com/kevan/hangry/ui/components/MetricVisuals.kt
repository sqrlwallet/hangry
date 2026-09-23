package com.kevan.hangry.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kevan.hangry.domain.model.BodyMetric
import com.kevan.hangry.domain.model.MetricBand
import com.kevan.hangry.domain.model.MetricTone
import com.kevan.hangry.ui.theme.LocalHangryTokens
import java.util.Locale
import kotlin.math.abs

// Shared visuals for any banded metric (Body Metrics, Health Records): status chip, a coloured
// range bar with the user's marker, a ranges table and the explainer blocks.

@Composable
fun MetricInfoBlock(heading: String, body: String) {
    val tokens = LocalHangryTokens.current
    Column {
        Text(heading, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = tokens.textPrimary)
        Spacer(Modifier.height(2.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary)
    }
}

@Composable
fun MetricBandTable(metric: BodyMetric) {
    val tokens = LocalHangryTokens.current
    val current = metric.band
    val decimals = decimalsFor(metric.bands)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("Ranges", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = tokens.textPrimary)
        metric.bands.forEachIndexed { index, band ->
            val lower = metric.bands.getOrNull(index - 1)?.upTo
            val range = when {
                lower == null && band.upTo != null -> "under ${fmt(band.upTo, decimals)}"
                band.upTo == null && lower != null -> "${fmt(lower, decimals)} and over"
                lower != null && band.upTo != null -> "${fmt(lower, decimals)} – ${fmt(band.upTo, decimals)}"
                else -> "any"
            }
            val isCurrent = metric.isAvailable && band == current
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (isCurrent) metricToneColor(band.tone).copy(alpha = 0.12f) else Color.Transparent,
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(8.dp).background(metricToneColor(band.tone), RoundedCornerShape(50)))
                Spacer(Modifier.width(10.dp))
                Text(
                    band.label + if (isCurrent) "  (you)" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                    color = tokens.textPrimary,
                    modifier = Modifier.weight(1f)
                )
                Text("$range ${metric.unit}".trim(), style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
            }
        }
    }
}

/** Coloured band segments with a marker at the user's value. */
@Composable
fun MetricRangeBar(metric: BodyMetric) {
    val tokens = LocalHangryTokens.current
    val value = metric.value ?: return
    val min = metric.scaleMin
    val max = metric.scaleMax
    val span = (max - min).takeIf { it > 0 } ?: return
    val bandColors = metric.bands.map { metricToneColor(it.tone) }
    val markerColor = tokens.textPrimary
    val trackBg = tokens.cardBorder
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(16.dp)
    ) {
        val barHeight = 6.dp.toPx()
        val top = (size.height - barHeight) / 2
        drawRoundRect(trackBg, Offset(0f, top), Size(size.width, barHeight), CornerRadius(barHeight / 2))
        var start = min
        metric.bands.forEachIndexed { i, band ->
            val end = (band.upTo ?: max).coerceIn(min, max)
            val from = ((start.coerceIn(min, max) - min) / span * size.width).toFloat()
            val to = ((end - min) / span * size.width).toFloat()
            if (to > from) {
                drawRoundRect(
                    color = bandColors[i].copy(alpha = 0.55f),
                    topLeft = Offset(from + 1f, top),
                    size = Size(to - from - 2f, barHeight),
                    cornerRadius = CornerRadius(barHeight / 2)
                )
            }
            start = band.upTo ?: max
        }
        val x = ((value.coerceIn(min, max) - min) / span * size.width).toFloat()
        drawCircle(color = Color.White, radius = 7.dp.toPx(), center = Offset(x, size.height / 2))
        drawCircle(color = markerColor, radius = 5.dp.toPx(), center = Offset(x, size.height / 2))
    }
}

@Composable
fun MetricStatusChip(text: String, tone: MetricTone, modifier: Modifier = Modifier) {
    val color = metricToneColor(tone)
    Surface(color = color.copy(alpha = 0.15f), shape = RoundedCornerShape(12.dp), modifier = modifier) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun metricToneColor(tone: MetricTone): Color {
    val tokens = LocalHangryTokens.current
    return when (tone) {
        MetricTone.GOOD -> tokens.scoreColors.primed
        MetricTone.NEUTRAL -> tokens.textSecondary
        MetricTone.CAUTION -> tokens.scoreColors.balanced
        MetricTone.RISK -> tokens.scoreColors.rebuild
    }
}

/** Ratios like 0.85 need two decimals in the range table; BMI-style values one at most. */
private fun decimalsFor(bands: List<MetricBand>): Int {
    val cuts = bands.mapNotNull { it.upTo }
    return when {
        cuts.isNotEmpty() && cuts.all { it < 3 } -> 2
        cuts.any { abs(it - Math.round(it)) > 1e-6 } -> 1
        else -> 0
    }
}


private fun fmt(value: Double, decimals: Int): String = String.format(Locale.US, "%.${decimals}f", value)
