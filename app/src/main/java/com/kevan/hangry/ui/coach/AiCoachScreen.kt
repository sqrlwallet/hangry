package com.kevan.hangry.ui.coach

import com.kevan.hangry.R
import androidx.compose.ui.res.stringResource
import com.kevan.hangry.ui.components.DashEmptyState
import com.kevan.hangry.ui.components.DashEmptyScene
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kevan.hangry.data.local.entity.CoachJournalEntity
import com.kevan.hangry.data.local.entity.CoachMessageEntity
import com.kevan.hangry.domain.model.CoachAction
import com.kevan.hangry.domain.model.CoachActionStatus
import com.kevan.hangry.ui.navigation.LocalDockInset
import com.kevan.hangry.util.rememberMultiPhotoCaptureLauncher
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Mic
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import com.kevan.hangry.ui.components.HangryCard
import com.kevan.hangry.ui.components.HangryInfoTip
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val QUICK_STARTERS = listOf(
    R.string.coach_starter_ready_to_train,
    R.string.coach_starter_calorie_protein,
    R.string.coach_starter_sleep_recovery,
    R.string.coach_starter_posture_stretches,
    R.string.coach_starter_hamstrings_back,
    R.string.coach_starter_supplements
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiCoachScreen(
    viewModel: AiCoachViewModel,
    onNavigateToAiSettings: () -> Unit,
    /** Dash's OPEN_SCREEN action: (screen name, optional breathing pattern). */
    onOpenScreen: (String, String?) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val tokens = LocalHangryTokens.current
    val haptic = LocalHapticFeedback.current
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardManager = LocalClipboardManager.current

    var inputText by remember { mutableStateOf("") }
    var showAttachMenu by remember { mutableStateOf(false) }
    val photoLauncher = rememberMultiPhotoCaptureLauncher(maxItems = AiCoachViewModel.MAX_IMAGES) { uris ->
        viewModel.attachImages(uris)
    }
    val canSend = (inputText.isNotBlank() || uiState.pendingImages.isNotEmpty()) && !uiState.isLoading
    val defaultStarters = QUICK_STARTERS.map { stringResource(it) }
    val starters = uiState.suggestions.ifEmpty { defaultStarters }
    val context = LocalContext.current
    val askDashPrompt = stringResource(R.string.coach_ask_dash, MASCOT_NAME)
    val copiedMessage = stringResource(R.string.coach_copied_to_clipboard)

    // Talk to Dash: the system speech recogniser, no microphone permission needed.
    val speechIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, askDashPrompt)
    }
    val canDictate = remember { speechIntent.resolveActivity(context.packageManager) != null }
    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { spoken ->
            inputText = listOf(inputText.trim(), spoken).filter { it.isNotEmpty() }.joinToString(" ")
        }
    }

    LaunchedEffect(uiState.draftToRestore) {
        uiState.draftToRestore?.let {
            if (inputText.isBlank()) inputText = it
            viewModel.consumeDraft()
        }
    }
    LaunchedEffect(uiState.openScreen) {
        uiState.openScreen?.let { (screen, pattern) ->
            viewModel.consumeOpenScreen()
            onOpenScreen(screen, pattern)
        }
    }

    LaunchedEffect(uiState.actionMessage) {
        uiState.actionMessage?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            viewModel.clearActionMessage()
        }
    }
    var showClearDialog by remember { mutableStateOf(false) }
    var showAddJournalDialog by remember { mutableStateOf(false) }

    // Auto-scroll to bottom whenever a new message arrives or loading state changes
    LaunchedEffect(uiState.messages.size, uiState.isLoading) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    // Keep a streaming reply in view as it grows (jump, not animate - it updates many times a second).
    val streamedLines = (uiState.streamingReply?.length ?: 0) / 120
    LaunchedEffect(streamedLines) {
        if (uiState.streamingReply != null && uiState.messages.isNotEmpty()) {
            listState.scrollToItem(uiState.messages.size)
        }
    }

    // Refresh state on entry
    LaunchedEffect(Unit) {
        viewModel.refreshState()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState, modifier = Modifier.padding(bottom = LocalDockInset.current)) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DashAvatar(size = 32.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(MASCOT_NAME, style = MaterialTheme.typography.titleLarge)
                    }
                },
                // A tab, so no back arrow.
                actions = {
                    // Journal & Memories button
                    BadgedBox(
                        badge = {
                            if (uiState.journalEntries.isNotEmpty()) {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ) {
                                    Text("${uiState.journalEntries.size}")
                                }
                            }
                        }
                    ) {
                        IconButton(onClick = { viewModel.setShowJournalSheet(true) }) {
                            Icon(
                                imageVector = Icons.Default.Book,
                                contentDescription = stringResource(R.string.coach_personal_journal),
                                tint = tokens.textPrimary
                            )
                        }
                    }

                    // Clear chat
                    if (uiState.messages.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = stringResource(R.string.coach_clear_chat),
                                tint = tokens.textSecondary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        // The message box has to sit above the floating tab bar (hidden while typing).
        // The app draws edge to edge, so the window doesn't shrink for the keyboard: make room
        // for it here, on top of what's already reserved for the nav bar or dock.
        val dockInset = LocalDockInset.current
        val bottomReserved = maxOf(innerPadding.calculateBottomPadding(), dockInset)
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding(), bottom = bottomReserved)
                .consumeWindowInsets(PaddingValues(bottom = bottomReserved))
                .imePadding()
        ) {
            // Check AI configuration
            if (!uiState.isAiConfigured) {
                AiNotConfiguredBanner(
                    aiEnabled = uiState.aiFeaturesEnabled,
                    hasApiKey = uiState.hasApiKey,
                    onNavigateToAiSettings = onNavigateToAiSettings,
                    modifier = Modifier.padding(HangryTokens.Spacing.m)
                )
            } else {

            // Context header pill
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(HangryTokens.CornerRadii.small),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = HangryTokens.Spacing.m, vertical = HangryTokens.Spacing.xs)
            ) {
                Row(
                    modifier = Modifier.padding(start = 12.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DashAvatar(size = 18.dp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.coach_last_7_days_loaded),
                        style = MaterialTheme.typography.labelMedium,
                        color = tokens.textSecondary,
                        modifier = Modifier.weight(1f)
                    )
                    HangryInfoTip(
                        title = stringResource(R.string.coach_what_dash_knows_title),
                        body = stringResource(R.string.coach_what_dash_knows_body, MASCOT_NAME)
                    )
                }
            }

            // Error banner if any - a worried Dash with what went wrong.
            uiState.errorMessage?.let { err ->
                DashAlertCard(
                    title = stringResource(R.string.coach_couldnt_reply, MASCOT_NAME),
                    message = err,
                    onDismiss = { viewModel.dismissError() },
                    modifier = Modifier.padding(horizontal = HangryTokens.Spacing.m, vertical = 4.dp)
                )
            }

            // Main chat area
            Box(modifier = Modifier.weight(1f)) {
                if (uiState.messages.isEmpty()) {
                    // Empty welcome screen
                    EmptyConversationView(
                        starters = starters,
                        onSelectStarter = { starter ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            inputText = starter
                            viewModel.sendMessage(starter)
                            inputText = ""
                        }
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = HangryTokens.Spacing.m),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        items(uiState.messages, key = { it.id }) { message ->
                            CoachMessageItem(
                                message = message,
                                onExecuteAction = { index -> viewModel.executeAction(message.id, index) },
                                onDismissAction = { index -> viewModel.dismissAction(message.id, index) },
                                onCopy = { text ->
                                    clipboardManager.setText(AnnotatedString(text))
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(
                                            message = copiedMessage,
                                            duration = SnackbarDuration.Short
                                        )
                                    }
                                }
                            )
                        }

                        if (uiState.isLoading) {
                            item {
                                val partial = uiState.streamingReply
                                if (partial.isNullOrBlank()) CoachLoadingBubble() else StreamingReplyBubble(partial)
                            }
                        }
                    }
                }
            }

            // Quick starter chips when messages exist
            if (uiState.messages.isNotEmpty() && !uiState.isLoading) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = HangryTokens.Spacing.m, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(starters) { starter ->
                        SuggestionChip(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.sendMessage(starter)
                            },
                            label = {
                                Text(starter, style = MaterialTheme.typography.labelMedium)
                            }
                        )
                    }
                }
            }

            // Bottom input bar
            Surface(
                tonalElevation = 3.dp,
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier.fillMaxWidth()
            ) {
              Column {
                if (uiState.pendingImages.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(start = HangryTokens.Spacing.m, end = HangryTokens.Spacing.m, top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.pendingImages) { uri ->
                            Box(Modifier.size(64.dp)) {
                                AsyncImage(
                                    model = uri,
                                    contentDescription = stringResource(R.string.coach_attached_photo),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp))
                                )
                                IconButton(
                                    onClick = { viewModel.removeImage(uri) },
                                    modifier = Modifier.align(Alignment.TopEnd).size(22.dp)
                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), CircleShape)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.coach_remove_photo), modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = HangryTokens.Spacing.m, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        IconButton(
                            onClick = { showAttachMenu = true },
                            enabled = !uiState.isLoading && uiState.pendingImages.size < AiCoachViewModel.MAX_IMAGES
                        ) {
                            Icon(Icons.Default.AddAPhoto, contentDescription = stringResource(R.string.coach_attach_photo), tint = tokens.textSecondary)
                        }
                        DropdownMenu(expanded = showAttachMenu, onDismissRequest = { showAttachMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.coach_take_photo)) },
                                leadingIcon = { Icon(Icons.Default.CameraAlt, contentDescription = null) },
                                onClick = { showAttachMenu = false; photoLauncher.takePhoto() }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.coach_choose_from_gallery)) },
                                leadingIcon = { Icon(Icons.Default.PhotoLibrary, contentDescription = null) },
                                onClick = { showAttachMenu = false; photoLauncher.pickFromGallery() }
                            )
                        }
                    }
                    val messageInputDescription = stringResource(R.string.coach_message_input_description, MASCOT_NAME)
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = {
                            Text(
                                if (uiState.pendingImages.isEmpty()) stringResource(R.string.coach_input_placeholder, MASCOT_NAME) else stringResource(R.string.coach_input_placeholder_with_photo, MASCOT_NAME),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        trailingIcon = if (inputText.isNotEmpty()) {
                            {
                                IconButton(
                                    onClick = {
                                        inputText = ""
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = stringResource(R.string.coach_clear_input),
                                        tint = tokens.textMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        } else if (canDictate) {
                            {
                                IconButton(onClick = { runCatching { speechLauncher.launch(speechIntent) } }, enabled = !uiState.isLoading) {
                                    Icon(Icons.Default.Mic, contentDescription = stringResource(R.string.coach_speak_to, MASCOT_NAME), tint = tokens.textSecondary)
                                }
                            }
                        } else null,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Send
                        ),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (canSend) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    val textToSend = inputText
                                    inputText = ""
                                    viewModel.sendMessage(textToSend)
                                }
                            }
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .semantics { contentDescription = messageInputDescription },
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 4,
                        enabled = !uiState.isLoading
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val textToSend = inputText
                            inputText = ""
                            viewModel.sendMessage(textToSend)
                        },
                        enabled = canSend,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                if (canSend) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = if (uiState.isLoading) stringResource(R.string.coach_is_thinking, MASCOT_NAME) else stringResource(R.string.coach_send_message),
                            tint = if (canSend) MaterialTheme.colorScheme.onPrimary else tokens.textSecondary
                        )
                    }
                }
              }
            }
        }
    }
}

    // Journal & Memories Bottom Sheet
    if (uiState.showJournalSheet) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.setShowJournalSheet(false) },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            CoachJournalBottomSheet(
                journalEntries = uiState.journalEntries,
                onDelete = { viewModel.deleteJournalEntry(it) },
                onAddEntry = { showAddJournalDialog = true },
                onClose = { viewModel.setShowJournalSheet(false) }
            )
        }
    }

    // Manual Add Journal Dialog
    if (showAddJournalDialog) {
        AddJournalEntryDialog(
            onDismiss = { showAddJournalDialog = false },
            onConfirm = { category, summary, content ->
                viewModel.addManualJournalEntry(category, summary, content)
                showAddJournalDialog = false
            }
        )
    }

    // Clear Chat Confirmation Dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.coach_clear_history_title)) },
            text = { Text(stringResource(R.string.coach_clear_history_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearChat()
                        showClearDialog = false
                    }
                ) {
                    Text(stringResource(R.string.coach_clear), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun AiNotConfiguredBanner(
    aiEnabled: Boolean,
    hasApiKey: Boolean,
    onNavigateToAiSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalHangryTokens.current
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HangryCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = HangryTokens.Spacing.l
        ) {
            DashAvatar(size = 56.dp)
            Spacer(modifier = Modifier.height(HangryTokens.Spacing.m))
            val detail = when {
                !aiEnabled -> stringResource(R.string.coach_setup_detail_ai_disabled, MASCOT_NAME)
                !hasApiKey -> stringResource(R.string.coach_setup_detail_no_key)
                else -> stringResource(R.string.coach_setup_detail_not_configured, MASCOT_NAME)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.coach_setup_title, MASCOT_NAME),
                    style = MaterialTheme.typography.titleLarge,
                    color = tokens.textPrimary
                )
                HangryInfoTip(title = stringResource(R.string.coach_setup_title, MASCOT_NAME), body = detail)
            }
            Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))
            val message = when {
                !aiEnabled -> stringResource(R.string.coach_setup_message_ai_disabled)
                !hasApiKey -> stringResource(R.string.coach_setup_message_no_key)
                else -> stringResource(R.string.coach_setup_message_not_configured, MASCOT_NAME)
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.textSecondary
            )
            Spacer(modifier = Modifier.height(HangryTokens.Spacing.l))
            Button(
                onClick = onNavigateToAiSettings,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.coach_open_ai_settings), color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

@Composable
private fun EmptyConversationView(
    starters: List<String>,
    onSelectStarter: (String) -> Unit
) {
    val tokens = LocalHangryTokens.current
    val haptic = LocalHapticFeedback.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(HangryTokens.Spacing.l),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        DashExpression(mood = DashMood.WAVE, size = 168.dp)
        Spacer(modifier = Modifier.height(HangryTokens.Spacing.m))
        Text(
            text = stringResource(R.string.coach_greeting, MASCOT_NAME),
            style = MaterialTheme.typography.headlineSmall,
            color = tokens.textPrimary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))

        Surface(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            shape = RoundedCornerShape(100.dp)
        ) {
            Text(
                text = stringResource(R.string.coach_biometrics_active),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        Spacer(modifier = Modifier.height(HangryTokens.Spacing.m))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.coach_ask_me_anything),
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.textSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            HangryInfoTip(
                title = stringResource(R.string.coach_about_dash, MASCOT_NAME),
                body = stringResource(R.string.coach_about_dash_body)
            )
        }
        Spacer(modifier = Modifier.height(HangryTokens.Spacing.l))

        Text(
            text = stringResource(R.string.coach_quick_starters_label),
            style = MaterialTheme.typography.titleSmall,
            color = tokens.textPrimary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.Start)
        )
        Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))
        starters.forEach { starter ->
            HangryCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelectStarter(starter)
                    },
                cornerRadius = 14.dp,
                contentPadding = 14.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = starter,
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.textPrimary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = tokens.textMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PulsingDotsIndicator(
    modifier: Modifier = Modifier,
    dotColor: Color = MaterialTheme.colorScheme.primary,
    dotSize: Dp = 6.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulsing_dots")

    val alpha1 by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot1"
    )
    val alpha2 by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, delayMillis = 150, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot2"
    )
    val alpha3 by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, delayMillis = 300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot3"
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(dotSize)
                .clip(CircleShape)
                .background(dotColor.copy(alpha = alpha1))
        )
        Box(
            modifier = Modifier
                .size(dotSize)
                .clip(CircleShape)
                .background(dotColor.copy(alpha = alpha2))
        )
        Box(
            modifier = Modifier
                .size(dotSize)
                .clip(CircleShape)
                .background(dotColor.copy(alpha = alpha3))
        )
    }
}

@Composable
private fun FormattedMessageText(
    text: String,
    isUser: Boolean,
    modifier: Modifier = Modifier
) {
    val tokens = LocalHangryTokens.current
    val baseColor = if (isUser) MaterialTheme.colorScheme.onPrimary else tokens.textPrimary

    val annotatedString = remember(text, isUser) {
        // Pre-clean: convert raw asterisk or dash bullets to clean unicode bullets
        val preCleaned = text
            .replace(Regex("""(?m)^\s*[\*\-]\s+"""), "• ")
            .replace(Regex("""\n[\*\-]\s+"""), "\n• ")

        buildAnnotatedString {
            val boldRegex = Regex("""\*\*(.*?)\*\*""")
            var currentIndex = 0
            val matches = boldRegex.findAll(preCleaned)

            for (match in matches) {
                if (match.range.first > currentIndex) {
                    val rawPart = preCleaned.substring(currentIndex, match.range.first)
                    // Strip any stray asterisks from non-bold parts
                    append(rawPart.replace("*", ""))
                }
                val boldContent = match.groupValues[1].replace("*", "")
                withStyle(
                    SpanStyle(
                        fontWeight = FontWeight.Bold,
                        color = if (isUser) baseColor else tokens.chartColors.activeCalories
                    )
                ) {
                    append(boldContent)
                }
                currentIndex = match.range.last + 1
            }
            if (currentIndex < preCleaned.length) {
                val remaining = preCleaned.substring(currentIndex)
                append(remaining.replace("*", ""))
            }
        }
    }

    Text(
        text = annotatedString,
        style = MaterialTheme.typography.bodyMedium,
        color = baseColor,
        modifier = modifier
    )
}

@Composable
private fun CoachMessageItem(
    message: CoachMessageEntity,
    onExecuteAction: (Int) -> Unit,
    onDismissAction: (Int) -> Unit,
    onCopy: (String) -> Unit
) {
    val tokens = LocalHangryTokens.current
    val isUser = message.role == "user"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (!isUser) {
            Row(
                modifier = Modifier
                    .padding(start = 6.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DashAvatar(size = 24.dp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = MASCOT_NAME,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Surface(
            color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            modifier = Modifier.widthIn(max = 330.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                val photos = remember(message.imagePaths) { message.imagePaths?.split(',')?.filter { it.isNotBlank() }.orEmpty() }
                if (photos.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        photos.forEach { path ->
                            AsyncImage(
                                model = File(path),
                                contentDescription = stringResource(R.string.coach_attached_photo),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(96.dp).clip(RoundedCornerShape(10.dp))
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                FormattedMessageText(
                    text = message.content,
                    isUser = isUser
                )

                if (!isUser) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { onCopy(message.content.replace("*", "")) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = stringResource(R.string.coach_copy_text),
                                tint = tokens.textMuted,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }

        // Actions Dash proposed - each runs only when tapped
        val actions = remember(message.actionsJson) { decodeActions(message.actionsJson) }
        actions.forEachIndexed { index, action ->
            Spacer(modifier = Modifier.height(6.dp))
            CoachActionCard(action = action, onDo = { onExecuteAction(index) }, onDismiss = { onDismissAction(index) })
        }

        // Attached journal entry badge if created
        message.journalEntrySummary?.let { summary ->
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.widthIn(max = 330.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.BookmarkBorder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.coach_added_to_journal, summary),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

private val actionJson = Json { ignoreUnknownKeys = true }

private fun decodeActions(raw: String?): List<CoachAction> =
    raw?.let { runCatching { actionJson.decodeFromString(ListSerializer(CoachAction.serializer()), it) }.getOrNull() }.orEmpty()

@Composable
private fun CoachActionCard(action: CoachAction, onDo: () -> Unit, onDismiss: () -> Unit) {
    val tokens = LocalHangryTokens.current
    val (icon, verbRes) = when (action.type) {
        CoachAction.ADD_SUPPLEMENT -> Icons.Default.Medication to R.string.coach_action_add
        CoachAction.UPDATE_SUPPLEMENT -> Icons.Default.Medication to R.string.coach_action_update
        CoachAction.MARK_SUPPLEMENT_TAKEN -> Icons.Default.CheckCircle to R.string.coach_action_mark_taken
        CoachAction.LOG_MEAL -> Icons.Default.Restaurant to R.string.coach_action_log
        CoachAction.UPDATE_MEAL -> Icons.Default.Restaurant to R.string.coach_action_update
        CoachAction.ADD_MEAL_PLAN -> Icons.Default.Restaurant to R.string.coach_action_add
        CoachAction.ADD_READING -> Icons.Default.MonitorHeart to R.string.coach_action_save
        CoachAction.SET_GOAL, CoachAction.UPDATE_GOALS -> Icons.Default.Flag to R.string.coach_action_set
        CoachAction.LOG_WEIGHT, CoachAction.LOG_BODY_FAT, CoachAction.UPDATE_PROFILE -> Icons.Default.MonitorHeart to R.string.coach_action_save
        CoachAction.LOG_SLEEP -> Icons.Default.Bedtime to R.string.coach_action_log
        CoachAction.SET_HRV_FEELING -> Icons.Default.Favorite to R.string.coach_action_save
        CoachAction.SET_PREGNANCY, CoachAction.LOG_PERIOD -> Icons.Default.Favorite to R.string.coach_action_save
        CoachAction.OPEN_SCREEN -> Icons.AutoMirrored.Filled.ArrowForward to R.string.coach_action_open
        else -> Icons.Default.Add to R.string.coach_action_add
    }
    val verb = stringResource(verbRes)
    Surface(
        color = tokens.cardBackground,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, tokens.cardBorder),
        modifier = Modifier.widthIn(max = 330.dp).fillMaxWidth()
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(action.title.ifBlank { verb }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = tokens.textPrimary)
                when (action.status) {
                    CoachActionStatus.DONE -> Text(stringResource(R.string.coach_action_done_result, action.resultMessage ?: stringResource(R.string.coach_done)), style = MaterialTheme.typography.labelSmall, color = tokens.scoreColors.primed)
                    CoachActionStatus.FAILED -> Text(action.resultMessage ?: stringResource(R.string.coach_action_failed), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    CoachActionStatus.DISMISSED -> Text(stringResource(R.string.coach_action_dismissed), style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
                }
            }
            if (action.status == CoachActionStatus.PENDING || action.status == CoachActionStatus.FAILED) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.coach_action_skip), color = tokens.textMuted) }
                Button(onClick = onDo, contentPadding = PaddingValues(horizontal = 14.dp)) {
                    Text(if (action.status == CoachActionStatus.FAILED) stringResource(R.string.coach_action_retry) else verb)
                }
            }
        }
    }
}

/** Dash's reply while it's still being written - replaced by the full message when done. */
@Composable
private fun StreamingReplyBubble(text: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalAlignment = Alignment.Start) {
        Row(modifier = Modifier.padding(start = 6.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            DashAvatar(size = 24.dp)
            Spacer(modifier = Modifier.width(6.dp))
            Text(MASCOT_NAME, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        }
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp),
            modifier = Modifier.widthIn(max = 330.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                FormattedMessageText(text = text, isUser = false)
            }
        }
    }
}

@Composable
private fun CoachLoadingBubble() {
    val tokens = LocalHangryTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DashExpression(mood = DashMood.THINKING, size = 44.dp, contentDescription = null)
                Spacer(modifier = Modifier.width(10.dp))
                PulsingDotsIndicator(dotColor = MaterialTheme.colorScheme.primary, dotSize = 7.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.coach_loading_bubble, MASCOT_NAME),
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary
                )
            }
        }
    }
}

@Composable
private fun CoachJournalBottomSheet(
    journalEntries: List<CoachJournalEntity>,
    onDelete: (Long) -> Unit,
    onAddEntry: () -> Unit,
    onClose: () -> Unit
) {
    val tokens = LocalHangryTokens.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(HangryTokens.Spacing.m)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.coach_journal_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f, fill = false)
                )
                HangryInfoTip(
                    title = stringResource(R.string.coach_journal_info_title),
                    body = stringResource(R.string.coach_journal_info_body, MASCOT_NAME)
                )
            }
            IconButton(onClick = onAddEntry) {
                Icon(imageVector = Icons.Default.Add, contentDescription = stringResource(R.string.coach_journal_add_entry), tint = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(modifier = Modifier.height(HangryTokens.Spacing.m))

        if (journalEntries.isEmpty()) {
            DashEmptyState(
                scene = DashEmptyScene.MEMORIES,
                title = stringResource(R.string.coach_journal_empty_title),
                body = stringResource(R.string.coach_journal_empty_body, MASCOT_NAME),
                modifier = Modifier.padding(vertical = HangryTokens.Spacing.m)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(journalEntries, key = { it.id }) { entry ->
                    JournalEntryCard(entry = entry, onDelete = { onDelete(entry.id) })
                }
            }
        }
        Spacer(modifier = Modifier.height(HangryTokens.Spacing.l))
    }
}

@Composable
private fun JournalEntryCard(
    entry: CoachJournalEntity,
    onDelete: () -> Unit
) {
    val tokens = LocalHangryTokens.current
    val categoryColor = when (entry.category.uppercase()) {
        "PROBLEM" -> MaterialTheme.colorScheme.error
        "INJURY" -> tokens.brandAccent
        "DIET" -> tokens.scoreColors.primed
        "GOAL" -> MaterialTheme.colorScheme.primary
        else -> tokens.textSecondary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = categoryColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = entry.category.uppercase(),
                        color = categoryColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entry.date.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.textSecondary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = stringResource(R.string.coach_delete),
                            tint = tokens.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = entry.summary,
                style = MaterialTheme.typography.titleSmall,
                color = tokens.textPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = entry.content,
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textSecondary
            )
        }
    }
}

@Composable
private fun AddJournalEntryDialog(
    onDismiss: () -> Unit,
    onConfirm: (category: String, summary: String, content: String) -> Unit
) {
    var category by remember { mutableStateOf("PROBLEM") }
    var summary by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    val categories = listOf("PROBLEM", "DIET", "INJURY", "HABIT", "GOAL", "NOTE")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.coach_journal_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.coach_journal_category_label), style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(categories) { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                OutlinedTextField(
                    value = summary,
                    onValueChange = { summary = it },
                    label = { Text(stringResource(R.string.coach_journal_summary_label)) },
                    placeholder = { Text(stringResource(R.string.coach_journal_summary_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text(stringResource(R.string.coach_journal_details_label)) },
                    placeholder = { Text(stringResource(R.string.coach_journal_details_placeholder)) },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(category, summary, content) },
                enabled = summary.isNotBlank() && content.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(stringResource(R.string.coach_journal_save), color = MaterialTheme.colorScheme.onPrimary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
