package com.kevan.hangry.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kevan.hangry.R
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens

@Composable
fun WelcomeScreen(
    onGetStarted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalHangryTokens.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(HangryTokens.Spacing.l),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(modifier = Modifier.height(HangryTokens.Spacing.m))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.m)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(vertical = HangryTokens.Spacing.xs)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.hangry_energy_sphere),
                    contentDescription = "Hangry Vitality Core",
                    modifier = Modifier
                        .size(160.dp)
                        .clip(CircleShape)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.hangry_logo),
                    contentDescription = stringResource(R.string.app_name),
                    modifier = Modifier.size(36.dp)
                )
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineLarge,
                    color = tokens.textPrimary
                )
            }

            Text(
                text = stringResource(R.string.welcome_headline),
                style = MaterialTheme.typography.titleMedium,
                color = tokens.textPrimary,
                textAlign = TextAlign.Center
            )

            Text(
                text = stringResource(R.string.product_promise),
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.textSecondary,
                textAlign = TextAlign.Center
            )
        }

        Surface(
            color = tokens.scoreColors.buildingBaselineContainer,
            shape = MaterialTheme.shapes.medium
        ) {
            Text(
                text = "🔒 100% Local-First & Private",
                style = MaterialTheme.typography.labelLarge,
                color = tokens.scoreColors.buildingBaseline,
                modifier = Modifier.padding(HangryTokens.Spacing.m)
            )
        }

        Button(
            onClick = onGetStarted,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text(
                text = "Get Started",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}
