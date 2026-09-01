# Workout App Plan

This is the tracked plan for the personal Workout Tracker app. Keep this file
updated at each checkpoint so the plan is visible from every device.

## Current State

- Branch: `PST01`
- Node toolchain: Node 22 for Capacitor/Android commands; `.nvmrc` tracks this.
- Stage 1: complete and committed with the React migration branch history.
- Stage 2: complete and committed as `2bc87c6 PST01: Migrate to react`.
- Stage 3: complete and pushed as `f832f04 PST01: Redesign exercise library`.
- Stage 4: complete and pushed as `d1796e4 PST01: Quest addition`.
- Stage 5: complete and included in the Stage 6 packaging commit.
- Stage 6: complete and pushed as
  `df0f94d PST01: Capacitor Packaging and Device Check`.
- Stage 7: complete in source and ready for review; data-layer storage policy
  is now covered by shared JVM tests.
- Active checkpoint: Stage 7 ready for review.
- Local commit state: Stage 7 follow-up changes are implemented locally but not
  committed.
- Commit rule: review and commit one stage at a time.

## Stage 1 - Reviewed Data, Levels, and Quest Definitions

**Status:** complete.

Goal: establish a reviewable exercise data boundary without changing PWA
behavior. Add app-level suitability tags, progression groups, and
evidence-informed quest CSV definitions before UI or tracking logic depends on
them.

Key files:

- `scripts/scrape-exercise-catalog.mjs`
- `pwa/public/data/exercises.csv`
- `pwa/public/data/quest-templates.csv`
- `pwa/public/data/quest-workouts.csv`
- `pwa/public/data/ATTRIBUTION.md`

Validation:

```sh
node --check scripts/scrape-exercise-catalog.mjs
npm run data:exercises
```

## Stage 2 - React, Vite, and TypeScript Migration

**Status:** complete and pushed on `origin/PST01`.

Goal: migrate the PWA source from DOM-driven vanilla JavaScript to React, Vite,
and TypeScript while preserving IndexedDB, service worker, and Capacitor
contracts.

Key files:

- `package.json`
- `package-lock.json`
- `tsconfig.json`
- `vite.config.mts`
- `capacitor.config.json`
- `pwa/index.html`
- `pwa/src/`
- `pwa/public/`

Validation completed:

```sh
npm run build
npx tsc --noEmit
npm run cap:sync
cd android
./gradlew :app:assembleDebug
```

Browser sweep completed:

- Clean-state CSV import with malformed-row skipping.
- Today completion and skip logs.
- History aggregation.
- Catalog search, playlist add, reorder, edit, and persistence.
- Fresh-session offline reload through the service worker.

## Stage 3 - Interactive Library Redesign

**Status:** complete and pushed on `origin/PST01`.

Goal: improve the mobile-first Library workflow. Add a
Beginner/Intermediate/Advanced level filter, level-aware default prescriptions,
clearer selected/reordered states, stronger responsive feedback, and a more
visible playlist workspace.

Files expected in this stage:

- `docs/WORKOUT_APP_PLAN.md`
- `.nvmrc`
- `package.json`
- `package-lock.json`
- `pwa/src/App.tsx`
- `pwa/src/types.ts`

Changes completed:

- Added this tracked plan file so the staged roadmap is visible from every
  device.
- Added Beginner/Intermediate/Advanced Library filtering.
- Persisted the selected level with the playlist draft.
- Added level-aware default prescriptions when exercises are added.
- Improved exercise rows with level/common badges, source link, default preview,
  and selected playlist position.
- Improved playlist rows with clearer ordering, edit, remove, and save states.
- Pinned Vite tooling to a Node 20.11-compatible stack after the newer
  Vite/Rolldown path required Node 20.19+.
- Added `.nvmrc` because Capacitor CLI 8 requires Node 22 for Android sync.

Validation completed:

```sh
npx tsc --noEmit
npm run build
npm run cap:sync # run under Node 22
cd android
./gradlew :app:assembleDebug
```

Browser acceptance completed:

1. Open the PWA and select Library.
2. Confirm catalog entries load and filters compose correctly.
3. Switch levels and confirm available exercises and default prescriptions
   adapt.
4. Add at least two exercises and verify immediate visual feedback.
5. Reorder, edit sets, reps/duration, and rest without layout shifts.
6. Reload and confirm playlist draft plus selected level persist.
7. Add the playlist to the weekly schedule and confirm duplicate rows are
   blocked.
8. Reload offline and confirm the Library still works.

Checkpoint 3: complete.

Suggested commit after approval:

```sh
git add docs/WORKOUT_APP_PLAN.md .nvmrc package.json package-lock.json pwa/src/App.tsx pwa/src/types.ts
git diff --cached
git commit -m "feat(pwa): redesign the leveled exercise library"
git push origin PST01
```

## Stage 4 - Daily Quest Experience

**Status:** complete and pushed on `origin/PST01`.

Goal: add a Quests tab that resolves the reviewed movement slots to the
selected level, presents the current quest day, schedules it into the existing
workout contract, and records quest/day progress without replacing normal
workout logs.

Files expected in this stage:

- `docs/WORKOUT_APP_PLAN.md`
- `pwa/public/service-worker.js`
- `pwa/src/App.tsx`
- `pwa/src/lib/db.ts`
- `pwa/src/lib/quests.ts`
- `pwa/src/types.ts`

Changes completed:

- Added a Quests tab to the PWA navigation.
- Added CSV parsers for quest template and quest workout definitions.
- Added IndexedDB `appState` persistence for one active quest.
- Added current-day quest resolution by selected level.
- Added quest scheduling into the existing `workouts` store.
- Added optional quest metadata on scheduled workout rows.
- Added automatic quest day completion when all scheduled quest rows receive a
  done/skipped log for the local day.
- Added completed-day history in quest state, preserving the level used for
  completed days while allowing future quest days to switch levels.
- Added replacement behavior so a newly scheduled quest day can replace prior
  completed quest rows at the same weekday/time without deleting logs.
- Bumped the service worker cache version to refresh installed PWAs.

Validation completed:

```sh
npx tsc --noEmit
npm run build
npm run cap:sync # run under Node 22
cd android
./gradlew :app:assembleDebug
```

Browser acceptance completed:

1. Loaded the Quests tab from production preview.
2. Enrolled in Balanced Foundations at Intermediate.
3. Confirmed only Week 1 / Day 1, Foundation A, was shown.
4. Scheduled the quest day into Today.
5. Logged all quest rows from Today and confirmed progress advanced to
   Week 1 / Day 2.
6. Changed the future quest level to Advanced and confirmed the completed
   Foundation A record stayed Intermediate.
7. Scheduled Foundation B at the same weekday/time and confirmed completed
   Foundation A workout rows were replaced while logs remained.
8. Reloaded offline and confirmed the PWA still rendered the scheduled quest
   rows.

Checkpoint 4: complete.

Committed as `d1796e4 PST01: Quest addition`.

## Stage 5 - Workout and Quest History

**Status:** complete and pushed on `origin/PST01`.

Goal: replace the small summary-only History panel with a proper history page
containing overview metrics, recent workout entries grouped by date, and quest
progress/completion history. Month/all-time filters remain available.

Files expected in this stage:

- `docs/WORKOUT_APP_PLAN.md`
- `pwa/public/service-worker.js`
- `pwa/src/App.tsx`
- `pwa/src/types.ts`

Changes completed:

- Added month/all-time History filtering.
- Added overview metrics for workout days, logged items, done entries, and
  skipped entries.
- Added completion percentage bar for the selected history range.
- Added quest progress summary with completed quest-day history.
- Added recent workout activity grouped by local date.
- Added per-exercise done/skipped breakdown with simple Tailwind bars.
- Bumped the service worker cache version so installed PWAs refresh the
  updated History shell.

Validation completed:

```sh
npx tsc --noEmit
npm run build
npm run cap:sync # run under Node 22
cd android
./gradlew :app:assembleDebug
```

Browser acceptance completed:

1. Started from a clean browser app-data state.
2. Enrolled in Balanced Foundations at Intermediate.
3. Scheduled Foundation A into Today's Workout.
4. Logged all five quest workout rows as done.
5. Confirmed History shows 1 workout day, 5 logged items, 5 done, and 0
   skipped.
6. Confirmed Quest Progress shows Balanced Foundations at 1 of 12 days
   complete.
7. Confirmed Recent Activity groups the five logs by local date.
8. Confirmed Per-exercise Breakdown shows each completed exercise.
9. Reinstalled the service worker from a clean cache, confirmed app-shell
   cache contents, reloaded offline, and confirmed History still renders from
   IndexedDB.

Checkpoint 5: stop for history calculations and mobile-layout review before
committing.

Suggested commit: `feat(pwa): add workout and quest history`.

## Stage 6 - Capacitor Packaging and Device Check

**Status:** ready for review.

Goal: verify all approved source commits package into Android. This is a test
checkpoint, not a source commit, unless the device check exposes a source bug.

Files changed in this stage:

- `capacitor.config.json`
- `pwa/src/App.tsx`
- `pwa/src/main.tsx`
- `pwa/src/styles.css`
- `android/wear/src/main/kotlin/app/personal/workouttracker/wear/download/WorkoutListScreen.kt`

Issues found and fixed:

- Capacitor's Android `SystemBars` default safe-area CSS injection logged
  `Error injecting safe area CSS` during WebView startup. Disabled the
  injection through `plugins.SystemBars.insetsHandling = "disable"`.
- The phone WebView rendered under the Android status bar. Added a
  Capacitor-shell root class and native-only safe-area padding for the app
  shell, toast layer, and sticky header.
- The Wear empty state clipped near the bottom of the round emulator. Added a
  centered empty-state layout and round-screen padding for the workout manager
  list.

Checks:

```sh
npx tsc --noEmit
npm run cap:sync
cd android
./gradlew :app:assembleDebug
./gradlew :wear:assembleDebug
```

APK outputs:

- `android/app/build/outputs/apk/debug/app-debug.apk`
- `android/wear/build/outputs/apk/debug/wear-debug.apk`

Device acceptance completed:

1. Started the local `Pixel_8` emulator and confirmed `emulator-5554` booted.
2. Installed and launched `app.personal.workouttracker`.
3. Confirmed the phone app foregrounded as `.MainActivity`.
4. Confirmed the Android 13+ notification permission prompt appears on first
   launch.
5. Granted notification permission and confirmed the app renders unobscured
   below the status bar.
6. Checked filtered logcat output and confirmed no crash or Capacitor
   safe-area console error remained.
7. Started the local `Wear_OS_XL_Round` emulator and confirmed
   `emulator-5556` booted.
8. Installed and launched `app.personal.workouttracker.wear`.
9. Confirmed the Wear app foregrounded as `.WearMainActivity`.
10. Confirmed the Wear empty state is readable on the round emulator.
11. Checked Wear logcat output and confirmed no fatal app crash.

Checkpoint 6: complete.

Committed as `df0f94d PST01: Capacitor Packaging and Device Check`.

## Stage 7 - Phone/Watch Data-Layer Contract Hardening

**Status:** ready for review.

Goal: harden the phone/watch sync boundary before real-device pairing tests.
The live Data Layer path needs both the phone and watch online; because the
current device rule is physical-device-first and otherwise only one emulator at
a time, this stage focuses on static review, shared policy tests, and compile
checks.

Files changed in this stage:

- `android/shared/build.gradle`
- `android/shared/src/main/kotlin/app/personal/workouttracker/shared/DataModels.kt`
- `android/shared/src/test/kotlin/app/personal/workouttracker/shared/DataModelsTest.kt`
- `android/wear/src/main/kotlin/app/personal/workouttracker/wear/data/LogQueueRepository.kt`
- `android/wear/src/main/kotlin/app/personal/workouttracker/wear/data/LogSyncManager.kt`
- `android/wear/src/main/kotlin/app/personal/workouttracker/wear/data/NotificationHelper.kt`
- `android/wear/src/main/kotlin/app/personal/workouttracker/wear/data/WorkoutRepository.kt`
- `android/wear/src/main/kotlin/app/personal/workouttracker/wear/data/WorkoutSetListenerService.kt`
- `android/wear/src/main/kotlin/app/personal/workouttracker/wear/download/WorkoutListScreen.kt`

Changes completed:

- Moved downloaded-workout display status and cap/duplicate/schema insert
  policy into the shared JVM contract module.
- Added shared tests for stale schema handling, duplicate-date protection,
  completed-entry eviction, cap blocking, and successful insert behavior.
- Fixed stale `WorkoutSetPayload` handling so the watch rejects unsupported
  schema versions instead of silently caching them as current data.
- Added a stale-workout notification path for scheduled/manual downloads that
  receive an unsupported payload.
- Fixed the offline watch log queue retry path to remove exactly the sent
  prefix count, avoiding accidental removal of duplicate-looking log entries.

Validation completed:

```sh
cd android
./gradlew :shared:test
./gradlew :app:assembleDebug :wear:assembleDebug
cd ..
npx tsc --noEmit
npm run build
```

Device note:

- `adb devices -l` showed no connected physical devices.
- No emulator was started for this stage because real phone-watch sync requires
  both endpoints online, and the standing rule is one emulator at a time when no
  physical devices are connected.

Checkpoint 7: stop for shared contract/code review before real-device sync
testing.

Suggested commit: `test(android): harden wearable data-layer contracts`.
