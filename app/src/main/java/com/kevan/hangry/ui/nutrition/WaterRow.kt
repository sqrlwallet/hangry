package com.kevan.hangry.ui.nutrition

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kevan.hangry.R
import com.kevan.hangry.domain.calculation.WaterIntake
import com.kevan.hangry.ui.components.HangryInfoTip
import com.kevan.hangry.ui.theme.LocalHangryTokens

/** A day's water against its target, for the daily target card. */
data class WaterProgress(
    val intakeMl: Int,
    val targetMl: Int,
    /** False when there's no weight on file and the target is the general default. */
    val targetFromWeight: Boolean
)

/** "Water 1.2 / 2.5 L", a thin bar, and a + that adds a glass, a bottle or a custom amount. */
@Composable
internal fun WaterRow(water: WaterProgress, onAdd: (Int) -> Unit, modifier: Modifier = Modifier) {
    val tokens = LocalHangryTokens.current
    val color = tokens.macroColors.water
    val progress by animateFloatAsState(
        targetValue = WaterIntake.progress(water.intakeMl, water.targetMl),
        animationSpec = tween(durationMillis = 800),
        label = "anim_water"
    )
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var customOpen by rememberSaveable { mutableStateOf(false) }
    val target = waterText(WaterFormat.amount(water.targetMl))

    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.nutrition_water),
                style = MaterialTheme.typography.labelMedium,
                color = color,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = waterText(WaterFormat.progress(water.intakeMl, water.targetMl)),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = tokens.textPrimary,
                modifier = Modifier.padding(start = 6.dp)
            )
            HangryInfoTip(
                title = stringResource(R.string.nutrition_water_tip_title),
                body = if (water.targetFromWeight) stringResource(R.string.nutrition_water_tip_body_weight, target)
                else stringResource(R.string.nutrition_water_tip_body_default, target)
            )
            Spacer(modifier = Modifier.weight(1f))
            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.nutrition_water_add),
                        tint = color,
                        modifier = Modifier.size(20.dp)
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.nutrition_water_glass, waterText(WaterFormat.amount(WaterIntake.GLASS_ML)))) },
                        onClick = { menuOpen = false; onAdd(WaterIntake.GLASS_ML) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.nutrition_water_bottle, waterText(WaterFormat.amount(WaterIntake.BOTTLE_ML)))) },
                        onClick = { menuOpen = false; onAdd(WaterIntake.BOTTLE_ML) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.nutrition_water_custom)) },
                        onClick = { menuOpen = false; customOpen = true }
                    )
                }
            }
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = color,
            trackColor = color.copy(alpha = 0.22f),
        )
    }

    if (customOpen) {
        CustomWaterDialog(
            onAdd = { customOpen = false; onAdd(it) },
            onDismiss = { customOpen = false }
        )
    }
}

@Composable
private fun CustomWaterDialog(onAdd: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val ml = WaterIntake.parseCustomMl(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.nutrition_water_custom_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { new -> text = new.filter { it.isDigit() }.take(5) },
                label = { Text(stringResource(R.string.nutrition_water_custom_label)) },
                isError = text.isNotEmpty() && ml == null,
                supportingText = if (text.isNotEmpty() && ml == null) {
                    { Text(stringResource(R.string.nutrition_water_custom_invalid, WaterIntake.MAX_SINGLE_ML)) }
                } else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        },
        confirmButton = {
            TextButton(onClick = { ml?.let(onAdd) }, enabled = ml != null) {
                Text(stringResource(R.string.nutrition_water_add_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.nutrition_water_cancel)) }
        }
    )
}
