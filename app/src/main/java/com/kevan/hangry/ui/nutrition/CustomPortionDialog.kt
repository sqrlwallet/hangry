package com.kevan.hangry.ui.nutrition

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.kevan.hangry.R
import com.kevan.hangry.data.local.entity.PORTIONS
import com.kevan.hangry.data.local.entity.parsePortionInput
import com.kevan.hangry.data.local.entity.portionLabel
import kotlin.math.roundToInt

/**
 * Any portion besides the ½ / 1 / 1½ / 2 shortcuts, typed in: "0.75", "1/3", "2.5".
 * With [calories] the dialog shows what the typed portion comes to before it's logged.
 */
@Composable
fun CustomPortionDialog(
    initial: Double,
    calories: Int?,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    // Starts empty unless a typed-in portion is already in use, so it can be tweaked.
    var text by remember { mutableStateOf(if (initial in PORTIONS) "" else initial.toBigDecimal().stripTrailingZeros().toPlainString()) }
    val portion = parsePortionInput(text)
    val showError = text.isNotBlank() && portion == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.nutrition_portion_custom_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { new -> if (new.length <= 8 && new.all { it.isDigit() || it in "., /" }) text = new },
                label = { Text(stringResource(R.string.nutrition_portion_custom_label)) },
                placeholder = { Text(stringResource(R.string.nutrition_portion_custom_hint)) },
                supportingText = {
                    when {
                        showError -> Text(stringResource(R.string.nutrition_portion_custom_invalid))
                        portion != null && calories != null -> Text(stringResource(R.string.nutrition_portion_kcal, portionLabel(portion), (calories * portion).roundToInt()))
                    }
                },
                isError = showError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { portion?.let(onConfirm) }),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(enabled = portion != null, onClick = { portion?.let(onConfirm) }) {
                Text(stringResource(R.string.nutrition_portion_custom_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
