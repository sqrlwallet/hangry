package com.kevan.hangry.ui.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import com.kevan.hangry.R
import java.io.File

/**
 * Writes a card to `cache/share_cards/` (shared through the FileProvider) and opens the system
 * share sheet with it. Only the latest image is kept: sharing another deletes the old one.
 */
object ShareCardFiles {

    fun dir(context: Context) = File(context.cacheDir, "share_cards")

    /** Paints [card] at full size into a PNG; call off the main thread. */
    fun write(context: Context, card: ShareCard): File {
        val dir = dir(context).apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "hangry-${card.kind}.png")
        val bitmap = ShareCardRenderer(context).render(card)
        try {
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            bitmap.recycle()
        }
        return file
    }

    fun share(context: Context, file: File, card: ShareCard) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, card.shareText)
            // Lets the share sheet show the image as its preview.
            clipData = ClipData.newRawUri(card.title, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, context.getString(R.string.share_chooser_title)))
    }
}
