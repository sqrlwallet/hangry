package com.kevan.hangry.data.repository

import com.kevan.hangry.data.datasource.HealthConnectDataSource
import com.kevan.hangry.data.local.HangryDatabase
import com.kevan.hangry.data.local.entity.DailyHealthSummaryEntity
import com.kevan.hangry.data.local.entity.RecoveryScoreEntity
import com.kevan.hangry.data.local.entity.SyncStateEntity
import com.kevan.hangry.domain.calculation.CalorieCalculator
import com.kevan.hangry.domain.calculation.DayMetrics
import com.kevan.hangry.domain.calculation.RecoveryCalculator
import com.kevan.hangry.domain.calculation.SleepCalculator
import com.kevan.hangry.domain.calculation.StrainCalculator
import com.kevan.hangry.domain.calculation.TrainingLoadCalculator
import com.kevan.hangry.domain.model.BiologicalSex
import com.kevan.hangry.domain.model.SyncProgress
import com.kevan.hangry.domain.model.SyncStatus
import com.kevan.hangry.domain.repository.HealthSyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
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
    private val calorieCalculator: CalorieCalculator
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
        val startDate = today.minusDays(days.toLong())

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
                totalRead += sleepSessions.size
                val sleepInserted = database.sleepSessionDao().insertOrIgnore(sleepSessions)
                val sleepCount = sleepInserted.count { it != -1L }
                totalInserted += sleepCount
                totalSkipped += (sleepSessions.size - sleepCount)

                // 2. Exercise Sessions
                emit(SyncProgress(status = SyncStatus.IN_PROGRESS, currentDataType = "$chunkLabel (Workouts)"))
                val workouts = dataSource.fetchExerciseSessions(chunkStartInstant, chunkEndInstant)
                totalRead += workouts.size
                val workoutInserted = database.exerciseSessionDao().insertOrIgnore(workouts)
                val workoutCount = workoutInserted.count { it != -1L }
                totalInserted += workoutCount
                totalSkipped += (workouts.size - workoutCount)

                // 3. Resting Heart Rate
                emit(SyncProgress(status = SyncStatus.IN_PROGRESS, currentDataType = "$chunkLabel (Resting HR)"))
                val rhrRecords = dataSource.fetchRestingHeartRates(chunkStart, chunkEnd)
                totalRead += rhrRecords.size
                val rhrInserted = database.restingHeartRateDao().insertOrIgnore(rhrRecords)
                val rhrCount = rhrInserted.count { it != -1L }
                totalInserted += rhrCount
                totalSkipped += (rhrRecords.size - rhrCount)

                // 4. HRV RMSSD
                emit(SyncProgress(status = SyncStatus.IN_PROGRESS, currentDataType = "$chunkLabel (HRV)"))
                val hrvRecords = dataSource.fetchHrvMeasurements(chunkStart, chunkEnd)
                totalRead += hrvRecords.size
                val hrvInserted = database.hrvDao().insertOrIgnore(hrvRecords)
                val hrvCount = hrvInserted.count { it != -1L }
                totalInserted += hrvCount
                totalSkipped += (hrvRecords.size - hrvCount)

                // 5. Steps & Activity
                emit(SyncProgress(status = SyncStatus.IN_PROGRESS, currentDataType = "$chunkLabel (Steps)"))
                val stepsRecords = dataSource.fetchStepsSummaries(chunkStart, chunkEnd)
                totalRead += stepsRecords.size
                // Each row is a re-aggregated running total for its date (see fetchStepsSummaries),
                // so re-syncing a day whose total has since changed must replace its row rather
                // than accumulate a new one alongside it - clear the range first for a clean upsert.
                database.stepsDao().deleteBetween(chunkStart, chunkEnd)
                val stepsInserted = database.stepsDao().insertOrIgnore(stepsRecords)
                val stepsCount = stepsInserted.count { it != -1L }
                totalInserted += stepsCount
                totalSkipped += (stepsRecords.size - stepsCount)

                // 6. Continuous Heart Rate Samples (Wear OS / Galaxy Watch / Fitbit continuous HR)
                emit(SyncProgress(status = SyncStatus.IN_PROGRESS, currentDataType = "$chunkLabel (Heart Rate)"))
                val hrRecords = dataSource.fetchHeartRateSamples(chunkStartInstant, chunkEndInstant)
                totalRead += hrRecords.size
                val hrInserted = database.heartRateDao().insertOrIgnore(hrRecords)
                val hrCount = hrInserted.count { it != -1L }
                totalInserted += hrCount
                totalSkipped += (hrRecords.size - hrCount)

                // 7. Weight & Height Measurements
                emit(SyncProgress(status = SyncStatus.IN_PROGRESS, currentDataType = "$chunkLabel (Weight)"))
                val weightRecords = dataSource.fetchWeightMeasurements(chunkStartInstant, chunkEndInstant)
                totalRead += weightRecords.size
                val weightInserted = database.weightDao().insertOrIgnore(weightRecords)
                val weightCount = weightInserted.count { it != -1L }
                totalInserted += weightCount
                totalSkipped += (weightRecords.size - weightCount)

                val heightRecords = dataSource.fetchHeightMeasurements(chunkStartInstant, chunkEndInstant)
                totalRead += heightRecords.size
                val heightInserted = database.heightDao().insertOrIgnore(heightRecords)
                val heightCount = heightInserted.count { it != -1L }
                totalInserted += heightCount
                totalSkipped += (heightRecords.size - heightCount)

                // 8. Vitals & Cardio Fitness (SpO2, VO2 Max, Respiratory Rate, Blood Pressure)
                emit(SyncProgress(status = SyncStatus.IN_PROGRESS, currentDataType = "$chunkLabel (Vitals)"))

                // Update SyncStateEntity checkpoint
                database.syncStateDao().insertOrReplace(
                    SyncStateEntity(
                        dataType = "ALL",
                        lastSuccessfulSyncTimestamp = Instant.now(),
                        historicalImportStart = chunkStartInstant,
                        historicalImportEnd = chunkEndInstant,
                        syncStatus = "IN_PROGRESS",
                        recordsRead = totalRead,
                        recordsInserted = totalInserted,
                        recordsSkipped = totalSkipped
                    )
                )
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

    override fun getSyncStates(): Flow<List<SyncStateEntity>> =
        database.syncStateDao().getAllSyncStates()

    override suspend fun clearAllData() {
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
    }

    private suspend fun computeDailySummaries(start: LocalDate, end: LocalDate) {
        val earliestSteps = database.stepsDao().getEarliestDate()
        val oldestSleep = database.sleepSessionDao().getOldestSession()?.startTime?.atZone(zone)?.toLocalDate()
        val oldestExercise = database.exerciseSessionDao().getOldestSession()?.startTime?.atZone(zone)?.toLocalDate()
        val earliestDataDate = listOfNotNull(earliestSteps, oldestSleep, oldestExercise).minOrNull()
        val effectiveStart = if (earliestDataDate != null && earliestDataDate.isBefore(start)) earliestDataDate else start

        val daysBetween = ChronoUnit.DAYS.between(effectiveStart, end).toInt()

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
            val prevDaySummary = database.dailyHealthSummaryDao().getSummaryForDateSync(previousDate)
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

            val prevDayDebt = prevDaySummary?.sleepDurationMinutes?.let { max(0, 480 - it) } ?: 0
            val recentSummariesForStrain = database.dailyHealthSummaryDao()
                .getSummariesBetweenList(date.minusDays(7), date.minusDays(1))
            val rollingAvgStrain = recentSummariesForStrain.mapNotNull { it.dayStrain }
                .let { if (it.isNotEmpty()) it.average() else null }

            val sleepAnalysis = sleepCalculator.analyzeSleep(
                currentSession = primarySleep,
                recentSessions = sleepHistoryForCalc,
                previousDaySleepDebtMinutes = prevDayDebt,
                previousDayStrain = prevDaySummary?.dayStrain,
                rollingAverageStrain = rollingAvgStrain
            )

            val rhr = database.restingHeartRateDao().getForDate(date)
            val hrv = database.hrvDao().getForDate(date)
            val steps = database.stepsDao().getForDate(date)

            // Resting heart rate is defined as the lowest recorded heart rate while NOT
            // sleeping, over this day's 24-hour window - not the wearable's own RHR record
            // (which can be computed differently, e.g. from sleeping HR) and not a rolling
            // average. sleepSessions covers dayStart-6h..dayEnd so it also excludes the tail
            // of a sleep session that started the previous evening.
            val awakeMinRhr = heartRateSamplesToday
                .asSequence()
                .filter { sample -> sample.bpm >= 35 }
                .filter { sample ->
                    sleepSessions.none { session ->
                        !sample.timestamp.isBefore(session.startTime) && sample.timestamp.isBefore(session.endTime)
                    }
                }
                .minOfOrNull { it.bpm }

            // Once a resting heart rate has been computed for this date, it is locked for the
            // rest of the day: re-syncing later (more samples arrive, a nap gets logged, etc.)
            // must not change the number a user already saw.
            val effectiveRhr = currentExistingSummary?.restingHeartRate
                ?: awakeMinRhr
                ?: rhr?.restingBpm
                ?: (primarySleep?.let { database.heartRateDao().getAverageBpmBetween(it.startTime, it.endTime) })
                ?: database.heartRateDao().getRestingBpmEstimateBetween(dayStart, dayEnd)
                ?: database.heartRateDao().getMinBpmBetween(dayStart, dayEnd)

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

            val summary = DailyHealthSummaryEntity(
                date = date,
                sleepDurationMinutes = primarySleep?.durationMinutes,
                sleepStartTime = primarySleep?.startTime,
                sleepEndTime = primarySleep?.endTime,
                sleepConsistencyScore = sleepAnalysis.consistencyPercentage.toDouble(),
                steps = steps?.stepCount,
                distanceMeters = steps?.distanceMeters,
                activeCalories = steps?.activeCalories,
                totalCalories = bmr?.let { it + (steps?.activeCalories ?: 0.0) } ?: steps?.activeCalories,
                bmrCalories = bmr,
                exerciseDurationMinutes = workouts.sumOf { it.durationMinutes }.takeIf { it > 0 },
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
                dataCompletenessRatio = completeness,
                dataQualityState = qualityState,
                calculationVersion = 1,
                lastCalculatedTimestamp = Instant.now()
            )
            database.dailyHealthSummaryDao().insertOrReplace(summary)

            // Rolling 7-day baseline history
            val baselineDays = (1..7).map { offset ->
                val prevDate = date.minusDays(offset.toLong())
                val prevSummary = database.dailyHealthSummaryDao().getSummaryForDateSync(prevDate)
                DayMetrics(
                    date = prevDate,
                    sleepDurationMinutes = prevSummary?.sleepDurationMinutes,
                    restingHeartRate = prevSummary?.restingHeartRate,
                    hrvRmssd = prevSummary?.hrvRmssd,
                    trainingLoad = prevSummary?.dailyTrainingLoad,
                    sleepConsistencyPercentage = prevSummary?.sleepConsistencyScore?.roundToInt() ?: 85
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
                baselineHistory = baselineDays
            )

            val scoreEntity = RecoveryScoreEntity(
                date = date,
                score = recoveryResult.score,
                confidence = recoveryResult.confidence.name,
                state = recoveryResult.state.name,
                algorithmVersion = recoveryResult.algorithmVersion,
                baselineWindowDays = 7,
                hrvComponentScore = recoveryResult.hrvScore,
                rhrComponentScore = recoveryResult.rhrScore,
                sleepComponentScore = recoveryResult.sleepScore,
                trainingLoadComponentScore = recoveryResult.trainingLoadScore,
                positiveContributors = recoveryResult.positiveContributors.joinToString("|"),
                negativeContributors = recoveryResult.negativeContributors.joinToString("|"),
                supportiveAdvice = recoveryResult.supportiveAdvice,
                calculatedAt = Instant.now()
            )
            database.recoveryScoreDao().insertOrReplace(scoreEntity)
        }
    }
}
