package com.kevan.hangry.ui.heart

import com.kevan.hangry.ui.coach.DashMood
import com.kevan.hangry.ui.coach.DashNote
import com.kevan.hangry.ui.components.DashEmptyState
import com.kevan.hangry.ui.components.DashEmptyScene
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kevan.hangry.R
import com.kevan.hangry.domain.repository.HeartRateRepository
import com.kevan.hangry.domain.repository.HRVRepository
import com.kevan.hangry.ui.components.HangryCard
import com.kevan.hangry.ui.components.HangryInfoIconButton
import com.kevan.hangry.ui.components.HangryInfoSection
import com.kevan.hangry.ui.components.HangryInfoTip
import com.kevan.hangry.ui.components.HangrySparkline
import com.kevan.hangry.ui.components.PastDayNote
import com.kevan.hangry.ui.dashboard.DashboardViewModel
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens
import com.kevan.hangry.ui.theme.SurfaceDark
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import com.kevan.hangry.ui.components.rememberToday
import androidx.lifecycle.compose.collectAsStateWithLifecycle

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
    cardioFitnessLoader: com.kevan.hangry.data.repository.CardioFitnessLoader,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val tokens = LocalHangryTokens.current
    val today = rememberToday()

    val rhrList by remember(today) { heartRateRepository.getRestingHeartRates(today.minusDays(28), today) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val hrvList by remember(today) { hrvRepository.getHrvBetween(today.minusDays(28), today) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val hrvByDate = remember(hrvList) { hrvList.associateBy { it.recordDate } }

    // Reloads when a sync brings a new VO2 max or workout.
    val cardio by produceState<com.kevan.hangry.data.repository.CardioFitnessSnapshot?>(null, today, uiState.dailySummary) {
        value = runCatching {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { cardioFitnessLoader.load(today) }
        }.getOrNull()
    }

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

    val isDark = tokens.cardBackground == SurfaceDark
    val rhrSparkline = remember(rhrList) { rhrList.takeLast(14).map { it.restingBpm } }
    val hrvSparkline = remember(hrvList) { hrvList.takeLast(14).map { it.rmssd } }

    Scaffold(
        topBar = {
            TopAppBar(
                expandedHeight = 56.dp,
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
            verticalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.section)
        ) {
            item { PastDayNote(date = uiState.selectedDate) }

            // Dash holds the beating heart when HRV is at or above your normal.
            if (hasHrvBaseline && currentHrv != null && currentHrv >= hrvMean) {
                item {
                    DashNote(
                        mood = DashMood.HEART,
                        text = stringResource(R.string.metrics_heart_dash_hrv_good)
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
                        if (rhrSparkline.size >= 2) {
                            Spacer(modifier = Modifier.height(8.dp))
                            HangrySparkline(
                                points = rhrSparkline,
                                color = tokens.chartColors.restingHeartRate,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        // Today against the 28-day average, or just the average without a reading.
                        val rhrDelta = currentRhr?.let { it - rhrMean }
                        Text(
                            text = when {
                                !hasRhrBaseline -> stringResource(R.string.metrics_heart_resting_baseline)
                                rhrDelta == null -> stringResource(R.string.metrics_heart_28_day_avg_bpm, rhrMean.toInt())
                                rhrDelta < -2.0 -> stringResource(R.string.metrics_heart_rhr_below_avg, kotlin.math.abs(rhrDelta), rhrMean.toInt())
                                rhrDelta > 2.0 -> stringResource(R.string.metrics_heart_rhr_above_avg, rhrDelta, rhrMean.toInt())
                                else -> stringResource(R.string.metrics_heart_rhr_within_avg, rhrMean.toInt())
                            },
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
                        if (hrvSparkline.size >= 2) {
                            Spacer(modifier = Modifier.height(8.dp))
                            HangrySparkline(
                                points = hrvSparkline,
                                color = tokens.chartColors.hrv,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(28.dp)
                            )
                        }
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

                        val animatedBadgeColor by animateColorAsState(
                            targetValue = statusBadgeColor,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "hrvStatusBadgeColor"
                        )

                        Surface(
                            color = animatedBadgeColor.copy(alpha = if (isDark) 0.18f else 0.12f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, animatedBadgeColor.copy(alpha = if (isDark) 0.35f else 0.25f))
                        ) {
                            Text(
                                text = statusBadgeText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = animatedBadgeColor,
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
                    val rawFraction = currentHrv?.let { ((it - displayMin) / totalSpan).toFloat().coerceIn(0f, 1f) }
                    val animatedNeedleFraction by animateFloatAsState(
                        targetValue = rawFraction ?: 0f,
                        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
                        label = "hrvNeedleAnimation"
                    )

                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(26.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(tokens.cardBorder.copy(alpha = if (isDark) 0.45f else 0.65f))
                            .border(1.dp, tokens.cardBorder.copy(alpha = if (isDark) 0.65f else 0.85f), RoundedCornerShape(8.dp))
                    ) {
                        // Optimal Band Highlight, placed against the bar's measured width
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .offset(x = maxWidth * bandStartFraction)
                                .width(maxWidth * (bandEndFraction - bandStartFraction))
                                .background(tokens.scoreColors.primed.copy(alpha = if (isDark) 0.35f else 0.25f))
                        )

                        // Current reading indicator needle, centred on the reading
                        if (rawFraction != null) {
                            val needleWidth = 5.dp
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(needleWidth)
                                    .offset(x = (maxWidth * animatedNeedleFraction - needleWidth / 2).coerceIn(0.dp, maxWidth - needleWidth))
                                    .clip(RoundedCornerShape(3.dp))
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

            cardio?.takeIf { it.vo2Max != null || it.recoveries.isNotEmpty() }?.let { snapshot ->
                item { CardioFitnessCard(snapshot) }
            }

            // Recent daily readings: one compact row per day in a single card.
            item {
                HangryCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = tokens.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.metrics_heart_recent_readings),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = tokens.textPrimary
                        )
                    }
                    if (rhrList.isEmpty()) {
                        DashEmptyState(
                            scene = DashEmptyScene.HRV,
                            title = stringResource(R.string.metrics_heart_no_rhr_title),
                            body = stringResource(R.string.metrics_heart_no_rhr_body),
                            modifier = Modifier.padding(vertical = HangryTokens.Spacing.s)
                        )
                    } else {
                        Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))
                        ReadingRow(
                            date = "",
                            rhr = stringResource(R.string.metrics_heart_rhr_short),
                            hrv = stringResource(R.string.metrics_heart_hrv_short),
                            isHeader = true
                        )
                        val dateFormat = remember { DateTimeFormatter.ofPattern("EEE d MMM") }
                        rhrList.forEach { reading ->
                            HorizontalDivider(color = tokens.cardBorder)
                            ReadingRow(
                                date = reading.recordDate.format(dateFormat),
                                rhr = stringResource(R.string.metrics_components_bpm_value, reading.restingBpm.toInt()),
                                hrv = hrvByDate[reading.recordDate]?.let { stringResource(R.string.metrics_components_ms_value, it.rmssd.toInt()) } ?: "—"
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One day in the recent readings card: date, resting HR and HRV in fixed columns. */
@Composable
private fun ReadingRow(date: String, rhr: String, hrv: String, isHeader: Boolean = false) {
    val tokens = LocalHangryTokens.current
    val valueStyle = if (isHeader) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodyMedium
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = if (isHeader) 2.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(date, style = MaterialTheme.typography.bodyMedium, color = tokens.textPrimary, modifier = Modifier.weight(1f))
        Text(
            rhr,
            style = valueStyle,
            color = if (isHeader) tokens.textMuted else tokens.chartColors.restingHeartRate,
            textAlign = TextAlign.End,
            modifier = Modifier.width(72.dp)
        )
        Text(
            hrv,
            style = valueStyle,
            color = if (isHeader) tokens.textMuted else tokens.chartColors.hrv,
            textAlign = TextAlign.End,
            modifier = Modifier.width(72.dp)
        )
    }
}
