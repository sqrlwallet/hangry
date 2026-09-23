package com.kevan.hangry.ui.recovery

import com.kevan.hangry.domain.model.RecoveryState
import com.kevan.hangry.ui.coach.DashMood
import com.kevan.hangry.ui.coach.DashNote
import com.kevan.hangry.ui.coach.dashMood
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kevan.hangry.R
import com.kevan.hangry.domain.model.HrvFeeling
import com.kevan.hangry.ui.components.HangryCard
import com.kevan.hangry.ui.components.HangryInfoIconButton
import com.kevan.hangry.ui.components.HangryInfoSection
import com.kevan.hangry.ui.components.HangryPendingNotice
import com.kevan.hangry.ui.components.HangryScoreHero
import com.kevan.hangry.ui.dashboard.DashboardViewModel
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens

private val RECOVERY_INFO_SECTIONS = listOf(
    HangryInfoSection(
        "Heart Rate Variability — 35% weight",
        "Reflects parasympathetic autonomic tone. Higher HRV relative to your 7-day rolling baseline indicates systemic readiness. If your device doesn't report HRV, we count it as excellent - and you can change that to how you actually feel."
    ),
    HangryInfoSection(
        "Resting Heart Rate — 25% weight",
        "Reflects cardiovascular recovery. A resting pulse matching or lower than your baseline indicates strong recovery."
    ),
    HangryInfoSection(
        "Sleep Duration — 25% weight",
        "Evaluates actual sleep against your personal 7-day baseline and target duration."
    ),
    HangryInfoSection(
        "Strain & Consistency — 15% weight",
        "Accounts for circadian sleep consistency and acute strain from recent workouts."
    ),
    HangryInfoSection(
        "Algorithm",
        "Hangry Recovery Algorithm - Autonomic-Restorative Heuristic, computed over a 7-day rolling baseline window."
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecoveryDetailsScreen(
    viewModel: DashboardViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val tokens = LocalHangryTokens.current
    val scoreEntity = uiState.recoveryScore
    val isPending = uiState.isPendingSleepData

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_recovery_details)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    HangryInfoIconButton(title = "About Recovery", sections = RECOVERY_INFO_SECTIONS)
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
            // Hero Score
            HangryScoreHero(scoreEntity = scoreEntity, isPending = isPending)

            if (isPending) {
                HangryPendingNotice(
                    message = "Waiting for today's sleep.",
                    details = "Recovery is calculated once today's sleep is recorded. Log it manually from the Sleep screen, or sync after waking.",
                    dashMood = DashMood.SLEEPY
                )
                return@Column
            }

            scoreEntity?.state?.let { RecoveryState.valueOf(it) }?.let { state ->
                DashNote(mood = state.dashMood(), text = state.dashLine())
            }

            Text(
                text = "Component Contributions",
                style = MaterialTheme.typography.titleLarge,
                color = tokens.textPrimary
            )

            // No HRV from the device: the score assumes an excellent day unless the user says
            // otherwise. With a real reading there's nothing to pick - it's calculated.
            if (scoreEntity?.score != null && scoreEntity.hrvComponentScore == null) {
                HrvFeelingCard(selected = uiState.hrvFeeling, onSelect = viewModel::setHrvFeeling)
            } else {
                RecoveryComponentRow(
                    label = "Heart Rate Variability",
                    valueColor = tokens.chartColors.hrv,
                    score = scoreEntity?.hrvComponentScore
                )
            }
            RecoveryComponentRow(
                label = "Resting Heart Rate",
                valueColor = tokens.chartColors.restingHeartRate,
                score = scoreEntity?.rhrComponentScore
            )
            RecoveryComponentRow(
                label = "Sleep Duration",
                valueColor = tokens.chartColors.sleep,
                score = scoreEntity?.sleepComponentScore
            )
            RecoveryComponentRow(
                label = "Strain & Consistency",
                valueColor = tokens.chartColors.trainingLoad,
                score = scoreEntity?.trainingLoadComponentScore
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HrvFeelingCard(selected: HrvFeeling, onSelect: (HrvFeeling) -> Unit) {
    val tokens = LocalHangryTokens.current
    HangryCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Heart Rate Variability",
                style = MaterialTheme.typography.titleMedium,
                color = tokens.chartColors.hrv
            )
            Text(
                text = selected.label,
                style = MaterialTheme.typography.titleMedium,
                color = tokens.textPrimary
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (selected == HrvFeeling.DEFAULT) {
                "Your device didn't report HRV today, so we're counting it as excellent. Feeling different? Pick what fits and your recovery updates."
            } else {
                "Your device didn't report HRV today, so we're using how you feel: ${selected.description.lowercase()}."
            },
            style = MaterialTheme.typography.bodySmall,
            color = tokens.textSecondary
        )
        Spacer(modifier = Modifier.height(10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            HrvFeeling.entries.forEach { feeling ->
                FilterChip(
                    selected = feeling == selected,
                    onClick = { onSelect(feeling) },
                    label = { Text(feeling.label) }
                )
            }
        }
    }
}

@Composable
private fun RecoveryComponentRow(
    label: String,
    valueColor: androidx.compose.ui.graphics.Color,
    score: Double?,
    missingLabel: String = "Calibrating"
) {
    val tokens = LocalHangryTokens.current
    HangryCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = valueColor
            )
            Text(
                text = score?.let { "${it.toInt()}%" } ?: missingLabel,
                style = if (score != null) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelMedium,
                color = if (score != null) tokens.textPrimary else tokens.textSecondary
            )
        }
    }
}

private fun RecoveryState.dashLine(): String = when (this) {
    RecoveryState.PRIMED -> "You're primed. A great day to push."
    RecoveryState.BALANCED -> "Nicely balanced. Train as planned."
    RecoveryState.REBUILD -> "Recovery is low today. Go easy and prioritise sleep."
    RecoveryState.BUILDING_BASELINE -> "Still learning your baseline. Keep syncing and I'll sharpen up."
}
