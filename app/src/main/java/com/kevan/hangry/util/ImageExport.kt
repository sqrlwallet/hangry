package com.kevan.hangry.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.io.File
import java.time.LocalDate

/**
 * Copies a photo into Pictures/Hangry so it shows up in the gallery - only when the user asks.
 * Android 10+ needs no storage permission for this; on Android 9 use [copyImageTo] with a file
 * the user picks instead.
 */
@RequiresApi(29)
fun Context.saveImageToGallery(source: File, displayName: String): Boolean = runCatching {
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Hangry")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
    try {
        contentResolver.openOutputStream(uri)?.use { out -> source.inputStream().use { it.copyTo(out) } }
            ?: error("No output stream")
        contentResolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
    } catch (e: Exception) {
        contentResolver.delete(uri, null, null)
        throw e
    }
    true
}.getOrDefault(false)

/** Copies a photo to a file the user picked (e.g. from ActivityResultContracts.CreateDocument). */
fun Context.copyImageTo(source: File, target: Uri): Boolean = runCatching {
    contentResolver.openOutputStream(target)?.use { out -> source.inputStream().use { it.copyTo(out) } } != null
}.getOrDefault(false)

/** e.g. "Hangry_Chicken_Caesar_Salad_2026-10-14.jpg". */
fun foodPhotoFileName(foodName: String, date: LocalDate): String {
    val name = foodName.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_').take(40).ifBlank { "Meal" }
    return "Hangry_${name}_$date.jpg"
}
