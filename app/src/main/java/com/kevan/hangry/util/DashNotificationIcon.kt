package com.kevan.hangry.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.kevan.hangry.R

/** Dash's face as the large picture on Hangry's notifications, decoded once and reused. */
object DashNotificationIcon {
    @Volatile
    private var cached: Bitmap? = null

    /** Notification pictures show at about 64dp; 192px covers the densest screens. */
    private const val SIZE_PX = 192

    fun get(context: Context): Bitmap? = cached ?: runCatching {
        val full = BitmapFactory.decodeResource(context.resources, R.drawable.dash_avatar)
        Bitmap.createScaledBitmap(full, SIZE_PX, SIZE_PX, true).also { if (it !== full) full.recycle() }
    }.getOrNull()?.also { cached = it }
}
