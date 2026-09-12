# Feature Gap Roadmap

Purpose: compare Pasingot's current feature set against established
workout-tracking apps (Strong, Hevy, JEFIT, Fitbod, Strava, Nike Training
Club, Apple Fitness+) and list what's missing, so we can decide what belongs
on the roadmap. This is a working checklist, not a commitment — check the
`Wanted?` column as you review, then we turn the checked rows into staged
plan entries in `docs/WORKOUT_APP_PLAN.md`.

## Current baseline (already built, for reference)

**Reviewed against the code on 2026-09-12.** Stage 16 media/custom quests are
committed in `6d97150`; integrity fixes and follow-ups are now in `3ba1bc6`.
Iteration status and
verification are in [Implementation progress](IMPLEMENTATION_PROGRESS.md).

- Weekly day/time schedule (playlist of exercises: sets, reps, rest, and now
  optional load weight/unit — Stage 12).
- Exercise catalog (wger.de sourced), filterable by level/muscle/
  equipment/category, with source link and license attribution.
- Quests: built-in programs and user-authored playlist templates, with an
  explicit action to add the current quest day to the weekly plan. Separate
  enrollments have stable run identity; departed quests retain archived progress.
- CSV import of the weekly schedule (now including optional load columns —
  Stage 12).
- Live workout player: complete set, skip exercise, **rest countdown that
  auto-starts and auto-advances to the next set/exercise** with +5/+10/+30s
  extensions, pause/resume/restart, end workout, elapsed time, crash/close
  recovery, and per-set reps/load input (Stage 12).
- **PWA rest-end cues**: haptic, sound, and optional text-to-speech voice,
  each independently toggleable. Wear OS has haptic feedback on session
  actions; watch rest-end sound/voice is not implemented (Stage 14).
- **Weighted-set logging and strength analytics**: per-set reps/load records,
  personal-record detection (max load, max reps at a load, best estimated
  1RM), and per-exercise trend data (Stage 12-13).
- **Body-weight log**: a separate on-device log (date, weight, unit, note)
  with edit/delete and change-from-last-entry display (Stage 11).
- History: workout logs (done/skipped), quest completion history, session
  summaries (including actual-vs-estimated duration), per-exercise breakdown,
  month/all-time filters, **plus a Stage 10 stats layer**: current/longest
  streaks, an activity calendar/heatmap, weekly and monthly trend bars,
  muscle/category balance, and this-week-vs-last-week /
  this-month-vs-last-month comparisons.
- Local notification when a scheduled workout's start time arrives.
- Native Wear OS companion: downloads today's workout, runs the same live
  session flow (including the auto-advancing rest countdown), syncs logs back
  to the phone over the Wearable Data Layer, offline retry queue.
- Browser/PWA watch setup guidance and native platform/plugin capability checks
  are the Iteration 3 correction. Automatic website → watch transfer remains
  unimplemented; see [feasibility and transport options](BROWSER_WATCH_SYNC.md).
- Fully offline, on-device (IndexedDB), no accounts, no cloud backend.

## How to use this

For each row, mark `Wanted?` with your call: **Yes**, **No**, or **Maybe**
(leave blank if undecided). Priority is my rough guess for a personal,
offline-first, strength+bodyweight app like this one — not a ranking of
importance in general.

## Strength-training core

| Wanted? | Feature | What it is | Common in | Priority | Notes |
|---|---|---|---|---|---|
| ✅ Done | Weight/load per set | Log kg/lb alongside reps, per set | Strong, Hevy, JEFIT, Fitbod (universal) | High | Implemented: `loadWeight`/`loadUnit` on `WorkoutRow`, `PlaylistItem`, and the new `WorkoutSetLog` store (Stage 12, `pwa/src/types.ts`, `pwa/src/lib/db.ts`). |
| ✅ Done | Personal record (PR) tracking | Auto-detect new weight/rep/volume PRs, surface at log time | Hevy, Strong, JEFIT | High | Implemented: `buildStrengthPersonalRecords` in `pwa/src/lib/strength-analytics.ts`, surfaced in History (Stage 13). |
| ✅ Done | 1RM estimate | Estimate one-rep max from submax sets (Epley/Brzycki-style) | JEFIT, Strong, Hevy, Fitbod | Medium | Implemented: `estimatedOneRepMax` in `strength-analytics.ts`, hidden for bodyweight/duration-only logs (Stage 13). |
| | Plate calculator | Suggests plate combo for a target barbell weight | Hevy, Strong, JEFIT | Medium | Still open — explicitly deferred in Stage 13's notes ("Deferred RPE/RIR and plate calculator controls for a later opt-in stage"). |
| | RPE/RIR logging | Optional perceived-exertion rating per set | Hevy, JEFIT | Medium | Still open — same Stage 13 deferral as the plate calculator. |
| | Supersets/circuits | Group exercises with shared or zero rest between them | Hevy, Strong, JEFIT | Medium | Still open — playlist model is currently flat, ordered rows; not part of any proposed stage yet. |
| ✅ Done | Volume/strength trend charts | Per-exercise graph of weight/volume/est. 1RM over time | Strong, Hevy, JEFIT | Medium | Implemented: per-exercise trend bars from daily training volume, in History (Stage 13). Per-exercise only, not an all-up volume chart. |
| ✅ Done | Custom exercise creation | User-defined exercises beyond the wger catalog | Strong, Hevy, JEFIT | Medium | Implemented and committed in Stage 16, including create/edit/delete, Library integration, and backup/restore. |
| 🚧 Partial | Exercise video/GIF demos | Visual form demonstration per exercise | Strong, Hevy, JEFIT, Fitbod | Medium | Stage 16 now bundles 32 reviewed, attributed built-in images for offline use, links 15 attributed upstream videos, and supports optional custom exercise media URLs. Coverage depends on licensed upstream availability; large videos are deliberately not bundled. |
| | Warm-up set suggestions | Auto-suggested ramp-up sets before working sets | Fitbod, JEFIT | Low | Still open, not on a proposed stage yet. |
| | AI/adaptive programming | Auto-builds next session from recovery/history instead of a fixed template | Fitbod, JEFIT | Low | Runs counter to the curated-quest design; likely out of scope. |

## Body tracking

| Wanted? | Feature | What it is | Common in | Priority | Notes |
|---|---|---|---|---|---|
| ✅ Done | Body weight logging | Standalone weight log, independent of sets | Strong, Hevy, JEFIT, Apple Health/Health Connect | High | Implemented: `bodyMetrics` IndexedDB store with date/weight/unit/note, add/edit/delete, and change-from-last-entry in History (Stage 11). |
| | Body measurements | Waist/chest/arms/etc. tracked over time | Strong, JEFIT | Medium | Still open — Stage 11 intentionally scoped to weight only ("measurements later if desired"). |
| | Progress photos | Timestamped photos, often side-by-side compare | Strong, JEFIT | Low–Medium | Still open. Needs an on-device photo storage plan (IndexedDB blob or filesystem); not on a proposed stage yet. |

## Social / gamification

| Wanted? | Feature | What it is | Common in | Priority | Notes |
|---|---|---|---|---|---|
| | Streaks/badges/achievements | Cross-cutting gamification beyond one quest program (login streaks, milestones) | Strava, Nike Run Club, Fitbit/Google Fit | Medium | Complements the existing Quests system without needing accounts. |
| | Follow/social feed | Feed of followed users' workouts, PRs | Hevy | Low | Needs a backend + accounts; conflicts with the offline/single-user design. |
| | Leaderboards | Rank lifts against friends | Hevy | Low | Same account/backend dependency as above. |
| | Community/shared routine templates | Marketplace or shared programs | Hevy, JEFIT, Boostcamp | Low | Same dependency. |

## Stats & analytics history

| Wanted? | Feature | What it is | Common in | Priority | Notes |
|---|---|---|---|---|---|
| ✅ Done | Workout streaks | Current streak / longest streak of on-schedule training days | Strava, Nike Run Club, Fitbit/Google Fit, Hevy | Medium | Implemented: `calculateStreaks` in `pwa/src/lib/history-stats.ts`, shown in History (Stage 10). |
| ✅ Done | Calendar / heatmap view | GitHub-style calendar of which days had a workout, at a glance | Strava, JEFIT, Apple Fitness+ | Medium | Implemented: `ActivityCalendar` component fed by `buildActivityDays`, in History (Stage 10). |
| ✅ Done | Volume/frequency trend charts | Sets/reps/workouts per week or month, plotted over time (not just a single-period snapshot) | Strong, Hevy, JEFIT | Medium | Implemented: `buildWeeklyTrend`/`buildMonthlyTrend` bars in History (Stage 10). |
| ✅ Done | Muscle-group / category balance | Sets or sessions broken down by primary muscle or category over a period, to spot imbalance | JEFIT, Fitbod | Medium | Implemented: `buildBalanceStats`, joining logged exercise names to catalog `primaryMuscles`/`category` (Stage 10). |
| ✅ Done | Period-over-period comparison | "This week vs. last week" style delta on key stats | Strava, JEFIT | Low | Implemented: `buildPeriodComparisons` (this week/last week, this month/last month), in History (Stage 10). |
| ✅ Done | Time-in-training stats | Total/average session duration vs. the app's own estimated duration | Strong, Hevy | Low | Implemented: `formatActualVsEstimated` compares `WorkoutSessionEvent.elapsedSeconds` against `estimatedDurationSeconds`, shown per session in History. |

## Wearable / health-platform integration

| Wanted? | Feature | What it is | Common in | Priority | Notes |
|---|---|---|---|---|---|
| ✅ Done | Auto-advancing rest countdown + TTS | Rest starts automatically after a set, counts down, and auto-starts the next set/exercise — no manual "start" tap needed | Strong, Hevy, Nike Training Club, JEFIT | High | **Implemented and committed** in `WorkoutPlayer.tsx` and `SessionViewModel.kt`. Rest is timestamp-based and auto-transitions to `active` when it hits 0 on both phone and watch. The PWA has independently configurable haptic, sound, and voice cues; voice defaults **off**. A manual "Start now" action remains for skipping rest early. See "Polish opportunities" below for optional refinements. |
| 🚧 In progress | Google Health Connect sync | Write workouts/body weight to Android's Health Connect | Most Android fitness apps | Medium | Workout and body-weight add/update/delete implementation is committed. Iteration 2 serializes retries and mutations and persists requests before native calls; physical-device validation is pending. Heart-rate summaries remain deferred. |
| | Heart-rate during workout | Live/avg HR shown per session via the watch | Hevy, Strong, Apple Fitness+ | Medium | Still open. Stage 17 now has workout-session and body-weight writes, but no Wear OS heart-rate capture or summary model. |
| | Calorie burn estimate | Estimated kcal per session | Nearly all major apps | Low–Medium | |
| | Apple Health sync / iOS app | N/A today — Pasingot is Android + Wear OS only | Strong, Hevy, JEFIT, Fitbod | N/A | Would require a full iOS build; flagging for awareness, not a near-term item. |

## Cloud / cross-device

| Wanted? | Feature | What it is | Common in | Priority | Notes |
|---|---|---|---|---|---|
| | Cloud backup / cross-device sync | Optional account that syncs logs across phone/tablet/web | Strong ("Strong Cloud," free), Hevy, JEFIT | Medium | Direct tension with the current no-accounts/offline-first design. Stage 15 completed local export/import; account-based cloud sync remains intentionally deferred. |
| Under review | Website/PWA → Wear OS sync | Send browser-planned workouts and receive watch history without the Pasingot phone app | Product-specific capability | Pending choice | Feasible via new authenticated HTTPS service/watch client; not supplied by PWA installation or the existing Wear bridge. BLE is experimental, device/browser-dependent. See `BROWSER_WATCH_SYNC.md`; no transport selected or implemented. |
| ✅ Done | Data export beyond CSV schedule | Export full history/logs (not just the weekly schedule) | Strong, Hevy, JEFIT | Medium | Stage 15 is committed: full local JSON export/restore covers schedule, logs, session events, set logs, body metrics, custom exercises, quests, settings, and active app state. |

## Polish opportunities for the rest countdown + TTS feature

The core behavior is done (see above). These are the refinements that would
bring it in line with what Nike Training Club, Peloton, and interval-timer
apps (Seconds Pro) do during a rest period:

| Wanted? | Refinement | What it is | Reference | Priority | Notes |
|---|---|---|---|---|---|
| | Voice on by default | Flip `voiceEnabled` default to `true` (or prompt once on first workout) | — | Medium | Currently defaults off (`initialWorkoutCueSettings`); a user who never opens the cue toggles never hears it. |
| | Announce the upcoming exercise at the *start* of rest, not just the end | "Up next: push-ups" spoken as rest begins, so the user knows what's coming without looking at the screen | Peloton, Nike Training Club | Medium | Today's `playWorkoutCue` only fires once, when rest hits 0 ("Rest complete. Next set: X"). |
| | Spoken/audible final countdown | "3, 2, 1, go" in the last few seconds of rest | Nike Training Club, Seconds Pro, most interval timers | Medium | Cheap addition to the existing 1-second `syncTimers` tick — trigger extra cues at `restSeconds` 3/2/1. |
| | Wear OS voice cue | Speak the same "rest complete" cue on the watch, not just haptic | Some Wear OS fitness apps with speaker support | Low | Not implemented. Current watch feedback is action haptics via `rememberCueAction`; PWA voice settings do not apply to the watch. This remains an optional candidate. |
| | Large, glanceable countdown number/ring on the rest screen | Big circular countdown so the phone can be glanced at across the room, not just heard | Strong, Hevy, Nike Training Club | Medium | See the UI/UX section below — this is really a visual-design change to `WorkoutPlayer.tsx`'s resting view. |

## UI/UX design direction, screen by screen

This UI direction has now been folded into `docs/WORKOUT_APP_PLAN.md` as
Stage 18 - Cross-Device UI Overhaul. Treat the rows below as the comparison
checklist for that stage.

Current baseline UI (`AppShell.tsx`, `styles.css`): Stage 18's first slice now
provides shared shell tokens, icon-and-label navigation, ready badges, a
persistent workout action, status/install affordances, and phone bottom
navigation. Local feature-screen redesigns now extend this foundation. The table below
retains the original targets; full browser and device acceptance is still open.

| Wanted? | Screen | Current UI | Reference pattern | Proposed direction | Priority |
|---|---|---|---|---|---|
| ✅ Done | Global nav (`AppShell`) | Responsive Lucide icon-and-label tabs with active state and Today/Quest ready badges | Strong/Hevy/Nike Training Club bottom tab bars | Completed in Stage 18's first shell slice | Medium |
| ✅ Done | Global nav | Persistent Start/Continue action in the sticky header | Strava's prominent record button; Nike Training Club's Start Workout CTA | Completed in Stage 18's first shell slice | Medium |
| 🚧 Partial | Installed PWA/mobile shell | Thumb-reachable bottom nav, safe-area spacing, active-session banner, online/Health/install/reminder status | Native fitness apps | Shell behavior is implemented; feature screens still need mobile-specific visual passes | High |
| 🚧 Partial | Today | Local hero, explicit status counts, queue filters, weekly schedule preview, and empty-state actions; player remains inline | Hevy/Strong home screen: a single "today's workout" card with a big primary Start/Continue button, a compact stat strip above it (streak, sessions this week) | Redesign Today as one hero card (exercise count, estimated time, Start/Continue button) sitting above the exercise list, with the streak/weekly-count strip from the Stats section above it | High |
| ✅ Implemented | Workout Player — one screen, two states | Active set inputs and a large rest countdown ring share the same exercise header, with rest extensions and compact secondary actions | Strong/Hevy/Nike Training Club | Preserve active/rest as states of one player; finish physical-device cue/readability acceptance | High |
| 🚧 Partial | Quests | Program cards, active progress ring, current-day exercise path, completion history, and archived runs | JEFIT/Fitbod program browser and day-by-day path | Current-day path is implemented; a map of every program day remains a design candidate | Medium |
| 🚧 Partial | Library | Search, level/category filters, exercise cards and attributed thumbnails | Strong/Hevy exercise browser | Dedicated muscle/equipment filter chips and mobile polish remain candidates; text search already matches those fields | Medium |
| ✅ Implemented | History | Activity rings, calendar heatmap, trends, body metrics, strength records, and archived quests | Apple Fitness+/Strava | Core planned panels are implemented; physical-device readability remains part of acceptance | Medium |
| 🚧 Partial | Wear OS session screen | Compact Compose chips list (Complete Set, Pause, Skip, etc.) | Google/Samsung Wear fitness complications: the countdown number fills most of the round screen, 1-2 buttons max, everything else swiped away | Make the rest-remaining number the dominant element on the round screen (large centered text or a ring around the bezel), and reduce the resting-state action list to Start now / Pause only, moving Restart/End behind the existing Cancel/Paused flow | Low |

## Out of scope for this app's thesis (listed for completeness, not recommended)

- GPS run/ride tracking, pace, route maps (Strava, Nike Run Club) — this is a
  strength/bodyweight app, not a cardio-distance tracker.
- Nutrition/calorie tracking (JEFIT, Fitbod integrations) — separate product
  surface; likely better left to a dedicated nutrition app.

## Next step

The Stage 18 shell and Stage 17 body-weight sync are committed in `b7f251f`.
Stage 18 feature screens and weekly planning are committed in `4f5da66`, and
the Wear session redesign/manual sync in `0a8bcf6`. Today additionally has
weekly schedule previews grouped by time, pending/done/skipped queue filters,
and empty-state navigation. These are implemented changes, not a claim that
all screen-by-screen acceptance criteria above are complete.

1. Review the verified local follow-up to `6a75c06`. Iteration 2 is complete
   locally, with regression, UI, and build evidence in the progress log.
2. Finish physical-device recovery and Health Connect mutation/retry checks.
3. Complete paired phone/watch delivery and physical battery evidence.
4. Choose the next feature independently: RPE/RIR, plate calculator, supersets,
   warm-up suggestions, or body measurements. These remain candidates.
5. Continue Stage 14B's remaining hook extraction as maintenance; its first
   cleanup slice did not achieve the whole composition-root goal.

Use the delivery board in `WORKOUT_APP_PLAN.md` for current, pending, next,
and future work. Earlier comparison rows describe the original design target;
the delivery board and stage status distinguish implementation from validation.

## 2026-09-10 reconciliation

PWA feature-screen and weekly plan work is committed in `4f5da66`; the watch
redesign/manual sync followed in `0a8bcf6`. Current local fixes address watch
battery use, missing live session sync, queued history retries, and custom
exercise names/time labels; see the delivery board in `WORKOUT_APP_PLAN.md`.
Actual battery consumption, Health Connect, and paired sync still need physical
validation. At that checkpoint, Stage 16 media and custom-exercise quest
authoring/reference safeguards remained unimplemented. Do not treat
all comparison-table suggestions as approved requirements.

The 2026-09-10 watch pass now has round-emulator evidence for active/rest/paused
layouts, process-restart recovery, end confirmation, and schedule adjustment.
Physical and paired-device acceptance remains open; no second emulator was run.

## 2026-09-11 Stage 16 follow-up

The two pending Stage 16 implementation items are now complete locally. The
catalog generator verifies per-media Creative Commons attribution, bundles the
reviewed still images, generates an offline cache manifest, and retains
attributed video links. The Library renders author/license details. Users can
also create a reusable quest from the current Library playlist, including
custom exercises and planned loads. Stable exercise IDs, rename aliases, and
cross-store reference checks prevent referenced custom exercises or quest
templates from being deleted. See the delivery board and validation evidence in
`WORKOUT_APP_PLAN.md`.

## 2026-09-12 integrity review

The review found gaps in quest run isolation/history retention, restored-data
validation, atomic backup replacement, local-date progress, delayed watch-log
reconciliation, Health Connect queue ordering, and CSV replacement/native IDs.
Corrections and per-iteration completion evidence are maintained in
`IMPLEMENTATION_PROGRESS.md`. This review does not promote the unchecked future
feature candidates into committed scope.
