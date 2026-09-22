package com.kevan.hangry.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kevan.hangry.ui.theme.LocalHangryTokens

/** A heading + explanatory paragraph shown inside a [HangryInfoIconButton] dialog. */
data class HangryInfoSection(val heading: String, val body: String)

/**
 * A small info-icon trigger that reveals methodology/explanatory text on demand instead of
 * leaving it permanently visible on screen. Use this to move "how is this calculated" style
 * copy out of the main flow and keep card bodies focused on the numbers.
 */
@Composable
fun HangryInfoIconButton(
    title: String,
    sections: List<HangryInfoSection>,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }
    val tokens = LocalHangryTokens.current

    IconButton(onClick = { showDialog = true }, modifier = modifier) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = "About $title",
            tint = tokens.textMuted
        )
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(title) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    sections.forEach { section ->
                        Column {
                            Text(
                                text = section.heading,
                                style = MaterialTheme.typography.labelLarge,
                                color = tokens.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = section.body,
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.textSecondary
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Got it")
                }
            }
        )
    }
}
