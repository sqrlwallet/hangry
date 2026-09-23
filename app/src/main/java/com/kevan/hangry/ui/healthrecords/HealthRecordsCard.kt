package com.kevan.hangry.ui.healthrecords

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kevan.hangry.domain.calculation.HealthMarkerCalculator
import com.kevan.hangry.domain.model.HealthRecordsSnapshot
import com.kevan.hangry.domain.model.MarkerType
import com.kevan.hangry.ui.components.HangryCard
import com.kevan.hangry.ui.components.metricToneColor
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens

/** Home-page glance: the three most recently updated markers, or a prompt to add the first. */
@Composable
fun HealthRecordsCard(records: HealthRecordsSnapshot, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val tokens = LocalHangryTokens.current
    val recent = records.readings.map { it.type }.distinct().take(3)
    HangryCard(modifier = modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.MonitorHeart, contentDescription = null, tint = tokens.chartColors.restingHeartRate, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Health Records", style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
            }
            Text(
                if (records.goals.isNotEmpty()) "${records.goals.size} goal${if (records.goals.size == 1) "" else "s"} →" else "Open →",
                style = MaterialTheme.typography.labelSmall, color = tokens.textMuted
            )
        }
        Spacer(Modifier.height(HangryTokens.Spacing.s))
        if (recent.isEmpty()) {
            Text(
                "Track blood pressure, blood sugar, cholesterol and more - and set goals to bring them where you want.",
                style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary
            )
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.m)) {
                recent.forEach { type -> MarkerGlance(records, type, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun MarkerGlance(records: HealthRecordsSnapshot, type: MarkerType, modifier: Modifier) {
    val tokens = LocalHangryTokens.current
    val metric = HealthMarkerCalculator.describe(type, records.latest(type), records.sex)
    Column(modifier) {
        Text(metric.displayValue, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = tokens.textPrimary, maxLines = 1)
        Text(type.label, style = MaterialTheme.typography.labelSmall, color = tokens.textMuted, maxLines = 1)
        metric.status?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = metricToneColor(metric.tone), maxLines = 1) }
    }
}
