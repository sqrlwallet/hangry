package com.kevan.hangry.ui.trends

import com.kevan.hangry.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.annotation.StringRes
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
import com.kevan.hangry.data.local.dao.WeightDao
import com.kevan.hangry.data.local.entity.BodyFatScanEntity
import com.kevan.hangry.data.local.entity.DailyHealthSummaryEntity
import com.kevan.hangry.data.local.entity.ExerciseSessionEntity
import com.kevan.hangry.data.local.entity.FoodLogEntity
import com.kevan.hangry.data.local.entity.RecoveryScoreEntity
import com.kevan.hangry.domain.repository.BodyFatRepository
import com.kevan.hangry.domain.repository.DailySummaryRepository
import com.kevan.hangry.domain.repository.FoodLogRepository
import com.kevan.hangry.domain.repository.WorkoutRepository
import com.kevan.hangry.ui.components.DashEmptyScene
import com.kevan.hangry.ui.components.DashEmptyState
import com.kevan.hangry.ui.components.DayDetailSheet
import com.kevan.hangry.ui.components.HangryCard
import com.kevan.hangry.ui.components.HangryInfoTip
import com.kevan.hangry.ui.components.HangryInteractiveTrendChart
import com.kevan.hangry.ui.components.TrendPoint
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

/** Localised chip text for a [TrendTimeframe] ("7D", "All", ...). */
@Composable
private fun TrendTimeframe.displayLabel(): String = when (this) {
    TrendTimeframe.ALL_TIME -> stringResource(R.string.body_trends_timeframe_all)
    else -> stringResource(R.string.body_trends_timeframe_days, days?.toInt() ?: 0)
}

enum class TrendMetricCategory(
    @StringRes val labelRes: Int,
    val icon: ImageVector,
    val unit: String
) {
    RECOVERY(R.string.body_trends_metric_recovery, Icons.AutoMirrored.Filled.TrendingUp, "%"),
    STRAIN(R.string.body_trends_metric_strain, Icons.Default.LocalFireDepartment, ""),
    SLEEP(R.string.body_trends_metric_sleep, Icons.Default.Bedtime, "h"),
    HRV(R.string.body_trends_metric_hrv, Icons.Default.Favorite, "ms"),
    RHR(R.string.body_trends_metric_rhr, Icons.Default.HeartBroken, "bpm"),
    STEPS(R.string.body_trends_metric_steps, Icons.AutoMirrored.Filled.DirectionsRun, "steps"),
    ACTIVE_CALORIES(R.string.body_trends_metric_active_burn, Icons.Default.Whatshot, "kcal"),
    NUTRITION_CALORIES(R.string.body_trends_metric_calories_in, Icons.Default.Restaurant, "kcal"),
    WEIGHT(R.string.body_trends_metric_weight, Icons.Default.MonitorWeight, "kg")
}

private enum class HistoryFilter(@StringRes val labelRes: Int) {
    ALL(R.string.body_trends_filter_all_days),
    WORKOUTS(R.string.body_trends_filter_workouts),
    PRIMED(R.string.body_trends_filter_primed),
    REBUILD(R.string.body_trends_filter_rebuild),
    NUTRITION(R.string.body_trends_filter_has_meals)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrendsScreen(
    dailySummaryRepository: DailySummaryRepository,
    weightDao: WeightDao,
    workoutRepository: WorkoutRepository,
    foodLogRepository: FoodLogRepository,
    bodyFatRepository: BodyFatRepository? = null,
    onNavigateBack: () -> Unit,
    onNavigateToDashboardForDate: (LocalDate) -> Unit = {},
    onNavigateToNutritionForDate: (LocalDate) -> Unit = {},
    onNavigateToBodyFatCalculator: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val tokens = LocalHangryTokens.current
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
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
    val allBodyFatScans by (bodyFatRepository?.getAllScans() ?: kotlinx.coroutines.flow.flowOf(emptyList()))
        .collectAsState(initial = emptyList())

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
        TrendMetricCategory.NUTRITION_CALORIES -> tokens.macroColors.calories
        TrendMetricCategory.WEIGHT -> tokens.chartColors.sleep
    }

    val formatMetricValue: (Double) -> String = { v ->
        when (selectedMetric) {
            TrendMetricCategory.RECOVERY -> "${v.toInt()}"
            TrendMetricCategory.STRAIN -> String.format(Locale.US, "%.1f", v)
            TrendMetricCategory.SLEEP -> {
                val totalMin = (v * 60).toInt()
                context.getString(R.string.body_trends_duration_hm, totalMin / 60, totalMin % 60)
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
                title = { Text(stringResource(R.string.body_trends_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.body_back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showJumpDatePicker = true }) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = stringResource(R.string.body_trends_jump_to_date),
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
            // Clear of the system navigation bar (3-button nav is 48dp tall).
            contentPadding = PaddingValues(
                top = HangryTokens.Spacing.s,
                bottom = 32.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            ),
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
                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent,
                            border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
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
                                    text = timeframe.displayLabel(),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else tokens.textSecondary
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
                                text = stringResource(R.string.body_trends_metric_explorer),
                                style = MaterialTheme.typography.titleMedium,
                                color = tokens.textPrimary
                            )
                        }

                        Surface(
                            color = tokens.cardBorder,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.body_trends_days_logged, chronologicalDates.size),
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
                                color = if (isChosen) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else tokens.cardBorder.copy(alpha = 0.4f),
                                border = if (isChosen) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
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
                                        tint = if (isChosen) MaterialTheme.colorScheme.primary else tokens.textSecondary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = stringResource(metric.labelRes),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Medium
                                        ),
                                        color = if (isChosen) MaterialTheme.colorScheme.primary else tokens.textSecondary
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
                                label = stringResource(R.string.body_trends_period_avg),
                                value = formatMetricValue(avg),
                                color = metricColor,
                                modifier = Modifier.weight(1f)
                            )
                            if (peak != null && peak.value != null) {
                                HighlightPill(
                                    label = stringResource(R.string.body_trends_peak_on, peak.date.format(DateTimeFormatter.ofPattern("MMM d"))),
                                    value = formatMetricValue(peak.value),
                                    color = tokens.scoreColors.primed,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (low != null && low.value != null) {
                                HighlightPill(
                                    label = stringResource(R.string.body_trends_low_on, low.date.format(DateTimeFormatter.ofPattern("MMM d"))),
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
                                text = stringResource(R.string.body_trends_weight_title),
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
                                    text = stringResource(R.string.body_trends_health_connect_badge),
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
                                    text = stringResource(R.string.body_trends_kg_value, weightKg),
                                    style = MaterialTheme.typography.displayMedium,
                                    color = tokens.textPrimary
                                )
                                Text(
                                    text = stringResource(R.string.body_trends_lbs_approx, weightLbs),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = tokens.textMuted
                                )
                            }

                            if (rollingAvgWeight != null) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = stringResource(R.string.body_trends_kg_value, rollingAvgWeight!!),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = tokens.chartColors.sleep
                                    )
                                    Text(
                                        text = stringResource(R.string.body_trends_moving_avg_7d),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = tokens.textSecondary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))

                        val delta = rollingAvgWeight?.let { weightKg - it }
                        val trendNote = when {
                            delta == null -> stringResource(R.string.body_trends_weight_stable_baseline)
                            abs(delta) < 0.3 -> stringResource(R.string.body_trends_weight_stable_variance)
                            delta > 0 -> stringResource(R.string.body_trends_weight_delta_up, delta)
                            else -> stringResource(R.string.body_trends_weight_delta_down, delta)
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
                                    text = stringResource(R.string.body_trends_weight_trajectory),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = tokens.textMuted
                                )
                                Text(
                                    text = stringResource(R.string.body_trends_weight_net_change, if (netChange > 0) "+" else "", netChange, selectedTimeframe.displayLabel()),
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

                    // Body Fat % over time chart (if any scans exist in the selected timeframe)
                    val bodyFatPoints = remember(allBodyFatScans, queryStartDate) {
                        allBodyFatScans
                            .filter { !it.date.isBefore(queryStartDate) && !it.date.isAfter(today) }
                            .sortedBy { it.date }
                            .map { TrendPoint(date = it.date, value = it.bodyFatPercentage) }
                    }
                    if (bodyFatPoints.size >= 2) {
                        Spacer(modifier = Modifier.height(HangryTokens.Spacing.m))
                        HorizontalDivider(color = tokens.cardBorder.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.body_trends_body_fat_trend),
                                style = MaterialTheme.typography.labelSmall,
                                color = tokens.textMuted
                            )
                            val latestBf = bodyFatPoints.last().value
                            if (latestBf != null) {
                                Text(
                                    text = String.format(Locale.US, "%.1f%%", latestBf),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = tokens.scoreColors.primed
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        HangryInteractiveTrendChart(
                            points = bodyFatPoints,
                            color = tokens.scoreColors.primed,
                            unit = "%",
                            formatValue = { String.format(Locale.US, "%.1f", it) }
                        )
                    } else if (bodyFatPoints.size == 1) {
                        Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))
                        HorizontalDivider(color = tokens.cardBorder.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))
                        val bf = bodyFatPoints.first().value
                        if (bf != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stringResource(R.string.body_trends_latest_body_fat),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = tokens.textSecondary
                                )
                                Text(
                                    text = String.format(Locale.US, "%.1f%%", bf),
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = tokens.scoreColors.primed
                                )
                            }
                            Text(
                                text = stringResource(R.string.body_trends_more_scans),
                                style = MaterialTheme.typography.labelSmall,
                                color = tokens.textMuted
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
                        Text(stringResource(R.string.body_trends_body_fat_calculator_button))
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
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.body_trends_nutrition_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = tokens.textPrimary
                            )
                        }

                        Text(
                            text = stringResource(R.string.body_trends_days_tracked, foodLogsByDate.keys.size),
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
                                label = stringResource(R.string.body_trends_avg_intake),
                                value = stringResource(R.string.body_trends_kcal_value, avgCaloriesPerDay),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            HighlightPill(
                                label = stringResource(R.string.body_trends_avg_protein),
                                value = stringResource(R.string.body_trends_grams_value, avgProtein.toInt()),
                                color = tokens.scoreColors.primed,
                                modifier = Modifier.weight(1f)
                            )
                            HighlightPill(
                                label = stringResource(R.string.body_trends_avg_carbs),
                                value = stringResource(R.string.body_trends_grams_value, avgCarbs.toInt()),
                                color = tokens.scoreColors.balanced,
                                modifier = Modifier.weight(1f)
                            )
                            HighlightPill(
                                label = stringResource(R.string.body_trends_avg_fat),
                                value = stringResource(R.string.body_trends_grams_value, avgFat.toInt()),
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
                            text = stringResource(R.string.body_trends_macro_split, proteinPct, carbsPct, fatPct),
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.textSecondary
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.body_trends_no_food_in_range),
                                style = MaterialTheme.typography.bodyMedium,
                                color = tokens.textSecondary
                            )
                            HangryInfoTip(
                                title = stringResource(R.string.body_trends_nutrition_title),
                                body = stringResource(R.string.body_trends_no_food_tip)
                            )
                        }
                    }
                }
            }

            // 5. Weekly Performance Recap (Last 7 Days vs Prior 7 Days)
            item {
                HangryCard {
                    Text(
                        text = stringResource(R.string.body_trends_weekly_recap_title),
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
                        label = stringResource(R.string.body_trends_avg_recovery),
                        thisWeek = thisAvgRecovery,
                        lastWeek = lastAvgRecovery,
                        color = tokens.scoreColors.primed,
                        format = { context.getString(R.string.body_trends_percent_value, it.toInt()) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = tokens.cardBorder)
                    WeeklyRecapRow(
                        label = stringResource(R.string.body_trends_avg_daily_strain),
                        thisWeek = thisAvgStrain,
                        lastWeek = lastAvgStrain,
                        color = tokens.chartColors.trainingLoad,
                        format = { String.format(Locale.US, "%.1f", it) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = tokens.cardBorder)
                    WeeklyRecapRow(
                        label = stringResource(R.string.body_trends_avg_sleep),
                        thisWeek = thisAvgSleep,
                        lastWeek = lastAvgSleep,
                        color = tokens.chartColors.sleep,
                        format = { context.getString(R.string.body_trends_duration_hm, (it / 60).toInt(), (it % 60).toInt()) },
                        deltaFormat = { context.getString(R.string.body_trends_minutes_value, it.toInt()) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = tokens.cardBorder)
                    WeeklyRecapRow(
                        label = stringResource(R.string.body_trends_avg_daily_steps),
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
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.body_trends_history_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = tokens.textPrimary,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        HangryInfoTip(
                            title = stringResource(R.string.body_trends_history_title),
                            body = stringResource(R.string.body_trends_history_tip)
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
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = stringResource(R.string.body_trends_pick_date),
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
                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else tokens.cardBorder.copy(alpha = 0.4f),
                            border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
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
                                    text = stringResource(filter.labelRes),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else tokens.textSecondary
                                )
                            }
                        }
                    }
                }
            }

            if (filteredHistoryDates.isEmpty()) {
                item {
                    HangryCard(cornerRadius = HangryTokens.CornerRadii.large) {
                        DashEmptyState(
                            scene = DashEmptyScene.RECORDS,
                            title = stringResource(R.string.body_trends_history_empty_title),
                            body = stringResource(R.string.body_trends_history_empty_body),
                            modifier = Modifier.padding(vertical = HangryTokens.Spacing.s)
                        )
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
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = stringResource(R.string.body_trends_today_badge),
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (scoreVal != null) stringResource(R.string.body_trends_history_recovery, scoreVal, matchingScore.state) else stringResource(R.string.body_trends_history_baseline_calibrating),
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
                                            text = stringResource(R.string.body_trends_history_sleep, h, m),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = tokens.chartColors.sleep
                                        )
                                    }
                                    if (matchingWorkouts.isNotEmpty()) {
                                        Text(
                                            text = if (matchingWorkouts.size > 1) stringResource(R.string.body_trends_history_workouts, matchingWorkouts.size) else stringResource(R.string.body_trends_history_workout_one, matchingWorkouts.size),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = tokens.chartColors.trainingLoad
                                        )
                                    }
                                    if (matchingFood.isNotEmpty()) {
                                        val cals = matchingFood.sumOf { it.calories }
                                        Text(
                                            text = stringResource(R.string.body_trends_history_kcal_in, cals),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
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
                                        text = stringResource(R.string.body_trends_history_strain, trainingLoad),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = tokens.chartColors.trainingLoad
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = stringResource(R.string.body_trends_history_steps, matchingSummary?.steps ?: 0L),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = tokens.textMuted
                                    )
                                    if (matchingSummary?.restingHeartRate != null) {
                                        Text(
                                            text = stringResource(R.string.body_trends_history_rhr, matchingSummary.restingHeartRate.toInt()),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = tokens.chartColors.restingHeartRate
                                        )
                                    }
                                }

                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = stringResource(R.string.body_trends_inspect),
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
                    Text(stringResource(R.string.body_trends_inspect_day), color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showJumpDatePicker = false }) {
                    Text(stringResource(R.string.cancel), color = tokens.textSecondary)
                }
            }
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(
                    containerColor = tokens.cardBackground,
                    titleContentColor = tokens.textPrimary,
                    headlineContentColor = tokens.textPrimary,
                    selectedDayContainerColor = MaterialTheme.colorScheme.primary,
                    selectedDayContentColor = MaterialTheme.colorScheme.onPrimary
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
                style = MaterialTheme.typography.labelSmall,
                color = tokens.textMuted
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}
