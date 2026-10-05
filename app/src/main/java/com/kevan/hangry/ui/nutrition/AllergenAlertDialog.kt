package com.kevan.hangry.ui.nutrition

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kevan.hangry.R
import com.kevan.hangry.ui.coach.DashExpression
import com.kevan.hangry.ui.coach.DashMood

/**
 * Shown right after a meal is logged if AI thinks it may contain one of the user's allergies.
 * A dialog rather than a snackbar: this one shouldn't be missed.
 */
@Composable
fun AllergenAlertDialog(alert: AllergenAlert, onDismiss: () -> Unit, onEdit: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { DashExpression(mood = DashMood.CONCERNED, size = 96.dp, contentDescription = null, interactive = false) },
        title = { Text(stringResource(R.string.nutrition_allergen_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.nutrition_allergen_may_contain, alert.entry.foodName), style = MaterialTheme.typography.bodyMedium)
                alert.warnings.forEach { Text(stringResource(R.string.nutrition_bullet_item, it), style = MaterialTheme.typography.bodyMedium) }
                Text(
                    stringResource(R.string.nutrition_allergen_disclaimer),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.nutrition_got_it)) } },
        dismissButton = { TextButton(onClick = onEdit) { Text(stringResource(R.string.nutrition_allergen_edit_entry)) } }
    )
}
