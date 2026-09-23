package com.kevan.hangry.domain.calculation

import com.kevan.hangry.domain.model.BiologicalSex
import com.kevan.hangry.domain.model.BodyMetric
import com.kevan.hangry.domain.model.CycleStats
import com.kevan.hangry.domain.model.GlucoseContext
import com.kevan.hangry.domain.model.GoalDirection
import com.kevan.hangry.domain.model.GoalProgress
import com.kevan.hangry.domain.model.MarkerGoal
import com.kevan.hangry.domain.model.MarkerReading
import com.kevan.hangry.domain.model.MarkerType
import com.kevan.hangry.domain.model.MetricBand
import com.kevan.hangry.domain.model.MetricInfo
import com.kevan.hangry.domain.model.MetricTone
import com.kevan.hangry.domain.model.MetricTone.CAUTION
import com.kevan.hangry.domain.model.MetricTone.GOOD
import com.kevan.hangry.domain.model.MetricTone.NEUTRAL
import com.kevan.hangry.domain.model.MetricTone.RISK
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.max

/**
 * Reference ranges, explanations and goal progress for lab/vital markers. These are general
 * adult reference ranges from public guidelines for context while tracking - not a diagnosis.
 */
object HealthMarkerCalculator {

    /** A marker's latest reading as a displayable metric (value, status, ranges, info). */
    fun describe(type: MarkerType, latest: MarkerReading?, sex: BiologicalSex?): BodyMetric {
        val ranges = ranges(type, sex, latest?.glucoseContext)
        val info = info(type, latest, sex)
        if (latest == null) {
            return BodyMetric(
                id = type.id, name = type.label, value = null, displayValue = "—", unit = type.canonicalUnit,
                bands = ranges.bands, scaleMin = ranges.min, scaleMax = ranges.max, info = info,
                missingInputs = listOf("A reading")
            )
        }
        val display = format(type, latest.value, latest.secondaryValue)
        if (type == MarkerType.BLOOD_PRESSURE) {
            val category = bloodPressureCategory(latest.value, latest.secondaryValue ?: 0.0)
            return BodyMetric(
                id = type.id, name = type.label, value = latest.value, displayValue = display, unit = type.canonicalUnit,
                bands = ranges.bands, scaleMin = ranges.min, scaleMax = ranges.max,
                statusOverride = category.first, toneOverride = category.second, info = info
            )
        }
        return BodyMetric(
            id = type.id, name = type.label, value = latest.value, displayValue = display, unit = type.canonicalUnit,
            bands = ranges.bands, scaleMin = ranges.min, scaleMax = ranges.max, info = info
        )
    }

    fun format(type: MarkerType, value: Double, secondary: Double? = null): String {
        val main = String.format(Locale.US, "%.${type.decimals}f", value)
        return if (type.hasSecondaryValue && secondary != null) "$main/${String.format(Locale.US, "%.0f", secondary)}" else main
    }

    /** AHA/ACC 2017 categories - the worse of systolic and diastolic decides. */
    fun bloodPressureCategory(systolic: Double, diastolic: Double): Pair<String, MetricTone> = when {
        systolic > 180 || diastolic > 120 -> "Very high - seek care" to RISK
        systolic >= 140 || diastolic >= 90 -> "High (stage 2)" to RISK
        systolic >= 130 || diastolic >= 80 -> "High (stage 1)" to CAUTION
        systolic >= 120 -> "Elevated" to CAUTION
        systolic < 90 || diastolic < 60 -> "Low" to CAUTION
        else -> "Normal" to GOOD
    }

    data class Ranges(val bands: List<MetricBand>, val min: Double, val max: Double)

    fun ranges(type: MarkerType, sex: BiologicalSex?, glucoseContext: GlucoseContext?): Ranges = when (type) {
        MarkerType.BLOOD_PRESSURE -> Ranges(
            listOf(
                MetricBand("Low", 90.0, CAUTION),
                MetricBand("Normal", 120.0, GOOD),
                MetricBand("Elevated", 130.0, CAUTION),
                MetricBand("Stage 1", 140.0, CAUTION),
                MetricBand("Stage 2", 180.0, RISK),
                MetricBand("Very high", null, RISK)
            ), 80.0, 190.0
        )
        MarkerType.BLOOD_GLUCOSE -> when (glucoseContext) {
            GlucoseContext.FASTING, GlucoseContext.BEFORE_MEAL -> Ranges(
                listOf(
                    MetricBand("Low", 70.0, CAUTION),
                    MetricBand("Normal", 100.0, GOOD),
                    MetricBand("Prediabetes range", 126.0, CAUTION),
                    MetricBand("Diabetes range", null, RISK)
                ), 50.0, 200.0
            )
            else -> Ranges(
                listOf(
                    MetricBand("Low", 70.0, CAUTION),
                    MetricBand("Normal", 140.0, GOOD),
                    MetricBand("Elevated", 200.0, CAUTION),
                    MetricBand("High", null, RISK)
                ), 50.0, 260.0
            )
        }
        MarkerType.HBA1C -> Ranges(
            listOf(
                MetricBand("Normal", 5.7, GOOD),
                MetricBand("Prediabetes range", 6.5, CAUTION),
                MetricBand("Diabetes range", null, RISK)
            ), 4.0, 10.0
        )
        MarkerType.TOTAL_CHOLESTEROL -> Ranges(
            listOf(
                MetricBand("Desirable", 200.0, GOOD),
                MetricBand("Borderline high", 240.0, CAUTION),
                MetricBand("High", null, RISK)
            ), 120.0, 300.0
        )
        MarkerType.LDL -> Ranges(
            listOf(
                MetricBand("Optimal", 100.0, GOOD),
                MetricBand("Near optimal", 130.0, GOOD),
                MetricBand("Borderline high", 160.0, CAUTION),
                MetricBand("High", 190.0, RISK),
                MetricBand("Very high", null, RISK)
            ), 50.0, 220.0
        )
        MarkerType.HDL -> {
            val low = when (sex) {
                BiologicalSex.MALE -> 40.0
                BiologicalSex.FEMALE -> 50.0
                else -> 45.0
            }
            Ranges(
                listOf(
                    MetricBand("Low", low, CAUTION),
                    MetricBand("OK", 60.0, NEUTRAL),
                    MetricBand("Protective", null, GOOD)
                ), 20.0, 90.0
            )
        }
        MarkerType.TRIGLYCERIDES -> Ranges(
            listOf(
                MetricBand("Normal", 150.0, GOOD),
                MetricBand("Borderline high", 200.0, CAUTION),
                MetricBand("High", 500.0, RISK),
                MetricBand("Very high", null, RISK)
            ), 40.0, 600.0
        )
        MarkerType.TESTOSTERONE -> when (sex) {
            BiologicalSex.MALE -> Ranges(
                listOf(
                    MetricBand("Low", 300.0, CAUTION),
                    MetricBand("Typical", 1000.0, GOOD),
                    MetricBand("High", null, NEUTRAL)
                ), 100.0, 1200.0
            )
            BiologicalSex.FEMALE -> Ranges(
                listOf(
                    MetricBand("Low", 15.0, NEUTRAL),
                    MetricBand("Typical", 70.0, GOOD),
                    MetricBand("High", null, CAUTION)
                ), 0.0, 120.0
            )
            // Ranges differ several-fold by sex; without it there's no honest band to show.
            else -> Ranges(emptyList(), 0.0, 1.0)
        }
    }

    /** A sensible starting target: the edge of the healthy range in the goal's direction. */
    fun suggestedGoal(type: MarkerType, sex: BiologicalSex?): Pair<Double, Double?> = when (type) {
        MarkerType.BLOOD_PRESSURE -> 120.0 to 80.0
        MarkerType.BLOOD_GLUCOSE -> 100.0 to null
        MarkerType.HBA1C -> 5.6 to null
        MarkerType.TOTAL_CHOLESTEROL -> 200.0 to null
        MarkerType.LDL -> 100.0 to null
        MarkerType.HDL -> 60.0 to null
        MarkerType.TRIGLYCERIDES -> 150.0 to null
        MarkerType.TESTOSTERONE -> (if (sex == BiologicalSex.FEMALE) 30.0 else 500.0) to null
    }

    fun progress(goal: MarkerGoal, latest: MarkerReading?): GoalProgress {
        if (latest == null) return GoalProgress(goal, null, null, reached = false, remaining = null)
        val lower = goal.direction == GoalDirection.LOWER
        fun reachedFor(value: Double, target: Double) = if (lower) value <= target else value >= target
        val primaryReached = reachedFor(latest.value, goal.targetValue)
        val secondaryReached = goal.targetSecondary == null || latest.secondaryValue == null ||
            reachedFor(latest.secondaryValue, goal.targetSecondary)
        val remaining = max(0.0, if (lower) latest.value - goal.targetValue else goal.targetValue - latest.value)
        val start = goal.startValue
        val fraction = if (start != null && start != goal.targetValue) {
            ((start - latest.value) / (start - goal.targetValue)).coerceIn(0.0, 1.0)
        } else if (primaryReached) 1.0 else null
        return GoalProgress(goal, latest, fraction, reached = primaryReached && secondaryReached, remaining = remaining)
    }

    /** Averages ignore implausible gaps (a missed log shows up as a 2-month "cycle"). */
    fun cycleStats(periods: List<Pair<LocalDate, LocalDate?>>, today: LocalDate): CycleStats {
        val sorted = periods.sortedBy { it.first }
        val cycleLengths = sorted.zipWithNext { a, b -> ChronoUnit.DAYS.between(a.first, b.first).toInt() }
            .filter { it in 15..60 }
        val periodLengths = sorted.mapNotNull { (start, end) ->
            end?.let { ChronoUnit.DAYS.between(start, it).toInt() + 1 }?.takeIf { it in 1..15 }
        }
        val averageCycle = cycleLengths.takeIf { it.isNotEmpty() }?.average()
        val averagePeriod = periodLengths.takeIf { it.isNotEmpty() }?.average()
        val last = sorted.lastOrNull()
        val lastStart = last?.first
        val inPeriod = last != null && !today.isBefore(last.first) && (
            last.second?.let { !today.isAfter(it) }
                ?: (ChronoUnit.DAYS.between(last.first, today) < (averagePeriod ?: 5.0))
            )
        return CycleStats(
            periodCount = sorted.size,
            averageCycleDays = averageCycle,
            averagePeriodDays = averagePeriod,
            lastPeriodStart = lastStart,
            predictedNextStart = if (lastStart != null && averageCycle != null) lastStart.plusDays(Math.round(averageCycle)) else null,
            currentCycleDay = lastStart?.let { ChronoUnit.DAYS.between(it, today).toInt() + 1 }?.takeIf { it >= 1 },
            inPeriodNow = inPeriod
        )
    }

    private fun info(type: MarkerType, latest: MarkerReading?, sex: BiologicalSex?): MetricInfo {
        val reading = latest?.let {
            val context = it.glucoseContext?.let { c -> " (${c.label.lowercase()})" } ?: ""
            "Your latest: ${format(type, it.value, it.secondaryValue)} ${type.canonicalUnit}$context on ${it.date}, ${it.source.label.lowercase()}."
        } ?: "No reading yet. Add one from a home monitor or a lab report."
        return when (type) {
            MarkerType.BLOOD_PRESSURE -> MetricInfo(
                whatItIs = "The pressure of blood against your artery walls: systolic (when the heart beats) over diastolic (between beats).",
                howCalculated = "$reading The category uses whichever of the two numbers is worse.",
                whyItMatters = "Raised blood pressure usually has no symptoms but strains the heart, arteries and kidneys over years. Sleep, salt, alcohol, activity and weight all move it. Readings vary a lot through the day, so look at the trend across several readings taken seated and rested.",
                source = "American Heart Association / ACC blood pressure categories (2017)."
            )
            MarkerType.BLOOD_GLUCOSE -> MetricInfo(
                whatItIs = "The amount of sugar in your blood right now.",
                howCalculated = "$reading The range shown depends on when it was taken - fasting readings have tighter limits than after a meal.",
                whyItMatters = "Consistently high blood sugar damages blood vessels and nerves over time. Fasting readings are the most comparable day to day; after-meal readings show how you handle a particular food.",
                source = "American Diabetes Association Standards of Care diagnostic thresholds."
            )
            MarkerType.HBA1C -> MetricInfo(
                whatItIs = "Your average blood sugar over roughly the last 3 months, measured as the share of red-blood-cell haemoglobin with sugar attached.",
                howCalculated = reading,
                whyItMatters = "It smooths out daily swings, so it's the steadier picture of blood sugar control. It changes slowly - retesting more often than every 3 months rarely shows much.",
                source = "American Diabetes Association Standards of Care."
            )
            MarkerType.TOTAL_CHOLESTEROL -> MetricInfo(
                whatItIs = "All the cholesterol carried in your blood - LDL, HDL and others combined.",
                howCalculated = reading,
                whyItMatters = "A quick overview, but the split matters more than the total: high HDL raises the total in a good way, so read it alongside LDL and HDL.",
                source = "NCEP Adult Treatment Panel III."
            )
            MarkerType.LDL -> MetricInfo(
                whatItIs = "Low-density lipoprotein - the \"bad\" cholesterol that can build up in artery walls.",
                howCalculated = reading,
                whyItMatters = "LDL is the lipid most directly linked to heart attack and stroke risk. Diet (saturated fat, fibre), exercise, weight and genetics all affect it.",
                source = "NCEP Adult Treatment Panel III."
            )
            MarkerType.HDL -> MetricInfo(
                whatItIs = "High-density lipoprotein - the \"good\" cholesterol that carries cholesterol away from the arteries.",
                howCalculated = reading + if (sex == null) " Set your sex in Settings for the right low threshold (40 men / 50 women)." else "",
                whyItMatters = "Higher is generally better. Regular aerobic exercise, not smoking and losing excess weight tend to raise it.",
                source = "NCEP Adult Treatment Panel III."
            )
            MarkerType.TRIGLYCERIDES -> MetricInfo(
                whatItIs = "The main type of fat in your blood, used for energy.",
                howCalculated = "$reading Best measured fasting - a recent meal pushes it up.",
                whyItMatters = "High levels are linked with heart disease and often travel with high blood sugar and belly fat. Refined carbs, sugar and alcohol raise them most.",
                source = "NCEP Adult Treatment Panel III."
            )
            MarkerType.TESTOSTERONE -> MetricInfo(
                whatItIs = "The main male sex hormone, also present in smaller amounts in women.",
                howCalculated = reading + if (sex == null || sex == BiologicalSex.OTHER) " Typical ranges differ several-fold by sex, so ranges only show once your sex is set to male or female." else " Best measured in the morning, when it peaks.",
                whyItMatters = "It affects energy, muscle, libido, mood and bone density. Sleep, body fat, stress and training load all influence it, and levels naturally drift down with age.",
                source = "American Urological Association (2018) for men; typical adult female reference range."
            )
        }
    }
}
