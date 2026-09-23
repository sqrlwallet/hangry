package com.kevan.hangry.ui.healthrecords

import com.kevan.hangry.ui.components.DashEmptyState
import com.kevan.hangry.ui.components.DashEmptyScene
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import com.kevan.hangry.domain.calculation.HealthMarkerCalculator
import com.kevan.hangry.domain.model.GoalDirection
import com.kevan.hangry.domain.model.HealthProfileItem
import com.kevan.hangry.domain.model.HealthProfileKind
import com.kevan.hangry.domain.model.HealthRecordsSnapshot
import com.kevan.hangry.domain.model.MarkerType
import com.kevan.hangry.ui.components.HangryCard
import com.kevan.hangry.ui.components.HangryInfoIconButton
import com.kevan.hangry.ui.components.HangryInfoSection
import com.kevan.hangry.ui.components.MetricRangeBar
import com.kevan.hangry.ui.components.MetricStatusChip
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val HEALTH_RECORDS_INFO = listOf(
    HangryInfoSection(
        "For tracking, not medical advice",
        "Hangry helps you keep your own numbers in one place and see trends. The ranges shown are general adult reference ranges from public guidelines - they aren't a diagnosis and can't account for your history or medication. Always talk to a doctor about your results."
    ),
    HangryInfoSection(
        "Your data stays yours",
        "Hangry is open source. These records are stored on your phone, not on anyone's server. The only time they leave it is when you chat with Ask Dash: they're included in the message sent to the AI provider you set up with your own key, so Dash can answer with your real numbers."
    ),
    HangryInfoSection(
        "Health Connect",
        "Blood pressure and blood sugar from home monitors are read from Health Connect. On phones that support Health Connect medical records (Android 16+), lab results, conditions and allergies shared by your clinic are imported too. Hangry never writes to your medical records."
    ),
    HangryInfoSection(
        "Goals",
        "Set a target for any marker - lower blood pressure, blood sugar or LDL, or raise HDL - and Hangry tracks how far you've come from where you started."
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthRecordsScreen(
    viewModel: HealthRecordsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val records by viewModel.records.collectAsState()
    val import by viewModel.import.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var addingFor by remember { mutableStateOf<MarkerType?>(null) }
    var showAddPicker by remember { mutableStateOf(false) }
    var detailFor by remember { mutableStateOf<MarkerType?>(null) }
    var goalFor by remember { mutableStateOf<MarkerType?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { viewModel.onPermissionResult() }

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
                title = { Text("Health Records") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = { HangryInfoIconButton(title = "About Health Records", sections = HEALTH_RECORDS_INFO) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            if (tabs[selectedTab] == "Markers") {
                ExtendedFloatingActionButton(
                    onClick = { showAddPicker = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Add reading") }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            PrimaryTabRow(selectedTabIndex = selectedTab, containerColor = MaterialTheme.colorScheme.background) {
                tabs.forEachIndexed { index, title ->
                    Tab(selected = selectedTab == index, onClick = { tab = index }, text = { Text(title) })
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = HangryTokens.Spacing.m, vertical = HangryTokens.Spacing.m),
                verticalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.m)
            ) {
                when (tabs[selectedTab]) {
                    "Markers" -> {
                        DisclaimerCard()
                        HealthConnectCard(
                            state = import,
                            onConnect = { permissionLauncher.launch(import.permissionsToRequest) },
                            onImport = viewModel::importFromHealthConnect
                        )
                        records.visibleMarkers.forEach { type ->
                            MarkerCard(records = records, type = type, onClick = { detailFor = type })
                        }
                        Spacer(Modifier.height(72.dp)) // clear the FAB
                    }
                    "Profile" -> {
                        DisclaimerCard()
                        ProfileTab(records = records, viewModel = viewModel)
                    }
                    "Cycle" -> CycleTab(records = records, viewModel = viewModel)
                }
            }
        }
    }

    if (showAddPicker) {
        MarkerPickerDialog(
            markers = records.visibleMarkers,
            onDismiss = { showAddPicker = false },
            onPick = {
                showAddPicker = false
                addingFor = it
            }
        )
    }
    addingFor?.let { type ->
        AddReadingSheet(
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
            onDeleteReading = viewModel::deleteReading
        )
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

@Composable
private fun DisclaimerCard() {
    val tokens = LocalHangryTokens.current
    Surface(color = tokens.brandAccentContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = tokens.brandAccent, modifier = Modifier.size(18.dp))
            Text(
                "For your own tracking - not medical advice. Hangry is open source and keeps these records on your phone. Talk to a doctor about your results.",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textPrimary
            )
        }
    }
}

@Composable
private fun HealthConnectCard(state: HealthConnectImportState, onConnect: () -> Unit, onImport: () -> Unit) {
    val tokens = LocalHangryTokens.current
    if (!state.available) return
    HangryCard(modifier = Modifier.fillMaxWidth(), contentPadding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Favorite, contentDescription = null, tint = tokens.textSecondary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Health Connect", style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                Text(
                    text = when {
                        state.permissionsToRequest.isNotEmpty() ->
                            "Allow access to import blood pressure and blood sugar" +
                                if (state.medicalRecordsSupported) ", plus lab results, conditions and allergies from your medical records." else "."
                        state.medicalRecordsSupported -> "Importing vitals and medical records."
                        else -> "Importing vitals. Medical records need Android 16 or later."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary
                )
            }
            when {
                state.isImporting -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                state.permissionsToRequest.isNotEmpty() -> TextButton(onClick = onConnect) { Text("Allow") }
                else -> IconButton(onClick = onImport) { Icon(Icons.Default.Sync, contentDescription = "Import now") }
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
    HangryCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), contentPadding = 14.dp) {
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
                    Text("No readings yet", style = MaterialTheme.typography.bodySmall, color = tokens.textMuted)
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
            text = "Goal $arrow $target ${type.canonicalUnit}" + (goal.targetDate?.let { " by ${it.format(DATE)}" } ?: ""),
            style = MaterialTheme.typography.labelMedium,
            color = tokens.textSecondary,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = when {
                progress.reached -> "Reached"
                progress.remaining != null -> "${HealthMarkerCalculator.format(type, progress.remaining)} to go"
                else -> "Add a reading"
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
    var adding by remember { mutableStateOf<HealthProfileKind?>(null) }

    ProfileSection(
        title = "Allergies",
        hint = "Dash avoids these in every food suggestion.",
        items = records.allergies,
        onAdd = { adding = HealthProfileKind.ALLERGY },
        onDelete = viewModel::deleteProfileItem
    )
    ProfileSection(
        title = "Conditions",
        hint = "Dash keeps these in mind for exercise and nutrition advice.",
        items = records.conditions,
        onAdd = { adding = HealthProfileKind.CONDITION },
        onDelete = viewModel::deleteProfileItem
    )

    if (records.showsFemaleHealth) {
        var pickingDueDate by remember { mutableStateOf(false) }
        HangryCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Pregnant", style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                    Text(
                        "Dash keeps exercise and nutrition suggestions pregnancy-appropriate and won't suggest a calorie deficit.",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.textSecondary
                    )
                }
                Switch(checked = records.isPregnant, onCheckedChange = { viewModel.setPregnancy(it, records.pregnancyDueDate) })
            }
            if (records.isPregnant) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { pickingDueDate = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(records.pregnancyDueDate?.let { "Due date: ${it.format(DATE)}" } ?: "Add due date (optional)")
                }
            }
        }
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
            "Set your sex to Female in Settings to track pregnancy and your menstrual cycle.",
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
    HangryCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                Text(hint, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
            }
            TextButton(onClick = onAdd) { Text("Add") }
        }
        if (items.isEmpty()) {
            Text("None added", style = MaterialTheme.typography.bodySmall, color = tokens.textMuted, modifier = Modifier.padding(top = 4.dp))
        } else {
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items.forEach { item ->
                    InputChip(
                        selected = false,
                        onClick = {},
                        label = { Text(item.name + (item.note?.let { " · $it" } ?: "")) },
                        trailingIcon = {
                            Icon(Icons.Default.Close, contentDescription = "Remove ${item.name}",
                                modifier = Modifier.size(16.dp).clickable { onDelete(item.id) })
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CycleTab(records: HealthRecordsSnapshot, viewModel: HealthRecordsViewModel) {
    val tokens = LocalHangryTokens.current
    var logging by remember { mutableStateOf(false) }
    val stats = HealthMarkerCalculator.cycleStats(records.periods.map { it.startDate to it.endDate }, LocalDate.now())

    HangryCard(modifier = Modifier.fillMaxWidth()) {
        if (stats.lastPeriodStart == null) {
            DashEmptyState(
                scene = DashEmptyScene.RECORDS,
                title = "No periods logged yet",
                body = "Log your periods here, or allow Health Connect on the Markers tab to import them from another app. Predictions start after two periods.",
                imageSize = 130.dp
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.m)) {
                CycleStat(if (stats.inPeriodNow) "On period" else "Cycle day", stats.currentCycleDay?.toString() ?: "—", Modifier.weight(1f))
                CycleStat("Next period", stats.predictedNextStart?.format(DATE) ?: "Need 2 cycles", Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.m)) {
                CycleStat("Avg cycle", stats.averageCycleDays?.let { String.format(Locale.US, "%.0f days", it) } ?: "—", Modifier.weight(1f))
                CycleStat("Avg period", stats.averagePeriodDays?.let { String.format(Locale.US, "%.0f days", it) } ?: "—", Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = { logging = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Log a period")
        }
    }
    Text(
        "Predictions are estimates from your own history and aren't reliable for contraception or fertility planning.",
        style = MaterialTheme.typography.labelSmall,
        color = tokens.textMuted
    )

    if (records.periods.isNotEmpty()) {
        HangryCard(modifier = Modifier.fillMaxWidth(), contentPadding = 12.dp) {
            records.periods.take(12).forEachIndexed { index, period ->
                if (index > 0) HorizontalDivider(color = tokens.cardBorder.copy(alpha = 0.5f))
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            period.startDate.format(DATE) + (period.endDate?.let { " – ${it.format(DATE)}" } ?: " – ongoing"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = tokens.textPrimary
                        )
                        Text(period.source.label, style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
                    }
                    IconButton(onClick = { viewModel.deletePeriod(period.id) }) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete period", tint = tokens.textMuted)
                    }
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
