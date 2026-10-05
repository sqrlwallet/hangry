package com.kevan.hangry.ui.programs

import com.kevan.hangry.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kevan.hangry.domain.model.ProgramAdvice
import com.kevan.hangry.domain.model.ProgramExercise
import com.kevan.hangry.domain.model.ProgramFeel
import com.kevan.hangry.domain.model.ProgramKind
import com.kevan.hangry.domain.model.ProgramSession
import com.kevan.hangry.domain.model.ProgramSnapshot
import com.kevan.hangry.ui.coach.DashExpression
import com.kevan.hangry.ui.coach.DashMood
import com.kevan.hangry.ui.components.HangryCard
import com.kevan.hangry.ui.components.HangryInfoIconButton
import com.kevan.hangry.ui.components.HangryInfoSection
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DAY_FORMAT = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())

@Composable
private fun infoFor(s: ProgramSnapshot): List<HangryInfoSection> {
    val p = s.program
    val idea = stringResource(R.string.coach_programs_info_idea)
    val howTitle = stringResource(R.string.coach_programs_info_how_title)
    val howBody = stringResource(R.string.coach_programs_info_how_body, p.sessionsPerWeek, p.sessionsToAdvance)
    val notRightTitle = stringResource(R.string.coach_programs_info_not_right_title)
    val notRightBody = if (p.kind == ProgramKind.BODY) stringResource(R.string.coach_programs_info_not_right_body)
        else stringResource(R.string.coach_programs_info_not_right_mind)
    val needTitle = stringResource(R.string.coach_programs_info_need)
    val safetyTitle = stringResource(R.string.coach_programs_info_safety)
    val creditTitle = stringResource(R.string.coach_programs_info_credit)
    return buildList {
        add(HangryInfoSection(idea, p.intro))
        add(HangryInfoSection(howTitle, howBody))
        add(HangryInfoSection(notRightTitle, notRightBody))
        p.equipment?.let { add(HangryInfoSection(needTitle, it)) }
        add(HangryInfoSection(safetyTitle, p.safety))
        p.credit?.let { add(HangryInfoSection(creditTitle, it)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgramScreen(
    programId: String,
    viewModel: ProgramsViewModel,
    onOpenBreathing: (String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val programs by viewModel.programs.collectAsState()
    val checkedAll by viewModel.checked.collectAsState()
    val s = programs?.firstOrNull { it.program.id == programId }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s?.program?.title ?: stringResource(R.string.coach_programs_program)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.coach_back)) }
                },
                actions = { s?.let { HangryInfoIconButton(title = stringResource(R.string.coach_programs_about, it.program.title), sections = infoFor(it)) } },
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
            s ?: return@Column
            val id = s.program.id
            if (!s.enabled) {
                IntroCard(s, onStart = { viewModel.setEnabled(id, true) })
            } else {
                LevelCard(s, onSetLevel = { viewModel.setLevel(id, it) })
                SessionCard(
                    s, checkedAll[id].orEmpty(),
                    onToggle = { viewModel.toggle(id, it) },
                    onFinish = { viewModel.finish(id, it) },
                    onOpenBreathing = onOpenBreathing
                )
                if (s.sessions.isNotEmpty()) HistoryCard(s, onDelete = viewModel::deleteSession)
                LevelsOverview(s)
                SettingsCard(s, onSetLevel = { viewModel.setLevel(id, it) }, onTurnOff = { viewModel.setEnabled(id, false) })
                Text(s.program.safety, style = MaterialTheme.typography.labelSmall, color = LocalHangryTokens.current.textMuted)
            }
        }
    }
}

@Composable
private fun IntroCard(s: ProgramSnapshot, onStart: () -> Unit) {
    val tokens = LocalHangryTokens.current
    val p = s.program
    var understood by remember { mutableStateOf(false) }
    HangryCard(modifier = Modifier.fillMaxWidth()) {
        DashExpression(mood = programMood(p.id), size = 110.dp, contentDescription = null)
        Spacer(Modifier.height(8.dp))
        Text(p.tagline, style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
        Text(p.intro, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
        Spacer(Modifier.height(12.dp))
        listOf(
            stringResource(R.string.coach_programs_intro_levels, p.levels.size),
            stringResource(R.string.coach_programs_intro_sessions, p.sessionsPerWeek),
            stringResource(R.string.coach_programs_intro_tick),
            stringResource(R.string.coach_programs_intro_move_up)
        ).forEach { Text(stringResource(R.string.coach_programs_bullet, it), style = MaterialTheme.typography.bodySmall, color = tokens.textPrimary) }
        p.equipment?.let {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.coach_programs_youll_need, it), style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
        }
        Spacer(Modifier.height(12.dp))
        Surface(color = tokens.brandAccentContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.WarningAmber, contentDescription = null, tint = tokens.brandAccent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.coach_programs_before_you_start), style = MaterialTheme.typography.labelLarge, color = tokens.textPrimary)
                }
                Spacer(Modifier.height(4.dp))
                Text(p.safety, style = MaterialTheme.typography.bodySmall, color = tokens.textPrimary)
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { understood = !understood }.padding(vertical = 4.dp)
        ) {
            Checkbox(checked = understood, onCheckedChange = { understood = it })
            Text(stringResource(R.string.coach_programs_understand), style = MaterialTheme.typography.bodySmall, color = tokens.textPrimary)
        }
        Button(onClick = onStart, enabled = understood, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Text(stringResource(R.string.coach_programs_start_level_1))
        }
        p.credit?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = tokens.textMuted, modifier = Modifier.padding(top = 6.dp))
        }
        Text(
            stringResource(R.string.coach_programs_off_until_start),
            style = MaterialTheme.typography.labelSmall,
            color = tokens.textMuted,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun LevelCard(s: ProgramSnapshot, onSetLevel: (Int) -> Unit) {
    val tokens = LocalHangryTokens.current
    val p = s.program
    val level = s.currentLevel
    val atLevel = s.sessionsAtLevel.size
    HangryCard(modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.coach_programs_level_of, level.number, p.levels.size), style = MaterialTheme.typography.labelMedium, color = tokens.textMuted)
        Text(level.title, style = MaterialTheme.typography.titleLarge, color = tokens.textPrimary)
        Text(level.focus, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MiniProgress(stringResource(R.string.coach_programs_sessions_at_level), atLevel.coerceAtMost(p.sessionsToAdvance), p.sessionsToAdvance, Modifier.weight(1f))
            MiniProgress(stringResource(R.string.coach_programs_this_week), s.sessionsThisWeek.coerceAtMost(p.sessionsPerWeek), p.sessionsPerWeek, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        val body = p.kind == ProgramKind.BODY
        when (val advice = s.advice) {
            ProgramAdvice.ReadyToMoveUp -> {
                // "Stay" just hides the offer until the next session.
                var staying by remember(atLevel) { mutableStateOf(false) }
                if (staying) {
                    Text(stringResource(R.string.coach_programs_staying), style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                } else {
                    AdviceNote(DashMood.LEVEL_UP, stringResource(R.string.coach_programs_ready_move_up, level.number + 1))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onSetLevel(level.number + 1) }) { Text(stringResource(R.string.coach_programs_move_up)) }
                        OutlinedButton(onClick = { staying = true }) { Text(stringResource(R.string.coach_programs_stay_longer)) }
                    }
                }
            }
            ProgramAdvice.Maintain ->
                AdviceNote(DashMood.CELEBRATE, stringResource(R.string.coach_programs_maintain, (p.sessionsPerWeek - 1).coerceAtLeast(2), p.sessionsPerWeek))
            ProgramAdvice.EaseOff ->
                AdviceNote(
                    if (body) DashMood.OUCH else DashMood.REST,
                    if (body) stringResource(R.string.coach_programs_ease_off_body)
                    else stringResource(R.string.coach_programs_ease_off_mind)
                )
            ProgramAdvice.StepBack -> {
                AdviceNote(
                    if (body) DashMood.OUCH else DashMood.REST,
                    if (body) stringResource(R.string.coach_programs_step_back_body)
                    else stringResource(R.string.coach_programs_step_back_mind)
                )
                if (level.number > 1) OutlinedButton(onClick = { onSetLevel(level.number - 1) }) { Text(stringResource(R.string.coach_programs_go_back_level, level.number - 1)) }
            }
            is ProgramAdvice.KeepGoing -> Text(
                if (atLevel >= p.sessionsToAdvance) stringResource(R.string.coach_programs_keep_going_ready)
                else stringResource(
                    if (advice.remaining == 1) R.string.coach_programs_keep_going_one else R.string.coach_programs_keep_going_other,
                    advice.remaining
                ),
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textSecondary
            )
        }
    }
}

@Composable
private fun MiniProgress(label: String, value: Int, goal: Int, modifier: Modifier = Modifier) {
    val tokens = LocalHangryTokens.current
    Column(modifier) {
        Row {
            Text(label, style = MaterialTheme.typography.labelSmall, color = tokens.textMuted, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.coach_fraction, value, goal), style = MaterialTheme.typography.labelSmall, color = tokens.textSecondary)
        }
        LinearProgressIndicator(
            progress = { value.toFloat() / goal },
            color = tokens.brandAccent,
            trackColor = tokens.cardBorder,
            strokeCap = StrokeCap.Round,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(5.dp)
        )
    }
}

@Composable
private fun AdviceNote(mood: DashMood, text: String) {
    val tokens = LocalHangryTokens.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
        DashExpression(mood = mood, size = 44.dp, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = tokens.textPrimary)
    }
}

@Composable
private fun SessionCard(
    s: ProgramSnapshot,
    checked: Set<String>,
    onToggle: (String) -> Unit,
    onFinish: (ProgramFeel) -> Unit,
    onOpenBreathing: (String) -> Unit
) {
    val tokens = LocalHangryTokens.current
    val kind = s.program.kind
    var asking by remember { mutableStateOf(false) }
    val exercises = s.currentLevel.exercises
    HangryCard(modifier = Modifier.fillMaxWidth(), contentPadding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.coach_programs_todays_session), style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.coach_fraction, checked.size, exercises.size), style = MaterialTheme.typography.labelMedium, color = tokens.textSecondary)
        }
        if (s.doneToday) {
            DashExpression(mood = programMood(s.program.id), size = 64.dp, contentDescription = null)
            Text(
                if (kind == ProgramKind.BODY && s.program.sessionsPerWeek <= 3) stringResource(R.string.coach_programs_done_today_rest)
                else stringResource(R.string.coach_programs_done_today),
                style = MaterialTheme.typography.bodySmall,
                color = tokens.scoreColors.primed
            )
        }
        Spacer(Modifier.height(4.dp))
        exercises.forEach { exercise ->
            ExerciseRow(exercise, done = exercise.id in checked, onToggle = { onToggle(exercise.id) }, onOpenBreathing = onOpenBreathing)
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = { asking = true }, enabled = checked.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Text(if (checked.size == exercises.size) stringResource(R.string.coach_programs_finish_session) else stringResource(R.string.coach_programs_finish_with, checked.size))
        }
    }
    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text(stringResource(R.string.coach_programs_how_did_it_feel)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProgramFeel.entries.forEach { feel ->
                        OutlinedCard(onClick = { asking = false; onFinish(feel) }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text(feel.label(kind), style = MaterialTheme.typography.titleSmall)
                                Text(feel.detail(kind), style = MaterialTheme.typography.bodySmall, color = LocalHangryTokens.current.textSecondary)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { asking = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun ExerciseRow(exercise: ProgramExercise, done: Boolean, onToggle: () -> Unit, onOpenBreathing: (String) -> Unit) {
    val tokens = LocalHangryTokens.current
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { expanded = !expanded }) {
            Checkbox(checked = done, onCheckedChange = { onToggle() })
            Column(Modifier.weight(1f)) {
                Text(exercise.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = if (done) tokens.textMuted else tokens.textPrimary)
                Text(exercise.dose, style = MaterialTheme.typography.labelSmall, color = tokens.textSecondary)
            }
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = if (expanded) stringResource(R.string.coach_programs_hide_how_to) else stringResource(R.string.coach_programs_show_how_to), tint = tokens.textMuted)
        }
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(start = 48.dp, end = 8.dp, bottom = 8.dp)) {
                Text(exercise.howTo, style = MaterialTheme.typography.bodySmall, color = tokens.textPrimary)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.coach_programs_easier, exercise.easier), style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
            }
        }
        exercise.breathingPattern?.let { pattern ->
            TextButton(onClick = { onOpenBreathing(pattern) }, modifier = Modifier.padding(start = 40.dp)) {
                Icon(Icons.Default.Air, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.coach_programs_breathe_with_dash))
            }
        }
    }
}

@Composable
private fun HistoryCard(s: ProgramSnapshot, onDelete: (Long) -> Unit) {
    val tokens = LocalHangryTokens.current
    HangryCard(modifier = Modifier.fillMaxWidth(), contentPadding = 12.dp) {
        Text(stringResource(R.string.coach_recent_sessions), style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
        s.sessions.take(10).forEach { session: ProgramSession ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.coach_programs_history_row, session.date.format(DAY_FORMAT), session.level), style = MaterialTheme.typography.bodyMedium, color = tokens.textPrimary)
                    Text(
                        session.feel.label(s.program.kind),
                        style = MaterialTheme.typography.labelSmall,
                        color = when (session.feel) {
                            ProgramFeel.EASY -> tokens.scoreColors.primed
                            ProgramFeel.OK -> tokens.textSecondary
                            ProgramFeel.HARD -> tokens.scoreColors.rebuild
                        }
                    )
                }
                IconButton(onClick = { onDelete(session.id) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.coach_programs_delete_session), tint = tokens.textMuted, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun LevelsOverview(s: ProgramSnapshot) {
    val tokens = LocalHangryTokens.current
    var open by remember { mutableStateOf(false) }
    HangryCard(modifier = Modifier.fillMaxWidth(), contentPadding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { open = !open }) {
            Text(stringResource(R.string.coach_programs_the_path), style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary, modifier = Modifier.weight(1f))
            Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = if (open) stringResource(R.string.coach_programs_hide_exercises) else stringResource(R.string.coach_programs_show_exercises), tint = tokens.textMuted)
        }
        s.program.levels.forEach { level ->
            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                Text(
                    "${level.number}",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (level.number == s.level) tokens.brandAccent else if (level.number < s.level) tokens.scoreColors.primed else tokens.textMuted,
                    modifier = Modifier.width(24.dp)
                )
                Column {
                    Text(if (level.number == s.level) stringResource(R.string.coach_programs_youre_here, level.title) else level.title, style = MaterialTheme.typography.bodyMedium, color = tokens.textPrimary)
                    if (open) Text(level.exercises.joinToString(" · ") { it.name }, style = MaterialTheme.typography.labelSmall, color = tokens.textSecondary)
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(s: ProgramSnapshot, onSetLevel: (Int) -> Unit, onTurnOff: () -> Unit) {
    val tokens = LocalHangryTokens.current
    var pickLevel by remember { mutableStateOf(false) }
    var confirmOff by remember { mutableStateOf(false) }
    HangryCard(modifier = Modifier.fillMaxWidth(), contentPadding = 12.dp) {
        TextButton(onClick = { pickLevel = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.coach_programs_change_level)) }
        HorizontalDivider(color = tokens.cardBorder)
        TextButton(onClick = { confirmOff = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.coach_programs_turn_off_program), color = MaterialTheme.colorScheme.error)
        }
    }
    if (pickLevel) {
        AlertDialog(
            onDismissRequest = { pickLevel = false },
            title = { Text(stringResource(R.string.coach_programs_choose_level)) },
            text = {
                Column {
                    s.program.levels.forEach { level ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onSetLevel(level.number); pickLevel = false }
                        ) {
                            RadioButton(selected = s.level == level.number, onClick = { onSetLevel(level.number); pickLevel = false })
                            Text(stringResource(R.string.coach_programs_level_option, level.number, level.title))
                        }
                    }
                    Text(stringResource(R.string.coach_programs_new_start_level_1), style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
                }
            },
            confirmButton = { TextButton(onClick = { pickLevel = false }) { Text(stringResource(R.string.coach_done)) } }
        )
    }
    if (confirmOff) {
        AlertDialog(
            onDismissRequest = { confirmOff = false },
            title = { Text(stringResource(R.string.coach_programs_turn_off_title, s.program.title.lowercase())) },
            text = { Text(stringResource(R.string.coach_programs_turn_off_body)) },
            confirmButton = { TextButton(onClick = { confirmOff = false; onTurnOff() }) { Text(stringResource(R.string.coach_programs_turn_off)) } },
            dismissButton = { TextButton(onClick = { confirmOff = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}
