package com.kevan.hangry.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens

/**
 * Segmented progress bar shared across the onboarding flow (About you -> Extras -> Connect -> History -> Sync) so the
 * user always knows how many steps remain - deliberately excludes the Welcome screen, which is
 * an intro rather than a step toward a decision.
 */
@Composable
fun OnboardingStepIndicator(
    currentStep: Int,
    totalSteps: Int,
    modifier: Modifier = Modifier
) {
    val tokens = LocalHangryTokens.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
    ) {
        repeat(totalSteps) { index ->
            val isFilled = index < currentStep
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (isFilled) tokens.scoreColors.primed else tokens.cardBorder)
            )
        }
    }
}

/**
 * Icon-in-a-circle + title/subtitle row used for the value-prop list on the "Connect Your Health
 * Data" screen - the same visual language as HangryPendingNotice and Settings action rows
 * elsewhere in the app, instead of the raw emoji this screen used to use.
 */
@Composable
fun OnboardingFeatureRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    val tokens = LocalHangryTokens.current
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(HangryTokens.Spacing.m))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = tokens.textPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textSecondary
            )
        }
    }
}
