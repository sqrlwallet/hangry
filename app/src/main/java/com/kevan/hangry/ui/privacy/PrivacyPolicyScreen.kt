package com.kevan.hangry.ui.privacy

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kevan.hangry.R
import com.kevan.hangry.ui.components.HangryCard
import com.kevan.hangry.ui.components.LocalFirstBanner
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalHangryTokens.current
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy & Data Protection") },
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
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = HangryTokens.Spacing.m, vertical = HangryTokens.Spacing.s),
            verticalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.m)
        ) {
            // Header summary banner
            LocalFirstBanner()

            // Zero-Cloud Commitment
            Text(
                text = "Zero-Cloud Commitment",
                style = MaterialTheme.typography.titleLarge,
                color = tokens.textPrimary
            )
            HangryCard {
                PrivacyDetailItem(
                    icon = Icons.Default.CloudOff,
                    title = "No Servers or Cloud Sync",
                    description = "Only a local database on your phone."
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = tokens.cardBorder)
                PrivacyDetailItem(
                    icon = Icons.Default.NoPhotography,
                    title = "Zero Tracking or Advertising",
                    description = "No analytics, trackers, or ad SDKs."
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = tokens.cardBorder)
                PrivacyDetailItem(
                    icon = Icons.Default.Lock,
                    title = "Full Data Sovereignty",
                    description = "Export as JSON/CSV or delete it all, anytime."
                )
            }

            // Permission Audit
            Text(
                text = "Health Connect Permissions",
                style = MaterialTheme.typography.titleLarge,
                color = tokens.textPrimary
            )
            HangryCard {
                PrivacyPermissionRow(category = "Sleep Sessions", purpose = "Sleep debt, consistency & quality")
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = tokens.cardBorder)
                PrivacyPermissionRow(category = "Heart Rate & HRV", purpose = "Recovery & autonomic tone vs. baseline")
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = tokens.cardBorder)
                PrivacyPermissionRow(category = "Workouts & Exercise", purpose = "Strain, HR zones & training load")
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = tokens.cardBorder)
                PrivacyPermissionRow(category = "Steps, Distance & Calories", purpose = "Daily activity & energy burn")
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = tokens.cardBorder)
                PrivacyPermissionRow(category = "Body Weight", purpose = "Body mass trend over time")
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = tokens.cardBorder)
                PrivacyPermissionRow(category = "Blood Pressure & Blood Sugar", purpose = "Health Records tracking & goals; readings you enter are also written back for other apps")
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = tokens.cardBorder)
                PrivacyPermissionRow(category = "Menstruation (female only)", purpose = "Cycle tracking & predictions")
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = tokens.cardBorder)
                PrivacyPermissionRow(category = "Medical Records (Android 16+)", purpose = "Lab results, vitals, conditions & allergies - read only")
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = tokens.cardBorder)
                PrivacyPermissionRow(category = "Historical Data", purpose = "One-time read on setup to build your baseline")
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = tokens.cardBorder)
                PrivacyPermissionRow(category = "Background Read", purpose = "Refreshes readiness before you open the app")
            }

            // System Control Action
            HangryCard {
                Text(
                    text = "Review or revoke Health Connect permissions anytime in Android Settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        openHealthConnectSettings(context)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(imageVector = Icons.Default.Settings, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Manage in Android Settings")
                }
            }

            // AI Features (Optional) - the one place this data flow differs from the rest of the app
            Text(
                text = "AI Features (Optional)",
                style = MaterialTheme.typography.titleLarge,
                color = tokens.textPrimary
            )
            HangryCard {
                Text(
                    text = "Off by default. When you enable it and add your own OpenRouter key, " +
                        "photos/text you submit for food, supplement or posture analysis - and photos " +
                        "you send to Ask Dash - are sent directly " +
                        "from your device to OpenRouter - never through a Hangry server. When you " +
                        "chat with Ask Dash, your recent health data (including Health Records such " +
                        "as lab results, allergies, conditions, pregnancy and cycle, plus your supplements) is included so " +
                        "answers use your real numbers. Turn it off anytime in Settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary
                )
            }

            // Non-medical notice
            Surface(
                color = tokens.cardBackground,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(HangryTokens.Spacing.m)) {
                    Text(
                        text = stringResource(R.string.wellness_disclaimer_title),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = tokens.textPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.wellness_disclaimer_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.textMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun PrivacyDetailItem(
    icon: ImageVector,
    title: String,
    description: String
) {
    val tokens = LocalHangryTokens.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = tokens.textPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textSecondary
            )
        }
    }
}

@Composable
private fun PrivacyPermissionRow(
    category: String,
    purpose: String
) {
    val tokens = LocalHangryTokens.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = category,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = tokens.textPrimary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = purpose,
            style = MaterialTheme.typography.bodySmall,
            color = tokens.textSecondary
        )
    }
}

private fun openHealthConnectSettings(context: Context) {
    try {
        val intent = Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS")
        context.startActivity(intent)
    } catch (e: Exception) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }
}
