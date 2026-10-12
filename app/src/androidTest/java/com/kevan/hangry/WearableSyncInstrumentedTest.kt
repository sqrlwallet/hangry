package com.kevan.hangry

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kevan.hangry.data.datasource.HealthConnectDataSource
import com.kevan.hangry.data.datasource.ImportedHealthRecords
import com.kevan.hangry.data.local.HangryDatabase
import com.kevan.hangry.data.local.entity.*
import com.kevan.hangry.data.repository.DefaultHealthSyncManager
import com.kevan.hangry.domain.calculation.BodySignals
import com.kevan.hangry.domain.calculation.HangryCalorieCalculator
import com.kevan.hangry.domain.calculation.HangryRecoveryCalculator
import com.kevan.hangry.domain.calculation.HangrySleepCalculator
import com.kevan.hangry.domain.calculation.HangryStrainCalculator
import com.kevan.hangry.domain.calculation.HangryTrainingLoadCalculator
import com.kevan.hangry.domain.calculation.NightlyVitals
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The whole sync with a wearable's overnight data: readings placed in the right night, one app
 * per night, sleeping heart rate, skin temperature and the body-signals flag stored on the day.
 */
@RunWith(AndroidJUnit4::class)
class WearableSyncInstrumentedTest {

    private lateinit var db: HangryDatabase
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.now(zone)

    /** Night that ends on [date]: 23:00 the evening before to 07:00. */
    private fun nightStart(date: LocalDate) = date.minusDays(1).atTime(23, 0).atZone(zone).toInstant()
    private fun nightEnd(date: LocalDate) = date.atTime(7, 0).atZone(zone).toInstant()
    private val days = (0L..12L).map { today.minusDays(it) }

    /** A Fitbit-like tracker that runs warm with a raised heart rate on the last night. */
    private inner class FakeWearable : HealthConnectDataSource {
        override suspend fun isAvailable() = true
        override suspend fun hasPermissions() = true
        override suspend fun fetchSleepSessions(start: Instant, end: Instant) = days.map { d ->
            SleepSessionEntity(
                sourceRecordId = "sleep-$d", sourcePackageName = FITBIT, recordFingerprint = "sleep-$d",
                startTime = nightStart(d), endTime = nightEnd(d), durationMinutes = 480, timeInBedMinutes = 480
            )
        }.filter { it.endTime.isAfter(start) && it.startTime.isBefore(end) }

        override suspend fun fetchHeartRateSamples(start: Instant, end: Instant) = days.flatMap { d ->
            val bpm = if (d == today) 62.0 else 52.0
            (0 until 96).map { i ->
                val t = nightStart(d).plus(Duration.ofMinutes(i * 5L))
                HeartRateSampleEntity(sourcePackageName = FITBIT, recordFingerprint = "hr-$d-$i", timestamp = t, bpm = bpm + (i % 3))
            }
        }.filter { !it.timestamp.isBefore(start) && it.timestamp.isBefore(end) }

        override suspend fun fetchOxygenSaturationReadings(start: Instant, end: Instant) = days.flatMap { d ->
            // The tracker logs 6 overnight readings; a phone app adds one odd spot check.
            (1..6).map { NightlyVitals.Reading(nightStart(d).plus(Duration.ofHours(it.toLong())), 96.0, FITBIT) } +
                NightlyVitals.Reading(nightStart(d).plus(Duration.ofHours(3)), 88.0, OTHER_APP)
        }.filter { !it.time.isBefore(start) && it.time.isBefore(end) }

        override suspend fun fetchRespiratoryRateReadings(start: Instant, end: Instant) = days.map { d ->
            NightlyVitals.Reading(nightStart(d).plus(Duration.ofHours(4)), 14.0, FITBIT)
        }.filter { !it.time.isBefore(start) && it.time.isBefore(end) }

        override suspend fun fetchSkinTemperatureDeltas(start: LocalDate, end: LocalDate) =
            days.filter { !it.isBefore(start) && !it.isAfter(end) }
                .associateWith { d -> if (d == today) 0.9 else 0.05 * (d.dayOfMonth % 3) }

        override suspend fun fetchExerciseSessions(start: Instant, end: Instant) = emptyList<ExerciseSessionEntity>()
        override suspend fun fetchRestingHeartRates(start: LocalDate, end: LocalDate) = emptyList<RestingHeartRateEntity>()
        override suspend fun fetchHrvMeasurements(start: LocalDate, end: LocalDate) = emptyList<HrvMeasurementEntity>()
        override suspend fun fetchStepsSummaries(start: LocalDate, end: LocalDate) = emptyList<StepsSummaryEntity>()
        override suspend fun fetchWeightMeasurements(start: Instant, end: Instant) = emptyList<WeightMeasurementEntity>()
        override suspend fun fetchHeightMeasurements(start: Instant, end: Instant) = emptyList<HeightMeasurementEntity>()
        override suspend fun fetchVo2Max(start: LocalDate, end: LocalDate) = emptyMap<LocalDate, Double>()
        override suspend fun fetchBloodPressure(start: LocalDate, end: LocalDate) = emptyMap<LocalDate, Pair<Double, Double>>()
        override suspend fun fetchBasalMetabolicRates(start: LocalDate, end: LocalDate) = emptyMap<LocalDate, Double>()
        override suspend fun fetchHydration(start: LocalDate, end: LocalDate) = emptyMap<LocalDate, Double>()
        override suspend fun fetchBodyFat(start: Instant, end: Instant) = emptyMap<LocalDate, Double>()
        override suspend fun fetchNutritionRecords(start: Instant, end: Instant) = emptyList<FoodLogEntity>()
        override suspend fun findEarliestDataDate(): LocalDate? = days.last()
        override suspend fun writeNutritionRecord(entry: FoodLogEntity) = false
        override suspend fun deleteNutritionRecord(entry: FoodLogEntity) = false
        override suspend fun breathingWritePermissions() = emptySet<String>()
        override suspend fun hasBreathingWritePermissions() = false
        override suspend fun writeBreathingSession(session: BreathingSessionEntity, title: String) = false
        override suspend fun grantedPermissions() = emptySet<String>()
        override suspend fun healthRecordPermissions(includeCycle: Boolean) = emptySet<String>()
        override suspend fun supportsMedicalRecords() = false
        override suspend fun writeHealthMarker(marker: HealthMarkerEntity) = false
        override suspend fun deleteHealthMarker(marker: HealthMarkerEntity) = false
        override suspend fun fetchHealthRecords(since: Instant, includeCycle: Boolean) = ImportedHealthRecords()
    }

    @Before
    fun createDb() {
        db = HangryDatabase.createInMemoryDatabase(ApplicationProvider.getApplicationContext<Context>())
    }

    @After
    fun closeDb() = db.close()

    @Test
    fun overnightReadingsLandOnTheRightDayAndRaiseBodySignals() = runBlocking {
        val trainingLoad = HangryTrainingLoadCalculator()
        val manager = DefaultHealthSyncManager(
            database = db,
            dataSource = FakeWearable(),
            recoveryCalculator = HangryRecoveryCalculator(),
            sleepCalculator = HangrySleepCalculator(),
            trainingLoadCalculator = trainingLoad,
            strainCalculator = HangryStrainCalculator(trainingLoad),
            calorieCalculator = HangryCalorieCalculator()
        )
        manager.syncHistorical(days = 12).collect { }

        val yesterday = db.dailyHealthSummaryDao().getSummaryForDateSync(today.minusDays(1))
        assertNotNull(yesterday)
        // The tracker's overnight readings, not the other app's 88% spot check.
        assertEquals(96.0, yesterday!!.spo2Percentage!!, 0.01)
        assertEquals(14.0, yesterday.respiratoryRate!!, 0.01)
        assertEquals(53.0, yesterday.sleepingHeartRate!!, 0.5)
        assertTrue(BodySignals.decode(yesterday.bodySignals).isEmpty())

        val last = db.dailyHealthSummaryDao().getSummaryForDateSync(today)!!
        assertEquals(0.9, last.skinTempDeltaCelsius!!, 0.001)
        val kinds = BodySignals.decode(last.bodySignals).map { it.kind }.toSet()
        assertTrue("signals $kinds", kinds.containsAll(setOf(BodySignals.Kind.RESTING_HR, BodySignals.Kind.SKIN_TEMP)))
    }

    private companion object {
        const val FITBIT = "com.fitbit.FitbitMobile"
        const val OTHER_APP = "com.example.spotcheck"
    }
}
