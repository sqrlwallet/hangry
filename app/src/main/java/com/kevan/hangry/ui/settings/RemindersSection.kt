package com.kevan.hangry.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kevan.hangry.data.nudges.NudgeNotifications
import com.kevan.hangry.data.nudges.NudgePrefs
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens

/** Switches for the morning readiness brief and the bedtime reminder. */
@Composable
internal fun RemindersSection() {
    val context = LocalContext.current
    val tokens = LocalHangryTokens.current
    var morning by remember { mutableStateOf(NudgePrefs.morningEnabled(context)) }
    var bedtime by remember { mutableStateOf(NudgePrefs.bedtimeEnabled(context)) }
    var allowed by remember { mutableStateOf(NudgeNotifications.canNotify(context)) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }
    fun askIfNeeded() {
        if (!allowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    SettingsCollapsibleSection(
        title = "Reminders",
        summary = listOfNotNull("Morning brief".takeIf { morning }, "bedtime".takeIf { bedtime })
            .joinToString(" & ").replaceFirstChar { it.uppercase() }.ifEmpty { "Off" },
        icon = Icons.Default.NotificationsActive
    ) {
        ReminderSwitch(
            icon = Icons.Default.WbSunny,
            title = "Morning readiness",
            subtitle = "Your recovery and today's strain target, once last night's sleep syncs",
            checked = morning,
            onChange = {
                morning = it
                NudgePrefs.setMorningEnabled(context, it)
                if (it) askIfNeeded()
            }
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = tokens.cardBorder)
        ReminderSwitch(
            icon = Icons.Default.Bedtime,
            title = "Bedtime reminder",
            subtitle = "A nudge 30 minutes before your suggested bedtime",
            checked = bedtime,
            onChange = {
                bedtime = it
                NudgePrefs.setBedtimeEnabled(context, it)
                if (it) askIfNeeded()
            }
        )
        if (!allowed && (morning || bedtime)) {
            Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))
            Text(
                "Notifications are off for Hangry, so these can't show.",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textSecondary
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                TextButton(onClick = { permission.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Allow notifications") }
            }
        }
    }
}

@Composable
private fun ReminderSwitch(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val tokens = LocalHangryTokens.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tokens.textSecondary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(HangryTokens.Spacing.m))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = tokens.textPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
