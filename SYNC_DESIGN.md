# Hangry — Synchronization Engine & Idempotency Specification

## 1. Synchronization Architecture

Hangry's synchronization engine is built for **resilience, bounded memory usage, and absolute idempotency**.

```
             +------------------------------+
             | User triggers "Sync Now" or  |
             | WorkManager periodic job     |
             +------------------------------+
                            |
                            v
             +------------------------------+
             | Read SyncState checkpoint    |
             | Apply 2-hour overlap window  |
             +------------------------------+
                            |
                            v
             +------------------------------+
             | Fetch records in bounded     |
             | batches (max 14 days/query)  |
             +------------------------------+
                            |
                            v
             +------------------------------+
             | Generate record fingerprints |
             | Deduplicate in-memory vs DB  |
             +------------------------------+
                            |
                            v
             +------------------------------+
             | Batch INSERT OR REPLACE Room |
             +------------------------------+
                            |
                            v
             +------------------------------+
             | Recalculate affected Daily   |
             | Summaries & Recovery Scores  |
             +------------------------------+
                            |
                            v
             +------------------------------+
             | Update SyncStateEntity       |
             | with new checkpoint & stats  |
             +------------------------------+
```

---

## 2. Historical Synchronization Protocol

When a user connects Health Connect or requests historical import:
1. **Range Selection**: User selects from:
   - Last 7 days
   - Last 30 days
   - Last 90 days
   - Last 365 days
   - All available data (capped at 730 days / 2 years to protect battery/storage)
2. **Chunking / Bounded Execution**:
   - Never load an entire year's worth of data into Android heap memory simultaneously.
   - The sync engine chunks historical ranges into **7-day or 14-day execution windows**.
   - Each chunk is fetched, mapped, fingerprinted, written to Room within a database transaction, and freed before processing the next chunk.
3. **Progress Reporting**:
   - `SyncProgress(currentChunk, totalChunks, currentDataType, percentageComplete)` is emitted to the UI via `StateFlow`.
4. **Summary Pipeline**:
   - After historical chunks are imported, the engine iterates over affected calendar dates to compute `DailyHealthSummaryEntity` and baseline-dependent `RecoveryScoreEntity`.

---

## 3. Idempotency & Deduplication Engine

Running synchronization multiple times must **never** create duplicate records or skew baseline averages.

### 3.1 Stable Record Fingerprinting
For each record, a SHA-256 fingerprint is calculated:
```kotlin
fun generateFingerprint(
    dataType: String,
    sourcePackage: String?,
    sourceRecordId: String?,
    startTime: Instant,
    endTime: Instant,
    primaryValue: Double
): String = sha256("$dataType|$sourcePackage|$sourceRecordId|${startTime.toEpochMilli()}|${endTime.toEpochMilli()}|$primaryValue")
```
- A `UNIQUE` constraint is placed on `recordFingerprint` in SQLite.
- Using Room's `@Insert(onConflict = OnConflictStrategy.IGNORE)` or `@Upsert` ensures identical records are discarded or refreshed without ballooning row counts.

### 3.2 Overlap Window for Late-Arriving Records
Smartwatches and fitness trackers often sync historical data hours or days after the event occurs.
- On subsequent incremental syncs, Hangry queries `Health Connect` starting from:
  $$\text{QueryStart} = \text{LastSuccessfulSyncTimestamp} - 2\text{ hours}$$
- Deduplication prevents re-insertion of records already processed during earlier runs.

### 3.3 Absence Does Not Mean Deletion
A query to Health Connect returning fewer records than a previous query **does not** trigger local deletion. Local records remain preserved unless the user explicitly invokes the in-app "Delete Data" action.

---

## 4. WorkManager & Background Sync

- **Periodic Job**: Scheduled using Android `WorkManager` with `PeriodicWorkRequestBuilder<HealthSyncWorker>(6, TimeUnit.HOURS)`.
- **Constraints**:
  - `NetworkType.NOT_REQUIRED` (Local-only operation).
  - `RequiresBatteryNotLow(true)` (Preserve battery).
- **Foreground Service / Manual Sync**: "Sync Now" button in the Dashboard runs an immediate coroutine job in `ViewModelScope` with instant UI progress feedback.

---

## 5. Cascading Recalculation on Deletion

When the user exercises their right to delete data:
1. **Targeted Wipe**: If the user deletes a specific workout or day, corresponding rows in `ExerciseSessionEntity` or `SleepSessionEntity` are deleted.
2. **Recalculation Trigger**: The affected dates and subsequent rolling baseline windows (next 7 to 28 days) are automatically queued for summary re-computation.
3. **Full Wipe**: "Delete All Health Data" executes `db.clearAllTables()`, deletes SharedPreferences, resets `SyncStateEntity`, and returns the UI to the onboarding state.
