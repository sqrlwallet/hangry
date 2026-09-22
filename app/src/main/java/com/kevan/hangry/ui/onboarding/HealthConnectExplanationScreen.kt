package com.kevan.hangry.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kevan.hangry.R
import com.kevan.hangry.ui.components.HangryCard
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthConnectExplanationScreen(
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalHangryTokens.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Connecting Health Data") },
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
                    onClick = onContinue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("Continue to Permissions", style = MaterialTheme.typography.titleMedium)
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
            Text(
                text = "Why Hangry Needs Your Data",
                style = MaterialTheme.typography.headlineMedium,
                color = tokens.textPrimary
            )

            Text(
                text = stringResource(R.string.permission_education_body),
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.textSecondary
            )

            HangryCard {
                Text(
                    text = "🛌 Sleep Sessions",
                    style = MaterialTheme.typography.titleMedium,
                    color = tokens.chartColors.sleep
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Duration & consistency → sleep debt.",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary
                )
            }

            HangryCard {
                Text(
                    text = "💓 Heart Rate & HRV",
                    style = MaterialTheme.typography.titleMedium,
                    color = tokens.chartColors.hrv
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Autonomic balance vs. your baseline.",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary
                )
            }

            HangryCard {
                Text(
                    text = "🏃 Workouts & Daily Steps",
                    style = MaterialTheme.typography.titleMedium,
                    color = tokens.chartColors.trainingLoad
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Daily strain vs. recovery readiness.",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary
                )
            }

            Surface(
                color = tokens.cardBackground,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "🛡️ Calculated entirely on your phone. Nothing is sent to a server.",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textMuted,
                    modifier = Modifier.padding(HangryTokens.Spacing.m)
                )
            }
        }
    }
}
