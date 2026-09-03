# Feature Gap Roadmap

Purpose: compare Pasingot's current feature set against established
workout-tracking apps (Strong, Hevy, JEFIT, Fitbod, Strava, Nike Training
Club, Apple Fitness+) and list what's missing, so we can decide what belongs
on the roadmap. This is a working checklist, not a commitment — check the
`Wanted?` column as you review, then we turn the checked rows into staged
plan entries in `docs/WORKOUT_APP_PLAN.md`.

## Current baseline (already built, for reference)

- Weekly day/time schedule (playlist of exercises: sets, reps, rest).
- Exercise catalog (wger.de sourced), filterable by level/muscle/
  equipment/category, with source link and license attribution.
- Quests: templated, evidence-informed multi-week progression programs that
  self-schedule into the weekly plan.
- CSV import of the weekly schedule.
- Live workout player: complete set, skip exercise, rest countdown with
  +5/+10/+30s, pause/resume/restart, end workout, elapsed time, crash/close
  recovery.
- History: workout logs (done/skipped), quest completion history, session
  summaries, per-exercise breakdown, month/all-time filters.
- Local notification when a scheduled workout's start time arrives.
- Native Wear OS companion: downloads today's workout, runs the same live
  session flow, syncs logs back to the phone over the Wearable Data Layer,
  offline retry queue.
- Fully offline, on-device (IndexedDB), no accounts, no cloud backend.

## How to use this

For each row, mark `Wanted?` with your call: **Yes**, **No**, or **Maybe**
(leave blank if undecided). Priority is my rough guess for a personal,
offline-first, strength+bodyweight app like this one — not a ranking of
importance in general.

## Strength-training core

| Wanted? | Feature | What it is | Common in | Priority | Notes |
|---|---|---|---|---|---|
| | Weight/load per set | Log kg/lb alongside reps, per set | Strong, Hevy, JEFIT, Fitbod (universal) | High | Biggest structural gap — `WorkoutRow`/logs have no weight field at all today. |
| | Personal record (PR) tracking | Auto-detect new weight/rep/volume PRs, surface at log time | Hevy, Strong, JEFIT | High | Depends on weight logging existing first. |
| | 1RM estimate | Estimate one-rep max from submax sets (Epley/Brzycki) | JEFIT, Strong, Hevy, Fitbod | Medium | Depends on weight logging. |
| | Plate calculator | Suggests plate combo for a target barbell weight | Hevy, Strong, JEFIT | Medium | Depends on weight logging. |
| | RPE/RIR logging | Optional perceived-exertion rating per set | Hevy, JEFIT | Medium | |
| | Supersets/circuits | Group exercises with shared or zero rest between them | Hevy, Strong, JEFIT | Medium | Playlist model is currently flat, ordered rows. |
| | Volume/strength trend charts | Per-exercise graph of weight/volume/est. 1RM over time | Strong, Hevy, JEFIT | Medium | Depends on weight logging + History. |
| | Custom exercise creation | User-defined exercises beyond the wger catalog | Strong, Hevy, JEFIT | Medium | |
| | Exercise video/GIF demos | Visual form demonstration per exercise | Strong, Hevy, JEFIT, Fitbod | Medium | wger has images for many entries; catalog type doesn't carry an image field yet. |
| | Warm-up set suggestions | Auto-suggested ramp-up sets before working sets | Fitbod, JEFIT | Low | |
| | AI/adaptive programming | Auto-builds next session from recovery/history instead of a fixed template | Fitbod, JEFIT | Low | Runs counter to the curated-quest design; likely out of scope. |

## Body tracking

| Wanted? | Feature | What it is | Common in | Priority | Notes |
|---|---|---|---|---|---|
| | Body weight logging | Standalone weight log, independent of sets | Strong, Hevy, JEFIT, Apple Health/Health Connect | High | No body-metrics store exists today. |
| | Body measurements | Waist/chest/arms/etc. tracked over time | Strong, JEFIT | Medium | |
| | Progress photos | Timestamped photos, often side-by-side compare | Strong, JEFIT | Low–Medium | Needs on-device photo storage plan (IndexedDB blob or filesystem). |

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
| | Workout streaks | Current streak / longest streak of on-schedule training days | Strava, Nike Run Club, Fitbit/Google Fit, Hevy | Medium | No streak concept exists today; History only shows month/all-time snapshot totals, not a running streak. |
| | Calendar / heatmap view | GitHub-style calendar of which days had a workout, at a glance | Strava, JEFIT, Apple Fitness+ | Medium | `WorkoutLog` already has per-day dates — this is mostly a rendering feature, no new data needed. |
| | Volume/frequency trend charts | Sets/reps/workouts per week or month, plotted over time (not just a single-period snapshot) | Strong, Hevy, JEFIT | Medium | Current History filters (month/all-time) show one period's totals, not a trend line across periods. A basic version (sets or sessions per week) doesn't need weight data; a full volume trend does. |
| | Muscle-group / category balance | Sets or sessions broken down by primary muscle or category over a period, to spot imbalance | JEFIT, Fitbod | Medium | Buildable now — `ExerciseCatalogItem.primaryMuscles`/`category` already exist and are just unused for aggregation in History. |
| | Period-over-period comparison | "This week vs. last week" style delta on key stats | Strava, JEFIT | Low | |
| | Time-in-training stats | Total/average session duration vs. the app's own estimated duration | Strong, Hevy | Low | The estimated-duration calculation already exists for playlist building; History doesn't yet compare estimate vs. actual elapsed time (which `WorkoutSessionEvent.elapsedSeconds` already records). |

## Wearable / health-platform integration

| Wanted? | Feature | What it is | Common in | Priority | Notes |
|---|---|---|---|---|---|
| | Rest-timer sound/vibration on phone | Audible/haptic alert when rest ends in the PWA/app | Universal | High | Confirmed gap: phone-side player has no `Audio`/vibration cue anywhere; only a scheduled-start-time browser `Notification`. Wear OS already vibrates. |
| | Google Health Connect sync | Write workouts/body weight to Android's Health Connect | Most Android fitness apps | Medium | Android-native, no third-party account needed — fits the offline-first model well. |
| | Heart-rate during workout | Live/avg HR shown per session via the watch | Hevy, Strong, Apple Fitness+ | Medium | Natural extension since a native Wear OS app already exists. |
| | Calorie burn estimate | Estimated kcal per session | Nearly all major apps | Low–Medium | |
| | Apple Health sync / iOS app | N/A today — Pasingot is Android + Wear OS only | Strong, Hevy, JEFIT, Fitbod | N/A | Would require a full iOS build; flagging for awareness, not a near-term item. |

## Cloud / cross-device

| Wanted? | Feature | What it is | Common in | Priority | Notes |
|---|---|---|---|---|---|
| | Cloud backup / cross-device sync | Optional account that syncs logs across phone/tablet/web | Strong ("Strong Cloud," free), Hevy, JEFIT | Medium | Direct tension with the current no-accounts/offline-first design — consider a manual encrypted export/import file instead of full accounts, if the goal is just "don't lose data." |
| | Data export beyond CSV schedule | Export full history/logs (not just the weekly schedule) | Strong, Hevy, JEFIT | Medium | CSV import exists for the schedule; there's no export of logs/history today. |

## Voice / audio

| Wanted? | Feature | What it is | Common in | Priority | Notes |
|---|---|---|---|---|---|
| | Voice coaching / audio cues | Spoken rep/rest/set cues during a session | Nike Run Club, Apple Fitness+ | Medium | Fits the existing live player and Wear OS session flow without needing accounts. |

## Out of scope for this app's thesis (listed for completeness, not recommended)

- GPS run/ride tracking, pace, route maps (Strava, Nike Run Club) — this is a
  strength/bodyweight app, not a cardio-distance tracker.
- Nutrition/calorie tracking (JEFIT, Fitbod integrations) — separate product
  surface; likely better left to a dedicated nutrition app.

## Next step

Once you've marked `Wanted?`, I'll fold the "Yes" rows into
`docs/WORKOUT_APP_PLAN.md` as new staged plan entries (data model changes
first, e.g. adding weight to `WorkoutRow`/`WorkoutLog`, since several other
rows depend on it).
