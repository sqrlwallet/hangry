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
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes

/** One pose in a photo guide, illustrated by Dash. */
data class GuidePose(
    @StringRes val title: Int,
    @StringRes val subtitle: Int,
    @StringRes val tip: Int,
    @DrawableRes val imageRes: Int
)

val POSTURE_POSES = listOf(
    GuidePose(R.string.metrics_components_pose_front, R.string.metrics_components_pose_anterior, R.string.metrics_components_pose_tip_posture_front, R.drawable.posture_pose_front),
    GuidePose(R.string.metrics_components_pose_side, R.string.metrics_components_pose_lateral, R.string.metrics_components_pose_tip_posture_side, R.drawable.posture_pose_side),
    GuidePose(R.string.metrics_components_pose_back, R.string.metrics_components_pose_posterior, R.string.metrics_components_pose_tip_posture_back, R.drawable.posture_pose_back),
    GuidePose(R.string.metrics_components_pose_arms_overhead, R.string.metrics_components_pose_mobility, R.string.metrics_components_pose_tip_posture_overhead, R.drawable.posture_pose_overhead),
    GuidePose(R.string.metrics_components_pose_biceps_flex, R.string.metrics_components_pose_optional, R.string.metrics_components_pose_tip_posture_flex, R.drawable.posture_pose_flex)
)

val BODY_FAT_POSES = listOf(
    GuidePose(R.string.metrics_components_pose_front, R.string.metrics_components_pose_anterior, R.string.metrics_components_pose_tip_bodyfat_front, R.drawable.bodyfat_pose_front),
    GuidePose(R.string.metrics_components_pose_side, R.string.metrics_components_pose_lateral, R.string.metrics_components_pose_tip_bodyfat_side, R.drawable.bodyfat_pose_side),
    GuidePose(R.string.metrics_components_pose_back, R.string.metrics_components_pose_posterior, R.string.metrics_components_pose_tip_bodyfat_back, R.drawable.bodyfat_pose_back)
)

/** Horizontal row of pose cards showing which photos to take. */
@Composable
fun PoseGuide(poses: List<GuidePose>, modifier: Modifier = Modifier) {
    val tokens = LocalHangryTokens.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.s)) {
        Text(
            text = stringResource(R.string.metrics_components_how_to_pose),
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
    val title = stringResource(pose.title)
    Column(
        modifier = Modifier
            .width(140.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(tokens.cardBackground)
    ) {
        Image(
            painter = painterResource(pose.imageRes),
            contentDescription = stringResource(R.string.metrics_components_pose_image_description, title.lowercase()),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
        )
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = stringResource(R.string.metrics_components_pose_numbered_title, number, title),
                style = MaterialTheme.typography.titleSmall,
                color = tokens.textPrimary
            )
            Text(
                text = stringResource(pose.subtitle),
                style = MaterialTheme.typography.labelSmall,
                color = tokens.textMuted
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(pose.tip),
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textSecondary,
                minLines = 3
            )
        }
    }
}
