# Workout App Plan

This is the tracked plan for the personal Workout Tracker app. Keep this file
updated at each checkpoint so the plan is visible from every device.

## Current State

- Branch: `PST01`
- Node toolchain: Node 22 for Capacitor/Android commands; `.nvmrc` tracks this.
- Stage 1: complete and committed with the React migration branch history.
- Stage 2: complete and committed as `2bc87c6 PST01: Migrate to react`.
- Stage 3: complete and committed as `f832f04 PST01: Redesign exercise library`.
- Active checkpoint: Stage 4 ready for review.
- Local commit state: Stage 4 is implemented locally but not committed.
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

**Status:** ready for review.

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

Checkpoint 4: stop for browser and data review before committing.

Suggested commit: `feat(pwa): add daily workout quests`.

## Stage 5 - Workout and Quest History

**Status:** pending.

Goal: replace the small summary-only History panel with a proper history page
containing overview metrics, recent workout entries grouped by date, and quest
progress/completion history. Month/all-time filters remain available.

Checkpoint 5: stop for history calculations and mobile-layout review before
committing.

Suggested commit: `feat(pwa): add workout and quest history`.

## Stage 6 - Capacitor Packaging and Device Check

**Status:** pending.

Goal: verify all approved source commits package into Android. This is a test
checkpoint, not a source commit, unless the device check exposes a source bug.

Checks:

```sh
npm run cap:sync
cd android
./gradlew :app:assembleDebug
```

Expected APK:

`android/app/build/outputs/apk/debug/app-debug.apk`
