package com.kevan.hangry

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import org.robolectric.RuntimeEnvironment
import com.kevan.hangry.data.local.entity.DailyHealthSummaryEntity
import com.kevan.hangry.data.local.entity.ExerciseSessionEntity
import com.kevan.hangry.data.local.entity.FoodLogEntity
import com.kevan.hangry.data.local.entity.UserProfileEntity
import com.kevan.hangry.data.local.entity.WeightMeasurementEntity
import com.kevan.hangry.data.report.DoctorReportPdf
import com.kevan.hangry.data.report.ReportUnits
import com.kevan.hangry.domain.model.BiologicalSex
import com.kevan.hangry.domain.model.GlucoseContext
import com.kevan.hangry.domain.model.HealthProfileItem
import com.kevan.hangry.domain.model.HealthProfileKind
import com.kevan.hangry.domain.model.HealthRecordsSnapshot
import com.kevan.hangry.domain.model.LengthUnit
import com.kevan.hangry.domain.model.MarkerReading
import com.kevan.hangry.domain.model.MarkerType
import com.kevan.hangry.domain.model.RecordSource
import com.kevan.hangry.domain.model.Supplement
import com.kevan.hangry.domain.model.SupplementIngredient
import com.kevan.hangry.domain.model.SupplementsSnapshot
import com.kevan.hangry.domain.model.WeightUnit
import com.kevan.hangry.domain.report.DoctorReport
import com.kevan.hangry.domain.report.DoctorReportBuilder
import com.kevan.hangry.domain.report.DoctorReportInputs
import com.kevan.hangry.domain.report.ReportOptions
import com.kevan.hangry.domain.report.ReportPeriod
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import java.util.Random

/**
 * Lays out and paints a doctor report full of realistic data, page by page, onto bitmaps with
 * real Android graphics. Catches layout crashes, and leaves PNGs in
 * `app/build/doctor-report-preview/` to look at when changing the design.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class DoctorReportRenderTest {

    private val zone = ZoneId.of("America/New_York")
    private val now = ZonedDateTime.of(2026, 10, 22, 9, 30, 0, 0, zone)
    private val today = now.toLocalDate()

    @Test
    fun rendersAFullReportOnLetterPaper() {
        val pages = render(sampleReport(ReportOptions(period = ReportPeriod.DAYS_90, patientName = "Alex Morgan", visitNotes = VISIT_NOTES)), Locale.US, "letter")
        assertTrue("expected several pages, got $pages", pages in 3..8)
    }

    @Test
    fun rendersAnEmptyReportOnA4() {
        val empty = DoctorReportBuilder.build(
            DoctorReportInputs(
                now = now, options = ReportOptions(period = ReportPeriod.DAYS_30), profile = null, summaries = emptyList(),
                weights = emptyList(), records = HealthRecordsSnapshot(), supplements = SupplementsSnapshot(),
                workouts = emptyList(), food = emptyList(), latestBodyFatPercent = null, dataSources = emptyList()
            )
        )
        val pages = render(empty, Locale.UK, "empty-a4")
        assertTrue(pages in 1..2)
    }

    @Test
    fun rendersAYearInImperialUnits() {
        val pages = render(sampleReport(ReportOptions(period = ReportPeriod.YEAR)), Locale.US, "year", ReportUnits(WeightUnit.LB, LengthUnit.IN))
        assertTrue(pages >= 3)
    }

    private fun render(report: DoctorReport, locale: Locale, name: String, units: ReportUnits = ReportUnits(WeightUnit.KG, LengthUnit.CM)): Int {
        val pdf = DoctorReportPdf(RuntimeEnvironment.getApplication(), units, locale)
        val dir = File("build/doctor-report-preview").apply { mkdirs() }
        dir.listFiles { f -> f.name.startsWith("$name-") }?.forEach { it.delete() }
        var count = 0
        pdf.forEachPage(report) { index, total, paintPage ->
            val scale = 2f
            val bitmap = Bitmap.createBitmap((pdf.pageWidth * scale).toInt(), (pdf.pageHeight * scale).toInt(), Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            val canvas = Canvas(bitmap)
            canvas.scale(scale, scale)
            paintPage(canvas)
            File(dir, "$name-${index + 1}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            count = total
        }
        return count
    }

    private fun sampleReport(options: ReportOptions): DoctorReport {
        val random = Random(42)
        fun noise(spread: Double) = random.nextGaussian() * spread
        val days = options.period.days * 2
        val summaries = (0 until days).mapNotNull { back ->
            if (random.nextInt(12) == 0) return@mapNotNull null // the odd night the watch was charging
            val date = today.minusDays(back.toLong())
            val drift = back / 30.0
            val sleep = (415 + noise(45.0)).toInt().coerceIn(250, 600)
            val wake = date.atTime(LocalTime.of(6, 50)).plusMinutes(noise(25.0).toLong()).atZone(zone).toInstant()
            DailyHealthSummaryEntity(
                date = date,
                sleepDurationMinutes = sleep,
                sleepStartTime = wake.minusSeconds((sleep + 25) * 60L),
                sleepEndTime = wake,
                steps = (8200 + noise(2600.0)).toLong().coerceAtLeast(1200),
                activeMinutes = (48 + noise(18.0)).toInt().coerceAtLeast(5),
                restingHeartRate = 57 + drift * 0.6 + noise(2.0),
                sleepingHeartRate = 60 + drift * 0.5 + noise(2.5),
                hrvRmssd = 46 - drift + noise(7.0),
                spo2Percentage = (96.8 + noise(0.8)).coerceAtMost(100.0),
                respiratoryRate = 14.6 + noise(0.5),
                vo2Max = if (back % 7 == 0) 42.5 - drift * 0.1 + noise(0.3) else null
            )
        }
        val weights = (0 until days step 3).map { back ->
            val date = today.minusDays(back.toLong())
            WeightMeasurementEntity(
                recordFingerprint = "w$back",
                timestamp = date.atTime(7, 5).atZone(zone).toInstant(),
                weightKg = 81.2 + back * 0.012 + noise(0.35)
            )
        }
        var id = 0L
        fun reading(type: MarkerType, value: Double, secondary: Double?, date: LocalDate, source: RecordSource, context: GlucoseContext? = null, note: String? = null) =
            MarkerReading(++id, type, value, secondary, date.atTime(8, 15).atZone(zone).toInstant(), date, context, source, note)
        val readings = buildList {
            (0 until days step 6).forEach { back ->
                add(reading(MarkerType.BLOOD_PRESSURE, 131 + noise(6.0), 83 + noise(4.0), today.minusDays(back.toLong()), RecordSource.HEALTH_CONNECT))
            }
            add(reading(MarkerType.BLOOD_GLUCOSE, 104.0, null, today.minusDays(12), RecordSource.MANUAL, GlucoseContext.FASTING, "Before breakfast"))
            add(reading(MarkerType.BLOOD_GLUCOSE, 132.0, null, today.minusDays(11), RecordSource.MANUAL, GlucoseContext.AFTER_MEAL, "2 h after lunch"))
            val labDay = today.minusDays(64)
            add(reading(MarkerType.HBA1C, 5.8, null, labDay, RecordSource.AI_SCAN))
            add(reading(MarkerType.TOTAL_CHOLESTEROL, 214.0, null, labDay, RecordSource.AI_SCAN))
            add(reading(MarkerType.LDL, 141.0, null, labDay, RecordSource.AI_SCAN))
            add(reading(MarkerType.HDL, 47.0, null, labDay, RecordSource.AI_SCAN))
            add(reading(MarkerType.TRIGLYCERIDES, 158.0, null, labDay, RecordSource.AI_SCAN))
            add(reading(MarkerType.TESTOSTERONE, 520.0, null, today.minusDays(210), RecordSource.MEDICAL_RECORD))
        }.sortedByDescending { it.measuredAt }
        val records = HealthRecordsSnapshot(
            readings = readings,
            profileItems = listOf(
                HealthProfileItem(1, HealthProfileKind.CONDITION, "Elevated blood pressure", "Diagnosed 2024, diet and exercise", RecordSource.MANUAL),
                HealthProfileItem(2, HealthProfileKind.CONDITION, "Seasonal allergic rhinitis", null, RecordSource.MEDICAL_RECORD),
                HealthProfileItem(3, HealthProfileKind.ALLERGY, "Penicillin", "Hives", RecordSource.MANUAL),
                HealthProfileItem(4, HealthProfileKind.ALLERGY, "Tree nuts", null, RecordSource.MANUAL)
            ),
            sex = BiologicalSex.MALE
        )
        fun supplement(sid: Long, name: String, brand: String?, amount: Double, unit: String, times: List<LocalTime>, ingredients: List<SupplementIngredient>, tracked: Boolean, notes: String? = null) =
            Supplement(sid, name, brand, null, amount, unit, times, ingredients, remindersEnabled = tracked, tracked = tracked, notes = notes, photoPath = null, active = true)
        val supplements = SupplementsSnapshot(
            supplements = listOf(
                supplement(1, "Vitamin D3 + K2", "Thorne", 1.0, "capsule", listOf(LocalTime.of(8, 0)),
                    listOf(SupplementIngredient("Vitamin D3", 25.0, "mcg"), SupplementIngredient("Vitamin K2 (MK-4)", 1.0, "mg")), tracked = true),
                supplement(2, "Omega-3 fish oil", "Nordic Naturals", 2.0, "softgels", listOf(LocalTime.of(8, 0), LocalTime.of(19, 0)),
                    listOf(SupplementIngredient("EPA", 650.0, "mg"), SupplementIngredient("DHA", 450.0, "mg")), tracked = true),
                supplement(3, "Magnesium glycinate", null, 200.0, "mg", listOf(LocalTime.of(21, 30)),
                    listOf(SupplementIngredient("Magnesium", 200.0, "mg")), tracked = false, notes = "For sleep"),
                supplement(4, "Creatine monohydrate", null, 5.0, "g", emptyList(), emptyList(), tracked = false)
            ),
            weekAdherence = mapOf(1L to (6 to 7), 2L to (11 to 14))
        )
        val workouts = (0 until options.period.days).mapNotNull { back ->
            val type = when (back % 7) {
                1, 4 -> "RUNNING"
                2 -> "STRENGTH_TRAINING"
                5 -> "BIKING"
                6 -> if (back % 14 == 6) "YOGA" else null
                else -> null
            } ?: return@mapNotNull null
            val minutes = when (type) { "RUNNING" -> 38; "STRENGTH_TRAINING" -> 50; "BIKING" -> 65; else -> 45 } + random.nextInt(10)
            val start = today.minusDays(back.toLong()).atTime(18, 0).atZone(zone).toInstant()
            ExerciseSessionEntity(recordFingerprint = "x$back", exerciseType = type, startTime = start, endTime = start.plusSeconds(minutes * 60L), durationMinutes = minutes)
        }
        val food = (1..40).flatMap { back ->
            val date = today.minusDays(back.toLong())
            listOf(
                FoodLogEntity(date = date, source = "MANUAL", foodName = "Breakfast", calories = 520, proteinG = 32.0, carbsG = 55.0, fatG = 18.0, fiberG = 8.0, sugarG = 14.0, sodiumMg = 600.0),
                FoodLogEntity(date = date, source = "MANUAL", foodName = "Lunch", calories = 720, proteinG = 45.0, carbsG = 70.0, fatG = 26.0, fiberG = 9.0, sugarG = 9.0, sodiumMg = 1100.0),
                FoodLogEntity(date = date, source = "MANUAL", foodName = "Dinner", calories = 810 + random.nextInt(200), proteinG = 50.0, carbsG = 80.0, fatG = 30.0, fiberG = 7.0, sugarG = 12.0, sodiumMg = 1250.0)
            )
        }
        return DoctorReportBuilder.build(
            DoctorReportInputs(
                now = now,
                options = options,
                profile = UserProfileEntity(
                    heightCm = 178.0, age = 41, dateOfBirth = LocalDate.of(1985, 3, 14), biologicalSex = "MALE",
                    waistCircumferenceCm = 92.5, sleepGoalMinutes = 450, dailyStepGoal = 8000
                ),
                summaries = summaries,
                weights = weights,
                records = records,
                supplements = supplements,
                workouts = workouts,
                food = food,
                latestBodyFatPercent = 21.4,
                dataSources = listOf("Pixel Watch", "Fitbit / Google Health", "Withings")
            )
        )
    }

    private companion object {
        const val VISIT_NOTES = "I've been waking up tired since August even on nights I sleep 7+ hours. " +
            "Home blood pressure has crept up a little - is it time to talk about medication? Also want to review the LDL result from August."
    }
}
