package com.kevan.hangry.data.datasource

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.kevan.hangry.data.local.entity.*
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.reflect.KClass

class RealHealthConnectDataSource(
    private val context: Context
) : HealthConnectDataSource {

    private val client: HealthConnectClient? by lazy {
        if (HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE) {
            HealthConnectClient.getOrCreate(context)
        } else {
            null
        }
    }

    companion object {
        const val PERMISSION_READ_HEALTH_DATA_HISTORY = "android.permission.health.READ_HEALTH_DATA_HISTORY"
        const val PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND = "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"

        val PERMISSIONS = setOf(
            PERMISSION_READ_HEALTH_DATA_HISTORY,
            PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND,
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(DistanceRecord::class),
            HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(ExerciseSessionRecord::class),
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(RestingHeartRateRecord::class),
            HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
            HealthPermission.getReadPermission(WeightRecord::class),
            HealthPermission.getReadPermission(HeightRecord::class),
            HealthPermission.getReadPermission(OxygenSaturationRecord::class),
            HealthPermission.getReadPermission(Vo2MaxRecord::class),
            HealthPermission.getReadPermission(SpeedRecord::class),
            HealthPermission.getReadPermission(ElevationGainedRecord::class),
            HealthPermission.getReadPermission(FloorsClimbedRecord::class),
            HealthPermission.getReadPermission(BasalMetabolicRateRecord::class),
            HealthPermission.getReadPermission(HydrationRecord::class),
            HealthPermission.getReadPermission(NutritionRecord::class),
            HealthPermission.getReadPermission(BodyTemperatureRecord::class),
            HealthPermission.getReadPermission(BodyFatRecord::class),
            // Write-only - used solely by the opt-in AI calorie tracker to log a food entry
            // the user explicitly saved.
            HealthPermission.getWritePermission(NutritionRecord::class)
        )

        val CORE_PERMISSIONS = setOf(
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(ExerciseSessionRecord::class),
            HealthPermission.getReadPermission(HeartRateRecord::class)
        )

        // Per-category permission groups so the UI can show which specific categories are
        // granted vs pending, rather than one aggregate boolean (see HEALTH_CONNECT.md §4.3).
        val PERMISSION_CATEGORIES: Map<String, Set<String>> = linkedMapOf(
            "Sleep Sessions" to setOf(HealthPermission.getReadPermission(SleepSessionRecord::class)),
            "Resting Heart Rate" to setOf(HealthPermission.getReadPermission(RestingHeartRateRecord::class)),
            "Heart Rate Variability (HRV RMSSD)" to setOf(HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class)),
            "Workouts & Exercise" to setOf(HealthPermission.getReadPermission(ExerciseSessionRecord::class)),
            "Steps & Distance" to setOf(
                HealthPermission.getReadPermission(StepsRecord::class),
                HealthPermission.getReadPermission(DistanceRecord::class)
            ),
            "Vitals & Cardio Fitness" to setOf(
                HealthPermission.getReadPermission(OxygenSaturationRecord::class),
                HealthPermission.getReadPermission(Vo2MaxRecord::class)
            )
        )
    }

    override suspend fun isAvailable(): Boolean {
        return HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
    }

    override suspend fun hasPermissions(): Boolean {
        val activeClient = client ?: return false
        val granted = activeClient.permissionController.getGrantedPermissions()
        return granted.containsAll(CORE_PERMISSIONS) || (granted.isNotEmpty() && granted.contains(HealthPermission.getReadPermission(StepsRecord::class)))
    }

    suspend fun getGrantedPermissions(): Set<String> {
        val activeClient = client ?: return emptySet()
        return activeClient.permissionController.getGrantedPermissions()
    }

    /**
     * Per-category grant state, e.g. {"Sleep Sessions": true, "Heart Rate Variability (HRV RMSSD)": false},
     * so the permission setup screen can reflect a partial grant instead of one aggregate flag.
     */
    suspend fun getPermissionCategoryStatus(): Map<String, Boolean> {
        val granted = getGrantedPermissions()
        return PERMISSION_CATEGORIES.mapValues { (_, required) -> granted.containsAll(required) }
    }

    /**
     * Generic paginated record reader. Any failure - permission revocation or otherwise - is
     * rethrown rather than swallowed: a partial page read silently returned as "success" would
     * let the sync pipeline persist scores computed from incomplete data with no indication
     * anything was wrong. The sync manager's own try/catch turns this into a surfaced FAILED
     * status instead.
     */
    private suspend fun <T : Record> readAllRecords(
        recordType: KClass<T>,
        timeRangeFilter: TimeRangeFilter,
        pageSize: Int = 1000
    ): List<T> {
        val activeClient = client ?: return emptyList()
        val allRecords = mutableListOf<T>()
        var pageToken: String? = null
        try {
            do {
                val request = ReadRecordsRequest(
                    recordType = recordType,
                    timeRangeFilter = timeRangeFilter,
                    pageSize = pageSize,
                    pageToken = pageToken
                )
                val response = activeClient.readRecords(request)
                allRecords.addAll(response.records)
                pageToken = response.pageToken
            } while (!pageToken.isNullOrEmpty())
        } catch (e: SecurityException) {
            throw e
        } catch (e: Exception) {
            Log.w("HangryHealthConnect", "Failed reading ${recordType.simpleName}: ${e.message}")
            throw e
        }
        return allRecords
    }

    override suspend fun fetchSleepSessions(start: Instant, end: Instant): List<SleepSessionEntity> {
        val records = readAllRecords(SleepSessionRecord::class, TimeRangeFilter.between(start, end))
        return records.map { record ->
            val durationMinutes = java.time.Duration.between(record.startTime, record.endTime).toMinutes().toInt()
            val pkg = record.metadata.dataOrigin.packageName
            val recordId = record.metadata.id
            val fingerprint = sha256("SLEEP|$pkg|$recordId|${record.startTime.toEpochMilli()}")

            var deepMin: Int? = null
            var remMin: Int? = null
            var lightMin: Int? = null
            var awakeMin: Int? = null

            if (record.stages.isNotEmpty()) {
                var d = 0; var r = 0; var l = 0; var a = 0
                for (stage in record.stages) {
                    val m = java.time.Duration.between(stage.startTime, stage.endTime).toMinutes().toInt()
                    when (stage.stage) {
                        SleepSessionRecord.STAGE_TYPE_DEEP -> d += m
                        SleepSessionRecord.STAGE_TYPE_REM -> r += m
                        SleepSessionRecord.STAGE_TYPE_LIGHT -> l += m
                        SleepSessionRecord.STAGE_TYPE_AWAKE,
                        SleepSessionRecord.STAGE_TYPE_OUT_OF_BED,
                        SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED -> a += m
                    }
                }
                deepMin = d; remMin = r; lightMin = l; awakeMin = a
            }

            SleepSessionEntity(
                sourceRecordId = recordId,
                sourcePackageName = pkg,
                recordFingerprint = fingerprint,
                startTime = record.startTime,
                endTime = record.endTime,
                durationMinutes = durationMinutes,
                timeInBedMinutes = durationMinutes,
                deepSleepMinutes = deepMin,
                remSleepMinutes = remMin,
                lightSleepMinutes = lightMin,
                awakeMinutes = awakeMin,
                timeZoneOffset = record.startZoneOffset?.toString(),
                isManualEntry = false,
                dataQualityState = "VALID"
            )
        }
    }

    override suspend fun fetchExerciseSessions(start: Instant, end: Instant): List<ExerciseSessionEntity> {
        val filter = TimeRangeFilter.between(start, end)
        val records = readAllRecords(ExerciseSessionRecord::class, filter)
        val activeCalRecords = try {
            readAllRecords(ActiveCaloriesBurnedRecord::class, filter)
        } catch (_: Exception) {
            emptyList()
        }
        val totalCalRecords = try {
            readAllRecords(TotalCaloriesBurnedRecord::class, filter)
        } catch (_: Exception) {
            emptyList()
        }

        return records.map { record ->
            val durationMinutes = java.time.Duration.between(record.startTime, record.endTime).toMinutes().toInt()
            val pkg = record.metadata.dataOrigin.packageName
            val recordId = record.metadata.id
            val fingerprint = sha256("EXERCISE|$pkg|$recordId|${record.startTime.toEpochMilli()}")

            val matchingActiveCals = activeCalRecords.filter {
                !it.startTime.isBefore(record.startTime) && !it.endTime.isAfter(record.endTime)
            }
            val activeCalories = if (matchingActiveCals.isNotEmpty()) {
                matchingActiveCals.sumOf { it.energy.inKilocalories }
            } else null

            val matchingTotalCals = totalCalRecords.filter {
                !it.startTime.isBefore(record.startTime) && !it.endTime.isAfter(record.endTime)
            }
            val totalCalories = if (matchingTotalCals.isNotEmpty()) {
                matchingTotalCals.sumOf { it.energy.inKilocalories }
            } else null

            ExerciseSessionEntity(
                sourceRecordId = recordId,
                sourcePackageName = pkg,
                recordFingerprint = fingerprint,
                exerciseType = mapExerciseType(record.exerciseType),
                title = record.title,
                startTime = record.startTime,
                endTime = record.endTime,
                durationMinutes = durationMinutes,
                activeCalories = activeCalories,
                totalCalories = totalCalories,
                estimatedTrainingLoad = null,
                dataQualityState = "VALID"
            )
        }
    }

    override suspend fun fetchRestingHeartRates(start: LocalDate, end: LocalDate): List<RestingHeartRateEntity> {
        val startInstant = start.atStartOfDay(ZoneId.systemDefault()).toInstant()
        val endInstant = end.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
        val records = readAllRecords(RestingHeartRateRecord::class, TimeRangeFilter.between(startInstant, endInstant))
        return records.map { record ->
            val pkg = record.metadata.dataOrigin.packageName
            val recordId = record.metadata.id
            val date = record.time.atZone(ZoneId.systemDefault()).toLocalDate()
            val fingerprint = sha256("RHR|$pkg|$recordId|${date.toEpochDay()}")
            RestingHeartRateEntity(
                sourceRecordId = recordId,
                sourcePackageName = pkg,
                recordFingerprint = fingerprint,
                recordDate = date,
                timestamp = record.time,
                restingBpm = record.beatsPerMinute.toDouble(),
                dataQualityState = "VALID"
            )
        }
    }

    override suspend fun fetchHrvMeasurements(start: LocalDate, end: LocalDate): List<HrvMeasurementEntity> {
        val startInstant = start.atStartOfDay(ZoneId.systemDefault()).toInstant()
        val endInstant = end.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
        val records = readAllRecords(HeartRateVariabilityRmssdRecord::class, TimeRangeFilter.between(startInstant, endInstant))
        return records.map { record ->
            val pkg = record.metadata.dataOrigin.packageName
            val recordId = record.metadata.id
            val date = record.time.atZone(ZoneId.systemDefault()).toLocalDate()
            val fingerprint = sha256("HRV|$pkg|$recordId|${date.toEpochDay()}")
            HrvMeasurementEntity(
                sourceRecordId = recordId,
                sourcePackageName = pkg,
                recordFingerprint = fingerprint,
                recordDate = date,
                timestamp = record.time,
                rmssd = record.heartRateVariabilityMillis,
                dataQualityState = "VALID"
            )
        }
    }

    override suspend fun fetchStepsSummaries(start: LocalDate, end: LocalDate): List<StepsSummaryEntity> {
        val startInstant = start.atStartOfDay(ZoneId.systemDefault()).toInstant()
        val endInstant = end.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
        val filter = TimeRangeFilter.between(startInstant, endInstant)

        // 1. Steps
        val stepRecords = readAllRecords(StepsRecord::class, filter)
        val stepsByDate = stepRecords.groupBy { it.startTime.atZone(ZoneId.systemDefault()).toLocalDate() }

        // 2. Real Distance
        val distanceRecords = readAllRecords(DistanceRecord::class, filter)
        val distanceByDate = distanceRecords.groupBy { it.startTime.atZone(ZoneId.systemDefault()).toLocalDate() }

        // 3. Real Active Calories
        val activeCalRecords = readAllRecords(ActiveCaloriesBurnedRecord::class, filter)
        val activeCalByDate = activeCalRecords.groupBy { it.startTime.atZone(ZoneId.systemDefault()).toLocalDate() }

        val allDates = (stepsByDate.keys + distanceByDate.keys + activeCalByDate.keys).distinct()

        return allDates.map { date ->
            val dateSteps = stepsByDate[date] ?: emptyList()
            val totalSteps = dateSteps.sumOf { it.count }

            val dateDistances = distanceByDate[date] ?: emptyList()
            val totalDistanceMeters = if (dateDistances.isNotEmpty()) {
                dateDistances.sumOf { it.distance.inMeters }
            } else {
                null
            }

            val dateCalories = activeCalByDate[date] ?: emptyList()
            val totalActiveCal = if (dateCalories.isNotEmpty()) {
                dateCalories.sumOf { it.energy.inKilocalories }
            } else {
                null
            }

            val pkg = dateSteps.firstOrNull()?.metadata?.dataOrigin?.packageName
                ?: dateDistances.firstOrNull()?.metadata?.dataOrigin?.packageName
                ?: dateCalories.firstOrNull()?.metadata?.dataOrigin?.packageName
                ?: "com.google.android.health"
            val fingerprint = sha256("STEPS|$pkg|${date.toEpochDay()}|$totalSteps|${totalDistanceMeters?.toInt() ?: 0}")

            StepsSummaryEntity(
                sourceRecordId = "steps-$date",
                sourcePackageName = pkg,
                recordFingerprint = fingerprint,
                recordDate = date,
                stepCount = totalSteps,
                distanceMeters = totalDistanceMeters,
                activeCalories = totalActiveCal,
                dataQualityState = "VALID"
            )
        }
    }

    override suspend fun fetchHeartRateSamples(start: Instant, end: Instant): List<HeartRateSampleEntity> {
        val records = readAllRecords(HeartRateRecord::class, TimeRangeFilter.between(start, end))
        return records.flatMap { record ->
            val pkg = record.metadata.dataOrigin.packageName
            val recordId = record.metadata.id
            record.samples.map { sample ->
                val fingerprint = sha256("HR_SAMPLE|$pkg|$recordId|${sample.time.toEpochMilli()}")
                HeartRateSampleEntity(
                    sourceRecordId = recordId,
                    sourcePackageName = pkg,
                    recordFingerprint = fingerprint,
                    timestamp = sample.time,
                    bpm = sample.beatsPerMinute.toDouble(),
                    dataQualityState = "VALID"
                )
            }
        }
    }

    override suspend fun fetchWeightMeasurements(start: Instant, end: Instant): List<WeightMeasurementEntity> {
        val records = readAllRecords(WeightRecord::class, TimeRangeFilter.between(start, end))
        return records.map { record ->
            val pkg = record.metadata.dataOrigin.packageName
            val recordId = record.metadata.id
            val fingerprint = sha256("WEIGHT|$pkg|$recordId|${record.time.toEpochMilli()}")
            WeightMeasurementEntity(
                sourceRecordId = recordId,
                sourcePackageName = pkg,
                recordFingerprint = fingerprint,
                timestamp = record.time,
                weightKg = record.weight.inKilograms,
                dataQualityState = "VALID"
            )
        }
    }

    override suspend fun fetchHeightMeasurements(start: Instant, end: Instant): List<HeightMeasurementEntity> {
        val records = readAllRecords(HeightRecord::class, TimeRangeFilter.between(start, end))
        return records.map { record ->
            val pkg = record.metadata.dataOrigin.packageName
            val recordId = record.metadata.id
            val fingerprint = sha256("HEIGHT|$pkg|$recordId|${record.time.toEpochMilli()}")
            HeightMeasurementEntity(
                sourceRecordId = recordId,
                sourcePackageName = pkg,
                recordFingerprint = fingerprint,
                timestamp = record.time,
                heightCm = record.height.inMeters * 100.0,
                dataQualityState = "VALID"
            )
        }
    }

    override suspend fun fetchVo2Max(start: LocalDate, end: LocalDate): Map<LocalDate, Double> {
        val startInstant = start.atStartOfDay(ZoneId.systemDefault()).toInstant()
        val endInstant = end.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
        val records = readAllRecords(Vo2MaxRecord::class, TimeRangeFilter.between(startInstant, endInstant))
        return records.groupBy { it.time.atZone(ZoneId.systemDefault()).toLocalDate() }
            .mapValues { (_, dayRecords) ->
                dayRecords.map { it.vo2MillilitersPerMinuteKilogram }.average()
            }
    }

    override suspend fun fetchOxygenSaturation(start: LocalDate, end: LocalDate): Map<LocalDate, Double> {
        val startInstant = start.atStartOfDay(ZoneId.systemDefault()).toInstant()
        val endInstant = end.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
        val records = readAllRecords(OxygenSaturationRecord::class, TimeRangeFilter.between(startInstant, endInstant))
        return records.groupBy { it.time.atZone(ZoneId.systemDefault()).toLocalDate() }
            .mapValues { (_, dayRecords) ->
                dayRecords.map { it.percentage.value }.average()
            }
    }

    override suspend fun fetchRespiratoryRate(start: LocalDate, end: LocalDate): Map<LocalDate, Double> {
        return emptyMap()
    }

    override suspend fun fetchBloodPressure(start: LocalDate, end: LocalDate): Map<LocalDate, Pair<Double, Double>> {
        return emptyMap()
    }

    override suspend fun writeNutritionRecord(entry: FoodLogEntity): Boolean {
        val activeClient = client ?: return false
        return try {
            val startInstant = entry.timestamp.minusSeconds(60)
            activeClient.insertRecords(
                listOf(
                    NutritionRecord(
                        startTime = startInstant,
                        startZoneOffset = null,
                        endTime = entry.timestamp,
                        endZoneOffset = null,
                        metadata = androidx.health.connect.client.records.metadata.Metadata.manualEntry(),
                        name = entry.foodName,
                        energy = androidx.health.connect.client.units.Energy.kilocalories(entry.calories.toDouble()),
                        protein = androidx.health.connect.client.units.Mass.grams(entry.proteinG),
                        totalCarbohydrate = androidx.health.connect.client.units.Mass.grams(entry.carbsG),
                        totalFat = androidx.health.connect.client.units.Mass.grams(entry.fatG),
                        dietaryFiber = androidx.health.connect.client.units.Mass.grams(entry.fiberG),
                        sugar = androidx.health.connect.client.units.Mass.grams(entry.sugarG),
                        sodium = androidx.health.connect.client.units.Mass.grams(entry.sodiumMg / 1000.0)
                    )
                )
            )
            true
        } catch (e: Exception) {
            Log.w("HangryHealthConnect", "Failed writing nutrition record: ${e.message}")
            false
        }
    }

    private fun mapExerciseType(type: Int): String = when (type) {
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> "RUNNING"
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> "CYCLING"
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> "SWIMMING"
        ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "WALKING"
        ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING -> "STRENGTH_TRAINING"
        ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING -> "HIIT"
        ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> "YOGA"
        else -> "OTHER"
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
