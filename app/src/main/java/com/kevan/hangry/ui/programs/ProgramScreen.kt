package com.kevan.hangry.ui.programs

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

private fun infoFor(s: ProgramSnapshot): List<HangryInfoSection> = buildList {
    val p = s.program
    add(HangryInfoSection("The idea", p.intro))
    add(
        HangryInfoSection(
            "How to use it",
            "About ${p.sessionsPerWeek} sessions a week. Tick each part as you go and say how it felt. After ${p.sessionsToAdvance} sessions, once two in a row feel easy, you can move up a level - only if you want to."
        )
    )
    add(
        HangryInfoSection(
            "If it doesn't feel right",
            if (p.kind == ProgramKind.BODY) "Use the easier version, go less far, or skip that exercise. If sessions keep hurting, step back a level and check with a physio."
            else "Use the easier version or a shorter time. If it keeps not helping, step back a level - and talk to someone if you're struggling."
        )
    )
    p.equipment?.let { add(HangryInfoSection("You'll need", it)) }
    add(HangryInfoSection("Safety", p.safety))
    p.credit?.let { add(HangryInfoSection("Where it comes from", it)) }
    val pillars = listOfNotNull(
        "Mobility".takeIf { p.countsAsMobility },
        p.strengthFromLevel?.let { if (it <= 1) "Strength" else "Strength (from level $it)" }
    )
    if (pillars.isNotEmpty()) add(HangryInfoSection("Longevity pillars", "Finished sessions count toward: ${pillars.joinToString(" and ")}."))
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
                title = { Text(s?.program?.title ?: "Program") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = { s?.let { HangryInfoIconButton(title = "About ${it.program.title}", sections = infoFor(it)) } },
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
            "${p.levels.size} levels, starting easy",
            "About ${p.sessionsPerWeek} short sessions a week",
            "Tick off each part and say how it felt",
            "Move up a level when it feels easy - your choice"
        ).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = tokens.textPrimary) }
        p.equipment?.let {
            Spacer(Modifier.height(8.dp))
            Text("You'll need: $it", style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
        }
        Spacer(Modifier.height(12.dp))
        Surface(color = tokens.brandAccentContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.WarningAmber, contentDescription = null, tint = tokens.brandAccent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Before you start", style = MaterialTheme.typography.labelLarge, color = tokens.textPrimary)
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
            Text("I understand and will check with a professional if I'm unsure", style = MaterialTheme.typography.bodySmall, color = tokens.textPrimary)
        }
        Button(onClick = onStart, enabled = understood, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Text("Start at Level 1")
        }
        p.credit?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = tokens.textMuted, modifier = Modifier.padding(top = 6.dp))
        }
        Text(
            "Off until you start it, and you can turn it off any time.",
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
        Text("Level ${level.number} of ${p.levels.size}", style = MaterialTheme.typography.labelMedium, color = tokens.textMuted)
        Text(level.title, style = MaterialTheme.typography.titleLarge, color = tokens.textPrimary)
        Text(level.focus, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MiniProgress("Sessions at this level", atLevel.coerceAtMost(p.sessionsToAdvance), p.sessionsToAdvance, Modifier.weight(1f))
            MiniProgress("This week", s.sessionsThisWeek.coerceAtMost(p.sessionsPerWeek), p.sessionsPerWeek, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        val body = p.kind == ProgramKind.BODY
        when (val advice = s.advice) {
            ProgramAdvice.ReadyToMoveUp -> {
                // "Stay" just hides the offer until the next session.
                var staying by remember(atLevel) { mutableStateOf(false) }
                if (staying) {
                    Text("Staying at this level. The offer comes back after your next session.", style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                } else {
                    AdviceNote(DashMood.LEVEL_UP, "Your last sessions felt easy. Ready for Level ${level.number + 1}?")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onSetLevel(level.number + 1) }) { Text("Move up") }
                        OutlinedButton(onClick = { staying = true }) { Text("Stay a bit longer") }
                    }
                }
            }
            ProgramAdvice.Maintain ->
                AdviceNote(DashMood.CELEBRATE, "You've reached the top level. Keep ${(p.sessionsPerWeek - 1).coerceAtLeast(2)}-${p.sessionsPerWeek} sessions a week to hold on to it.")
            ProgramAdvice.EaseOff ->
                AdviceNote(
                    if (body) DashMood.OUCH else DashMood.REST,
                    if (body) "Last session didn't feel right. Next time use the easier versions and go less far. Stay at this level."
                    else "Last session didn't feel right. Try the easier versions or a shorter time next time."
                )
            ProgramAdvice.StepBack -> {
                AdviceNote(
                    if (body) DashMood.OUCH else DashMood.REST,
                    if (body) "Two sessions in a row didn't feel right. Take it back a level, and check with a physio if the pain keeps coming."
                    else "Two sessions in a row didn't feel right. Take it back a level - and if you're struggling, talk to someone you trust or a professional."
                )
                if (level.number > 1) OutlinedButton(onClick = { onSetLevel(level.number - 1) }) { Text("Go back to Level ${level.number - 1}") }
            }
            is ProgramAdvice.KeepGoing -> Text(
                if (atLevel >= p.sessionsToAdvance) "Move up once two sessions in a row feel easy."
                else "${advice.remaining} more session${if (advice.remaining == 1) "" else "s"} here, then you can move up if it feels easy.",
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
            Text("$value/$goal", style = MaterialTheme.typography.labelSmall, color = tokens.textSecondary)
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
            Text("Today's session", style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary, modifier = Modifier.weight(1f))
            Text("${checked.size}/${exercises.size}", style = MaterialTheme.typography.labelMedium, color = tokens.textSecondary)
        }
        if (s.doneToday) {
            DashExpression(mood = programMood(s.program.id), size = 64.dp, contentDescription = null)
            Text(
                if (kind == ProgramKind.BODY && s.program.sessionsPerWeek <= 3) "Done for today ✓ Rest is part of it - aim for every other day."
                else "Done for today ✓",
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
            Text(if (checked.size == exercises.size) "Finish session" else "Finish with ${checked.size} done")
        }
    }
    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text("How did it feel?") },
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
            dismissButton = { TextButton(onClick = { asking = false }) { Text("Cancel") } }
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
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = if (expanded) "Hide how-to" else "Show how-to", tint = tokens.textMuted)
        }
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(start = 48.dp, end = 8.dp, bottom = 8.dp)) {
                Text(exercise.howTo, style = MaterialTheme.typography.bodySmall, color = tokens.textPrimary)
                Spacer(Modifier.height(4.dp))
                Text("Easier: ${exercise.easier}", style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
            }
        }
        exercise.breathingPattern?.let { pattern ->
            TextButton(onClick = { onOpenBreathing(pattern) }, modifier = Modifier.padding(start = 40.dp)) {
                Icon(Icons.Default.Air, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Breathe with Dash")
            }
        }
    }
}

@Composable
private fun HistoryCard(s: ProgramSnapshot, onDelete: (Long) -> Unit) {
    val tokens = LocalHangryTokens.current
    HangryCard(modifier = Modifier.fillMaxWidth(), contentPadding = 12.dp) {
        Text("Recent sessions", style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
        s.sessions.take(10).forEach { session: ProgramSession ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${session.date.format(DAY_FORMAT)} · Level ${session.level}", style = MaterialTheme.typography.bodyMedium, color = tokens.textPrimary)
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
                    Icon(Icons.Default.Close, contentDescription = "Delete session", tint = tokens.textMuted, modifier = Modifier.size(16.dp))
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
            Text("The path", style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary, modifier = Modifier.weight(1f))
            Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = if (open) "Hide exercises" else "Show exercises", tint = tokens.textMuted)
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
                    Text(level.title + if (level.number == s.level) " · you're here" else "", style = MaterialTheme.typography.bodyMedium, color = tokens.textPrimary)
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
        TextButton(onClick = { pickLevel = true }, modifier = Modifier.fillMaxWidth()) { Text("Change level") }
        HorizontalDivider(color = tokens.cardBorder)
        TextButton(onClick = { confirmOff = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Turn off program", color = MaterialTheme.colorScheme.error)
        }
    }
    if (pickLevel) {
        AlertDialog(
            onDismissRequest = { pickLevel = false },
            title = { Text("Choose a level") },
            text = {
                Column {
                    s.program.levels.forEach { level ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onSetLevel(level.number); pickLevel = false }
                        ) {
                            RadioButton(selected = s.level == level.number, onClick = { onSetLevel(level.number); pickLevel = false })
                            Text("${level.number}. ${level.title}")
                        }
                    }
                    Text("If you're new to this, start at Level 1.", style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
                }
            },
            confirmButton = { TextButton(onClick = { pickLevel = false }) { Text("Done") } }
        )
    }
    if (confirmOff) {
        AlertDialog(
            onDismissRequest = { confirmOff = false },
            title = { Text("Turn off ${s.program.title.lowercase()}?") },
            text = { Text("Your level and session history are kept if you turn it back on.") },
            confirmButton = { TextButton(onClick = { confirmOff = false; onTurnOff() }) { Text("Turn off") } },
            dismissButton = { TextButton(onClick = { confirmOff = false }) { Text("Cancel") } }
        )
    }
}
