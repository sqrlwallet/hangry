package com.kevan.hangry.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.res.stringResource
import com.kevan.hangry.R

/**
 * Undo-able delete: the caller hides [pending] straight away, this shows "<message> · Undo" and
 * then calls [onUndo] or [onCommit]. The real delete (files, Health Connect records) only happens
 * in [onCommit] - when the snackbar times out or is dismissed, a newer delete replaces it, or the
 * screen goes away. [onSettled] clears the caller's pending item afterwards.
 */
@Composable
fun <T : Any> UndoDeleteEffect(
    pending: T?,
    snackbarHostState: SnackbarHostState,
    message: (T) -> String,
    onUndo: (T) -> Unit,
    onCommit: (T) -> Unit,
    onSettled: () -> Unit
) {
    val undoLabel = stringResource(R.string.undo)
    val currentMessage by rememberUpdatedState(message)
    val currentUndo by rememberUpdatedState(onUndo)
    val currentCommit by rememberUpdatedState(onCommit)
    val currentSettled by rememberUpdatedState(onSettled)
    LaunchedEffect(pending) {
        val item = pending ?: return@LaunchedEffect
        var settled = false
        try {
            val result = snackbarHostState.showSnackbar(
                message = currentMessage(item),
                actionLabel = undoLabel,
                withDismissAction = true,
                duration = SnackbarDuration.Short
            )
            settled = true
            if (result == SnackbarResult.ActionPerformed) currentUndo(item) else currentCommit(item)
            currentSettled()
        } finally {
            // Cancelled (another delete, rotation, leaving the screen): nobody tapped Undo, so commit.
            if (!settled) currentCommit(item)
        }
    }
}

/**
 * Sheet state that asks before throwing away typed input: while [hasUnsavedChanges], a swipe
 * down, scrim tap or back press calls [onAttemptDismiss] instead of closing the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberDiscardGuardSheetState(hasUnsavedChanges: Boolean, onAttemptDismiss: () -> Unit): SheetState {
    val dirty by rememberUpdatedState(hasUnsavedChanges)
    val attempt by rememberUpdatedState(onAttemptDismiss)
    return rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { value ->
            if (value == SheetValue.Hidden && dirty) {
                attempt()
                false
            } else true
        }
    )
}

/** "Discard changes?" - [onDiscard] throws the input away, [onKeepEditing] goes back to it. */
@Composable
fun DiscardChangesDialog(onDiscard: () -> Unit, onKeepEditing: () -> Unit) {
    AlertDialog(
        onDismissRequest = onKeepEditing,
        title = { Text(stringResource(R.string.discard_changes_title)) },
        text = { Text(stringResource(R.string.discard_changes_body)) },
        confirmButton = { TextButton(onClick = onDiscard) { Text(stringResource(R.string.discard)) } },
        dismissButton = { TextButton(onClick = onKeepEditing) { Text(stringResource(R.string.keep_editing)) } }
    )
}
