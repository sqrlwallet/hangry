package com.kevan.hangry.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kevan.hangry.R
import com.kevan.hangry.data.ai.OpenRouterClient
import com.kevan.hangry.data.local.dao.HeightDao
import com.kevan.hangry.data.local.dao.WeightDao
import com.kevan.hangry.data.local.entity.UserProfileEntity
import com.kevan.hangry.data.local.entity.WeightMeasurementEntity
import com.kevan.hangry.data.security.SecureKeyStore
import com.kevan.hangry.domain.calculation.CalorieCalculator
import com.kevan.hangry.domain.model.AgeMath
import com.kevan.hangry.domain.model.BiologicalSex
import com.kevan.hangry.domain.repository.LocalExportManager
import com.kevan.hangry.domain.repository.LocalStorageManager
import com.kevan.hangry.domain.repository.StorageBreakdown
import com.kevan.hangry.domain.repository.HealthSyncManager
import com.kevan.hangry.domain.repository.UserProfileRepository
import com.kevan.hangry.ui.bodyage.BirthdayPickerDialog
import com.kevan.hangry.ui.components.HangryCard
import com.kevan.hangry.ui.components.HangryInfoIconButton
import com.kevan.hangry.ui.components.HangryInfoSection
import com.kevan.hangry.ui.components.HangryInfoTip
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(
    syncManager: HealthSyncManager,
    exportManager: LocalExportManager,
    localStorageManager: LocalStorageManager,
    userProfileRepository: UserProfileRepository,
    weightDao: WeightDao,
    heightDao: HeightDao,
    calorieCalculator: CalorieCalculator,
    secureKeyStore: SecureKeyStore,
    openRouterClient: OpenRouterClient,
    onNavigateBack: () -> Unit,
    onNavigateToHistoricalSync: () -> Unit,
    onNavigateToDataSources: () -> Unit,
    onNavigateToPrivacyPolicy: () -> Unit = {},
    onResetToWelcome: () -> Unit,
    modifier: Modifier = Modifier,
    /** Opened from an "Open AI Settings" link: start with AI Features expanded and in view. */
    expandAiInitially: Boolean = false
) {
    val tokens = LocalHangryTokens.current
    val aiSectionRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(expandAiInitially) {
        if (expandAiInitially) {
            // Let the expand animation start so the whole section scrolls into view.
            kotlinx.coroutines.delay(250)
            aiSectionRequester.bringIntoView()
        }
    }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showDeleteHealthDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showEditGoalsDialog by remember { mutableStateOf(false) }
    var isDeletingHealth by remember { mutableStateOf(false) }
    var isResetting by remember { mutableStateOf(false) }
    var isOptimizingDb by remember { mutableStateOf(false) }
    var isCleaningOrphans by remember { mutableStateOf(false) }
    var storageBreakdown by remember { mutableStateOf<StorageBreakdown?>(null) }
    // Shared across Sync Now / Recalculate Baselines / Export - these all hit the same local
    // DB and Health Connect client, so running two at once serves no purpose and previously
    // let an impatient double-tap queue up duplicate work with no feedback that it happened.
    var isBusy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        storageBreakdown = localStorageManager.getStorageBreakdown()
    }

    val profile by userProfileRepository.getProfile().collectAsState(initial = null)
    val latestWeight by weightDao.getLatestWeight().collectAsState(initial = null)
    val latestHeight by heightDao.getLatestHeight().collectAsState(initial = null)
    // Health Connect (e.g. a smart scale) is the freshest source when present; the manually
    // entered profile value is only a fallback for people without a synced height reading.
    val effectiveHeightCm = latestHeight?.heightCm ?: profile?.heightCm

    val jsonExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                isBusy = true
                val json = exportManager.exportDataAsJson()
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                isBusy = false
                snackbarHostState.showSnackbar(context.getString(R.string.settings_json_export_saved))
            }
        }
    }
    val csvExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                isBusy = true
                val csv = exportManager.exportDataAsCsv()
                context.contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray()) }
                isBusy = false
                snackbarHostState.showSnackbar(context.getString(R.string.settings_csv_export_saved))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back)
                        )
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
            // Goals & Body Metrics Section
            Text(text = stringResource(R.string.settings_goals_body_metrics), style = MaterialTheme.typography.titleLarge, color = tokens.textPrimary)
            HangryCard {
                val currentProfile = profile
                val effectiveWeightKg = latestWeight?.weightKg ?: currentProfile?.currentWeightKg
                val hasBodyMetrics = currentProfile?.age != null && effectiveHeightCm != null && currentProfile.biologicalSex != null
                val bmrPreview = if (hasBodyMetrics && effectiveWeightKg != null) {
                    val sex = runCatching { BiologicalSex.valueOf(currentProfile!!.biologicalSex!!) }.getOrNull()
                    sex?.let { calorieCalculator.calculateBmr(effectiveWeightKg, effectiveHeightCm!!, currentProfile!!.age!!, it) }
                } else {
                    null
                }

                if (hasBodyMetrics) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = stringResource(R.string.settings_age), style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary)
                        Text(text = "${currentProfile?.age}", style = MaterialTheme.typography.bodyMedium, color = tokens.textPrimary)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = if (latestHeight != null) stringResource(R.string.settings_height_synced) else stringResource(R.string.settings_height),
                            style = MaterialTheme.typography.bodyMedium,
                            color = tokens.textSecondary
                        )
                        Text(text = stringResource(R.string.settings_value_cm, "${effectiveHeightCm?.toInt()}"), style = MaterialTheme.typography.bodyMedium, color = tokens.textPrimary)
                    }
                    if (effectiveWeightKg != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                text = if (latestWeight != null) stringResource(R.string.settings_current_weight_synced) else stringResource(R.string.settings_current_weight),
                                style = MaterialTheme.typography.bodyMedium,
                                color = tokens.textSecondary
                            )
                            Text(text = stringResource(R.string.settings_value_kg, "%.1f".format(java.util.Locale.US, effectiveWeightKg)), style = MaterialTheme.typography.bodyMedium, color = tokens.textPrimary)
                        }
                    }
                    if (bmrPreview != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = stringResource(R.string.settings_estimated_bmr), style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary)
                            Text(text = stringResource(R.string.settings_value_kcal, bmrPreview.toInt()), style = MaterialTheme.typography.bodyMedium, color = tokens.chartColors.trainingLoad)
                        }
                    }
                    val circumferencesList = listOfNotNull(
                        currentProfile?.neckCircumferenceCm?.let { stringResource(R.string.settings_circumference_neck, it.toInt()) },
                        currentProfile?.chestCircumferenceCm?.let { stringResource(R.string.settings_circumference_chest, it.toInt()) },
                        currentProfile?.waistCircumferenceCm?.let { stringResource(R.string.settings_circumference_waist, it.toInt()) },
                        currentProfile?.hipCircumferenceCm?.let { stringResource(R.string.settings_circumference_hips, it.toInt()) }
                    )
                    if (circumferencesList.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = stringResource(R.string.settings_circumferences), style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary)
                            Text(text = circumferencesList.joinToString(" • "), style = MaterialTheme.typography.bodyMedium, color = tokens.textPrimary)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = tokens.cardBorder)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (currentProfile?.weightGoalKg != null && currentProfile.goalTargetDate != null) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = stringResource(R.string.settings_weight_goal), style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary)
                        Text(text = stringResource(R.string.settings_weight_goal_by, currentProfile.weightGoalKg.toString(), currentProfile.goalTargetDate.toString()), style = MaterialTheme.typography.bodyMedium, color = tokens.textPrimary)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                } else if (!hasBodyMetrics) {
                    Text(
                        text = stringResource(R.string.settings_add_body_metrics_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.textSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                SettingsActionRow(
                    icon = Icons.Default.Edit,
                    title = stringResource(R.string.settings_edit_goals_title),
                    subtitle = stringResource(R.string.settings_edit_goals_subtitle),
                    info = stringResource(R.string.settings_edit_goals_info),
                    onClick = { showEditGoalsDialog = true }
                )
            }

            // Body fat, health records and widgets live in the More tab; only privacy stays here.
            HangryCard {
                // Privacy & Legal - kept to a single link rather than duplicating the wellness/
                // non-medical notice here too; that disclosure already lives on the same screen.
                SettingsActionRow(
                    icon = Icons.Default.Lock,
                    title = stringResource(R.string.settings_privacy_legal_title),
                    subtitle = stringResource(R.string.settings_privacy_legal_subtitle),
                    info = stringResource(R.string.settings_privacy_legal_info),
                    onClick = onNavigateToPrivacyPolicy
                )
            }

            // Everything below is tucked into collapsed sections so the screen stays short.
            BackgroundSection()
            RemindersSection()

            SettingsCollapsibleSection(
                title = stringResource(R.string.settings_sync_section_title),
                summary = stringResource(R.string.settings_sync_section_summary),
                icon = Icons.Default.Sync
            ) {
                SettingsActionRow(
                    icon = Icons.Default.Refresh,
                    title = stringResource(R.string.settings_sync_now_title),
                    subtitle = stringResource(R.string.settings_sync_now_subtitle),
                    enabled = !isBusy,
                    onClick = {
                        isBusy = true
                        coroutineScope.launch {
                            syncManager.syncRecent().collect { }
                            isBusy = false
                            snackbarHostState.showSnackbar(context.getString(R.string.settings_sync_completed))
                        }
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = tokens.cardBorder)
                SettingsActionRow(
                    icon = Icons.Default.History,
                    title = stringResource(R.string.settings_historical_sync_title),
                    subtitle = stringResource(R.string.settings_historical_sync_subtitle),
                    info = stringResource(R.string.settings_historical_sync_info),
                    onClick = onNavigateToHistoricalSync
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = tokens.cardBorder)
                SettingsActionRow(
                    icon = Icons.Default.Calculate,
                    title = stringResource(R.string.settings_recalculate_title),
                    subtitle = stringResource(R.string.settings_recalculate_subtitle),
                    info = stringResource(R.string.settings_recalculate_info),
                    enabled = !isBusy,
                    onClick = {
                        isBusy = true
                        coroutineScope.launch {
                            syncManager.recalculateAllBaselines().collect { }
                            isBusy = false
                            snackbarHostState.showSnackbar(context.getString(R.string.settings_recalculate_done))
                        }
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = tokens.cardBorder)
                SettingsActionRow(
                    icon = Icons.Default.Sensors,
                    title = stringResource(R.string.settings_data_sources_row_title),
                    subtitle = stringResource(R.string.settings_data_sources_row_subtitle),
                    onClick = onNavigateToDataSources
                )
            }

            // AI Features Section (opt-in - see AiFeaturesSection for the consent flow)
            SettingsCollapsibleSection(
                title = stringResource(R.string.settings_ai_features),
                summary = if (profile?.aiFeaturesEnabled == true) stringResource(R.string.settings_ai_summary_on) else stringResource(R.string.settings_ai_summary_off),
                icon = Icons.Default.SmartToy,
                initiallyExpanded = expandAiInitially,
                modifier = Modifier.bringIntoViewRequester(aiSectionRequester)
            ) {
                AiFeaturesSection(
                    userProfileRepository = userProfileRepository,
                    secureKeyStore = secureKeyStore,
                    openRouterClient = openRouterClient,
                    coroutineScope = coroutineScope,
                    snackbarHostState = snackbarHostState
                )
            }

            // Data Sovereignty & Export Section
            SettingsCollapsibleSection(
                title = stringResource(R.string.settings_export_section_title),
                summary = stringResource(R.string.settings_export_section_summary),
                icon = Icons.Default.FileDownload
            ) {
                SettingsActionRow(
                    icon = Icons.Default.FileDownload,
                    title = stringResource(R.string.settings_export_json_title),
                    subtitle = stringResource(R.string.settings_export_json_subtitle),
                    info = stringResource(R.string.settings_export_json_info),
                    enabled = !isBusy,
                    onClick = {
                        jsonExportLauncher.launch("hangry_export_${LocalDate.now()}.json")
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = tokens.cardBorder)
                SettingsActionRow(
                    icon = Icons.Default.TableChart,
                    title = stringResource(R.string.settings_export_csv_title),
                    subtitle = stringResource(R.string.settings_export_csv_subtitle),
                    enabled = !isBusy,
                    onClick = {
                        csvExportLauncher.launch("hangry_export_${LocalDate.now()}.csv")
                    }
                )
            }

            // Local Storage & Database Health Section
            SettingsCollapsibleSection(
                title = stringResource(R.string.settings_storage_section_title),
                summary = storageBreakdown?.let { stringResource(R.string.settings_storage_summary_using, StorageBreakdown.formatBytes(it.totalBytes)) }
                    ?: stringResource(R.string.settings_storage_summary_default),
                icon = Icons.Default.Storage
            ) {
                val breakdown = storageBreakdown
                if (breakdown != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.settings_storage_total),
                            style = MaterialTheme.typography.titleMedium,
                            color = tokens.textPrimary
                        )
                        Text(
                            text = StorageBreakdown.formatBytes(breakdown.totalBytes),
                            style = MaterialTheme.typography.titleMedium,
                            color = tokens.scoreColors.primed
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = stringResource(R.string.settings_storage_main_db), style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary)
                        Text(text = StorageBreakdown.formatBytes(breakdown.databaseBytes), style = MaterialTheme.typography.bodyMedium, color = tokens.textPrimary)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = stringResource(R.string.settings_storage_wal), style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary)
                        Text(text = StorageBreakdown.formatBytes(breakdown.walBytes), style = MaterialTheme.typography.bodyMedium, color = tokens.textPrimary)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = stringResource(R.string.settings_storage_food_photos), style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary)
                        Text(text = StorageBreakdown.formatBytes(breakdown.foodPhotosBytes), style = MaterialTheme.typography.bodyMedium, color = tokens.textPrimary)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = stringResource(R.string.settings_storage_posture_photos), style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary)
                        Text(text = StorageBreakdown.formatBytes(breakdown.posturePhotosBytes), style = MaterialTheme.typography.bodyMedium, color = tokens.textPrimary)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = stringResource(R.string.settings_storage_cache), style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary)
                        Text(text = StorageBreakdown.formatBytes(breakdown.cacheBytes), style = MaterialTheme.typography.bodyMedium, color = tokens.textPrimary)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = tokens.cardBorder)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                SettingsActionRow(
                    icon = Icons.Default.Speed,
                    title = if (isOptimizingDb) stringResource(R.string.settings_optimizing_db) else stringResource(R.string.settings_optimize_db_title),
                    subtitle = stringResource(R.string.settings_optimize_db_subtitle),
                    info = stringResource(R.string.settings_optimize_db_info),
                    enabled = !isBusy && !isOptimizingDb,
                    onClick = {
                        coroutineScope.launch {
                            isOptimizingDb = true
                            localStorageManager.optimizeDatabase()
                            storageBreakdown = localStorageManager.getStorageBreakdown()
                            isOptimizingDb = false
                            snackbarHostState.showSnackbar(context.getString(R.string.settings_optimize_db_done))
                        }
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = tokens.cardBorder)
                SettingsActionRow(
                    icon = Icons.Default.CleaningServices,
                    title = if (isCleaningOrphans) stringResource(R.string.settings_cleaning_orphans) else stringResource(R.string.settings_clean_orphans_title),
                    subtitle = stringResource(R.string.settings_clean_orphans_subtitle),
                    info = stringResource(R.string.settings_clean_orphans_info),
                    enabled = !isBusy && !isCleaningOrphans,
                    onClick = {
                        coroutineScope.launch {
                            isCleaningOrphans = true
                            val cleanedFiles = localStorageManager.cleanOrphanedFiles()
                            val freedCacheBytes = localStorageManager.clearCache()
                            storageBreakdown = localStorageManager.getStorageBreakdown()
                            isCleaningOrphans = false
                            snackbarHostState.showSnackbar(
                                if (cleanedFiles > 0 || freedCacheBytes > 0)
                                    context.getString(R.string.settings_clean_orphans_done, cleanedFiles, StorageBreakdown.formatBytes(freedCacheBytes))
                                else
                                    context.getString(R.string.settings_clean_orphans_nothing)
                            )
                        }
                    }
                )
            }

            // Danger Zone & Data Deletion
            SettingsCollapsibleSection(
                title = stringResource(R.string.settings_data_management_title),
                summary = stringResource(R.string.settings_data_management_summary),
                icon = Icons.Default.DeleteForever,
                titleColor = tokens.scoreColors.rebuild
            ) {
                SettingsActionRow(
                    icon = Icons.Default.DeleteForever,
                    title = stringResource(R.string.delete_all_data_title),
                    subtitle = stringResource(R.string.settings_delete_health_subtitle),
                    info = stringResource(R.string.settings_delete_health_info),
                    titleColor = tokens.scoreColors.rebuild,
                    onClick = { showDeleteHealthDialog = true }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = tokens.cardBorder)
                SettingsActionRow(
                    icon = Icons.Default.RestartAlt,
                    title = stringResource(R.string.settings_reset_title),
                    subtitle = stringResource(R.string.settings_reset_subtitle),
                    titleColor = tokens.scoreColors.rebuild,
                    onClick = { showResetDialog = true }
                )
            }

            Spacer(modifier = Modifier.height(HangryTokens.Spacing.l))
        }
    }

    // Delete All Health Data Confirmation Dialog
    if (showDeleteHealthDialog) {
        AlertDialog(
            onDismissRequest = { if (!isDeletingHealth) showDeleteHealthDialog = false },
            title = { Text(stringResource(R.string.delete_all_data_title)) },
            text = { Text(stringResource(R.string.delete_all_data_confirmation)) },
            confirmButton = {
                Button(
                    enabled = !isDeletingHealth,
                    onClick = {
                        isDeletingHealth = true
                        coroutineScope.launch {
                            syncManager.clearAllData()
                            localStorageManager.cleanOrphanedFiles()
                            localStorageManager.clearCache()
                            localStorageManager.optimizeDatabase()
                            storageBreakdown = localStorageManager.getStorageBreakdown()
                            isDeletingHealth = false
                            showDeleteHealthDialog = false
                            snackbarHostState.showSnackbar(context.getString(R.string.settings_delete_health_done))
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = tokens.scoreColors.rebuild)
                ) {
                    Text(stringResource(R.string.settings_delete_everything))
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isDeletingHealth,
                    onClick = { showDeleteHealthDialog = false }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Reset Application Confirmation Dialog
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { if (!isResetting) showResetDialog = false },
            title = { Text(stringResource(R.string.settings_reset_title)) },
            text = { Text(stringResource(R.string.settings_reset_confirmation)) },
            confirmButton = {
                Button(
                    enabled = !isResetting,
                    onClick = {
                        isResetting = true
                        coroutineScope.launch {
                            syncManager.clearAllData()
                            onResetToWelcome()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = tokens.scoreColors.rebuild)
                ) {
                    Text(stringResource(R.string.settings_reset_everything))
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isResetting,
                    onClick = { showResetDialog = false }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Edit Goals & Body Metrics Dialog
    if (showEditGoalsDialog) {
        EditGoalsDialog(
            profile = profile,
            initialWeightKg = latestWeight?.weightKg ?: profile?.currentWeightKg,
            onDismiss = { showEditGoalsDialog = false },
            onSave = { updated, newWeight ->
                coroutineScope.launch {
                    val scoringInputsChanged = updated.maxHeartRate != profile?.maxHeartRate ||
                        updated.sleepGoalMinutes != profile?.sleepGoalMinutes || updated.age != profile?.age
                    userProfileRepository.saveProfile(updated)
                    if (newWeight != null && newWeight > 0.0) {
                        val record = WeightMeasurementEntity(
                            recordFingerprint = "manual_entry_${Instant.now().toEpochMilli()}",
                            timestamp = Instant.now(),
                            weightKg = newWeight,
                            sourcePackageName = "com.kevan.hangry.manual"
                        )
                        weightDao.insertOrIgnore(listOf(record))
                    }
                    showEditGoalsDialog = false
                    snackbarHostState.showSnackbar(context.getString(R.string.settings_goals_saved))
                    // Strain zones and sleep need depend on these - rescore history with them.
                    if (scoringInputsChanged) syncManager.recalculateAllBaselines().collect { }
                }
            }
        )
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditGoalsDialog(
    profile: UserProfileEntity?,
    initialWeightKg: Double?,
    onDismiss: () -> Unit,
    onSave: (UserProfileEntity, Double?) -> Unit
) {
    val tokens = LocalHangryTokens.current
    var ageInput by remember { mutableStateOf(profile?.age?.toString() ?: "") }
    var dateOfBirth by remember { mutableStateOf(profile?.dateOfBirth) }
    var pickingBirthday by remember { mutableStateOf(false) }
    if (pickingBirthday) {
        BirthdayPickerDialog(initial = dateOfBirth, onDismiss = { pickingBirthday = false }, onPicked = {
            dateOfBirth = it
            ageInput = AgeMath.years(it).toString()
            pickingBirthday = false
        })
    }
    var heightInput by remember { mutableStateOf(profile?.heightCm?.toInt()?.toString() ?: "") }
    var currentWeightInput by remember { mutableStateOf(initialWeightKg?.toString() ?: profile?.currentWeightKg?.toString() ?: "") }
    var weightGoalInput by remember { mutableStateOf(profile?.weightGoalKg?.toString() ?: "") }
    var sleepGoalInput by remember {
        mutableStateOf(((profile?.sleepGoalMinutes ?: 480) / 60.0).let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() })
    }
    var maxHrInput by remember { mutableStateOf(profile?.maxHeartRate?.toString() ?: "") }
    var selectedSex by remember { mutableStateOf(profile?.biologicalSex?.let { runCatching { BiologicalSex.valueOf(it) }.getOrNull() }) }
    var targetDate by remember { mutableStateOf(profile?.goalTargetDate) }
    var neckInput by remember { mutableStateOf(profile?.neckCircumferenceCm?.toString() ?: "") }
    var chestInput by remember { mutableStateOf(profile?.chestCircumferenceCm?.toString() ?: "") }
    var waistInput by remember { mutableStateOf(profile?.waistCircumferenceCm?.toString() ?: "") }
    var hipInput by remember { mutableStateOf(profile?.hipCircumferenceCm?.toString() ?: "") }
    var showDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_goals_body_metrics), modifier = Modifier.weight(1f, fill = false))
                HangryInfoIconButton(
                    title = stringResource(R.string.settings_goals_body_metrics),
                    sections = listOf(
                        HangryInfoSection(
                            stringResource(R.string.settings_goals_info_privacy_heading),
                            stringResource(R.string.settings_goals_info_privacy_body)
                        ),
                        HangryInfoSection(
                            stringResource(R.string.settings_biological_sex),
                            stringResource(R.string.settings_goals_info_sex_body)
                        ),
                        HangryInfoSection(
                            stringResource(R.string.settings_height),
                            stringResource(R.string.settings_goals_info_height_body)
                        ),
                        HangryInfoSection(
                            stringResource(R.string.settings_current_weight),
                            stringResource(R.string.settings_goals_info_weight_body)
                        )
                    ),
                    compact = true
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_on_device_only),
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary
                )

                OutlinedTextField(
                    value = ageInput,
                    onValueChange = { ageInput = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(R.string.settings_age)) },
                    // With a birthday, age is worked out from it and always current.
                    readOnly = dateOfBirth != null,
                    supportingText = { Text(if (dateOfBirth != null) stringResource(R.string.settings_age_from_birthday) else stringResource(R.string.settings_age_add_birthday_hint)) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(onClick = { pickingBirthday = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(dateOfBirth?.let { stringResource(R.string.settings_birthday_value, it.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy"))) } ?: stringResource(R.string.settings_add_birthday))
                }

                Text(text = stringResource(R.string.settings_biological_sex), style = MaterialTheme.typography.labelMedium, color = tokens.textSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    BiologicalSex.values().forEach { sex ->
                        val isSelected = selectedSex == sex
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedSex = sex },
                            label = {
                                Text(
                                    when (sex) {
                                        BiologicalSex.MALE -> stringResource(R.string.settings_sex_male)
                                        BiologicalSex.FEMALE -> stringResource(R.string.settings_sex_female)
                                        BiologicalSex.OTHER -> stringResource(R.string.settings_sex_other)
                                    }
                                )
                            }
                        )
                    }
                }

                OutlinedTextField(
                    value = heightInput,
                    onValueChange = { heightInput = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(R.string.settings_height_cm_label)) },
                    supportingText = { Text(stringResource(R.string.settings_height_synced_priority)) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider(color = tokens.cardBorder)

                OutlinedTextField(
                    value = currentWeightInput,
                    onValueChange = { currentWeightInput = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text(stringResource(R.string.settings_current_weight_kg_label)) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = weightGoalInput,
                    onValueChange = { weightGoalInput = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text(stringResource(R.string.settings_weight_goal_kg_label)) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = sleepGoalInput,
                    onValueChange = { sleepGoalInput = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text(stringResource(R.string.settings_sleep_goal_hours_label)) },
                    supportingText = { Text(stringResource(R.string.settings_sleep_goal_supporting)) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                val estimatedMaxHr = com.kevan.hangry.domain.calculation.HeartRateZones.estimateMaxHr(ageInput.toIntOrNull()).toInt()
                OutlinedTextField(
                    value = maxHrInput,
                    onValueChange = { maxHrInput = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text(stringResource(R.string.settings_max_hr_label)) },
                    placeholder = { Text("$estimatedMaxHr") },
                    supportingText = {
                        Text(
                            if (maxHrInput.isBlank()) stringResource(R.string.settings_max_hr_estimated, estimatedMaxHr)
                            else stringResource(R.string.settings_max_hr_custom)
                        )
                    },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(targetDate?.let { stringResource(R.string.settings_target_date_value, it.toString()) } ?: stringResource(R.string.settings_choose_target_date))
                }

                HorizontalDivider(color = tokens.cardBorder)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.settings_body_circumferences_optional),
                        style = MaterialTheme.typography.titleSmall,
                        color = tokens.textPrimary,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    HangryInfoTip(
                        title = stringResource(R.string.settings_body_circumferences),
                        body = stringResource(R.string.settings_body_circumferences_info)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = neckInput,
                        onValueChange = { neckInput = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text(stringResource(R.string.settings_neck_cm_label)) },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = chestInput,
                        onValueChange = { chestInput = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text(stringResource(R.string.settings_chest_cm_label)) },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = waistInput,
                        onValueChange = { waistInput = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text(stringResource(R.string.settings_waist_cm_label)) },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = hipInput,
                        onValueChange = { hipInput = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text(stringResource(R.string.settings_hips_cm_label)) },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parsedCurrentWeight = currentWeightInput.toDoubleOrNull()
                    val updated = (profile ?: UserProfileEntity()).copy(
                        age = dateOfBirth?.let { AgeMath.years(it) } ?: ageInput.toIntOrNull(),
                        dateOfBirth = dateOfBirth,
                        biologicalSex = selectedSex?.name,
                        heightCm = heightInput.toDoubleOrNull(),
                        currentWeightKg = parsedCurrentWeight,
                        weightGoalKg = weightGoalInput.toDoubleOrNull(),
                        sleepGoalMinutes = sleepGoalInput.toDoubleOrNull()
                            ?.let { (it * 60).toInt().coerceIn(240, 720) } ?: (profile?.sleepGoalMinutes ?: 480),
                        goalTargetDate = targetDate,
                        maxHeartRate = maxHrInput.toIntOrNull()?.takeIf { it in 120..230 },
                        neckCircumferenceCm = neckInput.toDoubleOrNull(),
                        chestCircumferenceCm = chestInput.toDoubleOrNull(),
                        waistCircumferenceCm = waistInput.toDoubleOrNull(),
                        hipCircumferenceCm = hipInput.toDoubleOrNull(),
                        updatedAt = Instant.now()
                    )
                    onSave(updated, parsedCurrentWeight)
                }
            ) {
                Text(stringResource(R.string.settings_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = targetDate
                ?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        targetDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) {
                    Text(stringResource(R.string.settings_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
internal fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    titleColor: androidx.compose.ui.graphics.Color? = null,
    enabled: Boolean = true,
    info: String? = null,
    onClick: () -> Unit
) {
    val tokens = LocalHangryTokens.current
    val contentAlpha = if (enabled) 1f else 0.4f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp)
            .alpha(contentAlpha),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = titleColor ?: tokens.textSecondary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = titleColor ?: tokens.textPrimary
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary
                )
            }
        }
        if (info != null) {
            HangryInfoTip(title = title, body = info)
        }
    }
}

/**
 * A settings group that starts collapsed: a tappable header (icon, title, one-line summary,
 * chevron) that reveals its rows. Keeps Settings short for people who never need these.
 */
@Composable
internal fun SettingsCollapsibleSection(
    title: String,
    summary: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    titleColor: Color? = null,
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val tokens = LocalHangryTokens.current
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val expandedStateLabel = stringResource(R.string.settings_state_expanded)
    val collapsedStateLabel = stringResource(R.string.settings_state_collapsed)
    val chevronRotation by animateFloatAsState(if (expanded) 180f else 0f, label = "sectionChevron")
    HangryCard(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClickLabel = if (expanded) stringResource(R.string.settings_collapse_section, title) else stringResource(R.string.settings_expand_section, title)) { expanded = !expanded }
                .semantics { stateDescription = if (expanded) expandedStateLabel else collapsedStateLabel },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = titleColor ?: tokens.textSecondary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(HangryTokens.Spacing.m))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium, color = titleColor ?: tokens.textPrimary)
                Text(text = summary, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
            }
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = null,
                tint = tokens.textSecondary,
                modifier = Modifier.rotate(chevronRotation)
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column {
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = tokens.cardBorder)
                content()
            }
        }
    }
}
