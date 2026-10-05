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
import com.kevan.hangry.ui.coach.DashSpinner
import com.kevan.hangry.ui.components.HangryCard
import com.kevan.hangry.ui.components.HangryInfoTip
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.kevan.hangry.R

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
    var showCoachModelDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
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
                        text = stringResource(R.string.settings_ai_enable),
                        style = MaterialTheme.typography.titleSmall,
                        color = tokens.textPrimary
                    )
                    HangryInfoTip(
                        title = stringResource(R.string.settings_ai_features),
                        body = stringResource(R.string.settings_ai_features_info)
                    )
                }
                Text(
                    text = stringResource(R.string.settings_ai_uses_own_key),
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
                title = stringResource(R.string.settings_ai_openrouter_api_key),
                subtitle = if (secureKeyStore.getApiKey() != null) stringResource(R.string.settings_ai_key_saved) else stringResource(R.string.settings_ai_key_not_set),
                onClick = { showApiKeyDialog = true }
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = tokens.cardBorder)
            SettingsActionRow(
                icon = Icons.Default.SmartToy,
                title = stringResource(R.string.settings_ai_model),
                subtitle = AiDefaults.analysisModel(profile?.preferredAiModel),
                onClick = { showModelDialog = true }
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = tokens.cardBorder)
            SettingsActionRow(
                icon = Icons.Default.SmartToy,
                title = stringResource(R.string.settings_ai_dash_model),
                subtitle = profile?.preferredCoachModel?.takeIf { it.isNotBlank() } ?: AiDefaults.COACH_MODEL,
                onClick = { showCoachModelDialog = true }
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
        val apiKeySavedMessage = stringResource(R.string.settings_ai_api_key_saved)
        ApiKeyDialog(
            secureKeyStore = secureKeyStore,
            openRouterClient = openRouterClient,
            preferredModel = AiDefaults.analysisModel(profile?.preferredAiModel),
            onDismiss = { showApiKeyDialog = false },
            onSaved = {
                showApiKeyDialog = false
                coroutineScope.launch { snackbarHostState.showSnackbar(apiKeySavedMessage) }
            }
        )
    }

    if (showModelDialog) {
        ModelDialog(
            title = stringResource(R.string.settings_ai_model),
            description = stringResource(R.string.settings_ai_model_description),
            currentModel = AiDefaults.analysisModel(profile?.preferredAiModel),
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

    if (showCoachModelDialog) {
        ModelDialog(
            title = stringResource(R.string.settings_ai_dash_model),
            description = stringResource(R.string.settings_ai_dash_model_description),
            currentModel = profile?.preferredCoachModel?.takeIf { it.isNotBlank() } ?: AiDefaults.COACH_MODEL,
            onDismiss = { showCoachModelDialog = false },
            onSave = { newModel ->
                coroutineScope.launch {
                    val current = profile ?: com.kevan.hangry.data.local.entity.UserProfileEntity()
                    userProfileRepository.saveProfile(current.copy(preferredCoachModel = newModel))
                    showCoachModelDialog = false
                }
            }
        )
    }
}

@Composable
private fun AiConsentDialog(onDismiss: () -> Unit, onAccept: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_ai_enable)) },
        text = {
            Text(
                stringResource(R.string.settings_ai_consent_body)
            )
        },
        confirmButton = {
            Button(onClick = onAccept) { Text(stringResource(R.string.settings_ai_consent_accept)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
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
    val connectionWorksMessage = stringResource(R.string.settings_ai_connection_works)
    val couldNotVerifyMessage = stringResource(R.string.settings_ai_could_not_verify)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_ai_openrouter_api_key)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.settings_ai_get_key_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary
                )
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it; testResult = null },
                    label = { Text(stringResource(R.string.settings_ai_api_key_label)) },
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
                                onSuccess = { connectionWorksMessage },
                                onFailure = { e -> (e as? OpenRouterException)?.message ?: couldNotVerifyMessage }
                            )
                            isTesting = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isTesting) {
                        DashSpinner(size = 24.dp, contentDescription = stringResource(R.string.settings_ai_testing_connection))
                    } else {
                        Text(stringResource(R.string.settings_ai_test_connection))
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
            ) { Text(stringResource(R.string.settings_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun ModelDialog(
    title: String,
    description: String,
    currentModel: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    val tokens = LocalHangryTokens.current
    var modelInput by remember { mutableStateOf(currentModel) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary
                )
                OutlinedTextField(
                    value = modelInput,
                    onValueChange = { modelInput = it },
                    label = { Text(stringResource(R.string.settings_ai_model_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = modelInput.isNotBlank(),
                onClick = { onSave(modelInput.trim()) }
            ) { Text(stringResource(R.string.settings_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
