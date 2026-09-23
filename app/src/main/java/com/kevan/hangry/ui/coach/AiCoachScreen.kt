package com.kevan.hangry.ui.coach

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
    "⚡ Am I ready to train today?",
    "🥗 How's my calorie & protein balance?",
    "💤 How did sleep affect my recovery?",
    "🧘 Stretches for my posture scan?",
    "📝 Tight hamstrings & lower back",
    "💊 Review my supplements"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiCoachScreen(
    viewModel: AiCoachViewModel,
    onNavigateBack: () -> Unit,
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
    val starters = uiState.suggestions.ifEmpty { QUICK_STARTERS }
    val context = LocalContext.current

    // Talk to Dash: the system speech recogniser, no microphone permission needed.
    val speechIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Ask $MASCOT_NAME")
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DashAvatar(size = 32.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(MASCOT_NAME, style = MaterialTheme.typography.titleLarge)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
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
                                contentDescription = "Personal Journal",
                                tint = tokens.textPrimary
                            )
                        }
                    }

                    // Clear chat
                    if (uiState.messages.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Clear Chat",
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
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
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
                        text = "Last 7 days loaded",
                        style = MaterialTheme.typography.labelMedium,
                        color = tokens.textSecondary,
                        modifier = Modifier.weight(1f)
                    )
                    HangryInfoTip(
                        title = "What Dash knows",
                        body = "Context loaded: Last 7 days of sleep, strain, workouts & journal"
                    )
                }
            }

            // Error banner if any
            uiState.errorMessage?.let { err ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(HangryTokens.CornerRadii.small),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = HangryTokens.Spacing.m, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = err,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.dismissError() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
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
                                            message = "Copied to clipboard",
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
                                    contentDescription = "Attached photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp))
                                )
                                IconButton(
                                    onClick = { viewModel.removeImage(uri) },
                                    modifier = Modifier.align(Alignment.TopEnd).size(22.dp)
                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), CircleShape)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove photo", modifier = Modifier.size(14.dp))
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
                            Icon(Icons.Default.AddAPhoto, contentDescription = "Attach a photo", tint = tokens.textSecondary)
                        }
                        DropdownMenu(expanded = showAttachMenu, onDismissRequest = { showAttachMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Take a photo") },
                                leadingIcon = { Icon(Icons.Default.CameraAlt, contentDescription = null) },
                                onClick = { showAttachMenu = false; photoLauncher.takePhoto() }
                            )
                            DropdownMenuItem(
                                text = { Text("Choose from gallery") },
                                leadingIcon = { Icon(Icons.Default.PhotoLibrary, contentDescription = null) },
                                onClick = { showAttachMenu = false; photoLauncher.pickFromGallery() }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = {
                            Text(
                                if (uiState.pendingImages.isEmpty()) "Ask $MASCOT_NAME or send a photo..." else "What should $MASCOT_NAME do with it?",
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
                                        contentDescription = "Clear input",
                                        tint = tokens.textMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        } else if (canDictate) {
                            {
                                IconButton(onClick = { runCatching { speechLauncher.launch(speechIntent) } }, enabled = !uiState.isLoading) {
                                    Icon(Icons.Default.Mic, contentDescription = "Speak to $MASCOT_NAME", tint = tokens.textSecondary)
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
                            .semantics { contentDescription = "Message input for $MASCOT_NAME" },
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
                            contentDescription = if (uiState.isLoading) "$MASCOT_NAME is thinking" else "Send message",
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
            title = { Text("Clear Chat History?") },
            text = { Text("Messages will be cleared. Journal & memories are kept.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearChat()
                        showClearDialog = false
                    }
                ) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
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
                !aiEnabled -> "AI features are disabled in Settings. Enable AI Features to give $MASCOT_NAME access to your 7-day health, nutrition, and journal memory."
                !hasApiKey -> "An OpenRouter API key is required to use AI features. Add your API key in Settings."
                else -> "$MASCOT_NAME is currently not configured."
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Set up $MASCOT_NAME",
                    style = MaterialTheme.typography.titleLarge,
                    color = tokens.textPrimary
                )
                HangryInfoTip(title = "Set up $MASCOT_NAME", body = detail)
            }
            Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))
            val message = when {
                !aiEnabled -> "Turn on AI Features in Settings."
                !hasApiKey -> "Add your OpenRouter key in Settings."
                else -> "$MASCOT_NAME isn't configured yet."
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
                Text("Open AI Settings", color = MaterialTheme.colorScheme.onPrimary)
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
        DashHero(size = 168.dp)
        Spacer(modifier = Modifier.height(HangryTokens.Spacing.m))
        Text(
            text = "Hi, I'm $MASCOT_NAME!",
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
                text = "⚡ 7-Day Biometrics & Memories Active",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        Spacer(modifier = Modifier.height(HangryTokens.Spacing.m))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Ask me anything about your health.",
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.textSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            HangryInfoTip(
                title = "About $MASCOT_NAME",
                body = "I continuously synthesize your sleep, recovery, workouts, nutrition, posture, and personal health journal. Ask me anything or share a new ache, goal, or problem."
            )
        }
        Spacer(modifier = Modifier.height(HangryTokens.Spacing.l))

        Text(
            text = "Quick Starters:",
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
                                contentDescription = "Attached photo",
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
                                contentDescription = "Copy text",
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
                        text = "Added to journal: $summary",
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
    val (icon, verb) = when (action.type) {
        CoachAction.ADD_SUPPLEMENT -> Icons.Default.Medication to "Add"
        CoachAction.UPDATE_SUPPLEMENT -> Icons.Default.Medication to "Update"
        CoachAction.MARK_SUPPLEMENT_TAKEN -> Icons.Default.CheckCircle to "Mark taken"
        CoachAction.LOG_MEAL -> Icons.Default.Restaurant to "Log"
        CoachAction.UPDATE_MEAL -> Icons.Default.Restaurant to "Update"
        CoachAction.ADD_MEAL_PLAN -> Icons.Default.Restaurant to "Add"
        CoachAction.ADD_READING -> Icons.Default.MonitorHeart to "Save"
        CoachAction.SET_GOAL, CoachAction.UPDATE_GOALS -> Icons.Default.Flag to "Set"
        CoachAction.LOG_WEIGHT, CoachAction.LOG_BODY_FAT, CoachAction.UPDATE_PROFILE -> Icons.Default.MonitorHeart to "Save"
        CoachAction.LOG_SLEEP -> Icons.Default.Bedtime to "Log"
        CoachAction.SET_HRV_FEELING -> Icons.Default.Favorite to "Save"
        CoachAction.SET_PREGNANCY, CoachAction.LOG_PERIOD -> Icons.Default.Favorite to "Save"
        CoachAction.OPEN_SCREEN -> Icons.AutoMirrored.Filled.ArrowForward to "Open"
        else -> Icons.Default.Add to "Add"
    }
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
                    CoachActionStatus.DONE -> Text("✓ " + (action.resultMessage ?: "Done"), style = MaterialTheme.typography.labelSmall, color = tokens.scoreColors.primed)
                    CoachActionStatus.FAILED -> Text(action.resultMessage ?: "Couldn't do that", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    CoachActionStatus.DISMISSED -> Text("Dismissed", style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
                }
            }
            if (action.status == CoachActionStatus.PENDING || action.status == CoachActionStatus.FAILED) {
                TextButton(onClick = onDismiss) { Text("Skip", color = tokens.textMuted) }
                Button(onClick = onDo, contentPadding = PaddingValues(horizontal = 14.dp)) {
                    Text(if (action.status == CoachActionStatus.FAILED) "Retry" else verb)
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
                    text = "$MASCOT_NAME is sniffing through your 7-day trends…",
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
                    "Journal & Memories",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f, fill = false)
                )
                HangryInfoTip(
                    title = "Personal Journal & Memories",
                    body = "Problems, injuries, and details remembered by $MASCOT_NAME. Tell $MASCOT_NAME about any problems, symptoms, or food reactions in chat, and they will be remembered here."
                )
            }
            IconButton(onClick = onAddEntry) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Journal Entry", tint = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(modifier = Modifier.height(HangryTokens.Spacing.m))

        if (journalEntries.isEmpty()) {
            DashEmptyState(
                scene = DashEmptyScene.MEMORIES,
                title = "No memories yet",
                body = "Mention an issue in chat and $MASCOT_NAME will remember it.",
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
                            contentDescription = "Delete",
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
        title = { Text("Add Personal Note / Problem") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Category:", style = MaterialTheme.typography.labelMedium)
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
                    label = { Text("Short Summary") },
                    placeholder = { Text("e.g. Knee soreness during squats") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Details") },
                    placeholder = { Text("e.g. Sharp right-knee pain on heavy sets") },
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
                Text("Save to Journal", color = MaterialTheme.colorScheme.onPrimary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
