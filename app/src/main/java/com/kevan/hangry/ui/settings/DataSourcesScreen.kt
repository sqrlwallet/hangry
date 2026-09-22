package com.kevan.hangry.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kevan.hangry.data.local.entity.SyncStateEntity
import com.kevan.hangry.domain.repository.HealthSyncManager
import com.kevan.hangry.ui.components.HangryCard
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataSourcesScreen(
    syncManager: HealthSyncManager,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalHangryTokens.current
    val syncStates by syncManager.getSyncStates().collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Data Sources & Sync Status") },
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
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = HangryTokens.Spacing.m, vertical = HangryTokens.Spacing.s),
            verticalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.m)
        ) {
            item {
                HangryCard {
                    Text(
                        text = "Connected Providers",
                        style = MaterialTheme.typography.titleMedium,
                        color = tokens.textPrimary
                    )
                    Spacer(modifier = Modifier.height(HangryTokens.Spacing.xs))
                    Text(
                        text = "Google Health Connect aggregates readings from your active wearables and fitness tracking applications.",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.textSecondary
                    )
                }
            }

            item {
                Text(
                    text = "Sync Checkpoints by Data Type",
                    style = MaterialTheme.typography.titleLarge,
                    color = tokens.textPrimary,
                    modifier = Modifier.padding(top = HangryTokens.Spacing.s)
                )
            }

            if (syncStates.isEmpty()) {
                item {
                    HangryCard {
                        Text(
                            text = "No sync records yet. Tap 'Sync Now' on the dashboard to pull records.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = tokens.textSecondary
                        )
                    }
                }
            } else {
                items(syncStates, key = { it.dataType }) { state ->
                    SyncStateRow(state = state)
                }
            }
        }
    }
}

@Composable
private fun SyncStateRow(state: SyncStateEntity) {
    val tokens = LocalHangryTokens.current
    HangryCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = state.dataType, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                Text(
                    text = "Status: ${state.syncStatus} • ${state.recordsInserted} inserted",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.syncStatus == "SUCCESS") tokens.scoreColors.primed else tokens.scoreColors.balanced
                )
            }
            if (state.recordsSkipped > 0) {
                Text(
                    text = "${state.recordsSkipped} deduplicated",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.textMuted
                )
            }
        }
    }
}
