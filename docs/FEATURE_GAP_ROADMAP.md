# Feature Gap Roadmap

Purpose: compare Pasingot's current feature set against established
workout-tracking apps (Strong, Hevy, JEFIT, Fitbod, Strava, Nike Training
Club, Apple Fitness+) and list what's missing, so we can decide what belongs
on the roadmap. This is a working checklist, not a commitment — check the
`Wanted?` column as you review, then we turn the checked rows into staged
plan entries in `docs/WORKOUT_APP_PLAN.md`.

## Current baseline (already built, for reference)

**Sanity-checked against the live code on 2026-09-03** — several rows below
were gaps when this doc was first written and are now done, per
`docs/WORKOUT_APP_PLAN.md` Stages 10-14 (implemented locally, not yet
committed). Cross-references to that plan's stage numbers are included so the
two docs stay traceable to each other.

- Weekly day/time schedule (playlist of exercises: sets, reps, rest, and now
  optional load weight/unit — Stage 12).
- Exercise catalog (wger.de sourced), filterable by level/muscle/
  equipment/category, with source link and license attribution.
- Quests: templated, evidence-informed multi-week progression programs that
  self-schedule into the weekly plan.
- CSV import of the weekly schedule (now including optional load columns —
  Stage 12).
- Live workout player: complete set, skip exercise, **rest countdown that
  auto-starts and auto-advances to the next set/exercise** with +5/+10/+30s
  extensions, pause/resume/restart, end workout, elapsed time, crash/close
  recovery, and per-set reps/load input (Stage 12).
- **Rest-end cues**: haptic, sound, and optional text-to-speech voice
  announcement, each independently toggleable, on both the PWA and Wear OS
  (Stage 14).
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
| | Custom exercise creation | User-defined exercises beyond the wger catalog | Strong, Hevy, JEFIT | Medium | Scoped as **Stage 16 - Exercise Library Expansion** in `docs/WORKOUT_APP_PLAN.md` — proposed, not yet implemented. |
| | Exercise video/GIF demos | Visual form demonstration per exercise | Strong, Hevy, JEFIT, Fitbod | Medium | Also part of **Stage 16** (exercise media fields + attribution) — proposed, not yet implemented. |
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
| ✅ Done | Auto-advancing rest countdown + TTS | Rest starts automatically after a set, counts down, and auto-starts the next set/exercise — no manual "start" tap needed | Strong, Hevy, Nike Training Club, JEFIT | High | **Already implemented** in the current working tree (`pwa/src/App.tsx`, `WorkoutPlayer.tsx`, and mirrored in `SessionViewModel.kt` on Wear OS). Rest is timestamp-based and auto-transitions to `active` when it hits 0 on both phone and watch. The PWA has a haptic/sound/**voice (TTS)** cue at the end of rest, with per-cue toggles already wired into the player UI (`CueToggle` in `WorkoutPlayer.tsx`) — voice defaults **off**. A manual "Start now" button remains to skip the rest early, which matches how Strong/Hevy also keep a skip control alongside auto-advance. See "Polish opportunities" below for gaps worth closing before calling this finished. |
| | Google Health Connect sync | Write workouts/body weight to Android's Health Connect | Most Android fitness apps | Medium | Scoped as **Stage 17 - Health Connect Integration** in `docs/WORKOUT_APP_PLAN.md` — proposed, explicitly gated on Stage 11 (body weight) and marked optional "after local logging is stable." |
| | Heart-rate during workout | Live/avg HR shown per session via the watch | Hevy, Strong, Apple Fitness+ | Medium | Also folded into Stage 17's plan ("consider heart-rate summaries from Wear OS only after session and permission handling are proven reliable") — proposed, not yet implemented. |
| | Calorie burn estimate | Estimated kcal per session | Nearly all major apps | Low–Medium | |
| | Apple Health sync / iOS app | N/A today — Pasingot is Android + Wear OS only | Strong, Hevy, JEFIT, Fitbod | N/A | Would require a full iOS build; flagging for awareness, not a near-term item. |

## Cloud / cross-device

| Wanted? | Feature | What it is | Common in | Priority | Notes |
|---|---|---|---|---|---|
| | Cloud backup / cross-device sync | Optional account that syncs logs across phone/tablet/web | Strong ("Strong Cloud," free), Hevy, JEFIT | Medium | Direct tension with the current no-accounts/offline-first design. Stage 15 (below) explicitly plans local export/import first and defers cloud sync until after that. |
| | Data export beyond CSV schedule | Export full history/logs (not just the weekly schedule) | Strong, Hevy, JEFIT | Medium | Scoped as **Stage 15 - Data Portability** in `docs/WORKOUT_APP_PLAN.md` — proposed: full export/import of schedule, logs, session events, quests, and body metrics, with encrypted backup considered before any cloud sync. |

## Polish opportunities for the rest countdown + TTS feature

The core behavior is done (see above). These are the refinements that would
bring it in line with what Nike Training Club, Peloton, and interval-timer
apps (Seconds Pro) do during a rest period:

| Wanted? | Refinement | What it is | Reference | Priority | Notes |
|---|---|---|---|---|---|
| | Voice on by default | Flip `voiceEnabled` default to `true` (or prompt once on first workout) | — | Medium | Currently defaults off (`initialWorkoutCueSettings`); a user who never opens the cue toggles never hears it. |
| | Announce the upcoming exercise at the *start* of rest, not just the end | "Up next: push-ups" spoken as rest begins, so the user knows what's coming without looking at the screen | Peloton, Nike Training Club | Medium | Today's `playWorkoutCue` only fires once, when rest hits 0 ("Rest complete. Next set: X"). |
| | Spoken/audible final countdown | "3, 2, 1, go" in the last few seconds of rest | Nike Training Club, Seconds Pro, most interval timers | Medium | Cheap addition to the existing 1-second `syncTimers` tick — trigger extra cues at `restSeconds` 3/2/1. |
| | Wear OS voice cue | Speak the same "rest complete" cue on the watch, not just haptic | Some Wear OS fitness apps with speaker support | Low | Depends on whether the target watches have a speaker; haptic-only is a reasonable fallback if not. |
| | Large, glanceable countdown number/ring on the rest screen | Big circular countdown so the phone can be glanced at across the room, not just heard | Strong, Hevy, Nike Training Club | Medium | See the UI/UX section below — this is really a visual-design change to `WorkoutPlayer.tsx`'s resting view. |

## UI/UX design direction, screen by screen

This UI direction has now been folded into `docs/WORKOUT_APP_PLAN.md` as
Stage 18 - Cross-Device UI Overhaul. Treat the rows below as the comparison
checklist for that stage.

Current baseline UI (`AppShell.tsx`, `styles.css`): a single-column,
mobile-first, dark slate/Tailwind theme with a 5-tab **text-only** segmented
bar (Today/Quests/Library/Import/History) under a sticky header, card
sections with thin progress bars. It's functional but plain compared to the
apps below — no icons in nav, no imagery, mostly text and thin flat bars.
This section proposes, screen by screen, borrowing specific patterns from
apps people already know, so the app feels immediately familiar rather than
bespoke. Mark `Wanted?` the same way as the feature rows above.

| Wanted? | Screen | Current UI | Reference pattern | Proposed direction | Priority |
|---|---|---|---|---|---|
| | Global nav (`AppShell`) | Text-only 5-way segmented control | Strong/Hevy/Nike Training Club bottom tab bars: icon + label per tab, active tab in an accent color, a badge dot for "today's quest ready" | Add an icon per tab (simple line icons), keep labels, add a small dot/badge on Today or Quests when there's unstarted work due today | Medium |
| | Global nav | No single obvious "go" action | Strava's prominent orange record button; Nike Training Club's big "Start Workout" CTA | Add one persistent primary action — a floating or header "Start Workout" button that jumps straight into today's plan/player from anywhere | Medium |
| | Installed PWA/mobile shell | Same web layout, only scaled down | Native fitness apps: thumb-reachable bottom nav, clear active-session banner, offline/install state outside the primary workout flow | Tune mobile/PWA layout separately from desktop: larger tap targets, bottom-safe-area spacing, active workout resume banner, and no fragile hover-only affordances | High |
| | Today | Plan list + small progress bar, player opens inline | Hevy/Strong home screen: a single "today's workout" card with a big primary Start/Continue button, a compact stat strip above it (streak, sessions this week) | Redesign Today as one hero card (exercise count, estimated time, Start/Continue button) sitting above the exercise list, with the streak/weekly-count strip from the Stats section above it | High |
| | Workout Player — one screen, two states | Active set (text reps/weight fields, "Complete Set") and resting (small "Rest remaining" text + `+5/+10/+30s`) already live in the same `WorkoutPlayer.tsx` component/screen, switching on `session.status` | Strong/Hevy/Nike Training Club: the exercise name, set count, and screen chrome stay fixed in place; only the central focal element morphs — steppers/Complete Set while active, a large circular countdown ring while resting | **Important:** resting is a *state* of the active-exercise screen, not a separate destination — don't turn it into its own tab/screen when redesigning. Keep the exercise header pinned across both states; swap only the center: numeric steppers + "Complete Set" while active, a large ring countdown while resting. Demote skip/pause/end to a small icon row in both states. | High |
| | Quests | Flat "current day" panel per template | JEFIT/Fitbod program browser: horizontal scrollable program cards with a progress ring per program; Duolingo/Habitica-style vertical path map for day-by-day progression, which fits the existing "Quest" naming well | Show enrolled/available quests as cards with a progress ring (days complete / total), and render the day-by-day sequence as a vertical path/map instead of a plain list, leaning into the game-like "quest" framing already in the copy | Medium |
| | Library | Segmented level filter + flat list rows | Strong/Hevy exercise browser: search bar pinned at top, horizontal scrollable filter chips (muscle group/equipment), a photo/thumbnail per exercise card | Add a search bar above the filters, turn muscle-group/equipment/category into scrollable filter chips alongside the existing level filter, and surface an exercise thumbnail once exercise images are added (see catalog media row above) | Medium |
| | History | Text overview metrics + thin flat bars | Apple Fitness+ "close your rings"; Strava's calendar heatmap + trend line charts | Turn the done/pending/skipped `PlanProgressSummary` bars into activity rings, and add the calendar heatmap + trend charts already proposed in the Stats & analytics section above as the top of the History screen | Medium |
| | Wear OS session screen | Compact Compose chips list (Complete Set, Pause, Skip, etc.) | Google/Samsung Wear fitness complications: the countdown number fills most of the round screen, 1-2 buttons max, everything else swiped away | Make the rest-remaining number the dominant element on the round screen (large centered text or a ring around the bezel), and reduce the resting-state action list to Start now / Pause only, moving Restart/End behind the existing Cancel/Paused flow | Low |

## Out of scope for this app's thesis (listed for completeness, not recommended)

- GPS run/ride tracking, pace, route maps (Strava, Nike Run Club) — this is a
  strength/bodyweight app, not a cardio-distance tracker.
- Nutrition/calorie tracking (JEFIT, Fitbod integrations) — separate product
  surface; likely better left to a dedicated nutrition app.

## Next step

`docs/WORKOUT_APP_PLAN.md` already has proposed stages for several remaining
"Yes" candidates: **Stage 15** (data export/import), **Stage 16** (custom
exercises + exercise media), **Stage 17** (Health Connect/HR), and **Stage
18** (the UI overhaul this doc's screen-by-screen section feeds into). Items
without a stage yet (plate calculator, RPE/RIR, supersets, warm-up
suggestions, body measurements, progress photos, streak-based badges) would
need a new stage once marked `Wanted?`. Keep Stage 18 as the UI overhaul
parent and split approved UI rows into smaller implementation slices,
starting with PWA design tokens and app shell before touching Today, Workout
Player, History, and Wear OS screens.
