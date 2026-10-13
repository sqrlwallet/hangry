package com.kevan.hangry.ui.widget

import android.content.Context
import android.os.Build
import android.widget.RemoteViews
import androidx.annotation.ColorRes

/**
 * Text colours set from code, as colour resources so they follow the phone's dark mode like the
 * layouts do (res/values and values-notnight widget_colors.xml). On Android 12+ the launcher
 * resolves the resource itself, so the colour flips with dark mode without waiting for a refresh.
 */
internal object WidgetColors {

    /** Drawn into bitmaps (rings, bars): a neutral track that reads on both the dark and light surface. */
    const val TRACK = 0x33908894

    fun setTextColor(context: Context, views: RemoteViews, viewId: Int, @ColorRes color: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setColor(viewId, "setTextColor", color)
        } else {
            views.setTextColor(viewId, context.getColor(color))
        }
    }
}
