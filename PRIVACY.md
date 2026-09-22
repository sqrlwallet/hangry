# Hangry — Privacy & Data Protection Specification

## 1. Absolute Local-Only Guarantee

Hangry is designed on the principle that your body's physiological metrics belong solely to you.

```
       +------------------------------------------------------+
       |                   Your Android Phone                 |
       |                                                      |
       |   +------------------+         +-----------------+   |
       |   |  Health Connect  | ------> |    Hangry App   |   |
       |   | (System Storage) |         | (Private SQLite)|   |
       |   +------------------+         +-----------------+   |
       +------------------------------------------------------+
                                   |
                         NO DATA LEAVES DEVICE
                                   |
                                   x  (Zero Cloud Servers)
                                   x  (Zero Analytics Trackers)
                                   x  (Zero Advertising SDKs)
```

- **Zero Remote Cloud Servers**: Hangry operates no servers, databases, or API proxies.
- **No User Accounts**: No email, username, or login required.
- **No Analytics / Advertising SDKs**: No Google Analytics, Firebase, Facebook SDK, or ad tracking frameworks are bundled in the binary.
- **App-Private Storage**: All health data resides in the app's protected internal SQLite sandbox (`/data/user/0/com.kevan.hangry/databases/hangry.db`), guarded by Android Linux filesystem permissions and SELinux policies.

---

## 2. User Data Controls & Sovereignty

Hangry puts full data management in the user's hands:
1. **Export Local Data**: Export the entire database as plain, unencrypted JSON or CSV files to the user's local filesystem at any time.
2. **Delete Selected Records**: Remove specific dates, workouts, or sleep sessions.
3. **Delete All Health Data**: Execute a complete local purge of all imported records and derived metrics.
4. **App Reset**: Clears database tables, SharedPreferences, and resets onboarding state.

---

## 3. Post-Deletion Verification Protocol

When deletion is triggered, Hangry enforces:
- **Immediate Database Execution**: Target rows are deleted inside an atomic SQLite transaction.
- **Cascading Recalculation**: Derived records in `DailyHealthSummaryEntity` and `RecoveryScoreEntity` are immediately recomputed or removed.
- **UI Refresh**: Reactive `Flow` emissions trigger an instant UI update to display calibration or empty states.
- **Export Scrubbing**: Subsequent exports will contain zero traces of the deleted records.
