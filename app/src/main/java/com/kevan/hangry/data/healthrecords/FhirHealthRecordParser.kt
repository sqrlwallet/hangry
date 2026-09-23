package com.kevan.hangry.data.healthrecords

import com.kevan.hangry.data.local.entity.HealthMarkerEntity
import com.kevan.hangry.data.local.entity.HealthProfileItemEntity
import com.kevan.hangry.domain.model.GlucoseContext
import com.kevan.hangry.domain.model.HealthProfileKind
import com.kevan.hangry.domain.model.MarkerType
import com.kevan.hangry.domain.model.RecordSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * Pulls the few things Hangry tracks out of Health Connect medical records (FHIR R4 JSON):
 * lab/vital Observations identified by LOINC code, plus active Conditions and allergies.
 * Anything it doesn't recognise is skipped rather than guessed at.
 */
object FhirHealthRecordParser {

    private val json = Json { ignoreUnknownKeys = true }

    private data class LoincTarget(val type: MarkerType, val context: GlucoseContext? = null)

    private val LOINC: Map<String, LoincTarget> = mapOf(
        "2345-7" to LoincTarget(MarkerType.BLOOD_GLUCOSE, GlucoseContext.RANDOM),
        "2339-0" to LoincTarget(MarkerType.BLOOD_GLUCOSE, GlucoseContext.RANDOM),
        "41653-7" to LoincTarget(MarkerType.BLOOD_GLUCOSE, GlucoseContext.RANDOM),
        "1558-6" to LoincTarget(MarkerType.BLOOD_GLUCOSE, GlucoseContext.FASTING),
        "4548-4" to LoincTarget(MarkerType.HBA1C),
        "17856-6" to LoincTarget(MarkerType.HBA1C),
        "2093-3" to LoincTarget(MarkerType.TOTAL_CHOLESTEROL),
        "13457-7" to LoincTarget(MarkerType.LDL),
        "18262-6" to LoincTarget(MarkerType.LDL),
        "2089-1" to LoincTarget(MarkerType.LDL),
        "2085-9" to LoincTarget(MarkerType.HDL),
        "2571-8" to LoincTarget(MarkerType.TRIGLYCERIDES),
        "2986-8" to LoincTarget(MarkerType.TESTOSTERONE)
    )
    private const val LOINC_BP_PANEL = "85354-9"
    private const val LOINC_BP_PANEL_ALT = "55284-4"
    private const val LOINC_SYSTOLIC = "8480-6"
    private const val LOINC_DIASTOLIC = "8462-4"

    fun parseObservation(data: String, resourceId: String, zone: ZoneId = ZoneId.systemDefault()): HealthMarkerEntity? {
        val obs = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull() ?: return null
        if (obs.str("status") == "entered-in-error") return null
        val codes = loincCodes(obs["code"])
        val measuredAt = effectiveInstant(obs, zone) ?: return null
        val sourceId = "phr:$resourceId"

        if (LOINC_BP_PANEL in codes || LOINC_BP_PANEL_ALT in codes) {
            val components = (obs["component"] as? JsonArray).orEmpty().map { it.jsonObject }
            val sys = components.firstOrNull { LOINC_SYSTOLIC in loincCodes(it["code"]) }?.let { quantity(it)?.first }
            val dia = components.firstOrNull { LOINC_DIASTOLIC in loincCodes(it["code"]) }?.let { quantity(it)?.first }
            if (sys == null || dia == null) return null
            return marker(MarkerType.BLOOD_PRESSURE, sys, dia, measuredAt, null, sourceId, zone)
        }

        val target = codes.firstNotNullOfOrNull { LOINC[it] } ?: return null
        val (value, unit) = quantity(obs) ?: return null
        val canonical = toCanonical(target.type, value, unit) ?: return null
        return marker(target.type, canonical, null, measuredAt, target.context, sourceId, zone)
    }

    /** What a pregnancy resource says: a due date, a pregnant/not-pregnant status, or both. */
    data class PregnancyInfo(val dueDate: LocalDate?, val pregnant: Boolean?, val observedAt: Instant?)

    private val LOINC_DUE_DATES = setOf("11778-8", "11779-6", "11780-4", "53692-0")
    private const val LOINC_PREGNANCY_STATUS = "82810-3"
    private const val SNOMED_PREGNANT = "77386006"
    private const val SNOMED_NOT_PREGNANT = "60001007"

    fun parsePregnancy(data: String, zone: ZoneId = ZoneId.systemDefault()): PregnancyInfo? {
        val obs = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull() ?: return null
        if (obs.str("status") == "entered-in-error") return null
        val codes = loincCodes(obs["code"])
        val observedAt = effectiveInstant(obs, zone)
        if (codes.any { it in LOINC_DUE_DATES }) {
            val raw = obs.str("valueDateTime") ?: obs.str("valueDate") ?: return null
            val due = runCatching { LocalDate.parse(raw.take(10)) }.getOrNull() ?: return null
            return PregnancyInfo(dueDate = due, pregnant = null, observedAt = observedAt)
        }
        if (LOINC_PREGNANCY_STATUS in codes) {
            val coding = ((obs["valueCodeableConcept"] as? JsonObject)?.get("coding") as? JsonArray).orEmpty()
                .mapNotNull { it.jsonObject.str("code") }
            val pregnant = when {
                SNOMED_PREGNANT in coding -> true
                SNOMED_NOT_PREGNANT in coding -> false
                else -> return null
            }
            return PregnancyInfo(dueDate = null, pregnant = pregnant, observedAt = observedAt)
        }
        return null
    }

    fun parseCondition(data: String, resourceId: String): HealthProfileItemEntity? {
        val obj = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull() ?: return null
        val clinical = firstCode(obj["clinicalStatus"])
        if (clinical != null && clinical in setOf("resolved", "inactive", "remission")) return null
        if (firstCode(obj["verificationStatus"]) in setOf("entered-in-error", "refuted")) return null
        val name = conceptText(obj["code"]) ?: return null
        return HealthProfileItemEntity(
            kind = HealthProfileKind.CONDITION.name,
            name = name,
            source = RecordSource.MEDICAL_RECORD.name,
            sourceRecordId = "phr:$resourceId"
        )
    }

    fun parseAllergy(data: String, resourceId: String): HealthProfileItemEntity? {
        val obj = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull() ?: return null
        if (firstCode(obj["clinicalStatus"]) in setOf("resolved", "inactive")) return null
        if (firstCode(obj["verificationStatus"]) in setOf("entered-in-error", "refuted")) return null
        val name = conceptText(obj["code"]) ?: return null
        val reaction = (obj["reaction"] as? JsonArray)?.firstOrNull()?.jsonObject
            ?.get("manifestation")?.let { it as? JsonArray }?.firstOrNull()
            ?.let { conceptText(it) }
        return HealthProfileItemEntity(
            kind = HealthProfileKind.ALLERGY.name,
            name = name,
            note = reaction,
            source = RecordSource.MEDICAL_RECORD.name,
            sourceRecordId = "phr:$resourceId"
        )
    }

    /** Converts a value in any unit the marker accepts to its canonical unit; null if unknown. */
    fun toCanonical(type: MarkerType, value: Double, unit: String?): Double? {
        val normalized = unit?.trim()?.lowercase()?.replace(" ", "") ?: return value
        if (normalized.isEmpty()) return value
        val alias = when (normalized) {
            "mm[hg]", "mmhg" -> "mmhg"
            "mg/dl", "mg/100ml" -> "mg/dl"
            "mmol/l" -> "mmol/l"
            "%", "percent" -> "%"
            "ng/dl" -> "ng/dl"
            "nmol/l" -> "nmol/l"
            else -> normalized
        }
        return type.units.firstOrNull { it.label.lowercase().replace(" ", "") == alias }?.let { value * it.toCanonical }
    }

    private fun marker(
        type: MarkerType, value: Double, secondary: Double?, at: Instant, context: GlucoseContext?,
        sourceId: String, zone: ZoneId
    ) = HealthMarkerEntity(
        type = type.id,
        value = value,
        secondaryValue = secondary,
        measuredAt = at,
        date = at.atZone(zone).toLocalDate(),
        glucoseContext = context?.name,
        source = RecordSource.MEDICAL_RECORD.name,
        sourceRecordId = sourceId
    )

    private fun loincCodes(concept: JsonElement?): Set<String> =
        (concept as? JsonObject)?.get("coding")?.let { it as? JsonArray }.orEmpty()
            .mapNotNull { coding ->
                val c = coding.jsonObject
                val system = c.str("system")
                c.str("code")?.takeIf { system == null || system.contains("loinc", ignoreCase = true) }
            }.toSet()

    private fun conceptText(concept: JsonElement?): String? {
        val obj = concept as? JsonObject ?: return null
        return obj.str("text")?.takeIf { it.isNotBlank() }
            ?: (obj["coding"] as? JsonArray)?.firstNotNullOfOrNull { it.jsonObject.str("display")?.takeIf { d -> d.isNotBlank() } }
    }

    private fun firstCode(concept: JsonElement?): String? =
        ((concept as? JsonObject)?.get("coding") as? JsonArray)?.firstOrNull()?.jsonObject?.str("code")

    private fun quantity(obj: JsonObject): Pair<Double, String?>? {
        val q = obj["valueQuantity"] as? JsonObject ?: return null
        val value = q["value"]?.jsonPrimitive?.doubleOrNull ?: return null
        return value to (q.str("unit") ?: q.str("code"))
    }

    private fun effectiveInstant(obs: JsonObject, zone: ZoneId): Instant? {
        val raw = obs.str("effectiveDateTime")
            ?: (obs["effectivePeriod"] as? JsonObject)?.str("start")
            ?: obs.str("issued")
            ?: return null
        return runCatching { OffsetDateTime.parse(raw).toInstant() }.getOrNull()
            ?: runCatching { Instant.parse(raw) }.getOrNull()
            ?: runCatching { LocalDate.parse(raw.take(10)).atStartOfDay(zone).toInstant() }.getOrNull()
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
}
