package com.kevan.hangry.ui.bodyfat

import android.net.Uri
import com.kevan.hangry.data.local.entity.BodyFatScanEntity
import com.kevan.hangry.domain.model.BiologicalSex
import com.kevan.hangry.domain.model.BodyFatAnalysisResult
import com.kevan.hangry.domain.model.BodyFatCalculationResult

data class BodyFatCalculatorUiState(
    val heightCm: String = "",
    val weightKg: String = "",
    val age: String = "",
    val biologicalSex: BiologicalSex? = null,
    val neckCm: String = "",
    val chestCm: String = "",
    val waistCm: String = "",
    val hipCm: String = "",
    val photos: List<Uri> = emptyList(),
    val algorithmicResult: BodyFatCalculationResult? = null,
    val isAiAnalyzing: Boolean = false,
    val aiAnalysisResult: BodyFatAnalysisResult? = null,
    val aiErrorMessage: String? = null,
    val isAiEnabled: Boolean = false,
    val hasApiKey: Boolean = false,
    val savedScans: List<BodyFatScanEntity> = emptyList(),
    val latestSavedScan: BodyFatScanEntity? = null,
    val isSaving: Boolean = false,
    val saveSuccessMessage: String? = null
)
