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

- **HRV RMSSD ($w_{\text{hrv}} = 0.35$)**: each night's HRV is the average of readings taken during the main sleep (±30 min), else the day's first reading. HRV is compared on a log scale, as in HRV research. Single nights are noisy, so the score leans on the last 7 nights' average. Plews et al. 2013 found weekly averages track real changes in fitness; Garmin's HRV Status and Oura's HRV Balance work the same way. Last night still counts as a smaller part, so a sudden drop shows:
  $$z_{\text{night}} = \frac{\ln \text{HRV}_{\text{today}} - \overline{\ln \text{HRV}}_{30d}}{\sigma}, \quad z_{\text{week}} = \frac{\overline{\ln \text{HRV}}_{7d} - \overline{\ln \text{HRV}}_{30d}}{0.5\,\sigma}, \quad \sigma = \max(\text{SD}_{30d}, 0.08)$$
  $$S_{\text{hrv}} = 0.6\,(65 + 15 \min(z_{\text{week}}, 1.5)) + 0.4\,(65 + 15 z_{\text{night}}), \text{ each part clamped to } 0\text{–}100$$
  The 7-night average includes last night and needs at least 3 nights; with fewer, last night is scored on its own. A week's average moves about half as much as single nights do, so its normal band is half as wide. One low night after a normal week is softened, and a whole low week counts fully. A week running more than 1.5 SD *above* normal earns no more points, and past 2.5 SD it is flagged as possible overreaching, since very high HRV can follow very hard training too (Buchheit 2014).
  When the device reports no HRV, the user's "how do you feel" answer stands in (Excellent = 90 by default).
- **Resting Heart Rate ($w_{\text{rhr}} = 0.25$)**: the lowest 5-minute average heart rate during the main sleep. Without overnight heart rate: Health Connect's resting heart rate, else the lowest 5-minute awake average. Scored in standard deviations from the 30-day normal, like HRV, so a heart rate that naturally wanders needs a bigger rise to count:
  $$z_{\text{rhr}} = \frac{\text{RHR}_{\text{today}} - \overline{\text{RHR}}_{30d}}{\max(\text{SD}_{30d}, 1.5)}, \quad S_{\text{rhr}} = \text{clamp}(0, 100, 100 - 7\,[\max(0, z_{\text{rhr}}) + \max(0, -z_{\text{rhr}} - z_{\text{low}})])$$
  At or below normal is 100 and each SD above costs 7 points. For a steady 1.5 bpm SD that's about 4.7 points per bpm; for a 3 bpm SD about 2.3. The score is two-sided, as Oura's is. A reading far *below* normal also costs 7 points per SD past $z_{\text{low}} = \max(2, 8 / \text{SD})$, i.e. 2 SD and at least 8 bpm, since an unusually low resting heart rate can follow very hard training or be a sensor glitch. A modest dip still scores 100 and is listed as a positive.
- **Sleep ($w_{\text{sleep}} = 0.25$)**: time asleep against the night's **sleep need** (§7), with a quarter for sleep quality (§7.1) when known:
  $$S_{\text{need}} = \text{clamp}(0, 100, 85 + (\tfrac{\text{asleep}}{\text{need}} - 1) \times 150), \quad S_{\text{sleep}} = 0.75\,S_{\text{need}} + 0.25\,\text{Quality}$$
  Meeting your need scores 85, 110% or more scores 100, 80% scores 55.
- **Strain balance ($w_{\text{load}} = 0.15$)**: yesterday's final strain against the target band it was given (§6.3, from yesterday's recovery and the week before). In the band, or lighter, scores 100 (lighter means there's room to push today); over it costs 12 points per strain point: $S = \text{clamp}(0, 100, 100 - 12 \times \max(0, \text{strain} - \text{high}))$. Sleep consistency counts in the Sleep Score (§7.1), not here.
- **Breathing rate**: not weighted, but a rise of 1+ breath/min over the 30-day normal (with 5+ days of history) subtracts $\text{clamp}(0, 12, (\text{rise} - 0.5) \times 6)$ points and is listed as a contributor, since a raised breathing rate is often an early sign of illness.
- **Skin temperature**: not weighted either. The overnight change from the wearable's baseline (the average of the night's `SkinTemperatureRecord` deltas, dated to the morning it ends on) is compared with your own 30-day average of those changes, since each device uses its own baseline. A rise of 0.5 °C or more (with 5+ nights of history) subtracts $\text{clamp}(0, 10, (\text{rise} - 0.3) \times 20)$ points and is listed as a contributor. In the luteal phase (§12) the bar is 0.3 °C higher and the allowance is taken off the rise first.

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
- **Sleep Consistency**: regularity of bed and wake times over the last 14 nights (including this one; Apple uses 13, and a single week is noisy), from the circular standard deviation of each, so 23:50 and 00:10 are 20 minutes apart, not 23 hours:
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

$$\text{SleepNeed} = \min(\text{Goal} + \text{DebtRepayment} + \text{StrainAdjustment},\ \text{Goal} + 1.5\text{ h}) - \min(\text{Naps}, 1.5\text{ h})$$

- **Debt repayment**: sleep debt is paid back over up to two weeks, not in one night. The debt is the running shortfall against the goal over the last 14 nights (longer nights pay some back), and each night needs $\text{debt} / 14$ extra, at most 60 minutes. Three nights an hour short adds about 13 minutes.
- **Strain adjustment**: if yesterday's strain exceeded the 7-day average, add `(ratio − 1) × 30` minutes, up to 30.
- **Naps**: a nap taken after the day's main sleep ended lowers the *next* night's need minute for minute, up to 90 minutes. That night's need can then fall below the goal. WHOOP and Garmin both do this, because a nap relieves sleep pressure. For debt, each day counts only its sleep up to and including the main night, so a nap counts once, against the next night. It still adds to that day's sleep total and Sleep Score (§4).
- **Tonight's need** (Sleep screen) is the same with last night included in the debt, less any nap since you woke.
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

$$\text{Maintenance} = \frac{\overline{\text{Resting}} + \overline{\text{StepCalories}} + \overline{\text{WorkoutCalories}}}{0.9}$$

Resting is BMR over each day's inactive minutes: Katch–McArdle from lean mass when there's a measured body-fat scan, else Mifflin–St Jeor. The $\div 0.9$ is the thermic effect of food. Digesting costs about 10% of what you **eat** (Westerterp 2004), not 10% of what you burn. At maintenance you eat what you burn, so intake $I$ satisfies $I = \text{burn} + 0.1I$. It needs weight, height, age, sex and at least 5 of the 7 days with step data; otherwise it lists what's missing instead of guessing.

### 8.5 Daily Calorie Target

`DailyCalorieTargetCalculator` works the target out for each day from that day's own burn, not a weekly average, so a workout or a big walking day earns more food and a quiet day less:

$$\text{Burn}_{d} = \frac{\text{BMR} \times \frac{1440 - \text{NEATMinutes} - \text{WorkoutMinutes}}{1440} + \text{NEAT}_{d} + \text{Workouts}_{d}}{0.9} \times \frac{\text{Maintenance}}{\text{FormulaMaintenance}}$$

- **BMR** as in §8.4 (Katch–McArdle with a recent measured scan, else Mifflin–St Jeor), for the minutes that weren't active.
- **NEAT** is the day's steps outside workouts, costed as §8.2 — or the 7-day average (§8.4) while that's higher. Steps keep adding up all day, so this keeps the morning target from starting at almost nothing; once the day's steps pass a usual day, they set it.
- **Workouts** are the day's actual workouts, in full (§8.2), added as soon as they sync. Rest days add none.
- **÷ 0.9** is the thermic effect of food (10% of what you eat, §8.4).
- The last factor is what the logs have taught (§8.4's adaptive blend ÷ the activity formula); 1.0 until there are 14+ well-logged days and a weight trend.

Then the goal, from a goal weight (Settings → Goals & Body Metrics):

$$\text{Target}_{d} = \text{Burn}_{d} + \frac{\text{DailyAdjustment}}{0.9}$$

DailyAdjustment is the energy deficit or surplus. Eating less also saves the digestion of the food left out, so the food changes by a little more: a 550 kcal deficit is about 611 kcal less food. Pace caps apply to the energy deficit; floors apply to the food target.

- **With a target date**: $\text{DailyAdjustment} = \frac{(\text{GoalWeight} - \text{CurrentWeight}) \times 7700}{\text{DaysRemaining}}$ (7700 kcal ≈ 1 kg).
- **Without one**: a default pace of 0.5 kg/week to lose (−550 kcal/day) or 0.25 kg/week to gain (+275 kcal/day); within 0.5 kg of the goal it's maintenance.
- **No goal weight**: the target is the day's burn — eat it to stay the same weight.

Guardrails, consistent with the app's non-shaming, non-medical wellness stance:
- **Pace cap**: loss at most ~1% of body weight a week and 750 kcal/day; gain at most ~0.5% a week and 500 kcal/day. A target date asking for more is flagged (`isPaceAdjustedForSafety`) and the guidance explains the safer pace used.
- **Calorie floor**: a loss target never drops below resting burn or 1,500 kcal (male) / 1,200 kcal (female), unless the day's burn itself is lower.

The Today card, Nutrition tab, Calories and Nutrition widgets, Body Metrics ("Today's calorie target", with the full breakdown) and Dash all use this number. Nothing is guessed: without the §8.4 inputs there is no target, and the UI says what's missing.

### 7.1 Sleep Score and Sleep Quality (0–100)

Both use only what was measured and re-weight around anything missing (never scored as zero). Stages are never estimated from fixed percentages.

| Component | Score | Sleep Score weight | Quality weight |
|---|---|---|---|
| Need met | Sleep Performance, capped at 100 | 0.45 | — |
| Efficiency | 92%+ asleep in bed = 100, 65% or less = 0 | 0.10 | 0.25 |
| Restorative | minutes of deep and REM against healthy amounts, each capped, averaged | 0.15 | 0.30 |
| Interruptions | time awake after falling asleep, and how often you woke | 0.15 | 0.25 |
| Consistency | §4 | 0.15 | 0.20 |

- **Restorative** is scored in minutes, not as a share of the night, so a short night with a normal mix can't score full marks (Oura scores REM and deep in minutes too). REM's target is 90 minutes. Deep's target is 90 minutes up to age 30, then 0.75 minutes less a year, to at least 60, because deep sleep falls with age. It is 80 minutes when age isn't set.
- **Interruptions** come from the main night's stage timeline, between first falling asleep and the final wake-up. Awake time before sleep and lying in afterwards don't count, and back-to-back awake segments are one spell. Up to 20 minutes awake scores 100, falling to 0 at 90 minutes. One awakening of 5+ minutes is free (Garmin counts them from 5 minutes), and each one after it costs 20. The two parts are averaged. Apple's Sleep Score gives interruptions 20 of its 100 points. Without a stage timeline this part is left out, never guessed from the awake total.

**Sleep Score** is the headline night score (how much you got and how well); **Quality** is how well you slept regardless of length. Both are stored on the daily summary.

---

## 9. Autonomic Stress Calculation (0–100)

Health Connect does not expose a native `StressRecord`. Commercial wearables calculate stress on-device using proprietary algorithms and do not export direct stress values into Health Connect. Hangry computes an objective physiological stress index from overnight `HeartRateVariabilityRmssdRecord` and resting heart rate, against the user's own **last 30 days**, read the same way Hangry Recovery reads them (§2.2). A 30-day normal (WHOOP uses 30, Polar 28) doesn't absorb a stressful week the way a 7-day one would. Scoring in standard deviations means someone whose readings swing a lot needs a bigger move to register.

### 9.1 Biomarkers & Heuristics
Each needs 3+ days of history. At normal each scores 42 (mid-`MODERATE`), and each SD in the stressed direction adds 20:
- **HRV suppression ($w = 0.60$)**, on a log scale:
  $$z_{hrv} = \frac{\ln \text{HRV}_{\text{today}} - \overline{\ln \text{HRV}}_{30d}}{\max(\text{SD}_{30d}, 0.08)}, \quad S_{hrv} = \text{clamp}(0, 100, 42 - 20\,z_{hrv})$$
  Suppressed HRV relative to your normal indicates sympathetic autonomic dominance and physical/mental stress.
- **Resting heart rate elevation ($w = 0.40$)**:
  $$z_{rhr} = \frac{\text{RHR}_{\text{today}} - \overline{\text{RHR}}_{30d}}{\max(\text{SD}_{30d}, 1.5)}, \quad S_{rhr} = \text{clamp}(0, 100, 42 + 20\,z_{rhr})$$
  Elevated resting heart rate above your normal indicates cardiovascular strain or systemic fatigue.
- **Cardiovascular Strain Adjustment**: If day strain exceeds 14.0, up to +8 points are added to reflect heavy training fatigue.

About 1.2 SD off normal is `ELEVATED`, and 2 SD is `HIGH`. The card shows HRV against the geometric mean of the 30 days, and resting heart rate against their average.

### 9.2 Stress States
- `LOW` (0–34): Parasympathetic dominance, low systemic stress.
- `MODERATE` (35–64): Normal everyday autonomic activity.
- `ELEVATED` (65–79): Elevated physiological strain or fatigue.
- `HIGH` (80–100): High autonomic stress; restorative recovery recommended.
- `BUILDING_BASELINE`: no score (shown as calibrating) until at least 3 days of HRV or resting heart rate history exist.


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

---

## 12. Overnight vitals, body signals and cycle phase

### 12.1 Overnight vitals
SpO2 and breathing rate are the average of readings taken during the night's main sleep (±30 minutes); without sleep or readings during it, the average of the day's readings. When two apps recorded that night, only the one with the most readings counts (devices measure differently). Readings are read from Health Connect a month at a time. Sleeping heart rate is the average of heart-rate samples during the main sleep (10+ samples). Skin temperature: §2.2.

### 12.2 Body signals (illness early warning)
Each night is compared with your own previous 30 days (each reading needs 5+ days of history):

| Signal | Off normal when |
|---|---|
| Resting heart rate | 3+ bpm above the average |
| HRV | 20%+ below the average on a log scale |
| Breathing rate | 1+ breath/min above the average |
| Skin temperature | 0.5 °C+ above the average (0.8 °C in the luteal phase) |

Each threshold is the larger of the minimum above and $1.5\sigma$ of your own last 30 days (HRV on the log scale), so someone whose readings swing a lot needs a bigger move. Two or more at once is flagged (`BodySignals`): a card on Today, a line at the top of the morning brief and a note to Dash. One on its own is common and isn't shown. Stored per day in `daily_health_summaries.bodySignals`.

### 12.3 Cycle phase (women tracking periods)
Phase comes from the last period start and the average cycle length $L$ (default 28): menstrual while the period lasts; fertile window from day $(L - P) - 5$ to $(L - P) + 1$; follicular before it; luteal after. $P$ is your luteal length - 14 days until it's learned: for each finished cycle where temperature showed the shift, the days from ovulation (the night before the first warm night) to the next period, averaged (8-18 days only). Once this cycle's shift is seen, the next period is predicted as ovulation + $P$. Skin temperature confirms ovulation with the "three over six" rule: from cycle day 8, the first three nights that are all warmer than each of the previous six, by 0.2 °C+ on average, start the luteal phase. Once temperature is tracked in a cycle, the calendar alone never declares the luteal phase - a late ovulation stays follicular until the rise shows. Used to allow for the post-ovulation warmth in §2.2 and §12.2, and shown on Health Records → Cycle. Never a contraception or fertility tool.

