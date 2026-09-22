# Hangry — Health Connect Integration Guide

## 1. SDK Selection & Compatibility Rationale

### Selected SDK Versions
- **`compileSdk`**: `35` (or latest Android 15 release) — Required for modern Android toolchains and Health Connect platform API declarations.
- **`targetSdk`**: `35` — Matches modern Google Play Store submission requirements.
- **`minSdk`**: `28` (Android 9.0 Pie) —
  - *Rationale*: Google Health Connect client library (`androidx.health.connect:connect-client`) supports devices running Android 8.0 (API 26) or higher via the standalone Health Connect APK downloaded from the Play Store, and is directly built into the platform on Android 14+ (API 34+).
  - Targeting `minSdk = 28` ensures reliable support for encrypted storage, modern biometric security, notification channels, and coroutine execution while covering >95% of active global Android hardware.

---

## 2. Supported Record Types

Hangry requests read-only permissions for the following Health Connect records:

| Record Type | Description | Local Entity Mapping | Missing Data Policy |
|---|---|---|---|
| `SleepSessionRecord` | Start/end times, durations, optional stages | `SleepSessionEntity` | Show "No sleep recorded" |
| `StepsRecord` | Daily and interval step counts | `StepsSummaryEntity` | Show "No steps recorded" |
| `DistanceRecord` | Walking/running distance | `StepsSummaryEntity` | Omit or mark unavailable |
| `TotalCaloriesBurnedRecord` | Total metabolic + active calories | `DailyHealthSummaryEntity` | Show "Not available" |
| `ActiveCaloriesBurnedRecord` | Calories from activity/exercise | `DailyHealthSummaryEntity` | Show "Not available" |
| `ExerciseSessionRecord` | Logged workouts (running, gym, etc.) | `ExerciseSessionEntity` | Show "No workouts today" |
| `HeartRateRecord` | Continuous pulse rate samples | `HeartRateSampleEntity` | Omit from chart |
| `RestingHeartRateRecord` | Resting baseline pulse | `RestingHeartRateEntity` | Exclude from baseline ratio |
| `HeartRateVariabilityRmssdRecord` | RMSSD HRV values | `HrvMeasurementEntity` | Never convert to 0; lower confidence |
| `WeightRecord` | Body weight measurements | `WeightMeasurementEntity` | Optional, omit if absent |
| `HeightRecord` | Height measurement | `UserProfileEntity` | Optional, omit if absent |

> [!WARNING]
> **Zero Replacement Rule**: Missing health data must **never** be replaced with zero (e.g., zero steps, zero HRV, or zero resting heart rate). Missing records are tracked as `null` or `DataQualityState.UNAVAILABLE` to protect baseline calculations from severe distortions.

---

## 3. Availability & Provider Status Lifecycle

Health Connect can exist in several states on an Android device:
1. **`SDK_AVAILABLE` (Android 14+ or APK installed)**: Proceed with normal permission checks and sync.
2. **`SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED`**: Prompt the user with a direct Play Store link to update or install the Google Health Connect service app.
3. **`SDK_UNAVAILABLE`**: The device hardware or OS version does not support Health Connect. Transition the app into standalone offline mode with manual journal entries and informational guidance.

> On Android 11-13, Health Connect is a separate installed app, so reliable status detection requires declaring package visibility for it via a `<queries>` entry in the manifest (`com.google.android.apps.healthdata`) — without it, `getSdkStatus()` can under-report availability due to package-visibility filtering.

---

## 4. Permission Model & Gradual Disclosure

Hangry practices **Gradual & Just-In-Time Consent**:
1. **Pre-Permission Education Screen**: Before triggering the platform permission dialog, Hangry displays an education screen detailing:
   - What data will be read (e.g., Sleep, Heart Rate, Steps).
   - Why it is needed (to calculate personalized daily recovery and baseline trends).
   - The privacy guarantee: data stays on-device and is never shared with third parties or cloud servers.
2. **Handling Denied Permissions**: The user is guided smoothly without blocking access to the app. An educational banner indicates which metrics are limited.
3. **Handling Partial Permissions**: If a user grants Sleep and Steps but denies HRV, Hangry calculates recovery using available components and displays a "Medium" confidence badge with a clear breakdown.
4. **Handling Revoked Permissions**: If the user revokes permissions in system settings, subsequent sync cycles handle `SecurityException` gracefully and update `SyncStateEntity` to `PERMISSION_REVOKED`.

---

## 5. Multi-Source Records & Conflict Resolution

Health Connect aggregates data from multiple apps (e.g., Samsung Health, Google Fit, Garmin Connect, Whoop, Polar, Pixel Watch). When multiple sources submit records for the same time window:
1. **Retain Source Metadata**: Store original `sourcePackageName` and `sourceRecordId` on every entity.
2. **Deduplication Key**: Composite key `(dataType, startTime, endTime, sourcePackageName)`.
3. **Sleep Session Overlap**: If multiple sources log overlapping sleep periods, prioritize the source with the most complete stage/heart rate data, or select the user-preferred default source.
4. **Source Transparency**: The UI displays the recording source (e.g., "Synced from Garmin Connect via Health Connect") and the timestamp of the last synchronization.
