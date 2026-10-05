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
import androidx.compose.ui.res.stringResource
import com.kevan.hangry.R

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
        title = stringResource(R.string.settings_onboarding_bg_title),
        step = OnboardingSteps.BACKGROUND,
        onNavigateBack = onNavigateBack,
        primaryLabel = stringResource(R.string.settings_continue),
        primaryEnabled = true,
        onPrimary = done,
        secondaryLabel = if (allSet) null else stringResource(R.string.settings_not_now),
        onSecondary = done,
        dashMood = if (allSet) DashMood.CELEBRATE else DashMood.SLEEPY,
        heading = if (allSet) stringResource(R.string.settings_onboarding_bg_all_set) else stringResource(R.string.settings_onboarding_bg_heading),
        subheading = stringResource(R.string.settings_onboarding_bg_subheading)
    ) {
        HangryCard { BackgroundAccessRows(state, actions) }
        Text(
            stringResource(R.string.settings_onboarding_bg_footer),
            style = MaterialTheme.typography.bodySmall,
            color = tokens.textMuted
        )
    }
}
