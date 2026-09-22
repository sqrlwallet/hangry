package com.kevan.hangry.ui.coach

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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.SmartToy
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
import androidx.compose.ui.unit.sp
import com.kevan.hangry.data.local.entity.CoachJournalEntity
import com.kevan.hangry.data.local.entity.CoachMessageEntity
import com.kevan.hangry.ui.components.HangryCard
import com.kevan.hangry.ui.theme.EmberAccent
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val QUICK_STARTERS = listOf(
    "⚡ What is my training readiness today?",
    "🥗 Analyze my calorie and protein balance",
    "💤 How did my sleep affect my recovery?",
    "🧘 What stretches match my posture scan?",
    "📝 I have tight hamstrings and lower back"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiCoachScreen(
    viewModel: AiCoachViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToAiSettings: () -> Unit,
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
    var showClearDialog by remember { mutableStateOf(false) }
    var showAddJournalDialog by remember { mutableStateOf(false) }

    // Auto-scroll to bottom whenever a new message arrives or loading state changes
    LaunchedEffect(uiState.messages.size, uiState.isLoading) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
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
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = EmberAccent,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("AI Coach", style = MaterialTheme.typography.titleLarge)
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
                                    containerColor = EmberAccent,
                                    contentColor = Color.White
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
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = null,
                        tint = EmberAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Context loaded: Last 7 days of sleep, strain, workouts & journal",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.textSecondary
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
                                CoachLoadingBubble()
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
                    items(QUICK_STARTERS) { starter ->
                        SuggestionChip(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.sendMessage(starter)
                            },
                            label = {
                                Text(starter, style = MaterialTheme.typography.labelSmall)
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = HangryTokens.Spacing.m, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = {
                            Text("Ask coach or share a problem...", style = MaterialTheme.typography.bodyMedium)
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
                        } else null,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Send
                        ),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (inputText.isNotBlank() && !uiState.isLoading) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    val textToSend = inputText
                                    inputText = ""
                                    viewModel.sendMessage(textToSend)
                                }
                            }
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .semantics { contentDescription = "Message input for Hangry AI Coach" },
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
                        enabled = inputText.isNotBlank() && !uiState.isLoading,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                if (inputText.isNotBlank() && !uiState.isLoading) EmberAccent
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = if (uiState.isLoading) "Coach is thinking" else "Send message",
                            tint = if (inputText.isNotBlank() && !uiState.isLoading) Color.White else tokens.textSecondary
                        )
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
            text = { Text("This will clear the conversation messages. Your saved journal entries and memories will be kept.") },
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
            Icon(
                imageVector = Icons.Default.SmartToy,
                contentDescription = null,
                tint = EmberAccent,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(HangryTokens.Spacing.m))
            Text(
                text = "AI Coach Setup",
                style = MaterialTheme.typography.titleLarge,
                color = tokens.textPrimary
            )
            Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))
            val message = when {
                !aiEnabled -> "AI features are disabled in Settings. Enable AI Features to give your coach access to your 7-day health, nutrition, and journal memory."
                !hasApiKey -> "An OpenRouter API key is required to use AI features. Add your API key in Settings."
                else -> "AI Coach is currently not configured."
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.textSecondary
            )
            Spacer(modifier = Modifier.height(HangryTokens.Spacing.l))
            Button(
                onClick = onNavigateToAiSettings,
                colors = ButtonDefaults.buttonColors(containerColor = EmberAccent),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open AI Settings", color = Color.White)
            }
        }
    }
}

@Composable
private fun EmptyConversationView(
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
        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = EmberAccent,
            modifier = Modifier.size(54.dp)
        )
        Spacer(modifier = Modifier.height(HangryTokens.Spacing.m))
        Text(
            text = "Meet your AI Coach",
            style = MaterialTheme.typography.headlineSmall,
            color = tokens.textPrimary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))

        Surface(
            color = EmberAccent.copy(alpha = 0.12f),
            shape = RoundedCornerShape(100.dp)
        ) {
            Text(
                text = "⚡ 7-Day Biometrics & Memories Active",
                style = MaterialTheme.typography.labelMedium,
                color = EmberAccent,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        Spacer(modifier = Modifier.height(HangryTokens.Spacing.m))
        Text(
            text = "I continuously synthesize your sleep, recovery, workouts, nutrition, posture, and personal health journal. Ask me anything or share a new ache, goal, or problem.",
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.textSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 22.sp,
            modifier = Modifier.padding(horizontal = HangryTokens.Spacing.m)
        )
        Spacer(modifier = Modifier.height(HangryTokens.Spacing.l))

        Text(
            text = "Quick Starters:",
            style = MaterialTheme.typography.titleSmall,
            color = tokens.textPrimary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.Start)
        )
        Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))
        QUICK_STARTERS.forEach { starter ->
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
    dotColor: Color = EmberAccent,
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
    val baseColor = if (isUser) Color.White else tokens.textPrimary

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
                        color = if (isUser) Color.White else tokens.chartColors.activeCalories
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
        lineHeight = 22.sp,
        modifier = modifier
    )
}

@Composable
private fun CoachMessageItem(
    message: CoachMessageEntity,
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
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = EmberAccent,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Hangry Coach",
                    style = MaterialTheme.typography.labelSmall,
                    color = EmberAccent,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Surface(
            color = if (isUser) EmberAccent else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            modifier = Modifier.widthIn(max = 330.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
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
                PulsingDotsIndicator(dotColor = EmberAccent, dotSize = 7.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Analyzing your 7-day health trends…",
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
            Column {
                Text("Personal Journal & Memories", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Problems, injuries, and details remembered by your AI Coach",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary
                )
            }
            IconButton(onClick = onAddEntry) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Journal Entry", tint = EmberAccent)
            }
        }

        Spacer(modifier = Modifier.height(HangryTokens.Spacing.m))

        if (journalEntries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No journal memories yet. Tell your AI Coach about any problems, symptoms, or food reactions in chat, and they will be remembered here!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.textSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
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
        "PROBLEM" -> Color(0xFFE53935)
        "INJURY" -> Color(0xFFFB8C00)
        "DIET" -> Color(0xFF43A047)
        "GOAL" -> Color(0xFF1E88E5)
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
                    placeholder = { Text("e.g. Experienced sharp pain in right knee during heavy sets.") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(category, summary, content) },
                enabled = summary.isNotBlank() && content.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = EmberAccent)
            ) {
                Text("Save to Journal", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
