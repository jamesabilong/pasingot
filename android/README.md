# Android (Capacitor Wrapper) — Build & Run

This wraps the PWA in `/pwa` as a native Android app. This sandbox has Node
but no Java/Gradle/Android SDK on `PATH`, so the steps below are meant to be
run from a machine with Android Studio (already installed on this Mac, just
not CLI-drivable here).

## One-time setup (already done in this repo, listed for reference)

```bash
npm install
npx cap add android      # generates ./android — already committed
```

`minSdkVersion` is set to **33** in [`variables.gradle`](variables.gradle)
(Android 13+ only, per the personal-use scope — Galaxy S25 ships on 15).

## After any change to `/pwa`

Capacitor's `webDir` (`pwa/`) is copied into the native project's assets —
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
