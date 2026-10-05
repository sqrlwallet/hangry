package com.kevan.hangry.ui.posture

import com.kevan.hangry.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.kevan.hangry.data.local.entity.PostureScanEntity
import com.kevan.hangry.data.local.entity.exercises
import com.kevan.hangry.data.local.entity.findings
import com.kevan.hangry.data.local.entity.photoPaths
import com.kevan.hangry.domain.repository.PostureScanRepository
import com.kevan.hangry.ui.components.HangryCard
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostureScanDetailScreen(
    scanId: Long,
    postureScanRepository: PostureScanRepository,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalHangryTokens.current
    var scan by remember { mutableStateOf<PostureScanEntity?>(null) }

    LaunchedEffect(scanId) {
        scan = postureScanRepository.getById(scanId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(scan?.date?.toString() ?: stringResource(R.string.body_posture_scan)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.body_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        val current = scan
        if (current == null) {
            Box(modifier = modifier.fillMaxSize().padding(innerPadding))
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
                Text(text = stringResource(R.string.body_posture_score), style = MaterialTheme.typography.titleMedium, color = tokens.textSecondary)
                Text(text = "${current.score}", style = MaterialTheme.typography.displayMedium, color = tokens.chartColors.hrv)
            }

            val photos = current.photoPaths()
            if (photos.isNotEmpty()) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.s)
                ) {
                    photos.forEach { path ->
                        AsyncImage(
                            model = path,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(120.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    }
                }
            }

            val findings = current.findings()
            if (findings.isNotEmpty()) {
                HangryCard {
                    Text(text = stringResource(R.string.body_posture_findings), style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
                    Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))
                    findings.forEach {
                        Text(stringResource(R.string.body_posture_bullet, it), style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary)
                    }
                }
            }

            val exercises = current.exercises()
            if (exercises.isNotEmpty()) {
                Text(text = stringResource(R.string.body_posture_recommended_exercises), style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
                exercises.forEach { ex ->
                    HangryCard {
                        Text(ex.name, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(ex.description, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            stringResource(R.string.body_posture_exercise_detail, ex.sets.toString(), ex.reps.toString(), ex.targetArea),
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.textMuted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(HangryTokens.Spacing.m))
        }
    }
}
