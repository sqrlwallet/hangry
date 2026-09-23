package com.kevan.hangry.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.kevan.hangry.R
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens

/** One pose in a photo guide, illustrated by Dash. */
data class GuidePose(
    val title: String,
    val subtitle: String,
    val tip: String,
    @DrawableRes val imageRes: Int
)

val POSTURE_POSES = listOf(
    GuidePose("Front", "Anterior", "Stand relaxed, arms un-obscured.", R.drawable.posture_pose_front),
    GuidePose("Side", "Lateral", "Capture your natural spinal curve. Hold still.", R.drawable.posture_pose_side),
    GuidePose("Back", "Posterior", "Checks spinal and shoulder-blade symmetry.", R.drawable.posture_pose_back),
    GuidePose("Arms overhead", "Mobility", "From behind, raise both arms to test shoulder mobility.", R.drawable.posture_pose_overhead),
    GuidePose("Biceps flex", "Optional", "Flex both biceps to show muscle development.", R.drawable.posture_pose_flex)
)

val BODY_FAT_POSES = listOf(
    GuidePose("Front", "Anterior", "Stand relaxed, arms at sides. Even lighting, full body in frame.", R.drawable.bodyfat_pose_front),
    GuidePose("Side", "Lateral", "Turn 90°, keep your natural curve. Skip bulky clothing.", R.drawable.bodyfat_pose_side),
    GuidePose("Back", "Posterior", "Arms slightly out from your sides to show fat distribution.", R.drawable.bodyfat_pose_back)
)

/** Horizontal row of pose cards showing which photos to take. */
@Composable
fun PoseGuide(poses: List<GuidePose>, modifier: Modifier = Modifier) {
    val tokens = LocalHangryTokens.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.s)) {
        Text(
            text = "How to pose",
            style = MaterialTheme.typography.titleMedium,
            color = tokens.textPrimary
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.s)
        ) {
            poses.forEachIndexed { index, pose ->
                PoseCard(number = index + 1, pose = pose)
            }
        }
    }
}

@Composable
private fun PoseCard(number: Int, pose: GuidePose) {
    val tokens = LocalHangryTokens.current
    Column(
        modifier = Modifier
            .width(140.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(tokens.cardBackground)
    ) {
        Image(
            painter = painterResource(pose.imageRes),
            contentDescription = "Dash demonstrating the ${pose.title.lowercase()} pose",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
        )
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = "$number. ${pose.title}",
                style = MaterialTheme.typography.titleSmall,
                color = tokens.textPrimary
            )
            Text(
                text = pose.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = tokens.textMuted
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = pose.tip,
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textSecondary,
                minLines = 3
            )
        }
    }
}
