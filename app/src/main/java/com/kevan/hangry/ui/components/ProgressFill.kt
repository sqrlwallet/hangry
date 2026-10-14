package com.kevan.hangry.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

/**
 * The filled part of a progress bar, drawn inside its track. [progress] is read while drawing,
 * so an animated fill only redraws - the bar isn't recomposed and re-measured on every frame.
 * [colors] run left to right across the filled part (one color for a solid fill).
 */
@Composable
fun ProgressFill(
    progress: () -> Float,
    colors: List<Color>,
    cornerRadius: Dp,
    modifier: Modifier = Modifier
) {
    Spacer(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                val fraction = progress().coerceIn(0f, 1f)
                if (fraction <= 0.001f) return@drawBehind
                val width = size.width * fraction
                val radius = CornerRadius(cornerRadius.toPx())
                if (colors.size == 1) {
                    drawRoundRect(color = colors[0], size = Size(width, size.height), cornerRadius = radius)
                } else {
                    drawRoundRect(
                        brush = Brush.horizontalGradient(colors, startX = 0f, endX = width),
                        size = Size(width, size.height),
                        cornerRadius = radius
                    )
                }
            }
    )
}
