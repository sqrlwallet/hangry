package com.kevan.hangry.ui.nutrition

import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kevan.hangry.R
import com.kevan.hangry.util.copyImageTo
import com.kevan.hangry.util.foodPhotoFileName
import com.kevan.hangry.util.saveImageToGallery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

private const val MAX_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f

/**
 * A meal photo, whole and full screen: pinch or double-tap to zoom, drag to look around, and
 * save a copy to the phone's Pictures/Hangry folder.
 */
@Composable
fun FoodPhotoViewer(photoPath: String, foodName: String, date: LocalDate, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val file = remember(photoPath) { File(photoPath) }
    val fileName = remember(foodName, date) { foodPhotoFileName(foodName, date) }

    fun report(saved: Boolean, where: Int) {
        Toast.makeText(context, if (saved) where else R.string.nutrition_photo_save_failed, Toast.LENGTH_SHORT).show()
    }

    // Android 9 can't write to the gallery without a storage permission: save to a picked file.
    val saveAsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { uri ->
        if (uri != null) scope.launch {
            report(withContext(Dispatchers.IO) { context.copyImageTo(file, uri) }, R.string.nutrition_photo_saved_file)
        }
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    // Keep the zoomed photo covering the screen rather than dragging off into the black.
    fun clamp(o: Offset, s: Float): Offset {
        val maxX = size.width * (s - 1f) / 2f
        val maxY = size.height * (s - 1f) / 2f
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .onSizeChanged { size = it }
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(1f, MAX_ZOOM)
                        // Zoom around the fingers rather than the middle of the screen.
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val focus = (centroid - center - offset) * (1 - newScale / scale)
                        offset = clamp(offset + focus + pan, newScale)
                        scale = newScale
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { tap ->
                            if (scale > 1f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                val center = Offset(size.width / 2f, size.height / 2f)
                                offset = clamp((center - tap) * (DOUBLE_TAP_ZOOM - 1f), DOUBLE_TAP_ZOOM)
                                scale = DOUBLE_TAP_ZOOM
                            }
                        }
                    )
                }
        ) {
            AsyncImage(
                // Sharp enough to zoom into, without decoding a 12 MP photo at full size.
                model = ImageRequest.Builder(context).data(file).size(2560).build(),
                contentDescription = foodName,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(8.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
            ) {
                val buttonColors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.45f),
                    contentColor = Color.White
                )
                IconButton(onClick = onDismiss, colors = buttonColors) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.nutrition_photo_close))
                }
                IconButton(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= 29) {
                            scope.launch {
                                report(withContext(Dispatchers.IO) { context.saveImageToGallery(file, fileName) }, R.string.nutrition_photo_saved_gallery)
                            }
                        } else {
                            saveAsLauncher.launch(fileName)
                        }
                    },
                    colors = buttonColors
                ) {
                    Icon(Icons.Default.Download, contentDescription = stringResource(R.string.nutrition_photo_save))
                }
            }
        }
    }
}
