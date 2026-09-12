# Android (Capacitor Wrapper) — Build & Run

This wraps the built React PWA (`pwa/dist`) in a native Android app and ships
a separate Wear OS companion. Use Node 22, Android SDK, and the project's
Gradle-configured Java toolchain for native builds.

## Manual phone → watch sync

1. Install the current phone **and** Wear APKs. Pair/connect the watch to this
   phone using its companion app. Both builds need the same application ID
   and signing certificate for the Wearable Data Layer.
2. In the Android phone app, add a playlist, quest, or CSV schedule with
   exercises for **today**. A playlist draft alone is not a saved schedule.
3. On the phone, open **Today → Send today to watch**; or on the watch, open
   **Workouts → Sync from phone**. The watch action is above saved workouts.
4. The phone confirms that exercises were queued, not that the watch stored
   them. Check the watch for its received/already-saved/error message.
5. If today's workout is already downloaded, it is preserved, including its
   progress. To fetch an edited schedule, use that workout's **Options →
   Delete download → Confirm**, then sync again. This deletes that local
   download and its session progress; do not do it to an in-progress workout
   unless you intend to restart it.

This transfers today's scheduled exercises, not the full exercise library,
body measurements, or phone history. Watch completion logs return to the phone
through the existing automatic queue when connected and the phone app opens.
Health Connect is separate. Desktop/mobile browser PWAs do not provide the
native Wear bridge; use the installed Android APK on the paired phone.

Website/PWA → watch is possible with an additional transport, but it is not
implemented by installing the PWA. For the existing manual browser-backup route,
future HTTPS/BLE options, and acceptance gaps, see
[Browser-to-watch feasibility](../docs/BROWSER_WATCH_SYNC.md). Restore replaces
the destination app's local data; back up that app first. Browser and Android
databases do not automatically synchronize.

Without a connection, the watch reports an unreachable phone; a sent request
that receives no result times out after 20 seconds and can be retried. Empty
schedules, incompatible payloads, storage limits, and duplicate dates are shown
in the watch app even when notification permission is off.

## Watch → phone session updates

Install both updated APKs. Starting a downloaded workout on the watch now
publishes its status to **Today → Watch sync** on the paired Android phone.
Set/rest changes, pause/resume, completion, and ending also update this card.
It shows the last watch update with a timestamp; controls remain on the watch.
This uses the standard Wear OS Data Layer, not a Samsung-only API. Both apps
must have matching package IDs and signing certificates.

When the phone app is already open, native events refresh its status/history.
When closed, the native listener stores incoming data for the next app open.
Offline live updates are coalesced to the latest state by Play services;
exercise/session history stays queued for retries or the next watch app launch.
An ended workout with no completed exercises is also retried. Replayed imports
are deduplicated before the phone acknowledges them.

On the watch, hidden session screens stop their one-second redraw loop. Rest
deadlines survive and resolve when the screen returns. The previous perpetual
15-minute upload poll is removed; failed uploads use bounded work with backoff.
Background cue timing and actual battery savings still require physical testing.

Paired-device acceptance still to run:

1. Keep Today open on the phone, start a watch workout, then complete a set,
   pause, and resume. Confirm each state arrives without reopening the phone app.
2. Close the phone app, end a watch workout, then reopen the phone and inspect
   History. Repeat by ending before completing any exercise.
3. Disconnect the pair, complete/skip/end on the watch, then reconnect. Verify
   the latest state and eventual history arrive once, including after app restarts.
4. Compare equal-duration idle and workout battery runs on the physical watch,
   including wrist-down/rest and app-exit cases. Capture battery level and
   `adb -s WATCH_SERIAL shell dumpsys batterystats app.personal.workouttracker`
   before/after each run; an emulator cannot establish physical battery savings.

## One-time setup (already done in this repo, listed for reference)

```bash
npm install
npx cap add android      # generates ./android — already committed
```

`minSdkVersion` is set to **33** in [`variables.gradle`](variables.gradle)
(Android 13+ only, per the personal-use scope — Galaxy S25 ships on 15).

## After any change to `/pwa`

Capacitor's `webDir` (`pwa/dist`) is copied into the native project's assets —
re-run this after editing anything in `/pwa`:

```bash
npx cap sync android
```

This only copies web assets and updates the plugin list; it does **not**
require Java and works in this sandbox (verified).

## Build / run a debug APK (requires Android Studio or a Java 17+ JDK + Android SDK)

**Option A — Android Studio (recommended for first run):**
1. Open `android/` as a project in Android Studio.
2. Let Gradle sync finish (first sync downloads the AGP/Kotlin/Compose
   toolchains).
3. Run ▶ on a connected device or emulator (API 33+).

**Option B — command line, once a JDK is on `PATH`:**
```bash
cd android
./gradlew assembleDebug
# APK at: android/app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Windows setup notes

Use **PowerShell** from the repository root unless noted otherwise.

Required installs:

1. **Android Studio** with Android SDK Platform **37** installed.
2. **JDK 17**. Android Studio's bundled JDK is fine; if command-line Gradle
   cannot find Java, set `JAVA_HOME` to Android Studio's bundled JDK.
3. **Node 22**. The repo has `.nvmrc`; with `nvm-windows`, run:

```powershell
nvm install 22
nvm use 22
node -v
```

4. **Samsung USB Driver for Windows**, if testing on a physical Galaxy phone.
5. USB debugging enabled on the phone:
   `Settings > About phone > Software information > tap Build number 7 times`,
   then `Developer options > USB debugging`.

First-time checkout/build:

```powershell
npm ci
npm run build
npm run cap:sync
cd android
.\gradlew.bat :shared:test
.\gradlew.bat :app:assembleDebug :wear:assembleDebug
```

APK outputs:

- Phone APK: `android\app\build\outputs\apk\debug\app-debug.apk`
- Watch APK: `android\wear\build\outputs\apk\debug\wear-debug.apk`

Device checks from PowerShell:

```powershell
adb devices -l
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

For the watch APK, prefer Android Studio's device picker if the paired Galaxy
Watch appears there. If it does not, enable watch debugging from the Galaxy
Wearable developer options and connect it through Android Studio's Wear OS
pairing/debugging flow.

Real phone-watch sync testing needs both the phone and watch online and paired.
Using a single emulator is fine for basic launch checks, but it cannot prove
the full Wearable Data Layer flow by itself.

## Wear OS behavior to verify on device

1. Launch the watch app and grant notification permission when prompted.
2. Tap **Download Now** while the paired phone app has a synced schedule for
   today.
3. Confirm the downloaded workout shows an estimated duration before starting.
4. In the PWA, confirm Today's Workout and the active quest day show **Plan
   Progress** with completed, pending, and skipped counts.
5. Start the PWA **workout player** from Today's Workout. Confirm **Complete
   set** advances sets, starts rest, supports **+5s**/**+10s**/**+30s**, and
   updates Plan Progress as rows become done/skipped.
6. Pause the PWA workout player, reload the app, then confirm **Resume**,
   **Restart**, and **End** recover the same session state.
7. End or complete a PWA workout player session and confirm PWA History shows a
   workout session summary without changing exercise done/skipped counts.
8. Open the downloaded workout. **Complete Set** should start the configured
   rest countdown between sets, then advance set progress when rest ends.
9. During that rest countdown, tap **+5s**, **+10s**, and **+30s** and confirm
   the countdown extends from its current remaining time.
10. Tap **Pause** during an active set, close and reopen the workout, then
   confirm the recovery screen offers **Resume** and **End Workout**.
11. Confirm the recovery screen also shows elapsed time and offers **Restart**.
12. Use **Restart** and confirm the session returns to exercise 1, set 1 without
   sending a `done` log.
13. Tap **Pause** during a rest countdown, resume, and confirm the countdown
   continues from the paused remaining time.
14. End a paused workout and confirm it shows **Ended** in the downloads list
   without sending a `done` log for the unfinished exercise.
15. Bring the phone app foreground and confirm PWA History shows the ended
   workout under **Workout Sessions** with elapsed time.
16. Finish the final set for an exercise. The watch should log the exercise only
   after that final set, then use the same rest countdown before the next
   exercise.
17. Bring the phone app foreground and confirm PWA History shows the completed
   workout under **Workout Sessions** without changing done/skipped exercise
   counts.
18. Tap **Skip** on another exercise and confirm it advances immediately.
19. Bring the phone app foreground again; pending watch logs should drain into
   the PWA History through `WorkoutLogBridge`.
20. Fill the watch with three active/paused/resting downloads, then request another
   download. The watch should keep existing entries and show the blocked
   download notification.

## What's already wired up (Prompt 3)

- **`minSdkVersion 33`** — [`variables.gradle`](variables.gradle).
- **IndexedDB** — needs no extra config; Android's WebView (Chromium-based)
  supports IndexedDB by default, so the PWA's `workouts`/`logs` stores
  (Prompt 1) work unchanged inside the wrapper.
- **File upload (CSV import)** — the PWA's `<input type="file">` (see
  [`pwa/index.html`](../pwa/index.html)) triggers the native file picker
  inside the WebView with no extra glue code required. `@capacitor/filesystem`
  is installed as a dependency for any future native-side file read/write
  (e.g. the Prompt 6/8 bridge plugins persisting to app-private storage) —
  it's not needed just to make the existing `<input type=file>` work.
- **Notifications** — `@capacitor/local-notifications` is installed;
  `capacitor.config.json` configures its icon/color. Its own manifest (see
  `node_modules/@capacitor/local-notifications/android/.../AndroidManifest.xml`)
  only requests `RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK`, `POST_NOTIFICATIONS` —
  **no** `SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM`. The app manifest
  defensively blocks both anyway via `tools:node="remove"` in case a future
  plugin bump reintroduces them (see [`AndroidManifest.xml`](app/src/main/AndroidManifest.xml)).
- **`INTERNET`** — declared (Capacitor's default template already includes
  it).
- **`POST_NOTIFICATIONS` runtime request** — [`MainActivity.kt`](app/src/main/java/app/personal/workouttracker/MainActivity.kt)
  requests it on first launch only (SDK ≥ 33), guarded by a `SharedPreferences`
  flag so it's never re-prompted.
- **Offline support carries over** — the service worker registered by the
  React PWA entry point (`pwa/src/main.tsx`, Prompt 1 req 7) runs inside the
  WebView exactly as it does in a browser tab; no native changes needed for
  offline caching.

## Module layout

```
android/
  app/       phone module — Capacitor wrapper + native data-layer code (Prompt 6, 8)
  shared/    plain kotlin("jvm") module — data contract shared with :wear
  wear/      Wear OS module — Prompt 4, 5 (added separately)
```
