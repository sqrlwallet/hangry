package com.kevan.hangry.ui.settings

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.kevan.hangry.R
import com.kevan.hangry.data.background.BackgroundReadStatus
import com.kevan.hangry.data.background.BackgroundSyncLog
import com.kevan.hangry.ui.background.BackgroundAccessRows
import com.kevan.hangry.ui.background.rememberBackgroundAccess
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens

/** Background sync status and the permissions it needs. */
@Composable
internal fun BackgroundSection() {
    val context = LocalContext.current
    val tokens = LocalHangryTokens.current
    val (state, actions) = rememberBackgroundAccess()

    SettingsCollapsibleSection(
        title = stringResource(R.string.settings_background_title),
        summary = when (state?.healthRead) {
            null -> stringResource(R.string.settings_background_checking)
            BackgroundReadStatus.GRANTED -> if (state.batteryUnrestricted) stringResource(R.string.settings_background_hourly_on) else stringResource(R.string.settings_background_hourly_on_battery_limited)
            BackgroundReadStatus.NOT_GRANTED -> stringResource(R.string.settings_background_off_syncs_when_open)
            BackgroundReadStatus.UNSUPPORTED -> stringResource(R.string.settings_background_syncs_when_open)
        },
        icon = Icons.Default.CloudSync
    ) {
        BackgroundAccessRows(state, actions)
        Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))
        val last = BackgroundSyncLog.lastSync(context)
        val result = BackgroundSyncLog.lastResult(context)
        Text(
            when {
                last == null -> stringResource(R.string.settings_background_no_sync_yet)
                result == BackgroundSyncLog.SYNCED -> stringResource(R.string.settings_background_last_sync, relative(last.toEpochMilli()))
                result == BackgroundSyncLog.SKIPPED_NO_PERMISSION -> stringResource(R.string.settings_background_last_run_skipped, relative(last.toEpochMilli()))
                else -> stringResource(R.string.settings_background_last_sync_unfinished, relative(last.toEpochMilli()))
            },
            style = MaterialTheme.typography.bodySmall,
            color = tokens.textMuted
        )
    }
}

private fun relative(millis: Long): String =
    DateUtils.getRelativeTimeSpanString(millis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString().lowercase()
