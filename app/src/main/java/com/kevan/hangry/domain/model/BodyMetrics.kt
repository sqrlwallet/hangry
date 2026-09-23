package com.kevan.hangry.domain.model

/** How a metric reads for the user - drives the status colour. */
enum class MetricTone { GOOD, NEUTRAL, CAUTION, RISK }

/**
 * One range of a metric. Bands are ordered and contiguous: a value falls in the first band
 * whose [upTo] it is below; the last band has `upTo = null` and catches everything above.
 */
data class MetricBand(val label: String, val upTo: Double?, val tone: MetricTone)

/** The "info tab" for a metric: what it is, how we got the number, why it matters. */
data class MetricInfo(
    val whatItIs: String,
    val howCalculated: String,
    val whyItMatters: String,
    val source: String
)

data class BodyMetric(
    val id: String,
    val name: String,
    /** Null when the inputs it needs are missing - see [missingInputs]. */
    val value: Double?,
    val displayValue: String,
    val unit: String = "",
    val bands: List<MetricBand> = emptyList(),
    /** Visible range of the scale bar; ignored when there are no bands. */
    val scaleMin: Double = 0.0,
    val scaleMax: Double = 1.0,
    /** Overrides the band label, e.g. "4 kg above range". */
    val statusOverride: String? = null,
    /** Overrides the band tone, e.g. blood pressure where diastolic can be the worse number. */
    val toneOverride: MetricTone? = null,
    val info: MetricInfo,
    val missingInputs: List<String> = emptyList()
) {
    val isAvailable: Boolean get() = value != null

    val band: MetricBand?
        get() {
            val v = value ?: return null
            return bands.firstOrNull { it.upTo == null || v < it.upTo }
        }

    val status: String? get() = if (value == null) null else statusOverride ?: band?.label

    val tone: MetricTone get() = toneOverride ?: band?.tone ?: MetricTone.NEUTRAL
}

data class BodyMetricGroup(
    val id: String,
    val title: String,
    val description: String,
    val metrics: List<BodyMetric>
)

data class BodyMetricsInput(
    val heightCm: Double? = null,
    val weightKg: Double? = null,
    val age: Int? = null,
    val sex: BiologicalSex? = null,
    val neckCm: Double? = null,
    val chestCm: Double? = null,
    val waistCm: Double? = null,
    val hipCm: Double? = null,
    /** From the latest saved body-fat scan, if any. */
    val bodyFatPercent: Double? = null,
    val bodyFatSource: String? = null,
    /** Mean daily total calories over recent synced days, if any. */
    val averageDailyBurnKcal: Double? = null,
    val averageDailyBurnDays: Int = 0,
    /** Maintenance + goal calories from the last 7 days of steps and workouts. */
    val energy: EnergyBalanceResult? = null
)

data class BodyMetricsSnapshot(
    val input: BodyMetricsInput,
    val bodyFatScanDate: java.time.LocalDate?,
    val groups: List<BodyMetricGroup>
)
