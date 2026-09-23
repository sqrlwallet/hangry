# Hangry — Brand Assets & Attribution

## 1. Brand Asset Inventory

| Asset Name | Resource Path | Description | Format |
|---|---|---|---|
| **Hangry Logo** | `app/src/main/res/drawable/hangry_logo.xml` | Primary brand emblem: stylized flame + vital pulse | Vector Drawable (XML) |
| **Adaptive Launcher Foreground** | `app/src/main/res/drawable/ic_launcher_foreground.xml` | Modern app icon foreground centered on ember badge | Vector Drawable (XML) |
| **Adaptive Launcher Background** | `app/src/main/res/drawable/ic_launcher_background.xml` | Warm peach radial gradient (`#FFE3D3` → `#FFB38A`) | Vector Drawable (XML) |
| **Adaptive Launcher Monochrome** | `app/src/main/res/drawable/ic_launcher_monochrome.xml` | Themed/monochrome layer for Android 13+ (flame silhouette only) | Vector Drawable (XML) |
| **Splash Screen Icon** | `app/src/main/res/drawable/hangry_logo.xml` | Centered vector icon during app cold start | Vector Drawable (XML) |

---

## 2. Emblem Design Concept

The Hangry emblem combines two organic, vital motifs as a single flat silhouette (no gradients, glows, or shading, so it stays legible at small launcher sizes):
1. **The Flame**: Represents metabolism, energy expenditure, hunger for life, and personal drive. Rendered as one solid-color teardrop shape with a small inner flick for character.
2. **The Pulse Wave**: A heartbeat blip stroked through the flame's lower third in the background color, reading as a cutout rather than a second overlapping color.

Content is kept within the standard ~66dp adaptive-icon safe zone (radius ~33dp from the 108dp canvas center) so it isn't clipped by circular, squircle, or rounded-square launcher masks.

Colors used:
- Ember Coral `#FF7043` (flame fill, matches `EmberPrimaryDark` in `Color.kt`)
- Background Dark `#0C1014` / `#101014` (pulse cutout stroke, matches the adaptive icon background / app dark background)
- Pure White `#FFFFFF` (monochrome/themed icon layer only)

---

## 3. Theme Preview Screen

To verify visual tokens without navigating all app states, the application includes a dedicated **Theme Preview Screen** accessible via the top app bar in development builds:
- Displays all Light & Dark color swatches.
- Demonstrates score-state badges (Primed, Balanced, Rebuild, Baseline Building).
- Visualizes typography scale with large metric numbers.
- Previews the Hangry logo across varied surface elevations.

---

## 4. Attribution & Licensing Notes

- All vector path graphics created for Hangry are original provisional designs released under the Apache 2.0 license.
- No proprietary visual assets, fonts, trade dress, or icons from WHOOP, Oura, Garmin, or Apple have been replicated or imported.
- All typography utilizes Android platform default Roboto / Roboto Flex system fonts to avoid bundling third-party font binaries.
