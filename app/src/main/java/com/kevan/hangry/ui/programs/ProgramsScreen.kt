package com.kevan.hangry.ui.programs

import com.kevan.hangry.ui.components.DashEmptyScene
import com.kevan.hangry.ui.components.DashEmptyState
import com.kevan.hangry.ui.coach.DashExpression
import com.kevan.hangry.ui.coach.DashMood
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.SportsGymnastics
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.kevan.hangry.domain.model.ProgramKind
import com.kevan.hangry.domain.model.ProgramSnapshot
import com.kevan.hangry.ui.components.HangryCard
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens

/** Dash doing each program's kind of exercise. */
internal fun programMood(id: String): DashMood = when (id) {
    "stress" -> DashMood.MEDITATE
    "focus" -> DashMood.FOCUS
    "mobility" -> DashMood.STRETCH
    "shoulder" -> DashMood.SHOULDER
    "back" -> DashMood.BACK_CARE
    "pull_up" -> DashMood.PULL_UP
    "push_up" -> DashMood.STRENGTH
    else -> DashMood.KNEES
}

internal fun programIcon(id: String): ImageVector = when (id) {
    "stress" -> Icons.Default.Spa
    "focus" -> Icons.Default.CenterFocusStrong
    "mobility" -> Icons.Default.SelfImprovement
    "shoulder" -> Icons.Default.Accessibility
    "back" -> Icons.Default.AccessibilityNew
    "pull_up" -> Icons.Default.SportsGymnastics
    "push_up" -> Icons.Default.FitnessCenter
    else -> Icons.AutoMirrored.Filled.DirectionsWalk
}

/** Every guided program, with the ones you're doing first. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgramsScreen(
    viewModel: ProgramsViewModel,
    onOpenProgram: (String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalHangryTokens.current
    val programs by viewModel.programs.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Programs") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
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
            verticalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.s)
        ) {
            Text(
                "Simple step-by-step programs. Each is off until you start it - do a short session, say how it felt, and move up a level when it feels easy.",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textSecondary
            )
            val all = programs ?: return@Column
            val active = all.filter { it.enabled }
            if (active.isEmpty()) {
                DashEmptyState(
                    scene = DashEmptyScene.PROGRAMS,
                    title = "Pick a path",
                    body = "Start one program - you can add more any time.",
                    imageSize = 130.dp
                )
            }
            if (active.isNotEmpty()) {
                SectionTitle("Your programs")
                active.forEach { ProgramRow(it, onOpenProgram) }
            }
            val mind = all.filter { !it.enabled && it.program.kind == ProgramKind.MIND }
            val body = all.filter { !it.enabled && it.program.kind == ProgramKind.BODY }
            if (mind.isNotEmpty()) {
                SectionTitle("Mind")
                mind.forEach { ProgramRow(it, onOpenProgram) }
            }
            if (body.isNotEmpty()) {
                SectionTitle("Body")
                body.forEach { ProgramRow(it, onOpenProgram) }
            }
            Text(
                "General wellbeing and fitness guidance, not medical advice. Check with a professional before starting if you have pain, an injury or a health condition.",
                style = MaterialTheme.typography.labelSmall,
                color = tokens.textMuted,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = LocalHangryTokens.current.textPrimary, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun ProgramRow(s: ProgramSnapshot, onOpen: (String) -> Unit) {
    val tokens = LocalHangryTokens.current
    HangryCard(modifier = Modifier.fillMaxWidth().clickable { onOpen(s.program.id) }, contentPadding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DashExpression(mood = programMood(s.program.id), size = 52.dp, contentDescription = null, interactive = false)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(s.program.title, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                Text(
                    if (s.enabled) "Level ${s.level} of ${s.program.levels.size} · ${s.sessionsThisWeek}/${s.program.sessionsPerWeek} this week" +
                        if (s.doneToday) " · done today ✓" else ""
                    else s.program.tagline,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (s.enabled) tokens.scoreColors.primed else tokens.textSecondary
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = tokens.textMuted)
        }
    }
}
