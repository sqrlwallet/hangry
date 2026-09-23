package com.kevan.hangry.ui.coach

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kevan.hangry.ui.theme.LocalHangryTokens

/** A goal-reached moment: Dash bursts in with confetti. */
@Composable
fun DashCelebration(title: String, message: String, onDismiss: () -> Unit) {
    val tokens = LocalHangryTokens.current
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(Unit) { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DashExpression(mood = DashMood.CELEBRATE, size = 150.dp)
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = tokens.textPrimary, textAlign = TextAlign.Center)
                Text(message, style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary, textAlign = TextAlign.Center)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Nice!") } }
    )
}

/** Remembers which celebrations have been shown, so each one only happens once. */
object Celebrations {
    private const val FILE = "hangry_celebrations"

    /** True the first time it's called for [key] - and marks it as shown. */
    fun claim(context: Context, key: String): Boolean {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        if (prefs.getBoolean(key, false)) return false
        prefs.edit().putBoolean(key, true).apply()
        return true
    }
}
