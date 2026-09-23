package com.kevan.hangry.ui.bodyfat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.kevan.hangry.domain.model.BiologicalSex
import com.kevan.hangry.domain.model.BodyFatCategory
import com.kevan.hangry.ui.components.HangryCard
import com.kevan.hangry.ui.components.BODY_FAT_POSES
import com.kevan.hangry.ui.components.HangryInfoTip
import com.kevan.hangry.ui.components.PoseGuide
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens
import com.kevan.hangry.util.rememberMultiPhotoCaptureLauncher
import kotlinx.serialization.json.Json
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyFatCalculatorScreen(
    viewModel: BodyFatCalculatorViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToBodyMetrics: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val tokens = LocalHangryTokens.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val photoLauncher = rememberMultiPhotoCaptureLauncher(
        maxItems = (3 - uiState.photos.size).coerceAtLeast(2)
    ) { uris ->
        viewModel.addPhotos(uris)
    }

    LaunchedEffect(uiState.saveSuccessMessage) {
        uiState.saveSuccessMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissSaveMessage()
        }
    }

    LaunchedEffect(uiState.aiErrorMessage) {
        uiState.aiErrorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Body Fat & Composition") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = HangryTokens.Spacing.m, vertical = HangryTokens.Spacing.s),
            verticalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.m)
        ) {
            // Dual-Engine Banner
            HangryCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccessibilityNew,
                        contentDescription = null,
                        tint = tokens.chartColors.trainingLoad,
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        text = "Dual-Engine Body Composition",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = tokens.textPrimary,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    HangryInfoTip(
                        title = "Body Composition",
                        body = "Combine U.S. Navy tape measurements with AI multimodal vision for accurate body fat and lean mass tracking."
                    )
                }
            }

            // 1. Biometrics & Circumferences Section
            Text(text = "1. Biometrics & Circumferences", style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
            HangryCard {
                // Sex selection
                Text(text = "Biological Sex", style = MaterialTheme.typography.labelMedium, color = tokens.textSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    BiologicalSex.values().forEach { sex ->
                        val isSelected = uiState.biologicalSex == sex
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.onSexSelected(sex) },
                            label = { Text(sex.name.lowercase().replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                // Height, Weight, Age
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = uiState.heightCm,
                        onValueChange = viewModel::onHeightChanged,
                        label = { Text("Height (cm)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = uiState.weightKg,
                        onValueChange = viewModel::onWeightChanged,
                        label = { Text("Weight (kg)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = uiState.age,
                        onValueChange = viewModel::onAgeChanged,
                        label = { Text("Age") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(0.8f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = tokens.cardBorder)
                Spacer(modifier = Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Tape Circumferences (cm)",
                        style = MaterialTheme.typography.labelMedium,
                        color = tokens.textSecondary
                    )
                    HangryInfoTip(
                        title = "How to measure",
                        body = "Measure at narrowest point for waist and neck; widest point for hips and chest."
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = uiState.neckCm,
                        onValueChange = viewModel::onNeckChanged,
                        label = { Text("Neck (cm)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = uiState.chestCm,
                        onValueChange = viewModel::onChestChanged,
                        label = { Text("Chest (cm)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = uiState.waistCm,
                        onValueChange = viewModel::onWaistChanged,
                        label = { Text("Waist (cm)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = uiState.hipCm,
                        onValueChange = viewModel::onHipChanged,
                        label = { Text("Hips (cm)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }

                // Live Physical Indices
                val alg = uiState.algorithmicResult
                if (alg != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        alg.waistToHeightRatio?.let { whtr ->
                            MetricBadge(
                                label = "Waist/Height",
                                value = String.format(Locale.US, "%.2f", whtr),
                                status = if (whtr <= 0.5) "Healthy" else "Elevated",
                                modifier = Modifier.weight(1f)
                            )
                        }
                        alg.waistToHipRatio?.let { whr ->
                            MetricBadge(
                                label = "Waist/Hip",
                                value = String.format(Locale.US, "%.2f", whr),
                                status = if (whr <= 0.9) "Optimal" else "Elevated",
                                modifier = Modifier.weight(1f)
                            )
                        }
                        alg.chestToWaistRatio?.let { ctwr ->
                            MetricBadge(
                                label = "V-Taper",
                                value = String.format(Locale.US, "%.2f", ctwr),
                                status = if (ctwr >= 1.2) "Athletic" else "Balanced",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onNavigateToBodyMetrics, modifier = Modifier.align(Alignment.End)) {
                    Text("See all body metrics")
                    Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }

            // 2. Dedicated Calculation from Personal Biometric History
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "2. Health History Estimate", style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
                HangryInfoTip(
                    title = "Health History Estimate",
                    body = "Calculated automatically from your recorded height, weight history, age, and sex using the clinical Deurenberg body composition model. No measuring tape or photos required."
                )
            }
            HangryCard {
                val hist = uiState.historyResult
                if (hist != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "${hist.bodyFatPercentage}%",
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                color = tokens.scoreColors.primed
                            )
                            Text(
                                text = "Biometric History Estimate",
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.textSecondary
                            )
                        }
                        CategoryChip(category = hist.category)
                    }

                    if (hist.leanMassKg != null && hist.fatMassKg != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(text = "Lean Mass", style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                                Text(
                                    text = "${hist.leanMassKg} kg",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = tokens.scoreColors.primed
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(text = "Fat Mass", style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                                Text(
                                    text = "${hist.fatMassKg} kg",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = tokens.textPrimary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.saveAssessment(useAiResult = false, useHistoryResult = true) },
                        enabled = !uiState.isSaving,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save History Estimate")
                    }
                } else {
                    val missing = buildList {
                        if (uiState.heightCm.isBlank()) add("Height")
                        if (uiState.weightKg.isBlank()) add("Weight")
                        if (uiState.age.isBlank()) add("Age")
                        if (uiState.biologicalSex == null) add("Sex")
                    }
                    Text(
                        text = "Enter ${missing.joinToString(", ")} above to estimate.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.textSecondary
                    )
                }
            }

            // 3. U.S. Navy Standard Calculation
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "3. U.S. Navy Tape Method", style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
                HangryInfoTip(
                    title = "U.S. Navy Method",
                    body = "U.S. Navy standard calculation from your tape circumferences (neck, waist, and hips for women) plus height."
                )
            }
            HangryCard {
                val alg = uiState.algorithmicResult
                if (alg != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "${alg.bodyFatPercentage}%",
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                color = tokens.chartColors.trainingLoad
                            )
                            Text(
                                text = "Estimated Body Fat",
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.textSecondary
                            )
                        }
                        CategoryChip(category = alg.category)
                    }

                    if (alg.leanMassKg != null && alg.fatMassKg != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(text = "Lean Mass", style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                                Text(
                                    text = "${alg.leanMassKg} kg",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = tokens.scoreColors.primed
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(text = "Fat Mass", style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                                Text(
                                    text = "${alg.fatMassKg} kg",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = tokens.textPrimary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.saveAssessment(useAiResult = false) },
                        enabled = !uiState.isSaving,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save Navy Estimate")
                    }
                } else {
                    val missing = buildList {
                        if (uiState.heightCm.isBlank()) add("Height")
                        if (uiState.neckCm.isBlank()) add("Neck")
                        if (uiState.waistCm.isBlank()) add("Waist")
                        if (uiState.biologicalSex == BiologicalSex.FEMALE && uiState.hipCm.isBlank()) add("Hips")
                        if (uiState.biologicalSex == null) add("Sex")
                    }
                    Text(
                        text = if (missing.isNotEmpty()) {
                            "Enter ${missing.joinToString(", ")} above to calculate."
                        } else {
                            "Waist must exceed neck to calculate."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.textSecondary
                    )
                }
            }

            // 4. AI Multimodal Vision Analysis
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "4. AI Photo Analysis", style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
                HangryInfoTip(
                    title = "AI Photo Analysis",
                    body = "Take up to 3 photos following the pose guide: front, side, and back. Use even lighting with your full body in frame, and wear fitted gym clothes, shorts, or swimwear so your silhouette is clear."
                )
            }
            PoseGuide(poses = BODY_FAT_POSES)
            HangryCard {
                Text(
                    text = "Add 1–3 photos, one per pose above",
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.textSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Photo carousel
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    uiState.photos.forEachIndexed { index, uri ->
                        Box(modifier = Modifier.size(100.dp)) {
                            AsyncImage(
                                model = uri,
                                contentDescription = "Physique photo ${index + 1}",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(12.dp))
                            )
                            IconButton(
                                onClick = { viewModel.removePhoto(index) },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(24.dp)
                                    .background(tokens.cardBackground, RoundedCornerShape(50))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove",
                                    tint = tokens.textPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    if (uiState.photos.size < 3) {
                        OutlinedButton(
                            onClick = { photoLauncher.takePhoto() },
                            modifier = Modifier.size(100.dp),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(4.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(imageVector = Icons.Default.PhotoCamera, contentDescription = "Camera")
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Camera", style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        OutlinedButton(
                            onClick = { photoLauncher.pickFromGallery() },
                            modifier = Modifier.size(100.dp),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(4.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(imageVector = Icons.Default.PhotoLibrary, contentDescription = "Gallery")
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Gallery", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (!uiState.isAiEnabled || !uiState.hasApiKey) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = tokens.cardBackground),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(HangryTokens.CornerRadii.medium))
                            .clickable { onNavigateToSettings() }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.VpnKey, contentDescription = null, tint = tokens.scoreColors.primed)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (!uiState.isAiEnabled) "Enable AI Features in Settings" else "Configure OpenRouter API Key",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = tokens.textPrimary
                                )
                                Text(
                                    text = "Needed for AI photo analysis",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = tokens.textSecondary
                                )
                            }
                            Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = tokens.textSecondary)
                        }
                    }
                } else {
                    Button(
                        onClick = { viewModel.analyzeWithAi() },
                        enabled = !uiState.isAiAnalyzing && uiState.photos.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (uiState.isAiAnalyzing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Analyzing physique…")
                        } else {
                            Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Analyze with AI (${uiState.photos.size}/3 photos)")
                        }
                    }
                }

                // AI Result Display
                val ai = uiState.aiAnalysisResult
                AnimatedVisibility(visible = ai != null && ai.isValid) {
                    if (ai != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            HorizontalDivider(color = tokens.cardBorder)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "${ai.bodyFatPercentage}%",
                                        style = MaterialTheme.typography.headlineLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = tokens.scoreColors.primed
                                    )
                                    if (ai.confidenceRangeMin != null && ai.confidenceRangeMax != null) {
                                        Text(
                                            text = "Confidence: ${ai.confidenceRangeMin}% – ${ai.confidenceRangeMax}%",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = tokens.textSecondary
                                        )
                                    }
                                }
                                ai.category?.let { CategoryChip(category = it) }
                            }

                            if (ai.leanMassKg != null && ai.fatMassKg != null) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column {
                                        Text(text = "Lean Mass", style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                                        Text(
                                            text = "${ai.leanMassKg} kg",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = tokens.scoreColors.primed
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(text = "Fat Mass", style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                                        Text(
                                            text = "${ai.fatMassKg} kg",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = tokens.textPrimary
                                        )
                                    }
                                }
                            }

                            if (ai.visualObservations.isNotEmpty()) {
                                Text(
                                    text = "Visual Observations",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = tokens.textPrimary
                                )
                                ai.visualObservations.forEach { observation ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("•", color = tokens.chartColors.trainingLoad)
                                        Text(text = observation, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                                    }
                                }
                            }

                            if (ai.healthInsights.isNotEmpty()) {
                                Text(
                                    text = "Health Insights",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = tokens.textPrimary
                                )
                                ai.healthInsights.forEach { insight ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("•", color = tokens.scoreColors.primed)
                                        Text(text = insight, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                                    }
                                }
                            }

                            ai.circumferenceConsistencyNote?.let { note ->
                                Text(
                                    text = "Tape Measurement Alignment",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = tokens.textPrimary
                                )
                                Text(text = note, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                            }

                            Button(
                                onClick = { viewModel.saveAssessment(useAiResult = true) },
                                enabled = !uiState.isSaving,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Save AI Scan")
                            }
                        }
                    }
                }
            }

            // 5. Past Composition Scans History
            if (uiState.savedScans.isNotEmpty()) {
                Text(text = "5. Scan History", style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
                HangryCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        uiState.savedScans.take(10).forEachIndexed { index, scan ->
                            if (index > 0) {
                                HorizontalDivider(color = tokens.cardBorder)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        val methodColor = when (scan.method) {
                                            "AI_MULTIMODAL" -> tokens.scoreColors.primed
                                            "BIOMETRIC_HISTORY" -> tokens.scoreColors.rebuild
                                            else -> tokens.chartColors.trainingLoad
                                        }
                                        val methodLabel = when (scan.method) {
                                            "AI_MULTIMODAL" -> "AI Vision"
                                            "BIOMETRIC_HISTORY" -> "Health History"
                                            "REPORTED" -> "Reported"
                                            else -> "US Navy"
                                        }
                                        Text(
                                            text = "${scan.bodyFatPercentage}%",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = methodColor
                                        )
                                        Text(
                                            text = methodLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = tokens.textMuted
                                        )
                                    }
                                    val dateStr = scan.date.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
                                    val metricsStr = buildList {
                                        scan.weightKg?.let { add("${it}kg") }
                                        scan.leanMassKg?.let { add("LBM: ${it}kg") }
                                        scan.fatMassKg?.let { add("Fat: ${it}kg") }
                                    }.joinToString(" • ")
                                    Text(
                                        text = "$dateStr • $metricsStr",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = tokens.textSecondary
                                    )
                                }
                                IconButton(onClick = { viewModel.deleteScan(scan.id) }) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Delete scan",
                                        tint = tokens.textMuted,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricBadge(
    label: String,
    value: String,
    status: String,
    modifier: Modifier = Modifier
) {
    val tokens = LocalHangryTokens.current
    Card(
        colors = CardDefaults.cardColors(containerColor = tokens.cardBorder.copy(alpha = 0.35f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
            Text(text = value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = tokens.textPrimary)
            Text(text = status, style = MaterialTheme.typography.labelSmall, color = tokens.scoreColors.primed)
        }
    }
}

@Composable
private fun CategoryChip(category: BodyFatCategory) {
    val tokens = LocalHangryTokens.current
    val color = when (category) {
        BodyFatCategory.ESSENTIAL_FAT -> tokens.scoreColors.rebuild
        BodyFatCategory.ATHLETIC -> tokens.scoreColors.primed
        BodyFatCategory.FITNESS -> tokens.scoreColors.primed
        BodyFatCategory.AVERAGE -> tokens.chartColors.trainingLoad
        BodyFatCategory.ABOVE_AVERAGE -> tokens.scoreColors.rebuild
    }
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(
            text = category.displayName,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}
