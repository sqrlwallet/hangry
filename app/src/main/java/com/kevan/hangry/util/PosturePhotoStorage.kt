package com.kevan.hangry.util

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Moves accepted posture photos from the temp capture cache into app-private permanent storage
 * (never MediaStore/public gallery, never re-uploaded anywhere after the one analysis call).
 * Deletes them back out again if a scan is discarded or removed.
 */
object PosturePhotoStorage {
    private const val DIR_NAME = "posture_photos"

    /** Each scan gets its own timestamp-keyed folder - independent of the Room row id, which
     *  isn't known until after insert. */
    fun moveToPermanentStorage(context: Context, tempUris: List<Uri>): List<String> {
        val dir = File(context.filesDir, "$DIR_NAME/${System.currentTimeMillis()}").apply { mkdirs() }
        return tempUris.mapIndexedNotNull { index, uri ->
            runCatching {
                val dest = File(dir, "photo_$index.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
                dest.absolutePath
            }.getOrNull()
        }
    }

    fun deletePhotos(paths: List<String>) {
        paths.forEach { path ->
            val file = File(path)
            file.delete()
            file.parentFile?.let { if (it.listFiles()?.isEmpty() == true) it.delete() }
        }
    }
}
