package com.kevan.hangry.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kevan.hangry.R
import com.kevan.hangry.domain.model.RestModeState
import com.kevan.hangry.domain.model.RestPeriod
import com.kevan.hangry.domain.model.RestReason
import com.kevan.hangry.ui.coach.DashAlertCard
import com.kevan.hangry.ui.coach.DashMood
import com.kevan.hangry.ui.theme.LocalHangryTokens
import java.time.LocalDate

/**
 * Rest mode is on: what it changes, and a way out. Once overnight readings are back to normal
 * after a couple of days, Dash suggests ending it.
 */
@Composable
fun RestModeCard(
    rest: RestPeriod,
    today: LocalDate,
    readingsBackToNormal: Boolean,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val day = rest.dayNumber(today)
    val suggestEnd = readingsBackToNormal && day > RestModeState.SUGGEST_END_AFTER_DAYS
    DashAlertCard(
        mood = DashMood.REST,
        title = stringResource(R.string.dashboard_rest_title, day, rest.reason.label()),
        message = if (suggestEnd) stringResource(R.string.dashboard_rest_back_to_normal) else stringResource(R.string.dashboard_rest_message),
        modifier = modifier,
        action = {
            TextButton(onClick = onEnd, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp)) {
                Text(stringResource(R.string.dashboard_rest_end))
            }
        }
    )
}

/** "Turn on rest mode" - opens [RestReasonDialog] first. */
@Composable
fun StartRestButton(onStart: (RestReason) -> Unit) {
    var asking by remember { mutableStateOf(false) }
    TextButton(onClick = { asking = true }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp)) {
        Text(stringResource(R.string.dashboard_rest_start))
    }
    if (asking) {
        RestReasonDialog(
            onConfirm = { reason ->
                asking = false
                onStart(reason)
            },
            onDismiss = { asking = false }
        )
    }
}

@Composable
fun RestReasonDialog(onConfirm: (RestReason) -> Unit, onDismiss: () -> Unit) {
    val tokens = LocalHangryTokens.current
    var reason by remember { mutableStateOf(RestReason.UNWELL) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dashboard_rest_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.dashboard_rest_dialog_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                RestReason.entries.forEach { option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = option == reason, role = Role.RadioButton, onClick = { reason = option })
                    ) {
                        RadioButton(selected = option == reason, onClick = null)
                        Text(option.label(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(reason) }) { Text(stringResource(R.string.dashboard_rest_dialog_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun RestReason.label(): String = stringResource(
    when (this) {
        RestReason.UNWELL -> R.string.dashboard_rest_reason_unwell
        RestReason.INJURY -> R.string.dashboard_rest_reason_injury
        RestReason.RECOVERY -> R.string.dashboard_rest_reason_recovery
    }
)
