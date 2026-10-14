package com.kevan.hangry.ui.posture

import com.kevan.hangry.R
import androidx.compose.ui.res.stringResource
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.kevan.hangry.data.local.entity.exercises
import com.kevan.hangry.data.local.entity.findings
import com.kevan.hangry.data.local.entity.photoPaths
import com.kevan.hangry.domain.model.PostureExercise
import com.kevan.hangry.ui.coach.DashExpression
import com.kevan.hangry.ui.coach.DashMood
import com.kevan.hangry.ui.components.HangryCard
import com.kevan.hangry.ui.components.HangryInfoTip
import com.kevan.hangry.ui.components.POSTURE_POSES
import com.kevan.hangry.ui.components.PoseGuide
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens
import com.kevan.hangry.util.rememberMultiPhotoCaptureLauncher
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostureCaptureScreen(
    viewModel: PostureViewModel,
    onNavigateBack: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalHangryTokens.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val photoLauncher = rememberMultiPhotoCaptureLauncher(
        maxItems = (MAX_POSTURE_PHOTOS - uiState.capturedPhotos.size).coerceAtLeast(2)
    ) { uris: List<Uri> ->
        viewModel.addPhotos(uris)
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.clearCapture() }
    }

    val savedScan = uiState.justSavedScan

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                expandedHeight = 56.dp,
                title = { Text(if (savedScan != null) stringResource(R.string.body_posture_check_saved) else stringResource(R.string.body_posture_new_check)) },
                navigationIcon = {
                    IconButton(onClick = if (savedScan != null) onDone else onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.body_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (savedScan != null) {
            PostureResultContent(
                modifier = modifier.padding(innerPadding),
                score = savedScan.score,
                findings = savedScan.findings(),
                exercises = savedScan.exercises(),
                onDone = onDone
            )
            return@Scaffold
        }

        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = HangryTokens.Spacing.m, vertical = HangryTokens.Spacing.s),
            verticalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.section)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.body_posture_add_photos_hint, MAX_POSTURE_PHOTOS),
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.textSecondary,
                    modifier = Modifier.weight(1f, fill = false)
                )
                HangryInfoTip(
                    title = stringResource(R.string.body_posture_photo_tips_title),
                    body = stringResource(R.string.body_posture_photo_tips_body)
                )
            }

            PoseGuide(poses = POSTURE_POSES)

            if (uiState.capturedPhotos.isNotEmpty()) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.s)
                ) {
                    uiState.capturedPhotos.forEachIndexed { index, uri ->
                        Box(modifier = Modifier.size(96.dp)) {
                            AsyncImage(
                                model = uri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(12.dp))
                            )
                            IconButton(
                                onClick = { viewModel.removePhoto(index) },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(28.dp)
                                    .background(tokens.cardBackground, RoundedCornerShape(50))
                            ) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = stringResource(R.string.body_posture_remove), tint = tokens.textPrimary)
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { photoLauncher.takePhoto() },
                    enabled = uiState.capturedPhotos.size < MAX_POSTURE_PHOTOS,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.body_posture_take_photo))
                }
                Button(
                    onClick = { photoLauncher.pickFromGallery() },
                    enabled = uiState.capturedPhotos.size < MAX_POSTURE_PHOTOS,
                    modifier = Modifier.weight(1.3f)
                ) {
                    Text(stringResource(R.string.body_posture_select_gallery))
                }
            }

            if (uiState.isAnalyzing) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DashExpression(mood = DashMood.THINKING, size = 56.dp, contentDescription = null)
                    Spacer(modifier = Modifier.width(HangryTokens.Spacing.s))
                    Text(stringResource(R.string.body_posture_analyzing), style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary)
                }
            } else {
                Button(
                    onClick = { viewModel.analyze() },
                    enabled = uiState.capturedPhotos.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val count = uiState.capturedPhotos.size
                    Text(
                        if (count == 0) stringResource(R.string.body_posture_add_one_photo)
                        else if (count > 1) stringResource(R.string.body_posture_analyze_photos, count)
                        else stringResource(R.string.body_posture_analyze_photo_one, count)
                    )
                }
            }
        }
    }
}

@Composable
private fun PostureResultContent(
    score: Int,
    findings: List<String>,
    exercises: List<PostureExercise>,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalHangryTokens.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = HangryTokens.Spacing.m, vertical = HangryTokens.Spacing.s),
        verticalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.section)
    ) {
        PostureScoreRow(
            score = score,
            mood = when {
                score >= 85 -> DashMood.CELEBRATE
                score >= 70 -> DashMood.HAPPY
                score >= 55 -> DashMood.CHEER
                else -> DashMood.CONCERNED
            }
        )

        if (findings.isNotEmpty()) {
            HangryCard {
                Text(text = stringResource(R.string.body_posture_findings), style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
                Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))
                findings.forEach {
                    Text(stringResource(R.string.body_posture_bullet, it), style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary)
                }
            }
        }

        if (exercises.isNotEmpty()) {
            PostureExercisesCard(exercises)
        }

        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.body_posture_done))
        }
        Spacer(modifier = Modifier.height(HangryTokens.Spacing.m))
    }
}

/** Compact score line shared by the capture result and scan detail screens. */
@Composable
internal fun PostureScoreRow(score: Int, mood: DashMood? = null) {
    val tokens = LocalHangryTokens.current
    HangryCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (mood != null) {
                DashExpression(mood = mood, size = 48.dp, contentDescription = null)
                Spacer(modifier = Modifier.width(HangryTokens.Spacing.s))
            }
            Text(
                text = stringResource(R.string.body_posture_score_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = tokens.textPrimary,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "$score",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = tokens.chartColors.hrv
            )
        }
    }
}

/** All recommended exercises in one card, separated by dividers. */
@Composable
internal fun PostureExercisesCard(exercises: List<PostureExercise>) {
    val tokens = LocalHangryTokens.current
    HangryCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = Icons.Default.FitnessCenter, contentDescription = null, tint = tokens.textSecondary, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.body_posture_recommended_exercises),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = tokens.textPrimary
            )
        }
        exercises.forEachIndexed { index, ex ->
            if (index > 0) HorizontalDivider(color = tokens.cardBorder)
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(ex.name, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary, modifier = Modifier.weight(1f))
                    Text(
                        stringResource(R.string.body_posture_exercise_detail, ex.sets.toString(), ex.reps.toString(), ex.targetArea),
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.textMuted
                    )
                }
                Text(ex.description, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
            }
        }
    }
}
