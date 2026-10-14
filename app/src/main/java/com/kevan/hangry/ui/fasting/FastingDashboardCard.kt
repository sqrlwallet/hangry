package com.kevan.hangry.ui.fasting

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kevan.hangry.R
import com.kevan.hangry.domain.model.FastingMath
import com.kevan.hangry.domain.model.FastingSnapshot
import com.kevan.hangry.domain.model.FastingStage
import com.kevan.hangry.ui.components.HangryCard
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens
import com.kevan.hangry.ui.theme.SurfaceDark
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
    val isDark = tokens.cardBackground == SurfaceDark
    val haptic = LocalHapticFeedback.current
    val now = rememberNow(periodMs = 30_000)
    val active = snapshot.active

    val targetProgress = active?.progress(now) ?: 0f
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 800),
        label = "fasting_dashboard_progress"
    )

    HangryCard(modifier = modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Timer, contentDescription = null, tint = FastingColor, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(HangryTokens.Spacing.xs))
            Text(stringResource(R.string.nutrition_fasting_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = tokens.textPrimary, modifier = Modifier.weight(1f))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (snapshot.streak > 0) FastingColor.copy(alpha = if (isDark) 0.20f else 0.12f) else tokens.cardBackground,
                border = BorderStroke(1.dp, if (snapshot.streak > 0) FastingColor.copy(alpha = if (isDark) 0.40f else 0.28f) else tokens.cardBorder)
            ) {
                Text(
                    text = if (snapshot.streak > 0) fastingStreakText(snapshot.streak) else snapshot.plan.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (snapshot.streak > 0) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (snapshot.streak > 0) FastingColor else tokens.textMuted,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        if (active != null) {
            val reached = active.reachedGoal(now)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = FastingMath.formatDuration(active.elapsed(now)),
                    style = MaterialTheme.typography.headlineSmall,
                    color = tokens.textPrimary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (reached) stringResource(R.string.nutrition_fasting_goal_reached_lower) else stringResource(R.string.nutrition_fasting_to_go, FastingMath.formatDuration(Duration.between(now, active.goalAt()))),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (reached) tokens.scoreColors.primed else tokens.textSecondary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(FastingColor.copy(alpha = if (isDark) 0.18f else 0.12f))
            ) {
                if (animatedProgress > 0.001f) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction = animatedProgress.coerceIn(0f, 1f))
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        FastingColor.copy(alpha = 0.82f),
                                        FastingColor
                                    )
                                )
                            )
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(FastingStage.at(active.elapsed(now)).title, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary, modifier = Modifier.weight(1f))
                // Ending early is confirmed on the Fasting screen, which the whole card opens.
                if (reached) {
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onEnd()
                        }
                    ) {
                        Text(stringResource(R.string.nutrition_fasting_end_fast))
                    }
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    snapshot.lastFinished?.let { stringResource(R.string.nutrition_fasting_eating_window_last, FastingMath.formatDuration(it.elapsed())) }
                        ?: stringResource(R.string.nutrition_fasting_start_prompt, snapshot.targetHours),
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onStart()
                    }
                ) {
                    Text(stringResource(R.string.nutrition_fasting_start_fast))
                }
            }
        }
    }
}

/** "🔥 1 day" / "🔥 N days". */
@Composable
private fun fastingStreakText(streak: Int): String =
    if (streak == 1) stringResource(R.string.nutrition_fasting_streak_one)
    else stringResource(R.string.nutrition_fasting_streak_many, streak)
