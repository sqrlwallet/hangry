# Hangry — Android Wellness & Fitness App

[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-blue.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-green.svg)](https://developer.android.com/jetpack/compose)
[![Room](https://img.shields.io/badge/Room-2.7.2-orange.svg)](https://developer.android.com/training/data-storage/room)
[![Health Connect](https://img.shields.io/badge/Health%20Connect-1.1.0-red.svg)](https://developer.android.com/health-and-fitness/guides/health-connect)
[![Tests](https://img.shields.io/badge/Tests-65%20Passed-brightgreen.svg)]()

**Hangry** is a local-first Android wellness and fitness app inspired by the recovery-tracking category. It connects to health data (via Android Health Connect), stores records locally in Room, normalizes daily summaries, calculates personalized recovery and training metrics, and presents actionable daily insights through a supportive, human, and non-judgmental Jetpack Compose interface.

Hangry contains **no backend servers, no cloud databases, no user accounts, no advertising SDKs, and zero analytics tracking**. All health metrics remain strictly inside the app-private SQLite database on the user's device.

---

## 📲 Download & Install

Download and install the APK directly on your Android phone (Android 9.0+ / API 28+):

[![Download APK](https://img.shields.io/badge/Download-Hangry%20v1.7.1%20APK-brightgreen?style=for-the-badge&logo=android)](https://github.com/sqrlwallet/hangry/releases/latest)

1. Download [**`hangry-v1.7.1.apk`**](https://github.com/sqrlwallet/hangry/releases/latest) from the Releases page.
2. Tap the file in your downloads folder on your device to install (allow "Install from Unknown Sources" if prompted).
3. Open **Hangry** - onboarding walks you through connecting Health Connect and requests every permission it needs in one step. 100% private, zero-fake wellness tracking!

## 📚 Complete Project Documentation

| Document | Description |
|---|---|
| [PRODUCT_SCOPE.md](PRODUCT_SCOPE.md) | Product boundaries, non-medical wellness positioning, supported record types, feature roadmap. |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Layered local-first architecture, clean boundaries, unidirectional data flow, interfaces. |
| [HEALTH_CONNECT.md](HEALTH_CONNECT.md) | Record types, SDK compatibility rationale, permissions, and conflict resolution. |
| [DATA_MODEL.md](DATA_MODEL.md) | Complete 14-entity Room schema, indexes, audit metadata, and type converters. |
| [SYNC_DESIGN.md](SYNC_DESIGN.md) | Bounded historical sync, deduplication via SHA-256 fingerprints, and idempotency. |
| [CALCULATIONS.md](CALCULATIONS.md) | Mathematical specification for Hangry Recovery (0–100), sleep debt, and training load. |
| [BRAND_GUIDELINES.md](BRAND_GUIDELINES.md) | Brand identity, supportive non-clinical voice, visual tokens, and copy matrix. |
| [BRAND_ASSETS.md](BRAND_ASSETS.md) | Emblem design, vector paths, launcher icon specifications, and licensing notes. |
| [PRIVACY.md](PRIVACY.md) | Absolute local-only privacy guarantees, export tools, and data deletion protocol. |
| [TESTING.md](TESTING.md) | Automated testing strategy, test inventory, and execution commands. |
| [ROADMAP.md](ROADMAP.md) | Progress checklist across Delivery Phases 1 to 5. |

---

## 🛠️ Technology Stack

- **Language**: Kotlin 2.2.10
- **UI Framework**: Jetpack Compose with Material 3
- **Local Persistence**: Android Jetpack Room 2.7.2 with KSP (`2.2.10-2.0.2`)
- **Navigation**: Navigation Compose 2.8.8
- **Background Work**: Android WorkManager 2.10.0
- **Platform Health**: Android Health Connect Client 1.1.0
- **Architecture**: Clean Architecture / MVVM with unidirectional data flow
- **Asynchronous**: Kotlin Coroutines 1.10.1 & StateFlow
- **Automated Testing**: JUnit 4, Kotlinx Coroutines Test, Turbine, Room Testing

---

## 🚀 Building & Testing

### Prerequisites
- Android Studio Ladybug / Meerkat or newer
- Android SDK with Platform 36 (`compileSdk = 36`, `minSdk = 28`, `targetSdk = 35`)
- JDK 17+ (e.g. Android Studio bundled JBR)

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

## 🌟 Key Features

- **Daily Activity 3-Concentric Rings**: Real-time tracking of Steps, Active Minutes, and Active Calories with customizable daily goals. Tap to expand numbers and percentages.
- **Hangry Recovery (0–100)**: Sleep-driven autonomic recovery score calculated with HRV RMSSD, Resting HR, and sleep performance.
- **Customizable Dashboard**: Modular home screen where users can reorder, show/hide, and create custom metric widgets.
- **Cardiovascular Strain & Training Load**: 0.0–21.0 Day Strain metric and 7-day Acute to Chronic Workload Ratio (ACWR).
- **Opt-in AI Features (BYOK)**: Meal photo calorie analysis and posture assessment powered by OpenRouter using the user's own API key stored securely in Android Keystore.
- **Strict Privacy**: 100% local SQLite database. No accounts, no servers, no ads, no trackers.

---

## 📜 License

This project is open-source under the **PolyForm Noncommercial License 1.0.0** (`LICENSE`).
You are free to download, inspect, run, modify, and distribute this software for **personal and non-commercial purposes**. Commercial use is strictly prohibited.

