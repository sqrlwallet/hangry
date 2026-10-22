package com.kevan.hangry.data.report

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.text.format.DateFormat
import androidx.annotation.StringRes
import androidx.core.graphics.PathParser
import com.kevan.hangry.R
import com.kevan.hangry.domain.calculation.HealthMarkerCalculator
import com.kevan.hangry.domain.model.BiologicalSex
import com.kevan.hangry.domain.model.BodyUnits
import com.kevan.hangry.domain.model.LengthUnit
import com.kevan.hangry.domain.model.MarkerType
import com.kevan.hangry.domain.model.MetricTone
import com.kevan.hangry.domain.model.RecordSource
import com.kevan.hangry.domain.model.WeightUnit
import com.kevan.hangry.domain.report.DoctorReport
import com.kevan.hangry.domain.report.ReportSection
import com.kevan.hangry.domain.report.TrendChart
import com.kevan.hangry.domain.report.TrendPoint
import com.kevan.hangry.domain.report.VitalKind
import com.kevan.hangry.domain.report.VitalStat
import java.io.OutputStream
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** Display units for the report - the same ones the user picked for the app. */
data class ReportUnits(val weight: WeightUnit, val length: LengthUnit)

/**
 * Lays a [DoctorReport] out as a printable PDF with Android's own [PdfDocument]: no library, no
 * network, everything drawn on the phone. Content is a list of measured blocks that are paginated
 * first (so every page knows the page count) and then drawn; tables repeat their header on each
 * page they run onto, and a heading never sits alone at the foot of a page.
 *
 * All sizes are PostScript points (1/72 inch). Letter paper where it's the norm, A4 elsewhere.
 */
class DoctorReportPdf(
    private val context: Context,
    private val units: ReportUnits,
    private val locale: Locale = Locale.getDefault()
) {
    private val letter = locale.country in LETTER_COUNTRIES
    internal val pageWidth = if (letter) 612 else 595
    internal val pageHeight = if (letter) 792 else 842
    private val contentWidth = (pageWidth - 2 * MARGIN).toFloat()
    private val bodyTop = MARGIN + RUNNING_HEADER_HEIGHT
    private val bodyBottom = pageHeight - MARGIN - FOOTER_HEIGHT

    private val numbers = NumberFormat.getNumberInstance(locale)
    private val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    private val shortDateFormat = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "MMMd"), locale)
    private val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)

    // ---- Type -----------------------------------------------------------------------------

    private val regular = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    private val medium = Typeface.create(regular, 500, false)
    private val bold = Typeface.create(regular, 700, false)

    private fun paint(size: Float, face: Typeface = regular, color: Int = Ink.BODY, spacing: Float = 0f) =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            typeface = face
            this.color = color
            letterSpacing = spacing
        }

    private val titleText = paint(24f, bold, Ink.TEXT, -0.01f)
    private val subtitleText = paint(11f, medium, Ink.BODY)
    private val sectionText = paint(12.5f, bold, Ink.TEXT)
    private val sectionNoteText = paint(7.5f, regular, Ink.MUTED)
    private val bodyText = paint(8.5f, regular, Ink.BODY)
    private val bodyBoldText = paint(8.5f, bold, Ink.TEXT)
    private val smallText = paint(7.5f, regular, Ink.MUTED)
    private val labelText = paint(6.5f, bold, Ink.MUTED, 0.08f)
    private val tileValueText = paint(17f, bold, Ink.TEXT)
    private val tileUnitText = paint(7.5f, medium, Ink.MUTED)
    private val statValueText = paint(11.5f, bold, Ink.TEXT)
    private val tableHeadText = paint(6.5f, bold, Ink.MUTED, 0.06f)
    private val tableText = paint(8f, regular, Ink.BODY)
    private val tableStrongText = paint(8f, medium, Ink.TEXT)
    private val chartTitleText = paint(8.5f, bold, Ink.TEXT)
    private val axisText = paint(6.5f, regular, Ink.MUTED)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    private fun str(@StringRes id: Int, vararg args: Any): String = context.getString(id, *args)

    // ---- Entry point ----------------------------------------------------------------------

    /** Writes the PDF to [out] and returns how many pages it has. */
    fun write(report: DoctorReport, out: OutputStream): Int {
        val document = PdfDocument()
        var count = 0
        try {
            forEachPage(report) { index, total, paintPage ->
                val page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create())
                paintPage(page.canvas)
                document.finishPage(page)
                count = total
            }
            document.writeTo(out)
        } finally {
            document.close()
        }
        return count
    }

    /**
     * Lays the report out, then hands each page's painter to [onPage] in order. The painter
     * draws onto any canvas in points, [pageWidth] × [pageHeight] - a PDF page, or a bitmap in tests.
     */
    internal fun forEachPage(report: DoctorReport, onPage: (index: Int, count: Int, paintPage: (Canvas) -> Unit) -> Unit) {
        val pages = paginate(blocks(report))
        pages.forEachIndexed { index, placed ->
            onPage(index, pages.size) { canvas ->
                if (index > 0) drawRunningHeader(canvas, report)
                placed.forEach { (block, y) -> block.draw(canvas, MARGIN.toFloat(), y) }
                drawFooter(canvas, index + 1, pages.size)
            }
        }
    }

    // ---- Pagination -----------------------------------------------------------------------

    private abstract class Block {
        abstract val height: Float
        abstract fun draw(canvas: Canvas, x: Float, y: Float)
        /** Gap above this block, dropped at the top of a page. */
        open val spaceBefore: Float = 0f
        /** Move to the next page together with the following block (headings, table headers). */
        open val keepWithNext: Boolean = false
        /** Drawn first when this block starts a new page (a table's header row). */
        open val repeatOnBreak: Block? = null
        /** Always start on a fresh page. */
        open val pageBreakBefore: Boolean = false
    }

    private fun paginate(blocks: List<Block>): List<List<Pair<Block, Float>>> {
        val pages = mutableListOf(mutableListOf<Pair<Block, Float>>())
        // The first page carries the masthead instead of the running header.
        var top = MARGIN.toFloat()
        var y = top
        fun newPage() {
            pages += mutableListOf<Pair<Block, Float>>()
            top = bodyTop.toFloat()
            y = top
        }
        blocks.forEachIndexed { i, block ->
            if (block.pageBreakBefore && pages.last().isNotEmpty()) newPage()
            // A heading drags along everything chained to it, up to the first real content block.
            var needed = block.height + if (pages.last().isEmpty()) 0f else block.spaceBefore
            var j = i
            while (blocks[j].keepWithNext && j + 1 < blocks.size) {
                j++
                needed += blocks[j].spaceBefore + blocks[j].height
            }
            needed = min(needed, (bodyBottom - bodyTop).toFloat())
            if (y + needed > bodyBottom && pages.last().isNotEmpty()) {
                newPage()
                block.repeatOnBreak?.let { header ->
                    pages.last() += header to y
                    y += header.height
                }
            }
            if (pages.last().isNotEmpty()) y += block.spaceBefore
            pages.last() += block to y
            y += block.height
        }
        return pages
    }

    // ---- Page furniture -------------------------------------------------------------------

    private fun drawRunningHeader(canvas: Canvas, report: DoctorReport) {
        val left = listOfNotNull(str(R.string.report_pdf_title), report.patient.name, periodText(report)).joinToString("  ·  ")
        canvas.drawText(TextUtils.ellipsize(left, smallText, contentWidth - 70f, TextUtils.TruncateAt.END).toString(), MARGIN.toFloat(), MARGIN + 8f, smallText)
        val brand = str(R.string.report_pdf_brand)
        val brandPaint = paint(8.5f, bold, Ink.TEXT)
        val brandX = pageWidth - MARGIN - brandPaint.measureText(brand)
        canvas.drawText(brand, brandX, MARGIN + 8f, brandPaint)
        drawLogo(canvas, brandX - 11f, MARGIN - 2f, 11f)
        rule(canvas, MARGIN + 16f, Ink.HAIRLINE, 0.6f)
    }

    private fun drawFooter(canvas: Canvas, page: Int, pages: Int) {
        val y = (pageHeight - MARGIN).toFloat()
        rule(canvas, y - 14f, Ink.HAIRLINE, 0.6f)
        canvas.drawText(str(R.string.report_pdf_footer), MARGIN.toFloat(), y - 3f, smallText)
        val pageLabel = str(R.string.report_pdf_page, page, pages)
        canvas.drawText(pageLabel, pageWidth - MARGIN - smallText.measureText(pageLabel), y - 3f, smallText)
    }

    private fun rule(canvas: Canvas, y: Float, color: Int, width: Float) {
        stroke.color = color
        stroke.strokeWidth = width
        stroke.pathEffect = null
        canvas.drawLine(MARGIN.toFloat(), y, (pageWidth - MARGIN).toFloat(), y, stroke)
    }

    /**
     * The flame-and-pulse mark from hangry_logo.xml, drawn as paths so it stays sharp in print
     * (a VectorDrawable would be rasterised at its tiny on-page size). [x], [y] is its top-left;
     * it is [size] tall and about 0.72 × [size] wide.
     */
    private fun drawLogo(canvas: Canvas, x: Float, y: Float, size: Float) {
        val scale = size / LOGO_HEIGHT
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(scale, scale)
        canvas.translate(-LOGO_LEFT, -LOGO_TOP)
        fill.color = Ink.LOGO_FLAME
        canvas.drawPath(logoFlame, fill)
        stroke.color = Ink.LOGO_PULSE
        stroke.strokeWidth = 5.5f
        stroke.strokeCap = Paint.Cap.ROUND
        stroke.strokeJoin = Paint.Join.ROUND
        canvas.drawPath(logoPulse, stroke)
        stroke.strokeCap = Paint.Cap.BUTT
        stroke.strokeJoin = Paint.Join.MITER
        canvas.restore()
    }

    private val logoFlame: Path = PathParser.createPathFromPathData(
        "M54,24.8 C69.4,40.2 75.5,54 75.5,64.8 C75.5,77.1 66.3,84.8 54,84.8 C41.7,84.8 32.5,77.1 32.5,64.8 " +
            "C32.5,52.5 40.2,43.2 47.8,34 C46.3,41.7 47.8,47.8 52.5,47.8 C57.1,47.8 57.1,38.6 54,24.8 Z"
    )
    private val logoPulse: Path = PathParser.createPathFromPathData("M38.6,64.8 L47.8,64.8 L50.9,57.1 L57.1,72.5 L60.2,64.8 L69.4,64.8")

    // ---- Content --------------------------------------------------------------------------

    private fun blocks(report: DoctorReport): List<Block> = buildList {
        add(Masthead(report))
        add(TitleBlock(report))
        add(PatientPanel(report))
        report.options.visitNotes?.trim()?.takeIf { it.isNotEmpty() }?.let { add(NotesBlock(it)) }

        if (report.includes(ReportSection.VITALS)) {
            addSection(str(R.string.report_pdf_glance), str(R.string.report_pdf_glance_note, report.days))
            if (report.vitals.isEmpty()) add(Paragraph(str(R.string.report_pdf_no_vitals), smallText))
            else addAll(tileRows(report.vitals))
        }

        if (report.includes(ReportSection.CONDITIONS)) {
            addSection(str(R.string.report_pdf_profile), null)
            add(ProfileColumns(report))
        }

        if (report.includes(ReportSection.SUPPLEMENTS)) {
            addSection(str(R.string.report_pdf_supplements), str(R.string.report_pdf_supplements_note))
            if (report.supplements.isEmpty()) add(Paragraph(str(R.string.report_pdf_no_supplements), smallText))
            else addAll(supplementTable(report))
        }

        if (report.includes(ReportSection.LABS)) {
            addSection(str(R.string.report_pdf_labs), str(R.string.report_pdf_labs_note))
            if (report.markers.isEmpty()) add(Paragraph(str(R.string.report_pdf_no_labs), smallText))
            else addAll(markerTable(report))
        }

        if (report.includes(ReportSection.VITALS) && report.vitals.isNotEmpty()) {
            addSection(str(R.string.report_pdf_vitals), str(R.string.report_pdf_vitals_note, report.days, date(report.start.minusDays(1))))
            addAll(vitalsTable(report))
        }

        if (report.trends.isNotEmpty()) {
            addSection(
                str(R.string.report_pdf_trends),
                str(if (report.trends.first().weekly) R.string.report_pdf_trends_weekly else R.string.report_pdf_trends_daily)
            )
            report.trends.chunked(2).forEach { pair -> add(ChartRow(pair)) }
        }

        if (report.includes(ReportSection.SLEEP)) {
            addSection(str(R.string.report_pdf_sleep), null)
            val sleep = report.sleep
            if (sleep == null) add(Paragraph(str(R.string.report_pdf_no_sleep), smallText))
            else add(
                StatGrid(
                    listOfNotNull(
                        str(R.string.report_pdf_sleep_average) to hoursMinutes(sleep.averageMinutes),
                        str(R.string.report_pdf_sleep_median) to hoursMinutes(sleep.medianMinutes),
                        str(R.string.report_pdf_sleep_range) to "${hoursMinutes(sleep.shortestMinutes.toDouble())} – ${hoursMinutes(sleep.longestMinutes.toDouble())}",
                        str(R.string.report_pdf_sleep_nights) to str(R.string.report_pdf_of_days, sleep.nightsRecorded, report.days),
                        sleep.typicalBedtime?.let { str(R.string.report_pdf_sleep_bedtime) to time(it) },
                        sleep.typicalWakeTime?.let { str(R.string.report_pdf_sleep_wake) to time(it) },
                        sleep.bedtimeVariabilityMinutes?.let { str(R.string.report_pdf_sleep_variability) to str(R.string.report_pdf_plus_minus_minutes, it.roundToInt()) },
                        str(R.string.report_pdf_sleep_short) to nightsText(sleep.nightsUnder6h, sleep.nightsRecorded),
                        str(R.string.report_pdf_sleep_long) to nightsText(sleep.nightsOver9h, sleep.nightsRecorded),
                        sleep.goalMinutes?.let { str(R.string.report_pdf_sleep_goal) to hoursMinutes(it.toDouble()) }
                    )
                )
            )
        }

        if (report.includes(ReportSection.ACTIVITY)) {
            addSection(str(R.string.report_pdf_activity), str(R.string.report_pdf_activity_note))
            val activity = report.activity
            if (activity == null) add(Paragraph(str(R.string.report_pdf_no_activity), smallText))
            else {
                add(
                    StatGrid(
                        listOfNotNull(
                            activity.averageSteps?.let { str(R.string.report_pdf_activity_steps) to whole(it) },
                            activity.averageActiveMinutes?.let { str(R.string.report_pdf_activity_active) to str(R.string.report_pdf_minutes, whole(it)) },
                            activity.stepGoal?.takeIf { activity.daysRecorded > 0 }?.let {
                                str(R.string.report_pdf_activity_goal, whole(it.toDouble())) to str(R.string.report_pdf_of_days, activity.daysMeetingStepGoal, activity.daysRecorded)
                            },
                            str(R.string.report_pdf_activity_workouts) to whole(activity.workouts.toDouble()),
                            str(R.string.report_pdf_activity_per_week) to decimal(activity.workoutsPerWeek, 1),
                            str(R.string.report_pdf_activity_minutes_week) to str(R.string.report_pdf_minutes, whole(activity.workoutMinutesPerWeek))
                        )
                    )
                )
                if (activity.topWorkouts.isNotEmpty()) {
                    add(SubHeading(str(R.string.report_pdf_activity_types)))
                    val weeks = report.days / 7.0
                    addAll(
                        table(
                            listOf(
                                Column(str(R.string.report_pdf_col_activity), 2.2f),
                                Column(str(R.string.report_pdf_col_sessions), 1f, Layout.Alignment.ALIGN_OPPOSITE),
                                Column(str(R.string.report_pdf_col_total_time), 1.2f, Layout.Alignment.ALIGN_OPPOSITE),
                                Column(str(R.string.report_pdf_col_per_week), 1.2f, Layout.Alignment.ALIGN_OPPOSITE)
                            ),
                            activity.topWorkouts.map { w ->
                                listOf(
                                    Cell(w.label, strong = true),
                                    Cell(whole(w.sessions.toDouble())),
                                    Cell(hoursMinutes(w.minutes.toDouble())),
                                    Cell(str(R.string.report_pdf_minutes, whole(w.minutes / weeks)))
                                )
                            }
                        )
                    )
                }
            }
        }

        if (report.includes(ReportSection.NUTRITION)) {
            addSection(str(R.string.report_pdf_nutrition), report.nutrition?.let { str(R.string.report_pdf_nutrition_note, it.daysLogged) })
            val n = report.nutrition
            if (n == null) add(Paragraph(str(R.string.report_pdf_no_nutrition), smallText))
            else add(
                StatGrid(
                    listOf(
                        str(R.string.report_pdf_nutrition_calories) to str(R.string.report_pdf_kcal, whole(n.averageCalories)),
                        str(R.string.report_pdf_nutrition_protein) to grams(n.averageProteinG),
                        str(R.string.report_pdf_nutrition_carbs) to grams(n.averageCarbsG),
                        str(R.string.report_pdf_nutrition_fat) to grams(n.averageFatG),
                        str(R.string.report_pdf_nutrition_fiber) to grams(n.averageFiberG),
                        str(R.string.report_pdf_nutrition_sugar) to grams(n.averageSugarG),
                        str(R.string.report_pdf_nutrition_sodium) to str(R.string.report_pdf_mg, whole(n.averageSodiumMg))
                    )
                )
            )
        }

        val cycle = report.cycle
        if (report.includes(ReportSection.CYCLE) && cycle != null) {
            addSection(str(R.string.report_pdf_cycle), null)
            val stats = cycle.stats
            add(
                StatGrid(
                    listOfNotNull(
                        stats.lastPeriodStart?.let { str(R.string.report_pdf_cycle_last) to date(it) },
                        stats.averageCycleDays?.let { str(R.string.report_pdf_cycle_length) to str(R.string.report_pdf_days, decimal(it, 0)) },
                        stats.averagePeriodDays?.let { str(R.string.report_pdf_cycle_period) to str(R.string.report_pdf_days, decimal(it, 0)) },
                        str(R.string.report_pdf_cycle_logged) to whole(stats.periodCount.toDouble()),
                        stats.predictedNextStart?.takeIf { !report.patient.isPregnant }?.let { str(R.string.report_pdf_cycle_next) to date(it) }
                    )
                )
            )
            if (cycle.startsInPeriod.isNotEmpty()) {
                add(Paragraph(str(R.string.report_pdf_cycle_starts, cycle.startsInPeriod.joinToString(", ") { shortDate(it) }), smallText, spaceBefore = 6f))
            }
        }

        // The full log is reference material, so it comes last.
        if (report.readings.isNotEmpty()) {
            addSection(str(R.string.report_pdf_readings), str(R.string.report_pdf_readings_note, report.readings.size))
            addAll(readingsTable(report))
        }

        addSection(str(R.string.report_pdf_about), null)
        add(Paragraph(aboutText(report), smallText))
    }

    private fun MutableList<Block>.addSection(title: String, note: String?) {
        add(SectionHeading(title, note))
    }

    private fun periodText(report: DoctorReport) = "${date(report.start)} – ${date(report.end)}"

    private fun aboutText(report: DoctorReport): String {
        val sources = report.dataSources.takeIf { it.isNotEmpty() }?.joinToString(", ")
        return listOfNotNull(
            str(R.string.report_pdf_about_generated, date(report.generatedAt.toLocalDate()), time(report.generatedAt.toLocalTime())),
            sources?.let { str(R.string.report_pdf_about_sources, it) } ?: str(R.string.report_pdf_about_sources_unknown),
            str(R.string.report_pdf_about_method, report.days),
            str(R.string.report_pdf_about_ranges),
            str(R.string.report_pdf_about_disclaimer)
        ).joinToString("\n\n")
    }

    // ---- Formatting -----------------------------------------------------------------------

    private fun decimal(value: Double, decimals: Int): String = numbers.apply {
        minimumFractionDigits = decimals
        maximumFractionDigits = decimals
    }.format(value)

    private fun whole(value: Double): String = decimal(value, 0)
    private fun grams(value: Double) = str(R.string.report_pdf_grams, whole(value))
    private fun date(value: LocalDate): String = value.format(dateFormat)
    private fun shortDate(value: LocalDate): String = value.format(shortDateFormat).replace('\u202F', ' ').replace('\u00A0', ' ')
    private fun time(value: LocalTime): String = value.format(timeFormat)

    private fun hoursMinutes(minutes: Double): String {
        val total = minutes.roundToInt()
        return if (total >= 60) str(R.string.report_pdf_hours_minutes, total / 60, total % 60) else str(R.string.report_pdf_minutes, total.toString())
    }

    private fun nightsText(count: Int, of: Int): String =
        if (of == 0) "—" else str(R.string.report_pdf_nights_percent, count, (count * 100.0 / of).roundToInt())

    private val weightLabel get() = if (units.weight == WeightUnit.KG) str(R.string.report_pdf_unit_kg) else str(R.string.report_pdf_unit_lb)

    private fun weight(kg: Double): String = "${decimal(BodyUnits.weightIn(kg, units.weight), 1)} $weightLabel"

    private fun length(cm: Double): String = if (units.length == LengthUnit.CM) {
        str(R.string.report_pdf_cm, whole(cm))
    } else {
        val (feet, inches) = BodyUnits.cmToFeetInches(cm)
        str(R.string.report_pdf_feet_inches, feet, inches)
    }

    private fun circumference(cm: Double): String =
        if (units.length == LengthUnit.CM) str(R.string.report_pdf_cm, decimal(cm, 1)) else str(R.string.report_pdf_inches, decimal(BodyUnits.cmToInches(cm), 1))

    private fun vitalLabel(kind: VitalKind): String = str(
        when (kind) {
            VitalKind.RESTING_HEART_RATE -> R.string.report_pdf_vital_rhr
            VitalKind.HRV -> R.string.report_pdf_vital_hrv
            VitalKind.SLEEPING_HEART_RATE -> R.string.report_pdf_vital_sleeping_hr
            VitalKind.BLOOD_PRESSURE -> R.string.report_pdf_vital_bp
            VitalKind.SPO2 -> R.string.report_pdf_vital_spo2
            VitalKind.RESPIRATORY_RATE -> R.string.report_pdf_vital_breathing
            VitalKind.VO2_MAX -> R.string.report_pdf_vital_vo2
            VitalKind.WEIGHT -> R.string.report_pdf_vital_weight
            VitalKind.SLEEP_DURATION -> R.string.report_pdf_vital_sleep
            VitalKind.STEPS -> R.string.report_pdf_vital_steps
        }
    )

    private fun vitalUnit(kind: VitalKind): String = when (kind) {
        VitalKind.RESTING_HEART_RATE, VitalKind.SLEEPING_HEART_RATE -> str(R.string.report_pdf_unit_bpm)
        VitalKind.HRV -> str(R.string.report_pdf_unit_ms)
        VitalKind.BLOOD_PRESSURE -> str(R.string.report_pdf_unit_mmhg)
        VitalKind.SPO2 -> "%"
        VitalKind.RESPIRATORY_RATE -> str(R.string.report_pdf_unit_breaths)
        VitalKind.VO2_MAX -> str(R.string.report_pdf_unit_vo2)
        VitalKind.WEIGHT -> weightLabel
        VitalKind.SLEEP_DURATION -> ""
        VitalKind.STEPS -> str(R.string.report_pdf_unit_steps)
    }

    /** A vital's value without its unit; sleep is "7 h 12 min" and carries its own. */
    private fun vitalValue(kind: VitalKind, value: Double): String = when (kind) {
        VitalKind.SLEEP_DURATION -> hoursMinutes(value)
        VitalKind.WEIGHT -> decimal(BodyUnits.weightIn(value, units.weight), 1)
        VitalKind.SPO2, VitalKind.RESPIRATORY_RATE, VitalKind.VO2_MAX -> decimal(value, 1)
        else -> whole(value)
    }

    private fun VitalStat.averageText(): String =
        if (kind == VitalKind.BLOOD_PRESSURE) "${whole(average)}/${whole(secondaryAverage ?: 0.0)}" else vitalValue(kind, average)

    private fun VitalStat.latestText(): String =
        if (kind == VitalKind.BLOOD_PRESSURE) "${whole(latest)}/${whole(secondaryLatest ?: 0.0)}" else vitalValue(kind, latest)

    private fun VitalStat.rangeText(): String = if (kind == VitalKind.BLOOD_PRESSURE) {
        "${whole(min)}–${whole(max)} / ${whole(secondaryMin ?: 0.0)}–${whole(secondaryMax ?: 0.0)}"
    } else if (kind == VitalKind.SLEEP_DURATION) {
        "${hoursMinutes(min)} – ${hoursMinutes(max)}"
    } else "${vitalValue(kind, min)}–${vitalValue(kind, max)}"

    private fun VitalStat.previousText(): String = when {
        previousAverage == null -> "—"
        kind == VitalKind.BLOOD_PRESSURE -> "${whole(previousAverage)}/${previousSecondaryAverage?.let { whole(it) } ?: "—"}"
        else -> vitalValue(kind, previousAverage)
    }

    private fun sexText(sex: BiologicalSex?): String? = when (sex) {
        BiologicalSex.MALE -> str(R.string.report_pdf_sex_male)
        BiologicalSex.FEMALE -> str(R.string.report_pdf_sex_female)
        BiologicalSex.OTHER -> str(R.string.report_pdf_sex_other)
        null -> null
    }

    private fun sourceText(source: RecordSource): String = str(
        when (source) {
            RecordSource.MANUAL -> R.string.report_pdf_source_manual
            RecordSource.HEALTH_CONNECT -> R.string.report_pdf_source_health_connect
            RecordSource.MEDICAL_RECORD -> R.string.report_pdf_source_medical
            RecordSource.AI_SCAN -> R.string.report_pdf_source_scan
        }
    )

    private fun toneColor(tone: MetricTone?): Int = when (tone) {
        MetricTone.GOOD -> Ink.GOOD
        MetricTone.CAUTION -> Ink.CAUTION
        MetricTone.RISK -> Ink.RISK
        MetricTone.NEUTRAL, null -> Ink.BODY
    }

    // ---- Blocks ---------------------------------------------------------------------------

    private fun layout(text: CharSequence, paint: TextPaint, width: Float, align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL, spacing: Float = 1.18f): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, max(1, width.toInt()))
            .setAlignment(align)
            .setLineSpacing(0f, spacing)
            .setIncludePad(false)
            .build()

    private fun Canvas.drawLayout(layout: StaticLayout, x: Float, y: Float) {
        save()
        translate(x, y)
        layout.draw(this)
        restore()
    }

    private inner class Masthead(private val report: DoctorReport) : Block() {
        override val height = 34f
        override fun draw(canvas: Canvas, x: Float, y: Float) {
            drawLogo(canvas, x, y, 21f)
            canvas.drawText(str(R.string.report_pdf_brand), x + 20f, y + 16f, paint(14f, bold, Ink.TEXT))
            val kicker = str(R.string.report_pdf_kicker).uppercase(locale)
            val prepared = str(R.string.report_pdf_prepared, date(report.generatedAt.toLocalDate()))
            val right = x + contentWidth
            canvas.drawText(kicker, right - labelText.measureText(kicker), y + 7f, labelText)
            canvas.drawText(prepared, right - smallText.measureText(prepared), y + 17f, smallText)
            fill.color = Ink.ACCENT
            canvas.drawRect(x, y + 27f, x + contentWidth, y + 29f, fill)
        }
    }

    private inner class TitleBlock(private val report: DoctorReport) : Block() {
        private val name = report.patient.name
        override val spaceBefore = 14f
        override val height = if (name != null) 58f else 42f
        override fun draw(canvas: Canvas, x: Float, y: Float) {
            canvas.drawText(str(R.string.report_pdf_title), x, y + 22f, titleText)
            var line = y + 22f
            if (name != null) {
                line += 18f
                canvas.drawText(name, x, line, subtitleText)
            }
            line += 15f
            canvas.drawText(str(R.string.report_pdf_period, periodText(report), report.days), x, line, smallText)
        }
    }

    /** Who the report is about, in one shaded band. */
    private inner class PatientPanel(report: DoctorReport) : Block() {
        private val p = report.patient
        private val items: List<Pair<String, String>> = listOfNotNull(
            p.dateOfBirth?.let { str(R.string.report_pdf_dob) to date(it) },
            p.age?.let { str(R.string.report_pdf_age) to it.toString() },
            sexText(p.sex)?.let { str(R.string.report_pdf_sex) to it },
            p.heightCm?.let { str(R.string.report_pdf_height) to length(it) },
            p.weightKg?.let { str(R.string.report_pdf_weight) to weight(it) },
            p.bmi?.let { str(R.string.report_pdf_bmi) to decimal(it, 1) },
            p.waistCm?.let { str(R.string.report_pdf_waist) to circumference(it) },
            p.bodyFatPercent?.let { str(R.string.report_pdf_body_fat) to "${decimal(it, 1)}%" },
            if (p.isPregnant) str(R.string.report_pdf_pregnancy) to (p.pregnancyDueDate?.let { str(R.string.report_pdf_due, date(it)) } ?: str(R.string.report_pdf_pregnant)) else null
        ).ifEmpty { listOf(str(R.string.report_pdf_profile_label) to str(R.string.report_pdf_profile_empty)) }
        private val columns = min(5, items.size)
        private val rows = items.chunked(columns)
        override val spaceBefore = 12f
        override val height = 14f + rows.size * 30f
        override fun draw(canvas: Canvas, x: Float, y: Float) {
            fill.color = Ink.PANEL
            canvas.drawRoundRect(RectF(x, y, x + contentWidth, y + height), 6f, 6f, fill)
            val cell = (contentWidth - 24f) / columns
            rows.forEachIndexed { r, row ->
                row.forEachIndexed { c, (label, value) ->
                    val cx = x + 12f + c * cell
                    val cy = y + 9f + r * 30f
                    canvas.drawText(label.uppercase(locale), cx, cy + 7f, labelText)
                    canvas.drawText(TextUtils.ellipsize(value, bodyBoldText, cell - 8f, TextUtils.TruncateAt.END).toString(), cx, cy + 21f, paint(10f, bold, Ink.TEXT))
                }
            }
        }
    }

    private inner class NotesBlock(notes: String) : Block() {
        private val text = layout(notes, bodyText, contentWidth - 28f)
        override val spaceBefore = 12f
        override val height = text.height + 34f
        override fun draw(canvas: Canvas, x: Float, y: Float) {
            fill.color = Ink.ACCENT_SOFT
            canvas.drawRoundRect(RectF(x, y, x + contentWidth, y + height), 6f, 6f, fill)
            fill.color = Ink.ACCENT
            canvas.drawRect(x, y, x + 3f, y + height, fill)
            canvas.drawText(str(R.string.report_pdf_notes).uppercase(locale), x + 14f, y + 15f, paint(6.5f, bold, Ink.ACCENT_DARK, 0.08f))
            canvas.drawLayout(text, x + 14f, y + 23f)
        }
    }

    private inner class SectionHeading(title: String, note: String?) : Block() {
        private val titleLayout = layout(title, sectionText, contentWidth)
        private val noteLayout = note?.let { layout(it, sectionNoteText, contentWidth) }
        override val spaceBefore = 22f
        override val keepWithNext = true
        override val height = titleLayout.height + 7f + (noteLayout?.let { it.height + 3f } ?: 0f) + 8f
        override fun draw(canvas: Canvas, x: Float, y: Float) {
            fill.color = Ink.ACCENT
            canvas.drawRoundRect(RectF(x, y + 2f, x + 3f, y + titleLayout.height - 2f), 1.5f, 1.5f, fill)
            canvas.drawLayout(titleLayout, x + 9f, y)
            var line = y + titleLayout.height + 4f
            noteLayout?.let {
                canvas.drawLayout(it, x, line)
                line += it.height + 3f
            }
            stroke.color = Ink.HAIRLINE
            stroke.strokeWidth = 0.6f
            stroke.pathEffect = null
            canvas.drawLine(x, line + 3f, x + contentWidth, line + 3f, stroke)
        }
    }

    private inner class SubHeading(title: String) : Block() {
        private val text = layout(title.uppercase(locale), labelText, contentWidth)
        override val spaceBefore = 12f
        override val keepWithNext = true
        override val height = text.height + 5f
        override fun draw(canvas: Canvas, x: Float, y: Float) = canvas.drawLayout(text, x, y)
    }

    private inner class Paragraph(text: String, paint: TextPaint, override val spaceBefore: Float = 0f) : Block() {
        private val body = layout(text, paint, contentWidth)
        override val height = body.height.toFloat()
        override fun draw(canvas: Canvas, x: Float, y: Float) = canvas.drawLayout(body, x, y)
    }

    // At a glance: four bordered tiles per row.

    private fun tileRows(vitals: List<VitalStat>): List<Block> {
        val order = listOf(
            VitalKind.RESTING_HEART_RATE, VitalKind.HRV, VitalKind.BLOOD_PRESSURE, VitalKind.SLEEP_DURATION,
            VitalKind.STEPS, VitalKind.WEIGHT, VitalKind.SPO2, VitalKind.RESPIRATORY_RATE,
            VitalKind.VO2_MAX, VitalKind.SLEEPING_HEART_RATE
        )
        val tiles = order.mapNotNull { kind -> vitals.firstOrNull { it.kind == kind } }.take(8)
        return tiles.chunked(TILE_COLUMNS).mapIndexed { i, row -> TileRow(row, if (i == 0) 0f else TILE_GAP) }
    }

    private inner class TileRow(private val stats: List<VitalStat>, override val spaceBefore: Float) : Block() {
        override val height = 60f
        override fun draw(canvas: Canvas, x: Float, y: Float) {
            val w = (contentWidth - TILE_GAP * (TILE_COLUMNS - 1)) / TILE_COLUMNS
            stats.forEachIndexed { i, stat ->
                val left = x + i * (w + TILE_GAP)
                stroke.color = Ink.HAIRLINE
                stroke.strokeWidth = 0.8f
                stroke.pathEffect = null
                canvas.drawRoundRect(RectF(left, y, left + w, y + height), 6f, 6f, stroke)
                fill.color = vitalColor(stat.kind)
                canvas.drawCircle(left + 12f, y + 12.5f, 2.6f, fill)
                val label = TextUtils.ellipsize(vitalLabel(stat.kind).uppercase(locale), labelText, w - 28f, TextUtils.TruncateAt.END).toString()
                canvas.drawText(label, left + 19f, y + 15f, labelText)

                val showLatest = stat.kind == VitalKind.WEIGHT
                val value = if (showLatest) stat.latestText() else stat.averageText()
                canvas.drawText(value, left + 10f, y + 36f, tileValueText)
                val unit = vitalUnit(stat.kind)
                if (unit.isNotEmpty()) canvas.drawText(unit, left + 13f + tileValueText.measureText(value), y + 36f, tileUnitText)

                // The range when it fits; otherwise how many days the figure rests on.
                val withRange = str(if (showLatest) R.string.report_pdf_tile_latest else R.string.report_pdf_tile_average, stat.rangeText())
                val caption = if (smallText.measureText(withRange) <= w - 20f) withRange
                else str(if (showLatest) R.string.report_pdf_tile_latest_days else R.string.report_pdf_tile_average_days, stat.daysWithData)
                canvas.drawText(TextUtils.ellipsize(caption, smallText, w - 20f, TextUtils.TruncateAt.END).toString(), left + 10f, y + 50f, smallText)
            }
        }
    }

    private fun vitalColor(kind: VitalKind): Int = when (kind) {
        VitalKind.RESTING_HEART_RATE, VitalKind.SLEEPING_HEART_RATE -> Ink.CHART_HEART
        VitalKind.HRV -> Ink.CHART_HRV
        VitalKind.BLOOD_PRESSURE -> Ink.CHART_BP
        VitalKind.SLEEP_DURATION -> Ink.CHART_SLEEP
        VitalKind.STEPS -> Ink.CHART_STEPS
        VitalKind.WEIGHT -> Ink.CHART_WEIGHT
        VitalKind.SPO2, VitalKind.RESPIRATORY_RATE -> Ink.CHART_OXYGEN
        VitalKind.VO2_MAX -> Ink.CHART_FITNESS
    }

    // Conditions and allergies, side by side.

    private inner class ProfileColumns(report: DoctorReport) : Block() {
        private val columnWidth = (contentWidth - 20f) / 2
        private val conditions = listLayout(str(R.string.report_pdf_conditions), report.conditions.map { it.name to listOfNotNull(it.note, sourceText(it.source).takeIf { _ -> it.source == RecordSource.MEDICAL_RECORD }).joinToString(" · ") })
        private val allergies = listLayout(str(R.string.report_pdf_allergies), report.allergies.map { it.name to (it.note ?: "") })
        override val height = max(conditions.sumOf { it.height.toDouble() }, allergies.sumOf { it.height.toDouble() }).toFloat()

        private fun listLayout(title: String, items: List<Pair<String, String>>): List<StaticLayout> = buildList {
            add(layout(title.uppercase(locale) + "\n", labelText, columnWidth))
            if (items.isEmpty()) add(layout(str(R.string.report_pdf_none_recorded), smallText, columnWidth))
            items.forEach { (name, note) ->
                val text = android.text.SpannableStringBuilder("•  $name")
                text.setSpan(android.text.style.StyleSpan(Typeface.BOLD), 0, text.length, 0)
                if (note.isNotBlank()) {
                    val start = text.length
                    text.append(" — ").append(note)
                    text.setSpan(android.text.style.ForegroundColorSpan(Ink.MUTED), start, text.length, 0)
                }
                add(layout(text, bodyText, columnWidth, spacing = 1.3f))
            }
        }

        override fun draw(canvas: Canvas, x: Float, y: Float) {
            var top = y
            conditions.forEach { canvas.drawLayout(it, x, top); top += it.height }
            top = y
            allergies.forEach { canvas.drawLayout(it, x + columnWidth + 20f, top); top += it.height }
        }
    }

    // Tables.

    private data class Column(val title: String, val weight: Float, val align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL)
    private data class Cell(val text: String, val strong: Boolean = false, val color: Int? = null, val sub: String? = null)

    private fun table(columns: List<Column>, rows: List<List<Cell>>, footnote: String? = null): List<Block> {
        val total = columns.sumOf { it.weight.toDouble() }.toFloat()
        val widths = columns.map { contentWidth * it.weight / total }
        val header = TableHeader(columns, widths)
        return buildList {
            add(header)
            rows.forEachIndexed { i, row -> add(TableRow(row, columns, widths, zebra = i % 2 == 1, header = header)) }
            footnote?.let { add(Paragraph(it, smallText, spaceBefore = 4f)) }
        }
    }

    private inner class TableHeader(columns: List<Column>, private val widths: List<Float>) : Block() {
        private val cells = columns.mapIndexed { i, c -> layout(c.title.uppercase(locale), tableHeadText, widths[i] - 2 * CELL_PAD, c.align) }
        override val keepWithNext = true
        override val height = (cells.maxOf { it.height } + 10f)
        override fun draw(canvas: Canvas, x: Float, y: Float) {
            var left = x
            cells.forEachIndexed { i, cell ->
                canvas.drawLayout(cell, left + CELL_PAD, y + height - cell.height - 5f)
                left += widths[i]
            }
            stroke.color = Ink.RULE
            stroke.strokeWidth = 0.8f
            stroke.pathEffect = null
            canvas.drawLine(x, y + height, x + contentWidth, y + height, stroke)
        }
    }

    private inner class TableRow(
        row: List<Cell>,
        columns: List<Column>,
        private val widths: List<Float>,
        private val zebra: Boolean,
        header: TableHeader
    ) : Block() {
        override val repeatOnBreak: Block = header
        private val cells = row.mapIndexed { i, cell ->
            val paint = TextPaint(if (cell.strong) tableStrongText else tableText).apply { cell.color?.let { color = it } }
            val text = android.text.SpannableStringBuilder(cell.text)
            cell.sub?.let { sub ->
                val start = text.length
                text.append("\n").append(sub)
                text.setSpan(android.text.style.ForegroundColorSpan(Ink.MUTED), start, text.length, 0)
                text.setSpan(android.text.style.AbsoluteSizeSpan(7), start, text.length, 0)
                text.setSpan(android.text.style.TypefaceSpan(regular), start, text.length, 0)
            }
            layout(text, paint, widths[i] - 2 * CELL_PAD, columns[i].align)
        }
        override val height = cells.maxOf { it.height } + 2 * ROW_PAD
        override fun draw(canvas: Canvas, x: Float, y: Float) {
            if (zebra) {
                fill.color = Ink.ZEBRA
                canvas.drawRect(x, y, x + contentWidth, y + height, fill)
            }
            var left = x
            cells.forEachIndexed { i, cell ->
                canvas.drawLayout(cell, left + CELL_PAD, y + ROW_PAD)
                left += widths[i]
            }
        }
    }

    private fun vitalsTable(report: DoctorReport): List<Block> = table(
        listOf(
            Column(str(R.string.report_pdf_col_measure), 2.3f),
            Column(str(R.string.report_pdf_col_average), 1.15f, Layout.Alignment.ALIGN_OPPOSITE),
            Column(str(R.string.report_pdf_col_range), 1.6f, Layout.Alignment.ALIGN_OPPOSITE),
            Column(str(R.string.report_pdf_col_latest), 1.15f, Layout.Alignment.ALIGN_OPPOSITE),
            Column(str(R.string.report_pdf_col_previous, report.days), 1.15f, Layout.Alignment.ALIGN_OPPOSITE),
            Column(str(R.string.report_pdf_col_days), 0.75f, Layout.Alignment.ALIGN_OPPOSITE)
        ),
        report.vitals.map { v ->
            val unit = vitalUnit(v.kind)
            listOf(
                Cell(vitalLabel(v.kind), strong = true, sub = unit.takeIf { it.isNotEmpty() }),
                Cell(v.averageText(), strong = true),
                Cell(v.rangeText()),
                Cell(v.latestText(), sub = shortDate(v.latestDate)),
                Cell(v.previousText(), color = Ink.MUTED),
                Cell(v.daysWithData.toString())
            )
        }
    )

    private fun markerTable(report: DoctorReport): List<Block> = table(
        listOf(
            Column(str(R.string.report_pdf_col_test), 1.9f),
            Column(str(R.string.report_pdf_col_latest_result), 1.3f, Layout.Alignment.ALIGN_OPPOSITE),
            Column(str(R.string.report_pdf_col_date), 1.15f),
            Column(str(R.string.report_pdf_col_range_label), 1.5f),
            Column(str(R.string.report_pdf_col_in_period), 1.6f, Layout.Alignment.ALIGN_OPPOSITE)
        ),
        report.markers.map { m ->
            // Blood pressure averages meaningfully; glucose mixes fasting and after-meal, so show its spread.
            val inPeriod = when {
                m.readingsInPeriod == 0 -> "—"
                m.readingsInPeriod == 1 -> "1"
                m.type == MarkerType.BLOOD_PRESSURE && m.periodAverage != null ->
                    str(R.string.report_pdf_in_period_average, m.readingsInPeriod, HealthMarkerCalculator.format(m.type, m.periodAverage, m.periodSecondaryAverage))
                m.periodMin != null && m.periodMax != null ->
                    str(R.string.report_pdf_in_period_range, m.readingsInPeriod, HealthMarkerCalculator.format(m.type, m.periodMin), HealthMarkerCalculator.format(m.type, m.periodMax))
                else -> m.readingsInPeriod.toString()
            }
            listOf(
                Cell(m.type.label, strong = true, sub = m.type.canonicalUnit),
                Cell(HealthMarkerCalculator.format(m.type, m.latest.value, m.latest.secondaryValue), strong = true),
                Cell(date(m.latest.date), sub = sourceText(m.latest.source)),
                Cell(m.category ?: "—", strong = true, color = toneColor(m.tone), sub = m.latest.glucoseContext?.label),
                Cell(inPeriod)
            )
        }
    )

    private fun readingsTable(report: DoctorReport): List<Block> {
        val shown = report.readings.take(MAX_READINGS)
        return table(
            listOf(
                Column(str(R.string.report_pdf_col_date), 1.25f),
                Column(str(R.string.report_pdf_col_test), 1.6f),
                Column(str(R.string.report_pdf_col_result), 1.2f, Layout.Alignment.ALIGN_OPPOSITE),
                Column(str(R.string.report_pdf_col_context), 2.3f),
                Column(str(R.string.report_pdf_col_source), 1.2f)
            ),
            shown.map { r ->
                val zoned = r.measuredAt.atZone(report.generatedAt.zone)
                listOf(
                    Cell(date(r.date), sub = time(zoned.toLocalTime())),
                    Cell(r.type.label),
                    Cell("${HealthMarkerCalculator.format(r.type, r.value, r.secondaryValue)} ${r.type.canonicalUnit}", strong = true),
                    Cell(listOfNotNull(r.glucoseContext?.label, r.note?.takeIf { it.isNotBlank() }).joinToString(" · ").ifEmpty { "—" }),
                    Cell(sourceText(r.source), color = Ink.MUTED)
                )
            },
            footnote = if (report.readings.size > shown.size) str(R.string.report_pdf_readings_truncated, shown.size, report.readings.size) else null
        )
    }

    private fun supplementTable(report: DoctorReport): List<Block> = table(
        listOf(
            Column(str(R.string.report_pdf_col_supplement), 2f),
            Column(str(R.string.report_pdf_col_dose), 1.1f),
            Column(str(R.string.report_pdf_col_when), 1.2f),
            Column(str(R.string.report_pdf_col_taken), 0.95f, Layout.Alignment.ALIGN_OPPOSITE),
            Column(str(R.string.report_pdf_col_contents), 2.6f)
        ),
        report.supplements.map { s ->
            listOf(
                Cell(s.name, strong = true, sub = s.brand),
                Cell(s.dose),
                Cell(s.times.takeIf { it.isNotEmpty() }?.joinToString(", ") { time(it) } ?: str(R.string.report_pdf_as_needed)),
                Cell(s.adherence?.let { (taken, scheduled) -> "$taken/$scheduled" } ?: "—"),
                Cell(s.ingredients ?: "—", sub = s.notes)
            )
        }
    )

    // A key figure grid: label above value, four across.

    private inner class StatGrid(private val items: List<Pair<String, String>>) : Block() {
        private val rows = items.chunked(STAT_COLUMNS)
        override val height = rows.size * 32f - 4f
        override fun draw(canvas: Canvas, x: Float, y: Float) {
            val w = (contentWidth - TILE_GAP * (STAT_COLUMNS - 1)) / STAT_COLUMNS
            rows.forEachIndexed { r, row ->
                row.forEachIndexed { c, (label, value) ->
                    val left = x + c * (w + TILE_GAP)
                    val top = y + r * 32f
                    fill.color = Ink.PANEL
                    canvas.drawRoundRect(RectF(left, top, left + w, top + 28f), 5f, 5f, fill)
                    canvas.drawText(TextUtils.ellipsize(label.uppercase(locale), labelText, w - 16f, TextUtils.TruncateAt.END).toString(), left + 8f, top + 10.5f, labelText)
                    canvas.drawText(TextUtils.ellipsize(value, statValueText, w - 16f, TextUtils.TruncateAt.END).toString(), left + 8f, top + 23f, statValueText)
                }
            }
        }
    }

    // Charts.

    private inner class ChartRow(private val charts: List<TrendChart>) : Block() {
        override val spaceBefore = 10f
        override val height = 138f
        override fun draw(canvas: Canvas, x: Float, y: Float) {
            val w = (contentWidth - CHART_GAP) / 2
            charts.forEachIndexed { i, chart -> drawChart(canvas, chart, x + i * (w + CHART_GAP), y, w, height) }
        }
    }

    /** Plotted value: hours for sleep, the user's unit for weight, otherwise as stored. */
    private fun plotValue(kind: VitalKind, value: Double): Double = when (kind) {
        VitalKind.SLEEP_DURATION -> value / 60.0
        VitalKind.WEIGHT -> BodyUnits.weightIn(value, units.weight)
        else -> value
    }

    /** The smallest y-axis span worth showing for each vital, in plotted units. */
    private fun minimumSpan(kind: VitalKind): Double = when (kind) {
        VitalKind.RESTING_HEART_RATE, VitalKind.SLEEPING_HEART_RATE -> 16.0
        VitalKind.HRV -> 24.0
        VitalKind.SLEEP_DURATION -> 3.0
        VitalKind.STEPS -> 6000.0
        VitalKind.WEIGHT -> if (units.weight == WeightUnit.KG) 4.0 else 8.0
        VitalKind.SPO2 -> 6.0
        VitalKind.RESPIRATORY_RATE -> 4.0
        VitalKind.VO2_MAX -> 8.0
        VitalKind.BLOOD_PRESSURE -> 50.0
    }

    private fun tickLabel(kind: VitalKind, value: Double, step: Double): String = when {
        kind == VitalKind.STEPS && value >= 1000 -> {
            val k = value / 1000
            if (k == floor(k)) str(R.string.report_pdf_thousands, whole(k)) else str(R.string.report_pdf_thousands, decimal(k, 1))
        }
        step < 1 -> decimal(value, if (step < 0.1) 2 else 1)
        else -> whole(value)
    }

    private fun drawChart(canvas: Canvas, chart: TrendChart, x: Float, y: Float, w: Float, h: Float) {
        val kind = chart.kind
        val color = vitalColor(kind)
        val primary = chart.points.map { TrendPoint(it.date, plotValue(kind, it.value)) }
        val secondary = chart.secondary.map { TrendPoint(it.date, plotValue(kind, it.value)) }
        val all = primary + secondary

        // Frame and title.
        stroke.color = Ink.HAIRLINE
        stroke.strokeWidth = 0.8f
        stroke.pathEffect = null
        canvas.drawRoundRect(RectF(x, y, x + w, y + h), 6f, 6f, stroke)
        canvas.drawText(vitalLabel(kind), x + 10f, y + 15f, chartTitleText)
        val unit = when (kind) {
            VitalKind.SLEEP_DURATION -> str(R.string.report_pdf_unit_hours)
            else -> vitalUnit(kind)
        }
        canvas.drawText(unit, x + w - 10f - axisText.measureText(unit), y + 15f, axisText)

        val plotLeft = x + 30f
        val plotRight = x + w - 12f
        val plotTop = y + 28f
        val plotBottom = y + h - 20f

        // Axis scale. Blood pressure always shows its 120/80 reference lines.
        var lo = all.minOf { it.value }
        var hi = all.maxOf { it.value }
        if (kind == VitalKind.BLOOD_PRESSURE) {
            lo = min(lo, 75.0)
            hi = max(hi, 125.0)
        } else {
            // A floor on the visible span, so a 2 bpm wobble doesn't fill the chart like a real change.
            val span = minimumSpan(kind)
            if (hi - lo < span) {
                val mid = (hi + lo) / 2
                lo = max(mid - span / 2, 0.0)
                hi = lo + span
                if (kind == VitalKind.SPO2 && hi > 100.0) {
                    hi = 100.0
                    lo = hi - span
                }
            }
        }
        val ticks = niceTicks(lo, hi)
        val yMin = ticks.first()
        val yMax = ticks.last()
        val step = if (ticks.size > 1) ticks[1] - ticks[0] else 1.0
        fun py(v: Double) = plotBottom - ((v - yMin) / (yMax - yMin)).toFloat() * (plotBottom - plotTop)

        val firstDay = all.minOf { it.date.toEpochDay() }
        val lastDay = all.maxOf { it.date.toEpochDay() }
        val span = max(1L, lastDay - firstDay)
        fun px(d: LocalDate) = plotLeft + ((d.toEpochDay() - firstDay).toFloat() / span) * (plotRight - plotLeft)

        // Grid.
        stroke.color = Ink.GRID
        stroke.strokeWidth = 0.5f
        ticks.forEach { t ->
            val ty = py(t)
            canvas.drawLine(plotLeft, ty, plotRight, ty, stroke)
            val label = tickLabel(kind, t, step)
            canvas.drawText(label, plotLeft - 4f - axisText.measureText(label), ty + 2.2f, axisText)
        }

        // Dates along the bottom: first, middle and last.
        val first = all.minBy { it.date }.date
        val last = all.maxBy { it.date }.date
        canvas.drawText(shortDate(first), plotLeft, plotBottom + 11f, axisText)
        val lastLabel = shortDate(last)
        canvas.drawText(lastLabel, plotRight - axisText.measureText(lastLabel), plotBottom + 11f, axisText)
        if (span > 14) {
            val mid = LocalDate.ofEpochDay(firstDay + span / 2)
            val midLabel = shortDate(mid)
            canvas.drawText(midLabel, px(mid) - axisText.measureText(midLabel) / 2, plotBottom + 11f, axisText)
        }

        if (kind == VitalKind.BLOOD_PRESSURE) {
            stroke.color = Ink.REFERENCE
            stroke.strokeWidth = 0.7f
            stroke.pathEffect = DashPathEffect(floatArrayOf(2.5f, 2f), 0f)
            listOf(120.0, 80.0).forEach { ref -> canvas.drawLine(plotLeft, py(ref), plotRight, py(ref), stroke) }
            stroke.pathEffect = null
            drawLegend(canvas, x + w - 10f - axisText.measureText(unit) - 8f, y + 15f, listOf(str(R.string.report_pdf_systolic) to color, str(R.string.report_pdf_diastolic) to Ink.CHART_BP_LOW))
        } else {
            // The period average, dashed, labelled at the right.
            val average = primary.map { it.value }.average()
            stroke.color = Ink.MUTED
            stroke.strokeWidth = 0.6f
            stroke.pathEffect = DashPathEffect(floatArrayOf(2f, 2f), 0f)
            canvas.drawLine(plotLeft, py(average), plotRight, py(average), stroke)
            stroke.pathEffect = null
        }

        drawSeries(canvas, primary, color, ::px, ::py, fillUnder = kind != VitalKind.BLOOD_PRESSURE, baseline = plotBottom)
        if (secondary.isNotEmpty()) drawSeries(canvas, secondary, Ink.CHART_BP_LOW, ::px, ::py, fillUnder = false, baseline = plotBottom)
    }

    private fun drawSeries(
        canvas: Canvas,
        points: List<TrendPoint>,
        color: Int,
        px: (LocalDate) -> Float,
        py: (Double) -> Float,
        fillUnder: Boolean,
        baseline: Float
    ) {
        if (points.isEmpty()) return
        val line = Path()
        points.forEachIndexed { i, p -> if (i == 0) line.moveTo(px(p.date), py(p.value)) else line.lineTo(px(p.date), py(p.value)) }
        if (fillUnder && points.size > 1) {
            val area = Path(line).apply {
                lineTo(px(points.last().date), baseline)
                lineTo(px(points.first().date), baseline)
                close()
            }
            fill.color = (color and 0x00FFFFFF) or (0x1A shl 24)
            canvas.drawPath(area, fill)
        }
        stroke.color = color
        stroke.strokeWidth = 1.4f
        stroke.strokeJoin = Paint.Join.ROUND
        stroke.strokeCap = Paint.Cap.ROUND
        canvas.drawPath(line, stroke)
        stroke.strokeJoin = Paint.Join.MITER
        stroke.strokeCap = Paint.Cap.BUTT
        // Dots only when they won't merge into a smear.
        if (points.size <= 40) {
            points.forEach { p ->
                fill.color = Ink.PAPER
                canvas.drawCircle(px(p.date), py(p.value), 1.9f, fill)
                fill.color = color
                canvas.drawCircle(px(p.date), py(p.value), 1.3f, fill)
            }
        }
    }

    private fun drawLegend(canvas: Canvas, right: Float, baseline: Float, items: List<Pair<String, Int>>) {
        var x = right
        items.reversed().forEach { (label, color) ->
            x -= axisText.measureText(label)
            canvas.drawText(label, x, baseline, axisText)
            x -= 6f
            fill.color = color
            canvas.drawCircle(x, baseline - 2.2f, 2.2f, fill)
            x -= 10f
        }
    }

    /** 4-6 round-number gridlines covering [lo]..[hi] (Heckbert's "nice numbers"). */
    private fun niceTicks(lo: Double, hi: Double): List<Double> {
        var min = lo
        var max = hi
        if (max - min < 1e-9) {
            val pad = if (abs(min) < 1e-9) 1.0 else abs(min) * 0.05
            min -= pad
            max += pad
        }
        val step = niceNumber(niceNumber(max - min, round = false) / 4, round = true)
        val start = floor(min / step) * step
        val end = ceil(max / step) * step
        val count = ((end - start) / step).roundToInt()
        return (0..count).map { start + it * step }
    }

    private fun niceNumber(range: Double, round: Boolean): Double {
        val exponent = floor(log10(range))
        val fraction = range / 10.0.pow(exponent)
        val nice = if (round) when {
            fraction < 1.5 -> 1.0
            fraction < 3 -> 2.0
            fraction < 7 -> 5.0
            else -> 10.0
        } else when {
            fraction <= 1 -> 1.0
            fraction <= 2 -> 2.0
            fraction <= 5 -> 5.0
            else -> 10.0
        }
        return nice * 10.0.pow(exponent)
    }

    /** Print palette: the app's brand colours, deepened where needed to read well on paper. */
    private object Ink {
        const val PAPER = 0xFFFFFFFF.toInt()
        const val TEXT = 0xFF1C252B.toInt()
        const val BODY = 0xFF37474F.toInt()
        const val MUTED = 0xFF78909C.toInt()
        const val RULE = 0xFFB0BEC5.toInt()
        const val HAIRLINE = 0xFFE1E6E9.toInt()
        const val GRID = 0xFFEDF0F2.toInt()
        const val REFERENCE = 0xFFB0BEC5.toInt()
        const val PANEL = 0xFFF4F6F7.toInt()
        const val ZEBRA = 0xFFF8F9FA.toInt()
        const val ACCENT = 0xFFFF5722.toInt()
        const val ACCENT_DARK = 0xFFD84315.toInt()
        const val ACCENT_SOFT = 0xFFFFF3EE.toInt()
        const val GOOD = 0xFF2E7D32.toInt()
        const val CAUTION = 0xFFE65100.toInt()
        const val RISK = 0xFFC62828.toInt()
        const val CHART_HEART = 0xFFE53935.toInt()
        const val CHART_HRV = 0xFFD81B60.toInt()
        const val CHART_BP = 0xFFC62828.toInt()
        const val CHART_BP_LOW = 0xFFEF9A9A.toInt()
        const val CHART_SLEEP = 0xFF5C6BC0.toInt()
        const val CHART_STEPS = 0xFF00897B.toInt()
        const val CHART_WEIGHT = 0xFF546E7A.toInt()
        const val CHART_OXYGEN = 0xFF1E88E5.toInt()
        const val CHART_FITNESS = 0xFFFB8C00.toInt()
        const val LOGO_FLAME = 0xFFFF7E1D.toInt()
        const val LOGO_PULSE = 0xFFFFFFFF.toInt()
    }

    private companion object {
        const val MARGIN = 40
        const val RUNNING_HEADER_HEIGHT = 30
        const val FOOTER_HEIGHT = 22
        const val TILE_COLUMNS = 4
        const val STAT_COLUMNS = 4
        const val TILE_GAP = 8f
        const val CHART_GAP = 10f
        const val CELL_PAD = 5f
        const val ROW_PAD = 5f
        const val MAX_READINGS = 150

        /** The mark's bounds inside hangry_logo.xml's 108-unit viewport. */
        const val LOGO_LEFT = 32.5f
        const val LOGO_TOP = 24.8f
        const val LOGO_HEIGHT = 60f

        /** Where US Letter is the everyday paper size. */
        val LETTER_COUNTRIES = setOf("US", "CA", "MX", "PH", "CL", "CO", "VE", "PR", "GT", "CR", "PA", "SV", "DO", "NI", "HN", "BZ")
    }
}
