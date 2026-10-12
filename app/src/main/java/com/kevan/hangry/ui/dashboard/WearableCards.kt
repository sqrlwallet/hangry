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

/**
 * Body check: last night's resting heart rate, HRV, breathing rate and skin temperature, each
 * against your usual. The early-warning card at the top of Today only appears when two or more
 * are off; this shows the state every day. Hidden until the wearable reports any of them.
 */
@Composable
fun BodyCheckCard(comparisons: List<BodySignals.Comparison>, onClick: () -> Unit) {
    if (comparisons.none { it.today != null }) return
    val tokens = LocalHangryTokens.current
    val offCount = comparisons.count { it.off }
    HangryCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.HealthAndSafety, contentDescription = null, tint = tokens.scoreColors.primed, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(HangryTokens.Spacing.xs))
            Text(stringResource(R.string.dashboard_body_check_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = tokens.textPrimary, modifier = Modifier.weight(1f))
            Text(
                when {
                    offCount >= BodySignals.MIN_SIGNALS -> stringResource(R.string.dashboard_body_check_several_off, offCount)
                    offCount == 1 -> stringResource(R.string.dashboard_body_check_one_off)
                    else -> stringResource(R.string.dashboard_body_check_normal)
                },
                style = MaterialTheme.typography.labelMedium,
                color = when {
                    offCount >= BodySignals.MIN_SIGNALS -> tokens.scoreColors.rebuild
                    offCount == 1 -> tokens.scoreColors.balanced
                    else -> tokens.scoreColors.primed
                }
            )
        }
        Spacer(Modifier.height(HangryTokens.Spacing.s))
        comparisons.forEach { c -> BodyCheckRow(c) }
        Text(stringResource(R.string.dashboard_body_check_footnote), style = MaterialTheme.typography.labelSmall, color = tokens.textMuted, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun BodyCheckRow(c: BodySignals.Comparison) {
    val tokens = LocalHangryTokens.current
    val label = stringResource(
        when (c.kind) {
            BodySignals.Kind.RESTING_HR -> R.string.dashboard_body_check_rhr
            BodySignals.Kind.HRV -> R.string.dashboard_body_check_hrv
            BodySignals.Kind.BREATHING -> R.string.dashboard_body_check_breathing
            BodySignals.Kind.SKIN_TEMP -> R.string.dashboard_body_check_skin_temp
        }
    )
    fun value(v: Double): String = when (c.kind) {
        BodySignals.Kind.RESTING_HR -> String.format(Locale.US, "%.0f bpm", v)
        BodySignals.Kind.HRV -> String.format(Locale.US, "%.0f ms", v)
        BodySignals.Kind.BREATHING -> String.format(Locale.US, "%.1f /min", v)
        BodySignals.Kind.SKIN_TEMP -> ""
    }
    val today = c.today?.let { if (c.kind == BodySignals.Kind.SKIN_TEMP) skinTempText(it) else value(it) } ?: "—"
    val usual = c.usual?.let { if (c.kind == BodySignals.Kind.SKIN_TEMP) skinTempText(it) else value(it) }
    val dotColor = when {
        c.today == null || c.usual == null -> tokens.textMuted
        c.off -> tokens.scoreColors.rebuild
        else -> tokens.scoreColors.primed
    }
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.size(8.dp).background(dotColor, CircleShape))
        Spacer(Modifier.width(HangryTokens.Spacing.s))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = tokens.textPrimary, modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(today, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = tokens.textPrimary)
            Text(
                usual?.let { stringResource(R.string.dashboard_body_check_usual, it) } ?: stringResource(R.string.dashboard_body_check_learning),
                style = MaterialTheme.typography.labelSmall,
                color = tokens.textMuted
            )
        }
    }
}

/**
 * Cycle phase and day, with the temperature-confirmed ovulation when the wearable shows it.
 * Only for women who log periods (or import them from Health Connect).
 */
@Composable
fun CycleCard(records: HealthRecordsSnapshot, skinTemps: Map<LocalDate, Double>, onClick: () -> Unit) {
    if (!records.showsFemaleHealth || records.periods.isEmpty() || records.isPregnant) return
    val today = LocalDate.now()
    val stats = HealthMarkerCalculator.cycleStats(records.periods.map { it.startDate to it.endDate }, today)
    val insight = CycleTemperature.insight(stats, skinTemps, today, records.periods.map { it.startDate }) ?: return
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
            style = MaterialTheme.typography.titleLarge,
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
