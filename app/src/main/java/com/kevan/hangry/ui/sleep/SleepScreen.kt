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
import com.kevan.hangry.domain.model.SleepStageCodec
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@Composable
private fun sleepCoachSections(): List<HangryInfoSection> = listOf(
    HangryInfoSection(
        stringResource(R.string.metrics_sleep_tonights_need),
        stringResource(R.string.metrics_sleep_tonights_need_coach_body)
    ),
    HangryInfoSection(
        stringResource(R.string.metrics_sleep_score_title),
        stringResource(R.string.metrics_sleep_score_parts_body)
    ),
    HangryInfoSection(
        stringResource(R.string.metrics_sleep_quality_title),
        stringResource(R.string.metrics_sleep_quality_body)
    ),
    HangryInfoSection(
        stringResource(R.string.metrics_sleep_suggested_bedtime_title),
        stringResource(R.string.metrics_sleep_suggested_bedtime_body)
    )
)

@Composable
private fun sleepStageSections(): List<HangryInfoSection> = listOf(
    HangryInfoSection(
        stringResource(R.string.metrics_sleep_deep_title),
        stringResource(R.string.metrics_sleep_deep_body)
    ),
    HangryInfoSection(
        stringResource(R.string.metrics_sleep_rem_title),
        stringResource(R.string.metrics_sleep_rem_body)
    ),
    HangryInfoSection(
        stringResource(R.string.metrics_sleep_light_title),
        stringResource(R.string.metrics_sleep_light_body)
    ),
    HangryInfoSection(
        stringResource(R.string.metrics_sleep_awake_title),
        stringResource(R.string.metrics_sleep_awake_body)
    )
)

@Composable
private fun sleepInfoSections(): List<HangryInfoSection> = listOf(
    HangryInfoSection(
        stringResource(R.string.metrics_sleep_score_title),
        stringResource(R.string.metrics_sleep_score_blend_body)
    ),
    HangryInfoSection(
        stringResource(R.string.metrics_sleep_tonights_need),
        stringResource(R.string.metrics_sleep_tonights_need_info_body)
    ),
    HangryInfoSection(
        stringResource(R.string.metrics_sleep_score_title),
        stringResource(R.string.metrics_sleep_score_parts_body)
    ),
    HangryInfoSection(
        stringResource(R.string.metrics_sleep_quality_title),
        stringResource(R.string.metrics_sleep_quality_body)
    ),
    HangryInfoSection(
        stringResource(R.string.metrics_sleep_suggested_bedtime_title),
        stringResource(R.string.metrics_sleep_suggested_bedtime_body)
    ),
    HangryInfoSection(
        stringResource(R.string.metrics_sleep_deep_title),
        stringResource(R.string.metrics_sleep_deep_body)
    ),
    HangryInfoSection(
        stringResource(R.string.metrics_sleep_rem_title),
        stringResource(R.string.metrics_sleep_rem_body)
    ),
    HangryInfoSection(
        stringResource(R.string.metrics_sleep_light_title),
        stringResource(R.string.metrics_sleep_light_body)
    ),
    HangryInfoSection(
        stringResource(R.string.metrics_sleep_awake_title),
        stringResource(R.string.metrics_sleep_awake_body)
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

    // The selected night and the six before it, for the hypnogram and weekly stage chart.
    val zone = remember { ZoneId.systemDefault() }
    val weekSessions by remember(uiState.selectedDate) {
        sleepRepository.getSessionsBetween(
            uiState.selectedDate.minusDays(7).atStartOfDay(zone).toInstant(),
            uiState.selectedDate.plusDays(1).atStartOfDay(zone).toInstant()
        )
    }.collectAsState(initial = emptyList())
    val selectedNight = remember(weekSessions) { primaryNightFor(uiState.selectedDate, weekSessions, zone) }
    val weekNights = remember(weekSessions) { weekOfNights(uiState.selectedDate, weekSessions, zone) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_sleep)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.metrics_components_back)
                        )
                    }
                },
                actions = {
                    HangryInfoIconButton(title = stringResource(R.string.metrics_sleep_about_sleep), sections = sleepInfoSections())
                    IconButton(onClick = { showManualDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.metrics_sleep_log_manually)
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
                    message = stringResource(R.string.metrics_sleep_pending_message),
                    details = stringResource(R.string.metrics_sleep_pending_details),
                    dashMood = DashMood.SLEEPY
                )
            } else if (sleepMin <= 0) {
                // A past day with nothing logged - say so rather than showing empty cards.
                HangryCard {
                    com.kevan.hangry.ui.components.DashEmptyState(
                        scene = com.kevan.hangry.ui.components.DashEmptyScene.SLEEP,
                        title = stringResource(R.string.metrics_sleep_none_recorded_title),
                        body = stringResource(R.string.metrics_sleep_none_recorded_body),
                        imageSize = 120.dp
                    )
                }
            } else {
                val performance = analysis?.sleepPerformancePercentage
                DashNote(
                    mood = when {
                        performance == null -> DashMood.SLEEPY
                        performance >= 95 -> DashMood.SLEEP_GREAT
                        performance >= 75 -> DashMood.SLEEPY
                        else -> DashMood.SLEEP_SHORT
                    },
                    text = when {
                        performance == null -> stringResource(R.string.metrics_sleep_dash_default)
                        performance >= 95 -> stringResource(R.string.metrics_sleep_dash_great)
                        performance >= 75 -> stringResource(R.string.metrics_sleep_dash_ok, performance)
                        else -> stringResource(R.string.metrics_sleep_dash_short, performance)
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
                                Text(stringResource(R.string.metrics_sleep_of_100), style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
                            }
                        }
                        Spacer(modifier = Modifier.width(HangryTokens.Spacing.m))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.metrics_sleep_score_label), style = MaterialTheme.typography.titleMedium, color = tokens.textSecondary)
                            Text(
                                text = stringResource(R.string.metrics_sleep_asleep_duration, sleepMin / 60, sleepMin % 60),
                                style = MaterialTheme.typography.headlineSmall,
                                color = tokens.chartColors.sleep
                            )
                            val need = analysis?.sleepNeedMinutes
                            analysis?.sleepPerformancePercentage?.let { pct ->
                                Text(
                                    text = stringResource(R.string.metrics_sleep_pct_of_need, pct, need?.let { stringResource(R.string.metrics_components_duration_h_m, it / 60, it % 60) } ?: formatGoal(uiState.sleepGoalMinutes)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = tokens.textSecondary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(HangryTokens.Spacing.m))
                    // How the score was built - only the parts that were measured.
                    SleepScorePart(stringResource(R.string.metrics_sleep_part_hours_vs_need), analysis?.sleepPerformancePercentage?.coerceAtMost(100), "50%")
                    SleepScorePart(stringResource(R.string.metrics_sleep_part_time_asleep_in_bed), analysis?.efficiencyScore, "15%")
                    SleepScorePart(stringResource(R.string.metrics_sleep_part_deep_rem), analysis?.restorativeScore, "20%")
                    SleepScorePart(stringResource(R.string.metrics_sleep_part_regular_times), analysis?.consistencyPercentage, "15%")

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
                        Text(text = stringResource(R.string.metrics_sleep_time_in_bed), style = MaterialTheme.typography.titleSmall, color = tokens.textSecondary)
                        Spacer(modifier = Modifier.height(8.dp))
                        val inBed = analysis?.timeInBedMinutes
                        Text(
                            text = inBed?.let { stringResource(R.string.metrics_components_duration_h_m, it / 60, it % 60) } ?: "—",
                            style = MaterialTheme.typography.headlineSmall,
                            color = tokens.textPrimary
                        )
                        analysis?.efficiencyPercentage?.takeIf { inBed != null && inBed > sleepMin }?.let {
                            Text(stringResource(R.string.metrics_sleep_pct_asleep, it), style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
                        }
                    }
                    HangryCard(modifier = Modifier.weight(1f)) {
                        Text(text = stringResource(R.string.metrics_sleep_debt), style = MaterialTheme.typography.titleSmall, color = tokens.textSecondary)
                        Spacer(modifier = Modifier.height(8.dp))
                        val debt = analysis?.sleepDebtMinutes ?: 0
                        Text(
                            text = stringResource(R.string.metrics_sleep_minutes_short, if (debt > 0) debt else 0),
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
                                text = stringResource(R.string.metrics_sleep_guidance),
                                style = MaterialTheme.typography.titleMedium,
                                color = tokens.textPrimary
                            )
                            HangryInfoIconButton(title = stringResource(R.string.metrics_sleep_guidance), sections = sleepCoachSections(), compact = true)
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
                                    text = stringResource(R.string.metrics_sleep_quality_value, quality),
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
                        text = stringResource(R.string.metrics_sleep_tonights_need_value, need / 60, need % 60) + (performance?.let { stringResource(R.string.metrics_sleep_need_met_suffix, it) } ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.textPrimary
                    )

                    val bedtime = analysis?.recommendedBedtime
                    if (bedtime != null) {
                        Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))
                        HorizontalDivider(color = tokens.cardBorder)
                        Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))
                        Text(
                            text = stringResource(R.string.metrics_sleep_suggested_bedtime_value, bedtime.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))),
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
                        Text(stringResource(R.string.metrics_sleep_stages_lower), style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.metrics_sleep_no_stages_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.textSecondary
                        )
                    }
                } else {
                val deep = deepRecorded
                val rem = remRecorded
                val light = analysis.lightSleepMinutes ?: 0
                val awake = analysis.awakeMinutes ?: 0
                val restorativePct = analysis.restorativePercentage ?: 0

                HangryCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.metrics_sleep_architecture),
                                style = MaterialTheme.typography.titleMedium,
                                color = tokens.textPrimary
                            )
                            HangryInfoIconButton(title = stringResource(R.string.metrics_sleep_stages_title), sections = sleepStageSections(), compact = true)
                        }
                        Surface(
                            color = tokens.scoreColors.primed.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.metrics_sleep_restorative_pct, restorativePct),
                                style = MaterialTheme.typography.labelMedium,
                                color = tokens.scoreColors.primed,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // The night stage by stage - once the timeline has been synced.
                    val segments = remember(selectedNight) {
                        selectedNight?.let { SleepStageCodec.decode(it.startTime, it.stageSegments) }.orEmpty()
                    }
                    if (segments.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        SleepHypnogram(segments)
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    SleepStageBreakdown(deep = deep, rem = rem, light = light, awake = awake)
                }
                }
            }

            // Stages night by night for the week ending on the selected day.
            if (weekNights.any { it.asleepMinutes > 0 }) {
                HangryCard {
                    Text(stringResource(R.string.metrics_sleep_last_7_nights), style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
                    Spacer(modifier = Modifier.height(4.dp))
                    WeeklySleepStagesChart(weekNights, uiState.selectedDate)
                }
            }

            // Averages and Consistency Card
            HangryCard {
                Text(text = stringResource(R.string.metrics_sleep_baseline_trends), style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
                Spacer(modifier = Modifier.height(HangryTokens.Spacing.m))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = stringResource(R.string.metrics_sleep_7_day_avg), style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary)
                    val avg7 = analysis?.sevenDayAverageMinutes
                    Text(text = avg7?.let { stringResource(R.string.metrics_components_duration_h_m, it / 60, it % 60) } ?: "—", style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = tokens.cardBorder)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = stringResource(R.string.metrics_sleep_30_day_avg), style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary)
                    val avg30 = analysis?.thirtyDayAverageMinutes
                    Text(text = avg30?.let { stringResource(R.string.metrics_components_duration_h_m, it / 60, it % 60) } ?: "—", style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = tokens.cardBorder)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = stringResource(R.string.metrics_sleep_consistency_index), style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary)
                    val consistency = analysis?.consistencyPercentage
                    Text(
                        text = consistency?.let { stringResource(R.string.metrics_components_percent_value, it) } ?: stringResource(R.string.metrics_sleep_needs_3_nights),
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
                    Text(stringResource(R.string.metrics_sleep_log_manually))
                    HangryInfoTip(
                        title = stringResource(R.string.metrics_sleep_manual_title),
                        body = stringResource(R.string.metrics_sleep_manual_body)
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = hoursInput,
                        onValueChange = { hoursInput = it },
                        label = { Text(stringResource(R.string.metrics_sleep_hours)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = minsInput,
                        onValueChange = { minsInput = it },
                        label = { Text(stringResource(R.string.metrics_sleep_minutes)) },
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
                    Text(stringResource(R.string.metrics_components_save))
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isSaving,
                    onClick = { showManualDialog = false }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun formatGoal(minutes: Int): String = if (minutes % 60 == 0) stringResource(R.string.metrics_sleep_hours_short, minutes / 60) else stringResource(R.string.metrics_components_duration_h_m, minutes / 60, minutes % 60)

/** One part of the Sleep Score as a labelled bar; skipped when it wasn't measured. */
@Composable
private fun SleepScorePart(label: String, value: Int?, weight: String) {
    val tokens = LocalHangryTokens.current
    if (value == null) return
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = tokens.textPrimary, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.metrics_sleep_score_part_value, value, weight), style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
        }
        LinearProgressIndicator(
            progress = { value / 100f },
            color = tokens.chartColors.sleep,
            trackColor = tokens.cardBorder,
            modifier = Modifier.fillMaxWidth().padding(top = 3.dp).height(5.dp).clip(RoundedCornerShape(3.dp))
        )
    }
}
