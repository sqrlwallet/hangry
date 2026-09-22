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

            // Row 1: SpO2 & VO2 Max
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.m)
            ) {
                VitalItem(
                    label = "Blood Oxygen",
                    value = summary?.spo2Percentage?.let { String.format(Locale.US, "%.0f%%", it) } ?: "—",
                    subtitle = if (summary?.spo2Percentage != null) "Normal (95–100%)" else "Pending sync",
                    valueColor = if (summary?.spo2Percentage != null) tokens.scoreColors.primed else tokens.textMuted,
                    modifier = Modifier.weight(1f)
                )

                VitalItem(
                    label = "VO₂ Max",
                    value = summary?.vo2Max?.let { String.format(Locale.US, "%.1f", it) } ?: "—",
                    unit = if (summary?.vo2Max != null) "mL/kg/min" else null,
                    subtitle = if (summary?.vo2Max != null) "Cardio Fitness" else "Pending sync",
                    valueColor = if (summary?.vo2Max != null) tokens.scoreColors.primed else tokens.textMuted,
                    modifier = Modifier.weight(1f)
                )
            }

            // Row 2: Respiratory Rate & Blood Pressure
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.m)
            ) {
                VitalItem(
                    label = "Respiration",
                    value = summary?.respiratoryRate?.let { String.format(Locale.US, "%.1f", it) } ?: "—",
                    unit = if (summary?.respiratoryRate != null) "rpm" else null,
                    subtitle = if (summary?.respiratoryRate != null) "Resting (12–20)" else "Pending sync",
                    valueColor = if (summary?.respiratoryRate != null) tokens.textPrimary else tokens.textMuted,
                    modifier = Modifier.weight(1f)
                )

                val hasBp = summary?.bloodPressureSystolic != null && summary?.bloodPressureDiastolic != null
                val bpText = if (hasBp) {
                    "${summary!!.bloodPressureSystolic!!.toInt()}/${summary.bloodPressureDiastolic!!.toInt()}"
                } else {
                    "—"
                }

                VitalItem(
                    label = "Blood Pressure",
                    value = bpText,
                    unit = if (hasBp) "mmHg" else null,
                    subtitle = if (hasBp) "Optimal (<120/80)" else "Pending sync",
                    valueColor = if (hasBp) tokens.textPrimary else tokens.textMuted,
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
