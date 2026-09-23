package com.kevan.hangry.ui.breathing

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kevan.hangry.data.breathing.BreathingSessionState
import com.kevan.hangry.domain.model.BreathingPattern
import com.kevan.hangry.domain.model.BreathingStats
import com.kevan.hangry.ui.components.HangryCard
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens
import java.util.Locale

/** Compact home-page entry point: one tap on a pattern opens the session screen with it selected. */
@Composable
fun BreathingExercisesCard(
    stats: BreathingStats,
    session: BreathingSessionState,
    onOpenPattern: (BreathingPattern) -> Unit,
    onOpenSession: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalHangryTokens.current
    val accent = tokens.chartColors.sleep

    HangryCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Air,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Breathing Exercises",
                    style = MaterialTheme.typography.titleMedium,
                    color = tokens.textPrimary
                )
            }
            Text(
                text = if (stats.minutesToday > 0) "${stats.minutesToday} min today" else "Guided audio",
                style = MaterialTheme.typography.labelSmall,
                color = tokens.textMuted
            )
        }

        Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))

        if (session is BreathingSessionState.Active) {
            Surface(
                onClick = onOpenSession,
                shape = RoundedCornerShape(12.dp),
                color = accent.copy(alpha = 0.12f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(accent, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "${session.pattern.shortLabel} in progress · " +
                            (if (session.isPaused) "Paused" else session.position.phase.type.label),
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.textPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = String.format(
                            Locale.US, "%d:%02d", session.remainingSeconds / 60, session.remainingSeconds % 60
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = accent
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.s)
            ) {
                BreathingPattern.entries.forEach { pattern ->
                    Surface(
                        onClick = { onOpenPattern(pattern) },
                        shape = RoundedCornerShape(12.dp),
                        color = accent.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, accent.copy(alpha = 0.25f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = pattern.shortLabel,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = tokens.textPrimary,
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                            Text(
                                text = pattern.phases.joinToString("·") { "${it.seconds}" },
                                style = MaterialTheme.typography.labelSmall,
                                color = tokens.textMuted,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
