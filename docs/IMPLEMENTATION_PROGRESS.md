# Implementation progress

This is the durable iteration log for implementation reviews and fixes. Update
the current iteration as work proceeds and close it only after its relevant
checks pass. Link new iterations from `WORKOUT_APP_PLAN.md`.

For each iteration, record: date, starting checkpoint, findings, changes,
verification commands/results, completed work, remaining work, and the next
action. Keep implementation completion separate from device acceptance. Do not
rewrite old evidence as if a newer fix had already been tested at that time.

## Iteration 1 — 2026-09-11 — Stage 16 follow-up

Status: **Completed locally**, subsequently committed in `6d97150`.

Starting checkpoint: `e06dfc6`.

Completed:

- Added 32 offline exercise images, 15 attributed video links, per-media license
  metadata, and an offline media manifest for the 58-exercise catalog.
- Added custom quests from Library playlists and custom-exercise reference
  safeguards, preserving prescriptions, planned loads, and source IDs.
- Repaired the Gradle launchers' empty classpath argument.
- Updated the staged plan and feature roadmap.

Verification: TypeScript, production web build, Capacitor sync, shared/Wear/phone
unit tests, both debug APK builds, 10 custom-quest checks, 6 workout-session
checks, 9 watch-sync checks, and browser media/quest-form inspection passed.

Remaining: physical battery, paired transfer, Health Connect mutations and
retries, and interruption/reboot acceptance. Later review found additional
integrity gaps; those are tracked in Iteration 2, not hidden in this checkpoint.

## Iteration 2 — 2026-09-12 — Data integrity and plan reconciliation

Status: **Completed locally — functional corrections and verification passed**.

Starting checkpoint: `6d97150`. Initial fixes are now in `6a75c06`; a commit by
itself is not evidence that this iteration's acceptance checks have passed.

Findings and changes implemented:

- Quest completion could update an unrelated active quest or a previous run.
  Added run identity, scoped completion, and atomic reconciliation.
- Leaving a quest discarded completion history. Added archived progress with a
  template snapshot, History rendering, and removal of that run's schedule.
- Missing exercises could silently reduce a quest day. Resolution now blocks
  the entire day, and restored definitions require complete, valid day sequences.
- Wrong-type custom quest fields could throw during loading. Added runtime
  validation and bounds matching the authoring form.
- UTC date-prefix comparisons disagreed with local Today/History dates. Added
  local-date status resolution and midnight/foreground refresh.
- Backup restore could commit only some stores. Added record validation and a
  single transaction across user stores, including rollback on synchronous errors.
- Health Connect retries could erase newer queued mutations. Serialized retries,
  writes, and deletes; persisted mutations before native calls; refreshed restored
  sync preferences and checked persisted opt-out before writes.
- Invalid/empty CSV imports could erase schedules, and native sync received rows
  without their assigned database IDs. Added validation, replacement confirmation,
  active-workout protection, and synchronization of persisted rows.
- Custom-exercise name repair could overwrite an existing stable identity.
  Backfill now applies only when identity is absent.

Verification:

- `npx tsc --noEmit`: passed on the final implementation.
- `/tests/data-integrity.html`: all 35 checks passed in the browser using an
  isolated real IndexedDB database. Covers malformed data, quest isolation,
  local dates, archive/restore, rollback, and overlapping native retries.
- Existing fixtures passed: 10 custom-quest, 6 workout-session, and 9 watch-sync
  checks. The watch fixture's deliberately failed ACK is expected.
- Isolated browser workflow passed: archived progress in History, custom quest
  enrollment, scheduling with 8 kg planned load, completing a set, Today at
  100%, and the quest at 1/1 completed days.
- Final scheduled-day level/time controls were confirmed disabled after saving.
- `npm run cap:sync`: production build and Android asset synchronization passed.
- `gradlew.bat :shared:test :wear:testDebugUnitTest :app:testDebugUnitTest
  :app:assembleDebug :wear:assembleDebug`: passed. Existing native tests were
  up to date; no native source was changed. Both debug APK targets built.
- `git diff --check` and service-worker syntax check: passed. Git emits the
  repository's existing LF/CRLF conversion notices; no whitespace errors.
- `adb devices -l`: no connected devices, so this iteration has no new physical
  or paired-device evidence.

Follow-up corrections during verification:

- Delayed watch history now credits its original workout date. The added
  regression passed in the final run.
- Scheduled quest day settings are locked so changing the displayed level/time
  cannot leave a different prescription in the saved schedule.
- Archived History cards have a distinct heading; the empty current-quest state
  no longer claims that there is no historical progress.
- Added root `AGENTS.md` instructions to maintain this log in future iterations.
- Updated plan and roadmap with committed checkpoints and superseded findings.
- Corrected the roadmap's claim that PWA voice settings also exist on Wear OS:
  current Wear code supplies action haptics only; watch voice remains optional.
- Preserved backup compatibility with unfinished playlist form drafts; runtime
  validation now checks draft shape without requiring a finished prescription.
- Corrected Stage 14B's status to first-slice complete/full goal partial. The
  remaining handler/effect extraction is a maintenance follow-up, not completed
  merely because some helpers were extracted.

Completed in this iteration: the functional corrections above, 60 browser
regression checks, visible workflow verification, production/native checks,
accurate plan/roadmap statuses, and persistent iteration instructions.

Remaining outside this completed review slice: physical battery and paired
phone/watch transfer, Health Connect permission/mutation/retry acceptance,
interruption/reboot checks, and Stage 14B's broader hook extraction. Optional
feature candidates remain unselected.

Checkpoint: `6a75c06` plus the local follow-up represented by this iteration.
No additional commit was created by the agent. Debug APKs are under
`android/app/build/outputs/apk/debug/` and
`android/wear/build/outputs/apk/debug/`.

Next action: review/commit the local follow-up, then open the next dated
iteration for device acceptance or the remaining Stage 14B maintenance.

Reproduce browser checks with `npm run dev`, then open `/tests/data-integrity.html`,
`/tests/custom-quests.html`, `/tests/workout-session.html`, and `/tests/watch-sync.html`.
For a seeded UI workflow, open `/tests/workflow-smoke.html`. Fixtures use isolated
databases; do not substitute the production database name.

## Iteration 3 — 2026-09-12 — Browser-to-watch feasibility and capability UX

Status: **Completed locally — feasibility review and capability corrections verified**.

Starting checkpoint: `3ba1bc6 PST01: Gap fix`, clean working tree. This commit
contains Iteration 2's follow-up; the earlier statement that it was local is
superseded by this checkpoint, without changing its historical test evidence.

Findings:

- Browser-to-Wear OS sync is feasible with a new transport, but Pasingot only
  implements the native Android/Wear Data Layer route. Installing the browser
  PWA does not add that bridge. A server-backed watch client would be a new
  architecture, not a small compatibility fix.
- The browser hid Watch sync entirely, providing no supported transfer path.
- The bridge's missing-plugin error always suggested updating the Android app,
  even in browsers; platform and plugin capability were not checked together.

Changes in this iteration:

- Show browser setup and manual backup-transfer guidance, including restore's
  replacement warning and the fact that browser/app data do not auto-sync.
- Use shared platform/plugin capability checks for the UI and manual action;
  separate browser, unsupported-platform, and outdated/missing Android bridge.
- Bump the production service-worker cache for the changed browser UI.
- Add [Browser-to-watch feasibility](BROWSER_WATCH_SYNC.md), with official
  platform sources, safe manual transfer instructions, transport tradeoffs,
  security/data-model gaps, and separate device acceptance gates.
- Update the plan, feature roadmap, and Android guide; reconcile Iteration 2's
  committed checkpoint without rewriting historical validation evidence.

Validation:

- `npx tsc --noEmit`: passed on the final code.
- `/tests/watch-sync.html`: 22 checks passed (9 existing + 13 new), including
  browser/other-native/Android plugin gating, missing legacy methods, migration
  guidance, enabled/disabled send UI, schedule-before-send ordering, and cache
  failure propagation. The deliberately failed ACK remains expected test output.
- Existing fixtures passed: 35 data-integrity, 10 custom-quest, and 6
  workout-session checks; **73 browser checks total**.
- Isolated Today workflow: watch guidance is visible in the actual app, expands
  correctly, includes the restore replacement warning, and has no unsupported
  browser send button. Inspected the rendered panel visually.
- `npm run cap:sync`: final production build and Android asset sync passed.
  Initial sandbox attempts hit esbuild `spawn EPERM`; approved reruns succeeded.
- `gradlew.bat :app:assembleDebug --quiet`: passed with the final web bundle.
  No native Kotlin/manifest source changed; Wear APK and JVM tests were not rerun
  in this iteration. Earlier device/build evidence remains separately recorded.
- `node --check pwa/public/service-worker.js` and `git diff --check`: passed.
  Existing Git line-ending conversion notices are not whitespace errors.
- `adb devices -l`: no connected devices. No new actual phone/watch delivery,
  BLE, battery, or installed-device validation is claimed.

Completed: feasibility assessment, accurate browser setup/UI errors, shared
capability gating, focused regression coverage, build checks, and durable docs.

Remaining: automatic browser/watch transfer is **not implemented**. Selecting
a server-backed or experimental local route is still required before that
architecture work. Physical paired-device acceptance and previous maintenance
items remain open.

Checkpoint: local changes after `3ba1bc6`; no commit created by the agent.
Phone APK refreshed at `android/app/build/outputs/apk/debug/app-debug.apk`.

Next action: review this correction checkpoint and choose whether to scope an
authenticated HTTPS browser/watch route. Record its implementation as a new
iteration; do not mark browser sync delivered based on this feasibility result.

## Iteration 4 — 2026-09-25 — Watch Quick Start planning

Status: **Plan audit complete and incorporated; implementation not started**.

Starting checkpoint: `23dd15c PST01: Finalize code`, clean working tree.

Decision:

- Add a phone-selected Watch Quick Start workflow for one exercise, the current
  Library playlist, or an ordered multi-selection.
- The phone sends and tracks delivery; the watch requires one explicit Start
  tap. Silent remote start and replacement of an active session are out of
  scope.
- Keep this workflow separate from weekly schedule sync and **Send today to
  watch**. Use isolated shared, phone-native, React, and Wear `quickstart`
  modules with a request/acknowledgement contract.
- Use one list-based contract for all entry paths, preserve order, cap requests
  at 24 items, and reject the whole request if any item is invalid.
- Add a separate Wear cue/success module used by all watch workouts: explicit
  voice opt-in, exercise/prescription briefing, five-second start warning,
  prescribed-rest and next-target announcements, Go cue, per-exercise success,
  and detailed final workout success. TTS failure falls back to complete visual
  and haptic behavior.
- Prevent rest-extension/TTS races with a persisted final-countdown lock. The
  `+5`, `+10`, and `+30` actions remain available above five seconds, then are
  atomically disabled before the five-second cue. The ViewModel repeats the
  guard, and Start now/natural zero cancel unfinished warning speech before Go.

Planning artifact:

- [Watch Quick Start plan](WATCH_QUICK_START_PLAN.md) defines product states,
  module/file boundaries, protocol reliability rules, five phased checkpoints,
  automated/device acceptance, risks, definition of done, and a durable resume
  marker.

Audit amendments incorporated on 2026-09-25:

- Made the phone the permanent source of truth for library, playlists,
  schedule, and History. Quick Start uses a separate one-package transient
  watch store plus the durable unsynced-result queue; it does not consume or
  collide with the existing three-entry date-keyed download store.
- Added explicit `READY`/`STARTING` lifecycle design, a global blocking-session
  invariant, atomic Start requirements, persisted final summaries, and distinct
  **Saved on watch** versus **Synced to phone** states.
- Completed the protocol planning surface with revisions, cancellation races,
  target-node binding, clock-skew-tolerant TTL, request-scoped Data Item cleanup,
  migration/capability gating, mixed-version behavior, and replay-safe pruning.
- Added short-rest speech rules, deterministic brief generation, TTS discovery
  and lifecycle, audio output/focus, TalkBack, ambient/reduced-motion behavior,
  and durable cue-ledger ordering.
- Added a battery/runtime budget: no manual wake lock, continuous polling,
  per-second persistence, or continuous progress sync; use deadline-derived
  timers, lifecycle-aware redraws, callbacks, and bounded retry/backoff.
- Declared automated contract/state/persistence/cue/bridge validation headless.
  Physical checks remain for Data Layer delivery, TTS/haptics/audio routing,
  ambient behavior, Play internal-track installation, and measured battery use.

Validation for this planning-only iteration:

- Source inspection confirmed that current phone-to-watch behavior sends only
  today's complete `WorkoutSetPayload`; the watch stores it and the user starts
  it locally. No phone-to-watch individual-exercise or remote-start command
  exists.
- No production source, schema, APK, or test fixture changed in this iteration.

Remaining:

- All Stage 19 implementation and validation phases are open.
- Existing physical-device acceptance items remain separate and open.

Next action: begin Stage 19 Phase 0. Finalize transient package persistence,
global session states/atomic Start, request/ack/cancel transitions, schema and
capability migration, short-rest/TTS behavior, durable summaries, and the
battery budget; then add the shared list-based Quick Start contract and failing
headless fixtures before UI work.

## Iteration 5 — 2026-09-25 — Stage 19 shared contract slice

Status: **Completed locally — headless shared checks pass; Phase 0 remains in
progress**.

Starting checkpoint: local planning changes after `23dd15c`; existing planning
and roadmap edits were preserved.

Implemented:

- Added an isolated `shared/quickstart` contract with a feature-specific schema,
  request/acknowledgement models, explicit serialized protocol names, source and
  rejection enums, request-scoped Data Layer path prefixes, and a transient
  `WatchSessionPackage` independent of the date-keyed download store.
- Added all-or-nothing validation for UUID/revision/node identity, five-minute
  TTL plus 30-second clock-skew tolerance, four entry-source rules, 24-item cap,
  stable/unique item IDs, bounded prescriptions, rest/load values, and Today-row
  source identity.
- Chose the conservative first-release pending policy: reject a second pending
  request rather than silently replacing the first.
- Added explicit non-regressing acknowledgement transitions, terminal-state
  protection, per-request reconciliation, and rejection-reason validation.
- Kept this checkpoint deliberately limited to the shared JVM module. No phone
  bridge, Data Layer service, Wear persistence/UI, session engine, or TTS code
  changed.

Validation:

- `./gradlew :shared:test --no-daemon --quiet`: passed.
- 31 tests passed: 15 new Quick Start contract tests plus 16 existing shared
  data/transfer tests. Coverage includes ordering, empty/oversized/partially
  invalid lists, duplicate IDs, schema/revision errors, TTL/clock skew, Today
  identity, loads, serialization names, terminal regression, unrelated request
  isolation, and acknowledgement reasons.

Remaining:

- Phase 0 persistence, atomic global session-start rules, capability/migration
  fixtures, cancellation fixtures, success-summary model, and cue contracts are
  still open. Later phone/Wear/UI/device phases have not started.

Next action: implement the isolated Wear `WatchSessionPackageStore` behind an
interface, with headless tests for one-package retention, duplicate idempotency,
pending-request rejection, expiry, restart recovery, and atomic `READY` to
`STARTING`. Do not wire Data Layer or UI in that slice.

## Iteration 6 — 2026-09-25 — Stage 19 transient Wear store

Status: **Completed locally — headless shared/Wear checks pass; Phase 0 remains
in progress**.

Starting checkpoint: Iteration 5's local shared Quick Start contract slice;
existing uncommitted planning and contract changes were preserved.

Implemented:

- Added a `QuickStartPackageStore` interface and serialized
  `WatchSessionPackageStore` that owns exactly one transient package outside the
  permanent date-keyed `WorkoutRepository`.
- Added a Preferences DataStore persistence adapter in the isolated Wear
  `quickstart` package. Writes complete before accept/start success is returned.
- Persisted a watch-local expiry derived from receive time, keeping phone/watch
  clock skew at the validation boundary and making restart recovery
  deterministic.
- Added all-or-nothing accept results for accepted, duplicate, pending-conflict,
  and invalid requests. Expired/malformed/stale ready data is cleared safely.
- Serialized `READY` to `STARTING` with a process-wide mutex so separate service
  and activity store instances cannot both win the transition. A persisted
  `STARTING` package remains available after its original offer expiry.
- Deliberately did not connect Data Layer, navigation, the legacy session
  repository, UI, result acknowledgement, or TTS.

Validation:

- `./gradlew :shared:test :wear:testDebugUnitTest --no-daemon --quiet`: passed.
- 31 shared tests passed: 15 Quick Start plus 16 existing.
- 19 Wear tests passed: 9 new transient-store tests plus 10 existing session and
  queued-history tests.
- New Wear coverage includes store recreation, duplicate no-rewrite,
  second-request rejection, local expiry/replacement, invalid isolation,
  malformed cleanup, identity checks, persisted Start, and simultaneous Start
  attempts through separate store instances.
- The first compile exposed an invalid explicit DataStore `remove` import; it
  was removed and the full scoped test command then passed.

Remaining:

- The store does not yet perform the global legacy-session conflict check or
  create a runtime workout session. Result acknowledgement/removal,
  cancellation, capability/migration fixtures, summaries, cue contracts,
  transport, and UI remain open.

Next action: implement a pure global session-start gate that inspects all legacy
downloaded sessions plus the transient package and permits exactly one atomic
Quick Start transition. Cover active/resting/paused/completed/ended, expiry,
and concurrent attempts headlessly; do not wire Data Layer or UI yet.

## Iteration 7 — 2026-09-25 — Stage 19 global session-start gate

Status: **Completed and committed in `4e97388` — headless shared/Wear checks
pass**.

Starting checkpoint: `ae0d8df PST01: Add Watch Quick Start foundations`. That
commit is local only and was not pushed.

Implemented:

- Added a process-wide `GlobalSessionStartGate` as the single serialization
  point for Quick Start and legacy downloaded-session start attempts.
- Added a narrow `LegacySessionSnapshotSource` and production adapter over
  `WorkoutRepository.entries`, without changing that repository's permanent
  date-keyed storage.
- Quick Start remains `READY` when any legacy session is active, resting,
  paused, or has an unknown persisted status. Null, explicitly completed, and
  explicitly ended legacy states do not block.
- Legacy start is blocked by either `READY` or `STARTING` Quick Start packages,
  and can resume its own entry while still rejecting another blocking entry.
- The legacy persistence callback runs inside the same process-wide gate, so a
  concurrent legacy/Quick Start race has exactly one winner once production
  entry points use this API.
- Kept this slice headless and isolated: the gate is not yet wired into
  `SessionViewModel`, navigation, Data Layer, or UI.

Validation:

- `./gradlew :shared:test :wear:testDebugUnitTest --no-daemon --quiet`: passed.
- 31 shared tests passed unchanged.
- 27 Wear tests passed: 8 new global-gate tests, 9 transient-store tests, and 10
  existing session/queued-history tests.
- New coverage includes all legacy states, fail-closed unknown status,
  Quick Start ready/starting conflicts, expiry cleanup, own-entry resume,
  wrong identity, and concurrent Quick Start/legacy arbitration.

Remaining:

- Production session entry points do not yet call the gate. Revisioned
  dismiss/cancel, result acknowledgement/removal, capability/migration,
  summaries, cues, transport, and UI remain open.

Next action: add revisioned Dismiss and Cancel operations to the transient
store. Cover `READY` removal, `STARTING` refusal, duplicate terminal requests,
wrong identity/revision, and concurrent Start-versus-Cancel headlessly; do not
wire Data Layer or UI yet.

## Iteration 8 — 2026-09-25 — Stage 19 Dismiss/Cancel persistence

Status: **Completed and committed in `d9466aa` — headless shared/Wear checks
pass**.

Starting checkpoint: `4e97388 PST01: Add global watch session gate`. That
commit is local only and was not pushed.

Implemented:

- Added revisioned `dismiss` and `cancel` operations to the isolated transient
  package-store interface and implementation.
- A valid terminal operation removes the `READY` package and durably retains a
  small dismissed/cancelled tombstone. Replayed delivery of the same request
  returns the prior terminal outcome instead of recreating the workout.
- Enforced the protocol race decision: only request revision + 1 may terminate
  a ready package; once `STARTING` is persisted, Dismiss/Cancel is refused.
- Serialized Start and termination through the existing cross-instance mutex,
  giving concurrent Start-versus-Cancel exactly one winner.
- Added tombstone shape validation. Corrupt or unsupported terminal status is
  cleared rather than suppressing a future request indefinitely.
- Kept transport, Data Item cleanup, notification/UI actions, runtime session
  creation, result acknowledgement, and TTS out of this slice.

Validation:

- `./gradlew :shared:test :wear:testDebugUnitTest --no-daemon --quiet`: passed.
- 31 shared tests passed unchanged.
- 33 Wear tests passed: 15 transient-store tests, 8 global-gate tests, and 10
  existing session/queued-history tests.
- New coverage includes Dismiss persistence/replay, Cancel replay blocking,
  STARTING refusal, wrong identity/revision, corrupt tombstone cleanup, and
  simultaneous Start-versus-Cancel arbitration.

Remaining:

- Terminal tombstones are not yet emitted as acknowledgements or age-pruned by
  Data Item cleanup. Completion summaries, capability/migration, production
  gate wiring, transport, cues, UI, and device acceptance remain open.

Next action: add the shared immutable workout-completion summary and
per-exercise outcome contract required by the watch success screen. Cover
completed/skipped exercises, set totals, elapsed/estimated time, schema
serialization, and invalid summaries headlessly; do not wire UI or sync yet.

## Iteration 9 — 2026-09-25 — Stage 19 progress/completion summary contract

Status: **Completed locally — headless shared/Wear checks pass; not yet
committed**.

Starting checkpoint: `d9466aa PST01: Add Quick Start cancellation handling`.
That commit is local only and was not pushed.

Implemented:

- Added a separate shared `session` summary module used by any watch workout,
  rather than coupling success data to Quick Start transport.
- Added immutable per-exercise outcomes with stable completed, skipped, and
  pending statuses plus completed/planned set counts.
- Added explicit `ExerciseProgressCounts(total, completed, skipped, pending)`.
  The pending number is derived from the outcome list and validation rejects a
  mismatched counter, preventing UI/history drift.
- Added immutable progress snapshots with aggregate set totals, elapsed active
  time, optional estimated time, session/title identity, and schema version.
- Added final completion summaries that require zero pending exercises while
  still allowing partially completed skipped exercises.
- Added validation for identities, duplicate items, outcome/set semantics,
  aggregate counters, durations, schema, and completion time.
- Updated the watch design plan so exercise-success and final-success states
  visibly include the pending number.

Validation:

- `./gradlew :shared:test :wear:testDebugUnitTest --no-daemon --quiet`: passed.
- 38 shared tests passed: 7 new summary tests, 15 Quick Start tests, and 16
  existing data/transfer tests.
- 33 Wear regression tests passed unchanged.
- New summary coverage includes mixed completed/skipped/pending progress,
  explicit pending serialization, final zero-pending enforcement, aggregate
  mismatch rejection, invalid set/outcome semantics, and duplicate identity.

Remaining:

- The runtime session engine does not yet produce or persist this summary. The
  watch success UI, saved/synced states, transport, cues, and device acceptance
  remain open.

Next action: implement a pure Wear outcome reducer that produces the shared
progress snapshot from set completion and exercise skip transitions. Cover
partial sets, completed/skipped/pending counts, duplicate transitions, and
final-summary creation headlessly; do not wire UI or persistence yet.
