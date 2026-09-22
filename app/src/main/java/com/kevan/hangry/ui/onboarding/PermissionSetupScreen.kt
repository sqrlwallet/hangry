package com.kevan.hangry.ui.onboarding

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import com.kevan.hangry.R
import com.kevan.hangry.data.datasource.HealthConnectDataSource
import com.kevan.hangry.data.datasource.RealHealthConnectDataSource
import com.kevan.hangry.ui.components.HangryCard
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionSetupScreen(
    dataSource: HealthConnectDataSource,
    onProceedToHistoricalSync: () -> Unit,
    providerStatus: Int = HealthConnectClient.SDK_UNAVAILABLE,
    modifier: Modifier = Modifier
) {
    val tokens = LocalHangryTokens.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isAvailable by remember { mutableStateOf(false) }
    var hasFullPermissions by remember { mutableStateOf(false) }
    var permissionRequested by remember { mutableStateOf(false) }
    var categoryStatus by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }

    val updateRequired = providerStatus == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED

    suspend fun refreshCategoryStatus() {
        categoryStatus = (dataSource as? RealHealthConnectDataSource)?.getPermissionCategoryStatus() ?: emptyMap()
    }

    LaunchedEffect(Unit) {
        isAvailable = dataSource.isAvailable()
        hasFullPermissions = dataSource.hasPermissions()
        refreshCategoryStatus()
    }

    val requestPermissionsLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        permissionRequested = true
        coroutineScope.launch {
            hasFullPermissions = dataSource.hasPermissions()
            refreshCategoryStatus()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Permission Setup") },
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
                    onClick = onProceedToHistoricalSync,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        text = when {
                            hasFullPermissions -> "Proceed to Historical Sync"
                            !isAvailable -> "Continue Without Health Connect"
                            else -> "Continue with Available Permissions"
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
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
                text = "Authorize Health Connect",
                style = MaterialTheme.typography.headlineMedium,
                color = tokens.textPrimary
            )

            Text(
                text = "Hangry requests read-only access to synchronize your recorded activity and vitals into local storage.",
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.textSecondary
            )

            if (updateRequired) {
                Surface(
                    color = tokens.scoreColors.balancedContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(HangryTokens.Spacing.m)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = tokens.scoreColors.balanced
                            )
                            Text(
                                text = "Health Connect needs an update before Hangry can connect to it.",
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.textPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))
                        TextButton(onClick = { openHealthConnectStoreListing(context) }) {
                            Text("Update Health Connect")
                        }
                    }
                }
            } else if (!isAvailable) {
                Surface(
                    color = tokens.scoreColors.buildingBaselineContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(HangryTokens.Spacing.m)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = tokens.scoreColors.buildingBaseline
                            )
                            Text(
                                text = "Health Connect isn't installed on this device. Hangry has no data to show until it's installed and connected.",
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.textPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))
                        TextButton(onClick = { openHealthConnectStoreListing(context) }) {
                            Text("Install Health Connect")
                        }
                    }
                }
            } else {
                Button(
                    onClick = {
                        requestPermissionsLauncher.launch(RealHealthConnectDataSource.PERMISSIONS)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(containerColor = tokens.scoreColors.primed)
                ) {
                    Text("Grant Health Connect Permissions")
                }
            }

            // Permission checklist - reflects real per-category grant state, falling back to the
            // aggregate flag while Health Connect itself is unavailable.
            HangryCard {
                val categories = RealHealthConnectDataSource.PERMISSION_CATEGORIES.keys.toList()
                categories.forEachIndexed { index, name ->
                    val isGranted = if (categoryStatus.isNotEmpty()) {
                        categoryStatus[name] ?: false
                    } else {
                        hasFullPermissions || !isAvailable
                    }
                    PermissionCheckRow(name = name, isGranted = isGranted)
                    if (index != categories.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = tokens.cardBorder)
                    }
                }
            }

            if (permissionRequested && !hasFullPermissions && isAvailable) {
                Text(
                    text = stringResource(R.string.permission_denied_banner),
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.scoreColors.balanced
                )
            }
        }
    }
}

@Composable
private fun PermissionCheckRow(name: String, isGranted: Boolean) {
    val tokens = LocalHangryTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = name, style = MaterialTheme.typography.bodyMedium, color = tokens.textPrimary)
        if (isGranted) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Granted",
                tint = tokens.scoreColors.primed,
                modifier = Modifier.size(20.dp)
            )
        } else {
            Text(text = "Pending", style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
        }
    }
}

/**
 * The `market://` scheme has no handler on devices without the Play Store (common on emulators
 * and some Android builds), which throws instead of failing gracefully - falls back to the
 * https Play Store URL, which any browser can open.
 */
private fun openHealthConnectStoreListing(context: Context) {
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata"))
        )
    } catch (_: ActivityNotFoundException) {
        try {
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata")
                )
            )
        } catch (_: ActivityNotFoundException) {
            // No app can handle either link - nothing more we can do here.
        }
    }
}
