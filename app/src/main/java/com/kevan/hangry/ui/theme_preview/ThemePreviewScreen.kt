package com.kevan.hangry.ui.theme_preview

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kevan.hangry.R
import com.kevan.hangry.domain.model.RecoveryState
import com.kevan.hangry.ui.components.HangryCard
import com.kevan.hangry.ui.components.HangryStatusBadge
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemePreviewScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalHangryTokens.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_theme_preview)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(HangryTokens.Spacing.m),
            verticalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.l)
        ) {
            // Brand Logo & Identity
            HangryCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.hangry_logo),
                        contentDescription = "Hangry Logo",
                        modifier = Modifier.size(64.dp)
                    )
                    Column {
                        Text(
                            text = HangryTokens.APP_NAME,
                            style = MaterialTheme.typography.headlineMedium,
                            color = tokens.textPrimary
                        )
                        Text(
                            text = "Energy, Recovery & Vitality",
                            style = MaterialTheme.typography.bodyMedium,
                            color = tokens.textSecondary
                        )
                    }
                }
            }

            // Brand Hero Visual & Core
            HangryCard {
                Text(
                    text = "Vitality Core & Adaptive Branding",
                    style = MaterialTheme.typography.titleMedium,
                    color = tokens.textPrimary
                )
                Spacer(modifier = Modifier.height(HangryTokens.Spacing.m))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(
                            painter = painterResource(id = R.drawable.hangry_energy_sphere),
                            contentDescription = "Hero Energy Sphere",
                            modifier = Modifier
                                .size(88.dp)
                                .clip(CircleShape)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Vitality Core",
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.textMuted
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_launcher_foreground),
                            contentDescription = "Launcher Icon Foreground",
                            modifier = Modifier
                                .size(88.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color(0xFF0C1014))
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Adaptive Icon",
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.textMuted
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(
                            painter = painterResource(id = R.drawable.hangry_logo),
                            contentDescription = "Emblem",
                            modifier = Modifier.size(88.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "App Emblem",
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.textMuted
                        )
                    }
                }
            }

            // Score State Badges
            HangryCard {
                Text(
                    text = "Score States (Supportive & Non-Clinical)",
                    style = MaterialTheme.typography.titleMedium,
                    color = tokens.textPrimary
                )
                Spacer(modifier = Modifier.height(HangryTokens.Spacing.m))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    HangryStatusBadge(state = RecoveryState.PRIMED)
                    HangryStatusBadge(state = RecoveryState.BALANCED)
                    HangryStatusBadge(state = RecoveryState.REBUILD)
                }
                Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))
                HangryStatusBadge(state = RecoveryState.BUILDING_BASELINE)
            }

            // Metric Colors
            HangryCard {
                Text(
                    text = "Chart & Metric Colors",
                    style = MaterialTheme.typography.titleMedium,
                    color = tokens.textPrimary
                )
                Spacer(modifier = Modifier.height(HangryTokens.Spacing.m))

                ColorSwatch(label = "Sleep", color = tokens.chartColors.sleep)
                ColorSwatch(label = "HRV (RMSSD)", color = tokens.chartColors.hrv)
                ColorSwatch(label = "Resting Heart Rate", color = tokens.chartColors.restingHeartRate)
                ColorSwatch(label = "Training Load", color = tokens.chartColors.trainingLoad)
                ColorSwatch(label = "Steps & Movement", color = tokens.chartColors.steps)
            }

            // Typography Scale
            HangryCard {
                Text(
                    text = "Typography Scale",
                    style = MaterialTheme.typography.titleMedium,
                    color = tokens.textPrimary
                )
                Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))
                Text(
                    text = "87%",
                    style = MaterialTheme.typography.displayLarge,
                    color = tokens.scoreColors.primed
                )
                Text(
                    text = "Headline Medium (24sp)",
                    style = MaterialTheme.typography.headlineMedium,
                    color = tokens.textPrimary
                )
                Text(
                    text = "Body Medium: Practical wellness insights that inform, never shame.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.textSecondary
                )
                Text(
                    text = "LABEL SMALL (11SP)",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.textMuted
                )
            }
        }
    }
}

@Composable
private fun ColorSwatch(label: String, color: Color) {
    val tokens = LocalHangryTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.textSecondary
        )
        Box(
            modifier = Modifier
                .size(width = 48.dp, height = 24.dp)
                .background(color, RoundedCornerShape(4.dp))
        )
    }
}
