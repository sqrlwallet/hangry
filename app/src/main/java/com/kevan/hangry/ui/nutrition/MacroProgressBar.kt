package com.kevan.hangry.ui.nutrition

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kevan.hangry.domain.model.FiberGuide
import com.kevan.hangry.ui.components.HangryCard
import com.kevan.hangry.ui.components.HangryInfoIconButton
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
    modifier: Modifier = Modifier
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
            // Header Row: Consumed vs Target
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Daily Nutrition Target",
                        style = MaterialTheme.typography.titleMedium,
                        color = tokens.textPrimary
                    )
                    Text(
                        text = if (targetCalories != null) "$totalCalories of $targetCalories kcal"
                        else "$totalCalories kcal eaten · add your details in Settings for a target",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.textSecondary
                    )
                }

                if (targetCalories != null && targetCalories > 0) {
                    val percent = ((totalCalories.toDouble() / targetCalories) * 100).roundToInt()
                    Text(
                        text = "$percent%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (percent > 100) tokens.scoreColors.rebuild else tokens.chartColors.activeCalories
                    )
                }
            }

            Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))

            // Overall Calorie Bar
            LinearProgressIndicator(
                progress = { animCalorieProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = tokens.chartColors.activeCalories,
                trackColor = tokens.chartColors.activeCalories.copy(alpha = 0.18f),
            )

            Spacer(modifier = Modifier.height(HangryTokens.Spacing.m))

            // 3 Macro Nutrient Distribution Columns
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.s)
            ) {
                MacroPill(
                    label = "Protein",
                    currentG = proteinG,
                    goalG = proteinGoalG,
                    color = tokens.macroColors.protein,
                    modifier = Modifier.weight(1f)
                )
                MacroPill(
                    label = "Carbs",
                    currentG = carbsG,
                    goalG = carbsGoalG,
                    color = tokens.macroColors.carbs,
                    modifier = Modifier.weight(1f)
                )
                MacroPill(
                    label = "Fat",
                    currentG = fatG,
                    goalG = fatGoalG,
                    color = tokens.macroColors.fat,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))

            FiberRow(currentG = fiberG, goalG = fiberGoalG)
        }
    }
}

private val FIBER_INFO_SECTIONS = listOf(
    HangryInfoSection("Why fiber", FiberGuide.SUMMARY),
    HangryInfoSection("How to get more", FiberGuide.HOW_TO.joinToString("\n\n") { "• $it" })
)

/** Fiber against the day's target, with a one-line nudge on how to close the gap. */
@Composable
private fun FiberRow(currentG: Double, goalG: Double) {
    val tokens = LocalHangryTokens.current
    val color = tokens.macroColors.fiber
    val progress = if (goalG > 0) (currentG / goalG).toFloat().coerceIn(0f, 1f) else 0f
    val animProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 800),
        label = "anim_macro_fiber"
    )
    val remaining = (goalG - currentG).roundToInt()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(HangryTokens.CornerRadii.medium))
            .background(color.copy(alpha = 0.08f))
            .padding(start = 10.dp, top = 4.dp, bottom = 10.dp, end = 4.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Fiber",
                    style = MaterialTheme.typography.labelMedium,
                    color = color,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${currentG.roundToInt()}g",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = tokens.textPrimary
                )
                Text(
                    text = " / ${goalG.roundToInt()}g",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.textMuted
                )
                Spacer(modifier = Modifier.weight(1f))
                HangryInfoIconButton(title = "Fiber", sections = FIBER_INFO_SECTIONS, compact = true)
            }
            LinearProgressIndicator(
                progress = { animProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 6.dp)
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = color,
                trackColor = color.copy(alpha = 0.22f),
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = when {
                    remaining <= 0 -> "Fiber target hit - nice. Keep up the water too."
                    currentG < 1 -> "Beans, oats, berries, whole grains and veg are the easiest fiber sources."
                    else -> "${remaining}g to go - a cup of berries (~8g) or half a cup of lentils (~8g) gets you there."
                },
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textSecondary,
                modifier = Modifier.padding(end = 6.dp)
            )
        }
    }
}

@Composable
private fun MacroPill(
    label: String,
    currentG: Double,
    goalG: Double?,
    color: Color,
    modifier: Modifier = Modifier
) {
    val tokens = LocalHangryTokens.current
    val progress = if (goalG != null && goalG > 0) (currentG.toFloat() / goalG.toFloat()).coerceIn(0f, 1f) else 0f
    val animProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 800),
        label = "anim_macro_$label"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(HangryTokens.CornerRadii.medium))
            .background(color.copy(alpha = 0.08f))
            .padding(10.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = color,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = goalG?.let { "${it.roundToInt()}g" } ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.textMuted
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "${currentG.roundToInt()}g",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = tokens.textPrimary
            )

            Spacer(modifier = Modifier.height(6.dp))

            LinearProgressIndicator(
                progress = { animProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = color,
                trackColor = color.copy(alpha = 0.22f),
            )
        }
    }
}
