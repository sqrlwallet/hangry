package com.kevan.hangry.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kevan.hangry.R
import com.kevan.hangry.data.local.entity.DailyHealthSummaryEntity
import com.kevan.hangry.domain.calculation.BodySignals
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens
import java.util.Locale

/** How a reading sits against your usual - drives the dot beside its label. */
private enum class VitalStatus { NORMAL, WATCH, OFF, UNKNOWN }

private data class Vital(
    val label: String,
    val value: String,
    val note: String?,
    val status: VitalStatus,
    val sparklinePoints: List<Double?>? = null,
    val sparklineColor: Color? = null
)

/**
 * Every overnight and heart reading in one card: resting heart rate, HRV, breathing, skin
 * temperature, blood oxygen, sleeping heart rate and VO2 max. Readings with a usual (from the
 * body check) show it underneath, with a dot that turns red when it's off. Only readings the
 * wearable reports are shown, so trackers without a sensor don't get blank tiles.
 */
@Composable
fun VitalsCard(
    summary: DailyHealthSummaryEntity?,
    comparisons: List<BodySignals.Comparison>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    rhrHistory: List<Double?> = emptyList(),
    hrvHistory: List<Double?> = emptyList()
) {
    val tokens = LocalHangryTokens.current
    val byKind = comparisons.associateBy { it.kind }

    fun format(kind: BodySignals.Kind, v: Double): String = when (kind) {
        BodySignals.Kind.RESTING_HR -> String.format(Locale.US, "%.0f", v)
        BodySignals.Kind.HRV -> String.format(Locale.US, "%.0f", v)
        BodySignals.Kind.BREATHING -> String.format(Locale.US, "%.1f", v)
        BodySignals.Kind.SKIN_TEMP -> ""
    }

    @Composable
    fun compared(
        kind: BodySignals.Kind,
        label: String,
        unit: String,
        fallback: Double?,
        history: List<Double?> = emptyList(),
        historyColor: Color? = null
    ): Vital? {
        val c = byKind[kind]
        val today = c?.today ?: fallback ?: return null
        val isTemp = kind == BodySignals.Kind.SKIN_TEMP
        val value = if (isTemp) skinTempText(today) else format(kind, today) + unit
        val note = when {
            c?.usual != null -> stringResource(R.string.dashboard_body_check_usual, if (isTemp) skinTempText(c.usual) else format(kind, c.usual))
            c != null -> stringResource(R.string.dashboard_vitals_learning)
            else -> null
        }
        val status = when {
            c?.usual == null -> VitalStatus.UNKNOWN
            c.off -> VitalStatus.OFF
            else -> VitalStatus.NORMAL
        }
        val validHistory = history.takeIf { it.filterNotNull().size >= 2 }
        return Vital(label, value, note, status, validHistory, historyColor)
    }

    val vitals = listOfNotNull(
        compared(
            BodySignals.Kind.RESTING_HR,
            stringResource(R.string.dashboard_vitals_rhr),
            stringResource(R.string.dashboard_vitals_bpm_suffix),
            summary?.restingHeartRate,
            history = rhrHistory,
            historyColor = tokens.chartColors.restingHeartRate
        ),
        compared(
            BodySignals.Kind.HRV,
            stringResource(R.string.dashboard_vitals_hrv),
            stringResource(R.string.dashboard_vitals_ms_suffix),
            summary?.hrvRmssd?.takeIf { it > 0 },
            history = hrvHistory,
            historyColor = tokens.chartColors.hrv
        ),
        summary?.spo2Percentage?.let { spo2 ->
            Vital(
                label = stringResource(R.string.dashboard_vitals_spo2),
                value = String.format(Locale.US, "%.0f%%", spo2),
                note = stringResource(
                    when {
                        spo2 >= 95.0 -> R.string.dashboard_vitals_spo2_normal
                        spo2 >= 90.0 -> R.string.metrics_components_spo2_little_low
                        else -> R.string.metrics_components_spo2_low
                    }
                ),
                status = when {
                    spo2 >= 95.0 -> VitalStatus.NORMAL
                    spo2 >= 90.0 -> VitalStatus.WATCH
                    else -> VitalStatus.OFF
                }
            )
        },
        compared(BodySignals.Kind.BREATHING, stringResource(R.string.dashboard_vitals_breathing), "", summary?.respiratoryRate),
        compared(BodySignals.Kind.SKIN_TEMP, stringResource(R.string.dashboard_vitals_skin_temp), "", summary?.skinTempDeltaCelsius),
        summary?.sleepingHeartRate?.let {
            Vital(stringResource(R.string.dashboard_vitals_sleeping_hr), String.format(Locale.US, "%.0f", it) + stringResource(R.string.dashboard_vitals_bpm_suffix), stringResource(R.string.dashboard_vitals_overnight_avg), VitalStatus.UNKNOWN)
        },
        summary?.vo2Max?.let {
            Vital(stringResource(R.string.dashboard_vitals_vo2), String.format(Locale.US, "%.1f", it), stringResource(R.string.dashboard_vitals_vo2_unit), VitalStatus.UNKNOWN)
        }
    )

    val offCount = comparisons.count { it.off }
    val title = stringResource(R.string.dashboard_vitals_title)

    HangryCard(
        modifier = modifier.fillMaxWidth(),
        onClickLabel = stringResource(R.string.dashboard_vitals_open),
        onClick = onClick
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.MonitorHeart, contentDescription = null, tint = tokens.chartColors.restingHeartRate, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(HangryTokens.Spacing.xs))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = tokens.textPrimary)
            HangryInfoTip(title = title, body = stringResource(R.string.dashboard_body_check_footnote))
            Spacer(Modifier.weight(1f))
            if (comparisons.any { it.usual != null && it.today != null }) {
                Text(
                    when {
                        offCount >= BodySignals.MIN_SIGNALS -> stringResource(R.string.dashboard_body_check_several_off, offCount)
                        offCount == 1 -> stringResource(R.string.dashboard_body_check_one_off)
                        else -> stringResource(R.string.dashboard_body_check_normal)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = when {
                        offCount >= BodySignals.MIN_SIGNALS -> tokens.scoreColors.rebuild
                        offCount == 1 -> tokens.scoreColors.balanced
                        else -> tokens.scoreColors.primed
                    }
                )
            }
        }

        if (vitals.isEmpty()) {
            Text(
                stringResource(R.string.dashboard_vitals_empty),
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textSecondary,
                modifier = Modifier.padding(top = 4.dp)
            )
            return@HangryCard
        }

        Spacer(Modifier.height(HangryTokens.Spacing.s))
        vitals.chunked(3).forEachIndexed { i, row ->
            if (i > 0) Spacer(Modifier.height(HangryTokens.Spacing.s + 2.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.s)) {
                row.forEach { VitalTile(it, Modifier.weight(1f)) }
                // Keep columns aligned on a short last row.
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun VitalTile(vital: Vital, modifier: Modifier = Modifier) {
    val tokens = LocalHangryTokens.current
    val dot: Color? = when (vital.status) {
        VitalStatus.NORMAL -> tokens.scoreColors.primed
        VitalStatus.WATCH -> tokens.scoreColors.balanced
        VitalStatus.OFF -> tokens.scoreColors.rebuild
        VitalStatus.UNKNOWN -> null
    }
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (dot != null) {
                Spacer(Modifier.size(6.dp).background(dot, CircleShape))
                Spacer(Modifier.width(4.dp))
            }
            Text(vital.label, style = MaterialTheme.typography.labelSmall, color = tokens.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            vital.value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (vital.status == VitalStatus.OFF) tokens.scoreColors.rebuild else tokens.textPrimary,
            maxLines = 1
        )
        if (vital.sparklinePoints != null && vital.sparklineColor != null) {
            Spacer(Modifier.height(4.dp))
            HangrySparkline(
                points = vital.sparklinePoints,
                color = vital.sparklineColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp),
                contentDescription = stringResource(R.string.metrics_components_a11y_sparkline, vital.label)
            )
            Spacer(Modifier.height(2.dp))
        }
        vital.note?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = tokens.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
