package com.kevan.hangry.ui.settings

import com.kevan.hangry.data.prefs.UnitPrefs
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Straighten
import com.kevan.hangry.domain.model.LengthUnit
import com.kevan.hangry.domain.model.WeightUnit
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kevan.hangry.R
import com.kevan.hangry.data.nudges.NudgeNotifications
import com.kevan.hangry.data.nudges.NudgePrefs
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens

/** Switches for the morning readiness brief, the bedtime reminder and cardio fitness alerts. */
@Composable
internal fun RemindersSection() {
    val context = LocalContext.current
    val tokens = LocalHangryTokens.current
    var morning by remember { mutableStateOf(NudgePrefs.morningEnabled(context)) }
    var bedtime by remember { mutableStateOf(NudgePrefs.bedtimeEnabled(context)) }
    var cardio by remember { mutableStateOf(NudgePrefs.cardioAlertsEnabled(context)) }
    var allowed by remember { mutableStateOf(NudgeNotifications.canNotify(context)) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }
    fun askIfNeeded() {
        if (!allowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    SettingsCollapsibleSection(
        title = stringResource(R.string.settings_reminders_title),
        summary = when {
            morning && bedtime -> stringResource(R.string.settings_reminders_summary_both)
            morning -> stringResource(R.string.settings_reminders_summary_morning)
            bedtime -> stringResource(R.string.settings_reminders_summary_bedtime)
            cardio -> stringResource(R.string.settings_reminders_cardio_title)
            else -> stringResource(R.string.settings_off)
        },
        icon = Icons.Default.NotificationsActive
    ) {
        ReminderSwitch(
            icon = Icons.Default.WbSunny,
            title = stringResource(R.string.settings_reminders_morning_title),
            subtitle = stringResource(R.string.settings_reminders_morning_subtitle),
            checked = morning,
            onChange = {
                morning = it
                NudgePrefs.setMorningEnabled(context, it)
                if (it) askIfNeeded()
            }
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = tokens.cardBorder)
        ReminderSwitch(
            icon = Icons.Default.Bedtime,
            title = stringResource(R.string.settings_reminders_bedtime_title),
            subtitle = stringResource(R.string.settings_reminders_bedtime_subtitle),
            checked = bedtime,
            onChange = {
                bedtime = it
                NudgePrefs.setBedtimeEnabled(context, it)
                if (it) askIfNeeded()
            }
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = tokens.cardBorder)
        ReminderSwitch(
            icon = Icons.Default.Favorite,
            title = stringResource(R.string.settings_reminders_cardio_title),
            subtitle = stringResource(R.string.settings_reminders_cardio_subtitle),
            checked = cardio,
            onChange = {
                cardio = it
                NudgePrefs.setCardioAlertsEnabled(context, it)
                if (it) askIfNeeded()
            }
        )
        if (!allowed && (morning || bedtime || cardio)) {
            Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))
            Text(
                stringResource(R.string.settings_reminders_notifications_off),
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textSecondary
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                TextButton(onClick = { permission.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text(stringResource(R.string.settings_reminders_allow_notifications)) }
            }
        }
    }
}

/** Display units. Values are stored in kg, cm and °C; these only change how they're shown and typed. */
@Composable
internal fun UnitsSection() {
    val context = LocalContext.current
    var fahrenheit by remember { mutableStateOf(UnitPrefs.fahrenheit(context)) }
    val weightUnit = UnitPrefs.weightUnit(context)
    val lengthUnit = UnitPrefs.lengthUnit(context)
    val weightLabels = mapOf(
        WeightUnit.KG to stringResource(R.string.settings_unit_kg),
        WeightUnit.LB to stringResource(R.string.settings_unit_lb),
        WeightUnit.ST_LB to stringResource(R.string.settings_unit_st_lb)
    )
    val lengthLabels = mapOf(
        LengthUnit.CM to stringResource(R.string.settings_unit_cm),
        LengthUnit.IN to stringResource(R.string.settings_unit_in)
    )
    SettingsCollapsibleSection(
        title = stringResource(R.string.settings_units_title),
        summary = stringResource(
            R.string.settings_units_summary,
            weightLabels.getValue(weightUnit),
            lengthLabels.getValue(lengthUnit),
            stringResource(if (fahrenheit) R.string.settings_unit_fahrenheit else R.string.settings_unit_celsius)
        ),
        icon = Icons.Default.Straighten
    ) {
        UnitChoiceRow(
            icon = Icons.Default.MonitorWeight,
            title = stringResource(R.string.settings_units_weight_title),
            options = weightLabels,
            selected = weightUnit,
            onSelect = { UnitPrefs.setWeightUnit(context, it) }
        )
        UnitChoiceRow(
            icon = Icons.Default.Straighten,
            title = stringResource(R.string.settings_units_length_title),
            options = lengthLabels,
            selected = lengthUnit,
            onSelect = { UnitPrefs.setLengthUnit(context, it) }
        )
        ReminderSwitch(
            icon = Icons.Default.Thermostat,
            title = stringResource(R.string.settings_units_temp_title),
            subtitle = stringResource(R.string.settings_units_temp_subtitle),
            checked = fahrenheit,
            onChange = {
                fahrenheit = it
                UnitPrefs.setFahrenheit(context, it)
            }
        )
    }
}

@Composable
private fun <T> UnitChoiceRow(icon: ImageVector, title: String, options: Map<T, String>, selected: T, onSelect: (T) -> Unit) {
    val tokens = LocalHangryTokens.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tokens.textSecondary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(HangryTokens.Spacing.m))
        Text(title, style = MaterialTheme.typography.bodyLarge, color = tokens.textPrimary, modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { (unit, label) ->
                FilterChip(selected = unit == selected, onClick = { onSelect(unit) }, label = { Text(label) })
            }
        }
    }
}

@Composable
private fun ReminderSwitch(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val tokens = LocalHangryTokens.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tokens.textSecondary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(HangryTokens.Spacing.m))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = tokens.textPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
