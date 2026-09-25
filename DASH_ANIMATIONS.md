# Dash — New Poses & Animations Needed

> **Status (2026-09-25):** #1–#25 are in the app (`app/src/main/res/drawable-nodpi/`), cut from the three source sheets and wired in as `DashMood` / `DashEmptyScene` entries. Stretch (4 frames), strength (3) and pull-up (2) loop as animations. Still open: #26, the small widget crops.

A brief for the next batch of Dash illustrations. Recent features (fasting, guided programs, longevity pillars, Sleep Score, strain balance) either have no Dash pose that fits, or reuse a mood that means something else.

## Style guide

Match the existing art in `app/src/main/res/drawable-nodpi/`:

- Full-body Dash: cheerful red fox with a blue-and-white striped scarf.
- Transparent background, **WebP**, same framing and line weight as `dash_mood_*`.
- **Size:** 400 × 400 px for moods (`dash_mood_*`), about 520 px on the long side for empty-state scenes (`dash_empty_*`).
- Friendly and encouraging, never dramatic, even for pain or illness.

**"Motion"** below is what the app animates in code on top of the still image, the same way it already adds floating Zs to Sleepy, a heartbeat to Heart and confetti to Celebrate. Illustrators only need to supply the still pose, unless a row says extra frames are needed.

### What exists today

| Kind | Files |
|---|---|
| Moods | `dash_mood_happy`, `dash_mood_cheer`, `dash_mood_celebrate`, `dash_mood_concerned`, `dash_mood_sleepy`, `dash_mood_thinking`, `dash_mood_workout`, `dash_mood_nutrition`, `dash_wave`, `dash_heart` |
| Empty states | `dash_empty_hrv`, `dash_empty_meals`, `dash_empty_memories`, `dash_empty_posture`, `dash_empty_records` |
| Breathing frames | `dash_breathe_in`, `dash_breathe_hold`, `dash_breathe_out`, `dash_breathe_out_end`, `dash_breathe_rest` |
| Spinner / avatar | `dash_spin_front`, `dash_spin_quarter`, `dash_spin_side`, `dash_spin_back`, `dash_avatar` |

---

## Priority 1 — features with no fitting pose

| # | File name | Where it shows | Uses today | Pose | Motion |
|---|---|---|---|---|---|
| 1 | `dash_fasting` | Fasting screen, Today card, Fasting widget, goal notification | Happy | Holding a timer or stopwatch, calm and content | Timer hand ticks slowly |
| 2 | `dash_fasting_done` | "Fasting goal reached" notification and ring | Happy | Proudly holding the timer up, small sparkle | Pops in, one sparkle |
| 3 | `dash_meditate` | Reduce stress program, calm moments, low-stress days | Sleepy / Heart | Sitting cross-legged, eyes closed, soft smile | Slow rise and fall in time with breathing |
| 4 | `dash_focus` | Increase focus program, focus blocks | Thinking | At a desk or holding a notebook, determined look, headband | Subtle blink; small "ding" at the end of a block |
| 5 | `dash_stretch` | Mobility program, Mobility pillar, Longevity card | Workout | Deep lunge with one arm reaching up | Gentle side-to-side sway |
| 6 | `dash_strength` | Pull-up and push-up programs, Strength pillar | Workout (running) | Doing a push-up, or holding a small dumbbell | Up–down rep loop (2 frames: up, down) |
| 7 | `dash_pullup` | First pull-up program, "first pull-up" win | Workout | Hanging from a bar, chin over it | Short pull-up loop (2 frames: hang, top) |
| 8 | `dash_knees` | Knees over toes program | Workout | Deep split squat, front knee forward past the toes | Slow lower and rise |
| 9 | `dash_back_care` | Fix back program | Workout | Bird-dog on hands and knees, one arm and the opposite leg out | Extend, hold, return |
| 10 | `dash_shoulder` | Fix shoulders program | Workout | Arms raised in a Y, or mid shoulder roll | Slow arm raise |
| 11 | `dash_balance` | Balance pillar tick-offs | none | Standing on one leg, arms out, a little wobbly | Wobble that settles |
| 12 | `dash_level_up` | Program "Move up to Level N", Longevity 5/5 | Happy / Celebrate | Stepping up onto a higher step, fist raised | Step-up hop, then sparkles |

## Priority 2 — moods doing too many jobs

`dash_mood_concerned` is used in 12 places for very different situations (pain, sync errors, overdoing strain, stress, illness). Thinking and Sleepy also cover several jobs each.

| # | File name | Where it shows | Uses today | Pose | Motion |
|---|---|---|---|---|---|
| 13 | `dash_ouch` | Program "Some pain", Ease off / Step back advice | Concerned | Gently holding its knee, caring expression (not dramatic) | Small wince, then a reassuring nod |
| 14 | `dash_rest` | Strain over target ("your body needs recovery"), Rebuild days | Concerned | On a sofa with a blanket and a mug | Steam from the mug |
| 15 | `dash_push` | Strain under target ("room to push"), Primed days | Happy / Cheer | Sprint-start crouch | Takes off |
| 16 | `dash_sync_error` | "Sync didn't finish" card | Concerned | Holding an unplugged cable, sheepish | Cable wiggle |
| 17 | `dash_unwell` | Raised breathing-rate warning in Recovery | Concerned | Blanket over shoulders, thermometer, tissues | Small sniffle bob |
| 18 | `dash_sleep_great` | Sleep Score 85+, sleep need met | Happy | Stretching awake in pajamas, sun behind | Big yawn-stretch |
| 19 | `dash_sleep_short` | Short sleep, sleep debt | Sleepy | Pajamas, heavy eyes, holding a coffee | Droopy blink |
| 20 | `dash_bedtime` | Bedtime reminder, "Suggested bedtime" | Sleepy | Pulling up a blanket, moon lamp nearby | Lamp dims |

## Priority 3 — empty states and extras

| # | File name | Where it shows | Uses today | Pose |
|---|---|---|---|---|
| 21 | `dash_empty_workouts` | Training tab and Longevity card before any workouts | none | Holding a water bottle next to a mat, waiting |
| 22 | `dash_empty_sleep` | Sleep screen with no sleep recorded | Text note only | Hugging a pillow |
| 23 | `dash_empty_programs` | Programs screen before any are started | none | Looking at a signpost with arrows |
| 24 | `dash_supplements` | Supplements screen and Today card | Medication icon | Holding a pill bottle |
| 25 | `dash_streak_fire` | Streak milestones (7, 30, 100 days) | Celebrate | Next to a small flame (motion: flame flickers) |
| 26 | Widget crops of #1, #3, #18 | Home-screen widgets (30–52 dp) | Full Happy / Thinking / Breathe images | Head-and-shoulders crop, readable at small size |

Home-screen widgets can't run the in-app animations, so they only need the still crops.

---

## If we can only do a few

Start with **#1, #3, #6, #11, #12, #13 and #14**: fasting, stress, strength, balance, levelling up, pain and recovery. Those are the gaps users will notice most.

## Adding them to the app

For each new pose:

1. Drop the WebP into `app/src/main/res/drawable-nodpi/`.
2. Add a `DashMood` entry in `ui/coach/DashMascot.kt` (or a `DashEmptyScene` entry in `ui/components/DashEmptyState.kt` for empty states), with an accessible description.
3. Add any motion to `DashExpression` alongside the existing per-mood flourishes.
4. Swap it in at the places listed in the "Where it shows" column.
