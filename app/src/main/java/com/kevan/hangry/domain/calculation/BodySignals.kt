package com.kevan.hangry.domain.calculation

import com.kevan.hangry.data.local.entity.DailyHealthSummaryEntity
import kotlin.math.ln

/**
 * Early-warning check, like Oura's Symptom Radar or WHOOP's health monitor: when several
 * overnight readings move away from your own normal together, the body is often fighting
 * something - a cold, a hangover, overtraining - before you feel it. One reading off on its own
 * is common and means little, so it takes two or more to raise a flag.
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
     * [history] is the days before [today] (any order, up to a month); each reading needs at
     * least 5 days of its own history. [lutealPhase] raises the skin temperature bar.
     */
    fun evaluate(today: DailyHealthSummaryEntity, history: List<DailyHealthSummaryEntity>, lutealPhase: Boolean = false): Result {
        val signals = mutableListOf<Signal>()
        fun usual(pick: (DailyHealthSummaryEntity) -> Double?) =
            history.mapNotNull(pick).takeIf { it.size >= MIN_HISTORY }?.average()

        today.restingHeartRate?.let { now ->
            usual { it.restingHeartRate }?.let { base ->
                if (now - base >= RHR_RISE_BPM) signals += Signal(Kind.RESTING_HR, now - base)
            }
        }
        today.hrvRmssd?.takeIf { it > 0 }?.let { now ->
            history.mapNotNull { it.hrvRmssd?.takeIf { v -> v > 0 } }.takeIf { it.size >= MIN_HISTORY }?.let { past ->
                val logBase = past.map { ln(it) }.average()
                if (ln(now) - logBase <= ln(1 - HRV_DROP_FRACTION)) {
                    signals += Signal(Kind.HRV, (now / kotlin.math.exp(logBase) - 1) * 100)
                }
            }
        }
        today.respiratoryRate?.let { now ->
            usual { it.respiratoryRate }?.let { base ->
                if (now - base >= BREATHING_RISE) signals += Signal(Kind.BREATHING, now - base)
            }
        }
        today.skinTempDeltaCelsius?.let { now ->
            usual { it.skinTempDeltaCelsius }?.let { base ->
                val bar = SKIN_TEMP_RISE_C + if (lutealPhase) LUTEAL_TEMP_ALLOWANCE_C else 0.0
                if (now - base >= bar) signals += Signal(Kind.SKIN_TEMP, now - base)
            }
        }
        return Result(signals)
    }

    /** Plain-English list for notifications and Dash, e.g. "resting heart rate +4 bpm". */
    fun describe(signal: Signal): String = when (signal.kind) {
        Kind.RESTING_HR -> String.format(java.util.Locale.US, "resting heart rate +%.0f bpm", signal.change)
        Kind.HRV -> String.format(java.util.Locale.US, "HRV %.0f%%", signal.change)
        Kind.BREATHING -> String.format(java.util.Locale.US, "breathing rate +%.1f/min", signal.change)
        Kind.SKIN_TEMP -> String.format(java.util.Locale.US, "skin temperature +%.1f°C", signal.change)
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
