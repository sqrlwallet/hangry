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

A day with **no sleep, resting heart rate or HRV** gets no score at all (shown as "—"). Missing HRV alone still counts as an excellent reading next to real ones, but it can't stand in for all three. The consistency part of the load component is only included once there are 3 recent nights to judge it; it's never assumed.

### 2.2 Component Weights & Heuristics
Baselines are the user's own **last 30 days** (at least 3 usable days before a score appears). HRV and resting heart rate are scored by how unusual today is *for this user*, in standard deviations ($z$) from their normal. Someone whose HRV naturally swings a lot needs a bigger drop to be flagged than someone whose HRV is steady. A reading right at normal scores 65; each SD moves it 15 points.

- **HRV RMSSD ($w_{\text{hrv}} = 0.35$)**: last night's HRV, the average of readings taken during the main sleep (±30 min), else the day's first reading. Compared on a log scale, as in HRV research:
  $$z_{\text{hrv}} = \frac{\ln \text{HRV}_{\text{today}} - \overline{\ln \text{HRV}}_{30d}}{\max(\text{SD}_{30d}, 0.08)}, \quad S_{\text{hrv}} = \text{clamp}(0, 100, 65 + 15 z_{\text{hrv}})$$
  When the device reports no HRV, the user's "how do you feel" answer stands in (Excellent = 90 by default).
- **Resting Heart Rate ($w_{\text{rhr}} = 0.25$)**: the lowest 5-minute average heart rate during the main sleep. Without overnight heart rate: Health Connect's resting heart rate, else the lowest 5-minute awake average. Scored against the monthly (30-day) average: at or below it is 100, and each bpm above it costs 2 points (1 bpm → 98, 3 → 94, 10 → 80):
  $$S_{\text{rhr}} = \text{clamp}(0, 100, 100 - 2 \times \max(0, \text{RHR}_{\text{today}} - \overline{\text{RHR}}_{30d}))$$
- **Sleep ($w_{\text{sleep}} = 0.25$)**: time asleep against the night's **sleep need** (§7), with a quarter for sleep quality (§7.1) when known:
  $$S_{\text{need}} = \text{clamp}(0, 100, 85 + (\tfrac{\text{asleep}}{\text{need}} - 1) \times 150), \quad S_{\text{sleep}} = 0.75\,S_{\text{need}} + 0.25\,\text{Quality}$$
  Meeting your need scores 85, 110% or more scores 100, 80% scores 55.
- **Strain balance ($w_{\text{load}} = 0.15$)**: yesterday's final strain against the target band it was given (§6.3, from yesterday's recovery and the week before). In the band, or lighter, scores 100 (lighter means there's room to push today); over it costs 12 points per strain point: $S = \text{clamp}(0, 100, 100 - 12 \times \max(0, \text{strain} - \text{high}))$. Sleep consistency counts in the Sleep Score (§7.1), not here.
- **Breathing rate**: not weighted, but a rise of 1+ breath/min over the 30-day normal (with 5+ days of history) subtracts $\text{clamp}(0, 12, (\text{rise} - 0.5) \times 6)$ points and is listed as a contributor, since a raised breathing rate is often an early sign of illness.

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

- **Sleep Duration**: time **asleep**. When the device records stages it's deep + REM + light (+ unspecified "sleeping"); awake spells and untracked gaps are excluded. Without stages it's the whole session.
- **Time in Bed**: from bedtime to final wake time. **Efficiency** = asleep ÷ in bed.
- **Nights**: the longest session per wake-up day; naps and split sessions don't count as extra nights.
- **Sleep Debt**: $\max(0, \text{SleepNeed} - \text{asleep})$ for the night.
- **Sleep Consistency**: regularity of bed and wake times over the last 7 nights (including this one), from the circular standard deviation of each, so 23:50 and 00:10 are 20 minutes apart, not 23 hours:
  $$\text{Consistency} = \text{clamp}(0, 100, 100 - 0.5 \times \tfrac{\text{SD}_{\text{bed}} + \text{SD}_{\text{wake}}}{2})$$
  A 30-minute spread scores 85, an hour 70, two hours 40. Needs 3 nights.

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
Zones are **personal**, from heart-rate reserve (Karvonen). Max heart rate is the user's own (Settings) or Tanaka's $208 - 0.7 \times \text{age}$ (age 35 if unknown); resting heart rate is their 30-day average (60 if unknown).

| Zone | % of heart-rate reserve | Points per minute (Edwards TRIMP) |
|---|---|---|
| Light activity | 40–50% | 0.5 |
| 1 | 50–60% | 1 |
| 2 | 60–70% | 2 |
| 3 | 70–80% | 3 |
| 4 | 80–90% | 4 |
| 5 | 90%+ | 5 |

Heart rate recorded **during sleep never counts**, and below 40% of reserve (sitting, resting) earns nothing, so wearing a watch all day no longer adds strain by itself. Gaps over 10 minutes are discarded (device off). Workouts logged without heart rate during them (e.g. a phone-tracked run) add their workout estimate (§6.2) on top.

$$\text{Day Strain} = \text{clamp}(0, 21, 21 \times (1 - e^{-\text{Points} / 150}))$$

An easy hour lands around 4–12 depending on effort, a hard hour (zone 4) around 17. Confidence is `HIGH` at 200+ awake samples, `MEDIUM` otherwise.

### 6.1a Live during the day
Today's strain is recalculated as the day goes on: every 5 minutes while the app is open (a quiet pull of today's heart rate, workouts and steps from Health Connect) and hourly in the background. The Today screen leads with yesterday's final strain and shows today's "so far" under it with today's target. The first sync after midnight recalculates yesterday with its complete data, and from then on it's shown as the final number for that day.

### 6.2 Fallback: workout-only estimate
With under 20 awake heart-rate samples, strain is estimated from logged workouts: $\text{Points} = \text{SessionLoad} \times 2.4$ (a 45-minute run ≈ 200 points ≈ strain 15), through the same curve. Always `LOW` confidence and labelled as an estimate. A day with neither heart rate nor workouts stores **no** strain (not 0), so it doesn't drag rolling averages and strain targets down.

### 6.3 Recovery ↔ Strain coaching
Rather than folding strain into the Hangry Recovery formula itself (which would silently change the meaning of historical scores), a separate `recommendStrainTarget` maps today's `RecoveryState` to a target strain **band**, scaled off the user's own rolling recent-strain average rather than a fixed population number:

| State | Target band (× rolling average) |
|---|---|
| `PRIMED` | 1.0× – 1.3× |
| `BALANCED` | 0.75× – 1.05× |
| `REBUILD` | 0.3× – 0.65× |
| `BUILDING_BASELINE` | 0.5× – 1.0× |


---

## 7. Sleep Need & Performance

`HangrySleepCalculator` computes a day-specific sleep need starting from the user's **sleep goal** (Settings, 8 h default), not their recent average, which would make chronically short sleep look normal:

$$\text{SleepNeed} = \text{Goal} + \text{DebtRepayment} + \text{StrainAdjustment}, \quad \le \text{Goal} + 1.5\text{ h}$$

- **Debt repayment**: sleep debt is paid back over up to two weeks, not in one night. The debt is the running shortfall against the goal over the last 14 nights (longer nights pay some back), and each night needs $\text{debt} / 14$ extra, at most 60 minutes. Three nights an hour short adds about 13 minutes.
- **Strain adjustment**: if yesterday's strain exceeded the 7-day average, add `(ratio − 1) × 30` minutes, up to 30.
- **Tonight's need** (Sleep screen) is the same with last night included in the debt.
- **Sleep Performance**: $\text{clamp}(0, 150, \text{round}(\text{asleep} / \text{SleepNeed} \times 100))$.
- **Recommended bedtime**: the circular-mean wake time over the last 7 nights minus tonight's need.

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

### 7.1 Sleep Score and Sleep Quality (0–100)

Both use only what was measured and re-weight around anything missing (never scored as zero). Stages are never estimated from fixed percentages.

| Component | Score | Sleep Score weight | Quality weight |
|---|---|---|---|
| Need met | Sleep Performance, capped at 100 | 0.50 | — |
| Efficiency | 92%+ asleep in bed = 100, 65% or less = 0 | 0.15 | 0.40 |
| Restorative | deep vs 15% and REM vs 20% of sleep, each capped, averaged | 0.20 | 0.35 |
| Consistency | §4 | 0.15 | 0.25 |

**Sleep Score** is the headline night score (how much you got and how well); **Quality** is how well you slept regardless of length. Both are stored on the daily summary.

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


---

## 10. Body Age

`BodyAgeCalculator` estimates how old your body "acts" from the last 30 days, compared with typical values for your age and sex. Each factor adds or removes a capped number of years; the total is capped at ±12 years. Factors with no data are listed as "not counted yet", never assumed. It uses your **exact age from your date of birth** (e.g. 34.7, via `AgeMath`), falling back to a typed-in whole age, and needs at least 7 days with data and at least 4 measured factors. The previous-month comparison uses your age a month ago.

| Factor | Neutral point | Effect (cap) |
|---|---|---|
| Cardio fitness (VO₂ max) | Typical for age/sex: 47 (men) / 40 (women) at 20, −0.4 ml/kg/min per year | 2 years per 3.5 ml/kg/min (1 MET) from typical (±5) |
| Resting heart rate | 60 bpm | 1 year per 5 bpm (±3) |
| HRV (RMSSD) | 62 − 0.6 × (age − 20) ms, min 20 | ± up to 1.5 years, scaled by % from typical |
| Daily steps | 5,000–7,499 | ≥12k −2, ≥10k −1.5, ≥7.5k −1, <5k +1, <3k +2 |
| Weekly exercise (non mind-body workouts) | 75–149 min | ≥300 −2, ≥150 −1, <75 +1, <30 +2 |
| Strength sessions a week | — | ≥2 −1.5, ≥1 −0.5, 0 +1 |
| Sleep | — | 7–9 h −1, 6–7 h or >9 h +0.5, <6 h +2 |
| Sleep consistency | 70–84% | ≥85% −1, <70% +1 |
| Body composition | Body-fat scan if within 180 days, else BMI | Healthy −1 (BMI −0.5), high +1, very high +2.5 |

Zero workouts only counts as "no exercise" when the person's apps have recorded workouts before; otherwise exercise and strength are left out. Body Age for the 30 days before is also computed to show the month-over-month change. It's a motivational estimate, not a medical measurement.

## 11. Duplicate data from several apps

Health Connect keeps a separate copy from every app that records the same thing, such as a watch and its phone app, or one run that Strava, Samsung Health and Fit all save. Hangry counts each real-world event once (`HealthDedup`, `RealHealthConnectDataSource`).

| Data | How it's counted once |
|---|---|
| Daily steps, distance, active calories, water | Health Connect's own aggregate totals, which count each minute once using the app priority order in Health Connect settings. Raw records are not added up. |
| Workouts | Copies from different apps that overlap by at least 50% of the shorter one are the same workout. Hangry keeps the copy with the most detail (calories, distance, steps, sets, laps, power, title, a specific type), then the longer one. The same app's copies are merged only if they start and end within 2 minutes. |
| A workout's calories, steps, distance, elevation, power | Taken from one app: the one that recorded the workout, otherwise the app with the most. |
| Sleep | The same overlap rule. Hangry keeps the copy with sleep stages, then the longer one. A nap and the night's sleep don't overlap, so both are kept. |
| Weigh-ins, meals | A reading from another app with the same value (weight to 0.1 kg, or a meal's name and calories) within 2 minutes is a copy. Entries from the same app are all kept, so two identical snacks count as two. |

Each sync also removes imported records in the synced window that Health Connect no longer returns: records deleted or edited at the source, and duplicates the rules above dropped. Anything entered in Hangry itself is never removed. Without full-history access, this applies only to the last 30 days.

Heart-rate strain needs no special handling. It adds up the time between consecutive samples, so interleaved samples from two devices don't double the minutes.

After an update that changes these rules, all imported history is re-read once, so earlier days are corrected too (`DEDUP_VERSION`).
