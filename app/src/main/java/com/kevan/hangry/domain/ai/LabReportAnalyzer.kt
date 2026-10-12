package com.kevan.hangry.domain.ai

import com.kevan.hangry.domain.model.GlucoseContext
import com.kevan.hangry.domain.model.MarkerType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import java.time.LocalDate

interface LabReportAnalyzer {
    /** Reads every test result printed on 1+ photos of a lab/medical report. Nothing is saved. */
    suspend fun readReport(imagesBase64: List<String>, markers: List<MarkerType>): Result<LabReportScan>
}

/** Why a scanned value isn't ready to add as-is. */
enum class ScanIssue { NO_VALUE, UNKNOWN_UNIT, IMPLAUSIBLE }

data class ScannedLabValue(
    /** The test name exactly as printed. */
    val printedName: String,
    /** Null when the test isn't one Hangry tracks. */
    val type: MarkerType?,
    /** In the marker's canonical unit; null when missing or the unit couldn't be converted. */
    val value: Double?,
    /** Diastolic for blood pressure. */
    val secondaryValue: Double?,
    /** Value and unit as printed, for display. */
    val printedValue: String,
    val unit: String?,
    val referenceRange: String?,
    val glucoseContext: GlucoseContext?,
    val issue: ScanIssue?
) {
    val selectedByDefault: Boolean get() = type != null && issue == null
}

data class LabReportScan(
    /** When the sample was collected, if printed. */
    val reportDate: LocalDate?,
    val values: List<ScannedLabValue>
) {
    val tracked: List<ScannedLabValue> get() = values.filter { it.type != null }
    val untracked: List<ScannedLabValue> get() = values.filter { it.type == null }
}

@Serializable
internal data class LabReportJson(
    val reportDate: JsonElement? = null,
    val results: List<LabResultJson> = emptyList()
)

@Serializable
internal data class LabResultJson(
    val marker: String? = null,
    val name: String? = null,
    val value: JsonElement? = null,
    val secondaryValue: JsonElement? = null,
    val unit: String? = null,
    val date: JsonElement? = null,
    val referenceRange: JsonElement? = null,
    val fasting: JsonElement? = null
)

/** Turns the model's answer into reviewable values: marker mapping, unit conversion and sanity checks. */
object LabReportParser {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }

    /** The marker list the model maps onto. */
    fun markerCatalog(markers: List<MarkerType>): String = markers.joinToString("\n") { m ->
        "- ${m.id}: ${m.label}, canonical unit ${m.canonicalUnit}" +
            (if (m.units.size > 1) " (also ${m.units.drop(1).joinToString { it.label }})" else "") +
            (if (m.hasSecondaryValue) " - value = systolic, secondaryValue = diastolic" else "")
    }

    /**
     * [allowed] are the markers this user can track; anything else is listed as not tracked.
     * Throws when the answer has no JSON object in it.
     */
    fun parse(raw: String, allowed: Set<MarkerType> = MarkerType.entries.toSet(), today: LocalDate = LocalDate.now()): LabReportScan {
        val report = json.decodeFromString(LabReportJson.serializer(), jsonObjectIn(raw))
        val values = report.results.mapNotNull { r -> toValue(r, allowed) }
        val reportDate = (listOf(report.reportDate) + report.results.map { it.date })
            .firstNotNullOfOrNull { date(it.text(), today) }
        return LabReportScan(reportDate, values)
    }

    /** Strips code fences and any chatter around the JSON object. */
    internal fun jsonObjectIn(raw: String): String {
        val s = extractJsonPayload(raw)
        val start = s.indexOf('{')
        val end = s.lastIndexOf('}')
        return if (start >= 0 && end > start) s.substring(start, end + 1) else s
    }

    private fun toValue(r: LabResultJson, allowed: Set<MarkerType>): ScannedLabValue? {
        val name = r.name?.trim()?.takeIf { it.isNotEmpty() } ?: r.marker?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val valueText = r.value.text()?.trim().orEmpty()
        val type = markerFor(r.marker, name)?.takeIf { it in allowed }
        var raw = number(valueText)
        var rawSecondary = number(r.secondaryValue.text())
        // "120/80" printed as one value.
        if (type == MarkerType.BLOOD_PRESSURE && rawSecondary == null && '/' in valueText) {
            raw = number(valueText.substringBefore('/'))
            rawSecondary = number(valueText.substringAfter('/'))
        }
        val unit = r.unit?.trim()?.takeIf { it.isNotEmpty() }
        val printed = listOf(valueText.ifEmpty { "—" }, unit.orEmpty()).filter { it.isNotEmpty() }.joinToString(" ")
        val range = r.referenceRange.text()?.trim()?.takeIf { it.isNotEmpty() && it != "null" }
        if (type == null) {
            return ScannedLabValue(name, null, null, null, printed, unit, range, null, null)
        }
        val value = raw?.let { type.toCanonical(it, unit) }
        val secondary = rawSecondary?.let { type.toCanonical(it, unit) }
        val issue = when {
            raw == null || (type.hasSecondaryValue && rawSecondary == null) -> ScanIssue.NO_VALUE
            value == null -> ScanIssue.UNKNOWN_UNIT
            !isPlausible(type, value, secondary) -> ScanIssue.IMPLAUSIBLE
            else -> null
        }
        return ScannedLabValue(
            printedName = name,
            type = type,
            value = value,
            secondaryValue = secondary.takeIf { type.hasSecondaryValue },
            printedValue = printed,
            unit = unit,
            referenceRange = range,
            glucoseContext = if (type == MarkerType.BLOOD_GLUCOSE) glucoseContext(name, r.fasting) else null,
            issue = issue
        )
    }

    /** The model's key first; a cautious match on the printed name as a fallback. Ratios and look-alikes never map. */
    fun markerFor(key: String?, printedName: String): MarkerType? {
        val n = printedName.lowercase()
        val excluded = listOf("ratio", "non-hdl", "non hdl", "vldl", "free", "bioavailable", "urine", "/hdl", "hdl/", "ldl/")
        if (excluded.any { it in n }) return null
        key?.trim()?.lowercase()?.let { k -> MarkerType.fromId(k)?.let { return it } }
        return when {
            "hdl" in n -> MarkerType.HDL
            "ldl" in n -> MarkerType.LDL
            "triglycer" in n -> MarkerType.TRIGLYCERIDES
            "a1c" in n || "glycated" in n || "glycosylated" in n -> MarkerType.HBA1C
            "cholesterol" in n -> MarkerType.TOTAL_CHOLESTEROL
            "glucose" in n || "blood sugar" in n -> MarkerType.BLOOD_GLUCOSE
            "testosterone" in n -> MarkerType.TESTOSTERONE
            "blood pressure" in n -> MarkerType.BLOOD_PRESSURE
            else -> null
        }
    }

    /** Wide physiological limits (canonical units) - outside them it's almost certainly a misread. */
    fun isPlausible(type: MarkerType, value: Double, secondary: Double? = null): Boolean {
        val range = when (type) {
            MarkerType.BLOOD_PRESSURE -> 60.0..260.0
            MarkerType.BLOOD_GLUCOSE -> 20.0..800.0
            MarkerType.HBA1C -> 3.0..20.0
            MarkerType.TOTAL_CHOLESTEROL -> 50.0..700.0
            MarkerType.LDL -> 10.0..500.0
            MarkerType.HDL -> 5.0..200.0
            MarkerType.TRIGLYCERIDES -> 20.0..5000.0
            MarkerType.TESTOSTERONE -> 2.0..2500.0
        }
        if (value !in range) return false
        if (type.hasSecondaryValue) return secondary != null && secondary in 30.0..160.0 && secondary < value
        return true
    }

    private fun glucoseContext(name: String, fasting: JsonElement?): GlucoseContext {
        val n = name.lowercase()
        val flag = (fasting as? JsonPrimitive)?.let { it.booleanOrNull ?: it.contentOrNull?.lowercase()?.let { s -> s == "yes" || s == "true" } }
        return when {
            flag == true || "fasting" in n -> GlucoseContext.FASTING
            "post" in n || "pp" in n.split(' ', '(', ')', '-') || "after" in n -> GlucoseContext.AFTER_MEAL
            else -> GlucoseContext.RANDOM
        }
    }

    /** First number in a printed value: "5.4", "<0.5", "1,234", "5,4". */
    internal fun number(text: String?): Double? {
        val match = Regex("""\d[\d,]*(?:\.\d+)?""").find(text ?: return null)?.value ?: return null
        val normalized = if (Regex("""\d{1,3}(,\d{3})+(\.\d+)?""").matches(match)) match.replace(",", "") else match.replace(',', '.')
        return normalized.toDoubleOrNull()
    }

    private fun date(text: String?, today: LocalDate): LocalDate? =
        text?.trim()?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.takeIf { !it.isAfter(today) }

    private fun JsonElement?.text(): String? = when (this) {
        null -> null
        is JsonPrimitive -> contentOrNull
        else -> toString()
    }
}
