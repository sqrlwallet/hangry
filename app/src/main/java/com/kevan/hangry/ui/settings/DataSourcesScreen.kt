package com.kevan.hangry.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kevan.hangry.R
import com.kevan.hangry.data.local.entity.SyncStateEntity
import com.kevan.hangry.domain.repository.HealthSyncManager
import com.kevan.hangry.ui.components.HangryCard
import com.kevan.hangry.ui.components.HangryInfoIconButton
import com.kevan.hangry.ui.components.HangryInfoSection
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataSourcesScreen(
    syncManager: HealthSyncManager,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalHangryTokens.current
    val syncStates by remember(syncManager) { syncManager.getSyncStates() }.collectAsStateWithLifecycle(initialValue = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                expandedHeight = 56.dp,
                title = { Text(stringResource(R.string.settings_data_sources_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back)
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
            verticalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.section)
        ) {
            item {
                HangryCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.settings_data_by_app_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = tokens.textPrimary
                        )
                        HangryInfoIconButton(
                            title = stringResource(R.string.settings_data_by_app_title),
                            sections = listOf(
                                HangryInfoSection("", stringResource(R.string.settings_data_by_app_info)),
                                HangryInfoSection(
                                    stringResource(R.string.settings_google_health_connect),
                                    stringResource(R.string.settings_data_sources_connected_providers_info)
                                )
                            ),
                            compact = true
                        )
                    }
                    Text(
                        text = stringResource(R.string.settings_google_health_connect),
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.textMuted
                    )
                    Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))
                    DataSourcesByApp()
                }
            }

            item {
                HangryCard {
                    Text(
                        text = stringResource(R.string.settings_data_sources_checkpoints_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = tokens.textPrimary
                    )
                    Spacer(modifier = Modifier.height(HangryTokens.Spacing.xs))
                    if (syncStates.isEmpty()) {
                        Text(
                            text = stringResource(R.string.settings_data_sources_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = tokens.textSecondary
                        )
                    } else {
                        syncStates.forEachIndexed { index, state ->
                            if (index > 0) HorizontalDivider(color = tokens.cardBorder.copy(alpha = 0.5f))
                            SyncStateRow(state = state)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncStateRow(state: SyncStateEntity) {
    val tokens = LocalHangryTokens.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(text = state.dataType, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
            Text(
                text = stringResource(R.string.settings_data_sources_status, state.syncStatus, state.recordsInserted),
                style = MaterialTheme.typography.bodySmall,
                color = if (state.syncStatus == "SUCCESS") tokens.scoreColors.primed else tokens.scoreColors.balanced
            )
        }
        if (state.recordsSkipped > 0) {
            Text(
                text = stringResource(R.string.settings_data_sources_deduplicated, state.recordsSkipped),
                style = MaterialTheme.typography.labelSmall,
                color = tokens.textMuted
            )
        }
    }
}
