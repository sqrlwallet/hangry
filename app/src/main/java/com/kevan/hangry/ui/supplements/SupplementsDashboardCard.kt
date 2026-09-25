package com.kevan.hangry.ui.supplements

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.kevan.hangry.domain.model.SupplementsSnapshot
import com.kevan.hangry.ui.components.HangryCard
import com.kevan.hangry.ui.theme.LocalHangryTokens
import java.time.LocalTime

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
    HangryCard(modifier = modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Medication, contentDescription = null, tint = tokens.brandAccent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Supplements", style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary, modifier = Modifier.weight(1f))
            val doses = snapshot.todayDoses
            Text(
                when {
                    snapshot.supplements.isEmpty() -> "Add →"
                    doses.isEmpty() -> "Open →"
                    snapshot.takenToday == doses.size -> "All taken"
                    else -> "${snapshot.takenToday}/${doses.size} taken"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (doses.isNotEmpty() && snapshot.takenToday == doses.size) tokens.scoreColors.primed else tokens.textMuted
            )
        }
        if (snapshot.supplements.isEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Snap your daily supplements - AI reads the label so Dash knows what you take.",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textSecondary
            )
            return@HangryCard
        }
        if (snapshot.todayDoses.isEmpty()) {
            snapshot.active.takeIf { it.isNotEmpty() }?.let { active ->
                Spacer(Modifier.height(6.dp))
                Text(
                    "Taking " + active.joinToString(", ") { it.name },
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary,
                    maxLines = 2
                )
            }
            return@HangryCard
        }
        snapshot.todayDoses.take(MAX_ROWS).forEach { dose ->
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onToggle(dose.supplement.id, dose.time, !dose.taken) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = dose.taken, onCheckedChange = { onToggle(dose.supplement.id, dose.time, it) })
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
            Text("+${snapshot.todayDoses.size - MAX_ROWS} more", style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
        }
    }
}

private const val MAX_ROWS = 4
