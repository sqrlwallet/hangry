package com.kevan.hangry.ui.sleep

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kevan.hangry.R
import com.kevan.hangry.data.local.entity.DailyHealthSummaryEntity
import com.kevan.hangry.ui.components.HangryCard
import com.kevan.hangry.ui.components.skinTempText
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens
import com.kevan.hangry.ui.theme.SurfaceDark

/**
 * What the wearable measured while you slept - the calm, comparable readings recovery is built
 * on. Only readings the device recorded are shown; nothing at all hides the card.
 */
@Composable
fun OvernightVitalsCard(summary: DailyHealthSummaryEntity, onClick: (() -> Unit)? = null) {
    val tokens = LocalHangryTokens.current
    val isDark = tokens.cardBackground == SurfaceDark
    val items = listOfNotNull(
        summary.sleepingHeartRate?.let { stringResource(R.string.metrics_sleep_avg_sleeping_hr) to stringResource(R.string.metrics_sleep_bpm_value, it) },
        summary.restingHeartRate?.let { stringResource(R.string.metrics_sleep_lowest_hr) to stringResource(R.string.metrics_sleep_bpm_value, it) },
        summary.hrvRmssd?.let { stringResource(R.string.metrics_sleep_hrv) to stringResource(R.string.metrics_sleep_ms_value, it) },
        summary.respiratoryRate?.let { stringResource(R.string.metrics_components_breathing_rate) to stringResource(R.string.metrics_components_breathing_rate_value, it) },
        summary.spo2Percentage?.let { stringResource(R.string.metrics_sleep_spo2) to stringResource(R.string.metrics_sleep_percent_value, it) },
        summary.skinTempDeltaCelsius?.let { stringResource(R.string.metrics_components_skin_temp) to skinTempText(it) }
    )
    if (items.isEmpty()) return
    HangryCard(onClick = onClick) {
        Text(
            text = stringResource(R.string.metrics_sleep_overnight_vitals),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = tokens.textPrimary
        )
        Spacer(modifier = Modifier.height(HangryTokens.Spacing.m))
        items.chunked(3).forEachIndexed { rowIndex, row ->
            if (rowIndex > 0) {
                Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { (label, value) ->
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(HangryTokens.CornerRadii.medium),
                        color = if (isDark) tokens.cardBorder.copy(alpha = 0.25f) else tokens.cardBorder.copy(alpha = 0.35f),
                        border = BorderStroke(
                            1.dp,
                            if (isDark) tokens.cardBorder.copy(alpha = 0.5f) else tokens.cardBorder.copy(alpha = 0.6f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = value,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = tokens.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = tokens.textMuted
                            )
                        }
                    }
                }
                // Keep columns aligned on a short last row.
                repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}
