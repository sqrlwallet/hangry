package com.kevan.hangry.ui.supplements

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.kevan.hangry.R
import com.kevan.hangry.domain.model.Supplement
import com.kevan.hangry.domain.model.SupplementIngredient
import com.kevan.hangry.domain.model.SupplementsSnapshot
import com.kevan.hangry.ui.coach.DashExpression
import com.kevan.hangry.ui.coach.DashMood
import com.kevan.hangry.ui.coach.DashSpinner
import com.kevan.hangry.ui.components.HangryCard
import com.kevan.hangry.ui.components.HangryInfoIconButton
import com.kevan.hangry.ui.components.HangryInfoSection
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens
import com.kevan.hangry.util.rememberMultiPhotoCaptureLauncher
import java.io.File
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
private fun supplementsInfoSections(): List<HangryInfoSection> = listOf(
    HangryInfoSection(stringResource(R.string.nutrition_supplements_info_snap_title), stringResource(R.string.nutrition_supplements_info_snap_body)),
    HangryInfoSection(stringResource(R.string.nutrition_supplements_info_tracking_title), stringResource(R.string.nutrition_supplements_info_tracking_body)),
    HangryInfoSection(stringResource(R.string.nutrition_supplements_info_dash_title), stringResource(R.string.nutrition_supplements_info_dash_body)),
    HangryInfoSection(stringResource(R.string.nutrition_supplements_info_medical_title), stringResource(R.string.nutrition_supplements_info_medical_body))
)

internal val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupplementsScreen(
    viewModel: SupplementsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalHangryTokens.current
    val snapshot by viewModel.snapshot.collectAsState()
    val editor by viewModel.editor.collectAsState()
    val aiEnabled by viewModel.aiEnabled.collectAsState()

    val photoLauncher = rememberMultiPhotoCaptureLauncher(maxItems = SupplementsViewModel.MAX_PHOTOS) { uris ->
        viewModel.onPhotoCaptured(uris)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nutrition_supplements_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nutrition_cd_back)) }
                },
                actions = { HangryInfoIconButton(title = stringResource(R.string.nutrition_supplements_about), sections = supplementsInfoSections()) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            if (snapshot.supplements.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { photoLauncher.takePhoto() },
                    icon = { Icon(Icons.Default.CameraAlt, contentDescription = null) },
                    text = { Text(stringResource(R.string.nutrition_supplements_add)) }
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = HangryTokens.Spacing.m, vertical = HangryTokens.Spacing.s)
                // Room so the floating add button never covers the last card.
                .padding(bottom = 72.dp),
            verticalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.m)
        ) {
            if (snapshot.supplements.isEmpty()) {
                EmptyState(
                    onSnap = { photoLauncher.takePhoto() },
                    onGallery = { photoLauncher.pickFromGallery() },
                    onManual = viewModel::startManual
                )
            } else {
                if (snapshot.todayDoses.isNotEmpty()) TodayChecklist(snapshot = snapshot, onToggle = viewModel::setTaken)
                Text(stringResource(R.string.nutrition_supplements_yours), style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
                snapshot.supplements.forEach { supplement ->
                    SupplementCard(
                        supplement = supplement,
                        adherence = snapshot.weekAdherence[supplement.id],
                        onClick = { viewModel.edit(supplement) }
                    )
                }
                TextButton(onClick = viewModel::startManual) { Text(stringResource(R.string.nutrition_supplements_add_without_photo)) }
                Spacer(Modifier.height(72.dp)) // clear the FAB
            }
        }
    }

    editor?.let { state ->
        SupplementEditorSheet(
            state = state,
            aiEnabled = aiEnabled,
            onChange = viewModel::updateEditor,
            onSetTracked = viewModel::setTracked,
            onAddPhoto = { photoLauncher.takePhoto() },
            onPickPhotos = { photoLauncher.pickFromGallery() },
            onSave = viewModel::save,
            onDelete = { viewModel.delete(state.id) },
            onDismiss = viewModel::dismissEditor
        )
    }
}

@Composable
private fun EmptyState(onSnap: () -> Unit, onGallery: () -> Unit, onManual: () -> Unit) {
    val tokens = LocalHangryTokens.current
    HangryCard(modifier = Modifier.fillMaxWidth()) {
        DashExpression(mood = DashMood.SUPPLEMENTS, size = 96.dp, contentDescription = null)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.nutrition_supplements_empty_title), style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
        Text(
            stringResource(R.string.nutrition_supplements_empty_body),
            style = MaterialTheme.typography.bodySmall,
            color = tokens.textSecondary
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = onSnap, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.nutrition_supplements_snap))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onGallery) { Text(stringResource(R.string.nutrition_supplements_choose_photos)) }
            TextButton(onClick = onManual) { Text(stringResource(R.string.nutrition_supplements_enter_manually)) }
        }
    }
}

@Composable
internal fun TodayChecklist(
    snapshot: SupplementsSnapshot,
    onToggle: (Long, LocalTime, Boolean) -> Unit,
    maxRows: Int = Int.MAX_VALUE
) {
    val tokens = LocalHangryTokens.current
    HangryCard(modifier = Modifier.fillMaxWidth(), contentPadding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.nutrition_supplements_today), style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary, modifier = Modifier.weight(1f))
            Text(
                if (snapshot.todayDoses.isEmpty()) stringResource(R.string.nutrition_supplements_no_doses) else stringResource(R.string.nutrition_supplements_taken_of, snapshot.takenToday, snapshot.todayDoses.size),
                style = MaterialTheme.typography.labelMedium,
                color = if (snapshot.todayDoses.isNotEmpty() && snapshot.takenToday == snapshot.todayDoses.size) tokens.scoreColors.primed else tokens.textSecondary
            )
        }
        if (snapshot.todayDoses.isNotEmpty() && snapshot.takenToday == snapshot.todayDoses.size) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                DashExpression(mood = DashMood.HAPPY, size = 52.dp, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.nutrition_supplements_all_taken_today), style = MaterialTheme.typography.bodyMedium, color = tokens.textPrimary)
            }
        }
        snapshot.todayDoses.take(maxRows).forEach { dose ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onToggle(dose.supplement.id, dose.time, !dose.taken) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = dose.taken, onCheckedChange = { onToggle(dose.supplement.id, dose.time, it) })
                Column(Modifier.weight(1f)) {
                    Text(dose.supplement.name, style = MaterialTheme.typography.bodyMedium, color = tokens.textPrimary)
                    Text(
                        stringResource(R.string.nutrition_supplements_dose, SupplementsViewModel.formatAmount(dose.supplement.doseAmount), dose.supplement.doseUnit),
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.textMuted
                    )
                }
                Text(dose.time.format(TIME_FORMAT), style = MaterialTheme.typography.labelMedium, color = tokens.textSecondary)
            }
        }
        if (snapshot.todayDoses.size > maxRows) {
            Text(stringResource(R.string.nutrition_supplements_more, snapshot.todayDoses.size - maxRows), style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
        }
    }
}

@Composable
private fun SupplementCard(supplement: Supplement, adherence: Pair<Int, Int>?, onClick: () -> Unit) {
    val tokens = LocalHangryTokens.current
    HangryCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), contentPadding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            supplement.photoPath?.let { path ->
                AsyncImage(
                    model = File(path),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp))
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    if (!supplement.active) stringResource(R.string.nutrition_supplements_paused_name, supplement.name) else supplement.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (supplement.active) tokens.textPrimary else tokens.textMuted
                )
                val schedule = supplement.times.joinToString(", ") { it.format(TIME_FORMAT) }
                    .ifEmpty { stringResource(R.string.nutrition_supplements_taken_as_usual) }
                Text(
                    stringResource(R.string.nutrition_supplements_dose_schedule, SupplementsViewModel.formatAmount(supplement.doseAmount), supplement.doseUnit, schedule),
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary
                )
                if (supplement.ingredients.isNotEmpty()) {
                    Text(
                        supplement.ingredients.take(3).joinToString(" · ") { ingredientLabel(it) },
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.textMuted,
                        maxLines = 1
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                if (supplement.remindersEnabled && supplement.active) {
                    Icon(Icons.Default.NotificationsActive, contentDescription = stringResource(R.string.nutrition_supplements_reminders_on), tint = tokens.textSecondary, modifier = Modifier.size(16.dp))
                } else if (supplement.tracked && supplement.active) {
                    Text(stringResource(R.string.nutrition_supplements_tracking), style = MaterialTheme.typography.labelSmall, color = tokens.textSecondary)
                }
                adherence?.takeIf { it.second > 0 }?.let { (taken, scheduled) ->
                    Text(stringResource(R.string.nutrition_supplements_this_week, taken, scheduled), style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
                }
            }
        }
    }
}

internal fun ingredientLabel(i: SupplementIngredient): String {
    val amount = i.amount?.let { a -> SupplementsViewModel.formatAmount(a) + (i.unit?.let { " $it" } ?: "") }
    return listOfNotNull(i.name, amount).joinToString(" ")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SupplementEditorSheet(
    state: SupplementEditorState,
    aiEnabled: Boolean,
    onChange: ((SupplementEditorState) -> SupplementEditorState) -> Unit,
    onSetTracked: (Boolean) -> Unit,
    onAddPhoto: () -> Unit,
    onPickPhotos: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val tokens = LocalHangryTokens.current
    val context = LocalContext.current
    var pickingTimeIndex by remember { mutableStateOf<Int?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    // Reminders need notification permission on Android 13+; saving goes ahead either way.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onSave() }
    val save = {
        val needsPermission = state.tracked && state.remindersEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else onSave()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = HangryTokens.Spacing.m)
                .padding(bottom = HangryTokens.Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(if (state.isNew) stringResource(R.string.nutrition_supplements_add) else stringResource(R.string.nutrition_supplements_edit), style = MaterialTheme.typography.titleLarge)

            // Photos
            if (state.photos.isNotEmpty() || state.existingPhotoPath != null || state.isNew) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val models: List<Any> = state.photos.ifEmpty { listOfNotNull(state.existingPhotoPath?.let(::File)) }
                    models.forEach { model ->
                        AsyncImage(
                            model = model,
                            contentDescription = stringResource(R.string.nutrition_supplements_photo_cd),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(84.dp).clip(RoundedCornerShape(12.dp))
                        )
                    }
                    if (state.photos.size < SupplementsViewModel.MAX_PHOTOS && state.isNew) {
                        OutlinedButton(onClick = onAddPhoto, modifier = Modifier.size(84.dp), shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(4.dp)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.CameraAlt, contentDescription = null)
                                Text(if (state.photos.isEmpty()) stringResource(R.string.nutrition_supplements_photo) else stringResource(R.string.nutrition_supplements_label), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        OutlinedButton(onClick = onPickPhotos, modifier = Modifier.size(84.dp), shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(4.dp)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                                Text(stringResource(R.string.nutrition_action_gallery), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            if (state.isAnalyzing) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DashSpinner(size = 40.dp, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.nutrition_supplements_reading_label), style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary)
                }
            } else if (state.isNew && state.photos.size == 1 && aiEnabled && state.analysisError == null) {
                Text(stringResource(R.string.nutrition_supplements_label_tip),
                    style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
            }
            state.analysisError?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = tokens.scoreColors.rebuild)
            }

            if (state.cautions.isNotEmpty()) {
                Surface(color = tokens.brandAccentContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.WarningAmber, contentDescription = null, tint = tokens.brandAccent, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.nutrition_supplements_worth_checking), style = MaterialTheme.typography.labelLarge, color = tokens.textPrimary)
                        }
                        state.cautions.forEach { Text(stringResource(R.string.nutrition_bullet_item, it), style = MaterialTheme.typography.bodySmall, color = tokens.textPrimary) }
                        Text(stringResource(R.string.nutrition_supplements_caution_note),
                            style = MaterialTheme.typography.labelSmall, color = tokens.textSecondary)
                    }
                }
            }

            OutlinedTextField(
                value = state.name, onValueChange = { v -> onChange { it.copy(name = v) } },
                label = { Text(stringResource(R.string.nutrition_field_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.brand, onValueChange = { v -> onChange { it.copy(brand = v) } },
                label = { Text(stringResource(R.string.nutrition_supplements_brand)) }, singleLine = true, modifier = Modifier.fillMaxWidth()
            )

            Text(stringResource(R.string.nutrition_supplements_how_much), style = MaterialTheme.typography.labelLarge, color = tokens.textPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.doseAmount,
                    onValueChange = { v -> onChange { it.copy(doseAmount = v.filter { c -> c.isDigit() || c == '.' || c == ',' }) } },
                    label = { Text(stringResource(R.string.nutrition_supplements_amount)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(0.4f)
                )
                OutlinedTextField(
                    value = state.doseUnit, onValueChange = { v -> onChange { it.copy(doseUnit = v) } },
                    label = { Text(stringResource(R.string.nutrition_supplements_unit)) }, singleLine = true, placeholder = { Text(stringResource(R.string.nutrition_supplements_unit_placeholder)) },
                    modifier = Modifier.weight(0.6f)
                )
            }

            Text(
                if (state.tracked) stringResource(R.string.nutrition_supplements_when_tracked) else stringResource(R.string.nutrition_supplements_when_untracked),
                style = MaterialTheme.typography.labelLarge, color = tokens.textPrimary
            )
            state.suggestedTiming?.let {
                Text(stringResource(R.string.nutrition_supplements_suggested, it), style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                state.times.forEachIndexed { index, time ->
                    InputChip(
                        selected = true,
                        onClick = { pickingTimeIndex = index },
                        label = { Text(time.format(TIME_FORMAT)) },
                        trailingIcon = {
                            // A 32dp touch area around the 16dp icon, so it's easy to hit.
                            IconButton(
                                onClick = { onChange { s -> s.copy(times = s.times.filterIndexed { i, _ -> i != index }) } },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.nutrition_supplements_remove_time), modifier = Modifier.size(16.dp))
                            }
                        }
                    )
                }
                AssistChip(onClick = { pickingTimeIndex = -1 }, label = { Text(stringResource(R.string.nutrition_supplements_add_time)) }, leadingIcon = { Icon(Icons.Default.Add, null, Modifier.size(16.dp)) })
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.nutrition_supplements_help_track), style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                    Text(
                        if (state.tracked) stringResource(R.string.nutrition_supplements_help_track_on)
                        else stringResource(R.string.nutrition_supplements_help_track_off),
                        style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary
                    )
                }
                Switch(checked = state.tracked, onCheckedChange = onSetTracked)
            }
            if (state.tracked) {
                if (state.times.isEmpty()) {
                    Text(stringResource(R.string.nutrition_supplements_add_time_to_track), style = MaterialTheme.typography.bodySmall, color = tokens.scoreColors.rebuild)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.nutrition_supplements_remind_me), style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                        Text(stringResource(R.string.nutrition_supplements_remind_me_body), style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                    }
                    Switch(checked = state.remindersEnabled, onCheckedChange = { v -> onChange { it.copy(remindersEnabled = v) } }, enabled = state.times.isNotEmpty())
                }
                ExactAlarmHint(visible = state.remindersEnabled && state.times.isNotEmpty())
            }

            if (state.ingredients.isNotEmpty()) {
                Text(stringResource(R.string.nutrition_supplements_ingredients), style = MaterialTheme.typography.labelLarge, color = tokens.textPrimary)
                state.ingredients.forEachIndexed { index, ingredient ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(ingredientLabel(ingredient), style = MaterialTheme.typography.bodyMedium, color = tokens.textPrimary, modifier = Modifier.weight(1f))
                        ingredient.dailyValuePercent?.let {
                            Text(stringResource(R.string.nutrition_supplements_dv, SupplementsViewModel.formatAmount(it)), style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
                        }
                        IconButton(onClick = { onChange { s -> s.copy(ingredients = s.ingredients.filterIndexed { i, _ -> i != index }) } }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.nutrition_supplements_remove_ingredient), tint = tokens.textMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            OutlinedTextField(
                value = state.notes, onValueChange = { v -> onChange { it.copy(notes = v) } },
                label = { Text(stringResource(R.string.nutrition_supplements_notes)) }, modifier = Modifier.fillMaxWidth()
            )

            if (!state.isNew) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.nutrition_supplements_currently_taking), style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary, modifier = Modifier.weight(1f))
                    Switch(checked = state.active, onCheckedChange = { v -> onChange { it.copy(active = v) } })
                }
            }

            Button(onClick = save, enabled = state.canSave, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text(if (state.isNew) stringResource(R.string.nutrition_supplements_save_new) else stringResource(R.string.nutrition_supplements_save_changes))
            }
            if (!state.isNew) {
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.nutrition_delete), color = MaterialTheme.colorScheme.error)
                }
            }
            Text(stringResource(R.string.nutrition_fasting_disclaimer), style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
        }
    }

    pickingTimeIndex?.let { index ->
        val initial = state.times.getOrNull(index) ?: LocalTime.of(8, 0)
        TimePickerDialog(
            initial = initial,
            onDismiss = { pickingTimeIndex = null },
            onPick = { picked ->
                onChange { s ->
                    val times = if (index >= 0) s.times.toMutableList().also { it[index] = picked } else s.times + picked
                    s.copy(times = times.distinct().sorted())
                }
                pickingTimeIndex = null
            }
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.nutrition_supplements_delete_title, state.name)) },
            text = { Text(stringResource(R.string.nutrition_supplements_delete_body)) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text(stringResource(R.string.nutrition_delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

/** On Android 12+ exact alarms can be off; reminders then arrive within a few minutes. */
@Composable
private fun ExactAlarmHint(visible: Boolean) {
    if (!visible || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val context = LocalContext.current
    val canExact = remember { context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms() }
    if (canExact) return
    TextButton(onClick = {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }) { Text(stringResource(R.string.nutrition_supplements_exact_alarm_hint)) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(initial: LocalTime, onDismiss: () -> Unit, onPick: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.nutrition_supplements_dose_time)) },
        text = { TimePicker(state = state) },
        confirmButton = { TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)) }) { Text(stringResource(R.string.nutrition_fasting_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
