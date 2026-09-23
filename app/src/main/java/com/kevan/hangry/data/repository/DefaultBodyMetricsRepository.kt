package com.kevan.hangry.data.repository

import com.kevan.hangry.data.local.dao.DailyHealthSummaryDao
import com.kevan.hangry.data.local.dao.ExerciseSessionDao
import com.kevan.hangry.data.local.dao.HeightDao
import com.kevan.hangry.data.local.dao.WeightDao
import com.kevan.hangry.data.local.entity.BodyFatScanEntity
import com.kevan.hangry.data.local.entity.DailyHealthSummaryEntity
import com.kevan.hangry.data.local.entity.ExerciseSessionEntity
import com.kevan.hangry.data.local.entity.UserProfileEntity
import com.kevan.hangry.domain.calculation.BodyMetricsCalculator
import com.kevan.hangry.domain.calculation.EnergyBalanceCalculator
import com.kevan.hangry.domain.model.BiologicalSex
import com.kevan.hangry.domain.model.BodyMetricsInput
import com.kevan.hangry.domain.model.BodyMetricsSnapshot
import com.kevan.hangry.domain.repository.BodyFatRepository
import com.kevan.hangry.domain.repository.BodyMetricsRepository
import com.kevan.hangry.domain.repository.UserProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.ZoneId

class DefaultBodyMetricsRepository(
    private val userProfileRepository: UserProfileRepository,
    private val weightDao: WeightDao,
    private val heightDao: HeightDao,
    private val bodyFatRepository: BodyFatRepository,
    private val dailyHealthSummaryDao: DailyHealthSummaryDao,
    private val exerciseSessionDao: ExerciseSessionDao,
    private val bodyMetricsCalculator: BodyMetricsCalculator,
    private val energyBalanceCalculator: EnergyBalanceCalculator,
    private val zone: ZoneId = ZoneId.systemDefault()
) : BodyMetricsRepository {

    private data class Window(val today: LocalDate) {
        // Only full past days - today's steps and burn are still accumulating.
        val summariesStart: LocalDate = today.minusDays(14)
        val end: LocalDate = today.minusDays(1)
        val workoutsStart: LocalDate = today.minusDays(EnergyBalanceCalculator.WINDOW_DAYS.toLong())
    }

    override fun observe(): Flow<BodyMetricsSnapshot> {
        val w = Window(LocalDate.now(zone))
        val measurements = combine(
            userProfileRepository.getProfile(),
            weightDao.getLatestWeight(),
            heightDao.getLatestHeight(),
            bodyFatRepository.getLatestScan()
        ) { profile, weight, height, scan -> Measurements(profile, weight?.weightKg, height?.heightCm, scan) }
        return combine(
            measurements,
            dailyHealthSummaryDao.getSummariesBetween(w.summariesStart, w.end),
            exerciseSessionDao.getSessionsBetween(w.workoutsStart.atStartOfDay(zone).toInstant(), w.today.atStartOfDay(zone).toInstant())
        ) { m, summaries, workouts -> build(w.today, m, summaries, workouts) }
    }

    override suspend fun current(): BodyMetricsSnapshot {
        val w = Window(LocalDate.now(zone))
        val m = Measurements(
            profile = userProfileRepository.getProfileSync(),
            latestWeightKg = weightDao.getLatestWeightSync()?.weightKg,
            latestHeightCm = heightDao.getLatestHeightSync()?.heightCm,
            scan = bodyFatRepository.getLatestScanSync()
        )
        return build(
            today = w.today,
            m = m,
            summaries = dailyHealthSummaryDao.getSummariesBetweenList(w.summariesStart, w.end),
            workouts = exerciseSessionDao.getSessionsBetweenList(
                w.workoutsStart.atStartOfDay(zone).toInstant(), w.today.atStartOfDay(zone).toInstant()
            )
        )
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
        workouts: List<ExerciseSessionEntity>
    ): BodyMetricsSnapshot {
        val profile = m.profile
        val weightKg = m.latestWeightKg ?: profile?.currentWeightKg
        val heightCm = m.latestHeightCm ?: profile?.heightCm
        val sex = profile?.biologicalSex?.let { runCatching { BiologicalSex.valueOf(it) }.getOrNull() }
        val burns = summaries.mapNotNull { it.totalCalories?.takeIf { kcal -> kcal > 0 } }

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
                ?.bodyFatPercentage
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
        fun scanMethodLabel(method: String): String = when (method) {
            "AI_MULTIMODAL" -> "AI photo"
            "BIOMETRIC_HISTORY" -> "health-history"
            "REPORTED" -> "reported"
            else -> "tape-measure"
        }
    }
}
