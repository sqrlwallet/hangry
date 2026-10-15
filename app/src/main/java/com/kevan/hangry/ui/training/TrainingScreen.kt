package com.kevan.hangry.ui.training

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kevan.hangry.R
import com.kevan.hangry.data.local.entity.ExerciseSessionEntity
import com.kevan.hangry.domain.calculation.ActiveActivityCalculator
import com.kevan.hangry.domain.model.HeartRateZoneDistribution
import com.kevan.hangry.domain.model.WorkoutText
import com.kevan.hangry.domain.repository.HeartRateRepository
import com.kevan.hangry.domain.repository.WorkoutRepository
import com.kevan.hangry.ui.coach.DashExpression
import com.kevan.hangry.ui.coach.DashMood
import com.kevan.hangry.ui.coach.DashNote
import com.kevan.hangry.ui.components.DashEmptyScene
import com.kevan.hangry.ui.components.DashEmptyState
import com.kevan.hangry.ui.components.DateNavigatorBar
import com.kevan.hangry.ui.components.HangryCard
import com.kevan.hangry.ui.components.WorkoutFormat
import com.kevan.hangry.ui.components.HangryInfoIconButton
import com.kevan.hangry.ui.components.HangryInfoSection
import com.kevan.hangry.ui.components.HangryInfoTip
import com.kevan.hangry.ui.components.dayHeading
import com.kevan.hangry.ui.dashboard.DashPullIndicator
import com.kevan.hangry.ui.dashboard.DashboardViewModel
import com.kevan.hangry.ui.navigation.LocalDockInset
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens
import com.kevan.hangry.ui.theme.SurfaceDark
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import androidx.lifecycle.compose.collectAsStateWithLifecycle

// 5-zone cardio palette, from the theme so it adapts to light and dark mode.
private val Zone1Color: Color @Composable get() = LocalHangryTokens.current.chartColors.zones[0] // Active recovery
private val Zone2Color: Color @Composable get() = LocalHangryTokens.current.chartColors.zones[1] // Aerobic base
private val Zone3Color: Color @Composable get() = LocalHangryTokens.current.chartColors.zones[2] // Aerobic tempo
private val Zone4Color: Color @Composable get() = LocalHangryTokens.current.chartColors.zones[3] // Threshold
private val Zone5Color: Color @Composable get() = LocalHangryTokens.current.chartColors.zones[4] // Peak

@Composable
private fun trainingInfoSections(): List<HangryInfoSection> = listOf(
    HangryInfoSection(
        stringResource(R.string.metrics_training_info_zones_title),
        stringResource(R.string.metrics_training_info_zones_body)
    ),
    HangryInfoSection(
        stringResource(R.string.metrics_training_info_zone_purposes_title),
        listOf(
            R.string.metrics_training_zone1_name to R.string.metrics_training_zone1_purpose,
            R.string.metrics_training_zone2_name to R.string.metrics_training_zone2_purpose,
            R.string.metrics_training_zone3_name to R.string.metrics_training_zone3_purpose,
            R.string.metrics_training_zone4_name to R.string.metrics_training_zone4_purpose,
            R.string.metrics_training_zone5_name to R.string.metrics_training_zone5_purpose
        ).map { (name, purpose) ->
            stringResource(R.string.metrics_training_zone_range_purpose, stringResource(name), stringResource(purpose))
        }.joinToString("\n")
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingScreen(
    viewModel: DashboardViewModel,
    workoutRepository: WorkoutRepository,
    heartRateRepository: HeartRateRepository,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val tokens = LocalHangryTokens.current

    val zone = ZoneId.systemDefault()
    val activeDate = uiState.selectedDate
    val isToday = activeDate == LocalDate.now(zone)
    val dayStart = activeDate.atStartOfDay(zone).toInstant()
    val dayEnd = activeDate.plusDays(1).atStartOfDay(zone).toInstant()

    // Scoped to active date - reports workouts and heart rate distribution for the inspected date
    val workouts by remember(dayStart, dayEnd) { workoutRepository.getSessionsBetween(dayStart, dayEnd) }.collectAsStateWithLifecycle(initialValue = emptyList())

    // Collect active date's zone distribution, falling back to 7-day trailing if active date has no samples
    val zones = uiState.heartRateZones
    val zoneLabels = remember(zones) { zones.labels() }
    val todayZoneDistribution by remember(dayStart, zones) { heartRateRepository.getZoneDistribution(dayStart, dayEnd, zones) }
        .collectAsStateWithLifecycle(initialValue = HeartRateZoneDistribution())

    val trailing7dStart = dayStart.minus(7, ChronoUnit.DAYS)
    val weekZoneDistribution by remember(dayStart, zones) { heartRateRepository.getZoneDistribution(trailing7dStart, dayEnd, zones) }
        .collectAsStateWithLifecycle(initialValue = HeartRateZoneDistribution())

    val effectiveDistribution = if (todayZoneDistribution.totalCount > 0) {
        todayZoneDistribution
    } else {
        weekZoneDistribution
    }
    val isTodayData = todayZoneDistribution.totalCount > 0

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val tabReselectionBus = com.kevan.hangry.ui.navigation.LocalTabReselectionBus.current
    LaunchedEffect(tabReselectionBus) {
        tabReselectionBus.events.collect { route ->
            if (route == com.kevan.hangry.ui.navigation.Screen.Training.route) {
                listState.animateScrollToItem(0)
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        // Pull down to sync new workouts from Health Connect, with Dash, like on Today.
        val pullState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = uiState.isSyncing,
            onRefresh = { viewModel.syncNow(days = 3) },
            state = pullState,
            modifier = Modifier.padding(top = innerPadding.calculateTopPadding()),
            indicator = {
                DashPullIndicator(state = pullState, isRefreshing = uiState.isSyncing, modifier = Modifier.align(Alignment.TopCenter))
            }
        ) {
        LazyColumn(
            state = listState,
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = HangryTokens.Spacing.m),
            // Clear of the floating tab bar, which the list scrolls behind.
            contentPadding = PaddingValues(top = HangryTokens.Spacing.s, bottom = HangryTokens.Spacing.m + LocalDockInset.current),
            verticalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.section)
        ) {
            item {
                // The day shown here is the one picked on Today (shared); show it and let it change.
                DateNavigatorBar(
                    selectedDate = activeDate,
                    onDateSelected = { date -> viewModel.selectDate(date) }
                )
            }

            item {
                // A tab without a top bar: active burn in one slim row leads, with the screen's
                // explainer at its end. The workout count is in Dash's note below.
                HangryCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isToday) stringResource(R.string.metrics_training_active_burn_today) else stringResource(R.string.metrics_training_active_burn),
                            style = MaterialTheme.typography.titleSmall,
                            color = tokens.textSecondary,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = stringResource(R.string.metrics_components_kcal_value, uiState.todayActiveCalories.toInt()),
                            style = MaterialTheme.typography.titleMedium,
                            color = tokens.chartColors.trainingLoad
                        )
                        HangryInfoIconButton(title = stringResource(R.string.metrics_training_about), sections = trainingInfoSections(), compact = true)
                    }
                }
            }

            // What was done comes first; the zone breakdown follows.
            item {
                Text(
                    text = dayHeading(stringResource(R.string.metrics_training_workouts), activeDate),
                    style = MaterialTheme.typography.titleMedium,
                    color = tokens.textPrimary
                )
            }

            if (workouts.isEmpty()) {
                item {
                    HangryCard {
                        DashEmptyState(
                            scene = DashEmptyScene.WORKOUTS,
                            title = if (isToday) stringResource(R.string.metrics_training_no_workouts_today) else stringResource(R.string.metrics_training_no_workouts_day),
                            body = if (isToday) stringResource(R.string.metrics_training_no_workouts_body) else null 
                        )
                    }
                }
            } else {
                // Dash cheers on the day's training with his running gear.
                item {
                    val totalMinutes = workouts.sumOf { it.durationMinutes }
                    val kcal = workouts.sumOf { ActiveActivityCalculator.workoutCalories(it, bmr = null) ?: 0.0 }
                    val count = if (workouts.size == 1) stringResource(R.string.metrics_training_a_workout) else stringResource(R.string.metrics_training_n_workouts, workouts.size)
                    val kcalSuffix = if (kcal > 0) stringResource(R.string.metrics_training_kcal_suffix, kcal.toInt()) else "."
                    DashNote(
                        mood = DashMood.WORKOUT,
                        text = if (isToday) {
                            stringResource(R.string.metrics_training_dash_today, count, WorkoutText.durationText(totalMinutes)) + kcalSuffix
                        } else {
                            stringResource(R.string.metrics_training_dash_day, count.replaceFirstChar { it.uppercase() }, WorkoutText.durationText(totalMinutes)) + kcalSuffix
                        }
                    )
                }
                items(workouts, key = { it.id }) { workout ->
                    WorkoutItemCard(
                        workout = workout,
                        modifier = Modifier.animateItem()
                    )
                }
            }

            // Cardio Intensity & Heart Rate Zones Section
            item {
                if (effectiveDistribution.totalCount == 0) {
                    // No heart-rate samples at all: say so, rather than five 0% zones.
                    HangryCard {
                        DashEmptyState(
                            scene = DashEmptyScene.HRV,
                            title = stringResource(R.string.metrics_training_no_hr_title),
                            body = stringResource(R.string.metrics_training_no_hr_body) ,
                            modifier = Modifier.padding(vertical = HangryTokens.Spacing.s)
                        )
                    }
                } else HangryCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = tokens.chartColors.trainingLoad,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.metrics_training_info_zones_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = tokens.textPrimary
                            )
                        }

                        Surface(
                            color = tokens.cardBorder,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = if (isTodayData) stringResource(R.string.metrics_training_zones_today) else stringResource(R.string.metrics_training_zones_7_day),
                                style = MaterialTheme.typography.labelSmall,
                                color = tokens.textSecondary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(HangryTokens.Spacing.m))

                    // Primary Focus Pill
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.metrics_training_cardio_focus),
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.textSecondary
                            )
                            HangryInfoTip(
                                title = stringResource(R.string.metrics_training_cardio_focus),
                                body = effectiveDistribution.physiologicalInsight
                            )
                        }
                        Text(
                            text = effectiveDistribution.primaryFocus,
                            style = MaterialTheme.typography.labelLarge,
                            color = tokens.chartColors.trainingLoad
                        )
                    }

                    Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))

                    // Stacked 5-Zone Visual Bar
                    if (effectiveDistribution.totalCount > 0) {
                        val animZ1 by animateFloatAsState(targetValue = effectiveDistribution.zone1Pct, animationSpec = tween(600, easing = FastOutSlowInEasing), label = "z1")
                        val animZ2 by animateFloatAsState(targetValue = effectiveDistribution.zone2Pct, animationSpec = tween(600, easing = FastOutSlowInEasing), label = "z2")
                        val animZ3 by animateFloatAsState(targetValue = effectiveDistribution.zone3Pct, animationSpec = tween(600, easing = FastOutSlowInEasing), label = "z3")
                        val animZ4 by animateFloatAsState(targetValue = effectiveDistribution.zone4Pct, animationSpec = tween(600, easing = FastOutSlowInEasing), label = "z4")
                        val animZ5 by animateFloatAsState(targetValue = effectiveDistribution.zone5Pct, animationSpec = tween(600, easing = FastOutSlowInEasing), label = "z5")

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(16.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(tokens.cardBorder)
                                .border(BorderStroke(1.dp, tokens.cardBorder.copy(alpha = 0.6f)), RoundedCornerShape(8.dp))
                        ) {
                            if (animZ1 > 0.005f) {
                                Box(
                                    modifier = Modifier
                                        .weight(animZ1.coerceAtLeast(0.01f))
                                        .fillMaxHeight()
                                        .background(Zone1Color)
                                )
                            }
                            if (animZ2 > 0.005f) {
                                Box(
                                    modifier = Modifier
                                        .weight(animZ2.coerceAtLeast(0.01f))
                                        .fillMaxHeight()
                                        .background(Zone2Color)
                                )
                            }
                            if (animZ3 > 0.005f) {
                                Box(
                                    modifier = Modifier
                                        .weight(animZ3.coerceAtLeast(0.01f))
                                        .fillMaxHeight()
                                        .background(Zone3Color)
                                )
                            }
                            if (animZ4 > 0.005f) {
                                Box(
                                    modifier = Modifier
                                        .weight(animZ4.coerceAtLeast(0.01f))
                                        .fillMaxHeight()
                                        .background(Zone4Color)
                                )
                            }
                            if (animZ5 > 0.005f) {
                                Box(
                                    modifier = Modifier
                                        .weight(animZ5.coerceAtLeast(0.01f))
                                        .fillMaxHeight()
                                        .background(Zone5Color)
                                )
                            }
                        }
                    } else {
                        LinearProgressIndicator(
                            progress = { 0f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp)
                                .clip(RoundedCornerShape(6.dp)),
                        )
                    }

                    Spacer(modifier = Modifier.height(HangryTokens.Spacing.m))

                    // 5-Zone Breakdown List
                    ZoneDetailRow(
                        color = Zone1Color,
                        name = stringResource(R.string.metrics_training_zone1_name),
                        range = stringResource(R.string.metrics_training_bpm_range, zoneLabels[0]),
                        percentage = (effectiveDistribution.zone1Pct * 100).toInt()
                    )
                    ZoneDetailRow(
                        color = Zone2Color,
                        name = stringResource(R.string.metrics_training_zone2_name),
                        range = stringResource(R.string.metrics_training_bpm_range, zoneLabels[1]),
                        percentage = (effectiveDistribution.zone2Pct * 100).toInt()
                    )
                    ZoneDetailRow(
                        color = Zone3Color,
                        name = stringResource(R.string.metrics_training_zone3_name),
                        range = stringResource(R.string.metrics_training_bpm_range, zoneLabels[2]),
                        percentage = (effectiveDistribution.zone3Pct * 100).toInt()
                    )
                    ZoneDetailRow(
                        color = Zone4Color,
                        name = stringResource(R.string.metrics_training_zone4_name),
                        range = stringResource(R.string.metrics_training_bpm_range, zoneLabels[3]),
                        percentage = (effectiveDistribution.zone4Pct * 100).toInt()
                    )
                    ZoneDetailRow(
                        color = Zone5Color,
                        name = stringResource(R.string.metrics_training_zone5_name),
                        range = stringResource(R.string.metrics_training_bpm_range, zoneLabels[4]),
                        percentage = (effectiveDistribution.zone5Pct * 100).toInt()
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun ZoneDetailRow(
    color: Color,
    name: String,
    range: String,
    percentage: Int
) {
    val tokens = LocalHangryTokens.current
    val animPct by animateFloatAsState(
        targetValue = (percentage / 100f).coerceIn(0f, 1f),
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "zonePctBar"
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(color)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = tokens.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = range,
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.textMuted
                )
            }

            Text(
                text = stringResource(R.string.metrics_components_percent_value, percentage),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = tokens.textPrimary
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { animPct },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(1.5.dp)),
            color = color,
            trackColor = tokens.cardBorder.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun WorkoutItemCard(
    workout: ExerciseSessionEntity,
    modifier: Modifier = Modifier
) {
    val tokens = LocalHangryTokens.current
    val isDark = tokens.cardBackground == SurfaceDark
    val details = WorkoutText.details(workout)
    val loadColor = tokens.chartColors.trainingLoad

    HangryCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = loadColor.copy(alpha = if (isDark) 0.16f else 0.10f),
                border = BorderStroke(1.dp, loadColor.copy(alpha = if (isDark) 0.35f else 0.22f)),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = WorkoutFormat.icon(workout),
                        contentDescription = null,
                        tint = loadColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = WorkoutText.name(workout),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = tokens.textPrimary
                )
                Text(
                    text = WorkoutText.subtitle(workout),
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary
                )
            }
            // Everything burned during the workout, not just what was above resting.
            ActiveActivityCalculator.workoutCalories(workout, bmr = null)?.let { kcal ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = loadColor.copy(alpha = if (isDark) 0.14f else 0.08f),
                    border = BorderStroke(1.dp, loadColor.copy(alpha = if (isDark) 0.28f else 0.18f))
                ) {
                    Text(
                        text = stringResource(R.string.metrics_components_kcal_value, kcal.toInt()),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = loadColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
        if (details.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = details.joinToString("  ·  "),
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textPrimary,
                modifier = Modifier.padding(start = 56.dp)
            )
        }
        workout.segmentSummary?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textSecondary,
                modifier = Modifier.padding(start = 56.dp, top = 2.dp)
            )
        }
        workout.notes?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 56.dp, top = 2.dp)
            )
        }
        WorkoutRouteSection(workout)
    }
}
