package com.kevan.hangry.ui.sleep

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.kevan.hangry.R
import com.kevan.hangry.data.local.entity.DailyHealthSummaryEntity
import com.kevan.hangry.ui.components.HangryCard
import com.kevan.hangry.ui.components.skinTempText
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens

/**
 * What the wearable measured while you slept - the calm, comparable readings recovery is built
 * on. Only readings the device recorded are shown; nothing at all hides the card.
 */
@Composable
fun OvernightVitalsCard(summary: DailyHealthSummaryEntity, onClick: (() -> Unit)? = null) {
    val tokens = LocalHangryTokens.current
    val items = listOfNotNull(
        summary.sleepingHeartRate?.let { stringResource(R.string.metrics_sleep_avg_sleeping_hr) to stringResource(R.string.metrics_sleep_bpm_value, it) },
        summary.restingHeartRate?.let { stringResource(R.string.metrics_sleep_lowest_hr) to stringResource(R.string.metrics_sleep_bpm_value, it) },
        summary.hrvRmssd?.let { stringResource(R.string.metrics_sleep_hrv) to stringResource(R.string.metrics_sleep_ms_value, it) },
        summary.respiratoryRate?.let { stringResource(R.string.metrics_components_breathing_rate) to stringResource(R.string.metrics_components_breathing_rate_value, it) },
        summary.spo2Percentage?.let { stringResource(R.string.metrics_sleep_spo2) to stringResource(R.string.metrics_sleep_percent_value, it) },
        summary.skinTempDeltaCelsius?.let { stringResource(R.string.metrics_components_skin_temp) to skinTempText(it) }
    )
    if (items.isEmpty()) return
    HangryCard(modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier) {
        Text(
            text = stringResource(R.string.metrics_sleep_overnight_vitals),
            style = MaterialTheme.typography.titleMedium,
            color = tokens.textPrimary
        )
        Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))
        items.chunked(3).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.m)) {
                row.forEach { (label, value) ->
                    Column(modifier = Modifier.weight(1f)) {
                        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = tokens.textPrimary)
                        Text(label, style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
                    }
                }
                // Keep columns aligned on a short last row.
                repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
            Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))
        }
    }
}
