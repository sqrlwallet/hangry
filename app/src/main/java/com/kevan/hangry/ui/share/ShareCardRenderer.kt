package com.kevan.hangry.ui.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.compose.ui.graphics.toArgb
import com.kevan.hangry.R
import com.kevan.hangry.ui.theme.BackgroundDark
import com.kevan.hangry.ui.theme.Cream
import com.kevan.hangry.ui.theme.SurfaceVariantDark
import com.kevan.hangry.ui.theme.TextMutedDark
import com.kevan.hangry.ui.theme.TextSecondaryDark
import kotlin.math.ceil

/**
 * Paints a [ShareCard] as a 1080 x 1350 image (4:5, the size Instagram, X and WhatsApp all show
 * uncropped): the Hangry mark and date along the top, Dash on a glow of the card's accent, the
 * headline, a chart, the stats in a grid and the app link along the bottom. Always dark, so it
 * looks the same whichever theme the phone is in.
 */
class ShareCardRenderer(private val context: Context) {

    private val nunito: Typeface = runCatching { ResourcesCompat.getFont(context, R.font.nunito) }.getOrNull() ?: Typeface.SANS_SERIF

    private fun paint(size: Float, weight: Int, color: Int, align: Paint.Align = Paint.Align.LEFT) =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = nunito
            // Nunito is a variable font: pick the weight on its axis.
            fontVariationSettings = "'wght' $weight"
            textSize = size
            this.color = color
            textAlign = align
        }

    private val brandText = paint(50f, 800, Cream.toArgb())
    private val dateText = paint(34f, 600, TextSecondaryDark.toArgb(), Paint.Align.RIGHT)
    private val headlineText = paint(76f, 800, Cream.toArgb())
    private val messageText = paint(38f, 600, TextSecondaryDark.toArgb())
    private val valueText = paint(72f, 800, Cream.toArgb())
    private val labelText = paint(32f, 600, TextSecondaryDark.toArgb())
    private val compactValueText = paint(56f, 800, Cream.toArgb())
    private val compactLabelText = paint(28f, 600, TextSecondaryDark.toArgb())
    private val axisText = paint(26f, 600, TextMutedDark.toArgb(), Paint.Align.CENTER)
    private val footerText = paint(29f, 600, TextMutedDark.toArgb(), Paint.Align.CENTER)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f }
    private val image = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val goalLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = ColorUtils.setAlphaComponent(Cream.toArgb(), 0x99)
        pathEffect = DashPathEffect(floatArrayOf(14f, 10f), 0f)
    }

    /** The card as an image; a [scale] below 1 makes a smaller copy for previews. */
    fun render(card: ShareCard, scale: Float = 1f): Bitmap {
        val bitmap = Bitmap.createBitmap((WIDTH * scale).toInt(), (HEIGHT * scale).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(scale, scale)
        draw(canvas, card)
        return bitmap
    }

    fun draw(canvas: Canvas, card: ShareCard) {
        drawBackground(canvas)
        drawHeader(canvas, card)

        // Everything between the header and footer is laid out first, then centred, with Dash
        // taking whatever height the text and stats leave.
        val contentWidth = WIDTH - 2 * MARGIN
        val headline = layout(card.headline, headlineText, contentWidth, Layout.Alignment.ALIGN_CENTER, maxLines = 2)
        val message = card.message?.let { layout(it, messageText, contentWidth, Layout.Alignment.ALIGN_CENTER, maxLines = 2) }
        val columns = if (card.stats.size == 3) 3 else 2
        val tileHeight = if (columns == 3) COMPACT_TILE_HEIGHT else TILE_HEIGHT
        val rows = ceil(card.stats.size / columns.toDouble()).toInt()
        val statsHeight = if (rows == 0) 0 else rows * tileHeight + (rows - 1) * GAP
        val textHeight = headline.height + (message?.let { it.height + 12 } ?: 0)
        val available = FOOTER_TOP - CONTENT_TOP - 2 * GAP
        val fixed = textHeight + (if (card.chart != null) CHART_HEIGHT + SECTION_GAP else 0) +
            (if (rows > 0) statsHeight + SECTION_GAP else 0) + SECTION_GAP
        val dashSize = (available - fixed).coerceIn(MIN_DASH, MAX_DASH)
        var y = CONTENT_TOP + GAP + ((available - fixed - dashSize) / 2).coerceAtLeast(0)

        drawDash(canvas, card, y, dashSize)
        y += dashSize + SECTION_GAP
        drawLayout(canvas, headline, MARGIN.toFloat(), y.toFloat())
        y += headline.height
        message?.let {
            y += 12
            drawLayout(canvas, it, MARGIN.toFloat(), y.toFloat())
            y += it.height
        }
        card.chart?.let {
            y += SECTION_GAP
            when (it.type) {
                ChartType.BARS -> drawBars(canvas, it, y)
                ChartType.DONUT -> drawDonut(canvas, it, y)
            }
            y += CHART_HEIGHT
        }
        if (rows > 0) drawStats(canvas, card.stats, y + SECTION_GAP, columns, tileHeight)

        drawFooter(canvas)
    }

    private fun drawBackground(canvas: Canvas) {
        fill.shader = LinearGradient(0f, 0f, 0f, HEIGHT.toFloat(), TOP_COLOR, BackgroundDark.toArgb(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), fill)
        fill.shader = null
    }

    private fun drawHeader(canvas: Canvas, card: ShareCard) {
        val baseline = HEADER_CENTER + 18f
        ContextCompat.getDrawable(context, R.drawable.hangry_logo)?.let { logo ->
            // The mark sits in the middle of a 108-unit canvas; draw it larger so it fills ~60px.
            logo.setBounds(MARGIN - 34, HEADER_CENTER - 64, MARGIN - 34 + 128, HEADER_CENTER + 64)
            logo.draw(canvas)
        }
        canvas.drawText(context.getString(R.string.share_card_brand), MARGIN + 82f, baseline, brandText)
        canvas.drawText(card.dateLabel, (WIDTH - MARGIN).toFloat(), baseline - 2f, dateText)
    }

    private fun drawDash(canvas: Canvas, card: ShareCard, top: Int, size: Int) {
        val cx = WIDTH / 2f
        val cy = top + size / 2f
        fill.shader = RadialGradient(
            cx, cy, size * 0.62f,
            intArrayOf(ColorUtils.setAlphaComponent(card.accent, 0x66), ColorUtils.setAlphaComponent(card.accent, 0x1A), 0),
            floatArrayOf(0f, 0.6f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, size * 0.62f, fill)
        fill.shader = null
        val dash = BitmapFactory.decodeResource(context.resources, card.mood.imageRes) ?: return
        val left = ((WIDTH - size) / 2)
        canvas.drawBitmap(dash, null, Rect(left, top, left + size, top + size), image)
        dash.recycle()
    }

    private fun drawStats(canvas: Canvas, stats: List<ShareStat>, top: Int, columns: Int, tileHeight: Int) {
        val compact = columns == 3
        val tileWidth = (WIDTH - 2 * MARGIN - (columns - 1) * GAP) / columns
        val value = if (compact) compactValueText else valueText
        val label = if (compact) compactLabelText else labelText
        stats.forEachIndexed { index, stat ->
            val row = index / columns
            val column = index % columns
            val alone = index == stats.lastIndex && column == 0 && columns == 2
            // An odd last tile sits in the middle rather than hanging off the left.
            val left = if (alone) (WIDTH - tileWidth) / 2 else MARGIN + column * (tileWidth + GAP)
            val y = top + row * (tileHeight + GAP)
            val rect = RectF(left.toFloat(), y.toFloat(), (left + tileWidth).toFloat(), (y + tileHeight).toFloat())
            fill.color = ColorUtils.setAlphaComponent(SurfaceVariantDark.toArgb(), 0xD9)
            canvas.drawRoundRect(rect, TILE_RADIUS, TILE_RADIUS, fill)
            stroke.color = ColorUtils.setAlphaComponent(stat.color, 0x55)
            canvas.drawRoundRect(rect, TILE_RADIUS, TILE_RADIUS, stroke)
            // A short bar of the stat's colour down the left edge.
            fill.color = stat.color
            canvas.drawRoundRect(rect.left + 24f, rect.top + 36f, rect.left + 32f, rect.bottom - 36f, 4f, 4f, fill)

            val textLeft = rect.left + 52f
            val textWidth = (tileWidth - 72).toFloat()
            value.color = stat.color
            canvas.drawText(ellipsize(stat.value, value, textWidth), textLeft, rect.top + if (compact) 80f else 96f, value)
            canvas.drawText(ellipsize(stat.label, label, textWidth), textLeft, rect.top + if (compact) 124f else 146f, label)
        }
    }

    /** One bar per day, scaled to the highest day (or the goal, if higher); empty days are a faint stub. */
    private fun drawBars(canvas: Canvas, chart: ShareChart, top: Int) {
        val points = chart.points
        if (points.isEmpty()) return
        val left = MARGIN.toFloat()
        val width = (WIDTH - 2 * MARGIN).toFloat()
        val bottom = top + CHART_HEIGHT - AXIS_HEIGHT.toFloat()
        val plotHeight = bottom - top - 12f
        val max = (listOfNotNull(points.mapNotNull { it.value }.maxOrNull(), chart.goal).maxOrNull() ?: 0.0).coerceAtLeast(1e-6) * 1.08
        val slot = width / points.size
        val barWidth = (slot * 0.64f).coerceAtMost(84f)
        val radius = (barWidth / 2).coerceAtMost(14f)

        fill.color = ColorUtils.setAlphaComponent(Cream.toArgb(), 0x1F)
        canvas.drawRect(left, bottom, left + width, bottom + 2f, fill)
        points.forEachIndexed { i, point ->
            val cx = left + slot * (i + 0.5f)
            val value = point.value
            if (value == null || value <= 0) {
                fill.color = ColorUtils.setAlphaComponent(Cream.toArgb(), 0x26)
                canvas.drawRoundRect(cx - barWidth / 2, bottom - 8f, cx + barWidth / 2, bottom, 4f, 4f, fill)
            } else {
                val h = (value / max * plotHeight).toFloat().coerceAtLeast(8f)
                // The paint's own alpha would fade the gradient too.
                fill.color = point.color
                fill.shader = LinearGradient(0f, bottom - h, 0f, bottom, point.color, ColorUtils.setAlphaComponent(point.color, 0x8C), Shader.TileMode.CLAMP)
                canvas.drawRoundRect(cx - barWidth / 2, bottom - h, cx + barWidth / 2, bottom, radius, radius, fill)
                fill.shader = null
            }
            if (point.label.isNotEmpty()) canvas.drawText(point.label, cx, bottom + AXIS_HEIGHT - 8f, axisText)
        }
        chart.goal?.takeIf { it > 0 }?.let { goal ->
            val y = bottom - (goal / max * plotHeight).toFloat()
            canvas.drawLine(left, y, left + width, y, goalLine)
        }
    }

    /** A ring split by each point's share of the total, with a small gap between slices. */
    private fun drawDonut(canvas: Canvas, chart: ShareChart, top: Int) {
        val total = chart.points.sumOf { it.value ?: 0.0 }
        if (total <= 0) return
        val thickness = 56f
        val radius = CHART_HEIGHT / 2f - thickness / 2 - 8f
        val cx = WIDTH / 2f
        val cy = top + CHART_HEIGHT / 2f
        val ring = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        stroke.strokeWidth = thickness
        var angle = -90f
        chart.points.forEach { point ->
            val sweep = ((point.value ?: 0.0) / total * 360).toFloat()
            if (sweep <= 0f) return@forEach
            stroke.color = point.color
            canvas.drawArc(ring, angle + 1.5f, (sweep - 3f).coerceAtLeast(1f), false, stroke)
            angle += sweep
        }
        stroke.strokeWidth = 2f
    }

    private fun drawFooter(canvas: Canvas) {
        val text = context.getString(R.string.share_card_footer, ShareCards.APP_URL_SHORT)
        val width = footerText.measureText(text) + 64f
        val rect = RectF((WIDTH - width) / 2f, FOOTER_TOP + 30f, (WIDTH + width) / 2f, FOOTER_TOP + 96f)
        fill.color = ColorUtils.setAlphaComponent(SurfaceVariantDark.toArgb(), 0xB3)
        canvas.drawRoundRect(rect, rect.height() / 2, rect.height() / 2, fill)
        canvas.drawText(text, WIDTH / 2f, rect.centerY() + 10f, footerText)
    }

    private fun layout(text: String, paint: TextPaint, width: Int, align: Layout.Alignment, maxLines: Int) =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(align)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setLineSpacing(0f, 1.0f)
            .build()

    private fun drawLayout(canvas: Canvas, layout: StaticLayout, x: Float, y: Float) {
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun ellipsize(text: String, paint: TextPaint, width: Float) =
        TextUtils.ellipsize(text, paint, width, TextUtils.TruncateAt.END).toString()

    companion object {
        const val WIDTH = 1080
        const val HEIGHT = 1350
        private const val MARGIN = 80
        private const val GAP = 24
        private const val SECTION_GAP = 36
        private const val HEADER_CENTER = 110
        private const val CONTENT_TOP = 170
        private const val FOOTER_TOP = 1220
        private const val TILE_HEIGHT = 180
        private const val COMPACT_TILE_HEIGHT = 150
        private const val CHART_HEIGHT = 320
        private const val AXIS_HEIGHT = 44
        private const val TILE_RADIUS = 40f
        private const val MIN_DASH = 200
        private const val MAX_DASH = 520
        private val TOP_COLOR = 0xFF2B2630.toInt()
    }
}
