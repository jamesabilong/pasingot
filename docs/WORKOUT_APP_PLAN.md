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
- Stage 7: complete and committed as
  `55c17bb PST01: Watch Data-Layer Contract Hardening`.
- Latest committed Android checkpoint:
  `4c583e5 PST01: Android update`.
- Active checkpoint: Stage 9 session resilience in progress locally.
- Local commit state: Stage 8 follow-up and Stage 9 slices 1-5 are implemented
  locally but not committed.
- Cross-device visibility: this file documents the current local state, but the
  code and plan changes will only be visible on another device after this work
  is committed and pushed/synced from this machine.
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

**Status:** complete and committed as `55c17bb PST01: Watch Data-Layer Contract Hardening`.

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

## Stage 8 - Watch Flow Cleanup

**Status:** implemented locally; ready for review.

Goal: close the remaining Wear session gaps found during the follow-up review
and keep the documentation aligned with the current branch state.

Files changed in this stage:

- `android/wear/src/main/kotlin/app/personal/workouttracker/wear/WearMainActivity.kt`
- `android/wear/build.gradle`
- `android/shared/src/main/kotlin/app/personal/workouttracker/shared/DataModels.kt`
- `android/shared/src/test/kotlin/app/personal/workouttracker/shared/DataModelsTest.kt`
- `android/wear/src/main/kotlin/app/personal/workouttracker/wear/download/WorkoutListScreen.kt`
- `android/wear/src/main/kotlin/app/personal/workouttracker/wear/session/SessionScreen.kt`
- `android/wear/src/main/kotlin/app/personal/workouttracker/wear/session/SessionViewModel.kt`
- `pwa/src/styles.css`
- `pwa/src/App.tsx`
- `android/README.md`
- `docs/WORKOUT_APP_PLAN.md`

Changes completed:

- Added set-by-set Wear session progress: `Complete Set` now advances
  `currentSet` and logs `done` only after the final set for the exercise.
- Added a Wear rest countdown that uses each playlist exercise's `rest` value
  between sets and before the next exercise, with resting progress persisted by
  timestamp so resume/background timing stays aligned.
- Added `+5s`, `+10s`, and `+30s` controls to the active Wear rest countdown so
  users can extend recovery time without leaving the session.
- Added estimated workout duration labels to playlist building, quest-day
  previews, today's schedule, and downloaded watch workouts. Estimates include
  sets, reps/durations, rest intervals, between-exercise transitions, and a
  larger beginner/unknown-level buffer.
- Kept the app and web app on the same estimate calculation while styling the
  web version as a fuller allocation panel and the native app as a compact
  touch-friendly estimate pill.
- Added a `Resting` downloaded-workout status and bumped the native shared
  schema after extending `SessionState`.
- Added the missing Wear `Skip` action so the watch can emit the shared
  `skipped` log status that the phone/PWA bridge already supports.
- Kept `currentSet` valid when the user downgrades the active exercise's set
  count.
- Added a one-time Wear runtime notification permission request so blocked or
  stale scheduled-download notifications are not silently suppressed.
- Updated the checkpoint docs to reflect that Stage 7 is committed and this
  cleanup is the current local review stage.

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

- Real phone-watch sync still needs a paired physical phone and watch, or two
  concurrently connected endpoints. Static/compile checks can verify wiring,
  but they cannot prove live Wearable Data Layer delivery by themselves.

## Stage 9 - Session Resilience and Recovery

**Status:** in progress locally.

Implementation visibility:

- Local only: the implementation and this plan update are present in the
  current working tree on this machine.
- Not yet committed: Stage 9 should be reviewed with the Stage 8 local
  follow-up changes before creating the next checkpoint commit.
- Other devices: pull/sync will not show these updates until a commit is pushed
  or the working tree is otherwise synced.

Goal: make active workouts reliable when the user intentionally pauses/stops or
the app/watch is interrupted unexpectedly. The watch, native app shell, and PWA
should all preserve enough session state for users to continue safely or end
cleanly without losing progress.

Planned behavior:

- Add an explicit **Pause** action during an active set and rest countdown.
  First slice: implemented locally on Wear.
- Add a deliberate **Stop / End Workout** flow with confirmation, so accidental
  taps do not discard the session. First slice: implemented locally from the
  paused Wear recovery screen without sending `done` logs for unfinished rows.
- Persist active session progress on lifecycle interruptions: watch screen
  close, app background, app swipe-away, low-battery interruption, process kill,
  and device restart where supported. First slice: active-set close/background
  persists as paused; rest countdowns still use timestamp-based recovery.
- On reopen, show a recovery screen with **Resume**, **Restart**, and
  **End Workout** choices, including the last exercise, set number, rest
  remaining, and elapsed time when available. First slice: Wear now shows
  **Resume**, confirmed **Restart**, confirmed **End Workout**, paused rest
  remaining, and elapsed active workout time.
- Track why a session stopped: completed, paused by user, ended by user,
  skipped, app closed, or unexpectedly interrupted. First slice: stored
  `paused` and `ended` states are available; second slice adds stored
  stop/recovery reasons for completed, user-paused, app-closed, user-ended, and
  unexpected-interruption paths.
- Keep rest countdowns timestamp-based so long background pauses do not freeze
  time incorrectly; if rest elapsed while closed, resume at the next set/exercise
  with a clear state.
- Queue unsent watch logs and session-end records until the phone/PWA bridge is
  reachable again. Third slice: watch session-end events now use their own
  offline queue, phone Data Layer listener, phone pending store, and PWA drain
  path.
- Mirror the same lifecycle rules in the future PWA/mobile workout-player view,
  but adapt the design: watch gets glanceable controls, while PWA/mobile gets a
  richer recovery panel and workout summary.

Progress tracker:

- Completed: Wear explicit pause during active sets and rest countdowns.
- Completed: Wear paused recovery screen with resume, restart, close, and
  confirmed end workout.
- Completed: Wear elapsed active workout time on active, rest, and recovery
  screens.
- Completed: Manual ended workouts use `ended` status and do not emit `done`
  logs for unfinished rows.
- Completed: Watch session completed/ended events queue offline, sync to phone,
  and appear in PWA History.
- Completed: PWA plan progress cards show completed, pending, and skipped
  counts for Today's Workout and the active quest day.
- Completed: PWA/mobile live workout-player screen for Today's Workout with
  complete set, skip exercise, rest countdown, rest extensions, pause, resume,
  restart, end workout, elapsed time, persisted recovery, and local session
  summaries.
- Pending: Device-level validation for app swipe-away, process kill,
  low-battery interruption, and device restart.
- Pending: Richer post-workout summary comparing estimated vs actual completion
  time.

Current validation completed:

```sh
npx tsc --noEmit
npm run cap:sync
cd android
./gradlew :shared:test :app:assembleDebug :wear:assembleDebug
cd ..
git diff --check
```

Current validation status:

- Passed: TypeScript compile.
- Passed: PWA production build through `npm run cap:sync`.
- Passed: Capacitor asset sync into the Android shell.
- Passed: shared Kotlin tests.
- Passed: phone debug APK build.
- Passed: Wear OS debug APK build.
- Not yet performed: physical phone/watch sync and interruption testing.

First-slice files changed:

- `android/shared/src/main/kotlin/app/personal/workouttracker/shared/DataModels.kt`
- `android/shared/src/test/kotlin/app/personal/workouttracker/shared/DataModelsTest.kt`
- `android/wear/src/main/kotlin/app/personal/workouttracker/wear/download/WorkoutListScreen.kt`
- `android/wear/src/main/kotlin/app/personal/workouttracker/wear/session/SessionScreen.kt`
- `android/wear/src/main/kotlin/app/personal/workouttracker/wear/session/SessionViewModel.kt`
- `docs/WORKOUT_APP_PLAN.md`

Second-slice behavior:

- Added elapsed active workout time to the Wear active, rest, and paused
  recovery screens. Elapsed time freezes while paused/ended and resumes when
  the session resumes.
- Added confirmed **Restart** from the Wear paused recovery screen. Restart
  returns to exercise 1, set 1, clears rest/timing progress, and does not send
  any completion logs.
- Added persisted timing fields and stop-reason metadata to `SessionState` and
  bumped the shared schema again so stale watch sessions are re-downloaded
  instead of silently misread.

Third-slice behavior:

- Added a separate watch-to-phone `/session-event` Data Layer path for
  workout-level completed/ended events.
- Added offline retry for watch session events through the existing startup and
  periodic flush path.
- Added phone-native pending session-event staging plus PWA bridge acking, so
  events are removed only after the PWA commits them to IndexedDB.
- Added a PWA `sessionEvents` store and History **Workout Sessions** section that
  shows completed/ended watch sessions, elapsed time, stop reason, current
  exercise, set, and event time without changing exercise done/skipped counts.

Fourth-slice behavior:

- Added PWA **Plan Progress** summaries for Today's Workout and the active quest
  day, with completed, pending, skipped, and handled percentage indicators.
- Added this Stage 9 progress tracker so completed and pending resilience items
  are visible directly in the plan.

Fifth-slice behavior:

- Added a PWA/mobile live workout player to Today's Workout. It persists active
  session state in IndexedDB app state and supports complete set, skip exercise,
  rest countdown, `+5s`/`+10s`/`+30s` rest extensions, pause, resume, restart,
  end workout, elapsed time, and close/reopen recovery.
- PWA/mobile completion and manual end now create local workout session
  summaries in the same `sessionEvents` store used by watch sync, without
  changing exercise done/skipped counts.

Validation to add:

1. Start a workout, pause during a set, close the watch app, reopen, and resume
   from the same exercise and set.
2. Start a rest countdown, add extra rest, background the watch app, reopen, and
   confirm the remaining time is based on real elapsed time.
3. Force-stop or kill the watch app mid-session, reopen it, and confirm the
   recovery screen offers resume/restart/end.
4. End a workout manually and confirm no extra `done` log is sent for unfinished
   exercises.
5. Disconnect the phone, finish or end a watch session, reconnect, and confirm
   queued logs sync into PWA History.
