package com.kevan.hangry.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.outlined.Info
import androidx.compose.foundation.layout.size
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
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    var showDialog by remember { mutableStateOf(false) }
    val tokens = LocalHangryTokens.current

    IconButton(
        onClick = { showDialog = true },
        modifier = if (compact) modifier.size(32.dp) else modifier
    ) {
        Icon(
            imageVector = if (compact) Icons.Outlined.Info else Icons.Default.Info,
            contentDescription = "About $title",
            tint = tokens.textMuted,
            modifier = if (compact) Modifier.size(18.dp) else Modifier
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
                            if (section.heading.isNotBlank()) {
                                Text(
                                    text = section.heading,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = tokens.textPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                            }
                            Text(
                                text = section.body,
                                style = MaterialTheme.typography.bodyMedium,
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

/**
 * Compact (i) that sits inline after a label or card title and opens [body] in a dialog. The
 * go-to way to keep explanations out of the main layout: show a short label, tuck the rest here.
 */
@Composable
fun HangryInfoTip(
    title: String,
    body: String,
    modifier: Modifier = Modifier
) {
    HangryInfoIconButton(
        title = title,
        sections = listOf(HangryInfoSection(heading = "", body = body)),
        modifier = modifier,
        compact = true
    )
}
