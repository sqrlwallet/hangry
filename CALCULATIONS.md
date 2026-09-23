# Hangry — Calculation Engine & Mathematical Specification

## 1. Philosophical Grounding

Hangry's calculation engine is built on three unshakeable rules:
1. **Personal Baselines, Not Population Averages**: A healthy resting heart rate or HRV for an endurance athlete is completely different from a casual walker. All scoring compares the user against their personal rolling baseline.
2. **Never Substitute Missing Data with Zero**: Missing records represent missing observations, not absence of physiological function. Missing components dynamically redistribute weights and adjust confidence ratings.
3. **Transparent & Deterministic**: The user can inspect positive and negative contributors for every daily score. Identical inputs will always produce identical results.

---

## 2. Hangry Recovery Score (0–100)

### 2.1 Core Formula
The daily *Hangry Recovery* score represents physiological readiness based on autonomic and restorative markers:

$$R_{\text{raw}} = \frac{\sum_{i \in \text{Available}} (w_i \cdot S_i)}{\sum_{i \in \text{Available}} w_i}$$

$$\text{Hangry Recovery} = \text{clamp}(0, 100, \text{round}(R_{\text{raw}}))$$

### 2.2 Component Weights & Heuristics
When all data streams are present:
- **HRV RMSSD ($w_{\text{hrv}} = 0.35$)**:
  $$\text{Ratio}_{\text{hrv}} = \frac{\text{HRV}_{\text{today}}}{\text{HRV}_{\text{7d\_baseline}}}$$
  $$S_{\text{hrv}} = \text{clamp}(0, 100, 65.0 + (\text{Ratio}_{\text{hrv}} - 1.0) \times 120.0)$$
  - Higher HRV relative to baseline indicates parasympathetic dominance and strong systemic recovery.
- **Resting Heart Rate ($w_{\text{rhr}} = 0.25$)**:
  $$\text{Ratio}_{\text{rhr}} = \frac{\text{RHR}_{\text{today}}}{\text{RHR}_{\text{7d\_baseline}}}$$
  $$S_{\text{rhr}} = \text{clamp}(0, 100, 65.0 + (1.0 - \text{Ratio}_{\text{rhr}}) \times 160.0)$$
  - Lower resting pulse indicates cardiovascular efficiency and autonomic recovery; elevated RHR suggests fatigue or systemic stress.
- **Sleep Duration ($w_{\text{sleep}} = 0.25$)**:
  $$\text{Ratio}_{\text{sleep}} = \frac{\text{SleepDuration}_{\text{today}}}{\text{Sleep}_{\text{baseline}}}$$
  $$S_{\text{sleep}} = \text{clamp}(0, 100, 65.0 + (\text{Ratio}_{\text{sleep}} - 1.0) \times 90.0)$$
- **Training Load Balance & Consistency ($w_{\text{load}} = 0.15$)**:
  $$S_{\text{load}} = \text{clamp}(0, 100, (\text{Consistency} \times 0.7) + 15.0)$$

---

## 3. Score States & Confidence Scoring

| State | Score Range | Meaning & Supportive Advice |
|---|---|---|
| **`PRIMED`** | 70–100 | Markers are elevated above baseline. Primed for high effort and challenging workouts. |
| **`BALANCED`** | 40–69 | Steady energy. Well-suited for consistent training and everyday active movement. |
| **`REBUILD`** | 0–39 | Body is recovering from high strain or short sleep. Prioritize restorative rest and sleep. |
| **`BUILDING_BASELINE`** | Null | Fewer than 3 usable days of history. Baseline is calibrating. |

### Confidence Levels
- **`HIGH`**: 7+ days of usable history and all primary metrics (Sleep, HRV, RHR) available for today.
- **`MEDIUM`**: 3–6 days of baseline history, or 1 non-critical component missing today.
- **`LOW`**: Fewer than 3 days of baseline, or multiple signals missing.

---

## 4. Sleep Metrics & Sleep Debt

- **Sleep Duration**: Actual recorded sleep interval from `SleepSessionRecord`.
- **Time in Bed**: Elapsed period from initial bedtime to final wake time.
- **Sleep Debt**:
  $$\text{Debt} = \max(0, \text{TargetSleepMinutes} - \text{SleepDurationMinutes})$$
- **Sleep Consistency**: Variance of bedtimes and wake times over rolling 7-day window.

---

## 5. Training Load (Strain Approximation)

Training load quantifies cardiovascular and neuromuscular effort from logged workouts:
$$\text{SessionLoad} = (\text{DurationMinutes} \times 0.8 + \text{CaloriesBonus} \times 5.0) \times M_{\text{intensity}}$$

`CaloriesBonus` is the workout's full calories ÷ 100 (the recorded total, or active calories plus the resting burn during it).

Intensity multipliers ($M_{\text{intensity}}$) come from each Health Connect exercise type's family (`WorkoutCategory`):
- Running, stair climbing, HIIT, boxing, boot camp, martial arts: $1.4$
- Cycling, elliptical, rowing machine, swimming, team and racquet sports, winter sports: $1.2$
- Strength training, weightlifting, calisthenics, climbing, rowing, paddling and other water sports: $1.1$
- Walking, hiking, golf, yoga, pilates, stretching: $0.7$
- Anything else: $1.0$

*Note: Training load is clearly indicated in the UI as a calculated estimate.*

---

## 6. Day Strain (0–21)

Day strain is a bounded, saturating measure of a single day's cardiovascular effort — it rises quickly at first and flattens as effort accumulates, so an already-hard day can't run away to an unbounded number.

### 6.1 Preferred path: heart-rate zones
When at least 20 continuous heart-rate samples exist for the day (`HangryStrainCalculator`), consecutive samples are turned into minutes spent in each of the five fixed cardio zones (the same 114/133/152/171 bpm boundaries used for `HeartRateZoneDistribution`), gaps over 10 minutes are discarded (device off/no signal), and each zone's minutes are weighted:

| Zone | bpm | Points per minute |
|---|---|---|
| 1 | < 114 | 0.05 |
| 2 | 114–132 | 0.20 |
| 3 | 133–151 | 0.45 |
| 4 | 152–170 | 0.80 |
| 5 | ≥ 171 | 1.20 |

$$\text{Points} = \sum_{\text{zone}} \text{minutes}_{\text{zone}} \times \text{weight}_{\text{zone}}$$
$$\text{Day Strain} = \text{clamp}(0, 21, 21 \times (1 - e^{-0.04 \times \text{Points}}))$$

Confidence is `HIGH` at 200+ samples, `MEDIUM` otherwise.

### 6.2 Fallback: workout-only estimate
When there's no continuous heart-rate trace for the day (common without a wearable worn all day), strain is instead estimated from logged workouts via the existing `TrainingLoadCalculator.estimateSessionLoad`, converted to the same points scale ($\text{Points} = \text{SessionLoad} \times 0.4$) and passed through the same saturating formula above. This path is always labeled `LOW` confidence and the UI marks it as an estimate, since it can't see effort outside logged sessions.

### 6.3 Recovery ↔ Strain coaching
Rather than folding strain into the Hangry Recovery formula itself (which would silently change the meaning of historical scores), a separate `recommendStrainTarget` maps today's `RecoveryState` to a target strain **band**, scaled off the user's own rolling recent-strain average rather than a fixed population number:

| State | Target band (× rolling average) |
|---|---|
| `PRIMED` | 1.0× – 1.3× |
| `BALANCED` | 0.75× – 1.05× |
| `REBUILD` | 0.3× – 0.65× |
| `BUILDING_BASELINE` | 0.5× – 1.0× |

*Simplification: intraday/real-time strain and biological-sex-specific heart-rate-zone formulas are out of scope for this version — zones use fixed bpm bands, not %-of-max-HR.*

---

## 7. Sleep Need & Performance

`HangrySleepCalculator` computes a personal, day-specific sleep need rather than a static 8-hour target:

$$\text{SleepNeed} = \text{PersonalBaseline}_{\text{7d avg}} + \text{DebtCarryover} + \text{StrainAdjustment}$$

- **Debt carryover**: 30% of yesterday's unpaid sleep debt (`max(0, target - actualDuration)`), capped at 90 minutes.
- **Strain adjustment**: if yesterday's day-strain exceeded the user's rolling average strain, add up to 60 minutes, scaled by how far above average yesterday was (`(ratio - 1.0) × 60`, clamped to [0, 60]).
- **Sleep Performance**: $\text{clamp}(0, 150, \text{round}(\text{duration} / \text{SleepNeed} \times 100))$.
- **Recommended bedtime**: the rolling average wake time-of-day over the last 7 sessions, minus tonight's `SleepNeed`. Returns no suggestion until there's at least one recent session to anchor a wake-time baseline on.

*Simplification: naps are not tracked in the current schema and are intentionally excluded from this formula rather than silently approximated.*

---

## 8. Calorie Burn & Goal Target

`HangryCalorieCalculator` estimates daily energy expenditure and, optionally, a personalized daily calorie target toward a user-set weight goal. This is a wellness estimate, not medical or dietary advice (see PRODUCT_SCOPE.md's non-medical notice).

### 8.1 Resting Metabolic Rate (BMR) — Mifflin-St Jeor

$$\text{BMR}_{\text{male}} = 10w + 6.25h - 5a + 5 \qquad \text{BMR}_{\text{female}} = 10w + 6.25h - 5a - 161$$

where $w$ = weight (kg, latest known reading), $h$ = height (cm), $a$ = age (years), all set once in Settings → Goals & Body Metrics. `OTHER` averages the male/female offsets ($-78$) rather than forcing a binary choice — documented as an approximation, not a clinical claim. BMR is computed once from the *latest* known weight and applied across any recomputed date range (a simplification: point-in-time historical weight isn't reconstructed).

### 8.2 Active Calories & Active Time

`ActiveActivityCalculator` works these out when each day's summary is built:

- **Workouts count in full** — every calorie burned during the workout (the recorded total; if a source only reports active calories, those plus the resting burn over the workout's duration), and every minute of it as active time.
- **Steps outside workouts count too** — every 150 steps adds one active minute, and each step costs its full amount: the walking itself ($0.5 \text{ kcal} \times w \times$ stride km, stride $= 0.415h$) plus the resting burn during the 1/150 minute it takes ($\text{BMR} / 1440 / 150$).

$$\text{ActiveCalories} = \sum \text{WorkoutCalories} + \text{Steps}_{\text{outside workouts}} \times \text{kcal per step}$$
$$\text{ActiveMinutes} = \sum \text{WorkoutMinutes} + \frac{\text{Steps}_{\text{outside workouts}}}{150}$$

Steps inside a workout aren't counted again as steps, since the workout already covers them. If a workout has no calorie figure, its minutes still count and its steps are costed as steps. Without weight or height a step can't be costed, so Health Connect's own active-calorie total is used instead.

Stored days calculated under an older rule (`calculationVersion` below `SUMMARY_CALCULATION_VERSION`) are rebuilt automatically the next time the app opens.

### 8.3 Daily Burn Breakdown

$$\text{Total Burned} = \text{BMR} \times \frac{1440 - \text{ActiveMinutes}}{1440} + \text{ActiveCalories}$$

Active calories already include the resting burn during active time, so BMR only covers the minutes that weren't active — otherwise that resting burn would be counted twice. For the on-screen breakdown, `Exercise` is the workouts' full calories and `NEAT = max(0, ActiveCalories − Exercise)`. Never substitutes a missing BMR or active-calorie reading with zero — the total is left `null` and the UI shows "—" with guidance on what's missing (§1).

### 8.4 Maintenance Calories

`EnergyBalanceCalculator` averages the 7 full days before today (days with step data only), counting activity exactly as §8.2:

$$\text{Maintenance} = (\overline{\text{Resting}} + \overline{\text{StepCalories}} + \overline{\text{WorkoutCalories}}) \times 1.10$$

where Resting is BMR over each day's inactive minutes (Katch–McArdle from lean mass when there's a measured body-fat scan, else Mifflin–St Jeor) and the extra 10% is the thermic effect of food. It needs weight, height, age, sex and at least 5 of the 7 days with step data; otherwise it lists what's missing instead of guessing.

### 8.5 Daily Calorie Goal

Given a weight goal and target date (Settings → Goals & Body Metrics), plus today's Total Burned as a TDEE estimate:

$$\text{DailyAdjustment} = \frac{(\text{GoalWeight} - \text{CurrentWeight}) \times 7700}{\text{DaysRemaining}} \qquad \text{Target} = \text{TDEE} + \text{DailyAdjustment}$$

(7700 kcal ≈ 1 kg of body mass, a standard approximation.) Two safety guardrails, consistent with the app's non-shaming, non-medical wellness stance:
- **Pace cap**: `|DailyAdjustment|` is capped at 750 kcal/day (≈0.68 kg/week); a more ambitious target date is flagged (`isPaceAdjustedForSafety`) and the guidance explains the safer pace used instead.
- **Calorie floor**: a weight-loss target never drops below 1200 kcal/day.

A recommendation is only returned once TDEE, current weight, goal weight, and a future target date are all available — otherwise `null`, so the UI can show its own "add your goal" prompt rather than a fabricated number.

### 7.1 Sleep Quality Score (0–100)

Sleep Performance (§7 above) is a **quantity** measure — how much you slept versus how much you needed, and can exceed 100%. Sleep Quality is a separate, bounded **quality** measure of how *good* that sleep actually was, independent of duration:

| Component | Formula | Weight |
|---|---|---|
| Efficiency | $\text{clamp}(0, 100, \text{duration} / \text{timeInBed} \times 100)$ — how much of time in bed was actually spent asleep | 0.40 |
| Restorative sleep | $\text{clamp}(0, 100, \text{restorativePercentage} / 45 \times 100)$ — deep+REM share of total sleep, scored against a healthy ~45% target rather than used raw | 0.35 |
| Consistency | `consistencyPercentage` (§4) — night-to-night regularity vs. the 7-day baseline | 0.25 |

$$\text{Sleep Quality} = \text{round}\left(\frac{\sum w_i \cdot S_i}{\sum w_i}\right)$$

If `timeInBedMinutes` isn't available for a session, the efficiency component is dropped and the remaining two dynamically re-weight — consistent with the app's "never substitute missing data with zero" rule (§1).

---

## 9. Autonomic Stress Calculation (0–100)

Health Connect does not expose a native `StressRecord`. Commercial wearables calculate stress on-device using proprietary algorithms and do not export direct stress values into Health Connect. Hangry computes an objective physiological stress index based on autonomic biomarkers (`HeartRateVariabilityRmssdRecord` and `RestingHeartRateRecord`) compared to the user's rolling 7-day personal baseline:

### 9.1 Biomarkers & Heuristics
- **HRV RMSSD Suppression ($w = 0.60$)**:
  $$Ratio_{hrv} = \frac{\text{HRV}_{\text{today}}}{\text{HRV}_{\text{7d\_baseline}}}$$
  $$S_{hrv} = \text{clamp}(0, 100, 50.0 + (1.0 - Ratio_{hrv}) \times 150.0)$$
  Suppressed HRV relative to baseline indicates sympathetic autonomic dominance and physical/mental stress.
- **Resting Heart Rate Elevation ($w = 0.40$)**:
  $$\Delta_{rhr} = \text{RHR}_{\text{today}} - \text{RHR}_{\text{7d\_baseline}}$$
  $$S_{rhr} = \text{clamp}(0, 100, 35.0 + \Delta_{rhr} \times 7.0)$$
  Elevated resting heart rate above baseline indicates cardiovascular strain or systemic fatigue.
- **Cardiovascular Strain Adjustment**: If day strain exceeds 14.0, up to +8 points are added to reflect heavy training fatigue.

### 9.2 Stress States
- `LOW` (0–34): Parasympathetic dominance, low systemic stress.
- `MODERATE` (35–64): Normal everyday autonomic activity.
- `ELEVATED` (65–79): Elevated physiological strain or fatigue.
- `HIGH` (80–100): High autonomic stress; restorative recovery recommended.
- `BUILDING_BASELINE`: Calibrating until at least 3 usable days of history exist.

