// Medical records (Personal Health Record) APIs are experimental in connect-client 1.1.0.
@file:OptIn(androidx.health.connect.client.feature.ExperimentalPersonalHealthRecordApi::class)

package com.kevan.hangry.data.datasource

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.feature.ExperimentalMindfulnessSessionApi
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.kevan.hangry.data.healthrecords.FhirHealthRecordParser
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

        // Write-only - used solely to log breathing sessions the user completed in the app.
        const val PERMISSION_WRITE_MINDFULNESS = "android.permission.health.WRITE_MINDFULNESS"
        val PERMISSION_WRITE_EXERCISE = HealthPermission.getWritePermission(ExerciseSessionRecord::class)

        // Health records (read-only): home vitals plus clinical records, where supported.
        val PERMISSION_READ_BLOOD_PRESSURE = HealthPermission.getReadPermission(BloodPressureRecord::class)
        val PERMISSION_READ_BLOOD_GLUCOSE = HealthPermission.getReadPermission(BloodGlucoseRecord::class)
        val PERMISSION_READ_MENSTRUATION = HealthPermission.getReadPermission(MenstruationPeriodRecord::class)
        val PERMISSION_WRITE_BLOOD_PRESSURE = HealthPermission.getWritePermission(BloodPressureRecord::class)
        val PERMISSION_WRITE_BLOOD_GLUCOSE = HealthPermission.getWritePermission(BloodGlucoseRecord::class)
        val MEDICAL_RECORD_PERMISSIONS = setOf(
            HealthPermission.PERMISSION_READ_MEDICAL_DATA_LABORATORY_RESULTS,
            HealthPermission.PERMISSION_READ_MEDICAL_DATA_VITAL_SIGNS,
            HealthPermission.PERMISSION_READ_MEDICAL_DATA_CONDITIONS,
            HealthPermission.PERMISSION_READ_MEDICAL_DATA_ALLERGIES_INTOLERANCES
        )

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
            HealthPermission.getReadPermission(RespiratoryRateRecord::class),
            HealthPermission.getReadPermission(BloodPressureRecord::class),
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
                HealthPermission.getReadPermission(Vo2MaxRecord::class),
                HealthPermission.getReadPermission(RespiratoryRateRecord::class),
                HealthPermission.getReadPermission(BloodPressureRecord::class)
            ),
            "Nutrition & Hydration" to setOf(
                HealthPermission.getReadPermission(NutritionRecord::class),
                HealthPermission.getReadPermission(HydrationRecord::class)
            ),
            "Body Measurements" to setOf(
                HealthPermission.getReadPermission(WeightRecord::class),
                HealthPermission.getReadPermission(HeightRecord::class),
                HealthPermission.getReadPermission(BodyFatRecord::class)
            ),
            "Full History Access" to setOf(PERMISSION_READ_HEALTH_DATA_HISTORY)
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
        val records = readAllRecords(ExerciseSessionRecord::class, filter).filterNot {
            // Breathing sessions this app logged via the Guided Breathing fallback aren't training.
            it.exerciseType == ExerciseSessionRecord.EXERCISE_TYPE_GUIDED_BREATHING &&
                it.metadata.dataOrigin.packageName == context.packageName
        }
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
        val stepRecords = try {
            readAllRecords(StepsRecord::class, filter)
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

            // Same raw-record summing as the daily step total, pro-rated for records that only
            // partly overlap the session, so workout steps can be subtracted from it cleanly.
            val sessionSteps = stepRecords.sumOf { steps ->
                val overlapStart = maxOf(steps.startTime, record.startTime)
                val overlapEnd = minOf(steps.endTime, record.endTime)
                val recordMs = java.time.Duration.between(steps.startTime, steps.endTime).toMillis()
                val overlapMs = java.time.Duration.between(overlapStart, overlapEnd).toMillis()
                when {
                    overlapMs <= 0 -> 0.0
                    recordMs <= 0 -> steps.count.toDouble()
                    else -> steps.count * overlapMs.toDouble() / recordMs
                }
            }.toLong().takeIf { stepRecords.isNotEmpty() }

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
                steps = sessionSteps,
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
        val startInstant = start.atStartOfDay(ZoneId.systemDefault()).toInstant()
        val endInstant = end.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
        val records = try {
            readAllRecords(RespiratoryRateRecord::class, TimeRangeFilter.between(startInstant, endInstant))
        } catch (e: Exception) {
            Log.w("HangryHealthConnect", "Failed reading respiratory rates: ${e.message}")
            emptyList<RespiratoryRateRecord>()
        }
        return records.groupBy { it.time.atZone(ZoneId.systemDefault()).toLocalDate() }
            .mapValues { (_, dayRecords) ->
                dayRecords.map { it.rate }.average()
            }
    }

    override suspend fun fetchBloodPressure(start: LocalDate, end: LocalDate): Map<LocalDate, Pair<Double, Double>> {
        val startInstant = start.atStartOfDay(ZoneId.systemDefault()).toInstant()
        val endInstant = end.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
        val records = try {
            readAllRecords(BloodPressureRecord::class, TimeRangeFilter.between(startInstant, endInstant))
        } catch (e: Exception) {
            Log.w("HangryHealthConnect", "Failed reading blood pressure: ${e.message}")
            emptyList<BloodPressureRecord>()
        }
        return records.groupBy { it.time.atZone(ZoneId.systemDefault()).toLocalDate() }
            .mapValues { (_, dayRecords) ->
                val avgSystolic = dayRecords.map { it.systolic.inMillimetersOfMercury }.average()
                val avgDiastolic = dayRecords.map { it.diastolic.inMillimetersOfMercury }.average()
                Pair(avgSystolic, avgDiastolic)
            }
    }

    override suspend fun fetchBasalMetabolicRates(start: LocalDate, end: LocalDate): Map<LocalDate, Double> {
        val startInstant = start.atStartOfDay(ZoneId.systemDefault()).toInstant()
        val endInstant = end.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
        val records = try {
            readAllRecords(BasalMetabolicRateRecord::class, TimeRangeFilter.between(startInstant, endInstant))
        } catch (e: Exception) {
            Log.w("HangryHealthConnect", "Failed reading BMR records: ${e.message}")
            emptyList<BasalMetabolicRateRecord>()
        }
        return records.groupBy { it.time.atZone(ZoneId.systemDefault()).toLocalDate() }
            .mapValues { (_, dayRecords) ->
                dayRecords.map { it.basalMetabolicRate.inKilocaloriesPerDay }.average()
            }
    }

    override suspend fun fetchHydration(start: LocalDate, end: LocalDate): Map<LocalDate, Double> {
        val startInstant = start.atStartOfDay(ZoneId.systemDefault()).toInstant()
        val endInstant = end.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
        val records = try {
            readAllRecords(HydrationRecord::class, TimeRangeFilter.between(startInstant, endInstant))
        } catch (e: Exception) {
            Log.w("HangryHealthConnect", "Failed reading hydration records: ${e.message}")
            emptyList<HydrationRecord>()
        }
        return records.groupBy { it.startTime.atZone(ZoneId.systemDefault()).toLocalDate() }
            .mapValues { (_, dayRecords) ->
                dayRecords.sumOf { it.volume.inLiters }
            }
    }

    override suspend fun fetchBodyFat(start: Instant, end: Instant): Map<LocalDate, Double> {
        val records = try {
            readAllRecords(BodyFatRecord::class, TimeRangeFilter.between(start, end))
        } catch (e: Exception) {
            Log.w("HangryHealthConnect", "Failed reading body fat records: ${e.message}")
            emptyList<BodyFatRecord>()
        }
        return records.groupBy { it.time.atZone(ZoneId.systemDefault()).toLocalDate() }
            .mapValues { (_, dayRecords) ->
                dayRecords.map { it.percentage.value }.average()
            }
    }

    override suspend fun fetchNutritionRecords(start: Instant, end: Instant): List<FoodLogEntity> {
        val records = try {
            readAllRecords(NutritionRecord::class, TimeRangeFilter.between(start, end))
        } catch (e: Exception) {
            Log.w("HangryHealthConnect", "Failed reading external nutrition records: ${e.message}")
            emptyList<NutritionRecord>()
        }
        val appPackage = context.packageName
        return records
            // Never re-import entries written by Hangry itself to avoid duplication
            .filter { it.metadata.dataOrigin.packageName != appPackage }
            .map { record ->
                val date = record.startTime.atZone(ZoneId.systemDefault()).toLocalDate()
                val calories = record.energy?.inKilocalories?.toInt() ?: 0
                val title = record.name?.takeIf { it.isNotBlank() } ?: "Health Connect Meal"
                FoodLogEntity(
                    date = date,
                    timestamp = record.startTime,
                    source = FoodLogSource.HEALTH_CONNECT,
                    foodName = title,
                    calories = calories,
                    proteinG = record.protein?.inGrams ?: 0.0,
                    carbsG = record.totalCarbohydrate?.inGrams ?: 0.0,
                    fatG = record.totalFat?.inGrams ?: 0.0,
                    fiberG = record.dietaryFiber?.inGrams ?: 0.0,
                    sugarG = record.sugar?.inGrams ?: 0.0,
                    sodiumMg = (record.sodium?.inGrams ?: 0.0) * 1000.0,
                    healthConnectSynced = true,
                    sourceRecordId = record.metadata.id
                )
            }
    }

    override suspend fun findEarliestDataDate(): LocalDate? {
        val activeClient = client ?: return null
        val now = Instant.now()
        val filter = TimeRangeFilter.before(now)
        var earliestDate: LocalDate? = null

        val recordClasses = listOf(
            StepsRecord::class,
            SleepSessionRecord::class,
            ExerciseSessionRecord::class,
            HeartRateRecord::class,
            WeightRecord::class,
            NutritionRecord::class
        )

        for (recordClass in recordClasses) {
            try {
                val request = ReadRecordsRequest(
                    recordType = recordClass,
                    timeRangeFilter = filter,
                    ascendingOrder = true,
                    pageSize = 1
                )
                val response = activeClient.readRecords(request)
                val record = response.records.firstOrNull() ?: continue
                val recordDate = when (record) {
                    is StepsRecord -> record.startTime.atZone(ZoneId.systemDefault()).toLocalDate()
                    is SleepSessionRecord -> record.startTime.atZone(ZoneId.systemDefault()).toLocalDate()
                    is ExerciseSessionRecord -> record.startTime.atZone(ZoneId.systemDefault()).toLocalDate()
                    is HeartRateRecord -> record.startTime.atZone(ZoneId.systemDefault()).toLocalDate()
                    is WeightRecord -> record.time.atZone(ZoneId.systemDefault()).toLocalDate()
                    is NutritionRecord -> record.startTime.atZone(ZoneId.systemDefault()).toLocalDate()
                    else -> null
                }
                if (recordDate != null) {
                    if (earliestDate == null || recordDate.isBefore(earliestDate)) {
                        earliestDate = recordDate
                    }
                }
            } catch (e: Exception) {
                Log.d("HangryHealthConnect", "Earliest date probe notice for ${recordClass.simpleName}: ${e.message}")
            }
        }
        return earliestDate
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

    @OptIn(ExperimentalMindfulnessSessionApi::class)
    private fun supportsMindfulness(activeClient: HealthConnectClient): Boolean = try {
        activeClient.features.getFeatureStatus(HealthConnectFeatures.FEATURE_MINDFULNESS_SESSION) ==
            HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
    } catch (_: Exception) {
        false
    }

    override suspend fun breathingWritePermissions(): Set<String> {
        val activeClient = client ?: return emptySet()
        return if (supportsMindfulness(activeClient)) {
            setOf(PERMISSION_WRITE_MINDFULNESS)
        } else {
            setOf(PERMISSION_WRITE_EXERCISE)
        }
    }

    override suspend fun hasBreathingWritePermissions(): Boolean {
        val required = breathingWritePermissions()
        return required.isNotEmpty() && getGrantedPermissions().containsAll(required)
    }

    @OptIn(ExperimentalMindfulnessSessionApi::class)
    override suspend fun writeBreathingSession(session: BreathingSessionEntity, title: String): Boolean {
        val activeClient = client ?: return false
        return try {
            val zone = ZoneId.systemDefault().rules
            val startOffset = zone.getOffset(session.startTime)
            val endOffset = zone.getOffset(session.endTime)
            // Stable client id makes a retry of the same session an upsert, not a duplicate.
            val metadata = androidx.health.connect.client.records.metadata.Metadata.manualEntry(
                clientRecordId = "hangry-breathing-${session.id}"
            )
            val granted = getGrantedPermissions()
            val record: Record = when {
                supportsMindfulness(activeClient) && PERMISSION_WRITE_MINDFULNESS in granted ->
                    MindfulnessSessionRecord(
                        startTime = session.startTime,
                        startZoneOffset = startOffset,
                        endTime = session.endTime,
                        endZoneOffset = endOffset,
                        metadata = metadata,
                        mindfulnessSessionType = MindfulnessSessionRecord.MINDFULNESS_SESSION_TYPE_BREATHING,
                        title = title
                    )
                PERMISSION_WRITE_EXERCISE in granted ->
                    ExerciseSessionRecord(
                        startTime = session.startTime,
                        startZoneOffset = startOffset,
                        endTime = session.endTime,
                        endZoneOffset = endOffset,
                        metadata = metadata,
                        exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_GUIDED_BREATHING,
                        title = title
                    )
                else -> return false
            }
            activeClient.insertRecords(listOf(record))
            true
        } catch (e: Exception) {
            Log.w("HangryHealthConnect", "Failed writing breathing session: ${e.message}")
            false
        }
    }

    override suspend fun grantedPermissions(): Set<String> = runCatching { getGrantedPermissions() }.getOrDefault(emptySet())

    override suspend fun supportsMedicalRecords(): Boolean {
        val activeClient = client ?: return false
        return try {
            activeClient.features.getFeatureStatus(HealthConnectFeatures.FEATURE_PERSONAL_HEALTH_RECORD) ==
                HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun healthRecordPermissions(includeCycle: Boolean): Set<String> {
        if (client == null) return emptySet()
        return buildSet {
            add(PERMISSION_READ_BLOOD_PRESSURE)
            add(PERMISSION_READ_BLOOD_GLUCOSE)
            // So readings entered in Hangry show up in other health apps too.
            add(PERMISSION_WRITE_BLOOD_PRESSURE)
            add(PERMISSION_WRITE_BLOOD_GLUCOSE)
            if (includeCycle) add(PERMISSION_READ_MENSTRUATION)
            if (supportsMedicalRecords()) {
                addAll(MEDICAL_RECORD_PERMISSIONS)
                if (includeCycle) add(HealthPermission.PERMISSION_READ_MEDICAL_DATA_PREGNANCY)
            }
        }
    }

    private fun markerClientId(marker: HealthMarkerEntity) = "hangry-marker-${marker.id}"

    override suspend fun writeHealthMarker(marker: HealthMarkerEntity): Boolean {
        val activeClient = client ?: return false
        val offset = ZoneId.systemDefault().rules.getOffset(marker.measuredAt)
        // Stable client id: a retry replaces rather than duplicates, and delete can find it.
        val metadata = androidx.health.connect.client.records.metadata.Metadata.manualEntry(clientRecordId = markerClientId(marker))
        val record: Record = when (marker.type) {
            com.kevan.hangry.domain.model.MarkerType.BLOOD_PRESSURE.id -> BloodPressureRecord(
                time = marker.measuredAt,
                zoneOffset = offset,
                metadata = metadata,
                systolic = androidx.health.connect.client.units.Pressure.millimetersOfMercury(marker.value),
                diastolic = androidx.health.connect.client.units.Pressure.millimetersOfMercury(marker.secondaryValue ?: return false),
                bodyPosition = BloodPressureRecord.BODY_POSITION_UNKNOWN,
                measurementLocation = BloodPressureRecord.MEASUREMENT_LOCATION_UNKNOWN
            )
            com.kevan.hangry.domain.model.MarkerType.BLOOD_GLUCOSE.id -> BloodGlucoseRecord(
                time = marker.measuredAt,
                zoneOffset = offset,
                metadata = metadata,
                level = androidx.health.connect.client.units.BloodGlucose.milligramsPerDeciliter(marker.value),
                specimenSource = BloodGlucoseRecord.SPECIMEN_SOURCE_UNKNOWN,
                mealType = MealType.MEAL_TYPE_UNKNOWN,
                relationToMeal = when (com.kevan.hangry.domain.model.GlucoseContext.fromName(marker.glucoseContext)) {
                    com.kevan.hangry.domain.model.GlucoseContext.FASTING -> BloodGlucoseRecord.RELATION_TO_MEAL_FASTING
                    com.kevan.hangry.domain.model.GlucoseContext.BEFORE_MEAL -> BloodGlucoseRecord.RELATION_TO_MEAL_BEFORE_MEAL
                    com.kevan.hangry.domain.model.GlucoseContext.AFTER_MEAL -> BloodGlucoseRecord.RELATION_TO_MEAL_AFTER_MEAL
                    else -> BloodGlucoseRecord.RELATION_TO_MEAL_GENERAL
                }
            )
            else -> return false
        }
        return try {
            activeClient.insertRecords(listOf(record))
            true
        } catch (e: Exception) {
            Log.w("HangryHealthConnect", "Failed writing ${marker.type}: ${e.message}")
            false
        }
    }

    override suspend fun deleteHealthMarker(marker: HealthMarkerEntity): Boolean {
        val activeClient = client ?: return false
        val type = when (marker.type) {
            com.kevan.hangry.domain.model.MarkerType.BLOOD_PRESSURE.id -> BloodPressureRecord::class
            com.kevan.hangry.domain.model.MarkerType.BLOOD_GLUCOSE.id -> BloodGlucoseRecord::class
            else -> return false
        }
        return try {
            activeClient.deleteRecords(type, recordIdsList = emptyList(), clientRecordIdsList = listOf(markerClientId(marker)))
            true
        } catch (e: Exception) {
            Log.w("HangryHealthConnect", "Failed deleting ${marker.type}: ${e.message}")
            false
        }
    }

    override suspend fun fetchHealthRecords(since: Instant, includeCycle: Boolean): ImportedHealthRecords {
        if (client == null) return ImportedHealthRecords()
        val granted = grantedPermissions()
        val zone = ZoneId.systemDefault()
        val filter = TimeRangeFilter.between(since, Instant.now())
        val markers = mutableListOf<HealthMarkerEntity>()
        val items = mutableListOf<HealthProfileItemEntity>()
        val periods = mutableListOf<MenstrualPeriodEntity>()
        val pregnancy = mutableListOf<FhirHealthRecordParser.PregnancyInfo>()

        if (PERMISSION_READ_BLOOD_PRESSURE in granted) {
            runCatching { readAllRecords(BloodPressureRecord::class, filter) }.getOrDefault(emptyList())
                .filterNot { it.metadata.dataOrigin.packageName == context.packageName } // our own writes
                .forEach { r ->
                markers += HealthMarkerEntity(
                    type = com.kevan.hangry.domain.model.MarkerType.BLOOD_PRESSURE.id,
                    value = r.systolic.inMillimetersOfMercury,
                    secondaryValue = r.diastolic.inMillimetersOfMercury,
                    measuredAt = r.time,
                    date = r.time.atZone(zone).toLocalDate(),
                    source = com.kevan.hangry.domain.model.RecordSource.HEALTH_CONNECT.name,
                    sourceRecordId = "hc:${r.metadata.id}"
                )
            }
        }
        if (PERMISSION_READ_BLOOD_GLUCOSE in granted) {
            runCatching { readAllRecords(BloodGlucoseRecord::class, filter) }.getOrDefault(emptyList())
                .filterNot { it.metadata.dataOrigin.packageName == context.packageName } // our own writes
                .forEach { r ->
                val context = when (r.relationToMeal) {
                    BloodGlucoseRecord.RELATION_TO_MEAL_FASTING -> com.kevan.hangry.domain.model.GlucoseContext.FASTING
                    BloodGlucoseRecord.RELATION_TO_MEAL_BEFORE_MEAL -> com.kevan.hangry.domain.model.GlucoseContext.BEFORE_MEAL
                    BloodGlucoseRecord.RELATION_TO_MEAL_AFTER_MEAL -> com.kevan.hangry.domain.model.GlucoseContext.AFTER_MEAL
                    else -> com.kevan.hangry.domain.model.GlucoseContext.RANDOM
                }
                markers += HealthMarkerEntity(
                    type = com.kevan.hangry.domain.model.MarkerType.BLOOD_GLUCOSE.id,
                    value = r.level.inMilligramsPerDeciliter,
                    measuredAt = r.time,
                    date = r.time.atZone(zone).toLocalDate(),
                    glucoseContext = context.name,
                    source = com.kevan.hangry.domain.model.RecordSource.HEALTH_CONNECT.name,
                    sourceRecordId = "hc:${r.metadata.id}"
                )
            }
        }
        if (includeCycle && PERMISSION_READ_MENSTRUATION in granted) {
            runCatching { readAllRecords(MenstruationPeriodRecord::class, filter) }.getOrDefault(emptyList()).forEach { r ->
                periods += MenstrualPeriodEntity(
                    startDate = r.startTime.atZone(zone).toLocalDate(),
                    // HC end is exclusive; the last period day is the day before.
                    endDate = r.endTime.minusMillis(1).atZone(zone).toLocalDate(),
                    source = com.kevan.hangry.domain.model.RecordSource.HEALTH_CONNECT.name,
                    sourceRecordId = "hc:${r.metadata.id}"
                )
            }
        }
        if (supportsMedicalRecords() && granted.any { it in MEDICAL_RECORD_PERMISSIONS }) {
            readMedical(MedicalResource.MEDICAL_RESOURCE_TYPE_LABORATORY_RESULTS, granted).forEach { res ->
                FhirHealthRecordParser.parseObservation(res.fhirResource.data, res.fhirResource.id, zone)?.let { markers += it }
            }
            readMedical(MedicalResource.MEDICAL_RESOURCE_TYPE_VITAL_SIGNS, granted).forEach { res ->
                FhirHealthRecordParser.parseObservation(res.fhirResource.data, res.fhirResource.id, zone)
                    ?.takeIf { it.type == com.kevan.hangry.domain.model.MarkerType.BLOOD_PRESSURE.id }
                    ?.let { markers += it }
            }
            readMedical(MedicalResource.MEDICAL_RESOURCE_TYPE_CONDITIONS, granted).forEach { res ->
                FhirHealthRecordParser.parseCondition(res.fhirResource.data, res.fhirResource.id)?.let { items += it }
            }
            readMedical(MedicalResource.MEDICAL_RESOURCE_TYPE_ALLERGIES_INTOLERANCES, granted).forEach { res ->
                FhirHealthRecordParser.parseAllergy(res.fhirResource.data, res.fhirResource.id)?.let { items += it }
            }
            if (includeCycle) {
                readMedical(MedicalResource.MEDICAL_RESOURCE_TYPE_PREGNANCY, granted).forEach { res ->
                    FhirHealthRecordParser.parsePregnancy(res.fhirResource.data, zone)?.let { pregnancy += it }
                }
            }
        }
        return ImportedHealthRecords(markers, items, periods, pregnancy)
    }

    private suspend fun readMedical(type: Int, granted: Set<String>): List<MedicalResource> {
        val needed = when (type) {
            MedicalResource.MEDICAL_RESOURCE_TYPE_LABORATORY_RESULTS -> HealthPermission.PERMISSION_READ_MEDICAL_DATA_LABORATORY_RESULTS
            MedicalResource.MEDICAL_RESOURCE_TYPE_VITAL_SIGNS -> HealthPermission.PERMISSION_READ_MEDICAL_DATA_VITAL_SIGNS
            MedicalResource.MEDICAL_RESOURCE_TYPE_CONDITIONS -> HealthPermission.PERMISSION_READ_MEDICAL_DATA_CONDITIONS
            MedicalResource.MEDICAL_RESOURCE_TYPE_PREGNANCY -> HealthPermission.PERMISSION_READ_MEDICAL_DATA_PREGNANCY
            else -> HealthPermission.PERMISSION_READ_MEDICAL_DATA_ALLERGIES_INTOLERANCES
        }
        if (needed !in granted) return emptyList()
        val activeClient = client ?: return emptyList()
        val all = mutableListOf<MedicalResource>()
        try {
            var response = activeClient.readMedicalResources(
                androidx.health.connect.client.request.ReadMedicalResourcesInitialRequest(type, emptySet(), 1000)
            )
            all += response.medicalResources
            while (response.nextPageToken != null) {
                response = activeClient.readMedicalResources(
                    androidx.health.connect.client.request.ReadMedicalResourcesPageRequest(response.nextPageToken!!, 1000)
                )
                all += response.medicalResources
            }
        } catch (e: Exception) {
            Log.w("HangryHealthConnect", "Failed reading medical records ($type): ${e.message}")
        }
        return all
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
