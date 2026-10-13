package com.kevan.hangry.ui

import com.kevan.hangry.data.local.entity.HeightMeasurementEntity
import com.kevan.hangry.data.local.entity.SyncStateEntity
import com.kevan.hangry.data.local.entity.UserProfileEntity
import com.kevan.hangry.data.local.entity.WeightMeasurementEntity
import com.kevan.hangry.domain.model.BodyMetricsInput
import com.kevan.hangry.domain.model.BodyMetricsSnapshot
import com.kevan.hangry.domain.model.SyncProgress
import com.kevan.hangry.domain.repository.BodyMetricsRepository
import com.kevan.hangry.domain.repository.HealthSyncManager
import com.kevan.hangry.domain.repository.HeightRepository
import com.kevan.hangry.domain.repository.LocalExportManager
import com.kevan.hangry.domain.repository.LocalStorageManager
import com.kevan.hangry.domain.repository.StorageBreakdown
import com.kevan.hangry.domain.repository.UserProfileRepository
import com.kevan.hangry.domain.repository.WeightRepository
import com.kevan.hangry.ui.settings.SettingsEvent
import com.kevan.hangry.ui.settings.SettingsViewModel
import com.kevan.hangry.util.AppDispatchers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val dispatchers = object : AppDispatchers {
        override val main = testDispatcher
        override val io = testDispatcher
        override val default = testDispatcher
    }

    private class FakeUserProfileRepo : UserProfileRepository {
        var profile: UserProfileEntity? = UserProfileEntity(id = 1, age = 30, heightCm = 175.0, onboardingCompleted = true)
        private val _flow = MutableStateFlow(profile)

        override fun getProfile(): Flow<UserProfileEntity?> = _flow.asStateFlow()
        override suspend fun getProfileSync(): UserProfileEntity? = profile
        override suspend fun saveProfile(profile: UserProfileEntity) {
            this.profile = profile
            _flow.value = profile
        }
    }

    private class FakeWeightRepo : WeightRepository {
        val list = mutableListOf<WeightMeasurementEntity>()
        private val _flow = MutableStateFlow<List<WeightMeasurementEntity>>(emptyList())

        override fun getAllWeights(): Flow<List<WeightMeasurementEntity>> = _flow.asStateFlow()
        override fun getLatestWeight(): Flow<WeightMeasurementEntity?> = flowOf(list.lastOrNull())
        override suspend fun getLatestWeightSync(): WeightMeasurementEntity? = list.lastOrNull()
        override fun getRollingAverageWeight(): Flow<Double?> = flowOf(null)
        override suspend fun getCount(): Int = list.size
        override suspend fun getImportedFingerprintsBetween(start: Instant, end: Instant): List<String> = emptyList()
        override suspend fun deleteByFingerprints(fingerprints: List<String>) {}
        override suspend fun insertOrIgnore(records: List<WeightMeasurementEntity>): List<Long> {
            list.addAll(records)
            _flow.value = list.toList()
            return records.map { 1L }
        }
        override suspend fun deleteAll() { list.clear(); _flow.value = emptyList() }
    }

    private class FakeHeightRepo : HeightRepository {
        val list = mutableListOf<HeightMeasurementEntity>()
        private val _flow = MutableStateFlow<List<HeightMeasurementEntity>>(emptyList())

        override fun getLatestHeight(): Flow<HeightMeasurementEntity?> = flowOf(list.lastOrNull())
        override suspend fun getLatestHeightSync(): HeightMeasurementEntity? = list.lastOrNull()
        override suspend fun getCount(): Int = list.size
        override suspend fun getImportedFingerprintsBetween(start: Instant, end: Instant): List<String> = emptyList()
        override suspend fun deleteByFingerprints(fingerprints: List<String>) {}
        override suspend fun insertOrIgnore(records: List<HeightMeasurementEntity>): List<Long> {
            list.addAll(records)
            _flow.value = list.toList()
            return records.map { 1L }
        }
        override suspend fun deleteAll() { list.clear(); _flow.value = emptyList() }
    }

    private class FakeLocalStorageMgr : LocalStorageManager {
        var optimizeCalled = false
        var cleanCalled = false
        var clearCacheCalled = false

        override suspend fun getStorageBreakdown(): StorageBreakdown = StorageBreakdown(
            databaseBytes = 20L,
            walBytes = 10L,
            foodPhotosBytes = 30L,
            posturePhotosBytes = 20L,
            cacheBytes = 20L,
            totalBytes = 100L
        )
        override suspend fun optimizeDatabase() { optimizeCalled = true }
        override suspend fun cleanOrphanedFiles(): Int { cleanCalled = true; return 3 }
        override suspend fun clearCache(): Long { clearCacheCalled = true; return 1024L }
    }

    private class FakeSyncMgr : HealthSyncManager {
        var syncCalled = false
        var recalCalled = false
        var clearAllCalled = false

        override fun syncHistorical(days: Int): Flow<SyncProgress> = flowOf()
        override fun syncRecent(): Flow<SyncProgress> = flow { syncCalled = true; emit(SyncProgress(status = com.kevan.hangry.domain.model.SyncStatus.SUCCESS)) }
        override fun recalculateAllBaselines(): Flow<SyncProgress> = flow { recalCalled = true; emit(SyncProgress(status = com.kevan.hangry.domain.model.SyncStatus.SUCCESS)) }
        override fun getSyncStates(): Flow<List<SyncStateEntity>> = flowOf(emptyList())
        override suspend fun clearAllData() { clearAllCalled = true }
        override suspend fun refreshToday(): Boolean = false
    }

    private class FakeExportMgr : LocalExportManager {
        var exportJsonCalled = false
        var exportCsvCalled = false

        override suspend fun exportDataAsJson(): String {
            exportJsonCalled = true
            return "{}"
        }

        override suspend fun exportDataAsCsv(): String {
            exportCsvCalled = true
            return "header1,header2"
        }
    }

    private class FakeBodyMetricsRepo : BodyMetricsRepository {
        var loggedWeight: Double? = null
        val snapshot = BodyMetricsSnapshot(BodyMetricsInput(), null, emptyList())
        override fun observe(): Flow<BodyMetricsSnapshot> = flowOf(snapshot)
        override suspend fun current(): BodyMetricsSnapshot = snapshot
        override suspend fun logWeight(kg: Double, takenAt: Instant) { loggedWeight = kg }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        userProfileRepo: UserProfileRepository = FakeUserProfileRepo(),
        weightRepo: WeightRepository = FakeWeightRepo(),
        heightRepo: HeightRepository = FakeHeightRepo(),
        storageMgr: LocalStorageManager = FakeLocalStorageMgr(),
        syncMgr: HealthSyncManager = FakeSyncMgr(),
        exportMgr: LocalExportManager = FakeExportMgr(),
        bodyMetricsRepo: BodyMetricsRepository = FakeBodyMetricsRepo()
    ): SettingsViewModel {
        return SettingsViewModel(
            syncManager = syncMgr,
            exportManager = exportMgr,
            localStorageManager = storageMgr,
            userProfileRepository = userProfileRepo,
            bodyMetricsRepository = bodyMetricsRepo,
            weightRepository = weightRepo,
            heightRepository = heightRepo,
            dispatchers = dispatchers
        )
    }

    @Test
    fun initialState_loadsProfileAndBreakdown() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        val job = launch { viewModel.uiState.collect { } }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(175.0, state.effectiveHeightCm)
        assertEquals(100L, state.storageBreakdown?.totalBytes)
        assertFalse(state.isBusy)
        job.cancel()
    }

    @Test
    fun syncRecent_invokesSyncAndEmitsEvent() = runTest(testDispatcher) {
        val syncMgr = FakeSyncMgr()
        val viewModel = createViewModel(syncMgr = syncMgr)

        val events = mutableListOf<SettingsEvent>()
        val job = launch {
            viewModel.events.collect { events.add(it) }
        }

        viewModel.syncRecent()
        advanceUntilIdle()

        assertTrue(syncMgr.syncCalled)
        assertEquals(listOf(SettingsEvent.SyncCompleted), events)
        job.cancel()
    }

    @Test
    fun recalculateAllBaselines_invokesSyncManagerAndEmitsEvent() = runTest(testDispatcher) {
        val syncMgr = FakeSyncMgr()
        val viewModel = createViewModel(syncMgr = syncMgr)

        val events = mutableListOf<SettingsEvent>()
        val job = launch {
            viewModel.events.collect { events.add(it) }
        }

        viewModel.recalculateAllBaselines()
        advanceUntilIdle()

        assertTrue(syncMgr.recalCalled)
        assertEquals(listOf(SettingsEvent.RecalculateDone), events)
        job.cancel()
    }

    @Test
    fun optimizeDatabase_callsStorageManagerAndEmitsEvent() = runTest(testDispatcher) {
        val storageMgr = FakeLocalStorageMgr()
        val viewModel = createViewModel(storageMgr = storageMgr)

        val events = mutableListOf<SettingsEvent>()
        val job = launch {
            viewModel.events.collect { events.add(it) }
        }

        viewModel.optimizeDatabase()
        advanceUntilIdle()

        assertTrue(storageMgr.optimizeCalled)
        assertEquals(listOf(SettingsEvent.DatabaseOptimized), events)
        job.cancel()
    }

    @Test
    fun cleanOrphanedFiles_callsStorageManagerAndEmitsEvent() = runTest(testDispatcher) {
        val storageMgr = FakeLocalStorageMgr()
        val viewModel = createViewModel(storageMgr = storageMgr)

        val events = mutableListOf<SettingsEvent>()
        val job = launch {
            viewModel.events.collect { events.add(it) }
        }

        viewModel.cleanOrphanedFiles()
        advanceUntilIdle()

        assertTrue(storageMgr.cleanCalled)
        assertTrue(storageMgr.clearCacheCalled)
        assertEquals(1, events.size)
        assertTrue(events.first() is SettingsEvent.CleanOrphansDone)
        val event = events.first() as SettingsEvent.CleanOrphansDone
        assertEquals(3, event.cleanedFiles)
        assertEquals(1024L, event.freedCacheBytes)
        job.cancel()
    }

    @Test
    fun clearAllHealthData_callsSyncAndStorageManagersAndEmitsEvent() = runTest(testDispatcher) {
        val syncMgr = FakeSyncMgr()
        val storageMgr = FakeLocalStorageMgr()
        val viewModel = createViewModel(syncMgr = syncMgr, storageMgr = storageMgr)

        val events = mutableListOf<SettingsEvent>()
        val job = launch {
            viewModel.events.collect { events.add(it) }
        }

        var completed = false
        viewModel.clearAllHealthData { completed = true }
        advanceUntilIdle()

        assertTrue(completed)
        assertTrue(syncMgr.clearAllCalled)
        assertTrue(storageMgr.cleanCalled)
        assertTrue(storageMgr.clearCacheCalled)
        assertTrue(storageMgr.optimizeCalled)
        assertEquals(listOf(SettingsEvent.HealthDataDeleted), events)
        job.cancel()
    }

    @Test
    fun resetApplication_invokesSyncManagerClearAll() = runTest(testDispatcher) {
        val syncMgr = FakeSyncMgr()
        val viewModel = createViewModel(syncMgr = syncMgr)

        var completed = false
        viewModel.resetApplication { completed = true }
        advanceUntilIdle()

        assertTrue(completed)
        assertTrue(syncMgr.clearAllCalled)
    }

    @Test
    fun saveProfileAndGoals_updatesProfileAndWeight() = runTest(testDispatcher) {
        val userProfileRepo = FakeUserProfileRepo()
        val bodyMetricsRepo = FakeBodyMetricsRepo()
        val viewModel = createViewModel(
            userProfileRepo = userProfileRepo,
            bodyMetricsRepo = bodyMetricsRepo
        )

        var savedCallback = false
        val updated = userProfileRepo.profile!!.copy(age = 35)
        viewModel.saveProfileAndGoals(updated, 80.0) {
            savedCallback = true
        }

        advanceUntilIdle()

        assertTrue(savedCallback)
        assertEquals(35, userProfileRepo.profile?.age)
        assertEquals(80.0, bodyMetricsRepo.loggedWeight)
    }
}
