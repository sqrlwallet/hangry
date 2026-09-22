package com.kevan.hangry.ui.posture

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kevan.hangry.R
import com.kevan.hangry.data.local.entity.PostureScanEntity
import com.kevan.hangry.ui.components.HangryCard
import com.kevan.hangry.ui.components.HangryInfoIconButton
import com.kevan.hangry.ui.components.HangryInfoSection
import com.kevan.hangry.ui.components.HangryPendingNotice
import com.kevan.hangry.ui.components.HangryRingGauge
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens

private val POSTURE_INFO_SECTIONS = listOf(
    HangryInfoSection(
        "How it works",
        "Submit 1–5 photos (front, side, or back) standing or sitting naturally. Athletic or casual clothing is fine. The AI evaluates posture alignment and suggests corrective exercises."
    ),
    HangryInfoSection(
        "Your photos",
        "Kept only in this app's private storage on your device, for your own before/after comparison - never re-uploaded anywhere after the one analysis call."
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostureScreen(
    viewModel: PostureViewModel,
    onNavigateBack: () -> Unit,
    onStartNewScan: () -> Unit,
    onOpenScan: (Long) -> Unit,
    onNavigateToAiSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalHangryTokens.current
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_posture)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    HangryInfoIconButton(title = "About Posture", sections = POSTURE_INFO_SECTIONS)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (!uiState.aiFeaturesEnabled) {
            Column(
                modifier = modifier.fillMaxSize().padding(innerPadding).padding(HangryTokens.Spacing.m),
                verticalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.m)
            ) {
                HangryPendingNotice(message = "Enable AI Features in Settings to run a posture check.")
                HangryCard(modifier = Modifier.clickable { onNavigateToAiSettings() }) {
                    Text("Go to AI Settings", style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = HangryTokens.Spacing.m, vertical = HangryTokens.Spacing.s),
            verticalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.m)
        ) {
            item {
                HangryCard(cornerRadius = HangryTokens.CornerRadii.large, contentPadding = HangryTokens.Spacing.l) {
                    Text(text = "Latest Posture Score", style = MaterialTheme.typography.titleMedium, color = tokens.textSecondary)
                    Spacer(modifier = Modifier.height(HangryTokens.Spacing.m))
                    val latest = uiState.latestScan
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HangryRingGauge(
                            progress = (latest?.score ?: 0) / 100f,
                            color = tokens.chartColors.hrv,
                            modifier = Modifier.size(104.dp),
                            strokeWidth = 10.dp
                        ) {
                            Text(
                                text = latest?.score?.toString() ?: "--",
                                style = MaterialTheme.typography.headlineLarge,
                                color = tokens.chartColors.hrv
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(HangryTokens.Spacing.m))
                    Button(onClick = onStartNewScan, modifier = Modifier.fillMaxWidth()) {
                        Text("New Posture Check")
                    }
                }
            }

            item {
                Text(text = "History", style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
            }

            if (uiState.scans.isEmpty()) {
                item {
                    HangryCard {
                        Text(
                            text = "No posture checks yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = tokens.textSecondary
                        )
                    }
                }
            } else {
                items(uiState.scans, key = { it.id }) { scan ->
                    PostureScanRow(scan = scan, onClick = { onOpenScan(scan.id) }, onDelete = { viewModel.deleteScan(scan) })
                }
            }

            item { Spacer(modifier = Modifier.height(HangryTokens.Spacing.m)) }
        }
    }
}

@Composable
private fun PostureScanRow(scan: PostureScanEntity, onClick: () -> Unit, onDelete: () -> Unit) {
    val tokens = LocalHangryTokens.current
    HangryCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = scan.date.toString(), style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                Text(text = "Score ${scan.score}", style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
            }
            IconButton(onClick = onDelete) {
                Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = "Delete", tint = tokens.textMuted)
            }
        }
    }
}
