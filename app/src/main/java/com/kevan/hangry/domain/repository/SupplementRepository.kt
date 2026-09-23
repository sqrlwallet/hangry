package com.kevan.hangry.domain.repository

import com.kevan.hangry.domain.model.SupplementAnalysisResult
import com.kevan.hangry.domain.model.SupplementIngredient
import com.kevan.hangry.domain.model.SupplementsSnapshot
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalTime

/** Everything needed to create or update a supplement; id = 0 creates a new one. */
data class SupplementDraft(
    val id: Long = 0,
    val name: String,
    val brand: String? = null,
    val form: String? = null,
    val doseAmount: Double = 1.0,
    val doseUnit: String = "serving",
    val times: List<LocalTime> = emptyList(),
    val ingredients: List<SupplementIngredient> = emptyList(),
    val remindersEnabled: Boolean = false,
    val notes: String? = null,
    val photoPath: String? = null,
    val active: Boolean = true
)

interface SupplementRepository {
    fun observe(): Flow<SupplementsSnapshot>
    suspend fun current(): SupplementsSnapshot

    /** Saves and re-schedules reminders. Returns the supplement id. */
    suspend fun save(draft: SupplementDraft): Long
    suspend fun delete(id: Long)
    suspend fun setTaken(supplementId: Long, time: LocalTime, taken: Boolean, date: LocalDate = LocalDate.now())

    /** Marks every untaken dose scheduled at [time] today as taken (the reminder's action). */
    suspend fun markSlotTaken(time: LocalTime, date: LocalDate = LocalDate.now())

    /** Reads a supplement from photos, flagging cautions against the user's profile. */
    suspend fun analyzePhotos(imagesBase64: List<String>, note: String?): Result<SupplementAnalysisResult>

    /** Re-arms reminder alarms from what's saved (after boot, time changes, app updates). */
    suspend fun rescheduleReminders()

    /** Copies a captured photo into app storage; returns its path. */
    suspend fun storePhoto(uri: android.net.Uri): String?
}
