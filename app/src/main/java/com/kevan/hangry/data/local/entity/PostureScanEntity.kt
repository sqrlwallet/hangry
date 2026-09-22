package com.kevan.hangry.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

/**
 * One completed posture assessment. [findingsJson]/[exercisesJson]/[photoPathsJson] hold
 * kotlinx.serialization-encoded lists (List<String> / List<PostureExercise> / List<String>) -
 * a normalized child-table schema isn't worth it for data that's always read/written as one
 * complete scan. Photos referenced by [photoPathsJson] live in app-private storage
 * (see util/PosturePhotoStorage) - never Health Connect, never re-uploaded anywhere.
 */
@Entity(
    tableName = "posture_scans",
    indices = [Index(value = ["date"])]
)
data class PostureScanEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: LocalDate,
    val timestamp: Instant = Instant.now(),
    val score: Int,
    val findingsJson: String,
    val exercisesJson: String,
    val photoPathsJson: String
)
