package com.kevan.hangry.ui.nutrition

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
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
        title = { Text("Possible allergen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${alert.entry.foodName} may contain:", style = MaterialTheme.typography.bodyMedium)
                alert.warnings.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                Text(
                    "This is an AI estimate from the photo - check the ingredients or ask. It's still logged; edit or delete it if it's wrong.",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Got it") } },
        dismissButton = { TextButton(onClick = onEdit) { Text("Edit entry") } }
    )
}
