package com.kevan.hangry.ui.background

import android.Manifest
import android.content.ActivityNotFoundException
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.kevan.hangry.data.background.BackgroundAccess
import com.kevan.hangry.data.background.BackgroundAccessState
import com.kevan.hangry.data.background.BackgroundReadStatus
import com.kevan.hangry.data.worker.HealthSyncWorker
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens
import kotlinx.coroutines.launch

/**
 * The live state of background access, re-read whenever the screen comes back (the battery and
 * notification answers are given in system screens), plus the actions that ask for each piece.
 */
@Composable
fun rememberBackgroundAccess(): Pair<BackgroundAccessState?, BackgroundAccessActions> {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<BackgroundAccessState?>(null) }
    fun refresh() = scope.launch { state = BackgroundAccess.state(context) }

    val healthLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        if (granted.containsAll(BackgroundAccess.BACKGROUND_PERMISSION)) HealthSyncWorker.syncSoon(context)
        refresh()
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }
    val batteryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh() }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val actions = remember {
        BackgroundAccessActions(
            askHealthRead = { healthLauncher.launch(BackgroundAccess.BACKGROUND_PERMISSION) },
            askBattery = {
                try {
                    batteryLauncher.launch(BackgroundAccess.batteryExemptionIntent(context))
                } catch (_: ActivityNotFoundException) {
                    runCatching { batteryLauncher.launch(BackgroundAccess.batterySettingsIntent()) }
                }
            },
            askNotifications = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        )
    }
    return state to actions
}

class BackgroundAccessActions(
    val askHealthRead: () -> Unit,
    val askBattery: () -> Unit,
    val askNotifications: () -> Unit
)

/** One row per background permission: what it's for, and either "On" or a button to allow it. */
@Composable
fun BackgroundAccessRows(state: BackgroundAccessState?, actions: BackgroundAccessActions) {
    val tokens = LocalHangryTokens.current
    Column(verticalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.s)) {
        AccessRow(
            icon = Icons.Default.Sync,
            title = "Sync in the background",
            subtitle = when (state?.healthRead) {
                BackgroundReadStatus.UNSUPPORTED ->
                    "This version of Health Connect only shares data while Hangry is open. Updating Health Connect adds background sync."
                else -> "Reads new sleep, heart and activity data from Health Connect every hour, even when Hangry is closed."
            },
            granted = state?.healthRead == BackgroundReadStatus.GRANTED,
            available = state != null && state.healthRead != BackgroundReadStatus.UNSUPPORTED,
            onAllow = actions.askHealthRead
        )
        HorizontalDivider(color = tokens.cardBorder)
        AccessRow(
            icon = Icons.Default.BatteryChargingFull,
            title = "Run without battery limits",
            subtitle = "Stops Android from holding background syncs back for hours. Each sync takes a few seconds.",
            granted = state?.batteryUnrestricted == true,
            available = state != null,
            onAllow = actions.askBattery
        )
        HorizontalDivider(color = tokens.cardBorder)
        AccessRow(
            icon = Icons.Default.NotificationsActive,
            title = "Notifications",
            subtitle = "Your morning readiness brief, bedtime and supplement reminders.",
            granted = state?.notifications == true,
            available = state != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
            onAllow = actions.askNotifications
        )
    }
}

@Composable
private fun AccessRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    granted: Boolean,
    available: Boolean,
    onAllow: () -> Unit
) {
    val tokens = LocalHangryTokens.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Icon(icon, contentDescription = null, tint = tokens.textSecondary, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(HangryTokens.Spacing.m))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = tokens.textPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
        }
        Spacer(modifier = Modifier.width(HangryTokens.Spacing.s))
        when {
            granted -> Icon(Icons.Default.CheckCircle, contentDescription = "Allowed", tint = tokens.scoreColors.primed, modifier = Modifier.size(24.dp))
            available -> FilledTonalButton(onClick = onAllow) { Text("Allow") }
        }
    }
}
