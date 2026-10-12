package com.kevan.hangry

import com.kevan.hangry.data.datasource.HealthConnectDataSource
import com.kevan.hangry.domain.model.GlucoseContext
import com.kevan.hangry.domain.model.HealthProfileKind
import com.kevan.hangry.domain.model.HealthRecordsImportResult
import com.kevan.hangry.domain.model.HealthRecordsSnapshot
import com.kevan.hangry.domain.model.MarkerReading
import com.kevan.hangry.domain.model.MarkerType
import com.kevan.hangry.domain.model.MenstrualPeriod
import com.kevan.hangry.domain.model.RecordSource
import com.kevan.hangry.domain.repository.HealthRecordsRepository
import com.kevan.hangry.ui.healthrecords.HealthRecordsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy
import java.time.Instant
import java.time.LocalDate

/** Deletes on Health Records hide at once, restore on Undo, and only hit storage on commit. */
@OptIn(ExperimentalCoroutinesApi::class)
class HealthRecordsUndoDeleteTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun reading(id: Long) = MarkerReading(
        id = id, type = MarkerType.BLOOD_GLUCOSE, value = 5.0, secondaryValue = null,
        measuredAt = Instant.EPOCH, date = LocalDate.of(2026, 1, id.toInt()),
        glucoseContext = GlucoseContext.FASTING, source = RecordSource.MANUAL, note = null
    )

    private class Repo(initial: HealthRecordsSnapshot) : HealthRecordsRepository {
        val state = MutableStateFlow(initial)
        val deletedReadings = mutableListOf<Long>()
        val deletedPeriods = mutableListOf<Long>()
        val updated = mutableListOf<Pair<Long, Double>>()
        override fun observe(): Flow<HealthRecordsSnapshot> = state
        override suspend fun current() = state.value
        override suspend fun addReading(type: MarkerType, value: Double, secondaryValue: Double?, measuredAt: Instant, glucoseContext: GlucoseContext?, note: String?, source: RecordSource) {}
        override suspend fun deleteReading(id: Long) {
            deletedReadings += id
            state.value = state.value.copy(readings = state.value.readings.filter { it.id != id })
        }
        override suspend fun updateReading(id: Long, value: Double, secondaryValue: Double?, measuredAt: Instant, glucoseContext: GlucoseContext?, note: String?) {
            updated += id to value
        }
        override suspend fun setGoal(type: MarkerType, targetValue: Double, targetSecondary: Double?, targetDate: LocalDate?) {}
        override suspend fun clearGoal(type: MarkerType) {}
        override suspend fun addProfileItem(kind: HealthProfileKind, name: String, note: String?) {}
        override suspend fun deleteProfileItem(id: Long) {}
        override suspend fun logPeriod(start: LocalDate, end: LocalDate?) {}
        override suspend fun updatePeriod(id: Long, start: LocalDate, end: LocalDate?) {}
        override suspend fun deletePeriod(id: Long) {
            deletedPeriods += id
            state.value = state.value.copy(periods = state.value.periods.filter { it.id != id })
        }
        override suspend fun setPregnancy(isPregnant: Boolean, dueDate: LocalDate?) {}
        override suspend fun importFromHealthConnect() = HealthRecordsImportResult(0, 0, 0, false)
    }

    /** Health Connect "not available", so the view model never tries to import. */
    private val noHealthConnect = Proxy.newProxyInstance(
        HealthConnectDataSource::class.java.classLoader,
        arrayOf(HealthConnectDataSource::class.java)
    ) { _, method, _ -> if (method.name == "isAvailable") false else null } as HealthConnectDataSource

    @Test
    fun `hidden reading disappears, undo brings it back, nothing is deleted`() = runTest(dispatcher) {
        val repo = Repo(HealthRecordsSnapshot(readings = listOf(reading(1), reading(2))))
        val vm = HealthRecordsViewModel(repo, noHealthConnect)
        backgroundScope.launch { vm.records.collect {} }

        vm.hideReading(1)
        assertEquals(listOf(2L), vm.records.value.readings.map { it.id })

        vm.undoReadingDelete(1)
        assertEquals(listOf(1L, 2L), vm.records.value.readings.map { it.id })
        assertTrue(repo.deletedReadings.isEmpty())
    }

    @Test
    fun `committing deletes the reading for real`() = runTest(dispatcher) {
        val repo = Repo(HealthRecordsSnapshot(readings = listOf(reading(1), reading(2))))
        val vm = HealthRecordsViewModel(repo, noHealthConnect)
        backgroundScope.launch { vm.records.collect {} }

        vm.hideReading(1)
        vm.commitReadingDelete(1)
        assertEquals(listOf(1L), repo.deletedReadings)
        assertEquals(listOf(2L), vm.records.value.readings.map { it.id })
    }

    @Test
    fun `periods follow the same hide, undo and commit steps`() = runTest(dispatcher) {
        val period = MenstrualPeriod(7, LocalDate.of(2026, 3, 1), null, RecordSource.MANUAL)
        val repo = Repo(HealthRecordsSnapshot(periods = listOf(period)))
        val vm = HealthRecordsViewModel(repo, noHealthConnect)
        backgroundScope.launch { vm.records.collect {} }

        vm.hidePeriod(7)
        assertTrue(vm.records.value.periods.isEmpty())
        vm.undoPeriodDelete(7)
        assertEquals(1, vm.records.value.periods.size)
        vm.hidePeriod(7)
        vm.commitPeriodDelete(7)
        assertEquals(listOf(7L), repo.deletedPeriods)
    }

    @Test
    fun `editing a reading updates it in place`() = runTest(dispatcher) {
        val repo = Repo(HealthRecordsSnapshot(readings = listOf(reading(1))))
        val vm = HealthRecordsViewModel(repo, noHealthConnect)

        vm.updateReading(1, 6.2, null, Instant.EPOCH, GlucoseContext.FASTING, null)
        assertEquals(listOf(1L to 6.2), repo.updated)
    }
}
