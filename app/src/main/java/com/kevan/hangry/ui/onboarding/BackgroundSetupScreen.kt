package com.kevan.hangry.ui.onboarding

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.kevan.hangry.ui.background.BackgroundPromptPrefs
import com.kevan.hangry.ui.background.BackgroundAccessRows
import com.kevan.hangry.ui.background.rememberBackgroundAccess
import com.kevan.hangry.ui.coach.DashMood
import com.kevan.hangry.ui.components.HangryCard
import com.kevan.hangry.ui.theme.LocalHangryTokens

/**
 * Asks for what Hangry needs to stay current while closed: Health Connect background reads,
 * freedom from battery limits, and notifications. Each is optional - without them, Hangry
 * simply catches up whenever it's opened.
 */
@Composable
fun BackgroundSetupScreen(onNavigateBack: () -> Unit, onContinue: () -> Unit) {
    val tokens = LocalHangryTokens.current
    val context = LocalContext.current
    val (state, actions) = rememberBackgroundAccess()
    // Seen here, so Today won't ask again.
    val done = {
        BackgroundPromptPrefs.markAsked(context)
        onContinue()
    }
    val allSet = state != null && state.backgroundSyncWorks && state.batteryUnrestricted && state.notifications

    OnboardingProfileScaffold(
        title = "Stay Up to Date",
        step = OnboardingSteps.BACKGROUND,
        onNavigateBack = onNavigateBack,
        primaryLabel = "Continue",
        primaryEnabled = true,
        onPrimary = done,
        secondaryLabel = if (allSet) null else "Not now",
        onSecondary = done,
        dashMood = if (allSet) DashMood.CELEBRATE else DashMood.SLEEPY,
        heading = if (allSet) "All set!" else "Keep Hangry fresh",
        subheading = "Let Hangry sync while it's closed, so your recovery, widgets and morning brief are ready before you open the app."
    ) {
        HangryCard { BackgroundAccessRows(state, actions) }
        Text(
            "Reading happens on this device only - nothing is uploaded. You can change these any time in Settings → Background updates.",
            style = MaterialTheme.typography.bodySmall,
            color = tokens.textMuted
        )
    }
}
