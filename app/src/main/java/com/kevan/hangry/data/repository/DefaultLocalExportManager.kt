package com.kevan.hangry.data.repository

import com.kevan.hangry.domain.repository.DailySummaryRepository
import com.kevan.hangry.domain.repository.LocalExportManager
import kotlinx.coroutines.flow.firstOrNull
import java.time.LocalDate

class DefaultLocalExportManager(
    private val dailySummaryRepository: DailySummaryRepository
) : LocalExportManager {

    override suspend fun exportDataAsJson(): String {
        val summaries = dailySummaryRepository.getAllSummaries().firstOrNull() ?: emptyList()
        val scores = dailySummaryRepository.getAllScores().firstOrNull() ?: emptyList()

        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"appName\": \"Hangry\",\n")
        sb.append("  \"exportTimestamp\": \"${System.currentTimeMillis()}\",\n")
        sb.append("  \"dailySummaries\": [\n")
        summaries.forEachIndexed { index, s ->
            sb.append("    {\"date\": \"${s.date}\", \"sleepMinutes\": ${s.sleepDurationMinutes}, \"steps\": ${s.steps}, \"activeCalories\": ${s.activeCalories}, \"totalCalories\": ${s.totalCalories}, \"restingHeartRate\": ${s.restingHeartRate}, \"hrvRmssd\": ${s.hrvRmssd}, \"vo2Max\": ${s.vo2Max}, \"spo2\": ${s.spo2Percentage}, \"respRate\": ${s.respiratoryRate}, \"bpSystolic\": ${s.bloodPressureSystolic}, \"bpDiastolic\": ${s.bloodPressureDiastolic}, \"hydrationLiters\": ${s.hydrationLiters}, \"bodyFatPercentage\": ${s.bodyFatPercentage}}")
            if (index < summaries.size - 1) sb.append(",")
            sb.append("\n")
        }
        sb.append("  ],\n")
        sb.append("  \"recoveryScores\": [\n")
        scores.forEachIndexed { index, r ->
            sb.append("    {\"date\": \"${r.date}\", \"score\": ${r.score}, \"confidence\": \"${r.confidence}\", \"state\": \"${r.state}\"}")
            if (index < scores.size - 1) sb.append(",")
            sb.append("\n")
        }
        sb.append("  ]\n")
        sb.append("}\n")
        return sb.toString()
    }

    override suspend fun exportDataAsCsv(): String {
        val summaries = dailySummaryRepository.getAllSummaries().firstOrNull() ?: emptyList()

        val sb = StringBuilder()
        sb.append("Date,SleepMinutes,Steps,DistanceMeters,ActiveCalories,TotalCalories,RestingHeartRate,HrvRmssd,TrainingLoad,Vo2Max,SpO2,HydrationLiters,BodyFatPct\n")
        summaries.forEach { s ->
            sb.append("${s.date},${s.sleepDurationMinutes ?: ""},${s.steps ?: ""},${s.distanceMeters ?: ""},${s.activeCalories ?: ""},${s.totalCalories ?: ""},${s.restingHeartRate ?: ""},${s.hrvRmssd ?: ""},${s.dailyTrainingLoad ?: ""},${s.vo2Max ?: ""},${s.spo2Percentage ?: ""},${s.hydrationLiters ?: ""},${s.bodyFatPercentage ?: ""}\n")
        }
        return sb.toString()
    }
}
