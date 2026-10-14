package com.kevan.hangry.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kevan.hangry.R
import com.kevan.hangry.domain.calculation.BodySignals
import com.kevan.hangry.domain.calculation.CycleTemperature
import com.kevan.hangry.domain.calculation.HealthMarkerCalculator
import com.kevan.hangry.domain.model.HealthRecordsSnapshot
import com.kevan.hangry.ui.components.HangryCard
import com.kevan.hangry.ui.components.skinTempText
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What the cycle card shows, or null when it has nothing to show (Today leaves it out). */
internal fun cycleCardData(
    records: HealthRecordsSnapshot,
    skinTemps: Map<LocalDate, Double>,
    today: LocalDate = LocalDate.now()
): Pair<com.kevan.hangry.domain.model.CycleStats, CycleTemperature.Insight>? {
    if (!records.showsFemaleHealth || records.periods.isEmpty() || records.isPregnant) return null
    val stats = HealthMarkerCalculator.cycleStats(records.periods.map { it.startDate to it.endDate }, today)
    val insight = CycleTemperature.insight(stats, skinTemps, today, records.periods.map { it.startDate }) ?: return null
    return stats to insight
}

/**
 * Cycle phase and day, with the temperature-confirmed ovulation when the wearable shows it.
 * Only for women who log periods (or import them from Health Connect).
 */
@Composable
fun CycleCard(records: HealthRecordsSnapshot, skinTemps: Map<LocalDate, Double>, onClick: () -> Unit) {
    val today = LocalDate.now()
    val (stats, insight) = remember(records, skinTemps, today) { cycleCardData(records, skinTemps, today) } ?: return
    val tokens = LocalHangryTokens.current
    val cycleLength = stats.averageCycleDays ?: 28.0
    val next = insight.predictedNextStart ?: stats.predictedNextStart
    HangryCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.WaterDrop, contentDescription = null, tint = tokens.scoreColors.rebuild, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(HangryTokens.Spacing.xs))
            Text(stringResource(R.string.dashboard_cycle_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = tokens.textPrimary, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.dashboard_cycle_day, insight.cycleDay), style = MaterialTheme.typography.labelMedium, color = tokens.textSecondary)
        }
        Spacer(Modifier.height(HangryTokens.Spacing.s))
        Text(
            stringResource(
                when (insight.phase) {
                    CycleTemperature.Phase.MENSTRUAL -> R.string.body_records_phase_menstrual
                    CycleTemperature.Phase.FOLLICULAR -> R.string.body_records_phase_follicular
                    CycleTemperature.Phase.FERTILE_WINDOW -> R.string.body_records_phase_fertile
                    CycleTemperature.Phase.LUTEAL -> if (insight.confirmedByTemperature) R.string.body_records_phase_luteal else R.string.body_records_phase_luteal_est
                }
            ),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = tokens.textPrimary
        )
        LinearProgressIndicator(
            progress = { (insight.cycleDay / cycleLength).toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            color = tokens.scoreColors.rebuild,
            trackColor = tokens.cardBorder
        )
        Row(horizontalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.m)) {
            Text(
                next?.let { stringResource(R.string.dashboard_cycle_next, it.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))) }
                    ?: stringResource(R.string.body_records_need_two_cycles),
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textSecondary,
                modifier = Modifier.weight(1f)
            )
            insight.shiftSizeC?.let {
                Text(stringResource(R.string.dashboard_cycle_temp_confirmed, skinTempText(it)), style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
            }
        }
    }
}
