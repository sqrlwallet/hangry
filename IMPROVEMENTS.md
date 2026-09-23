# Hangry — Comprehensive App Improvement Plan & Technical Roadmap

> **Vision**: Hangry is a luxury, local-first wellness and biometric tracking app inspired by elite recovery platforms. It delivers clinical-grade physiological analytics, posture screening, nutrition intelligence, and personalized AI coaching with **absolute zero telemetry, zero cloud databases, and 100% on-device data sovereignty**.

---

## 📑 Table of Contents
1. [Immediate UI & Screen Space Optimization](#1-immediate-ui--screen-space-optimization)
2. [Biometric Engine & Physiological Algorithms](#2-biometric-engine--physiological-algorithms)
3. [Visual Design Language & Luxury Aesthetic](#3-visual-design-language--luxury-aesthetic)
4. [Nutrition & Fueling Experience](#4-nutrition--fueling-experience)
5. [Computer Vision Posture Screening](#5-computer-vision-posture-screening)
6. [AI Coach & Health Intelligence](#6-ai-coach--health-intelligence)
7. [Health Connect & Wearable Ecosystem](#7-health-connect--wearable-ecosystem)
8. [Data Privacy, Security & Sovereignty](#8-data-privacy-security--sovereignty)
9. [Performance, Architecture & Testing](#9-performance-architecture--testing)
10. [Prioritized Implementation Matrix](#10-prioritized-implementation-matrix)

---

## 1. Immediate UI & Screen Space Optimization

### 1.1 Top Space Reclamation & Header Simplification
- **Eliminate Giant Top Space Gap**:
  - Resolve the triple-inset stacking bug where `MainActivity.Scaffold` (status bar insets), screen-level `TopAppBar` (window insets), and content `Column` padding were all independently applying top padding.
  - Remove the redundant top app bar containing the "Hangry" logo and app name on primary tabs (`Dashboard`, `Nutrition`, `Posture`, `Trends`).
- **Compact Floating Top Utility Row**:
  - Replace the 64dp+ TopAppBar on the Dashboard with an ultra-compact, hairline utility bar positioned directly below the system status bar (`statusBarsPadding()`).
  - Right-align minimalist action icons: **Customize Widgets** (`Icons.Default.DashboardCustomize`), **Settings** (`Icons.Default.Settings`), and **Sync Status/Refresh** (`Icons.Default.Refresh`).
  - Bring the primary **Recovery Hero Gauge** up near the top of the viewport, maximizing vertical content visibility.

### 1.2 Dynamic Edge-to-Edge Scroll Clearance
- **Floating Bottom Dock Scrim**:
  - Ensure list content on all screens scrolls edge-to-edge behind the floating navigation capsule dock.
  - Apply `contentPadding = PaddingValues(bottom = 100.dp)` on all `LazyColumn` and scroll containers so the lowest card or action button can be scrolled completely into clear view above the floating bar.
- **Keyboard & Input Handling**:
  - Keep the smart soft keyboard hiding behavior (`WindowInsets.ime`) on the floating dock so input bars (AI Coach, Quick Log) sit flush with the keyboard without jitter.

---

## 2. Biometric Engine & Physiological Algorithms

### 2.1 Resting Heart Rate (RHR) Overhaul & Day-Locking
- **Strict 24-Hour Non-Sleeping Lowest Heart Rate**:
  - **New Definition**: Resting heart rate must be computed as the **lowest recorded heart rate while not sleeping within the last 24 hours**.
  - **SQL Filter**: Exclude all continuous heart rate samples falling inside registered sleep intervals (`NOT EXISTS (SELECT 1 FROM sleep_sessions s WHERE h.timestamp >= s.startTime AND h.timestamp <= s.endTime)`).
- **Intraday Immutability (Day-Locked RHR)**:
  - Once RHR is calculated for the day (during the morning sync when sleep and recovery scores are processed), **lock the value for that calendar day**.
  - Subsequent intraday syncs, workout logs, or manual refreshes must **never overwrite** or fluctuate the day's established resting heart rate.
  - Persist an immutable `isLockedForDay` flag or preserve `DailyHealthSummaryEntity.restingHeartRate` across subsequent sync passes.

### 2.2 HRV RMSSD Baseline Calibration & Outlier Filtering
- **Dynamic 30-Day Rolling Baseline**:
  - Implement log-normal transformation for HRV RMSSD (natural logarithm $\ln(\text{RMSSD})$) to normalize skewed distributions.
  - Calculate standard deviation bands ($\pm 1.0\sigma$ to $\pm 1.5\sigma$) to define personal "Balanced", "Elevated", and "Suppressed" autonomic recovery zones.
- **Artifact & Outlier Rejection**:
  - Filter out extreme single-sample spikes caused by movement artifacts or loose watch sensors before calculating daily sleep HRV averages.

### 2.3 Sleep Debt Decay & Cumulative Recovery
- **Circadian-Aware Sleep Need**:
  - Scale baseline sleep need (typically 7.5–8.5h) based on trailing 3-day accumulated strain and physical load.
  - Implement a 5-day exponential decay for sleep debt: recent sleep deficits impact recovery heavier than sleep lost 4–5 days prior.
- **Sleep Staging Breakdown**:
  - Track REM %, Deep (Slow Wave) %, Light %, and Wake After Sleep Onset (WASO).
  - Score restorative sleep efficiency based on deep + REM ratios rather than total duration alone.

### 2.4 Acute-to-Chronic Workload Ratio (ACWR)
- **Injury Risk & Overreaching Detection**:
  - Calculate the ratio between Acute Load (last 7 days) and Chronic Load (last 28 days).
  - Flag "Sweet Spot" training ($0.8 \le \text{ACWR} \le 1.3$), "Under-training" ($< 0.8$), and "High Injury Risk / Overreaching" ($> 1.5$).

---

## 3. Visual Design Language & Luxury Aesthetic

### 3.1 Frosted Obsidian Glass & Material Expressiveness
- **Backdrop Blur & RenderEffect (Android 12+)**:
  - On API 31+, apply native runtime blur (`RenderEffect.createBlurEffect`) to the floating navigation dock and dialog overlays.
  - Provide graceful degradation with deep translucent obsidian surfaces (`Color(0xF214151B)`) on API 28–30.
- **Chamfer Hairline Metallic Borders**:
  - Standardize 1dp dual-stop vertical gradient hairline borders (`BorderStroke(1.dp, Brush.verticalGradient(listOf(Color.White.copy(0.14f), Color.White.copy(0.03f)))))` across all cards, modals, and sheets.

### 3.2 Interactive Biometric Visualizations
- **Touch-Scrubbing on Line Charts**:
  - Add horizontal touch scrubbers to `HangryLineChart` on the Trends screen.
  - Dragging across the chart reveals an interactive tooltip with exact date, metric value, and baseline delta percentage.
- **Micro-Sparklines in Dashboard Metric Cards**:
  - Embed 7-day mini sparklines inside Resting HR, HRV, and Sleep cards on the main dashboard to provide instant trajectory context.
- **Adaptive Ambient Glows**:
  - Introduce subtle, dynamic radial gradient glow under the primary Recovery score ring matching the recovery tier (Mint green for Primed, Ember orange for Balanced, Crimson for Rebuild).

### 3.3 Dashboard Modularity & Customization
- **Drag-and-Drop Card Reordering**:
  - Upgrade the `CustomizeDashboardSheet` from simple up/down arrows to an interactive reorderable list with smooth spring animations.
- **New Modular Widgets**:
  - **Quick Biometric Strip**: Compact 1-line horizontal summary of SPO2, VO2 Max, and Resting HR.
  - **Sleep Architecture Breakdown**: Horizontal segmented bar showing Deep / REM / Light / Awake breakdown.
  - **Daily Habit & Hydration Tracker**: 1-tap water logging rings and recovery habits.

---

## 4. Nutrition & Fueling Experience

### 4.1 On-Device Barcode Scanner
- **ML Kit Barcode Scanning**:
  - Integrate on-device Google ML Kit Barcode Scanning via the device camera.
  - Match barcodes locally against Open Food Facts offline cache or lightweight lookup for instantaneous calorie and macro logging.

### 4.2 Multi-Photo & Nutrition Label Vision
- **Dual Photo Inputs**:
  - Allow users to snap a photo of their meal alongside a photo of the nutrition label or menu item description.
  - AI prompt synthesizes both inputs for near-perfect macro estimation without manual typing.
- **Photo Diary & Gallery View**:
  - Add an optional grid view of today's logged meals with thumbnail photos, timestamps, and calorie tags.

### 4.3 Water & Electrolyte Tracking
- **Interactive Hydration Dial**:
  - Quick-log presets (+250ml, +500ml, +750ml) with daily target calculation scaled to workout sweat loss and day strain.

---

## 5. Computer Vision Posture Screening

### 5.1 Real-Time Pose Guidance Overlay
- **Live Camera Pose Landmarks**:
  - Integrate on-device ML Kit Pose Detection in `PostureCaptureScreen`.
  - Display interactive visual guide lines (ear, shoulder, hip, knee, ankle alignment axis) in real time before photo capture.
  - Provide instant feedback: "Move farther back", "Align shoulders horizontally", "Turn 90° for profile view".

### 5.2 Before-and-After Comparison Slider
- **Visual Progress Comparer**:
  - Interactive split-screen or overlay comparison between posture check #1 and the latest scan to visually demonstrate alignment gains over weeks.
- **Built-in Posture Timer & Audio Cues**:
  - In-app guided countdown timers for the 3-minute posture reset drills (Chin Tucks, Scapular Wall Slides, Glute Bridges) with subtle haptic vibration cues for set/rep completions.

---

## 6. AI Coach & Health Intelligence

### 6.1 Proactive Morning Briefing Notification
- **Morning Readiness Notification**:
  - When morning sync completes, optionally deliver a single, private local notification summarizing readiness:
    > *"Recovery 88% (Primed) · HRV +12ms above baseline · Optimal day for high-intensity training. Target strain: 14.5–17.0."*
  - Generated 100% locally or via local template without telemetry.

### 6.2 Voice Note & Conversational Audio
- **Audio Message Input**:
  - Allow speaking to the coach using on-device Android SpeechRecognizer.
  - Speech-to-text converts voice questions into text for effortless coaching while driving or stretching.

### 6.3 Offline Heuristic Fallback Engine
- **Deterministic Coach Mode**:
  - If AI features are disabled, the user is offline, or no API key is provided, provide high-quality algorithmic recovery recommendations generated from deterministic medical and athletic heuristics.

---

## 7. Health Connect & Wearable Ecosystem

### 7.1 Source Attribution & Wearable Badges
- **Device & Provider Badges**:
  - Inspect `metadata.dataOrigin` on Health Connect records to show which device provided each data point (e.g. *Pixel Watch 3*, *Galaxy Watch 6*, *Garmin Connect*, *Oura Ring*, *Whoop*).
  - Surface active provider badges on the Data Sources screen.

### 7.2 Background Sync Reliability & Battery Efficiency
- **Smart Adaptive Sync Scheduling**:
  - Run sync jobs frequently during typical wake-up hours (6:00 AM – 9:00 AM) to catch fresh sleep data immediately.
  - Throttle sync frequency during daytime work hours to preserve battery.
- **Battery Optimization Exemption Guide**:
  - Provide a clean in-app prompt guiding users to disable OEM battery restrictions (Samsung, Xiaomi, Pixel) to prevent background WorkManager sync delays.

---

## 8. Data Privacy, Security & Sovereignty

### 8.1 Encrypted Backups & KeyStore Integration
- **AES-GCM Encrypted Local Backup**:
  - Allow users to export their entire Hangry SQLite database encrypted with a user passphrase via Android Keystore.
  - One-tap restore on a new device with zero cloud intermediary.

### 8.2 Standard Open Export Formats
- **Multi-Format Export**:
  - Export historical biometric data as **CSV** (for spreadsheet analysis), **JSON** (for personal health archives), or **GPX** (for workout tracks).
- **Granular Record Deletion**:
  - Allow selective deletion of single-day records, single meal logs, or individual posture scans without having to wipe the entire database.

---

## 9. Performance, Architecture & Testing

### 9.1 Baseline Profiles & 120Hz Animation Tuning
- **Jetpack Macrobenchmark & Baseline Profiles**:
  - Generate Android Baseline Profiles to eliminate initial JIT compilation stutter and achieve instant cold-start launch times (<350ms).
  - Profile and enforce continuous 120fps scrolling on complex LazyLists (`TrendsScreen`, `AiCoachScreen`).

### 9.2 Automated UI & Screenshot Regression Testing
- **Compose UI Test Suite**:
  - Add Paparazzi or Roborazzi snapshot testing for all core screens across light and dark theme tokens.
  - Verify layout integrity on varying screen densities, tablets, and foldable form factors.

---

## 10. Prioritized Implementation Matrix

| Phase | Feature / Improvement | Impact | Complexity | Status |
|:---:|---|:---:|:---:|:---:|
| **P0** | **Reclaim top space**: Eliminate redundant TopAppBar, remove app name/logo, move Recovery Hero up | High | Low | **Immediate** |
| **P0** | **RHR 24h Non-Sleeping Definition**: Calculate lowest non-sleeping HR in last 24h & lock for the day | High | Medium | **Immediate** |
| **P0** | **Ensure bottom clearance**: Verify edge-to-edge floating dock scroll clearance across all screens | High | Low | **Immediate** |
| **P1** | **Line Chart Scrubbers & Tooltips**: Interactive dragging to inspect historical biometric values | High | Medium | Near-Term |
| **P1** | **Micro-Sparklines**: 7-day mini trend sparklines inside Dashboard metric cards | Medium | Medium | Near-Term |
| **P1** | **ML Kit Pose Guidance Overlay**: Live alignment guidelines in `PostureCaptureScreen` | High | High | Near-Term |
| **P1** | **Barcode Scanner**: On-device camera scanning for instant food & calorie logging | High | Medium | Near-Term |
| **P2** | **Encrypted Local Backup**: Password-protected AES-GCM database export/import | High | Medium | Planned |
| **P2** | **Device Attribution Badges**: Show Pixel Watch / Galaxy Watch / Garmin icons on data sources | Medium | Low | Planned |
| **P2** | **Morning Readiness Notification**: Local automated daily recovery summary on wake-up | Medium | Low | Planned |
| **P2** | **Split-Screen Posture Comparer**: Before-and-after alignment slider for posture tracking | Medium | Medium | Planned |
| **P2** | **Baseline Profiles**: Ahead-of-time compilation profiles for zero-jank 120Hz scrolling | High | Medium | Planned |
