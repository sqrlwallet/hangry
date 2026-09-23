package com.kevan.hangry.domain.repository

import com.kevan.hangry.domain.model.GlucoseContext
import com.kevan.hangry.domain.model.HealthProfileKind
import com.kevan.hangry.domain.model.HealthRecordsImportResult
import com.kevan.hangry.domain.model.HealthRecordsSnapshot
import com.kevan.hangry.domain.model.MarkerType
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

/** User-owned lab/vital readings, goals, allergies, conditions, pregnancy and cycle data. */
interface HealthRecordsRepository {
    fun observe(): Flow<HealthRecordsSnapshot>
    suspend fun current(): HealthRecordsSnapshot

    /** [value]/[secondaryValue] in the marker's canonical unit. */
    suspend fun addReading(
        type: MarkerType,
        value: Double,
        secondaryValue: Double?,
        measuredAt: Instant,
        glucoseContext: GlucoseContext?,
        note: String?
    )
    suspend fun deleteReading(id: Long)

    suspend fun setGoal(type: MarkerType, targetValue: Double, targetSecondary: Double?, targetDate: LocalDate?)
    suspend fun clearGoal(type: MarkerType)

    suspend fun addProfileItem(kind: HealthProfileKind, name: String, note: String?)
    suspend fun deleteProfileItem(id: Long)

    suspend fun logPeriod(start: LocalDate, end: LocalDate?)
    suspend fun deletePeriod(id: Long)
    suspend fun setPregnancy(isPregnant: Boolean, dueDate: LocalDate?)

    /** Pulls blood pressure, glucose, periods and medical records from Health Connect. */
    suspend fun importFromHealthConnect(): HealthRecordsImportResult
}
