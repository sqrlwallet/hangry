package com.kevan.hangry.ui.sleep

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import com.kevan.hangry.ui.components.HangryCard
import com.kevan.hangry.ui.components.HangryInfoIconButton
import com.kevan.hangry.ui.components.HangryInfoSection
import com.kevan.hangry.ui.components.HangryInfoTip
import com.kevan.hangry.ui.components.HangryPendingNotice
import com.kevan.hangry.ui.dashboard.DashboardViewModel
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit

private val SLEEP_COACH_SECTIONS = listOf(
    HangryInfoSection(
        "Tonight's Sleep Need",
        "Personal baseline plus carryover for recent debt and yesterday's strain."
    ),
    HangryInfoSection(
        "Suggested Bedtime",
        "Estimated from your recent wake-time pattern - adjust as your schedule needs."
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
        "Personal baseline plus carryover for recent debt and yesterday's strain."
    ),
    HangryInfoSection(
        "Suggested Bedtime",
        "Estimated from your recent wake-time pattern - adjust as your schedule needs."
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
            if (uiState.isPendingSleepData) {
                HangryPendingNotice(
                    message = "Log last night's sleep to see today's insights.",
                    details = "Log last night's sleep to see your sleep score, architecture, and insights for today."
                )
            } else {
                // Main Sleep Duration Card
                HangryCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Last Sleep Session",
                            style = MaterialTheme.typography.titleMedium,
                            color = tokens.textSecondary
                        )
                        Text(
                            text = stringResource(R.string.sleep_target_label, 8),
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.textMuted
                        )
                    }

                    Spacer(modifier = Modifier.height(HangryTokens.Spacing.m))

                    val hours = sleepMin / 60
                    val mins = sleepMin % 60
                    Text(
                        text = "${hours}h ${mins}m",
                        style = MaterialTheme.typography.displayLarge,
                        color = tokens.chartColors.sleep
                    )

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
                        val inBed = analysis?.timeInBedMinutes ?: (sleepMin + 20)
                        Text(
                            text = "${inBed / 60}h ${inBed % 60}m",
                            style = MaterialTheme.typography.headlineSmall,
                            color = tokens.textPrimary
                        )
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
                        val quality = analysis?.sleepQualityScore ?: 70
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
                    Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))

                    val need = analysis?.sleepNeedMinutes ?: 480
                    val performance = analysis?.sleepPerformancePercentage ?: 100
                    Text(
                        text = "Tonight's need: ${need / 60}h ${need % 60}m · $performance% met last night",
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

                // Sleep Architecture & Stages Breakdown
                val deep = analysis?.deepSleepMinutes ?: 0
                val rem = analysis?.remSleepMinutes ?: 0
                val light = analysis?.lightSleepMinutes ?: 0
                val awake = analysis?.awakeMinutes ?: 0
                val totalStageMinutes = maxOf(1, deep + rem + light + awake)
                val restorativePct = analysis?.restorativePercentage ?: 0

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
                                    .background(Color(0xFF0C6FF9), RoundedCornerShape(topStart = 7.dp, bottomStart = 7.dp))
                            )
                        }
                        if (rem > 0) {
                            Box(
                                modifier = Modifier
                                    .weight(rem.toFloat() / totalStageMinutes)
                                    .fillMaxHeight()
                                    .background(Color(0xFF00A4FF))
                            )
                        }
                        if (light > 0) {
                            Box(
                                modifier = Modifier
                                    .weight(light.toFloat() / totalStageMinutes)
                                    .fillMaxHeight()
                                    .background(Color(0xFF7DBBFF))
                            )
                        }
                        if (awake > 0) {
                            Box(
                                modifier = Modifier
                                    .weight(awake.toFloat() / totalStageMinutes)
                                    .fillMaxHeight()
                                    .background(Color(0xFFFF7E1D), RoundedCornerShape(topEnd = 7.dp, bottomEnd = 7.dp))
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    SleepStageRow(
                        color = Color(0xFF0C6FF9),
                        name = "Deep Sleep",
                        durationMinutes = deep,
                        percentage = (deep.toDouble() / totalStageMinutes * 100).toInt()
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = tokens.cardBorder)
                    SleepStageRow(
                        color = Color(0xFF00A4FF),
                        name = "REM Sleep",
                        durationMinutes = rem,
                        percentage = (rem.toDouble() / totalStageMinutes * 100).toInt()
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = tokens.cardBorder)
                    SleepStageRow(
                        color = Color(0xFF7DBBFF),
                        name = "Light Sleep",
                        durationMinutes = light,
                        percentage = (light.toDouble() / totalStageMinutes * 100).toInt()
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = tokens.cardBorder)
                    SleepStageRow(
                        color = Color(0xFFFF7E1D),
                        name = "Awake / Restless",
                        durationMinutes = awake,
                        percentage = (awake.toDouble() / totalStageMinutes * 100).toInt()
                    )
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
                    val avg7 = analysis?.sevenDayAverageMinutes ?: 480
                    Text(text = "${avg7 / 60}h ${avg7 % 60}m", style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = tokens.cardBorder)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "30-Day Average", style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary)
                    val avg30 = analysis?.thirtyDayAverageMinutes ?: 480
                    Text(text = "${avg30 / 60}h ${avg30 % 60}m", style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = tokens.cardBorder)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Consistency Index", style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary)
                    Text(text = "${analysis?.consistencyPercentage ?: 85}%", style = MaterialTheme.typography.titleMedium, color = tokens.scoreColors.primed)
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
