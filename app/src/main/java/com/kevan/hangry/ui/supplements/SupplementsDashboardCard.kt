package com.kevan.hangry.ui.supplements

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kevan.hangry.R
import com.kevan.hangry.domain.model.SupplementsSnapshot
import com.kevan.hangry.ui.components.HangryCard
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens
import com.kevan.hangry.ui.theme.SurfaceDark
import java.time.LocalTime
import com.kevan.hangry.ui.components.rememberHangryHaptics

/**
 * Home-page checklist: today's doses of tracked supplements with one-tap ticks. Untracked ones are
 * assumed taken, so they're just listed; with none added, a prompt to add the first one.
 */
@Composable
fun SupplementsDashboardCard(
    snapshot: SupplementsSnapshot,
    onToggle: (Long, LocalTime, Boolean) -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalHangryTokens.current
    val isDark = tokens.cardBackground == SurfaceDark
    val haptic = rememberHangryHaptics()

    HangryCard(modifier = modifier.fillMaxWidth(), onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Medication, contentDescription = null, tint = tokens.brandAccent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(HangryTokens.Spacing.xs))
            Text(stringResource(R.string.nutrition_supplements_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = tokens.textPrimary, modifier = Modifier.weight(1f))
            val doses = snapshot.todayDoses
            if (doses.isNotEmpty() && snapshot.takenToday == doses.size) {
                Surface(
                    shape = RoundedCornerShape(HangryTokens.CornerRadii.pill),
                    color = tokens.scoreColors.primed.copy(alpha = if (isDark) 0.16f else 0.10f),
                    border = BorderStroke(1.dp, tokens.scoreColors.primed.copy(alpha = if (isDark) 0.35f else 0.22f))
                ) {
                    Text(
                        text = stringResource(R.string.nutrition_supplements_all_taken),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = tokens.scoreColors.primed,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            } else {
                Text(
                    text = when {
                        snapshot.supplements.isEmpty() -> stringResource(R.string.nutrition_supplements_add_arrow)
                        doses.isEmpty() -> stringResource(R.string.nutrition_supplements_open_arrow)
                        else -> stringResource(R.string.nutrition_supplements_taken_ratio, snapshot.takenToday, doses.size)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.textMuted
                )
            }
        }
        if (snapshot.supplements.isEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.nutrition_supplements_card_empty),
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textSecondary
            )
            return@HangryCard
        }
        if (snapshot.todayDoses.isEmpty()) {
            snapshot.active.takeIf { it.isNotEmpty() }?.let { active ->
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.nutrition_supplements_taking, active.joinToString(", ") { it.name }),
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary,
                    maxLines = 2
                )
            }
            return@HangryCard
        }
        snapshot.todayDoses.take(MAX_ROWS).forEach { dose ->
            val rowAlpha by animateFloatAsState(
                targetValue = if (dose.taken) 0.65f else 1.0f,
                animationSpec = tween(250),
                label = "doseRowAlpha"
            )
            val checkScale by animateFloatAsState(
                targetValue = if (dose.taken) 1.05f else 1.0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                label = "checkScale"
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(rowAlpha)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        haptic.tick()
                        onToggle(dose.supplement.id, dose.time, !dose.taken)
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.scale(checkScale)) {
                    Checkbox(
                        checked = dose.taken,
                        onCheckedChange = {
                            haptic.tick()
                            onToggle(dose.supplement.id, dose.time, it)
                        }
                    )
                }
                Text(
                    dose.supplement.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (dose.taken) tokens.textMuted else tokens.textPrimary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
                Text(dose.time.format(TIME_FORMAT), style = MaterialTheme.typography.labelMedium, color = tokens.textSecondary)
            }
        }
        if (snapshot.todayDoses.size > MAX_ROWS) {
            Text(stringResource(R.string.nutrition_supplements_more, snapshot.todayDoses.size - MAX_ROWS), style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
        }
    }
}

private const val MAX_ROWS = 4
