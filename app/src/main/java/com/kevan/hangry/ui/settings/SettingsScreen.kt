package com.kevan.hangry.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.res.painterResource
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
import com.kevan.hangry.domain.model.BiologicalSex
import com.kevan.hangry.domain.repository.LocalExportManager
import com.kevan.hangry.domain.repository.LocalStorageManager
import com.kevan.hangry.domain.repository.StorageBreakdown
import com.kevan.hangry.domain.repository.HealthSyncManager
import com.kevan.hangry.domain.repository.UserProfileRepository
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

@OptIn(ExperimentalMaterial3Api::class)
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
    onNavigateToHomeScreenWidgets: () -> Unit = {},
    onNavigateToBodyFatCalculator: () -> Unit = {},
    onNavigateToHealthRecords: () -> Unit = {},
    onResetToWelcome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalHangryTokens.current
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
                snackbarHostState.showSnackbar("JSON export saved.")
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
                snackbarHostState.showSnackbar("CSV export saved.")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings & Privacy") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
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
            // Brand Identity Card
            HangryCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.hangry_logo),
                        contentDescription = "Hangry Brand Logo",
                        modifier = Modifier.size(48.dp)
                    )
                    Column {
                        Text(
                            text = "Hangry",
                            style = MaterialTheme.typography.titleLarge,
                            color = tokens.textPrimary
                        )
                        Text(
                            text = "Local-first & private",
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.textSecondary
                        )
                    }
                }
            }

            // Goals & Body Metrics Section
            Text(text = "Goals & Body Metrics", style = MaterialTheme.typography.titleLarge, color = tokens.textPrimary)
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
                        Text(text = "Age", style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary)
                        Text(text = "${currentProfile?.age}", style = MaterialTheme.typography.bodyMedium, color = tokens.textPrimary)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = if (latestHeight != null) "Height (synced)" else "Height",
                            style = MaterialTheme.typography.bodyMedium,
                            color = tokens.textSecondary
                        )
                        Text(text = "${effectiveHeightCm?.toInt()} cm", style = MaterialTheme.typography.bodyMedium, color = tokens.textPrimary)
                    }
                    if (effectiveWeightKg != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                text = if (latestWeight != null) "Current weight (synced)" else "Current weight",
                                style = MaterialTheme.typography.bodyMedium,
                                color = tokens.textSecondary
                            )
                            Text(text = "%.1f kg".format(java.util.Locale.US, effectiveWeightKg), style = MaterialTheme.typography.bodyMedium, color = tokens.textPrimary)
                        }
                    }
                    if (bmrPreview != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Estimated resting burn (BMR)", style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary)
                            Text(text = "${bmrPreview.toInt()} kcal", style = MaterialTheme.typography.bodyMedium, color = tokens.chartColors.trainingLoad)
                        }
                    }
                    val circumferencesList = listOfNotNull(
                        currentProfile?.neckCircumferenceCm?.let { "Neck: ${it.toInt()}cm" },
                        currentProfile?.chestCircumferenceCm?.let { "Chest: ${it.toInt()}cm" },
                        currentProfile?.waistCircumferenceCm?.let { "Waist: ${it.toInt()}cm" },
                        currentProfile?.hipCircumferenceCm?.let { "Hips: ${it.toInt()}cm" }
                    )
                    if (circumferencesList.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Circumferences", style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary)
                            Text(text = circumferencesList.joinToString(" • "), style = MaterialTheme.typography.bodyMedium, color = tokens.textPrimary)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = tokens.cardBorder)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (currentProfile?.weightGoalKg != null && currentProfile.goalTargetDate != null) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "Weight goal", style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary)
                        Text(text = "${currentProfile.weightGoalKg} kg by ${currentProfile.goalTargetDate}", style = MaterialTheme.typography.bodyMedium, color = tokens.textPrimary)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                } else if (!hasBodyMetrics) {
                    Text(
                        text = "Add body metrics to estimate burn",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.textSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                SettingsActionRow(
                    icon = Icons.Default.Edit,
                    title = "Edit Goals & Body Metrics",
                    subtitle = "Age, height, weight & goals",
                    info = "Age, sex, height, current & goal weight, circumferences",
                    onClick = { showEditGoalsDialog = true }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = tokens.cardBorder)
                SettingsActionRow(
                    icon = Icons.Default.AccessibilityNew,
                    title = "AI Body Fat & Composition",
                    subtitle = "Photos & tape measurements",
                    info = "Calculate body fat % via photos & tape circumferences",
                    onClick = onNavigateToBodyFatCalculator
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = tokens.cardBorder)
                SettingsActionRow(
                    icon = Icons.Default.MonitorHeart,
                    title = "Health Records",
                    subtitle = "Blood pressure, labs, allergies & goals",
                    info = "Track blood pressure, blood sugar, cholesterol and testosterone, set goals, and add allergies and conditions - for your own tracking, not medical advice",
                    onClick = onNavigateToHealthRecords
                )
            }

            // Synchronization Section
            Text(text = "Synchronization", style = MaterialTheme.typography.titleLarge, color = tokens.textPrimary)
            HangryCard {
                SettingsActionRow(
                    icon = Icons.Default.Refresh,
                    title = "Sync Now",
                    subtitle = "Pull latest from Health Connect",
                    enabled = !isBusy,
                    onClick = {
                        isBusy = true
                        coroutineScope.launch {
                            syncManager.syncRecent().collect { }
                            isBusy = false
                            snackbarHostState.showSnackbar("Synchronization completed.")
                        }
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = tokens.cardBorder)
                SettingsActionRow(
                    icon = Icons.Default.History,
                    title = "Historical Sync Range",
                    subtitle = "Re-import past records",
                    info = "Re-import all available Health Connect records",
                    onClick = onNavigateToHistoricalSync
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = tokens.cardBorder)
                SettingsActionRow(
                    icon = Icons.Default.Calculate,
                    title = "Recalculate Physiological Baselines",
                    subtitle = "Recompute scores from local data",
                    info = "Recompute daily summaries & recovery scores for all local data",
                    enabled = !isBusy,
                    onClick = {
                        isBusy = true
                        coroutineScope.launch {
                            syncManager.recalculateAllBaselines().collect { }
                            isBusy = false
                            snackbarHostState.showSnackbar("All historical baselines & recovery scores successfully recalculated.")
                        }
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = tokens.cardBorder)
                SettingsActionRow(
                    icon = Icons.Default.Sensors,
                    title = "Data Sources & Checkpoints",
                    subtitle = "Connected apps & import status",
                    onClick = onNavigateToDataSources
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = tokens.cardBorder)
                SettingsActionRow(
                    icon = Icons.Default.Widgets,
                    title = "Home Screen Widgets",
                    subtitle = "Pin widgets to your home screen",
                    info = "Pin Steps, Calories, Sleep, Recovery & Quick Log widgets",
                    onClick = onNavigateToHomeScreenWidgets
                )
            }

            // AI Features Section (opt-in - see AiFeaturesSection for the consent flow)
            AiFeaturesSection(
                userProfileRepository = userProfileRepository,
                secureKeyStore = secureKeyStore,
                openRouterClient = openRouterClient,
                coroutineScope = coroutineScope,
                snackbarHostState = snackbarHostState
            )

            // Data Sovereignty & Export Section
            Text(text = "Data Sovereignty & Export", style = MaterialTheme.typography.titleLarge, color = tokens.textPrimary)
            HangryCard {
                SettingsActionRow(
                    icon = Icons.Default.FileDownload,
                    title = "Export Data as JSON",
                    subtitle = "Daily summaries & scores",
                    info = "Save raw normalized daily summaries and recovery scores to a file",
                    enabled = !isBusy,
                    onClick = {
                        jsonExportLauncher.launch("hangry_export_${LocalDate.now()}.json")
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = tokens.cardBorder)
                SettingsActionRow(
                    icon = Icons.Default.TableChart,
                    title = "Export Data as CSV",
                    subtitle = "For spreadsheet analysis",
                    enabled = !isBusy,
                    onClick = {
                        csvExportLauncher.launch("hangry_export_${LocalDate.now()}.csv")
                    }
                )
            }

            // Privacy & Legal - kept to a single link rather than duplicating the wellness/
            // non-medical notice here too; that disclosure already lives on the same screen.
            HangryCard {
                SettingsActionRow(
                    icon = Icons.Default.Lock,
                    title = "Privacy & Legal",
                    subtitle = "Privacy policy & wellness notice",
                    info = "Zero-cloud architecture, Health Connect audit & wellness notice",
                    onClick = onNavigateToPrivacyPolicy
                )
            }

            // Local Storage & Database Health Section
            Text(text = "Local Storage & Database Health", style = MaterialTheme.typography.titleLarge, color = tokens.textPrimary)
            HangryCard {
                val breakdown = storageBreakdown
                if (breakdown != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Total App Storage",
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
                        Text(text = "Main Database", style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary)
                        Text(text = StorageBreakdown.formatBytes(breakdown.databaseBytes), style = MaterialTheme.typography.bodyMedium, color = tokens.textPrimary)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "WAL & Shared Memory", style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary)
                        Text(text = StorageBreakdown.formatBytes(breakdown.walBytes), style = MaterialTheme.typography.bodyMedium, color = tokens.textPrimary)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "Food Photos", style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary)
                        Text(text = StorageBreakdown.formatBytes(breakdown.foodPhotosBytes), style = MaterialTheme.typography.bodyMedium, color = tokens.textPrimary)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "Posture Photos", style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary)
                        Text(text = StorageBreakdown.formatBytes(breakdown.posturePhotosBytes), style = MaterialTheme.typography.bodyMedium, color = tokens.textPrimary)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "Temporary Cache", style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary)
                        Text(text = StorageBreakdown.formatBytes(breakdown.cacheBytes), style = MaterialTheme.typography.bodyMedium, color = tokens.textPrimary)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = tokens.cardBorder)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                SettingsActionRow(
                    icon = Icons.Default.Speed,
                    title = if (isOptimizingDb) "Optimizing Database..." else "Optimize & Compact Database",
                    subtitle = "Reclaim space & speed up queries",
                    info = "Runs WAL checkpoint, reclaims freed disk pages & updates query stats",
                    enabled = !isBusy && !isOptimizingDb,
                    onClick = {
                        coroutineScope.launch {
                            isOptimizingDb = true
                            localStorageManager.optimizeDatabase()
                            storageBreakdown = localStorageManager.getStorageBreakdown()
                            isOptimizingDb = false
                            snackbarHostState.showSnackbar("Database compacted and optimized.")
                        }
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = tokens.cardBorder)
                SettingsActionRow(
                    icon = Icons.Default.CleaningServices,
                    title = if (isCleaningOrphans) "Cleaning Orphaned Files..." else "Clean Orphaned Assets & Cache",
                    subtitle = "Remove unused images & cache",
                    info = "Removes unreferenced food/posture images and temporary cache files",
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
                                    "Cleaned $cleanedFiles orphaned file(s) and freed ${StorageBreakdown.formatBytes(freedCacheBytes)} cache."
                                else
                                    "Storage is clean. No orphaned files found."
                            )
                        }
                    }
                )
            }

            // Danger Zone & Data Deletion
            Text(text = "Data Management", style = MaterialTheme.typography.titleLarge, color = tokens.scoreColors.rebuild)
            HangryCard {
                SettingsActionRow(
                    icon = Icons.Default.DeleteForever,
                    title = "Delete All Health Data",
                    subtitle = "Removes sessions & scores",
                    info = "Permanently removes all imported sessions and derived scores",
                    titleColor = tokens.scoreColors.rebuild,
                    onClick = { showDeleteHealthDialog = true }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = tokens.cardBorder)
                SettingsActionRow(
                    icon = Icons.Default.RestartAlt,
                    title = "Reset Application",
                    subtitle = "Wipe all data and restart onboarding",
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
                            snackbarHostState.showSnackbar("All local health data permanently deleted and database compacted.")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = tokens.scoreColors.rebuild)
                ) {
                    Text("Delete Everything")
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
            title = { Text("Reset Application") },
            text = { Text("This permanently deletes all health data and restarts onboarding from scratch. This cannot be undone.") },
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
                    Text("Reset Everything")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isResetting,
                    onClick = { showResetDialog = false }
                ) {
                    Text("Cancel")
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
                    snackbarHostState.showSnackbar("Goals & body metrics saved.")
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
    var heightInput by remember { mutableStateOf(profile?.heightCm?.toInt()?.toString() ?: "") }
    var currentWeightInput by remember { mutableStateOf(initialWeightKg?.toString() ?: profile?.currentWeightKg?.toString() ?: "") }
    var weightGoalInput by remember { mutableStateOf(profile?.weightGoalKg?.toString() ?: "") }
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
                Text("Goals & Body Metrics", modifier = Modifier.weight(1f, fill = false))
                HangryInfoIconButton(
                    title = "Goals & Body Metrics",
                    sections = listOf(
                        HangryInfoSection(
                            "Privacy",
                            "Used only on-device to estimate resting calorie burn, body composition, and personalized goals. Never shared."
                        ),
                        HangryInfoSection(
                            "Biological sex",
                            "Used for Mifflin-St Jeor resting metabolism & U.S. Navy body fat formulas."
                        ),
                        HangryInfoSection(
                            "Height",
                            "Used only as a fallback - a height synced from Health Connect always takes priority."
                        ),
                        HangryInfoSection(
                            "Current weight",
                            "Saving records your current weight immediately for daily calorie targets and trend tracking."
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
                    text = "On-device only. Never shared.",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary
                )

                OutlinedTextField(
                    value = ageInput,
                    onValueChange = { ageInput = it.filter { c -> c.isDigit() } },
                    label = { Text("Age") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(text = "Biological sex", style = MaterialTheme.typography.labelMedium, color = tokens.textSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    BiologicalSex.values().forEach { sex ->
                        val isSelected = selectedSex == sex
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedSex = sex },
                            label = { Text(sex.name.lowercase().replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }

                OutlinedTextField(
                    value = heightInput,
                    onValueChange = { heightInput = it.filter { c -> c.isDigit() } },
                    label = { Text("Height (cm)") },
                    supportingText = { Text("Synced height takes priority") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider(color = tokens.cardBorder)

                OutlinedTextField(
                    value = currentWeightInput,
                    onValueChange = { currentWeightInput = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Current Weight (kg)") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = weightGoalInput,
                    onValueChange = { weightGoalInput = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Weight goal (kg)") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(targetDate?.let { "Target date: $it" } ?: "Choose a target date")
                }

                HorizontalDivider(color = tokens.cardBorder)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Body Circumferences (Optional)",
                        style = MaterialTheme.typography.titleSmall,
                        color = tokens.textPrimary,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    HangryInfoTip(
                        title = "Body Circumferences",
                        body = "Tape circumferences enable the algorithmic U.S. Navy body fat calculator and enhance AI vision accuracy."
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = neckInput,
                        onValueChange = { neckInput = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Neck (cm)") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = chestInput,
                        onValueChange = { chestInput = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Chest (cm)") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = waistInput,
                        onValueChange = { waistInput = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Waist (cm)") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = hipInput,
                        onValueChange = { hipInput = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Hips (cm)") },
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
                        age = ageInput.toIntOrNull(),
                        biologicalSex = selectedSex?.name,
                        heightCm = heightInput.toDoubleOrNull(),
                        currentWeightKg = parsedCurrentWeight,
                        weightGoalKg = weightGoalInput.toDoubleOrNull(),
                        goalTargetDate = targetDate,
                        neckCircumferenceCm = neckInput.toDoubleOrNull(),
                        chestCircumferenceCm = chestInput.toDoubleOrNull(),
                        waistCircumferenceCm = waistInput.toDoubleOrNull(),
                        hipCircumferenceCm = hipInput.toDoubleOrNull(),
                        updatedAt = Instant.now()
                    )
                    onSave(updated, parsedCurrentWeight)
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
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
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
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
