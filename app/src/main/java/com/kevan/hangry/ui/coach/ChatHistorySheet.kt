package com.kevan.hangry.ui.coach

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kevan.hangry.R
import com.kevan.hangry.data.local.entity.CoachConversationSummary
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** When a chat was last used, as the history groups it. */
private enum class ChatAge(val labelRes: Int) {
    TODAY(R.string.coach_history_today),
    YESTERDAY(R.string.coach_history_yesterday),
    THIS_WEEK(R.string.coach_history_week),
    EARLIER(R.string.coach_history_earlier)
}

private fun ageOf(time: Instant, today: LocalDate = LocalDate.now()): ChatAge {
    val day = time.atZone(ZoneId.systemDefault()).toLocalDate()
    return when {
        !day.isBefore(today) -> ChatAge.TODAY
        day == today.minusDays(1) -> ChatAge.YESTERDAY
        day.isAfter(today.minusDays(7)) -> ChatAge.THIS_WEEK
        else -> ChatAge.EARLIER
    }
}

/**
 * Past chats with Dash: search, open, rename, delete, or start a new one. Memories Dash saved
 * to the journal belong to you, not a chat, so they stay when a chat is deleted.
 */
@Composable
fun ChatHistorySheet(
    conversations: List<CoachConversationSummary>,
    activeId: Long?,
    query: String,
    hasAnyChats: Boolean,
    onQueryChange: (String) -> Unit,
    onOpen: (Long) -> Unit,
    onNewChat: () -> Unit,
    onRename: (Long, String) -> Unit,
    onDelete: (Long) -> Unit,
    onDeleteAll: () -> Unit
) {
    val tokens = LocalHangryTokens.current
    var renaming by remember { mutableStateOf<CoachConversationSummary?>(null) }
    var deleting by remember { mutableStateOf<CoachConversationSummary?>(null) }
    var deletingAll by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f).padding(horizontal = HangryTokens.Spacing.m)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.coach_history_title),
                style = MaterialTheme.typography.titleLarge,
                color = tokens.textPrimary,
                modifier = Modifier.weight(1f)
            )
            FilledTonalButton(onClick = onNewChat) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.coach_new_chat))
            }
        }
        Spacer(Modifier.height(HangryTokens.Spacing.s))
        if (hasAnyChats) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.coach_history_search)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.coach_history_clear_search))
                        }
                    }
                }
            )
            Spacer(Modifier.height(HangryTokens.Spacing.s))
        }

        when {
            !hasAnyChats -> Text(
                stringResource(R.string.coach_history_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.textSecondary,
                modifier = Modifier.padding(vertical = HangryTokens.Spacing.l)
            )
            conversations.isEmpty() -> Text(
                stringResource(R.string.coach_history_no_matches, query.trim()),
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.textSecondary,
                modifier = Modifier.padding(vertical = HangryTokens.Spacing.l)
            )
            else -> {
                val groups = conversations.groupBy { ageOf(it.updatedAt) }
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    ChatAge.entries.forEach { age ->
                        val inGroup = groups[age].orEmpty()
                        if (inGroup.isNotEmpty()) {
                            item(key = "header-$age") {
                                Text(
                                    stringResource(age.labelRes),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = tokens.textMuted,
                                    modifier = Modifier.padding(top = HangryTokens.Spacing.s, bottom = 4.dp)
                                )
                            }
                            items(inGroup, key = { it.id }) { chat ->
                                ChatRow(
                                    chat = chat,
                                    active = chat.id == activeId,
                                    modifier = Modifier.animateItem(),
                                    onOpen = { onOpen(chat.id) },
                                    onRename = { renaming = chat },
                                    onDelete = { deleting = chat }
                                )
                            }
                        }
                    }
                    item(key = "delete-all") {
                        TextButton(onClick = { deletingAll = true }, modifier = Modifier.padding(vertical = HangryTokens.Spacing.s)) {
                            Text(stringResource(R.string.coach_history_delete_all), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(HangryTokens.Spacing.m))
    }

    renaming?.let { chat ->
        var title by remember(chat.id) { mutableStateOf(chat.title) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text(stringResource(R.string.coach_history_rename)) },
            text = { OutlinedTextField(value = title, onValueChange = { title = it.take(60) }, singleLine = true) },
            confirmButton = {
                TextButton(enabled = title.isNotBlank(), onClick = { onRename(chat.id, title); renaming = null }) {
                    Text(stringResource(R.string.coach_history_save))
                }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
    deleting?.let { chat ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.coach_history_delete_title)) },
            text = { Text(stringResource(R.string.coach_history_delete_body, chat.title)) },
            confirmButton = {
                TextButton(onClick = { onDelete(chat.id); deleting = null }) {
                    Text(stringResource(R.string.coach_history_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
    if (deletingAll) {
        AlertDialog(
            onDismissRequest = { deletingAll = false },
            title = { Text(stringResource(R.string.coach_history_delete_all_title)) },
            text = { Text(stringResource(R.string.coach_clear_history_body)) },
            confirmButton = {
                TextButton(onClick = { onDeleteAll(); deletingAll = false }) {
                    Text(stringResource(R.string.coach_history_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deletingAll = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun ChatRow(
    chat: CoachConversationSummary,
    active: Boolean,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val tokens = LocalHangryTokens.current
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (active) tokens.cardBorder.copy(alpha = 0.5f) else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(12.dp))
            .clickable(onClick = onOpen)
            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                chat.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                color = tokens.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                listOfNotNull(
                    DateUtils.getRelativeTimeSpanString(chat.updatedAt.toEpochMilli(), System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
                    chat.lastMessage?.lineSequence()?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = { menu = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.coach_history_options, chat.title), tint = tokens.textSecondary)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.coach_history_rename)) },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                onClick = { menu = false; onRename() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.coach_history_delete)) },
                leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null) },
                onClick = { menu = false; onDelete() }
            )
        }
    }
}
