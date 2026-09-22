# Hangry — Room Data Model & Storage Specification

## 1. Overview & Storage Guarantees

Hangry uses an app-private SQLite database managed via Android Jetpack Room (`HangryDatabase`). All data is stored in the app's internal sandbox (`/data/user/0/com.kevan.hangry/databases/hangry.db`).

### Schema Principles
1. **Audit & Traceability**: Every imported record retains its source ID, source package, original value, unit, import timestamp, and SHA-256 fingerprint.
2. **Immutable Imports**: Raw imported records are stored faithfully without destructive normalization. Daily summaries are calculated as derived records.
3. **Optimized Indexes**: Indexed on `(recordDate, startTime, dataType, sourceRecordId, sourcePackageName)`.
4. **No Raw PII in Logs**: Logcat outputs only record counts and status codes, never raw physiological numbers or timestamps.

---

## 2. Entity Definitions (14 Room Entities)

### 2.1 `UserProfileEntity`
Stores user profile configuration and baseline preferences.
- `id`: Long (Primary Key, autoincrement)
- `sleepDurationTargetMinutes`: Int (Default 480 / 8 hours)
- `baselineWindowDays`: Int (Default 7)
- `onboardingCompleted`: Boolean
- `preferredDataSourcePackage`: String?
- `createdAt`: Instant
- `updatedAt`: Instant

### 2.2 `SleepSessionEntity`
Stores imported and manually entered sleep intervals.
- `id`: Long (Primary Key, autoincrement)
- `sourceRecordId`: String? (Health Connect Record ID)
- `sourcePackageName`: String? (e.g. "com.google.android.apps.fitness")
- `recordFingerprint`: String (Unique index)
- `startTime`: Instant
- `endTime`: Instant
- `durationMinutes`: Int
- `timeInBedMinutes`: Int?
- `sleepQualityScore`: Int?
- `isManualEntry`: Boolean (Default false)
- `timeZoneOffset`: String?
- `importTimestamp`: Instant

### 2.3 `ExerciseSessionEntity`
Stores completed workouts and physical activities.
- `id`: Long (Primary Key, autoincrement)
- `sourceRecordId`: String?
- `sourcePackageName`: String?
- `recordFingerprint`: String (Unique index)
- `exerciseType`: String (e.g., "RUNNING", "CYCLING", "STRENGTH")
- `title`: String?
- `startTime`: Instant
- `endTime`: Instant
- `durationMinutes`: Int
- `activeCalories`: Double?
- `totalCalories`: Double?
- `estimatedTrainingLoad`: Double?
- `importTimestamp`: Instant

### 2.4 `HeartRateSampleEntity`
Stores continuous time-series heart rate samples.
- `id`: Long (Primary Key, autoincrement)
- `sourceRecordId`: String?
- `sourcePackageName`: String?
- `recordFingerprint`: String
- `timestamp`: Instant (Indexed)
- `bpm`: Double
- `importTimestamp`: Instant

### 2.5 `RestingHeartRateEntity`
Stores daily resting pulse records.
- `id`: Long (Primary Key, autoincrement)
- `sourceRecordId`: String?
- `sourcePackageName`: String?
- `recordFingerprint`: String (Unique index)
- `recordDate`: LocalDate (Indexed)
- `timestamp`: Instant
- `restingBpm`: Double
- `dataQualityState`: String ("VALID", "SUSPECT", "UNAVAILABLE")
- `importTimestamp`: Instant

### 2.6 `HrvMeasurementEntity`
Stores source-provided RMSSD HRV readings.
- `id`: Long (Primary Key, autoincrement)
- `sourceRecordId`: String?
- `sourcePackageName`: String?
- `recordFingerprint`: String (Unique index)
- `recordDate`: LocalDate (Indexed)
- `timestamp`: Instant
- `rmssd`: Double (in milliseconds)
- `dataQualityState`: String ("VALID", "SUSPECT", "UNAVAILABLE")
- `importTimestamp`: Instant

### 2.7 `StepsSummaryEntity`
Stores daily aggregated steps, distance, and calories.
- `id`: Long (Primary Key, autoincrement)
- `sourceRecordId`: String?
- `sourcePackageName`: String?
- `recordFingerprint`: String (Unique index)
- `recordDate`: LocalDate (Indexed)
- `stepCount`: Long
- `distanceMeters`: Double?
- `activeCalories`: Double?
- `importTimestamp`: Instant

### 2.8 `WeightMeasurementEntity`
Stores biometric weight logs.
- `id`: Long (Primary Key, autoincrement)
- `sourceRecordId`: String?
- `sourcePackageName`: String?
- `recordFingerprint`: String (Unique index)
- `timestamp`: Instant (Indexed)
- `weightKg`: Double
- `importTimestamp`: Instant

### 2.9 `DailyHealthSummaryEntity`
Normalized day-level aggregate combining all metric streams for a local calendar date.
- `date`: LocalDate (Primary Key)
- `sleepDurationMinutes`: Int?
- `sleepStartTime`: Instant?
- `sleepEndTime`: Instant?
- `sleepConsistencyScore`: Double? (0-100)
- `steps`: Long?
- `distanceMeters`: Double?
- `activeCalories`: Double?
- `totalCalories`: Double?
- `exerciseDurationMinutes`: Int?
- `exerciseCount`: Int
- `dailyTrainingLoad`: Double?
- `dayStrain`: Double? (0-21 bounded day-strain, see CALCULATIONS.md §6; added in schema v3)
- `restingHeartRate`: Double?
- `averageHeartRate`: Double?
- `hrvRmssd`: Double?
- `dataCompletenessRatio`: Double (0.0 to 1.0)
- `dataQualityState`: String ("COMPLETE", "PARTIAL", "INSUFFICIENT", "UNAVAILABLE")
- `calculationVersion`: Int
- `lastCalculatedTimestamp`: Instant

### 2.10 `RecoveryScoreEntity`
Stores calculated daily Hangry Recovery score and component attribution.
- `date`: LocalDate (Primary Key)
- `score`: Int? (0 to 100, null if building baseline)
- `confidence`: String ("LOW", "MEDIUM", "HIGH")
- `algorithmVersion`: Int
- `baselineWindowDays`: Int
- `hrvComponentScore`: Double?
- `rhrComponentScore`: Double?
- `sleepComponentScore`: Double?
- `trainingLoadComponentScore`: Double?
- `positiveContributors`: String (JSON list of positive driver strings)
- `negativeContributors`: String (JSON list of negative driver strings)
- `supportiveAdvice`: String
- `calculatedAt`: Instant

### 2.11 `JournalEntryEntity`
Stores daily subjective recovery observations.
- `date`: LocalDate (Primary Key)
- `perceivedRecovery`: Int (1-5 scale)
- `stressLevel`: Int? (1-5 scale)
- `muscleSoreness`: Int? (1-5 scale)
- `notes`: String?
- `updatedAt`: Instant

### 2.12 `SyncStateEntity`
Tracks synchronization status, checkpoints, and record counters per data type.
- `dataType`: String (Primary Key, e.g. "SLEEP", "HRV", "STEPS", "EXERCISE")
- `lastSuccessfulSyncTimestamp`: Instant?
- `historicalImportStart`: Instant?
- `historicalImportEnd`: Instant?
- `currentCursor`: String?
- `syncStatus`: String ("IDLE", "IN_PROGRESS", "SUCCESS", "FAILED", "PERMISSION_REVOKED")
- `lastError`: String?
- `recordsRead`: Int
- `recordsInserted`: Int
- `recordsUpdated`: Int
- `recordsSkipped`: Int
- `lastSyncTimestamp`: Instant

### 2.13 `CalculationMetadataEntity`
Audit log of calculation engine runs and parameters.
- `calculationType`: String (Primary Key, e.g. "RECOVERY", "TRAINING_LOAD")
- `algorithmVersion`: Int
- `lastRunTimestamp`: Instant
- `parametersHash`: String

### 2.14 `DataSourceEntity`
Catalog of discovered third-party health app providers submitting data via Health Connect.
- `packageName`: String (Primary Key)
- `displayName`: String
- `lastSeenTimestamp`: Instant
- `totalRecordsProvided`: Int
- `isEnabled`: Boolean (Default true)

---

## 3. Database Indexes

To guarantee 60fps UI performance and sub-100ms calculation queries, Room indexes are created for:
1. `(recordDate)` on `RestingHeartRateEntity`, `HrvMeasurementEntity`, `StepsSummaryEntity`, `DailyHealthSummaryEntity`.
2. `(startTime, endTime)` on `SleepSessionEntity` and `ExerciseSessionEntity`.
3. `(timestamp)` on `HeartRateSampleEntity` and `WeightMeasurementEntity`.
4. `(recordFingerprint)` UNIQUE on all imported entities for instant collision detection and deduplication.
5. `(sourceRecordId, sourcePackageName)` on all imported records for source tracing.
