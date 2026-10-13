package com.kevan.hangry.ui.coach

import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.kevan.hangry.R
import com.kevan.hangry.data.local.entity.CoachJournalEntity
import com.kevan.hangry.data.local.entity.CoachMessageEntity
import com.kevan.hangry.domain.model.CoachAction
import com.kevan.hangry.domain.model.CoachActionStatus
import com.kevan.hangry.ui.components.DashEmptyScene
import com.kevan.hangry.ui.components.DashEmptyState
import com.kevan.hangry.ui.components.HangryCard
import com.kevan.hangry.ui.components.HangryInfoDialog
import com.kevan.hangry.ui.components.HangryInfoSection
import com.kevan.hangry.ui.components.HangryInfoTip
import com.kevan.hangry.ui.navigation.LocalDockInset
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens
import com.kevan.hangry.util.rememberMultiPhotoCaptureLauncher
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

private val QUICK_STARTERS = listOf(
    R.string.coach_starter_ready_to_train,
    R.string.coach_starter_calorie_protein,
    R.string.coach_starter_sleep_recovery,
    R.string.coach_starter_posture_stretches,
    R.string.coach_starter_hamstrings_back,
    R.string.coach_starter_supplements
)

/** Gap between the chat and the screen edges. */
private val ChatGutter = 12.dp

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

    // Saveable: a half-typed question survives rotation and process death.
    var inputText by rememberSaveable { mutableStateOf("") }
    val photoLauncher = rememberMultiPhotoCaptureLauncher(maxItems = AiCoachViewModel.MAX_IMAGES) { uris ->
        viewModel.attachImages(uris)
    }
    val canSend = (inputText.isNotBlank() || uiState.pendingImages.isNotEmpty()) && !uiState.isLoading
    val defaultStarters = QUICK_STARTERS.map { stringResource(it) }
    val starters = uiState.suggestions.ifEmpty { defaultStarters }
    val context = LocalContext.current
    val tabReselectionBus = com.kevan.hangry.ui.navigation.LocalTabReselectionBus.current
    LaunchedEffect(tabReselectionBus, uiState.messages.size) {
        tabReselectionBus.events.collect { route ->
            if (route == com.kevan.hangry.ui.navigation.Screen.AiCoach.route && uiState.messages.isNotEmpty()) {
                listState.animateScrollToItem(uiState.messages.size - 1)
            }
        }
    }
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

    val sendInput = {
        if (canSend) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            val textToSend = inputText
            inputText = ""
            viewModel.sendMessage(textToSend)
        }
    }
    val sendStarter: (String) -> Unit = { starter ->
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        viewModel.sendMessage(starter)
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

    var showAboutDash by remember { mutableStateOf(false) }

    // No top bar (like Today): content starts under the status bar and a small floating
    // cluster at the top end holds history / new chat, with the rarer items in its menu.
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState, modifier = Modifier.padding(bottom = LocalDockInset.current)) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        // The message box has to sit above the floating tab bar (hidden while typing).
        // The app draws edge to edge, so the window doesn't shrink for the keyboard: make room
        // for it here, on top of what's already reserved for the nav bar or dock.
        val dockInset = LocalDockInset.current
        val bottomReserved = maxOf(innerPadding.calculateBottomPadding(), dockInset)
        val topInset = innerPadding.calculateTopPadding()
        Box(modifier = modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topInset, bottom = bottomReserved)
                    .consumeWindowInsets(PaddingValues(bottom = bottomReserved))
                    .imePadding()
            ) {
                if (!uiState.isAiConfigured) {
                    AiNotConfiguredBanner(
                        aiEnabled = uiState.aiFeaturesEnabled,
                        hasApiKey = uiState.hasApiKey,
                        onNavigateToAiSettings = onNavigateToAiSettings,
                        modifier = Modifier
                            .padding(top = TopControlsClearance)
                            .padding(HangryTokens.Spacing.m)
                    )
                } else {

                    // Error banner if any - a worried Dash with what went wrong.
                    uiState.errorMessage?.let { err ->
                        DashAlertCard(
                            title = stringResource(R.string.coach_couldnt_reply, MASCOT_NAME),
                            message = err,
                            onDismiss = { viewModel.dismissError() },
                            mascotSize = 40.dp,
                            modifier = Modifier
                                .padding(top = TopControlsClearance)
                                .padding(horizontal = ChatGutter, vertical = HangryTokens.Spacing.xs)
                        )
                    }
                    // Room for the floating controls, unless the error card already pushed things down.
                    val chatTopPadding = if (uiState.errorMessage != null) HangryTokens.Spacing.s else TopControlsClearance

                    // Main chat area
                    Box(modifier = Modifier.weight(1f)) {
                        if (uiState.messages.isEmpty()) {
                            EmptyConversationView(starters = starters, onSelectStarter = sendStarter, topPadding = chatTopPadding)
                        } else {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.s),
                                contentPadding = PaddingValues(
                                    start = ChatGutter,
                                    end = ChatGutter,
                                    top = chatTopPadding,
                                    bottom = HangryTokens.Spacing.s
                                )
                            ) {
                                items(uiState.messages, key = { it.id }) { message ->
                                    CoachMessageItem(
                                        message = message,
                                        onExecuteAction = { index -> viewModel.executeAction(message.id, index) },
                                        onDismissAction = { index -> viewModel.dismissAction(message.id, index) },
                                        onCopy = { text ->
                                            clipboardManager.setText(AnnotatedString(text))
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar(message = copiedMessage, duration = SnackbarDuration.Short)
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

                    // Follow-up suggestions; out of the way while Dash replies or you're writing.
                    if (uiState.messages.isNotEmpty() && !uiState.isLoading && inputText.isEmpty() && uiState.pendingImages.isEmpty()) {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth().padding(bottom = HangryTokens.Spacing.xs),
                            contentPadding = PaddingValues(horizontal = ChatGutter),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(starters) { starter ->
                                StarterChip(text = starter, onClick = { sendStarter(starter) }, singleLine = true)
                            }
                        }
                    }

                    ChatInputBar(
                        text = inputText,
                        onTextChange = { inputText = it },
                        pendingImages = uiState.pendingImages,
                        onRemoveImage = viewModel::removeImage,
                        canAttach = !uiState.isLoading && uiState.pendingImages.size < AiCoachViewModel.MAX_IMAGES,
                        onTakePhoto = { photoLauncher.takePhoto() },
                        onPickFromGallery = { photoLauncher.pickFromGallery() },
                        canDictate = canDictate,
                        onDictate = { runCatching { speechLauncher.launch(speechIntent) } },
                        isLoading = uiState.isLoading,
                        canSend = canSend,
                        onSend = sendInput
                    )
                }
            }

            DashTopControls(
                showNewChat = uiState.messages.isNotEmpty() || uiState.activeConversationId != null,
                newChatEnabled = !uiState.isLoading,
                journalCount = uiState.journalEntries.size,
                showAbout = uiState.isAiConfigured,
                onHistory = { viewModel.setShowHistory(true) },
                onNewChat = { viewModel.newChat() },
                onJournal = { viewModel.setShowJournalSheet(true) },
                onAbout = { showAboutDash = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = topInset + HangryTokens.Spacing.xs, end = ChatGutter)
            )
        }
    }

    if (showAboutDash) {
        HangryInfoDialog(
            title = stringResource(R.string.coach_about_dash, MASCOT_NAME),
            sections = listOf(
                HangryInfoSection(heading = "", body = stringResource(R.string.coach_about_dash_body)),
                HangryInfoSection(
                    heading = stringResource(R.string.coach_what_dash_knows_title),
                    body = stringResource(R.string.coach_what_dash_knows_body, MASCOT_NAME)
                )
            ),
            onDismiss = { showAboutDash = false }
        )
    }

    if (uiState.showHistory) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.setShowHistory(false) },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            ChatHistorySheet(
                conversations = uiState.visibleConversations,
                activeId = uiState.activeConversationId,
                query = uiState.historyQuery,
                hasAnyChats = uiState.conversations.isNotEmpty(),
                onQueryChange = viewModel::searchHistory,
                onOpen = viewModel::openConversation,
                onNewChat = viewModel::newChat,
                onRename = viewModel::renameConversation,
                onDelete = viewModel::deleteConversation,
                onDeleteAll = viewModel::deleteAllChats
            )
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
}

/** Space the chat leaves at the top so its first item clears [DashTopControls]. */
private val TopControlsClearance = 52.dp

/**
 * Dash's top controls: a small translucent pill floating at the top end, so the chat scrolls
 * underneath instead of sitting below a solid bar. History and new chat are one tap away; the
 * journal and "About Dash" are tucked in the overflow menu.
 */
@Composable
private fun DashTopControls(
    showNewChat: Boolean,
    newChatEnabled: Boolean,
    journalCount: Int,
    showAbout: Boolean,
    onHistory: () -> Unit,
    onNewChat: () -> Unit,
    onJournal: () -> Unit,
    onAbout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalHangryTokens.current
    var menuOpen by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = tokens.glassSurface,
        border = BorderStroke(1.dp, tokens.cardBorder),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = HangryTokens.Spacing.xxs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onHistory, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Default.History,
                    contentDescription = stringResource(R.string.coach_chat_history),
                    tint = tokens.textPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            if (showNewChat) {
                IconButton(onClick = onNewChat, enabled = newChatEnabled, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Default.AddComment,
                        contentDescription = stringResource(R.string.coach_new_chat),
                        tint = if (newChatEnabled) tokens.textPrimary else tokens.textMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.coach_more_options),
                        tint = tokens.textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (journalCount > 0) stringResource(R.string.coach_menu_journal_count, journalCount)
                                else stringResource(R.string.coach_menu_journal)
                            )
                        },
                        leadingIcon = { Icon(Icons.Default.Book, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onJournal()
                        }
                    )
                    if (showAbout) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.coach_about_dash, MASCOT_NAME)) },
                            leadingIcon = { Icon(Icons.Outlined.Info, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onAbout()
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * The message box: one rounded pill holding attach, the text, dictate/clear and send, with any
 * attached photos as small thumbnails above it.
 */
@Composable
private fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    pendingImages: List<Uri>,
    onRemoveImage: (Uri) -> Unit,
    canAttach: Boolean,
    onTakePhoto: () -> Unit,
    onPickFromGallery: () -> Unit,
    canDictate: Boolean,
    onDictate: () -> Unit,
    isLoading: Boolean,
    canSend: Boolean,
    onSend: () -> Unit
) {
    val tokens = LocalHangryTokens.current
    val haptic = LocalHapticFeedback.current
    var showAttachMenu by remember { mutableStateOf(false) }
    val messageInputDescription = stringResource(R.string.coach_message_input_description, MASCOT_NAME)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(start = ChatGutter, end = ChatGutter, top = HangryTokens.Spacing.xs, bottom = HangryTokens.Spacing.s)
    ) {
        if (pendingImages.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(pendingImages) { uri ->
                    Box(Modifier.size(56.dp)) {
                        AsyncImage(
                            model = uri,
                            contentDescription = stringResource(R.string.coach_attached_photo),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp))
                        )
                        IconButton(
                            onClick = { onRemoveImage(uri) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(2.dp)
                                .size(20.dp)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), CircleShape)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.coach_remove_photo), modifier = Modifier.size(12.dp))
                        }
                    }
                }
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Icons sit on the bottom line so they stay put as a long message grows upwards.
            Row(
                modifier = Modifier.padding(4.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Box {
                    IconButton(onClick = { showAttachMenu = true }, enabled = canAttach, modifier = Modifier.size(40.dp)) {
                        Icon(
                            Icons.Default.AddAPhoto,
                            contentDescription = stringResource(R.string.coach_attach_photo),
                            tint = tokens.textSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    DropdownMenu(expanded = showAttachMenu, onDismissRequest = { showAttachMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.coach_take_photo)) },
                            leadingIcon = { Icon(Icons.Default.CameraAlt, contentDescription = null) },
                            onClick = { showAttachMenu = false; onTakePhoto() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.coach_choose_from_gallery)) },
                            leadingIcon = { Icon(Icons.Default.PhotoLibrary, contentDescription = null) },
                            onClick = { showAttachMenu = false; onPickFromGallery() }
                        )
                    }
                }

                val textStyle = MaterialTheme.typography.bodyLarge
                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    textStyle = textStyle.copy(color = tokens.textPrimary),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Send
                    ),
                    keyboardActions = KeyboardActions(onSend = { onSend() }),
                    maxLines = 5,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp, vertical = 8.dp)
                        .semantics { contentDescription = messageInputDescription },
                    decorationBox = { innerTextField ->
                        Box {
                            if (text.isEmpty()) {
                                Text(
                                    if (pendingImages.isEmpty()) stringResource(R.string.coach_input_placeholder, MASCOT_NAME)
                                    else stringResource(R.string.coach_input_placeholder_with_photo, MASCOT_NAME),
                                    style = textStyle,
                                    color = tokens.textMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            innerTextField()
                        }
                    }
                )

                if (text.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            onTextChange("")
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.coach_clear_input), tint = tokens.textMuted, modifier = Modifier.size(18.dp))
                    }
                } else if (canDictate) {
                    IconButton(onClick = onDictate, enabled = !isLoading, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Mic, contentDescription = stringResource(R.string.coach_speak_to, MASCOT_NAME), tint = tokens.textSecondary)
                    }
                }

                IconButton(
                    onClick = onSend,
                    enabled = canSend,
                    modifier = Modifier
                        .size(40.dp)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(if (canSend) MaterialTheme.colorScheme.primary else Color.Transparent)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = if (isLoading) stringResource(R.string.coach_is_thinking, MASCOT_NAME) else stringResource(R.string.coach_send_message),
                        tint = if (canSend) MaterialTheme.colorScheme.onPrimary else tokens.textMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyConversationView(
    starters: List<String>,
    onSelectStarter: (String) -> Unit,
    topPadding: Dp = 0.dp
) {
    val tokens = LocalHangryTokens.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = topPadding)
            .padding(horizontal = HangryTokens.Spacing.m, vertical = HangryTokens.Spacing.m),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        DashExpression(mood = DashMood.WAVE, size = 120.dp)
        Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))
        Text(
            text = stringResource(R.string.coach_greeting, MASCOT_NAME),
            style = MaterialTheme.typography.titleLarge,
            color = tokens.textPrimary,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(R.string.coach_ask_me_anything),
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.textSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(HangryTokens.Spacing.m))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            starters.forEach { starter ->
                StarterChip(text = starter, onClick = { onSelectStarter(starter) })
            }
        }
    }
}

/** A tap-to-ask suggestion: sends [text] to Dash straight away. */
@Composable
private fun StarterChip(text: String, onClick: () -> Unit, singleLine: Boolean = false) {
    val tokens = LocalHangryTokens.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = tokens.cardBackground,
        border = BorderStroke(1.dp, tokens.cardBorder)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = tokens.textPrimary,
            maxLines = if (singleLine) 1 else 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
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

private val BubbleRadius = 18.dp
private val BubbleTail = 4.dp
private val DashAvatarSize = 26.dp

/** Dash's side of the chat: a small avatar beside left-aligned content, leaving room on the right. */
@Composable
private fun DashSide(
    avatar: @Composable () -> Unit = { DashAvatar(size = DashAvatarSize) },
    content: @Composable ColumnScope.() -> Unit
) {
    Row(
        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(end = 28.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(Modifier.size(DashAvatarSize), contentAlignment = Alignment.Center) { avatar() }
        Spacer(modifier = Modifier.width(6.dp))
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            content = content
        )
    }
}

/** Your side of the chat: right-aligned, with room on the left so it reads as a reply. */
@Composable
private fun UserSide(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 48.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content
    )
}

/** A chat bubble. Long-press runs [onLongPress] (copy), announced to TalkBack as [longPressLabel]. */
@Composable
private fun MessageBubble(
    isUser: Boolean,
    onLongPress: (() -> Unit)? = null,
    longPressLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = if (isUser) {
        RoundedCornerShape(topStart = BubbleRadius, topEnd = BubbleRadius, bottomStart = BubbleRadius, bottomEnd = BubbleTail)
    } else {
        RoundedCornerShape(topStart = BubbleTail, topEnd = BubbleRadius, bottomStart = BubbleRadius, bottomEnd = BubbleRadius)
    }
    val longPress by rememberUpdatedState(onLongPress)
    val gestures = if (onLongPress != null) {
        Modifier
            .pointerInput(Unit) { detectTapGestures(onLongPress = { longPress?.invoke() }) }
            .semantics { onLongClick(label = longPressLabel) { longPress?.invoke(); true } }
    } else Modifier
    Surface(
        color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        shape = shape,
        modifier = gestures
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), content = content)
    }
}

@Composable
private fun CoachMessageItem(
    message: CoachMessageEntity,
    onExecuteAction: (Int) -> Unit,
    onDismissAction: (Int) -> Unit,
    onCopy: (String) -> Unit
) {
    val isUser = message.role == "user"
    val photos = remember(message.imagePaths) { message.imagePaths?.split(',')?.filter { it.isNotBlank() }.orEmpty() }
    val actions = remember(message.actionsJson) { decodeActions(message.actionsJson) }
    val copyLabel = stringResource(R.string.coach_copy_text)

    val body: @Composable ColumnScope.() -> Unit = {
        MessageBubble(
            isUser = isUser,
            onLongPress = { onCopy(message.content.replace("*", "")) }.takeIf { message.content.isNotBlank() },
            longPressLabel = copyLabel
        ) {
            if (photos.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    photos.forEach { path ->
                        AsyncImage(
                            model = File(path),
                            contentDescription = stringResource(R.string.coach_attached_photo),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(88.dp).clip(RoundedCornerShape(10.dp))
                        )
                    }
                }
                if (message.content.isNotBlank()) Spacer(Modifier.height(6.dp))
            }
            if (message.content.isNotBlank()) {
                FormattedMessageText(text = message.content, isUser = isUser)
            }
        }

        // Actions Dash proposed - each runs only when tapped
        actions.forEachIndexed { index, action ->
            CoachActionCard(action = action, onDo = { onExecuteAction(index) }, onDismiss = { onDismissAction(index) })
        }

        message.journalEntrySummary?.let { summary -> JournalSavedChip(summary) }
    }

    if (isUser) UserSide(body) else DashSide(content = body)
}

/** Shown under a message when Dash saved something from it to the journal. */
@Composable
private fun JournalSavedChip(summary: String) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.BookmarkBorder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.coach_added_to_journal, summary),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Medium
            )
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
        border = BorderStroke(1.dp, tokens.cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
                Text(action.title.ifBlank { verb }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = tokens.textPrimary)
                when (action.status) {
                    CoachActionStatus.DONE -> Text(stringResource(R.string.coach_action_done_result, action.resultMessage ?: stringResource(R.string.coach_done)), style = MaterialTheme.typography.labelSmall, color = tokens.scoreColors.primed)
                    CoachActionStatus.FAILED -> Text(action.resultMessage ?: stringResource(R.string.coach_action_failed), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    CoachActionStatus.DISMISSED -> Text(stringResource(R.string.coach_action_dismissed), style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
                }
            }
            if (action.status == CoachActionStatus.PENDING || action.status == CoachActionStatus.FAILED) {
                TextButton(onClick = onDismiss, contentPadding = PaddingValues(horizontal = 10.dp)) {
                    Text(stringResource(R.string.coach_action_skip), color = tokens.textMuted)
                }
                Button(onClick = onDo, contentPadding = PaddingValues(horizontal = 14.dp), modifier = Modifier.height(36.dp)) {
                    Text(if (action.status == CoachActionStatus.FAILED) stringResource(R.string.coach_action_retry) else verb)
                }
            }
        }
    }
}

/** Dash's reply while it's still being written - replaced by the full message when done. */
@Composable
private fun StreamingReplyBubble(text: String) {
    DashSide {
        MessageBubble(isUser = false) {
            FormattedMessageText(text = text, isUser = false)
        }
    }
}

/** Dash thinking before the first words of a reply arrive. */
@Composable
private fun CoachLoadingBubble() {
    val tokens = LocalHangryTokens.current
    DashSide(avatar = { DashExpression(mood = DashMood.THINKING, size = DashAvatarSize, contentDescription = null) }) {
        MessageBubble(isUser = false) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PulsingDotsIndicator(dotColor = MaterialTheme.colorScheme.primary, dotSize = 6.dp)
                Spacer(modifier = Modifier.width(8.dp))
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
                modifier = Modifier.padding(vertical = HangryTokens.Spacing.m),
                actionLabel = stringResource(R.string.coach_journal_add_entry),
                onAction = onAddEntry
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
