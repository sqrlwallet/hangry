package com.kevan.hangry.ui.breathing

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kevan.hangry.R
import com.kevan.hangry.domain.model.BreathingVideos
import com.kevan.hangry.ui.components.HangryCard
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens

/** Compact home-page entry point: tapping a session opens the breathing videos with it playing. */
@Composable
fun BreathingExercisesCard(
    onOpenVideo: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalHangryTokens.current
    val accent = tokens.chartColors.sleep

    HangryCard(modifier = modifier.fillMaxWidth(), onClick = { onOpenVideo(null) }) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Air,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(HangryTokens.Spacing.xs))
                Text(
                    text = stringResource(R.string.coach_breathing_exercises),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = tokens.textPrimary
                )
            }
            Text(
                text = stringResource(R.string.coach_breathing_videos_count, BreathingVideos.all.size),
                style = MaterialTheme.typography.labelSmall,
                color = tokens.textMuted
            )
        }

        Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.s)
        ) {
            BreathingVideos.all.forEach { video ->
                Surface(
                    onClick = { onOpenVideo(video.key) },
                    shape = RoundedCornerShape(12.dp),
                    color = accent.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.25f))
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 6.dp, horizontal = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = video.shortLabel,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = tokens.textPrimary,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                        Text(
                            text = stringResource(R.string.coach_breathing_video_minutes, video.minutes),
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.textMuted,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
