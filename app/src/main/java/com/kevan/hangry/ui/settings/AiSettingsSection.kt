package com.kevan.hangry.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kevan.hangry.data.ai.OpenRouterClient
import com.kevan.hangry.data.ai.OpenRouterException
import com.kevan.hangry.data.security.SecureKeyStore
import com.kevan.hangry.domain.ai.AiDefaults
import com.kevan.hangry.domain.repository.UserProfileRepository
import com.kevan.hangry.ui.components.HangryCard
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Settings > AI Features. Off by default - the toggle only flips on after the consent dialog
 * is explicitly accepted, and the key/model rows stay inert until then. Nothing here ever
 * calls OpenRouter except the explicit "Test Connection" button and the two feature screens'
 * own explicit Analyze actions.
 */
@Composable
fun AiFeaturesSection(
    userProfileRepository: UserProfileRepository,
    secureKeyStore: SecureKeyStore,
    openRouterClient: OpenRouterClient,
    coroutineScope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    val tokens = LocalHangryTokens.current
    val profile by userProfileRepository.getProfile().collectAsState(initial = null)
    val aiEnabled = profile?.aiFeaturesEnabled ?: false

    var showConsentDialog by remember { mutableStateOf(false) }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var showModelDialog by remember { mutableStateOf(false) }

    Text(text = "AI Features", style = MaterialTheme.typography.titleLarge, color = tokens.textPrimary, modifier = modifier)
    HangryCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = null,
                        tint = tokens.textSecondary,
                        modifier = Modifier.height(20.dp)
                    )
                    Spacer(modifier = Modifier.width(HangryTokens.Spacing.s))
                    Text(
                        text = "Enable AI Features",
                        style = MaterialTheme.typography.titleSmall,
                        color = tokens.textPrimary
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Off by default. Sends food/posture photos to OpenRouter using your own key.",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary
                )
            }
            Switch(
                checked = aiEnabled,
                onCheckedChange = { turningOn ->
                    if (turningOn) {
                        showConsentDialog = true
                    } else {
                        coroutineScope.launch {
                            val current = profile ?: com.kevan.hangry.data.local.entity.UserProfileEntity()
                            userProfileRepository.saveProfile(current.copy(aiFeaturesEnabled = false))
                        }
                    }
                }
            )
        }

        if (aiEnabled) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = tokens.cardBorder)
            SettingsActionRow(
                icon = Icons.Default.SmartToy,
                title = "OpenRouter API Key",
                subtitle = if (secureKeyStore.getApiKey() != null) "Key saved - tap to change" else "Not set - AI features won't work yet",
                onClick = { showApiKeyDialog = true }
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = tokens.cardBorder)
            SettingsActionRow(
                icon = Icons.Default.SmartToy,
                title = "AI Model",
                subtitle = profile?.preferredAiModel?.takeIf { it.isNotBlank() } ?: AiDefaults.DEFAULT_MODEL,
                onClick = { showModelDialog = true }
            )
        }
    }

    if (showConsentDialog) {
        AiConsentDialog(
            onDismiss = { showConsentDialog = false },
            onAccept = {
                coroutineScope.launch {
                    val current = profile ?: com.kevan.hangry.data.local.entity.UserProfileEntity()
                    userProfileRepository.saveProfile(current.copy(aiFeaturesEnabled = true))
                    showConsentDialog = false
                }
            }
        )
    }

    if (showApiKeyDialog) {
        ApiKeyDialog(
            secureKeyStore = secureKeyStore,
            openRouterClient = openRouterClient,
            preferredModel = profile?.preferredAiModel?.takeIf { it.isNotBlank() } ?: AiDefaults.DEFAULT_MODEL,
            onDismiss = { showApiKeyDialog = false },
            onSaved = {
                showApiKeyDialog = false
                coroutineScope.launch { snackbarHostState.showSnackbar("API key saved.") }
            }
        )
    }

    if (showModelDialog) {
        ModelDialog(
            currentModel = profile?.preferredAiModel?.takeIf { it.isNotBlank() } ?: AiDefaults.DEFAULT_MODEL,
            onDismiss = { showModelDialog = false },
            onSave = { newModel ->
                coroutineScope.launch {
                    val current = profile ?: com.kevan.hangry.data.local.entity.UserProfileEntity()
                    userProfileRepository.saveProfile(current.copy(preferredAiModel = newModel))
                    showModelDialog = false
                }
            }
        )
    }
}

@Composable
private fun AiConsentDialog(onDismiss: () -> Unit, onAccept: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enable AI Features") },
        text = {
            Text(
                "When enabled, photos and text you submit for analysis are sent directly from " +
                    "your device to OpenRouter and the model you choose, using your own API key. " +
                    "Hangry has no server and never sees this data. Nothing is sent unless you " +
                    "tap Analyze. You can disable this anytime."
            )
        },
        confirmButton = {
            Button(onClick = onAccept) { Text("I Understand, Enable") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ApiKeyDialog(
    secureKeyStore: SecureKeyStore,
    openRouterClient: OpenRouterClient,
    preferredModel: String,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val tokens = LocalHangryTokens.current
    val coroutineScope = rememberCoroutineScope()
    var keyInput by remember { mutableStateOf(secureKeyStore.getApiKey() ?: "") }
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("OpenRouter API Key") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Get a key at openrouter.ai - it's yours, Hangry never stores it anywhere but this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary
                )
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it; testResult = null },
                    label = { Text("API Key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                testResult?.let {
                    Text(text = it, style = MaterialTheme.typography.labelSmall, color = tokens.textSecondary)
                }
                OutlinedButton(
                    enabled = keyInput.isNotBlank() && !isTesting,
                    onClick = {
                        isTesting = true
                        testResult = null
                        coroutineScope.launch {
                            val result = openRouterClient.chatCompletion(
                                apiKey = keyInput.trim(),
                                model = preferredModel,
                                systemPrompt = "Reply with exactly one word: OK",
                                userText = "Respond with OK to confirm this connection works."
                            )
                            testResult = result.fold(
                                onSuccess = { "Connection works." },
                                onFailure = { e -> (e as? OpenRouterException)?.message ?: "Couldn't verify the key." }
                            )
                            isTesting = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(modifier = Modifier.height(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Test Connection")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = keyInput.isNotBlank(),
                onClick = {
                    secureKeyStore.setApiKey(keyInput)
                    onSaved()
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ModelDialog(currentModel: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val tokens = LocalHangryTokens.current
    var modelInput by remember { mutableStateOf(currentModel) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AI Model") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "The OpenRouter model slug used for food and posture photo analysis.",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary
                )
                OutlinedTextField(
                    value = modelInput,
                    onValueChange = { modelInput = it },
                    label = { Text("Model") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = modelInput.isNotBlank(),
                onClick = { onSave(modelInput.trim()) }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
