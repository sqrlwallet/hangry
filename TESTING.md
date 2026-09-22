# Hangry — Automated Testing Strategy & Verification Guide

## 1. Testing Philosophy

Hangry follows a test-driven approach to health data processing:
1. **Mathematical Rigor**: All calculation algorithms (Recovery, Sleep Debt, Training Load) are verified across edge cases, missing data, extreme outliers, and clamping boundaries.
2. **Sync Idempotency**: Repeated synchronizations must never introduce duplicate records or distort baseline calculations.
3. **Reactive UI State**: ViewModels are tested using `kotlinx-coroutines-test` and `Turbine` to guarantee deterministic state flow from database to UI.

---

## 2. Test Suite Inventory

| Test Class | Category | Primary Verifications |
|---|---|---|
| `HangryRecoveryCalculatorTest` | Unit Test | 0 days, 1-2 days (baseline building), 7-day baseline, missing HRV/Sleep/RHR, extreme outliers (clamping 0-100), determinism, non-shameful copy. |
| `HangrySleepCalculatorTest` | Unit Test | Sleep duration, time in bed, midnight crossing, 7d/30d rolling averages, sleep debt, supportive messaging. |
| `HangryTrainingLoadCalculatorTest` | Unit Test | Rest days, intensity weights (Running vs. Walking), multi-workout aggregation, acute-to-chronic load ratio. |
| `SyncPipelineIdempotencyUnitTest` | Unit Test | Repeated sync deduplication via SHA-256 fingerprints, post-deletion recalculation integrity. |
| `LocalExportManagerTest` | Unit Test | JSON and CSV export structure, metric formatting, zero PII leakage. |
| `FakeHealthConnectDataSourceTest` | Unit Test | 14-day deterministic trace generation, fingerprint consistency, edge cases (missing HRV, short sleep). |
| `DashboardViewModelTest` | Unit Test | Initial loading state, StateFlow emissions from combined repositories, manual `syncNow()` trigger. |
| `HangryDatabaseInstrumentedTest` | Instrumented Test | In-memory Room database creation, unique constraint conflict rejection, date queries, complete deletion. |

---

## 3. Running Automated Tests

### Host Machine Unit Tests (JVM)
```bash
export JAVA_HOME="/snap/android-studio/244/jbr"
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew testDebugUnitTest
```

### Build Verification
```bash
export JAVA_HOME="/snap/android-studio/244/jbr"
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew assembleDebug
```
