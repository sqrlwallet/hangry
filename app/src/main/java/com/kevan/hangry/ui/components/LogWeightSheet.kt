package com.kevan.hangry.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.kevan.hangry.R
import com.kevan.hangry.domain.model.BodyUnits
import com.kevan.hangry.ui.healthrecords.HangryDatePickerDialog
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * One weigh-in, in the user's unit: a weight and (optionally) the day it was taken. Calls
 * [onSave] with kilograms and when it was taken - now for today, midday for an earlier day.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogWeightSheet(
    lastWeightKg: Double?,
    onDismiss: () -> Unit,
    onSave: (kg: Double, takenAt: Instant) -> Unit
) {
    val tokens = LocalHangryTokens.current
    val haptic = rememberHangryHaptics()
    val formatter = rememberBodyUnitFormatter()
    var kgText by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(LocalDate.now()) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    var confirmingDiscard by rememberSaveable { mutableStateOf(false) }

    val kg = BodyUnits.plausibleWeight(BodyUnits.parseNumber(kgText))
    val showError = kgText.isNotBlank() && kg == null
    val sheetState = rememberDiscardGuardSheetState(hasUnsavedChanges = kgText.isNotBlank(), onAttemptDismiss = { confirmingDiscard = true })

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = HangryTokens.Spacing.m)
                .padding(bottom = HangryTokens.Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.body_log_weight_title), style = MaterialTheme.typography.titleLarge, color = tokens.textPrimary)
            WeightInputField(
                kgText = kgText,
                onKgTextChange = { kgText = it },
                label = stringResource(R.string.body_measure_weight),
                isError = showError,
                supportingText = when {
                    showError -> stringResource(
                        R.string.body_log_weight_invalid,
                        formatter.weight(BodyUnits.WEIGHT_KG_RANGE.start),
                        formatter.weight(BodyUnits.WEIGHT_KG_RANGE.endInclusive)
                    )
                    lastWeightKg != null -> stringResource(R.string.body_log_weight_last, formatter.weight(lastWeightKg))
                    else -> null
                },
                imeAction = ImeAction.Done,
                placeholderKg = lastWeightKg,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = {
                        haptic.tick()
                        pickingDate = true
                    },
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (date == LocalDate.now()) stringResource(R.string.body_log_weight_today) else date.format(DATE))
                }
                Button(
                    onClick = {
                        haptic.confirm()
                        val takenAt = if (date == LocalDate.now()) Instant.now()
                        else date.atTime(LocalTime.NOON).atZone(ZoneId.systemDefault()).toInstant()
                        onSave(kg!!, takenAt)
                    },
                    enabled = kg != null,
                    modifier = Modifier.weight(1f).height(48.dp)
                ) { Text(stringResource(R.string.body_log_weight_save)) }
            }
        }
    }
    if (pickingDate) {
        HangryDatePickerDialog(initial = date, allowFuture = false, onDismiss = { pickingDate = false }, onPick = {
            date = it
            pickingDate = false
        })
    }
    if (confirmingDiscard) {
        DiscardChangesDialog(
            onDiscard = {
                confirmingDiscard = false
                onDismiss()
            },
            onKeepEditing = { confirmingDiscard = false }
        )
    }
}

private val DATE = DateTimeFormatter.ofPattern("EEE d MMM")
