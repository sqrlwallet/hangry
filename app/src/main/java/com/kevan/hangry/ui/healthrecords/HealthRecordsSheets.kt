package com.kevan.hangry.ui.healthrecords

import com.kevan.hangry.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kevan.hangry.domain.calculation.HealthMarkerCalculator
import com.kevan.hangry.domain.model.BiologicalSex
import com.kevan.hangry.domain.model.GlucoseContext
import com.kevan.hangry.domain.model.GoalDirection
import com.kevan.hangry.domain.model.HealthProfileKind
import com.kevan.hangry.domain.model.HealthRecordsSnapshot
import com.kevan.hangry.domain.model.MarkerGoal
import com.kevan.hangry.domain.model.MarkerType
import com.kevan.hangry.domain.model.MarkerUnit
import com.kevan.hangry.ui.components.MetricBandTable
import com.kevan.hangry.ui.components.MetricInfoBlock
import com.kevan.hangry.ui.components.MetricRangeBar
import com.kevan.hangry.ui.components.MetricStatusChip
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

@Composable
internal fun MarkerPickerDialog(markers: List<MarkerType>, onDismiss: () -> Unit, onPick: (MarkerType) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.body_records_add_a_reading_title)) },
        text = {
            Column {
                markers.forEach { type ->
                    Text(
                        type.label,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth().clickable { onPick(type) }.padding(vertical = 12.dp)
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

/** [onSave] receives values already converted to the marker's canonical unit. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun AddReadingSheet(
    type: MarkerType,
    onDismiss: () -> Unit,
    onSave: (value: Double, secondary: Double?, measuredAt: Instant, context: GlucoseContext?, note: String?) -> Unit
) {
    val tokens = LocalHangryTokens.current
    var unit by remember { mutableStateOf(type.units.first()) }
    var valueText by remember { mutableStateOf("") }
    var secondaryText by remember { mutableStateOf("") }
    var context by remember { mutableStateOf(GlucoseContext.FASTING) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var note by remember { mutableStateOf("") }
    var pickingDate by remember { mutableStateOf(false) }

    val value = valueText.replace(',', '.').toDoubleOrNull()
    val secondary = secondaryText.replace(',', '.').toDoubleOrNull()
    val valid = value != null && value > 0 && (!type.hasSecondaryValue || (secondary != null && secondary > 0))

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = HangryTokens.Spacing.m).padding(bottom = HangryTokens.Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.body_records_add_marker, type.label.lowercase()), style = MaterialTheme.typography.titleLarge)
            if (type.units.size > 1) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    type.units.forEach { u ->
                        FilterChip(selected = unit == u, onClick = { unit = u }, label = { Text(u.label) })
                    }
                }
            }
            if (type.hasSecondaryValue) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(stringResource(R.string.body_records_systolic_top), valueText, { valueText = it }, Modifier.weight(1f))
                    NumberField(stringResource(R.string.body_records_diastolic_bottom), secondaryText, { secondaryText = it }, Modifier.weight(1f))
                }
            } else {
                NumberField(stringResource(R.string.body_records_marker_with_unit, type.label, unit.label), valueText, { valueText = it }, Modifier.fillMaxWidth())
            }
            if (type == MarkerType.BLOOD_GLUCOSE) {
                Text(stringResource(R.string.body_records_when_taken), style = MaterialTheme.typography.labelMedium, color = tokens.textSecondary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    GlucoseContext.entries.forEach { c ->
                        FilterChip(selected = context == c, onClick = { context = c }, label = { Text(c.label) })
                    }
                }
            }
            OutlinedButton(onClick = { pickingDate = true }, modifier = Modifier.fillMaxWidth()) {
                Text(if (date == LocalDate.now()) stringResource(R.string.body_records_today) else date.format(DATE))
            }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(stringResource(R.string.body_records_note_optional)) },
                placeholder = { Text(stringResource(R.string.body_records_note_placeholder)) },
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    val at = if (date == LocalDate.now()) Instant.now()
                    else date.atTime(LocalTime.NOON).atZone(ZoneId.systemDefault()).toInstant()
                    onSave(
                        value!! * unit.toCanonical,
                        secondary?.times(unit.toCanonical),
                        at,
                        context.takeIf { type == MarkerType.BLOOD_GLUCOSE },
                        note
                    )
                },
                enabled = valid,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) { Text(stringResource(R.string.body_records_save_reading)) }
            Text(
                stringResource(R.string.body_records_tracking_disclaimer_short),
                style = MaterialTheme.typography.labelSmall,
                color = tokens.textMuted
            )
        }
    }
    if (pickingDate) {
        HangryDatePickerDialog(initial = date, allowFuture = false, onDismiss = { pickingDate = false }, onPick = {
            date = it
            pickingDate = false
        })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MarkerDetailSheet(
    records: HealthRecordsSnapshot,
    type: MarkerType,
    onDismiss: () -> Unit,
    onAddReading: () -> Unit,
    onEditGoal: () -> Unit,
    onDeleteReading: (Long) -> Unit
) {
    val tokens = LocalHangryTokens.current
    val history = records.history(type)
    val metric = HealthMarkerCalculator.describe(type, history.firstOrNull(), records.sex)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = HangryTokens.Spacing.m).padding(bottom = HangryTokens.Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.m)
        ) {
            Column {
                Text(type.label, style = MaterialTheme.typography.titleLarge, color = tokens.textPrimary)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(metric.displayValue, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold,
                        color = if (metric.isAvailable) tokens.textPrimary else tokens.textMuted)
                    if (metric.isAvailable) {
                        Text(" ${type.canonicalUnit}", style = MaterialTheme.typography.titleMedium, color = tokens.textSecondary, modifier = Modifier.padding(bottom = 6.dp))
                    }
                }
                metric.status?.let { MetricStatusChip(it, metric.tone) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAddReading, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.body_records_add_reading))
                }
                OutlinedButton(onClick = onEditGoal, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Flag, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (records.goal(type) != null) stringResource(R.string.body_records_edit_goal) else stringResource(R.string.body_records_set_goal))
                }
            }
            if (records.goal(type) != null) GoalSummary(type = type, records = records)
            if (metric.bands.isNotEmpty()) {
                if (metric.isAvailable) MetricRangeBar(metric)
                MetricBandTable(metric)
                if (type == MarkerType.BLOOD_GLUCOSE) {
                    Text(
                        stringResource(R.string.body_records_glucose_ranges_note, (history.firstOrNull()?.glucoseContext ?: GlucoseContext.RANDOM).label.lowercase()),
                        style = MaterialTheme.typography.labelSmall, color = tokens.textMuted
                    )
                }
            }
            if (history.isNotEmpty()) {
                Text(stringResource(R.string.body_records_history), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = tokens.textPrimary)
                history.take(30).forEach { reading ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${HealthMarkerCalculator.format(type, reading.value, reading.secondaryValue)} ${type.canonicalUnit}" +
                                    (reading.glucoseContext?.let { " · ${it.label.lowercase()}" } ?: ""),
                                style = MaterialTheme.typography.bodyMedium, color = tokens.textPrimary
                            )
                            Text(
                                "${reading.date.format(DATE)} · ${reading.source.label}" + (reading.note?.let { " · $it" } ?: ""),
                                style = MaterialTheme.typography.labelSmall, color = tokens.textMuted
                            )
                        }
                        IconButton(onClick = { onDeleteReading(reading.id) }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = stringResource(R.string.body_records_delete_reading), tint = tokens.textMuted)
                        }
                    }
                }
            }
            MetricInfoBlock(stringResource(R.string.body_records_what_it_is), metric.info.whatItIs)
            MetricInfoBlock(stringResource(R.string.body_records_your_reading), metric.info.howCalculated)
            MetricInfoBlock(stringResource(R.string.body_records_why_it_matters), metric.info.whyItMatters)
            MetricInfoBlock(stringResource(R.string.body_records_ranges_from), metric.info.source)
            Text(
                stringResource(R.string.body_records_reference_disclaimer),
                style = MaterialTheme.typography.labelSmall, color = tokens.textMuted
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun GoalSheet(
    type: MarkerType,
    existing: MarkerGoal?,
    sex: BiologicalSex?,
    onDismiss: () -> Unit,
    onSave: (target: Double, secondary: Double?, date: LocalDate?) -> Unit,
    onClear: () -> Unit
) {
    val tokens = LocalHangryTokens.current
    val (suggested, suggestedSecondary) = HealthMarkerCalculator.suggestedGoal(type, sex)
    var unit by remember { mutableStateOf(type.units.first()) }
    fun display(canonical: Double?, u: MarkerUnit) = canonical?.let { String.format(Locale.US, "%.${if (u.toCanonical == 1.0) type.decimals else 1}f", it / u.toCanonical) } ?: ""
    var targetText by remember { mutableStateOf(display(existing?.targetValue ?: suggested, type.units.first())) }
    var secondaryText by remember { mutableStateOf(display(existing?.targetSecondary ?: suggestedSecondary, type.units.first())) }
    var date by remember { mutableStateOf(existing?.targetDate) }
    var pickingDate by remember { mutableStateOf(false) }

    val target = targetText.replace(',', '.').toDoubleOrNull()
    val secondary = secondaryText.replace(',', '.').toDoubleOrNull()
    val lower = type.defaultGoalDirection == GoalDirection.LOWER

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = HangryTokens.Spacing.m).padding(bottom = HangryTokens.Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(if (lower) stringResource(R.string.body_records_goal_lower_title, type.label.lowercase()) else stringResource(R.string.body_records_goal_raise_title, type.label.lowercase()), style = MaterialTheme.typography.titleLarge)
            Text(
                if (lower) stringResource(R.string.body_records_goal_aim_below) else stringResource(R.string.body_records_goal_aim_above),
                style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary
            )
            if (type.units.size > 1) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    type.units.forEach { u ->
                        FilterChip(selected = unit == u, onClick = {
                            // Keep the same target when switching units.
                            target?.let { targetText = display(it * unit.toCanonical, u) }
                            unit = u
                        }, label = { Text(u.label) })
                    }
                }
            }
            if (type.hasSecondaryValue) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(stringResource(R.string.body_records_systolic_target), targetText, { targetText = it }, Modifier.weight(1f))
                    NumberField(stringResource(R.string.body_records_diastolic_target), secondaryText, { secondaryText = it }, Modifier.weight(1f))
                }
            } else {
                NumberField(stringResource(R.string.body_records_target_with_unit, unit.label), targetText, { targetText = it }, Modifier.fillMaxWidth())
            }
            OutlinedButton(onClick = { pickingDate = true }, modifier = Modifier.fillMaxWidth()) {
                Text(date?.let { stringResource(R.string.body_records_by_date, it.format(DATE)) } ?: stringResource(R.string.body_records_add_target_date))
            }
            Button(
                onClick = { onSave(target!! * unit.toCanonical, secondary?.times(unit.toCanonical), date) },
                enabled = target != null && target > 0 && (!type.hasSecondaryValue || secondary != null),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) { Text(stringResource(R.string.body_records_save_goal)) }
            if (existing != null) {
                TextButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.body_records_remove_goal)) }
            }
        }
    }
    if (pickingDate) {
        HangryDatePickerDialog(initial = date ?: LocalDate.now().plusMonths(3), allowFuture = true, onDismiss = { pickingDate = false }, onPick = {
            date = it
            pickingDate = false
        })
    }
}

@Composable
internal fun AddProfileItemDialog(kind: HealthProfileKind, onDismiss: () -> Unit, onSave: (String, String?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val isAllergy = kind == HealthProfileKind.ALLERGY
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isAllergy) stringResource(R.string.body_records_add_allergy) else stringResource(R.string.body_records_add_condition)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, singleLine = true,
                    label = { Text(if (isAllergy) stringResource(R.string.body_records_allergy) else stringResource(R.string.body_records_condition)) },
                    placeholder = { Text(if (isAllergy) stringResource(R.string.body_records_allergy_placeholder) else stringResource(R.string.body_records_condition_placeholder)) }
                )
                OutlinedTextField(
                    value = note, onValueChange = { note = it }, singleLine = true,
                    label = { Text(if (isAllergy) stringResource(R.string.body_records_reaction_optional) else stringResource(R.string.body_records_note_optional)) }
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, note) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.body_records_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
internal fun LogPeriodDialog(onDismiss: () -> Unit, onSave: (LocalDate, LocalDate?) -> Unit) {
    var start by remember { mutableStateOf(LocalDate.now()) }
    var end by remember { mutableStateOf<LocalDate?>(null) }
    var picking by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.body_records_log_period)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { picking = "start" }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.body_records_period_started, start.format(DATE))) }
                OutlinedButton(onClick = { picking = "end" }, modifier = Modifier.fillMaxWidth()) {
                    Text(end?.let { stringResource(R.string.body_records_period_ended, it.format(DATE)) } ?: stringResource(R.string.body_records_period_still_going))
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(start, end) }) { Text(stringResource(R.string.body_records_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
    picking?.let { which ->
        HangryDatePickerDialog(
            initial = if (which == "start") start else end ?: start,
            allowFuture = false,
            onDismiss = { picking = null },
            onPick = {
                if (which == "start") start = it else end = it
                picking = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HangryDatePickerDialog(initial: LocalDate, allowFuture: Boolean, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val todayUtcMillis = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = allowFuture || utcTimeMillis <= todayUtcMillis
        }
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
            }) { Text(stringResource(R.string.body_records_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    ) { DatePicker(state = state) }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onChange(input.filter { it.isDigit() || it == '.' || it == ',' }) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier
    )
}
