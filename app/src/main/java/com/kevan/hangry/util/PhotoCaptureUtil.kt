package com.kevan.hangry.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max

/** Exposes "take a photo" / "pick from gallery" as two simple calls, hiding the launcher/FileProvider plumbing. */
class PhotoCaptureLauncher internal constructor(
    val takePhoto: () -> Unit,
    val pickFromGallery: () -> Unit
)

/** Multi-photo capture launcher for batch photo selection from the gallery or sequential camera shots. */
class MultiPhotoCaptureLauncher internal constructor(
    val takePhoto: () -> Unit,
    val pickFromGallery: () -> Unit
)

/**
 * Shared camera/gallery capture entry point for single photo capture (e.g. food photos).
 */
@Composable
fun rememberPhotoCaptureLauncher(onPhotoCaptured: (Uri) -> Unit): PhotoCaptureLauncher {
    val context = LocalContext.current
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (success && uri != null) {
            onPhotoCaptured(uri)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(onPhotoCaptured)
    }

    return remember(context) {
        PhotoCaptureLauncher(
            takePhoto = {
                val uri = createTempImageUri(context)
                pendingCameraUri = uri
                cameraLauncher.launch(uri)
            },
            pickFromGallery = {
                galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        )
    }
}

/**
 * Multi-photo camera/gallery capture entry point for posture analysis (up to 5 photos) and
 * body fat analysis (up to 3 photos). Photos selected in a single gallery interaction are returned together.
 */
@Composable
fun rememberMultiPhotoCaptureLauncher(
    maxItems: Int = 5,
    onPhotosCaptured: (List<Uri>) -> Unit
): MultiPhotoCaptureLauncher {
    val context = LocalContext.current
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (success && uri != null) {
            onPhotosCaptured(listOf(uri))
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems.coerceAtLeast(2))
    ) { uris ->
        if (uris.isNotEmpty()) {
            onPhotosCaptured(uris)
        }
    }

    return remember(context, maxItems) {
        MultiPhotoCaptureLauncher(
            takePhoto = {
                val uri = createTempImageUri(context)
                pendingCameraUri = uri
                cameraLauncher.launch(uri)
            },
            pickFromGallery = {
                galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        )
    }
}

private fun createTempImageUri(context: Context): Uri {
    val imagesDir = File(context.cacheDir, "captured_images").apply { mkdirs() }
    val file = File(imagesDir, "capture_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

/**
 * Reads an image Uri, downscales it to [maxDimension] on the long edge, and JPEG-encodes it as
 * base64 - what actually gets sent to the AI model. Full-resolution photos would be slow to
 * upload and unnecessarily large for a vision model that doesn't benefit from more detail.
 */
fun Context.readImageAsBase64Jpeg(uri: Uri, maxDimension: Int = 1024, quality: Int = 82): String? {
    return runCatching {
        val original = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            ?: return null
        val scaled = downscale(original, maxDimension)
        val output = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, output)
        if (scaled !== original) original.recycle()
        android.util.Base64.encodeToString(output.toByteArray(), android.util.Base64.NO_WRAP)
    }.getOrNull()
}

private fun downscale(bitmap: Bitmap, maxDimension: Int): Bitmap {
    val longEdge = max(bitmap.width, bitmap.height)
    if (longEdge <= maxDimension) return bitmap
    val scale = maxDimension.toFloat() / longEdge
    val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
    val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(bitmap, width, height, true)
}

/** Deletes every file under the shared temp-capture cache dir - call after a scan/entry is saved or discarded. */
fun Context.clearCapturedImageCache() {
    File(cacheDir, "captured_images").listFiles()?.forEach { it.delete() }
}
