package com.kevan.hangry.ui.sync

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import com.kevan.hangry.ui.components.HangryCard
import com.kevan.hangry.ui.onboarding.OnboardingStepIndicator
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens

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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(HangryTokens.Spacing.l),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(modifier = Modifier.height(HangryTokens.Spacing.xl))

        if (showStepIndicator) {
            OnboardingStepIndicator(currentStep = 3, totalSteps = 3)
            Spacer(modifier = Modifier.height(HangryTokens.Spacing.l))
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.m)
        ) {
            when (progress.status) {
                SyncStatus.IN_PROGRESS -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(72.dp),
                        strokeWidth = 6.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))
                    Text(
                        text = "Importing Historical Data",
                        style = MaterialTheme.typography.headlineMedium,
                        color = tokens.textPrimary
                    )
                    Text(
                        text = "Reading ${progress.currentDataType}…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.textSecondary
                    )
                }
                SyncStatus.SUCCESS -> {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Success",
                        tint = tokens.scoreColors.primed,
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))
                    Text(
                        text = "Import Complete",
                        style = MaterialTheme.typography.headlineMedium,
                        color = tokens.textPrimary
                    )
                    Text(
                        text = "Your historical baselines and summaries have been computed.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.textSecondary,
                        textAlign = TextAlign.Center
                    )
                }
                SyncStatus.FAILED -> {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = tokens.scoreColors.balanced,
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))
                    Text(
                        text = "Sync Incomplete",
                        style = MaterialTheme.typography.headlineMedium,
                        color = tokens.textPrimary
                    )
                    Text(
                        text = progress.errorMessage ?: "We could not sync all historical data yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.textSecondary,
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
                    Text(text = "Records Read", style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary)
                    Text(text = "${progress.recordsRead}", style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = tokens.cardBorder)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Records Inserted", style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary)
                    Text(text = "${progress.recordsInserted}", style = MaterialTheme.typography.titleMedium, color = tokens.scoreColors.primed)
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = tokens.cardBorder)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Duplicates Skipped", style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary)
                    Text(text = "${progress.recordsSkipped}", style = MaterialTheme.typography.titleMedium, color = tokens.textMuted)
                }
            }
        }

        if (progress.status == SyncStatus.FAILED) {
            OutlinedButton(
                onClick = { retryCount++ },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Retry Sync", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))
        }

        Button(
            onClick = onComplete,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            enabled = (progress.status != SyncStatus.IN_PROGRESS),
            shape = MaterialTheme.shapes.medium
        ) {
            Text(
                text = if (progress.status == SyncStatus.SUCCESS) "Enter Hangry" else "Continue to Dashboard",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}
