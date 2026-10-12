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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.kevan.hangry.ui.components.HangryInfoIconButton
import com.kevan.hangry.ui.components.HangryInfoSection
import com.kevan.hangry.ui.components.HangryInfoTip
import com.kevan.hangry.ui.components.PoseGuide
import com.kevan.hangry.ui.components.UndoDeleteEffect
import com.kevan.hangry.ui.components.LengthInputField
import com.kevan.hangry.ui.components.WeightInputField
import com.kevan.hangry.ui.components.rememberBodyUnitFormatter
import com.kevan.hangry.domain.model.LengthUnit
import com.kevan.hangry.domain.model.WeightUnit
import com.kevan.hangry.data.local.entity.BodyFatScanEntity
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens
import com.kevan.hangry.util.rememberMultiPhotoCaptureLauncher
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
    val units = rememberBodyUnitFormatter()
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    // Hidden at once; only removed from storage when the undo snackbar closes.
    var pendingDelete by remember { mutableStateOf<BodyFatScanEntity?>(null) }
    UndoDeleteEffect(
        pending = pendingDelete,
        snackbarHostState = snackbarHostState,
        message = { context.getString(R.string.body_bodyfat_scan_deleted, it.date.format(SCAN_DATE)) },
        onUndo = { viewModel.undoDelete(it.id) },
        onCommit = { viewModel.commitDelete(it.id) },
        onSettled = { pendingDelete = null }
    )

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
                expandedHeight = 56.dp,
                title = { Text(stringResource(R.string.body_bodyfat_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.body_back))
                    }
                },
                actions = {
                    HangryInfoIconButton(
                        title = stringResource(R.string.body_bodyfat_banner_tip_title),
                        sections = listOf(HangryInfoSection(heading = "", body = stringResource(R.string.body_bodyfat_banner_tip_body)))
                    )
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
            verticalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.section)
        ) {
            // Biometrics & Circumferences
            HangryCard {
                CardHeader(icon = Icons.Default.Straighten, title = stringResource(R.string.body_bodyfat_section_biometrics))
                Spacer(modifier = Modifier.height(8.dp))
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

                // Height, Weight, Age - in the units chosen in Settings. Feet + inches or
                // stones + pounds take two boxes each, so weight gets its own row then.
                val heightField = @Composable { fieldModifier: Modifier ->
                    LengthInputField(
                        cmText = uiState.heightCm,
                        onCmTextChange = viewModel::onHeightChanged,
                        label = stringResource(R.string.body_measure_height),
                        height = true,
                        modifier = fieldModifier
                    )
                }
                val weightField = @Composable { fieldModifier: Modifier ->
                    WeightInputField(
                        kgText = uiState.weightKg,
                        onKgTextChange = viewModel::onWeightChanged,
                        label = stringResource(R.string.body_measure_weight),
                        modifier = fieldModifier
                    )
                }
                val ageField = @Composable { fieldModifier: Modifier ->
                    OutlinedTextField(
                        value = uiState.age,
                        onValueChange = viewModel::onAgeChanged,
                        label = { Text(stringResource(R.string.body_bodyfat_age)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = fieldModifier
                    )
                }
                if (units.lengthUnit == LengthUnit.CM && units.weightUnit != WeightUnit.ST_LB) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        heightField(Modifier.weight(1f))
                        weightField(Modifier.weight(1f))
                        ageField(Modifier.weight(0.8f))
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        heightField(Modifier.weight(2f))
                        ageField(Modifier.weight(1f))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    weightField(Modifier.fillMaxWidth())
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
                    LengthInputField(
                        cmText = uiState.neckCm,
                        onCmTextChange = viewModel::onNeckChanged,
                        label = stringResource(R.string.body_measure_neck),
                        modifier = Modifier.weight(1f)
                    )
                    LengthInputField(
                        cmText = uiState.chestCm,
                        onCmTextChange = viewModel::onChestChanged,
                        label = stringResource(R.string.body_measure_chest),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    LengthInputField(
                        cmText = uiState.waistCm,
                        onCmTextChange = viewModel::onWaistChanged,
                        label = stringResource(R.string.body_measure_waist),
                        modifier = Modifier.weight(1f)
                    )
                    LengthInputField(
                        cmText = uiState.hipCm,
                        onCmTextChange = viewModel::onHipChanged,
                        label = stringResource(R.string.body_measure_hips),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                // Measurements save on their own; an estimate is optional.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = viewModel::saveMeasurements,
                        enabled = !uiState.isSaving && listOf(uiState.heightCm, uiState.weightKg, uiState.age, uiState.neckCm, uiState.chestCm, uiState.waistCm, uiState.hipCm).any { it.isNotBlank() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.body_bodyfat_save_measurements))
                    }
                    TextButton(onClick = onNavigateToBodyMetrics) {
                        Text(stringResource(R.string.body_bodyfat_see_all_metrics))
                        Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            }

            // AI Photo Analysis
            PoseGuide(poses = BODY_FAT_POSES)
            HangryCard {
                CardHeader(icon = Icons.Default.AutoAwesome, title = stringResource(R.string.body_bodyfat_section_ai)) {
                    HangryInfoTip(
                        title = stringResource(R.string.body_bodyfat_ai_tip_title),
                        body = stringResource(R.string.body_bodyfat_ai_tip_body)
                    )
                }
                Text(
                    text = stringResource(R.string.body_bodyfat_add_photos_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Chosen photo thumbnails
                if (uiState.photos.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        uiState.photos.forEachIndexed { index, uri ->
                            Box(modifier = Modifier.size(72.dp)) {
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
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (uiState.photos.size < 3) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { photoLauncher.takePhoto() }, modifier = Modifier.weight(1f)) {
                            Icon(imageVector = Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.body_bodyfat_camera))
                        }
                        OutlinedButton(onClick = { photoLauncher.pickFromGallery() }, modifier = Modifier.weight(1f)) {
                            Icon(imageVector = Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.body_bodyfat_gallery))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

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

                // AI findings (the estimate itself lives in the Estimates card)
                val ai = uiState.aiAnalysisResult
                val hasAiNotes = ai != null && ai.isValid &&
                    (ai.visualObservations.isNotEmpty() || ai.healthInsights.isNotEmpty() || ai.circumferenceConsistencyNote != null)
                AnimatedVisibility(visible = hasAiNotes) {
                    if (ai != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            HorizontalDivider(color = tokens.cardBorder)

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
                        }
                    }
                }
            }

            // Estimates: one row per method
            HangryCard {
                CardHeader(icon = Icons.Default.Calculate, title = stringResource(R.string.body_bodyfat_section_estimates))
                Spacer(modifier = Modifier.height(4.dp))

                val hist = uiState.historyResult
                val alg = uiState.algorithmicResult
                val ai = uiState.aiAnalysisResult?.takeIf { it.isValid && it.bodyFatPercentage != null }
                val available = listOfNotNull(
                    EstimateMethod.AI.takeIf { ai != null },
                    EstimateMethod.NAVY.takeIf { alg != null },
                    EstimateMethod.HISTORY.takeIf { hist != null }
                )
                var selectedMethod by rememberSaveable { mutableStateOf<EstimateMethod?>(null) }
                val activeMethod = selectedMethod?.takeIf { it in available } ?: available.firstOrNull()
                val selectable = available.size > 1

                // Health history
                val heightLabel = stringResource(R.string.body_bodyfat_field_height)
                val weightLabel = stringResource(R.string.body_bodyfat_field_weight)
                val ageLabel = stringResource(R.string.body_bodyfat_field_age)
                val sexFieldLabel = stringResource(R.string.body_bodyfat_field_sex)
                val histMissing = buildList {
                    if (uiState.heightCm.isBlank()) add(heightLabel)
                    if (uiState.weightKg.isBlank()) add(weightLabel)
                    if (uiState.age.isBlank()) add(ageLabel)
                    if (uiState.biologicalSex == null) add(sexFieldLabel)
                }
                EstimateRow(
                    label = stringResource(R.string.body_bodyfat_method_history),
                    tipTitle = stringResource(R.string.body_bodyfat_history_tip_title),
                    tipBody = stringResource(R.string.body_bodyfat_history_tip_body),
                    percent = hist?.bodyFatPercentage,
                    category = hist?.category,
                    color = tokens.scoreColors.rebuild,
                    subtitle = if (hist == null) stringResource(R.string.body_bodyfat_enter_to_estimate, histMissing.joinToString(", ")) else null,
                    selected = selectable && activeMethod == EstimateMethod.HISTORY,
                    onSelect = { selectedMethod = EstimateMethod.HISTORY },
                )
                HorizontalDivider(color = tokens.cardBorder)

                // U.S. Navy tape
                val neckLabel = stringResource(R.string.body_bodyfat_field_neck)
                val waistLabel = stringResource(R.string.body_bodyfat_field_waist)
                val hipsLabel = stringResource(R.string.body_bodyfat_field_hips)
                val navyMissing = buildList {
                    if (uiState.heightCm.isBlank()) add(heightLabel)
                    if (uiState.neckCm.isBlank()) add(neckLabel)
                    if (uiState.waistCm.isBlank()) add(waistLabel)
                    if (uiState.biologicalSex == BiologicalSex.FEMALE && uiState.hipCm.isBlank()) add(hipsLabel)
                    if (uiState.biologicalSex == null) add(sexFieldLabel)
                }
                EstimateRow(
                    label = stringResource(R.string.body_bodyfat_method_navy),
                    tipTitle = stringResource(R.string.body_bodyfat_navy_tip_title),
                    tipBody = stringResource(R.string.body_bodyfat_navy_tip_body),
                    percent = alg?.bodyFatPercentage,
                    category = alg?.category,
                    color = tokens.chartColors.trainingLoad,
                    subtitle = when {
                        alg != null -> null
                        navyMissing.isNotEmpty() -> stringResource(R.string.body_bodyfat_enter_to_calculate, navyMissing.joinToString(", "))
                        else -> stringResource(R.string.body_bodyfat_waist_must_exceed_neck)
                    },
                    selected = selectable && activeMethod == EstimateMethod.NAVY,
                    onSelect = { selectedMethod = EstimateMethod.NAVY },
                )

                // AI photo analysis (only once a valid analysis exists)
                if (ai != null) {
                    HorizontalDivider(color = tokens.cardBorder)
                    EstimateRow(
                        label = stringResource(R.string.body_bodyfat_method_ai),
                        tipTitle = null,
                        tipBody = null,
                        percent = ai.bodyFatPercentage,
                        category = ai.category,
                        color = tokens.scoreColors.primed,
                        subtitle = if (ai.confidenceRangeMin != null && ai.confidenceRangeMax != null) {
                            stringResource(R.string.body_bodyfat_confidence_range, ai.confidenceRangeMin.toString(), ai.confidenceRangeMax.toString())
                        } else null,
                        selected = selectable && activeMethod == EstimateMethod.AI,
                        onSelect = { selectedMethod = EstimateMethod.AI },
                    )
                }

                // Lean / fat mass, shown once for the selected (or best available) estimate
                val (leanKg, fatKg) = when (activeMethod) {
                    EstimateMethod.AI -> ai?.leanMassKg to ai?.fatMassKg
                    EstimateMethod.NAVY -> alg?.leanMassKg to alg?.fatMassKg
                    EstimateMethod.HISTORY -> hist?.leanMassKg to hist?.fatMassKg
                    null -> null to null
                }
                if (leanKg != null && fatKg != null) {
                    HorizontalDivider(color = tokens.cardBorder)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(text = stringResource(R.string.body_bodyfat_lean_mass), style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                            Text(
                                text = units.weight(leanKg),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = tokens.scoreColors.primed
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(text = stringResource(R.string.body_bodyfat_fat_mass), style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                            Text(
                                text = units.weight(fatKg),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = tokens.textPrimary
                            )
                        }
                    }
                }
                // One clear save for the selected (or best available) estimate.
                if (activeMethod != null) {
                    val methodName = stringResource(
                        when (activeMethod) {
                            EstimateMethod.AI -> R.string.body_bodyfat_method_ai
                            EstimateMethod.NAVY -> R.string.body_bodyfat_method_navy
                            EstimateMethod.HISTORY -> R.string.body_bodyfat_method_history
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            when (activeMethod) {
                                EstimateMethod.AI -> viewModel.saveAssessment(useAiResult = true)
                                EstimateMethod.NAVY -> viewModel.saveAssessment(useAiResult = false)
                                EstimateMethod.HISTORY -> viewModel.saveAssessment(useAiResult = false, useHistoryResult = true)
                            }
                        },
                        enabled = !uiState.isSaving,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.body_bodyfat_save_estimate, methodName))
                    }
                    if (selectable) {
                        Text(
                            stringResource(R.string.body_bodyfat_tap_to_choose),
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.textMuted,
                            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp)
                        )
                    }
                }
            }

            // Past composition scans
            if (uiState.savedScans.isNotEmpty()) {
                HangryCard {
                    CardHeader(icon = Icons.Default.History, title = stringResource(R.string.body_bodyfat_section_history_scans))
                    Spacer(modifier = Modifier.height(8.dp))
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
                                    val dateStr = scan.date.format(SCAN_DATE)
                                    val weightStr = scan.weightKg?.let(units::weight)
                                    val lbmStr = scan.leanMassKg?.let { stringResource(R.string.body_bodyfat_scan_lbm, units.weight(it)) }
                                    val fatStr = scan.fatMassKg?.let { stringResource(R.string.body_bodyfat_scan_fat, units.weight(it)) }
                                    val metricsStr = listOfNotNull(weightStr, lbmStr, fatStr).joinToString(" • ")
                                    Text(
                                        text = "$dateStr • $metricsStr",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = tokens.textSecondary
                                    )
                                }
                                IconButton(onClick = {
                                    viewModel.hideScan(scan.id)
                                    pendingDelete = scan
                                }) {
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

private enum class EstimateMethod { AI, NAVY, HISTORY }

private val SCAN_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())

@Composable
private fun CardHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    trailing: @Composable (() -> Unit)? = null
) {
    val tokens = LocalHangryTokens.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 32.dp)) {
        Icon(imageVector = icon, contentDescription = null, tint = tokens.textSecondary, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = tokens.textPrimary,
            modifier = Modifier.weight(1f, fill = false)
        )
        trailing?.invoke()
    }
}

/** One method row in the Estimates card: label (+tip), value and category; tap to select it. */
@Composable
private fun EstimateRow(
    label: String,
    tipTitle: String?,
    tipBody: String?,
    percent: Double?,
    category: BodyFatCategory?,
    color: androidx.compose.ui.graphics.Color,
    subtitle: String?,
    selected: Boolean,
    onSelect: () -> Unit
) {
    val tokens = LocalHangryTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .then(if (selected) Modifier.background(tokens.cardBorder.copy(alpha = 0.35f)) else Modifier)
            .then(if (percent != null) Modifier.clickable(onClick = onSelect) else Modifier)
            .padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = label, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                if (tipTitle != null && tipBody != null) {
                    HangryInfoTip(title = tipTitle, body = tipBody)
                }
            }
            subtitle?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
            }
        }
        if (percent != null) {
            Text(
                text = stringResource(R.string.body_bodyfat_percent_value, percent.toString()),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
            category?.let { CategoryChip(category = it) }
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
