package com.kevan.hangry.ui.posture

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.kevan.hangry.data.local.entity.exercises
import com.kevan.hangry.data.local.entity.findings
import com.kevan.hangry.data.local.entity.photoPaths
import com.kevan.hangry.ui.components.HangryCard
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens
import com.kevan.hangry.util.rememberPhotoCaptureLauncher

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostureCaptureScreen(
    viewModel: PostureViewModel,
    onNavigateBack: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalHangryTokens.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val photoLauncher = rememberPhotoCaptureLauncher { uri: Uri -> viewModel.addPhoto(uri) }

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
                title = { Text(if (savedScan != null) "Posture Check Saved" else "New Posture Check") },
                navigationIcon = {
                    IconButton(onClick = if (savedScan != null) onDone else onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            verticalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.m)
        ) {
            HangryCard {
                Text(
                    text = "Take 1–$MAX_POSTURE_PHOTOS photos (side, front, or back) standing naturally. Athletic or casual clothing is fine. We'll analyze your alignment and save results automatically.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.textPrimary
                )
            }

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
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Remove", tint = tokens.textPrimary)
                        }
                    }
                }
                if (uiState.capturedPhotos.size < MAX_POSTURE_PHOTOS) {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(tokens.cardBackground)
                            .clickable { photoLauncher.takePhoto() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Add Photo", tint = tokens.textMuted)
                    }
                }
            }

            TextButton(
                onClick = { photoLauncher.pickFromGallery() },
                enabled = uiState.capturedPhotos.size < MAX_POSTURE_PHOTOS
            ) {
                Text("Or choose from gallery")
            }

            if (uiState.isAnalyzing) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(HangryTokens.Spacing.s))
                    Text("Analyzing and saving…", style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary)
                }
            } else {
                Button(
                    onClick = { viewModel.analyze() },
                    enabled = uiState.capturedPhotos.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val count = uiState.capturedPhotos.size
                    Text(
                        if (count == 0) "Add at least 1 photo to analyze"
                        else "Analyze Posture ($count photo${if (count > 1) "s" else ""})"
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
    exercises: List<com.kevan.hangry.domain.model.PostureExercise>,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalHangryTokens.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = HangryTokens.Spacing.m, vertical = HangryTokens.Spacing.s),
        verticalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.m)
    ) {
        HangryCard {
            Text(text = "Score", style = MaterialTheme.typography.titleMedium, color = tokens.textSecondary)
            Text(text = "$score", style = MaterialTheme.typography.displayMedium, color = tokens.chartColors.hrv)
        }

        if (findings.isNotEmpty()) {
            HangryCard {
                Text(text = "Findings", style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
                Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))
                findings.forEach {
                    Text("• $it", style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                }
            }
        }

        if (exercises.isNotEmpty()) {
            Text(text = "Recommended Exercises", style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
            exercises.forEach { ex ->
                HangryCard {
                    Text(ex.name, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(ex.description, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "${ex.sets} sets x ${ex.reps} · ${ex.targetArea}",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.textMuted
                    )
                }
            }
        }

        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text("Done")
        }
        Spacer(modifier = Modifier.height(HangryTokens.Spacing.m))
    }
}
