package com.kevan.hangry.ui.training

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kevan.hangry.R
import com.kevan.hangry.data.local.entity.ExerciseSessionEntity
import com.kevan.hangry.domain.calculation.ActiveActivityCalculator
import com.kevan.hangry.domain.model.HeartRateZoneDistribution
import com.kevan.hangry.domain.model.WorkoutText
import com.kevan.hangry.domain.repository.HeartRateRepository
import com.kevan.hangry.domain.repository.WorkoutRepository
import com.kevan.hangry.ui.components.HangryCard
import com.kevan.hangry.ui.components.WorkoutFormat
import com.kevan.hangry.ui.components.HangryInfoIconButton
import com.kevan.hangry.ui.components.HangryInfoSection
import com.kevan.hangry.ui.dashboard.DashboardViewModel
import com.kevan.hangry.ui.navigation.LocalDockInset
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale

// 5-Zone Cardio Palette
private val Zone1Color = Color(0xFF00A4FF) // Azure Radiance (Active Recovery)
private val Zone2Color = Color(0xFF01A652) // Green Haze (Aerobic Base)
private val Zone3Color = Color(0xFFFFCE00) // Supernova (Aerobic Tempo)
private val Zone4Color = Color(0xFFFF7E1D) // Pumpkin (Threshold)
private val Zone5Color = Color(0xFFE5484D) // Crimson (Peak VO2)

private val TRAINING_INFO_SECTIONS = listOf(
    HangryInfoSection(
        "Heart Rate Zones",
        "5 zones from active recovery to peak effort, based on continuous heart-rate samples. " +
            "Today's zone mix is shown when available, falling back to a 7-day window otherwise."
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
    val uiState by viewModel.uiState.collectAsState()
    val tokens = LocalHangryTokens.current

    val zone = ZoneId.systemDefault()
    val activeDate = uiState.selectedDate
    val isToday = activeDate == LocalDate.now(zone)
    val dayStart = activeDate.atStartOfDay(zone).toInstant()
    val dayEnd = activeDate.plusDays(1).atStartOfDay(zone).toInstant()

    // Scoped to active date - reports workouts and heart rate distribution for the inspected date
    val workouts by workoutRepository.getSessionsBetween(dayStart, dayEnd).collectAsState(initial = emptyList())

    // Collect active date's zone distribution, falling back to 7-day trailing if active date has no samples
    val todayZoneDistribution by heartRateRepository.getZoneDistribution(dayStart, dayEnd)
        .collectAsState(initial = HeartRateZoneDistribution())

    val trailing7dStart = dayStart.minus(7, ChronoUnit.DAYS)
    val weekZoneDistribution by heartRateRepository.getZoneDistribution(trailing7dStart, dayEnd)
        .collectAsState(initial = HeartRateZoneDistribution())

    val effectiveDistribution = if (todayZoneDistribution.totalCount > 0) {
        todayZoneDistribution
    } else {
        weekZoneDistribution
    }
    val isTodayData = todayZoneDistribution.totalCount > 0

    Scaffold(
        topBar = {
            TopAppBar(
                // A tab now, so no back arrow.
                title = { Text(stringResource(R.string.title_training)) },
                actions = {
                    HangryInfoIconButton(title = "About Training & Zones", sections = TRAINING_INFO_SECTIONS)
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
            // Clear of the floating tab bar, which the list scrolls behind.
            contentPadding = PaddingValues(top = HangryTokens.Spacing.s, bottom = HangryTokens.Spacing.m + LocalDockInset.current),
            verticalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.m)
        ) {
            item {
                // Today's Training Summary Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.m)
                ) {
                    HangryCard(modifier = Modifier.weight(1f)) {
                        Text(text = if (isToday) "Workouts Today" else "Workouts", style = MaterialTheme.typography.titleSmall, color = tokens.textSecondary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${workouts.size}",
                            style = MaterialTheme.typography.headlineSmall,
                            color = tokens.textPrimary
                        )
                    }
                    HangryCard(modifier = Modifier.weight(1f)) {
                        Text(text = if (isToday) "Active Burn Today" else "Active Burn", style = MaterialTheme.typography.titleSmall, color = tokens.textSecondary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${uiState.todayActiveCalories.toInt()} kcal",
                            style = MaterialTheme.typography.headlineSmall,
                            color = tokens.chartColors.trainingLoad
                        )
                    }
                }
            }

            // Cardio Intensity & Heart Rate Zones Section
            item {
                HangryCard {
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
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Heart Rate Zones",
                                style = MaterialTheme.typography.titleMedium,
                                color = tokens.textPrimary
                            )
                        }

                        Surface(
                            color = tokens.cardBorder,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = if (isTodayData) "TODAY" else "7-DAY WINDOW",
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
                        Text(
                            text = "Cardio Focus",
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.textSecondary
                        )
                        Text(
                            text = effectiveDistribution.primaryFocus,
                            style = MaterialTheme.typography.labelLarge,
                            color = tokens.chartColors.trainingLoad
                        )
                    }

                    Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))

                    // Stacked 5-Zone Visual Bar
                    if (effectiveDistribution.totalCount > 0) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(16.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(tokens.cardBorder)
                        ) {
                            if (effectiveDistribution.zone1Pct > 0f) {
                                Box(
                                    modifier = Modifier
                                        .weight(effectiveDistribution.zone1Pct.coerceAtLeast(0.01f))
                                        .fillMaxHeight()
                                        .background(Zone1Color)
                                )
                            }
                            if (effectiveDistribution.zone2Pct > 0f) {
                                Box(
                                    modifier = Modifier
                                        .weight(effectiveDistribution.zone2Pct.coerceAtLeast(0.01f))
                                        .fillMaxHeight()
                                        .background(Zone2Color)
                                )
                            }
                            if (effectiveDistribution.zone3Pct > 0f) {
                                Box(
                                    modifier = Modifier
                                        .weight(effectiveDistribution.zone3Pct.coerceAtLeast(0.01f))
                                        .fillMaxHeight()
                                        .background(Zone3Color)
                                )
                            }
                            if (effectiveDistribution.zone4Pct > 0f) {
                                Box(
                                    modifier = Modifier
                                        .weight(effectiveDistribution.zone4Pct.coerceAtLeast(0.01f))
                                        .fillMaxHeight()
                                        .background(Zone4Color)
                                )
                            }
                            if (effectiveDistribution.zone5Pct > 0f) {
                                Box(
                                    modifier = Modifier
                                        .weight(effectiveDistribution.zone5Pct.coerceAtLeast(0.01f))
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
                        name = "Zone 1: Active Recovery",
                        range = "< 114 bpm",
                        percentage = (effectiveDistribution.zone1Pct * 100).toInt(),
                        purpose = "Warm-up & Restoration"
                    )
                    ZoneDetailRow(
                        color = Zone2Color,
                        name = "Zone 2: Aerobic Base",
                        range = "114–133 bpm",
                        percentage = (effectiveDistribution.zone2Pct * 100).toInt(),
                        purpose = "Mitochondrial Density"
                    )
                    ZoneDetailRow(
                        color = Zone3Color,
                        name = "Zone 3: Aerobic Tempo",
                        range = "133–152 bpm",
                        percentage = (effectiveDistribution.zone3Pct * 100).toInt(),
                        purpose = "Cardiovascular Stamina"
                    )
                    ZoneDetailRow(
                        color = Zone4Color,
                        name = "Zone 4: Lactate Threshold",
                        range = "152–171 bpm",
                        percentage = (effectiveDistribution.zone4Pct * 100).toInt(),
                        purpose = "Speed Endurance"
                    )
                    ZoneDetailRow(
                        color = Zone5Color,
                        name = "Zone 5: Peak / VO2 Max",
                        range = "≥ 171 bpm",
                        percentage = (effectiveDistribution.zone5Pct * 100).toInt(),
                        purpose = "Anaerobic Power"
                    )

                    Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))

                    Text(
                        text = effectiveDistribution.physiologicalInsight,
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.textSecondary
                    )
                }
            }

            item {
                Text(
                    text = "Today's Workouts",
                    style = MaterialTheme.typography.titleLarge,
                    color = tokens.textPrimary,
                    modifier = Modifier.padding(top = HangryTokens.Spacing.s)
                )
            }

            if (workouts.isEmpty()) {
                item {
                    HangryCard {
                        Text(
                            text = stringResource(R.string.training_load_rest_day),
                            style = MaterialTheme.typography.bodyMedium,
                            color = tokens.textSecondary
                        )
                    }
                }
            } else {
                items(workouts, key = { it.id }) { workout ->
                    WorkoutItemCard(workout = workout)
                }
            }

            item {
                Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))
            }
        }
    }
}

@Composable
private fun ZoneDetailRow(
    color: Color,
    name: String,
    range: String,
    percentage: Int,
    purpose: String
) {
    val tokens = LocalHangryTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
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
            Column {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textPrimary
                )
                Text(
                    text = "$range • $purpose",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.textMuted
                )
            }
        }

        Text(
            text = "$percentage%",
            style = MaterialTheme.typography.titleSmall,
            color = tokens.textPrimary
        )
    }
}

@Composable
private fun WorkoutItemCard(workout: ExerciseSessionEntity) {
    val tokens = LocalHangryTokens.current
    val details = WorkoutText.details(workout)
    HangryCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = WorkoutFormat.icon(workout),
                contentDescription = null,
                tint = tokens.chartColors.trainingLoad,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = WorkoutText.name(workout),
                    style = MaterialTheme.typography.titleSmall,
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
                Text(
                    text = "${kcal.toInt()} kcal",
                    style = MaterialTheme.typography.titleSmall,
                    color = tokens.chartColors.trainingLoad
                )
            }
        }
        if (details.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = details.joinToString("  ·  "),
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textPrimary,
                modifier = Modifier.padding(start = 36.dp)
            )
        }
        workout.segmentSummary?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textSecondary,
                modifier = Modifier.padding(start = 36.dp, top = 2.dp)
            )
        }
        workout.notes?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 36.dp, top = 2.dp)
            )
        }
    }
}
