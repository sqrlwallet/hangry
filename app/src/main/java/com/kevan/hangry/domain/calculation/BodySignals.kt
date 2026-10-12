package com.kevan.hangry.domain.calculation

import com.kevan.hangry.data.local.entity.DailyHealthSummaryEntity
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Early-warning check, like Oura's Symptom Radar or WHOOP's health monitor: when several
 * overnight readings move away from your own normal together, the body is often fighting
 * something - a cold, a hangover, overtraining - before you feel it. One reading off on its own
 * is common and means little, so it takes two or more to raise a flag. Each is judged against
 * your own usual and how much it normally varies (see [SPREAD_MULTIPLIER]).
 */
object BodySignals {

    enum class Kind { RESTING_HR, HRV, BREATHING, SKIN_TEMP }

    /** [change] is today minus your usual, in the reading's own unit (HRV: percent). */
    data class Signal(val kind: Kind, val change: Double)

    data class Result(val signals: List<Signal>) {
        val flagged: Boolean get() = signals.size >= MIN_SIGNALS
    }

    const val MIN_SIGNALS = 2
    private const val MIN_HISTORY = 5
    const val RHR_RISE_BPM = 3.0
    /** HRV this far below your usual (on a log scale, so 20% down = about -0.22). */
    const val HRV_DROP_FRACTION = 0.20
    const val BREATHING_RISE = 1.0
    const val SKIN_TEMP_RISE_C = 0.5
    /** Skin temperature runs about this much warmer after ovulation - expected, not a warning. */
    const val LUTEAL_TEMP_ALLOWANCE_C = 0.3

    /**
     * How unusual a change must be, in your own night-to-night spread (standard deviations). The
     * fixed minimums above still apply, so a very steady person isn't flagged for tiny moves -
     * and someone whose readings swing a lot needs a bigger move than the minimum.
     */
    const val SPREAD_MULTIPLIER = 1.5

    /**
     * [history] is the days before [today] (any order, up to a month); each reading needs at
     * least 5 days of its own history. [lutealPhase] raises the skin temperature bar.
     */
    fun evaluate(today: DailyHealthSummaryEntity, history: List<DailyHealthSummaryEntity>, lutealPhase: Boolean = false): Result =
        Result(compare(today, history, lutealPhase).mapNotNull { c -> c.change?.takeIf { c.off }?.let { Signal(c.kind, it) } })

    /** One reading tonight against your usual - what the Body check card shows. */
    data class Comparison(
        val kind: Kind,
        val today: Double?,
        /** Average of the history; null with under 5 days of it. */
        val usual: Double?,
        /** Today minus usual (HRV: percent). */
        val change: Double?,
        /** Far enough from usual to count as a signal. */
        val off: Boolean
    )

    /** Every reading with data today or in the history, compared with your usual. */
    fun compare(today: DailyHealthSummaryEntity, history: List<DailyHealthSummaryEntity>, lutealPhase: Boolean = false): List<Comparison> {
        fun check(kind: Kind, now: Double?, past: List<Double>, minimum: Double, transform: (Double) -> Double = { it }): Comparison? {
            if (now == null && past.isEmpty()) return null
            val enough = past.size >= MIN_HISTORY
            val usual = if (enough) past.average() else null
            val change = if (now != null && usual != null) transform(now) - transform(usual) else null
            val bar = if (enough) maxOf(minimum, SPREAD_MULTIPLIER * spread(past.map(transform))) else Double.MAX_VALUE
            return Comparison(kind, now, usual, change, change != null && change >= bar)
        }
        val hrvPast = history.mapNotNull { it.hrvRmssd?.takeIf { v -> v > 0 } }
        val hrv = check(Kind.HRV, today.hrvRmssd?.takeIf { it > 0 }, hrvPast, -ln(1 - HRV_DROP_FRACTION)) { -ln(it) }
            // On the log scale "change" is the size of the drop; report it as a percent instead.
            ?.let { c ->
                val logBase = hrvPast.takeIf { it.size >= MIN_HISTORY }?.map { ln(it) }?.average()
                val pct = if (c.today != null && logBase != null) (c.today / exp(logBase) - 1) * 100 else null
                c.copy(usual = logBase?.let { exp(it) }, change = pct)
            }
        val tempBar = SKIN_TEMP_RISE_C + if (lutealPhase) LUTEAL_TEMP_ALLOWANCE_C else 0.0
        return listOfNotNull(
            check(Kind.RESTING_HR, today.restingHeartRate, history.mapNotNull { it.restingHeartRate }, RHR_RISE_BPM),
            hrv,
            check(Kind.BREATHING, today.respiratoryRate, history.mapNotNull { it.respiratoryRate }, BREATHING_RISE),
            check(Kind.SKIN_TEMP, today.skinTempDeltaCelsius, history.mapNotNull { it.skinTempDeltaCelsius }, tempBar)
        )
    }

    private fun spread(values: List<Double>): Double {
        val mean = values.average()
        return sqrt(values.sumOf { (it - mean) * (it - mean) } / (values.size - 1).coerceAtLeast(1))
    }

    /** Plain-English list for notifications and Dash, e.g. "resting heart rate +4 bpm". */
    fun describe(signal: Signal, fahrenheit: Boolean = false): String = when (signal.kind) {
        Kind.RESTING_HR -> String.format(java.util.Locale.US, "resting heart rate +%.0f bpm", signal.change)
        Kind.HRV -> String.format(java.util.Locale.US, "HRV %.0f%%", signal.change)
        Kind.BREATHING -> String.format(java.util.Locale.US, "breathing rate +%.1f/min", signal.change)
        Kind.SKIN_TEMP -> "skin temperature " + com.kevan.hangry.domain.model.TemperatureText.delta(signal.change, fahrenheit)
    }

    /** Stored form: "SKIN_TEMP:0.70,RESTING_HR:4.00". Null when nothing is off. */
    fun encode(signals: List<Signal>): String? =
        signals.takeIf { it.isNotEmpty() }?.joinToString(",") { String.format(java.util.Locale.US, "%s:%.2f", it.kind.name, it.change) }

    fun decode(text: String?): List<Signal> = text.orEmpty().split(",").mapNotNull { part ->
        val (kind, change) = part.split(":").takeIf { it.size == 2 } ?: return@mapNotNull null
        val k = Kind.entries.firstOrNull { it.name == kind } ?: return@mapNotNull null
        change.toDoubleOrNull()?.let { Signal(k, it) }
    }
}
