package com.kevan.hangry.ui.components

import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kevan.hangry.R
import com.kevan.hangry.data.prefs.UnitPrefs
import com.kevan.hangry.domain.model.BodyUnits
import com.kevan.hangry.domain.model.LengthUnit
import com.kevan.hangry.domain.model.WeightUnit
import java.util.Locale
import kotlin.math.abs

/**
 * The one place weights and lengths become text in the user's units (Settings → Units).
 * Everything is stored in kg and cm; pass those in.
 */
class BodyUnitFormatter(private val res: Resources, val weightUnit: WeightUnit, val lengthUnit: LengthUnit) {

    /** e.g. "72.4 kg", "159.6 lb", "11 st 5.6 lb". */
    fun weight(kg: Double): String = when (weightUnit) {
        WeightUnit.KG -> res.getString(R.string.body_unit_weight_kg, num(kg))
        WeightUnit.LB -> res.getString(R.string.body_unit_weight_lb, num(BodyUnits.kgToLb(kg)))
        WeightUnit.ST_LB -> BodyUnits.kgToStonesPounds(kg).let { (st, lb) -> res.getString(R.string.body_unit_weight_st_lb, st, num(lb)) }
    }

    /** A weight as one number in [weightSymbol] - for charts, deltas and rates (stones chart in lb). */
    fun weightNumber(kg: Double): Double = BodyUnits.weightIn(kg, weightUnit)

    /** "kg" or "lb" - the unit [weightNumber] is in. */
    val weightSymbol: String
        get() = res.getString(if (weightUnit == WeightUnit.KG) R.string.settings_unit_kg else R.string.settings_unit_lb)

    /** A signed change, e.g. "+0.4 kg" or "-1.1 lb". */
    fun weightChange(kg: Double): String =
        res.getString(R.string.body_unit_value_unit, String.format(Locale.US, "%+.1f", weightNumber(kg)), weightSymbol)

    /** A signed weekly rate, e.g. "-0.4 kg/wk". */
    fun weeklyRate(kgPerWeek: Double): String = res.getString(R.string.body_unit_per_week, weightChange(kgPerWeek))

    /** e.g. "82.5 cm" or "32.5 in". */
    fun length(cm: Double, decimals: Int = 1): String = when (lengthUnit) {
        LengthUnit.CM -> res.getString(R.string.body_unit_length_cm, num(cm, decimals))
        LengthUnit.IN -> res.getString(R.string.body_unit_length_in, num(BodyUnits.cmToInches(cm), decimals))
    }

    /** e.g. "178 cm" or "5 ft 10 in". */
    fun height(cm: Double): String = when (lengthUnit) {
        LengthUnit.CM -> res.getString(R.string.body_unit_length_cm, num(cm, 0))
        LengthUnit.IN -> BodyUnits.cmToFeetInches(cm).let { (ft, inch) -> res.getString(R.string.body_unit_height_ft_in, ft, inch) }
    }

    private fun num(value: Double, decimals: Int = 1) = String.format(Locale.US, "%.${decimals}f", value)
}

/** The formatter for the units chosen in Settings; recomposes when they change. */
@Composable
fun rememberBodyUnitFormatter(): BodyUnitFormatter {
    val context = LocalContext.current
    val weightUnit = UnitPrefs.weightUnit(context)
    val lengthUnit = UnitPrefs.lengthUnit(context)
    return remember(context, weightUnit, lengthUnit) { BodyUnitFormatter(context.resources, weightUnit, lengthUnit) }
}

/** Emitted values are rounded to 0.01 kg / 0.1 cm; within this they're the same value. */
private const val SYNC_TOLERANCE = 0.06

private fun metricText(value: Double): String = String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')

private fun oneDecimal(value: Double): String = BodyUnits.round1(value).let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }

private fun decimalOnly(text: String): String = text.filter { it.isDigit() || it == '.' || it == ',' }

/**
 * Keeps unit-converted text fields and a metric value string (what screens store) in step: the
 * fields show what the user typed in their unit; the metric string changes only when they type,
 * and the fields reload only when the metric value changes from outside (prefill, reset).
 */
@Composable
private fun SyncFromMetric(metricText: String, currentMetric: () -> Double?, reload: (Double?) -> Unit) {
    LaunchedEffect(metricText) {
        val external = BodyUnits.parseNumber(metricText)
        val mine = currentMetric()
        when {
            external == null && metricText.isBlank() && mine != null -> reload(null)
            external != null && (mine == null || abs(mine - external) > SYNC_TOLERANCE) -> reload(external)
        }
    }
}

/**
 * A weight field in the user's unit (two fields for stones and pounds) bound to a kg string.
 * Typing emits kg; text that isn't a number is passed through as-is so the screen can flag it.
 */
@Composable
fun WeightInputField(
    kgText: String,
    onKgTextChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: String? = null,
    imeAction: ImeAction = ImeAction.Next,
    placeholderKg: Double? = null
) {
    val unit = UnitPrefs.weightUnit(LocalContext.current)
    fun display(kg: Double?): Pair<String, String> = when {
        kg == null -> "" to ""
        unit == WeightUnit.KG -> metricText(kg) to ""
        unit == WeightUnit.LB -> oneDecimal(BodyUnits.kgToLb(kg)) to ""
        else -> BodyUnits.kgToStonesPounds(kg).let { (st, lb) -> st.toString() to oneDecimal(lb) }
    }
    val initial = remember(unit) { display(BodyUnits.parseNumber(kgText)) }
    var main by remember(unit) { mutableStateOf(if (unit == WeightUnit.KG) kgText else initial.first) }
    var extra by remember(unit) { mutableStateOf(initial.second) }

    fun parsedKg(m: String, e: String): Double? {
        val a = BodyUnits.parseNumber(m)
        val b = BodyUnits.parseNumber(e)
        return when (unit) {
            WeightUnit.KG -> a
            WeightUnit.LB -> a?.let(BodyUnits::lbToKg)
            WeightUnit.ST_LB -> if (a == null && b == null) null else BodyUnits.stonesPoundsToKg((a ?: 0.0).toInt(), b ?: 0.0)
        }
    }
    fun emit(m: String, e: String) {
        val kg = parsedKg(m, e)
        onKgTextChange(
            when {
                m.isBlank() && e.isBlank() -> ""
                kg == null -> m
                unit == WeightUnit.KG -> m.replace(',', '.')
                else -> String.format(Locale.US, "%.2f", kg)
            }
        )
    }
    SyncFromMetric(kgText, { parsedKg(main, extra) }) { kg -> display(kg).let { main = it.first; extra = it.second } }

    val placeholder = placeholderKg?.let { display(it) }
    val keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = imeAction)
    if (unit == WeightUnit.ST_LB) {
        Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = main,
                onValueChange = { main = it.filter(Char::isDigit).take(2); emit(main, extra) },
                label = { Text(label) },
                suffix = { Text(stringResource(R.string.body_unit_st)) },
                placeholder = placeholder?.let { { Text(it.first) } },
                isError = isError,
                supportingText = supportingText?.let { { Text(it) } },
                singleLine = true,
                keyboardOptions = keyboard.copy(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = extra,
                onValueChange = { extra = decimalOnly(it).take(4); emit(main, extra) },
                suffix = { Text(stringResource(R.string.settings_unit_lb)) },
                placeholder = placeholder?.let { { Text(it.second) } },
                isError = isError,
                singleLine = true,
                keyboardOptions = keyboard,
                modifier = Modifier.weight(1f)
            )
        }
    } else {
        OutlinedTextField(
            value = main,
            onValueChange = { main = decimalOnly(it).take(6); emit(main, extra) },
            label = { Text(label) },
            suffix = { Text(stringResource(if (unit == WeightUnit.KG) R.string.settings_unit_kg else R.string.settings_unit_lb)) },
            placeholder = placeholder?.let { { Text(it.first) } },
            isError = isError,
            supportingText = supportingText?.let { { Text(it) } },
            singleLine = true,
            keyboardOptions = keyboard,
            modifier = modifier
        )
    }
}

/**
 * A length field in the user's unit bound to a cm string. [height] shows feet and inches as two
 * fields when the unit is inches; tape measurements stay in decimal inches.
 */
@Composable
fun LengthInputField(
    cmText: String,
    onCmTextChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    height: Boolean = false,
    isError: Boolean = false,
    supportingText: String? = null,
    imeAction: ImeAction = ImeAction.Next
) {
    val unit = UnitPrefs.lengthUnit(LocalContext.current)
    val feet = height && unit == LengthUnit.IN
    fun display(cm: Double?): Pair<String, String> = when {
        cm == null -> "" to ""
        unit == LengthUnit.CM -> (if (height) oneDecimal(cm) else metricText(cm)) to ""
        feet -> BodyUnits.cmToFeetInches(cm).let { (ft, inch) -> ft.toString() to inch.toString() }
        else -> oneDecimal(BodyUnits.cmToInches(cm)) to ""
    }
    val initial = remember(unit, height) { display(BodyUnits.parseNumber(cmText)) }
    var main by remember(unit, height) { mutableStateOf(if (unit == LengthUnit.CM) cmText else initial.first) }
    var extra by remember(unit, height) { mutableStateOf(initial.second) }

    fun parsedCm(m: String, e: String): Double? {
        val a = BodyUnits.parseNumber(m)
        val b = BodyUnits.parseNumber(e)
        return when {
            unit == LengthUnit.CM -> a
            feet -> if (a == null && b == null) null else BodyUnits.feetInchesToCm((a ?: 0.0).toInt(), b ?: 0.0)
            else -> a?.let(BodyUnits::inchesToCm)
        }
    }
    fun emit(m: String, e: String) {
        val cm = parsedCm(m, e)
        onCmTextChange(
            when {
                m.isBlank() && e.isBlank() -> ""
                cm == null -> m
                unit == LengthUnit.CM -> m.replace(',', '.')
                else -> String.format(Locale.US, "%.1f", cm)
            }
        )
    }
    SyncFromMetric(cmText, { parsedCm(main, extra) }) { cm -> display(cm).let { main = it.first; extra = it.second } }

    val keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = imeAction)
    if (feet) {
        Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = main,
                onValueChange = { main = it.filter(Char::isDigit).take(1); emit(main, extra) },
                label = { Text(label) },
                suffix = { Text(stringResource(R.string.body_unit_ft)) },
                isError = isError,
                supportingText = supportingText?.let { { Text(it) } },
                singleLine = true,
                keyboardOptions = keyboard.copy(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = extra,
                onValueChange = { extra = decimalOnly(it).take(4); emit(main, extra) },
                suffix = { Text(stringResource(R.string.settings_unit_in)) },
                isError = isError,
                singleLine = true,
                keyboardOptions = keyboard,
                modifier = Modifier.weight(1f)
            )
        }
    } else {
        OutlinedTextField(
            value = main,
            onValueChange = { main = decimalOnly(it).take(6); emit(main, extra) },
            label = { Text(label) },
            suffix = { Text(stringResource(if (unit == LengthUnit.CM) R.string.settings_unit_cm else R.string.settings_unit_in)) },
            isError = isError,
            supportingText = supportingText?.let { { Text(it) } },
            singleLine = true,
            keyboardOptions = keyboard,
            modifier = modifier
        )
    }
}
