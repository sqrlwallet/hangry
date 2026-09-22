package com.kevan.hangry.ui.posture

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kevan.hangry.data.ai.OpenRouterException
import com.kevan.hangry.data.local.entity.buildPostureScanEntity
import com.kevan.hangry.data.local.entity.photoPaths
import com.kevan.hangry.domain.ai.PostureAnalyzer
import com.kevan.hangry.domain.repository.PostureScanRepository
import com.kevan.hangry.domain.repository.UserProfileRepository
import com.kevan.hangry.util.PosturePhotoStorage
import com.kevan.hangry.util.clearCapturedImageCache
import com.kevan.hangry.util.readImageAsBase64Jpeg
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/** Photos in, saved scan out - a valid analysis is saved immediately, no separate confirm tap. */
class PostureViewModel(
    @field:SuppressLint("StaticFieldLeak")
    private val context: Context,
    private val postureScanRepository: PostureScanRepository,
    private val postureAnalyzer: PostureAnalyzer,
    private val userProfileRepository: UserProfileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PostureUiState())
    val uiState: StateFlow<PostureUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                postureScanRepository.getAll(),
                postureScanRepository.getLatest(),
                userProfileRepository.getProfile()
            ) { scans, latest, profile ->
                _uiState.update {
                    it.copy(
                        scans = scans,
                        latestScan = latest,
                        aiFeaturesEnabled = profile?.aiFeaturesEnabled ?: false
                    )
                }
            }.collect { }
        }
    }

    fun addPhoto(uri: Uri) {
        _uiState.update {
            if (it.capturedPhotos.size >= MAX_POSTURE_PHOTOS) it
            else it.copy(capturedPhotos = it.capturedPhotos + uri)
        }
    }

    fun removePhoto(index: Int) {
        _uiState.update { it.copy(capturedPhotos = it.capturedPhotos.filterIndexed { i, _ -> i != index }) }
    }

    fun clearCapture() {
        _uiState.update { it.copy(capturedPhotos = emptyList(), justSavedScan = null) }
        context.clearCapturedImageCache()
    }

    fun analyze() {
        val photos = _uiState.value.capturedPhotos
        if (photos.size < MIN_POSTURE_PHOTOS) return

        val base64Photos = photos.mapNotNull { context.readImageAsBase64Jpeg(it) }
        if (base64Photos.size != photos.size) {
            _uiState.update { it.copy(errorMessage = "Couldn't read one of the photos. Try retaking it.") }
            return
        }

        _uiState.update { it.copy(isAnalyzing = true) }
        viewModelScope.launch {
            postureAnalyzer.analyzeScan(base64Photos).fold(
                onSuccess = { result ->
                    val score = result.score
                    if (result.isValid && score != null) {
                        val permanentPaths = PosturePhotoStorage.moveToPermanentStorage(context, photos)
                        val entity = buildPostureScanEntity(
                            date = LocalDate.now(ZoneId.systemDefault()),
                            score = score,
                            findings = result.findings,
                            exercises = result.exercises,
                            photoPaths = permanentPaths
                        )
                        val id = postureScanRepository.insert(entity)
                        context.clearCapturedImageCache()
                        _uiState.update {
                            it.copy(isAnalyzing = false, capturedPhotos = emptyList(), justSavedScan = entity.copy(id = id))
                        }
                    } else {
                        val reasonSuffix = result.rejectionReason?.let { " ($it)" }.orEmpty()
                        _uiState.update {
                            it.copy(
                                isAnalyzing = false,
                                errorMessage = if (result.invalidPhotoIndices.isNotEmpty()) {
                                    "Photo${if (result.invalidPhotoIndices.size > 1) "s" else ""} " +
                                        "${result.invalidPhotoIndices.map { i -> i + 1 }} need${if (result.invalidPhotoIndices.size == 1) "s" else ""} " +
                                        "a retake$reasonSuffix"
                                } else {
                                    result.rejectionReason ?: "Could not complete posture analysis. Please try again with clear photos."
                                }
                            )
                        }
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            isAnalyzing = false,
                            errorMessage = (e as? OpenRouterException)?.message ?: "Something went wrong analyzing those photos. Try again."
                        )
                    }
                }
            )
        }
    }

    fun deleteScan(scan: com.kevan.hangry.data.local.entity.PostureScanEntity) {
        viewModelScope.launch {
            postureScanRepository.delete(scan)
            PosturePhotoStorage.deletePhotos(scan.photoPaths())
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    companion object {
        fun provideFactory(
            context: Context,
            postureScanRepository: PostureScanRepository,
            postureAnalyzer: PostureAnalyzer,
            userProfileRepository: UserProfileRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return PostureViewModel(
                    context = context.applicationContext,
                    postureScanRepository = postureScanRepository,
                    postureAnalyzer = postureAnalyzer,
                    userProfileRepository = userProfileRepository
                ) as T
            }
        }
    }
}
