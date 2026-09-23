package com.kevan.hangry.data.storage

import android.content.Context
import com.kevan.hangry.data.local.HangryDatabase
import com.kevan.hangry.data.local.dao.FoodLogDao
import com.kevan.hangry.data.local.dao.PostureScanDao
import com.kevan.hangry.domain.repository.LocalStorageManager
import com.kevan.hangry.domain.repository.StorageBreakdown
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

class DefaultLocalStorageManager(
    private val context: Context,
    private val database: HangryDatabase,
    private val foodLogDao: FoodLogDao,
    private val postureScanDao: PostureScanDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : LocalStorageManager {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getStorageBreakdown(): StorageBreakdown = withContext(ioDispatcher) {
        val dbFile = context.getDatabasePath("hangry.db")
        val dbBytes = if (dbFile.exists()) dbFile.length() else 0L

        val walFile = File(dbFile.path + "-wal")
        val walBytes = if (walFile.exists()) walFile.length() else 0L

        val shmFile = File(dbFile.path + "-shm")
        val shmBytes = if (shmFile.exists()) shmFile.length() else 0L

        val foodPhotosDir = File(context.filesDir, "food_photos")
        val foodPhotosBytes = directorySize(foodPhotosDir)

        val posturePhotosDir = File(context.filesDir, "posture_photos")
        val posturePhotosBytes = directorySize(posturePhotosDir)

        val cacheBytes = directorySize(context.cacheDir)

        val total = dbBytes + walBytes + shmBytes + foodPhotosBytes + posturePhotosBytes + cacheBytes

        StorageBreakdown(
            databaseBytes = dbBytes,
            walBytes = walBytes + shmBytes,
            foodPhotosBytes = foodPhotosBytes,
            posturePhotosBytes = posturePhotosBytes,
            cacheBytes = cacheBytes,
            totalBytes = total
        )
    }

    override suspend fun cleanOrphanedFiles(): Int = withContext(ioDispatcher) {
        var deletedCount = 0

        // 1. Food photos
        val validFoodPhotos = foodLogDao.getAllPhotoPaths().toHashSet()
        val foodPhotosDir = File(context.filesDir, "food_photos")
        if (foodPhotosDir.exists() && foodPhotosDir.isDirectory) {
            foodPhotosDir.listFiles()?.forEach { file ->
                if (file.isFile && !validFoodPhotos.contains(file.absolutePath)) {
                    if (file.delete()) deletedCount++
                }
            }
        }

        // 2. Posture photos
        val postureJsons = postureScanDao.getAllPhotoPathsJson()
        val validPosturePhotos = HashSet<String>()
        postureJsons.forEach { jsonStr ->
            runCatching {
                val list = json.decodeFromString<List<String>>(jsonStr)
                validPosturePhotos.addAll(list)
            }
        }

        val posturePhotosDir = File(context.filesDir, "posture_photos")
        if (posturePhotosDir.exists() && posturePhotosDir.isDirectory) {
            posturePhotosDir.walkBottomUp().forEach { file ->
                if (file.isFile) {
                    if (!validPosturePhotos.contains(file.absolutePath)) {
                        if (file.delete()) deletedCount++
                    }
                } else if (file.isDirectory && file != posturePhotosDir) {
                    // Remove empty scan subdirectories
                    if (file.listFiles()?.isEmpty() == true) {
                        file.delete()
                    }
                }
            }
        }

        // 3. Captured images cache
        val capturedImagesDir = File(context.cacheDir, "captured_images")
        if (capturedImagesDir.exists() && capturedImagesDir.isDirectory) {
            capturedImagesDir.listFiles()?.forEach { file ->
                if (file.isFile) {
                    if (file.delete()) deletedCount++
                }
            }
        }

        deletedCount
    }

    override suspend fun optimizeDatabase(): Unit = withContext(ioDispatcher) {
        database.checkpointAndOptimize()
    }

    override suspend fun clearCache(): Long = withContext(ioDispatcher) {
        var freedBytes = 0L
        context.cacheDir?.walkBottomUp()?.forEach { file ->
            if (file.isFile) {
                val len = file.length()
                if (file.delete()) {
                    freedBytes += len
                }
            } else if (file.isDirectory && file != context.cacheDir) {
                file.delete()
            }
        }
        freedBytes
    }

    private fun directorySize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var size = 0L
        dir.walkTopDown().forEach { file ->
            if (file.isFile) {
                size += file.length()
            }
        }
        return size
    }
}
