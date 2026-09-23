package com.kevan.hangry.domain.repository

data class StorageBreakdown(
    val databaseBytes: Long,
    val walBytes: Long,
    val foodPhotosBytes: Long,
    val posturePhotosBytes: Long,
    val cacheBytes: Long,
    val totalBytes: Long
) {
    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes < 1024) return "$bytes B"
            val kb = bytes / 1024.0
            if (kb < 1024) return "%.1f KB".format(java.util.Locale.US, kb)
            val mb = kb / 1024.0
            if (mb < 1024) return "%.1f MB".format(java.util.Locale.US, mb)
            val gb = mb / 1024.0
            return "%.2f GB".format(java.util.Locale.US, gb)
        }
    }
}

interface LocalStorageManager {
    suspend fun getStorageBreakdown(): StorageBreakdown
    suspend fun cleanOrphanedFiles(): Int
    suspend fun optimizeDatabase()
    suspend fun clearCache(): Long
}
