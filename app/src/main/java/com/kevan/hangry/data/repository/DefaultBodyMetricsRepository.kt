package com.kevan.hangry.data.repository

import com.kevan.hangry.data.local.dao.DailyHealthSummaryDao
import com.kevan.hangry.data.local.dao.ExerciseSessionDao
import com.kevan.hangry.data.local.dao.FoodLogDao
import com.kevan.hangry.data.local.dao.HeightDao
import com.kevan.hangry.data.local.dao.WeightDao
import com.kevan.hangry.data.local.entity.BodyFatScanEntity
import com.kevan.hangry.data.local.entity.DailyHealthSummaryEntity
import com.kevan.hangry.data.local.entity.ExerciseSessionEntity
import com.kevan.hangry.data.local.entity.FoodLogEntity
import com.kevan.hangry.data.local.entity.UserProfileEntity
import com.kevan.hangry.data.local.entity.WeightMeasurementEntity
import com.kevan.hangry.domain.calculation.BodyMetricsCalculator
import com.kevan.hangry.domain.calculation.EnergyBalanceCalculator
import com.kevan.hangry.domain.model.BiologicalSex
import com.kevan.hangry.domain.model.BodyMetricsInput
import com.kevan.hangry.domain.model.BodyMetricsSnapshot
import com.kevan.hangry.domain.repository.HeightRepository
import com.kevan.hangry.domain.repository.WeightRepository
import com.kevan.hangry.domain.repository.BodyFatRepository
import com.kevan.hangry.domain.repository.BodyMetricsRepository
import com.kevan.hangry.domain.repository.UserProfileRepository
import com.kevan.hangry.util.todayFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class DefaultBodyMetricsRepository(
    private val userProfileRepository: UserProfileRepository,
    private val weightRepository: WeightRepository,
    private val heightRepository: HeightRepository,
    private val bodyFatRepository: BodyFatRepository,
    private val dailyHealthSummaryDao: DailyHealthSummaryDao,
    private val exerciseSessionDao: ExerciseSessionDao,
    private val foodLogDao: FoodLogDao,
    private val bodyMetricsCalculator: BodyMetricsCalculator,
    private val energyBalanceCalculator: EnergyBalanceCalculator,
    private val zone: ZoneId = ZoneId.systemDefault()
) : BodyMetricsRepository {

    private data class Window(val today: LocalDate) {
        // Averages and learning use full past days only (the calculator windows them); today's
        // steps and workouts are loaded too, for today's own calorie target.
        val summariesStart: LocalDate = today.minusDays(14)
        val end: LocalDate = today.minusDays(1)
        val workoutsEnd: LocalDate = today.plusDays(1)
        val workoutsStart: LocalDate = today.minusDays(EnergyBalanceCalculator.WINDOW_DAYS.toLong())
        val adaptiveStart: LocalDate = today.minusDays(EnergyBalanceCalculator.ADAPTIVE_WINDOW_DAYS.toLong())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observe(): Flow<BodyMetricsSnapshot> = todayFlow(zone).flatMapLatest { today -> observe(Window(today)) }

    private fun observe(w: Window): Flow<BodyMetricsSnapshot> {
        val measurements = combine(
            userProfileRepository.getProfile(),
            weightRepository.getLatestWeight(),
            heightRepository.getLatestHeight(),
            bodyFatRepository.getLatestScan()
        ) { profile, weight, height, scan -> Measurements(profile, weight?.weightKg, height?.heightCm, scan) }
        return combine(
            measurements,
            dailyHealthSummaryDao.getSummariesBetween(w.summariesStart, w.today),
            exerciseSessionDao.getSessionsBetween(w.workoutsStart.atStartOfDay(zone).toInstant(), w.workoutsEnd.atStartOfDay(zone).toInstant()),
            foodLogDao.getBetween(w.adaptiveStart, w.end),
            weightRepository.getAllWeights()
        ) { m, summaries, workouts, foodLogs, weights -> build(w.today, m, summaries, workouts, foodLogs, weights) }
    }

    override suspend fun current(): BodyMetricsSnapshot {
        val w = Window(LocalDate.now(zone))
        val m = Measurements(
            profile = userProfileRepository.getProfileSync(),
            latestWeightKg = weightRepository.getLatestWeightSync()?.weightKg,
            latestHeightCm = heightRepository.getLatestHeightSync()?.heightCm,
            scan = bodyFatRepository.getLatestScanSync()
        )
        return build(
            today = w.today,
            m = m,
            summaries = dailyHealthSummaryDao.getSummariesBetweenList(w.summariesStart, w.today),
            workouts = exerciseSessionDao.getSessionsBetweenList(
                w.workoutsStart.atStartOfDay(zone).toInstant(), w.workoutsEnd.atStartOfDay(zone).toInstant()
            ),
            foodLogs = foodLogDao.getBetweenList(w.adaptiveStart, w.end),
            weights = weightRepository.getAllWeights().first()
        )
    }

    override suspend fun logWeight(kg: Double, takenAt: Instant) {
        val newest = weightRepository.getLatestWeightSync()?.timestamp?.let { !takenAt.isBefore(it) } ?: true
        weightRepository.insertOrIgnore(
            listOf(
                WeightMeasurementEntity(
                    recordFingerprint = "manual_entry_${Instant.now().toEpochMilli()}",
                    timestamp = takenAt,
                    weightKg = kg,
                    sourcePackageName = MANUAL_SOURCE
                )
            )
        )
        if (newest) {
            userProfileRepository.getProfileSync()?.let { profile ->
                userProfileRepository.saveProfile(profile.copy(currentWeightKg = kg, updatedAt = Instant.now()))
            }
        }
    }

    private data class Measurements(
        val profile: UserProfileEntity?,
        val latestWeightKg: Double?,
        val latestHeightCm: Double?,
        val scan: BodyFatScanEntity?
    )

    private fun build(
        today: LocalDate,
        m: Measurements,
        summaries: List<DailyHealthSummaryEntity>,
        workouts: List<ExerciseSessionEntity>,
        foodLogs: List<FoodLogEntity>,
        weights: List<WeightMeasurementEntity>
    ): BodyMetricsSnapshot {
        val profile = m.profile
        val weightKg = m.latestWeightKg ?: profile?.currentWeightKg
        val heightCm = m.latestHeightCm ?: profile?.heightCm
        val sex = profile?.biologicalSex?.let { runCatching { BiologicalSex.valueOf(it) }.getOrNull() }
        // Full days only: today's burn is still adding up.
        val burns = summaries.filter { it.date.isBefore(today) }.mapNotNull { it.totalCalories?.takeIf { kcal -> kcal > 0 } }

        val energy = energyBalanceCalculator.estimate(
            weightKg = weightKg,
            heightCm = heightCm,
            age = profile?.age,
            sex = sex,
            summaries = summaries,
            workouts = workouts,
            goalWeightKg = profile?.weightGoalKg,
            goalDate = profile?.goalTargetDate,
            today = today,
            zone = zone,
            // Only a real measurement (tape or photo) within ~3 months is good enough for Katch-McArdle.
            measuredBodyFatPercent = m.scan
                ?.takeIf { it.method in setOf("NAVY_CIRCUMFERENCE", "AI_MULTIMODAL", "REPORTED") }
                ?.takeIf { !it.date.isBefore(today.minusDays(90)) }
                ?.bodyFatPercentage,
            foodLogs = foodLogs,
            weights = weights
        )
        val input = BodyMetricsInput(
            heightCm = heightCm,
            weightKg = weightKg,
            age = profile?.age,
            sex = sex,
            neckCm = profile?.neckCircumferenceCm,
            chestCm = profile?.chestCircumferenceCm,
            waistCm = profile?.waistCircumferenceCm,
            hipCm = profile?.hipCircumferenceCm,
            bodyFatPercent = m.scan?.bodyFatPercentage,
            bodyFatSource = m.scan?.method?.let(::scanMethodLabel),
            averageDailyBurnKcal = burns.takeIf { it.isNotEmpty() }?.average(),
            averageDailyBurnDays = burns.size,
            energy = energy
        )
        return BodyMetricsSnapshot(
            input = input,
            bodyFatScanDate = m.scan?.date,
            groups = bodyMetricsCalculator.calculate(input)
        )
    }

    companion object {
        const val MANUAL_SOURCE = "com.kevan.hangry.manual"

        fun scanMethodLabel(method: String): String = when (method) {
            "AI_MULTIMODAL" -> "AI photo"
            "BIOMETRIC_HISTORY" -> "health-history"
            "REPORTED" -> "reported"
            else -> "tape-measure"
        }
    }
}
