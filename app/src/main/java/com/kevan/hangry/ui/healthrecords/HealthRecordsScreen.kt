package com.kevan.hangry.ui.healthrecords

import com.kevan.hangry.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import com.kevan.hangry.ui.coach.Celebrations
import com.kevan.hangry.ui.coach.DashCelebration
import com.kevan.hangry.ui.coach.DashSpinner
import com.kevan.hangry.ui.components.DashEmptyState
import com.kevan.hangry.ui.components.DashEmptyScene
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import com.kevan.hangry.domain.calculation.CycleTemperature
import com.kevan.hangry.domain.calculation.HealthMarkerCalculator
import com.kevan.hangry.domain.model.GoalDirection
import com.kevan.hangry.domain.model.HealthProfileItem
import com.kevan.hangry.domain.model.HealthProfileKind
import com.kevan.hangry.domain.model.HealthRecordsSnapshot
import com.kevan.hangry.domain.model.MarkerType
import com.kevan.hangry.ui.components.HangryCard
import com.kevan.hangry.ui.components.skinTempText
import com.kevan.hangry.ui.components.HangryInfoIconButton
import com.kevan.hangry.ui.components.HangryInfoSection
import com.kevan.hangry.ui.components.HangryInfoTip
import com.kevan.hangry.ui.components.MetricRangeBar
import com.kevan.hangry.ui.components.MetricStatusChip
import com.kevan.hangry.ui.components.UndoDeleteEffect
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens
import com.kevan.hangry.util.rememberMultiPhotoCaptureLauncher
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
private fun healthRecordsInfo(): List<HangryInfoSection> = listOf(
    HangryInfoSection(
        stringResource(R.string.body_records_info_tracking_title),
        stringResource(R.string.body_records_info_tracking_body)
    ),
    HangryInfoSection(
        stringResource(R.string.body_records_info_data_title),
        stringResource(R.string.body_records_info_data_body)
    ),
    HangryInfoSection(
        stringResource(R.string.body_records_info_hc_title),
        stringResource(R.string.body_records_info_hc_body)
    ),
    HangryInfoSection(
        stringResource(R.string.body_records_info_goals_title),
        stringResource(R.string.body_records_info_goals_body)
    )
)

/** Tab ids double as stable keys; this maps them to their on-screen labels. */
@Composable
private fun tabLabel(id: String): String = when (id) {
    "Markers" -> stringResource(R.string.body_records_tab_markers)
    "Profile" -> stringResource(R.string.body_records_tab_profile)
    "Cycle" -> stringResource(R.string.body_records_tab_cycle)
    else -> id
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthRecordsScreen(
    viewModel: HealthRecordsViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToAiSettings: () -> Unit = {},
    onCreateReport: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val records by viewModel.records.collectAsStateWithLifecycle()

    // A marker goal gets one confetti moment when a reading first reaches it.
    val context = LocalContext.current
    var celebration by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(records) {
        val reached = records.goals.filter { goal ->
            goal.type.isVisibleFor(records.sex) &&
                HealthMarkerCalculator.progress(goal, records.latest(goal.type)).reached &&
                Celebrations.claim(context, "marker_goal_${goal.type.id}_${goal.targetValue}_${goal.targetSecondary}")
        }
        if (reached.isNotEmpty()) {
            celebration = context.getString(
                R.string.body_records_goal_reached_message,
                reached.joinToString(context.getString(R.string.body_records_goal_join)) { it.type.label.lowercase() }
            )
        }
    }
    celebration?.let { message ->
        DashCelebration(title = stringResource(R.string.body_records_goal_reached_title), message = message, onDismiss = { celebration = null })
    }
    val import by viewModel.import.collectAsStateWithLifecycle()
    val scan by viewModel.scan.collectAsStateWithLifecycle()
    val aiEnabled by viewModel.aiEnabled.collectAsStateWithLifecycle()
    val scanSaved by viewModel.scanSaved.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scanLauncher = rememberMultiPhotoCaptureLauncher(maxItems = HealthRecordsViewModel.MAX_SCAN_PAGES) { viewModel.onScanPages(it) }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    // Saveable so an open sheet (and what's typed in it) survives rotation.
    var addingFor by rememberSaveable { mutableStateOf<MarkerType?>(null) }
    var showAddPicker by rememberSaveable { mutableStateOf(false) }
    var detailFor by rememberSaveable { mutableStateOf<MarkerType?>(null) }
    var goalFor by rememberSaveable { mutableStateOf<MarkerType?>(null) }
    var editingReadingId by rememberSaveable { mutableStateOf<Long?>(null) }

    // Deleted rows are hidden at once and only removed (with their Health Connect copies) once
    // the undo snackbar closes.
    var pendingDelete by remember { mutableStateOf<PendingRecordDelete?>(null) }
    UndoDeleteEffect(
        pending = pendingDelete,
        snackbarHostState = snackbar,
        message = {
            context.getString(if (it.isPeriod) R.string.body_records_period_deleted else R.string.body_records_reading_deleted)
        },
        onUndo = { if (it.isPeriod) viewModel.undoPeriodDelete(it.id) else viewModel.undoReadingDelete(it.id) },
        onCommit = { if (it.isPeriod) viewModel.commitPeriodDelete(it.id) else viewModel.commitReadingDelete(it.id) },
        onSettled = { pendingDelete = null }
    )

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { viewModel.onPermissionResult() }

    LaunchedEffect(scanSaved) {
        scanSaved?.let {
            snackbar.showSnackbar(context.resources.getQuantityString(R.plurals.body_records_scan_added, it, it))
            viewModel.clearScanSaved()
        }
    }

    LaunchedEffect(import.message) {
        import.message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearImportMessage()
        }
    }

    val tabs = buildList {
        add("Markers")
        add("Profile")
        if (records.showsFemaleHealth) add("Cycle")
    }
    // The Cycle tab disappears if sex changes away from female; fall back to the first tab.
    val selectedTab = tab.coerceAtMost(tabs.lastIndex)

    Scaffold(
        topBar = {
            TopAppBar(
                expandedHeight = 56.dp,
                title = { Text(stringResource(R.string.body_records_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.body_back)) }
                },
                actions = {
                    IconButton(onClick = onCreateReport) { Icon(Icons.Default.Description, contentDescription = stringResource(R.string.report_open)) }
                    HangryInfoIconButton(title = stringResource(R.string.body_records_about), sections = healthRecordsInfo())
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            if (tabs[selectedTab] == "Markers") {
                ExtendedFloatingActionButton(
                    onClick = { showAddPicker = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.body_records_add_reading)) }
                )
            }
        },
        // While the marker sheet is open it shows the snackbar itself (it would be hidden behind it).
        snackbarHost = { if (detailFor == null) SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            PrimaryTabRow(selectedTabIndex = selectedTab, containerColor = MaterialTheme.colorScheme.background) {
                tabs.forEachIndexed { index, title ->
                    Tab(selected = selectedTab == index, onClick = { tab = index }, text = { Text(tabLabel(title)) })
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = HangryTokens.Spacing.m, vertical = HangryTokens.Spacing.m)
                    // Room so the floating add button never covers the last card.
                    .padding(bottom = 72.dp),
                verticalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.section)
            ) {
                when (tabs[selectedTab]) {
                    "Markers" -> {
                        HealthConnectCard(
                            state = import,
                            onConnect = { permissionLauncher.launch(import.permissionsToRequest) },
                            onImport = viewModel::importFromHealthConnect
                        )
                        records.visibleMarkers.forEach { type ->
                            MarkerCard(records = records, type = type, onClick = { detailFor = type })
                        }
                        Text(
                            stringResource(R.string.body_records_disclaimer),
                            style = MaterialTheme.typography.labelSmall,
                            color = LocalHangryTokens.current.textMuted
                        )
                    }
                    "Profile" -> {
                        ProfileTab(records = records, viewModel = viewModel)
                    }
                    "Cycle" -> CycleTab(
                        records = records,
                        viewModel = viewModel,
                        onDeletePeriod = { id ->
                            viewModel.hidePeriod(id)
                            pendingDelete = PendingRecordDelete(id, isPeriod = true)
                        }
                    )
                }
            }
        }
    }

    if (showAddPicker) {
        MarkerPickerDialog(
            markers = records.visibleMarkers,
            onDismiss = { showAddPicker = false },
            onScan = {
                showAddPicker = false
                viewModel.openScan()
            },
            onPick = {
                showAddPicker = false
                addingFor = it
            }
        )
    }
    scan?.let { state ->
        ReportScanSheet(
            state = state,
            aiEnabled = aiEnabled,
            onTakePhoto = scanLauncher.takePhoto,
            onPickPhotos = scanLauncher.pickFromGallery,
            onRead = viewModel::readScan,
            onRestart = viewModel::restartScan,
            onUpdateRow = viewModel::updateScanRow,
            onSetDate = viewModel::setScanDate,
            onSave = viewModel::saveScan,
            onOpenAiSettings = {
                viewModel.dismissScan()
                onNavigateToAiSettings()
            },
            onDismiss = viewModel::dismissScan
        )
    }
    addingFor?.let { type ->
        ReadingSheet(
            type = type,
            onDismiss = { addingFor = null },
            onSave = { value, secondary, at, context, note ->
                viewModel.addReading(type, value, secondary, at, context, note)
                addingFor = null
            }
        )
    }
    detailFor?.let { type ->
        MarkerDetailSheet(
            records = records,
            type = type,
            onDismiss = { detailFor = null },
            onAddReading = { addingFor = type },
            onEditGoal = { goalFor = type },
            onEditReading = { editingReadingId = it.id },
            snackbarHostState = snackbar
        )
    }
    editingReadingId?.let { id ->
        val reading = records.readings.firstOrNull { it.id == id }
        if (reading == null) {
            // Deleted elsewhere (or the snapshot is still loading after a restore).
            if (records.readings.isNotEmpty()) LaunchedEffect(id) { editingReadingId = null }
        } else {
            ReadingSheet(
                type = reading.type,
                existing = reading,
                onDismiss = { editingReadingId = null },
                onSave = { value, secondary, at, glucoseContext, note ->
                    viewModel.updateReading(reading.id, value, secondary, at, glucoseContext, note)
                    editingReadingId = null
                },
                onDelete = {
                    editingReadingId = null
                    viewModel.hideReading(reading.id)
                    pendingDelete = PendingRecordDelete(reading.id, isPeriod = false)
                }
            )
        }
    }
    goalFor?.let { type ->
        GoalSheet(
            type = type,
            existing = records.goal(type),
            sex = records.sex,
            onDismiss = { goalFor = null },
            onSave = { target, secondary, date ->
                viewModel.setGoal(type, target, secondary, date)
                goalFor = null
            },
            onClear = {
                viewModel.clearGoal(type)
                goalFor = null
            }
        )
    }
}

/** A reading or period waiting out its undo snackbar. */
private data class PendingRecordDelete(val id: Long, val isPeriod: Boolean)

@Composable
private fun HealthConnectCard(state: HealthConnectImportState, onConnect: () -> Unit, onImport: () -> Unit) {
    val tokens = LocalHangryTokens.current
    if (!state.available) return
    HangryCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Favorite, contentDescription = null, tint = tokens.textSecondary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.body_records_health_connect), style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                Text(
                    text = when {
                        state.permissionsToRequest.isNotEmpty() ->
                            if (state.medicalRecordsSupported) stringResource(R.string.body_records_hc_allow_with_medical)
                            else stringResource(R.string.body_records_hc_allow_vitals)
                        state.medicalRecordsSupported -> stringResource(R.string.body_records_hc_importing_all)
                        else -> stringResource(R.string.body_records_hc_importing_vitals)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary
                )
            }
            when {
                state.isImporting -> DashSpinner(size = 32.dp, contentDescription = stringResource(R.string.body_records_importing))
                state.permissionsToRequest.isNotEmpty() -> TextButton(onClick = onConnect) { Text(stringResource(R.string.body_records_allow)) }
                else -> IconButton(onClick = onImport) { Icon(Icons.Default.Sync, contentDescription = stringResource(R.string.body_records_import_now)) }
            }
        }
    }
}

@Composable
private fun MarkerCard(records: HealthRecordsSnapshot, type: MarkerType, onClick: () -> Unit) {
    val tokens = LocalHangryTokens.current
    val latest = records.latest(type)
    val metric = HealthMarkerCalculator.describe(type, latest, records.sex)
    val goal = records.goal(type)
    HangryCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(type.label, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                if (latest != null) {
                    metric.status?.let { MetricStatusChip(it, metric.tone, Modifier.padding(top = 4.dp)) }
                    Text(
                        text = latest.date.format(DATE) + (latest.glucoseContext?.let { " · ${it.label.lowercase()}" } ?: ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.textMuted,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                } else {
                    Text(stringResource(R.string.body_records_no_readings), style = MaterialTheme.typography.bodySmall, color = tokens.textMuted)
                }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(metric.displayValue, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                    color = if (latest != null) tokens.textPrimary else tokens.textMuted)
                if (latest != null) {
                    Text(" ${type.canonicalUnit}", style = MaterialTheme.typography.labelMedium, color = tokens.textSecondary, modifier = Modifier.padding(bottom = 3.dp))
                }
            }
        }
        if (latest != null && metric.bands.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            MetricRangeBar(metric)
        }
        if (goal != null) {
            Spacer(Modifier.height(10.dp))
            GoalSummary(type = type, records = records)
        }
    }
}

@Composable
internal fun GoalSummary(type: MarkerType, records: HealthRecordsSnapshot) {
    val tokens = LocalHangryTokens.current
    val goal = records.goal(type) ?: return
    val progress = HealthMarkerCalculator.progress(goal, records.latest(type))
    val target = HealthMarkerCalculator.format(type, goal.targetValue, goal.targetSecondary)
    val arrow = if (goal.direction == GoalDirection.LOWER) "≤" else "≥"
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Flag, contentDescription = null, tint = tokens.textSecondary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.body_records_goal_summary, arrow, target, type.canonicalUnit) +
                (goal.targetDate?.let { stringResource(R.string.body_records_goal_by_date, it.format(DATE)) } ?: ""),
            style = MaterialTheme.typography.labelMedium,
            color = tokens.textSecondary,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = when {
                progress.reached -> stringResource(R.string.body_records_goal_reached)
                progress.remaining != null -> stringResource(R.string.body_records_goal_to_go, HealthMarkerCalculator.format(type, progress.remaining))
                else -> stringResource(R.string.body_records_goal_add_reading)
            },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (progress.reached) tokens.scoreColors.primed else tokens.textPrimary
        )
    }
    progress.fraction?.let { fraction ->
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { fraction.toFloat() },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = tokens.scoreColors.primed,
            trackColor = tokens.cardBorder,
            drawStopIndicator = {}
        )
    }
}

@Composable
private fun ProfileTab(records: HealthRecordsSnapshot, viewModel: HealthRecordsViewModel) {
    val tokens = LocalHangryTokens.current
    var adding by rememberSaveable { mutableStateOf<HealthProfileKind?>(null) }

    var pickingDueDate by rememberSaveable { mutableStateOf(false) }
    HangryCard(modifier = Modifier.fillMaxWidth()) {
        ProfileSection(
            title = stringResource(R.string.body_records_allergies),
            hint = stringResource(R.string.body_records_allergies_hint),
            items = records.allergies,
            onAdd = { adding = HealthProfileKind.ALLERGY },
            onDelete = viewModel::deleteProfileItem
        )
        HorizontalDivider(Modifier.padding(vertical = 4.dp), color = tokens.cardBorder.copy(alpha = 0.5f))
        ProfileSection(
            title = stringResource(R.string.body_records_conditions),
            hint = stringResource(R.string.body_records_conditions_hint),
            items = records.conditions,
            onAdd = { adding = HealthProfileKind.CONDITION },
            onDelete = viewModel::deleteProfileItem
        )
        if (records.showsFemaleHealth) {
            HorizontalDivider(Modifier.padding(vertical = 4.dp), color = tokens.cardBorder.copy(alpha = 0.5f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.body_records_pregnant), style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                HangryInfoTip(title = stringResource(R.string.body_records_pregnant), body = stringResource(R.string.body_records_pregnant_hint))
                Spacer(Modifier.weight(1f))
                Switch(checked = records.isPregnant, onCheckedChange = { viewModel.setPregnancy(it, records.pregnancyDueDate) })
            }
            if (records.isPregnant) {
                OutlinedButton(onClick = { pickingDueDate = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(records.pregnancyDueDate?.let { stringResource(R.string.body_records_due_date, it.format(DATE)) } ?: stringResource(R.string.body_records_add_due_date))
                }
            }
        }
    }

    if (records.showsFemaleHealth) {
        if (pickingDueDate) {
            HangryDatePickerDialog(
                initial = records.pregnancyDueDate ?: LocalDate.now().plusMonths(6),
                allowFuture = true,
                onDismiss = { pickingDueDate = false },
                onPick = {
                    viewModel.setPregnancy(true, it)
                    pickingDueDate = false
                }
            )
        }
    } else if (records.sex == null) {
        Text(
            stringResource(R.string.body_records_set_sex_female),
            style = MaterialTheme.typography.bodySmall,
            color = tokens.textMuted
        )
    }

    adding?.let { kind ->
        AddProfileItemDialog(
            kind = kind,
            onDismiss = { adding = null },
            onSave = { name, note ->
                viewModel.addProfileItem(kind, name, note)
                adding = null
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileSection(
    title: String,
    hint: String,
    items: List<HealthProfileItem>,
    onAdd: () -> Unit,
    onDelete: (Long) -> Unit
) {
    val tokens = LocalHangryTokens.current
    // Allergies drive the food allergen alerts, so removing one is confirmed.
    var removing by remember { mutableStateOf<HealthProfileItem?>(null) }
    removing?.let { item ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringResource(R.string.body_records_remove_item_confirm, item.name)) },
            confirmButton = {
                TextButton(onClick = { onDelete(item.id); removing = null }) { Text(stringResource(R.string.body_records_remove)) }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
            HangryInfoTip(title = title, body = hint)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onAdd) { Text(stringResource(R.string.body_records_add)) }
        }
        if (items.isEmpty()) {
            Text(stringResource(R.string.body_records_none_added), style = MaterialTheme.typography.bodySmall, color = tokens.textMuted)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items.forEach { item ->
                    val removeLabel = stringResource(R.string.body_records_remove_item, item.name)
                    // The whole chip is the remove button - a 16dp icon alone is too small to hit.
                    InputChip(
                        selected = false,
                        onClick = { removing = item },
                        label = { Text(item.name + (item.note?.let { " · $it" } ?: "")) },
                        trailingIcon = {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        modifier = Modifier.semantics { onClick(label = removeLabel, action = null) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CycleTab(records: HealthRecordsSnapshot, viewModel: HealthRecordsViewModel, onDeletePeriod: (Long) -> Unit) {
    val tokens = LocalHangryTokens.current
    var logging by rememberSaveable { mutableStateOf(false) }
    var editingPeriodId by rememberSaveable { mutableStateOf<Long?>(null) }
    val stats = HealthMarkerCalculator.cycleStats(records.periods.map { it.startDate to it.endDate }, LocalDate.now())
    val skinTemps by viewModel.skinTempByDate.collectAsStateWithLifecycle()
    val insight = CycleTemperature.insight(stats, skinTemps, LocalDate.now(), records.periods.map { it.startDate })
    // Once temperature confirms ovulation, the next period follows your own luteal length.
    val nextStart = insight?.predictedNextStart ?: stats.predictedNextStart

    HangryCard(modifier = Modifier.fillMaxWidth()) {
        if (stats.lastPeriodStart == null) {
            DashEmptyState(
                scene = DashEmptyScene.RECORDS,
                title = stringResource(R.string.body_records_no_periods_title),
                body = stringResource(R.string.body_records_no_periods_body),
                actionLabel = stringResource(R.string.body_records_log_period),
                onAction = { logging = true }
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.m)) {
                CycleStat(if (stats.inPeriodNow) stringResource(R.string.body_records_on_period) else stringResource(R.string.body_records_cycle_day), stats.currentCycleDay?.toString() ?: "—", Modifier.weight(1f))
                CycleStat(stringResource(R.string.body_records_next_period), nextStart?.format(DATE) ?: stringResource(R.string.body_records_need_two_cycles), Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.m)) {
                CycleStat(stringResource(R.string.body_records_avg_cycle), stats.averageCycleDays?.let { stringResource(R.string.body_records_days_value, it) } ?: "—", Modifier.weight(1f))
                CycleStat(stringResource(R.string.body_records_avg_period), stats.averagePeriodDays?.let { stringResource(R.string.body_records_days_value, it) } ?: "—", Modifier.weight(1f))
            }
            insight?.let { CyclePhase(it, hasTemperature = skinTemps.isNotEmpty()) }
            Spacer(Modifier.height(12.dp))
            Button(onClick = { logging = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.body_records_log_period))
            }
        }
    }
    Text(
        stringResource(R.string.body_records_prediction_disclaimer),
        style = MaterialTheme.typography.labelSmall,
        color = tokens.textMuted
    )

    if (records.periods.isNotEmpty()) {
        HangryCard(modifier = Modifier.fillMaxWidth()) {
            records.periods.take(12).forEachIndexed { index, period ->
                if (index > 0) HorizontalDivider(color = tokens.cardBorder.copy(alpha = 0.5f))
                // Tap a period to correct its dates or delete it.
                Row(
                    Modifier.fillMaxWidth()
                        .clickable(onClickLabel = stringResource(R.string.body_records_edit_period)) { editingPeriodId = period.id }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            period.startDate.format(DATE) + (period.endDate?.let { " – ${it.format(DATE)}" } ?: stringResource(R.string.body_records_period_ongoing)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = tokens.textPrimary
                        )
                        Text(period.source.label, style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = tokens.textMuted, modifier = Modifier.size(20.dp))
                }
            }
        }
    }

    if (logging) {
        LogPeriodDialog(
            onDismiss = { logging = false },
            onSave = { start, end ->
                viewModel.logPeriod(start, end)
                logging = false
            }
        )
    }
    editingPeriodId?.let { id ->
        records.periods.firstOrNull { it.id == id }?.let { period ->
            LogPeriodDialog(
                existing = period,
                onDismiss = { editingPeriodId = null },
                onSave = { start, end ->
                    viewModel.updatePeriod(period.id, start, end)
                    editingPeriodId = null
                },
                onDelete = {
                    editingPeriodId = null
                    onDeletePeriod(period.id)
                }
            )
        }
    }
}

@Composable
private fun CyclePhase(insight: CycleTemperature.Insight, hasTemperature: Boolean) {
    val tokens = LocalHangryTokens.current
    Spacer(Modifier.height(12.dp))
    CycleStat(
        stringResource(R.string.body_records_phase),
        stringResource(
            when (insight.phase) {
                CycleTemperature.Phase.MENSTRUAL -> R.string.body_records_phase_menstrual
                CycleTemperature.Phase.FOLLICULAR -> R.string.body_records_phase_follicular
                CycleTemperature.Phase.FERTILE_WINDOW -> R.string.body_records_phase_fertile
                CycleTemperature.Phase.LUTEAL -> if (insight.confirmedByTemperature) R.string.body_records_phase_luteal else R.string.body_records_phase_luteal_est
            }
        )
    )
    val note = when {
        insight.shiftDetectedOn != null && insight.shiftSizeC != null ->
            stringResource(R.string.body_records_temp_shift, skinTempText(insight.shiftSizeC), insight.shiftDetectedOn.format(DATE))
        hasTemperature && insight.phase != CycleTemperature.Phase.MENSTRUAL -> stringResource(R.string.body_records_temp_waiting)
        else -> null
    }
    note?.let {
        Spacer(Modifier.height(6.dp))
        Text(it, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
    }
    insight.personalLutealDays?.let {
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.body_records_luteal_learned, it), style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
    }
}

@Composable
private fun CycleStat(label: String, value: String, modifier: Modifier = Modifier) {
    val tokens = LocalHangryTokens.current
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = tokens.textPrimary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
    }
}

internal val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
