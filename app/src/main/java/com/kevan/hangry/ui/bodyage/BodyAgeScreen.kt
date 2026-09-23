package com.kevan.hangry.ui.bodyage

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kevan.hangry.data.repository.BodyAgeSnapshot
import com.kevan.hangry.domain.calculation.BodyAgeFactor
import com.kevan.hangry.domain.calculation.BodyAgeResult
import com.kevan.hangry.domain.model.AgeMath
import com.kevan.hangry.ui.coach.DashExpression
import com.kevan.hangry.ui.coach.DashMood
import com.kevan.hangry.ui.components.HangryCard
import com.kevan.hangry.ui.dashboard.DashboardViewModel
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.abs

private fun years(value: Double) = String.format(Locale.US, "%.1f", abs(value))

/** How Dash feels about a Body Age: younger is a win, within a year is steady, older is a nudge. */
internal fun BodyAgeResult.mood(): DashMood = when {
    difference <= -1.0 -> DashMood.CELEBRATE
    difference < 1.0 -> DashMood.CHEER
    else -> DashMood.CONCERNED
}

internal fun BodyAgeResult.comparison(): String {
    val real = String.format(Locale.US, "%.1f", chronologicalAge)
    return when {
        difference <= -0.5 -> "${years(difference)} years younger than your real age of $real"
        difference >= 0.5 -> "${years(difference)} years older than your real age of $real"
        else -> "Right on your real age of $real"
    }
}

internal fun BodyAgeSnapshot.trend(): String? {
    val now = current ?: return null
    val before = previous ?: return null
    val change = now.bodyAge - before.bodyAge
    return when {
        change <= -0.1 -> "▼ ${years(change)} years since last month"
        change >= 0.1 -> "▲ ${years(change)} years since last month"
        else -> "Steady since last month"
    }
}

/** The Today card: Body Age against real age, with Dash. */
@Composable
fun BodyAgeCard(snapshot: BodyAgeSnapshot?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val tokens = LocalHangryTokens.current
    val result = snapshot?.current
    HangryCard(modifier = modifier.fillMaxWidth().clickable(onClickLabel = "Open Body Age") { onClick() }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DashExpression(mood = result?.mood() ?: DashMood.THINKING, size = 64.dp, contentDescription = null, interactive = false)
            Spacer(modifier = Modifier.width(HangryTokens.Spacing.m))
            Column(modifier = Modifier.weight(1f)) {
                Text("Body Age", style = MaterialTheme.typography.labelMedium, color = tokens.textMuted)
                if (snapshot?.needsBirthday == true) {
                    Text("Add your birthday", style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
                    Text("Tap to see how old your body acts", style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                } else if (result == null) {
                    Text("Needs a little more data", style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
                    Text("Tap to see what's missing", style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                } else {
                    Text(
                        String.format(Locale.US, "%.1f", result.bodyAge),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = tokens.textPrimary
                    )
                    Text(result.comparison(), style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                    snapshot.trend()?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = tokens.textMuted) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyAgeScreen(
    viewModel: DashboardViewModel,
    onNavigateBack: () -> Unit,
    /** Saves the date of birth; Body Age then uses the exact age from it. */
    onSaveBirthday: (LocalDate) -> Unit
) {
    val tokens = LocalHangryTokens.current
    val state by viewModel.uiState.collectAsState()
    val snapshot = state.bodyAge
    val result = snapshot?.current
    var pickingBirthday by remember { mutableStateOf(false) }
    if (pickingBirthday) {
        BirthdayPickerDialog(onDismiss = { pickingBirthday = false }, onPicked = { pickingBirthday = false; onSaveBirthday(it) })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Body Age") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = HangryTokens.Spacing.m, vertical = HangryTokens.Spacing.s),
            verticalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.m)
        ) {
            HangryCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    DashExpression(mood = result?.mood() ?: DashMood.THINKING, size = 140.dp)
                    if (snapshot?.needsBirthday == true) {
                        Text("When's your birthday?", style = MaterialTheme.typography.titleLarge, color = tokens.textPrimary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Body Age compares your body with your exact age, so it needs your date of birth. It stays on your phone.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = tokens.textSecondary
                        )
                        Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))
                        Button(onClick = { pickingBirthday = true }) { Text("Add my birthday") }
                    } else if (result == null) {
                        Text("Not enough data yet", style = MaterialTheme.typography.titleLarge, color = tokens.textPrimary)
                        Spacer(modifier = Modifier.height(6.dp))
                        snapshot?.needs.orEmpty().forEach {
                            Text("• $it", style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary)
                        }
                    } else {
                        Text(
                            String.format(Locale.US, "%.1f", result.bodyAge),
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold,
                            color = tokens.textPrimary
                        )
                        Text(result.comparison(), style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary)
                        snapshot.trend()?.let {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(it, style = MaterialTheme.typography.labelLarge, color = tokens.textMuted)
                        }
                    }
                }
            }

            if (result != null && snapshot.exactAge == false) {
                HangryCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Make it exact", style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                    Text(
                        "You entered your age as a whole number. Add your birthday so Body Age uses your exact age and keeps up as birthdays pass.",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.textSecondary
                    )
                    TextButton(onClick = { pickingBirthday = true }) { Text("Add my birthday") }
                }
            }

            if (result != null) {
                Text("What's shaping it", style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
                result.factors.forEach { FactorRow(it) }
                if (result.missing.isNotEmpty()) {
                    HangryCard(modifier = Modifier.fillMaxWidth()) {
                        Text("Not counted yet", style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                        Text(
                            result.missing.joinToString(", ") + ". These count once there's data - VO₂ max needs a watch that estimates it, body composition needs a weight or a body-fat scan.",
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.textSecondary
                        )
                    }
                }
            }

            Text(
                "Body Age compares your last 30 days - sleep, activity, fitness, heart and body composition - with typical values for your age and sex. It's an estimate to motivate healthy habits, not a medical measurement.",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textMuted
            )
            Spacer(modifier = Modifier.height(HangryTokens.Spacing.l))
        }
    }
}

@Composable
private fun FactorRow(factor: BodyAgeFactor) {
    val tokens = LocalHangryTokens.current
    val younger = factor.years < -0.05
    val older = factor.years > 0.05
    val color: Color = when {
        younger -> tokens.scoreColors.primed
        older -> tokens.scoreColors.rebuild
        else -> tokens.textMuted
    }
    HangryCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(factor.name, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                Text(factor.value, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
            }
            Text(
                text = when {
                    younger -> "−${years(factor.years)} yrs"
                    older -> "+${years(factor.years)} yrs"
                    else -> "±0"
                },
                style = MaterialTheme.typography.labelLarge,
                color = color,
                modifier = Modifier
                    .background(color.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        if (older || !younger) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(factor.tip, style = MaterialTheme.typography.bodySmall, color = tokens.textMuted)
        }
    }
}

/** A date picker for the date of birth, limited to plausible adult birthdays. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BirthdayPickerDialog(initial: LocalDate? = null, onDismiss: () -> Unit, onPicked: (LocalDate) -> Unit) {
    val today = LocalDate.now()
    val utc = ZoneOffset.UTC
    val state = rememberDatePickerState(
        initialSelectedDateMillis = (initial ?: today.minusYears(30)).atStartOfDay(utc).toInstant().toEpochMilli(),
        yearRange = (today.year - 110)..(today.year - 13),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                AgeMath.isPlausible(Instant.ofEpochMilli(utcTimeMillis).atZone(utc).toLocalDate(), today)
        }
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedDateMillis != null,
                onClick = { state.selectedDateMillis?.let { onPicked(Instant.ofEpochMilli(it).atZone(utc).toLocalDate()) } }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) {
        DatePicker(state = state, title = { Text("Your date of birth", modifier = Modifier.padding(start = 24.dp, top = 16.dp)) })
    }
}
