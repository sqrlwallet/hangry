package com.kevan.hangry.ui.posture

import android.net.Uri
import com.kevan.hangry.data.local.entity.PostureScanEntity

data class PostureUiState(
    val scans: List<PostureScanEntity> = emptyList(),
    val latestScan: PostureScanEntity? = null,
    val aiFeaturesEnabled: Boolean = false,
    val capturedPhotos: List<Uri> = emptyList(),
    val isAnalyzing: Boolean = false,
    /** Set once analysis succeeds and the scan is already saved - screen shows results, no separate confirm step. */
    val justSavedScan: PostureScanEntity? = null,
    val errorMessage: String? = null
)

const val MIN_POSTURE_PHOTOS = 3
const val MAX_POSTURE_PHOTOS = 5
