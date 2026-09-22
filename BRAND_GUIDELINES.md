# Hangry — Brand Guidelines & Design System

## 1. Brand Essence & Positioning

- **Brand Name**: Hangry
- **Product Category**: Personal wellness, recovery, sleep, and fitness insights
- **Brand Personality**: Energetic, human, practical, motivating, honest, and approachable
- **Voice**: Concise, clear, conversational, encouraging, and non-judgmental
- **Tone**: Useful and confident without sounding clinical, alarmist, or preachy
- **Positioning**: Turn personal health data into simple daily actions users can understand and follow
- **Core Promise**: Help users understand their body, manage energy, and make better daily decisions
- **Primary Audience**: Active individuals, fitness enthusiasts, and everyday people who want practical recovery, sleep, and fitness insights without complexity or clinical anxiety
- **Guiding Principle**: **Data should inform the user, never intimidate or shame them.**

---

## 2. Voice, Tone & Supportive Copy System

### 2.1 Copy Principles
1. **Supportive, Not Clinical**: Use plain, conversational language. Avoid diagnostic or medical terms.
2. **Actionable, Not Alarmist**: Pair every metric with a constructive suggestion.
3. **Patience During Baseline Building**: Clearly celebrate calibration instead of complaining about lack of data.
4. **Honest About Sync**: Explain technical delays simply and politely.

### 2.2 Reusable Copy Matrix

| Context | Recommended Supportive Copy (Hangry) | Prohibited Shame/Clinical Copy |
|---|---|---|
| **Calibration / No Data** | *"Your baseline is still building"* | *"Not enough data"* / *"Incomplete data"* |
| **Low Recovery** | *"Take it easier today — recharge and rest"* | *"You are unhealthy"* / *"Poor recovery"* |
| **Moderate Recovery** | *"Balanced energy — ready for steady movement"* | *"Average condition"* / *"Moderate risk"* |
| **High Recovery** | *"You're primed for peak effort today"* | *"Optimal performance"* / *"High capacity"* |
| **Short Sleep** | *"Your sleep was shorter than your usual pattern"* | *"Poor sleep"* / *"Sleep deprivation"* |
| **Sync Issue** | *"We couldn't sync this data yet — we'll try again"* | *"Sync failed"* / *"Network error"* |
| **Empty Workouts** | *"No workouts logged yet today — rest is good too"* | *"0 workouts"* / *"Inactive today"* |
| **Wellness Disclaimer** | *"Hangry provides wellness insights, not medical advice. Consult your doctor for medical questions."* | (Missing or hidden disclaimer) |

---

## 3. Visual System Tokens (Provisional)

All visual tokens are centralized in Kotlin composables (`HangryTokens`, `HangryColors`, `HangryTypography`) to enable seamless brand asset swaps.

### 3.1 Color Palette

#### Primary Brand Colors (Energetic Sunset & Vitality)
- **Brand Primary**: Ember Coral `#FF5722` (Light) / `#FF7043` (Dark)
- **Brand Primary Container**: Warm Flame `#FFCCBC` (Light) / `#BF360C` (Dark)
- **Brand Secondary**: Vitality Teal `#00897B` (Light) / `#26A69A` (Dark)
- **Brand Tertiary**: Deep Slate `#37474F` (Light) / `#90A4AE` (Dark)

#### Score-State Semantic Colors
- **Primed / Optimal (70–100%)**: Vibrant Emerald `#2E7D32` (Light) / `#4CAF50` (Dark)
- **Balanced / Steady (40–69%)**: Vitality Amber `#F57C00` (Light) / `#FFA726` (Dark)
- **Rebuild / Rest (0–39%)**: Warm Coral `#D84315` (Light) / `#FF7043` (Dark)
- **Baseline Building**: Calm Slate `#546E7A` (Light) / `#78909C` (Dark)

#### Metric Chart Colors
- **Sleep**: Night Lavender `#7986CB`
- **HRV (RMSSD)**: Pulse Rose `#EC407A`
- **Resting Heart Rate**: Vital Crimson `#E53935`
- **Training Load / Workouts**: Energetic Amber `#FB8C00`
- **Steps & Movement**: Vitality Teal `#26A69A`

---

## 4. Typography & Spacing Scale

### Typography (Material 3 Adaptive Scale)
- **Hero Recovery Metric**: 64sp Bold, Rounded Sans
- **Metric Value Numbers**: 32sp SemiBold
- **Card Headlines**: 18sp SemiBold
- **Body Text**: 14sp Regular, Line Height 20sp
- **Micro Labels & Badges**: 11sp Medium, All-Caps Tracking

### Spacing Scale
- `Spacing.xxs`: 2dp
- `Spacing.xs`: 4dp
- `Spacing.s`: 8dp
- `Spacing.m`: 16dp (Standard container padding)
- `Spacing.l`: 24dp
- `Spacing.xl`: 32dp
- `Spacing.xxl`: 48dp

### Corner Radius
- `CornerRadius.small`: 8dp (Chips, small badges)
- `CornerRadius.medium`: 16dp (Metric cards, buttons)
- `CornerRadius.large`: 24dp (Hero score cards, dialogs)
