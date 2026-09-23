package com.kevan.hangry.ui.components

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kevan.hangry.data.local.entity.DailyHealthSummaryEntity
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens
import java.util.Locale

@Composable
fun VitalsCard(
    summary: DailyHealthSummaryEntity?,
    modifier: Modifier = Modifier
) {
    val tokens = LocalHangryTokens.current

    HangryCard(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.m)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.xs)
                ) {
                    Icon(
                        imageVector = Icons.Default.MonitorHeart,
                        contentDescription = null,
                        tint = tokens.scoreColors.primed,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Key Vitals & Cardio",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = tokens.textPrimary
                    )
                }

                Text(
                    text = "Health Connect",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.textMuted
                )
            }

            // SpO2 & VO2 Max
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.m)
            ) {
                VitalItem(
                    label = "Blood Oxygen",
                    value = summary?.spo2Percentage?.let { String.format(Locale.US, "%.0f%%", it) } ?: "—",
                    subtitle = when (val spo2 = summary?.spo2Percentage) {
                        null -> "No data yet"
                        in 95.0..100.0 -> "Normal (95–100%)"
                        in 90.0..95.0 -> "A little low"
                        else -> "Low - worth checking"
                    },
                    valueColor = when (val spo2 = summary?.spo2Percentage) {
                        null -> tokens.textMuted
                        in 95.0..100.0 -> tokens.scoreColors.primed
                        else -> tokens.scoreColors.rebuild
                    },
                    modifier = Modifier.weight(1f)
                )

                VitalItem(
                    label = "VO₂ Max",
                    value = summary?.vo2Max?.let { String.format(Locale.US, "%.1f", it) } ?: "—",
                    unit = if (summary?.vo2Max != null) "mL/kg/min" else null,
                    subtitle = if (summary?.vo2Max != null) "Cardio Fitness" else "No data yet",
                    valueColor = if (summary?.vo2Max != null) tokens.scoreColors.primed else tokens.textMuted,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun VitalItem(
    label: String,
    value: String,
    subtitle: String,
    valueColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    unit: String? = null
) {
    val tokens = LocalHangryTokens.current

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tokens.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (unit != null) {
                Text(
                    text = unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.textMuted,
                    modifier = Modifier.padding(bottom = 2.dp),
                    maxLines = 1
                )
            }
        }

        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = tokens.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
