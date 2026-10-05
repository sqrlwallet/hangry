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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.kevan.hangry.R
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

@Composable
private fun fastingInfoSections(): List<HangryInfoSection> = listOf(
    HangryInfoSection(stringResource(R.string.nutrition_info_how_title), stringResource(R.string.nutrition_fasting_info_how_body)),
    HangryInfoSection(stringResource(R.string.nutrition_fasting_info_plans_title), stringResource(R.string.nutrition_fasting_info_plans_body)),
    HangryInfoSection(stringResource(R.string.nutrition_fasting_info_stages_title), stringResource(R.string.nutrition_fasting_info_stages_body)),
    HangryInfoSection(stringResource(R.string.nutrition_fasting_info_for_you_title), stringResource(R.string.nutrition_fasting_info_for_you_body)),
    HangryInfoSection(stringResource(R.string.nutrition_fasting_info_off_title), stringResource(R.string.nutrition_fasting_info_off_body))
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
                title = { Text(stringResource(R.string.nutrition_fasting_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nutrition_cd_back)) }
                },
                actions = { HangryInfoIconButton(title = stringResource(R.string.nutrition_fasting_about), sections = fastingInfoSections()) },
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
                stringResource(R.string.nutrition_fasting_disclaimer),
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
        Text(stringResource(R.string.nutrition_fasting_intro_title), style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
        Text(
            stringResource(R.string.nutrition_fasting_intro_body),
            style = MaterialTheme.typography.bodySmall,
            color = tokens.textSecondary
        )
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.nutrition_fasting_choose_plan), style = MaterialTheme.typography.labelLarge, color = tokens.textPrimary)
        Spacer(Modifier.height(4.dp))
        PlanChips(snapshot, onPickPlan)
        Spacer(Modifier.height(12.dp))
        if (isPregnant) {
            PregnancyWarning()
            Spacer(Modifier.height(12.dp))
        }
        Surface(color = tokens.brandAccentContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.nutrition_fasting_intro_caution),
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textPrimary,
                modifier = Modifier.padding(12.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onTurnOn, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Text(stringResource(R.string.nutrition_fasting_turn_on))
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
                stringResource(R.string.nutrition_fasting_pregnancy_warning),
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
        if (snapshot.plan == FastingPlan.CUSTOM) stringResource(R.string.nutrition_fasting_custom_hours_fast, snapshot.targetHours) else snapshot.plan.blurb,
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
        Text(stringResource(R.string.nutrition_fasting_hours, hours.toInt()), style = MaterialTheme.typography.labelMedium, color = tokens.textPrimary)
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
                        Text(if (reached) stringResource(R.string.nutrition_fasting_goal_reached) else stringResource(R.string.nutrition_fasting_title), style = MaterialTheme.typography.labelLarge, color = if (reached) tokens.scoreColors.primed else tokens.textSecondary)
                        Text(formatClock(active.elapsed(now)), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = tokens.textPrimary)
                        Text(
                            if (reached) stringResource(R.string.nutrition_fasting_past_goal, FastingMath.formatDuration(Duration.between(active.goalAt(), now)), active.targetMinutes / 60)
                            else stringResource(R.string.nutrition_fasting_to_go, FastingMath.formatDuration(Duration.between(now, active.goalAt()))),
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.textSecondary
                        )
                    } else {
                        Text(stringResource(R.string.nutrition_fasting_eating_window), style = MaterialTheme.typography.labelLarge, color = tokens.textSecondary)
                        val sinceLast = snapshot.lastFinished?.endAt?.let { Duration.between(it, now) }
                        Text(
                            sinceLast?.let { formatClock(it) } ?: stringResource(R.string.nutrition_fasting_hours_short, snapshot.targetHours),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = tokens.textPrimary
                        )
                        Text(
                            if (sinceLast != null) stringResource(R.string.nutrition_fasting_since_last) else stringResource(R.string.nutrition_fasting_plan_fast, snapshot.plan.label),
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
                    stringResource(R.string.nutrition_fasting_started_goal, active.startAt.atZone(zone).format(TIME_FORMAT), active.goalAt().atZone(zone).format(TIME_FORMAT)),
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary
                )
                Spacer(Modifier.height(8.dp))
                StageRow(FastingStage.at(active.elapsed(now)))
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { if (active.reachedGoal(now)) onEnd() else confirmEnd = true },
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) { Text(stringResource(R.string.nutrition_fasting_end_fast)) }
                TextButton(onClick = { picker = TimePick.EDIT_START }) { Text(stringResource(R.string.nutrition_fasting_edit_start)) }
            } else {
                Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Text(stringResource(R.string.nutrition_fasting_start_hours_fast, snapshot.targetHours))
                }
                TextButton(onClick = { picker = TimePick.STARTED_EARLIER }) { Text(stringResource(R.string.nutrition_fasting_started_earlier)) }
            }
        }
    }

    picker?.let { mode ->
        val zone = ZoneId.systemDefault()
        val initial = if (mode == TimePick.EDIT_START && active != null) active.startAt.atZone(zone).toLocalTime() else LocalTime.now().minusHours(1)
        FastingTimePicker(
            title = if (mode == TimePick.EDIT_START) stringResource(R.string.nutrition_fasting_picker_edit_title) else stringResource(R.string.nutrition_fasting_picker_start_title),
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
            title = { Text(stringResource(R.string.nutrition_fasting_end_early_title)) },
            text = {
                Text(
                    stringResource(R.string.nutrition_fasting_end_early_body, FastingMath.formatDuration(active.elapsed(now)), FastingMath.formatDuration(Duration.between(now, active.goalAt())))
                )
            },
            confirmButton = { TextButton(onClick = { confirmEnd = false; onEnd() }) { Text(stringResource(R.string.nutrition_fasting_end_fast)) } },
            dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text(stringResource(R.string.nutrition_fasting_keep_going)) } }
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
        Text(stringResource(R.string.nutrition_fasting_stage_from, stage.fromHours), style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
    }
}

@Composable
private fun PlanCard(snapshot: FastingSnapshot, onPickPlan: (FastingPlan, Int?) -> Unit) {
    val tokens = LocalHangryTokens.current
    HangryCard(modifier = Modifier.fillMaxWidth(), contentPadding = 12.dp) {
        Text(stringResource(R.string.nutrition_fasting_your_plan), style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
        Spacer(Modifier.height(4.dp))
        PlanChips(snapshot, onPickPlan)
        if (snapshot.active != null) {
            Text(stringResource(R.string.nutrition_fasting_plan_change_note), style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
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
            Text(stringResource(R.string.nutrition_fasting_progress), style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary, modifier = Modifier.weight(1f))
            if (snapshot.streak >= 3) DashExpression(mood = DashMood.STREAK, size = 44.dp, contentDescription = null)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                stringResource(R.string.nutrition_fasting_streak),
                if (snapshot.streak == 1) stringResource(R.string.nutrition_fasting_one_day) else stringResource(R.string.nutrition_fasting_n_days, snapshot.streak),
                stringResource(R.string.nutrition_fasting_best, snapshot.bestStreak),
                Modifier.weight(1f)
            )
            StatTile(stringResource(R.string.nutrition_fasting_this_week), stringResource(R.string.nutrition_fasting_ratio, week.count { it.reachedGoal() }, week.size), stringResource(R.string.nutrition_fasting_fasts_hit_goal), Modifier.weight(1f))
            StatTile(
                stringResource(R.string.nutrition_fasting_average),
                average?.let(FastingMath::formatDuration) ?: "—",
                longest?.let { stringResource(R.string.nutrition_fasting_longest, FastingMath.formatDuration(it)) } ?: stringResource(R.string.nutrition_fasting_last_7_days),
                Modifier.weight(1f)
            )
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
        Text(stringResource(R.string.nutrition_fasting_recent), style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
        history.take(MAX_HISTORY).forEach { fast ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.nutrition_fasting_history_day, fast.startAt.atZone(zone).format(DAY_FORMAT), FastingMath.formatDuration(fast.elapsed())),
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.textPrimary
                    )
                    Text(
                        stringResource(R.string.nutrition_fasting_history_range, fast.startAt.atZone(zone).format(TIME_FORMAT), fast.endAt?.atZone(zone)?.format(TIME_FORMAT).orEmpty(), fast.targetMinutes / 60),
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.textMuted
                    )
                }
                Text(
                    if (fast.reachedGoal()) stringResource(R.string.nutrition_fasting_history_goal) else stringResource(R.string.nutrition_fasting_history_early),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (fast.reachedGoal()) tokens.scoreColors.primed else tokens.textMuted
                )
                IconButton(onClick = { deleting = fast }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.nutrition_fasting_delete_cd), tint = tokens.textMuted, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
    deleting?.let { fast ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.nutrition_fasting_delete_title)) },
            text = { Text(stringResource(R.string.nutrition_fasting_delete_body, FastingMath.formatDuration(fast.elapsed()), fast.startAt.atZone(zone).format(DAY_FORMAT))) },
            confirmButton = { TextButton(onClick = { onDelete(fast.id); deleting = null }) { Text(stringResource(R.string.nutrition_delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } }
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
                Text(stringResource(R.string.nutrition_fasting_goal_reminder), style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                Text(stringResource(R.string.nutrition_fasting_goal_reminder_body), style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
            }
            Switch(checked = snapshot.goalReminder, onCheckedChange = onGoalReminder)
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = tokens.cardBorder)
        TextButton(onClick = { confirmOff = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.nutrition_fasting_turn_off), color = MaterialTheme.colorScheme.error)
        }
    }
    if (confirmOff) {
        AlertDialog(
            onDismissRequest = { confirmOff = false },
            title = { Text(stringResource(R.string.nutrition_fasting_turn_off_title)) },
            text = {
                Text(
                    (if (snapshot.active != null) stringResource(R.string.nutrition_fasting_turn_off_active_prefix) else "") +
                        stringResource(R.string.nutrition_fasting_turn_off_body),
                    textAlign = TextAlign.Start
                )
            },
            confirmButton = { TextButton(onClick = { confirmOff = false; onTurnOff() }) { Text(stringResource(R.string.nutrition_fasting_turn_off_confirm)) } },
            dismissButton = { TextButton(onClick = { confirmOff = false }) { Text(stringResource(R.string.cancel)) } }
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
                    stringResource(R.string.nutrition_fasting_picker_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalHangryTokens.current.textMuted
                )
            }
        },
        confirmButton = { TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)) }) { Text(stringResource(R.string.nutrition_fasting_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
