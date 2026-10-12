package com.kevan.hangry

import com.kevan.hangry.data.ai.labReportSystemPrompt
import com.kevan.hangry.domain.ai.LabReportParser
import com.kevan.hangry.domain.ai.ScanIssue
import com.kevan.hangry.domain.model.GlucoseContext
import com.kevan.hangry.domain.model.MarkerType
import com.kevan.hangry.ui.healthrecords.ReportScanRow
import com.kevan.hangry.ui.healthrecords.ReportScanState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LabReportScanTest {

    private val today = LocalDate.of(2026, 10, 12)
    private fun parse(raw: String, allowed: Set<MarkerType> = MarkerType.entries.toSet()) = LabReportParser.parse(raw, allowed, today)

    private val report = """{"reportDate":"2026-09-30","results":[
        {"marker":"ldl","name":"LDL Cholesterol (Direct)","value":3.2,"unit":"mmol/L","referenceRange":"< 3.0"},
        {"marker":"hdl","name":"HDL-C","value":"52","unit":"mg/dL"},
        {"marker":"blood_glucose","name":"Glucose, Fasting (Plasma)","value":"5,4","unit":"mmol/L","fasting":true},
        {"marker":null,"name":"Vitamin D, 25-OH","value":32,"unit":"ng/mL"},
        {"marker":"hdl","name":"Total Cholesterol/HDL Ratio","value":3.6,"unit":null}
    ]}"""

    @Test
    fun convertsToCanonicalUnits() {
        val scan = parse(report)
        val ldl = scan.values.first { it.type == MarkerType.LDL }
        assertEquals(3.2 * 38.67, ldl.value!!, 0.01)
        assertEquals("< 3.0", ldl.referenceRange)
        assertEquals("3.2 mmol/L", ldl.printedValue)
        assertEquals(52.0, scan.values.first { it.type == MarkerType.HDL }.value!!, 0.0)
        val glucose = scan.values.first { it.type == MarkerType.BLOOD_GLUCOSE }
        assertEquals(5.4 * 18.016, glucose.value!!, 0.01)
        assertEquals(GlucoseContext.FASTING, glucose.glucoseContext)
        assertTrue(glucose.selectedByDefault)
    }

    @Test
    fun unknownTestsAndRatiosAreListedAsNotTracked() {
        val scan = parse(report)
        assertEquals(listOf("Vitamin D, 25-OH", "Total Cholesterol/HDL Ratio"), scan.untracked.map { it.printedName })
        assertFalse(scan.untracked.any { it.selectedByDefault })
        assertEquals(3, scan.tracked.size)
    }

    @Test
    fun reportDateIsReadAndFutureDatesIgnored() {
        assertEquals(LocalDate.of(2026, 9, 30), parse(report).reportDate)
        assertNull(parse("""{"reportDate":"2027-01-01","results":[]}""").reportDate)
        // Falls back to a per-result date when the report date is missing.
        assertEquals(LocalDate.of(2026, 8, 1), parse("""{"results":[{"name":"HbA1c","value":5.6,"unit":"%","date":"2026-08-01"}]}""").reportDate)
    }

    @Test
    fun fencesAndChatterAroundJsonAreStripped() {
        val fenced = "```json\n$report\n```"
        assertEquals(5, parse(fenced).values.size)
        val chatty = "Sure! Here are the results:\n$report\nLet me know if you need anything else."
        assertEquals(5, parse(chatty).values.size)
    }

    @Test(expected = Exception::class)
    fun noJsonThrows() {
        parse("I couldn't read this image.")
    }

    @Test
    fun unconvertibleUnitIsFlaggedNotGuessed() {
        val v = parse("""{"results":[{"marker":"ldl","name":"LDL","value":1.2,"unit":"g/L"}]}""").values.single()
        assertEquals(MarkerType.LDL, v.type)
        assertNull(v.value)
        assertEquals(ScanIssue.UNKNOWN_UNIT, v.issue)
        assertFalse(v.selectedByDefault)
    }

    @Test
    fun implausibleValuesAreFlagged() {
        val v = parse("""{"results":[{"marker":"triglycerides","name":"Triglycerides","value":15000,"unit":"mg/dL"}]}""").values.single()
        assertEquals(ScanIssue.IMPLAUSIBLE, v.issue)
        assertFalse(v.selectedByDefault)
        assertTrue(LabReportParser.isPlausible(MarkerType.TRIGLYCERIDES, 150.0))
        assertFalse(LabReportParser.isPlausible(MarkerType.BLOOD_PRESSURE, 120.0, 130.0))
    }

    @Test
    fun bloodPressurePrintedAsOneValueIsSplit() {
        val v = parse("""{"results":[{"marker":"blood_pressure","name":"BP","value":"128/82","unit":"mmHg"}]}""").values.single()
        assertEquals(128.0, v.value!!, 0.0)
        assertEquals(82.0, v.secondaryValue!!, 0.0)
        assertNull(v.issue)
    }

    @Test
    fun missingValueIsFlagged() {
        val v = parse("""{"results":[{"marker":"ldl","name":"LDL","value":"see note","unit":"mg/dL"}]}""").values.single()
        assertEquals(ScanIssue.NO_VALUE, v.issue)
    }

    @Test
    fun markerMappingFallsBackToPrintedNameCautiously() {
        assertEquals(MarkerType.HBA1C, LabReportParser.markerFor(null, "Glycated Haemoglobin (HbA1c)"))
        assertEquals(MarkerType.TOTAL_CHOLESTEROL, LabReportParser.markerFor("cholesterol", "Cholesterol, Total"))
        assertEquals(MarkerType.TRIGLYCERIDES, LabReportParser.markerFor(null, "Triglycerides"))
        assertNull(LabReportParser.markerFor(null, "VLDL Cholesterol"))
        assertNull(LabReportParser.markerFor("hdl", "Non-HDL Cholesterol"))
        assertNull(LabReportParser.markerFor("testosterone", "Free Testosterone"))
        assertNull(LabReportParser.markerFor(null, "Glucose, Urine"))
        assertEquals(MarkerType.LDL, LabReportParser.markerFor("LDL", "whatever"))
    }

    @Test
    fun markersTheUserCannotTrackAreNotTracked() {
        val raw = """{"results":[{"marker":"testosterone","name":"Testosterone, Total","value":15.2,"unit":"nmol/L"}]}"""
        val allowed = MarkerType.entries.toSet() - MarkerType.TESTOSTERONE
        assertNull(parse(raw, allowed).values.single().type)
        assertEquals(15.2 * 28.84, parse(raw).values.single().value!!, 0.01)
    }

    @Test
    fun numbersParseFromPrintedText() {
        assertEquals(0.5, LabReportParser.number("<0.5")!!, 0.0)
        assertEquals(1234.0, LabReportParser.number("1,234")!!, 0.0)
        assertEquals(5.4, LabReportParser.number("5,4 H")!!, 0.0)
        assertNull(LabReportParser.number("pending"))
    }

    @Test
    fun unitAliasesConvert() {
        assertEquals(100.0, MarkerType.TOTAL_CHOLESTEROL.toCanonical(100.0, "mg/100 mL")!!, 0.0)
        assertEquals(5.6, MarkerType.HBA1C.toCanonical(5.6, "% of total Hb")!!, 0.0)
        assertEquals(120.0, MarkerType.BLOOD_PRESSURE.toCanonical(120.0, "mm Hg")!!, 0.0)
        assertEquals(450.0, MarkerType.TESTOSTERONE.toCanonical(4.5, "ng/mL")!!, 1e-9)
    }

    @Test
    fun ifccHbA1cConvertsToPercent() {
        // 42 mmol/mol is the usual diabetes-screening cut point, about 6.0 %.
        assertEquals(5.99, MarkerType.HBA1C.toCanonical(42.0, "mmol/mol")!!, 0.01)
        assertEquals(5.99, parse("""{"results":[{"marker":"hba1c","name":"HbA1c (IFCC)","value":42,"unit":"mmol/mol"}]}""").values.single().value!!, 0.01)
    }

    @Test
    fun reviewRowsOnlyAddCheckedCompleteValues() {
        val scan = parse(report)
        val rows = scan.values.map(ReportScanRow::from)
        assertEquals("124", rows[0].valueText) // LDL 3.2 mmol/L in mg/dL, 0 decimals
        val state = ReportScanState(rows = rows)
        assertEquals(3, state.toAdd.size)
        val edited = state.copy(rows = rows.mapIndexed { i, r -> if (i == 0) r.copy(checked = false) else r })
        assertEquals(2, edited.toAdd.size)
        val cleared = state.copy(rows = rows.mapIndexed { i, r -> if (i == 1) r.copy(valueText = "") else r })
        assertEquals(2, cleared.toAdd.size)
    }

    @Test
    fun promptListsSupportedMarkersWithCanonicalUnits() {
        val prompt = labReportSystemPrompt(MarkerType.entries)
        MarkerType.entries.forEach { assertTrue(prompt.contains("- ${it.id}: ${it.label}, canonical unit ${it.canonicalUnit}")) }
    }
}
