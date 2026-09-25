package com.kevan.hangry.ui.sleep

import com.kevan.hangry.ui.coach.DashMood
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kevan.hangry.R
import com.kevan.hangry.data.local.entity.SleepSessionEntity
import com.kevan.hangry.domain.repository.SleepRepository
import com.kevan.hangry.ui.coach.DashNote
import com.kevan.hangry.ui.components.HangryRingGauge
import com.kevan.hangry.ui.components.HangryCard
import com.kevan.hangry.ui.components.HangryInfoIconButton
import com.kevan.hangry.ui.components.HangryInfoSection
import com.kevan.hangry.ui.components.HangryInfoTip
import com.kevan.hangry.ui.components.HangryPendingNotice
import com.kevan.hangry.ui.components.PastDayNote
import com.kevan.hangry.ui.dashboard.DashboardViewModel
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit

private val SLEEP_COACH_SECTIONS = listOf(
    HangryInfoSection(
        "Tonight's Sleep Need",
        "Your sleep goal, plus part of any sleep you've been short over the last week (last night counts most), plus up to an hour after a harder-than-usual day."
    ),
    HangryInfoSection(
        "Sleep Score",
        "Half is how much of your need you got. The rest is how well you slept: time asleep vs time in bed, deep and REM sleep against healthy ranges, and how regular your bed and wake times are."
    ),
    HangryInfoSection(
        "Quality",
        "How well you slept regardless of length: efficiency, deep + REM, and regular timing."
    ),
    HangryInfoSection(
        "Suggested Bedtime",
        "Your usual wake time minus tonight's need - adjust as your schedule needs."
    )
)

private val SLEEP_STAGE_SECTIONS = listOf(
    HangryInfoSection(
        "Deep Sleep",
        "Cellular repair, growth hormone release & physical recovery."
    ),
    HangryInfoSection(
        "REM Sleep",
        "Cognitive processing, memory consolidation & mental recovery."
    ),
    HangryInfoSection(
        "Light Sleep",
        "Baseline physiological maintenance & body recovery."
    ),
    HangryInfoSection(
        "Awake / Restless",
        "Micro-arousals and wake episodes."
    )
)

private val SLEEP_INFO_SECTIONS = listOf(
    HangryInfoSection(
        "Sleep Score",
        "Blends sleep efficiency, restorative (deep + REM) sleep, and night-to-night consistency - not just how long you slept."
    ),
    HangryInfoSection(
        "Tonight's Sleep Need",
        "Your sleep goal, plus part of any sleep you've been short over the last week (last night counts most), plus up to an hour after a harder-than-usual day."
    ),
    HangryInfoSection(
        "Sleep Score",
        "Half is how much of your need you got. The rest is how well you slept: time asleep vs time in bed, deep and REM sleep against healthy ranges, and how regular your bed and wake times are."
    ),
    HangryInfoSection(
        "Quality",
        "How well you slept regardless of length: efficiency, deep + REM, and regular timing."
    ),
    HangryInfoSection(
        "Suggested Bedtime",
        "Your usual wake time minus tonight's need - adjust as your schedule needs."
    ),
    HangryInfoSection(
        "Deep Sleep",
        "Cellular repair, growth hormone release & physical recovery."
    ),
    HangryInfoSection(
        "REM Sleep",
        "Cognitive processing, memory consolidation & mental recovery."
    ),
    HangryInfoSection(
        "Light Sleep",
        "Baseline physiological maintenance & body recovery."
    ),
    HangryInfoSection(
        "Awake / Restless",
        "Micro-arousals and wake episodes."
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepScreen(
    viewModel: DashboardViewModel,
    sleepRepository: SleepRepository,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val tokens = LocalHangryTokens.current
    val coroutineScope = rememberCoroutineScope()
    var showManualDialog by remember { mutableStateOf(false) }

    val sleepMin = uiState.dailySummary?.sleepDurationMinutes ?: 0
    val analysis = uiState.sleepAnalysis

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_sleep)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    HangryInfoIconButton(title = "About Sleep", sections = SLEEP_INFO_SECTIONS)
                    IconButton(onClick = { showManualDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Log Sleep Manually"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = HangryTokens.Spacing.m, vertical = HangryTokens.Spacing.s),
            verticalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.m)
        ) {
            PastDayNote(date = uiState.selectedDate)
            if (uiState.isPendingSleepData) {
                HangryPendingNotice(
                    message = "Log last night's sleep to see today's insights.",
                    details = "Log last night's sleep to see your sleep score, architecture, and insights for today.",
                    dashMood = DashMood.SLEEPY
                )
            } else if (sleepMin <= 0) {
                // A past day with nothing logged - say so rather than showing empty cards.
                DashNote(mood = DashMood.SLEEPY, text = "No sleep was recorded for this day. Tap + to log it.")
            } else {
                val performance = analysis?.sleepPerformancePercentage
                DashNote(
                    mood = DashMood.SLEEPY,
                    text = when {
                        performance == null -> "Here's how last night went."
                        performance >= 95 -> "You got all the sleep you needed. Well rested!"
                        performance >= 75 -> "You got $performance% of the sleep you needed. Not bad!"
                        else -> "Only $performance% of the sleep you needed. An early night would help."
                    }
                )
                // Sleep Score hero: the night out of 100, with the time and how it was scored.
                HangryCard {
                    val score = analysis?.sleepScore
                    val scoreColor = when {
                        score == null -> tokens.chartColors.sleep
                        score >= 85 -> tokens.scoreColors.primed
                        score >= 65 -> tokens.scoreColors.balanced
                        else -> tokens.scoreColors.rebuild
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HangryRingGauge(
                            progress = (score ?: 0) / 100f,
                            color = scoreColor,
                            modifier = Modifier.size(116.dp),
                            strokeWidth = 10.dp
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(score?.toString() ?: "—", style = MaterialTheme.typography.headlineLarge, color = scoreColor)
                                Text("of 100", style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
                            }
                        }
                        Spacer(modifier = Modifier.width(HangryTokens.Spacing.m))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Sleep score", style = MaterialTheme.typography.titleMedium, color = tokens.textSecondary)
                            Text(
                                text = "${sleepMin / 60}h ${sleepMin % 60}m asleep",
                                style = MaterialTheme.typography.headlineSmall,
                                color = tokens.chartColors.sleep
                            )
                            val need = analysis?.sleepNeedMinutes
                            analysis?.sleepPerformancePercentage?.let { pct ->
                                Text(
                                    text = "$pct% of the ${need?.let { "${it / 60}h ${it % 60}m" } ?: formatGoal(uiState.sleepGoalMinutes)} you needed",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = tokens.textSecondary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(HangryTokens.Spacing.m))
                    // How the score was built - only the parts that were measured.
                    SleepScorePart("Hours vs need", analysis?.sleepPerformancePercentage?.coerceAtMost(100), "50%")
                    SleepScorePart("Time asleep in bed", analysis?.efficiencyScore, "15%")
                    SleepScorePart("Deep & REM sleep", analysis?.restorativeScore, "20%")
                    SleepScorePart("Regular bed & wake times", analysis?.consistencyPercentage, "15%")

                    Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))
                    Text(
                        text = analysis?.supportiveNote ?: stringResource(R.string.sleep_aligned_with_pattern),
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.textPrimary
                    )
                }

                // Metrics Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.m)
                ) {
                    HangryCard(modifier = Modifier.weight(1f)) {
                        Text(text = "Time in Bed", style = MaterialTheme.typography.titleSmall, color = tokens.textSecondary)
                        Spacer(modifier = Modifier.height(8.dp))
                        val inBed = analysis?.timeInBedMinutes
                        Text(
                            text = inBed?.let { "${it / 60}h ${it % 60}m" } ?: "—",
                            style = MaterialTheme.typography.headlineSmall,
                            color = tokens.textPrimary
                        )
                        analysis?.efficiencyPercentage?.takeIf { inBed != null && inBed > sleepMin }?.let {
                            Text("$it% asleep", style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
                        }
                    }
                    HangryCard(modifier = Modifier.weight(1f)) {
                        Text(text = "Sleep Debt", style = MaterialTheme.typography.titleSmall, color = tokens.textSecondary)
                        Spacer(modifier = Modifier.height(8.dp))
                        val debt = analysis?.sleepDebtMinutes ?: 0
                        Text(
                            text = if (debt > 0) "${debt}m" else "0m",
                            style = MaterialTheme.typography.headlineSmall,
                            color = if (debt > 45) tokens.scoreColors.rebuild else tokens.scoreColors.primed
                        )
                    }
                }

                // Sleep Coach: tonight's need, how last night measured up, and a bedtime suggestion
                HangryCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Sleep Guidance",
                                style = MaterialTheme.typography.titleMedium,
                                color = tokens.textPrimary
                            )
                            HangryInfoIconButton(title = "Sleep Guidance", sections = SLEEP_COACH_SECTIONS, compact = true)
                        }
                        // Quality needs stages, time in bed or a few nights of history.
                        analysis?.sleepQualityScore?.let { quality ->
                            val qualityColor = when {
                                quality >= 85 -> tokens.scoreColors.primed
                                quality >= 65 -> tokens.scoreColors.balanced
                                else -> tokens.scoreColors.rebuild
                            }
                            Surface(
                                color = qualityColor.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "Quality $quality/100",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = qualityColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))

                    val need = analysis?.tonightsNeedMinutes ?: uiState.sleepGoalMinutes
                    val performance = analysis?.sleepPerformancePercentage
                    Text(
                        text = "Tonight's need: ${need / 60}h ${need % 60}m" + (performance?.let { " · $it% met last night" } ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.textPrimary
                    )

                    val bedtime = analysis?.recommendedBedtime
                    if (bedtime != null) {
                        Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))
                        HorizontalDivider(color = tokens.cardBorder)
                        Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))
                        Text(
                            text = "Suggested bedtime: ${bedtime.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = tokens.chartColors.sleep
                        )
                    }
                }

                // Sleep Architecture & Stages Breakdown - only when the device recorded stages.
                val deepRecorded = analysis?.deepSleepMinutes
                val remRecorded = analysis?.remSleepMinutes
                if (deepRecorded == null || remRecorded == null) {
                    HangryCard {
                        Text("Sleep stages", style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Your device didn't record sleep stages for this night. A watch or ring that tracks sleep adds deep, REM and light sleep here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.textSecondary
                        )
                    }
                } else {
                val deep = deepRecorded
                val rem = remRecorded
                val light = analysis.lightSleepMinutes ?: 0
                val awake = analysis.awakeMinutes ?: 0
                val totalStageMinutes = maxOf(1, deep + rem + light + awake)
                val restorativePct = analysis.restorativePercentage ?: 0

                HangryCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Sleep Architecture",
                                style = MaterialTheme.typography.titleMedium,
                                color = tokens.textPrimary
                            )
                            HangryInfoIconButton(title = "Sleep Stages", sections = SLEEP_STAGE_SECTIONS, compact = true)
                        }
                        Surface(
                            color = tokens.scoreColors.primed.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "$restorativePct% Restorative",
                                style = MaterialTheme.typography.labelMedium,
                                color = tokens.scoreColors.primed,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Stacked Stage Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                            .background(tokens.cardBorder, RoundedCornerShape(7.dp))
                    ) {
                        if (deep > 0) {
                            Box(
                                modifier = Modifier
                                    .weight(deep.toFloat() / totalStageMinutes)
                                    .fillMaxHeight()
                                    .background(tokens.chartColors.sleepDeep, RoundedCornerShape(topStart = 7.dp, bottomStart = 7.dp))
                            )
                        }
                        if (rem > 0) {
                            Box(
                                modifier = Modifier
                                    .weight(rem.toFloat() / totalStageMinutes)
                                    .fillMaxHeight()
                                    .background(tokens.chartColors.sleepRem)
                            )
                        }
                        if (light > 0) {
                            Box(
                                modifier = Modifier
                                    .weight(light.toFloat() / totalStageMinutes)
                                    .fillMaxHeight()
                                    .background(tokens.chartColors.sleepLight)
                            )
                        }
                        if (awake > 0) {
                            Box(
                                modifier = Modifier
                                    .weight(awake.toFloat() / totalStageMinutes)
                                    .fillMaxHeight()
                                    .background(tokens.chartColors.sleepAwake, RoundedCornerShape(topEnd = 7.dp, bottomEnd = 7.dp))
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    SleepStageRow(
                        color = tokens.chartColors.sleepDeep,
                        name = "Deep Sleep",
                        durationMinutes = deep,
                        percentage = (deep.toDouble() / totalStageMinutes * 100).toInt()
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = tokens.cardBorder)
                    SleepStageRow(
                        color = tokens.chartColors.sleepRem,
                        name = "REM Sleep",
                        durationMinutes = rem,
                        percentage = (rem.toDouble() / totalStageMinutes * 100).toInt()
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = tokens.cardBorder)
                    SleepStageRow(
                        color = tokens.chartColors.sleepLight,
                        name = "Light Sleep",
                        durationMinutes = light,
                        percentage = (light.toDouble() / totalStageMinutes * 100).toInt()
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = tokens.cardBorder)
                    SleepStageRow(
                        color = tokens.chartColors.sleepAwake,
                        name = "Awake / Restless",
                        durationMinutes = awake,
                        percentage = (awake.toDouble() / totalStageMinutes * 100).toInt()
                    )
                }
                }
            }

            // Averages and Consistency Card
            HangryCard {
                Text(text = "Baseline Trends", style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
                Spacer(modifier = Modifier.height(HangryTokens.Spacing.m))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "7-Day Average", style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary)
                    val avg7 = analysis?.sevenDayAverageMinutes
                    Text(text = avg7?.let { "${it / 60}h ${it % 60}m" } ?: "—", style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = tokens.cardBorder)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "30-Day Average", style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary)
                    val avg30 = analysis?.thirtyDayAverageMinutes
                    Text(text = avg30?.let { "${it / 60}h ${it % 60}m" } ?: "—", style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = tokens.cardBorder)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Consistency Index", style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary)
                    val consistency = analysis?.consistencyPercentage
                    Text(
                        text = consistency?.let { "$it%" } ?: "Needs 3 nights",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (consistency != null) tokens.scoreColors.primed else tokens.textMuted
                    )
                }
            }
        }
    }

    // Manual Sleep Entry Dialog
    if (showManualDialog) {
        var hoursInput by remember { mutableStateOf("7") }
        var minsInput by remember { mutableStateOf("30") }
        var isSaving by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isSaving) showManualDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Log Sleep Manually")
                    HangryInfoTip(
                        title = "Manual sleep",
                        body = "Manually entered sleep is clearly marked as manual data and factored into daily summaries."
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = hoursInput,
                        onValueChange = { hoursInput = it },
                        label = { Text("Hours") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = minsInput,
                        onValueChange = { minsInput = it },
                        label = { Text("Minutes") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !isSaving,
                    onClick = {
                        isSaving = true
                        val h = hoursInput.toIntOrNull() ?: 7
                        val m = minsInput.toIntOrNull() ?: 30
                        val totalMinutes = h * 60 + m
                        coroutineScope.launch {
                            val now = Instant.now()
                            val session = SleepSessionEntity(
                                recordFingerprint = "manual-sleep-${now.toEpochMilli()}",
                                sourceRecordId = "manual-${now.toEpochMilli()}",
                                sourcePackageName = "com.kevan.hangry.manual",
                                startTime = now.minus(totalMinutes.toLong(), ChronoUnit.MINUTES),
                                endTime = now,
                                durationMinutes = totalMinutes,
                                timeInBedMinutes = totalMinutes,
                                isManualEntry = true
                            )
                            sleepRepository.insertSessions(listOf(session))
                            viewModel.syncNow()
                            isSaving = false
                            showManualDialog = false
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isSaving,
                    onClick = { showManualDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SleepStageRow(
    color: Color,
    name: String,
    durationMinutes: Int,
    percentage: Int
) {
    val tokens = LocalHangryTokens.current
    val h = durationMinutes / 60
    val m = durationMinutes % 60
    val durationText = if (h > 0) "${h}h ${m}m" else "${m}m"

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                color = tokens.textPrimary
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = durationText,
                style = MaterialTheme.typography.titleSmall,
                color = tokens.textPrimary
            )
            Text(
                text = "$percentage%",
                style = MaterialTheme.typography.labelSmall,
                color = tokens.textSecondary
            )
        }
    }
}

private fun formatGoal(minutes: Int): String = if (minutes % 60 == 0) "${minutes / 60}h" else "${minutes / 60}h ${minutes % 60}m"

/** One part of the Sleep Score as a labelled bar; skipped when it wasn't measured. */
@Composable
private fun SleepScorePart(label: String, value: Int?, weight: String) {
    val tokens = LocalHangryTokens.current
    if (value == null) return
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = tokens.textPrimary, modifier = Modifier.weight(1f))
            Text("$value · $weight", style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
        }
        LinearProgressIndicator(
            progress = { value / 100f },
            color = tokens.chartColors.sleep,
            trackColor = tokens.cardBorder,
            modifier = Modifier.fillMaxWidth().padding(top = 3.dp).height(5.dp).clip(RoundedCornerShape(3.dp))
        )
    }
}
