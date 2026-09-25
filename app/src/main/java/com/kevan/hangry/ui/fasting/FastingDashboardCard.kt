package com.kevan.hangry.ui.fasting

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.kevan.hangry.domain.model.FastingMath
import com.kevan.hangry.domain.model.FastingSnapshot
import com.kevan.hangry.domain.model.FastingStage
import com.kevan.hangry.ui.components.HangryCard
import com.kevan.hangry.ui.theme.LocalHangryTokens
import java.time.Duration

/** Today card for the fasting timer. Shows nothing unless the user has turned fasting on. */
@Composable
fun FastingDashboardCard(
    snapshot: FastingSnapshot,
    onStart: () -> Unit,
    onEnd: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!snapshot.enabled) return
    val tokens = LocalHangryTokens.current
    val now = rememberNow(periodMs = 30_000)
    val active = snapshot.active
    HangryCard(modifier = modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Timer, contentDescription = null, tint = FastingColor, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Fasting", style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary, modifier = Modifier.weight(1f))
            Text(
                if (snapshot.streak > 0) "🔥 ${snapshot.streak} day${if (snapshot.streak == 1) "" else "s"}" else snapshot.plan.label,
                style = MaterialTheme.typography.labelSmall,
                color = tokens.textMuted
            )
        }
        Spacer(Modifier.height(8.dp))
        if (active != null) {
            val reached = active.reachedGoal(now)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(FastingMath.formatDuration(active.elapsed(now)), style = MaterialTheme.typography.headlineSmall, color = tokens.textPrimary)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (reached) "goal reached ✓" else FastingMath.formatDuration(Duration.between(now, active.goalAt())) + " to go",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (reached) tokens.scoreColors.primed else tokens.textSecondary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            LinearProgressIndicator(
                progress = { active.progress(now) },
                color = FastingColor,
                trackColor = tokens.cardBorder,
                strokeCap = StrokeCap.Round,
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(FastingStage.at(active.elapsed(now)).title, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary, modifier = Modifier.weight(1f))
                // Ending early is confirmed on the Fasting screen.
                if (reached) Button(onClick = onEnd) { Text("End fast") } else OutlinedButton(onClick = onOpen) { Text("Open") }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    snapshot.lastFinished?.let { "Eating window · last fast " + FastingMath.formatDuration(it.elapsed()) }
                        ?: "Start a ${snapshot.targetHours}h fast when you finish eating",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = onStart) { Text("Start fast") }
            }
        }
    }
}
