# Hangry — Android Wellness & Fitness App

[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-blue.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-green.svg)](https://developer.android.com/jetpack/compose)
[![Room](https://img.shields.io/badge/Room-2.7.2-orange.svg)](https://developer.android.com/training/data-storage/room)
[![Health Connect](https://img.shields.io/badge/Health%20Connect-1.1.0-red.svg)](https://developer.android.com/health-and-fitness/guides/health-connect)
[![Tests](https://img.shields.io/badge/Tests-141%20Passed-brightgreen.svg)]()

**Hangry** is a local-first, open-source Android wellness and fitness app. It reads your health data through Android Health Connect, stores it on your phone in Room, and turns it into recovery, sleep, strain, nutrition and body-composition insights - plus **Dash**, an AI companion that knows your data and can log things for you.

There are **no Hangry servers, no accounts, no ads and no analytics**. Your data lives in the app's private database on your device. The optional AI features call [OpenRouter](https://openrouter.ai) directly from your phone with **your own API key** - nothing goes through a Hangry server.

> Hangry is for your own tracking and is **not medical advice**. Ranges and estimates are general reference points; talk to a doctor about your results.

---

## 📲 Download & Install

Android 9.0+ (API 28+):

[![Download APK](https://img.shields.io/badge/Download-Latest%20Hangry%20APK-brightgreen?style=for-the-badge&logo=android)](https://github.com/sqrlwallet/hangry/releases/latest)

1. Download the latest **`hangry-vX.Y.Z.apk`** from the [Releases page](https://github.com/sqrlwallet/hangry/releases/latest).
2. Open it on your phone to install (allow "Install unknown apps" if prompted).
3. Open **Hangry** - onboarding connects Health Connect and asks for permissions in one step.
4. Optional: turn on **AI Features** in Settings and add your OpenRouter API key to unlock Dash, meal photo logging, supplement label reading, body-fat and posture analysis.

Release notes for every version are on the [Releases page](https://github.com/sqrlwallet/hangry/releases).

---

## 🌟 Features

### Today
- **Hangry Recovery (0–100)** from HRV, resting heart rate and sleep against your own 7-day baseline. No HRV from your device? It counts as excellent by default, and you can set how you actually feel.
- **Sleep & Strain** - sleep score, debt and consistency, plus a 0–21 day strain and training load.
- **Daily Activity rings** for steps and active calories with editable goals.
- **Customizable dashboard** - reorder, hide or add metric cards, plus home-screen widgets (activity, sleep, recovery, overview, quick meal log, supplements).

### Nutrition
- **Snap to log** - Log Meal opens the camera; AI estimates calories and macros and logs it straight away, with an Edit shortcut. Manual entry is still there in the menu.
- **Allergy alerts** - if you've added allergies, meal photos are checked against them.
- **Calorie target from real life** - maintenance = BMR + everyday steps + workouts + 10% for digesting food, from your last 7 full days. Set a goal weight and date for a daily target and safe deficit.
- Meal plans for one-tap logging; entries sync to Health Connect.

### Body Metrics
- 20+ metrics from your height, weight, age, sex, tape measurements and body-fat scans: BMI, healthy weight range, FFMI, fat mass index, waist-to-height, waist-to-hip, body roundness index, conicity, V-taper, BMR (Mifflin–St Jeor / Katch–McArdle), protein and water targets and more.
- Every metric explains what it is, the exact sum with your numbers, why it matters and where its ranges come from.
- **Body fat calculator** - U.S. Navy tape method, health-history estimate, or AI photo analysis.

### Health Records & Goals
- Track blood pressure, blood sugar, HbA1c, cholesterol (total, LDL, HDL), triglycerides and testosterone (men), in mg/dL or mmol/L.
- Set goals (lower blood pressure, blood sugar or LDL, raise HDL) and track progress.
- Allergies, conditions, and - for women - pregnancy and menstrual cycle tracking with next-period predictions.
- Imports from Health Connect, including **medical records** (lab results, conditions, allergies, pregnancy) on Android 16+. Blood pressure and blood sugar you enter are shared back to Health Connect for other apps.

### Supplements
- Photograph the bottle and label - AI reads the dose and ingredients and flags overlaps or cautions.
- Set dose times, get reminders with a **Mark taken** button, and tick doses off from the app or the home-screen widget.

### Ask Dash 🦊
- An AI companion that sees your recovery, sleep, trends, nutrition, body metrics, health records and supplements.
- **Send photos** (meals, supplement labels, lab reports, monitor screens) and Dash proposes actions - log a meal, save lab readings, add a supplement, update goals, log sleep and more. **Nothing is saved until you tap to confirm.**
- Replies stream word by word; data-aware suggestion chips; voice input.

### More
- **Guided breathing** - box breathing and 6, 5 and 3 breaths-per-minute sessions with audio beeps that keep going with the screen locked, logged to Health Connect.
- **Posture analysis** from photos with corrective exercises.

---

## 🔒 Privacy

- All data is stored locally in Room on your device; there is no Hangry backend.
- Health Connect access is requested per category and can be revoked any time in Android settings.
- AI features are **off by default**. When on, the photos and text you submit - and, when you chat with Dash, your health context - are sent from your phone to OpenRouter using your own key.
- See [PRIVACY.md](PRIVACY.md) and the in-app Privacy screen for details.

---

## 📚 Project Documentation

| Document | Description |
|---|---|
| [PRODUCT_SCOPE.md](PRODUCT_SCOPE.md) | Product boundaries, non-medical wellness positioning and supported record types. |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Layered local-first architecture, boundaries and unidirectional data flow. |
| [HEALTH_CONNECT.md](HEALTH_CONNECT.md) | Record types, SDK compatibility, permissions and conflict resolution. |
| [DATA_MODEL.md](DATA_MODEL.md) | Room schema, indexes, audit metadata and type converters. |
| [SYNC_DESIGN.md](SYNC_DESIGN.md) | Bounded historical sync, SHA-256 fingerprint deduplication and idempotency. |
| [CALCULATIONS.md](CALCULATIONS.md) | Specification for recovery, sleep debt, training load and calorie calculations. |
| [BRAND_GUIDELINES.md](BRAND_GUIDELINES.md) | Brand identity, supportive non-clinical voice, visual tokens and copy. |
| [BRAND_ASSETS.md](BRAND_ASSETS.md) | Emblem design, vector paths, launcher icon specs and licensing notes. |
| [PRIVACY.md](PRIVACY.md) | Local-only privacy guarantees, export tools and data deletion. |
| [TESTING.md](TESTING.md) | Testing strategy, test inventory and commands. |
| [ROADMAP.md](ROADMAP.md) | Progress across delivery phases. |

---

## 🛠️ Technology Stack

- **Language**: Kotlin 2.2.10
- **UI**: Jetpack Compose with Material 3
- **Local persistence**: Room 2.7.2 with KSP (`2.2.10-2.0.2`)
- **Navigation**: Navigation Compose 2.8.8
- **Background work**: WorkManager 2.10.0, AlarmManager (supplement reminders), a foreground service (breathing sessions)
- **Health data**: Health Connect Client 1.1.0, including medical records (FHIR)
- **AI (optional)**: OpenRouter via OkHttp, with streaming; API key encrypted with Android Keystore
- **Architecture**: Clean Architecture / MVVM with unidirectional data flow
- **Async**: Kotlin Coroutines 1.10.1 & StateFlow
- **Testing**: JUnit 4, Kotlinx Coroutines Test, Turbine, Room Testing

---

## 🚀 Building & Testing

### Prerequisites
- Android Studio Ladybug / Meerkat or newer
- Android SDK Platform 36 (`compileSdk = 36`, `minSdk = 28`, `targetSdk = 35`)
- JDK 17+ (e.g. Android Studio's bundled JBR)

### Run Unit Tests
```bash
export JAVA_HOME="/path/to/android-studio/jbr"
./gradlew testDebugUnitTest
```

### Build Debug APK
```bash
export JAVA_HOME="/path/to/android-studio/jbr"
./gradlew assembleDebug
```

---

## 📜 License

This project is open-source under the **PolyForm Noncommercial License 1.0.0** (`LICENSE`).
You are free to download, inspect, run, modify, and distribute this software for **personal and non-commercial purposes**. Commercial use is prohibited.
