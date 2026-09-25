package com.kevan.hangry.ui.training

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kevan.hangry.domain.model.LongevityPillar
import com.kevan.hangry.domain.model.LongevityWeek
import com.kevan.hangry.domain.model.PillarProgress
import com.kevan.hangry.ui.components.HangryCard
import com.kevan.hangry.ui.components.HangryInfoIconButton
import com.kevan.hangry.ui.components.HangryInfoSection
import com.kevan.hangry.ui.theme.LocalHangryTokens
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val LONGEVITY_INFO = LongevityPillar.entries.map { HangryInfoSection("${it.label} · ${it.goal} ${it.unit}/week", it.why) } +
    HangryInfoSection(
        "How it's counted",
        "Strength and mobility come from workouts you log (strength training, weightlifting, yoga, pilates, stretching...). Cardio minutes come from time in your own heart-rate zones, so they need a watch. Balance - and mobility you didn't log as a workout - you tick off by tapping the day."
    )

private val DAY_FORMAT = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())

@Composable
private fun LongevityPillar.color(): Color {
    val tokens = LocalHangryTokens.current
    return when (this) {
        LongevityPillar.STRENGTH -> tokens.chartColors.trainingLoad
        LongevityPillar.ZONE2 -> tokens.chartColors.zones[1]
        LongevityPillar.HIGH_INTENSITY -> tokens.chartColors.zones[4]
        LongevityPillar.MOBILITY -> tokens.chartColors.hrv
        LongevityPillar.BALANCE -> tokens.chartColors.sleep
    }
}

/**
 * The week's five longevity pillars: a radar of how close each is to its weekly target, then a
 * row per pillar. Daily pillars show the week's days, and balance/mobility days can be ticked.
 */
@Composable
fun LongevityCard(
    week: LongevityWeek,
    today: LocalDate,
    onToggleDay: (LongevityPillar, LocalDate, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalHangryTokens.current
    HangryCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.SelfImprovement, contentDescription = null, tint = tokens.chartColors.hrv, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Longevity pillars", style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
                    HangryInfoIconButton(title = "Longevity pillars", sections = LONGEVITY_INFO, compact = true)
                }
                Text(
                    "${week.start.format(DAY_FORMAT)} – ${week.end.format(DAY_FORMAT)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.textMuted
                )
            }
            Surface(color = tokens.cardBorder, shape = RoundedCornerShape(8.dp)) {
                Text(
                    "${week.pillarsMet}/5 done",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (week.pillarsMet == 5) tokens.scoreColors.primed else tokens.textSecondary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        PillarRadar(week, Modifier.fillMaxWidth().height(230.dp).padding(vertical = 8.dp))

        week.pillars.forEach { progress ->
            PillarRow(progress, week, today, onToggleDay)
        }
        Text(
            "Tap a day to tick off balance or mobility practice.",
            style = MaterialTheme.typography.labelSmall,
            color = tokens.textMuted,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

/** Five axes, one per pillar; the shape reaches the edge where a weekly target is met. */
@Composable
private fun PillarRadar(week: LongevityWeek, modifier: Modifier = Modifier) {
    val tokens = LocalHangryTokens.current
    val colors = week.pillars.map { it.pillar.color() }
    val gridColor = tokens.cardBorder
    val fill = tokens.chartColors.hrv
    val labelColor = tokens.textSecondary.toArgb()
    val labelSize = with(LocalDensity.current) { 11.sp.toPx() }
    val description = week.pillars.joinToString(", ") { "${it.pillar.label} ${(it.fraction * 100).toInt()}%" }
    Canvas(modifier.semantics { contentDescription = "Longevity pillars this week: $description" }) {
        val center = Offset(size.width / 2, size.height / 2 + 6.dp.toPx())
        val radius = minOf(size.width, size.height) / 2 - 30.dp.toPx()
        fun point(i: Int, r: Float): Offset {
            val angle = -PI / 2 + i * 2 * PI / 5
            return Offset(center.x + (r * cos(angle)).toFloat(), center.y + (r * sin(angle)).toFloat())
        }
        // Grid: rings at 25/50/75/100% and the spokes.
        listOf(0.25f, 0.5f, 0.75f, 1f).forEach { level ->
            val ring = Path().apply {
                (0 until 5).forEach { i -> point(i, radius * level).let { if (i == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) } }
                close()
            }
            drawPath(ring, gridColor, style = Stroke(width = if (level == 1f) 1.5.dp.toPx() else 1.dp.toPx()))
        }
        (0 until 5).forEach { i -> drawLine(gridColor, center, point(i, radius), strokeWidth = 1.dp.toPx()) }

        // This week's shape; a sliver at the centre for untouched pillars so it stays readable.
        val shape = Path().apply {
            week.pillars.forEachIndexed { i, p ->
                point(i, radius * p.fraction.coerceAtLeast(0.04f)).let { if (i == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) }
            }
            close()
        }
        drawPath(shape, fill.copy(alpha = 0.22f))
        drawPath(shape, fill, style = Stroke(width = 2.dp.toPx()))
        week.pillars.forEachIndexed { i, p ->
            drawCircle(colors[i], radius = 4.dp.toPx(), center = point(i, radius * p.fraction.coerceAtLeast(0.04f)))
        }

        // Axis labels just outside the outer ring.
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = labelColor
            textSize = labelSize
            textAlign = android.graphics.Paint.Align.CENTER
        }
        week.pillars.forEachIndexed { i, p ->
            val at = point(i, radius + 16.dp.toPx())
            val short = when (p.pillar) {
                LongevityPillar.ZONE2 -> "Zone 2"
                LongevityPillar.HIGH_INTENSITY -> "Zone 4–5"
                else -> p.pillar.label
            }
            drawContext.canvas.nativeCanvas.drawText(short, at.x, at.y + labelSize / 3, paint)
        }
    }
}

@Composable
private fun PillarRow(
    progress: PillarProgress,
    week: LongevityWeek,
    today: LocalDate,
    onToggleDay: (LongevityPillar, LocalDate, Boolean) -> Unit
) {
    val tokens = LocalHangryTokens.current
    val color = progress.pillar.color()
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(8.dp))
            Text(progress.pillar.label, style = MaterialTheme.typography.bodyMedium, color = tokens.textPrimary, modifier = Modifier.weight(1f))
            Text(
                when {
                    !progress.measurable -> "Needs a watch"
                    else -> "${progress.value} / ${progress.pillar.goal} ${progress.pillar.unit}"
                },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (progress.met) FontWeight.SemiBold else FontWeight.Normal,
                color = if (progress.met) tokens.scoreColors.primed else tokens.textSecondary
            )
        }
        LinearProgressIndicator(
            progress = { progress.fraction },
            color = color,
            trackColor = tokens.cardBorder,
            strokeCap = StrokeCap.Round,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(5.dp)
        )
        if (progress.pillar.daily || progress.pillar == LongevityPillar.STRENGTH) {
            val tickable = progress.pillar == LongevityPillar.MOBILITY || progress.pillar == LongevityPillar.BALANCE
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                week.days.forEach { day ->
                    val done = day in progress.days
                    val canTick = tickable && !day.isAfter(today) &&
                        // A day filled by a logged workout can't be un-ticked here.
                        (!done || day in progress.checkedDays)
                    DayDot(
                        label = day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                        done = done,
                        isToday = day == today,
                        color = color,
                        onClick = if (canTick) ({ onToggleDay(progress.pillar, day, !done) }) else null,
                        description = "${progress.pillar.label} ${day.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())}: ${if (done) "done" else "not done"}"
                    )
                }
            }
        }
    }
}

@Composable
private fun DayDot(label: String, done: Boolean, isToday: Boolean, color: Color, onClick: (() -> Unit)?, description: String) {
    val tokens = LocalHangryTokens.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(if (done) color else Color.Transparent)
                .border(1.5.dp, if (done) color else if (isToday) tokens.textSecondary else tokens.cardBorder, CircleShape)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = if (done) Color.White else if (isToday) tokens.textPrimary else tokens.textMuted
            )
        }
    }
}
