package com.kevan.hangry.ui.sync

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import com.kevan.hangry.R
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import com.kevan.hangry.ui.components.HangryInfoIconButton
import com.kevan.hangry.ui.components.HangryInfoSection
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kevan.hangry.data.datasource.HealthConnectDataSource
import com.kevan.hangry.ui.components.HangryCard
import com.kevan.hangry.ui.onboarding.OnboardingStepIndicator
import com.kevan.hangry.ui.onboarding.OnboardingSteps
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import androidx.compose.ui.res.stringResource

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

    val allDataFromTemplate = stringResource(R.string.settings_sync_all_data_from)
    val allDataFullHistoryLabel = stringResource(R.string.settings_sync_all_data_full_history)
    val allTimeLabel = remember(earliestDate, allDataFromTemplate, allDataFullHistoryLabel) {
        val date = earliestDate
        if (date != null) {
            val years = ChronoUnit.DAYS.between(date, LocalDate.now()) / 365.25
            val dateStr = date.format(DateTimeFormatter.ofPattern("MMM yyyy"))
            allDataFromTemplate.format(dateStr, years)
        } else {
            allDataFullHistoryLabel
        }
    }

    val ranges = listOf(
        Pair(-1, allTimeLabel),
        Pair(365, stringResource(R.string.settings_sync_range_365)),
        Pair(90, stringResource(R.string.settings_sync_range_90)),
        Pair(30, stringResource(R.string.settings_sync_range_30)),
        Pair(7, stringResource(R.string.settings_sync_range_7))
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(com.kevan.hangry.ui.theme.BackgroundDark)
    ) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(id = R.drawable.onboarding_ambient_bg),
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        0.0f to androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f),
                        0.6f to androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.65f),
                        1.0f to com.kevan.hangry.ui.theme.BackgroundDark.copy(alpha = 0.95f)
                    )
                )
        )

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.settings_sync_historical_import), color = androidx.compose.ui.graphics.Color.White) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.settings_back),
                                tint = androidx.compose.ui.graphics.Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                )
            },
            bottomBar = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                listOf(androidx.compose.ui.graphics.Color.Transparent, com.kevan.hangry.ui.theme.BackgroundDark.copy(alpha = 0.95f))
                            )
                        )
                        .padding(HangryTokens.Spacing.m)
                ) {
                    Button(
                        onClick = { onStartSync(selectedRange) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(27.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = com.kevan.hangry.ui.theme.BlueRibbon
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.settings_sync_start_import),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            ),
                            color = androidx.compose.ui.graphics.Color.White
                        )
                    }
                }
            },
            containerColor = androidx.compose.ui.graphics.Color.Transparent
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = HangryTokens.Spacing.m, vertical = HangryTokens.Spacing.s),
                verticalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.m)
            ) {
                if (showStepIndicator) {
                    OnboardingStepIndicator(currentStep = OnboardingSteps.HISTORY, totalSteps = OnboardingSteps.TOTAL)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.settings_sync_choose_range),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        ),
                        color = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    HangryInfoIconButton(
                        title = stringResource(R.string.settings_sync_historical_import),
                        sections = listOf(
                            HangryInfoSection(
                                stringResource(R.string.settings_sync_info_why_heading),
                                stringResource(R.string.settings_sync_info_why_body)
                            ),
                            HangryInfoSection(
                                stringResource(R.string.settings_sync_info_all_heading),
                                stringResource(R.string.settings_sync_info_all_body)
                            ),
                            HangryInfoSection(
                                stringResource(R.string.settings_sync_info_batching_heading),
                                stringResource(R.string.settings_sync_info_batching_body)
                            )
                        ),
                        compact = true
                    )
                }

                Text(
                    text = stringResource(R.string.settings_sync_more_history),
                    style = MaterialTheme.typography.bodyMedium,
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.70f)
                )

                HangryCard {
                    ranges.forEachIndexed { index, (days, label) ->
                        val isSelected = selectedRange == days
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedRange = days }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { selectedRange = days },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal
                                    ),
                                    color = if (isSelected) androidx.compose.ui.graphics.Color.White else tokens.textPrimary
                                )
                                if (days == -1) {
                                    Text(
                                        text = stringResource(R.string.settings_sync_no_cutoff),
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
            }
        }
    }
}
