package com.kevan.hangry.domain.calculation

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Daily water: the target, today's intake against it, and which Health Connect drinks count.
 *
 * Intake for a day = drinks other apps wrote to Health Connect (synced into the daily summary)
 * + drinks logged in Hangry (kept locally). Hangry's own HydrationRecords are never read back,
 * so a glass logged here is counted once.
 */
object WaterIntake {

    /** Same 30-35 ml/kg guideline as BodyMetricsCalculator's "Water target" - its midpoint. */
    const val ML_PER_KG_LOW = 30.0
    const val ML_PER_KG_HIGH = 35.0

    /** With no weight on file: a common adult baseline (EFSA's 2.0 L for women; men need a bit more). */
    const val DEFAULT_TARGET_ML = 2000

    const val GLASS_ML = 250
    const val BOTTLE_ML = 500
    /** A sane cap for one custom entry - catches a typo like 25000. */
    const val MAX_SINGLE_ML = 3000

    /** Prefix of the clientRecordId on every HydrationRecord Hangry writes. */
    const val CLIENT_ID_PREFIX = "hangry-water-"

    fun clientId(entryId: Long): String = "$CLIENT_ID_PREFIX$entryId"

    /** Daily target in ml from body weight, or [DEFAULT_TARGET_ML] when there's no weight. */
    fun targetMl(weightKg: Double?): Int =
        if (weightKg == null || weightKg <= 0) DEFAULT_TARGET_ML
        else (weightKg * (ML_PER_KG_LOW + ML_PER_KG_HIGH) / 2).roundToInt()

    /** Everything drunk on a day: other apps' total (liters, from the daily summary) plus Hangry's log. */
    fun totalMl(externalLiters: Double?, loggedMl: Int): Int =
        ((externalLiters ?: 0.0) * 1000).roundToInt().coerceAtLeast(0) + loggedMl.coerceAtLeast(0)

    /** 0..1 progress toward [targetMl]. */
    fun progress(intakeMl: Int, targetMl: Int): Float =
        if (targetMl <= 0) 0f else (intakeMl.toFloat() / targetMl).coerceIn(0f, 1f)

    /** A custom amount typed by the user, or null if it isn't a sensible single drink. */
    fun parseCustomMl(text: String): Int? =
        text.trim().toIntOrNull()?.takeIf { it in 1..MAX_SINGLE_ML }

    /** One HydrationRecord as read from Health Connect. */
    data class Drink(val time: Instant, val source: String, val ml: Double, val clientRecordId: String?)

    /** Hangry's own write: by package, or by our client id (survives a package rename/restore). */
    fun isOwn(drink: Drink, ownPackage: String): Boolean =
        drink.source == ownPackage || drink.clientRecordId?.startsWith(CLIENT_ID_PREFIX) == true

    /**
     * Liters per day from other apps only. Hangry's own records are dropped (they're already in
     * the local log), and so is any other app's mirror of one of them - same volume within a
     * couple of minutes. A drink another app mirrored is counted once, like meals; two identical
     * glasses from the same app are both kept.
     */
    fun externalDailyLiters(
        drinks: List<Drink>,
        ownPackage: String,
        zone: ZoneId,
        start: LocalDate,
        end: LocalDate
    ): Map<LocalDate, Double> {
        val (own, others) = drinks.partition { isOwn(it, ownPackage) }
        val notMirrors = others.filterNot { d -> own.any { sameDrink(it, d) } }
        val distinct = HealthDedup.dropCrossAppCopies(notMirrors.map {
            HealthDedup.Point(it, it.time, it.source, it.ml.roundToInt().toString())
        })
        return distinct
            .groupBy { it.time.atZone(zone).toLocalDate() }
            .filterKeys { !it.isBefore(start) && !it.isAfter(end) }
            .mapValues { (_, day) -> day.sumOf { it.ml } / 1000.0 }
            .filterValues { it > 0 }
    }

    private fun sameDrink(a: Drink, b: Drink): Boolean =
        a.ml.roundToInt() == b.ml.roundToInt() &&
            abs(Duration.between(a.time, b.time).toMillis()) <= HealthDedup.SAME_APP_TOLERANCE.toMillis()
}
