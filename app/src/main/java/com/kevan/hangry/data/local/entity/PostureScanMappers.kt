package com.kevan.hangry.data.local.entity

import com.kevan.hangry.domain.model.PostureExercise
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Keeps the JSON encode/decode for [PostureScanEntity]'s text columns in one place. */
private val json = Json { ignoreUnknownKeys = true }

fun PostureScanEntity.findings(): List<String> =
    runCatching { json.decodeFromString<List<String>>(findingsJson) }.getOrDefault(emptyList())

fun PostureScanEntity.exercises(): List<PostureExercise> =
    runCatching { json.decodeFromString<List<PostureExercise>>(exercisesJson) }.getOrDefault(emptyList())

fun PostureScanEntity.photoPaths(): List<String> =
    runCatching { json.decodeFromString<List<String>>(photoPathsJson) }.getOrDefault(emptyList())

fun buildPostureScanEntity(
    date: java.time.LocalDate,
    score: Int,
    findings: List<String>,
    exercises: List<PostureExercise>,
    photoPaths: List<String>
): PostureScanEntity = PostureScanEntity(
    date = date,
    score = score,
    findingsJson = json.encodeToString(findings),
    exercisesJson = json.encodeToString(exercises),
    photoPathsJson = json.encodeToString(photoPaths)
)
