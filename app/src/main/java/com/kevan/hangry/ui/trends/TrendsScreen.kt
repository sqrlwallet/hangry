package com.kevan.hangry.ui.trends

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kevan.hangry.data.local.dao.WeightDao
import com.kevan.hangry.data.local.entity.DailyHealthSummaryEntity
import com.kevan.hangry.data.local.entity.ExerciseSessionEntity
import com.kevan.hangry.data.local.entity.FoodLogEntity
import com.kevan.hangry.data.local.entity.RecoveryScoreEntity
import com.kevan.hangry.domain.repository.DailySummaryRepository
import com.kevan.hangry.domain.repository.FoodLogRepository
import com.kevan.hangry.domain.repository.WorkoutRepository
import com.kevan.hangry.ui.components.DayDetailSheet
import com.kevan.hangry.ui.components.HangryCard
import com.kevan.hangry.ui.components.HangryInteractiveTrendChart
import com.kevan.hangry.ui.components.TrendPoint
import com.kevan.hangry.ui.theme.EmberAccent
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

enum class TrendTimeframe(val label: String, val days: Long?) {
    DAYS_7("7D", 7),
    DAYS_14("14D", 14),
    DAYS_30("30D", 30),
    DAYS_90("90D", 90),
    ALL_TIME("All", null)
}

enum class TrendMetricCategory(
    val label: String,
    val icon: ImageVector,
    val unit: String
) {
    RECOVERY("Recovery", Icons.AutoMirrored.Filled.TrendingUp, "%"),
    STRAIN("Strain", Icons.Default.LocalFireDepartment, ""),
    SLEEP("Sleep", Icons.Default.Bedtime, "h"),
    HRV("HRV", Icons.Default.Favorite, "ms"),
    RHR("RHR", Icons.Default.HeartBroken, "bpm"),
    STEPS("Steps", Icons.AutoMirrored.Filled.DirectionsRun, "steps"),
    ACTIVE_CALORIES("Active Burn", Icons.Default.Whatshot, "kcal"),
    NUTRITION_CALORIES("Calories In", Icons.Default.Restaurant, "kcal"),
    WEIGHT("Weight", Icons.Default.MonitorWeight, "kg")
}

private enum class HistoryFilter(val label: String) {
    ALL("All Days"),
    WORKOUTS("Workouts"),
    PRIMED("Primed (67%+)"),
    REBUILD("Rebuild (<34%)"),
    NUTRITION("Has Meals")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrendsScreen(
    dailySummaryRepository: DailySummaryRepository,
    weightDao: WeightDao,
    workoutRepository: WorkoutRepository,
    foodLogRepository: FoodLogRepository,
    onNavigateBack: () -> Unit,
    onNavigateToDashboardForDate: (LocalDate) -> Unit = {},
    onNavigateToNutritionForDate: (LocalDate) -> Unit = {},
    onNavigateToBodyFatCalculator: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val tokens = LocalHangryTokens.current
    val haptic = LocalHapticFeedback.current
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }

    var selectedTimeframe by remember { mutableStateOf(TrendTimeframe.DAYS_30) }
    var selectedMetric by remember { mutableStateOf(TrendMetricCategory.RECOVERY) }
    var historyFilter by remember { mutableStateOf(HistoryFilter.ALL) }

    var inspectingDate by remember { mutableStateOf<LocalDate?>(null) }
    var showJumpDatePicker by remember { mutableStateOf(false) }

    // Start date for queries
    val queryStartDate = remember(selectedTimeframe) {
        val days = selectedTimeframe.days
        if (days != null) today.minusDays(days) else LocalDate.of(2020, 1, 1)
    }

    // Biometric data flows for selected timeframe
    val scoresFlow = remember(queryStartDate) {
        dailySummaryRepository.getScoresBetween(queryStartDate, today)
    }
    val summariesFlow = remember(queryStartDate) {
        dailySummaryRepository.getSummariesBetween(queryStartDate, today)
    }
    val foodLogsFlow = remember(queryStartDate) {
        foodLogRepository.getBetween(queryStartDate, today)
    }

    val scores by scoresFlow.collectAsState(initial = emptyList())
    val summaries by summariesFlow.collectAsState(initial = emptyList())
    val foodLogs by foodLogsFlow.collectAsState(initial = emptyList())
    val allWorkouts by workoutRepository.getAllSessions().collectAsState(initial = emptyList())
    val allWeights by weightDao.getAllWeights().collectAsState(initial = emptyList())

    // Weight tracking metrics
    val latestWeight by weightDao.getLatestWeight().collectAsState(initial = null)
    val rollingAvgWeight by weightDao.getRollingAverageWeight().collectAsState(initial = null)

    // Weekly recap: always last 7 days vs the 7 days before that
    val thisWeekScores by remember { dailySummaryRepository.getScoresBetween(today.minusDays(6), today) }
        .collectAsState(initial = emptyList())
    val lastWeekScores by remember { dailySummaryRepository.getScoresBetween(today.minusDays(13), today.minusDays(7)) }
        .collectAsState(initial = emptyList())
    val thisWeekSummaries by remember { dailySummaryRepository.getSummariesBetween(today.minusDays(6), today) }
        .collectAsState(initial = emptyList())
    val lastWeekSummaries by remember { dailySummaryRepository.getSummariesBetween(today.minusDays(13), today.minusDays(7)) }
        .collectAsState(initial = emptyList())

    // Map workouts by date
    val workoutsByDate = remember(allWorkouts) {
        allWorkouts.groupBy { it.startTime.atZone(zone).toLocalDate() }
    }
    // Map food logs by date
    val foodLogsByDate = remember(foodLogs) {
        foodLogs.groupBy { it.date }
    }
    // Map weights by date
    val weightsByDate = remember(allWeights) {
        allWeights.associateBy { it.timestamp.atZone(zone).toLocalDate() }
    }

    // Chronological date list
    val scoreByDate = remember(scores) { scores.associateBy { it.date } }
    val summaryByDate = remember(summaries) { summaries.associateBy { it.date } }

    val chronologicalDates = remember(scores, summaries, foodLogs, allWeights) {
        (scores.map { it.date } + summaries.map { it.date } + foodLogs.map { it.date } + allWeights.map { it.timestamp.atZone(zone).toLocalDate() })
            .filter { !it.isBefore(queryStartDate) && !it.isAfter(today) }
            .distinct()
            .sorted()
    }

    // Generate trend points for currently selected metric
    val trendPoints = remember(selectedMetric, chronologicalDates, scoreByDate, summaryByDate, foodLogsByDate, weightsByDate) {
        chronologicalDates.map { date ->
            val value = when (selectedMetric) {
                TrendMetricCategory.RECOVERY -> scoreByDate[date]?.score?.toDouble()
                TrendMetricCategory.STRAIN -> summaryByDate[date]?.dayStrain
                TrendMetricCategory.SLEEP -> summaryByDate[date]?.sleepDurationMinutes?.let { it / 60.0 }
                TrendMetricCategory.HRV -> summaryByDate[date]?.hrvRmssd
                TrendMetricCategory.RHR -> summaryByDate[date]?.restingHeartRate
                TrendMetricCategory.STEPS -> summaryByDate[date]?.steps?.toDouble()
                TrendMetricCategory.ACTIVE_CALORIES -> summaryByDate[date]?.activeCalories
                TrendMetricCategory.NUTRITION_CALORIES -> foodLogsByDate[date]?.sumOf { it.calories }?.toDouble()
                TrendMetricCategory.WEIGHT -> weightsByDate[date]?.weightKg
            }
            TrendPoint(date = date, value = value)
        }
    }

    val metricColor = when (selectedMetric) {
        TrendMetricCategory.RECOVERY -> tokens.scoreColors.primed
        TrendMetricCategory.STRAIN -> tokens.chartColors.trainingLoad
        TrendMetricCategory.SLEEP -> tokens.chartColors.sleep
        TrendMetricCategory.HRV -> tokens.chartColors.hrv
        TrendMetricCategory.RHR -> tokens.chartColors.restingHeartRate
        TrendMetricCategory.STEPS -> tokens.chartColors.steps
        TrendMetricCategory.ACTIVE_CALORIES -> tokens.chartColors.activeCalories
        TrendMetricCategory.NUTRITION_CALORIES -> EmberAccent
        TrendMetricCategory.WEIGHT -> tokens.chartColors.sleep
    }

    val formatMetricValue: (Double) -> String = { v ->
        when (selectedMetric) {
            TrendMetricCategory.RECOVERY -> "${v.toInt()}"
            TrendMetricCategory.STRAIN -> String.format(Locale.US, "%.1f", v)
            TrendMetricCategory.SLEEP -> {
                val totalMin = (v * 60).toInt()
                "${totalMin / 60}h ${totalMin % 60}m"
            }
            TrendMetricCategory.HRV -> "${v.toInt()}"
            TrendMetricCategory.RHR -> "${v.toInt()}"
            TrendMetricCategory.STEPS -> String.format(Locale.US, "%,d", v.toLong())
            TrendMetricCategory.ACTIVE_CALORIES -> "${v.toInt()}"
            TrendMetricCategory.NUTRITION_CALORIES -> "${v.toInt()}"
            TrendMetricCategory.WEIGHT -> String.format(Locale.US, "%.1f", v)
        }
    }

    // Filtered history list
    val allHistoryDates = remember(scores, summaries, foodLogs, allWorkouts) {
        (scores.map { it.date } + summaries.map { it.date } + foodLogs.map { it.date } + allWorkouts.map { it.startTime.atZone(zone).toLocalDate() })
            .filter { !it.isBefore(queryStartDate) && !it.isAfter(today) }
            .distinct()
            .sortedDescending()
    }

    val filteredHistoryDates = remember(allHistoryDates, historyFilter, scoreByDate, workoutsByDate, foodLogsByDate) {
        when (historyFilter) {
            HistoryFilter.ALL -> allHistoryDates
            HistoryFilter.WORKOUTS -> allHistoryDates.filter { (workoutsByDate[it]?.size ?: 0) > 0 }
            HistoryFilter.PRIMED -> allHistoryDates.filter { (scoreByDate[it]?.score ?: 0) >= 67 }
            HistoryFilter.REBUILD -> allHistoryDates.filter {
                val sc = scoreByDate[it]?.score
                sc != null && sc < 34
            }
            HistoryFilter.NUTRITION -> allHistoryDates.filter { (foodLogsByDate[it]?.size ?: 0) > 0 }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Wellness Trends & Past Data") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showJumpDatePicker = true }) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = "Jump to Date",
                            tint = tokens.textSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .padding(horizontal = HangryTokens.Spacing.m),
            contentPadding = PaddingValues(top = HangryTokens.Spacing.s, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.m)
        ) {
            // 1. Timeframe Segmented Switcher
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(tokens.cardBackground)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TrendTimeframe.values().forEach { timeframe ->
                        val isSelected = timeframe == selectedTimeframe
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) EmberAccent.copy(alpha = 0.18f) else Color.Transparent,
                            border = if (isSelected) BorderStroke(1.dp, EmberAccent) else null,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 40.dp)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedTimeframe = timeframe
                                }
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = timeframe.label,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (isSelected) EmberAccent else tokens.textSecondary
                                )
                            }
                        }
                    }
                }
            }

            // 2. Interactive Metric Trend Explorer Card
            item {
                HangryCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = selectedMetric.icon,
                                contentDescription = null,
                                tint = metricColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Metric Explorer",
                                style = MaterialTheme.typography.titleMedium,
                                color = tokens.textPrimary
                            )
                        }

                        Surface(
                            color = tokens.cardBorder,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "${chronologicalDates.size} Days Logged",
                                style = MaterialTheme.typography.labelSmall,
                                color = tokens.textSecondary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Metric Chip Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TrendMetricCategory.values().forEach { metric ->
                            val isChosen = metric == selectedMetric
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (isChosen) EmberAccent.copy(alpha = 0.18f) else tokens.cardBorder.copy(alpha = 0.4f),
                                border = if (isChosen) BorderStroke(1.dp, EmberAccent) else null,
                                modifier = Modifier
                                    .height(34.dp)
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        selectedMetric = metric
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = metric.icon,
                                        contentDescription = null,
                                        tint = if (isChosen) EmberAccent else tokens.textSecondary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = metric.label,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Medium
                                        ),
                                        color = if (isChosen) EmberAccent else tokens.textSecondary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(HangryTokens.Spacing.m))

                    // Interactive Line Chart with Scrubber
                    HangryInteractiveTrendChart(
                        points = trendPoints,
                        color = metricColor,
                        unit = selectedMetric.unit,
                        formatValue = formatMetricValue,
                        onPointClicked = { tappedDate ->
                            inspectingDate = tappedDate
                        }
                    )

                    Spacer(modifier = Modifier.height(HangryTokens.Spacing.m))

                    // Highlights: Period Average, Peak Day, Lowest Day, Trajectory
                    val validPoints = trendPoints.filter { it.value != null }
                    if (validPoints.isNotEmpty()) {
                        val peak = validPoints.maxByOrNull { it.value!! }
                        val low = validPoints.minByOrNull { it.value!! }
                        val avg = validPoints.mapNotNull { it.value }.average()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            HighlightPill(
                                label = "Period Avg",
                                value = formatMetricValue(avg),
                                color = metricColor,
                                modifier = Modifier.weight(1f)
                            )
                            if (peak != null && peak.value != null) {
                                HighlightPill(
                                    label = "Peak (${peak.date.format(DateTimeFormatter.ofPattern("MMM d"))})",
                                    value = formatMetricValue(peak.value),
                                    color = tokens.scoreColors.primed,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (low != null && low.value != null) {
                                HighlightPill(
                                    label = "Low (${low.date.format(DateTimeFormatter.ofPattern("MMM d"))})",
                                    value = formatMetricValue(low.value),
                                    color = tokens.textMuted,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            // 3. Body Weight Tracking & Trend Card
            item {
                HangryCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.MonitorWeight,
                                contentDescription = null,
                                tint = tokens.chartColors.sleep,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Body Weight & Composition",
                                style = MaterialTheme.typography.titleMedium,
                                color = tokens.textPrimary
                            )
                        }

                        if (latestWeight != null) {
                            Surface(
                                color = tokens.cardBorder,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "HEALTH CONNECT",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = tokens.textSecondary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(HangryTokens.Spacing.m))

                    val weightKg = latestWeight?.weightKg
                    if (weightKg != null) {
                        val weightLbs = weightKg * 2.20462
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Column {
                                Text(
                                    text = String.format(Locale.US, "%.1f kg", weightKg),
                                    style = MaterialTheme.typography.displayMedium,
                                    color = tokens.textPrimary
                                )
                                Text(
                                    text = String.format(Locale.US, "≈ %.1f lbs", weightLbs),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = tokens.textMuted
                                )
                            }

                            if (rollingAvgWeight != null) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = String.format(Locale.US, "%.1f kg", rollingAvgWeight),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = tokens.chartColors.sleep
                                    )
                                    Text(
                                        text = "7-Day Moving Avg",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = tokens.textSecondary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))

                        val delta = rollingAvgWeight?.let { weightKg - it }
                        val trendNote = when {
                            delta == null -> "Stable baseline"
                            abs(delta) < 0.3 -> "Stable, within normal variance."
                            delta > 0 -> String.format(Locale.US, "+%.1f kg vs 7-day average.", delta)
                            else -> String.format(Locale.US, "%.1f kg vs 7-day average.", delta)
                        }

                        Text(
                            text = trendNote,
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.textSecondary
                        )

                        // Weight chart if multiple weigh-ins exist
                        val weightPoints = remember(allWeights, queryStartDate) {
                            allWeights
                                .filter {
                                    val wDate = it.timestamp.atZone(zone).toLocalDate()
                                    !wDate.isBefore(queryStartDate) && !wDate.isAfter(today)
                                }
                                .sortedBy { it.timestamp }
                                .map {
                                    TrendPoint(
                                        date = it.timestamp.atZone(zone).toLocalDate(),
                                        value = it.weightKg
                                    )
                                }
                        }

                        if (weightPoints.size >= 2) {
                            Spacer(modifier = Modifier.height(HangryTokens.Spacing.m))
                            HorizontalDivider(color = tokens.cardBorder.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))

                            val firstWeight = weightPoints.first().value ?: weightKg
                            val netChange = weightKg - firstWeight
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Weight Trajectory",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = tokens.textMuted
                                )
                                Text(
                                    text = String.format(Locale.US, "%s%.1f kg in %s", if (netChange > 0) "+" else "", netChange, selectedTimeframe.label),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (netChange <= 0) tokens.scoreColors.primed else tokens.scoreColors.rebuild
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            HangryInteractiveTrendChart(
                                points = weightPoints,
                                color = tokens.chartColors.sleep,
                                unit = "kg",
                                formatValue = { String.format(Locale.US, "%.1f", it) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    FilledTonalButton(
                        onClick = onNavigateToBodyFatCalculator,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.AccessibilityNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("AI Body Fat & Composition Calculator")
                    }
                }
            }

            // 4. Nutrition & Macro Trends Card
            item {
                HangryCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Restaurant,
                                contentDescription = null,
                                tint = EmberAccent,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Nutrition & Macro Trends",
                                style = MaterialTheme.typography.titleMedium,
                                color = tokens.textPrimary
                            )
                        }

                        Text(
                            text = "${foodLogsByDate.keys.size} Days Tracked",
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.textMuted
                        )
                    }

                    Spacer(modifier = Modifier.height(HangryTokens.Spacing.m))

                    if (foodLogs.isNotEmpty()) {
                        val daysWithMeals = foodLogsByDate.keys.size.coerceAtLeast(1)
                        val totalCalories = foodLogs.sumOf { it.calories }
                        val avgCaloriesPerDay = totalCalories / daysWithMeals
                        val avgProtein = foodLogs.sumOf { it.proteinG } / daysWithMeals
                        val avgCarbs = foodLogs.sumOf { it.carbsG } / daysWithMeals
                        val avgFat = foodLogs.sumOf { it.fatG } / daysWithMeals

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            HighlightPill(
                                label = "Avg Intake",
                                value = "$avgCaloriesPerDay kcal",
                                color = EmberAccent,
                                modifier = Modifier.weight(1f)
                            )
                            HighlightPill(
                                label = "Avg Protein",
                                value = "${avgProtein.toInt()}g",
                                color = tokens.scoreColors.primed,
                                modifier = Modifier.weight(1f)
                            )
                            HighlightPill(
                                label = "Avg Carbs",
                                value = "${avgCarbs.toInt()}g",
                                color = tokens.scoreColors.balanced,
                                modifier = Modifier.weight(1f)
                            )
                            HighlightPill(
                                label = "Avg Fat",
                                value = "${avgFat.toInt()}g",
                                color = tokens.chartColors.sleep,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))

                        val totalMacroGrams = (avgProtein + avgCarbs + avgFat).coerceAtLeast(1.0)
                        val proteinPct = ((avgProtein / totalMacroGrams) * 100).toInt()
                        val carbsPct = ((avgCarbs / totalMacroGrams) * 100).toInt()
                        val fatPct = 100 - proteinPct - carbsPct

                        Text(
                            text = "Macro Split: $proteinPct% Protein · $carbsPct% Carbs · $fatPct% Fat",
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.textSecondary
                        )
                    } else {
                        Text(
                            text = "No food logged in this timeframe. Log meals using the camera or text prompt to track macro trends.",
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.textSecondary
                        )
                    }
                }
            }

            // 5. Weekly Performance Recap (Last 7 Days vs Prior 7 Days)
            item {
                HangryCard {
                    Text(
                        text = "Weekly Performance Recap",
                        style = MaterialTheme.typography.titleMedium,
                        color = tokens.textPrimary
                    )
                    Spacer(modifier = Modifier.height(HangryTokens.Spacing.m))

                    val thisAvgRecovery = thisWeekScores.mapNotNull { it.score?.toDouble() }.averageOrNull()
                    val lastAvgRecovery = lastWeekScores.mapNotNull { it.score?.toDouble() }.averageOrNull()
                    val thisAvgStrain = thisWeekSummaries.mapNotNull { it.dayStrain }.averageOrNull()
                    val lastAvgStrain = lastWeekSummaries.mapNotNull { it.dayStrain }.averageOrNull()
                    val thisAvgSleep = thisWeekSummaries.mapNotNull { it.sleepDurationMinutes?.toDouble() }.averageOrNull()
                    val lastAvgSleep = lastWeekSummaries.mapNotNull { it.sleepDurationMinutes?.toDouble() }.averageOrNull()
                    val thisAvgSteps = thisWeekSummaries.mapNotNull { it.steps?.toDouble() }.averageOrNull()
                    val lastAvgSteps = lastWeekSummaries.mapNotNull { it.steps?.toDouble() }.averageOrNull()

                    WeeklyRecapRow(
                        label = "Avg Recovery",
                        thisWeek = thisAvgRecovery,
                        lastWeek = lastAvgRecovery,
                        color = tokens.scoreColors.primed,
                        format = { "${it.toInt()}%" }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = tokens.cardBorder)
                    WeeklyRecapRow(
                        label = "Avg Daily Strain",
                        thisWeek = thisAvgStrain,
                        lastWeek = lastAvgStrain,
                        color = tokens.chartColors.trainingLoad,
                        format = { String.format(Locale.US, "%.1f", it) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = tokens.cardBorder)
                    WeeklyRecapRow(
                        label = "Avg Sleep",
                        thisWeek = thisAvgSleep,
                        lastWeek = lastAvgSleep,
                        color = tokens.chartColors.sleep,
                        format = { "${(it / 60).toInt()}h ${(it % 60).toInt()}m" },
                        deltaFormat = { "${it.toInt()}m" }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = tokens.cardBorder)
                    WeeklyRecapRow(
                        label = "Avg Daily Steps",
                        thisWeek = thisAvgSteps,
                        lastWeek = lastAvgSteps,
                        color = tokens.chartColors.steps,
                        format = { String.format(Locale.US, "%,d", it.toLong()) }
                    )
                }
            }

            // 6. Past Data & Daily Biometric History Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Daily Biometric History",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = tokens.textPrimary
                        )
                        Text(
                            text = "Tap any day to inspect full telemetry or open in dashboard",
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.textMuted
                        )
                    }

                    Surface(
                        onClick = { showJumpDatePicker = true },
                        shape = RoundedCornerShape(10.dp),
                        color = tokens.cardBorder.copy(alpha = 0.6f),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = EmberAccent,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Pick Date",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = tokens.textPrimary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // History Filter Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HistoryFilter.values().forEach { filter ->
                        val isSelected = filter == historyFilter
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) EmberAccent.copy(alpha = 0.18f) else tokens.cardBorder.copy(alpha = 0.4f),
                            border = if (isSelected) BorderStroke(1.dp, EmberAccent) else null,
                            modifier = Modifier
                                .height(30.dp)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    historyFilter = filter
                                }
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = filter.label,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    color = if (isSelected) EmberAccent else tokens.textSecondary
                                )
                            }
                        }
                    }
                }
            }

            if (filteredHistoryDates.isEmpty()) {
                item {
                    HangryCard(
                        cornerRadius = HangryTokens.CornerRadii.large,
                        contentPadding = HangryTokens.Spacing.m
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(tokens.chartColors.hrv.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                    contentDescription = null,
                                    tint = tokens.chartColors.hrv,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "No records matching this filter",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = tokens.textPrimary
                                )
                                Text(
                                    text = "Sync Health Connect daily to build rolling baseline records.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = tokens.textMuted
                                )
                            }
                        }
                    }
                }
            } else {
                items(filteredHistoryDates, key = { it }) { dateItem ->
                    val matchingScore = scoreByDate[dateItem]
                    val matchingSummary = summaryByDate[dateItem]
                    val matchingWorkouts = workoutsByDate[dateItem] ?: emptyList()
                    val matchingFood = foodLogsByDate[dateItem] ?: emptyList()

                    val trainingLoad = matchingSummary?.dailyTrainingLoad ?: 0.0
                    val scoreVal = matchingScore?.score
                    val scoreColor = when {
                        scoreVal == null -> tokens.textMuted
                        scoreVal >= 67 -> tokens.scoreColors.primed
                        scoreVal >= 34 -> tokens.scoreColors.balanced
                        else -> tokens.scoreColors.rebuild
                    }

                    HangryCard(
                        modifier = Modifier.clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            inspectingDate = dateItem
                        }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = dateItem.format(DateTimeFormatter.ofPattern("EEEE, MMM d")),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = tokens.textPrimary
                                    )
                                    if (dateItem == today) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = EmberAccent.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "TODAY",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold
                                                ),
                                                color = EmberAccent,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (scoreVal != null) "Recovery: $scoreVal% (${matchingScore.state})" else "Baseline Calibrating",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = scoreColor
                                )

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (matchingSummary?.sleepDurationMinutes != null) {
                                        val h = matchingSummary.sleepDurationMinutes / 60
                                        val m = matchingSummary.sleepDurationMinutes % 60
                                        Text(
                                            text = "Sleep: ${h}h ${m}m",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = tokens.chartColors.sleep
                                        )
                                    }
                                    if (matchingWorkouts.isNotEmpty()) {
                                        Text(
                                            text = "• ${matchingWorkouts.size} workout${if (matchingWorkouts.size > 1) "s" else ""}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = tokens.chartColors.trainingLoad
                                        )
                                    }
                                    if (matchingFood.isNotEmpty()) {
                                        val cals = matchingFood.sumOf { it.calories }
                                        Text(
                                            text = "• $cals kcal in",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = EmberAccent
                                        )
                                    }
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = String.format(Locale.US, "%.1f strain", trainingLoad),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = tokens.chartColors.trainingLoad
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${matchingSummary?.steps ?: 0} steps",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = tokens.textMuted
                                    )
                                    if (matchingSummary?.restingHeartRate != null) {
                                        Text(
                                            text = "RHR: ${matchingSummary.restingHeartRate.toInt()} bpm",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = tokens.chartColors.restingHeartRate
                                        )
                                    }
                                }

                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = "Inspect",
                                    tint = tokens.textMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Date Detail Bottom Sheet
    if (inspectingDate != null) {
        val date = inspectingDate!!
        DayDetailSheet(
            date = date,
            summary = summaryByDate[date],
            recoveryScore = scoreByDate[date],
            workouts = workoutsByDate[date] ?: emptyList(),
            foodLogs = foodLogsByDate[date] ?: emptyList(),
            onDismiss = { inspectingDate = null },
            onNavigateToDashboard = onNavigateToDashboardForDate,
            onNavigateToNutrition = onNavigateToNutritionForDate
        )
    }

    // Direct Jump To Date Calendar Picker
    if (showJumpDatePicker) {
        val initialMillis = remember { today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialMillis,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val dt = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneId.of("UTC")).toLocalDate()
                    return !dt.isAfter(today)
                }
            }
        )

        DatePickerDialog(
            onDismissRequest = { showJumpDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val chosenDate = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                            inspectingDate = chosenDate
                        }
                        showJumpDatePicker = false
                    }
                ) {
                    Text("Inspect Day", color = EmberAccent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showJumpDatePicker = false }) {
                    Text("Cancel", color = tokens.textSecondary)
                }
            }
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(
                    containerColor = tokens.cardBackground,
                    titleContentColor = tokens.textPrimary,
                    headlineContentColor = tokens.textPrimary,
                    selectedDayContainerColor = EmberAccent,
                    selectedDayContentColor = Color.White
                )
            )
        }
    }
}

private fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()

@Composable
private fun WeeklyRecapRow(
    label: String,
    thisWeek: Double?,
    lastWeek: Double?,
    color: Color,
    format: (Double) -> String,
    deltaFormat: (Double) -> String = format
) {
    val tokens = LocalHangryTokens.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = thisWeek?.let(format) ?: "—",
                style = MaterialTheme.typography.titleMedium,
                color = color
            )
            val delta = if (thisWeek != null && lastWeek != null) thisWeek - lastWeek else null
            if (delta != null && abs(delta) > 0.05) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = (if (delta > 0) "+" else "") + deltaFormat(delta),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (delta > 0) tokens.scoreColors.primed else tokens.scoreColors.rebuild
                )
            }
        }
    }
}

@Composable
private fun HighlightPill(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val tokens = LocalHangryTokens.current
    Surface(
        color = tokens.cardBorder.copy(alpha = 0.5f),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = tokens.textMuted
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp),
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}
