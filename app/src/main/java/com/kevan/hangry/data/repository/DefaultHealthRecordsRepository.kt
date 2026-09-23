package com.kevan.hangry.data.repository

import com.kevan.hangry.data.datasource.HealthConnectDataSource
import com.kevan.hangry.data.local.dao.HealthRecordsDao
import com.kevan.hangry.data.local.entity.HealthMarkerEntity
import com.kevan.hangry.data.local.entity.HealthProfileItemEntity
import com.kevan.hangry.data.local.entity.MarkerGoalEntity
import com.kevan.hangry.data.local.entity.MenstrualPeriodEntity
import com.kevan.hangry.data.local.entity.UserProfileEntity
import com.kevan.hangry.domain.model.BiologicalSex
import com.kevan.hangry.domain.model.GlucoseContext
import com.kevan.hangry.domain.model.GoalDirection
import com.kevan.hangry.domain.model.HealthProfileItem
import com.kevan.hangry.domain.model.HealthProfileKind
import com.kevan.hangry.domain.model.HealthRecordsImportResult
import com.kevan.hangry.domain.model.HealthRecordsSnapshot
import com.kevan.hangry.domain.model.MarkerGoal
import com.kevan.hangry.domain.model.MarkerReading
import com.kevan.hangry.domain.model.MarkerType
import com.kevan.hangry.domain.model.MenstrualPeriod
import com.kevan.hangry.domain.model.RecordSource
import com.kevan.hangry.domain.repository.HealthRecordsRepository
import com.kevan.hangry.domain.repository.UserProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class DefaultHealthRecordsRepository(
    private val dao: HealthRecordsDao,
    private val userProfileRepository: UserProfileRepository,
    private val healthConnectDataSource: HealthConnectDataSource,
    private val zone: ZoneId = ZoneId.systemDefault()
) : HealthRecordsRepository {

    override fun observe(): Flow<HealthRecordsSnapshot> = combine(
        dao.observeMarkers(),
        dao.observeGoals(),
        dao.observeProfileItems(),
        dao.observePeriods(),
        userProfileRepository.getProfile()
    ) { markers, goals, items, periods, profile -> snapshot(markers, goals, items, periods, profile) }

    override suspend fun current(): HealthRecordsSnapshot = snapshot(
        dao.getMarkers(), dao.getGoals(), dao.getProfileItems(), dao.getPeriods(), userProfileRepository.getProfileSync()
    )

    override suspend fun addReading(
        type: MarkerType, value: Double, secondaryValue: Double?, measuredAt: Instant,
        glucoseContext: GlucoseContext?, note: String?
    ) {
        dao.insertMarker(
            HealthMarkerEntity(
                type = type.id,
                value = value,
                secondaryValue = secondaryValue.takeIf { type.hasSecondaryValue },
                measuredAt = measuredAt,
                date = measuredAt.atZone(zone).toLocalDate(),
                glucoseContext = glucoseContext?.name.takeIf { type == MarkerType.BLOOD_GLUCOSE },
                source = RecordSource.MANUAL.name,
                note = note?.trim()?.takeIf { it.isNotEmpty() }
            )
        )
    }

    override suspend fun deleteReading(id: Long) = dao.deleteMarker(id)

    override suspend fun setGoal(type: MarkerType, targetValue: Double, targetSecondary: Double?, targetDate: LocalDate?) {
        // Progress is measured from where you stood when the goal was set.
        val latest = dao.getMarkers().firstOrNull { it.type == type.id }
        dao.upsertGoal(
            MarkerGoalEntity(
                type = type.id,
                targetValue = targetValue,
                targetSecondary = targetSecondary.takeIf { type.hasSecondaryValue },
                direction = type.defaultGoalDirection.name,
                startValue = latest?.value,
                startDate = LocalDate.now(zone),
                targetDate = targetDate
            )
        )
    }

    override suspend fun clearGoal(type: MarkerType) = dao.deleteGoal(type.id)

    override suspend fun addProfileItem(kind: HealthProfileKind, name: String, note: String?) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        dao.insertProfileItems(
            listOf(
                HealthProfileItemEntity(
                    kind = kind.name,
                    name = clean,
                    note = note?.trim()?.takeIf { it.isNotEmpty() },
                    source = RecordSource.MANUAL.name
                )
            )
        )
    }

    override suspend fun deleteProfileItem(id: Long) = dao.deleteProfileItem(id)

    override suspend fun logPeriod(start: LocalDate, end: LocalDate?) {
        dao.insertPeriods(
            listOf(MenstrualPeriodEntity(startDate = start, endDate = end?.takeIf { !it.isBefore(start) }, source = RecordSource.MANUAL.name))
        )
    }

    override suspend fun deletePeriod(id: Long) = dao.deletePeriod(id)

    override suspend fun setPregnancy(isPregnant: Boolean, dueDate: LocalDate?) {
        val profile = userProfileRepository.getProfileSync() ?: UserProfileEntity()
        userProfileRepository.saveProfile(
            profile.copy(isPregnant = isPregnant, pregnancyDueDate = if (isPregnant) dueDate else null)
        )
    }

    override suspend fun importFromHealthConnect(): HealthRecordsImportResult {
        val sex = sexOf(userProfileRepository.getProfileSync())
        val includeCycle = sex == BiologicalSex.FEMALE
        val since = Instant.now().minus(IMPORT_WINDOW_DAYS, ChronoUnit.DAYS)
        val imported = healthConnectDataSource.fetchHealthRecords(since, includeCycle)
        // Insert-ignore on the source record id: already-imported records are skipped, and
        // anything the user deleted locally stays deleted until it changes upstream.
        return HealthRecordsImportResult(
            newReadings = dao.insertMarkersIgnoringDuplicates(imported.markers).count { it != -1L },
            newProfileItems = dao.insertProfileItems(imported.profileItems).count { it != -1L },
            newPeriods = dao.insertPeriods(imported.periods).count { it != -1L },
            medicalRecordsSupported = healthConnectDataSource.supportsMedicalRecords()
        )
    }

    private fun snapshot(
        markers: List<HealthMarkerEntity>,
        goals: List<MarkerGoalEntity>,
        items: List<HealthProfileItemEntity>,
        periods: List<MenstrualPeriodEntity>,
        profile: UserProfileEntity?
    ): HealthRecordsSnapshot {
        val sex = sexOf(profile)
        val female = sex == BiologicalSex.FEMALE
        return HealthRecordsSnapshot(
            readings = markers.mapNotNull { m ->
                val type = MarkerType.fromId(m.type) ?: return@mapNotNull null
                MarkerReading(
                    id = m.id, type = type, value = m.value, secondaryValue = m.secondaryValue,
                    measuredAt = m.measuredAt, date = m.date,
                    glucoseContext = GlucoseContext.fromName(m.glucoseContext),
                    source = RecordSource.fromName(m.source), note = m.note
                )
            },
            goals = goals.mapNotNull { g ->
                val type = MarkerType.fromId(g.type) ?: return@mapNotNull null
                MarkerGoal(
                    type = type, targetValue = g.targetValue, targetSecondary = g.targetSecondary,
                    direction = runCatching { GoalDirection.valueOf(g.direction) }.getOrDefault(type.defaultGoalDirection),
                    startValue = g.startValue, startDate = g.startDate, targetDate = g.targetDate
                )
            },
            profileItems = items.mapNotNull { i ->
                val kind = runCatching { HealthProfileKind.valueOf(i.kind) }.getOrNull() ?: return@mapNotNull null
                HealthProfileItem(i.id, kind, i.name, i.note, RecordSource.fromName(i.source))
            },
            periods = if (female) periods.map { MenstrualPeriod(it.id, it.startDate, it.endDate, RecordSource.fromName(it.source)) } else emptyList(),
            sex = sex,
            isPregnant = female && profile?.isPregnant == true,
            pregnancyDueDate = profile?.pregnancyDueDate.takeIf { female }
        )
    }

    private fun sexOf(profile: UserProfileEntity?): BiologicalSex? =
        profile?.biologicalSex?.let { runCatching { BiologicalSex.valueOf(it) }.getOrNull() }

    private companion object {
        const val IMPORT_WINDOW_DAYS = 730L
    }
}
