# Hangry — Product Roadmap & Phase Milestones

## Phase 1: Local Foundation — COMPLETED
- [x] Configure Android Gradle Build with Kotlin 2.2, Room, KSP, Material 3, Navigation Compose, WorkManager, and Health Connect.
- [x] Create complete 14-entity Room schema with indexes, fingerprints, and audit metadata.
- [x] Implement Room DAOs with reactive Flow queries and conflict resolution.
- [x] Implement deterministic, versioned calculation engine:
  - Hangry Recovery (0–100 score, rolling baseline, confidence, dynamic re-weighting).
  - Sleep Calculator (duration, debt, consistency, baseline ratio).
  - Training Load Calculator (intensity multipliers, multi-workout aggregation).
- [x] Enforce 100% real Health Connect data (removed all fake data sources and synthetic fallbacks; display pending states for unrecorded metrics).
- [x] Build Jetpack Compose Dashboard with hero score card, metric cards, sync status, and theme preview.
- [x] Build comprehensive automated test suite.

---

## Phase 2: Health Connect Permissions & Device Integration — MOSTLY COMPLETE
- [x] Health Connect availability and provider status check (`SDK_AVAILABLE`, `SDK_UNAVAILABLE`, `SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED` now shown distinctly with an update CTA).
- [x] Pre-permission education UI explaining Sleep, Steps, HR, and HRV data needs.
- [x] Standard permission request contracts (`PermissionController.createRequestPermissionResultContract`) with per-category (sleep/RHR/HRV/exercise/steps) grant status, and revoked-permission detection surfaced as a distinct sync state (`PERMISSION_REVOKED`) instead of a silent zero-record success.
- [x] Background sync activated via `HealthSyncWorker.schedule()` on app start (hourly periodic `WorkManager` job; needs Health Connect background-read permission, asked for in onboarding and Settings → Background updates).
- [ ] Data sources status screen displaying connected third-party health apps (screen exists; still lists locally-seen packages only, no live Health Connect "connected apps" query).

---

## Phase 3: Historical Data Import & Bounded Sync
- [ ] User-selectable historical import range (7 days, 30 days, 90 days, 365 days, All).
- [ ] Bounded 14-day chunking to prevent memory spikes.
- [ ] Real-time progress bar reporting imported chunks and data types.
- [ ] Overlap window for late-arriving records.

---

## Phase 4: Advanced Calculations & Trend Visualizations
- [x] Bounded 0-21 day-strain calculation (`HangryStrainCalculator`) from continuous heart-rate zones, with a workout-only fallback — see CALCULATIONS.md §6.
- [x] Recovery-scaled daily strain target recommendation on the dashboard.
- [x] Personal sleep-need, sleep performance %, and estimated bedtime (`HangrySleepCalculator`) — see CALCULATIONS.md §7.
- [x] Trend charts (Recovery, Strain, Sleep Duration, HRV, RHR) and a Weekly Performance Recap card on the Trends screen.
- [ ] 28-day baseline trend views for HRV and Resting Heart Rate (current charts cover the screen's selected timeframe; a dedicated 28-day baseline-stability view is still open).
- [ ] Daily subjective Journal flow (perceived recovery, stress, soreness).
- [x] Acute-to-chronic training load balance ratio (`TrainingLoadCalculator.calculateDailyLoad`), shown on the Training screen.

---

## Phase 5: Privacy Hardening & Production Release
- [ ] Export local data as JSON and CSV to local Downloads directory.
- [ ] One-tap "Delete All Health Data" and selective record deletion verification.
- [ ] Room migration tests.
- [ ] Google Play Console Health Apps declaration review.
- [ ] Release APK assembly and performance benchmarks.
