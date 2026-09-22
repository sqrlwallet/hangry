## 🚀 What's New in Hangry v1.0.0

Hangry is a private, local-first wellness & recovery tracker for Android powered by Google Health Connect.

### 🛡️ 100% Real Health Connect Data (Zero Fake Data)
- Completely removed all synthetic mock data sources and fake fallbacks.
- When wearable data is missing or pending sync, displays transparent pending indicators (`—`) rather than fabricated values, strictly honoring Rule 2 data integrity.

### ⭕ Daily Activity Concentric Rings
- 3 concentric circular activity gauges tracking **Active Calories**, **Active Minutes**, and **Steps**.
- Configurable daily goals (defaults: 500 kcal, 90 min, 6,000 steps).
- Tap-to-expand interaction reveals exact counts and percentage completion.

### 🫀 Key Vitals & Cardio Fitness
- Direct integration with Google Health Connect for:
  - **VO₂ Max** (Cardio fitness level in mL/kg/min)
  - **SpO₂** (Blood oxygen saturation percentage)
  - **Respiration Rate** (Resting breaths per minute)
  - **Blood Pressure** (Systolic & Diastolic mmHg)
- Modular Vitals card with normal range indicators.
- Support for creating custom dashboard widgets for any vital metric.

### 📸 Quick Log Meals
- Floating camera quick-action button on the dashboard for instant meal photo logging.
- AI nutritional estimates with offline fallback.

### 🎨 Clean Minimal Dark UI & Flicker-Free Performance
- Consolidated color palette reducing visual noise.
- Optimized Compose state emissions to eliminate UI re-composition flicker.
- Responsive layout adapting to various Android screen densities.

### 🔒 Privacy & Open Source
- **100% Offline SQLite database (Room)**: No cloud databases, no user accounts, no ads, zero tracking.
- Open-source under PolyForm Noncommercial License 1.0.0.

---

### 📦 Installation
1. Download **`hangry-v1.0.0.apk`** below.
2. Tap the APK file on your device (enable "Install unknown apps" in Android settings if requested).
3. Open Hangry, grant Health Connect permissions, and enjoy private health tracking!
