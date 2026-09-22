# Hangry — Architecture Documentation

## 1. Architectural Overview

Hangry employs a strict **Local-First Layered Architecture** with unidirectional data flow (UDF). The application treats local storage (Room SQLite) as the single source of truth for the presentation layer. The user interface never communicates directly with Android Health Connect.

```
       +---------------------------------------------+
       |           Google Health Connect             |
       +---------------------------------------------+
                              |
                              v
       +---------------------------------------------+
       |       Health Connect Data-Source Layer      |
       |  (Reads raw records, paginates, checks API) |
       +---------------------------------------------+
                              |
                              v
       +---------------------------------------------+
       |         Sync & Normalization Layer          |
       |  (Deduplicates, fingerprints, resolves TZ)  |
       +---------------------------------------------+
                              |
                              v
       +---------------------------------------------+
       |             Room Local Database             |
       |    (14 Entities: Sessions, HR, HRV, etc.)   |
       +---------------------------------------------+
                              |
                              v
       +---------------------------------------------+
       |             Calculation Engine              |
       |   (Deterministic recovery, sleep, load)     |
       +---------------------------------------------+
                              |
                              v
       +---------------------------------------------+
       |          Repositories & ViewModels          |
       |    (Exposes StateFlow to presentation)      |
       +---------------------------------------------+
                              |
                              v
       +---------------------------------------------+
       |             Jetpack Compose UI              |
       |   (M3 Design, HangryTokens, Accessible)     |
       +---------------------------------------------+
```

---

## 2. Layer Responsibilities & Boundaries

### 2.1 Health Connect Data-Source Layer
- **Responsibility**: Interfaces with `androidx.health.connect:connect-client`. Checks API availability, queries records in bounded time windows, and catches platform exceptions.
- **Contract**: Defined by `HealthConnectDataSource`.
- **Implementations**:
  - `RealHealthConnectDataSource`: Direct bounded queries against the system Health Connect provider. Hangry enforces a strict 100% real data policy—synthetic/mock data providers and fabricated metric fallbacks are strictly prohibited; when data is unrecorded, the app displays pending indicators ("—") per Rule 2 integrity.
- **Rule**: Encapsulates all Health Connect SDK record types and classes. Never leaks raw Health Connect objects to repositories or UI.

### 2.2 Sync & Normalization Layer
- **Responsibility**: Orchestrates historical imports and ongoing incremental synchronizations via `HealthSyncManager`.
- **Normalization**:
  - Converts time-series points and intervals into local UTC epoch timestamps (`Instant`) and local wall-clock dates (`LocalDate`).
  - Generates deterministic SHA-256 fingerprints: `hash(sourcePackage + recordId + startTime + originalValue)`.
  - Deduplicates records matching identical fingerprints or overlapping windows.
  - Manages `SyncStateEntity` tracking checkpoints, cursors, records read/inserted/skipped, and error logs.

### 2.3 Local Database Layer (Room)
- **Responsibility**: App-private persistence of raw normalized records and aggregated daily summaries.
- **Schema**: 14 distinct entities with indexes on `(date, dataType, sourcePackage, sourceRecordId)`.
- **Rule**: All queries return reactive `Flow<T>` streams to drive UI updates automatically upon data modification or sync completion.

### 2.4 Calculation Engine
- **Responsibility**: Pure, deterministic mathematical functions transforming historical and daily records into user-facing metrics.
- **Key Modules**:
  - `RecoveryCalculator`: Computes 0–100 Hangry Recovery score from rolling baselines (HRV RMSSD, resting heart rate, sleep duration vs. baseline, sleep consistency, recent training load).
  - `SleepCalculator`: Computes total sleep, time in bed, sleep consistency index, sleep debt, and 7d/30d trends.
  - `TrainingLoadCalculator`: Computes daily strain and rolling training load from exercise durations, types, and heart rates.
- **Rule**: Never convert missing values into 0. Output includes confidence levels (`LOW`, `MEDIUM`, `HIGH`) and human-readable contributor explanations.

### 2.5 Repository & ViewModel Layer
- **Responsibility**: Domain repositories combine Room DAOs and the calculation engine to expose clean `StateFlow<T>` objects to the UI.
- **ViewModels**: Retain UI state across configuration changes, handle user actions (e.g., "Sync Now", "Delete Data"), and manage loading/error/insufficient-data states.

### 2.6 Jetpack Compose UI
- **Responsibility**: Renders the visual interface using Material 3 and custom `HangryTokens`.
- **Rule**: 100% localization-ready via `strings.xml`. Zero hardcoded colors, sizes, or copy strings inside composables.

---

## 3. Core Architectural Interfaces

| Interface | Primary Responsibilities |
|---|---|
| `HealthConnectDataSource` | Query Health Connect records, verify API availability, check granted permissions. |
| `SleepRepository` | Query local sleep sessions, observe daily sleep durations, insert manual logs. |
| `WorkoutRepository` | Query exercise sessions, observe daily workouts and energy expenditure. |
| `HeartRateRepository` | Query continuous heart rate samples and resting heart rate entries. |
| `HRVRepository` | Query source-provided HRV RMSSD measurements. |
| `ActivityRepository` | Query daily step summaries, distances, and active calories. |
| `HealthSyncManager` | Execute idempotent sync pipeline, manage sync checkpoints and status. |
| `RecoveryCalculator` | Evaluate recovery score, confidence level, and positive/negative drivers. |
| `SleepCalculator` | Calculate sleep debt, consistency, and rolling duration baselines. |
| `TrainingLoadCalculator` | Estimate session load, daily training volume, and acute-to-chronic load. |
| `LocalExportManager` | Export local database records to JSON/CSV files on external request. |
| `UserProfileRepository` | Store user sleep targets, baseline preferences, and onboarding state. |
| `JournalRepository` | Manage daily subjective recovery logs and lifestyle notes. |
