package com.kevan.hangry.ui.breathing

import com.kevan.hangry.R
import androidx.compose.ui.res.stringResource
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import com.kevan.hangry.data.breathing.BreathingSessionState
import com.kevan.hangry.data.local.entity.BreathingSessionEntity
import com.kevan.hangry.domain.model.BreathPhaseType
import com.kevan.hangry.domain.model.BreathingPattern
import com.kevan.hangry.ui.coach.DashExpression
import com.kevan.hangry.ui.coach.DashMood
import com.kevan.hangry.ui.components.HangryCard
import com.kevan.hangry.ui.components.HangryInfoIconButton
import com.kevan.hangry.ui.components.HangryInfoSection
import com.kevan.hangry.ui.components.HangryInfoTip
import com.kevan.hangry.ui.theme.CtaGradient
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val BREATHING_INFO_SECTIONS = listOf(
    R.string.coach_breathing_info_cues_title to R.string.coach_breathing_info_cues_body,
    R.string.coach_breathing_info_hrv_title to R.string.coach_breathing_info_hrv_body,
    R.string.coach_breathing_info_box_title to R.string.coach_breathing_info_box_body,
    R.string.coach_breathing_info_hc_title to R.string.coach_breathing_info_hc_body,
    R.string.coach_breathing_info_comfort_title to R.string.coach_breathing_info_comfort_body
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BreathingScreen(
    viewModel: BreathingViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val setup by viewModel.setup.collectAsState()
    // The session ticks every 50 ms. Only the active-session area reads it directly; the rest of
    // the screen follows just which kind of session it is, so it isn't recomposed on every tick.
    val sessionState = viewModel.session.collectAsState()
    val sessionKind by remember {
        derivedStateOf {
            when (sessionState.value) {
                is BreathingSessionState.Active -> SessionKind.ACTIVE
                is BreathingSessionState.Finished -> SessionKind.FINISHED
                BreathingSessionState.Idle -> SessionKind.IDLE
            }
        }
    }
    val stats by viewModel.stats.collectAsState()
    val recent by viewModel.recentSessions.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { viewModel.onPermissionResult() }
    // The ongoing notification (phase + End button) needs this on Android 13+. The session runs
    // either way, so the result is ignored.
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.start() }
    val context = LocalContext.current
    val startSession = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.start()
        }
    }
    val requestHealthConnect = {
        if (setup.permissionsToRequest.isNotEmpty()) permissionLauncher.launch(setup.permissionsToRequest)
    }

    // The breathing guide is meant to be watched; keep the display awake while a session runs.
    val view = LocalView.current
    val isActive = sessionKind == SessionKind.ACTIVE
    DisposableEffect(isActive) {
        view.keepScreenOn = isActive
        onDispose { view.keepScreenOn = false }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.coach_breathing_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.coach_back))
                    }
                },
                actions = {
                    HangryInfoIconButton(
                        title = stringResource(R.string.coach_breathing_about),
                        sections = BREATHING_INFO_SECTIONS.map { (title, body) -> HangryInfoSection(stringResource(title), stringResource(body)) }
                    )
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = HangryTokens.Spacing.m, vertical = HangryTokens.Spacing.s),
            verticalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.m)
        ) {
            when (sessionKind) {
                SessionKind.ACTIVE -> ActiveSessionHost(
                    sessionState = sessionState,
                    onPause = viewModel::pause,
                    onResume = viewModel::resume,
                    onStop = viewModel::stop,
                    onToggleSound = { viewModel.setSoundEnabled(!it) }
                )


                SessionKind.FINISHED -> (sessionState.value as? BreathingSessionState.Finished)?.let { current -> SessionSummary(
                    state = current,
                    healthConnect = setup.healthConnect,
                    onConnectHealth = requestHealthConnect,
                    onDone = viewModel::dismissSummary
                ) }

                SessionKind.IDLE -> SessionSetup(
                    setup = setup,
                    minutesToday = stats.minutesToday,
                    minutesThisWeek = stats.minutesThisWeek,
                    recent = recent,
                    onSelectPattern = viewModel::selectPattern,
                    onSelectMinutes = viewModel::selectMinutes,
                    onToggleSound = viewModel::setSoundEnabled,
                    onConnectHealth = requestHealthConnect,
                    onStart = startSession
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

// region Setup

@Composable
private fun SessionSetup(
    setup: BreathingSetupState,
    minutesToday: Int,
    minutesThisWeek: Int,
    recent: List<BreathingSessionEntity>,
    onSelectPattern: (BreathingPattern) -> Unit,
    onSelectMinutes: (Int) -> Unit,
    onToggleSound: (Boolean) -> Unit,
    onConnectHealth: () -> Unit,
    onStart: () -> Unit
) {
    val tokens = LocalHangryTokens.current

    Row(horizontalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.m)) {
        StatTile(value = "$minutesToday", label = stringResource(R.string.coach_breathing_min_today), modifier = Modifier.weight(1f))
        StatTile(value = "$minutesThisWeek", label = stringResource(R.string.coach_breathing_min_last_7_days), modifier = Modifier.weight(1f))
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.coach_breathing_pattern), style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
        HangryInfoTip(title = setup.pattern.title, body = setup.pattern.description)
    }
    BreathingPattern.entries.chunked(2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.s)) {
            row.forEach { pattern ->
                PatternOption(
                    pattern = pattern,
                    selected = pattern == setup.pattern,
                    onClick = { onSelectPattern(pattern) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    Text(stringResource(R.string.coach_breathing_duration), style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        BreathingPattern.DURATION_OPTIONS_MINUTES.forEach { minutes ->
            FilterChip(
                selected = setup.minutes == minutes,
                onClick = { onSelectMinutes(minutes) },
                label = {
                    Text(
                        text = "$minutes",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
    Text(
        text = stringResource(R.string.coach_breathing_minutes_breaths, setup.pattern.sessionSecondsFor(setup.minutes) / setup.pattern.cycleSeconds),
        style = MaterialTheme.typography.labelSmall,
        color = tokens.textMuted
    )

    HangryCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Headphones,
                contentDescription = null,
                tint = tokens.chartColors.sleep,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.coach_breathing_audio_cues), style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                HangryInfoTip(
                    title = stringResource(R.string.coach_breathing_audio_cues),
                    body = stringResource(R.string.coach_breathing_audio_cues_body)
                )
            }
            Switch(checked = setup.soundEnabled, onCheckedChange = onToggleSound)
        }
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 12.dp),
            color = tokens.cardBorder.copy(alpha = 0.5f)
        )
        HealthConnectRow(status = setup.healthConnect, onConnect = onConnectHealth)
    }

    PrimaryButton(text = stringResource(R.string.coach_breathing_start_session, setup.minutes), icon = Icons.Default.PlayArrow, onClick = onStart)

    if (recent.isNotEmpty()) {
        Text(stringResource(R.string.coach_recent_sessions), style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
        HangryCard(modifier = Modifier.fillMaxWidth(), contentPadding = 12.dp) {
            recent.forEachIndexed { index, session ->
                RecentSessionRow(session)
                if (index != recent.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = tokens.cardBorder.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    val tokens = LocalHangryTokens.current
    HangryCard(modifier = modifier, contentPadding = 12.dp) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = tokens.textPrimary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
    }
}

@Composable
private fun PatternOption(
    pattern: BreathingPattern,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalHangryTokens.current
    val accent = tokens.chartColors.sleep
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(HangryTokens.CornerRadii.medium),
        color = if (selected) accent.copy(alpha = 0.12f) else tokens.cardBackground,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) accent else tokens.cardBorder.copy(alpha = 0.6f)
        ),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = pattern.shortLabel,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (selected) accent else tokens.textPrimary
            )
            Text(
                text = pattern.title,
                style = MaterialTheme.typography.labelMedium,
                color = tokens.textSecondary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = pattern.cadenceLabel(),
                style = MaterialTheme.typography.labelSmall,
                color = tokens.textMuted
            )
        }
    }
}

@Composable
private fun HealthConnectRow(status: HealthConnectLogStatus, onConnect: () -> Unit) {
    val tokens = LocalHangryTokens.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = when (status) {
                HealthConnectLogStatus.CONNECTED -> Icons.Default.CheckCircle
                HealthConnectLogStatus.UNAVAILABLE -> Icons.Default.CloudOff
                else -> Icons.Default.Favorite
            },
            contentDescription = null,
            tint = if (status == HealthConnectLogStatus.CONNECTED) tokens.scoreColors.primed else tokens.textSecondary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.coach_breathing_health_connect), style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
            Text(
                text = when (status) {
                    HealthConnectLogStatus.CHECKING -> stringResource(R.string.coach_breathing_hc_checking)
                    HealthConnectLogStatus.UNAVAILABLE -> stringResource(R.string.coach_breathing_hc_unavailable)
                    HealthConnectLogStatus.NEEDS_PERMISSION -> stringResource(R.string.coach_breathing_hc_needs_permission)
                    HealthConnectLogStatus.CONNECTED -> stringResource(R.string.coach_breathing_hc_connected)
                },
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textSecondary
            )
        }
        if (status == HealthConnectLogStatus.NEEDS_PERMISSION) {
            TextButton(onClick = onConnect) { Text(stringResource(R.string.coach_breathing_allow)) }
        }
    }
}

@Composable
private fun RecentSessionRow(session: BreathingSessionEntity) {
    val tokens = LocalHangryTokens.current
    val pattern = BreathingPattern.fromId(session.patternId)
    val formatter = remember { DateTimeFormatter.ofPattern("EEE d MMM · h:mm a", Locale.getDefault()) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(pattern.title, style = MaterialTheme.typography.bodyMedium, color = tokens.textPrimary)
            Text(
                text = session.startTime.atZone(ZoneId.systemDefault()).format(formatter),
                style = MaterialTheme.typography.labelSmall,
                color = tokens.textMuted
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = formatDuration(session.durationSeconds),
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.textPrimary
            )
            Text(
                text = if (session.healthConnectSynced) stringResource(R.string.coach_breathing_health_connect) else stringResource(R.string.coach_breathing_on_device),
                style = MaterialTheme.typography.labelSmall,
                color = tokens.textMuted
            )
        }
    }
}

// endregion

// region Active session

private enum class SessionKind { IDLE, ACTIVE, FINISHED }

/** The one place that reads the 50 ms session ticks, so only this subtree recomposes with them. */
@Composable
private fun ActiveSessionHost(
    sessionState: State<BreathingSessionState>,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onToggleSound: (currentlyEnabled: Boolean) -> Unit
) {
    val current = sessionState.value as? BreathingSessionState.Active ?: return
    ActiveSession(
        state = current,
        onPause = onPause,
        onResume = onResume,
        onStop = onStop,
        onToggleSound = { onToggleSound(current.soundEnabled) }
    )
}

@Composable
private fun ActiveSession(
    state: BreathingSessionState.Active,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onToggleSound: () -> Unit
) {
    val tokens = LocalHangryTokens.current
    val accent = tokens.chartColors.sleep
    val position = state.position
    val phaseType = position.phase.type

    // Circle fills while inhaling, stays full on the top hold, empties while exhaling.
    val targetFill = when (phaseType) {
        BreathPhaseType.INHALE -> position.phaseProgress
        BreathPhaseType.HOLD_FULL -> 1f
        BreathPhaseType.EXHALE -> 1f - position.phaseProgress
        BreathPhaseType.HOLD_EMPTY -> 0f
    }
    // Ticks arrive every 50 ms; tween across each so the motion is continuous, not stepped.
    val fill by animateFloatAsState(
        targetValue = targetFill,
        animationSpec = tween(durationMillis = 80, easing = LinearEasing),
        label = "breathFill"
    )
    // Ease the size so the breath visibly slows at the top and bottom, like a real breath.
    val easedFill = fill * fill * (3 - 2 * fill)

    Text(
        text = state.pattern.title,
        style = MaterialTheme.typography.titleMedium,
        color = tokens.textSecondary,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer guide ring + phase progress arc.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 6.dp.toPx()
            drawCircle(color = accent.copy(alpha = 0.15f), style = Stroke(width = stroke))
            drawArc(
                color = accent,
                startAngle = -90f,
                sweepAngle = 360f * position.phaseProgress,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize(0.88f)
                .scale(0.42f + 0.58f * easedFill)
                .background(
                    brush = Brush.radialGradient(
                        listOf(accent.copy(alpha = 0.55f), accent.copy(alpha = 0.18f))
                    ),
                    shape = CircleShape
                )
        )
        DashBreathing(
            phase = phaseType,
            phaseProgress = position.phaseProgress,
            fill = easedFill,
            isPaused = state.isPaused,
            modifier = Modifier.fillMaxSize(0.6f)
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (state.isPaused) stringResource(R.string.coach_breathing_paused) else phaseType.label,
            style = MaterialTheme.typography.headlineSmall,
            color = tokens.textPrimary
        )
        if (!state.isPaused) {
            Spacer(modifier = Modifier.width(HangryTokens.Spacing.m))
            Text(
                text = "${position.phaseSecondsRemaining}",
                style = MaterialTheme.typography.displaySmall,
                color = accent
            )
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = stringResource(R.string.coach_breathing_breath_of, position.cycleIndex + 1, state.targetSeconds / state.pattern.cycleSeconds),
                style = MaterialTheme.typography.labelMedium,
                color = tokens.textSecondary
            )
            Text(
                text = stringResource(R.string.coach_breathing_time_left, formatClock(state.remainingSeconds)),
                style = MaterialTheme.typography.labelMedium,
                color = tokens.textSecondary
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { state.progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            color = accent,
            trackColor = accent.copy(alpha = 0.15f),
            strokeCap = StrokeCap.Round,
            drawStopIndicator = {}
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilledTonalIconButton(onClick = onToggleSound, modifier = Modifier.size(52.dp)) {
            Icon(
                imageVector = if (state.soundEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                contentDescription = if (state.soundEnabled) stringResource(R.string.coach_breathing_mute_cues) else stringResource(R.string.coach_breathing_unmute_cues)
            )
        }
        FilledIconButton(
            onClick = if (state.isPaused) onResume else onPause,
            modifier = Modifier.size(72.dp),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = accent)
        ) {
            Icon(
                imageVector = if (state.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                contentDescription = if (state.isPaused) stringResource(R.string.coach_breathing_resume) else stringResource(R.string.coach_breathing_pause),
                modifier = Modifier.size(32.dp)
            )
        }
        FilledTonalIconButton(onClick = onStop, modifier = Modifier.size(52.dp)) {
            Icon(imageVector = Icons.Default.Stop, contentDescription = stringResource(R.string.coach_breathing_end_session))
        }
    }

    Text(
        text = stringResource(R.string.coach_breathing_safe_to_lock),
        style = MaterialTheme.typography.bodySmall,
        color = tokens.textMuted,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center
    )
}

// endregion

// region Summary

@Composable
private fun SessionSummary(
    state: BreathingSessionState.Finished,
    healthConnect: HealthConnectLogStatus,
    onConnectHealth: () -> Unit,
    onDone: () -> Unit
) {
    val tokens = LocalHangryTokens.current
    val accent = tokens.chartColors.sleep

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = HangryTokens.Spacing.l),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(132.dp)
                .background(accent.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            DashExpression(
                mood = if (state.completed) DashMood.CELEBRATE else DashMood.CHEER,
                size = 116.dp,
                contentDescription = null
            )
        }
        Spacer(modifier = Modifier.height(HangryTokens.Spacing.m))
        Text(
            text = if (state.completed) stringResource(R.string.coach_breathing_session_complete) else stringResource(R.string.coach_breathing_session_ended),
            style = MaterialTheme.typography.headlineSmall,
            color = tokens.textPrimary
        )
        Text(
            text = state.pattern.title,
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.textSecondary
        )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.m)) {
        StatTile(value = formatDuration(state.durationSeconds), label = stringResource(R.string.coach_breathing_label_breathing), modifier = Modifier.weight(1f))
        StatTile(value = "${state.cycles}", label = stringResource(R.string.coach_breathing_label_breaths), modifier = Modifier.weight(1f))
    }

    HangryCard(modifier = Modifier.fillMaxWidth()) {
        val synced = state.healthConnectSynced || healthConnect == HealthConnectLogStatus.CONNECTED
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (state.saved == true && synced) Icons.Default.CheckCircle else Icons.Default.Favorite,
                contentDescription = null,
                tint = if (state.saved == true && synced) tokens.scoreColors.primed else tokens.textSecondary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = when {
                    state.saved == null -> stringResource(R.string.coach_breathing_not_logged)
                    state.saved == false -> stringResource(R.string.coach_breathing_saving)
                    synced -> stringResource(R.string.coach_breathing_saved_synced)
                    healthConnect == HealthConnectLogStatus.NEEDS_PERMISSION ->
                        stringResource(R.string.coach_breathing_saved_device_only)
                    else -> stringResource(R.string.coach_breathing_saved_device)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.textPrimary,
                modifier = Modifier.weight(1f)
            )
            if (state.saved == true && !synced && healthConnect == HealthConnectLogStatus.NEEDS_PERMISSION) {
                TextButton(onClick = onConnectHealth) { Text(stringResource(R.string.coach_breathing_allow)) }
            }
        }
    }

    PrimaryButton(text = stringResource(R.string.coach_done), icon = Icons.Default.CheckCircle, onClick = onDone)
}

// endregion

@Composable
private fun PrimaryButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(26.dp),
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(brush = Brush.horizontalGradient(CtaGradient), shape = RoundedCornerShape(26.dp))
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White
            )
        }
    }
}

/** "4-4-4-4 sec" style summary of one breathing cycle. */
@Composable
fun BreathingPattern.cadenceLabel(): String =
    stringResource(R.string.coach_breathing_cadence, phases.joinToString("-") { "${it.seconds}" })

private fun formatClock(totalSeconds: Int): String =
    String.format(Locale.US, "%d:%02d", totalSeconds / 60, totalSeconds % 60)

@Composable
private fun formatDuration(totalSeconds: Int): String = when {
    totalSeconds < 60 -> stringResource(R.string.coach_breathing_duration_seconds, totalSeconds)
    totalSeconds % 60 == 0 -> stringResource(R.string.coach_breathing_duration_minutes, totalSeconds / 60)
    else -> stringResource(R.string.coach_breathing_duration_minutes_seconds, totalSeconds / 60, totalSeconds % 60)
}
