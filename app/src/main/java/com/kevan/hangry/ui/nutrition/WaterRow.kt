package com.kevan.hangry.ui.nutrition

import com.kevan.hangry.ui.components.ProgressFill
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
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.kevan.hangry.ui.theme.SurfaceDark
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
import com.kevan.hangry.ui.components.rememberHangryHaptics

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

    val isDark = tokens.cardBackground == SurfaceDark
    val haptic = rememberHangryHaptics()

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

            // Quick log pill (+1 glass / 250ml)
            Surface(
                onClick = {
                    haptic.confirm()
                    onAdd(WaterIntake.GLASS_ML)
                },
                shape = RoundedCornerShape(12.dp),
                color = color.copy(alpha = if (isDark) 0.18f else 0.12f),
                border = BorderStroke(1.dp, color.copy(alpha = if (isDark) 0.35f else 0.25f)),
                modifier = Modifier.padding(end = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = waterText(WaterFormat.amount(WaterIntake.GLASS_ML)),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                }
            }

            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.nutrition_water_add),
                        tint = tokens.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.nutrition_water_glass, waterText(WaterFormat.amount(WaterIntake.GLASS_ML)))) },
                        onClick = {
                            menuOpen = false
                            haptic.confirm()
                            onAdd(WaterIntake.GLASS_ML)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.nutrition_water_bottle, waterText(WaterFormat.amount(WaterIntake.BOTTLE_ML)))) },
                        onClick = {
                            menuOpen = false
                            haptic.confirm()
                            onAdd(WaterIntake.BOTTLE_ML)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.nutrition_water_custom)) },
                        onClick = { menuOpen = false; customOpen = true }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color.copy(alpha = if (isDark) 0.18f else 0.12f))
        ) {
            ProgressFill(progress = { progress }, colors = listOf(color.copy(alpha = 0.85f), color), cornerRadius = 3.dp)
        }
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
