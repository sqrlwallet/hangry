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
        title = "Background updates",
        summary = when (state?.healthRead) {
            null -> "Checking…"
            BackgroundReadStatus.GRANTED -> if (state.batteryUnrestricted) "Hourly sync on" else "Hourly sync on · battery limited"
            BackgroundReadStatus.NOT_GRANTED -> "Off - syncs only when open"
            BackgroundReadStatus.UNSUPPORTED -> "Syncs only when open"
        },
        icon = Icons.Default.CloudSync
    ) {
        BackgroundAccessRows(state, actions)
        Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))
        val last = BackgroundSyncLog.lastSync(context)
        val result = BackgroundSyncLog.lastResult(context)
        Text(
            when {
                last == null -> "No background sync yet."
                result == BackgroundSyncLog.SYNCED -> "Last background sync ${relative(last.toEpochMilli())}."
                result == BackgroundSyncLog.SKIPPED_NO_PERMISSION -> "Last background run ${relative(last.toEpochMilli())} was skipped - background sync isn't allowed."
                else -> "Last background sync ${relative(last.toEpochMilli())} didn't finish; it will try again."
            },
            style = MaterialTheme.typography.bodySmall,
            color = tokens.textMuted
        )
    }
}

private fun relative(millis: Long): String =
    DateUtils.getRelativeTimeSpanString(millis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString().lowercase()
