package com.kevan.hangry.domain.model

import kotlin.math.roundToInt

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

    fun plausibleHeight(cm: Double?): Double? = cm?.takeIf { it in HEIGHT_CM_RANGE }
    fun plausibleWeight(kg: Double?): Double? = kg?.takeIf { it in WEIGHT_KG_RANGE }

    /** One decimal place, the precision people weigh themselves to. */
    fun round1(value: Double): Double = (value * 10).roundToInt() / 10.0
}
