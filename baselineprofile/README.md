# Baseline profile

Tells Android which of Hangry's code to compile ahead of time, so cold start and the first
switch to each tab are smooth straight after an install or update, instead of only once Android
has compiled the app in the background, which can take days. The profile ships inside the APK/AAB.
`profileinstaller` installs it on sideloaded installs too.

## Regenerating

Do this after big UI changes, especially to Today, the tabs or start-up, and before a release.

1. Connect the **test phone** (the dummy-account one) over USB with USB debugging on.
   **Not your everyday phone.** Generation installs a release build of Hangry over whatever is
   there. If the signing key differs (debug-signed sideload vs. the upload key), the old app has
   to be uninstalled first, and its data goes with it.
2. Run:

   ```bash
   ./gradlew :app:generateBaselineProfile
   ```

   It installs the app, walks through onboarding on a fresh install (skipping profile setup and
   carrying on without Health Connect permission prompts), then cold-starts the app and taps
   through every tab a few times. Expect a few minutes.
3. Commit the result: `app/src/release/generated/baselineProfiles/baseline-prof.txt` and
   `startup-prof.txt`.

The steps it records are in
[BaselineProfileGenerator.kt](src/main/java/com/kevan/hangry/baselineprofile/BaselineProfileGenerator.kt).
It finds things by their on-screen English text (tab labels, onboarding buttons). If those change
in `strings.xml`, update the generator to match.
