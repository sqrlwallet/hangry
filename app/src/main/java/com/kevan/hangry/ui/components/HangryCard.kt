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
import androidx.compose.ui.unit.Dp
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens

@Composable
fun HangryCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = HangryTokens.CornerRadii.medium,
    contentPadding: Dp = HangryTokens.Spacing.m,
    content: @Composable ColumnScope.() -> Unit
) {
    val tokens = LocalHangryTokens.current
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(cornerRadius),
        colors = CardDefaults.cardColors(
            containerColor = tokens.cardBackground
        ),
        border = BorderStroke(Dp.Hairline, tokens.cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = HangryTokens.Elevations.card)
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}
