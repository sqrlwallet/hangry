package com.kevan.hangry.ui.nutrition

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kevan.hangry.R
import com.kevan.hangry.domain.calculation.NutritionTargets
import com.kevan.hangry.domain.model.FiberGuide
import com.kevan.hangry.domain.model.NutritionReview
import com.kevan.hangry.domain.model.NutritionWeekSummary
import com.kevan.hangry.ui.coach.DashSpinner
import com.kevan.hangry.ui.components.DashEmptyScene
import com.kevan.hangry.ui.components.DashEmptyState
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val RANGE_FORMAT = DateTimeFormatter.ofPattern("MMM d")

/** The last week of eating: averages vs targets, the AI's recommendations, and how to eat more fiber. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeeklyReviewSheet(
    state: WeeklyReviewState,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onOpenAiSettings: () -> Unit
) {
    val tokens = LocalHangryTokens.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = HangryTokens.Spacing.m)
                .padding(bottom = HangryTokens.Spacing.xl)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.m)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.nutrition_info_weekly_title), style = MaterialTheme.typography.titleLarge, color = tokens.textPrimary)
                    state.summary?.let {
                        Text(
                            stringResource(R.string.nutrition_weekly_range, it.start.format(RANGE_FORMAT), it.end.format(RANGE_FORMAT), it.daysLogged),
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.textSecondary
                        )
                    }
                }
                if (state.aiAvailable && !state.isAiLoading && (state.summary?.daysLogged ?: 0) > 0) {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.nutrition_weekly_review_again), tint = tokens.textMuted)
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.nutrition_cd_close), tint = tokens.textMuted)
                }
            }

            val summary = state.summary
            when {
                state.isLoading || summary == null -> LoadingRow(stringResource(R.string.nutrition_weekly_crunching))
                summary.daysLogged == 0 -> DashEmptyState(
                    scene = DashEmptyScene.MEALS,
                    title = stringResource(R.string.nutrition_weekly_empty_title),
                    body = stringResource(R.string.nutrition_weekly_empty_body),
                    modifier = Modifier.padding(vertical = HangryTokens.Spacing.s)
                )
                else -> {
                    AveragesGrid(summary, state.calorieTarget)

                    if (state.aiAvailable) {
                        when {
                            state.isAiLoading -> LoadingRow(stringResource(R.string.nutrition_weekly_ai_loading))
                            state.aiReview != null -> AiReviewContent(state.aiReview)
                            state.aiError != null -> Column {
                                Text(state.aiError, style = MaterialTheme.typography.bodySmall, color = tokens.scoreColors.rebuild)
                                TextButton(onClick = onRefresh) { Text(stringResource(R.string.nutrition_try_again)) }
                            }
                        }
                    } else {
                        Surface(
                            color = tokens.brandAccentContainer,
                            shape = RoundedCornerShape(HangryTokens.CornerRadii.small),
                            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenAiSettings)
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = tokens.brandAccent, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    stringResource(R.string.nutrition_weekly_ai_off),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = tokens.textPrimary
                                )
                            }
                        }
                    }

                    if (state.tips.isNotEmpty()) {
                        SectionTitle(if (state.aiReview != null) stringResource(R.string.nutrition_weekly_by_numbers) else stringResource(R.string.nutrition_weekly_recommendations))
                        state.tips.forEach { tip -> ReviewCard(title = tip.title, body = tip.body) }
                    }

                    FiberGuideSection()
                }
            }
        }
    }
}

@Composable
private fun LoadingRow(text: String) {
    val tokens = LocalHangryTokens.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        DashSpinner(size = 40.dp, contentDescription = null)
        Spacer(Modifier.width(HangryTokens.Spacing.s))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = LocalHangryTokens.current.textPrimary)
}

/** Daily averages against target, one tile per nutrient. */
@Composable
private fun AveragesGrid(summary: NutritionWeekSummary, calorieTarget: Int?) {
    val tokens = LocalHangryTokens.current
    val macros = NutritionTargets.macros(calorieTarget)
    val fiberTarget = NutritionTargets.fiberG(calorieTarget)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.nutrition_weekly_daily_average), style = MaterialTheme.typography.labelLarge, color = tokens.textSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AverageTile(stringResource(R.string.nutrition_field_calories), "${summary.avgCalories}", calorieTarget?.let { stringResource(R.string.nutrition_weekly_of_kcal, it) } ?: stringResource(R.string.nutrition_kcal_unit),
                summary.avgCalories.toDouble(), calorieTarget?.toDouble(), tokens.macroColors.calories, Modifier.weight(1f))
            AverageTile(stringResource(R.string.nutrition_macro_protein), stringResource(R.string.nutrition_grams, summary.avgProteinG.roundToInt()), macros?.let { stringResource(R.string.nutrition_weekly_of_grams, it.proteinG.roundToInt()) } ?: "",
                summary.avgProteinG, macros?.proteinG, tokens.macroColors.protein, Modifier.weight(1f))
            AverageTile(stringResource(R.string.nutrition_macro_fiber), stringResource(R.string.nutrition_grams, summary.avgFiberG.roundToInt()), stringResource(R.string.nutrition_weekly_of_grams, fiberTarget.roundToInt()),
                summary.avgFiberG, fiberTarget, tokens.macroColors.fiber, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AverageTile(stringResource(R.string.nutrition_macro_carbs), stringResource(R.string.nutrition_grams, summary.avgCarbsG.roundToInt()), macros?.let { stringResource(R.string.nutrition_weekly_of_grams, it.carbsG.roundToInt()) } ?: "",
                summary.avgCarbsG, macros?.carbsG, tokens.macroColors.carbs, Modifier.weight(1f))
            AverageTile(stringResource(R.string.nutrition_macro_fat), stringResource(R.string.nutrition_grams, summary.avgFatG.roundToInt()), macros?.let { stringResource(R.string.nutrition_weekly_of_grams, it.fatG.roundToInt()) } ?: "",
                summary.avgFatG, macros?.fatG, tokens.macroColors.fat, Modifier.weight(1f))
            AverageTile(stringResource(R.string.nutrition_weekly_sodium), stringResource(R.string.nutrition_mg, summary.avgSodiumMg.roundToInt()), stringResource(R.string.nutrition_weekly_sodium_limit),
                summary.avgSodiumMg, 2300.0, tokens.textSecondary, Modifier.weight(1f))
        }
    }
}

@Composable
private fun AverageTile(
    label: String,
    value: String,
    caption: String,
    current: Double,
    goal: Double?,
    color: Color,
    modifier: Modifier = Modifier
) {
    val tokens = LocalHangryTokens.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(HangryTokens.CornerRadii.medium))
            .background(color.copy(alpha = 0.08f))
            .padding(10.dp)
    ) {
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.SemiBold)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = tokens.textPrimary)
            Text(caption, style = MaterialTheme.typography.labelSmall, color = tokens.textMuted, maxLines = 1)
            if (goal != null && goal > 0) {
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { (current / goal).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = color,
                    trackColor = color.copy(alpha = 0.22f)
                )
            }
        }
    }
}

@Composable
private fun AiReviewContent(review: NutritionReview) {
    val tokens = LocalHangryTokens.current
    Column(verticalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.s)) {
        if (review.headline.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = tokens.brandAccent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(review.headline, style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
            }
        }
        if (review.overview.isNotBlank()) {
            Text(review.overview, style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary)
        }
        if (review.wins.isNotEmpty()) {
            SectionTitle(stringResource(R.string.nutrition_weekly_whats_working))
            review.wins.forEach { Text(stringResource(R.string.nutrition_bullet_item, it), style = MaterialTheme.typography.bodyMedium, color = tokens.textPrimary) }
        }
        if (review.recommendations.isNotEmpty()) {
            SectionTitle(stringResource(R.string.nutrition_weekly_try_this_week))
            review.recommendations.forEach { rec ->
                ReviewCard(title = rec.title, body = rec.why, detail = rec.how)
            }
        }
        if (review.foodsToAdd.isNotEmpty()) {
            SectionTitle(stringResource(R.string.nutrition_weekly_foods_to_add))
            review.foodsToAdd.forEach { food ->
                Row {
                    Text("+ ", style = MaterialTheme.typography.bodyMedium, color = tokens.macroColors.carbs, fontWeight = FontWeight.Bold)
                    Column {
                        Text(food.food, style = MaterialTheme.typography.bodyMedium, color = tokens.textPrimary, fontWeight = FontWeight.SemiBold)
                        if (food.why.isNotBlank()) {
                            Text(food.why, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                        }
                    }
                }
            }
        }
        if (review.fiberPlan.isNotBlank()) {
            ReviewCard(title = stringResource(R.string.nutrition_weekly_fiber_plan), body = review.fiberPlan, accent = tokens.macroColors.fiber)
        }
    }
}

@Composable
private fun ReviewCard(title: String, body: String, detail: String? = null, accent: Color? = null) {
    val tokens = LocalHangryTokens.current
    val color = accent ?: tokens.brandAccent
    Surface(
        color = color.copy(alpha = 0.07f),
        shape = RoundedCornerShape(HangryTokens.CornerRadii.medium),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary, fontWeight = FontWeight.SemiBold)
            if (body.isNotBlank()) Text(body, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
            detail?.takeIf { it.isNotBlank() }?.let {
                Text(stringResource(R.string.nutrition_weekly_how, it), style = MaterialTheme.typography.bodySmall, color = tokens.textPrimary)
            }
        }
    }
}

@Composable
private fun FiberGuideSection() {
    val tokens = LocalHangryTokens.current
    var expanded by remember { mutableStateOf(false) }
    Column {
        TextButton(onClick = { expanded = !expanded }) {
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.nutrition_weekly_eat_more_fiber))
        }
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(horizontal = 4.dp)) {
                Text(FiberGuide.SUMMARY, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                FiberGuide.HOW_TO.forEach {
                    Text(stringResource(R.string.nutrition_bullet_item, it), style = MaterialTheme.typography.bodySmall, color = tokens.textPrimary)
                }
            }
        }
    }
}
