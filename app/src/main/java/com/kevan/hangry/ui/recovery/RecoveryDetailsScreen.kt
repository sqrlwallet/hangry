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
import com.kevan.hangry.ui.components.PastDayNote
import com.kevan.hangry.ui.dashboard.DashboardViewModel
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens

@Composable
private fun recoveryInfoSections(): List<HangryInfoSection> = listOf(
    HangryInfoSection(
        stringResource(R.string.metrics_recovery_info_hrv_title),
        stringResource(R.string.metrics_recovery_info_hrv_body)
    ),
    HangryInfoSection(
        stringResource(R.string.metrics_recovery_info_rhr_title),
        stringResource(R.string.metrics_recovery_info_rhr_body)
    ),
    HangryInfoSection(
        stringResource(R.string.metrics_recovery_info_sleep_title),
        stringResource(R.string.metrics_recovery_info_sleep_body)
    ),
    HangryInfoSection(
        stringResource(R.string.metrics_recovery_info_strain_title),
        stringResource(R.string.metrics_recovery_info_strain_body)
    ),
    HangryInfoSection(
        stringResource(R.string.metrics_recovery_info_algorithm_title),
        stringResource(R.string.metrics_recovery_info_algorithm_body)
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
                            contentDescription = stringResource(R.string.metrics_components_back)
                        )
                    }
                },
                actions = {
                    HangryInfoIconButton(title = stringResource(R.string.metrics_recovery_about), sections = recoveryInfoSections())
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
            // Hero Score
            HangryScoreHero(scoreEntity = scoreEntity, isPending = isPending)

            if (isPending) {
                HangryPendingNotice(
                    message = stringResource(R.string.metrics_recovery_pending_message),
                    details = stringResource(R.string.metrics_recovery_pending_details),
                    dashMood = DashMood.SLEEPY
                )
                return@Column
            }

            scoreEntity?.state?.let { RecoveryState.valueOf(it) }?.let { state ->
                val breathingUp = scoreEntity.negativeContributors.contains("Breathing rate")
                DashNote(
                    mood = if (breathingUp) DashMood.UNWELL else state.dashMood(),
                    text = if (breathingUp) stringResource(R.string.metrics_recovery_breathing_up) else state.dashLine()
                )
            }

            Text(
                text = stringResource(R.string.metrics_recovery_component_contributions),
                style = MaterialTheme.typography.titleLarge,
                color = tokens.textPrimary
            )

            // No HRV from the device: the score assumes an excellent day unless the user says
            // otherwise. With a real reading there's nothing to pick - it's calculated.
            if (scoreEntity?.score != null && scoreEntity.hrvComponentScore == null) {
                HrvFeelingCard(selected = uiState.hrvFeeling, onSelect = viewModel::setHrvFeeling)
            } else {
                RecoveryComponentRow(
                    label = stringResource(R.string.metrics_recovery_hrv),
                    valueColor = tokens.chartColors.hrv,
                    score = scoreEntity?.hrvComponentScore
                )
            }
            RecoveryComponentRow(
                label = stringResource(R.string.metrics_recovery_rhr),
                valueColor = tokens.chartColors.restingHeartRate,
                score = scoreEntity?.rhrComponentScore
            )
            RecoveryComponentRow(
                label = stringResource(R.string.metrics_recovery_sleep),
                valueColor = tokens.chartColors.sleep,
                score = scoreEntity?.sleepComponentScore
            )
            RecoveryComponentRow(
                label = stringResource(R.string.metrics_recovery_strain_balance),
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
                text = stringResource(R.string.metrics_recovery_hrv),
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
                stringResource(R.string.metrics_recovery_hrv_missing_default)
            } else {
                stringResource(R.string.metrics_recovery_hrv_missing_feeling, selected.description.lowercase())
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
    missingLabel: String = stringResource(R.string.metrics_components_calibrating)
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
                text = score?.let { stringResource(R.string.metrics_components_percent_value, it.toInt()) } ?: missingLabel,
                style = if (score != null) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelMedium,
                color = if (score != null) tokens.textPrimary else tokens.textSecondary
            )
        }
    }
}

@Composable
private fun RecoveryState.dashLine(): String = when (this) {
    RecoveryState.PRIMED -> stringResource(R.string.metrics_recovery_dash_primed)
    RecoveryState.BALANCED -> stringResource(R.string.metrics_recovery_dash_balanced)
    RecoveryState.REBUILD -> stringResource(R.string.metrics_recovery_dash_rebuild)
    RecoveryState.BUILDING_BASELINE -> stringResource(R.string.metrics_recovery_dash_baseline)
}
