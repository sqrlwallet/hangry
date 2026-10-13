## 🚀 What's New in Hangry v1.39.0 — A Calorie Target That Follows Your Day

Your calorie target is now worked out fresh every day from what that day actually burns.

### 🍽️ Daily calorie target
- **Built from today**: resting burn (BMR) + NEAT + today's workouts + 10% for digesting food.
- **NEAT** uses today's steps once they pass your usual day; until then, your 7-day average, so mornings don't start near zero.
- **Workouts** add their full calories to today's target as soon as they sync. Rest days get less, active days more.
- **Learns from you**: once you've logged enough meals and weigh-ins, the target is scaled by how far they show the formula is off.
- **Goal weight alone is enough**: lose about 0.5 kg/week or gain about 0.25 kg/week toward it. A target date still sets your own pace, within the same safety caps.
- **See the maths**: Body Metrics → "Today's calorie target" shows the full breakdown. Today, Nutrition, the calorie widgets and Dash all use the same number.

## 📌 Hangry v1.38.1 — Favourites First

### ✨ Improvements
- **Saved meals** pins your most-logged foods at the top: your top 5 foods logged at least twice, under "Most logged", with everything else below by most recent.

## 🚀 What's New in Hangry v1.38.0 — New Widgets, Light & Dark

Three new home screen widgets, and every widget now matches your phone's light or dark mode.

### 🧩 New widgets
- **Water**: today's water against your target, counting drinks from other apps too. Log a glass or a bottle without opening the app.
- **Strain**: today's strain so far on the 0–21 scale, with the range your recovery suggests shaded in, and today's workouts.
- **Bedtime**: tonight's suggested bedtime, when to start winding down, any sleep debt, and how last night went.

### ✨ Better widgets
- **Light and dark**: all widgets follow your phone's dark mode, with readable colours on the light Cream background.
- **Daily Activity** shows steps against your step goal with a progress bar, plus active minutes.
- **Weight Trend** and **Goals** use your units (kg, lb or stones).
- The widget gallery is grouped into sections, and its previews match light or dark mode.
- **Hide values on widgets** also covers Water and Strain.

### 🛠️ Fixes
- Logging water from a widget or in the app now updates your widgets straight away.

## 🚀 What's New in Hangry v1.37.0 — Appearance, Fluid Navigation & Architecture Modernization

Choose your theme, jump and swipe smoothly through dates, and enjoy snappy app startup with an overhauled architecture.

### 🎨 Appearance & Themes
- **Theme Selection**: Choose between **System Default**, **Dark (Obsidian)**, and **Light (Cream)** in Settings → Appearance. Theme changes apply instantly across the entire app without restarts.

### 📅 Fluid Date Navigation & Gestures
- **Date Picker Dialog**: Tap the date header anywhere to open an interactive calendar picker and jump directly to any date.
- **Quick "Today" Action**: Tap "Today" on the date navigation bar when viewing historical dates to return home instantly.
- **Horizontal Swipe Gestures**: Swipe left or right smoothly across Today, Nutrition, and Training tabs to flip between days.
- **Tab Reselection**: Tapping the active bottom navigation tab smoothly scrolls back to the top and returns the view to today.

### 🏗️ Architecture & Performance Overhaul
- **Domain Abstractions**: Introduced `WeightRepository` and `HeightRepository` interfaces, cleanly decoupling domain logic and view models from Room DAOs.
- **MVVM Decoupling**: Extracted `TrendsViewModel` and `SettingsViewModel`, shifting heavy multi-flow computations, database maintenance, and export logic off the Compose UI thread.
- **Fast Startup**: Eliminated cold-start `runBlocking` calls on the main thread during onboarding checks in `MainActivity`.
- **Background Decoupling**: Moved midnight resets and live strain polling out of the navigation graph into a dedicated `AppLifecycleSyncTracker`.
- **Warning-Free Compilation**: Modernized deprecated icon and lifecycle APIs and enabled Kotlin 2.x parameter-property annotation targets.

## 🩹 Hangry v1.36.1 — Save Your Measurements

### 🛠️ Fixes
- **Save measurements** without an estimate: Body Metrics → Measurements now has its own save button for height, weight, age, neck, chest, waist and hips. A weight is only logged as a new weigh-in when it changed.
- Saving a body fat estimate is one clear button for the estimate you pick, instead of small icons on each row.

## 🚀 What's New in Hangry v1.36.0 — Lab Reports, Water & Your Units

Scan your lab reports, track water, see weight in your own units, and a big round of fixes that keep your data safe.

### 🧪 Health Records
- **Scan a report**: photograph up to 4 pages and AI reads the results. You review every value before anything is saved; tests Hangry doesn't track are listed too.
- Tap any reading or period to **edit** it. Deleting now offers **Undo**.
- Removing an allergy or condition asks first.

### 💧 Water & ⚖️ weight
- **Water tracking** on the Nutrition card: a glass, a bottle or any amount, against your daily target, saved to Health Connect. A +250 ml button on the Nutrition widget.
- **Your units**: kg, lb or stones, cm or inches, chosen in onboarding or Settings → Units.
- **Log weight** in one step from Today, Body Metrics or Trends.
- Trends shows a **smoothed weight trend** and your weekly rate of change.

### 📱 More room on every tab
- Nutrition, Workouts and Dash drop their top bars. Nutrition's options sit in the ⋮ next to the date; Dash's history and new chat float in a small pill.
- **Body Metrics** redesigned: one card per group with aligned values, and missing metrics folded into one line.
- The **Daily briefing** can be hidden from Customize.
- Workouts has its own date bar.

### 🛡️ Safer data
- A Health Connect hiccup or a declined permission can no longer wipe or block your synced history.
- App updates can't silently erase your data, and backups take a consistent, checked snapshot.
- Undo after deleting food, posture scans, body fat scans and lab readings, and after an AI meal log.
- Forms keep what you typed when the screen rotates, and ask before discarding.

### 🛠️ Fixes
- The AI estimate in the manual meal sheet now checks your allergies.
- Saving goals no longer logs your old weight as a new weigh-in.
- Body Age uses your newest VO₂ max and body fat.
- Sleep is counted on the day it ends, so early-evening sleep isn't missed.
- AI replies with stray text or decimal calories no longer fail.
- Health Connect's permission prompt now appears after the explanation, not before.

## 🚀 What's New in Hangry v1.35.0 — Cleaner, More Compact Screens

Every screen shows more of what matters: less repetition, tighter cards and explanations tucked behind ⓘ.

### 🏠 Today
- One **Vitals** card replaces Heart Rate & HRV, Key Vitals, Body check and Overnight vitals: every reading against your usual, with a dot that turns red when it's off.
- **Streaks** fit in one row, filled in once each habit is done today.
- Smaller Today overview, Daily briefing, Body Age and Body composition cards. The strain target shows once.

### 🍽️ Nutrition
- The daily target card is half the height: calories up top, protein, carbs, fat and fiber side by side. Fiber tips open from the fiber column.
- Saved meals and Weekly review each have one way in instead of three.
- Slim Photo / Gallery / Describe buttons, and the manual log sheet shows saved meals once.

### 💬 Dash
- Slimmer top bar, compact suggestions, cleaner bubbles and a single message pill. You can type while Dash replies.
- Long-press any message to copy it.

### 📱 Everywhere
- Tighter cards, spacing, top bars and tab bar across the app.
- Sleep, Heart, Trends and Recovery stop repeating the same numbers; daily readings and history are compact rows.
- Body fat results are one Estimates card; Body Age, Posture, Fasting, Programs, Health records and Settings are shorter, with long text behind ⓘ.

### 🛠️ Fixes
- The HRV band and your reading on the Heart screen now line up on every screen size.

## 🚀 What's New in Hangry v1.34.0 — Dash Chats & Body Check

Start fresh conversations with Dash and keep every past one, plus new Today cards for what your wearable sees overnight.

### 💬 Chats with Dash
- **New chat** any time from Dash's top bar. Each chat keeps its own conversation, while Dash still knows your data and your journal.
- **Chat history**: reopen past chats, grouped by Today, Yesterday, Previous 7 days and Earlier, with a preview of the last message.
- **Search** your chats by title or anything said in them, and **rename** or **delete** them. Deleting a chat keeps the memories Dash saved to your journal.
- Dash reopens your most recent chat. Your existing messages are kept as one chat.

### 🩺 New Today cards
- **Body check**: last night's resting heart rate, HRV, breathing rate and skin temperature against your usual, every day.
- **Overnight vitals**: everything your wearable measured while you slept.
- **Cycle**: phase, cycle day and your next period, for women who log periods.

### 📈 Trends & workouts
- New Trends for **sleeping heart rate, skin temperature, breathing rate and SpO₂**, and a **Body signals** filter for the nights several readings were off.
- Routes now show **per-km splits** (fastest highlighted) and an **elevation profile** with total climb.

### 🧠 Smarter readings
- The illness warning now judges each reading against how much it normally varies for you, so fewer false alarms if your numbers naturally swing.
- When two devices recorded the same night, SpO₂ and breathing rate come from one of them instead of a mix.
- Cycle predictions learn your own post-ovulation length from your temperature.

### ⚙️ Settings
- **Settings → Units**: show temperatures in °F.
- **Data Sources** shows the real names of every app connected to Health Connect.
- The Health data row only lists data Hangry actually reads.

### 🛠️ Fixes
- Recalculating a long history no longer loads every overnight reading into memory at once.

---

## 🚀 What's New in Hangry v1.33.0 — More From Your Wearable

Hangry now uses everything a Fitbit Air (and other trackers) shares through Health Connect.

### 🌡️ Skin temperature
- **New reading**: your overnight skin temperature change shows on the Vitals card, the day details and the new Overnight vitals card on Sleep.
- **Recovery**: a night 0.5 °C or more warmer than your usual lowers your score, like a raised breathing rate already does.
- Settings → Background sync → **Health data** lets you allow any data type Hangry can't read yet, and fills in your whole history once you do.

### 🤒 Early illness warning
- When two or more overnight readings move off your normal together (resting heart rate, HRV, breathing rate, skin temperature), Today shows a heads-up and your morning brief leads with it.
- Dash knows too, so it can suggest taking it easy.

### 😴 Better overnight vitals
- Blood oxygen and breathing rate now come from readings taken while you slept, not the whole day.
- New **average sleeping heart rate**, shown with lowest HR, HRV, breathing, SpO₂ and skin temperature on Sleep.

### 🌸 Cycle phase
- Health Records → Cycle shows your current phase. Where your wearable records skin temperature, the rise after ovulation confirms the luteal phase.
- That warmer phase is allowed for, so it isn't mistaken for illness.

### 🗺️ Workout routes
- Tap **Show route** on a run, walk or ride to see its GPS outline. It's read only when you ask and never saved.

### 🔌 Data sources
- Settings → Data Sources now shows which app wrote each kind of data lately, so you can check your wearable is sharing.

### 🛠️ Fixes
- Workout power and elevation are now actually read (the permission was never asked for).
- Days where a tracker only records total calories now get active calories too.
- Hangry no longer asks for body temperature, speed or floors climbed, which it never used.

---

## 🚀 What's New in Hangry v1.32.0 — Smarter Calorie Targets

Your daily calorie target now learns from what actually happens.

### 🎯 Adaptive calorie target
- **Learns your real maintenance**: once you've fully logged meals on 14+ of the last 28 days and weighed in on at least 4 days spread over 2+ weeks, Hangry compares what you ate with what your weight did and blends that into your maintenance calories. The more you log, the more it relies on your own data.
- Partly-logged days are skipped, and numbers that don't add up (usually missed meals or a scale glitch) are ignored rather than steering your target.
- **Safer goals**: a weight-loss target never drops below your resting burn or 1,500 kcal (men) / 1,200 kcal (women). Losing is capped at about 1% of your weight a week, and gaining at about 0.5% a week so it's mostly muscle.
- Body Metrics and Dash both explain the blend and what's still needed.

### 🥗 Clearer weekly review
- Daily averages are now easy-to-scan rows with your target and an On target / Low / High badge.
- Dash's review is broken into numbered recommendations you tap to open, with What's working, Foods to add and your fiber plan folded away until you want them.
- "By the numbers" no longer repeats what Dash already said.

### 🛠️ Fixes
- Fixed Ask Dash crashing the app when a meal name contained a % sign (like "2% milk").
- If Dash can't load your data for any reason, it now still answers instead of closing the app.

---

## 🚀 What's New in Hangry v1.31.0 — Backup & Restore

Your data, kept safe on your terms.

### 💾 Backup & restore
- **Back up everything**: Settings → Backup & restore saves all your data, photos and settings into one file, wherever you choose (your Drive, a computer, a USB stick).
- **Restore on any phone**: pick a backup file and Hangry checks it, restores everything and restarts. Moving to a new phone no longer means starting from scratch.
- Your OpenRouter API key is never included; add it again after restoring.

### 🔒 No Google cloud backup
- Hangry's health data is no longer included in Android's automatic Google Drive backup. A direct phone-to-phone transfer during new-phone setup still brings it across.

### 🛠️ Fixes & polish
- Fixed a crash in Fasting on Android 9–13.
- All on-screen text now lives in string resources, the groundwork for translations.

---

## 🚀 What's New in Hangry v1.30.0 — Weekly Nutrition Review & Fiber

See how your week of eating actually went, and what to add next.

### 🥗 Weekly review
- **New in Nutrition**: tap **Weekly review** to look back over your last 7 days of meals.
- Daily averages for calories, protein, fiber, carbs, fat and sodium, each against your target. Days you didn't log don't drag the averages down.
- Straightforward recommendations: fiber gaps, low protein, eating over or under target, salt, sugar, late-night eating and variety.
- **With AI on**, Dash reviews the meals you actually logged: what's working, 3–5 changes to try this week (with why and how), foods to add and a personal fiber plan. Your allergies are never suggested. Reviews are remembered for the week, so reopening doesn't use more API credit.

### 🌾 Fiber, front and centre
- A new **Fiber** bar on the daily nutrition card, with a target of about 14g per 1,000 kcal (25–38g a day) and a nudge on how to close the gap.
- Tap the info icon for practical ways to eat more fiber: build up slowly, drink water, spread it across meals, swap in whole grains, beans, berries, chia and more.
- Fiber now shows on every logged meal and can be edited, including on manual entries.

### 🤖 Smarter photo analysis
- Food, posture, body-fat and supplement photos now use **Gemini 3.8 Flash** by default for better estimates. If you were on the old default, you're moved over automatically; a model you picked yourself is kept.

### 🏋️ Cleaner Training tab
- Today's workouts now sit right at the top, under the summary, with heart-rate zones below.
- The longevity pillars card has been removed.

---

## 🚀 What's New in Hangry v1.29.1 — Health Connect Meal Edits Fix

### 🔧 Fixes
- Editing a meal now reliably updates its Health Connect record, even when Hangry can't read nutrition back from Health Connect.
- Protein, carbs and fat you don't touch while editing keep their decimals instead of being rounded.
- Meals logged by another app now explain that edits stay in Hangry, since only the app that wrote a record can change it in Health Connect.

---

## 🚀 What's New in Hangry v1.29.0 — Sleep Stage Charts

See how your night actually went, stage by stage.

### 🌙 Your night, minute by minute
- **Sleep stage timeline**: A new chart in Sleep Architecture shows when you were awake, in REM, light and deep sleep through the night. Brief wake-ups show as ticks so they don't get lost.
- Times follow your phone's 12- or 24-hour setting.

### 📊 Stages against typical ranges
- Each stage gets its own bar with its share of the night and time spent, and the typical adult range hatched over it. You can see at a glance whether deep or REM sleep ran short.

### 📅 Sleep over the last 7 nights
- A new card stacks each night's stages into a column, with a line for your average time asleep. Nights without stage data show as a single column.

### ⌚ Works with any tracker in Health Connect
- Stages come from whatever your watch, ring or band writes to Health Connect, including the Google Fitbit Air via the Google Health app.
- Nights synced before this update get their timeline the next time a sync reads them. The 7-night chart works for them right away.

---

## 🚀 What's New in Hangry v1.28.0 — Portion-Accurate Photo Nutrition & Health Connect Edits

Photo logging now weighs what's actually on your plate, and meal edits reach Health Connect.

### 📸 Photo nutrition that follows your portion
- **Every item weighed separately**: Each food, side, sauce and topping on the plate gets its own estimate, and cooking oil and butter count too.
- **Scaled from what's in the photo**: Portions are sized against your plate, bowl, cutlery or hand, not a "standard serving". A small scoop of rice counts as a small scoop.
- **Your words win**: Type "200g chicken" or "I ate half" and that overrides the photo. A visible packaging label is used as-is.
- Photos are sent at a higher resolution so edges and depth are easier to judge.

### 🔄 Edits sync to Health Connect
- **Edit a meal, and Health Connect updates too**: Name, calories and macros are kept in step.
- **Deleting a meal also removes it from Health Connect**: Before, deleted meals kept counting there.
- Meals imported from other apps can only be changed by the app that created them, so edits to those stay in Hangry.

### 🍽️ A tidier Nutrition tab
- The built-in common foods are gone from Quick add, Saved Meals and the log sheet. Only your own meals show. Quick add appears once you've logged something. The Nutrition widget still offers the basics.

### 🧘 Longevity pillars
- Mobility and balance are now **60 minutes a week** each, instead of daily ticks. Yoga, pilates and stretching count toward both, and each tick-off adds 10 minutes.

---

## 🚀 What's New in Hangry v1.27.0 — Longevity Pillars, Guided Programs & New Dash

A weekly chart of the five kinds of exercise most linked with healthy ageing, eight simple step-by-step programs, and lots of new Dash.

### 🧘 Longevity pillars (Training tab)
- **One chart for your week**: A five-sided radar showing how close you are to each weekly target: strength (3 sessions), Zone 2 cardio (150 min), Zone 4–5 cardio (75 min), mobility (daily) and balance (daily).
- **Filled in for you**: Strength and mobility come from your logged workouts, cardio minutes from your own heart-rate zones. Tap a day to tick off balance or a quick stretch.

### 🧭 Programs (More → Programs)
- **Eight programs, all optional**: Reduce stress, Increase focus, Increase mobility, Fix your shoulders, Fix back issues, Your first pull-up, Your first push-up, and Knees over toes.
- **Simple and gentle**: 3–4 levels of easy-to-follow exercises, each with a how-to and an easier version.
- **You set the pace**: Tick off each session and say how it felt. When sessions feel easy you're offered the next level, and it's always your choice. If something hurts, Hangry suggests easing off or stepping back.
- **Safety first**: Each program asks you to check with a professional before starting, and back and shoulder programs list warning signs to watch for.
- Breathing steps open Dash's guided breathing, and sessions count toward your longevity pillars.

### 🦊 New Dash
- 22 new poses and animations: meditating, stretching, push-ups, pull-ups, balancing, levelling up, resting on the sofa, feeling unwell, well rested, bedtime and more.
- Dash now matches the moment: ready to sprint on primed days, resting on rebuild days, holding a stopwatch while you fast, next to a flame for streak milestones, and holding a cable when a sync fails.

---

## 🚀 What's New in Hangry v1.26.0 — Fairer Sleep Need & Smarter Recovery

Sleep need no longer asks you to catch up on all your sleep debt at once, and Recovery now looks at whether yesterday's strain matched its target.

### 😴 Sleep need
- **Debt repaid over two weeks**: Sleep debt from the last 14 nights is spread across the next two weeks instead of piling onto one night. A long night pays some of it back.
- **Realistic targets**: At most an hour extra for debt and 30 minutes after a hard day, so with an 8h goal your need tops out at 9½h.
- Your Sleep Score's "hours vs need" uses this, so past debt no longer drags every night's score down.

### 💚 Recovery
- **Resting heart rate vs your monthly average**: At or below your 30-day average is full marks. Each bpm above takes off 2 points.
- **New: Strain Balance**: Yesterday's strain is compared with the target it was given. In range or lighter is good (lighter means you have room to push today). Going over lowers recovery, because your body needs to recover.
- Recovery is now HRV (35%), resting heart rate (25%), sleep vs need (25%) and strain balance (15%). Sleep consistency now counts only in your Sleep Score.

### 🔥 Strain on Today
- **Yesterday's final strain leads**, with today's live "so far" and today's target underneath.

Your history is recalculated once when you update, so past numbers will shift.

---

## 🚀 What's New in Hangry v1.25.0 — More Accurate Scores, Sleep Score & Live Strain

Recovery, sleep and strain have been rebuilt to be more accurate and more personal. Your history is recalculated once when you update, so past numbers will shift.

### 😴 Sleep Score out of 100
- **One number for the night**: Sleep now leads with a score out of 100, shown on the Sleep screen, the Today card and the Sleep widget.
- **See how it's built**: Hours vs your need (50%), time asleep in bed (15%), deep & REM sleep (20%) and regular bed & wake times (15%).
- **Time asleep, not time in bed**: Awake time and untracked gaps no longer count as sleep. Nights already stored are corrected.
- **Honest sleep need**: Starts from your sleep goal and adds recent sleep debt (fading over a week) and extra after a hard day, so chronically short sleep no longer looks "normal".
- **Better consistency and bedtime**: Consistency now measures regular bed and wake times, naps no longer count as nights, and the suggested bedtime works for late sleepers.

### 💚 Recovery
- **Overnight readings**: Resting heart rate is your lowest 5-minute average while asleep, and HRV is your overnight average.
- **Compared with your own 30 days**: Scored by how unusual today is for you, allowing for how much your numbers normally vary.
- **Sleep scored against need**: Meeting your sleep need now scores well, even if you slept less than your recent average.
- **Illness early warning**: A breathing rate well above your usual lowers the score.
- "Strain & Consistency" is now correctly named **Sleep Consistency**.

### 🔥 Strain
- **Personal heart-rate zones**: Based on your max heart rate (new optional field in Settings, or estimated from age) and your resting heart rate.
- **No more strain from just wearing your watch**: Resting and sleeping heart rate earn nothing.
- **Live through the day**: "Strain so far" updates every few minutes while the app is open and hourly in the background. It's marked final once the day is over.
- **Phone-tracked workouts count** even without heart rate, and days with no data no longer drag your average down.

---

## 🚀 What's New in Hangry v1.24.0 — Intermittent Fasting & Simpler Supplements

An optional fasting timer with streaks and a home-screen widget, and supplements that no longer nag you unless you ask them to.

### ⏱️ Intermittent Fasting (optional)
- **Off until you want it**: Turn it on from **More → Fasting**, and turn it off from the same screen whenever you like. Your history is kept.
- **Popular plans**: 13:11, 14:10, 16:8, 18:6, 20:4, one meal a day, or a custom length from 12 to 72 hours.
- **Timer**: A ring counts up your fast and shows time to go and a rough stage (fat burning, ketosis...). Forgot to press Start? Use "I started earlier" or edit the start time.
- **Streak**: A new "Fasting goal" streak. A day counts when a fast reaches its goal.
- **Goal reminder**: A notification when you hit your goal, with an End fast button.
- **Today card**: Start or check your fast from Today (only shown while fasting is on).
- **Progress & history**: Streak, goals hit this week, average and longest fast, and your recent fasts.
- **Safety first**: A clear note on who shouldn't fast, and a stronger warning if your Health Records say you're pregnant.

### 📱 Fasting Widget (2 × 2)
- A live timer, your stage, a progress bar and your streak, with a button to start or end a fast without opening the app.

### 💊 Supplements: context unless you ask for tracking
- Adding a supplement now just tells Dash what you take, and it's assumed taken. No tick-offs, reminders or streak.
- Want help remembering? Turn on **Help me track this** for check-offs and reminders, or ask Dash to track it.
- Supplements you already had reminders on for stay tracked.

### 🦊 Dash
- Dash knows your fasting plan, current fast and streak (only when fasting is on) and can open the Fasting screen.
- Dash won't ask whether you took supplements you haven't asked to track.

---

## 🚀 What's New in Hangry v1.23.0 — Calories & Nutrition Widgets

See what you've eaten today without opening the app, and log your usual foods right from the home screen.

### 🔥 Calories Widget (2 × 2)
- **Today at a glance**: Calories eaten as a ring against your daily target, with a percentage badge and how much is left, e.g. "760 left of 2,000".
- **Honest numbers**: If you go over, the ring turns red and shows by how much. With too little data for a target yet, it shows only what you've eaten, never an assumed 2,000.

### 🍽️ Nutrition Widget (4 × 2)
- **Calories and macros**: The same ring, plus protein, carbs and fat against your daily targets.
- **One-tap logging from the home screen**: Three buttons for your most recent saved meals (or basics like a banana until you have some). One tap logs a portion for today without opening the app, syncs it to Health Connect, and confirms with a toast.
- **Snap a meal**: A camera button for photo logging.

### ⚙️ Also
- Both widgets are in **More → Home Screen Widgets** with previews and an Add button.
- They update as soon as you log, edit or remove food in the app.
- **Hide values on widgets** now covers them too: numbers are hidden and quick-add shows food names only.

---

## 🚀 What's New in Hangry v1.22.0 — Saved Meals, Common Foods & Portions

Logging food you eat all the time is now a single tap. No retyping, no waiting on the AI.

### 🍽️ Saved Meals
- **Saves itself**: Every food you log (photo, typed in, or through Dash) is saved automatically, once per food. Log it again and it picks up your latest numbers.
- **Your history comes along**: Foods you've already logged show up in Saved Meals as soon as you update.
- **Quick add**: A new row on the Nutrition tab puts your most recent foods one tap away. The new **Saved** button opens the full list.
- **Saved Meals screen**: Search, edit or remove your meals (with undo), and log several foods back to back. Meal Plan is now part of Saved Meals.
- **Type-ahead**: When you enter a meal by hand, matching foods appear as you type. Tap one to fill in its calories and macros.

### 🍌 Common Foods, Ready From Day One
- 56 everyday foods (banana, apple, eggs, oats, rice, roti, dal, paneer, chicken, yogurt, coffee and more) with typical values per serving, sorted by category.

### ⚖️ Portions
- Choose **½× / 1× / 1½× / 2×** on Saved Meals, or long-press any Quick add food. Every nutrient is scaled and the entry is logged as e.g. "Banana (×2)".

---

## 🚀 What's New in Hangry v1.21.0

### 🤖 Dash AI Coach Superpowers
- **Workout Logging**: Dash can now log workouts for you directly in conversation with exercise type, duration, calories, distance, and notes.
- **Journal Memory Resolution**: Healed from an injury or resolved a past health complaint? Ask Dash to resolve or remove it, and he will update your personal health memories.
- **Reminders Toggle**: Manage your morning readiness brief and bedtime reminder nudges right from chat with Dash.
- **Deeper Health Context**: Dash is now grounded in your 30-day Body Age trajectory and factors, habit streaks (steps, meals, sleep, supplements), 7-day sleep architecture (deep/REM/light percentages and sleep quality), and recovery strain target recommendations.

### 🏃 Dash Everywhere: New Animations
- Added dedicated animated workout runner and nutrition apple mascot graphics to training and nutrition empty states and Dash mascot views.

---

## 🚀 What's New in Hangry v1.20.0

### 🧮 No More Double Counting
- Steps, distance, active calories and water are counted once even when your phone and watch both record them. Hangry now uses Health Connect's own totals, following the app order in Health Connect settings.
- The same workout saved by several apps (watch, Strava, Samsung Health, Fit) counts once, keeping the most detailed copy. Its calories, steps and distance come from one app.
- The same night's sleep from two apps counts once; the copy with sleep stages wins. Naps are still kept.
- Weigh-ins and meals mirrored between apps show once.
- Records you delete or edit in another app are now removed or updated in Hangry. Things you entered in Hangry are never touched.
- Your existing history is re-read once after updating, so past days are corrected too.

### 🔄 Background Sync
- Hangry can sync every hour while closed, so your recovery, widgets and morning brief are ready before you open it.
- A new onboarding step (and **Settings → Background updates**) asks for background access, no battery limits and notifications, each explained and optional. Existing users are asked once on Today.
- The morning brief goes out as soon as last night's sleep syncs.

---

## 🚀 What's New in Hangry v1.19.0

### 👋 Get Set Up Right
- Onboarding now asks for your **date of birth, sex, height and weight**, in cm·kg or ft·lb, so calorie burn, body metrics and Body Age are about you from day one.
- A new optional step lets you add a **goal weight and date**, **sleep goal**, **step goal**, **allergies**, **health conditions** and **tape measurements**. Skip any or all; everything can be changed later in Settings.

### 🎂 Exact Age
- Add your birthday (onboarding, Settings or the Body Age screen) and your age stays current by itself. Body Age compares against your exact age, e.g. 34.7.

---

## 🚀 What's New in Hangry v1.18.0

### 🧬 Body Age
- See how old your body "acts" compared with your real age, from 30 days of sleep, activity, fitness (VO₂ max), heart and body composition.
- Every factor shows how many years it adds or removes, with a tip; the month-over-month change is shown too. An estimate for motivation, not a medical measurement.

### 🔥 Streaks
- Step goal, meals logged, sleep goal and all supplements taken - days in a row, with your best run. Dash celebrates 7, 14, 30, 60, 100, 180 and 365 days.

### 🔔 Reminders
- **Morning readiness**: once last night's sleep syncs, your recovery and today's strain target, with Dash.
- **Bedtime reminder**: a nudge 30 minutes before your suggested bedtime.
- Both can be switched off in Settings → Reminders.

---

## 🚀 What's New in Hangry v1.17.0

### ✅ Only Real Numbers
- Days with no sleep, heart rate or HRV get no recovery score (no more "68% Steady" from nothing); widgets show today only.
- Sleep stages appear only when your device recorded them; quality, averages and consistency stay blank until there's data.
- No invented 2000 kcal target, generic strain target, "Just now" sync time or permanent "Pending sync".
- Past days show their own sleep, not an older night's.

### 🐛 Fixes
- The date rolls over at midnight, so meals are logged to the right day; returning to the app catches up with a quick sync.
- Widget and notification links open on top of Today, so Back always works.
- Nothing hides behind the tab bar or floating buttons; links to a tab switch tabs instead of stacking a copy.
- Ask Dash's message box and the Body Fat fields stay above the keyboard.
- Set your own sleep goal in Settings.

### ⚡ Faster & Smaller
- App size down from ~55 MB to ~6.6 MB.
- Opening the app no longer recalculates your whole history; startup doesn't wait for the database.
- Dash's animations, the breathing timer and widgets use less battery.

### ✨ Polish
- Pull-to-refresh with Dash on Workouts; past-day notes on Sleep, Heart and Recovery.
- Clearer empty states, consistent "Today" wording, no back arrows on tabs, dark-mode-aware stage and zone colors.
- Accessibility: labelled Dash for TalkBack, bigger touch targets, larger widget text.

---

## 🚀 What's New in Hangry v1.16.0

### 🦊 Dash Everywhere
- **Spinning Dash** is the loader for meal photos, supplement labels, body fat analysis, health record imports and the first history import; a thinking Dash sits on the Log Meal button while a photo is read.
- **Goal celebrations**: confetti Dash when you hit your active calories, steps or active time goal (once a day each) or reach a health marker goal.
- **Reactions**: thumbs up when all supplements are taken (also on the widget), a cheer for the day's workouts, a sleepy Dash on rest days and at the top of Sleep, and Dash with the beating heart when your HRV is at or above normal.
- **Worried Dash** for allergen alerts, failed syncs (now a card that stays until you retry), Ask Dash errors and a missing Health Connect.
- **Recovery widget** shows the matching Dash, and reminders show Dash's face.

---

## 🚀 What's New in Hangry v1.15.1

### ✨ More Room on Today
- **Pull down to sync**: the refresh, settings and customize icons are gone from the top of Today, and Dash spins while it syncs.
- **Floating tab bar**: content now scrolls behind the tab bar instead of stopping above a blank strip.
- **More tab** now also holds Customize Today and Settings & Privacy.

---

## 🚀 What's New in Hangry v1.15.0

### 🏋️ Workouts, Properly Imported
- **Every Health Connect workout type**: weightlifting, elliptical, rowing, tennis, hiking and ~55 more now show by name instead of "Other".
- **Workout details**: distance, pace (or speed for rides, per 100 m for swims), average and max heart rate, elevation, power, strength sets and reps with the exercises done, laps and notes.
- **History fixed too**: workouts saved before this version are re-read from Health Connect once, then training load and strain are recalculated.
- **Training load** now rates each workout by its family of exercise and uses its full calories.

### 🔥 Active Calories & Active Time
- **Workouts count in full**: every calorie burned during a workout, not just the extra above resting, and every minute of it.
- **Every step counts**: 150 steps adds one active minute, and each step's full cost is included.
- **Daily Activity** shows the new active calories and a new Active Time row, with an editable Active Time goal.
- **Maintenance calories** use the same rules, and the day's total burn no longer counts resting calories twice.

### 🧩 Seven New Widgets
- Breathe (one tap starts a session), Heart, Health Markers, Goals, Weight Trend, Posture Check and Cycle.
- **Hide values on widgets** switch for privacy; widgets refresh whenever you leave the app.

### ✨ Polish
- Settings & Privacy is shorter: sections are collapsed until you need them.
- Peach app icon background.
- The Sleep widget no longer shows a made-up score; it shows when you slept and, with enough history, how steady your schedule is.
- Ask Dash's welcome screen uses the new waving Dash.

---

## 🚀 What's New in Hangry v1.14.0
- Illustrated pose guides for posture and body fat photos; the AI knows each view.
- Dash breathes along in the breathing exercise, with six expressions across the app, illustrated empty states and onboarding art.
- Floating Zs, heartbeat glow, confetti and tap-to-hop animations.

---

## 🚀 What's New in Hangry v1.13.0
- Supplements: snap a bottle to add it, overlap and dose checks, reminders and a widget.
- Ask Dash accepts photos, can fill things in for you after you confirm, and streams replies.
- Allergen alerts on logged meals; blood pressure and blood sugar shared back to Health Connect.

---

## 🚀 What's New in Hangry v1.12.0
- Health records and goals (blood pressure, labs, allergies, conditions, cycle, pregnancy).
- Body metrics (BMI, FFMI, waist-to-height and more) and an energy balance estimate.
- Ask Dash and camera-first meal logging.

---

## 🚀 What's New in Hangry v1.11.0
- Nunito typography, decluttered copy with info tips, the Dash app icon, and recovery that still works without HRV.

---

## 🚀 What's New in Hangry v1.10.0
- Meet Dash the fox, the AI coach's mascot, plus guided breathing exercises.

---

## 🚀 What's New in Hangry v1.9.0
- Warm cream rebrand with contrast-checked light and dark themes.

---

## 🚀 What's New in Hangry v1.8.0
- Body fat dashboard widget, body fat trend chart, coach model selector and a multi-photo picker.

---

## 🚀 What's New in Hangry v1.7.0 – v1.7.1
- AI body fat calculator, interactive trend history and faster local storage.
- Fixed a startup crash (v1.7.1).

---

## 🚀 What's New in Hangry v1.6.0
- Removed the double status-bar gap on Today and locked the day's resting heart rate once shown.

---

## 🚀 What's New in Hangry v1.5.0
- Floating bottom dock and more usable screen space across the app.

---

## 🚀 What's New in Hangry v1.4.0
- Modernized UI with bottom navigation, macro tracking and AI Coach polish.

---

## 🚀 What's New in Hangry v1.3.0

### 🎯 Streamlined Daily Activity & Metrics
- **Simplified Daily Activity Rings**: Refactored the concentric activity rings to focus exclusively on the two metrics that matter: **Calories Burned** and **Steps**. Removed minutes from the rings, legends, expanded metrics, and goal setting dialogs.
- **Removed Training Load**: Deprecated and completely removed the cardiovascular Training Load metric from the dashboard, widget selections, and the training screen hero, refocusing training on today's workouts and cardio intensity zones.
- **Removed Blood Pressure & Respiration**: Cleaned up Key Vitals and customizable dashboard widgets to remove blood pressure and respiration rate.
- **Permissions Cleanup**: Removed unused `READ_BLOOD_PRESSURE` and `READ_RESPIRATORY_RATE` Health Connect permission requests from the app manifest and permission flows.
- **Home Screen Widgets & Mockups Updated**: Updated Daily Activity and Daily Overview widgets to display only steps and active calories burned without minutes columns.

---

## 🚀 What's New in Hangry v1.2.0

### 🤖 AI Coach with 7-Day Context & Personal Problem Journal
- **Personalized Coaching**: Interactive AI Coach powered by `openai/gpt-5.6-luna` that answers health, training, nutrition, and recovery questions with deep awareness of your past 7 days of data (sleep, strain, workouts, nutrition & macros, posture, and weight).
- **Automatic Problem & Memory Journaling**: When you share personal problems, injuries, symptoms, food sensitivities, or habits in chat, the AI Coach automatically extracts and saves them to a persistent personal journal. These memories are remembered and incorporated into future coaching sessions across days.
- **Floating AI Coach Button**: Floating access button on the main dashboard directly above the Log Meal button (active when AI is enabled).
- **Personal Journal & Memories Sheet**: Inspect, manage, manually add, or delete coaching memories at any time via a dedicated bottom sheet.
- **Robust Multi-Turn History**: Chat conversations and saved memories are persisted locally in Room (v9 migration) with full offline security.

### 🧘 Relaxed Posture Guardrails & Improved AI Prompts
- **Relaxed Posture Guardrails**: Removed strict shirtless/shorts restrictions. Users can now perform posture checks wearing normal athletic or casual clothing (t-shirts, shorts, leggings, etc.).
- **Flexible Photo Count**: Reduced minimum photos from 3 to 1 (supporting 1–5 photos from any angle: side, front, or back).
- **Expert Biomechanics Assessment**: Redesigned posture prompt for calibrated scoring, constructive observations (cranio-cervical, scapular, pelvic, spinal curves), and targeted corrective exercises with clear form cues.
- **Smarter Food & Nutrition Estimation**: Enhanced food prompt with visual portion recognition, hidden cooking fats/oil accounting, calorie consistency, and informative portion breakdown notes.

---

## 🚀 What's New in Hangry v1.1.0

### 🧩 Home Screen Widgets
- 5 pinnable Android home screen widgets: Daily Activity, Quick Log Meal, Sleep Insights, Recovery Score, and Daily Overview.
- One-tap "Pin to Home" gallery in Settings, plus the standard long-press launcher picker.
- Widgets refresh automatically on app launch and after every periodic background sync - real data only, honest `—` placeholders when nothing has synced yet.
- The Quick Log Meal widget deep-links straight into the meal-logging sheet, even from a cold start.

### ✨ Streamlined Onboarding
- Merged the "why we need this" explanation screen into the permission screen - one fewer tap-through step.
- The Health Connect permission dialog now opens automatically as soon as onboarding reaches it, instead of waiting for a button tap.
- New step indicator and icon-based feature rows across onboarding, replacing emoji, to match the rest of the app's visual language.

### 🧹 Settings Cleanup
- Removed the Journal feature and Theme Preview screen.
- Consolidated Privacy & Legal into a single entry.
- JSON/CSV export now saves a real file via Android's file picker instead of only opening the share sheet.
- Height now syncs from Health Connect automatically, the same way weight already did.

### 🐛 Fixes
- Training screen's workout list was showing every workout ever logged instead of just today's.
- Daily steps/activity sync could leave stale duplicate rows behind instead of replacing the day's total.

---

## 🚀 What's New in Hangry v1.0.0

Hangry is a private, local-first wellness & recovery tracker for Android powered by Google Health Connect.

### 🛡️ 100% Real Health Connect Data (Zero Fake Data)
- Completely removed all synthetic mock data sources and fake fallbacks.
- When wearable data is missing or pending sync, displays transparent pending indicators (`—`) rather than fabricated values, strictly honoring Rule 2 data integrity.

### ⭕ Daily Activity Concentric Rings
- 3 concentric circular activity gauges tracking **Active Calories**, **Active Minutes**, and **Steps**.
- Configurable daily goals (defaults: 500 kcal, 90 min, 6,000 steps).
- Tap-to-expand interaction reveals exact counts and percentage completion.

### 🫀 Key Vitals & Cardio Fitness
- Direct integration with Google Health Connect for:
  - **VO₂ Max** (Cardio fitness level in mL/kg/min)
  - **SpO₂** (Blood oxygen saturation percentage)
  - **Respiration Rate** (Resting breaths per minute)
  - **Blood Pressure** (Systolic & Diastolic mmHg)
- Modular Vitals card with normal range indicators.
- Support for creating custom dashboard widgets for any vital metric.

### 📸 Quick Log Meals
- Floating camera quick-action button on the dashboard for instant meal photo logging.
- AI nutritional estimates with offline fallback.

### 🎨 Clean Minimal Dark UI & Flicker-Free Performance
- Consolidated color palette reducing visual noise.
- Optimized Compose state emissions to eliminate UI re-composition flicker.
- Responsive layout adapting to various Android screen densities.

### 🔒 Privacy & Open Source
- **100% Offline SQLite database (Room)**: No cloud databases, no user accounts, no ads, zero tracking.
- Open-source under PolyForm Noncommercial License 1.0.0.

---

### 📦 Installation
1. Download **`hangry-v1.1.0.apk`** from the [latest release](https://github.com/sqrlwallet/hangry/releases/latest).
2. Tap the APK file on your device (enable "Install unknown apps" in Android settings if requested).
3. Open Hangry - Health Connect permissions are now requested automatically as part of onboarding.
