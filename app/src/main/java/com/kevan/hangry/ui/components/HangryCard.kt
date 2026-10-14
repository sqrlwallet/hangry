package com.kevan.hangry.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens

import androidx.compose.ui.geometry.Offset
import com.kevan.hangry.ui.theme.SurfaceDark

@Composable
fun HangryCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = HangryTokens.CornerRadii.medium,
    contentPadding: Dp = HangryTokens.Spacing.card,
    content: @Composable ColumnScope.() -> Unit
) {
    val tokens = LocalHangryTokens.current
    val isDark = tokens.cardBackground == SurfaceDark
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(cornerRadius),
        colors = CardDefaults.cardColors(
            containerColor = tokens.cardBackground
        ),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = if (isDark) {
                    listOf(
                        Color.White.copy(alpha = 0.14f),
                        Color.White.copy(alpha = 0.03f)
                    )
                } else {
                    listOf(
                        tokens.edgeHighlight.copy(alpha = (tokens.edgeHighlight.alpha * 1.5f).coerceAtMost(0.20f)),
                        tokens.cardBorder.copy(alpha = 0.6f)
                    )
                },
                start = Offset.Zero,
                end = Offset.Infinite
            )
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = HangryTokens.Elevations.card)
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}
