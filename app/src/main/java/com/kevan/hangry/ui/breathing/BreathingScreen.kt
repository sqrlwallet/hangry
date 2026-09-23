package com.kevan.hangry.ui.breathing

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
    HangryInfoSection(
        "Following the cues",
        "A short beep marks the start of each step - breathe in, hold, or breathe out - and a double beep means the session is done. Watch the circle to learn the rhythm, then put on headphones, close your eyes and follow the sounds - you can lock the screen and the session keeps going."
    ),
    HangryInfoSection(
        "Slow breathing & HRV",
        "Breathing at around 5-6 breaths per minute lines your breathing up with your heart rate rhythm (resonance), which reliably raises heart rate variability and shifts you toward parasympathetic recovery."
    ),
    HangryInfoSection(
        "Box breathing",
        "Equal 4-second inhale, hold, exhale and hold. The holds make it a good tool for regaining focus under stress."
    ),
    HangryInfoSection(
        "Health Connect",
        "Sessions of 30 seconds or longer are saved on your device and, once you allow it, logged to Health Connect as a breathing (mindfulness) session so other health apps can see them."
    ),
    HangryInfoSection(
        "Stay comfortable",
        "Breathe through your nose if you can and never strain. If you feel light-headed, stop and breathe normally."
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BreathingScreen(
    viewModel: BreathingViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val setup by viewModel.setup.collectAsState()
    val session by viewModel.session.collectAsState()
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
    val isActive = session is BreathingSessionState.Active
    DisposableEffect(isActive) {
        view.keepScreenOn = isActive
        onDispose { view.keepScreenOn = false }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Breathing") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    HangryInfoIconButton(title = "About Breathing Exercises", sections = BREATHING_INFO_SECTIONS)
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
            when (val current = session) {
                is BreathingSessionState.Active -> ActiveSession(
                    state = current,
                    onPause = viewModel::pause,
                    onResume = viewModel::resume,
                    onStop = viewModel::stop,
                    onToggleSound = { viewModel.setSoundEnabled(!current.soundEnabled) }
                )

                is BreathingSessionState.Finished -> SessionSummary(
                    state = current,
                    healthConnect = setup.healthConnect,
                    onConnectHealth = requestHealthConnect,
                    onDone = viewModel::dismissSummary
                )

                BreathingSessionState.Idle -> SessionSetup(
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
        StatTile(value = "$minutesToday", label = "min today", modifier = Modifier.weight(1f))
        StatTile(value = "$minutesThisWeek", label = "min last 7 days", modifier = Modifier.weight(1f))
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Pattern", style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
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

    Text("Duration", style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
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
        text = "minutes · ${setup.pattern.sessionSecondsFor(setup.minutes) / setup.pattern.cycleSeconds} breaths",
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
                Text("Audio cues", style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                HangryInfoTip(
                    title = "Audio cues",
                    body = "A beep plays each time you switch between breathing in, holding and breathing out. " +
                        "You can lock your screen - the audio cues keep playing."
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

    PrimaryButton(text = "Start ${setup.minutes}-minute session", icon = Icons.Default.PlayArrow, onClick = onStart)

    if (recent.isNotEmpty()) {
        Text("Recent sessions", style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
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
            Text("Health Connect", style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
            Text(
                text = when (status) {
                    HealthConnectLogStatus.CHECKING -> "Checking access…"
                    HealthConnectLogStatus.UNAVAILABLE -> "Not available - sessions are saved on this device"
                    HealthConnectLogStatus.NEEDS_PERMISSION -> "Allow access to log sessions as breathing activity"
                    HealthConnectLogStatus.CONNECTED -> "Sessions are logged automatically"
                },
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textSecondary
            )
        }
        if (status == HealthConnectLogStatus.NEEDS_PERMISSION) {
            TextButton(onClick = onConnect) { Text("Allow") }
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
                text = if (session.healthConnectSynced) "Health Connect" else "On device",
                style = MaterialTheme.typography.labelSmall,
                color = tokens.textMuted
            )
        }
    }
}

// endregion

// region Active session

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
            text = if (state.isPaused) "Paused" else phaseType.label,
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
                text = "Breath ${position.cycleIndex + 1} of ${state.targetSeconds / state.pattern.cycleSeconds}",
                style = MaterialTheme.typography.labelMedium,
                color = tokens.textSecondary
            )
            Text(
                text = "${formatClock(state.remainingSeconds)} left",
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
                contentDescription = if (state.soundEnabled) "Mute cues" else "Unmute cues"
            )
        }
        FilledIconButton(
            onClick = if (state.isPaused) onResume else onPause,
            modifier = Modifier.size(72.dp),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = accent)
        ) {
            Icon(
                imageVector = if (state.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                contentDescription = if (state.isPaused) "Resume" else "Pause",
                modifier = Modifier.size(32.dp)
            )
        }
        FilledTonalIconButton(onClick = onStop, modifier = Modifier.size(52.dp)) {
            Icon(imageVector = Icons.Default.Stop, contentDescription = "End session")
        }
    }

    Text(
        text = "Safe to lock your screen",
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
            text = if (state.completed) "Session complete" else "Session ended",
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
        StatTile(value = formatDuration(state.durationSeconds), label = "breathing", modifier = Modifier.weight(1f))
        StatTile(value = "${state.cycles}", label = "breaths", modifier = Modifier.weight(1f))
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
                    state.saved == null -> "Under 30 seconds, so this one wasn't logged."
                    state.saved == false -> "Saving…"
                    synced -> "Saved and logged to Health Connect."
                    healthConnect == HealthConnectLogStatus.NEEDS_PERMISSION ->
                        "Saved on this device only."
                    else -> "Saved on this device."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.textPrimary,
                modifier = Modifier.weight(1f)
            )
            if (state.saved == true && !synced && healthConnect == HealthConnectLogStatus.NEEDS_PERMISSION) {
                TextButton(onClick = onConnectHealth) { Text("Allow") }
            }
        }
    }

    PrimaryButton(text = "Done", icon = Icons.Default.CheckCircle, onClick = onDone)
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
fun BreathingPattern.cadenceLabel(): String = phases.joinToString("-") { "${it.seconds}" } + " sec"

private fun formatClock(totalSeconds: Int): String =
    String.format(Locale.US, "%d:%02d", totalSeconds / 60, totalSeconds % 60)

private fun formatDuration(totalSeconds: Int): String = when {
    totalSeconds < 60 -> "${totalSeconds}s"
    totalSeconds % 60 == 0 -> "${totalSeconds / 60} min"
    else -> "${totalSeconds / 60}m ${totalSeconds % 60}s"
}
