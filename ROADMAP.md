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

## Phase 3: Historical Data Import & Bounded Sync — COMPLETED
- [x] User-selectable historical import range (7 days to 365 days, or all available data) in `HistoricalSyncSetupScreen`.
- [x] Bounded 14-day chunking to prevent memory spikes (`DefaultHealthSyncManager`).
- [x] Real-time progress reporting imported chunks and data types (`SyncProgressScreen`).
- [x] 2-hour overlap window for late-arriving records, on full and incremental syncs.

---

## Phase 4: Advanced Calculations & Trend Visualizations
- [x] Bounded 0-21 day-strain calculation (`HangryStrainCalculator`) from continuous heart-rate zones, with a workout-only fallback — see CALCULATIONS.md §6.
- [x] Recovery-scaled daily strain target recommendation on the dashboard.
- [x] Personal sleep-need, sleep performance %, and estimated bedtime (`HangrySleepCalculator`) — see CALCULATIONS.md §7.
- [x] Trend charts (Recovery, Strain, Sleep Duration, HRV, RHR) and a Weekly Performance Recap card on the Trends screen.
- [x] 28-day baseline views for HRV and Resting Heart Rate on the Heart & HRV screen, plus the Heart widget against your 4-week normal.
- [ ] Daily subjective Journal flow (perceived recovery, stress, soreness). A post-reading HRV "how do you feel" check-in exists; a full daily journal is still open.
- [x] Acute-to-chronic training load balance ratio (`TrainingLoadCalculator.calculateDailyLoad`), shown on the Training screen.

---

## Phase 5: Privacy Hardening & Production Release
- [x] Export local data as JSON or CSV to a file of the user's choosing (Settings, via the system file picker).
- [x] One-tap "Delete All Health Data" with confirmation in Settings; individual meals, scans and records can be deleted where they're shown.
- [ ] Room migration tests.
- [ ] Google Play Console Health Apps declaration review.
- [x] Release APK assembly: debug-signed sideload APKs on GitHub Releases (`-Psideload`); Play builds use the upload key.
- [ ] Performance benchmarks.
- [ ] Move hard-coded UI text into string resources so the app can be translated.

---

## Shipped since v1.20 (see CHANGELOG.md)
- Saved meals, common foods and portions (v1.22); calories & nutrition widgets (v1.23).
- Intermittent fasting and simpler supplements (v1.24).
- Rebuilt recovery, sleep score out of 100 and live strain (v1.25–v1.26).
- Guided programs and new Dash poses (v1.27). The longevity pillars from that release were removed again in v1.30.
- Portion-accurate photo nutrition and Health Connect meal edit sync (v1.28–v1.29.1).
- Sleep stage timeline and 7-night stage chart (v1.29).
- Weekly nutrition review, fiber tracking and Gemini 3.8 Flash for photo analysis (v1.30).
