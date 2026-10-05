package com.kevan.hangry.ui.sync

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import com.kevan.hangry.R
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kevan.hangry.domain.model.SyncProgress
import com.kevan.hangry.domain.model.SyncStatus
import com.kevan.hangry.domain.repository.HealthSyncManager
import com.kevan.hangry.ui.coach.DashSpinner
import com.kevan.hangry.ui.components.HangryCard
import com.kevan.hangry.ui.onboarding.OnboardingStepIndicator
import com.kevan.hangry.ui.onboarding.OnboardingSteps
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens
import androidx.compose.ui.res.stringResource

@Composable
fun SyncProgressScreen(
    rangeDays: Int,
    syncManager: HealthSyncManager,
    onComplete: () -> Unit,
    showStepIndicator: Boolean = false,
    modifier: Modifier = Modifier
) {
    val tokens = LocalHangryTokens.current
    var progress by remember { mutableStateOf(SyncProgress(status = SyncStatus.IN_PROGRESS)) }
    var retryCount by remember { mutableStateOf(0) }

    LaunchedEffect(rangeDays, retryCount) {
        progress = SyncProgress(status = SyncStatus.IN_PROGRESS)
        syncManager.syncHistorical(rangeDays).collect { p ->
            progress = p
        }
    }

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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(HangryTokens.Spacing.l),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))

            if (showStepIndicator) {
                OnboardingStepIndicator(currentStep = OnboardingSteps.SYNC, totalSteps = OnboardingSteps.TOTAL)
                Spacer(modifier = Modifier.height(HangryTokens.Spacing.l))
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.m)
            ) {
                when (progress.status) {
                    SyncStatus.IN_PROGRESS -> {
                        // Dash spins while the history comes in.
                        DashSpinner(size = 120.dp, contentDescription = stringResource(R.string.settings_sync_importing))
                        Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))
                        Text(
                            text = stringResource(R.string.settings_sync_importing_title),
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            ),
                            color = androidx.compose.ui.graphics.Color.White
                        )
                        Text(
                            text = stringResource(R.string.settings_sync_reading, progress.currentDataType),
                            style = MaterialTheme.typography.bodyMedium,
                            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.70f)
                        )
                    }
                    SyncStatus.SUCCESS -> {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = stringResource(R.string.settings_sync_success),
                            tint = tokens.scoreColors.primed,
                            modifier = Modifier.size(72.dp)
                        )
                        Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))
                        Text(
                            text = stringResource(R.string.settings_sync_import_complete),
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            ),
                            color = androidx.compose.ui.graphics.Color.White
                        )
                        Text(
                            text = stringResource(R.string.settings_sync_baselines_ready),
                            style = MaterialTheme.typography.bodyMedium,
                            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.70f),
                            textAlign = TextAlign.Center
                        )
                    }
                    SyncStatus.FAILED -> {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = stringResource(R.string.settings_sync_warning),
                            tint = tokens.scoreColors.balanced,
                            modifier = Modifier.size(72.dp)
                        )
                        Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))
                        Text(
                            text = stringResource(R.string.settings_sync_incomplete),
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            ),
                            color = androidx.compose.ui.graphics.Color.White
                        )
                        Text(
                            text = progress.errorMessage ?: stringResource(R.string.settings_sync_incomplete_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.70f),
                            textAlign = TextAlign.Center
                        )
                    }
                    SyncStatus.IDLE -> Unit
                }

                Spacer(modifier = Modifier.height(HangryTokens.Spacing.m))

                // Sync metrics card
                HangryCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = stringResource(R.string.settings_sync_records_read), style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary)
                        Text(text = "${progress.recordsRead}", style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = tokens.cardBorder)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = stringResource(R.string.settings_sync_records_inserted), style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary)
                        Text(text = "${progress.recordsInserted}", style = MaterialTheme.typography.titleMedium, color = tokens.scoreColors.primed)
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = tokens.cardBorder)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = stringResource(R.string.settings_sync_duplicates_skipped), style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary)
                        Text(text = "${progress.recordsSkipped}", style = MaterialTheme.typography.titleMedium, color = tokens.textMuted)
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.s)
            ) {
                if (progress.status == SyncStatus.FAILED) {
                    OutlinedButton(
                        onClick = { retryCount++ },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(27.dp)
                    ) {
                        Text(stringResource(R.string.settings_sync_retry), style = MaterialTheme.typography.titleMedium)
                    }
                }

                Button(
                    onClick = onComplete,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    enabled = (progress.status != SyncStatus.IN_PROGRESS),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(27.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = com.kevan.hangry.ui.theme.BlueRibbon,
                        disabledContainerColor = tokens.cardBorder
                    )
                ) {
                    Text(
                        text = if (progress.status == SyncStatus.SUCCESS) stringResource(R.string.settings_sync_enter_hangry) else stringResource(R.string.settings_sync_continue_to_today),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        ),
                        color = if (progress.status != SyncStatus.IN_PROGRESS) androidx.compose.ui.graphics.Color.White else tokens.textMuted
                    )
                }
            }
        }
    }
}
