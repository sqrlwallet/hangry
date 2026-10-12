package com.kevan.hangry.ui.healthrecords

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.kevan.hangry.R
import com.kevan.hangry.domain.ai.LabReportParser
import com.kevan.hangry.domain.ai.ScanIssue
import com.kevan.hangry.ui.coach.DashAlertCard
import com.kevan.hangry.ui.coach.DashSpinner
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens
import java.time.LocalDate

/** Photo → AI read → review. Nothing is saved until "Add N readings". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReportScanSheet(
    state: ReportScanState,
    aiEnabled: Boolean,
    onTakePhoto: () -> Unit,
    onPickPhotos: () -> Unit,
    onRead: () -> Unit,
    onRestart: () -> Unit,
    onUpdateRow: (Int, (ReportScanRow) -> ReportScanRow) -> Unit,
    onSetDate: (LocalDate) -> Unit,
    onSave: () -> Unit,
    onOpenAiSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val tokens = LocalHangryTokens.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = HangryTokens.Spacing.m).padding(bottom = HangryTokens.Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.DocumentScanner, contentDescription = null, tint = tokens.textSecondary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.body_records_scan_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            when {
                !aiEnabled -> DashAlertCard(
                    title = stringResource(R.string.body_records_scan_ai_off_title),
                    message = stringResource(R.string.body_records_scan_ai_off_body),
                    mascotSize = 48.dp,
                    action = { TextButton(onClick = onOpenAiSettings) { Text(stringResource(R.string.body_records_scan_open_ai_settings)) } }
                )
                state.rows == null -> CaptureStep(state, onTakePhoto, onPickPhotos, onRead)
                else -> ReviewStep(state, onUpdateRow, onSetDate, onSave, onRestart)
            }
        }
    }
}

@Composable
private fun CaptureStep(state: ReportScanState, onTakePhoto: () -> Unit, onPickPhotos: () -> Unit, onRead: () -> Unit) {
    val tokens = LocalHangryTokens.current
    Text(stringResource(R.string.body_records_scan_privacy), style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
    if (state.pages.isNotEmpty()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.pages.forEach { uri ->
                AsyncImage(
                    model = uri,
                    contentDescription = stringResource(R.string.body_records_scan_page_cd),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(72.dp).clip(RoundedCornerShape(12.dp))
                )
            }
        }
    }
    if (state.pages.size < HealthRecordsViewModel.MAX_SCAN_PAGES && !state.isReading) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onTakePhoto, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (state.pages.isEmpty()) stringResource(R.string.body_records_scan_take_photo) else stringResource(R.string.body_records_scan_add_page))
            }
            OutlinedButton(onClick = onPickPhotos, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.body_records_scan_gallery))
            }
        }
    }
    if (state.isReading) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DashSpinner(size = 40.dp, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.body_records_scan_reading), style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary)
        }
    }
    state.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = tokens.scoreColors.rebuild) }
    if (state.pages.isNotEmpty() && !state.isReading) {
        Button(onClick = onRead, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Text(pluralStringResource(R.plurals.body_records_scan_read, state.pages.size, state.pages.size))
        }
    }
}

@Composable
private fun ReviewStep(
    state: ReportScanState,
    onUpdateRow: (Int, (ReportScanRow) -> ReportScanRow) -> Unit,
    onSetDate: (LocalDate) -> Unit,
    onSave: () -> Unit,
    onRestart: () -> Unit
) {
    val tokens = LocalHangryTokens.current
    val rows = state.rows.orEmpty()
    var pickingDate by remember { mutableStateOf(false) }

    Text(stringResource(R.string.body_records_scan_review_hint), style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
    OutlinedButton(onClick = { pickingDate = true }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.body_records_scan_date, if (state.date == LocalDate.now()) stringResource(R.string.body_records_today) else state.date.format(DATE)))
    }
    if (rows.none { it.scanned.type != null }) {
        Text(stringResource(R.string.body_records_scan_none_tracked), style = MaterialTheme.typography.bodySmall, color = tokens.textMuted)
    }
    rows.forEachIndexed { index, row ->
        if (row.scanned.type != null) ScanRow(row) { transform -> onUpdateRow(index, transform) }
    }

    val untracked = rows.filter { it.scanned.type == null }
    if (untracked.isNotEmpty()) {
        HorizontalDivider(color = tokens.cardBorder.copy(alpha = 0.5f))
        Text(stringResource(R.string.body_records_scan_not_tracked), style = MaterialTheme.typography.labelLarge, color = tokens.textPrimary)
        Text(
            untracked.joinToString("\n") { "${it.scanned.printedName} · ${it.scanned.printedValue}" },
            style = MaterialTheme.typography.labelSmall,
            color = tokens.textMuted
        )
    }

    val count = state.toAdd.size
    Button(onClick = onSave, enabled = count > 0, modifier = Modifier.fillMaxWidth().height(48.dp)) {
        Text(pluralStringResource(R.plurals.body_records_scan_add, count, count))
    }
    TextButton(onClick = onRestart, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.body_records_scan_again)) }
    Text(stringResource(R.string.body_records_scan_check_note), style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)

    if (pickingDate) {
        HangryDatePickerDialog(initial = state.date, allowFuture = false, onDismiss = { pickingDate = false }, onPick = {
            onSetDate(it)
            pickingDate = false
        })
    }
}

@Composable
private fun ScanRow(row: ReportScanRow, onUpdate: ((ReportScanRow) -> ReportScanRow) -> Unit) {
    val tokens = LocalHangryTokens.current
    val type = row.scanned.type ?: return
    val value = row.value
    // Re-checked as the user edits, so a fixed misread clears its warning.
    val warning = when {
        row.scanned.issue == ScanIssue.UNKNOWN_UNIT && row.valueText.isBlank() ->
            stringResource(R.string.body_records_scan_issue_unit, row.scanned.unit.orEmpty(), type.canonicalUnit)
        !row.isComplete -> stringResource(R.string.body_records_scan_issue_missing)
        value != null && !LabReportParser.isPlausible(type, value, row.secondary) -> stringResource(R.string.body_records_scan_issue_implausible)
        else -> null
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = row.checked,
            onCheckedChange = { checked -> onUpdate { it.copy(checked = checked) } },
            enabled = row.isComplete
        )
        Column(Modifier.weight(1f)) {
            Text(type.label, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
            Text(
                row.scanned.printedName + " · " + row.scanned.printedValue +
                    (row.scanned.referenceRange?.let { " · " + stringResource(R.string.body_records_scan_ref, it) } ?: ""),
                style = MaterialTheme.typography.labelSmall,
                color = tokens.textMuted
            )
            warning?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = tokens.scoreColors.rebuild) }
        }
        Spacer(Modifier.width(8.dp))
        val edit = { primary: Boolean, text: String ->
            onUpdate { r ->
                val next = if (primary) r.copy(valueText = text) else r.copy(secondaryText = text)
                // Typing a value in selects the row; clearing it deselects.
                next.copy(checked = next.isComplete)
            }
        }
        if (type.hasSecondaryValue) {
            NumberField(null, row.valueText, { edit(true, it) }, Modifier.width(72.dp))
            Text("/", Modifier.padding(horizontal = 2.dp), color = tokens.textSecondary)
            NumberField(null, row.secondaryText, { edit(false, it) }, Modifier.width(72.dp))
        } else {
            NumberField(null, row.valueText, { edit(true, it) }, Modifier.width(88.dp))
        }
        Text(" ${type.canonicalUnit}", style = MaterialTheme.typography.labelSmall, color = tokens.textSecondary, modifier = Modifier.widthIn(min = 40.dp))
    }
}
