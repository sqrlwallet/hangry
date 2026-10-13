package com.kevan.hangry.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kevan.hangry.R
import com.kevan.hangry.data.prefs.ThemeMode
import com.kevan.hangry.data.prefs.ThemePrefs
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens

@Composable
internal fun AppearanceSection() {
    val context = LocalContext.current
    val currentMode = ThemePrefs.themeMode(context)

    val modeLabels = mapOf(
        ThemeMode.SYSTEM to stringResource(R.string.settings_appearance_theme_system),
        ThemeMode.DARK to stringResource(R.string.settings_appearance_theme_dark),
        ThemeMode.LIGHT to stringResource(R.string.settings_appearance_theme_light)
    )

    SettingsCollapsibleSection(
        title = stringResource(R.string.settings_appearance_title),
        summary = stringResource(R.string.settings_appearance_summary, modeLabels.getValue(currentMode)),
        icon = Icons.Default.Palette
    ) {
        ThemeChoiceRow(
            icon = Icons.Default.DarkMode,
            title = stringResource(R.string.settings_appearance_theme_title),
            options = modeLabels,
            selected = currentMode,
            onSelect = { ThemePrefs.setThemeMode(context, it) }
        )
    }
}

@Composable
private fun ThemeChoiceRow(
    icon: ImageVector,
    title: String,
    options: Map<ThemeMode, String>,
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit
) {
    val tokens = LocalHangryTokens.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tokens.textSecondary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(HangryTokens.Spacing.m))
        Text(title, style = MaterialTheme.typography.bodyLarge, color = tokens.textPrimary, modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { (mode, label) ->
                FilterChip(selected = mode == selected, onClick = { onSelect(mode) }, label = { Text(label) })
            }
        }
    }
}
