package com.kevan.hangry.data.repository

import com.kevan.hangry.data.datasource.HealthConnectDataSource
import com.kevan.hangry.data.local.HangryDatabase
import androidx.room.withTransaction
import com.kevan.hangry.data.local.entity.DailyHealthSummaryEntity
import com.kevan.hangry.data.local.entity.ExerciseSessionEntity
import com.kevan.hangry.data.local.entity.WORKOUT_DETAIL_VERSION
import com.kevan.hangry.data.local.entity.HrvFeelingEntity
import com.kevan.hangry.data.local.entity.RecoveryScoreEntity
import com.kevan.hangry.data.local.entity.SUMMARY_CALCULATION_VERSION
import com.kevan.hangry.data.local.entity.SyncStateEntity
import com.kevan.hangry.data.local.entity.SleepSessionEntity
import com.kevan.hangry.data.local.entity.WeightMeasurementEntity
import com.kevan.hangry.data.local.entity.HeartRateSampleEntity
import com.kevan.hangry.data.local.entity.HeightMeasurementEntity
import com.kevan.hangry.data.local.entity.HrvMeasurementEntity
import com.kevan.hangry.data.local.entity.RestingHeartRateEntity
import com.kevan.hangry.data.local.entity.FoodLogEntity
import com.kevan.hangry.data.local.entity.FoodLogSource
import com.kevan.hangry.data.datasource.RealHealthConnectDataSource
import com.kevan.hangry.domain.calculation.ActiveActivityCalculator
import com.kevan.hangry.domain.calculation.CalorieCalculator
import com.kevan.hangry.domain.calculation.DayMetrics
import com.kevan.hangry.domain.calculation.HeartRateZones
import com.kevan.hangry.domain.calculation.NightlyVitals
import com.kevan.hangry.domain.calculation.BodySignals
import com.kevan.hangry.domain.calculation.CycleTemperature
import com.kevan.hangry.domain.calculation.HealthMarkerCalculator
import com.kevan.hangry.domain.calculation.RecoveryCalculator
import com.kevan.hangry.domain.calculation.RecoveryConfig
import com.kevan.hangry.domain.calculation.SleepCalculator
import com.kevan.hangry.domain.calculation.StrainCalculator
import com.kevan.hangry.domain.calculation.TrainingLoadCalculator
import com.kevan.hangry.domain.model.BiologicalSex
import com.kevan.hangry.domain.model.HrvFeeling
import com.kevan.hangry.domain.model.RecoveryResult
import com.kevan.hangry.domain.model.RecoveryState
import com.kevan.hangry.domain.model.StrainSource
import com.kevan.hangry.domain.model.SyncProgress
import com.kevan.hangry.domain.model.SyncStatus
import com.kevan.hangry.domain.repository.HealthSyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.roundToInt

class DefaultHealthSyncManager(
    private val database: HangryDatabase,
    private val dataSource: HealthConnectDataSource,
    private val recoveryCalculator: RecoveryCalculator,
    private val sleepCalculator: SleepCalculator,
    private val trainingLoadCalculator: TrainingLoadCalculator,
    private val strainCalculator: StrainCalculator,
    private val calorieCalculator: CalorieCalculator,
    /** Remembers one-time data fixes that have run; null in tests. */
    private val prefs: android.content.SharedPreferences? = null,
    // Every "what day is it" / day-boundary computation in this class uses the device's local
    // zone, matching DashboardViewModel - a day starts at local midnight, not UTC midnight. Read
    // at the start of each sync or recalculation, so travelling moves the day boundaries too.
    private val zoneProvider: () -> ZoneId = { ZoneId.systemDefault() }
) : HealthSyncManager {

    // Serializes syncHistorical/recalculateAllBaselines so the periodic background worker and
    // a manual trigger (or two manual triggers) can never interleave writes to the same days -
    // without this, one run's insertOrReplace could overwrite the other's with a stale snapshot.
    private val syncMutex = Mutex()

    override fun syncHistorical(days: Int): Flow<SyncProgress> = flow {
      syncMutex.withLock {
        emit(SyncProgress(status = SyncStatus.IN_PROGRESS, currentDataType = "INITIALIZING"))

        val zone = zoneProvider()
        val today = LocalDate.now(zone)
        val startDate = if (days <= 0) {
            val earliestInHc = try {
                dataSource.findEarliestDataDate()
            } catch (_: Exception) {
                null
            }
            earliestInHc ?: today.minusDays(730)
        } else {
            today.minusDays(days.toLong())
        }

        // Break historical window into bounded chunks of at most 14 days
        val chunkSizeDays = 14
        val chunks = mutableListOf<Pair<LocalDate, LocalDate>>()
        var cursor = startDate
        while (!cursor.isAfter(today)) {
            val chunkEnd = if (cursor.plusDays((chunkSizeDays - 1).toLong()).isAfter(today)) today else cursor.plusDays((chunkSizeDays - 1).toLong())
            chunks.add(cursor to chunkEnd)
            cursor = chunkEnd.plusDays(1)
        }

        var totalRead = 0
        var totalInserted = 0
        var totalSkipped = 0
        // Without full-history access Health Connect hides older data, so "not returned" only
        // means "deleted" for the last 30 days - older local history is left alone.
        val granted = runCatching { dataSource.grantedPermissions() }.getOrDefault(emptySet())
        val canSeeAllHistory = RealHealthConnectDataSource.PERMISSION_READ_HEALTH_DATA_HISTORY in granted
        // Each type is read only with its own permission. A type that isn't granted is skipped -
        // nothing read, nothing stored removed - instead of failing the whole sync.
        val readable = HealthSyncPlan.readableTypes(granted)
        val fullyVisibleFrom = today.minusDays(29)

        try {
            // Health Connect missing (uninstalled, disabled, mid-update) or no access at all is a
            // failed sync, never an empty one - an empty read would look like everything deleted.
            if (!dataSource.isAvailable()) throw IllegalStateException("Health Connect isn't available right now")
            if (readable.isEmpty()) throw SecurityException("No Health Connect data can be read")

            for ((chunkIndex, chunk) in chunks.withIndex()) {
                val (chunkStart, chunkEnd) = chunk
                // Apply a 2-hour overlap window to catch late-arriving records
                val chunkStartInstant = chunkStart.atStartOfDay(zone).toInstant().minus(2, ChronoUnit.HOURS)
                val chunkEndInstant = chunkEnd.plusDays(1).atStartOfDay(zone).toInstant()

                // The day-based reads (resting HR, HRV) cover whole local days, no overlap.
                val chunkDaysStart = chunkStart.atStartOfDay(zone).toInstant()

                val chunkLabel = "Batch ${chunkIndex + 1}/${chunks.size}"

                // Null = not granted, so not read. A read that fails throws and fails the sync,
                // so a list here is always a complete read of its window.
                // 1. Sleep Sessions
                emit(SyncProgress(status = SyncStatus.IN_PROGRESS, currentDataType = "$chunkLabel (Sleep)"))
                val sleepSessions = if (SyncedType.SLEEP in readable) dataSource.fetchSleepSessions(chunkStartInstant, chunkEndInstant) else null

                // 2. Exercise Sessions
                emit(SyncProgress(status = SyncStatus.IN_PROGRESS, currentDataType = "$chunkLabel (Workouts)"))
                val workouts = if (SyncedType.WORKOUTS in readable) dataSource.fetchExerciseSessions(chunkStartInstant, chunkEndInstant) else null

                // 3. Resting Heart Rate
                emit(SyncProgress(status = SyncStatus.IN_PROGRESS, currentDataType = "$chunkLabel (Resting HR)"))
                val rhrRecords = if (SyncedType.RESTING_HEART_RATE in readable) dataSource.fetchRestingHeartRates(chunkStart, chunkEnd) else null

                // 4. HRV RMSSD
                emit(SyncProgress(status = SyncStatus.IN_PROGRESS, currentDataType = "$chunkLabel (HRV)"))
                val hrvRecords = if (SyncedType.HRV in readable) dataSource.fetchHrvMeasurements(chunkStart, chunkEnd) else null

                // 5. Steps & Activity
                emit(SyncProgress(status = SyncStatus.IN_PROGRESS, currentDataType = "$chunkLabel (Steps)"))
                val stepsRecords = if (SyncedType.STEPS in readable) dataSource.fetchStepsSummaries(chunkStart, chunkEnd) else null

                // 6. Continuous Heart Rate Samples (Wear OS / Galaxy Watch / Fitbit continuous HR)
                emit(SyncProgress(status = SyncStatus.IN_PROGRESS, currentDataType = "$chunkLabel (Heart Rate)"))
                val hrRecords = if (SyncedType.HEART_RATE in readable) dataSource.fetchHeartRateSamples(chunkStartInstant, chunkEndInstant) else null

                // 7. Weight & Height Measurements
                emit(SyncProgress(status = SyncStatus.IN_PROGRESS, currentDataType = "$chunkLabel (Weight)"))
                val weightRecords = if (SyncedType.WEIGHT in readable) dataSource.fetchWeightMeasurements(chunkStartInstant, chunkEndInstant) else null
                val heightRecords = if (SyncedType.HEIGHT in readable) dataSource.fetchHeightMeasurements(chunkStartInstant, chunkEndInstant) else null

                // 8. External Nutrition Records
                emit(SyncProgress(status = SyncStatus.IN_PROGRESS, currentDataType = "$chunkLabel (Nutrition)"))
                val nutritionRecords = if (SyncedType.NUTRITION in readable) dataSource.fetchNutritionRecords(chunkStartInstant, chunkEndInstant) else null

                // 9. Vitals & Cardio Fitness (SpO2, VO2 Max, Respiratory Rate, Blood Pressure)
                emit(SyncProgress(status = SyncStatus.IN_PROGRESS, currentDataType = "$chunkLabel (Vitals)"))

                val chunkRead = listOf(sleepSessions, workouts, rhrRecords, hrvRecords, stepsRecords, hrRecords,
                    weightRecords, heightRecords, nutritionRecords).sumOf { it?.size ?: 0 }
                totalRead += chunkRead

                // Perform all database writes for this chunk in an atomic transaction
                var chunkInserted = 0
                database.withTransaction {
                    // Drop what's no longer in Health Connect for this window: records deleted or
                    // edited at the source, and copies another app already reported (the fetch
                    // keeps one copy per event - see HealthDedup). Types not read are left alone.
                    val windowVisible = canSeeAllHistory || !chunkStart.isBefore(fullyVisibleFrom)
                    if (windowVisible) {
                        removeStale(
                            from = chunkStartInstant,
                            to = chunkEndInstant,
                            daysFrom = chunkDaysStart,
                            sleep = sleepSessions,
                            workouts = workouts,
                            weights = weightRecords,
                            // The nutrition read returns nothing on failure, so only trust an
                            // empty-looking window when something came back.
                            meals = nutritionRecords?.takeIf { it.isNotEmpty() },
                            restingHr = rhrRecords,
                            hrv = hrvRecords,
                            heartRate = hrRecords,
                            heights = heightRecords
                        )
                    }
                    val sleepInserted = database.sleepSessionDao().insertOrIgnore(sleepSessions.orEmpty())
                    chunkInserted += sleepInserted.count { it != -1L }
                    // Nights saved before stage timelines were kept get theirs now.
                    sleepSessions.orEmpty().forEach { session ->
                        session.stageSegments?.let { database.sleepSessionDao().fillStageSegments(session.recordFingerprint, it) }
                    }

                    val workoutInserted = database.exerciseSessionDao().insertOrIgnore(workouts.orEmpty())
                    chunkInserted += workoutInserted.count { it != -1L }
                    // Already-saved workouts are refreshed too, so edits and newly read details land.
                    workouts.orEmpty().forEach { database.exerciseSessionDao().updateDetails(it) }

                    val rhrInserted = database.restingHeartRateDao().insertOrIgnore(rhrRecords.orEmpty())
                    chunkInserted += rhrInserted.count { it != -1L }

                    val hrvInserted = database.hrvDao().insertOrIgnore(hrvRecords.orEmpty())
                    chunkInserted += hrvInserted.count { it != -1L }

                    // Older days Health Connect can't show (no full-history access) keep what's
                    // stored, and so do steps when they weren't read.
                    if (stepsRecords != null && windowVisible) {
                        database.stepsDao().deleteBetween(chunkStart, chunkEnd)
                    }
                    val stepsInserted = database.stepsDao().insertOrIgnore(stepsRecords.orEmpty())
                    chunkInserted += stepsInserted.count { it != -1L }

                    val hrInserted = database.heartRateDao().insertOrIgnore(hrRecords.orEmpty())
                    chunkInserted += hrInserted.count { it != -1L }
                    workouts.orEmpty().forEach { applyWorkoutHeartRate(it) }

                    val weightInserted = database.weightDao().insertOrIgnore(weightRecords.orEmpty())
                    chunkInserted += weightInserted.count { it != -1L }

                    val heightInserted = database.heightDao().insertOrIgnore(heightRecords.orEmpty())
                    chunkInserted += heightInserted.count { it != -1L }

                    val nutritionInserted = database.foodLogDao().insertOrIgnore(nutritionRecords.orEmpty())
                    chunkInserted += nutritionInserted.count { it != -1L }

                    // Update SyncStateEntity checkpoint
                    database.syncStateDao().insertOrReplace(
                        SyncStateEntity(
                            dataType = "ALL",
                            lastSuccessfulSyncTimestamp = Instant.now(),
                            historicalImportStart = chunkStartInstant,
                            historicalImportEnd = chunkEndInstant,
                            syncStatus = "IN_PROGRESS",
                            recordsRead = totalRead,
                            recordsInserted = totalInserted + chunkInserted,
                            recordsSkipped = totalSkipped + (chunkRead - chunkInserted)
                        )
                    )
                }
                totalInserted += chunkInserted
                totalSkipped += (chunkRead - chunkInserted)
            }

            // 6. Calculate Derived Daily Summaries & Recovery Scores across affected range
            emit(SyncProgress(status = SyncStatus.IN_PROGRESS, currentDataType = "CALCULATIONS"))
            computeDailySummaries(startDate, today, zone)

            // Final SyncState update
            database.syncStateDao().insertOrReplace(
                SyncStateEntity(
                    dataType = "ALL",
                    lastSuccessfulSyncTimestamp = Instant.now(),
                    historicalImportStart = startDate.atStartOfDay(zone).toInstant(),
                    historicalImportEnd = today.plusDays(1).atStartOfDay(zone).toInstant(),
                    syncStatus = "SUCCESS",
                    recordsRead = totalRead,
                    recordsInserted = totalInserted,
                    recordsSkipped = totalSkipped
                )
            )

            emit(
                SyncProgress(
                    status = SyncStatus.SUCCESS,
                    currentDataType = "DONE",
                    recordsRead = totalRead,
                    recordsInserted = totalInserted,
                    recordsSkipped = totalSkipped
                )
            )
        } catch (e: Exception) {
            val isPermissionRevoked = e is SecurityException
            database.syncStateDao().insertOrReplace(
                SyncStateEntity(
                    dataType = "ALL",
                    lastSuccessfulSyncTimestamp = null,
                    syncStatus = if (isPermissionRevoked) "PERMISSION_REVOKED" else "FAILED",
                    lastError = e.localizedMessage,
                    recordsRead = totalRead,
                    recordsInserted = totalInserted,
                    recordsSkipped = totalSkipped
                )
            )
            emit(
                SyncProgress(
                    status = SyncStatus.FAILED,
                    errorMessage = if (isPermissionRevoked) {
                        "We lost access to some health data. Reconnect access in Settings to keep syncing."
                    } else {
                        e.localizedMessage ?: "Synchronization failed"
                    }
                )
            )
        }
      }
    }.flowOn(Dispatchers.IO)

    override fun syncRecent(): Flow<SyncProgress> = syncHistorical(days = 3)

    override fun recalculateAllBaselines(): Flow<SyncProgress> = flow {
      syncMutex.withLock {
        emit(SyncProgress(status = SyncStatus.IN_PROGRESS, currentDataType = "SCANNING_RECORDS"))
        val zone = zoneProvider()
        val today = LocalDate.now(zone)
        val earliestSteps = database.stepsDao().getEarliestDate()
        val oldestSleep = database.sleepSessionDao().getOldestSession()?.startTime?.atZone(zone)?.toLocalDate()
        val oldestExercise = database.exerciseSessionDao().getOldestSession()?.startTime?.atZone(zone)?.toLocalDate()

        val earliestDate = listOfNotNull(earliestSteps, oldestSleep, oldestExercise).minOrNull() ?: today.minusDays(28)

        emit(SyncProgress(status = SyncStatus.IN_PROGRESS, currentDataType = "CALCULATING_HISTORICAL_DAYS"))
        computeDailySummaries(earliestDate, today, zone)
        emit(SyncProgress(status = SyncStatus.SUCCESS, currentDataType = "DONE"))
      }
    }.flowOn(Dispatchers.IO)

    /**
     * Drops what a complete read of a window no longer has. A null list means that type wasn't
     * read, so nothing of it is touched. [from]..[to] is the instant window that was read;
     * [daysFrom]..[to] the whole-day window of resting heart rate and HRV.
     */
    private suspend fun removeStale(
        from: Instant,
        to: Instant,
        daysFrom: Instant,
        sleep: List<SleepSessionEntity>?,
        workouts: List<ExerciseSessionEntity>?,
        weights: List<WeightMeasurementEntity>?,
        meals: List<FoodLogEntity>?,
        restingHr: List<RestingHeartRateEntity>?,
        hrv: List<HrvMeasurementEntity>?,
        heartRate: List<HeartRateSampleEntity>?,
        heights: List<HeightMeasurementEntity>?
    ) {
        if (sleep != null) {
            val dao = database.sleepSessionDao()
            HealthSyncPlan.stale(dao.getImportedFingerprintsStartingBetween(from, to), sleep.map { it.recordFingerprint })
                .chunked(500).forEach { dao.deleteByFingerprints(it) }
        }
        if (workouts != null) {
            val dao = database.exerciseSessionDao()
            HealthSyncPlan.stale(dao.getImportedFingerprintsStartingBetween(from, to), workouts.map { it.recordFingerprint })
                .chunked(500).forEach { dao.deleteByFingerprints(it) }
        }
        if (weights != null) {
            val dao = database.weightDao()
            HealthSyncPlan.stale(dao.getImportedFingerprintsBetween(from, to), weights.map { it.recordFingerprint })
                .chunked(500).forEach { dao.deleteByFingerprints(it) }
        }
        if (restingHr != null) {
            val dao = database.restingHeartRateDao()
            HealthSyncPlan.stale(dao.getImportedFingerprintsBetween(daysFrom, to), restingHr.map { it.recordFingerprint })
                .chunked(500).forEach { dao.deleteByFingerprints(it) }
        }
        if (hrv != null) {
            val dao = database.hrvDao()
            HealthSyncPlan.stale(dao.getImportedFingerprintsBetween(daysFrom, to), hrv.map { it.recordFingerprint })
                .chunked(500).forEach { dao.deleteByFingerprints(it) }
        }
        if (heights != null) {
            val dao = database.heightDao()
            HealthSyncPlan.stale(dao.getImportedFingerprintsBetween(from, to), heights.map { it.recordFingerprint })
                .chunked(500).forEach { dao.deleteByFingerprints(it) }
        }
        if (heartRate != null) {
            val dao = database.heartRateDao()
            val readIds = heartRate.mapNotNullTo(HashSet()) { it.sourceRecordId }
            // Records gone from Health Connect...
            HealthSyncPlan.staleHeartRateRecords(dao.getImportedRecordSpansBetween(from, to), readIds, from, to)
                .chunked(500).forEach { dao.deleteByRecordIds(it) }
            // ...and samples an edit took out of a record that's still there.
            val readSamples = heartRate.mapTo(HashSet()) { it.recordFingerprint }
            readIds.toList().chunked(500).forEach { ids ->
                HealthSyncPlan.stale(dao.getFingerprintsForRecords(ids), readSamples)
                    .chunked(500).forEach { dao.deleteByFingerprints(it) }
            }
        }
        if (meals != null) {
            val dao = database.foodLogDao()
            HealthSyncPlan.stale(dao.getImportedIdsBetween(FoodLogSource.HEALTH_CONNECT, from, to), meals.mapNotNull { it.sourceRecordId })
                .chunked(500).forEach { dao.deleteBySourceRecordIds(it) }
        }
    }

    /** Average and peak heart rate during a workout, from the continuous samples already saved. */
    private suspend fun applyWorkoutHeartRate(workout: ExerciseSessionEntity) {
        val hr = database.heartRateDao()
        database.exerciseSessionDao().updateHeartRate(
            workout.recordFingerprint,
            avg = hr.getAverageBpmBetween(workout.startTime, workout.endTime),
            max = hr.getMaxBpmBetween(workout.startTime, workout.endTime)
        )
    }

    /**
     * One-time re-read of workouts saved by an older import, so history gets proper exercise
     * types and details, not just new workouts. Returns true if anything was refreshed.
     */
    private suspend fun refreshWorkoutDetailsIfNeeded(): Boolean {
        val dao = database.exerciseSessionDao()
        val oldest = dao.getOldestStartNeedingDetails(WORKOUT_DETAIL_VERSION) ?: return false
        val now = Instant.now()
        var from = oldest.minus(1, ChronoUnit.DAYS)
        while (from.isBefore(now)) {
            val to = minOf(from.plus(30, ChronoUnit.DAYS), now)
            // No access right now (permission, Health Connect missing): try again next launch.
            val sessions = runCatching { dataSource.fetchExerciseSessions(from, to) }.getOrElse { return false }
            database.withTransaction {
                sessions.forEach { dao.updateDetails(it) }
                sessions.forEach { applyWorkoutHeartRate(it) }
            }
            from = to
        }
        // Anything left was deleted at the source; don't keep trying to re-read it.
        dao.markDetailVersion(WORKOUT_DETAIL_VERSION)
        return true
    }

    /**
     * Once per [DEDUP_VERSION]: re-read everything already imported, so days saved before
     * duplicate handling (a phone and a watch both counted, the same workout from two apps)
     * are corrected, not just new ones. Returns true if it ran - it recomputes summaries too.
     */
    private suspend fun reimportForDedupIfNeeded(): Boolean {
        val prefs = prefs ?: return false
        if (prefs.getInt(KEY_DEDUP_VERSION, 0) >= DEDUP_VERSION) return false
        val zone = zoneProvider()
        val today = LocalDate.now(zone)
        val oldest = listOfNotNull(
            database.stepsDao().getEarliestDate(),
            database.sleepSessionDao().getOldestSession()?.startTime?.atZone(zone)?.toLocalDate(),
            database.exerciseSessionDao().getOldestSession()?.startTime?.atZone(zone)?.toLocalDate()
        ).minOrNull()
        if (oldest == null) {
            // Nothing imported yet: the first import already uses the new rules.
            prefs.edit().putInt(KEY_DEDUP_VERSION, DEDUP_VERSION).apply()
            return false
        }
        val days = ChronoUnit.DAYS.between(oldest, today).toInt().coerceAtLeast(1)
        val result = syncHistorical(days).lastOrNull()
        // No access right now (permission, Health Connect missing): try again next launch, and
        // let the caller's own checks run - a failed sync recomputed nothing.
        val succeeded = HealthSyncPlan.reimportSucceeded(result?.status)
        if (succeeded) prefs.edit().putInt(KEY_DEDUP_VERSION, DEDUP_VERSION).apply()
        return succeeded
    }

    override suspend fun recalculateIfScoringChanged() {
        if (runCatching { reimportForDedupIfNeeded() }.getOrDefault(false)) return
        // Workout types feed training load and strain, so refreshed workouts mean a recalculation.
        val workoutsRefreshed = runCatching { refreshWorkoutDetailsIfNeeded() }.getOrDefault(false)
        val scoringChanged = database.recoveryScoreDao().getLatestScoreSync()
            ?.let { it.algorithmVersion < RecoveryConfig().algorithmVersion } == true
        val summariesChanged = database.dailyHealthSummaryDao().getLatestSummarySync()
            ?.let { it.calculationVersion < SUMMARY_CALCULATION_VERSION } == true
        if (!scoringChanged && !summariesChanged && !workoutsRefreshed) return
        recalculateAllBaselines().collect { }
    }

    override suspend fun refreshToday(): Boolean = withContext(Dispatchers.IO) {
        // A full sync covers today anyway; don't queue up behind it.
        if (!syncMutex.tryLock()) return@withContext false
        try {
            val zone = zoneProvider()
            val today = LocalDate.now(zone)
            // Same 2-hour overlap as a full sync, for records that arrive late.
            val from = today.atStartOfDay(zone).toInstant().minus(2, ChronoUnit.HOURS)
            val to = Instant.now()
            // Like a full sync, only what's granted is read.
            val readable = HealthSyncPlan.readableTypes(dataSource.grantedPermissions())
            val heartRate = if (SyncedType.HEART_RATE in readable) dataSource.fetchHeartRateSamples(from, to) else emptyList()
            val workouts = if (SyncedType.WORKOUTS in readable) dataSource.fetchExerciseSessions(from, to) else emptyList()
            val steps = if (SyncedType.STEPS in readable) dataSource.fetchStepsSummaries(today, today) else emptyList()
            database.withTransaction {
                database.heartRateDao().insertOrIgnore(heartRate)
                database.exerciseSessionDao().insertOrIgnore(workouts)
                workouts.forEach {
                    database.exerciseSessionDao().updateDetails(it)
                    applyWorkoutHeartRate(it)
                }
                if (steps.isNotEmpty()) {
                    database.stepsDao().deleteBetween(today, today)
                    database.stepsDao().insertOrIgnore(steps)
                }
            }
            computeDailySummaries(today, today, zone)
            true
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        } finally {
            syncMutex.unlock()
        }
    }

    override fun observeHrvFeeling(date: LocalDate): Flow<HrvFeeling?> =
        database.hrvFeelingDao().observeForDate(date).map { HrvFeeling.fromName(it?.feeling) }

    override suspend fun setHrvFeeling(date: LocalDate, feeling: HrvFeeling) = withContext(Dispatchers.IO) {
      syncMutex.withLock {
        database.hrvFeelingDao().upsert(HrvFeelingEntity(date = date, feeling = feeling.name))
        // Recompute just this day from its stored summary - the same inputs a full sync uses.
        val summaryDao = database.dailyHealthSummaryDao()
        val summary = summaryDao.getSummaryForDateSync(date) ?: return@withLock
        val history = (1..BASELINE_DAYS).map { offset ->
            val prevDate = date.minusDays(offset.toLong())
            summaryDao.getSummaryForDateSync(prevDate).toDayMetrics(prevDate)
        }
        val result = recoveryCalculator.calculateRecovery(
            date = date,
            currentDayMetrics = summary.toDayMetrics(date).withStrainBalance(
                date,
                { day -> summaryDao.getSummaryForDateSync(day) },
                { day -> database.recoveryScoreDao().getScoreForDateSync(day)?.state }
            ),
            baselineHistory = history,
            config = recoveryConfigFor(feeling)
        )
        database.recoveryScoreDao().insertOrReplace(result.toEntity(date))
      }
    }

    private fun recoveryConfigFor(feeling: HrvFeeling?) =
        RecoveryConfig(assumedHrvScore = (feeling ?: HrvFeeling.DEFAULT).score)

    private fun DailyHealthSummaryEntity?.toDayMetrics(date: LocalDate) = DayMetrics(
        date = date,
        sleepDurationMinutes = this?.sleepDurationMinutes,
        restingHeartRate = this?.restingHeartRate,
        hrvRmssd = this?.hrvRmssd,
        trainingLoad = this?.dailyTrainingLoad,
        sleepConsistencyPercentage = this?.sleepConsistencyScore?.roundToInt(),
        sleepNeedMinutes = this?.sleepNeedMinutes,
        sleepQualityScore = this?.sleepQualityScore,
        respiratoryRate = this?.respiratoryRate,
        skinTempDeltaCelsius = this?.skinTempDeltaCelsius
    )

    /**
     * Adds yesterday's strain and the target band it was given: yesterday's recovery state
     * applied to the week of strain before it, the same band the Today screen showed.
     */
    private suspend fun DayMetrics.withStrainBalance(
        date: LocalDate,
        summaryFor: suspend (LocalDate) -> DailyHealthSummaryEntity?,
        stateFor: suspend (LocalDate) -> String?
    ): DayMetrics {
        val yesterday = date.minusDays(1)
        val strain = summaryFor(yesterday)?.dayStrain ?: return this
        val weekBefore = (1..7).mapNotNull { summaryFor(yesterday.minusDays(it.toLong()))?.dayStrain }
        if (weekBefore.isEmpty()) return copy(previousDayStrain = strain)
        val state = stateFor(yesterday)?.let { runCatching { RecoveryState.valueOf(it) }.getOrNull() } ?: RecoveryState.BUILDING_BASELINE
        val target = strainCalculator.recommendStrainTarget(state, weekBefore)
        return copy(previousDayStrain = strain, previousDayStrainTarget = target.targetLow..target.targetHigh)
    }

    private fun RecoveryResult.toEntity(date: LocalDate) = RecoveryScoreEntity(
        date = date,
        score = score,
        confidence = confidence.name,
        state = state.name,
        algorithmVersion = algorithmVersion,
        baselineWindowDays = 7,
        hrvComponentScore = hrvScore,
        rhrComponentScore = rhrScore,
        sleepComponentScore = sleepScore,
        trainingLoadComponentScore = trainingLoadScore,
        positiveContributors = positiveContributors.joinToString("|"),
        negativeContributors = negativeContributors.joinToString("|"),
        supportiveAdvice = supportiveAdvice,
        calculatedAt = Instant.now()
    )

    override fun getSyncStates(): Flow<List<SyncStateEntity>> =
        database.syncStateDao().getAllSyncStates()

    override suspend fun clearAllData() {
        database.withTransaction {
            database.sleepSessionDao().deleteAll()
            database.exerciseSessionDao().deleteAll()
            database.heartRateDao().deleteAll()
            database.restingHeartRateDao().deleteAll()
            database.hrvDao().deleteAll()
            database.stepsDao().deleteAll()
            database.weightDao().deleteAll()
            database.dailyHealthSummaryDao().deleteAll()
            database.recoveryScoreDao().deleteAll()
            database.heightDao().deleteAll()
            database.syncStateDao().deleteAll()
            database.foodLogDao().deleteBySource(com.kevan.hangry.data.local.entity.FoodLogSource.HEALTH_CONNECT)
            database.bodyFatScanDao().deleteAll()
            database.hrvFeelingDao().deleteAll()
            with(database.healthRecordsDao()) {
                deleteAllMarkers()
                deleteAllGoals()
                deleteAllProfileItems()
                deleteAllPeriods()
            }
            database.supplementDao().deleteAllSupplements()
            database.supplementDao().deleteAllIntakes()
            database.fastDao().deleteAll()
            database.longevityCheckInDao().deleteAll()
            database.programSessionDao().deleteAll()
        }
    }

    /**
     * Rebuilds the daily summaries and recovery scores for [start]..[end] only. Days before
     * [start] are already stored and are read back for baselines (see the rolling 7-day history
     * below), so a quick sync no longer recomputes all of history - that full pass is
     * recalculateAllBaselines(), run when the scoring rules change.
     */
    private suspend fun computeDailySummaries(start: LocalDate, end: LocalDate, zone: ZoneId) {
        val effectiveStart = start

        val daysBetween = ChronoUnit.DAYS.between(effectiveStart, end).toInt()
        val hrvFeelings = database.hrvFeelingDao().getBetweenList(effectiveStart, end)
            .associate { it.date to HrvFeeling.fromName(it.feeling) }

        // Resting metabolic rate is computed once from the latest known profile/weight and
        // applied across the recomputed range - a documented simplification (see CALCULATIONS.md
        // §8) rather than reconstructing point-in-time weight/age per historical day.
        val profile = database.userProfileDao().getProfileSync()
        val latestWeightKg = database.weightDao().getLatestWeightSync()?.weightKg
        // Health Connect (e.g. a smart scale synced via a watch/phone app) is the freshest
        // source when available - the manually-entered profile value is only a fallback.
        val effectiveHeightCm = database.heightDao().getLatestHeightSync()?.heightCm ?: profile?.heightCm
        val bmr = if (profile?.age != null && effectiveHeightCm != null && profile.biologicalSex != null && latestWeightKg != null) {
            val sex = try {
                BiologicalSex.valueOf(profile.biologicalSex)
            } catch (_: IllegalArgumentException) {
                null
            }
            sex?.let { calorieCalculator.calculateBmr(latestWeightKg, effectiveHeightCm, profile.age, it) }
        } else {
            null
        }

        val vo2MaxMap = try {
            dataSource.fetchVo2Max(effectiveStart.minusDays(30), end)
        } catch (_: Exception) {
            emptyMap()
        }
        // Raw readings, each placed in its night's sleep - read a month at a time.
        val spo2Readings = ReadingsByMonth(zone, dataSource::fetchOxygenSaturationReadings)
        val respReadings = ReadingsByMonth(zone, dataSource::fetchRespiratoryRateReadings)
        val skinTempMap = try {
            dataSource.fetchSkinTemperatureDeltas(effectiveStart, end)
        } catch (_: Exception) {
            emptyMap()
        }
        val bpMap = try {
            dataSource.fetchBloodPressure(effectiveStart, end)
        } catch (_: Exception) {
            emptyMap()
        }
        val bmrMap = try {
            dataSource.fetchBasalMetabolicRates(effectiveStart, end)
        } catch (_: Exception) {
            emptyMap()
        }
        val hydrationMap = try {
            dataSource.fetchHydration(effectiveStart, end)
        } catch (_: Exception) {
            emptyMap()
        }
        val bodyFatMap = try {
            val startInstant = effectiveStart.atStartOfDay(zone).toInstant()
            val endInstant = end.plusDays(1).atStartOfDay(zone).toInstant()
            dataSource.fetchBodyFat(startInstant, endInstant)
        } catch (_: Exception) {
            emptyMap()
        }

        // Skin temperature runs warmer after ovulation - known for women tracking their cycle, so
        // it isn't taken for illness. Periods start dates plus the average cycle length.
        val cycle = if (profile?.biologicalSex == BiologicalSex.FEMALE.name) {
            database.healthRecordsDao().getPeriods().takeIf { it.isNotEmpty() }?.let { periods ->
                periods.map { it.startDate } to HealthMarkerCalculator.cycleStats(periods.map { it.startDate to it.endDate }, end).averageCycleDays
            }
        } else null

        val summariesToInsert = ArrayList<DailyHealthSummaryEntity>(daysBetween + 1)
        val scoresToInsert = ArrayList<RecoveryScoreEntity>(daysBetween + 1)
        val computedSummariesByDate = HashMap<LocalDate, DailyHealthSummaryEntity>(daysBetween + 1)
        // Baselines look back 30 days; read the stored days once rather than per day.
        val storedSummariesByDate = database.dailyHealthSummaryDao()
            .getSummariesBetweenList(effectiveStart.minusDays(BASELINE_DAYS + 1L), end)
            .associateBy { it.date }
        fun summaryFor(day: LocalDate) = computedSummariesByDate[day] ?: storedSummariesByDate[day]
        val skinTempHistory = storedSummariesByDate.mapNotNull { (d, s) -> s.skinTempDeltaCelsius?.let { d to it } }.toMap() + skinTempMap
        // Yesterday's recovery sets the strain target it's judged against today.
        val computedStatesByDate = HashMap<LocalDate, String>(daysBetween + 1)
        val storedStatesByDate = database.recoveryScoreDao()
            .getScoresBetweenList(effectiveStart.minusDays(1), end)
            .associate { it.date to it.state }

        for (i in 0..daysBetween) {
            val date = effectiveStart.plusDays(i.toLong())
            val dayStart = date.atStartOfDay(zone).toInstant()
            val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant()

            // A sleep belongs to the day it ends on - each session to exactly one day.
            val sleepSessions = database.sleepSessionDao().getSessionsEndingBetween(dayStart, dayEnd)
            val primarySleep = HealthSyncPlan.primarySleep(date, sleepSessions, zone)

            val workouts = database.exerciseSessionDao().getSessionsBetweenList(dayStart, dayEnd)
            val trainingLoad = workouts.sumOf { trainingLoadCalculator.estimateSessionLoad(it) }

            val heartRateSamplesToday = database.heartRateDao().getSamplesBetweenList(dayStart, dayEnd)
            // Personal zones from max heart rate (setting, or age) and your usual resting heart rate.
            val usualRestingHr = (1..BASELINE_DAYS).mapNotNull { summaryFor(date.minusDays(it.toLong()))?.restingHeartRate }
                .takeIf { it.isNotEmpty() }?.average()
            val zones = HeartRateZones.forUser(profile?.age, profile?.maxHeartRate, usualRestingHr)
            // Sleep that overlaps the day - its heart rate isn't activity.
            val sleepOverlappingDay = database.sleepSessionDao()
                .getSessionsBetweenList(dayStart.minus(16, ChronoUnit.HOURS), dayEnd.plus(16, ChronoUnit.HOURS))
                .filter { it.endTime.isAfter(dayStart) && it.startTime.isBefore(dayEnd) }
            val strainResult = strainCalculator.calculateDayStrain(heartRateSamplesToday, workouts, zones, sleepOverlappingDay)

            // Real sleep history (fixes the previous behavior of always analyzing sleep with
            // an empty history) so sleep need/performance/consistency are personal-baseline driven.
            val sleepHistoryForCalc = database.sleepSessionDao().getSessionsEndingBetween(
                dayStart.minus(30, ChronoUnit.DAYS),
                dayStart
            )
            val previousDate = date.minusDays(1)
            val prevDaySummary = summaryFor(previousDate)
            val currentExistingSummary = storedSummariesByDate[date]

            // VO2 Max is carried forward up to 30 days if not recorded on this specific day
            val effectiveVo2 = vo2MaxMap[date]
                ?: vo2MaxMap.entries
                    .filter { !it.key.isAfter(date) && !it.key.isBefore(date.minusDays(30)) }
                    .maxByOrNull { it.key }?.value
                ?: currentExistingSummary?.vo2Max
                ?: prevDaySummary?.vo2Max

            val effectiveSpo2 = NightlyVitals.overnightAverage(spo2Readings.around(date), primarySleep?.startTime, primarySleep?.endTime, dayStart, dayEnd)
                ?: currentExistingSummary?.spo2Percentage
            val effectiveRespRate = NightlyVitals.overnightAverage(respReadings.around(date), primarySleep?.startTime, primarySleep?.endTime, dayStart, dayEnd)
                ?: currentExistingSummary?.respiratoryRate
            val effectiveSkinTemp = skinTempMap[date] ?: currentExistingSummary?.skinTempDeltaCelsius
            val bpReading = bpMap[date]
            val effectiveBpSystolic = bpReading?.first ?: currentExistingSummary?.bloodPressureSystolic
            val effectiveBpDiastolic = bpReading?.second ?: currentExistingSummary?.bloodPressureDiastolic

            val sleepGoal = profile?.sleepGoalMinutes ?: 480
            // Days with no strain data are null, so they don't drag the average down.
            val rollingAvgStrain = (1..7).mapNotNull { summaryFor(date.minusDays(it.toLong()))?.dayStrain }
                .let { if (it.isNotEmpty()) it.average() else null }

            val sleepAnalysis = sleepCalculator.analyzeSleep(
                currentSession = primarySleep,
                recentSessions = sleepHistoryForCalc.sortedByDescending { it.startTime },
                targetDurationMinutes = sleepGoal,
                previousDayStrain = prevDaySummary?.dayStrain,
                rollingAverageStrain = rollingAvgStrain
            )

            val rhr = database.restingHeartRateDao().getForDate(date)
            val steps = database.stepsDao().getForDate(date)

            // Overnight HRV: readings taken during last night's sleep, else the day's first one.
            val hrvRmssd = NightlyVitals.nightlyHrv(
                readings = (primarySleep?.let {
                    database.hrvDao().getBetweenTimestamps(it.startTime.minus(1, ChronoUnit.HOURS), it.endTime.plus(1, ChronoUnit.HOURS))
                }.orEmpty() + database.hrvDao().getBetweenList(date, date)).distinctBy { it.id },
                sleepStart = primarySleep?.startTime,
                sleepEnd = primarySleep?.endTime
            )

            // Resting heart rate is measured overnight, like recovery wearables do: the lowest
            // 5-minute average during last night's sleep. It's final once the night is over.
            val sleepingRhr = primarySleep?.let { night ->
                NightlyVitals.lowestSustainedBpm(
                    database.heartRateDao().getSamplesBetweenList(night.startTime, night.endTime),
                    night.startTime, night.endTime
                )
            }
            val effectiveRhr = sleepingRhr
                // Without overnight heart rate, a daytime estimate - locked once shown, so later
                // syncs the same day don't change a number the user already saw.
                ?: currentExistingSummary?.takeIf { it.calculationVersion >= SUMMARY_CALCULATION_VERSION }?.restingHeartRate
                ?: rhr?.restingBpm
                ?: run {
                    val rhrWindowEnd = if (date == LocalDate.now(zone)) Instant.now() else dayEnd
                    val rhrWindowStart = rhrWindowEnd.minus(24, ChronoUnit.HOURS)
                    val awakeSamples = database.heartRateDao().getSamplesBetweenList(rhrWindowStart, rhrWindowEnd)
                        .filter { s -> sleepOverlappingDay.none { !s.timestamp.isBefore(it.startTime) && s.timestamp.isBefore(it.endTime) } }
                    NightlyVitals.lowestSustainedBpm(awakeSamples, rhrWindowStart, rhrWindowEnd, minSamples = 3)
                }
                ?: database.heartRateDao().getRestingBpmEstimateBetween(dayStart, dayEnd)

            val avgHeartRate = database.heartRateDao().getAverageBpmBetween(dayStart, dayEnd)
            val sleepingHr = primarySleep?.let { night ->
                NightlyVitals.averageSleepingBpm(
                    database.heartRateDao().getSamplesBetweenList(night.startTime, night.endTime),
                    night.startTime, night.endTime
                )
            } ?: currentExistingSummary?.sleepingHeartRate

            var completeness = 0.0
            if (primarySleep != null) completeness += 0.25
            if (effectiveRhr != null) completeness += 0.25
            if (hrvRmssd != null) completeness += 0.25
            if (steps != null) completeness += 0.25

            val qualityState = when {
                completeness >= 0.75 -> "COMPLETE"
                completeness >= 0.5 -> "PARTIAL"
                completeness > 0.0 -> "INSUFFICIENT"
                else -> "UNAVAILABLE"
            }

            val dayBmr = bmrMap[date] ?: bmr
            val effectiveHydration = hydrationMap[date] ?: currentExistingSummary?.hydrationLiters
            val effectiveBodyFat = bodyFatMap[date] ?: currentExistingSummary?.bodyFatPercentage

            val active = ActiveActivityCalculator.calculate(
                totalSteps = steps?.stepCount,
                workouts = workouts,
                weightKg = latestWeightKg ?: profile?.currentWeightKg,
                heightCm = effectiveHeightCm,
                bmr = dayBmr,
                recordedActiveCalories = steps?.activeCalories
            )

            val computedSummary = DailyHealthSummaryEntity(
                date = date,
                sleepDurationMinutes = primarySleep?.durationMinutes,
                sleepStartTime = primarySleep?.startTime,
                sleepEndTime = primarySleep?.endTime,
                sleepConsistencyScore = sleepAnalysis.consistencyPercentage?.toDouble(),
                sleepNeedMinutes = primarySleep?.let { sleepAnalysis.sleepNeedMinutes },
                sleepScore = sleepAnalysis.sleepScore,
                sleepQualityScore = sleepAnalysis.sleepQualityScore,
                steps = steps?.stepCount,
                distanceMeters = steps?.distanceMeters,
                activeCalories = active.activeCalories,
                totalCalories = ActiveActivityCalculator.totalBurn(dayBmr, active.activeCalories, active.activeMinutes),
                bmrCalories = dayBmr,
                exerciseDurationMinutes = workouts.sumOf { it.durationMinutes }.takeIf { it > 0 },
                activeMinutes = active.activeMinutes,
                exerciseCount = workouts.size,
                dailyTrainingLoad = trainingLoad,
                dayStrain = strainResult.dayStrain.takeIf { strainResult.source != StrainSource.NONE },
                restingHeartRate = effectiveRhr,
                averageHeartRate = avgHeartRate,
                sleepingHeartRate = sleepingHr,
                hrvRmssd = hrvRmssd,
                vo2Max = effectiveVo2,
                spo2Percentage = effectiveSpo2,
                respiratoryRate = effectiveRespRate,
                skinTempDeltaCelsius = effectiveSkinTemp,
                bloodPressureSystolic = effectiveBpSystolic,
                bloodPressureDiastolic = effectiveBpDiastolic,
                hydrationLiters = effectiveHydration,
                bodyFatPercentage = effectiveBodyFat,
                dataCompletenessRatio = completeness,
                dataQualityState = qualityState,
                calculationVersion = SUMMARY_CALCULATION_VERSION,
                lastCalculatedTimestamp = Instant.now()
            )
            val lutealPhase = cycle?.let { CycleTemperature.isLuteal(it.first, it.second, skinTempHistory, date) } == true
            val signals = BodySignals.evaluate(computedSummary, (1..BASELINE_DAYS).mapNotNull { summaryFor(date.minusDays(it.toLong())) }, lutealPhase)
            val summary = computedSummary.copy(bodySignals = BodySignals.encode(signals.signals))
            summariesToInsert.add(summary)
            computedSummariesByDate[date] = summary

            // Rolling 30-day baseline history
            val baselineDays = (1..BASELINE_DAYS).map { offset ->
                val prevDate = date.minusDays(offset.toLong())
                summaryFor(prevDate).toDayMetrics(prevDate)
            }
            val currentDayMetrics = summary.toDayMetrics(date).withStrainBalance(date, ::summaryFor) { day ->
                computedStatesByDate[day] ?: storedStatesByDate[day]
            }.copy(lutealPhase = lutealPhase)

            val recoveryResult = recoveryCalculator.calculateRecovery(
                date = date,
                currentDayMetrics = currentDayMetrics,
                baselineHistory = baselineDays,
                config = recoveryConfigFor(hrvFeelings[date])
            )
            scoresToInsert.add(recoveryResult.toEntity(date))
            computedStatesByDate[date] = recoveryResult.state.name
        }

        database.withTransaction {
            database.dailyHealthSummaryDao().insertOrReplaceAll(summariesToInsert)
            database.recoveryScoreDao().insertOrReplaceAll(scoresToInsert)
        }
    }

    private companion object {
        const val KEY_DEDUP_VERSION = "dedup_version"
        /** Bump when duplicate handling changes, to clean up already-imported history once more. */
        const val DEDUP_VERSION = 1
        /** Days of history behind recovery baselines and usual resting heart rate. */
        const val BASELINE_DAYS = 30
    }
}
