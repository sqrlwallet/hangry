package com.kevan.hangry.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens

/**
 * The one visual signature for Hangry's "your data never leaves this device" promise - reused
 * wherever that claim is made (onboarding, Privacy & Legal) so it reads as the same promise
 * rather than a different banner each time.
 */
@Composable
fun LocalFirstBanner(modifier: Modifier = Modifier) {
    val tokens = LocalHangryTokens.current
    Surface(
        color = tokens.scoreColors.primed.copy(alpha = 0.12f),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(HangryTokens.Spacing.m),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = tokens.scoreColors.primed,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "100% Local-First Architecture",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = tokens.scoreColors.primed
                )
                Text(
                    text = "Your health data never leaves your device. No cloud. No accounts. No telemetry.",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textPrimary
                )
            }
        }
    }
}
