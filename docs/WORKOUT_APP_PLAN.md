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
- Latest committed React checkpoint:
  `493754d PST01: Stage 16 implementation`. **Note:** despite the commit
  message, this commit's content is the Stage 17 Health Connect bridge,
  permission UI, and completed-workout write path (the real Stage 16 —
  custom exercises/exercise media — landed earlier as `2d1d558 PST01: Stage
  16 implemented`). Flagging the mislabel here rather than rewriting pushed
  history.
- Active checkpoint: Stage 17 Health Connect integration first
  completed-workout sync slice is implemented and committed (see Stage 17
  below). Body-weight sync and heart-rate summaries remain unbuilt follow-ups.
- Local commit state: working tree is clean; everything through Stage 17's
  first slice is committed and pushed to `origin/PST01`.
- 2026-09-03 audit: a full code-review pass over everything since `bce661a`
  (Stages 10-17) found and fixed 10 issues, including a build-breaking
  wiring bug in the Health Connect History panel (the app did not compile),
  two strength-analytics correctness bugs (0-rep sets corrupting PRs/1RM, and
  same-session warm-up ramps being flagged as false PRs), a CSV import bug
  silently coercing an unrecognized load unit to kg, an uncaught native crash
  opening the Health Connect installer with no Play Store, and a missing
  retry queue for failed Health Connect writes. All 10 are fixed; see the
  Stage 17 note below for the Health Connect specifics.
- Cross-device visibility: committed checkpoints are visible from another
  device after the `PST01` branch is fetched/synced.
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
- Completed: PWA and watch session summaries now preserve estimated duration
  for new workout-level session events and History compares actual time against
  estimate when available.
- Completed: PWA/mobile active sessions now track last user interaction,
  auto-pause after 45 minutes without player activity, and close stale
  prior-day sessions instead of letting the timer run into the next day.
- Pending: Device-level validation for app swipe-away, process kill,
  low-battery interruption, and device restart.

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

Sixth-slice behavior:

- Added `lastInteractionAtEpochMillis` to PWA/mobile active workout sessions.
- Player actions and set-input edits now refresh the interaction timestamp.
- Active/resting sessions automatically pause after 45 minutes without player
  activity so abandoned workouts stop accumulating elapsed time.
- Sessions whose `planDate` no longer matches the local day are closed,
  recorded as an ended session event with `stale_next_day`, and removed from
  active app state.
- History and the player now display clearer reasons for inactivity timeout and
  day-change cleanup.

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

## Stage 10 - History Stats Foundation

**Status:** implemented locally; ready for review.

Goal: make History more useful without changing the workout data model yet.
This stage should use data the app already stores: workout log dates, done/
skipped status, session events, and catalog muscle/category metadata.

Changes completed:

- Added a compact activity calendar showing days with completed or skipped
  workout logs.
- Added current streak and longest streak metrics based on completed workout
  days.
- Added weekly and monthly trend bars for completed items, skipped items, and
  workout session summaries.
- Added muscle/category balance summaries by joining logged exercise names to
  catalog primary-muscle metadata.
- Added this week vs last week and this month vs last month comparison cards.

Suggested files:

- `pwa/src/App.tsx`
- `pwa/src/styles.css`
- `pwa/src/types.ts` only if reusable stat types are helpful.

Validation:

```sh
npx tsc --noEmit
npm run build
git diff --check
```

Browser smoke test:

- Reloaded the app after extraction and opened History.
- Confirmed the extracted History screen renders with the Stage 10/11 sections.
- Added and deleted a temporary body-weight entry through `HistoryView`.

Manual acceptance:

1. Log workouts on several different dates, including skipped exercises.
2. Confirm the calendar/heatmap marks only days with activity.
3. Confirm streaks count training days, not individual exercises.
4. Confirm muscle/category totals match the catalog metadata for logged
   exercise names.

## Stage 11 - Body Metrics Log

**Status:** implemented locally; ready for review.

Goal: add personal body tracking as a separate local log, independent of
workout schedule rows. Keep this small and useful first: body weight now,
measurements later if desired.

Changes completed:

- Added a local `bodyMetrics` IndexedDB store for body-weight entries.
- Added body-weight entry type support with date, value, unit, and optional
  note.
- Added a History body-weight panel with latest value, change from prior entry,
  add/update form, and recent entry list.
- Added edit and delete controls for incorrect entries.
- Preserved the offline-first IndexedDB model; no cloud account required.

Suggested files:

- `pwa/src/types.ts`
- `pwa/src/lib/db.ts`
- `pwa/src/App.tsx`
- `pwa/src/styles.css`

Validation:

```sh
npx tsc --noEmit
npm run build
git diff --check
```

Browser smoke test:

- Opened `http://localhost:8090/`.
- Confirmed the History body-weight panel renders.
- Added a body-weight entry, edited it, then deleted the temporary test entry.

### Stage 11A - React Component Cleanup

**Status:** implemented locally; ready for review.

Goal: keep the Stage 10/11 additions from making `App.tsx` an oversized
single-component implementation. Extract feature-specific rendering and pure
calculation logic so the app uses React components more deliberately.

Changes completed:

- Extracted the History screen into `pwa/src/components/HistoryView.tsx`.
- Moved history/stat derivation helpers into `pwa/src/lib/history-stats.ts`.
- Moved body-metric draft parsing/normalization into
  `pwa/src/lib/body-metrics.ts`.
- Moved shared duration formatting into `pwa/src/lib/format.ts`.
- Added `docs/REACT_WEB_APP_INSTRUCTIONS.md` as the forward guideline for
  future React/PWA feature work.
- Extracted shell/header/navigation/toast display into
  `pwa/src/components/AppShell.tsx`.
- Extracted Today, Quests, Library, and Import tab display into
  `TodayView.tsx`, `QuestsView.tsx`, `LibraryView.tsx`, and `ImportView.tsx`.
- Left `App.tsx` responsible for app-level state, persistence, tab selection,
  and cross-feature orchestration only.

Validation:

```sh
npx tsc --noEmit
npm run build
git diff --check
```

Manual acceptance:

1. Add body-weight entries for multiple dates.
2. Reload the app and confirm entries persist.
3. Edit and delete entries and confirm History updates.

## Stage 12 - Weighted Set Logging Data Model

**Status:** implemented locally; ready for review.

Goal: introduce weight/load tracking carefully because many strength features
depend on it. This should be a schema-safe stage with backward compatibility
for existing bodyweight-only rows and logs.

Changes completed:

- Added optional `loadWeight` / `loadUnit` fields to scheduled workout rows and
  playlist items.
- Added `WorkoutSetLog` and an IndexedDB `setLogs` store for completed set
  reps/load records.
- Bumped the PWA IndexedDB version to 5 with backward-compatible store
  creation.
- Extracted the live workout player into
  `pwa/src/components/WorkoutPlayer.tsx` so Stage 12 UI stayed out of
  `App.tsx`.
- Added controlled per-set reps/load/unit inputs to the PWA workout player.
- Defaulted set inputs from the scheduled prescription while allowing blank
  load for bodyweight work.
- Recorded one set-log row on every completed PWA set, while preserving the
  existing exercise-level done/skipped log behavior.
- Added optional CSV import support for `load_weight` / `load_unit` and
  compatible camelCase/short aliases.
- Displayed planned load in Today rows and in the active player when present.
- Passed optional load through the phone-native schedule bridge to Wear OS and
  displayed it on the Wear active-session prescription line.

Files changed:

- `pwa/src/types.ts`
- `pwa/src/lib/db.ts`
- `pwa/src/App.tsx`
- `pwa/src/components/WorkoutPlayer.tsx`
- `android/shared/src/main/kotlin/app/personal/workouttracker/shared/DataModels.kt`
- `android/app/src/main/java/app/personal/workouttracker/weardata/ScheduleSyncPlugin.kt`
- `android/app/src/main/java/app/personal/workouttracker/weardata/WorkoutRequestListenerService.kt`
- `android/wear/src/main/kotlin/app/personal/workouttracker/wear/session/SessionScreen.kt`

Validation:

```sh
npx tsc --noEmit
npm run build
cd android
./gradlew :shared:test :app:assembleDebug :wear:assembleDebug
```

Browser smoke test:

- Opened `http://localhost:8090/`.
- Added a weighted playlist item with `25 kg`.
- Saved it to today's schedule and confirmed Today displayed the planned load.
- Started the extracted PWA workout player and confirmed the current set
  pre-filled reps/load/unit.
- Completed one set and confirmed Today displayed a completed-set count.

Manual acceptance:

1. Existing schedules and history load without migration errors.
2. Bodyweight-only workouts can still be completed without entering weight.
3. Weighted exercises can record load per set.
4. Reload/reopen recovery preserves entered set data.

## Stage 13 - Strength Analytics

**Status:** implemented locally; ready for review.

Goal: turn weighted set data into useful training feedback.

Changes completed:

- Added pure strength analytics helpers in `pwa/src/lib/strength-analytics.ts`.
- Added personal-record detection for max load, max reps at a recorded load,
  and best estimated 1RM.
- Added per-exercise strength cards in History for weighted set logs.
- Added compact per-exercise trend bars based on daily training volume.
- Kept estimated 1RM unavailable for bodyweight, duration-only, or unweighted
  logs by excluding those rows from strength analytics.
- Deferred RPE/RIR and plate calculator controls for a later opt-in stage.

Files changed:

- `pwa/src/App.tsx`
- `pwa/src/components/HistoryView.tsx`
- `pwa/src/lib/strength-analytics.ts`

Validation:

```sh
npx tsc --noEmit
npm run build
```

Manual acceptance:

1. Complete repeated weighted sets and confirm PRs are detected only when a
   true new best occurs.
2. Confirm charts separate exercises correctly.
3. Confirm estimated 1RM is hidden or marked unavailable for non-weighted logs.

## Stage 14 - Workout Player Cues

**Status:** complete and pushed on `origin/PST01` as part of
`b66d19c PST01: Stage 13, 14 and react clean up`.

Goal: improve in-session feedback with phone-side alerts and optional voice
cues, matching the polish users expect from workout apps without requiring a
backend.

Implemented behavior:

- Adds PWA rest-complete haptic, sound, and voice cues.
- Adds persisted in-workout settings to enable/disable haptics, sound, and
  voice cues.
- Prevents rest-complete cues from replaying after session reload/recovery.
- Adds lightweight Wear OS haptic feedback to session actions so watch
  interactions feel aligned with the phone-side session flow.

Key files:

- `pwa/src/App.tsx`
- `pwa/src/components/TodayView.tsx`
- `pwa/src/components/WorkoutPlayer.tsx`
- `android/wear/src/main/kotlin/app/personal/workouttracker/wear/session/SessionScreen.kt`

Validation:

```sh
npx tsc --noEmit
npm run build
cd android
./gradlew :app:assembleDebug :wear:assembleDebug
```

Manual acceptance:

1. Start a rest timer on phone/PWA and confirm the alert fires when rest ends.
2. Disable cues and confirm no haptic/sound fires.
3. Confirm cues do not repeat after reload or recovery.

## Stage 14B - React State and Logic Overhaul

**Status:** complete and pushed on `origin/PST01` as
`650ac65 PST01: React cleanup`.

Goal: reduce `App.tsx` from a large app controller into a thin composition
root by moving feature-specific state, effects, persistence, and action
handlers into focused hooks and service modules. `App.tsx` may still contain
tab routing and top-level composition, but it should not own every workflow's
business logic.

React guidance check:

- The official Vite React TypeScript starter keeps a small demo state in
  `App.tsx`, but that template is intentionally minimal and not a production
  architecture model for a multi-screen offline app.
- React's current docs recommend custom Hooks when logic needs to be reused or
  when a component should focus on intent instead of implementation details.
- React components may contain local UI state and event handlers, but complex
  side effects, persistence flows, data loading, and domain operations should
  be extracted when they make a component hard to reason about.

Planned behavior:

- Create `pwa/src/hooks/` for feature hooks:
  `useWorkoutData`, `useWorkoutSession`, `useWorkoutCueSettings`,
  `useQuestWorkflow`, `usePlaylistDraft`, `useBodyMetrics`,
  `useNotifications`, and `useWatchSync` as the code shape requires.
- Create or extend `pwa/src/lib/` service modules for pure/non-React domain
  logic: workout session transitions, schedule import/save, cue playback,
  notification timing, and quest completion reconciliation.
- Fix the Stage 14 cue-setting race by making cue setting updates functional
  or reducer-driven before extracting that logic.
- Keep screen components responsible for display and local form affordances
  only; they should receive focused props from hooks instead of broad app-wide
  state.
- Keep `App.tsx` as the composition root: choose the active tab, call feature
  hooks, connect hook outputs to screen components, and render `AppShell`.
- Add a lightweight architecture checklist so future stages do not reintroduce
  large feature workflows directly into `App.tsx`.

Implemented in the first cleanup slice:

- Extracted workout planning helpers, validation, default prescriptions, level
  labels, and duration estimates into `pwa/src/lib/workout-planning.ts`.
- Extracted PWA workout session types and transition helpers into
  `pwa/src/lib/workout-session.ts`.
- Extracted cue playback and cue settings normalization into
  `pwa/src/lib/workout-cues.ts`.
- Added focused hooks for body metrics, workout cue settings, toasts, and
  schedule notification polling.
- Fixed the Stage 14 cue-setting race with a ref-backed settings hook so rapid
  toggles compose against the latest setting snapshot.
- Simplified Wear OS session haptic handling with a remembered cue-action
  helper and shared cancel-session helper.

Suggested files:

- `pwa/src/App.tsx`
- `pwa/src/hooks/`
- `pwa/src/lib/workout-session.ts`
- `pwa/src/lib/workout-cues.ts`
- `pwa/src/lib/notifications.ts`
- `pwa/src/lib/watch-sync.ts`
- `docs/REACT_WEB_APP_INSTRUCTIONS.md`

Validation:

```sh
npx tsc --noEmit
npm run build
git diff --check
```

Manual acceptance:

1. `App.tsx` is materially smaller and has no feature-specific persistence
   algorithms or timer/state-machine code inline.
2. Today workout start, set completion, rest auto-advance, pause/resume,
   stale-session recovery, and cue toggles behave the same as Stage 14.
3. Quest scheduling/completion, playlist save/import, body metrics, History,
   and watch log drain still work.
4. Hooks have focused names and do not become generic catch-all wrappers.

## Stage 15 - Data Portability

**Status:** implemented locally; ready for review.

Goal: give the user a way to protect and move their data while preserving the
offline-first, no-account design.

Implemented behavior:

- Adds full local JSON backup export for schedule, logs, session events, set
  logs, body metrics, and app state including quests, draft playlist, cue
  settings, and active session state.
- Adds full backup restore with supported-format validation.
- Restores by replacing local user stores so duplicate IDs are not created
  during recovery.
- Pushes restored schedules back to the Android native cache.
- Keeps existing CSV schedule import intact.

Key files:

- `pwa/src/App.tsx`
- `pwa/src/components/ImportView.tsx`
- `pwa/src/lib/backup.ts`

Validation:

```sh
npx tsc --noEmit
npm run build
```

Manual acceptance:

1. Export a populated app database.
2. Clear local app data.
3. Import the backup and confirm schedule, history, quests, sessions, and body
   metrics are restored.

## Stage 16 - Exercise Library Expansion

**Status:** implemented locally as a first slice; ready for review.

Goal: make the library more personal and more useful for form reference.

Implemented behavior:

- Adds custom exercise creation, editing, and deletion.
- Adds optional image and video URL fields for custom exercises.
- Stores custom exercises separately from the built-in wger catalog so catalog
  refreshes do not overwrite user-created movements.
- Merges custom exercises into the Library filters and playlist builder.
- Carries custom exercises in full JSON backups and restores.
- Retains built-in source attribution and labels custom exercises as
  user-provided.

Deferred from this first slice:

- Built-in catalog media scraping and license review for third-party images.
- Quest-template authoring against custom exercises.
- Preventing deletion of a custom exercise already used by saved schedule rows
  or historic logs; those records currently keep their exercise name.

Key files:

- `pwa/src/types.ts`
- `pwa/src/lib/db.ts`
- `pwa/src/lib/custom-exercises.ts`
- `pwa/src/lib/backup.ts`
- `pwa/src/App.tsx`
- `pwa/src/components/LibraryView.tsx`

Validation:

```sh
npx tsc --noEmit
npm run build
```

Manual acceptance:

1. Create a custom exercise and add it to a playlist.
2. Complete the workout and confirm the custom exercise appears in History.
3. Add image/video URLs and confirm Library shows the custom media affordances.
4. Export/import a full backup and confirm custom exercises are restored.
5. Confirm built-in catalog attribution remains visible.

## Stage 17 - Health Connect Integration

**Status:** in progress; first completed-workout sync slice implemented and
committed (`493754d`, mislabeled "Stage 16 implementation" — see Current
State above).

**2026-09-03 audit findings on this slice (all fixed):**

- `pwa/src/App.tsx` referenced the `useHealthConnectSync` hook's fields as
  bare undeclared identifiers instead of destructuring the hook's return
  value — a hard `tsc`/build failure. Fixed by destructuring.
- `HealthConnectBridgePlugin.kt`'s Play Store installer intent had no
  try/catch, unlike the sibling permission-request path — an uncaught
  `ActivityNotFoundException` on a device with no Play Store. Fixed.
- Failed/interrupted Health Connect writes had no retry path, unlike the
  existing watch-log sync queue. Added a persisted pending-write queue in
  `pwa/src/lib/native-bridge.ts`, drained on the same app-open/visibility
  triggers `drainPendingWatchLogs` already uses.
- The hook's persisted `enabled` flag was only read on mount, so a full
  backup restore (Stage 15) that changed it left the in-memory value stale.
  Fixed by re-reading it on `visibilitychange` too.

Goal: integrate with Android's health ecosystem without introducing app
accounts or a custom backend.

Planned behavior:

- Write completed workouts to Health Connect where permissions allow.
- Keep body weight sync as a later Stage 17 follow-up after workout-session
  permission and write behavior is proven on device.
- Consider heart-rate summaries from Wear OS only after session and permission
  handling are proven reliable.
- Add clear local settings for sync enablement and permission state.

Suggested files:

- Android app module files for Health Connect permissions and writes.
- `pwa/src/lib/native-bridge.ts`
- `pwa/src/App.tsx`
- `pwa/src/components/HistoryView.tsx`

Validation:

```sh
npx tsc --noEmit
npm run build
cd android
./gradlew :app:assembleDebug :wear:assembleDebug
```

Manual acceptance:

1. Grant Health Connect permissions.
2. Complete a workout and confirm it appears in Health Connect.
3. Disable sync and confirm no additional writes occur.

## Stage 18 - Cross-Device UI Overhaul

**Status:** proposed; plan added after Stage 14 audit.

Goal: modernize the workout experience across web, installed PWA/mobile, and
Wear OS while preserving the component boundary rule that `App.tsx` coordinates
state and feature screens own their display.

Planned behavior:

- Establish shared design tokens for spacing, color, typography, elevation,
  focus states, and control sizing in the PWA styles.
- Rework the web/PWA app shell so navigation, active workout status, sync
  state, and install/offline affordances are consistent across desktop and
  mobile viewports.
- Redesign the Today flow around the current workout, resume/recovery state,
  rest cues, and next-step actions without burying workout controls in dense
  panels.
- Rework History into a dashboard-style view for stats history, strength
  analytics, body metrics, and recent sessions.
- Rework Library, Quest, and Import surfaces so playlist building, quest
  scheduling, and CSV/backup workflows are easier to scan.
- Add a watch UI pass for glanceable active/rest/paused screens, consistent
  haptic language, safer end/cancel flows, and better small-screen hierarchy.
- Keep UI components split by feature and extract shared controls only when
  repeated patterns prove stable.

Suggested files:

- `docs/REACT_WEB_APP_INSTRUCTIONS.md`
- `pwa/src/components/`
- `pwa/src/styles.css`
- `android/wear/src/main/kotlin/app/personal/workouttracker/wear/session/SessionScreen.kt`
- Wear list/home screens as needed after audit.

Validation:

```sh
npx tsc --noEmit
npm run build
cd android
./gradlew :app:assembleDebug :wear:assembleDebug
```

Manual acceptance:

1. Smoke test desktop web, narrow mobile PWA, and installed/PWA-like viewport.
2. Confirm Today, active workout, rest, paused/recovered session, History,
   Library, Quests, and Import still work.
3. Verify Wear OS active, rest, paused, completion, and cancel states on an
   emulator or watch-sized preview.
4. Confirm no new tab-level display markup is added directly to `App.tsx`.

## Deferred / Not Recommended By Default

These features are common in other apps but should stay out of the near-term
roadmap unless the app direction changes:

- Social feed, leaderboards, and community routine marketplace: require
  accounts, moderation, and backend infrastructure.
- GPS run/ride tracking: better suited to a cardio-distance app.
- Nutrition tracking: large separate product surface.
- AI/adaptive programming: potentially useful later, but it conflicts with the
  current curated quest model unless introduced very carefully.
