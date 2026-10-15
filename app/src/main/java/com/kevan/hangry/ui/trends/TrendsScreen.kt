package com.kevan.hangry.ui.trends

import com.kevan.hangry.data.prefs.UnitPrefs
import com.kevan.hangry.domain.calculation.BodySignals
import com.kevan.hangry.domain.model.TemperatureText
import com.kevan.hangry.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.layout.layout
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
import com.kevan.hangry.data.repository.DefaultWeightRepository
import com.kevan.hangry.domain.repository.WeightRepository
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
import com.kevan.hangry.ui.components.LogWeightSheet
import com.kevan.hangry.ui.components.rememberBodyUnitFormatter
import com.kevan.hangry.domain.calculation.WeightTrend
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens
import com.kevan.hangry.ui.theme.SurfaceDark
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import com.kevan.hangry.ui.components.rememberToday
import androidx.lifecycle.compose.collectAsStateWithLifecycle

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
    SLEEPING_HR(R.string.body_trends_metric_sleeping_hr, Icons.Default.Bedtime, "bpm"),
    SKIN_TEMP(R.string.body_trends_metric_skin_temp, Icons.Default.Thermostat, "°C"),
    BREATHING(R.string.body_trends_metric_breathing, Icons.Default.Air, "/min"),
    SPO2(R.string.body_trends_metric_spo2, Icons.Default.Bloodtype, "%"),
    STEPS(R.string.body_trends_metric_steps, Icons.AutoMirrored.Filled.DirectionsRun, "steps"),
    ACTIVE_CALORIES(R.string.body_trends_metric_active_burn, Icons.Default.Whatshot, "kcal"),
    NUTRITION_CALORIES(R.string.body_trends_metric_calories_in, Icons.Default.Restaurant, "kcal"),
    WEIGHT(R.string.body_trends_metric_weight, Icons.Default.MonitorWeight, "kg")
}

enum class TrendDomain(val label: String, val metrics: List<TrendMetricCategory>) {
    ALL("All", TrendMetricCategory.values().toList()),
    RECOVERY_SLEEP("Recovery & Sleep", listOf(TrendMetricCategory.RECOVERY, TrendMetricCategory.SLEEP, TrendMetricCategory.HRV, TrendMetricCategory.RHR, TrendMetricCategory.SLEEPING_HR, TrendMetricCategory.SPO2)),
    VITALS("Vitals", listOf(TrendMetricCategory.HRV, TrendMetricCategory.RHR, TrendMetricCategory.SKIN_TEMP, TrendMetricCategory.BREATHING, TrendMetricCategory.SPO2)),
    ACTIVITY("Activity", listOf(TrendMetricCategory.STRAIN, TrendMetricCategory.STEPS, TrendMetricCategory.ACTIVE_CALORIES)),
    BODY_FUEL("Body & Fuel", listOf(TrendMetricCategory.WEIGHT, TrendMetricCategory.NUTRITION_CALORIES))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrendsScreen(
    viewModel: TrendsViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToDashboardForDate: (LocalDate) -> Unit = {},
    onNavigateToNutritionForDate: (LocalDate) -> Unit = {},
    onNavigateToBodyFatCalculator: () -> Unit = {},
    onLogWeight: ((kg: Double, takenAt: java.time.Instant) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val tokens = LocalHangryTokens.current
    val isDark = tokens.cardBackground == SurfaceDark
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val zone = remember { ZoneId.systemDefault() }
    val today = rememberToday(zone)

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedTimeframe = state.selectedTimeframe
    val selectedMetric = state.selectedMetric
    val selectedDomain = state.selectedDomain
    val historyFilter = state.historyFilter
    val inspectingDate = state.inspectingDate

    var showJumpDatePicker by remember { mutableStateOf(false) }

    // Grouped by day in the ViewModel, off the main thread.
    val derived = state.derived
    val queryStartDate = derived.queryStartDate

    val scores = state.scores
    val summaries = state.summaries
    val foodLogs = state.foodLogs
    val allWorkouts = state.allWorkouts
    val allWeights = state.allWeights
    val allBodyFatScans = state.allBodyFatScans
    val latestWeight = state.latestWeight

    // Weight tracking metrics: the smoothed trend and weekly rate (see WeightTrend)
    val weightTrend = derived.weightTrend
    val units = rememberBodyUnitFormatter()
    var loggingWeight by remember { mutableStateOf(false) }

    // Weekly recap: always last 7 days vs the 7 days before that
    val thisWeekScores = state.thisWeekScores
    val lastWeekScores = state.lastWeekScores
    val thisWeekSummaries = state.thisWeekSummaries
    val lastWeekSummaries = state.lastWeekSummaries

    val workoutsByDate = derived.workoutsByDate
    val foodLogsByDate = derived.foodLogsByDate
    val weightsByDate = derived.weightsByDate
    val scoreByDate = derived.scoreByDate
    val summaryByDate = derived.summaryByDate
    val chronologicalDates = derived.chronologicalDates

    // Generate trend points for currently selected metric
    val fahrenheit = UnitPrefs.fahrenheit(context)
    val trendPoints = remember(selectedMetric, chronologicalDates, scoreByDate, summaryByDate, foodLogsByDate, weightsByDate, fahrenheit, units) {
        chronologicalDates.map { date ->
            val value = when (selectedMetric) {
                TrendMetricCategory.RECOVERY -> scoreByDate[date]?.score?.toDouble()
                TrendMetricCategory.STRAIN -> summaryByDate[date]?.dayStrain
                TrendMetricCategory.SLEEP -> summaryByDate[date]?.sleepDurationMinutes?.let { it / 60.0 }
                TrendMetricCategory.HRV -> summaryByDate[date]?.hrvRmssd
                TrendMetricCategory.RHR -> summaryByDate[date]?.restingHeartRate
                TrendMetricCategory.SLEEPING_HR -> summaryByDate[date]?.sleepingHeartRate
                TrendMetricCategory.SKIN_TEMP -> summaryByDate[date]?.skinTempDeltaCelsius?.let { TemperatureText.deltaValue(it, fahrenheit) }
                TrendMetricCategory.BREATHING -> summaryByDate[date]?.respiratoryRate
                TrendMetricCategory.SPO2 -> summaryByDate[date]?.spo2Percentage
                TrendMetricCategory.STEPS -> summaryByDate[date]?.steps?.toDouble()
                TrendMetricCategory.ACTIVE_CALORIES -> summaryByDate[date]?.activeCalories
                TrendMetricCategory.NUTRITION_CALORIES -> foodLogsByDate[date]?.sumOf { it.calories }?.toDouble()
                TrendMetricCategory.WEIGHT -> weightsByDate[date]?.let(units::weightNumber)
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
        TrendMetricCategory.SLEEPING_HR -> tokens.chartColors.restingHeartRate
        TrendMetricCategory.SKIN_TEMP -> tokens.scoreColors.rebuild
        TrendMetricCategory.BREATHING -> tokens.chartColors.hrv
        TrendMetricCategory.SPO2 -> tokens.scoreColors.primed
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
            TrendMetricCategory.SLEEPING_HR -> "${v.toInt()}"
            TrendMetricCategory.SKIN_TEMP -> String.format(Locale.US, "%+.1f", v)
            TrendMetricCategory.BREATHING -> String.format(Locale.US, "%.1f", v)
            TrendMetricCategory.SPO2 -> "${v.toInt()}"
            TrendMetricCategory.STEPS -> String.format(Locale.US, "%,d", v.toLong())
            TrendMetricCategory.ACTIVE_CALORIES -> "${v.toInt()}"
            TrendMetricCategory.NUTRITION_CALORIES -> "${v.toInt()}"
            TrendMetricCategory.WEIGHT -> String.format(Locale.US, "%.1f", v)
        }
    }

    // Filtered history list
    val allHistoryDates = derived.historyDates

    val filteredHistoryDates = remember(allHistoryDates, historyFilter, scoreByDate, workoutsByDate, foodLogsByDate, summaryByDate) {
        when (historyFilter) {
            HistoryFilter.ALL -> allHistoryDates
            HistoryFilter.WORKOUTS -> allHistoryDates.filter { (workoutsByDate[it]?.size ?: 0) > 0 }
            HistoryFilter.PRIMED -> allHistoryDates.filter { (scoreByDate[it]?.score ?: 0) >= 67 }
            HistoryFilter.REBUILD -> allHistoryDates.filter {
                val sc = scoreByDate[it]?.score
                sc != null && sc < 34
            }
            HistoryFilter.NUTRITION -> allHistoryDates.filter { (foodLogsByDate[it]?.size ?: 0) > 0 }
            // Nights where several readings were off your normal together.
            HistoryFilter.BODY_SIGNALS -> allHistoryDates.filter {
                BodySignals.decode(summaryByDate[it]?.bodySignals).size >= BodySignals.MIN_SIGNALS
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                expandedHeight = 56.dp,
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
            verticalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.section)
        ) {
            // 1. Timeframe Segmented Switcher
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(tokens.cardBackground)
                        .border(BorderStroke(1.dp, tokens.cardBorder), RoundedCornerShape(14.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TrendTimeframe.values().forEach { timeframe ->
                        val isSelected = timeframe == selectedTimeframe
                        val animBg by animateColorAsState(
                            targetValue = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.22f else 0.14f) else Color.Transparent,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "timeframeBg"
                        )
                        val animBorder by animateColorAsState(
                            targetValue = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.65f else 0.45f) else Color.Transparent,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "timeframeBorder"
                        )
                        val animTextColor by animateColorAsState(
                            targetValue = if (isSelected) MaterialTheme.colorScheme.primary else tokens.textSecondary,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "timeframeText"
                        )
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = animBg,
                            border = BorderStroke(1.dp, animBorder),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 40.dp)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.setTimeframe(timeframe)
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
                                    color = animTextColor
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

                    Spacer(modifier = Modifier.height(10.dp))

                    // Domain Category Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TrendDomain.values().forEach { domain ->
                            val isDomainSelected = domain == selectedDomain
                            val domainBg by animateColorAsState(
                                targetValue = if (isDomainSelected) MaterialTheme.colorScheme.primaryContainer else tokens.cardBorder.copy(alpha = 0.35f),
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                label = "domainBg"
                            )
                            val domainBorder by animateColorAsState(
                                targetValue = if (isDomainSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f) else Color.Transparent,
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                label = "domainBorder"
                            )
                            val domainTextColor by animateColorAsState(
                                targetValue = if (isDomainSelected) MaterialTheme.colorScheme.onPrimaryContainer else tokens.textSecondary,
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                label = "domainText"
                            )
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = domainBg,
                                border = BorderStroke(1.dp, domainBorder),
                                modifier = Modifier
                                    .height(30.dp)
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        viewModel.setDomain(domain)
                                        if (selectedMetric !in domain.metrics) {
                                            viewModel.setMetric(domain.metrics.first())
                                        }
                                    }
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = domain.label,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = if (isDomainSelected) FontWeight.Bold else FontWeight.Medium
                                        ),
                                        color = domainTextColor
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Metric Chip Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        selectedDomain.metrics.forEach { metric ->
                            val isChosen = metric == selectedMetric
                            val metricBg by animateColorAsState(
                                targetValue = if (isChosen) MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.22f else 0.14f) else tokens.cardBorder.copy(alpha = 0.4f),
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                label = "metricBg"
                            )
                            val metricBorder by animateColorAsState(
                                targetValue = if (isChosen) MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.65f else 0.45f) else Color.Transparent,
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                label = "metricBorder"
                            )
                            val metricTint by animateColorAsState(
                                targetValue = if (isChosen) MaterialTheme.colorScheme.primary else tokens.textSecondary,
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                label = "metricTint"
                            )
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = metricBg,
                                border = BorderStroke(1.dp, metricBorder),
                                modifier = Modifier
                                    .height(34.dp)
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        viewModel.setMetric(metric)
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
                                        tint = metricTint,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = stringResource(metric.labelRes),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Medium
                                        ),
                                        color = metricTint
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
                        unit = when {
                            selectedMetric == TrendMetricCategory.SKIN_TEMP && fahrenheit -> "°F"
                            selectedMetric == TrendMetricCategory.WEIGHT -> units.weightSymbol
                            else -> selectedMetric.unit
                        },
                        formatValue = formatMetricValue,
                        onPointClicked = { tappedDate ->
                            viewModel.setInspectingDate(tappedDate)
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
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.MonitorWeight,
                                contentDescription = null,
                                tint = tokens.chartColors.sleep,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.body_trends_weight_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = tokens.textPrimary
                            )
                        }

                        HangryInfoTip(
                            title = stringResource(R.string.body_trends_trend_weight),
                            body = stringResource(R.string.body_trends_weight_info)
                        )
                        IconButton(onClick = { loggingWeight = true }) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = stringResource(R.string.body_log_weight_action),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        TextButton(
                            onClick = onNavigateToBodyFatCalculator,
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.AccessibilityNew, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.body_trends_body_fat_calculator_short))
                        }
                    }

                    Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))

                    val weightKg = latestWeight?.weightKg
                    if (weightKg != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Column {
                                Text(
                                    text = units.weight(weightKg),
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = tokens.textPrimary
                                )
                                Text(
                                    text = stringResource(R.string.body_trends_latest_weigh_in),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = tokens.textSecondary
                                )
                            }
                            weightTrend.trendKg?.let { trendKg ->
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = units.weight(trendKg),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = tokens.chartColors.sleep
                                    )
                                    Text(
                                        text = stringResource(R.string.body_trends_trend_weight),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = tokens.textSecondary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))

                        val weekly = weightTrend.weeklyChangeKg
                        Text(
                            text = when {
                                weekly == null -> stringResource(R.string.body_trends_weight_rate_needs_data)
                                abs(weekly) < STEADY_KG_PER_WEEK -> stringResource(R.string.body_trends_weight_steady, units.weeklyRate(weekly))
                                else -> stringResource(R.string.body_trends_weight_rate, units.weeklyRate(weekly))
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.textSecondary
                        )

                        // One dot per weigh-in day in the timeframe, the trend as the line.
                        val inRange = remember(weightTrend, queryStartDate) {
                            weightTrend.points.filter { !it.date.isBefore(queryStartDate) && !it.date.isAfter(today) }
                        }
                        if (inRange.size >= 2) {
                            Spacer(modifier = Modifier.height(HangryTokens.Spacing.m))
                            HorizontalDivider(color = tokens.cardBorder.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))

                            val netChange = inRange.last().trendKg - inRange.first().trendKg
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
                                    text = stringResource(R.string.body_trends_weight_net_change, units.weightChange(netChange), selectedTimeframe.displayLabel()),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (netChange <= 0) tokens.scoreColors.primed else tokens.scoreColors.rebuild
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            HangryInteractiveTrendChart(
                                points = inRange.map { TrendPoint(date = it.date, value = units.weightNumber(it.weighInKg)) },
                                trend = inRange.map { units.weightNumber(it.trendKg) },
                                color = tokens.chartColors.sleep,
                                unit = units.weightSymbol,
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
                        val filterBg by animateColorAsState(
                            targetValue = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.22f else 0.14f) else tokens.cardBorder.copy(alpha = 0.4f),
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "filterBg"
                        )
                        val filterBorder by animateColorAsState(
                            targetValue = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.65f else 0.45f) else Color.Transparent,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "filterBorder"
                        )
                        val filterTextColor by animateColorAsState(
                            targetValue = if (isSelected) MaterialTheme.colorScheme.primary else tokens.textSecondary,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "filterText"
                        )
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = filterBg,
                            border = BorderStroke(1.dp, filterBorder),
                            modifier = Modifier
                                .height(30.dp)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.setHistoryFilter(filter)
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
                                    color = filterTextColor
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
                // One grouped card: each day is a lazy two-line row, with the card's corners
                // on the first and last rows so long ranges stay lazy.
                itemsIndexed(filteredHistoryDates, key = { _, date -> date }) { index, dateItem ->
                    val isFirst = index == 0
                    val isLast = index == filteredHistoryDates.lastIndex
                    val corner = HangryTokens.CornerRadii.medium
                    Surface(
                        color = tokens.cardBackground,
                        shape = RoundedCornerShape(
                            topStart = if (isFirst) corner else 0.dp,
                            topEnd = if (isFirst) corner else 0.dp,
                            bottomStart = if (isLast) corner else 0.dp,
                            bottomEnd = if (isLast) corner else 0.dp
                        ),
                        // Pull each row up over the list's item gap so the rows read as one card.
                        modifier = Modifier
                            .animateItem()
                            .fillMaxWidth()
                            .layout { measurable, constraints ->
                                val gap = if (isFirst) 0 else HangryTokens.Spacing.section.roundToPx()
                                val placeable = measurable.measure(constraints)
                                layout(placeable.width, (placeable.height - gap).coerceAtLeast(0)) {
                                    placeable.place(0, -gap)
                                }
                            }
                    ) {
                        Column {
                            if (!isFirst) HorizontalDivider(color = tokens.cardBorder, modifier = Modifier.padding(horizontal = HangryTokens.Spacing.card))
                            HistoryDayRow(
                                date = dateItem,
                                isToday = dateItem == today,
                                score = scoreByDate[dateItem],
                                summary = summaryByDate[dateItem],
                                workoutCount = workoutsByDate[dateItem]?.size ?: 0,
                                caloriesIn = foodLogsByDate[dateItem]?.takeIf { it.isNotEmpty() }?.sumOf { it.calories },
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.setInspectingDate(dateItem)
                                }
                            )
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
            onDismiss = { viewModel.setInspectingDate(null) },
            onNavigateToDashboard = onNavigateToDashboardForDate,
            onNavigateToNutrition = onNavigateToNutritionForDate
        )
    }

    // Direct Jump To Date Calendar Picker
    if (loggingWeight) {
        LogWeightSheet(
            lastWeightKg = latestWeight?.weightKg,
            onDismiss = { loggingWeight = false },
            onSave = { kg, at ->
                if (onLogWeight != null) {
                    onLogWeight(kg, at)
                } else {
                    viewModel.logWeight(kg, at)
                }
                loggingWeight = false
            }
        )
    }

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
                            viewModel.setInspectingDate(chosenDate)
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

@Deprecated("Use TrendsScreen with TrendsViewModel")
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
    onLogWeight: ((kg: Double, takenAt: java.time.Instant) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val weightRepository = remember(weightDao) { DefaultWeightRepository(weightDao) }
    val viewModel: TrendsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = TrendsViewModel.provideFactory(
            dailySummaryRepository = dailySummaryRepository,
            weightRepository = weightRepository,
            workoutRepository = workoutRepository,
            foodLogRepository = foodLogRepository,
            bodyFatRepository = bodyFatRepository
        )
    )
    TrendsScreen(
        viewModel = viewModel,
        onNavigateBack = onNavigateBack,
        onNavigateToDashboardForDate = onNavigateToDashboardForDate,
        onNavigateToNutritionForDate = onNavigateToNutritionForDate,
        onNavigateToBodyFatCalculator = onNavigateToBodyFatCalculator,
        onLogWeight = onLogWeight,
        modifier = modifier
    )
}

/** Under this many kg a week either way, the trend reads as holding steady. */
private const val STEADY_KG_PER_WEEK = 0.05

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

/**
 * One day in the history card, two lines: date and recovery, then the day's numbers
 * (sleep, strain, steps, RHR, workouts, food). Tapping opens the day's details.
 */
@Composable
private fun HistoryDayRow(
    date: LocalDate,
    isToday: Boolean,
    score: RecoveryScoreEntity?,
    summary: DailyHealthSummaryEntity?,
    workoutCount: Int,
    caloriesIn: Int?,
    onClick: () -> Unit
) {
    val tokens = LocalHangryTokens.current
    val scoreVal = score?.score
    val scoreColor = when {
        scoreVal == null -> tokens.textMuted
        scoreVal >= 67 -> tokens.scoreColors.primed
        scoreVal >= 34 -> tokens.scoreColors.balanced
        else -> tokens.scoreColors.rebuild
    }
    val stateLabel = score?.state?.replace('_', ' ')?.lowercase()?.replaceFirstChar { it.titlecase() }.orEmpty()
    val details = buildList {
        summary?.sleepDurationMinutes?.let { add(stringResource(R.string.body_trends_history_sleep, it / 60, it % 60)) }
        add(stringResource(R.string.body_trends_history_strain, summary?.dailyTrainingLoad ?: 0.0))
        add(stringResource(R.string.body_trends_history_steps, compactCount(summary?.steps ?: 0L)))
        summary?.restingHeartRate?.let { add(stringResource(R.string.body_trends_history_rhr, it.toInt())) }
        if (workoutCount > 0) {
            add(
                if (workoutCount > 1) stringResource(R.string.body_trends_history_workouts, workoutCount)
                else stringResource(R.string.body_trends_history_workout_one, workoutCount)
            )
        }
        caloriesIn?.let { add(stringResource(R.string.body_trends_history_kcal_in, it)) }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = HangryTokens.Spacing.card, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = date.format(DateTimeFormatter.ofPattern("EEE d")),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = tokens.textPrimary
                )
                Text(
                    text = " · " + if (scoreVal != null) stringResource(R.string.body_trends_history_recovery, scoreVal, stateLabel)
                    else stringResource(R.string.body_trends_history_baseline_calibrating),
                    style = MaterialTheme.typography.titleSmall,
                    color = scoreColor
                )
                if (isToday) {
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
            Text(
                text = details.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = tokens.textSecondary
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = stringResource(R.string.body_trends_inspect),
            tint = tokens.textMuted,
            modifier = Modifier.size(20.dp)
        )
    }
}

/** Step counts in a short form: 940, 9.1k, 12k. */
private fun compactCount(value: Long): String = when {
    value < 1_000 -> value.toString()
    value < 10_000 -> String.format(Locale.US, "%.1fk", value / 1000.0)
    else -> "${value / 1000}k"
}
