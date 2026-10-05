package com.kevan.hangry.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import android.net.Uri
import com.kevan.hangry.R
import com.kevan.hangry.data.backup.BackupException
import com.kevan.hangry.data.backup.HangryBackupManager
import com.kevan.hangry.ui.coach.DashSpinner
import com.kevan.hangry.ui.theme.LocalHangryTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Back up everything to a file the user picks, and restore it on this or a new phone. This is
 * the only way Hangry's data leaves the phone: Android's cloud Auto Backup is off for it.
 */
@Composable
internal fun BackupRestoreSection(
    backupManager: HangryBackupManager,
    snackbarHostState: SnackbarHostState
) {
    val context = LocalContext.current
    val tokens = LocalHangryTokens.current
    val scope = rememberCoroutineScope()
    var working by remember { mutableStateOf<String?>(null) }
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }
    var restoreError by remember { mutableStateOf<String?>(null) }

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            working = context.getString(R.string.settings_backup_working)
            val result = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { backupManager.writeBackup(it) }
                    ?: throw BackupException(context.getString(R.string.settings_backup_cant_write))
            }
            working = null
            snackbarHostState.showSnackbar(
                result.fold(
                    onSuccess = { context.getString(R.string.settings_backup_saved) },
                    onFailure = { context.getString(R.string.settings_backup_failed, it.message ?: "") }
                )
            )
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pendingRestore = uri
    }

    SettingsCollapsibleSection(
        title = stringResource(R.string.settings_backup_section_title),
        summary = stringResource(R.string.settings_backup_section_summary),
        icon = Icons.Default.SettingsBackupRestore
    ) {
        SettingsActionRow(
            icon = Icons.Default.Backup,
            title = stringResource(R.string.settings_backup_title),
            subtitle = stringResource(R.string.settings_backup_subtitle),
            info = stringResource(R.string.settings_backup_info),
            enabled = working == null,
            onClick = { backupLauncher.launch("hangry-backup-${LocalDate.now()}.zip") }
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = tokens.cardBorder)
        SettingsActionRow(
            icon = Icons.Default.Restore,
            title = stringResource(R.string.settings_restore_title),
            subtitle = stringResource(R.string.settings_restore_subtitle),
            enabled = working == null,
            onClick = { restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed")) }
        )
        working?.let { text ->
            androidx.compose.foundation.layout.Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                DashSpinner(size = 32.dp, contentDescription = null)
                Text(text, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }

    pendingRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text(stringResource(R.string.settings_restore_confirm_title)) },
            text = { Text(stringResource(R.string.settings_restore_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    scope.launch {
                        working = context.getString(R.string.settings_restore_working)
                        val result = runCatching {
                            context.contentResolver.openInputStream(uri)?.use { backupManager.restoreBackup(it) }
                                ?: throw BackupException(context.getString(R.string.settings_restore_cant_read))
                        }
                        result.fold(
                            onSuccess = {
                                working = context.getString(R.string.settings_restore_done)
                                delay(1200)
                                backupManager.restartApp()
                            },
                            onFailure = { e ->
                                working = null
                                if ((e as? BackupException)?.needsRestart == true) {
                                    snackbarHostState.showSnackbar(e.message.orEmpty())
                                    backupManager.restartApp()
                                } else {
                                    restoreError = e.message ?: context.getString(R.string.settings_restore_cant_read)
                                }
                            }
                        )
                    }
                }) { Text(stringResource(R.string.settings_restore_confirm_action)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestore = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    restoreError?.let { message ->
        AlertDialog(
            onDismissRequest = { restoreError = null },
            title = { Text(stringResource(R.string.settings_restore_failed_title)) },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { restoreError = null }) { Text(stringResource(R.string.settings_restore_ok)) } }
        )
    }
}
