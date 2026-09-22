## 🚀 What's New in Hangry v1.1.0

### 🧩 Home Screen Widgets
- 5 pinnable Android home screen widgets: Daily Activity, Quick Log Meal, Sleep Insights, Recovery Score, and Daily Overview.
- One-tap "Pin to Home" gallery in Settings, plus the standard long-press launcher picker.
- Widgets refresh automatically on app launch and after every periodic background sync - real data only, honest `—` placeholders when nothing has synced yet.
- The Quick Log Meal widget deep-links straight into the meal-logging sheet, even from a cold start.

### ✨ Streamlined Onboarding
- Merged the "why we need this" explanation screen into the permission screen - one fewer tap-through step.
- The Health Connect permission dialog now opens automatically as soon as onboarding reaches it, instead of waiting for a button tap.
- New step indicator and icon-based feature rows across onboarding, replacing emoji, to match the rest of the app's visual language.

### 🧹 Settings Cleanup
- Removed the Journal feature and Theme Preview screen.
- Consolidated Privacy & Legal into a single entry.
- JSON/CSV export now saves a real file via Android's file picker instead of only opening the share sheet.
- Height now syncs from Health Connect automatically, the same way weight already did.

### 🐛 Fixes
- Training screen's workout list was showing every workout ever logged instead of just today's.
- Daily steps/activity sync could leave stale duplicate rows behind instead of replacing the day's total.

---

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
1. Download **`hangry-v1.1.0.apk`** from the [latest release](https://github.com/sqrlwallet/hangry/releases/latest).
2. Tap the APK file on your device (enable "Install unknown apps" in Android settings if requested).
3. Open Hangry - Health Connect permissions are now requested automatically as part of onboarding.
