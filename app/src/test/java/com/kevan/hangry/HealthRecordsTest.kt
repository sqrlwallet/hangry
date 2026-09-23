package com.kevan.hangry

import com.kevan.hangry.data.healthrecords.FhirHealthRecordParser
import com.kevan.hangry.domain.calculation.HealthMarkerCalculator
import com.kevan.hangry.domain.model.BiologicalSex
import com.kevan.hangry.domain.model.GlucoseContext
import com.kevan.hangry.domain.model.GoalDirection
import com.kevan.hangry.domain.model.MarkerGoal
import com.kevan.hangry.domain.model.MarkerReading
import com.kevan.hangry.domain.model.MarkerType
import com.kevan.hangry.domain.model.MetricTone
import com.kevan.hangry.domain.model.RecordSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class HealthRecordsTest {

    private val today = LocalDate.of(2026, 9, 23)

    private fun reading(type: MarkerType, value: Double, secondary: Double? = null, context: GlucoseContext? = null) =
        MarkerReading(1, type, value, secondary, Instant.EPOCH, today, context, RecordSource.MANUAL, null)

    @Test
    fun `blood pressure uses the worse of the two numbers`() {
        // Normal systolic, stage-1 diastolic.
        val metric = HealthMarkerCalculator.describe(MarkerType.BLOOD_PRESSURE, reading(MarkerType.BLOOD_PRESSURE, 118.0, 84.0), null)
        assertEquals("High (stage 1)", metric.status)
        assertEquals(MetricTone.CAUTION, metric.tone)
        assertEquals("118/84", metric.displayValue)
    }

    @Test
    fun `glucose range depends on when it was taken`() {
        val fasting = HealthMarkerCalculator.describe(MarkerType.BLOOD_GLUCOSE, reading(MarkerType.BLOOD_GLUCOSE, 110.0, context = GlucoseContext.FASTING), null)
        val afterMeal = HealthMarkerCalculator.describe(MarkerType.BLOOD_GLUCOSE, reading(MarkerType.BLOOD_GLUCOSE, 110.0, context = GlucoseContext.AFTER_MEAL), null)
        assertEquals("Prediabetes range", fasting.status)
        assertEquals("Normal", afterMeal.status)
    }

    @Test
    fun `hdl and testosterone ranges are sex specific`() {
        val hdl45 = reading(MarkerType.HDL, 45.0)
        assertEquals("OK", HealthMarkerCalculator.describe(MarkerType.HDL, hdl45, BiologicalSex.MALE).status)
        assertEquals("Low", HealthMarkerCalculator.describe(MarkerType.HDL, hdl45, BiologicalSex.FEMALE).status)
        // No honest testosterone band without sex.
        assertTrue(HealthMarkerCalculator.describe(MarkerType.TESTOSTERONE, reading(MarkerType.TESTOSTERONE, 500.0), null).bands.isEmpty())
    }

    @Test
    fun `goal progress for lowering and raising`() {
        val lowerLdl = MarkerGoal(MarkerType.LDL, 100.0, null, GoalDirection.LOWER, startValue = 160.0, startDate = today, targetDate = null)
        val p = HealthMarkerCalculator.progress(lowerLdl, reading(MarkerType.LDL, 130.0))
        assertEquals(0.5, p.fraction!!, 1e-9)
        assertEquals(30.0, p.remaining!!, 1e-9)
        assertFalse(p.reached)

        val raiseHdl = MarkerGoal(MarkerType.HDL, 60.0, null, GoalDirection.RAISE, startValue = 40.0, startDate = today, targetDate = null)
        assertTrue(HealthMarkerCalculator.progress(raiseHdl, reading(MarkerType.HDL, 62.0)).reached)
    }

    @Test
    fun `blood pressure goal needs both numbers`() {
        val goal = MarkerGoal(MarkerType.BLOOD_PRESSURE, 120.0, 80.0, GoalDirection.LOWER, 140.0, today, null)
        assertFalse(HealthMarkerCalculator.progress(goal, reading(MarkerType.BLOOD_PRESSURE, 118.0, 85.0)).reached)
        assertTrue(HealthMarkerCalculator.progress(goal, reading(MarkerType.BLOOD_PRESSURE, 118.0, 78.0)).reached)
    }

    @Test
    fun `cycle stats predict the next period`() {
        val periods = listOf(
            LocalDate.of(2026, 7, 1) to LocalDate.of(2026, 7, 5),
            LocalDate.of(2026, 7, 29) to LocalDate.of(2026, 8, 2),
            LocalDate.of(2026, 8, 26) to LocalDate.of(2026, 8, 30)
        )
        val stats = HealthMarkerCalculator.cycleStats(periods, today)
        assertEquals(28.0, stats.averageCycleDays!!, 1e-9)
        assertEquals(5.0, stats.averagePeriodDays!!, 1e-9)
        assertEquals(LocalDate.of(2026, 9, 23), stats.predictedNextStart)
        assertEquals(29, stats.currentCycleDay)
        assertFalse(stats.inPeriodNow)
    }

    @Test
    fun `cycle prediction needs two periods`() {
        val stats = HealthMarkerCalculator.cycleStats(listOf(LocalDate.of(2026, 9, 20) to null), today)
        assertNull(stats.predictedNextStart)
        assertTrue(stats.inPeriodNow)
    }

    @Test
    fun `fhir ldl in mmol per litre is converted`() {
        val json = """
            {"resourceType":"Observation","status":"final",
             "code":{"coding":[{"system":"http://loinc.org","code":"13457-7","display":"LDL"}]},
             "effectiveDateTime":"2026-08-01T09:30:00Z",
             "valueQuantity":{"value":3.0,"unit":"mmol/L"}}
        """.trimIndent()
        val marker = FhirHealthRecordParser.parseObservation(json, "obs-1", ZoneOffset.UTC)
        assertNotNull(marker)
        assertEquals(MarkerType.LDL.id, marker!!.type)
        assertEquals(116.0, marker.value, 0.1)
        assertEquals("phr:obs-1", marker.sourceRecordId)
        assertEquals(LocalDate.of(2026, 8, 1), marker.date)
    }

    @Test
    fun `fhir blood pressure panel reads both components`() {
        val json = """
            {"resourceType":"Observation","status":"final",
             "code":{"coding":[{"system":"http://loinc.org","code":"85354-9"}]},
             "effectiveDateTime":"2026-08-01",
             "component":[
               {"code":{"coding":[{"system":"http://loinc.org","code":"8480-6"}]},"valueQuantity":{"value":128,"unit":"mm[Hg]"}},
               {"code":{"coding":[{"system":"http://loinc.org","code":"8462-4"}]},"valueQuantity":{"value":82,"unit":"mm[Hg]"}}
             ]}
        """.trimIndent()
        val marker = FhirHealthRecordParser.parseObservation(json, "bp-1", ZoneOffset.UTC)!!
        assertEquals(128.0, marker.value, 0.0)
        assertEquals(82.0, marker.secondaryValue!!, 0.0)
    }

    @Test
    fun `unknown observations and resolved conditions are skipped`() {
        val unknown = """{"resourceType":"Observation","code":{"coding":[{"code":"9999-9"}]},"effectiveDateTime":"2026-08-01","valueQuantity":{"value":1}}"""
        assertNull(FhirHealthRecordParser.parseObservation(unknown, "x"))
        val resolved = """{"resourceType":"Condition","clinicalStatus":{"coding":[{"code":"resolved"}]},"code":{"text":"Asthma"}}"""
        assertNull(FhirHealthRecordParser.parseCondition(resolved, "c1"))
        val active = """{"resourceType":"Condition","clinicalStatus":{"coding":[{"code":"active"}]},"code":{"text":"Asthma"}}"""
        assertEquals("Asthma", FhirHealthRecordParser.parseCondition(active, "c2")!!.name)
    }

    @Test
    fun `fhir allergy keeps the reaction`() {
        val json = """{"resourceType":"AllergyIntolerance","code":{"coding":[{"display":"Peanut"}]},"reaction":[{"manifestation":[{"text":"Hives"}]}]}"""
        val item = FhirHealthRecordParser.parseAllergy(json, "a1")!!
        assertEquals("Peanut", item.name)
        assertEquals("Hives", item.note)
    }

    @Test
    fun `testosterone is only offered to men`() {
        assertTrue(MarkerType.TESTOSTERONE in com.kevan.hangry.domain.model.HealthRecordsSnapshot(sex = BiologicalSex.MALE).visibleMarkers)
        assertFalse(MarkerType.TESTOSTERONE in com.kevan.hangry.domain.model.HealthRecordsSnapshot(sex = BiologicalSex.FEMALE).visibleMarkers)
        assertFalse(MarkerType.TESTOSTERONE in com.kevan.hangry.domain.model.HealthRecordsSnapshot(sex = null).visibleMarkers)
    }

    @Test
    fun `pregnancy records give a due date or a status`() {
        val due = """{"resourceType":"Observation","code":{"coding":[{"system":"http://loinc.org","code":"11778-8"}]},"valueDateTime":"2027-03-14"}"""
        assertEquals(LocalDate.of(2027, 3, 14), FhirHealthRecordParser.parsePregnancy(due)!!.dueDate)
        val status = """{"resourceType":"Observation","code":{"coding":[{"system":"http://loinc.org","code":"82810-3"}]},
            "effectiveDateTime":"2026-09-01","valueCodeableConcept":{"coding":[{"system":"http://snomed.info/sct","code":"77386006"}]}}"""
        assertEquals(true, FhirHealthRecordParser.parsePregnancy(status)!!.pregnant)
        assertNull(FhirHealthRecordParser.parsePregnancy("""{"code":{"coding":[{"code":"2093-3"}]}}"""))
    }

    @Test
    fun `allergen check is only added to the meal prompt when there are allergies`() {
        val instructions = com.kevan.hangry.data.ai.allergenInstructions(listOf("Peanuts", "Shellfish"))
        assertTrue(instructions.contains("Peanuts, Shellfish"))
        assertTrue(instructions.contains("allergenWarnings"))
    }
}
