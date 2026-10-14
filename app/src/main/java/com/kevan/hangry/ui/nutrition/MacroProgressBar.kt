package com.kevan.hangry.ui.nutrition

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kevan.hangry.ui.theme.SurfaceDark
import com.kevan.hangry.R
import com.kevan.hangry.domain.model.FiberGuide
import com.kevan.hangry.ui.components.HangryCard
import com.kevan.hangry.ui.components.HangryInfoDialog
import com.kevan.hangry.ui.components.HangryInfoSection
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens
import kotlin.math.roundToInt

@Composable
fun MacroProgressBar(
    totalCalories: Int,
    /** Null until there's enough profile and activity data for a real target - nothing is assumed. */
    targetCalories: Int?,
    proteinG: Double,
    proteinGoalG: Double?,
    carbsG: Double,
    carbsGoalG: Double?,
    fatG: Double,
    fatGoalG: Double?,
    fiberG: Double,
    fiberGoalG: Double,
    modifier: Modifier = Modifier,
    /** The day's water; null hides the row. */
    water: WaterProgress? = null,
    onAddWater: (Int) -> Unit = {}
) {
    val tokens = LocalHangryTokens.current

    val calorieProgress = if (targetCalories != null && targetCalories > 0) {
        (totalCalories.toFloat() / targetCalories).coerceIn(0f, 1f)
    } else 0f

    val animCalorieProgress by animateFloatAsState(
        targetValue = calorieProgress,
        animationSpec = tween(durationMillis = 800),
        label = "anim_macro_calories"
    )

    HangryCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header: what's eaten against the target, with the percent on the right.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                if (targetCalories != null) {
                    Text(
                        text = totalCalories.toString(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = tokens.textPrimary
                    )
                    Text(
                        text = stringResource(R.string.nutrition_kcal_of_target_short, targetCalories),
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.textSecondary,
                        modifier = Modifier.padding(start = 4.dp, bottom = 3.dp)
                    )
                } else {
                    Text(
                        text = stringResource(R.string.nutrition_kcal_eaten_no_target, totalCalories),
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.textSecondary
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                if (targetCalories != null && targetCalories > 0) {
                    val percent = ((totalCalories.toDouble() / targetCalories) * 100).roundToInt()
                    Text(
                        text = stringResource(R.string.nutrition_percent, percent),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (percent > 100) tokens.scoreColors.rebuild else tokens.chartColors.activeCalories,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
            }

            val isDark = tokens.cardBackground == SurfaceDark
            Spacer(modifier = Modifier.height(6.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isDark) tokens.cardBorder.copy(alpha = 0.5f) else tokens.cardBorder.copy(alpha = 0.7f))
            ) {
                if (animCalorieProgress > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = animCalorieProgress.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        tokens.chartColors.activeCalories,
                                        tokens.brandAccent
                                    )
                                )
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(HangryTokens.Spacing.s + 4.dp))

            // Protein, carbs, fat and fiber side by side as one compact grid.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.s)
            ) {
                MacroStat(
                    label = stringResource(R.string.nutrition_macro_protein),
                    currentG = proteinG,
                    goalG = proteinGoalG,
                    color = tokens.macroColors.protein,
                    modifier = Modifier.weight(1f)
                )
                MacroStat(
                    label = stringResource(R.string.nutrition_macro_carbs),
                    currentG = carbsG,
                    goalG = carbsGoalG,
                    color = tokens.macroColors.carbs,
                    modifier = Modifier.weight(1f)
                )
                MacroStat(
                    label = stringResource(R.string.nutrition_macro_fat),
                    currentG = fatG,
                    goalG = fatGoalG,
                    color = tokens.macroColors.fat,
                    modifier = Modifier.weight(1f)
                )
                FiberStat(currentG = fiberG, goalG = fiberGoalG, modifier = Modifier.weight(1f))
            }

            if (water != null) {
                Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))
                WaterRow(water = water, onAdd = onAddWater)
            }
        }
    }
}

/** Fiber's cell also carries how to close today's gap, one tap away instead of always on screen. */
@Composable
private fun FiberStat(currentG: Double, goalG: Double, modifier: Modifier = Modifier) {
    val tokens = LocalHangryTokens.current
    var showInfo by remember { mutableStateOf(false) }
    val remaining = (goalG - currentG).roundToInt()
    val title = stringResource(R.string.nutrition_macro_fiber)

    MacroStat(
        label = title,
        currentG = currentG,
        goalG = goalG,
        color = tokens.macroColors.fiber,
        showInfoIcon = true,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClickLabel = stringResource(R.string.metrics_components_about_title, title)) { showInfo = true }
    )

    if (showInfo) {
        HangryInfoDialog(
            title = title,
            sections = listOf(
                HangryInfoSection(
                    stringResource(R.string.nutrition_fiber_info_today),
                    when {
                        remaining <= 0 -> stringResource(R.string.nutrition_fiber_hit)
                        currentG < 1 -> stringResource(R.string.nutrition_fiber_none)
                        else -> stringResource(R.string.nutrition_fiber_remaining, remaining)
                    }
                ),
                HangryInfoSection(stringResource(R.string.nutrition_fiber_info_why), FiberGuide.SUMMARY),
                HangryInfoSection(stringResource(R.string.nutrition_fiber_info_how), FiberGuide.HOW_TO.joinToString("\n\n") { "• $it" })
            ),
            onDismiss = { showInfo = false }
        )
    }
}

@Composable
private fun MacroStat(
    label: String,
    currentG: Double,
    goalG: Double?,
    color: Color,
    modifier: Modifier = Modifier,
    showInfoIcon: Boolean = false
) {
    val tokens = LocalHangryTokens.current
    val progress = if (goalG != null && goalG > 0) (currentG.toFloat() / goalG.toFloat()).coerceIn(0f, 1f) else 0f
    val animProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 800),
        label = "anim_macro_$label"
    )

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = color,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            if (showInfoIcon) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = tokens.textMuted,
                    modifier = Modifier.padding(start = 3.dp).size(13.dp)
                )
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = stringResource(R.string.nutrition_grams, currentG.roundToInt()),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = tokens.textPrimary,
                maxLines = 1
            )
            if (goalG != null) {
                Text(
                    text = stringResource(R.string.nutrition_grams_of_goal_short, goalG.roundToInt()),
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.textMuted,
                    maxLines = 1,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }
        val isDark = tokens.cardBackground == SurfaceDark
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color.copy(alpha = if (isDark) 0.18f else 0.14f))
        ) {
            if (animProgress > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = animProgress.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(color)
                )
            }
        }
    }
}
