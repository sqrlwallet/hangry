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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
                    Text("Weekly review", style = MaterialTheme.typography.titleLarge, color = tokens.textPrimary)
                    state.summary?.let {
                        Text(
                            "${it.start.format(RANGE_FORMAT)} – ${it.end.format(RANGE_FORMAT)} · ${it.daysLogged} of 7 days logged",
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.textSecondary
                        )
                    }
                }
                if (state.aiAvailable && !state.isAiLoading && (state.summary?.daysLogged ?: 0) > 0) {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Review again", tint = tokens.textMuted)
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = tokens.textMuted)
                }
            }

            val summary = state.summary
            when {
                state.isLoading || summary == null -> LoadingRow("Crunching your week…")
                summary.daysLogged == 0 -> DashEmptyState(
                    scene = DashEmptyScene.MEALS,
                    title = "Nothing logged this week",
                    body = "Log your meals for a few days and come back - the review looks at your last 7 days of eating.",
                    modifier = Modifier.padding(vertical = HangryTokens.Spacing.s)
                )
                else -> {
                    AveragesGrid(summary, state.calorieTarget)

                    if (state.aiAvailable) {
                        when {
                            state.isAiLoading -> LoadingRow("Dash is reviewing your meals…")
                            state.aiReview != null -> AiReviewContent(state.aiReview)
                            state.aiError != null -> Column {
                                Text(state.aiError, style = MaterialTheme.typography.bodySmall, color = tokens.scoreColors.rebuild)
                                TextButton(onClick = onRefresh) { Text("Try again") }
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
                                    "Turn on AI Features for a personal review of what you ate, with foods to add and how.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = tokens.textPrimary
                                )
                            }
                        }
                    }

                    if (state.tips.isNotEmpty()) {
                        SectionTitle(if (state.aiReview != null) "By the numbers" else "Recommendations")
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
        Text("Daily average", style = MaterialTheme.typography.labelLarge, color = tokens.textSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AverageTile("Calories", "${summary.avgCalories}", calorieTarget?.let { "of $it kcal" } ?: "kcal",
                summary.avgCalories.toDouble(), calorieTarget?.toDouble(), tokens.macroColors.calories, Modifier.weight(1f))
            AverageTile("Protein", "${summary.avgProteinG.roundToInt()}g", macros?.let { "of ${it.proteinG.roundToInt()}g" } ?: "",
                summary.avgProteinG, macros?.proteinG, tokens.macroColors.protein, Modifier.weight(1f))
            AverageTile("Fiber", "${summary.avgFiberG.roundToInt()}g", "of ${fiberTarget.roundToInt()}g",
                summary.avgFiberG, fiberTarget, tokens.macroColors.fiber, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AverageTile("Carbs", "${summary.avgCarbsG.roundToInt()}g", macros?.let { "of ${it.carbsG.roundToInt()}g" } ?: "",
                summary.avgCarbsG, macros?.carbsG, tokens.macroColors.carbs, Modifier.weight(1f))
            AverageTile("Fat", "${summary.avgFatG.roundToInt()}g", macros?.let { "of ${it.fatG.roundToInt()}g" } ?: "",
                summary.avgFatG, macros?.fatG, tokens.macroColors.fat, Modifier.weight(1f))
            AverageTile("Sodium", "${summary.avgSodiumMg.roundToInt()}mg", "under 2300mg",
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
            SectionTitle("What's working")
            review.wins.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, color = tokens.textPrimary) }
        }
        if (review.recommendations.isNotEmpty()) {
            SectionTitle("Try this week")
            review.recommendations.forEach { rec ->
                ReviewCard(title = rec.title, body = rec.why, detail = rec.how)
            }
        }
        if (review.foodsToAdd.isNotEmpty()) {
            SectionTitle("Foods to add")
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
            ReviewCard(title = "Your fiber plan", body = review.fiberPlan, accent = tokens.macroColors.fiber)
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
                Text("How: $it", style = MaterialTheme.typography.bodySmall, color = tokens.textPrimary)
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
            Text("How to eat more fiber")
        }
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(horizontal = 4.dp)) {
                Text(FiberGuide.SUMMARY, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                FiberGuide.HOW_TO.forEach {
                    Text("• $it", style = MaterialTheme.typography.bodySmall, color = tokens.textPrimary)
                }
            }
        }
    }
}
