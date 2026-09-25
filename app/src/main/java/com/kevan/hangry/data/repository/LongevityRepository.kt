package com.kevan.hangry.data.repository

import com.kevan.hangry.data.local.HangryDatabase
import com.kevan.hangry.data.local.entity.LongevityCheckInEntity
import com.kevan.hangry.domain.calculation.HeartRateZones
import com.kevan.hangry.domain.calculation.LongevityCalculator
import com.kevan.hangry.domain.model.LongevityPillar
import com.kevan.hangry.domain.model.LongevityWeek
import com.kevan.hangry.domain.model.ProgramCatalog
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import java.time.LocalDate
import java.time.ZoneId

/**
 * The week's longevity pillars, re-read whenever a sync rewrites the week's daily summaries
 * (new workouts or heart rate) or a pillar is ticked off.
 */
class LongevityRepository(private val database: HangryDatabase, private val zone: ZoneId = ZoneId.systemDefault()) {

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeWeek(date: LocalDate): Flow<LongevityWeek> {
        val start = LongevityWeek.weekStart(date)
        val end = start.plusDays(6)
        return combine(
            database.longevityCheckInDao().observeBetween(start, end),
            database.dailyHealthSummaryDao().getSummariesBetween(start.minusDays(30), end),
            database.programSessionDao().observeBetween(start, end)
        ) { checkIns, summaries, programs -> Triple(checkIns, summaries, programs) }
            .mapLatest { (checkIns, summaries, programSessions) ->
                val from = start.atStartOfDay(zone).toInstant()
                val to = end.plusDays(1).atStartOfDay(zone).toInstant()
                val profile = database.userProfileDao().getProfileSync()
                val usualRhr = summaries.filter { it.date.isBefore(start) || it.date == start }
                    .mapNotNull { it.restingHeartRate }.takeIf { it.isNotEmpty() }?.average()
                LongevityCalculator.week(
                    weekStart = start,
                    workouts = database.exerciseSessionDao().getSessionsBetweenList(from, to),
                    heartRate = database.heartRateDao().getSamplesBetweenList(from, to),
                    sleep = database.sleepSessionDao().getSessionsBetweenList(from.minusSeconds(16 * 3600), to.plusSeconds(16 * 3600)),
                    zones = HeartRateZones.forUser(profile?.age, profile?.maxHeartRate, usualRhr),
                    checkIns = checkIns.groupBy { LongevityPillar.fromName(it.pillar) }
                        .filterKeys { it != null }
                        .mapKeys { it.key!! }
                        .mapValues { (_, v) -> v.map { it.date }.toSet() },
                    zone = zone,
                    extraStrengthDays = programSessions.filter { s ->
                        ProgramCatalog.byId(s.programId)?.strengthFromLevel?.let { s.level >= it } == true
                    }.map { it.date }.toSet(),
                    extraMobilityDays = programSessions.filter { ProgramCatalog.byId(it.programId)?.countsAsMobility == true }.map { it.date }.toSet()
                )
            }
    }

    suspend fun setDone(pillar: LongevityPillar, date: LocalDate, done: Boolean) {
        val dao = database.longevityCheckInDao()
        if (done) dao.insert(LongevityCheckInEntity(date, pillar.name)) else dao.delete(date, pillar.name)
    }
}
