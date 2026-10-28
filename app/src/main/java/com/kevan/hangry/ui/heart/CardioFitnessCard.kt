package com.kevan.hangry.ui.heart

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kevan.hangry.R
import com.kevan.hangry.data.repository.CardioFitnessSnapshot
import com.kevan.hangry.domain.calculation.CardioFitness
import com.kevan.hangry.domain.calculation.HeartRateRecovery
import com.kevan.hangry.ui.components.HangryCard
import com.kevan.hangry.ui.components.HangryInfoIconButton
import com.kevan.hangry.ui.components.HangryInfoSection
import com.kevan.hangry.ui.components.HangrySparkline
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * VO2 max against your age and sex with its 3-month trend, and how fast your heart rate settles
 * after workouts. Shown once there's either to show.
 */
@Composable
fun CardioFitnessCard(snapshot: CardioFitnessSnapshot, modifier: Modifier = Modifier) {
    val tokens = LocalHangryTokens.current
    HangryCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.DirectionsRun, contentDescription = null, tint = tokens.textSecondary, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.metrics_heart_cardio_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = tokens.textPrimary,
                modifier = Modifier.weight(1f)
            )
            HangryInfoIconButton(
                title = stringResource(R.string.metrics_heart_cardio_title),
                sections = listOf(
                    HangryInfoSection(stringResource(R.string.metrics_heart_cardio_info_vo2_title), stringResource(R.string.metrics_heart_cardio_info_vo2_body)),
                    HangryInfoSection(stringResource(R.string.metrics_heart_cardio_info_hrr_title), stringResource(R.string.metrics_heart_cardio_info_hrr_body))
                ),
                compact = true
            )
        }

        snapshot.vo2Max?.let { vo2 ->
            Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))
            Vo2Section(vo2, snapshot)
        }

        if (snapshot.recoveries.isNotEmpty()) {
            if (snapshot.vo2Max != null) {
                HorizontalDivider(color = tokens.cardBorder, modifier = Modifier.padding(vertical = HangryTokens.Spacing.s))
            } else {
                Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))
            }
            RecoverySection(snapshot)
        }
    }
}

@Composable
private fun Vo2Section(vo2: Double, snapshot: CardioFitnessSnapshot) {
    val tokens = LocalHangryTokens.current
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = String.format(Locale.US, "%.1f", vo2),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = tokens.textPrimary
        )
        Text(
            text = stringResource(R.string.metrics_heart_cardio_vo2_unit),
            style = MaterialTheme.typography.labelSmall,
            color = tokens.textMuted,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp).weight(1f)
        )
        snapshot.level?.let { LevelBadge(it) }
    }
    snapshot.change?.let { change ->
        val rounded = (change * 10).roundToInt() / 10.0
        Text(
            text = when {
                abs(rounded) < 0.1 -> stringResource(R.string.metrics_heart_cardio_trend_steady)
                rounded > 0 -> stringResource(R.string.metrics_heart_cardio_trend_up, rounded)
                else -> stringResource(R.string.metrics_heart_cardio_trend_down, abs(rounded))
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (change <= -CardioFitness.MEANINGFUL_DROP) tokens.scoreColors.rebuild else tokens.textSecondary
        )
    }
    snapshot.ranges?.let { r ->
        Text(
            text = stringResource(R.string.metrics_heart_cardio_ranges, r.typical.roundToInt(), r.lowBelow.roundToInt(), r.highFrom.roundToInt()),
            style = MaterialTheme.typography.bodySmall,
            color = tokens.textSecondary
        )
    }
    if (snapshot.needsBirthday) {
        Text(
            text = stringResource(R.string.metrics_heart_cardio_needs_birthday),
            style = MaterialTheme.typography.bodySmall,
            color = tokens.textMuted
        )
    }
    // Low or falling: what moves it, from the same advice as the longevity pillars.
    val falling = (snapshot.change ?: 0.0) <= -CardioFitness.MEANINGFUL_DROP
    if (snapshot.level == CardioFitness.Level.LOW || falling) {
        Spacer(modifier = Modifier.height(HangryTokens.Spacing.xs))
        Text(
            text = stringResource(R.string.metrics_heart_cardio_how_to_raise),
            style = MaterialTheme.typography.bodySmall,
            color = tokens.textPrimary
        )
    }
}

@Composable
private fun RecoverySection(snapshot: CardioFitnessSnapshot) {
    val tokens = LocalHangryTokens.current
    val latest = snapshot.recoveries.first().recovery
    val average = snapshot.averageOneMinuteDrop
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.metrics_heart_cardio_hrr_title),
                style = MaterialTheme.typography.titleSmall,
                color = tokens.textPrimary
            )
            Text(
                text = if (average != null) {
                    stringResource(R.string.metrics_heart_cardio_hrr_average, average.roundToInt(), snapshot.recoveries.count { it.recovery.oneMinuteDrop != null })
                } else {
                    stringResource(R.string.metrics_heart_cardio_hrr_latest_two, latest.twoMinuteDrop ?: 0)
                },
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textSecondary
            )
        }
        // Oldest to newest, so the line reads left to right like the other trends.
        val points = snapshot.recoveries.mapNotNull { it.recovery.oneMinuteDrop?.toDouble() }.take(14).reversed()
        if (points.size >= 2) {
            HangrySparkline(points = points, color = tokens.chartColors.restingHeartRate, modifier = Modifier.width(72.dp).height(28.dp))
            Spacer(modifier = Modifier.width(HangryTokens.Spacing.s))
        }
        RatingBadge(latest.rating)
    }
}

@Composable
private fun LevelBadge(level: CardioFitness.Level) {
    val tokens = LocalHangryTokens.current
    val (text, color) = when (level) {
        CardioFitness.Level.HIGH -> stringResource(R.string.metrics_heart_cardio_level_high) to tokens.scoreColors.primed
        CardioFitness.Level.ABOVE_AVERAGE -> stringResource(R.string.metrics_heart_cardio_level_above) to tokens.scoreColors.primed
        CardioFitness.Level.BELOW_AVERAGE -> stringResource(R.string.metrics_heart_cardio_level_below) to tokens.scoreColors.balanced
        CardioFitness.Level.LOW -> stringResource(R.string.metrics_heart_cardio_level_low) to tokens.scoreColors.rebuild
    }
    Badge(text, color)
}

@Composable
private fun RatingBadge(rating: HeartRateRecovery.Rating) {
    val tokens = LocalHangryTokens.current
    val (text, color) = when (rating) {
        HeartRateRecovery.Rating.STRONG -> stringResource(R.string.metrics_training_hrr_strong) to tokens.scoreColors.primed
        HeartRateRecovery.Rating.TYPICAL -> stringResource(R.string.metrics_training_hrr_typical) to tokens.scoreColors.balanced
        HeartRateRecovery.Rating.LOW -> stringResource(R.string.metrics_training_hrr_low) to tokens.scoreColors.rebuild
    }
    Badge(text, color)
}

@Composable
private fun Badge(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(8.dp), color = color.copy(alpha = 0.12f)) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}
