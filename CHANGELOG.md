## 🚀 What's New in Hangry v1.15.1

### ✨ More Room on Today
- **Pull down to sync**: the refresh, settings and customize icons are gone from the top of Today, and Dash spins while it syncs.
- **Floating tab bar**: content now scrolls behind the tab bar instead of stopping above a blank strip.
- **More tab** now also holds Customize Today and Settings & Privacy.

---

## 🚀 What's New in Hangry v1.15.0

### 🏋️ Workouts, Properly Imported
- **Every Health Connect workout type**: weightlifting, elliptical, rowing, tennis, hiking and ~55 more now show by name instead of "Other".
- **Workout details**: distance, pace (or speed for rides, per 100 m for swims), average and max heart rate, elevation, power, strength sets and reps with the exercises done, laps and notes.
- **History fixed too**: workouts saved before this version are re-read from Health Connect once, then training load and strain are recalculated.
- **Training load** now rates each workout by its family of exercise and uses its full calories.

### 🔥 Active Calories & Active Time
- **Workouts count in full**: every calorie burned during a workout, not just the extra above resting, and every minute of it.
- **Every step counts**: 150 steps adds one active minute, and each step's full cost is included.
- **Daily Activity** shows the new active calories and a new Active Time row, with an editable Active Time goal.
- **Maintenance calories** use the same rules, and the day's total burn no longer counts resting calories twice.

### 🧩 Seven New Widgets
- Breathe (one tap starts a session), Heart, Health Markers, Goals, Weight Trend, Posture Check and Cycle.
- **Hide values on widgets** switch for privacy; widgets refresh whenever you leave the app.

### ✨ Polish
- Settings & Privacy is shorter: sections are collapsed until you need them.
- Peach app icon background.
- The Sleep widget no longer shows a made-up score; it shows when you slept and, with enough history, how steady your schedule is.
- Ask Dash's welcome screen uses the new waving Dash.

---

## 🚀 What's New in Hangry v1.14.0
- Illustrated pose guides for posture and body fat photos; the AI knows each view.
- Dash breathes along in the breathing exercise, with six expressions across the app, illustrated empty states and onboarding art.
- Floating Zs, heartbeat glow, confetti and tap-to-hop animations.

---

## 🚀 What's New in Hangry v1.13.0
- Supplements: snap a bottle to add it, overlap and dose checks, reminders and a widget.
- Ask Dash accepts photos, can fill things in for you after you confirm, and streams replies.
- Allergen alerts on logged meals; blood pressure and blood sugar shared back to Health Connect.

---

## 🚀 What's New in Hangry v1.12.0
- Health records and goals (blood pressure, labs, allergies, conditions, cycle, pregnancy).
- Body metrics (BMI, FFMI, waist-to-height and more) and an energy balance estimate.
- Ask Dash and camera-first meal logging.

---

## 🚀 What's New in Hangry v1.11.0
- Nunito typography, decluttered copy with info tips, the Dash app icon, and recovery that still works without HRV.

---

## 🚀 What's New in Hangry v1.10.0
- Meet Dash the fox, the AI coach's mascot, plus guided breathing exercises.

---

## 🚀 What's New in Hangry v1.9.0
- Warm cream rebrand with contrast-checked light and dark themes.

---

## 🚀 What's New in Hangry v1.8.0
- Body fat dashboard widget, body fat trend chart, coach model selector and a multi-photo picker.

---

## 🚀 What's New in Hangry v1.7.0 – v1.7.1
- AI body fat calculator, interactive trend history and faster local storage.
- Fixed a startup crash (v1.7.1).

---

## 🚀 What's New in Hangry v1.6.0
- Removed the double status-bar gap on Today and locked the day's resting heart rate once shown.

---

## 🚀 What's New in Hangry v1.5.0
- Floating bottom dock and more usable screen space across the app.

---

## 🚀 What's New in Hangry v1.4.0
- Modernized UI with bottom navigation, macro tracking and AI Coach polish.

---

## 🚀 What's New in Hangry v1.3.0

### 🎯 Streamlined Daily Activity & Metrics
- **Simplified Daily Activity Rings**: Refactored the concentric activity rings to focus exclusively on the two metrics that matter: **Calories Burned** and **Steps**. Removed minutes from the rings, legends, expanded metrics, and goal setting dialogs.
- **Removed Training Load**: Deprecated and completely removed the cardiovascular Training Load metric from the dashboard, widget selections, and the training screen hero, refocusing training on today's workouts and cardio intensity zones.
- **Removed Blood Pressure & Respiration**: Cleaned up Key Vitals and customizable dashboard widgets to remove blood pressure and respiration rate.
- **Permissions Cleanup**: Removed unused `READ_BLOOD_PRESSURE` and `READ_RESPIRATORY_RATE` Health Connect permission requests from the app manifest and permission flows.
- **Home Screen Widgets & Mockups Updated**: Updated Daily Activity and Daily Overview widgets to display only steps and active calories burned without minutes columns.

---

## 🚀 What's New in Hangry v1.2.0

### 🤖 AI Coach with 7-Day Context & Personal Problem Journal
- **Personalized Coaching**: Interactive AI Coach powered by `openai/gpt-5.6-luna` that answers health, training, nutrition, and recovery questions with deep awareness of your past 7 days of data (sleep, strain, workouts, nutrition & macros, posture, and weight).
- **Automatic Problem & Memory Journaling**: When you share personal problems, injuries, symptoms, food sensitivities, or habits in chat, the AI Coach automatically extracts and saves them to a persistent personal journal. These memories are remembered and incorporated into future coaching sessions across days.
- **Floating AI Coach Button**: Floating access button on the main dashboard directly above the Log Meal button (active when AI is enabled).
- **Personal Journal & Memories Sheet**: Inspect, manage, manually add, or delete coaching memories at any time via a dedicated bottom sheet.
- **Robust Multi-Turn History**: Chat conversations and saved memories are persisted locally in Room (v9 migration) with full offline security.

### 🧘 Relaxed Posture Guardrails & Improved AI Prompts
- **Relaxed Posture Guardrails**: Removed strict shirtless/shorts restrictions. Users can now perform posture checks wearing normal athletic or casual clothing (t-shirts, shorts, leggings, etc.).
- **Flexible Photo Count**: Reduced minimum photos from 3 to 1 (supporting 1–5 photos from any angle: side, front, or back).
- **Expert Biomechanics Assessment**: Redesigned posture prompt for calibrated scoring, constructive observations (cranio-cervical, scapular, pelvic, spinal curves), and targeted corrective exercises with clear form cues.
- **Smarter Food & Nutrition Estimation**: Enhanced food prompt with visual portion recognition, hidden cooking fats/oil accounting, calorie consistency, and informative portion breakdown notes.

---

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
