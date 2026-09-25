package com.kevan.hangry.data.repository

import android.content.Context
import android.net.Uri
import com.kevan.hangry.data.local.dao.SupplementDao
import com.kevan.hangry.data.local.entity.SupplementEntity
import com.kevan.hangry.data.local.entity.SupplementIntakeEntity
import com.kevan.hangry.data.supplements.SupplementReminderScheduler
import com.kevan.hangry.domain.ai.SupplementAnalyzer
import com.kevan.hangry.domain.model.Supplement
import com.kevan.hangry.domain.model.SupplementAnalysisResult
import com.kevan.hangry.domain.model.SupplementDose
import com.kevan.hangry.domain.model.SupplementIngredient
import com.kevan.hangry.domain.model.SupplementTimes
import com.kevan.hangry.domain.model.SupplementsSnapshot
import com.kevan.hangry.domain.repository.HealthRecordsRepository
import com.kevan.hangry.domain.repository.SupplementDraft
import com.kevan.hangry.domain.repository.SupplementRepository
import com.kevan.hangry.ui.widget.HangryWidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class DefaultSupplementRepository(
    private val context: Context,
    private val dao: SupplementDao,
    private val analyzer: SupplementAnalyzer,
    private val healthRecordsRepository: HealthRecordsRepository,
    private val scheduler: SupplementReminderScheduler
) : SupplementRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val ingredientsSerializer = ListSerializer(SupplementIngredient.serializer())

    override fun observe(): Flow<SupplementsSnapshot> {
        val today = LocalDate.now()
        return combine(dao.observeAll(), dao.observeIntakesBetween(today.minusDays(7), today)) { supplements, intakes ->
            snapshot(supplements, intakes, today)
        }
    }

    override suspend fun current(): SupplementsSnapshot {
        val today = LocalDate.now()
        return snapshot(dao.getAll(), dao.getIntakesBetween(today.minusDays(7), today), today)
    }

    override suspend fun save(draft: SupplementDraft): Long {
        val entity = SupplementEntity(
            id = draft.id,
            name = draft.name.trim().ifEmpty { "Supplement" },
            brand = draft.brand?.trim()?.takeIf { it.isNotEmpty() },
            form = draft.form,
            doseAmount = draft.doseAmount.coerceAtLeast(0.0),
            doseUnit = draft.doseUnit.trim().ifEmpty { "serving" },
            times = SupplementTimes.format(draft.times),
            ingredientsJson = json.encodeToString(ingredientsSerializer, draft.ingredients),
            tracked = draft.tracked && draft.times.isNotEmpty(),
            remindersEnabled = draft.tracked && draft.remindersEnabled && draft.times.isNotEmpty(),
            notes = draft.notes?.trim()?.takeIf { it.isNotEmpty() },
            photoPath = draft.photoPath,
            active = draft.active
        )
        val id = if (draft.id == 0L) dao.insert(entity) else entity.id.also { dao.update(entity) }
        afterChange()
        return id
    }

    override suspend fun delete(id: Long) {
        dao.getById(id)?.photoPath?.let { runCatching { File(it).delete() } }
        dao.deleteIntakesFor(id)
        dao.delete(id)
        afterChange()
    }

    override suspend fun setTaken(supplementId: Long, time: LocalTime, taken: Boolean, date: LocalDate) {
        val key = SupplementTimes.key(time)
        if (taken) {
            dao.insertIntakes(listOf(SupplementIntakeEntity(supplementId = supplementId, date = date, scheduledTime = key)))
        } else {
            dao.deleteIntake(supplementId, date, key)
        }
        HangryWidgetUpdater.updateAllWidgets(context)
    }

    override suspend fun markSlotTaken(time: LocalTime, date: LocalDate) {
        val key = SupplementTimes.key(time)
        val intakes = current().supplements
            .filter { it.active && it.tracked && time in it.times }
            .map { SupplementIntakeEntity(supplementId = it.id, date = date, scheduledTime = key, takenAt = Instant.now()) }
        dao.insertIntakes(intakes)
    }

    override suspend fun analyzePhotos(imagesBase64: List<String>, note: String?): Result<SupplementAnalysisResult> {
        val records = healthRecordsRepository.current()
        val existing = current().active
        val userContext = buildString {
            if (records.isPregnant) appendLine("Pregnant: yes")
            if (records.allergies.isNotEmpty()) appendLine("Allergies: " + records.allergies.joinToString { it.name })
            if (records.conditions.isNotEmpty()) appendLine("Conditions: " + records.conditions.joinToString { it.name })
            if (existing.isNotEmpty()) {
                appendLine("Already taking: " + existing.joinToString("; ") { s ->
                    s.name + s.ingredients.take(6).joinToString(", ", " (", ")") { i ->
                        listOfNotNull(i.name, i.amount?.let { a -> "$a${i.unit.orEmpty()}" }).joinToString(" ")
                    }
                })
            }
        }
        return analyzer.analyzePhotos(imagesBase64, note, userContext.ifBlank { null })
    }

    override suspend fun rescheduleReminders() {
        scheduler.reschedule(current().supplements)
    }

    override suspend fun storePhoto(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.filesDir, "supplement_photos").apply { mkdirs() }
            val file = File(dir, "supplement_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } }
            file.absolutePath
        }.getOrNull()
    }

    private suspend fun afterChange() {
        rescheduleReminders()
        HangryWidgetUpdater.updateAllWidgets(context)
    }

    private fun snapshot(entities: List<SupplementEntity>, intakes: List<SupplementIntakeEntity>, today: LocalDate): SupplementsSnapshot {
        val supplements = entities.map { it.toModel() }
        val takenKeys = intakes.map { Triple(it.supplementId, it.date, it.scheduledTime) }.toSet()
        // Untracked supplements are assumed taken, so they have no doses to tick off.
        val todayDoses = supplements.filter { it.active && it.tracked }
            .flatMap { s -> s.times.map { t -> SupplementDose(s, t, Triple(s.id, today, SupplementTimes.key(t)) in takenKeys) } }
            .sortedWith(compareBy({ it.time }, { it.supplement.name.lowercase() }))
        // Last 7 full days; a supplement only counts from the day it was added.
        val weekDays = (1L..7L).map { today.minusDays(it) }
        val adherence = supplements.filter { it.active && it.tracked && it.times.isNotEmpty() }.associate { s ->
            val created = entities.first { it.id == s.id }.createdAt.atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            val days = weekDays.filter { !it.isBefore(created) }
            val scheduled = days.size * s.times.size
            val taken = days.sumOf { d -> s.times.count { t -> Triple(s.id, d, SupplementTimes.key(t)) in takenKeys } }
            s.id to (taken to scheduled)
        }
        return SupplementsSnapshot(supplements, today, todayDoses, adherence)
    }

    private fun SupplementEntity.toModel() = Supplement(
        id = id,
        name = name,
        brand = brand,
        form = form,
        doseAmount = doseAmount,
        doseUnit = doseUnit,
        times = SupplementTimes.parse(times),
        ingredients = runCatching { json.decodeFromString(ingredientsSerializer, ingredientsJson) }.getOrDefault(emptyList()),
        remindersEnabled = remindersEnabled && tracked,
        tracked = tracked,
        notes = notes,
        photoPath = photoPath,
        active = active
    )
}
