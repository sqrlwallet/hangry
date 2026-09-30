package com.kevan.hangry.ui.sleep

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kevan.hangry.data.local.entity.SleepSessionEntity
import com.kevan.hangry.domain.model.SleepStage
import com.kevan.hangry.domain.model.SleepStageSegment
import com.kevan.hangry.ui.theme.LocalHangryTokens
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** The night counted for [date], picked the way daily summaries pick it: the longest session
 * that ends that day and starts no earlier than 6pm the evening before. */
internal fun primaryNightFor(date: LocalDate, sessions: List<SleepSessionEntity>, zone: ZoneId): SleepSessionEntity? {
    val dayStart = date.atStartOfDay(zone).toInstant()
    val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant()
    val from = dayStart.minus(6, ChronoUnit.HOURS)
    return sessions.filter { !it.startTime.isBefore(from) && !it.endTime.isAfter(dayEnd) }
        .maxByOrNull { it.durationMinutes }
}

/** Awake at the top, deep at the bottom - as sleep apps draw it. Unstaged sleep sits with light. */
private fun SleepStage.lane(): Int = when (this) {
    SleepStage.AWAKE -> 0
    SleepStage.REM -> 1
    SleepStage.LIGHT, SleepStage.ASLEEP -> 2
    SleepStage.DEEP -> 3
}

@Composable
private fun SleepStage.color(): Color {
    val c = LocalHangryTokens.current.chartColors
    return when (this) {
        SleepStage.AWAKE -> c.sleepAwake
        SleepStage.REM -> c.sleepRem
        SleepStage.LIGHT -> c.sleepLight
        SleepStage.ASLEEP -> c.sleep
        SleepStage.DEEP -> c.sleepDeep
    }
}

@Composable
private fun timeFormatter(): DateTimeFormatter =
    DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(LocalContext.current)) "HH:mm" else "h:mm a")

/** The night as a hypnogram: which stage you were in, minute by minute. */
@Composable
fun SleepHypnogram(segments: List<SleepStageSegment>, modifier: Modifier = Modifier) {
    if (segments.isEmpty()) return
    val tokens = LocalHangryTokens.current
    val sorted = segments.sortedBy { it.start }
    val start = sorted.first().start
    val end = sorted.maxOf { it.end }
    val totalSec = maxOf(1L, end.epochSecond - start.epochSecond).toFloat()
    val colors = SleepStage.entries.associateWith { it.color() }
    val grid = tokens.cardBorder
    val laneNames = listOf("Awake", "REM", "Light", "Deep")
    val zone = ZoneId.systemDefault()
    val fmt = timeFormatter()

    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().height(148.dp)) {
            Column(
                modifier = Modifier.fillMaxHeight().width(44.dp),
                verticalArrangement = Arrangement.SpaceAround
            ) {
                laneNames.forEach {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
                }
            }
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .semantics { contentDescription = "Sleep stages through the night" }
            ) {
                val laneH = size.height / 4f
                val barH = minOf(laneH * 0.55f, 14.dp.toPx())
                fun x(t: java.time.Instant) = (t.epochSecond - start.epochSecond) / totalSec * size.width
                fun laneY(lane: Int) = laneH * lane + laneH / 2f

                for (lane in 0 until 4) {
                    drawLine(
                        grid, Offset(0f, laneY(lane)), Offset(size.width, laneY(lane)),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx()))
                    )
                }

                // Connectors between one stage and the next, fading from one colour to the other.
                sorted.zipWithNext().forEach { (a, b) ->
                    if (a.stage.lane() == b.stage.lane() || b.start.isAfter(a.end.plusSeconds(60))) return@forEach
                    val bx = x(b.start)
                    val ya = laneY(a.stage.lane())
                    val yb = laneY(b.stage.lane())
                    drawLine(
                        brush = Brush.verticalGradient(
                            listOf(colors.getValue(a.stage), colors.getValue(b.stage)),
                            startY = ya, endY = yb
                        ),
                        start = Offset(bx, ya), end = Offset(bx, yb),
                        strokeWidth = 1.5.dp.toPx()
                    )
                }

                sorted.forEach { seg ->
                    val left = x(seg.start)
                    val w = maxOf(x(seg.end) - left, 2.dp.toPx())
                    val y = laneY(seg.stage.lane())
                    // Awake spells are drawn as tall ticks so brief wake-ups still show.
                    val h = if (seg.stage == SleepStage.AWAKE) laneH * 0.8f else barH
                    drawRoundRect(
                        color = colors.getValue(seg.stage),
                        topLeft = Offset(left, y - h / 2f),
                        size = Size(w, h),
                        cornerRadius = CornerRadius(minOf(w, h) / 3f)
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth().padding(start = 44.dp)) {
            val ticks = (0..3).map { start.plusSeconds((totalSec * it / 3f).toLong()) }
            ticks.forEachIndexed { i, t ->
                Text(
                    t.atZone(zone).format(fmt),
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.textMuted,
                    textAlign = when (i) { 0 -> TextAlign.Start; 3 -> TextAlign.End; else -> TextAlign.Center },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** Share of the night healthy adults usually spend in each stage. */
private val TYPICAL_RANGE = mapOf(
    SleepStage.AWAKE to 0..10,
    SleepStage.REM to 20..25,
    SleepStage.LIGHT to 45..55,
    SleepStage.DEEP to 13..23
)

/** Each stage as a bar of the night, with the typical range hatched over it. */
@Composable
fun SleepStageBreakdown(deep: Int, rem: Int, light: Int, awake: Int, modifier: Modifier = Modifier) {
    val tokens = LocalHangryTokens.current
    val total = maxOf(1, deep + rem + light + awake)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(
            Triple(SleepStage.AWAKE, "Awake", awake),
            Triple(SleepStage.REM, "REM", rem),
            Triple(SleepStage.LIGHT, "Light", light),
            Triple(SleepStage.DEEP, "Deep", deep)
        ).forEach { (stage, name, minutes) ->
            val pct = (minutes * 100f / total).let { kotlin.math.round(it).toInt() }
            val color = stage.color()
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                    Spacer(Modifier.width(6.dp))
                    Text("$pct%", style = MaterialTheme.typography.titleSmall, color = color, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Text(formatMinutes(minutes), style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                }
                Spacer(Modifier.height(4.dp))
                StageRangeBar(fraction = minutes.toFloat() / total, range = TYPICAL_RANGE.getValue(stage), color = color)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val hatch = tokens.textSecondary
            Canvas(Modifier.size(12.dp)) { drawHatch(hatch, Offset.Zero, size) }
            Spacer(Modifier.width(6.dp))
            Text("Typical range", style = MaterialTheme.typography.labelMedium, color = tokens.textSecondary)
        }
    }
}

@Composable
private fun StageRangeBar(fraction: Float, range: IntRange, color: Color) {
    val tokens = LocalHangryTokens.current
    val track = tokens.cardBorder
    val hatch = tokens.textSecondary
    Canvas(modifier = Modifier.fillMaxWidth().height(22.dp)) {
        val r = CornerRadius(size.height / 2f)
        drawRoundRect(track, size = size, cornerRadius = r)
        val inset = 3.dp.toPx()
        val innerH = size.height - inset * 2
        val innerW = size.width - inset * 2
        val fillW = (innerW * fraction.coerceIn(0f, 1f)).coerceAtLeast(if (fraction > 0f) innerH else 0f)
        if (fillW > 0f) {
            drawRoundRect(color, topLeft = Offset(inset, inset), size = Size(fillW, innerH), cornerRadius = CornerRadius(innerH / 2f))
        }
        val from = inset + innerW * range.first / 100f
        val to = inset + innerW * range.last / 100f
        drawHatch(hatch, Offset(from, inset), Size(to - from, innerH))
    }
}

private fun DrawScope.drawHatch(color: Color, topLeft: Offset, area: Size) {
    val clip = Path().apply { addRoundRect(RoundRect(topLeft.x, topLeft.y, topLeft.x + area.width, topLeft.y + area.height, CornerRadius(2.dp.toPx()))) }
    clipPath(clip) {
        val step = 4.dp.toPx()
        var x = topLeft.x - area.height
        while (x < topLeft.x + area.width) {
            drawLine(
                color.copy(alpha = 0.6f),
                Offset(x, topLeft.y + area.height), Offset(x + area.height, topLeft.y),
                strokeWidth = 1.dp.toPx()
            )
            x += step
        }
    }
}

/** One night in the weekly stage chart. Stage minutes are null when the device didn't record stages. */
data class NightStages(
    val date: LocalDate,
    val asleepMinutes: Int,
    val deep: Int?,
    val rem: Int?,
    val light: Int?,
    val awake: Int?
)

internal fun weekOfNights(endDate: LocalDate, sessions: List<SleepSessionEntity>, zone: ZoneId): List<NightStages> =
    (6 downTo 0).map { endDate.minusDays(it.toLong()) }.map { date ->
        val night = primaryNightFor(date, sessions, zone)
        NightStages(
            date = date,
            asleepMinutes = night?.durationMinutes ?: 0,
            deep = night?.deepSleepMinutes,
            rem = night?.remSleepMinutes,
            light = night?.lightSleepMinutes,
            awake = night?.awakeMinutes
        )
    }

/** Seven nights as stacked stage columns (deep at the bottom, awake on top) with the average. */
@Composable
fun WeeklySleepStagesChart(nights: List<NightStages>, selectedDate: LocalDate, modifier: Modifier = Modifier) {
    val tokens = LocalHangryTokens.current
    val c = tokens.chartColors
    val recorded = nights.filter { it.asleepMinutes > 0 }
    val average = recorded.takeIf { it.isNotEmpty() }?.map { it.asleepMinutes }?.average()?.toInt()
    val maxMinutes = maxOf(
        60,
        nights.maxOfOrNull { n -> n.asleepMinutes + (n.awake ?: 0) } ?: 0,
        average ?: 0
    )
    val grid = tokens.cardBorder
    val avgColor = tokens.textMuted

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            "Average: " + (average?.let { formatMinutes(it) } ?: "—"),
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.textSecondary
        )
        Spacer(Modifier.height(12.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .semantics { contentDescription = "Sleep stages over the last 7 nights" }
        ) {
            val slot = size.width / nights.size
            val barW = minOf(slot * 0.36f, 18.dp.toPx())
            val scale = size.height / (maxMinutes * 1.08f)
            average?.let {
                val y = size.height - it * scale
                drawLine(
                    avgColor, Offset(0f, y), Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 4.dp.toPx()))
                )
            }
            drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())

            nights.forEachIndexed { i, n ->
                if (n.asleepMinutes <= 0) return@forEachIndexed
                val left = slot * i + (slot - barW) / 2f
                val staged = listOfNotNull(n.deep, n.light, n.rem).sum()
                // Deep, light and REM stacked; any unstaged sleep in the general sleep colour; awake on top.
                val parts = if (n.deep != null && n.rem != null) listOf(
                    n.deep to c.sleepDeep,
                    (n.light ?: 0) to c.sleepLight,
                    maxOf(0, n.asleepMinutes - staged) to c.sleep,
                    n.rem to c.sleepRem,
                    (n.awake ?: 0) to c.sleepAwake
                ) else listOf(n.asleepMinutes to c.sleep)
                val totalH = parts.sumOf { it.first } * scale
                val path = Path().apply {
                    addRoundRect(RoundRect(left, size.height - totalH, left + barW, size.height, CornerRadius(barW / 2f)))
                }
                clipPath(path) {
                    var y = size.height
                    parts.forEach { (minutes, color) ->
                        if (minutes <= 0) return@forEach
                        val h = minutes * scale
                        drawRect(color, topLeft = Offset(left, y - h), size = Size(barW, h))
                        y -= h
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            nights.forEach { n ->
                Text(
                    n.date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (n.date == selectedDate) tokens.chartColors.sleep else tokens.textMuted,
                    fontWeight = if (n.date == selectedDate) FontWeight.Bold else null,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private fun formatMinutes(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}
