package com.kevan.hangry.ui.supplements

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kevan.hangry.data.ai.OpenRouterException
import com.kevan.hangry.domain.model.Supplement
import com.kevan.hangry.domain.model.SupplementIngredient
import com.kevan.hangry.domain.model.SupplementsSnapshot
import com.kevan.hangry.domain.repository.SupplementDraft
import com.kevan.hangry.domain.repository.SupplementRepository
import com.kevan.hangry.domain.repository.UserProfileRepository
import com.kevan.hangry.util.clearCapturedImageCache
import com.kevan.hangry.util.readImageAsBase64Jpeg
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalTime

/** The add/edit form. Lives in the ViewModel so a camera round-trip doesn't lose it. */
data class SupplementEditorState(
    val id: Long = 0,
    val name: String = "",
    val brand: String = "",
    val form: String? = null,
    val doseAmount: String = "1",
    val doseUnit: String = "capsule",
    val times: List<LocalTime> = listOf(LocalTime.of(8, 0)),
    val ingredients: List<SupplementIngredient> = emptyList(),
    val remindersEnabled: Boolean = true,
    val notes: String = "",
    val suggestedTiming: String? = null,
    val cautions: List<String> = emptyList(),
    val photos: List<Uri> = emptyList(),
    val existingPhotoPath: String? = null,
    val active: Boolean = true,
    val isAnalyzing: Boolean = false,
    val analysisError: String? = null
) {
    val isNew: Boolean get() = id == 0L
    val canSave: Boolean get() = name.isNotBlank() && !isAnalyzing
}

class SupplementsViewModel(
    @field:SuppressLint("StaticFieldLeak") private val context: Context,
    private val repository: SupplementRepository,
    userProfileRepository: UserProfileRepository
) : ViewModel() {

    val snapshot: StateFlow<SupplementsSnapshot> = repository.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SupplementsSnapshot())

    val aiEnabled: StateFlow<Boolean> = userProfileRepository.getProfile()
        .map { it?.aiFeaturesEnabled == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _editor = MutableStateFlow<SupplementEditorState?>(null)
    val editor: StateFlow<SupplementEditorState?> = _editor.asStateFlow()

    /** Photo-first: each photo (bottle front, then label) is added and the lot re-analyzed. */
    fun onPhotoCaptured(uris: List<Uri>) {
        val current = _editor.value ?: SupplementEditorState()
        val photos = (current.photos + uris).take(MAX_PHOTOS)
        _editor.value = current.copy(photos = photos)
        if (aiEnabled.value) analyze(photos) else {
            _editor.update { it?.copy(analysisError = "Turn on AI Features in Settings to fill this in from the photo automatically.") }
        }
    }

    fun startManual() {
        _editor.value = SupplementEditorState()
    }

    fun edit(supplement: Supplement) {
        _editor.value = SupplementEditorState(
            id = supplement.id,
            name = supplement.name,
            brand = supplement.brand.orEmpty(),
            form = supplement.form,
            doseAmount = formatAmount(supplement.doseAmount),
            doseUnit = supplement.doseUnit,
            times = supplement.times,
            ingredients = supplement.ingredients,
            remindersEnabled = supplement.remindersEnabled,
            notes = supplement.notes.orEmpty(),
            existingPhotoPath = supplement.photoPath,
            active = supplement.active
        )
    }

    fun updateEditor(transform: (SupplementEditorState) -> SupplementEditorState) = _editor.update { it?.let(transform) }

    fun dismissEditor() {
        _editor.value = null
        context.clearCapturedImageCache()
    }

    fun save() {
        val e = _editor.value ?: return
        if (!e.canSave) return
        viewModelScope.launch {
            val photoPath = e.photos.firstOrNull()?.let { repository.storePhoto(it) } ?: e.existingPhotoPath
            repository.save(
                SupplementDraft(
                    id = e.id,
                    name = e.name,
                    brand = e.brand,
                    form = e.form,
                    doseAmount = e.doseAmount.replace(',', '.').toDoubleOrNull() ?: 1.0,
                    doseUnit = e.doseUnit,
                    times = e.times,
                    ingredients = e.ingredients,
                    remindersEnabled = e.remindersEnabled,
                    notes = e.notes,
                    photoPath = photoPath,
                    active = e.active
                )
            )
            dismissEditor()
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            repository.delete(id)
            dismissEditor()
        }
    }

    fun setTaken(supplementId: Long, time: LocalTime, taken: Boolean) =
        viewModelScope.launch { repository.setTaken(supplementId, time, taken) }

    private fun analyze(photos: List<Uri>) {
        val images = photos.mapNotNull { context.readImageAsBase64Jpeg(it) }
        if (images.isEmpty()) {
            _editor.update { it?.copy(analysisError = "Couldn't read that photo. Try again.") }
            return
        }
        _editor.update { it?.copy(isAnalyzing = true, analysisError = null) }
        viewModelScope.launch {
            repository.analyzePhotos(images, note = null).fold(
                onSuccess = { r ->
                    _editor.update { e ->
                        e?.copy(
                            isAnalyzing = false,
                            name = r.name.takeIf { it.isNotBlank() && it != "Unknown" } ?: e.name,
                            brand = r.brand ?: e.brand,
                            form = r.form ?: e.form,
                            doseAmount = r.servingAmount?.let(::formatAmount) ?: e.doseAmount,
                            doseUnit = r.servingUnit ?: r.form ?: e.doseUnit,
                            ingredients = r.ingredients.ifEmpty { e.ingredients },
                            suggestedTiming = r.suggestedTiming,
                            times = if (e.isNew) listOf(defaultTimeFor(r.suggestedTiming)) else e.times,
                            notes = r.notes ?: e.notes,
                            cautions = r.cautions,
                            analysisError = if (r.name == "Unknown") r.notes ?: "That doesn't look like a supplement label." else null
                        )
                    }
                },
                onFailure = { err ->
                    _editor.update {
                        it?.copy(
                            isAnalyzing = false,
                            analysisError = (err as? OpenRouterException)?.message ?: "Couldn't analyze the photo. Fill it in below."
                        )
                    }
                }
            )
        }
    }

    companion object {
        const val MAX_PHOTOS = 3

        fun formatAmount(value: Double): String =
            if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

        /** Turns "evening, before bed" style timing advice into a starting reminder time. */
        fun defaultTimeFor(timing: String?): LocalTime {
            val t = timing.orEmpty().lowercase()
            return when {
                "bed" in t || "night" in t || "evening" in t -> LocalTime.of(21, 0)
                "lunch" in t || "afternoon" in t || "midday" in t -> LocalTime.of(13, 0)
                "dinner" in t -> LocalTime.of(19, 0)
                else -> LocalTime.of(8, 0)
            }
        }

        fun provideFactory(context: Context, repository: SupplementRepository, userProfileRepository: UserProfileRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SupplementsViewModel(context.applicationContext, repository, userProfileRepository) as T
            }
    }
}
