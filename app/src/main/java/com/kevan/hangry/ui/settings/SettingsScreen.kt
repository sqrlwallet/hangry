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
import com.kevan.hangry.data.security.SecureKeyStore
import com.kevan.hangry.domain.calculation.CalorieCalculator
import com.kevan.hangry.domain.model.BiologicalSex
import com.kevan.hangry.domain.repository.LocalExportManager
import com.kevan.hangry.domain.repository.HealthSyncManager
import com.kevan.hangry.domain.repository.UserProfileRepository
import com.kevan.hangry.ui.components.HangryCard
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
    // Shared across Sync Now / Recalculate Baselines / Export - these all hit the same local
    // DB and Health Connect client, so running two at once serves no purpose and previously
    // let an impatient double-tap queue up duplicate work with no feedback that it happened.
    var isBusy by remember { mutableStateOf(false) }

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
                            text = "100% Local-First • Zero Cloud • Private Health Tracking",
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
                val hasBodyMetrics = currentProfile?.age != null && effectiveHeightCm != null && currentProfile.biologicalSex != null
                val bmrPreview = if (hasBodyMetrics && latestWeight != null) {
                    val sex = runCatching { BiologicalSex.valueOf(currentProfile!!.biologicalSex!!) }.getOrNull()
                    sex?.let { calorieCalculator.calculateBmr(latestWeight!!.weightKg, effectiveHeightCm!!, currentProfile!!.age!!, it) }
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
                    if (bmrPreview != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Estimated resting burn (BMR)", style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary)
                            Text(text = "${bmrPreview.toInt()} kcal", style = MaterialTheme.typography.bodyMedium, color = tokens.chartColors.trainingLoad)
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
                        text = "Add your body metrics to estimate calorie burn.",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.textSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                SettingsActionRow(
                    icon = Icons.Default.Edit,
                    title = "Edit Goals & Body Metrics",
                    subtitle = "Age, sex, height, and your weight goal",
                    onClick = { showEditGoalsDialog = true }
                )
            }

            // Synchronization Section
            Text(text = "Synchronization", style = MaterialTheme.typography.titleLarge, color = tokens.textPrimary)
            HangryCard {
                SettingsActionRow(
                    icon = Icons.Default.Refresh,
                    title = "Sync Now",
                    subtitle = "Fetch latest readings from Health Connect",
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
                    subtitle = "Re-import up to 2 years of Health Connect records",
                    onClick = onNavigateToHistoricalSync
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = tokens.cardBorder)
                SettingsActionRow(
                    icon = Icons.Default.Calculate,
                    title = "Recalculate Physiological Baselines",
                    subtitle = "Recompute daily summaries & recovery scores for all local data",
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
                    subtitle = "View connected apps and import status",
                    onClick = onNavigateToDataSources
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = tokens.cardBorder)
                SettingsActionRow(
                    icon = Icons.Default.Widgets,
                    title = "Home Screen Widgets",
                    subtitle = "Pin Steps, Calories, Sleep, Recovery & Quick Log widgets",
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
                    subtitle = "Save raw normalized daily summaries and recovery scores to a file",
                    enabled = !isBusy,
                    onClick = {
                        jsonExportLauncher.launch("hangry_export_${LocalDate.now()}.json")
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = tokens.cardBorder)
                SettingsActionRow(
                    icon = Icons.Default.TableChart,
                    title = "Export Data as CSV",
                    subtitle = "Save a tabular file for spreadsheet analysis",
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
                    subtitle = "Zero-cloud architecture, Health Connect audit & wellness notice",
                    onClick = onNavigateToPrivacyPolicy
                )
            }

            // Danger Zone & Data Deletion
            Text(text = "Data Management", style = MaterialTheme.typography.titleLarge, color = tokens.scoreColors.rebuild)
            HangryCard {
                SettingsActionRow(
                    icon = Icons.Default.DeleteForever,
                    title = "Delete All Health Data",
                    subtitle = "Permanently removes all imported sessions and derived scores",
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
                            isDeletingHealth = false
                            showDeleteHealthDialog = false
                            snackbarHostState.showSnackbar("All local health data permanently deleted.")
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
            onDismiss = { showEditGoalsDialog = false },
            onSave = { updated ->
                coroutineScope.launch {
                    userProfileRepository.saveProfile(updated)
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
    onDismiss: () -> Unit,
    onSave: (UserProfileEntity) -> Unit
) {
    val tokens = LocalHangryTokens.current
    var ageInput by remember { mutableStateOf(profile?.age?.toString() ?: "") }
    var heightInput by remember { mutableStateOf(profile?.heightCm?.toInt()?.toString() ?: "") }
    var weightGoalInput by remember { mutableStateOf(profile?.weightGoalKg?.toString() ?: "") }
    var selectedSex by remember { mutableStateOf(profile?.biologicalSex?.let { runCatching { BiologicalSex.valueOf(it) }.getOrNull() }) }
    var targetDate by remember { mutableStateOf(profile?.goalTargetDate) }
    var showDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Goals & Body Metrics") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Used only on-device to estimate your resting calorie burn and a personalized daily calorie target. Never shared.",
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
                Text(
                    text = "Used for the Mifflin-St Jeor resting-metabolism formula; OTHER averages the male/female coefficients.",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.textMuted
                )

                OutlinedTextField(
                    value = heightInput,
                    onValueChange = { heightInput = it.filter { c -> c.isDigit() } },
                    label = { Text("Height (cm)") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Used only as a fallback - a height synced from Health Connect always takes priority.",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.textMuted
                )

                HorizontalDivider(color = tokens.cardBorder)

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
                Text(
                    text = "We recommend a safe pace and cap the daily calorie target accordingly if this date is ambitious.",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.textMuted
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val updated = (profile ?: UserProfileEntity()).copy(
                        age = ageInput.toIntOrNull(),
                        biologicalSex = selectedSex?.name,
                        heightCm = heightInput.toDoubleOrNull(),
                        weightGoalKg = weightGoalInput.toDoubleOrNull(),
                        goalTargetDate = targetDate,
                        updatedAt = Instant.now()
                    )
                    onSave(updated)
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
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textSecondary
            )
        }
    }
}
