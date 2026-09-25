package com.kevan.hangry.ui.fasting

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.kevan.hangry.domain.model.Fast
import com.kevan.hangry.domain.model.FastingMath
import com.kevan.hangry.domain.model.FastingPlan
import com.kevan.hangry.domain.model.FastingSnapshot
import com.kevan.hangry.domain.model.FastingStage
import com.kevan.hangry.ui.coach.DashExpression
import com.kevan.hangry.ui.coach.DashMood
import com.kevan.hangry.ui.components.HangryCard
import com.kevan.hangry.ui.components.HangryInfoIconButton
import com.kevan.hangry.ui.components.HangryInfoSection
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The fasting ring and accents; reads on both light and dark backgrounds. */
internal val FastingColor = Color(0xFF9C7CE0)

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
private val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())

private val FASTING_INFO = listOf(
    HangryInfoSection(
        "How it works",
        "Pick a plan, tap Start fast when you finish eating and End fast at your next meal. Hangry counts the hours, tells you when you hit your goal, and keeps a streak of the days you reach it."
    ),
    HangryInfoSection(
        "Plans",
        "16:8 means 16 hours fasting and an 8-hour eating window. 13:11 and 14:10 are gentle places to start; 18:6, 20:4 and one meal a day are harder. Change plan any time - a running fast takes the new goal."
    ),
    HangryInfoSection(
        "Stages",
        "The stages (fat burning, ketosis...) are rough guides from common fasting apps. The hours vary a lot between people with diet, activity and metabolism."
    ),
    HangryInfoSection(
        "Is it for you?",
        "Fasting isn't advised if you're pregnant or breastfeeding, under 18, have a history of eating disorders, or take medication for diabetes or blood pressure - check with your doctor first. Stop if you feel unwell."
    ),
    HangryInfoSection(
        "Turning it off",
        "Fasting is optional. Turn it off at the bottom of this screen: the timer, reminder and streak stop, and your history is kept in case you come back."
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FastingScreen(
    viewModel: FastingViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val snapshot by viewModel.snapshot.collectAsState()
    val isPregnant by viewModel.isPregnant.collectAsState()
    val context = LocalContext.current

    // The goal reminder needs notification permission on Android 13+; fasting works either way.
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val askForNotifications = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fasting") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = { HangryInfoIconButton(title = "About Fasting", sections = FASTING_INFO) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = HangryTokens.Spacing.m, vertical = HangryTokens.Spacing.s)
                .padding(bottom = HangryTokens.Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.m)
        ) {
            val s = snapshot ?: return@Column
            if (!s.enabled) {
                IntroCard(
                    snapshot = s,
                    isPregnant = isPregnant,
                    onPickPlan = viewModel::setPlan,
                    onTurnOn = {
                        viewModel.setEnabled(true)
                        if (s.goalReminder) askForNotifications()
                    }
                )
            } else {
                if (isPregnant) PregnancyWarning()
                TimerCard(
                    snapshot = s,
                    onStart = viewModel::start,
                    onStartAt = viewModel::startAt,
                    onEnd = viewModel::end,
                    onEditStart = viewModel::editStart
                )
                PlanCard(snapshot = s, onPickPlan = viewModel::setPlan)
                StatsCard(snapshot = s)
                if (s.history.isNotEmpty()) HistoryCard(history = s.history, onDelete = viewModel::delete)
                SettingsCard(
                    snapshot = s,
                    onGoalReminder = { on ->
                        viewModel.setGoalReminder(on)
                        if (on) askForNotifications()
                    },
                    onTurnOff = { viewModel.setEnabled(false) }
                )
            }
            Text(
                "For your own tracking - not medical advice.",
                style = MaterialTheme.typography.labelSmall,
                color = LocalHangryTokens.current.textMuted
            )
        }
    }
}

/** A clock that ticks every [periodMs] so running timers stay current on screen. */
@Composable
internal fun rememberNow(periodMs: Long = 1_000): Instant {
    val now by produceState(Instant.now()) {
        while (true) {
            delay(periodMs)
            value = Instant.now()
        }
    }
    return now
}

internal fun formatClock(d: Duration): String {
    val secs = d.seconds.coerceAtLeast(0)
    return String.format(Locale.US, "%d:%02d:%02d", secs / 3600, (secs % 3600) / 60, secs % 60)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IntroCard(
    snapshot: FastingSnapshot,
    isPregnant: Boolean,
    onPickPlan: (FastingPlan, Int?) -> Unit,
    onTurnOn: () -> Unit
) {
    val tokens = LocalHangryTokens.current
    HangryCard(modifier = Modifier.fillMaxWidth()) {
        DashExpression(mood = DashMood.FASTING, size = 96.dp, contentDescription = null)
        Spacer(Modifier.height(8.dp))
        Text("Intermittent fasting", style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
        Text(
            "An optional timer for eating windows like 16:8. Start a fast when you finish eating, get a nudge when you hit your goal, and build a streak. Turn it off any time.",
            style = MaterialTheme.typography.bodySmall,
            color = tokens.textSecondary
        )
        Spacer(Modifier.height(12.dp))
        Text("Choose a plan", style = MaterialTheme.typography.labelLarge, color = tokens.textPrimary)
        Spacer(Modifier.height(4.dp))
        PlanChips(snapshot, onPickPlan)
        Spacer(Modifier.height(12.dp))
        if (isPregnant) {
            PregnancyWarning()
            Spacer(Modifier.height(12.dp))
        }
        Surface(color = tokens.brandAccentContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Text(
                "Skip fasting if you're pregnant or breastfeeding, under 18, have had an eating disorder, or take diabetes or blood pressure medication - ask your doctor first.",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textPrimary,
                modifier = Modifier.padding(12.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onTurnOn, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Text("Turn on fasting")
        }
    }
}

@Composable
private fun PregnancyWarning() {
    val tokens = LocalHangryTokens.current
    Surface(color = tokens.scoreColors.rebuildContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.WarningAmber, contentDescription = null, tint = tokens.scoreColors.rebuild, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "Your Health Records say you're pregnant. Fasting isn't recommended during pregnancy - please talk to your midwife or doctor first.",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textPrimary
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlanChips(snapshot: FastingSnapshot, onPickPlan: (FastingPlan, Int?) -> Unit) {
    val tokens = LocalHangryTokens.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FastingPlan.entries.forEach { plan ->
            FilterChip(
                selected = snapshot.plan == plan,
                onClick = { onPickPlan(plan, null) },
                label = { Text(plan.label) }
            )
        }
    }
    Text(
        if (snapshot.plan == FastingPlan.CUSTOM) "${snapshot.targetHours}h fast" else snapshot.plan.blurb,
        style = MaterialTheme.typography.bodySmall,
        color = tokens.textSecondary
    )
    if (snapshot.plan == FastingPlan.CUSTOM) {
        var hours by remember(snapshot.targetHours) { mutableFloatStateOf(snapshot.targetHours.toFloat()) }
        Slider(
            value = hours,
            onValueChange = { hours = it },
            onValueChangeFinished = { onPickPlan(FastingPlan.CUSTOM, hours.toInt()) },
            valueRange = FastingPlan.MIN_CUSTOM_HOURS.toFloat()..FastingPlan.MAX_CUSTOM_HOURS.toFloat(),
            steps = FastingPlan.MAX_CUSTOM_HOURS - FastingPlan.MIN_CUSTOM_HOURS - 1
        )
        Text("${hours.toInt()} hours", style = MaterialTheme.typography.labelMedium, color = tokens.textPrimary)
    }
}

@Composable
private fun TimerCard(
    snapshot: FastingSnapshot,
    onStart: () -> Unit,
    onStartAt: (LocalTime) -> Unit,
    onEnd: () -> Unit,
    onEditStart: (LocalTime) -> Unit
) {
    val tokens = LocalHangryTokens.current
    val now = rememberNow()
    val active = snapshot.active
    var picker by remember { mutableStateOf<TimePick?>(null) }
    var confirmEnd by remember { mutableStateOf(false) }

    HangryCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
                val track = tokens.cardBorder
                val progress = active?.progress(now) ?: 0f
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 16.dp.toPx()
                    val arc = Size(size.width - stroke, size.height - stroke)
                    val topLeft = Offset(stroke / 2, stroke / 2)
                    drawArc(track, 0f, 360f, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round))
                    if (progress > 0f) {
                        drawArc(FastingColor, -90f, 360f * progress, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round))
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (active != null) {
                        val reached = active.reachedGoal(now)
                        Text(if (reached) "Goal reached!" else "Fasting", style = MaterialTheme.typography.labelLarge, color = if (reached) tokens.scoreColors.primed else tokens.textSecondary)
                        Text(formatClock(active.elapsed(now)), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = tokens.textPrimary)
                        Text(
                            if (reached) "+" + FastingMath.formatDuration(Duration.between(active.goalAt(), now)) + " past ${active.targetMinutes / 60}h"
                            else FastingMath.formatDuration(Duration.between(now, active.goalAt())) + " to go",
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.textSecondary
                        )
                    } else {
                        Text("Eating window", style = MaterialTheme.typography.labelLarge, color = tokens.textSecondary)
                        val sinceLast = snapshot.lastFinished?.endAt?.let { Duration.between(it, now) }
                        Text(
                            sinceLast?.let { formatClock(it) } ?: "${snapshot.targetHours}h",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = tokens.textPrimary
                        )
                        Text(
                            if (sinceLast != null) "since your last fast" else "${snapshot.plan.label} fast",
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.textSecondary
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            if (active != null) {
                val zone = ZoneId.systemDefault()
                if (active.reachedGoal(now)) DashExpression(mood = DashMood.FASTING_DONE, size = 72.dp, contentDescription = null)
                Text(
                    "Started ${active.startAt.atZone(zone).format(TIME_FORMAT)} · goal ${active.goalAt().atZone(zone).format(TIME_FORMAT)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary
                )
                Spacer(Modifier.height(8.dp))
                StageRow(FastingStage.at(active.elapsed(now)))
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { if (active.reachedGoal(now)) onEnd() else confirmEnd = true },
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) { Text("End fast") }
                TextButton(onClick = { picker = TimePick.EDIT_START }) { Text("Edit start time") }
            } else {
                Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Text("Start ${snapshot.targetHours}h fast")
                }
                TextButton(onClick = { picker = TimePick.STARTED_EARLIER }) { Text("I started earlier") }
            }
        }
    }

    picker?.let { mode ->
        val zone = ZoneId.systemDefault()
        val initial = if (mode == TimePick.EDIT_START && active != null) active.startAt.atZone(zone).toLocalTime() else LocalTime.now().minusHours(1)
        FastingTimePicker(
            title = if (mode == TimePick.EDIT_START) "When did this fast start?" else "When did you stop eating?",
            initial = initial,
            onDismiss = { picker = null },
            onPick = { time ->
                if (mode == TimePick.EDIT_START) onEditStart(time) else onStartAt(time)
                picker = null
            }
        )
    }
    if (confirmEnd && active != null) {
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            title = { Text("End fast early?") },
            text = {
                Text(
                    "You're ${FastingMath.formatDuration(active.elapsed(now))} in, ${FastingMath.formatDuration(Duration.between(now, active.goalAt()))} short of your goal. " +
                        "It's saved to your history but won't count toward your streak."
                )
            },
            confirmButton = { TextButton(onClick = { confirmEnd = false; onEnd() }) { Text("End fast") } },
            dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text("Keep going") } }
        )
    }
}

private enum class TimePick { STARTED_EARLIER, EDIT_START }

@Composable
private fun StageRow(stage: FastingStage) {
    val tokens = LocalHangryTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(FastingColor.copy(alpha = 0.12f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(stage.title, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
            Text(stage.detail, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
        }
        Text("~${stage.fromHours}h+", style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
    }
}

@Composable
private fun PlanCard(snapshot: FastingSnapshot, onPickPlan: (FastingPlan, Int?) -> Unit) {
    val tokens = LocalHangryTokens.current
    HangryCard(modifier = Modifier.fillMaxWidth(), contentPadding = 12.dp) {
        Text("Your plan", style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
        Spacer(Modifier.height(4.dp))
        PlanChips(snapshot, onPickPlan)
        if (snapshot.active != null) {
            Text("Changing plan updates the goal of the fast you're on.", style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
        }
    }
}

@Composable
private fun StatsCard(snapshot: FastingSnapshot) {
    val tokens = LocalHangryTokens.current
    val week = FastingMath.lastWeek(snapshot.history)
    val average = week.takeIf { it.isNotEmpty() }?.let { list -> Duration.ofMinutes(list.sumOf { it.elapsed().toMinutes() } / list.size) }
    val longest = snapshot.history.maxOfOrNull { it.elapsed() }
    HangryCard(modifier = Modifier.fillMaxWidth(), contentPadding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Progress", style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary, modifier = Modifier.weight(1f))
            if (snapshot.streak >= 3) DashExpression(mood = DashMood.STREAK, size = 44.dp, contentDescription = null)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Streak", "${snapshot.streak} ${if (snapshot.streak == 1) "day" else "days"}", "best ${snapshot.bestStreak}", Modifier.weight(1f))
            StatTile("This week", "${week.count { it.reachedGoal() }}/${week.size}", "fasts hit goal", Modifier.weight(1f))
            StatTile("Average", average?.let(FastingMath::formatDuration) ?: "—", longest?.let { "longest " + FastingMath.formatDuration(it) } ?: "last 7 days", Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, caption: String, modifier: Modifier = Modifier) {
    val tokens = LocalHangryTokens.current
    Column(
        modifier
            .clip(RoundedCornerShape(HangryTokens.CornerRadii.small))
            .background(FastingColor.copy(alpha = 0.10f))
            .padding(10.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = tokens.textSecondary)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = tokens.textPrimary, maxLines = 1)
        Text(caption, style = MaterialTheme.typography.labelSmall, color = tokens.textMuted, maxLines = 1)
    }
}

@Composable
private fun HistoryCard(history: List<Fast>, onDelete: (Long) -> Unit) {
    val tokens = LocalHangryTokens.current
    var deleting by remember { mutableStateOf<Fast?>(null) }
    val zone = ZoneId.systemDefault()
    HangryCard(modifier = Modifier.fillMaxWidth(), contentPadding = 12.dp) {
        Text("Recent fasts", style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
        history.take(MAX_HISTORY).forEach { fast ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(
                        fast.startAt.atZone(zone).format(DAY_FORMAT) + " · " + FastingMath.formatDuration(fast.elapsed()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.textPrimary
                    )
                    Text(
                        "${fast.startAt.atZone(zone).format(TIME_FORMAT)} – ${fast.endAt?.atZone(zone)?.format(TIME_FORMAT).orEmpty()} · goal ${fast.targetMinutes / 60}h",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.textMuted
                    )
                }
                Text(
                    if (fast.reachedGoal()) "✓ Goal" else "Early",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (fast.reachedGoal()) tokens.scoreColors.primed else tokens.textMuted
                )
                IconButton(onClick = { deleting = fast }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Delete fast", tint = tokens.textMuted, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
    deleting?.let { fast ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete this fast?") },
            text = { Text("${FastingMath.formatDuration(fast.elapsed())} on ${fast.startAt.atZone(zone).format(DAY_FORMAT)}. This can change your streak.") },
            confirmButton = { TextButton(onClick = { onDelete(fast.id); deleting = null }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }
}

private const val MAX_HISTORY = 14

@Composable
private fun SettingsCard(snapshot: FastingSnapshot, onGoalReminder: (Boolean) -> Unit, onTurnOff: () -> Unit) {
    val tokens = LocalHangryTokens.current
    var confirmOff by remember { mutableStateOf(false) }
    HangryCard(modifier = Modifier.fillMaxWidth(), contentPadding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Goal reminder", style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                Text("A notification when your fast reaches its goal", style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
            }
            Switch(checked = snapshot.goalReminder, onCheckedChange = onGoalReminder)
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = tokens.cardBorder)
        TextButton(onClick = { confirmOff = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Turn off fasting", color = MaterialTheme.colorScheme.error)
        }
    }
    if (confirmOff) {
        AlertDialog(
            onDismissRequest = { confirmOff = false },
            title = { Text("Turn off fasting?") },
            text = {
                Text(
                    (if (snapshot.active != null) "Your current fast ends and is saved. " else "") +
                        "The timer, reminder, streak and widget stop. Your history is kept if you turn it back on.",
                    textAlign = TextAlign.Start
                )
            },
            confirmButton = { TextButton(onClick = { confirmOff = false; onTurnOff() }) { Text("Turn off") } },
            dismissButton = { TextButton(onClick = { confirmOff = false }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FastingTimePicker(title: String, initial: LocalTime, onDismiss: () -> Unit, onPick: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                TimePicker(state = state)
                Text(
                    "A time later than now means yesterday.",
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalHangryTokens.current.textMuted
                )
            }
        },
        confirmButton = { TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
