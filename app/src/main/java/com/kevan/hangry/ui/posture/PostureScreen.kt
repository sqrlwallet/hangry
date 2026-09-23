package com.kevan.hangry.ui.posture

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
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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
import com.kevan.hangry.ui.theme.CtaGradient
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens

private val ALIGNMENT_ZONE_SECTIONS = listOf(
    HangryInfoSection("Cervical Spine & Head", "Forward head angle & suboccipital compression"),
    HangryInfoSection("Thoracic & Scapulae", "Rounded shoulder posture & upper-crossed pattern"),
    HangryInfoSection("Lumbopelvic Rhythm", "Anterior/posterior pelvic tilt & spinal neutrality")
)

private val POSTURE_INFO_SECTIONS = listOf(
    HangryInfoSection(
        "How it works",
        "Submit 1–5 photos (front, side, or back) standing or sitting naturally. Athletic or casual clothing is fine. The AI evaluates posture alignment and suggests corrective exercises."
    ),
    HangryInfoSection(
        "Your photos",
        "Kept only in this app's private storage on your device, for your own before/after comparison - never re-uploaded anywhere after the one analysis call."
    )
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
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_posture)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    HangryInfoIconButton(title = "About Posture", sections = POSTURE_INFO_SECTIONS)
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
                verticalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.m)
            ) {
                HangryPendingNotice(message = "Enable AI Features in Settings to run a posture check.")
                HangryCard(modifier = Modifier.clickable { onNavigateToAiSettings() }) {
                    Text("Go to AI Settings", style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
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
            verticalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.m)
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
                val statusText = when {
                    score == null -> "Baseline Pending"
                    score >= 80 -> "Optimal Alignment"
                    score >= 60 -> "Mild Imbalance Detected"
                    else -> "Correction Recommended"
                }

                HangryCard(
                    cornerRadius = HangryTokens.CornerRadii.large,
                    contentPadding = HangryTokens.Spacing.l
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Biomechanical Score",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = tokens.textPrimary
                            )
                            HangryInfoTip(
                                title = "Biomechanical Score",
                                body = "Take 1-5 photos to analyze head, shoulder, and pelvic alignment. Front and lateral kinetic chain verified via computer vision."
                            )
                        }
                        Surface(
                            color = scoreColor.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.labelSmall,
                                color = scoreColor,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(HangryTokens.Spacing.m))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HangryRingGauge(
                            progress = ((score ?: 0) / 100f).coerceIn(0f, 1f),
                            color = scoreColor,
                            modifier = Modifier.size(100.dp),
                            strokeWidth = 9.dp
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = score?.toString() ?: "--",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = scoreColor
                                )
                                Text(
                                    text = "/ 100",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = tokens.textMuted
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(HangryTokens.Spacing.l))

                        Column(modifier = Modifier.weight(1f)) {
                            if (latest != null) {
                                Text(
                                    text = "Latest Assessment",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = tokens.textMuted
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = latest.date.toString(),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = tokens.textPrimary
                                )
                            } else {
                                Text(
                                    text = "No Scans Logged",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = tokens.textPrimary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Take 1–5 photos to get a score.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = tokens.textSecondary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(HangryTokens.Spacing.l))

                    // Luxury Action Capsule
                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onStartNewScan()
                        },
                        shape = RoundedCornerShape(26.dp),
                        color = Color.Transparent,
                        border = BorderStroke(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                listOf(Color.White.copy(alpha = 0.25f), Color.White.copy(alpha = 0.05f))
                            )
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .background(
                                brush = Brush.horizontalGradient(
                                    CtaGradient
                                ),
                                shape = RoundedCornerShape(26.dp)
                            )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(19.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (latest != null) "New Posture Check" else "Start First Posture Scan",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // Biomechanical Alignment Zones Card
            item {
                HangryCard(
                    cornerRadius = HangryTokens.CornerRadii.large,
                    contentPadding = HangryTokens.Spacing.m
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Kinetic Alignment Focus",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = tokens.textPrimary
                            )
                            HangryInfoIconButton(
                                title = "Alignment Zones",
                                sections = ALIGNMENT_ZONE_SECTIONS,
                                compact = true
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.AccessibilityNew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))

                    AlignmentZoneRow(
                        title = "Cervical Spine & Head",
                        status = "Craniovertebral Axis",
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    AlignmentZoneRow(
                        title = "Thoracic & Scapulae",
                        status = "Acromial Balance",
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    AlignmentZoneRow(
                        title = "Lumbopelvic Rhythm",
                        status = "Pelvic Neutral",
                        color = tokens.chartColors.sleep
                    )
                }
            }

            // Daily 3-Minute Reset Protocol
            item {
                HangryCard(
                    cornerRadius = HangryTokens.CornerRadii.large,
                    contentPadding = HangryTokens.Spacing.m
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Spa,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "3-Min Posture Reset",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = tokens.textPrimary
                            )
                            HangryInfoTip(
                                title = "Posture Reset",
                                body = "Quick restorative drills to counteract desk slouching and decompress the spine."
                            )
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(100.dp)
                        ) {
                            Text(
                                text = "Daily Habit",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(HangryTokens.Spacing.m))

                    PostureDrillItem(
                        step = "1",
                        name = "Chin Tucks",
                        reps = "3 sets · 10 reps",
                        benefit = "Retracts cervical spine & eases neck strain"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    PostureDrillItem(
                        step = "2",
                        name = "Scapular Wall Slides",
                        reps = "2 sets · 12 reps",
                        benefit = "Activates lower trapezius & opens chest"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    PostureDrillItem(
                        step = "3",
                        name = "Glute Bridge & Hip Opener",
                        reps = "2 sets · 15 reps",
                        benefit = "Restores neutral pelvic alignment"
                    )

                    if (onNavigateToAiCoach != null) {
                        Spacer(modifier = Modifier.height(HangryTokens.Spacing.m))
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
                                        text = "Ask Dash for personal drills",
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

            // History Section Header
            item {
                Text(
                    text = "Scan History",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = tokens.textPrimary,
                    modifier = Modifier.padding(top = HangryTokens.Spacing.xs)
                )
            }

            if (uiState.scans.isEmpty()) {
                item {
                    HangryCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FitnessCenter,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "No Scans Recorded Yet",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Medium,
                                        color = tokens.textPrimary
                                    )
                                    HangryInfoTip(
                                        title = "Scan History",
                                        body = "Your before-and-after posture timeline will be tracked here privately on your device."
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                items(uiState.scans, key = { it.id }) { scan ->
                    PostureScanRow(
                        scan = scan,
                        onClick = { onOpenScan(scan.id) },
                        onDelete = { viewModel.deleteScan(scan) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AlignmentZoneRow(
    title: String,
    status: String,
    color: Color
) {
    val tokens = LocalHangryTokens.current
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = tokens.textPrimary,
                modifier = Modifier.weight(1f)
            )
            Surface(
                color = color.copy(alpha = 0.12f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = color,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

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
                .size(28.dp)
                .clip(CircleShape)
                .background(tokens.cardBorder.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = step,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = tokens.textPrimary
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = tokens.textPrimary
                )
                Text(
                    text = reps,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
            Text(
                text = benefit,
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textSecondary
            )
        }
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
                        text = scan.date.toString(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = tokens.textPrimary
                    )
                    Text(
                        text = if (score >= 80) "Optimal Alignment" else if (score >= 60) "Minor Imbalance" else "Correction Needed",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.textMuted
                    )
                }
            }
            IconButton(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onDelete()
            }) {
                Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = "Delete", tint = tokens.textMuted)
            }
        }
    }
}
