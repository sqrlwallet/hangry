package com.kevan.hangry.data.datasource

import com.kevan.hangry.data.local.entity.*
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class FakeHealthConnectDataSource(
    private val daysOfData: Int = 14
) : HealthConnectDataSource {

    private val sourcePackage = "com.google.android.apps.fitness"

    override suspend fun isAvailable(): Boolean = true
    override suspend fun hasPermissions(): Boolean = true

    override suspend fun fetchSleepSessions(start: Instant, end: Instant): List<SleepSessionEntity> {
        val list = mutableListOf<SleepSessionEntity>()
        val today = LocalDate.now(ZoneId.systemDefault())

        for (i in 0 until daysOfData) {
            val date = today.minusDays(i.toLong())
            // Sleep starts evening before: 23:00 to 07:00
            val sleepDurationMinutes = when (i) {
                1 -> 495 // 8h 15m (Great sleep)
                2 -> 470 // ~7h 50m
                3 -> 480 // 8h
                5 -> 340 // Short sleep on day -5 (5h 40m)
                7 -> 510 // 8h 30m
                else -> 460 + ((i * 17) % 50)
            }

            val bedTime = date.minusDays(1).atTime(23, 0).atZone(ZoneId.systemDefault()).toInstant()
            val wakeTime = bedTime.plusSeconds(sleepDurationMinutes.toLong() * 60)

            if (wakeTime.isAfter(start) && bedTime.isBefore(end)) {
                val recordId = "fake-sleep-$date"
                val fingerprint = sha256("SLEEP|$sourcePackage|$recordId|${bedTime.toEpochMilli()}")
                list.add(
                    SleepSessionEntity(
                        sourceRecordId = recordId,
                        sourcePackageName = sourcePackage,
                        recordFingerprint = fingerprint,
                        startTime = bedTime,
                        endTime = wakeTime,
                        durationMinutes = sleepDurationMinutes,
                        timeInBedMinutes = sleepDurationMinutes + 25,
                        sleepQualityScore = 85,
                        isManualEntry = false,
                        timeZoneOffset = "+00:00"
                    )
                )
            }
        }
        return list
    }

    override suspend fun fetchExerciseSessions(start: Instant, end: Instant): List<ExerciseSessionEntity> {
        val list = mutableListOf<ExerciseSessionEntity>()
        val today = LocalDate.now(ZoneId.systemDefault())

        for (i in 0 until daysOfData) {
            val date = today.minusDays(i.toLong())
            // Workout on alternating days
            if (i % 2 == 0) {
                val workoutStart = date.atTime(17, 30).atZone(ZoneId.systemDefault()).toInstant()
                val duration = 45 + ((i * 7) % 25)
                val workoutEnd = workoutStart.plusSeconds(duration.toLong() * 60)
                val type = if (i % 4 == 0) "RUNNING" else "STRENGTH_TRAINING"

                if (workoutEnd.isAfter(start) && workoutStart.isBefore(end)) {
                    val recordId = "fake-workout-$date-1"
                    val fingerprint = sha256("EXERCISE|$sourcePackage|$recordId|${workoutStart.toEpochMilli()}")
                    list.add(
                        ExerciseSessionEntity(
                            sourceRecordId = recordId,
                            sourcePackageName = sourcePackage,
                            recordFingerprint = fingerprint,
                            exerciseType = type,
                            title = if (type == "RUNNING") "Evening Trail Run" else "Full Body Strength",
                            startTime = workoutStart,
                            endTime = workoutEnd,
                            durationMinutes = duration,
                            activeCalories = (duration * 8.5),
                            totalCalories = (duration * 10.0),
                            estimatedTrainingLoad = (duration * 1.3)
                        )
                    )
                }
            }
        }
        return list
    }

    override suspend fun fetchRestingHeartRates(start: LocalDate, end: LocalDate): List<RestingHeartRateEntity> {
        val list = mutableListOf<RestingHeartRateEntity>()
        val today = LocalDate.now(ZoneId.systemDefault())

        for (i in 0 until daysOfData) {
            val date = today.minusDays(i.toLong())
            if (!date.isBefore(start) && !date.isAfter(end)) {
                val bpm = when (i) {
                    0 -> 54.0 // Today
                    1 -> 55.0
                    2 -> 58.0 // After heavy workout
                    5 -> 62.0 // After short sleep
                    else -> 56.0 + ((i * 3) % 5)
                }
                val timestamp = date.atTime(6, 30).atZone(ZoneId.systemDefault()).toInstant()
                val recordId = "fake-rhr-$date"
                val fingerprint = sha256("RHR|$sourcePackage|$recordId|${date.toEpochDay()}")
                list.add(
                    RestingHeartRateEntity(
                        sourceRecordId = recordId,
                        sourcePackageName = sourcePackage,
                        recordFingerprint = fingerprint,
                        recordDate = date,
                        timestamp = timestamp,
                        restingBpm = bpm,
                        dataQualityState = "VALID"
                    )
                )
            }
        }
        return list
    }

    override suspend fun fetchHrvMeasurements(start: LocalDate, end: LocalDate): List<HrvMeasurementEntity> {
        val list = mutableListOf<HrvMeasurementEntity>()
        val today = LocalDate.now(ZoneId.systemDefault())

        for (i in 0 until daysOfData) {
            val date = today.minusDays(i.toLong())
            // Introduce an intentional edge case: Day 4 has no HRV reading to test missing HRV handling
            if (i == 4) continue

            if (!date.isBefore(start) && !date.isAfter(end)) {
                val rmssd = when (i) {
                    0 -> 68.0 // Today - high recovery
                    1 -> 65.0
                    2 -> 58.0
                    5 -> 46.0 // Dip after short sleep
                    else -> 60.0 + ((i * 5) % 15)
                }
                val timestamp = date.atTime(5, 45).atZone(ZoneId.systemDefault()).toInstant()
                val recordId = "fake-hrv-$date"
                val fingerprint = sha256("HRV|$sourcePackage|$recordId|${date.toEpochDay()}")
                list.add(
                    HrvMeasurementEntity(
                        sourceRecordId = recordId,
                        sourcePackageName = sourcePackage,
                        recordFingerprint = fingerprint,
                        recordDate = date,
                        timestamp = timestamp,
                        rmssd = rmssd,
                        dataQualityState = "VALID"
                    )
                )
            }
        }
        return list
    }

    override suspend fun fetchStepsSummaries(start: LocalDate, end: LocalDate): List<StepsSummaryEntity> {
        val list = mutableListOf<StepsSummaryEntity>()
        val today = LocalDate.now(ZoneId.systemDefault())

        for (i in 0 until daysOfData) {
            val date = today.minusDays(i.toLong())
            if (!date.isBefore(start) && !date.isAfter(end)) {
                val steps = 7500L + ((i * 739) % 5500)
                val recordId = "fake-steps-$date"
                val fingerprint = sha256("STEPS|$sourcePackage|$recordId|${date.toEpochDay()}")
                list.add(
                    StepsSummaryEntity(
                        sourceRecordId = recordId,
                        sourcePackageName = sourcePackage,
                        recordFingerprint = fingerprint,
                        recordDate = date,
                        stepCount = steps,
                        distanceMeters = (steps * 0.75),
                        activeCalories = (steps * 0.04)
                    )
                )
            }
        }
        return list
    }

    override suspend fun fetchHeartRateSamples(start: Instant, end: Instant): List<HeartRateSampleEntity> {
        return emptyList() // High-frequency samples are queried on-demand
    }

    override suspend fun fetchWeightMeasurements(start: Instant, end: Instant): List<WeightMeasurementEntity> {
        val today = LocalDate.now(ZoneId.systemDefault())
        val timestamp = today.atTime(7, 15).atZone(ZoneId.systemDefault()).toInstant()
        val fingerprint = sha256("WEIGHT|$sourcePackage|fake-weight|${timestamp.toEpochMilli()}")
        return listOf(
            WeightMeasurementEntity(
                sourceRecordId = "fake-weight-1",
                sourcePackageName = sourcePackage,
                recordFingerprint = fingerprint,
                timestamp = timestamp,
                weightKg = 73.5
            )
        )
    }

    override suspend fun fetchVo2Max(start: LocalDate, end: LocalDate): Map<LocalDate, Double> {
        val map = mutableMapOf<LocalDate, Double>()
        val today = LocalDate.now(ZoneId.systemDefault())
        for (i in 0 until daysOfData) {
            val date = today.minusDays(i.toLong())
            if (!date.isBefore(start) && !date.isAfter(end)) {
                if (i % 3 == 0) {
                    map[date] = 46.5 + ((i % 5) * 0.4)
                }
            }
        }
        return map
    }

    override suspend fun fetchOxygenSaturation(start: LocalDate, end: LocalDate): Map<LocalDate, Double> {
        val map = mutableMapOf<LocalDate, Double>()
        val today = LocalDate.now(ZoneId.systemDefault())
        for (i in 0 until daysOfData) {
            val date = today.minusDays(i.toLong())
            if (!date.isBefore(start) && !date.isAfter(end)) {
                map[date] = 97.5 + ((i * 3) % 20) * 0.1
            }
        }
        return map
    }

    override suspend fun fetchRespiratoryRate(start: LocalDate, end: LocalDate): Map<LocalDate, Double> {
        val map = mutableMapOf<LocalDate, Double>()
        val today = LocalDate.now(ZoneId.systemDefault())
        for (i in 0 until daysOfData) {
            val date = today.minusDays(i.toLong())
            if (!date.isBefore(start) && !date.isAfter(end)) {
                map[date] = 14.2 + ((i * 7) % 15) * 0.1
            }
        }
        return map
    }

    override suspend fun fetchBloodPressure(start: LocalDate, end: LocalDate): Map<LocalDate, Pair<Double, Double>> {
        val map = mutableMapOf<LocalDate, Pair<Double, Double>>()
        val today = LocalDate.now(ZoneId.systemDefault())
        for (i in 0 until daysOfData) {
            val date = today.minusDays(i.toLong())
            if (!date.isBefore(start) && !date.isAfter(end)) {
                val sys = 118.0 + ((i * 2) % 6)
                val dia = 76.0 + ((i * 3) % 5)
                map[date] = Pair(sys, dia)
            }
        }
        return map
    }

    override suspend fun writeNutritionRecord(entry: FoodLogEntity): Boolean = true

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
