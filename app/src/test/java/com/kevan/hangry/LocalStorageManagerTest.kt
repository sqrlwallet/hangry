package com.kevan.hangry

import com.kevan.hangry.domain.repository.StorageBreakdown
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class LocalStorageManagerTest {

    @Test
    fun testStorageBreakdown_formatBytes() {
        assertEquals("500 B", StorageBreakdown.formatBytes(500L))
        assertEquals("1.0 KB", StorageBreakdown.formatBytes(1024L))
        assertEquals("1.5 KB", StorageBreakdown.formatBytes(1536L))
        assertEquals("1.0 MB", StorageBreakdown.formatBytes(1024L * 1024L))
        assertEquals("10.5 MB", StorageBreakdown.formatBytes((10.5 * 1024 * 1024).toLong()))
        assertEquals("1.00 GB", StorageBreakdown.formatBytes(1024L * 1024L * 1024L))
        assertEquals("2.50 GB", StorageBreakdown.formatBytes((2.5 * 1024L * 1024L * 1024L).toLong()))
    }

    @Test
    fun testOrphanPhotoCleanup_identifiesAndDeletesUnreferencedFiles() {
        val tempFilesDir = Files.createTempDirectory("test_files_dir").toFile()
        val tempCacheDir = Files.createTempDirectory("test_cache_dir").toFile()

        try {
            // Setup food photos
            val foodDir = File(tempFilesDir, "food_photos").apply { mkdirs() }
            val validFoodFile = File(foodDir, "food_valid.jpg").apply { writeText("food1") }
            val orphanFoodFile = File(foodDir, "food_orphan.jpg").apply { writeText("orphan1") }

            // Setup posture photos
            val postureDir = File(tempFilesDir, "posture_photos").apply { mkdirs() }
            val scanDir = File(postureDir, "12345").apply { mkdirs() }
            val validPostureFile = File(scanDir, "posture_valid.jpg").apply { writeText("posture1") }
            val orphanPostureFile = File(scanDir, "posture_orphan.jpg").apply { writeText("orphan_posture") }

            // Setup cache images
            val cacheCapturedDir = File(tempCacheDir, "captured_images").apply { mkdirs() }
            val tempCacheFile = File(cacheCapturedDir, "temp_capture.jpg").apply { writeText("temp_photo") }

            val validFoodPaths = setOf(validFoodFile.absolutePath)
            val validPosturePaths = setOf(validPostureFile.absolutePath)

            // Simulate the orphan cleanup algorithm
            var deletedCount = 0

            // 1. Food photos cleanup
            foodDir.listFiles()?.forEach { file ->
                if (file.isFile && !validFoodPaths.contains(file.absolutePath)) {
                    if (file.delete()) deletedCount++
                }
            }

            // 2. Posture photos cleanup
            postureDir.walkBottomUp().forEach { file ->
                if (file.isFile) {
                    if (!validPosturePaths.contains(file.absolutePath)) {
                        if (file.delete()) deletedCount++
                    }
                } else if (file.isDirectory && file != postureDir) {
                    if (file.listFiles()?.isEmpty() == true) {
                        file.delete()
                    }
                }
            }

            // 3. Cache images cleanup
            cacheCapturedDir.listFiles()?.forEach { file ->
                if (file.isFile) {
                    if (file.delete()) deletedCount++
                }
            }

            assertEquals(3, deletedCount)
            assertTrue("Valid food photo should remain", validFoodFile.exists())
            assertFalse("Orphan food photo should be deleted", orphanFoodFile.exists())
            assertTrue("Valid posture photo should remain", validPostureFile.exists())
            assertFalse("Orphan posture photo should be deleted", orphanPostureFile.exists())
            assertFalse("Temp capture file should be deleted", tempCacheFile.exists())
        } finally {
            tempFilesDir.deleteRecursively()
            tempCacheDir.deleteRecursively()
        }
    }

    @Test
    fun testPostureJsonSerialization_extractsValidPaths() {
        val json = Json { ignoreUnknownKeys = true }
        val rawJson = """["/data/user/0/com.kevan.hangry/files/posture_photos/1/photo_0.jpg", "/data/user/0/com.kevan.hangry/files/posture_photos/1/photo_1.jpg"]"""
        
        val paths = json.decodeFromString<List<String>>(rawJson)
        assertEquals(2, paths.size)
        assertEquals("/data/user/0/com.kevan.hangry/files/posture_photos/1/photo_0.jpg", paths[0])
        assertEquals("/data/user/0/com.kevan.hangry/files/posture_photos/1/photo_1.jpg", paths[1])
    }
}
