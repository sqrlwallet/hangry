package com.kevan.hangry.ui.background

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kevan.hangry.data.background.BackgroundAccess
import com.kevan.hangry.data.background.BackgroundReadStatus
import com.kevan.hangry.ui.coach.DashExpression
import com.kevan.hangry.ui.coach.DashMood
import com.kevan.hangry.ui.theme.LocalHangryTokens

/**
 * Asks once - for people who finished onboarding before it had the background step - whether
 * Hangry may sync while closed. Only shown if Health Connect supports it and it isn't allowed yet.
 */
@Composable
fun BackgroundAccessPrompt() {
    val context = LocalContext.current
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        show = !BackgroundPromptPrefs.asked(context) && BackgroundAccess.healthRead(context) == BackgroundReadStatus.NOT_GRANTED
    }
    if (!show) return

    val tokens = LocalHangryTokens.current
    val (state, actions) = rememberBackgroundAccess()
    fun close() {
        BackgroundPromptPrefs.markAsked(context)
        show = false
    }
    AlertDialog(
        onDismissRequest = ::close,
        icon = { DashExpression(mood = DashMood.SLEEPY, size = 72.dp) },
        title = { Text("Keep Hangry up to date?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.Start) {
                Text(
                    "Hangry can now sync every hour while it's closed, so your recovery, widgets and morning brief are ready when you wake up.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.textSecondary
                )
                BackgroundAccessRows(state, actions)
            }
        },
        confirmButton = { TextButton(onClick = ::close) { Text(if (state?.backgroundSyncWorks == true) "Done" else "Not now") } }
    )
}

internal object BackgroundPromptPrefs {
    private const val FILE = "hangry_background"
    private const val KEY_ASKED = "background_prompt_asked"
    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    fun asked(context: Context) = prefs(context).getBoolean(KEY_ASKED, false)
    fun markAsked(context: Context) = prefs(context).edit().putBoolean(KEY_ASKED, true).apply()
}
