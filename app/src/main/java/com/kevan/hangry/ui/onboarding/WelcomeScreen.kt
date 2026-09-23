package com.kevan.hangry.ui.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kevan.hangry.R
import com.kevan.hangry.ui.coach.DashExpression
import com.kevan.hangry.ui.coach.DashMood
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens

@Composable
fun WelcomeScreen(
    onGetStarted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalHangryTokens.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(com.kevan.hangry.ui.theme.BackgroundDark)
    ) {
        // 1. Full-bleed AI-generated bioluminescent hero wallpaper
        Image(
            painter = painterResource(id = R.drawable.onboarding_hero_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // 2. Cinematic Multi-Stop Gradient Scrim for crystal clear typography
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Black.copy(alpha = 0.65f),
                        0.25f to Color.Black.copy(alpha = 0.20f),
                        0.60f to Color.Black.copy(alpha = 0.55f),
                        0.85f to com.kevan.hangry.ui.theme.BackgroundDark.copy(alpha = 0.92f),
                        1.0f to com.kevan.hangry.ui.theme.BackgroundDark
                    )
                )
        )

        // 3. Foreground Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = HangryTokens.Spacing.l, vertical = HangryTokens.Spacing.m),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))

            // Brand Header & Hero Core
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.m)
            ) {
                // Vitality Core with ambient glowing radial halo
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(vertical = HangryTokens.Spacing.xs)
                ) {
                    Box(
                        modifier = Modifier
                            .size(220.dp)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        tokens.scoreColors.primed.copy(alpha = 0.30f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                    DashExpression(mood = DashMood.WAVE, size = 190.dp)
                }

                // App Title with luxury letter spacing
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.hangry_logo),
                        contentDescription = stringResource(R.string.app_name),
                        modifier = Modifier.size(38.dp)
                    )
                    Text(
                        text = stringResource(R.string.app_name).uppercase(),
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 3.sp
                        ),
                        color = Color.White
                    )
                }

                // Welcome Headline
                Text(
                    text = stringResource(R.string.welcome_headline),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White.copy(alpha = 0.95f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                // Value Promise
                Text(
                    text = stringResource(R.string.product_promise),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.70f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            // Bottom Section: Floating Glass Privacy Capsule & Glowing CTA Button
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.l),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Frosted Glass Local-First Capsule
                Surface(
                    color = com.kevan.hangry.ui.theme.ShipGray.copy(alpha = 0.72f),
                    shape = CircleShape,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = tokens.scoreColors.primed,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "100% LOCAL-FIRST & PRIVATE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp
                            ),
                            color = tokens.scoreColors.primed
                        )
                    }
                }

                // Premium CTA Button
                Button(
                    onClick = onGetStarted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = com.kevan.hangry.ui.theme.BlueRibbon
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 6.dp,
                        pressedElevation = 2.dp
                    )
                ) {
                    Text(
                        text = "Get Started",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        ),
                        color = androidx.compose.ui.graphics.Color.White
                    )
                }
            }
        }
    }
}
