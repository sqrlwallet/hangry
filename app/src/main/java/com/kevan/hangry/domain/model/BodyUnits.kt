package com.kevan.hangry.domain.model

import java.util.Locale
import kotlin.math.roundToInt

/** How weights are shown and typed. Stored values are always kilograms. */
enum class WeightUnit { KG, LB, ST_LB }

/** How lengths (height, tape measurements) are shown and typed. Stored values are always centimetres. */
enum class LengthUnit { CM, IN }

/**
 * Converts what people type (cm or ft/in, kg or lb) into the metric values the app stores, and
 * rejects numbers no adult body could have - a typo shouldn't become someone's calorie target.
 */
object BodyUnits {
    private const val CM_PER_INCH = 2.54
    private const val KG_PER_LB = 0.45359237

    val HEIGHT_CM_RANGE = 120.0..230.0
    val WEIGHT_KG_RANGE = 30.0..300.0

    fun feetInchesToCm(feet: Int, inches: Double): Double = (feet * 12 + inches) * CM_PER_INCH
    fun lbToKg(lb: Double): Double = lb * KG_PER_LB
    fun kgToLb(kg: Double): Double = kg / KG_PER_LB

    /** Whole feet and rounded inches, e.g. 178 cm -> 5 ft 10 in. */
    fun cmToFeetInches(cm: Double): Pair<Int, Int> {
        val totalInches = (cm / CM_PER_INCH).roundToInt()
        return totalInches / 12 to totalInches % 12
    }

    fun cmToInches(cm: Double): Double = cm / CM_PER_INCH
    fun inchesToCm(inches: Double): Double = inches * CM_PER_INCH

    /** Whole stones and pounds to one decimal, e.g. 72.6 kg -> 11 st 6.1 lb (never "13.96 -> 14.0 lb"). */
    fun kgToStonesPounds(kg: Double): Pair<Int, Double> {
        val totalLb = round1(kgToLb(kg))
        val stones = (totalLb / LB_PER_STONE).toInt()
        return stones to round1(totalLb - stones * LB_PER_STONE)
    }

    fun stonesPoundsToKg(stones: Int, pounds: Double): Double = lbToKg(stones * LB_PER_STONE + pounds)

    /**
     * A weight as a single number in [unit], for charts and rates. Stones-and-pounds people
     * read plots and weekly changes in pounds, so ST_LB charts in lb.
     */
    fun weightIn(kg: Double, unit: WeightUnit): Double = if (unit == WeightUnit.KG) kg else kgToLb(kg)

    /** The inverse of [weightIn]. */
    fun weightToKg(value: Double, unit: WeightUnit): Double = if (unit == WeightUnit.KG) value else lbToKg(value)

    fun lengthIn(cm: Double, unit: LengthUnit): Double = if (unit == LengthUnit.CM) cm else cmToInches(cm)
    fun lengthToCm(value: Double, unit: LengthUnit): Double = if (unit == LengthUnit.CM) value else inchesToCm(value)

    /** What people type, with a decimal comma allowed: "72,5" -> 72.5. Null when it isn't a number. */
    fun parseNumber(text: String): Double? = text.trim().replace(',', '.').takeIf { it.isNotEmpty() }?.toDoubleOrNull()

    /**
     * A calculated body metric ([BodyMetric]) shown in the user's units: kg metrics move to lb
     * and cm metrics to inches - the value, its bands and scale, the display text ("62–84",
     * "< 94") and a status like "3.1 kg above range". The explanation text keeps its metric
     * working, since the published formulas are metric.
     */
    fun convertMetric(metric: BodyMetric, weightUnit: WeightUnit, lengthUnit: LengthUnit): BodyMetric {
        val (factor, unitLabel) = when {
            metric.unit == "kg" && weightUnit != WeightUnit.KG -> 1.0 / KG_PER_LB to "lb"
            metric.unit == "cm" && lengthUnit == LengthUnit.IN -> 1.0 / CM_PER_INCH to "in"
            else -> return metric
        }
        val source = metric.unit
        fun convertNumbers(text: String): String = NUMBER.replace(text) { m ->
            val decimals = m.value.substringAfter('.', "").length
            String.format(Locale.US, "%.${decimals}f", m.value.toDouble() * factor)
        }
        return metric.copy(
            value = metric.value?.times(factor),
            displayValue = convertNumbers(metric.displayValue),
            unit = unitLabel,
            bands = metric.bands.map { it.copy(upTo = it.upTo?.times(factor)) },
            scaleMin = metric.scaleMin * factor,
            scaleMax = metric.scaleMax * factor,
            statusOverride = metric.statusOverride?.let { status ->
                Regex("""(\d+(?:\.\d+)?) $source\b""").replace(status) { m ->
                    "${convertNumbers(m.groupValues[1])} $unitLabel"
                }
            }
        )
    }

    private const val LB_PER_STONE = 14.0
    private val NUMBER = Regex("""\d+(?:\.\d+)?""")

    fun plausibleHeight(cm: Double?): Double? = cm?.takeIf { it in HEIGHT_CM_RANGE }
    fun plausibleWeight(kg: Double?): Double? = kg?.takeIf { it in WEIGHT_KG_RANGE }

    /** One decimal place, the precision people weigh themselves to. */
    fun round1(value: Double): Double = (value * 10).roundToInt() / 10.0
}
