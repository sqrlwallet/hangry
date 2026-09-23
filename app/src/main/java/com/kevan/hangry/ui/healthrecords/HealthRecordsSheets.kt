package com.kevan.hangry.ui.healthrecords

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
internal fun MarkerPickerDialog(onDismiss: () -> Unit, onPick: (MarkerType) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a reading") },
        text = {
            Column {
                MarkerType.entries.forEach { type ->
                    Text(
                        type.label,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth().clickable { onPick(type) }.padding(vertical = 12.dp)
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
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
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = HangryTokens.Spacing.m).padding(bottom = HangryTokens.Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Add ${type.label.lowercase()}", style = MaterialTheme.typography.titleLarge)
            if (type.units.size > 1) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    type.units.forEach { u ->
                        FilterChip(selected = unit == u, onClick = { unit = u }, label = { Text(u.label) })
                    }
                }
            }
            if (type.hasSecondaryValue) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("Systolic (top)", valueText, { valueText = it }, Modifier.weight(1f))
                    NumberField("Diastolic (bottom)", secondaryText, { secondaryText = it }, Modifier.weight(1f))
                }
            } else {
                NumberField("${type.label} (${unit.label})", valueText, { valueText = it }, Modifier.fillMaxWidth())
            }
            if (type == MarkerType.BLOOD_GLUCOSE) {
                Text("When was it taken?", style = MaterialTheme.typography.labelMedium, color = tokens.textSecondary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    GlucoseContext.entries.forEach { c ->
                        FilterChip(selected = context == c, onClick = { context = c }, label = { Text(c.label) })
                    }
                }
            }
            OutlinedButton(onClick = { pickingDate = true }, modifier = Modifier.fillMaxWidth()) {
                Text(if (date == LocalDate.now()) "Today" else date.format(DATE))
            }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note (optional)") },
                placeholder = { Text("e.g. lab report, after coffee") },
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
            ) { Text("Save reading") }
            Text(
                "For your own tracking - not medical advice.",
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
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
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
                    Text("Add reading")
                }
                OutlinedButton(onClick = onEditGoal, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Flag, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (records.goal(type) != null) "Edit goal" else "Set a goal")
                }
            }
            if (records.goal(type) != null) GoalSummary(type = type, records = records)
            if (metric.bands.isNotEmpty()) {
                if (metric.isAvailable) MetricRangeBar(metric)
                MetricBandTable(metric)
                if (type == MarkerType.BLOOD_GLUCOSE) {
                    Text(
                        "Ranges shown are for ${(history.firstOrNull()?.glucoseContext ?: GlucoseContext.RANDOM).label.lowercase()} readings; fasting limits are tighter.",
                        style = MaterialTheme.typography.labelSmall, color = tokens.textMuted
                    )
                }
            }
            if (history.isNotEmpty()) {
                Text("History", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = tokens.textPrimary)
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
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete reading", tint = tokens.textMuted)
                        }
                    }
                }
            }
            MetricInfoBlock("What it is", metric.info.whatItIs)
            MetricInfoBlock("Your reading", metric.info.howCalculated)
            MetricInfoBlock("Why it matters", metric.info.whyItMatters)
            MetricInfoBlock("Ranges from", metric.info.source)
            Text(
                "General adult reference ranges for tracking only - not a diagnosis. Your doctor may set different targets for you.",
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
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = HangryTokens.Spacing.m).padding(bottom = HangryTokens.Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("${if (lower) "Lower" else "Raise"} your ${type.label.lowercase()}", style = MaterialTheme.typography.titleLarge)
            Text(
                "Aim to get ${if (lower) "at or below" else "at or above"} this. We've suggested the edge of the healthy range - if your doctor gave you a target, use theirs.",
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
                    NumberField("Systolic target", targetText, { targetText = it }, Modifier.weight(1f))
                    NumberField("Diastolic target", secondaryText, { secondaryText = it }, Modifier.weight(1f))
                }
            } else {
                NumberField("Target (${unit.label})", targetText, { targetText = it }, Modifier.fillMaxWidth())
            }
            OutlinedButton(onClick = { pickingDate = true }, modifier = Modifier.fillMaxWidth()) {
                Text(date?.let { "By ${it.format(DATE)}" } ?: "Add a target date (optional)")
            }
            Button(
                onClick = { onSave(target!! * unit.toCanonical, secondary?.times(unit.toCanonical), date) },
                enabled = target != null && target > 0 && (!type.hasSecondaryValue || secondary != null),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) { Text("Save goal") }
            if (existing != null) {
                TextButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) { Text("Remove goal") }
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
        title = { Text(if (isAllergy) "Add an allergy" else "Add a condition") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, singleLine = true,
                    label = { Text(if (isAllergy) "Allergy" else "Condition") },
                    placeholder = { Text(if (isAllergy) "e.g. Peanuts, penicillin" else "e.g. Type 2 diabetes, asthma") }
                )
                OutlinedTextField(
                    value = note, onValueChange = { note = it }, singleLine = true,
                    label = { Text(if (isAllergy) "Reaction (optional)" else "Note (optional)") }
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, note) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
internal fun LogPeriodDialog(onDismiss: () -> Unit, onSave: (LocalDate, LocalDate?) -> Unit) {
    var start by remember { mutableStateOf(LocalDate.now()) }
    var end by remember { mutableStateOf<LocalDate?>(null) }
    var picking by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log a period") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { picking = "start" }, modifier = Modifier.fillMaxWidth()) { Text("Started ${start.format(DATE)}") }
                OutlinedButton(onClick = { picking = "end" }, modifier = Modifier.fillMaxWidth()) {
                    Text(end?.let { "Ended ${it.format(DATE)}" } ?: "Still going / add end date")
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(start, end) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
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
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
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
