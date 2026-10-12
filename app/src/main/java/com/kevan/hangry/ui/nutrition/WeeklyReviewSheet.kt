package com.kevan.hangry.ui.nutrition

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
            verticalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.section)
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
                    AveragesList(summary, state.calorieTarget)

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
                        if (state.aiReview != null) {
                            // Dash's review already covers these - keep them one tap away, not repeated.
                            ExpandableSection(title = stringResource(R.string.nutrition_weekly_by_numbers_count, state.tips.size)) {
                                state.tips.forEach { tip -> ReviewItem(title = tip.title, body = tip.body) }
                            }
                        } else {
                            SectionTitle(stringResource(R.string.nutrition_weekly_recommendations))
                            state.tips.forEach { tip -> ReviewCard(title = tip.title, body = tip.body) }
                        }
                    }

                    // Dash's own fiber plan replaces the generic guide when there is one.
                    if (state.aiReview?.fiberPlan.isNullOrBlank()) FiberGuideSection()
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

private enum class NutrientStatus { ON_TARGET, LOW, HIGH, UNDER_LIMIT, OVER_LIMIT }

/**
 * Where a daily average sits against its goal. [band] is how far either side still counts as
 * on target; minimums (protein, fiber) are never "high", limits (sodium) are pass/fail.
 */
private fun nutrientStatus(current: Double, goal: Double?, band: Double, kind: GoalKind): NutrientStatus? {
    if (goal == null || goal <= 0) return null
    val ratio = current / goal
    return when (kind) {
        GoalKind.LIMIT -> if (ratio <= 1.0) NutrientStatus.UNDER_LIMIT else NutrientStatus.OVER_LIMIT
        GoalKind.MINIMUM -> if (ratio >= 1 - band) NutrientStatus.ON_TARGET else NutrientStatus.LOW
        GoalKind.TARGET -> when {
            ratio < 1 - band -> NutrientStatus.LOW
            ratio > 1 + band -> NutrientStatus.HIGH
            else -> NutrientStatus.ON_TARGET
        }
    }
}

private enum class GoalKind { TARGET, MINIMUM, LIMIT }

/** Daily averages against target: one full-width row per nutrient, with a status at the end. */
@Composable
private fun AveragesList(summary: NutritionWeekSummary, calorieTarget: Int?) {
    val tokens = LocalHangryTokens.current
    val macros = NutritionTargets.macros(calorieTarget)
    val fiberTarget = NutritionTargets.fiberG(calorieTarget)
    val grams: @Composable (Double) -> String = { g -> stringResource(R.string.nutrition_grams, g.roundToInt()) }
    Surface(
        color = tokens.textSecondary.copy(alpha = 0.06f),
        shape = RoundedCornerShape(HangryTokens.CornerRadii.medium),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.nutrition_weekly_daily_average), style = MaterialTheme.typography.labelLarge, color = tokens.textSecondary)
            NutrientRow(
                label = stringResource(R.string.nutrition_field_calories),
                value = "${summary.avgCalories}",
                goal = calorieTarget?.let { stringResource(R.string.nutrition_weekly_kcal_value, it) },
                current = summary.avgCalories.toDouble(), goalValue = calorieTarget?.toDouble(),
                status = nutrientStatus(summary.avgCalories.toDouble(), calorieTarget?.toDouble(), 0.10, GoalKind.TARGET),
                color = tokens.macroColors.calories, emphasized = true
            )
            NutrientRow(
                label = stringResource(R.string.nutrition_macro_protein), value = grams(summary.avgProteinG),
                goal = macros?.let { grams(it.proteinG) }, current = summary.avgProteinG, goalValue = macros?.proteinG,
                status = nutrientStatus(summary.avgProteinG, macros?.proteinG, 0.10, GoalKind.MINIMUM),
                color = tokens.macroColors.protein
            )
            NutrientRow(
                label = stringResource(R.string.nutrition_macro_fiber), value = grams(summary.avgFiberG),
                goal = grams(fiberTarget), current = summary.avgFiberG, goalValue = fiberTarget,
                status = nutrientStatus(summary.avgFiberG, fiberTarget, 0.10, GoalKind.MINIMUM),
                color = tokens.macroColors.fiber
            )
            NutrientRow(
                label = stringResource(R.string.nutrition_macro_carbs), value = grams(summary.avgCarbsG),
                goal = macros?.let { grams(it.carbsG) }, current = summary.avgCarbsG, goalValue = macros?.carbsG,
                status = nutrientStatus(summary.avgCarbsG, macros?.carbsG, 0.20, GoalKind.TARGET),
                color = tokens.macroColors.carbs
            )
            NutrientRow(
                label = stringResource(R.string.nutrition_macro_fat), value = grams(summary.avgFatG),
                goal = macros?.let { grams(it.fatG) }, current = summary.avgFatG, goalValue = macros?.fatG,
                status = nutrientStatus(summary.avgFatG, macros?.fatG, 0.20, GoalKind.TARGET),
                color = tokens.macroColors.fat
            )
            NutrientRow(
                label = stringResource(R.string.nutrition_weekly_sodium),
                value = stringResource(R.string.nutrition_mg, summary.avgSodiumMg.roundToInt()),
                goal = stringResource(R.string.nutrition_mg, SODIUM_LIMIT_MG.roundToInt()),
                current = summary.avgSodiumMg, goalValue = SODIUM_LIMIT_MG,
                status = nutrientStatus(summary.avgSodiumMg, SODIUM_LIMIT_MG, 0.0, GoalKind.LIMIT),
                color = tokens.textSecondary
            )
        }
    }
}

private const val SODIUM_LIMIT_MG = 2300.0

@Composable
private fun NutrientRow(
    label: String,
    value: String,
    goal: String?,
    current: Double,
    goalValue: Double?,
    status: NutrientStatus?,
    color: Color,
    emphasized: Boolean = false
) {
    val tokens = LocalHangryTokens.current
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(color))
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, color = tokens.textPrimary, modifier = Modifier.weight(1f))
            Text(
                value,
                style = if (emphasized) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = tokens.textPrimary
            )
            Text(
                goal?.let { stringResource(R.string.nutrition_weekly_slash_goal, it) } ?: stringResource(R.string.nutrition_weekly_no_target),
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textMuted
            )
            if (status != null) {
                Spacer(Modifier.width(8.dp))
                StatusChip(status)
            }
        }
        if (goalValue != null && goalValue > 0) {
            LinearProgressIndicator(
                progress = { (current / goalValue).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(if (emphasized) 6.dp else 4.dp).clip(RoundedCornerShape(3.dp)),
                color = color,
                trackColor = color.copy(alpha = 0.18f),
                drawStopIndicator = {}
            )
        }
    }
}

@Composable
private fun StatusChip(status: NutrientStatus) {
    val colors = LocalHangryTokens.current.scoreColors
    val (text, fg, bg) = when (status) {
        NutrientStatus.ON_TARGET -> Triple(R.string.nutrition_weekly_status_on_target, colors.primed, colors.primedContainer)
        NutrientStatus.UNDER_LIMIT -> Triple(R.string.nutrition_weekly_status_under_limit, colors.primed, colors.primedContainer)
        NutrientStatus.LOW -> Triple(R.string.nutrition_weekly_status_low, colors.balanced, colors.balancedContainer)
        NutrientStatus.HIGH -> Triple(R.string.nutrition_weekly_status_high, colors.rebuild, colors.rebuildContainer)
        NutrientStatus.OVER_LIMIT -> Triple(R.string.nutrition_weekly_status_over_limit, colors.rebuild, colors.rebuildContainer)
    }
    Surface(color = bg, shape = RoundedCornerShape(50)) {
        Text(
            stringResource(text),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

/**
 * Dash's review, scannable: the headline and a short overview up top, then each
 * recommendation as a card that opens for the why and how. Wins, foods and the fiber plan
 * sit in sections that open on tap, so the sheet isn't one long wall of text.
 */
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
            var expanded by rememberSaveable { mutableStateOf(false) }
            var overflows by remember { mutableStateOf(false) }
            Text(
                review.overview,
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.textSecondary,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
                modifier = Modifier.animateContentSize().clickable(enabled = overflows || expanded) { expanded = !expanded }
            )
        }
        if (review.recommendations.isNotEmpty()) {
            SectionTitle(stringResource(R.string.nutrition_weekly_try_this_week))
            review.recommendations.forEachIndexed { index, rec ->
                RecommendationCard(number = index + 1, rec = rec, initiallyExpanded = index == 0)
            }
        }
        if (review.wins.isNotEmpty()) {
            ExpandableSection(title = stringResource(R.string.nutrition_weekly_whats_working_count, review.wins.size), accent = tokens.scoreColors.primed) {
                review.wins.forEach { win ->
                    Row {
                        Icon(Icons.Default.Check, contentDescription = null, tint = tokens.scoreColors.primed, modifier = Modifier.size(16.dp).padding(top = 2.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(win, style = MaterialTheme.typography.bodySmall, color = tokens.textPrimary)
                    }
                }
            }
        }
        if (review.foodsToAdd.isNotEmpty()) {
            ExpandableSection(title = stringResource(R.string.nutrition_weekly_foods_to_add_count, review.foodsToAdd.size), accent = tokens.macroColors.carbs) {
                review.foodsToAdd.forEach { food -> ReviewItem(title = food.food, body = food.why) }
            }
        }
        if (review.fiberPlan.isNotBlank()) {
            ExpandableSection(title = stringResource(R.string.nutrition_weekly_fiber_plan), accent = tokens.macroColors.fiber) {
                Text(review.fiberPlan, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
            }
        }
    }
}

/** A recommendation: its title always visible, the why and how a tap away. */
@Composable
private fun RecommendationCard(number: Int, rec: NutritionReview.Recommendation, initiallyExpanded: Boolean) {
    val tokens = LocalHangryTokens.current
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val hasDetail = rec.why.isNotBlank() || rec.how.isNotBlank()
    Surface(
        color = tokens.brandAccent.copy(alpha = 0.07f),
        shape = RoundedCornerShape(HangryTokens.CornerRadii.medium),
        modifier = Modifier.fillMaxWidth().animateContentSize().clickable(enabled = hasDetail) { expanded = !expanded }
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(22.dp).clip(RoundedCornerShape(11.dp)).background(tokens.brandAccent),
                    contentAlignment = Alignment.Center
                ) {
                    Text("$number", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                }
                Spacer(Modifier.width(10.dp))
                Text(rec.title, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (hasDetail) ExpandIcon(expanded)
            }
            if (expanded) {
                if (rec.why.isNotBlank()) Text(rec.why, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                if (rec.how.isNotBlank()) {
                    Text(stringResource(R.string.nutrition_weekly_how, rec.how), style = MaterialTheme.typography.bodySmall, color = tokens.textPrimary)
                }
            }
        }
    }
}

/** A titled section that opens on tap, with its contents hidden until then. */
@Composable
private fun ExpandableSection(title: String, accent: Color? = null, content: @Composable ColumnScope.() -> Unit) {
    val tokens = LocalHangryTokens.current
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    Surface(
        color = (accent ?: tokens.textSecondary).copy(alpha = 0.07f),
        shape = RoundedCornerShape(HangryTokens.CornerRadii.medium),
        modifier = Modifier.fillMaxWidth().animateContentSize()
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = tokens.textPrimary, modifier = Modifier.weight(1f))
                ExpandIcon(expanded)
            }
            if (expanded) {
                Column(
                    Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    content = content
                )
            }
        }
    }
}

@Composable
private fun ExpandIcon(expanded: Boolean) {
    Icon(
        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
        contentDescription = stringResource(if (expanded) R.string.nutrition_weekly_show_less else R.string.nutrition_weekly_show_more),
        tint = LocalHangryTokens.current.textMuted,
        modifier = Modifier.size(20.dp)
    )
}

/** A bold line with a short explanation under it, for lists inside a section. */
@Composable
private fun ReviewItem(title: String, body: String) {
    val tokens = LocalHangryTokens.current
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = tokens.textPrimary)
        if (body.isNotBlank()) Text(body, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
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
