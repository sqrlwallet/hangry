package com.kevan.hangry.ui.sync

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kevan.hangry.data.datasource.HealthConnectDataSource
import com.kevan.hangry.ui.components.HangryCard
import com.kevan.hangry.ui.onboarding.OnboardingStepIndicator
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoricalSyncSetupScreen(
    onStartSync: (days: Int) -> Unit,
    onNavigateBack: () -> Unit,
    dataSource: HealthConnectDataSource? = null,
    showStepIndicator: Boolean = false,
    modifier: Modifier = Modifier
) {
    val tokens = LocalHangryTokens.current
    var selectedRange by remember { mutableIntStateOf(-1) } // Default to all available data (-1 = unbounded)
    var earliestDate by remember { mutableStateOf<LocalDate?>(null) }

    LaunchedEffect(dataSource) {
        earliestDate = try {
            dataSource?.findEarliestDataDate()
        } catch (_: Exception) {
            null
        }
    }

    val allTimeLabel = remember(earliestDate) {
        val date = earliestDate
        if (date != null) {
            val years = ChronoUnit.DAYS.between(date, LocalDate.now()) / 365.25
            val dateStr = date.format(DateTimeFormatter.ofPattern("MMM yyyy"))
            "All available data (From $dateStr · %.1f yrs - Recommended)".format(years)
        } else {
            "All available data (Full history - Recommended)"
        }
    }

    val ranges = listOf(
        Pair(-1, allTimeLabel),
        Pair(365, "Last 365 days (Annual history)"),
        Pair(90, "Last 90 days (Deeper trends)"),
        Pair(30, "Last 30 days (Standard baseline)"),
        Pair(7, "Last 7 days (Fastest setup)")
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Historical Import") },
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
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(HangryTokens.Spacing.m)
            ) {
                Button(
                    onClick = { onStartSync(selectedRange) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("Start Historical Import", style = MaterialTheme.typography.titleMedium)
                }
            }
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
            if (showStepIndicator) {
                OnboardingStepIndicator(currentStep = 2, totalSteps = 3)
            }

            Text(
                text = "Choose Historical Range",
                style = MaterialTheme.typography.headlineMedium,
                color = tokens.textPrimary
            )

            Text(
                text = "Importing your past health records allows Hangry to calculate your personalized 7-day and 28-day baselines immediately upon launch.",
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.textSecondary
            )

            HangryCard {
                ranges.forEachIndexed { index, (days, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedRange = days }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selectedRange == days),
                            onClick = { selectedRange = days }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = tokens.textPrimary
                            )
                            if (days == -1) {
                                Text(
                                    text = "Imports your entire Health Connect history without date cutoffs.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = tokens.scoreColors.primed
                                )
                            }
                        }
                    }
                    if (index < ranges.size - 1) {
                        HorizontalDivider(color = tokens.cardBorder)
                    }
                }
            }

            Surface(
                color = tokens.scoreColors.buildingBaselineContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(HangryTokens.Spacing.m),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = tokens.scoreColors.buildingBaseline,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Safe Memory Batching: Hangry processes historical records in 14-day bounded batches with duplicate suppression. You can cancel or re-sync at any time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.textPrimary
                    )
                }
            }
        }
    }
}
