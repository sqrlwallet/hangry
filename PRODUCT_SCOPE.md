# Hangry — Product Scope & Specifications

## 1. Product Vision & Mission

**Hangry** is a local-first Android wellness, recovery, sleep, and fitness insights app. Inspired by the recovery-tracking category, Hangry translates complex physiological signals into clear, actionable daily decisions without shame, anxiety, or clinical jargon.

### Brand Principles
- **Data should inform, never intimidate or shame**: Insights highlight what the user can do next rather than scolding them for poor metrics.
- **Human and approachable**: A conversational, energetic tone grounded in practical daily energy management.
- **Absolute privacy**: Health data belongs to the user and stays strictly on the user's device. No cloud sync, no tracking, no ad brokers.
- **Distinct identity**: Hangry establishes its own visual and conceptual language, never copying WHOOP or other competitors' branding, UI dials, proprietary scores, or terminology.

---

## 2. Product Boundary & Medical Disclaimer

> [!IMPORTANT]
> **Non-Medical Wellness Product Notice**
> Hangry is strictly a personal lifestyle, wellness, and fitness application. It is **not** a medical device, diagnostic tool, or clinical decision support system.
> - Hangry does **not** diagnose, treat, cure, or prevent any disease, illness, or medical condition.
> - Hangry does **not** provide clinical advice, medical prescriptions, or medical triage.
> - Calculations (such as the *Hangry Recovery* score, sleep debt estimates, and training loads) are heuristic wellness approximations based on rolling personal baselines, not clinically validated diagnostic measurements.
> - Users experiencing symptoms or health concerns are explicitly advised in the app to consult qualified medical professionals.

---

## 3. Strict Architectural Boundaries

To preserve trust and comply with health data stewardship best practices, Hangry enforces hard technical boundaries:
1. **Local-First & Device-Only**: All health data read from Android Health Connect is written to an encrypted/app-private SQLite database (via Room).
2. **Zero Backend Servers**: No external APIs, custom microservices, or remote databases.
3. **No Cloud Sync**: No Firebase, Supabase, AWS Amplify, or third-party cloud synchronization.
4. **Zero Analytics & Advertising SDKs**: No Google Analytics, Firebase Crashlytics with health data, Meta Pixel, or telemetry trackers.
5. **No Account Creation**: No username, email, phone number, password, or OAuth login required. The app opens directly to the user's local dashboard.
6. **Complete Data Sovereignty**: The user retains one-tap capabilities to export their data as plain JSON/CSV or completely wipe all local databases.

---

## 4. Feature Matrix Across Delivery Phases

| Feature Area | Phase 1 (Local Foundation) | Phase 2 (Health Connect Integration) | Phase 3 (Historical Import & Sync) | Phase 4 (Advanced Calculations) | Phase 5 (Release Hardening) |
|---|---|---|---|---|---|
| **Health Connect Client** | Direct Health Connect Source | API status, permission education & requests | Bounded historical sync & checkpoints | Incremental sync & change tokens | Production permission declarations |
| **Local Database (Room)** | Complete 14-entity schema & DAOs | Live data persistence | Batch imports, deduplication | Rolling baseline storage | Migration verification & wipe tests |
| **Recovery Engine** | Deterministic 0-100 score engine | Real baseline calculations | Multi-day trend baselines (7d/28d) | Factor contribution attribution | Versioned algorithm release |
| **Sleep Tracking** | Session duration, consistency | Health Connect sleep sessions | Historical sleep debt & averages | Trend analysis | Manual sleep adjustments |
| **Training Load** | Duration & intensity estimates | Exercise session records | Multi-workout aggregation | Load vs recovery balancing | Heart rate zone refinement |
| **Heart Metrics** | Resting HR & HRV RMSSD | Source-provided HRV RMSSD | Multi-source conflict handling | Outlier detection | Data quality tagging |
| **Data Sovereignty** | In-memory & test wipe | Database clear hooks | Selective record deletion | Cascade recalculation | Full JSON export & app reset |
| **UI Experience** | Dashboard & Theme Preview | Permission onboarding | Sync progress screen | Trend drill-downs | Accessibility & dark/light audit |

---

## 5. Supported Health Connect Records

Hangry integrates with Google Health Connect to read the following records when granted by the user:
- `SleepSessionRecord`: Sleep durations, start/end bounds, time in bed.
- `StepsRecord`: Cumulative daily step counts.
- `DistanceRecord`: Movement distance.
- `TotalCaloriesBurnedRecord` & `ActiveCaloriesBurnedRecord`: Energy expenditure.
- `ExerciseSessionRecord`: Workouts (running, cycling, strength, etc.) with timestamps.
- `HeartRateRecord`: Time-series heart rate samples.
- `RestingHeartRateRecord`: Daily resting pulse values.
- `HeartRateVariabilityRmssdRecord`: Root Mean Square of Successive Differences for parasympathetic assessment.
- `WeightRecord` & `HeightRecord`: Biometrics for optional personalization.

*Nutrition tracking is intentionally omitted until explicitly scoped in future revisions.*
