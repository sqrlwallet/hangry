package com.kevan.hangry.ui.trends

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.kevan.hangry.data.local.dao.WeightDao
import com.kevan.hangry.domain.repository.DailySummaryRepository
import com.kevan.hangry.ui.components.HangryCard
import com.kevan.hangry.ui.components.HangryLineChart
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class TrendTimeframe(val label: String, val days: Long?) {
    DAYS_7("7 Days", 7),
    DAYS_30("30 Days", 30),
    DAYS_90("90 Days", 90),
    ALL_TIME("All Time", null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrendsScreen(
    dailySummaryRepository: DailySummaryRepository,
    weightDao: WeightDao,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalHangryTokens.current
    val today = LocalDate.now()
    var selectedTimeframe by remember { mutableStateOf(TrendTimeframe.DAYS_30) }

    // Query scores and summaries based on selected timeframe
    val scoresFlow = remember(selectedTimeframe) {
        val days = selectedTimeframe.days
        if (days != null) {
            dailySummaryRepository.getScoresBetween(today.minusDays(days), today)
        } else {
            dailySummaryRepository.getAllScores()
        }
    }
    val summariesFlow = remember(selectedTimeframe) {
        val days = selectedTimeframe.days
        if (days != null) {
            dailySummaryRepository.getSummariesBetween(today.minusDays(days), today)
        } else {
            dailySummaryRepository.getAllSummaries()
        }
    }

    val scores by scoresFlow.collectAsState(initial = emptyList())
    val summaries by summariesFlow.collectAsState(initial = emptyList())

    // Weight tracking metrics
    val latestWeight by weightDao.getLatestWeight().collectAsState(initial = null)
    val rollingAvgWeight by weightDao.getRollingAverageWeight().collectAsState(initial = null)

    // Weekly recap: always last 7 days vs the 7 days before that, independent of the selected timeframe
    val thisWeekScores by remember { dailySummaryRepository.getScoresBetween(today.minusDays(6), today) }
        .collectAsState(initial = emptyList())
    val lastWeekScores by remember { dailySummaryRepository.getScoresBetween(today.minusDays(13), today.minusDays(7)) }
        .collectAsState(initial = emptyList())
    val thisWeekSummaries by remember { dailySummaryRepository.getSummariesBetween(today.minusDays(6), today) }
        .collectAsState(initial = emptyList())
    val lastWeekSummaries by remember { dailySummaryRepository.getSummariesBetween(today.minusDays(13), today.minusDays(7)) }
        .collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Wellness Trends & Baselines") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
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
                .padding(innerPadding)
                .padding(horizontal = HangryTokens.Spacing.m, vertical = HangryTokens.Spacing.s),
            verticalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.m)
        ) {
            // Timeframe Segmented Switcher
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(tokens.cardBackground)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TrendTimeframe.values().forEach { timeframe ->
                        val isSelected = timeframe == selectedTimeframe
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) tokens.scoreColors.primed.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface,
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, tokens.scoreColors.primed) else null,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .clickable { selectedTimeframe = timeframe }
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = timeframe.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isSelected) tokens.scoreColors.primed else tokens.textSecondary
                                )
                            }
                        }
                    }
                }
            }

            // Body Weight Tracking Card
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
                                modifier = Modifier.size(22.dp)
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
                            kotlin.math.abs(delta) < 0.3 -> "Stable, within normal variance."
                            delta > 0 -> String.format(Locale.US, "+%.1f kg vs 7-day average.", delta)
                            else -> String.format(Locale.US, "%.1f kg vs 7-day average.", delta)
                        }

                        Text(
                            text = trendNote,
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.textSecondary
                        )
                    } else {
                        Text(
                            text = "No weight records in Health Connect yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.textSecondary
                        )
                    }
                }
            }

            // Macro Averages & Stability Card
            item {
                HangryCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                contentDescription = null,
                                tint = tokens.scoreColors.primed,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Baseline Stability & Averages",
                                style = MaterialTheme.typography.titleMedium,
                                color = tokens.textPrimary
                            )
                        }

                        Text(
                            text = "${scores.size} Days Logged",
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.textMuted
                        )
                    }

                    Spacer(modifier = Modifier.height(HangryTokens.Spacing.m))

                    val validScores = scores.mapNotNull { it.score }
                    val avgRecovery = if (validScores.isNotEmpty()) validScores.average().toInt() else null

                    val validLoads = summaries.mapNotNull { it.dailyTrainingLoad }
                    val avgLoad = if (validLoads.isNotEmpty()) validLoads.average() else 0.0

                    val validSleep = summaries.mapNotNull { it.sleepDurationMinutes }
                    val avgSleepMins = if (validSleep.isNotEmpty()) validSleep.average().toInt() else null

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SummaryPill(
                            title = "Avg Recovery",
                            value = if (avgRecovery != null) "$avgRecovery%" else "—",
                            color = tokens.scoreColors.primed,
                            modifier = Modifier.weight(1f)
                        )
                        SummaryPill(
                            title = "Avg Daily Strain",
                            value = String.format(Locale.US, "%.1f", avgLoad),
                            color = tokens.chartColors.trainingLoad,
                            modifier = Modifier.weight(1f)
                        )
                        SummaryPill(
                            title = "Avg Sleep",
                            value = if (avgSleepMins != null) "${avgSleepMins / 60}h ${avgSleepMins % 60}m" else "—",
                            color = tokens.chartColors.sleep,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))

                    val stabilityText = if (scores.size >= 7) {
                        "High-confidence baseline."
                    } else {
                        "Calibrating baseline (${scores.size}/7 days)."
                    }

                    Text(
                        text = stabilityText,
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.textSecondary
                    )
                }
            }

            // Weekly Performance Recap: this week vs the prior week
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
                }
            }

            // Trend Charts: real chronological line charts (oldest to newest) for the selected timeframe
            item {
                val chronologicalDates = (scores.map { it.date } + summaries.map { it.date })
                    .distinct()
                    .sorted()
                val scoreByDate = scores.associateBy { it.date }
                val summaryByDate = summaries.associateBy { it.date }

                if (chronologicalDates.size >= 2) {
                    Column(verticalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.m)) {
                        TrendChartCard(
                            title = "Recovery",
                            latestLabel = scoreByDate[chronologicalDates.last()]?.score?.let { "$it%" } ?: "—",
                            values = chronologicalDates.map { scoreByDate[it]?.score?.toDouble() },
                            color = tokens.scoreColors.primed
                        )
                        TrendChartCard(
                            title = "Day Strain",
                            latestLabel = summaryByDate[chronologicalDates.last()]?.dayStrain?.let { String.format(Locale.US, "%.1f", it) } ?: "—",
                            values = chronologicalDates.map { summaryByDate[it]?.dayStrain },
                            color = tokens.chartColors.trainingLoad
                        )
                        TrendChartCard(
                            title = "Sleep Duration",
                            latestLabel = summaryByDate[chronologicalDates.last()]?.sleepDurationMinutes?.let { "${it / 60}h ${it % 60}m" } ?: "—",
                            values = chronologicalDates.map { summaryByDate[it]?.sleepDurationMinutes?.toDouble() },
                            color = tokens.chartColors.sleep
                        )
                        TrendChartCard(
                            title = "HRV RMSSD",
                            latestLabel = summaryByDate[chronologicalDates.last()]?.hrvRmssd?.let { "${it.toInt()} ms" } ?: "—",
                            values = chronologicalDates.map { summaryByDate[it]?.hrvRmssd },
                            color = tokens.chartColors.hrv
                        )
                        TrendChartCard(
                            title = "Resting Heart Rate",
                            latestLabel = summaryByDate[chronologicalDates.last()]?.restingHeartRate?.let { "${it.toInt()} bpm" } ?: "—",
                            values = chronologicalDates.map { summaryByDate[it]?.restingHeartRate },
                            color = tokens.chartColors.restingHeartRate
                        )
                    }
                }
            }

            // Daily Recovery & Strain History Header
            item {
                Text(
                    text = "Daily Biometric History",
                    style = MaterialTheme.typography.titleLarge,
                    color = tokens.textPrimary,
                    modifier = Modifier.padding(top = HangryTokens.Spacing.s)
                )
            }

            if (scores.isEmpty()) {
                item {
                    HangryCard {
                        Text(
                            text = "No data in this range yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = tokens.textSecondary
                        )
                    }
                }
            } else {
                items(scores, key = { it.date }) { scoreItem ->
                    val matchingSummary = summaries.find { it.date == scoreItem.date }
                    val trainingLoad = matchingSummary?.dailyTrainingLoad ?: 0.0
                    val scoreVal = scoreItem.score
                    val scoreColor = when {
                        scoreVal == null -> tokens.textMuted
                        scoreVal >= 67 -> tokens.scoreColors.primed
                        scoreVal >= 34 -> tokens.scoreColors.balanced
                        else -> tokens.scoreColors.rebuild
                    }

                    HangryCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = scoreItem.date.format(DateTimeFormatter.ofPattern("EEEE, MMM d")),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = tokens.textPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (scoreVal != null) "Recovery: $scoreVal% (${scoreItem.state})" else "Baseline Calibrating",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = scoreColor
                                )
                                if (matchingSummary?.sleepDurationMinutes != null) {
                                    val h = matchingSummary.sleepDurationMinutes / 60
                                    val m = matchingSummary.sleepDurationMinutes % 60
                                    Text(
                                        text = "Sleep: ${h}h ${m}m",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = tokens.textMuted
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = String.format(Locale.US, "%.1f strain", trainingLoad),
                                    style = MaterialTheme.typography.titleSmall,
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
                        }
                    }
                }
            }
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
            if (delta != null && kotlin.math.abs(delta) > 0.05) {
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
private fun TrendChartCard(
    title: String,
    latestLabel: String,
    values: List<Double?>,
    color: Color
) {
    val tokens = LocalHangryTokens.current
    HangryCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, color = tokens.textSecondary)
            Text(text = latestLabel, style = MaterialTheme.typography.titleMedium, color = color)
        }
        Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))
        HangryLineChart(
            values = values,
            color = color,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
        )
    }
}

@Composable
private fun SummaryPill(
    title: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    val tokens = LocalHangryTokens.current
    Surface(
        color = tokens.cardBorder.copy(alpha = 0.5f),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = tokens.textMuted
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                color = color
            )
        }
    }
}
