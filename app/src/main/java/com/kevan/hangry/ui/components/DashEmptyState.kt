package com.kevan.hangry.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kevan.hangry.R
import com.kevan.hangry.ui.coach.rememberIdleBob
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens

/** Dash scenes for screens with nothing to show yet. */
enum class DashEmptyScene(@DrawableRes val imageRes: Int) {
    POSTURE(R.drawable.dash_empty_posture),
    HRV(R.drawable.dash_empty_hrv),
    MEALS(R.drawable.dash_empty_meals),
    RECORDS(R.drawable.dash_empty_records),
    MEMORIES(R.drawable.dash_empty_memories),
    WORKOUTS(R.drawable.dash_empty_workouts),
    SLEEP(R.drawable.dash_empty_sleep),
    PROGRAMS(R.drawable.dash_empty_programs)
}

/** Centered Dash illustration with a title and short hint, for empty lists and missing data. */
@Composable
fun DashEmptyState(
    scene: DashEmptyScene,
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    infoTitle: String? = null,
    infoBody: String? = null,
    imageSize: Dp = 150.dp
) {
    val tokens = LocalHangryTokens.current
    val bob = rememberIdleBob(durationMillis = 2400)
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(scene.imageRes),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(imageSize)
                .graphicsLayer {
                    translationY = -6.dp.toPx() * bob.value
                }
        )
        Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = tokens.textPrimary,
                textAlign = TextAlign.Center
            )
            if (infoTitle != null && infoBody != null) {
                HangryInfoTip(title = infoTitle, body = infoBody)
            }
        }
        if (body != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}
