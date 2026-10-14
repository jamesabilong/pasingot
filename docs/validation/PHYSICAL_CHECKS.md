# Physical validation module

Current direction — 2026-10-13, Iteration 185: **deferred by user**. Resume
this module when physical checks are requested. Independent implementation
continues without connecting devices or changing their state. Code/browser/JVM
checks and physical acceptance remain separate.

Stage 19 baseline: **87/97 overall**, code Phases 0–3 **70/70**, physical
Phase 4 **17/27**. The ten open criteria below retain their original numbers
from [Quick Start Phase 4](../WATCH_QUICK_START_PLAN.md#phase-4--paired-device-acceptance).
Organizing them does not close any criterion. Historical observations and failed
attempts remain in [Implementation progress](../IMPLEMENTATION_PROGRESS.md)
and [Device acceptance](../DEVICE_ACCEPTANCE.md).

## Entry and evidence

Use a charged, available phone/watch pair and current explicit serials. Confirm
models, OS/API, app versions, installed APK identities, nearby reciprocal peers,
audio route and battery. Record the Git checkpoint and local changes. Use a new
evidence directory per run; do not overwrite earlier failures.

Read-only inventory, when this module is resumed:

```sh
python3 scripts/collect_physical_validation.py \
  --adb "$ANDROID_HOME/platform-tools/adb" \
  --phone-serial "$PHONE_SERIAL" --watch-serial "$WATCH_SERIAL" \
  --include-installed-hash \
  --output "output/device-validation/$RUN_ID/inventory"
```

The collector records inventory only; it does not establish pairing or
acceptance. Local-build comparison uses default APK output paths; compare the
specific tested artifacts separately if build outputs were isolated. Read-only
pairing readiness is available through `scripts/paired_preflight.py --help`.
Run neither tool during this deferral.

Before mutations, capture phone IndexedDB/native records, watch legacy workout
stores/settings, and current pending/active Quick Start state. Identify owned
test requests. Preserve personal history, workout state and preferences. Use
normal app controls, actual clock passage, and exact result/receipt binding;
synthetic receipts or private-store edits cannot close physical criteria.

Each check records: ID, tested source/build, device inventory, owned request
and prescription, steps, timestamped artifacts, human observations where
required, result (`pending`, `pass`, `fail`, `inconclusive`), limitations,
cleanup/restoration evidence, and next action. Update the original Phase 4 box
and progress log only after all required evidence passes.

## Transport and retained results

- [ ] **QS-07 — Disconnected wording.** Disconnect the actual phone/watch
  transport, including any cloud route. Send through normal phone UI; require
  actionable wording and absence of a stale Ready package/session. A radio-off
  probe that retains a route is inconclusive (Iteration 170). Restore original
  connectivity and compare preserved data.
- [ ] **QS-12 — Unsynced terminal-result durability.** Complete an owned
  workout offline, compare exact terminal result across watch process death and
  real reboot, then reconnect. Require actual phone import and matching sender,
  request/revision receipt before runtime/package/cue pruning. Reboot retention
  and acknowledgement remain unverified in Iteration 184.

**Resume interrupted Iteration 184 before starting a new test:** last owned
offer was `3a5e4739`; obtain its full identity from saved evidence rather than
using this prefix for matching. At the last checkpoint, phone Pasingot was
force-stopped; its latest native offer was Ready with no final result/receipt.
Watch debugging became unreachable after the offline helper. Component-disable
attempts were rejected by Android and settings stayed default. These are
historical states, not a current-device inventory.

1. Reconnect the verified Watch7 using its current endpoint and identity.
2. Read scoped offline helper captures and current radio/process/store state.
   Verify restoration; do not rerun the helper or start another request first.
3. If a terminal result exists, compare process-restart captures, then perform
   actual reboot retention while the phone cannot acknowledge it.
4. Reopen phone normally, reconnect, require exact import/receipt/pruning and
   original-data comparison. If no result exists, retain the interrupted run
   and use a fresh owned test. Do not infer completion from Ready or helper time.

## Cues, fallback and accessibility

- [ ] **QS-19 — Success does not replay.** Complete exercise/final success,
  navigate/recompose/restart normally and compare durable cue ownership. Require
  human confirmation of no repeated speech or physical haptics, exact results
  and cleanup.
- [ ] **QS-20 — TTS fallback.** Exercise unavailable/failed TTS on an owned
  case; observe complete prescription/countdown/progress, physical haptics and
  successful session completion. Restore the original engine/preferences.
- [ ] **QS-21 — Short-rest matrix.** Human-observe 0/5/8/12-second rests and
  cancelled briefing: 0 has no rest/warning/second Go, 5 and 8 have warning/Go
  without rest speech, 12 has one rest/warning/Go. Require no overlap, stale
  fallback or replay, exact receipts, phone media resumption and service cleanup.
  Iterations 182–183 scripted cases pass, but the human no-overlap reports are
  pending; repeat only the cases whose observations are unavailable.
- [ ] **QS-22 — Routes/TalkBack/ambient.** Cover speaker and Bluetooth route
  changes, TalkBack, ambient/screen-off recovery, and intentional Home exit.
  Compare exact deadlines/progress and hear no duplicate/stale speech. Paired
  Quick Start speech uses the foreground-armed bounded phone media service;
  watch owns timers/haptics and disconnected fallback. Legacy workouts keep
  local speech. Iteration 178's headset/YouTube pass is partial evidence for
  this composite criterion, not its closure.

## Battery, delivery and regression

- [ ] **QS-23 — Measured battery.** Run a comparable 60–90 minute physical
  watch workout off charger. Record duration, initial/final battery, settings,
  route, interaction frequency and relevant diagnostics. Inspect for wake
  locks, continuous polling and per-second persistence. Interrupted debugging
  battery readings in Iteration 183 do not satisfy this check.
- [ ] **QS-24 — Play and mixed versions.** Follow
  [Play internal testing](../PLAY_INTERNAL_TESTING.md): actual internal-track
  install/update, installed versions/signatures, data preservation and compatible
  versus unsupported capability gating. No store upload or release is recorded.
- [ ] **QS-25 — Existing features.** Repeat full-day phone send, manual and
  scheduled watch-requested download, completion logs, live status, offline
  history retry and Health Connect integration. Confirm exactly-once import
  and retained unrelated data.
- [ ] **QS-27 — Final closure.** Close code completion and physical acceptance
  explicitly and separately after all relevant checks pass. Do not relabel
  17/27 as fully accepted because automated checks pass.

## Phone feature checks, outside the Stage 19 count

These deferred checks do not increase the ten Stage 19 items above. Use the
same inventory/evidence protocol on the installed Android app.

- [ ] **PHONE-01 — Plate equipment.** Calculate kg/lb and closest lower
  allocations; save/reset separate unit presets, reopen, interleave calculator
  saves and backup/restore without losing the other unit. Target stays temporary.
- [ ] **PHONE-02 — Set effort/history.** Enter optional RPE/RIR, pause/reopen,
  complete once, inspect rated and unrated/duration sets, search and page beyond
  twenty rows. Check backup/restore and unchanged historical entries.
- [ ] **PHONE-03 — Weekly editors.** Move/remove one complete recurring
  non-quest session, preserve unrelated rows/history, verify day-switch editor
  scope, stale/active/collision rejection and failed-cache retry without replay.
- [ ] **PHONE-04 — Date-specific schedules (Iteration 185).** Move, skip and
  clear one occurrence from Today → Weekly plan → Change one date. Verify source
  and destination dates, following week's recurrence, recorded-date/active/stale/
  collision rejection, app restart and backup/restore. Compare actual Today
  Quick Start, full-day Send and watch-requested download against the resolved
  native schedule. No physical pass is claimed by browser/JVM projection tests.
- [ ] **PHONE-05 — Health Connect/portability.** Grant/revoke permissions,
  workout completion/body-weight add/edit/delete, offline retry and sync-off
  behavior; inspect provider records for duplicates and retained IDs. Verify
  actual Android backup save-file, restore and retained preferences/history.

- [ ] **PHONE-06 — Body measurements (Iteration 186).** Upgrade with existing
  weight/history data; log waist/chest/hips and left/right arm/thigh records in
  cm/in. Verify mixed-unit change, edit/cancel/confirmed delete, site and month/
  all-time filters, more than twenty entries, offline save and process restart.
  Export/restore through the native file picker, including an older backup
  without measurement records. Confirm original body-weight records and Health
  Connect behavior, and stale editor rejection after another tab or restore.
  Browser/packaged-asset evidence does not close installed-device acceptance.

- [ ] **PHONE-07 — Warm-up previews (Iteration 187).** For loaded rep-based
  Library playlist items and the first phone-player set, inspect/edit the
  percentage/rep example, equipment minimum and load increment in kg/lb.
  Verify rounding/empty states, prescription-change reset, offline use and
  readable controls. Pause/resume keeps the current preview; subsequent working
  sets hide it. Confirm preview edits create no history/schedule changes and
  Complete set still records only the prescribed working set. Suggestions are
  temporary previews; they are not appended to watch or phone workout plans.

- [ ] **PHONE-08 — Library filters (Iteration 188).** On the installed phone,
  combine primary-muscle/equipment selectors with search, category, level and
  common movements. Verify custom metadata labels after create/edit/delete and
  backup restore, no fallback from blank equipment to bodyweight, empty results,
  Clear preserving level/playlist and tab-return retention. Check long labels on
  the actual screen and offline/reopen behavior; filters reset on a fresh app
  load. Confirm original workout/history data and Quick Start selection remain.
  Browser smoke and APK asset identity do not close device acceptance.

- [ ] **PHONE-09 — Supersets/circuits (Iteration 189).** Upgrade using the tested
  phone APK while preserving existing flat schedules/history and Library filters.
  Create two- and three-member adjacent groups; update equal rounds/shared rest,
  reorder whole blocks, remove down to a singleton and ungroup safely. Save into
  an empty slot and repeat without duplicates; verify collision/active-workout
  rejection. Run A1/B1/A2/B2 with no intra-round rest, shared round/block rest and
  no final rest. Skip the final member and verify rest between remaining rounds.
  Pause, reopen after process death and reload offline; compare exact cursor,
  rep/duration targets, actual/planned kg/lb loads, effort, set numbers and one
  exercise/session completion. Export/restore version 2 through the Android file
  picker; confirm valid legacy version 1 data still restores and an older app
  rejects a grouped version 2 backup without mutation. Inspect manual full-day
  Send and Quick Start rejection, plus no new payload for a watch-requested grouped
  day. Manual Download Now may time out; scheduled requests retain the existing
  cache after message acceptance. Previously downloaded workouts remain cached under
  prior semantics and are not evidence of grouped execution. Grouped quest
  authoring was unavailable in Iteration 189 and is superseded by PHONE-10
  on the newer Iteration 190 build. Confirm unrelated data and Health Connect
  behavior. Browser/JVM/APK checks do not establish installed-device acceptance;
  the Iteration 189 Wear cache blocker is historical and superseded by passing
  Iteration 190 Wear build/JVM checks on macOS. This adds no device acceptance.

- [ ] **PHONE-10 — Grouped quest templates (Iteration 190).** Upgrade while
  preserving existing quests, schedules, history and Library groups. Create a
  quest from a mixed superset/circuit playlist; verify queued edits, ordered
  members, equal rounds, shared rest and individual rep/duration/kg/lb targets.
  Enroll and schedule the complete day; repeat without duplicates, reject occupied
  slots and active players, and verify atomic replacement for the next completed
  day. Execute exact round order, pause/process restart/offline recovery and skips;
  verify individual set logs and once-only quest progress. Leave/archive without
  losing history or unrelated rows. Export/restore a template-only version 2
  backup through the Android file picker; reject malformed groups and older
  wrappers lacking `questCircuits` before store changes. Verify phone cache
  retains markers and grouped watch transfers remain guarded. Automated browser,
  native and packaged-asset checks do not close installed-device acceptance.

## Resume order

Inventory and interrupted QS-12 recovery first; then QS-07/19/20/21/22 and
phone feature checks. Run regression and measured battery on the final tested
build. Finish actual Play delivery/mixed-version checks before QS-27 closure.
When implementation changes, rerun the affected physical module and identify
the tested build; retain previous evidence as historical.
