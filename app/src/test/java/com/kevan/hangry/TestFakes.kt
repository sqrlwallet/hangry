package com.kevan.hangry

import android.net.Uri
import com.kevan.hangry.data.local.entity.FoodLogEntity
import com.kevan.hangry.domain.model.GlucoseContext
import com.kevan.hangry.domain.model.HealthProfileKind
import com.kevan.hangry.domain.model.HealthRecordsImportResult
import com.kevan.hangry.domain.model.HealthRecordsSnapshot
import com.kevan.hangry.domain.model.MarkerType
import com.kevan.hangry.domain.model.SupplementAnalysisResult
import com.kevan.hangry.domain.model.SupplementsSnapshot
import com.kevan.hangry.domain.repository.FoodLogRepository
import com.kevan.hangry.domain.repository.HealthRecordsRepository
import com.kevan.hangry.domain.repository.SupplementDraft
import com.kevan.hangry.domain.repository.SupplementRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

// In-memory repositories shared by the Dash action tests.

internal class FakeSupplementRepository : SupplementRepository {
    val saved = mutableListOf<SupplementDraft>()
    override fun observe(): Flow<SupplementsSnapshot> = flowOf(SupplementsSnapshot())
    override suspend fun current() = SupplementsSnapshot()
    override suspend fun save(draft: SupplementDraft): Long { saved += draft; return saved.size.toLong() }
    override suspend fun delete(id: Long) {}
    override suspend fun setTaken(supplementId: Long, time: LocalTime, taken: Boolean, date: LocalDate) {}
    override suspend fun markSlotTaken(time: LocalTime, date: LocalDate) {}
    override suspend fun analyzePhotos(imagesBase64: List<String>, note: String?) = Result.success(SupplementAnalysisResult(name = "x"))
    override suspend fun rescheduleReminders() {}
    override suspend fun storePhoto(uri: Uri): String? = null
}

internal class FakeHealthRecordsRepository : HealthRecordsRepository {
    val readings = mutableListOf<Pair<MarkerType, Double>>()
    override fun observe(): Flow<HealthRecordsSnapshot> = flowOf(HealthRecordsSnapshot())
    override suspend fun current() = HealthRecordsSnapshot()
    override suspend fun addReading(type: MarkerType, value: Double, secondaryValue: Double?, measuredAt: Instant, glucoseContext: GlucoseContext?, note: String?) {
        readings += type to value
    }
    override suspend fun deleteReading(id: Long) {}
    override suspend fun setGoal(type: MarkerType, targetValue: Double, targetSecondary: Double?, targetDate: LocalDate?) {}
    override suspend fun clearGoal(type: MarkerType) {}
    override suspend fun addProfileItem(kind: HealthProfileKind, name: String, note: String?) {}
    override suspend fun deleteProfileItem(id: Long) {}
    override suspend fun logPeriod(start: LocalDate, end: LocalDate?) {}
    override suspend fun deletePeriod(id: Long) {}
    override suspend fun setPregnancy(isPregnant: Boolean, dueDate: LocalDate?) {}
    override suspend fun importFromHealthConnect() = HealthRecordsImportResult(0, 0, 0, false)
}

internal class FakeFoodLogRepository : FoodLogRepository {
    val entries = mutableListOf<FoodLogEntity>()
    override fun getForDate(date: LocalDate): Flow<List<FoodLogEntity>> = flowOf(entries)
    override fun getBetween(start: LocalDate, end: LocalDate): Flow<List<FoodLogEntity>> = flowOf(entries)
    override fun getTotalCaloriesForDate(date: LocalDate): Flow<Int> = flowOf(0)
    override suspend fun insert(entry: FoodLogEntity): Long { entries += entry; return entries.size.toLong() }
    override suspend fun update(entry: FoodLogEntity) {}
    override suspend fun delete(entry: FoodLogEntity) {}
    override suspend fun markSyncedToHealthConnect(id: Long) {}
    override suspend fun deleteAll() {}
}
