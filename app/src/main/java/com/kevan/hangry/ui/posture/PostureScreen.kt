package com.kevan.hangry.ui.posture

import com.kevan.hangry.ui.components.DashEmptyState
import com.kevan.hangry.ui.components.DashEmptyScene
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kevan.hangry.R
import com.kevan.hangry.data.local.entity.PostureScanEntity
import com.kevan.hangry.ui.components.HangryCard
import com.kevan.hangry.ui.components.HangryInfoIconButton
import com.kevan.hangry.ui.components.HangryInfoSection
import com.kevan.hangry.ui.components.HangryInfoTip
import com.kevan.hangry.ui.components.HangryPendingNotice
import com.kevan.hangry.ui.components.HangryRingGauge
import com.kevan.hangry.ui.components.UndoDeleteEffect
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Scan dates as "Mar 4, 2026", matching body fat history. */
internal val POSTURE_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())

@Composable
private fun postureInfoSections(): List<HangryInfoSection> = listOf(
    HangryInfoSection(
        stringResource(R.string.body_posture_info_how_title),
        stringResource(R.string.body_posture_info_how_body)
    ),
    HangryInfoSection(
        stringResource(R.string.body_posture_info_photos_title),
        stringResource(R.string.body_posture_info_photos_body)
    ),
    // What the check looks at (formerly a static "Alignment focus" card on the page).
    HangryInfoSection(stringResource(R.string.body_posture_zone_cervical), stringResource(R.string.body_posture_zone_cervical_info)),
    HangryInfoSection(stringResource(R.string.body_posture_zone_thoracic), stringResource(R.string.body_posture_zone_thoracic_info)),
    HangryInfoSection(stringResource(R.string.body_posture_zone_lumbopelvic), stringResource(R.string.body_posture_zone_lumbopelvic_info))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostureScreen(
    viewModel: PostureViewModel,
    onNavigateBack: () -> Unit,
    onStartNewScan: () -> Unit,
    onOpenScan: (Long) -> Unit,
    onNavigateToAiSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToAiCoach: (() -> Unit)? = null
) {
    val tokens = LocalHangryTokens.current
    val haptic = LocalHapticFeedback.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    // Hidden at once; the scan and its photos are only removed when the undo snackbar closes.
    var pendingDelete by remember { mutableStateOf<PostureScanEntity?>(null) }
    UndoDeleteEffect(
        pending = pendingDelete,
        snackbarHostState = snackbarHostState,
        message = { context.getString(R.string.body_posture_scan_deleted, it.date.format(POSTURE_DATE)) },
        onUndo = viewModel::undoDelete,
        onCommit = viewModel::commitDelete,
        onSettled = { pendingDelete = null }
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                expandedHeight = 56.dp,
                title = { Text(stringResource(R.string.title_posture)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.body_back))
                    }
                },
                actions = {
                    HangryInfoIconButton(title = stringResource(R.string.body_posture_about), sections = postureInfoSections())
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (!uiState.aiFeaturesEnabled) {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(HangryTokens.Spacing.m),
                verticalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.section)
            ) {
                HangryPendingNotice(message = stringResource(R.string.body_posture_enable_ai))
                HangryCard(modifier = Modifier.clickable { onNavigateToAiSettings() }) {
                    Text(stringResource(R.string.body_posture_go_to_ai_settings), style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .padding(horizontal = HangryTokens.Spacing.m),
            contentPadding = PaddingValues(top = HangryTokens.Spacing.s, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.section)
        ) {
            // Latest Posture Score Hero
            item {
                val latest = uiState.latestScan
                val score = latest?.score
                val scoreColor = when {
                    score == null -> tokens.chartColors.hrv
                    score >= 80 -> tokens.scoreColors.primed
                    score >= 60 -> tokens.scoreColors.balanced
                    else -> tokens.scoreColors.rebuild
                }

                HangryCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AccessibilityNew,
                                contentDescription = null,
                                tint = tokens.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.body_posture_score_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = tokens.textPrimary
                            )
                            HangryInfoTip(
                                title = stringResource(R.string.body_posture_score_title),
                                body = stringResource(R.string.body_posture_score_tip)
                            )
                        }
                        if (score != null) {
                            Surface(
                                color = scoreColor.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = when {
                                        score >= 80 -> stringResource(R.string.body_posture_status_optimal)
                                        score >= 60 -> stringResource(R.string.body_posture_status_mild)
                                        else -> stringResource(R.string.body_posture_status_correction)
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = scoreColor,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))

                    if (latest != null && score != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HangryRingGauge(
                                progress = (score / 100f).coerceIn(0f, 1f),
                                color = scoreColor,
                                modifier = Modifier.size(72.dp),
                                strokeWidth = 7.dp
                            ) {
                                Text(
                                    text = score.toString(),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = scoreColor
                                )
                            }
                            Spacer(modifier = Modifier.width(HangryTokens.Spacing.m))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.body_posture_latest_assessment),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = tokens.textMuted
                                )
                                Text(
                                    text = latest.date.format(POSTURE_DATE),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = tokens.textPrimary
                                )
                                Text(
                                    text = stringResource(R.string.body_posture_out_of_100),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = tokens.textMuted
                                )
                            }
                        }
                    } else {
                        // The page's single empty state: shown here rather than again under history.
                        DashEmptyState(
                            scene = DashEmptyScene.POSTURE,
                            title = stringResource(R.string.body_posture_no_scans_title),
                            body = stringResource(R.string.body_posture_no_scans_body),
                            actionLabel = stringResource(R.string.body_posture_start_first),
                            onAction = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onStartNewScan()
                            }
                        )
                    }

                    if (latest != null) {
                        Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))

                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onStartNewScan()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.body_posture_new_check))
                        }
                    }
                }
            }

            // Daily 3-Minute Reset Protocol
            item {
                HangryCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Spa,
                            contentDescription = null,
                            tint = tokens.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.body_posture_reset_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = tokens.textPrimary
                        )
                        HangryInfoTip(
                            title = stringResource(R.string.body_posture_reset_tip_title),
                            body = stringResource(R.string.body_posture_reset_tip_body)
                        )
                    }

                    PostureDrillItem(
                        step = "1",
                        name = stringResource(R.string.body_posture_drill_chin_tucks),
                        reps = stringResource(R.string.body_posture_sets_reps, 3, 10),
                        benefit = stringResource(R.string.body_posture_drill_chin_tucks_benefit)
                    )
                    PostureDrillItem(
                        step = "2",
                        name = stringResource(R.string.body_posture_drill_wall_slides),
                        reps = stringResource(R.string.body_posture_sets_reps, 2, 12),
                        benefit = stringResource(R.string.body_posture_drill_wall_slides_benefit)
                    )
                    PostureDrillItem(
                        step = "3",
                        name = stringResource(R.string.body_posture_drill_glute_bridge),
                        reps = stringResource(R.string.body_posture_sets_reps, 2, 15),
                        benefit = stringResource(R.string.body_posture_drill_glute_bridge_benefit)
                    )

                    if (onNavigateToAiCoach != null) {
                        Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))
                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onNavigateToAiCoach()
                            },
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, tokens.cardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.body_posture_ask_dash_drills),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = tokens.textPrimary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = tokens.textMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (uiState.scans.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.body_posture_scan_history),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = tokens.textPrimary,
                        modifier = Modifier.padding(top = HangryTokens.Spacing.xs)
                    )
                }
                items(uiState.scans, key = { it.id }) { scan ->
                    PostureScanRow(
                        scan = scan,
                        onClick = { onOpenScan(scan.id) },
                        onDelete = {
                            viewModel.hideScan(scan)
                            pendingDelete = scan
                        }
                    )
                }
            }
        }
    }
}

/** One-line drill row: step · name · sets×reps, with the benefit behind an (i). */
@Composable
private fun PostureDrillItem(
    step: String,
    name: String,
    reps: String,
    benefit: String
) {
    val tokens = LocalHangryTokens.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(tokens.cardBorder.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = step,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = tokens.textPrimary
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = tokens.textPrimary,
            maxLines = 1,
            modifier = Modifier.weight(1f, fill = false)
        )
        HangryInfoTip(title = name, body = benefit)
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = reps,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun PostureScanRow(scan: PostureScanEntity, onClick: () -> Unit, onDelete: () -> Unit) {
    val tokens = LocalHangryTokens.current
    val haptic = LocalHapticFeedback.current
    val score = scan.score
    val scoreColor = when {
        score >= 80 -> tokens.scoreColors.primed
        score >= 60 -> tokens.scoreColors.balanced
        else -> tokens.scoreColors.rebuild
    }

    HangryCard(
        modifier = Modifier.clickable {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = scoreColor.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "$score",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = scoreColor,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = scan.date.format(POSTURE_DATE),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = tokens.textPrimary
                    )
                    Text(
                        text = if (score >= 80) stringResource(R.string.body_posture_status_optimal) else if (score >= 60) stringResource(R.string.body_posture_row_minor) else stringResource(R.string.body_posture_row_correction),
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.textMuted
                    )
                }
            }
            IconButton(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onDelete()
            }) {
                Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = stringResource(R.string.body_posture_delete), tint = tokens.textMuted)
            }
        }
    }
}
