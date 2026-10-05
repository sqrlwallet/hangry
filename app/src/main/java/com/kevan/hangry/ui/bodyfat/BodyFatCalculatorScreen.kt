package com.kevan.hangry.ui.bodyfat

import com.kevan.hangry.R
import androidx.compose.ui.res.stringResource
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
import com.kevan.hangry.ui.coach.DashSpinner
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
                title = { Text(stringResource(R.string.body_bodyfat_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.body_back))
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
                // Keep the tape-measurement fields above the keyboard.
                .consumeWindowInsets(innerPadding)
                .imePadding()
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
                        text = stringResource(R.string.body_bodyfat_banner_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = tokens.textPrimary,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    HangryInfoTip(
                        title = stringResource(R.string.body_bodyfat_banner_tip_title),
                        body = stringResource(R.string.body_bodyfat_banner_tip_body)
                    )
                }
            }

            // 1. Biometrics & Circumferences Section
            Text(text = stringResource(R.string.body_bodyfat_section_biometrics), style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
            HangryCard {
                // Sex selection
                Text(text = stringResource(R.string.body_bodyfat_biological_sex), style = MaterialTheme.typography.labelMedium, color = tokens.textSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    BiologicalSex.values().forEach { sex ->
                        val isSelected = uiState.biologicalSex == sex
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.onSexSelected(sex) },
                            label = { Text(sexLabel(sex)) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                // Height, Weight, Age
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = uiState.heightCm,
                        onValueChange = viewModel::onHeightChanged,
                        label = { Text(stringResource(R.string.body_bodyfat_height_cm)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = uiState.weightKg,
                        onValueChange = viewModel::onWeightChanged,
                        label = { Text(stringResource(R.string.body_bodyfat_weight_kg)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = uiState.age,
                        onValueChange = viewModel::onAgeChanged,
                        label = { Text(stringResource(R.string.body_bodyfat_age)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(0.8f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = tokens.cardBorder)
                Spacer(modifier = Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.body_bodyfat_tape_circumferences),
                        style = MaterialTheme.typography.labelMedium,
                        color = tokens.textSecondary
                    )
                    HangryInfoTip(
                        title = stringResource(R.string.body_bodyfat_how_to_measure_title),
                        body = stringResource(R.string.body_bodyfat_how_to_measure_body)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = uiState.neckCm,
                        onValueChange = viewModel::onNeckChanged,
                        label = { Text(stringResource(R.string.body_bodyfat_neck_cm)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = uiState.chestCm,
                        onValueChange = viewModel::onChestChanged,
                        label = { Text(stringResource(R.string.body_bodyfat_chest_cm)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = uiState.waistCm,
                        onValueChange = viewModel::onWaistChanged,
                        label = { Text(stringResource(R.string.body_bodyfat_waist_cm)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = uiState.hipCm,
                        onValueChange = viewModel::onHipChanged,
                        label = { Text(stringResource(R.string.body_bodyfat_hips_cm)) },
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
                                label = stringResource(R.string.body_bodyfat_waist_height),
                                value = String.format(Locale.US, "%.2f", whtr),
                                status = if (whtr <= 0.5) stringResource(R.string.body_bodyfat_status_healthy) else stringResource(R.string.body_bodyfat_status_elevated),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        alg.waistToHipRatio?.let { whr ->
                            MetricBadge(
                                label = stringResource(R.string.body_bodyfat_waist_hip),
                                value = String.format(Locale.US, "%.2f", whr),
                                status = if (whr <= 0.9) stringResource(R.string.body_bodyfat_status_optimal) else stringResource(R.string.body_bodyfat_status_elevated),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        alg.chestToWaistRatio?.let { ctwr ->
                            MetricBadge(
                                label = stringResource(R.string.body_bodyfat_v_taper),
                                value = String.format(Locale.US, "%.2f", ctwr),
                                status = if (ctwr >= 1.2) stringResource(R.string.body_bodyfat_status_athletic) else stringResource(R.string.body_bodyfat_status_balanced),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onNavigateToBodyMetrics, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.body_bodyfat_see_all_metrics))
                    Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }

            // 2. Dedicated Calculation from Personal Biometric History
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = stringResource(R.string.body_bodyfat_section_history), style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
                HangryInfoTip(
                    title = stringResource(R.string.body_bodyfat_history_tip_title),
                    body = stringResource(R.string.body_bodyfat_history_tip_body)
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
                                text = stringResource(R.string.body_bodyfat_percent_value, hist.bodyFatPercentage.toString()),
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                color = tokens.scoreColors.primed
                            )
                            Text(
                                text = stringResource(R.string.body_bodyfat_history_estimate_label),
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
                                Text(text = stringResource(R.string.body_bodyfat_lean_mass), style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                                Text(
                                    text = stringResource(R.string.body_bodyfat_kg_value, hist.leanMassKg.toString()),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = tokens.scoreColors.primed
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(text = stringResource(R.string.body_bodyfat_fat_mass), style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                                Text(
                                    text = stringResource(R.string.body_bodyfat_kg_value, hist.fatMassKg.toString()),
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
                        Text(stringResource(R.string.body_bodyfat_save_history))
                    }
                } else {
                    val heightLabel = stringResource(R.string.body_bodyfat_field_height)
                    val weightLabel = stringResource(R.string.body_bodyfat_field_weight)
                    val ageLabel = stringResource(R.string.body_bodyfat_field_age)
                    val sexFieldLabel = stringResource(R.string.body_bodyfat_field_sex)
                    val missing = buildList {
                        if (uiState.heightCm.isBlank()) add(heightLabel)
                        if (uiState.weightKg.isBlank()) add(weightLabel)
                        if (uiState.age.isBlank()) add(ageLabel)
                        if (uiState.biologicalSex == null) add(sexFieldLabel)
                    }
                    Text(
                        text = stringResource(R.string.body_bodyfat_enter_to_estimate, missing.joinToString(", ")),
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.textSecondary
                    )
                }
            }

            // 3. U.S. Navy Standard Calculation
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = stringResource(R.string.body_bodyfat_section_navy), style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
                HangryInfoTip(
                    title = stringResource(R.string.body_bodyfat_navy_tip_title),
                    body = stringResource(R.string.body_bodyfat_navy_tip_body)
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
                                text = stringResource(R.string.body_bodyfat_percent_value, alg.bodyFatPercentage.toString()),
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                color = tokens.chartColors.trainingLoad
                            )
                            Text(
                                text = stringResource(R.string.body_bodyfat_estimated_body_fat),
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
                                Text(text = stringResource(R.string.body_bodyfat_lean_mass), style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                                Text(
                                    text = stringResource(R.string.body_bodyfat_kg_value, alg.leanMassKg.toString()),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = tokens.scoreColors.primed
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(text = stringResource(R.string.body_bodyfat_fat_mass), style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                                Text(
                                    text = stringResource(R.string.body_bodyfat_kg_value, alg.fatMassKg.toString()),
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
                        Text(stringResource(R.string.body_bodyfat_save_navy))
                    }
                } else {
                    val heightLabel = stringResource(R.string.body_bodyfat_field_height)
                    val neckLabel = stringResource(R.string.body_bodyfat_field_neck)
                    val waistLabel = stringResource(R.string.body_bodyfat_field_waist)
                    val hipsLabel = stringResource(R.string.body_bodyfat_field_hips)
                    val sexFieldLabel = stringResource(R.string.body_bodyfat_field_sex)
                    val missing = buildList {
                        if (uiState.heightCm.isBlank()) add(heightLabel)
                        if (uiState.neckCm.isBlank()) add(neckLabel)
                        if (uiState.waistCm.isBlank()) add(waistLabel)
                        if (uiState.biologicalSex == BiologicalSex.FEMALE && uiState.hipCm.isBlank()) add(hipsLabel)
                        if (uiState.biologicalSex == null) add(sexFieldLabel)
                    }
                    Text(
                        text = if (missing.isNotEmpty()) {
                            stringResource(R.string.body_bodyfat_enter_to_calculate, missing.joinToString(", "))
                        } else {
                            stringResource(R.string.body_bodyfat_waist_must_exceed_neck)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.textSecondary
                    )
                }
            }

            // 4. AI Multimodal Vision Analysis
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = stringResource(R.string.body_bodyfat_section_ai), style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
                HangryInfoTip(
                    title = stringResource(R.string.body_bodyfat_ai_tip_title),
                    body = stringResource(R.string.body_bodyfat_ai_tip_body)
                )
            }
            PoseGuide(poses = BODY_FAT_POSES)
            HangryCard {
                Text(
                    text = stringResource(R.string.body_bodyfat_add_photos_hint),
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
                                contentDescription = stringResource(R.string.body_bodyfat_physique_photo, index + 1),
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
                                    contentDescription = stringResource(R.string.body_bodyfat_remove),
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
                                Icon(imageVector = Icons.Default.PhotoCamera, contentDescription = stringResource(R.string.body_bodyfat_camera))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(stringResource(R.string.body_bodyfat_camera), style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        OutlinedButton(
                            onClick = { photoLauncher.pickFromGallery() },
                            modifier = Modifier.size(100.dp),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(4.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(imageVector = Icons.Default.PhotoLibrary, contentDescription = stringResource(R.string.body_bodyfat_gallery))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(stringResource(R.string.body_bodyfat_gallery), style = MaterialTheme.typography.labelSmall)
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
                                    text = if (!uiState.isAiEnabled) stringResource(R.string.body_bodyfat_enable_ai) else stringResource(R.string.body_bodyfat_configure_api_key),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = tokens.textPrimary
                                )
                                Text(
                                    text = stringResource(R.string.body_bodyfat_needed_for_ai),
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
                            DashSpinner(size = 28.dp, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.body_bodyfat_analyzing))
                        } else {
                            Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.body_bodyfat_analyze_with_ai, uiState.photos.size))
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
                                        text = stringResource(R.string.body_bodyfat_percent_value, ai.bodyFatPercentage.toString()),
                                        style = MaterialTheme.typography.headlineLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = tokens.scoreColors.primed
                                    )
                                    if (ai.confidenceRangeMin != null && ai.confidenceRangeMax != null) {
                                        Text(
                                            text = stringResource(R.string.body_bodyfat_confidence_range, ai.confidenceRangeMin.toString(), ai.confidenceRangeMax.toString()),
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
                                        Text(text = stringResource(R.string.body_bodyfat_lean_mass), style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                                        Text(
                                            text = stringResource(R.string.body_bodyfat_kg_value, ai.leanMassKg.toString()),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = tokens.scoreColors.primed
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(text = stringResource(R.string.body_bodyfat_fat_mass), style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                                        Text(
                                            text = stringResource(R.string.body_bodyfat_kg_value, ai.fatMassKg.toString()),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = tokens.textPrimary
                                        )
                                    }
                                }
                            }

                            if (ai.visualObservations.isNotEmpty()) {
                                Text(
                                    text = stringResource(R.string.body_bodyfat_visual_observations),
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
                                    text = stringResource(R.string.body_bodyfat_health_insights),
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
                                    text = stringResource(R.string.body_bodyfat_tape_alignment),
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
                                Text(stringResource(R.string.body_bodyfat_save_ai))
                            }
                        }
                    }
                }
            }

            // 5. Past Composition Scans History
            if (uiState.savedScans.isNotEmpty()) {
                Text(text = stringResource(R.string.body_bodyfat_section_history_scans), style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
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
                                            "AI_MULTIMODAL" -> stringResource(R.string.body_bodyfat_method_ai)
                                            "BIOMETRIC_HISTORY" -> stringResource(R.string.body_bodyfat_method_history)
                                            "REPORTED" -> stringResource(R.string.body_bodyfat_method_reported)
                                            else -> stringResource(R.string.body_bodyfat_method_navy)
                                        }
                                        Text(
                                            text = stringResource(R.string.body_bodyfat_percent_value, scan.bodyFatPercentage.toString()),
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
                                    val weightStr = scan.weightKg?.let { stringResource(R.string.body_bodyfat_scan_weight, it.toString()) }
                                    val lbmStr = scan.leanMassKg?.let { stringResource(R.string.body_bodyfat_scan_lbm, it.toString()) }
                                    val fatStr = scan.fatMassKg?.let { stringResource(R.string.body_bodyfat_scan_fat, it.toString()) }
                                    val metricsStr = listOfNotNull(weightStr, lbmStr, fatStr).joinToString(" • ")
                                    Text(
                                        text = "$dateStr • $metricsStr",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = tokens.textSecondary
                                    )
                                }
                                IconButton(onClick = { viewModel.deleteScan(scan.id) }) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = stringResource(R.string.body_bodyfat_delete_scan),
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
private fun sexLabel(sex: BiologicalSex): String = when (sex) {
    BiologicalSex.MALE -> stringResource(R.string.body_bodyfat_sex_male)
    BiologicalSex.FEMALE -> stringResource(R.string.body_bodyfat_sex_female)
    BiologicalSex.OTHER -> stringResource(R.string.body_bodyfat_sex_other)
}

@Composable
private fun categoryLabel(category: BodyFatCategory): String = when (category) {
    BodyFatCategory.ESSENTIAL_FAT -> stringResource(R.string.body_bodyfat_category_essential_fat)
    BodyFatCategory.ATHLETIC -> stringResource(R.string.body_bodyfat_category_athletic)
    BodyFatCategory.FITNESS -> stringResource(R.string.body_bodyfat_category_fitness)
    BodyFatCategory.AVERAGE -> stringResource(R.string.body_bodyfat_category_average)
    BodyFatCategory.ABOVE_AVERAGE -> stringResource(R.string.body_bodyfat_category_above_average)
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
            text = categoryLabel(category),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}
