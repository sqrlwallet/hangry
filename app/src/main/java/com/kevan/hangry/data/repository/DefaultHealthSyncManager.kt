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
import com.kevan.hangry.data.local.entity.FoodLogEntity
import com.kevan.hangry.data.local.entity.FoodLogSource
import com.kevan.hangry.data.datasource.RealHealthConnectDataSource
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.NutritionRecord
import com.kevan.hangry.domain.calculation.ActiveActivityCalculator
import com.kevan.hangry.domain.calculation.CalorieCalculator
import com.kevan.hangry.domain.calculation.DayMetrics
import com.kevan.hangry.domain.calculation.RecoveryCalculator
import com.kevan.hangry.domain.calculation.RecoveryConfig
import com.kevan.hangry.domain.calculation.SleepCalculator
import com.kevan.hangry.domain.calculation.StrainCalculator
import com.kevan.hangry.domain.calculation.TrainingLoadCalculator
import com.kevan.hangry.domain.model.BiologicalSex
import com.kevan.hangry.domain.model.HrvFeeling
import com.kevan.hangry.domain.model.RecoveryResult
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
    private val prefs: android.content.SharedPreferences? = null
) : HealthSyncManager {

    // Every "what day is it" / day-boundary computation in this class uses the device's local
    // zone, matching DashboardViewModel - a day starts at local midnight, not UTC midnight.
    private val zone = ZoneId.systemDefault()

    // Serializes syncHistorical/recalculateAllBaselines so the periodic background worker and
    // a manual trigger (or two manual triggers) can never interleave writes to the same days -
    // without this, one run's insertOrReplace could overwrite the other's with a stale snapshot.
    private val syncMutex = Mutex()

    override fun syncHistorical(days: Int): Flow<SyncProgress> = flow {
      syncMutex.withLock {
        emit(SyncProgress(status = SyncStatus.IN_PROGRESS, currentDataType = "INITIALIZING"))

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
        // The nutrition read returns nothing on failure, so only trust an empty-looking window
        // when access is there and something came back.
        val canReadMeals = HealthPermission.getReadPermission(NutritionRecord::class) in granted
        val fullyVisibleFrom = today.minusDays(29)

        try {
            for ((chunkIndex, chunk) in chunks.withIndex()) {
                val (chunkStart, chunkEnd) = chunk
                // Apply a 2-hour overlap window to catch late-arriving records
                val chunkStartInstant = chunkStart.atStartOfDay(zone).toInstant().minus(2, ChronoUnit.HOURS)
                val chunkEndInstant = chunkEnd.plusDays(1).atStartOfDay(zone).toInstant()

                val chunkLabel = "Batch ${chunkIndex + 1}/${chunks.size}"

                // 1. Sleep Sessions
                emit(SyncProgress(status = SyncStatus.IN_PROGRESS, currentDataType = "$chunkLabel (Sleep)"))
                val sleepSessions = dataSource.fetchSleepSessions(chunkStartInstant, chunkEndInstant)

                // 2. Exercise Sessions
                emit(SyncProgress(status = SyncStatus.IN_PROGRESS, currentDataType = "$chunkLabel (Workouts)"))
                val workouts = dataSource.fetchExerciseSessions(chunkStartInstant, chunkEndInstant)

                // 3. Resting Heart Rate
                emit(SyncProgress(status = SyncStatus.IN_PROGRESS, currentDataType = "$chunkLabel (Resting HR)"))
                val rhrRecords = dataSource.fetchRestingHeartRates(chunkStart, chunkEnd)

                // 4. HRV RMSSD
                emit(SyncProgress(status = SyncStatus.IN_PROGRESS, currentDataType = "$chunkLabel (HRV)"))
                val hrvRecords = dataSource.fetchHrvMeasurements(chunkStart, chunkEnd)

                // 5. Steps & Activity
                emit(SyncProgress(status = SyncStatus.IN_PROGRESS, currentDataType = "$chunkLabel (Steps)"))
                val stepsRecords = dataSource.fetchStepsSummaries(chunkStart, chunkEnd)

                // 6. Continuous Heart Rate Samples (Wear OS / Galaxy Watch / Fitbit continuous HR)
                emit(SyncProgress(status = SyncStatus.IN_PROGRESS, currentDataType = "$chunkLabel (Heart Rate)"))
                val hrRecords = dataSource.fetchHeartRateSamples(chunkStartInstant, chunkEndInstant)

                // 7. Weight & Height Measurements
                emit(SyncProgress(status = SyncStatus.IN_PROGRESS, currentDataType = "$chunkLabel (Weight)"))
                val weightRecords = dataSource.fetchWeightMeasurements(chunkStartInstant, chunkEndInstant)
                val heightRecords = dataSource.fetchHeightMeasurements(chunkStartInstant, chunkEndInstant)

                // 8. External Nutrition Records
                emit(SyncProgress(status = SyncStatus.IN_PROGRESS, currentDataType = "$chunkLabel (Nutrition)"))
                val nutritionRecords = dataSource.fetchNutritionRecords(chunkStartInstant, chunkEndInstant)

                // 9. Vitals & Cardio Fitness (SpO2, VO2 Max, Respiratory Rate, Blood Pressure)
                emit(SyncProgress(status = SyncStatus.IN_PROGRESS, currentDataType = "$chunkLabel (Vitals)"))

                val chunkRead = sleepSessions.size + workouts.size + rhrRecords.size + hrvRecords.size +
                    stepsRecords.size + hrRecords.size + weightRecords.size + heightRecords.size + nutritionRecords.size
                totalRead += chunkRead

                // Perform all database writes for this chunk in an atomic transaction
                var chunkInserted = 0
                database.withTransaction {
                    // Drop what's no longer in Health Connect for this window: records deleted or
                    // edited at the source, and copies another app already reported (the fetch
                    // keeps one copy per event - see HealthDedup).
                    if (canSeeAllHistory || !chunkStart.isBefore(fullyVisibleFrom)) {
                        removeStale(chunkStartInstant, chunkEndInstant, sleepSessions, workouts, weightRecords,
                            nutritionRecords.takeIf { canReadMeals && it.isNotEmpty() })
                    }
                    val sleepInserted = database.sleepSessionDao().insertOrIgnore(sleepSessions)
                    chunkInserted += sleepInserted.count { it != -1L }

                    val workoutInserted = database.exerciseSessionDao().insertOrIgnore(workouts)
                    chunkInserted += workoutInserted.count { it != -1L }
                    // Already-saved workouts are refreshed too, so edits and newly read details land.
                    workouts.forEach { database.exerciseSessionDao().updateDetails(it) }

                    val rhrInserted = database.restingHeartRateDao().insertOrIgnore(rhrRecords)
                    chunkInserted += rhrInserted.count { it != -1L }

                    val hrvInserted = database.hrvDao().insertOrIgnore(hrvRecords)
                    chunkInserted += hrvInserted.count { it != -1L }

                    // Older days Health Connect can't show (no full-history access) keep what's stored.
                    if (canSeeAllHistory || !chunkStart.isBefore(fullyVisibleFrom)) {
                        database.stepsDao().deleteBetween(chunkStart, chunkEnd)
                    }
                    val stepsInserted = database.stepsDao().insertOrIgnore(stepsRecords)
                    chunkInserted += stepsInserted.count { it != -1L }

                    val hrInserted = database.heartRateDao().insertOrIgnore(hrRecords)
                    chunkInserted += hrInserted.count { it != -1L }
                    workouts.forEach { applyWorkoutHeartRate(it) }

                    val weightInserted = database.weightDao().insertOrIgnore(weightRecords)
                    chunkInserted += weightInserted.count { it != -1L }

                    val heightInserted = database.heightDao().insertOrIgnore(heightRecords)
                    chunkInserted += heightInserted.count { it != -1L }

                    val nutritionInserted = database.foodLogDao().insertOrIgnore(nutritionRecords)
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
            computeDailySummaries(startDate, today)

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
        val today = LocalDate.now(zone)
        val earliestSteps = database.stepsDao().getEarliestDate()
        val oldestSleep = database.sleepSessionDao().getOldestSession()?.startTime?.atZone(zone)?.toLocalDate()
        val oldestExercise = database.exerciseSessionDao().getOldestSession()?.startTime?.atZone(zone)?.toLocalDate()

        val earliestDate = listOfNotNull(earliestSteps, oldestSleep, oldestExercise).minOrNull() ?: today.minusDays(28)

        emit(SyncProgress(status = SyncStatus.IN_PROGRESS, currentDataType = "CALCULATING_HISTORICAL_DAYS"))
        computeDailySummaries(earliestDate, today)
        emit(SyncProgress(status = SyncStatus.SUCCESS, currentDataType = "DONE"))
      }
    }.flowOn(Dispatchers.IO)

    private suspend fun removeStale(
        from: Instant,
        to: Instant,
        sleep: List<SleepSessionEntity>,
        workouts: List<ExerciseSessionEntity>,
        weights: List<WeightMeasurementEntity>,
        meals: List<FoodLogEntity>?
    ) {
        val sleepDao = database.sleepSessionDao()
        (sleepDao.getImportedFingerprintsStartingBetween(from, to) - sleep.map { it.recordFingerprint }.toSet())
            .chunked(500).forEach { sleepDao.deleteByFingerprints(it) }
        val workoutDao = database.exerciseSessionDao()
        (workoutDao.getImportedFingerprintsStartingBetween(from, to) - workouts.map { it.recordFingerprint }.toSet())
            .chunked(500).forEach { workoutDao.deleteByFingerprints(it) }
        val weightDao = database.weightDao()
        (weightDao.getImportedFingerprintsBetween(from, to) - weights.map { it.recordFingerprint }.toSet())
            .chunked(500).forEach { weightDao.deleteByFingerprints(it) }
        if (meals == null) return
        val foodDao = database.foodLogDao()
        (foodDao.getImportedIdsBetween(FoodLogSource.HEALTH_CONNECT, from, to) - meals.mapNotNull { it.sourceRecordId }.toSet())
            .chunked(500).forEach { foodDao.deleteBySourceRecordIds(it) }
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
        // No access right now (permission, Health Connect missing): try again next launch.
        if (result?.status == SyncStatus.SUCCESS) prefs.edit().putInt(KEY_DEDUP_VERSION, DEDUP_VERSION).apply()
        return true
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

    override fun observeHrvFeeling(date: LocalDate): Flow<HrvFeeling?> =
        database.hrvFeelingDao().observeForDate(date).map { HrvFeeling.fromName(it?.feeling) }

    override suspend fun setHrvFeeling(date: LocalDate, feeling: HrvFeeling) = withContext(Dispatchers.IO) {
      syncMutex.withLock {
        database.hrvFeelingDao().upsert(HrvFeelingEntity(date = date, feeling = feeling.name))
        // Recompute just this day from its stored summary - the same inputs a full sync uses.
        val summaryDao = database.dailyHealthSummaryDao()
        val summary = summaryDao.getSummaryForDateSync(date) ?: return@withLock
        val history = (1..7).map { offset ->
            val prevDate = date.minusDays(offset.toLong())
            summaryDao.getSummaryForDateSync(prevDate).toDayMetrics(prevDate)
        }
        val result = recoveryCalculator.calculateRecovery(
            date = date,
            currentDayMetrics = summary.toDayMetrics(date),
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
        sleepConsistencyPercentage = this?.sleepConsistencyScore?.roundToInt()
    )

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
        }
    }

    /**
     * Rebuilds the daily summaries and recovery scores for [start]..[end] only. Days before
     * [start] are already stored and are read back for baselines (see the rolling 7-day history
     * below), so a quick sync no longer recomputes all of history - that full pass is
     * recalculateAllBaselines(), run when the scoring rules change.
     */
    private suspend fun computeDailySummaries(start: LocalDate, end: LocalDate) {
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
        val spo2Map = try {
            dataSource.fetchOxygenSaturation(effectiveStart, end)
        } catch (_: Exception) {
            emptyMap()
        }
        val respRateMap = try {
            dataSource.fetchRespiratoryRate(effectiveStart, end)
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

        val summariesToInsert = ArrayList<DailyHealthSummaryEntity>(daysBetween + 1)
        val scoresToInsert = ArrayList<RecoveryScoreEntity>(daysBetween + 1)
        val computedSummariesByDate = HashMap<LocalDate, DailyHealthSummaryEntity>(daysBetween + 1)

        for (i in 0..daysBetween) {
            val date = effectiveStart.plusDays(i.toLong())
            val dayStart = date.atStartOfDay(zone).toInstant()
            val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant()

            val sleepSessions = database.sleepSessionDao().getSessionsBetweenList(dayStart.minus(6, ChronoUnit.HOURS), dayEnd)
            val primarySleep = sleepSessions.maxByOrNull { it.durationMinutes }

            val workouts = database.exerciseSessionDao().getSessionsBetweenList(dayStart, dayEnd)
            val trainingLoad = workouts.sumOf { trainingLoadCalculator.estimateSessionLoad(it) }

            val heartRateSamplesToday = database.heartRateDao().getSamplesBetweenList(dayStart, dayEnd)
            val strainResult = strainCalculator.calculateDayStrain(heartRateSamplesToday, workouts)

            // Real sleep history (fixes the previous behavior of always analyzing sleep with
            // an empty history) so sleep need/performance/consistency are personal-baseline driven.
            val sleepHistoryForCalc = database.sleepSessionDao().getSessionsBetweenList(
                dayStart.minus(6, ChronoUnit.HOURS).minus(30, ChronoUnit.DAYS),
                dayStart.minus(6, ChronoUnit.HOURS)
            )
            val previousDate = date.minusDays(1)
            val prevDaySummary = computedSummariesByDate[previousDate]
                ?: database.dailyHealthSummaryDao().getSummaryForDateSync(previousDate)
            val currentExistingSummary = database.dailyHealthSummaryDao().getSummaryForDateSync(date)

            // VO2 Max is carried forward up to 30 days if not recorded on this specific day
            val effectiveVo2 = vo2MaxMap[date]
                ?: vo2MaxMap.entries
                    .filter { !it.key.isAfter(date) && !it.key.isBefore(date.minusDays(30)) }
                    .maxByOrNull { it.key }?.value
                ?: currentExistingSummary?.vo2Max
                ?: prevDaySummary?.vo2Max

            val effectiveSpo2 = spo2Map[date] ?: currentExistingSummary?.spo2Percentage
            val effectiveRespRate = respRateMap[date] ?: currentExistingSummary?.respiratoryRate
            val bpReading = bpMap[date]
            val effectiveBpSystolic = bpReading?.first ?: currentExistingSummary?.bloodPressureSystolic
            val effectiveBpDiastolic = bpReading?.second ?: currentExistingSummary?.bloodPressureDiastolic

            val sleepGoal = profile?.sleepGoalMinutes ?: 480
            val prevDayDebt = prevDaySummary?.sleepDurationMinutes?.let { max(0, sleepGoal - it) } ?: 0
            val recentSummariesForStrain = database.dailyHealthSummaryDao()
                .getSummariesBetweenList(date.minusDays(7), date.minusDays(1))
            val rollingAvgStrain = recentSummariesForStrain.mapNotNull { it.dayStrain }
                .let { if (it.isNotEmpty()) it.average() else null }

            val sleepAnalysis = sleepCalculator.analyzeSleep(
                currentSession = primarySleep,
                recentSessions = sleepHistoryForCalc,
                targetDurationMinutes = sleepGoal,
                previousDaySleepDebtMinutes = prevDayDebt,
                previousDayStrain = prevDaySummary?.dayStrain,
                rollingAverageStrain = rollingAvgStrain
            )

            val rhr = database.restingHeartRateDao().getForDate(date)
            val hrv = database.hrvDao().getForDate(date)
            val steps = database.stepsDao().getForDate(date)

            // Once a resting heart rate has been computed for this date, it is locked for the
            // rest of the day: re-syncing later (more samples arrive, a nap gets logged, etc.)
            // must not change the number a user already saw.
            val effectiveRhr = if (currentExistingSummary?.restingHeartRate != null) {
                currentExistingSummary.restingHeartRate
            } else {
                // Resting heart rate is defined as the lowest recorded heart rate while NOT
                // sleeping, over the last 24-hour window prior to calculation.
                val rhrWindowEnd = if (date == LocalDate.now()) Instant.now() else dayEnd
                val rhrWindowStart = rhrWindowEnd.minus(24, ChronoUnit.HOURS)
                val rhrSamples24h = database.heartRateDao().getSamplesBetweenList(rhrWindowStart, rhrWindowEnd)
                val sleepSessions24h = database.sleepSessionDao().getSessionsBetweenList(
                    rhrWindowStart.minus(6, ChronoUnit.HOURS),
                    rhrWindowEnd
                )
                val awakeMinRhr = rhrSamples24h
                    .asSequence()
                    .filter { sample -> sample.bpm >= 35 }
                    .filter { sample ->
                        sleepSessions24h.none { session ->
                            !sample.timestamp.isBefore(session.startTime) && sample.timestamp.isBefore(session.endTime)
                        }
                    }
                    .minOfOrNull { it.bpm }

                awakeMinRhr
                    ?: database.heartRateDao().getMinBpmBetween(rhrWindowStart, rhrWindowEnd)
                    ?: rhr?.restingBpm
                    ?: database.heartRateDao().getRestingBpmEstimateBetween(dayStart, dayEnd)
            }

            val avgHeartRate = database.heartRateDao().getAverageBpmBetween(dayStart, dayEnd)

            var completeness = 0.0
            if (primarySleep != null) completeness += 0.25
            if (effectiveRhr != null) completeness += 0.25
            if (hrv != null) completeness += 0.25
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

            val summary = DailyHealthSummaryEntity(
                date = date,
                sleepDurationMinutes = primarySleep?.durationMinutes,
                sleepStartTime = primarySleep?.startTime,
                sleepEndTime = primarySleep?.endTime,
                sleepConsistencyScore = sleepAnalysis.consistencyPercentage?.toDouble(),
                steps = steps?.stepCount,
                distanceMeters = steps?.distanceMeters,
                activeCalories = active.activeCalories,
                totalCalories = ActiveActivityCalculator.totalBurn(dayBmr, active.activeCalories, active.activeMinutes),
                bmrCalories = dayBmr,
                exerciseDurationMinutes = workouts.sumOf { it.durationMinutes }.takeIf { it > 0 },
                activeMinutes = active.activeMinutes,
                exerciseCount = workouts.size,
                dailyTrainingLoad = trainingLoad,
                dayStrain = strainResult.dayStrain,
                restingHeartRate = effectiveRhr,
                averageHeartRate = avgHeartRate,
                hrvRmssd = hrv?.rmssd,
                vo2Max = effectiveVo2,
                spo2Percentage = effectiveSpo2,
                respiratoryRate = effectiveRespRate,
                bloodPressureSystolic = effectiveBpSystolic,
                bloodPressureDiastolic = effectiveBpDiastolic,
                hydrationLiters = effectiveHydration,
                bodyFatPercentage = effectiveBodyFat,
                dataCompletenessRatio = completeness,
                dataQualityState = qualityState,
                calculationVersion = SUMMARY_CALCULATION_VERSION,
                lastCalculatedTimestamp = Instant.now()
            )
            summariesToInsert.add(summary)
            computedSummariesByDate[date] = summary

            // Rolling 7-day baseline history
            val baselineDays = (1..7).map { offset ->
                val prevDate = date.minusDays(offset.toLong())
                val prevSummary = computedSummariesByDate[prevDate]
                    ?: database.dailyHealthSummaryDao().getSummaryForDateSync(prevDate)
                DayMetrics(
                    date = prevDate,
                    sleepDurationMinutes = prevSummary?.sleepDurationMinutes,
                    restingHeartRate = prevSummary?.restingHeartRate,
                    hrvRmssd = prevSummary?.hrvRmssd,
                    trainingLoad = prevSummary?.dailyTrainingLoad,
                    sleepConsistencyPercentage = prevSummary?.sleepConsistencyScore?.roundToInt()
                )
            }

            val currentDayMetrics = DayMetrics(
                date = date,
                sleepDurationMinutes = primarySleep?.durationMinutes,
                restingHeartRate = effectiveRhr,
                hrvRmssd = hrv?.rmssd,
                trainingLoad = trainingLoad,
                sleepConsistencyPercentage = sleepAnalysis.consistencyPercentage
            )

            val recoveryResult = recoveryCalculator.calculateRecovery(
                date = date,
                currentDayMetrics = currentDayMetrics,
                baselineHistory = baselineDays,
                config = recoveryConfigFor(hrvFeelings[date])
            )
            scoresToInsert.add(recoveryResult.toEntity(date))
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
    }
}
