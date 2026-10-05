package com.kevan.hangry.ui.heart

import com.kevan.hangry.ui.coach.DashMood
import com.kevan.hangry.ui.coach.DashNote
import com.kevan.hangry.ui.components.DashEmptyState
import com.kevan.hangry.ui.components.DashEmptyScene
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kevan.hangry.R
import com.kevan.hangry.domain.repository.HeartRateRepository
import com.kevan.hangry.domain.repository.HRVRepository
import com.kevan.hangry.ui.components.HangryCard
import com.kevan.hangry.ui.components.HangryInfoIconButton
import com.kevan.hangry.ui.components.HangryInfoSection
import com.kevan.hangry.ui.components.HangryInfoTip
import com.kevan.hangry.ui.components.PastDayNote
import com.kevan.hangry.ui.dashboard.DashboardViewModel
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

@Composable
private fun heartInfoSections(): List<HangryInfoSection> = listOf(
    HangryInfoSection(
        stringResource(R.string.metrics_heart_info_hrv_title),
        stringResource(R.string.metrics_heart_info_hrv_body)
    ),
    HangryInfoSection(
        stringResource(R.string.metrics_heart_info_missing_title),
        stringResource(R.string.metrics_heart_hrv_missing_body)
    ),
    HangryInfoSection(
        stringResource(R.string.metrics_heart_info_below_title),
        stringResource(R.string.metrics_heart_info_below_body)
    ),
    HangryInfoSection(
        stringResource(R.string.metrics_heart_info_elevated_title),
        stringResource(R.string.metrics_heart_info_elevated_body)
    ),
    HangryInfoSection(
        stringResource(R.string.metrics_heart_info_rhr_title),
        stringResource(R.string.metrics_heart_rhr_dynamics_body)
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeartMetricsScreen(
    viewModel: DashboardViewModel,
    heartRateRepository: HeartRateRepository,
    hrvRepository: HRVRepository,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val tokens = LocalHangryTokens.current
    val today = LocalDate.now()

    val rhrList by heartRateRepository.getRestingHeartRates(today.minusDays(28), today).collectAsState(initial = emptyList())
    val hrvList by hrvRepository.getHrvBetween(today.minusDays(28), today).collectAsState(initial = emptyList())
    val hrvByDate = remember(hrvList) { hrvList.associateBy { it.recordDate } }

    val currentRhr = uiState.dailySummary?.restingHeartRate
    val currentHrv = uiState.dailySummary?.hrvRmssd

    // HRV Baseline Normal Range Band Calculation (mean ± 1 std dev).
    // Only computed when there's at least one real reading - never fabricated from a
    // hardcoded default, since a band built on an assumed number would look like real data.
    val hrvValues = hrvList.map { it.rmssd }
    val hasHrvBaseline = hrvValues.size >= 3
    val hasAnyHrvData = hrvValues.isNotEmpty() || currentHrv != null
    val hrvMean = if (hasHrvBaseline) hrvValues.average() else (currentHrv ?: 0.0)
    val hrvVariance = if (hasHrvBaseline) hrvValues.map { (it - hrvMean).pow(2) }.average() else 0.0
    val hrvStdDev = sqrt(hrvVariance)
    val hrvLowerBand = max(10.0, hrvMean - hrvStdDev)
    val hrvUpperBand = hrvMean + hrvStdDev

    // RHR Baseline Calculation
    val rhrValues = rhrList.map { it.restingBpm }
    val hasRhrBaseline = rhrValues.size >= 3
    val rhrMean = if (hasRhrBaseline) rhrValues.average() else (currentRhr ?: 60.0)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_heart_metrics)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.metrics_components_back)
                        )
                    }
                },
                actions = {
                    HangryInfoIconButton(title = stringResource(R.string.metrics_heart_about), sections = heartInfoSections())
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
            item { PastDayNote(date = uiState.selectedDate) }

            // Dash holds the beating heart when HRV is at or above your normal.
            if (hasHrvBaseline && currentHrv != null && currentHrv >= hrvMean) {
                item {
                    DashNote(
                        mood = DashMood.HEART,
                        text = stringResource(R.string.metrics_heart_dash_hrv_good, currentHrv.toInt(), hrvMean.toInt())
                    )
                }
            }

            // Heart Metrics Dual Hero Card
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.m)
                ) {
                    HangryCard(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = null,
                                tint = tokens.chartColors.restingHeartRate,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.resting_hr_label),
                                style = MaterialTheme.typography.titleSmall,
                                color = tokens.textSecondary
                            )
                        }
                        Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))
                        Text(
                            text = if (currentRhr != null) stringResource(R.string.metrics_components_bpm_value, currentRhr.toInt()) else "—",
                            style = MaterialTheme.typography.headlineLarge,
                            color = tokens.chartColors.restingHeartRate
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (hasRhrBaseline) stringResource(R.string.metrics_heart_28_day_avg_bpm, rhrMean.toInt()) else stringResource(R.string.metrics_heart_resting_baseline),
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.textMuted
                        )
                    }

                    HangryCard(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = tokens.chartColors.hrv,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.hrv_rmssd_label),
                                style = MaterialTheme.typography.titleSmall,
                                color = tokens.textSecondary
                            )
                        }
                        Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))
                        Text(
                            text = if (currentHrv != null) stringResource(R.string.metrics_components_ms_value, currentHrv.toInt()) else "—",
                            style = MaterialTheme.typography.headlineLarge,
                            color = tokens.chartColors.hrv
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (hasHrvBaseline) stringResource(R.string.metrics_heart_28_day_avg_ms, hrvMean.toInt()) else stringResource(R.string.metrics_heart_rmssd_metric),
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.textMuted
                        )
                    }
                }
            }

            // No HRV source connected: an honest empty state instead of a band built on a fabricated number
            if (!hasAnyHrvData) {
                item {
                    HangryCard {
                        DashEmptyState(
                            scene = DashEmptyScene.HRV,
                            title = stringResource(R.string.metrics_heart_no_hrv_title),
                            body = stringResource(R.string.metrics_heart_no_hrv_body),
                            infoTitle = stringResource(R.string.metrics_heart_no_hrv_info_title),
                            infoBody = stringResource(R.string.metrics_heart_no_hrv_info_intro) + "\n\n" + stringResource(R.string.metrics_heart_hrv_missing_body),
                            modifier = Modifier.padding(vertical = HangryTokens.Spacing.s)
                        )
                    }
                }
            }

            // HRV Optimal Baseline Band Card (μ ± 1σ)
            if (hasAnyHrvData) item {
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
                            Text(
                                text = stringResource(R.string.metrics_heart_hrv_band),
                                style = MaterialTheme.typography.titleMedium,
                                color = tokens.textPrimary
                            )
                            HangryInfoTip(
                                title = stringResource(R.string.metrics_heart_hrv_band_info_title),
                                body = stringResource(R.string.metrics_heart_hrv_band_info_body)
                            )
                        }

                        val statusBadgeText: String
                        val statusBadgeColor: androidx.compose.ui.graphics.Color
                        if (currentHrv == null) {
                            statusBadgeText = stringResource(R.string.metrics_heart_badge_no_reading)
                            statusBadgeColor = tokens.textMuted
                        } else if (currentHrv < hrvLowerBand) {
                            statusBadgeText = stringResource(R.string.metrics_heart_badge_below)
                            statusBadgeColor = tokens.scoreColors.rebuild
                        } else if (currentHrv > hrvUpperBand) {
                            statusBadgeText = stringResource(R.string.metrics_heart_badge_elevated)
                            statusBadgeColor = tokens.chartColors.hrv
                        } else {
                            statusBadgeText = stringResource(R.string.metrics_heart_badge_optimal)
                            statusBadgeColor = tokens.scoreColors.primed
                        }

                        Surface(
                            color = statusBadgeColor.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = statusBadgeText,
                                style = MaterialTheme.typography.labelSmall,
                                color = statusBadgeColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(HangryTokens.Spacing.m))

                    // Band Statistics Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(text = stringResource(R.string.metrics_heart_lower_band), style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
                            Text(text = stringResource(R.string.metrics_components_ms_value, hrvLowerBand.toInt()), style = MaterialTheme.typography.titleSmall, color = tokens.textSecondary)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = stringResource(R.string.metrics_heart_personal_mean), style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
                            Text(text = stringResource(R.string.metrics_components_ms_value, hrvMean.toInt()), style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(text = stringResource(R.string.metrics_heart_upper_band), style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
                            Text(text = stringResource(R.string.metrics_components_ms_value, hrvUpperBand.toInt()), style = MaterialTheme.typography.titleSmall, color = tokens.textSecondary)
                        }
                    }

                    Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))

                    // Visual Band Bar
                    val displayMin = max(5.0, hrvLowerBand - 25.0)
                    val displayMax = hrvUpperBand + 25.0
                    val totalSpan = max(1.0, displayMax - displayMin)

                    val bandStartFraction = ((hrvLowerBand - displayMin) / totalSpan).toFloat().coerceIn(0f, 1f)
                    val bandEndFraction = ((hrvUpperBand - displayMin) / totalSpan).toFloat().coerceIn(0f, 1f)
                    val currentFraction = currentHrv?.let { ((it - displayMin) / totalSpan).toFloat().coerceIn(0f, 1f) }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(tokens.cardBorder)
                    ) {
                        // Optimal Band Highlight
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(bandEndFraction - bandStartFraction)
                                .offset(x = (bandStartFraction * 320).dp) // Approximate visual offset
                                .background(tokens.scoreColors.primed.copy(alpha = 0.35f))
                        )

                        // Current reading indicator needle
                        if (currentFraction != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(4.dp)
                                    .align(Alignment.CenterStart)
                                    .offset(x = (currentFraction * 300).dp)
                                    .background(tokens.chartColors.hrv)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(HangryTokens.Spacing.m))

                    val physiologicalBandNote = when {
                        currentHrv == null -> stringResource(R.string.metrics_heart_note_no_hrv)
                        currentHrv < hrvLowerBand -> stringResource(R.string.metrics_heart_note_below)
                        currentHrv > hrvUpperBand -> stringResource(R.string.metrics_heart_note_elevated)
                        else -> stringResource(R.string.metrics_heart_note_normal)
                    }

                    Text(
                        text = physiologicalBandNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.textSecondary
                    )
                }
            }

            // Resting Heart Rate Recovery Dynamics Card
            if (currentRhr != null && hasRhrBaseline) {
                item {
                    HangryCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.metrics_heart_cardio_recovery),
                                style = MaterialTheme.typography.titleMedium,
                                color = tokens.textPrimary
                            )
                            HangryInfoTip(
                                title = stringResource(R.string.metrics_heart_cardio_recovery_info_title),
                                body = stringResource(R.string.metrics_heart_rhr_dynamics_body)
                            )
                        }
                        Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))

                        val rhrDelta = currentRhr - rhrMean
                        val rhrExplanation = when {
                            rhrDelta < -2.0 -> stringResource(R.string.metrics_heart_rhr_below_avg, kotlin.math.abs(rhrDelta), rhrMean.toInt())
                            rhrDelta > 2.0 -> stringResource(R.string.metrics_heart_rhr_above_avg, rhrDelta, rhrMean.toInt())
                            else -> stringResource(R.string.metrics_heart_rhr_within_avg, rhrMean.toInt())
                        }

                        Text(
                            text = rhrExplanation,
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.textSecondary
                        )
                    }
                }
            }

            // Recent Observations List
            item {
                Text(
                    text = stringResource(R.string.metrics_heart_recent_readings),
                    style = MaterialTheme.typography.titleLarge,
                    color = tokens.textPrimary,
                    modifier = Modifier.padding(top = HangryTokens.Spacing.s)
                )
            }

            if (rhrList.isEmpty()) {
                item {
                    HangryCard {
                        DashEmptyState(
                            scene = DashEmptyScene.HRV,
                            title = stringResource(R.string.metrics_heart_no_rhr_title),
                            body = stringResource(R.string.metrics_heart_no_rhr_body),
                            imageSize = 120.dp,
                            modifier = Modifier.padding(vertical = HangryTokens.Spacing.s)
                        )
                    }
                }
            }

            items(rhrList, key = { it.id }) { item ->
                val matchingHrv = hrvByDate[item.recordDate]
                HangryCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = item.recordDate.toString(),
                                style = MaterialTheme.typography.titleSmall,
                                color = tokens.textPrimary
                            )
                            Text(
                                text = stringResource(R.string.data_source_label, item.sourcePackageName?.substringAfterLast('.') ?: "Health Connect"),
                                style = MaterialTheme.typography.labelSmall,
                                color = tokens.textMuted
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = stringResource(R.string.metrics_components_bpm_value, item.restingBpm.toInt()),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = tokens.chartColors.restingHeartRate
                                )
                                Text(text = stringResource(R.string.metrics_heart_rhr_short), style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                val hrvText = if (matchingHrv != null) stringResource(R.string.metrics_components_ms_value, matchingHrv.rmssd.toInt()) else stringResource(R.string.metrics_heart_not_available)
                                Text(
                                    text = hrvText,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = tokens.chartColors.hrv
                                )
                                Text(text = stringResource(R.string.metrics_heart_hrv_short), style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
                            }
                        }
                    }
                }
            }
        }
    }
}
