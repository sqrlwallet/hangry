package com.kevan.hangry.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens

/**
 * Shown in place of a score/metric card from midnight until today's sleep has been recorded.
 * Keeps the UI from displaying a zero or a stale carried-over value while data is unavailable.
 */
@Composable
fun HangryPendingNotice(
    message: String,
    modifier: Modifier = Modifier
) {
    val tokens = LocalHangryTokens.current
    HangryCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Bedtime,
                contentDescription = null,
                tint = tokens.scoreColors.buildingBaseline,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(HangryTokens.Spacing.s))
            Column {
                Text(
                    text = "Pending",
                    style = MaterialTheme.typography.titleSmall,
                    color = tokens.textPrimary
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary
                )
            }
        }
    }
}
