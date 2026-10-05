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
                title = { Text(stringResource(R.string.settings_privacy_title)) },
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
                text = stringResource(R.string.settings_privacy_zero_cloud),
                style = MaterialTheme.typography.titleLarge,
                color = tokens.textPrimary
            )
            HangryCard {
                PrivacyDetailItem(
                    icon = Icons.Default.CloudOff,
                    title = stringResource(R.string.settings_privacy_no_servers_title),
                    description = stringResource(R.string.settings_privacy_no_servers_body)
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = tokens.cardBorder)
                PrivacyDetailItem(
                    icon = Icons.Default.NoPhotography,
                    title = stringResource(R.string.settings_privacy_no_tracking_title),
                    description = stringResource(R.string.settings_privacy_no_tracking_body)
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = tokens.cardBorder)
                PrivacyDetailItem(
                    icon = Icons.Default.Lock,
                    title = stringResource(R.string.settings_privacy_sovereignty_title),
                    description = stringResource(R.string.settings_privacy_sovereignty_body)
                )
            }

            // Permission Audit
            Text(
                text = stringResource(R.string.settings_privacy_hc_permissions),
                style = MaterialTheme.typography.titleLarge,
                color = tokens.textPrimary
            )
            HangryCard {
                PrivacyPermissionRow(category = stringResource(R.string.settings_privacy_perm_sleep), purpose = stringResource(R.string.settings_privacy_perm_sleep_purpose))
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = tokens.cardBorder)
                PrivacyPermissionRow(category = stringResource(R.string.settings_privacy_perm_heart), purpose = stringResource(R.string.settings_privacy_perm_heart_purpose))
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = tokens.cardBorder)
                PrivacyPermissionRow(category = stringResource(R.string.settings_privacy_perm_workouts), purpose = stringResource(R.string.settings_privacy_perm_workouts_purpose))
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = tokens.cardBorder)
                PrivacyPermissionRow(category = stringResource(R.string.settings_privacy_perm_activity), purpose = stringResource(R.string.settings_privacy_perm_activity_purpose))
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = tokens.cardBorder)
                PrivacyPermissionRow(category = stringResource(R.string.settings_privacy_perm_weight), purpose = stringResource(R.string.settings_privacy_perm_weight_purpose))
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = tokens.cardBorder)
                PrivacyPermissionRow(category = stringResource(R.string.settings_privacy_perm_markers), purpose = stringResource(R.string.settings_privacy_perm_markers_purpose))
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = tokens.cardBorder)
                PrivacyPermissionRow(category = stringResource(R.string.settings_privacy_perm_cycle), purpose = stringResource(R.string.settings_privacy_perm_cycle_purpose))
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = tokens.cardBorder)
                PrivacyPermissionRow(category = stringResource(R.string.settings_privacy_perm_medical), purpose = stringResource(R.string.settings_privacy_perm_medical_purpose))
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = tokens.cardBorder)
                PrivacyPermissionRow(category = stringResource(R.string.settings_privacy_perm_history), purpose = stringResource(R.string.settings_privacy_perm_history_purpose))
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = tokens.cardBorder)
                PrivacyPermissionRow(category = stringResource(R.string.settings_privacy_perm_background), purpose = stringResource(R.string.settings_privacy_perm_background_purpose))
            }

            // System Control Action
            HangryCard {
                Text(
                    text = stringResource(R.string.settings_privacy_review_permissions),
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
                    Text(stringResource(R.string.settings_privacy_manage_in_settings))
                }
            }

            // AI Features (Optional) - the one place this data flow differs from the rest of the app
            Text(
                text = stringResource(R.string.settings_privacy_ai_title),
                style = MaterialTheme.typography.titleLarge,
                color = tokens.textPrimary
            )
            HangryCard {
                Text(
                    text = stringResource(R.string.settings_privacy_ai_body),
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
