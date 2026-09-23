package com.kevan.hangry.ui.theme

import androidx.compose.ui.graphics.Color

// ── Brand palette (raw values) ──────────────────────────────────────────────
// Use these directly only for fills, illustrations and large graphics. For text
// and icons, use the theme-aware tokens (MaterialTheme.colorScheme / LocalHangryTokens),
// which switch to contrast-safe shades per theme.
val Pumpkin = Color(0xFFFF7E1D)        // Signature brand color — energy, approachability
val ShipGray = Color(0xFF413D45)       // Primary typography — softer than pure black
val BlueRibbon = Color(0xFF0C6FF9)     // Primary CTA — buttons and key highlights
val Supernova = Color(0xFFFFCE00)      // Warmth — illustrations and highlights
val AzureRadiance = Color(0xFF00A4FF)  // Calm accent
val GreenHaze = Color(0xFF01A652)      // Success states
val Cream = Color(0xFFF9F4F2)          // Warm canvas — used instead of stark white

// ── Contrast-safe shades for text/icons on light surfaces (≥4.5:1 on white) ──
val PumpkinDeep = Color(0xFFB8520A)
val GreenHazeDeep = Color(0xFF00833F)
val SupernovaDeep = Color(0xFF8F6A00)
val AzureDeep = Color(0xFF0079C2)

// ── Lifted shades for text/icons on dark surfaces ───────────────────────────
val BlueRibbonLight = Color(0xFF6AA8FF)

// ── Light theme (default): warm cream canvas ────────────────────────────────
val BackgroundLight = Cream
val SurfaceLight = Color(0xFFFFFDFC)
val SurfaceVariantLight = Color(0xFFF2ECE8)
val OutlineLight = Color(0xFFE7DFDA)
val TextPrimaryLight = ShipGray
val TextSecondaryLight = Color(0xFF6E6873)
val TextMutedLight = Color(0xFF7D7681)

// ── Dark theme: warm charcoal derived from Ship Gray (no cold blue-blacks) ──
val BackgroundDark = Color(0xFF1C1A1F)
val SurfaceDark = Color(0xFF252329)
val SurfaceVariantDark = Color(0xFF2F2C33)
val OutlineDark = Color(0xFF3A363F)
val TextPrimaryDark = Cream
val TextSecondaryDark = Color(0xFFB5AEB8)
val TextMutedDark = Color(0xFF948D98)

// Primary CTA gradient (hero buttons / FAB). White label stays ≥4.5:1 across the whole ramp.
val CtaGradient = listOf(BlueRibbon, Color(0xFF0A5FD6))
