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

Status: **Completed and committed in `18a7554` — headless shared/Wear checks
pass**.

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

## Iteration 10 — 2026-09-25 — Stage 19 outcome reducer and progress text

Status: **Completed locally — headless shared/Wear checks pass; subsequently
committed in `92ef2cb`**. The uncommitted references below preserve the original
2026-09-25 audit; Iteration 11 records the newer checkpoint.

Starting checkpoint: `18a7554 PST01: Add workout progress summaries`. That
commit is local only and was not pushed.

Implemented:

- Added a pure Wear outcome reducer with ordered revisions for set-complete and
  exercise-skip transitions.
- Partial sets remain pending, final sets resolve as completed, and skips retain
  any completed-set count.
- Duplicate or old revisions are idempotent. Revision gaps, unknown items, and
  attempts to change resolved exercises leave state unchanged.
- Added valid progress snapshot creation and final-summary creation only when
  every exercise is completed or skipped.
- Added compact **completed/total** progress such as `2/5`, derived from the
  validated counters, plus descriptive TalkBack text such as
  **2 of 5 exercises completed**.
- Kept this slice pure and headless. It does not yet persist reducer state or
  connect to the current session engine or Compose UI.

Validation:

- `./gradlew :shared:test :wear:testDebugUnitTest --no-daemon --quiet`: passed
  after the final initialization guard and fixture.
- 38 shared tests passed unchanged. 40 Wear tests passed, including seven new
  reducer/progress cases.

Progress audit:

- Reconciled the Stage 19 checklist against code and test evidence. The current
  working tree is **16/93 items (17%) overall** and Phase 0 is
  **16/27 items (59%)**.
- The latest committed checkpoint `18a7554` is **14/92 items (15%)**; the delta
  is this uncommitted reducer/presentation slice plus fixture reconciliation.
- The completed/total ratio counts checklist items equally and is not an effort
  estimate. No Phase 1–4 item is closed, so the feature is not end-to-end usable
  and has no paired-device acceptance yet.
- Confirmed the combined empty/oversized/partial/order/expiry/clock-skew/
  duplicate/cancel/out-of-order/unsupported-schema fixture gate from the shared
  contract and Wear store tests, and updated the stale shared-test count from
  31 to 38.

Remaining:

- Reducer persistence, production session wiring, success UI rendering,
  transport, cues, saved/synced acknowledgements, and device acceptance remain
  open.

Next action: add a persisted reducer-state adapter with restart,
invalid-state cleanup, and atomic-transition coverage. Do not wire session UI
or Data Layer in that slice.

## Iteration 11 — 2026-09-26 — Stage 19 outcome-state persistence

Status: **Completed in this checkpoint — shared/Wear checks and Wear debug
build pass. Runtime integration and device acceptance remain open**.

Starting checkpoint: `92ef2cb PST01: Add workout outcome progress`. The working
tree was clean and HEAD matched the local `origin/PST01` tracking ref. This is
local Git evidence, not a fresh remote fetch.

Audit findings:

- Iteration 10 is now committed in `92ef2cb`; its original uncommitted status
  and the plan's `18a7554` latest-checkpoint marker are superseded.
- Stage 19 starts at 16/93 checklist items (17%), Phase 0 at 16/27 (59%). The
  Quick Start plan's top-level “implementation not started” label is stale;
  the detailed checklist correctly shows partial Phase 0 foundations.
- The pure reducer has no durable adapter. Recovery must validate both outcome
  semantics and the matching revision before accepting another transition.

Implemented:

- Added `WorkoutOutcomeStore`/`WatchWorkoutOutcomeStore` and a production
  Preferences DataStore adapter, separate from Quick Start offers and the
  permanent date-keyed repository. One versioned record stores session identity,
  ordered outcomes, and revision in a single awaited write.
- Reinitializing the same session/plan preserves progress. A different session,
  title, order, or prescription cannot overwrite it, even after all exercises
  resolve; acknowledged removal is a later integration step.
- Serialized initialization and read/reduce/write across store instances.
  Duplicate and rejected transitions do not rewrite storage. No optimistic
  cache can diverge from a failed or uncertain write.
- Restore validates outcome semantics and requires the revision to equal
  completed sets plus skip transitions. Malformed, unsupported, or inconsistent
  records are cleared; storage errors/cancellation propagate without erasing
  progress or falsely returning success.
- Reconciled the latest committed checkpoint and stale plan header. Added an
  explicit outcome-persistence checklist item: Stage 19 is now **17/94 (18%)**,
  Phase 0 **17/28 (61%)**. This ratio counts items equally, not effort. No
  Phase 1–4 item was closed.

Validation:

- `gradlew.bat :shared:test :wear:testDebugUnitTest :wear:assembleDebug
  --no-daemon --quiet`: passed.
- 38 shared tests passed unchanged; 54 Wear tests passed, including 13 new
  outcome-store cases and one real Preferences DataStore file recovery case.
- Covered partial/skipped outcomes and order, replay after recreation, final
  snapshot derivation, initialization conflicts, invalid-state cleanup,
  additive fields, concurrent instances, blocked writes, before/after-commit
  failures, read/cleanup failures, and cancellation/retry.
- The real-file test closes the original DataStore scope, reopens the same
  preferences file, restores both state and revision, rejects a duplicate, and
  verifies malformed-record cleanup. This is headless disk evidence, not an
  Android process-kill/reboot or paired-device test.
- Wear debug APK built successfully; `git diff --check` passed. The initial
  sandbox run could not lock the existing user Gradle cache; the permitted
  elevated run passed with that cache. No toolchain/dependency changes needed.

Remaining: production session integration, immutable final-result/queue
transactions, saved/synced receipts, transport, cue contracts, UI, and device
acceptance. This slice does not connect session UI or Data Layer.

Next action: add shared `quickstart/QuickStartCapability.kt` with headless
capability/schema negotiation and mixed phone/watch-version fixtures. Record
the migration policy before closing that Phase 0 gate; keep transport/UI out
of that slice.

## Iteration 12 — 2026-09-26 — Stage 19 capability and version contract

Status: **Completed in this checkpoint — shared/Wear headless checks pass.
Native delivery and device validation remain open**.

Starting checkpoint: `341a1f9 PST01: Persist watch workout outcomes`. Its staged
diff was reviewed, `git diff --cached --check` passed, and no unrelated files
were committed. The branch is ahead of the local `origin/PST01` tracking ref by
one commit; no remote fetch or push was made.

Finding: the request schema is fixed at version 1, but the shared module has no
typed capability advertisement or negotiation rule. The phone cannot yet
distinguish an unreachable watch, absent app capability, incompatible schema,
or a node identity mismatch before attempting transport.

Implemented:

- Added a shared v1 capability envelope with publisher node ID, phone/watch
  role, and the list of request schema versions that peer implements. The
  current helper includes only schema 1; the feature path remains separate from
  legacy schedule sync.
- Added pure negotiation for one selected reachable node, with identity/role
  binding and the highest common request schema. It distinguishes unreachable,
  absent, malformed, unsupported-envelope, wrong-node/role, and no-common-schema
  outcomes. Unknown optional v1 envelope fields are accepted; unknown envelope
  versions fail closed.
- Recorded the migration rule: hypothetical upgraded peers supporting schemas
  1 and 2 fall back to 1 with an old peer; 2-only versus 1-only is incompatible.
  Future binaries must not advertise schema 2 until they actually implement it.
  No native advertisement, discovery, or send gating is wired yet.
- Reconciled the checklist: Stage 19 is **19/95 items (20%)**, Phase 0
  **19/29 (66%)**. Two items were closed: the shared capability contract and
  the mixed-version headless fixture gate. Actual native mixed-build delivery
  and any future stored/request schema migration stay open.

Validation:

- `gradlew.bat :shared:test :wear:testDebugUnitTest --no-daemon --quiet`:
  passed, with 48 shared tests (10 new capability cases) and 54 Wear regression
  tests.
- New fixtures cover current v1 peers, reachability, absent capability,
  node/role mismatch, phone-new/watch-old and watch-new/phone-old fallback,
  incompatible schemas, malformed/unsupported envelopes, additive fields, and
  use of the negotiated schema by the existing request validator.
- `git diff --check` passed for tracked changes. This is headless contract
  validation, not native Data Layer or paired-device acceptance.

Remaining: result acknowledgement/removal and retained READY/STARTING recovery,
native capability publication/discovery, transport, phone/watch UI, cues,
session integration, and device acceptance.

Next action: define result acknowledgement/removal and READY/STARTING recovery
in the isolated Wear stores. Add headless fixtures that prevent clearing
unsynced outcomes and prove revision-safe replay; then record retention rules.

## Iteration 13 — 2026-09-26 — Stage 19 result retention and recovery

Status: **Completed locally and included in this audited checkpoint — shared/Wear
tests and Wear debug build pass. Native delivery and device acceptance remain open**.

Starting checkpoint: `0e7dee1 PST01: Define watch quick start capability`.
The working tree was clean after that commit; no remote fetch or push occurred.

Audit finding: the existing legacy `LogSyncManager` drops queued history after
Wear message transport succeeds. That is not a phone import receipt, so Quick
Start cannot use it as proof for **Synced to phone** or for pruning the new
transient workout state. Its separate result receipt needs exact identity and
revision checks. A persisted `STARTING` package must recover without launching
a workout unseen, while an expired `READY` offer must not start.

Implemented:

- Added an isolated Preferences DataStore-backed completed-result record with
  immutable summary, request/result identity, final outcome revision, and the
  expected phone node. The record remains available for resend until an exact
  receipt from that observed node is durably written. An unreadable/future
  record fails closed without deleting its raw bytes.
- Added a serial retention coordinator. It validates the package is `STARTING`
  and the outcome snapshot matches the saved summary before saving a result.
  After a matching phone receipt, it clears only the matching outcome revision,
  compacts the result to a small receipt tombstone, then releases the package.
  Every step is retryable after an interrupted
  write; transport acceptance alone performs no pruning.
- Added bounded acknowledged-request replay history to the package store. It
  retains up to 64 recent receipts through local expiry plus two skew
  allowances and refuses a new offer rather than evicting unexpired protection.
  A delayed old request stays blocked even after a newer offer is dismissed.
- Added a pure recovery decision: an unexpired `READY` offer returns to Ready;
  a persisted `STARTING` package requires explicit foreground resume; an
  expired offer cannot start. The pre-start countdown remains foreground-only
  and does not persist a hidden auto-start.
- Reconciled Stage 19 to **21/96 items (22%)** and Phase 0 to **21/30 (70%)**.
  Closed the READY/STARTING behavior decision and one completed-result retention
  item. Ended-before-completion results and native phone receipt delivery stay
  open; no Phase 1–4 item was closed.

Validation:

- `gradlew.bat :shared:test :wear:testDebugUnitTest :wear:assembleDebug
  --no-daemon --quiet`: passed. After the final replay-horizon fixture,
  `gradlew.bat :wear:testDebugUnitTest --no-daemon --quiet` passed.
- 48 shared tests passed unchanged; 66 Wear tests passed, including 11 new
  retention/recovery cases and one real Preferences DataStore file-reopen case.
- Headless fixtures cover no pruning on transport acceptance, exact node/ID/
  revision matching, wrong/stale receipts, store recreation, cleanup after lost
  write responses at every boundary, failed writes, malformed/future record
  preservation, replay after a newer offer, bounded history, and the final
  clock-skew-valid replay instant.
- The final debug APK build and `git diff --check` passed. This is code/storage
  evidence; it does not establish
  paired Data Layer delivery, Android reboot behavior, or physical acceptance.

Remaining: ended-session result variant, shared receipt wire contract, native
sender/receiver, runtime integration, UI, cues, and paired-device validation.

Next action: extend the immutable final result to ended-before-completion
sessions and define a shared phone receipt payload with sender-node validation.
Cover both terminal types and offline replay headlessly before native wiring.

Checkpoint audit addendum: fixed a package-first cleanup race that could strand
an acknowledged result after a new offer arrived. Package release now happens
last; a durable outcome tombstone recognizes interrupted cleanup without
touching a newer session and rejects reinitializing the acknowledged session.
Saving also binds the session ID and ordered exercise plan to the original
request and verifies that result revision equals accepted outcome transitions.
The original 66-test evidence above predates this audit; final evidence follows.

Final audit validation: 48 shared and 71 Wear tests pass (16 retention cases),
Wear debug APK build passes, and whitespace checks pass. Five new regressions
cover delayed compaction, newer outcomes, acknowledged history, package-plan
binding, and acknowledged-session resurrection. Independent audit found no
further blocking issue in this checkpoint. No native/device claim is added.

## Iteration 14 — 2026-09-26 — Stage 19 ended results and terminal outcome freeze

Status: **Completed locally and included in this audited checkpoint — shared/Wear
tests and Wear debug build pass. Native/device validation remains open**.

Starting checkpoint: `bffe396 PST01: Retain quick start results until phone receipt`.
That audited checkpoint passed 48 shared/71 Wear tests and the Wear debug build.
No remote fetch or push occurred.

Finding: a completed result cannot describe a workout ended with pending
exercises. Saving a separate ended snapshot without freezing the outcome store
would permit later set/skip events to change the revision and strand receipt
cleanup. Final metadata must survive a failure before result enqueue.

Implemented:

- Shared ended-summary variant preserves pending counts, supports zero sets,
  and distinguishes ending from completion. Existing completion JSON remains
  readable; exactly one terminal summary is required.
- The outcome store atomically freezes the exact final result with its current
  snapshot/revision. Frozen sessions reject subsequent transitions, and recovery
  reuses the original ID, timestamp, node and summary after a failed enqueue.
- Outcome envelope schema 2 records terminal state; valid schema-1 outcomes are
  still readable and upgrade on write. Invalid/future bytes now fail closed and
  remain untouched. This supersedes Iteration 11's destructive invalid-state
  cleanup, because the record can now contain the only saved terminal metadata.

Audit corrections: preflight existing results before freezing so regenerated
metadata cannot strand a legacy completion; expose persisted receipt recovery
so cleanup can finish offline after compaction succeeds but package release
fails. No further blocking finding remained after independent review.

Validation: `gradlew.bat :shared:test :wear:testDebugUnitTest :wear:assembleDebug
--no-daemon --quiet` passed: 54 shared and 81 Wear tests. Six new shared tests
cover ended-summary invariants; ten Wear cases cover zero/partial endings,
concurrent transitions/freeze, failure before/after writes, exact metadata
recovery, legacy completion upgrades/conflicts, and offline cleanup. Whitespace
checks passed. Stage 19 is 22/96 (23%); Phase 0 is 22/30 (73%).

Remaining: native session integration and device validation. Forward migration
is verified; downgrading to older builds that erase unsupported records is not
supported. Next checkpoint: shared result/receipt codec and sender-node binding.

## Iteration 15 — 2026-09-26 — Stage 19 shared result and persisted-receipt wire contract

Status: **Completed locally and included in this audited checkpoint — shared,
Wear and phone tests plus Wear debug build pass. Native/device validation is open**.

Starting checkpoint: `7596256 PST01: Freeze and retain ended quick start results`.
It passed 54 shared/81 Wear tests and the Wear debug build. No fetch or push.

Finding: result/receipt models lived only in Wear, so the phone had no shared
codec or rules to bind a result to its selected watch/request, or a receipt to
its observed phone sender. A raw transport-success signal must never authorize
cleanup. Compact receipt tombstones must still validate duplicate wire receipts.

Implemented: moved the stored models unchanged into shared contracts; added
required-version result/receipt envelopes, request/result-scoped paths, strict
payload and observed-node validation, and a persisted-only receipt status.
Connected the Wear receipt entry point to this decoder before any storage write.
Preserved existing local records and offline finalization/cleanup behavior.

Audit corrections: enforce path-safe wire identities for newly finalized
results before freezing, while keeping legacy disk readers compatible; reject
a phone identity equal to the target watch and bind explicit request titles.
The phone must persist the import receipt once and replay the same timestamp.
Independent review found no further blocking decode/binding issue.

Validation: `gradlew.bat :shared:test :wear:testDebugUnitTest :wear:assembleDebug
:app:testDebugUnitTest --no-daemon --quiet` passed. Reports show 73 shared,
87 Wear and one phone test, all without failures. Nineteen new shared tests
cover both terminal types, required versions/status, optional fields, sender/
target/path/identity binding, ordered plans, revision checks, legacy JSON,
delayed imports and payload limits. Six grouped Wear cases prove invalid wire
receipts perform no writes, exact replay before/after compaction, offline
recovery, and finalization guards. The final APK and whitespace checks passed.

Stage 19 is **23/97 items (24%)**; Phase 0 is **23/31 (74%)**. The new shared
wire-contract item adds one to both completed and total counts. No Phase 1–4
item is closed by these headless checks. Iterations 13 and 14 are committed as
`bffe396` and `7596256`; Iteration 15 is this checkpoint.

Remaining: native Data Layer listeners, observed origin-node persistence, phone
import transactions, transport cleanup, UI/runtime integration and physical
acceptance. Next action: finalize Phase 0 cue scripts/event keys/priority and
recovery rules, then implement the pure cue ledger and five-second extension
lock with boundary tests before native cue adapters or UI.

## Iteration 16 — 2026-09-26 — Stage 19 Phase 0 cue and protocol foundations

Status: **Headless Phase 0 complete and included in this audited checkpoint; native/device acceptance open**.

Starting checkpoint: `61edf55 PST01: Share quick start result and receipt contracts`.
The tree was clean when this iteration began. The local origin tracking ref
matched HEAD at the start; no fetch or push has been made in this iteration.

Audit findings: ownerless package data could let a duplicate from another phone
node be treated as the same offer. Dismissed/cancelled tombstones were lost when
a later offer was accepted, allowing an old request replay during its valid
window. The five-second rest threshold needs a persisted latch so pause/recovery
cannot enable extensions or replay a stale warning.

Implemented:

- Added optional observed `sourcePhoneNodeId` to the transient package. New
  accepted offers bind to the actual Data Layer sender; a different sender
  cannot claim a duplicate, and finalization checks the saved owner. Existing
  ownerless stored records remain readable for migration.
- Retained bounded dismissed/cancelled history through the replay horizon.
  When 64 records are still live, the store refuses a new offer rather than
  evicting replay protection. STARTING and exact result receipt cleanup remain
  separate from the date-keyed download repository.
- Added opt-in cue preferences, bounded deterministic scripts, short-rest
  suppression, event priority/identity, a serializable success ledger, and a
  serializable rest countdown lock. The lock allows extension above five seconds,
  rejects it at/below five, survives pause/recovery, and terminates on Start now.
  These are Phase 0 rules; SessionViewModel, Compose and TTS adapter wiring stay
  in Phase 2.
- Recorded phone ownership, cancellation/cleanup, capability migration,
  TalkBack/audio fallback and TTS lifecycle decisions in the Watch Quick Start
  plan. Eight Phase 0 decisions/checks are closed after headless evidence.

Validation: `gradlew.bat :shared:test :wear:testDebugUnitTest :wear:assembleDebug
:app:testDebugUnitTest --no-daemon --quiet` passed with 73 shared, 100 Wear, and
one phone test. Boundary fixtures cover six-second extension, five-second
refusal, pause recovery, short rests and 64 terminal replay records. The Wear
debug build and `git diff --check` passed. Phase 0 is **31/31 (100%)**; Stage 19
is **31/97 (32%)**. These are code/decision checks, not end-to-end or physical
watch acceptance.

Next action: build the isolated Phase 1 Android phone bridge and React feature
shell; keep the browser send control gated on a reachable compatible watch.

## Iteration 17 — 2026-09-26 — Stage 19 native phone bridge

Status: **Native phone slice verified and included in this audited checkpoint; React, watch runtime, and device acceptance open**.

Starting checkpoint: `085e39e PST01: Complete quick start foundation decisions`.
Git was clean at the start. That checkpoint passed 73 shared, 100 Wear and one
phone tests plus the Wear debug build. No fetch or push occurred.

Finding: a Data Layer `putDataItem` result confirms transport acceptance, not
that the watch is ready. A phone process can die between offer send and a watch
acknowledgement, so the original request and the latest valid acknowledgement
must survive independently. A different watch node must not confirm the offer.

Implemented a registered `WatchQuickStart` Capacitor plugin, phone Data Layer
client, and isolated DataStore-backed request/acknowledgement history. Sending
requires one connected watch whose published schema-1 capability negotiates
with the phone and matches the request target. The phone saves the validated
offer before sending and records transport acceptance separately. A listener
validates acknowledgement path, observed watch node, revision and state before
the durable write; the plugin exposes availability, send, status lookup, and a
live status hint. History is bounded at 64 and fails closed rather than evicting
unacknowledged requests. Audit correction: malformed decoded plugin arguments
also resolve as an error instead of escaping the bridge.

Validation: `gradlew.bat :app:testDebugUnitTest :app:assembleDebug --no-daemon
--quiet` passed with six phone tests and no failures. Fixtures cover transport
versus Ready, node/path/revision rejection, terminal non-regression, interrupted
writes, bounded history, and corrupt/future-state preservation. Kotlin compile,
APK assembly and `git diff --check` passed. Stage 19 is **32/97 (33%)** and
Phase 1 is **1/10 (10%)**. The watch does not yet publish the capability, so
the feature correctly remains unavailable end to end; no device delivery is
claimed.

Remaining: React sheet and entry points, mocked status fixture, watch receiver
and runtime wiring, final-result import, transport cleanup, and paired-device
acceptance. Next action: implement the isolated React feature hook/sheet and
browser-safe capability gating, then run TypeScript and browser fixtures.

## Iteration 18 — 2026-09-26 — Stage 19 React phone feature shell

Status: **React implementation and headless checks complete in this audited checkpoint; visible browser and paired-device checks open**.

Starting checkpoint: `e178b74 PST01: Add native quick start phone bridge`.
The working tree was clean after that commit. No fetch or push occurred.

Finding: the browser must not offer a send path for an unavailable native
plugin. Transport acceptance must remain a waiting state in the phone UI until
the watch's persisted Ready acknowledgement is restored or delivered live.
The source contract also caps request titles at 80 characters, so single-item
titles need truncation even though exercise names may reach 120.

Implemented an isolated feature model, bridge adapter, hook and editing sheet.
Library offers single exercise, saved playlist, and ordered selection entry
points; Today offers a scheduled-row entry. The sheet edits sets, reps/duration,
rest and optional load for one or many exercises, reorders items, validates all
prescriptions before send, and displays the six watch statuses and rejection
reasons. The hook handles native availability, send, live status hints, and
durable status reconciliation on visibility return. `App.tsx` only composes the
hook and passes callbacks. Browser controls are omitted when the native Android
plugin is unavailable. The final Vite bundle was synced into Capacitor.

Validation: `npx tsc --noEmit`, `npm run build`, `node
--experimental-strip-types pwa/tests/quick-start.test.mjs`, `npx cap sync
android`, `gradlew.bat :app:assembleDebug --no-daemon --quiet`, and `git diff
--check` passed. The two Node fixtures cover all-or-nothing validation,
selection order, title bounds, transport-versus-Ready wording, every
phone-visible status, and rejection reasons. `pwa/tests/quick-start.html`
adds a visible browser fixture, but the in-app browser reported
`net::ERR_BLOCKED_BY_CLIENT` for both localhost and 127.0.0.1. Consequently the
visible browser fixture and browser UI exit checks remain open. The first
sandboxed Vite build and Node test-runner attempts hit local spawn EPERM; the
permitted Vite rerun and direct Node test invocation passed.

Stage 19 is **39/97 items (40%)** and Phase 1 is **8/10 (80%)**. This is a
checklist ratio, not end-to-end feature completion. The watch does not yet
publish its capability or receive Quick Start offers, so a paired send cannot
be claimed. Next action: run the visible browser fixture and inspect phone-width
Library/Today UI, then close the two remaining Phase 1 browser checks if they
pass before beginning the Phase 2 watch receiver.

## Iteration 19 — 2026-09-27 — Stage 19 phone recovery audit

Status: **Phone recovery and Phase 1 browser checks complete in this audited checkpoint; watch/device delivery remains open**.

Starting checkpoint: `4a62ae0 PST01: Add quick start phone feature shell`.
The tree was clean at the start. The local origin tracking ref advanced to HEAD
during this iteration; no fetch or push was made by this task.

Audit findings: the Android store retained the latest offer, but React had no
way to discover its ID after a phone process restart. That made a persisted
Ready or waiting state invisible. The shared acknowledgement reducer also
accepted a different state at the same revision, although one revision should
represent one immutable watch decision. Finally, the phone could accept a
second offer while the first was unexpired and pending/Ready, contrary to the
Phase 0 pending-request decision.

Implemented `getLatestQuickStart` over the durable phone store and hydrated the
React hook before exposing native Quick Start entry points. Today now shows the
restored status with a View action, and opening a new entry while the latest
offer is pending/Ready returns to that offer. A single deadline refresh changes
stale waiting/Ready wording to expired without polling. The phone store rejects
a new offer while an unexpired transport-accepted or Ready offer exists; exact
same-ID retries remain idempotent. Equal-revision conflicting acknowledgements
are now stale. Audit correction: the fixed-width Today row action remains an
icon, and the recovered sheet keeps its original exercise order and targets.

Validation: `gradlew.bat :shared:test :app:testDebugUnitTest
:app:assembleDebug --no-daemon --quiet` passed with 74 shared and eight phone
tests, zero failures. `:wear:testDebugUnitTest :wear:assembleDebug` also passed
with 100 Wear tests and a fresh Wear APK. `npx tsc --noEmit`, `node --experimental-strip-types
pwa/tests/quick-start.test.mjs` (three fixtures), `npm run build`, `npx cap
sync android`, and the final phone debug APK build passed. Fixtures cover
same-revision conflicts, durable latest lookup, pending-offer rejection and
terminal/expiry release, exact React offer restoration, and expiry wording.
The in-app browser reached the project's configured Vite port on this audit.
`pwa/tests/quick-start.html` passed all 18 checks. At a 390px viewport, Library
and a scheduled Today row displayed no browser Quick Start send control and no
horizontal overflow. The sheet itself fit the viewport; its disabled send now
has a visible disabled style. The fixture's long results also wrap on mobile.
This supersedes Iteration 18's blocked browser attempt. No paired-device
behavior is claimed.

Stage 19 is **41/97 (42%)**, Phase 1 **10/10 (100%)**: the two browser exit
checks close on visible evidence. Phase 1 completion covers phone code and
browser validation only. Next action: implement Phase 2 watch capability
publication and an observed-sender request listener, with invalid/duplicate
receipt fixtures before building the ready prompt.

## Iteration 20 — 2026-09-27 — Stage 19 watch request transport audit

Status: **Native receive/persistence slice complete with Wear JVM/build evidence;
watch prompt, capability publication, and paired-device validation remain open**.

Starting checkpoint: `e0005d8 PST01: Complete quick start phone phase audit`.
The working tree was clean and the branch was one commit ahead of its tracked
remote. No fetch or push was made by this task.

Audit finding: the phone has a capability-gated Data Layer sender and the watch
already has a durable transient package store, but no native watch listener
connected them. Publishing the capability before a Start/Dismiss prompt would
expose an incomplete flow, so capability publication remains gated until that
prompt is usable. The global start gate is pure code and is still not wired to
the legacy session start path; an active-session snapshot check on receipt does
not yet prove race-free arbitration.

Implemented a manifest-registered request listener, a coordinator with strict
request path/observed phone node/local watch target binding, and a Data Layer
acknowledgement client. Accepted requests are written through the existing
transient DataStore before Ready is sent; the permanent date-keyed workout
repository is not written. Duplicate Ready delivery repeats its receipt without
rewriting the package. Malformed, unsupported, expired, active-session, and
pending requests have explicit outcomes where a safe request identity exists.
Expiry advances the revision so it can supersede a persisted Ready receipt on
the phone.
Replays already marked STARTING do not emit a premature Started receipt, and a
failed durable write emits no Ready receipt. Terminal replay uses the retained
store outcome. The native receiver does not yet recover missed receipts from
persisted items after a transport failure; that belongs to Phase 3.

Validation: `gradlew.bat :wear:testDebugUnitTest :wear:assembleDebug --no-daemon
--quiet` passed with **108 Wear tests**, including eight new coordinator tests,
and produced a debug APK. A first test compile found a fixture SAM-construction
issue; changing the existing single-method snapshot source to a fun interface
resolved it, and the full rerun passed. `git diff --check` passed. No device
delivery or visible prompt is claimed.

Stage 19 is **42/97 (43%)** and Phase 2 is **1/17 (6%)**. Only the checklist
item for transient persistence is closed. The listener/coordinator/receipt
client/prompt item remains open until the prompt exists; active-session handling
remains open until both legacy and Quick Start start paths share the gate.
Next action: build the Ready prompt and Start/Dismiss flow, wire the global gate
into both session starts, then publish capability and verify paired delivery.

## Iteration 21 — 2026-09-27 — Stage 19 session-admission audit

Status: **Watch offer and downloaded-workout admission serialized with JVM/build
evidence; Quick Start runtime UI and device validation remain open**.

Starting checkpoint: `fe6089d PST01: Add watch quick start request receiver`.
The working tree was clean and the branch was two commits ahead of its tracked
remote. No fetch or push was made by this task.

Audit finding: the previous receiver checked a snapshot of legacy sessions,
but the production `SessionViewModel` created and persisted downloaded-workout
sessions without using `GlobalSessionStartGate`. A legacy start could therefore
race the listener and both could claim the session slot. The listener also
checked active sessions before classifying invalid payloads, so an invalid
request could receive the wrong rejection reason.

Added an offer-admission method to the process-wide gate. It validates the
request, checks all blocking legacy sessions, and persists the transient
package while holding the same mutex used by `startLegacy`. The receiver uses
that method before publishing its acknowledgement. Downloaded-workout
initialization and resume now require `startLegacy`; a fresh session's
DataStore write completes inside the gate before the UI or watch snapshot says
it started. A blocked screen shows a reason and does not write a session or
publish a snapshot. A duplicate Ready replay is suppressed if an older
inconsistent state also contains an active legacy session. The UI still uses
the existing date-keyed repository only for downloaded workouts; Quick Start
remains in its separate transient package.

Validation: `gradlew.bat :wear:testDebugUnitTest :wear:assembleDebug --no-daemon
--quiet` passed with **113 Wear tests** and a debug APK. New cases cover a
concurrent offer versus legacy start (exactly one winner), invalid schema while
legacy is active, inconsistent duplicate replay, a blocked downloaded session
making no write or snapshot, and persistence before an admitted snapshot. The existing session/repository suite also
passes. `git diff --check` passed. This is JVM/build evidence, not physical-watch
or paired-device proof.

Stage 19 is **44/97 (45%)** and Phase 2 is **3/17 (18%)**. The active/invalid/
expired/duplicate handling item and existing session/repository test exit check
close. Ready prompt, Start/Dismiss/Cancel, runtime session creation, capability
publication, and paired delivery remain open. Next action: render the persisted
Ready offer in-app and connect Start/Dismiss to the runtime session engine; only
then advertise capability to the phone.

## Iteration 22 — 2026-09-27 — Full audit and reliability checkpoint

Status: **Resumed and closed with integrated automated validation. Runtime UI
and device acceptance remain open.** The interrupted-work record below is
historical and superseded by the resumed closure at the end of this iteration.

Starting checkpoint: `6a092d3 PST01: Serialize watch workout admission`, clean
tree, three commits ahead of the tracked remote. The requested audit covered
PWA session/backup/watch-sync/Health Connect workflows, Android phone bridge and
stores, shared protocol validation, Wear request/package/result/outcome stores,
session admission, the runtime engine, roadmap claims, and validation tooling.
No fetch or push occurred.

Baseline before edits passed: `npx tsc --noEmit`, `npm run build`, the three
Quick Start Node fixtures, and `gradlew.bat :shared:test :app:testDebugUnitTest
:wear:testDebugUnitTest :app:assembleDebug :wear:assembleDebug --no-daemon
--quiet`. This baseline evidence does not validate the later edits below.

Confirmed audit findings: offer TTL was checked before persisted STARTING and
terminal replay, expired Ready could gain a second local TTL, unreadable/future
package data was erased, phone requests did not reserve the pending slot until
transport returned, rejected outcomes were not durable, and revision/time
arithmetic needed overflow bounds. PWA completion wrote history and progress
separately, prior-day recovery deleted sessions without an ended event, and
Health Connect mutation responses overwrote granular permission state. Wear
transitions published UI/history before durable progress and lacked compare-and-
set protection against a reset/deleted/stale entry. The earlier checklist
completion claims are qualified by these newly found reliability gaps.

Uncommitted work now includes protocol replay/reservation/fail-closed fixes and
tests; durable rejected-outcome/source binding work (interrupted during audit);
a PWA session hook with atomic IndexedDB compare-and-commit, prior-day closure,
Health Connect status refresh and ambiguous-offer restoration; a Wear CAS
session commit with an atomic history outbox, stable replay payloads, serialized
commands and write-error UI; and a new transient Quick Start runtime store that
co-persists session progress, outcomes, exact Started receipt and frozen final
result. Runtime adapters, Ready/Start UI, capability publication and native final
result transport are still missing. New runtime fixtures and the new browser
`workout-session-integrity.html` fixture have been written but not yet validated
as an integrated checkpoint.

The user requested stopping at 10% remaining in the five-hour account window.
The live meter reported 97% remaining at the first check and 0% at the next;
parallel implementation was interrupted immediately when the threshold crossing
was observed. No further implementation or commit was attempted. The final
whitespace check passed; integrated compilation, all new tests and browser
validation remain open. The last committed progress ratio remains 44/97 (45%),
not a claim of end-to-end completeness.

Exact next action on explicit resume: inspect all WIP diffs and finish the
interrupted rejected-outcome audit; compile the new Wear session/store interfaces
and runtime fixtures; run the full JVM/APK baseline again; run TypeScript, Node,
Vite and all isolated browser fixtures (including the new session-integrity
fixture); audit failure/replay cases and then create logical validated commits.
Reconcile stale plan text claiming the global gate is not production-wired, and
distinguish request source-node persistence from still-missing final-result
transport. Do not publish capability until the runtime/terminal save path is
usable. No emulator was running; available Wear AVD is 454x454, so historical
480x480 evidence must not be reused as current proof.

### Resumed closure — 2026-09-27

The user explicitly requested finalization and commit. Tracking had caught up
with `6a092d3`; no fetch or push occurred. Repaired interrupted terminal fields
and Kotlin cross-module smart casts. Durable rejections retain sender, payload,
reason and expiry bounds; blocker removal cannot revive a rejected request.
Cross-bucket identity conflicts fail closed. Download-list errors now show
feedback while preserving unreadable storage. All WIP described above is now
validated. The runtime remains an independent foundation, not a wired UI.

Validation passed: TypeScript, production Vite build, three Node tests, and
**108 isolated browser checks** (watch sync 22, data integrity 35, custom quests
10, elapsed session 6, Quick Start 18, session integrity 17). The isolated App
passed a 390x844 smoke covering two-set progression, rest extension, pause/resume,
Start now and final logging without horizontal overflow. No personal DB was
modified. Integrated Gradle tests/builds passed: **75 shared, 9 phone, 145 Wear
tests**, zero failures/errors, and both debug APKs. This successful rerun
supersedes the first resumed compile failure. Whitespace checks passed.

Checklist remains **44/97 (45%)**. Request-source persistence and production
gate roadmap claims were reconciled. No new Wear visual, physical Health Connect
permission, paired-delivery or battery evidence is claimed. At 10% remaining
five-hour allowance, new implementation stopped; only final validation and
checkpoint recording continued. No next feature slice started.

Next action: await the durable pause before Save & close navigation (immediate
disposal can cancel the current ViewModel-scope exit write), then adapt the
session engine to the runtime and implement Ready/Start/Dismiss plus exact
terminal receipt cleanup. Keep capability unpublished until usable. Validate
the error surface and session flow on the 454x454 Wear emulator separately.

## Iteration 23 — 2026-09-27 — Paired emulator runtime validation

Status: **Phone-to-watch emulator pairing and legacy workout delivery passed;
Quick Start and physical-device acceptance remain open.**

Starting checkpoint: `PST01` matched its local `origin/PST01` tracking ref and
the working tree was clean. No fetch, push, build, or implementation change was
made. The existing phone and Wear debug APKs from Iteration 22 were used.

Launched the API 35 Google Play Pixel 8 AVD and the 454x454 Wear OS 7 AVD. The
official emulator-pairing flow initially waited because its ADB bridge had not
been established. Reproduced Android Studio's installed pairing-assistant
bridge (`phone tcp:5602 -> tcp:5601`, watch reverse `tcp:5601 -> tcp:5602`) and
restarted the companion emulator activity. Both Google Play Services pairing
status broadcasts then reported the reciprocal nodes connected and enabled:
phone `3710eec` to watch `cc1f21d2`, and watch `cc1f21d2` to phone `3710eec`.
Optional Google diagnostics, location, and watch notification permission were
left disabled.

Installed and opened the current Wear debug APK, then selected **Sync from
phone**. The watch displayed `Received 5 exercises from phone.`, stored one
workout, and rendered `Foundation A` as `Not Started · 5 exercises` with a
41-minute estimate. This is direct emulator Data Layer delivery evidence for
the existing manual schedule-sync flow. It does not validate the still-gated
Quick Start capability/request UI, final result transport, physical Bluetooth
pairing, background behavior after process/device restart, or battery impact.
The ADB bridge is session-scoped and must be recreated after emulator restart
unless Android Studio's Pair Wearable assistant restores it.

Checklist remains **44/97 (45%)** because no Stage 19 Quick Start acceptance
item is satisfied by the legacy sync result. Next implementation action remains
the Iteration 22 action: await durable pause before Save & close, adapt the
session engine to the runtime, and implement Ready/Start/Dismiss plus exact
terminal receipt cleanup before capability publication.

## Iteration 24 — 2026-09-27 — Watch Ready prompt and runtime session bridge

Status: **Code and 454x454 emulator UI validation complete; paired Quick Start,
cancellation, final-result transport, cues/TTS, and physical acceptance remain
open.**

Starting checkpoint: `051e228 PST01: Finalize session durability and replay
audit`; the uncommitted Iteration 23 emulator evidence was preserved. The
requested slice followed Iteration 22/23's recorded next action. No fetch or
push occurred.

Added a lifecycle-refreshed in-app Quick Start card over the durable transient
package. A listener-process event refreshes an already visible list without
polling. Ready offers show explicit Start and Dismiss actions; a STARTING offer
shows Resume. Start reuses the global admission gate, persists the independent
runtime and immutable Started receipt before best-effort transport, and can
recover a STARTING package whose first initialization was interrupted. Dismiss
persists its terminal record before sending its receipt.

Added `QuickStartSessionStore`, which maps the immutable phone exercise package
to the existing session engine while committing each cursor/outcome transition
through `QuickStartRuntimeStore`'s revisioned atomic write. Quick Start does not
enter the permanent date-keyed download store, publish legacy logs/snapshots,
or permit plan edits/restart that the immutable runtime contract cannot accept.
The shared session UI is reached through a request-scoped navigation route.

Fixed the documented explicit-exit race: system Back and Save & close now wait
for the pause commit before popping the navigation stack. Duplicate close taps
are serialized, failed writes retain the screen and existing error surface, and
lifecycle stop remains a best-effort fallback. A regression test holds the
write open and proves the close callback cannot fire early.

Validation passed: `gradlew.bat :wear:testDebugUnitTest :wear:assembleDebug
--no-daemon --quiet` with **151 tests, 0 failures, 0 errors**, plus the debug
APK. New tests cover Start persistence/receipt/navigation ordering, interrupted
STARTING recovery, Dismiss persistence, atomic engine/outcome advancement,
stale and plan-edit rejection, and awaited close persistence. `git diff
--check` passed.
On the 454x454 Wear OS 7 emulator the unchanged saved-workout list remained
readable, and a temporary instrumentation-only persisted offer rendered
**QUICK START / Upper Body Express / 2 exercises / Start / Dismiss** without
horizontal clipping. The temporary seed and screenshots were removed and are
not part of the checkpoint. The prior emulator pair did not reconnect after
cold boot, so this is local persisted-state UI evidence, not paired Quick Start
transport evidence; Iteration 23 remains the paired legacy-sync evidence.

The Phase 2 prompt item closes. Checklist is now **45/97 (46%)** overall and
Phase 2 is **4/17 (24%)**. Start/Dismiss/Cancel stays open because phone Cancel
is not yet wired. Capability remains intentionally unpublished. Exact next
action: implement the revisioned cancellation listener/race and native final-
result send/receipt cleanup, then publish capability and perform paired Quick
Start delivery/recovery validation. TTS remains a later Phase 2 cue-controller
slice, not part of this checkpoint.

## Iteration 25 — 2026-09-27 — Cancellation and terminal transport

Status: **Code, automated checks, and browser UI validation complete; paired
delivery/recovery and physical-device acceptance remain open.**

Starting checkpoint: `2509782 PST01: Wire watch Quick Start sessions`, clean
tree, with the branch one commit ahead of its tracked remote. The requested
iteration continued the recorded terminal-transport action. No fetch, push, or
emulator action occurred.

The shared schema now has a bounded, request-scoped cancellation payload whose
path, revision, selected watch, observed phone, and local watch identities are
validated together. The phone persists that exact cancellation before
transport, retries it after process reload while the watch is still pending,
and exposes an explicit pending/Ready cancel action in the React sheet. The
watch listener runs cancellation through the process-wide start mutex: READY
becomes a replay-safe Cancelled tombstone, while STARTING/Started wins without
being erased. A terminal acknowledgement is durable on the phone before its
request/cancel/ack Data Items are removed; duplicate acknowledgements retry a
previously interrupted cleanup.

Terminal workout transitions already froze their result with runtime state.
They now enqueue that exact result through Data Layer after the durable write.
The phone validates it against the original request and both nodes, atomically
stores the permanent result plus immutable receipt, and retries receipt delivery
after reload. The watch validates the receipt against the live result or compact
receipt tombstone, then clears runtime/package state in a restart-safe order and
removes the transport items. Failed result transport cannot roll back a
committed workout transition, and resume retries the retained result. Both phone
and watch now publish their schema-1 Quick Start capability; publication was
deliberately delayed until these terminal paths existed.

Review added two recovery corrections before closure: persisted cancellations
are retransmitted on phone-plugin load, and duplicate terminal acknowledgements
can repeat Data Item cleanup. An ambiguous React cancellation call reconciles
the durable native record instead of presenting a false retry state. No
unrelated working-tree changes were present.

Validation passed:

- `gradlew.bat :shared:test :app:testDebugUnitTest :wear:testDebugUnitTest
  :app:assembleDebug :wear:assembleDebug --no-daemon --quiet`: **77 shared, 11
  phone, and 156 Wear tests** (244 total), zero failures/errors, with both debug
  APKs built.
- `npx tsc --noEmit`, `node --experimental-strip-types
  tests/quick-start.test.mjs` (3/3), and `npm run build` passed.
- The visible local `tests/quick-start.html` fixture passed **19 checks**,
  including the pending-offer cancellation action.
- `git diff --check` passed; only the repository's existing LF/CRLF conversion
  warnings were emitted.

Per the user's instruction, the phone/Wear emulator was not opened. Therefore
this checkpoint is code completion, not paired capability, cancellation,
result-cleanup, reboot, or round-screen device proof. The last visual Wear
evidence remains Iteration 24's 454x454 Ready prompt, and the last paired
evidence remains Iteration 23's legacy schedule sync.

The Start/Dismiss/Cancel checklist item closes. Stage 19 is now **46/97 (47%)**
overall and Phase 2 is **5/17 (29%)**. Cues/TTS are still planned, not present.
Exact next action: add `WatchCueController.kt` under the existing Wear `cues`
package, the TTS adapter, persistent preferences/ledger, and focused failure/
recovery tests; then wire the foreground-only five-second start state. Run
paired capability/delivery/recovery validation separately when an emulator or
physical device session is allowed.

## Iteration 26 — 2026-09-28 — Wear cue controller foundation

Status: **Isolated cue foundation complete with Wear JVM/build evidence;
session-event wiring, settings UI, and device audio validation remain open.**

Starting checkpoint: `fcf5c62 PST01: Complete Quick Start terminal transport`,
clean tree matching the local `origin/PST01` tracking ref. No fetch, push, or
emulator action occurred.

Added an isolated `WatchCueController` that serializes cue admission, persists
the event before output, applies the existing priority rules, supports explicit
pause/start-now/restart/end/skip/navigation cancellation, suppresses TTS while
TalkBack owns speech, and bounds each speech attempt to five seconds. Visual
state remains outside the controller. Haptics continue when voice is disabled,
TalkBack is active, TTS initialization/output fails, or speech times out.

Added one atomic DataStore record for opt-in voice preferences and the
request/session cue ledger. Preferences default to voice off; all categories
default on after opt-in. The ledger survives process/store recreation, rejects
duplicate cues, switches only when a different session is admitted, and is
cleared only for the exact session after its final-result receipt is durable.
Unreadable or future state fails closed to voice off.

Added the foreground-only Android system-TTS adapter with service discovery,
system locale, accessibility speech usage, transient audio focus, route/focus
loss cancellation, TalkBack detection, distinct cue haptics, and explicit
shutdown. The manifest now declares the Android 11+ TTS service query; no
network speech service, wake lock, microphone, or background service was added.

Audit correction: the first draft gated the entire cue on voice preferences,
which would also have removed the required haptic fallback. Reservation and
exactly-once persistence are now independent of speech preference. A bounded
timeout and TTS `onStop` handling were also added so cancelled/stalled speech
cannot retain the caller indefinitely.

Validation:

- `./gradlew :shared:test :app:testDebugUnitTest :wear:testDebugUnitTest
  :app:assembleDebug :wear:assembleDebug --no-daemon --quiet` passed with 77
  shared, 11 phone, and the then-current 162 Wear tests plus both debug APKs.
- `./gradlew :wear:testDebugUnitTest :wear:assembleDebug --no-daemon --quiet`
  then passed on the final manifest/test tree with **163 Wear tests**, zero
  failures/errors, and a fresh debug APK. Seven new tests cover opt-in/fallback
  behavior, durable pre-output
  reservation, failed/stalled TTS without replay, TalkBack suppression,
  priority and explicit cancellation, exact-session ledger cleanup, and
  recovery from a separate DataStore scope.
- `git diff --check` passed before documentation closure.
- No emulator or physical watch was used. TTS engine discovery, speaker/
  Bluetooth routing, actual haptic patterns, TalkBack, and battery behavior are
  therefore code-complete foundations, not device acceptance.

The cue-controller/preferences/TTS/ledger checklist item closes. Stage 19 is
now **47/97 (48%)** overall and Phase 2 is **6/17 (35%)**. Exact next action:
wire the foreground-only persisted five-second start state and cancellation
path into Ready/Start navigation, then emit briefing/warning/Go events through
this controller without delaying the visual countdown. The settings surface,
rest events/final-countdown lock, success presentations, and device audio
validation remain separate open items.

## Iteration 27 — 2026-09-28 — Foreground Quick Start countdown

Status: **Foreground countdown and zero-boundary start complete with JVM/build
evidence; round-screen and device audio validation remain open.**

Starting checkpoint: `f1beaa0 PST01: Add Wear cue controller foundation`, clean
tree, one commit ahead of the local `origin/PST01` tracking ref. No fetch, push,
emulator, or physical-device action occurred.

Changed the Ready action so it opens a dedicated foreground countdown without
mutating the durable offer. The full-screen round layout shows a green perimeter
ring, `5` through `1`, the first exercise and target, then `GO`. Back or lifecycle
pause before zero cancels cue output, shuts down TTS, returns to the list, and
leaves the package `READY` with no runtime or Started receipt. A `STARTING`
package still exposes explicit Resume and does not replay the countdown.

At zero, a new start coordinator rechecks expiry and the global legacy-session
gate, durably commits `READY -> STARTING`, creates the active runtime, and only
then publishes the Started receipt. The controller emits the bounded briefing,
five-second warning, and Go events with attempt-specific keys. GO is shown while
optional speech finishes, so TTS cannot delay the timer or durable start.
Leaving the foreground after zero cancels/closes audio without rolling back the
started session. TalkBack receives one merged countdown/exercise/target label.

Audit corrections:

- Coroutine cancellation is now rethrown by `WatchCueController` instead of
  being converted to a haptic-only result, allowing navigation to stop obsolete
  speech promptly.
- Cue attempt identity uses an epoch deadline rather than the monotonic timer,
  avoiding key reuse after reboot while the visual timer remains monotonic.
- The lifecycle boundary moved to `ON_PAUSE`; a second zero-boundary guard
  prevents back/background races from cancelling a committed start.
- The first APK assembly found duplicate stale incremental Compose dex output.
  `:wear:clean` removed the generated duplicate; the clean rebuild passed with
  no source workaround.

Validation:

- `./gradlew :wear:clean :wear:testDebugUnitTest :wear:assembleDebug --no-daemon
  --quiet` passed on the clean tree.
- `./gradlew :shared:test :app:testDebugUnitTest :wear:testDebugUnitTest
  :app:assembleDebug :wear:assembleDebug --no-daemon --quiet` passed with **77
  shared, 11 phone, and 168 Wear tests**, zero failures/errors, and both debug
  APKs.
- Five new tests cover Ready remaining unchanged on Start, zero-boundary durable
  ordering, cancel-before-zero, expiry recheck, foreground loss after zero, and
  exact briefing/warning/Go scripts with replay suppression.
- `git diff --check` passed before documentation closure.

No round-screen emulator or physical watch was used, so countdown sizing,
actual TTS timing/routing, haptic feel, ambient behavior, and paired Started
delivery remain open device evidence.

The foreground-only five-second state/cancellation checklist item closes. Stage
19 is now **48/97 (49%)** overall and Phase 2 is **7/17 (41%)**. Exact next
action: wire rest-duration/five-second/Go cue events and the persisted final-
countdown lock into `SessionViewModel`, keeping the existing rest deadline
authoritative and rejecting `+5`, `+10`, and `+30` at five seconds or below.

## Iteration 28 — 2026-09-28 — Rest cues and durable final countdown

Status: **Rest transition cues and the persisted final-five lock are complete
with JVM/build evidence; round-screen and device audio validation remain open.**

Starting checkpoint: `33d4076 PST01: Add Wear Quick Start countdown`, clean
tree, two commits ahead of the local `origin/PST01` tracking ref. No fetch,
push, emulator, or physical-device action occurred.

Wired semantic rest-start, five-second, and Go events into the shared
`SessionViewModel` path used by downloaded and Quick Start sessions. Rest
announcements preserve the existing deadline and follow the short-rest rules;
the five-second latch is durably committed before its cue; natural zero and
**Start now** cancel obsolete warning audio before emitting Go. Pause, restart,
end, skip, and navigation also cancel obsolete output. Clearing the session
now disposes its owned TTS output.

Added backward-compatible `SessionState` fields for a stable rest-interval ID
and irreversible final-countdown latch. Existing active or paused rest records
are migrated on open. Pause/resume retains the interval and latch; terminal or
active transitions clear them. The `+5`, `+10`, and `+30` controls are disabled
and labeled at five seconds or below, while `onAddRestSeconds` independently
enforces the same exact millisecond boundary under the serialized transition
mutex. No per-second persistence was introduced.

Audit corrections:

- The first compile exposed Kotlin cross-module smart-cast restrictions in the
  recovery migration; stable local values now make the nullable checks valid.
- Session-owned system TTS is explicitly disposed with the ViewModel so the
  new production emitter cannot leak an engine after navigation.
- Added explicit simultaneous extension/threshold ordering, legacy-state
  migration, pause/resume retention, replay-safe cue ordering, and additive
  serialization compatibility coverage.

Validation:

- `./gradlew :shared:test :app:testDebugUnitTest :wear:testDebugUnitTest
  :app:assembleDebug :wear:assembleDebug --no-daemon --quiet` passed with **78
  shared, 11 phone, and 175 Wear tests**, zero failures/errors, and both debug
  APKs.
- Rest-boundary coverage verifies acceptance at six seconds, rejection at five,
  simultaneous tap/threshold ordering, retained pause/recovery lock, one
  **Start now** transition, and warning cancellation before Go. Cue coverage
  verifies exact rest/warning/Go scripts, haptics, durable keys, and shutdown.
- `git diff --check` passed before documentation closure.

No round-screen emulator or physical watch was used, so layout legibility,
actual TTS timing/routing, haptic feel, screen-off recovery, and paired delivery
remain open device evidence.

The rest-event, persisted-lock, and rest-boundary checklist items close. Stage
19 is now **51/97 (53%)** overall and Phase 2 is **10/17 (59%)**. Exact next
action: add persisted exercise-success and final workout-success presentations,
including durable **Saved on watch** state before rendering the final summary.

## Iteration 29 — 2026-09-28 — Persisted success presentations

Status: **Exercise/final success presentation and completion cues are complete
with JVM/build evidence; round-screen and physical audio validation remain
open.**

Starting checkpoint: `72e1e4e PST01: Add durable rest cues`, clean tree matching
the local `origin/PST01` tracking ref. No fetch, push, emulator, or physical-
device action occurred.

Added an additive persisted session-progress record containing ordered exercise
outcomes, per-exercise completed-set counts, and the currently presented
non-final exercise success. New and Quick Start sessions write these outcomes
with the existing atomic session transition; legacy records are migrated on
open. The Quick Start adapter projects its canonical reducer outcomes back into
the session presentation so recovered screens cannot drift from the immutable
result.

After a non-final exercise completes, active and rest screens now show a green
success mark, completed exercise name, compact completed/total progress,
explicit Completed/Skipped/Pending counts, and a TalkBack description. Rest
controls remain available. The semantic completion cue is reserved only after
the transition persists; when rest follows, its speech is merged into one
exercise-success/rest/next-exercise script. Recomposition and ViewModel
recreation do not replay it.

The former minimal completion page is replaced by a workout-success summary
with completed/skipped/pending exercise counts, completed/planned sets, elapsed
time, estimated time, and the navigation action. `resultSaved` becomes true in
the same successful terminal write that journals legacy history or freezes the
Quick Start final result. The screen therefore renders **Saved on watch** only
after that durable boundary; Quick Start separately shows **Waiting to sync**
without blocking exit. A distinct two-pulse workout-success haptic and the
persisted exactly-once completion script are emitted after commit.

Audit corrections:

- Replaced free-form persisted status strings with the shared serialized
  `ExerciseOutcomeStatus` enum and added structural/count validation before a
  stored presentation is accepted.
- Projected `resultSaved` from an existing Quick Start `finalResult` during
  recovery, so a process restart cannot regress a durably queued result to a
  false saving state.
- Added a failed-terminal-write test proving neither the saved state nor the
  completion cue becomes visible before persistence succeeds.

Validation:

- `./gradlew :shared:test :app:testDebugUnitTest :wear:testDebugUnitTest
  :app:assembleDebug :wear:assembleDebug --no-daemon --quiet` passed with **78
  shared, 11 phone, and 178 Wear tests**, zero failures/errors, and both debug
  APKs.
- New tests cover persisted exercise outcomes and recovery without cue replay,
  terminal write failure, merged exercise-success/rest wording, duplicate cue
  suppression, final success output, and Quick Start saved-state recovery.
- `git diff --check` passed; only the repository's existing LF/CRLF conversion
  warnings were emitted.

No round-screen emulator or physical watch was used, so success-screen sizing,
animation/reduced-motion behavior, TalkBack traversal, actual TTS timing, and
haptic feel remain device evidence rather than code closure.

The exercise-success/final-success and durable local-summary checklist items
close. Stage 19 is now **53/97 (55%)** overall and Phase 2 is **12/17 (71%)**.
Exact next action: implement notification permission/fallback behavior without
making notification permission a requirement for the in-app Ready prompt, then
complete TTS/settings/device behavior and paired recovery validation.

## Iteration 30 — 2026-09-29 — Quick Start notification fallback

Status: **Permission-aware Ready notification and durable in-app fallback are
complete with JVM/build evidence; device notification behavior remains open.**

Starting checkpoint: `daffebd PST01: Add Wear success presentations`, clean tree
matching the local `origin/PST01` tracking ref. No fetch, push, emulator, or
physical-device action occurred.

Added an isolated Quick Start offer notifier with a dedicated high-importance
channel. After a Ready offer is durably accepted, the watch posts a bounded
**Quick Start ready** notification containing the workout title, exercise count,
and first exercise. It expires with the locally authoritative offer TTL and
opens a fresh single-activity task on the workout list, where the existing
durable Ready card exposes Start and Dismiss.

Notification permission, global notification settings, and channel disablement
are all treated as optional delivery outcomes. Denial, disabled notifications,
or a posting failure cannot suppress the persisted package, in-app card, or
Ready receipt. Start, local Dismiss, phone Cancel, expiry, and recovered
STARTING state cancel the fixed notification ID so a stale Ready alert cannot
survive a terminal transition. Duplicate Ready delivery refreshes the same
notification instead of creating another offer or alert.

Audit corrections:

- The notification tap now recreates the single-activity task, guaranteeing it
  reaches the list's Ready card even when Settings was previously open.
- Posting remains strictly after the package write; a failed durable write is
  tested to emit neither notification nor Ready receipt.
- Notification output is best-effort and isolated from protocol acknowledgement
  transport, so permission or platform failures cannot corrupt offer state.

Validation:

- `./gradlew :shared:test :app:testDebugUnitTest :wear:testDebugUnitTest
  :app:assembleDebug :wear:assembleDebug --no-daemon --quiet` passed with **78
  shared, 11 phone, and 179 Wear tests**, zero failures/errors, and both debug
  APKs.
- New coverage verifies denied and failed notification delivery retain the
  durable in-app Ready offer and receipt, notification posting never precedes
  persistence, and both phone cancellation and zero-boundary Start clear the
  notification.
- `git diff --check` passed before documentation closure.

No emulator or physical watch was used, so the runtime permission dialog,
channel presentation, notification tap behavior, vibration/display behavior,
and round-screen Ready card remain device evidence rather than code closure.

The Phase 2 notification/fallback checklist item closes. Stage 19 is now
**54/97 (56%)** overall and Phase 2 is **13/17 (76%)**. Exact next action: add
the watch voice-cue settings and non-blocking availability state, then audit the
remaining TTS/audio-focus, TalkBack, ambient, reduced-motion, and round-screen
device behavior without claiming physical acceptance from automated checks.

## Iteration 31 — 2026-09-29 — Voice cue settings and test audit

Status: **Persisted voice opt-in/category settings and engine discovery are
complete with JVM/build evidence; runtime/device behavior remains open.**

Starting checkpoint: `298a5e8 PST01: Add Quick Start notification fallback`,
clean tree, one commit ahead of the local `origin/PST01` tracking ref. No fetch,
push, emulator, or physical-device action occurred.

Added the one-time **Use voice workout cues?** card to the watch workout list.
Voice remains off until **Enable** is chosen; **Not now** resolves the prompt
without enabling speech. The existing Settings screen now exposes the persisted
Voice cues master switch plus Start briefing, Rest announcements, Countdown cue,
and Completion cue controls. Enabling from the prompt turns on all categories;
later master changes preserve individual choices.

Added lightweight system-TTS service discovery using the manifest-visible
engine query. Settings shows a quiet **Voice unavailable** state while retaining
complete visual and haptic behavior. The prompt-resolution field is additive,
so existing cue records remain voice-off and receive the one-time choice.
Failed settings writes roll the visible selection back and surface a non-
blocking save error.

Audit corrections:

- Settings and session cue controllers use separate `WatchCueStore` instances.
  Their read-modify-write mutex is now process-wide so a preference update
  cannot race and erase an exactly-once ledger reservation, or vice versa.
- TTS discovery queries all declared engine services rather than only default-
  category services, avoiding a false unavailable result for valid engines.
- The first aggregate APK run encountered the repository's known stale
  incremental `" 2.dex"` duplicates. `:wear:clean` removed generated output;
  the clean source rebuild passed without a workaround or source rollback.

Validation:

- `./gradlew :wear:clean :shared:test :app:testDebugUnitTest
  :wear:testDebugUnitTest :app:assembleDebug :wear:assembleDebug --no-daemon
  --quiet` passed with **78 shared, 12 phone, and 183 Wear tests**, zero
  failures/errors, and both debug APKs.
- New tests cover opt-in defaults, **Not now**, category persistence and
  recreation, unavailable-engine state, failed-write rollback, additive legacy
  preference decoding, and concurrent preference/ledger preservation.
- Checklist audit confirms Wear JVM coverage for Ready, Started, Dismissed,
  Cancelled, Rejected, Expired, and duplicate delivery. Cue coverage includes
  exact scripts, thresholds, priority, cancellation, pause/resume, failure,
  process recovery, and exactly-once completion.
- `git diff --check` passed before documentation closure.

No emulator or physical watch was used. TTS initialization/locale/audio-route
reporting, actual audio focus, TalkBack behavior, ambient/reduced-motion UI,
round-screen layout, and haptic feel remain open device-sensitive evidence.

The two automated Phase 2 exit checks for receipt and cue coverage close. The
broader TTS/device-behavior implementation item remains open. Stage 19 is now
**56/97 (58%)** overall and Phase 2 is **15/17 (88%)**. Exact next action: wire
runtime TTS initialization/locale/audio-route availability back into Settings,
add explicit ambient/reduced-motion policies, then perform the round-screen
emulator review without claiming physical audio acceptance.

## Iteration 32 — 2026-09-29 — Runtime voice availability

Status: **TTS initialization, locale, audio-route, and focus outcomes now feed
the watch Settings state; device-sensitive UI and audio acceptance remain
open.**

Starting checkpoint: `08d5044 PST01: Add Wear voice cue settings`, clean tree,
two commits ahead of the local `origin/PST01` tracking ref. No fetch, push,
emulator, or physical-device action occurred.

Expanded voice availability from an installed-service yes/no probe into a
process-visible runtime state. Foreground TTS owners now report initialization
failure, unsupported current locale, missing audio output, transient focus
denial, and ready state. The Settings view observes those updates and presents
a specific quiet status while retaining the existing visual and haptic
fallback.

Registered an audio-device callback only after successful TTS and locale
initialization. Route addition/removal stops active speech, abandons transient
focus, and recomputes availability. Closing the cue output unregisters the
callback before shutting down TTS, preserving the existing foreground-only
lifecycle and avoiding a leaked route listener.

Audit findings and corrections:

- Installed-engine discovery is now labeled separately from confirmed runtime
  readiness; Settings says the runtime check completes when a workout starts
  instead of claiming that an uninitialized engine is ready.
- Reopening Settings cannot downgrade a previously observed runtime result to
  discovery-only status. A missing service can still replace stale readiness.
- Focus denial remains a haptic-only cue result and is visible as a transient
  availability outcome; speech never becomes a prerequisite for session
  progress.

Validation:

- `./gradlew :wear:clean :shared:test :app:testDebugUnitTest
  :wear:testDebugUnitTest :app:assembleDebug :wear:assembleDebug --no-daemon
  --quiet` passed with **78 shared, 12 phone, and 187 Wear tests**, zero
  failures/errors, and both debug APKs.
- New JVM coverage distinguishes initialization, locale, output-route, and
  ready outcomes and verifies that runtime updates replace discovery status in
  the Settings state.
- `git diff --check` passed before documentation closure.

No emulator or physical watch was used. Actual speaker/Bluetooth routing,
focus interaction with other watch audio, locale voice quality, TalkBack,
ambient/reduced-motion rendering, round-screen layout, and haptic feel remain
device evidence.

The combined Phase 2 TTS/device-behavior item stays open because ambient and
reduced-motion handling are not implemented and runtime audio is not physically
accepted. Stage 19 remains **56/97 (58%)** overall and Phase 2 remains **15/17
(88%)**. Exact next action: add explicit ambient and reduced-motion presentation
policies to the countdown/session success states, then run the 454x454 emulator
review when device work is allowed.

## Iteration 33 — 2026-09-29 — Ambient and reduced-motion policy

Status: **Ambient and reduced-motion presentation handling is implemented and
build-tested; round-screen and physical-device acceptance remain open.**

Starting checkpoint: `73a47c6 PST01: Report Wear voice runtime availability`,
clean tree, three commits ahead of the local `origin/PST01` tracking ref. No
fetch, push, emulator, or physical-device action occurred.

Added the stable `androidx.wear:wear:1.4.0` ambient lifecycle observer at the
single activity and exposed a shared presentation policy to every route. The
policy observes the system animator duration scale for reduced-motion changes
and records ambient burn-in/low-bit capabilities plus ambient update ticks.

Active, resting, paused, ended, and completed sessions now switch to a compact,
non-interactive ambient layout containing only essential workout state. When
burn-in protection is required, the content cycles through bounded two-dp
offsets on ambient updates. Workout list and Settings routes use a shared static
ambient fallback instead of leaving their full interactive layouts visible.
The foreground-only Quick Start countdown cancels back to its durable Ready
offer on ambient entry and briefly renders a static paused state while leaving.

Reduced motion suppresses changing/decorative countdown, rest, and completion
rings while preserving numeric state, action controls, haptics, and semantics.
Ambient views use static monochrome text, merged accessibility descriptions,
and no interactive controls or progress animation.

Audit findings and corrections:

- Activity-level ambient opt-in affects every navigation route. The initial
  slice handled workout routes only; the audit added static list/Settings
  fallbacks so no route retains a scrolling interactive surface in ambient.
- Loading and error branches originally preceded the ambient branch. Ambient
  handling now wins for those states as well, preventing blank or interactive
  error layouts during low-power mode.
- The existing `WAKE_LOCK` declaration already satisfies the ambient observer
  requirement, so no additional permission was introduced.
- TalkBack continues to suppress overlapping TTS while retaining haptics, and
  ambient/reduced-motion layouts retain explicit merged descriptions. Physical
  screen-reader behavior is still device evidence.

Validation:

- `./gradlew :wear:clean :shared:test :app:testDebugUnitTest
  :wear:testDebugUnitTest :app:assembleDebug :wear:assembleDebug --no-daemon
  --quiet` passed with **78 shared, 12 phone, and 191 Wear tests**, zero
  failures/errors, and both debug APKs.
- A final `./gradlew :wear:testDebugUnitTest :wear:assembleDebug --no-daemon
  --quiet` passed after the ambient loading/error audit correction.
- New JVM coverage verifies interactive, reduced-motion, and ambient policy
  decisions plus the complete burn-in offset cycle.
- `git diff --check` passed before documentation closure.

No emulator or physical watch was used. Round-screen clipping/readability,
actual ambient entry/update behavior, burn-in movement, low-bit rendering,
system reduced-motion changes, TalkBack order, and haptic/audio behavior remain
device evidence.

The combined Phase 2 TTS/TalkBack/ambient/reduced-motion implementation item is
now code-complete; device acceptance remains separate and the round-screen exit
check remains open. Stage 19 is now **57/97 (59%)** overall and Phase 2 is
**16/17 (94%)**. Exact next action: run the 454x454 emulator review for
Start/Dismiss, countdown, exercise-success, rest, final-success, and ambient
layouts, recording screenshots/findings without claiming physical acceptance.

## Iteration 34 — 2026-09-30 — Round-screen closure and Phase 3 recovery audit

Status: **Phase 2 round-screen exit check complete; Phase 3 event/resume wiring
and automated gates reconciled and verified. Paired and physical-device
acceptance remain open.**

Starting checkpoint: `1ca0df9 PST01: Add Wear ambient presentation policy`,
clean tree matching the local `origin/PST01` tracking ref. No fetch or push
occurred.

Installed the freshly built Wear APK on the existing round Wear OS 7 AVD and
used a temporary valid two-exercise Quick Start package. The Ready card showed
the full title, exercise count, Start, and Dismiss actions. The five-second
countdown kept its number, exercise name, target, and perimeter ring inside the
round viewport. Active and rest states remained readable; the success/rest
combination exposed the complete exercise-success summary and scrollable rest
controls. The final-success summary retained its counts, set total, elapsed and
estimated time, durable **Saved on watch** state, and scrollable return action.

Ambient validation showed static non-interactive active and final summaries
with merged accessibility descriptions. Entering ambient one second into the
countdown returned to the static Workouts fallback; waking restored the exact
Ready offer and no runtime had started. This is emulator presentation/runtime
evidence only. It does not validate physical burn-in movement, low-bit pixels,
TalkBack order, haptics, TTS routing, battery use, or paired transport.

The documentation audit found that Phase 3's first two checklist items were
already implemented but still unchecked. `QuickStartAckListenerService`
durably records acknowledgements and publishes the in-process event consumed by
`WatchQuickStartPlugin`; `useWatchQuickStart` listens for that event and
reconciles the authoritative native record on visibility/resume. Initial hook
hydration restores the latest durable native offer after recreation. These
paths were delivered in earlier iterations; this iteration reconciles their
status rather than claiming new production code.

Validation:

- Clean `./gradlew clean :shared:test :app:testDebugUnitTest
  :wear:testDebugUnitTest :app:assembleDebug :wear:assembleDebug --no-daemon
  --quiet` passed with **78 shared, 11 phone, and 191 Wear tests** (280 total),
  zero failures/errors, and both debug APKs. The latest XML result recount
  supersedes the current-summary claim of 12 phone tests; earlier iteration
  text is retained as historical evidence.
- `npx tsc --noEmit`, `node --experimental-strip-types
  pwa/tests/quick-start.test.mjs` (3/3), and `npm run cap:sync` passed.
- Real-browser fixtures passed **109 checks**: 19 Quick Start, 35 data
  integrity, 10 custom quest, 6 workout timing, 17 workout integrity, and 22
  watch sync. The expected watch-sync ACK failure log and missing favicon are
  fixture/development noise, not application failures. The isolated workflow
  smoke page also loaded successfully.
- `git diff --check` passed before documentation closure.

No production source changed. The verified checklist is now **65/97 (67%)**
overall: Phase 0 **31/31**, Phase 1 **10/10**, Phase 2 **17/17**, Phase 3
**7/12**, and Phase 4 **0/27**. Exact next action: run capability-gated paired
Quick Start delivery on the phone/watch emulators and verify Ready-to-Start
process death, cancellation/dismissal, stale replay/node binding, terminal Data
Item cleanup, and retained completion through reboot/receipt. Keep physical
TalkBack/audio/haptic/battery checks separate.

## Iteration 35 — 2026-09-30 — Paired prerequisite and legacy transport audit

Status: **Actionable legacy watch-transfer errors complete with clean automated
evidence; paired Quick Start validation blocked by the cold phone AVD's missing
official companion app.**

Starting checkpoint: `0e6312c PST01: Close Wear round-screen phase`, clean tree,
one commit ahead of the local `origin/PST01` tracking ref. No fetch or push was
made.

Launched the existing Pixel 8 and round Wear OS AVDs, installed the current
debug APKs, and restored the documented session bridge (`phone tcp:5602 ->
tcp:5601`, watch reverse `tcp:5601 -> tcp:5602`). The official watch pairing
refresh succeeded locally, but pairing status remained `Local:[643cb999]` and
`Peer:[null,false,true]`. Android Studio's Wear pairing assistant then identified
the prerequisite precisely: **Google Pixel Watch is not installed** on the
phone AVD. The phone Play Store is signed out. No Google credentials or
untrusted APK were used, both emulators were stopped, and no paired product
behavior is claimed. This supersedes interpreting the failed connection as a
Quick Start transport defect; Iteration 23 remains the latest successful paired
legacy-sync evidence.

The audit resumed a documented legacy regression from the 2026-09-11 emulator
evidence: **Send today to watch** exposed raw `Wearable.API`, status-code, and
timeout diagnostics. Added a bounded error normalizer at the native schedule
bridge. A missing/unavailable companion now tells the user to install or open
the watch companion and pair the watch; an unreachable node retains the prior
reconnect instruction; a timeout gives a retry action; unexpected failures use
a stable generic connection message. Cause inspection is bounded and uses a
locale-stable comparison. Five new phone tests cover unavailable services,
nested status, disconnected, timeout, and redacted unexpected failures.

Validation:

- Clean `./gradlew clean :shared:test :app:testDebugUnitTest
  :wear:testDebugUnitTest :app:assembleDebug :wear:assembleDebug --no-daemon
  --quiet` passed with **78 shared, 16 phone, and 191 Wear tests** (285 total),
  zero failures/errors, and both debug APKs.
- `npx tsc --noEmit`, the three Quick Start Node fixtures, `npm run build`, and
  `npm run cap:sync` passed. Iteration 34's 109 checks remain the latest
  real-browser evidence; this native-only error mapper did not change browser
  code.
- `git diff --check` passed before documentation closure.

The Stage 19 checklist remains **65/97 (67%)**: Phase 0 **31/31**, Phase 1
**10/10**, Phase 2 **17/17**, Phase 3 **7/12**, and Phase 4 **0/27**. No paired,
reboot, physical audio/accessibility, or battery item closes. Exact next action:
restore the official Google Pixel Watch companion through a user-authorized
Play Store session, or use a paired physical phone/watch; then execute the
recorded Phase 3 delivery/process-death/cancellation/replay/node-binding/
cleanup/reboot matrix before physical-device acceptance.

## Iteration 36 — 2026-10-02 — Schedule import workflow module

Status: **Schedule CSV import extraction complete and build-checked; paired
Stage 19 and physical-device acceptance remain open.**

Starting checkpoint: `6e13b60 PST01: Improve watch transport recovery audit`,
clean working tree. Stage 19 remains **65/97 (67%)**, Phase 3 **7/12**. The
next Stage 19 action still requires restoring the official phone companion or
using paired physical devices; no transport acceptance item closes here.

Implemented the next Stage 14B maintenance slice: `useScheduleImport` owns
CSV parsing, validation, replacement confirmation, active-session protection,
IndexedDB replacement, session clearing, native schedule handoff, and result
state. `App.tsx` supplies the current session and narrow update callbacks.
The existing handler was moved without changing its ordering or messages.
Backup/restore and quest workflows still remain in the composition root; this
is a focused slice, not completion of Stage 14B.

Review and validation:

- Reviewed the extraction diff against the previous handler; no persistence
  schema, native contract, UI markup, or dependencies changed.
- `npx tsc --noEmit`, `npm run build`, the three Quick Start Node fixtures,
  and `git diff --check` passed.
- In-app browser loaded the isolated workflow database and navigated to Import;
  CSV controls, backup controls, and the empty current schedule rendered.
  The subsequent local CSV file chooser stalled and its tab became unavailable,
  so successful import/cancellation/active-session browser validation is **not
  claimed**. The existing branch-level browser evidence remains Iteration 34.
- No APK, emulator pairing, physical-device test, fetch, or push was performed.

Completed: schedule import module extraction and static/build verification.
Remaining: full import interaction browser regression, backup/restore and quest
hook extraction, and the recorded paired recovery/device matrix.
Next action: verify CSV import success, cancellation, invalid input, and active
workout protection in the isolated browser fixture; then extract backup/restore
with explicit rehydration callbacks as the next maintenance module.

## Iteration 37 — 2026-10-02 — Backup transfer workflow module

Status: **Backup transfer extraction and isolated browser regression checks
complete. Paired Stage 19 and physical-device acceptance remain open.**

Starting checkpoint: `8dd6ff0 PST01: Extract schedule import workflow`, clean
working tree. Stage 19 remains **65/97 (67%)**, Phase 3 **7/12**.

Implemented `useWorkoutBackup` to own export creation/download initiation,
backup parsing and confirmation, transactional restore, native schedule handoff,
and result state. `App.tsx` supplies an explicit asynchronous `onRestored`
callback that retains the previous refresh ordering for user data, Health
Connect settings, custom exercises, quest state, playlist, cue settings, active
session, and catalog. Success is still reported only after rehydration finishes.
No schema, native contract, dependency, or user-facing UI changed. The full
Stage 14B refactor remains partial: quest actions and cross-feature restore
rehydration still live in the composition root.

Added `pwa/tests/import-workflows.html`, using a disposable IndexedDB database
and the actual React hooks with real Files. It avoids native file chooser
dependence and restores its confirmation stub/unmounts/deletes its test
database at completion. This supersedes Iteration 36's unverified **hook
interaction** status: success, cancellation, invalid CSV and active/resting/
paused protection now have browser evidence. Native file chooser interaction
itself is not validated by this fixture.

Review and validation:

- Reviewed both extracted handlers against the previous code: confirmation,
  persistence, native handoff, refresh, and result ordering are preserved.
- New browser fixture passed **15 checks**, including history preservation,
  valid/skipped CSV rows, replacement cancellation, unusable input, three live
  session states, invalid/canceled backup, committed restore visibility,
  summary reporting, and rehydration failure reporting.
- Existing browser data-integrity fixture passed **35 checks**, including
  malformed/unsupported backup rejection and transactional rollback.
- Isolated App screen navigated to Import and reported **Backup exported with
  0 schedule rows and 0 logs** after the export click, with no console errors.
  The browser download-event capture timed out; saved-file/download contents
  are not claimed as validated.
- `npx tsc --noEmit`, `npm run build`, the three Quick Start Node fixtures,
  and `git diff --check` passed.
- No native source changed; no APK build, emulator pairing, physical-device
  test, fetch, or push occurred.

Completed: backup transfer module extraction, CSV hook interaction closure,
and restore integrity regression checks. Remaining: native file chooser/export
file verification, quest workflow extraction, and paired/physical acceptance.
Next action: extract the quest workflow with regression coverage while keeping
its scheduling/reconciliation safeguards, or resume the Phase 3 paired matrix
when the official companion/paired devices are available.

## Iteration 38 — 2026-10-02 — Quest workflow module

Status: **Quest workflow extraction complete with browser and build checks;
full composition-root cleanup and paired/device acceptance remain open.**

Starting checkpoint: `f185475 PST01: Extract backup transfer workflow`, clean
working tree. Stage 19 remains **65/97 (67%)**, Phase 3 **7/12**.

Implemented `useQuestWorkflow` to own enrollment, custom quest creation/deletion,
quest-day scheduling, conflict and duplicate handling, leave/session guards,
log-driven completion reconciliation, derived day/progress presentation, and
durable quest state/history/custom-template hydration. `App.tsx` passes the
catalog, built-in definitions, playlist, workout/log data, active session, and
refresh callbacks, then wires hook results to Quests/History/Library.

Quest hydration now has one stable refresh entry point shared by initialization,
backup restore, and leave refresh; absent/unsupported state clears stale
enrollment, and initialization supplies its disposal guard. Pure total/week/day
calculations moved to `lib/quest-progress.ts`; day resolution uses the shared
calculation. Existing domain persistence and native handoff ordering remain
unchanged. No schema, dependency, native source, or user-facing copy changed.

Review and validation:

- Reviewed moved action handlers and derived state against the previous App
  implementation, including run identity, missing catalog handling, session
  guards, conflict checks, archiving, and completion replay behavior.
- New disposable-database React-hook browser fixture passed **18 checks**:
  empty/durable hydration, template authoring/enrollment, day resolution,
  enrolled-template deletion guard, invalid time/conflicts, custom identity,
  duplicate scheduling, linked paused-session leave guard, cancellation,
  completion/replay, archiving, unrelated schedule/history retention, deletion,
  and authoritative refresh.
- Existing custom quest **10**, data-integrity **35**, and import workflow
  **15** browser checks passed: **78 browser checks total**.
- Isolated App smoke check enrolled Test Mobility Program, rendered its
  day/level/load prescription, and scheduled it successfully. Ready navigation
  badges and the Start workout action updated; no console errors were reported.
- `npx tsc --noEmit`, `npm run build`, three Quick Start Node fixtures, and
  `git diff --check` passed.
- No APK builds, emulator pairing, physical-device tests, fetch, or push occurred.

Completed: quest workflow extraction and relevant regression verification.
Remaining: Library/custom exercise/playlist handlers, built-in catalog loading,
shared restore/bootstrap coordination, native file chooser/export verification,
and paired/physical acceptance. Stage 14B remains partial, with quest/import/
backup/session workflows now isolated rather than all feature workflows.
Next action: extract the Library playlist/custom-exercise workflow with
reference/deletion and schedule regression coverage; resume Stage 19 paired
recovery when official companion/paired devices are available.

## Iteration 39 — 2026-10-02 — Library workflow module

Status: **Library workflow extraction complete with browser/build checks;
shared bootstrap/restore cleanup and paired/device acceptance remain open.**

Starting checkpoint: `3fb7c44 PST01: Extract quest workflow module`, clean
working tree. Stage 19 remains **65/97 (67%)**, Phase 3 **7/12**.

Implemented `useLibraryWorkflow` for search/category/featured filtering, level
counts, playlist estimates and persistence, item addition/update/reordering,
playlist schedule saving, custom exercise creation/editing/deletion, result
state, and reference guards. Shared catalog/custom-exercise/playlist records
stay in App composition because Quests, History, initial loading, and backup
restore consume them; the hook receives explicit records and update callbacks.
No schema, dependency, native contract, or visible UI/copy changed.

Review and validation:

- Compared all seven moved action handlers with the previous App source; they
  match exactly, preserving name/ID/alias safeguards, write ordering, native
  handoff, confirmation, and schedule deduplication. Playlist persistence moved
  into the hook with the shared draft update callback in its dependency list.
- New disposable IndexedDB/React-hook browser fixture passed **18 checks**:
  combined search/category/featured filters, durable/duplicate additions,
  reorder, schedule prescriptions/load/identity, duplicate saves, invalid
  prescriptions, blank/duplicate custom names, custom persistence/catalog
  refresh, display names, rename identity/aliases/draft refresh, draft/history
  deletion guards, cancellation, editor cleanup, and unrelated schedule retention.
- Existing custom quest **10**, data-integrity **35**, quest workflow **18**,
  and import workflow **15** checks passed: **96 browser checks total**.
- Isolated App smoke check searched Test Tempo Hold, showed its saved
  prescription/load, and added the playlist to the weekly schedule. The Ready
  Today badge and Start workout action updated, with no console errors.
- `npx tsc --noEmit`, `npm run build`, three Quick Start Node fixtures, and
  `git diff --check` passed.
- No APK builds, emulator pairing, physical-device tests, fetch, or push occurred.

Completed: Library action/filter/result module extraction and relevant
regression verification. Remaining: shared bootstrap/catalog loading, restore
coordination and data ownership cleanup, native file chooser/export verification,
and paired/physical acceptance. Stage 14B remains partial; all principal
Library/quest/session/import/backup action workflows now have focused hooks.
Next action: isolate app data hydration/catalog loading and restore coordination
with regression coverage for initialization, offline fallback and restored data;
resume Stage 19 paired recovery when companion/paired devices are available.

## Iteration 40 — 2026-10-02 — Workout data hydration modules

Status: **Shared workout data, definition loading, and restore coordination
extracted and regression-checked; final composition-root and device acceptance
remain open.**

Starting checkpoint: `c4ef8c6 PST01: Extract Library workflow module`, clean
working tree. Stage 19 remains **65/97 (67%)**, Phase 3 **7/12**.

Implemented `useWorkoutData` to own shared schedule/history/catalog/custom-
exercise/playlist data, refresh callbacks, simple exercise logging, catalog
network/cache loading, built-in quest definitions, and restored Library
rehydration. Implemented `useWorkoutDataHydration` for startup repair/refresh,
schedule handoff, definition loading, quest/cue/session hydration, visibility
retry lifecycle, and backup/user-data refresh coordination. App now wires the
focused feature hooks to this shared data and coordinator.

Restore rehydration reads the committed IndexedDB custom exercises, draft and
base catalog rather than relying on a captured App catalog and backup argument.
The catalog stays sorted and absent/unsupported restored drafts reset to
defaults. Initialization retains cached-catalog fallback and disposal guards;
ordinary data renders do not restart its effect because dependencies use stable
refresh/load callbacks rather than the newly created data result object.

Review and validation:

- Reviewed data ownership, stable callback dependencies, startup ordering,
  offline fallback, disposed loads and post-commit restore ordering. Existing
  domain/native contracts and schemas are unchanged.
- New disposable-database hydration fixture passed **13 checks**: offline base/
  custom catalog, persisted draft/schedule/session callback, stable rerender,
  durable log identity/unsaved-row guard, fresh built-in/catalog loading,
  disposal guard, restored custom/draft removal, health/cue/session refresh,
  absent draft reset, and user-data refresh. Offline warning logs are expected
  fixture evidence; no console errors were reported.
- Existing data-integrity **35**, custom quest **10**, Library **18**, quest
  **18**, and import **15** checks passed: **109 browser checks total**.
- Isolated App initialized built-in and custom quest definitions plus the
  persisted Library playlist. Custom enrollment/scheduling succeeded with
  correct prescription/load, Ready badges, and enabled Start workout action;
  no console errors were reported.
- `npx tsc --noEmit`, `npm run build`, three Quick Start Node fixtures, and
  `git diff --check` passed.
- No APK builds, emulator pairing, physical-device tests, fetch, or push occurred.

Completed: shared data ownership, catalog/definition loading, startup and
backup hydration extraction. Remaining: inbound watch subscription/status and
queued-sync coordination plus Today derived presentation in App; native file
chooser/export verification and paired/physical acceptance. Stage 14B remains
partial until the remaining composition-root responsibilities are audited.
Next action: isolate inbound watch updates and queued-sync coordination with
resume/subscription cleanup regression coverage, then audit Stage 14B closure;
resume Stage 19 paired recovery when companion/paired devices are available.

## Iteration 41 — 2026-10-02 — Inbound watch updates and queued sync

Status: **Inbound watch/status/queued-sync module complete with lifecycle and
regression checks; final composition-root audit and paired/device acceptance
remain open.**

Starting checkpoint: `8e4e4dd PST01: Extract workout data hydration modules`,
clean working tree. Stage 19 remains **65/97 (67%)**, Phase 3 **7/12**.

Implemented `useWatchUpdates` for inbound watch subscription registration and
cleanup, imported log/session refresh, latest live status, timestamp-based
non-regression, and ordered watch/Health Connect queue retries with existing
count-aware feedback. App passes narrow refresh/toast callbacks and wires the
hook to startup/resume hydration and the shell online callback. Manual schedule
sending remains in the existing `useWatchSync` workflow.

The production bridge is a stable default adapter; an optional typed adapter
allows isolated lifecycle tests without native transport. Comparison against
the prior App block confirms the same status, queue and subscription ordering,
with adapter dependencies included in callbacks/effects. No schemas, native
contracts, dependencies, or user-facing UI/copy changed.

Review and validation:

- New real-React/mock-bridge fixture passed **12 checks**: initial refresh,
  stable render/subscription, stale status rejection, imported history and
  event refresh, empty queues, ordered health retry/feedback, status-read
  failure recovery, empty status clearing, unmount callbacks, and subscription
  registration resolving after unmount. Its first run exposed fixture render/
  effect timing; synchronous test mounting corrected the fixture before closure.
- Hydration fixture now passes **16 checks**, adding hidden-versus-visible
  resume retries and unmount listener cleanup to Iteration 40's 13 checks.
- Existing native watch bridge **22** and data-integrity **35** browser checks
  passed: **85 browser checks total** this iteration. Injected status/ACK
  failure console logs are expected regression evidence, not App failures.
- Isolated App initialization rendered Today, weekly plan, browser watch
  migration guidance, and disabled empty-schedule Start controls successfully.
- `npx tsc --noEmit`, `npm run build`, three Quick Start Node fixtures, and
  `git diff --check` passed.
- No APK builds, emulator pairing, physical-device tests, fetch, or push occurred.

Completed: inbound watch and queued-sync extraction plus subscription/resume
cleanup verification. Remaining: Today derived presentation and notification
permission coordination in App, final Stage 14B composition-root audit, native
file chooser/export verification, and paired/physical acceptance. No Stage 19
acceptance item closes from mock/browser evidence.
Next action: isolate Today overview calculations and shell notification
permission coordination, audit the remaining App responsibilities against
Stage 14B, and record its exact closure status; resume Stage 19 paired recovery
when companion/paired devices are available.

## Iteration 42 — 2026-10-02 — Today overview and composition-root closure

Status: **Today overview/permission modules complete; Stage 14B composition-
root code goal complete with full browser regression evidence. Native, paired
and physical acceptance remain separate and open.**

Starting checkpoint: `749ddcd PST01: Extract inbound watch update workflow`,
clean working tree. Stage 19 remains **65/97 (67%)**, Phase 3 **7/12**.

Implemented pure `buildTodayOverview` and memoized `useTodayOverview` for
weekday schedule selection/time ordering, estimated duration, latest local-date
statuses, plan progress, and local-date set counts. The overview uses one local
date for its weekday and history rather than separately reading the system
weekday; rollover/reconciliation therefore updates them together. Added
`useNotificationPermission` for shell permission initialization and explicit
request results, preserving grant/denial and unsupported-browser behavior.
No actual notification permission prompt was triggered during validation.

Composition-root audit:

- App now owns tab and History range selections and connects feature hook
  outputs to feature screens and AppShell. It has no domain functions, React
  effects, memoized domain calculations, persistence, network loading, timers
  or subscriptions inline. Cross-feature callbacks remain wiring between
  existing workflow actions (for example body metrics to Health Connect).
- Shared data/catalog, bootstrap/restore, session progression, cues, quest,
  Library/playlist, CSV import, backup transfer, watch updates, reminders and
  shell permission handling have focused hook/module boundaries.
- Added the lightweight composition-root review checklist to the React
  instructions. Stage 14B's earlier partial findings are superseded by this
  code audit; their historical evidence remains intact.

Validation:

- New overview/permission fixture passed **12 checks**: weekday/time selection,
  non-mutation, latest status ordering, local midnight counts, estimate, memo
  stability, date rollover, rest day, no initial permission prompt, grant,
  denial and unsupported API handling. A TypeScript weekday-width mismatch
  during implementation was corrected using the shared typed weekday list.
- Full browser regression audit passed **200 checks total**: new overview 12,
  data integrity 35, custom quests 10, Library 18, quest workflow 18, import 15,
  hydration 16, watch updates 12, native watch bridge 22, Quick Start 19,
  workout timing 6, and workout integrity 17. Injected failure/offline logs are
  expected fixture evidence.
- Isolated App saved its custom playlist and rendered Today with the correct
  weekday, prescription/load, one pending exercise, duration and set count,
  weekly plan and enabled Start action.
- `npx tsc --noEmit`, `npm run build`, three Quick Start Node fixtures and
  `git diff --check` passed.
- No native code, schemas, or dependencies changed. No APK build, emulator
  pairing, physical-device test, Git fetch or push occurred.

Completed: Today overview and shell permission extraction; Stage 14B code
architecture and browser regression closure. Remaining: native file chooser/
export file verification, paired Stage 19 recovery and physical acceptance,
including audio/accessibility and battery evidence. This checkpoint does not
close device acceptance or promote a future feature into the active roadmap.
Next action: verify native file chooser/export artifacts, or restore the
official companion/use paired devices and execute the recorded Stage 19
Phase 3 recovery matrix. No remaining Stage 14B extraction module is scheduled.

## Iteration 43 — 2026-10-02 — Android backup document export

Status: **Native save-file module implemented and build/regression-checked;
actual Android picker/provider and paired/physical acceptance remain open.**

Starting checkpoint: `4b308b5 PST01: Complete composition-root workflow
extraction`, clean working tree. Stage 14B remains code-complete. Stage 19
remains **65/97 (67%)**, Phase 3 **7/12**; its next implementation/acceptance
step is still paired recovery rather than another architecture extraction.

The next recorded follow-up was native file/export verification. Code review
found that backup export used a Blob download anchor on every platform with
no dedicated Android save path or provider completion acknowledgement. This
is a code-boundary finding, not a claim of observed Android download failure.
Implemented the focused `BackupExport` Capacitor plugin and `saveBackupFile`
adapter to make that boundary explicit and reviewable.

Android uses ACTION_CREATE_DOCUMENT with JSON MIME type and the generated
filename. Only the user-selected document is written, on an IO coroutine; no
storage permission or persistent URI grant is added. The plugin resolves
success only after the UTF-8 stream closes, resolves cancellation separately,
and reports picker/provider failures. Hook and native guards prevent duplicate
save dialogs. Older Android wrappers receive update guidance rather than a
browser fallback; browsers retain Blob downloads with temporary-link/URL
cleanup even when the click fails. Backup schemas/restoration are unchanged.

Validation and review:

- Reviewed plugin registration, chosen-URI write scope, output stream closure,
  IO/lifecycle handling, duplicate/cancel/error outcomes and browser routing.
- New disposable-database/mock-native export fixture passed **11 checks**:
  filename, restorable Unicode payload, success counts, cancellation, provider
  failure, duplicate taps, guard release, old-wrapper guidance, browser routing,
  browser Blob contents and temporary-link/source-data preservation.
- All existing **200 browser checks** passed: **211 total** this iteration.
  Mock picker/provider evidence does not close native runtime acceptance.
- `npx tsc --noEmit`, `npm run cap:sync` (including production build), three
  Quick Start Node fixtures and `git diff --check` passed.
- `./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon --quiet`
  passed with **16 phone tests**, zero failures/errors, and the debug phone APK.
  The initial sandbox cache-lock restriction was resolved with authorized
  Gradle execution; no build failures remained. Wear source did not change.
- No emulator/physical-device testing, paired transport, Git fetch or push
  occurred. No saved file from a real Android document provider is claimed.

Completed: Android backup save-file implementation, browser/native routing and
payload verification, phone build checks. Remaining: real Android save/cancel,
provider failure/interruption and reopen/restore of the saved artifact, native
import file chooser, paired Stage 19 recovery and physical acceptance. Earlier
export hook-only evidence is superseded for routing/payload checks, while its
unverified native provider/download observations remain open.
Next action: install the phone APK and verify JSON save/cancel plus reopen/
restore using an Android document provider, then execute the paired Stage 19
matrix when companion/paired devices are available.

## Iteration 44 — 2026-10-02 — Native backup acceptance attempt

Status: **APK installation/startup checked; document-provider acceptance
remains incomplete because the emulator could not sustain a usable UI.**

Starting checkpoint: `844d736 PST01: Add Android backup document export`,
clean working tree. Read the current plan, latest progress and React working
instructions. The next recorded step is native save/cancel and reopen/restore
validation; no additional Stage 14B extraction is scheduled.

Findings and validation:

- Started the existing Pixel_8 AVD (emulator 37.1.11, installed Android 37.1
  Play Store/16 KB ARM64 image). This runtime differs from the older API 35
  evidence; those historical checks do not establish acceptance here.
- Installed the existing debug phone APK with `adb install -r`: Success.
  No uninstall, app-data clear, backup restore or AVD wipe was performed.
- Launched MainActivity and confirmed it was the resumed activity. A fresh
  screenshot showed Pasingot's Today view with its existing weekly plan,
  covered by a **System UI isn't responding** dialog. No export was triggered.
- The first launch reported approximately 1.1 GB available host memory versus
  5 GB required, software rendering fallback, and an 8 GB host versus a
  suggested 16 GB. Selecting Wait did not produce a usable accessibility
  tree; fresh UI dumps returned null roots and a screenshot request stalled.
- Retried a cold boot with hardware graphics and requested 1536 MB RAM. The
  emulator forced RAM to 4096 MB; it booted but a fresh screenshot remained
  black apart from the navigation indicator and the UI dump again returned
  a null root. This is emulator/environment evidence, not proof of an app or
  document-provider defect. Both launched emulator processes were stopped.
- Reviewed native plugin registration, chosen-document write handling,
  cancellation/error guards, adapter platform routing and restore confirmation.
  No product source changes or demonstrated product defect resulted.
- Iteration 43's 211 browser checks, 16 phone tests, TypeScript, build/sync
  and APK checks remain the latest automated evidence; they were not rerun
  or represented as real provider checks in this documentation-only iteration.
- Reviewed the documentation diff and ran `git diff --check`. No Git fetch,
  push, paired transport or physical-device validation occurred.

Completed: progress audit, APK install/startup attempt and reviewable record
of the native validation blocker. **Acceptance iteration remains open:**
save/cancel, saved JSON contents, reopen/restore, provider failure/interruption
and native import chooser remain unverified. Stage 14B remains code-complete;
Stage 19 remains **65/97 (67%)**, Phase 3 **7/12**. No checklist item is closed
from this attempt.

Next action: use a responsive Android runtime (a stable emulator image with
sufficient host resources, or a physical phone) to execute save/cancel and
reopen/restore without clearing existing user data. Paired recovery still
requires the official companion/paired devices before its recorded matrix.

## Iteration 45 — 2026-10-02 — Pixel emulator recovery

Status: **Recovery smoke checks passed on the preserved Pixel_8 AVD; native
saved-file/restore and paired acceptance remain open.**

Starting checkpoint: `077b03b PST01: Record native backup validation blocker`,
clean working tree. User requested emulator repair and explicitly authorized
stopping Docker without restarting it. Read the current plan and latest log.

Findings and changes:

- Confirmed the Pixel_8 config requests 2 GB but Android 37.1 Play Store/16 KB
  forces 4 GB guest RAM. Disk space was sufficient. The 8 GB host had heavy
  compressed-memory/swap activity. Docker was configured for 4 GB, eight
  CPUs and had 12 running containers; no unrelated applications were closed.
- Tested hardware GLES with Vulkan disabled, two guest cores, half display
  density and no cameras. Google documents disabling Vulkan as an Apple
  Silicon workaround. With Docker running, Android booted in 31 seconds
  but still stalled and showed a System UI ANR. Graphics changes alone were
  insufficient in this observed workload.
- Stopped Docker Desktop with the user's explicit authorization; it remains
  stopped. Cold-booted the same AVD with normal display/four cores, hardware
  GLES, Vulkan disabled, no snapshots and no cameras. Boot took 17 seconds.
  No AVD wipe, app-data clear, uninstall, SDK replacement or global AVD
  configuration edit occurred.
- Added `scripts/start-phone-emulator.sh` and Android README instructions
  for that recovery profile. The script preserves app data, uses the configured
  SDK location and never stops other apps. It avoids loading/saving graphics
  snapshots and does not claim to reduce this image's enforced RAM minimum.

Validation:

- Fresh launcher screenshot rendered icons/status/navigation; fresh UI dump
  succeeded and identified the installed Workout Tracker launcher action.
- Pasingot rendered Today with existing weekly-plan data. Import navigation
  responded and showed four existing schedule rows across Wednesday/Thursday.
- Export opened the actual Android Downloads save picker with JSON filename
  and Save action; fresh picker UI dump and screenshot confirmed it. Back
  returned to the app with **Backup export canceled.** No file was saved and
  no restore was performed. Initial UI dumps during first activity launches
  sometimes returned null roots; settled reads/screens succeeded.
- Shell syntax (`bash -n`) and `git diff --check` passed. Reviewed the script
  and documentation. No app source changed, so prior automated app evidence
  remains unchanged rather than being represented as newly rerun tests.

Completed: emulator recovery under the recorded workload and actual picker/
cancellation smoke validation. Iteration 44's unusable-runtime finding is
superseded for this configuration with Docker stopped, not erased. Emulator
remains running; Docker remains stopped as requested. Resource pressure may
recur if both 4 GB virtual machines run together. The checks do not isolate
Vulkan as a root cause or establish long-duration stability.

Remaining: saved JSON contents, reopen/restore, provider interruption/failure,
native import chooser, paired Stage 19 recovery and physical acceptance.
Stage 19 remains **65/97 (67%)**, Phase 3 **7/12**; no paired checklist item
is closed. Next action: continue native backup save/reopen/restore on the
now-responsive runtime, preserving the existing user data.

## Iteration 46 — 2026-10-02 — 50% target and checkpoint audit

Status: **Progress audit complete; requested 50% threshold already exceeded.
No new implementation or device acceptance is claimed.**

Starting checkpoint: `abe7458 PST01: Add verified Pixel emulator recovery
profile`, clean working tree. Read the app plan, latest iteration, React
instructions, Quick Start checklist and feature roadmap. The user requested
progress review, next-plan implementation, audit and commit until 50%.

Findings and validation:

- Counted the Stage 19 Phase 0–4 checklist directly: **65 checked and 32
  unchecked items, 97 total (67%)**. Phase 3 remains **7/12 (58%)**.
  The active plan already exceeds the requested stopping point; there is no
  separate overall-app percentage defined by the roadmap.
- Iteration 45's recovery script and documentation are committed in `abe7458`.
  Its recovery evidence explicitly applies to the earlier Apple Silicon host.
  The current workspace runs on Windows. The local SDK is installed and lists
  `Pixel_8` and `Wear_OS_Large_Round`; `adb devices -l` reports no devices.
  No emulator was launched, app data changed, backup restored or Docker stopped.
- Reviewed plan/roadmap consistency: Stage 14B is code-complete, Stage 19
  remains at its existing count, and physical/paired acceptance remains open.
  Updated the current-state summary to identify this threshold audit and
  distinguish historical emulator evidence from current-host availability.
- Documentation diff review and `git diff --check` passed. Product source,
  dependencies and schemas are unchanged; existing automated results remain
  historical evidence rather than newly rerun checks.

Completed: progress/count audit and documentation checkpoint for the already
exceeded 50% target. Remaining: native backup save, saved JSON inspection,
reopen/restore and import chooser, provider failure/interruption, Stage 19
paired recovery and physical acceptance. No checklist item closes here.
Next action for work beyond this threshold: validate native backup on a
responsive local Android runtime while preserving existing data; restore the
official companion/use paired devices for the Phase 3 recovery matrix.

## Iteration 47 — 2026-10-02 — Windows native backup acceptance

Status: **Native Downloads save/cancel, reopen/restore, and pre-save process
interruption recovery passed. Provider write failure/interruption remains open.**

Starting checkpoint: `c4b3a6b PST01: Audit progress against 50 percent target`,
clean working tree. The user requested continued planned work, audit and
commits until the available limit. Read the plan, latest iteration and React
instructions. Next action is native backup save/reopen/restore before paired
recovery; Stage 19 remains 65/97 and Phase 3 remains 7/12.

The Windows Pixel_8 AVD uses API 35/x86_64 and 2 GB configured RAM, unlike the
previous Mac ARM64 runtime. Started it without snapshots or data wiping.
Production build/Capacitor sync and TypeScript pass. Phone Gradle tests and
APK build pass: 16 tests, zero failures/errors. Installed the current APK with
`adb install -r` successfully, retaining existing app data.

Validation and audit:

- Android Downloads ACTION_CREATE_DOCUMENT opened with the generated JSON
  filename. Save returned to Pasingot with **Backup exported with 5 schedule
  rows and 0 logs.** The provider created a 4,394-byte file with backup format
  `pasingot.workout-tracker.backup`, backup version 1 and schema version 1.
- The saved file contains five workout rows, one session event, two app-state
  records and empty remaining stores. Force-stopped/reopened Pasingot, chose
  that saved file through the native restore picker, and accepted the restore
  confirmation for the same current-data snapshot. The app reported five
  restored rows. A second native export deep-compares equal across **all seven
  stores**, including IDs, prescriptions, quest state and session history.
- Export picker Back cancellation reports **Backup export canceled.** and
  creates no third file. CSV picker opening/cancellation also passes; no CSV
  was imported and no test schedule replaced the existing data.
- Force-stopped Pasingot while the save picker was open, before choosing a
  destination. Reopening and opening a new export picker succeeds; cancel
  returns normally. This closes pre-save process interruption recovery only,
  not interruption during a provider write or arbitrary provider failures.
- First UI reads after activity launch occasionally returned null roots.
  Used fresh uniquely named dumps after settling rather than stale XML.
  Screenshots and saved backups remain under ignored
  `output/emulator-validation/`; personal backup payloads are not committed.
- Reviewed plugin registration, selected-URI stream closure, cancellation and
  guards against the observed results. No product defect or source change was
  demonstrated. Documentation review and `git diff --check` pass. No app test
  results from earlier iterations are represented as newly run browser checks.

Completed: real native save/cancel, saved JSON, native import chooser,
reopen/restore and exact store preservation on the Windows API 35 phone AVD.
Remaining: provider failure/during-write interruption, paired Phase 3 recovery,
and physical acceptance. Stage 19 stays **65/97**, Phase 3 **7/12**.
Next action: attempt the Phase 3 paired matrix on the local phone/watch AVDs.
The official companion is installed here, unlike Iteration 35's Mac phone;
startup logged a companion Bluetooth-permission crash, so pairing availability
must be established before claiming transport acceptance.

## Iteration 48 — 2026-10-02 — Windows paired recovery

Status: **Paired Ready/process-restart/Start and Cancel/Dismiss checks pass;
Phase 3 is 9/12. Receipt presentation/ledger findings require a follow-up.**

Starting checkpoint: `47e2b39 PST01: Verify native backup save and restore on
Windows`. Reviewed the plan and latest iteration. Phone API 35 and round Wear
AVDs run with existing data intact. Both native services retain matching peer
configuration initially reported disconnected. Restored the previously
documented local adb bridge and built/tested/installed the current Wear APK.

Validation and findings:

- Shared/Wear Gradle suites and Wear APK build pass: **78 shared and 191 Wear
  tests**, zero failures/errors. Iteration 47's 16 phone tests, phone APK,
  TypeScript, production build and Capacitor sync remain current for this
  unchanged-source checkpoint.
- Phone node `3710eec` and watch `cc1f21d2` connected through phone forward
  `tcp:5602 -> tcp:5601` and watch reverse `tcp:5601 -> tcp:5602`.
  Quick Start's capability gate enabled Send only after current Wear startup.
  The companion's earlier Bluetooth crash did not prevent this emulator
  network transport. No permission or account change was needed.
- The old September 27 emulator workout blocked the first send with **Watch
  already has an active workout.** Its five-exercise download remained intact.
  Ended that stale running emulator session through Pause/End/confirmation,
  retaining its ended history; its accumulated 120-hour duration is emulator
  test state, not real training or battery evidence.
- A Library single exercise reached Ready with the correct two sets, 8–10
  reps and 75-second rest. Watch force-stop/reopen retained the same offer.
  Phone Cancel returned **Cancelled on watch.** and removed the watch prompt;
  the persisted node-bound terminal record is revision 2. A second offer's
  watch Dismiss returned **Dismissed on watch.** with revision 2.
- A third offer survived watch force-stop/reopen, Start ran the visible
  countdown, and the active session/phone **Workout started on watch.**
  acknowledgement followed. Completed two sets using Start now for rest.
  Exact durable phone receipt cleared runtime and released the package.
- Removing adb forwarding alone left existing bridge sockets connected;
  completion therefore does **not** prove offline retention. Restarting the
  adb server subsequently confirmed **0 connected out of 1** on both peers.
  Offline completion/reboot remains next; no such checklist item closes here.
- Found two follow-ups: the still-open final screen says **Waiting to sync**
  even after its exact receipt cleared runtime, because its flag is fixed at
  ViewModel creation; the completed session's cue ledger remains retained
  after receipt, with no production call to its existing cleanup operation.
  Receipt-aware presentation and race-safe ledger pruning need implementation
  before closing the combined reboot/receipt/cleanup item.
- Documentation diff review and `git diff --check` pass. No app source changed
  and no physical-device, Play installation, audio or battery acceptance is
  claimed. Emulator artifacts are retained under ignored validation output.

Completed: Phase 3 watch process death between Ready and Start, and phone
cancellation/watch dismissal. **Stage 19: 67/97 (69%), Phase 3: 9/12 (75%)**.
Remaining: full replay/node/mixed-version/transport cleanup matrix,
offline completion/reboot/receipt/cue cleanup, and full legacy sync regression.
Next action: implement receipt-aware final presentation and audit cue cleanup
ordering; validate disconnected completion/reboot and exact acknowledgement
before pruning. Physical Phase 4 stays 0/27.

## Iteration 49 — 2026-10-02 — Receipt-aware watch completion

Status: **Receipt-aware final presentation implemented, 193 Wear tests and
APK build pass, and the open final screen updates correctly on the emulator.**

Starting checkpoint: `cd778be PST01: Validate paired Quick Start restart and
cancellation`. Read the updated plan and latest iteration. Stage 19 is 67/97,
Phase 3 9/12. Implement durable runtime receipt observation for the existing
session ViewModel, preserving its immutable success summary after runtime
pruning. Validate matching identity, early/late receipt and observer cleanup.

Implemented durable runtime-change observation through the existing DataStore
adapter. Only this request's validated cleared-receipt tombstone confirms sync;
missing runtime or another request's receipt cannot. The session ViewModel
clears its waiting flag while retaining its loaded summary. Observation runs
in its lifecycle scope, and later tombstone replacement cannot regress a
confirmed display. No wire contract or persistence version changes.

Validation and audit:

- All **193 Wear tests** and the debug APK build pass. Two new ViewModel tests
  cover late/early confirmation, immutable summary preservation, non-regression
  and subscriber cleanup. Expanded real-file DataStore coverage checks durable
  change observation, request identity and confirmation after reopening storage.
- The first test run found incorrect setup in the new fake-store fixtures:
  gateless initialization did not persist their initial sessions. Corrected
  the fixtures to use their existing persistence gate; all checks then passed.
- Installed the updated Wear APK. A native one-set Jumping Jacks result received
  its exact phone receipt; after scrolling the still-open final screen, it
  shows the same counts/duration and **Saved on watch**, with **Waiting to sync**
  absent. This directly supersedes Iteration 48's stale-label finding.
- Attempts to isolate transport by removing adb sockets and issuing Wi-Fi
  disable still received receipts; Wear re-enabled Wi-Fi automatically.
  These runs do not close offline/reboot retention. One reboot occurred after
  receipt, so it is not queued-result reboot evidence. The original airplane
  mode is disabled; a controlled airplane-mode test remains next.
- Audited ledger cleanup ordering: final cue reservation can follow immediate
  receipt transport, so simply clearing the ledger in the receipt callback
  can allow a late cue to repopulate it or replay. A compact acknowledged-
  session tombstone must permit one late terminal success while suppressing
  obsolete/replayed cues before production cleanup is wired.
- Source/diff audit and `git diff --check` pass. Shared and phone source is
  unchanged; no browser or physical-device validation is newly claimed.

Completed: live receipt-aware success presentation and meaningful lifecycle/
durability regression checks. Stage 19 stays **67/97**, Phase 3 **9/12**.
Next action: implement race-safe cue ledger pruning using a compact terminal
tombstone, then validate truly offline completion/reboot and receipt cleanup.

## Iteration 50 — 2026-10-02 — Safe receipt cue pruning

Status: **Exact-receipt cue pruning implemented; 198 Wear/16 phone tests,
both APKs, offline reboot retention and paired receipt cleanup pass.**

Starting checkpoint: `5cfc083 PST01: Refresh watch completion after durable
phone receipt`. Read the latest plan/progress. Stage 19 remains 67/97 and
Phase 3 9/12. Cleanup must preserve preferences, reject unrelated receipts,
remain retryable after write failure, and avoid suppressing the first success
cue when its receipt arrives before cue reservation.

Implemented a bounded acknowledged-session ID/success flag in cue state.
Exact receipt replaces that session's transient ledger with the compact
tombstone while preserving preferences. A first terminal success arriving
after receipt is still reserved once, without rebuilding the ledger; obsolete
and replayed cues stay suppressed. Another session's ledger is untouched.
Fields are additive with backward-compatible defaults; schema/wire versions
do not change. Production receipt coordination now calls this cleanup after
exact runtime/package acknowledgement and before transport-item deletion.
Failed cue writes leave transport items available for receipt retry. Phone
receipt Data Maps add a fresh delivery ID so an unchanged immutable receipt
can generate another native event on retry; the receipt payload stays exact.

Validation and audit:

- All **198 Wear and 16 phone tests**, zero failures/errors, and both debug
  APK builds pass. Five new tests cover receipt-before-success (including no
  prior cues), success-before-receipt, concurrent reservation/receipt, and
  failed-cleanup replay. Expanded real-file tests preserve tombstones and
  preferences across independent DataStore scopes. Corrected the old cleanup
  test that allowed an acknowledged exercise-success cue to replay.
- Installed both updated APKs without clearing data. Sent a one-set Jumping
  Jacks emulator fixture, started it, enabled watch airplane mode and restarted
  adb to close existing bridge sockets. Watch reported **Active default
  network: none**. Completed the set; its immutable result remained queued
  while the screen showed **Saved on watch / Waiting to sync**.
- Rebooted the watch with airplane mode still enabled. Boot completed with no
  active network. The queued result deep-compared equal before/after reboot:
  request `3b81456f-757a-497b-a98e-0ec511d08eb5`, revision 1, unchanged summary.
  Resume reopened the completed summary without starting another workout.
- Disabled airplane mode, enabled Wi-Fi and restored the adb forward/reverse
  bridge. Exact phone receipt removed runtime/package and all transient cue
  keys. Native stores show matching acknowledged request IDs, the terminal
  success flag and unchanged preferences. The same open final screen retains
  counts/duration and **Saved on watch**, with **Waiting to sync** absent.
- Airplane mode is restored to its original disabled state; Wi-Fi is enabled.
  No account or permission grant was performed. Test workouts are emulator
  history, not real training, physical audio or battery evidence.
- Reviewed scope/identity checks, persistence-before-output, idempotent retry,
  new-session protection, observer lifecycle and delivery metadata. Source/
  documentation review and `git diff --check` pass. This does not close the
  entire stale replay/node/mixed-version/transport cleanup matrix.

Completed: Phase 3 queued completion through watch reboot, exact phone receipt,
and safe transient package/cue pruning. **Stage 19: 68/97 (70%), Phase 3:
10/12 (83%)**. Remaining: full transport/replay/node/mixed-version matrix,
legacy send/download/log/live-status regression, and physical Phase 4 0/27.
Next action: inspect actual Data Items and run native replay/binding/capability
checks, then execute legacy sync regression on the paired Windows emulators.

## Iteration 51 — 2026-10-02 — Native transport replay and capability matrix

Status: **Native capability/binding/replay fixtures pass; audit identifies
resume cleanup gaps, so the combined Phase 3 acceptance item stays open.**

Starting checkpoint: `8f42bdb PST01: Prune acknowledged cues and verify offline
reboot recovery`. Read the plan/latest progress. Stage 19 is 68/97; Phase 3
10/12. Implement a test-only native phone probe and watch driver using existing
instrumentation dependencies. Require explicit opt-in, emulator hardware and
the exact connected peer; ordinary instrumented runs skip paired mutation.
Exercise production phone availability against actual watch-owned capability
Data Items, terminal cleanup and replay, mismatched target/ack identity, and
unchanged durable watch state. Restore the original capability and remove
fixture transport items. Fix the starter Android context test's obsolete
package name. No production transport or UI change is planned.

Validation and audit (completed across midnight, 2026-10-02–03):

- Both instrumentation APKs build. The paired native matrix passes on Windows
  phone API 35 and Wear API 37 emulators: seven capability states, mismatched
  acknowledgement identity, completed/wrong-target replay, fresh Ready/Cancel,
  cancellation replay, exact transport cleanup and unchanged replay state.
- Ordinary instrumentation runs skip the explicit paired fixtures and pass the
  corrected phone application-ID assertion. No production source changed.
- Initial paired runs timed out waiting for an old transport item to disappear.
  Moving the test probe off the main thread removed a harness blocking risk but
  did not resolve that timeout. After a runtime interruption, both AVDs were
  restarted without clearing data; the diagnostic build's native rerun passed.
  The failing URI was not captured, so the original timeout cause is unproven.
- Source audit found two concrete recovery gaps: failed terminal offer deletion
  has no startup retry, and startup republishes historical receipts even after
  their watch replay tombstones have been replaced. These remain follow-up
  implementation work despite the happy-path native matrix passing.
- User reported phone crashes. Crash logs identify only the Google Pixel Watch
  companion, missing `BLUETOOTH_CONNECT`; no Pasingot fatal crash or system ANR
  appeared in this boot. With explicit user approval, granted companion
  Bluetooth connect/scan on this emulator, relaunched it, and observed stable
  process/startup with no new crash. Notification permission was left unchanged.
- Added `PAIRED_EMULATOR_VALIDATION.md` with commands, prerequisites, mutation
  scope, skip behavior and emulator/physical evidence boundaries. Moved our
  emulator launch logs into ignored output. Diff review and whitespace check
  pass; earlier 198 Wear/16 phone JVM results remain the unchanged-source baseline.

Completed: repeatable native transport/capability acceptance harness and
companion crash diagnosis/setup correction. Stage 19 stays **68/97 (70%)**,
Phase 3 **10/12 (83%)** while cleanup recovery remains open.
Next action: implement durable terminal cleanup retry and reconcile actual
node-bound result/receipt items without recreating consumed historical receipts;
then run legacy send/download/log/live-status regression.

## Iteration 52 — 2026-10-03 — Recover terminal transport cleanup

Status: **Cleanup recovery code complete; 304 JVM tests, both debug APKs,
native launch/resume recovery and historical replay/capability checks pass.
Full fresh-fixture and legacy acceptance remain open.**

Starting checkpoint: `5db5278 PST01: Add native paired transport acceptance
checks`. Read the updated plan/latest progress. Stage 19 is 68/97, Phase 3
10/12. Retry cleanup of durably terminal requests, reconcile actual node-bound
result/receipt items, and preserve unrelated data. Do not recreate consumed
historical receipts. Add meaningful failure/resume and partial-delete tests,
then validate the updated phone on the paired emulators. Keep legacy and
physical acceptance separate from unit-level recovery evidence.

Implemented startup/resume transport reconciliation against actual Data Items.
Durable terminal decisions retry request/cancellation/ack deletion even after a
partial failure. Retained exact watch results retry the original immutable
receipt; consumed historical records do not recreate receipts. An exact
phone-owned receipt with no matching watch-owned result finishes orphan cleanup.
Malformed/conflicting payloads, paths and node identities remain untouched.
Records with a persisted receipt/cancellation bound to another phone are skipped
before any terminal cleanup; a replacement-phone regression preserves all items.
Recovery also imports a retained result if its listener was interrupted before
the phone write. The same validation/persistence boundary precedes receipt
delivery. Per-record failures do not block other records, cancellation propagates,
and plugin launch/resume retries are serialized and cancelled on destruction.
Validated stale acknowledgements now clean already-terminal offers without
changing durable phone status. Final-result import also retries terminal cleanup
when the original Started acknowledgement was missed.

Validation and findings:

- All **78 shared, 28 phone and 198 Wear JVM tests** pass, zero failures/errors;
  both debug APKs and instrumentation APKs build. Twelve recovery tests cover
  interrupted import, failed durable writes, per-record transport failure,
  partial deletes, reopen, consumed history, exact/conflicting receipts/results,
  node/path binding, cancellation and terminal/stale status preservation.
- The first JVM run found a malformed test fixture passed to the validating
  encoder. Constructed that intentionally invalid revision as raw wire JSON
  and added a valid-but-conflicting summary case; the corrected suite passes.
  The initial Gradle task typo (`:shared:testDebugUnitTest`) was corrected to
  the JVM module's `:shared:test` before validation.
- Started the existing Windows phone API 35 and Wear API 37 emulators without
  clearing data; restored phone `3710eec` / watch `cc1f21d2` adb transport.
  Installed current APKs. The opt-in native recovery fixture passes **OK (1
  test)** against real Data Items and MainActivity launch/background/resume.
  Injected partial deletion retries, exact orphan removal, conflicting receipt
  preservation and repeated resume without historical receipt recreation pass.
  Phone durable history is byte-identical before/after this fixture.
- Historical replay-only native checks pass on **both peers, OK (1 test)**:
  seven capability states, mismatched acknowledgement identity, completed/expired
  replay, wrong target, immutable phone status and cleaned offer/ack transport.
  Runtime and unrelated package/history fields remain exact. An expired
  replay may add its own durable refusal after bounded replay protection expires;
  the historical mode explicitly permits only that fixture's refusal history.
- The first historical run found a stale acknowledgement retained after the
  phone's Started decision; the listener correction and final native replay
  rerun supersede that finding. Reusing yesterday's completed fixture cannot
  require an unchanged whole package record after its replay window expires.
- Full fresh completion/Ready/Cancel matrix was attempted but the fresh offer
  was correctly rejected with `active_session`: cached legacy workout
  `2026-10-02` is active with one completed set. Preserved it and requested user
  direction before ending it. The failed offer remains as a terminal emulator
  fixture. Added opt-in fresh-completion and historical-only matrix modes,
  including an active-session preflight and probe cleanup on fixture failure. Fresh
  completion mode is built but not accepted at runtime in this iteration.
- Ordinary instrumentation passes the phone application-ID assertion while
  skipping paired/recovery mutations; the Wear paired fixture also skips.
  The legacy download/session JSON deep-compares exactly equal to its captured
  baseline. This is preservation evidence, not legacy transport acceptance.
  No phone fatal crash appears in this boot's crash buffer.
- Source/documentation review and `git diff --check` pass. Emulator/build logs
  are retained under ignored validation output. No React/PWA source changed;
  no new browser, physical audio, battery or Play acceptance is claimed.

Completed: Iteration 51's audited phone startup/resume cleanup gaps, interrupted
result import recovery and native recovery/stale replay validation.
**Stage 19 remains 68/97 (70%), Phase 3 10/12 (83%)**: the combined full matrix
and legacy regression items remain open. Changes are uncommitted on `PST01`;
the starting checkpoint still reads `5db5278` at the final Git audit.
Next action: resolve the active cached emulator session according to user
direction, run the full matrix with a fresh completion fixture, then validate
legacy Send today/download/log/live-status transport. Physical Phase 4 stays
0/27. Keep existing workout data intact until the user authorizes ending it.

## Iteration 53 — 2026-10-03 — Legacy paired transport regression

Status: **Validated — native legacy transport and browser regression pass;
full fresh Quick Start matrix and physical acceptance remain open.**

Starting checkpoint: `23d386a PST01: Recover terminal Quick Start transport on
launch and resume`. User requested commit then continuation. Git was clean
immediately after that commit. The full fresh Quick Start matrix still requires
the active October 2 legacy session to finish; no authorization to end that
specific workout was given. Continue the independent Phase 3 legacy regression
item: actual Send today transport, manual/scheduled download, queued log/session
event delivery, and live session status. Keep the existing cached entries and
active session exact. Use explicitly opted-in emulator-native fixtures, restore
temporary schedule/transport/live-status data and remove only fixture history.
Run relevant browser fixtures for the JS side and record distinct native,
browser, pending matrix and physical evidence.

Findings and changes:

- Added opt-in `PairedLegacySyncTest` phone probe and Wear driver. Both require
  an emulator, explicit `legacyPairedValidation=true`, and the expected sole
  connected peer. Wear preflight requires empty pending log/event/snapshot
  queues, a free cache slot, and no existing workout for the fixture date.
- Real phone Send today transport preserves all exercise/order/quest/load
  prescriptions on Wear. Manual download and the actual WorkManager
  `ScheduleDownloadWorker` receive fresh Data Items without resetting the
  downloaded entry. The worker succeeds and re-arms its normal scheduled job
  using existing settings; this does not measure wall-clock alarm timing.
- Real Wear queues/listeners deliver duplicate logs and session events only
  once to phone pending stores, with exact event metadata. Live session
  snapshots reach the phone store. The phone fixture restores its original
  raw schedule/live-status preferences and owned workout Data Item, removing
  only synthetic pending history; Wear restores its original snapshot Data
  Item and deletes only the untouched synthetic cache entry. Test work is
  cancelled; normal production scheduled-download/log retries remain.
- Existing cached entries/active legacy progress and Quick Start package/runtime
  remain unchanged. Independent post-run legacy download/session JSON compares
  exactly equal to the Iteration 52 baseline. No production source changed.
- Documented reproduction, preconditions, restoration and evidence limits in
  `PAIRED_EMULATOR_VALIDATION.md`; updated current roadmap/checklist statuses.

Validation:

- Both Android test APKs build. Corrected paired probes report **OK (1 test)**
  each on phone API 35 (`3710eec`) and Wear API 37 (`cc1f21d2`). The first run
  used a calendar date where `ScheduleCache` expects a weekday; corrected that
  fixture and awaited actual repository persistence before assertions. The
  final passing runs supersede the initial fixture failure. A successful
  phone cleanup alone does not establish paired acceptance.
- Ordinary instrumentation passes **OK (4 tests)** on phone and **OK (2 tests)**
  on Wear, with mutation fixtures skipped without explicit arguments.
- Real browser test pages pass **53 checks**: watch-sync 22, watch-updates 12,
  Quick Start 19. These use isolated IndexedDB and mock native bridges; native
  transport evidence comes from the separate paired runs. They do not establish
  a combined Android WebView UI/device workflow.
- Iteration 52's unchanged production baseline remains **304 JVM tests**
  (78 shared, 28 phone, 198 Wear) and both debug APKs. No React/PWA source
  changed. Native/browser regression and `git diff --check` pass. Build/native
  logs are retained in ignored `output/emulator-validation/`.

Completed: Phase 3's legacy Send today/scheduled download/log/live-status
regression item. **Stage 19 is 69/97 (71%), Phase 3 11/12 (92%)**. Iteration 52
is committed in `23d386a`; Iteration 53 tests/documentation remain uncommitted
on `PST01`. Final Git audit still identifies `23d386a` as HEAD.
Remaining: full fresh Quick Start cleanup/replay/binding/capability matrix;
physical Phase 4 stays **0/27**. No physical audio, battery, Play or scheduled
alarm timing acceptance is claimed.
Next action: resolve the active October 2 emulator workout according to user
direction, then run the documented full matrix with a fresh completion fixture.
Preserve existing workout data until the user authorizes ending that session.

## Iteration 54 — 2026-10-03 — Full Quick Start matrix preparation

Status: **Validated partial matrix — native binding/rejection checks pass;
fresh matrix still awaits direction for the active emulator workout.**

Starting checkpoint: `2c203d1 PST01: Validate paired legacy watch sync
regression`. User requested commit then continuation; Git is clean after the
commit. Both Windows emulators remain connected. The next Phase 3 item is the
full request/ack cleanup, stale replay, binding and capability matrix. Requested
operator direction before ending the existing active October 2 emulator
workout, as required by the validation plan. Preserve it while reviewing and
preparing independent matrix checks. Physical Phase 4 remains 0/27.

Findings and changes:

- The prior native matrix tested acknowledgement node mismatch and request
  wrong-target replay, but did not inject a valid acknowledgement under the
  wrong URI or exercise result sender/phone/schema/path rejection through real
  listeners. Added those cases to the existing opted-in Wear driver.
- Invalid result Data Items remain present without a persisted receipt; phone
  records and Wear runtime remain exact. Fixture injection first checks that
  no retained Data Item exists at the path, avoiding overwrite of user data.
  Only injected paths are removed in cleanup. Existing capability restoration
  and package/history preservation assertions remain in place.
- Phone probe accepts an optional completed-request seed, otherwise selects
  the newest durable receipt bound to the verified sole connected peer. It
  rejects a missing baseline before registering its listener. Explicit seed
  selection remains available. Fresh completion still creates its own seed.
- Updated reproduction and roadmap checkpoint markers. Iteration 53 is now
  committed in `2c203d1`; historical uncommitted notes remain dated evidence.

Validation:

- Both Android test APKs build; final Wear rebuild also passes. Installed them
  on the existing phone API 35 and Wear API 37 emulators without clearing data.
- Strengthened historical-only native matrix passes **OK (1 test) on both
  peers**, including all seven capability variants, node/path acknowledgement
  rejection, four invalid-result variants, completed/wrong-target replay,
  durable status preservation and transport fixture cleanup. Final logs are
  `iteration-54-matrix-phone-final.log` and `iteration-54-matrix-wear-final.log`
  under ignored `output/emulator-validation/`. The earlier expanded run also
  passed; final rerun adds absence-of-receipt and retained-item assertions.
- Ordinary instrumentation passes **OK (4 tests)** on phone and **OK (2 tests)**
  on Wear, skipping all opted-in mutations without flags.
- Independently extracted legacy download/session JSON is exactly equal to
  the Iteration 52 baseline. Existing active October 2 progress is preserved.
- `git diff --check` passes. No production or PWA source changes; Iteration 52's
  304 JVM tests/both APKs and Iteration 53's 53 browser checks remain the
  unchanged production baseline. No new physical-device evidence is claimed.

Completed: the previously missing native acknowledgement-path and
result-envelope/path binding checks, plus reproducible seed discovery.
**Stage 19 remains 69/97 (71%), Phase 3 11/12 (92%)**. This partial historical
mode does not close the combined full matrix checklist item. Changes remain
uncommitted on `PST01`; final Git audit identifies `2c203d1` as HEAD.
Remaining/next action: operator direction is still pending for the active
October 2 emulator workout. If authorized, end it through the normal session
End action while retaining its completed set/history, then run the documented
full matrix with `quickStartFreshCompletedFixture=true`. Otherwise preserve it
and retain the explicit fresh-matrix blocker. Physical Phase 4 remains 0/27.

## Iteration 55 — 2026-10-03 — Isolated fresh native matrix

Status: **Validated — full fresh native matrix passes on isolated AVD copies;
Phase 3 complete, physical Phase 4 still pending.**

Starting checkpoint: `af1c9bd PST01: Strengthen native Quick Start binding
validation`. User requested commit then continuation; Git is clean after the
commit. The prior emulator processes have stopped. Investigate Android
Emulator read-only copy-on-write instances and verify the source disk images
remain unchanged. Run fresh completion/Ready/Cancel only within an isolated
runtime; preserve the original active legacy workout and saved history.

Findings and changes:

- Created dedicated `Pasingot_Matrix_Phone` and `Pasingot_Matrix_Wear` AVDs
  under ignored `output/emulator-validation/iteration-55-avds/`, using the
  stopped original profiles' full disk chains and configuration. Used separate
  copies instead of relying on read-only instance behavior. Writable userdata,
  cache, encryption and SD-card paths all resolve inside the workspace copies.
  Captured SHA-256 hashes of all 16 original disk images before starting.
- The first clone boot recreated its overlay chain because version metadata
  was omitted; instrumentation reported no target app. Stopped those copies,
  restored disk chains plus `qemu-version.txt`/`version_num.cache`, and cold
  booted again. App installations and the exact saved legacy JSON then match
  the original baseline. Corrected runs supersede the initial setup failure;
  no source AVD data was modified.
- Added an opt-in Wear preparation test requiring emulator hardware and exact
  boot AVD name `Pasingot_Matrix_Wear`. It ends the explicitly selected copied
  legacy session through `SessionViewModel.onEndWorkout`, retaining its completed
  sets, exercises and other cached entries. It cannot run on the original named
  AVD and skips without `quickStartIsolatedLegacyPreparation=true`.
- Fresh completion imports successfully and prunes watch runtime/package state.
  The first full run found an orphan phone receipt still retained before replay.
  Extended the phone probe to exercise actual MainActivity launch/background/
  resume before requiring all terminal transport to disappear, using the
  production recovery boundary delivered in Iteration 52. Final full runs pass;
  cleanup is established across the real lifecycle retry boundary, not solely
  immediate listener delivery. No production source change was needed.
- Updated paired reproduction, roadmap counts and the exact next action.
  Iteration 54 is committed in `af1c9bd`; previous preservation blockers remain
  historical evidence superseded by this isolated setup.

Validation:

- Relevant phone and Wear test APK builds pass. Both copied AVDs boot with the
  original matching signatures, companion pairing and node IDs (`3710eec`
  phone API 35, `cc1f21d2` Wear API 37), using the adb bridge.
- Isolated legacy preparation passes **OK (1 test)**. Before preparation, its
  legacy download/session JSON equals the Iteration 52 source baseline exactly.
- Full fresh matrix passes **OK (1 test) on both peers**: fresh one-set native
  completion and exact phone receipt/runtime pruning; seven capability states;
  acknowledgement node/path binding; invalid result watch/phone/schema/path
  rejection without receipt; real phone launch/resume terminal cleanup;
  completed and wrong-target replay; fresh Ready/Cancel and cancelled replay;
  immutable durable status and watch package/runtime assertions.
  Final paired logs are `iteration-55-full-matrix-phone-final.log` and
  `iteration-55-full-matrix-wear-final.log` under ignored validation output.
- Ordinary instrumentation passes **OK (4 tests)** on phone and **OK (3 tests)**
  on Wear, skipping all mutation fixtures without explicit flags.
- Stopped only the two copied AVDs after validation. All **16 original disk
  hashes remain unchanged**, including a post-shutdown audit. The original
  active workout, completed set and saved history are therefore preserved.
- `git diff --check` passes. Production source is unchanged: the validated
  baseline remains 304 JVM tests, both APKs and 53 relevant browser checks.
  Native fixture completion does not establish UI countdown/cue or physical
  Bluetooth/audio/battery/Play acceptance.

Completed: Phase 3's full request/ack cleanup, stale replay, node binding and
mixed-version capability gating item. **Stage 19 is 70/97 (72%); Phase 3 is
12/12 (100%)**. Iteration 55 test/documentation changes remain uncommitted on
`PST01`; final Git audit identifies `af1c9bd` as HEAD.
Remaining/next action: physical Phase 4 **0/27**. No physical devices are
currently connected. Record phone/watch models and OS/app versions when
available, then verify connected single, playlist, reordered selection and
Today delivery before proceeding through lifecycle/audio/offline acceptance.
Dedicated test copies/history remain only in ignored local validation output.

## Iteration 56 — 2026-10-03 — Emulator UI acceptance and countdown lifecycle fix

Status: **Validated — countdown lifecycle fix, paired emulator UI, settled
visual evidence and preservation checks pass; physical acceptance stays open.**

Starting checkpoint: `fde9ddb PST01: Complete isolated fresh Quick Start
matrix`. User requested commit then continuation; Git is clean after the
commit. Phase 3 is complete. `adb devices -l` currently lists no devices, so
physical Phase 4 remains 0/27. Requested physical phone/watch models and a
debugging connection while preparing a read-only collector, reproducible
prescription/cue fixtures and evidence requirements for all 27 checks.

User direction supersedes that initial hardware-preparation next action:
**use emulator**. Restarted only the dedicated copied phone/Wear AVDs from
Iteration 55 and re-established the adb transport bridge. Original profiles
remain stopped and preserved. Physical Phase 4 counts remain distinct from
emulator observations.

Findings and changes so far:

- Added `scripts/collect-device-validation.ps1`: read-only adb inventory,
  model/OS/API/build fingerprint, package versions, battery snapshots, Git
  checkpoint/dirty state and local debug APK hashes. Optional installed-base
  APK pull/hash proves which build is installed. Emulator eligibility requires
  `-AllowEmulators`; default physical inventory excludes the copied AVDs.
  Reports are ignored under `output/device-validation/`.
- Added `docs/DEVICE_ACCEPTANCE.md` with emulator reproduction, fixture scope
  and observation/evidence requirements for all 27 Phase 4 checks.
- Added explicitly opted-in round-watch UI instrumentation and native phone
  offer/Started probes. The two-exercise fixture drives real Wear accessibility
  actions and Activity lifecycle boundaries, preserving legacy entries and
  restoring cue preferences. Reruns may finish only their own interrupted
  synthetic fixture through the normal engine; no app-data reset is used.
- First UI run reproduced a production `ConcurrentModificationException`:
  countdown cancellation navigated synchronously while NavController iterated
  its stack during Activity `ON_PAUSE`. `QuickStartCountdownViewModel.leave`
  now yields before the navigation callback; countdown/cue cancellation stays
  immediate. The fixed run cancels, waits beyond five seconds without a runtime,
  retries Start and reaches rest without that crash.
- A subsequent extension assertion read cached accessibility semantics showing
  0:57 while the actual screenshot showed 0:05. Clearing UiAutomation's cache
  before querying the tree resolves this fixture observation error. All three
  controls are observed disabled at final lock; attempted clicks preserve the
  deadline. Another fixture expectation assumed exercise advancement resumes
  directly to Active; corrected it to preserve the prior exercise's rest,
  then use actual Start now before completing exercise B. Earlier failed runs
  remain diagnostic evidence; final acceptance is pending the corrected run.

Validation so far:

- PowerShell syntax parsing and collector execution pass; default inventory
  rejects emulator physical eligibility, explicit emulator inventory selects
  the correct pair. Both pulled installed-base APK hashes equal local debug
  APKs in `20261003T134833319Z-iteration-56-patched`.
- **198 Wear JVM tests pass**, zero failures/errors; Wear production APK and
  both Android test APK builds pass after the lifecycle fix. Shared 78 and
  phone 28 JVM tests remain the unchanged baseline.
- Paired UI checks remain in progress; no physical audio, haptic, battery or
  Play acceptance is inferred from these runs.

Final validation updates (supersede the earlier in-progress UI findings):

- Corrected paired UI acceptance passes **OK (1 test) on both peers**, first
  in `iteration-56-ui-{phone,wear}-acceptance.log`. Visual review found toggle
  snapshots captured during their animation. Added fresh checked-semantics
  assertions and a settled-frame wait; final rerun also passes **OK (1 test)
  on both peers**, in `iteration-56-ui-{phone,wear}-verified.log`.
- Actual UI cancellation remains Ready with no runtime beyond the countdown;
  retry produces a phone Started acknowledgement. All three rest extensions
  increment the deadline exactly, final-five-second controls disable and
  attempted clicks cannot change it. Exercise A completes, exercise B preserves
  inter-exercise rest across explicit Pause/background/resume, and Start now
  advances correctly. Final phone result has a completion summary (no ended
  summary) with all three sets. Exact receipt prunes runtime while the loaded
  screen retains **Workout complete / Saved on watch**, with no pending-sync
  wording. Existing legacy entries remain exactly equal.
- Voice master/category changes persist across actual Activity recreation.
  Final rendered/checked states agree with stored values; original preferences
  are restored. Inspected round-screen screenshots for countdown, lock,
  advancement, summary and settings. Final 01–10 PNG/text artifacts are under
  `iteration-56-ui-artifacts-verified`; older `failure.*` files in the pulled
  directory are retained prior-run diagnostics, not final-run failures.
- Ordinary unflagged instrumentation passes **OK (4 tests) phone / OK (4 tests)
  Wear**, with opted-in mutation fixtures skipped. Final Wear test APK build
  passes. Relevant production baseline remains 304 JVM tests (198 Wear rerun)
  and both APKs; PWA production/browser files are unchanged.
- Final inventory `20261003T140730409Z-iteration-56-final` identifies copied
  phone API 35 and Wear API 37, app 1.0/code 1, emulator identity and matching
  installed/local base APK hashes. Inventory is readiness evidence, not physical
  acceptance. Stopped only `Pasingot_Matrix_Phone` and `Pasingot_Matrix_Wear`;
  all **16 original source disk hashes remain unchanged** after shutdown,
  preserving the original active legacy workout and history.

Completed: the countdown lifecycle crash fix and real Wear emulator UI
acceptance for foreground cancellation/retry, rest lock/recovery, completion
receipt/presentation and persisted cue settings. `git diff --check` and collector
syntax parsing pass. Code/emulator validation is closed for this iteration;
physical audio/haptics/routing/battery/Play checks remain open. Git audit confirms
`fde9ddb` as HEAD and only this iteration's changes uncommitted on `PST01`.

Remaining/next action: continue actual phone Library
single/playlist/reordered-selection and Today entry through the
Android WebView, comparing exact watch prescriptions. Continue emulator
lifecycle/cue cases; physical observations stay separate. Stage 19 remains
70/97; physical Phase 4 remains 0/27.

## Iteration 57 — 2026-10-03 — Phone WebView Quick Start entry acceptance

Status: **Validated — installed phone WebView entry, exact paired packages,
UI cancellation and preservation checks pass; physical acceptance stays open.**

Starting checkpoint: `d6b4153 PST01: Fix countdown lifecycle and validate
emulator UI`. Git is clean after the requested commit. Continue the user's
emulator direction with actual installed Android WebView Library single,
playlist, reordered selection and Today controls. Compare exact prescriptions
and order with native phone records and watch durable Ready packages. Preserve
original AVD disks, legacy workouts and existing phone IndexedDB data; only
owned synthetic fixtures and backed-up draft state may be changed.

Initial sandboxed emulator launch could not acquire the SDK metadata lock
outside the workspace. Stopped those launch processes and restarted only the
dedicated copied AVDs with approved SDK metadata access; no source AVD launch
or app-data reset is used. Acceptance evidence and closure remain pending.

Findings and changes:

- Added opt-in phone/Wear `PairedQuickStartEntryUiTest` fixtures. The phone
  drives the installed `https://localhost` Android WebView's actual rendered
  DOM controls and React handlers, using the production Capacitor bridge.
  It does not substitute a native offer API for the UI entry actions. Both
  peers require emulator hardware, their exact isolated AVD names and the
  sole verified counterpart. The Wear observer only reads arriving packages,
  captures the Ready list prompt and checks idle runtime/unchanged legacy data.
- Cases cover Library single; two-item playlist with edited 4 sets / 30 sec /
  25 seconds rest / 12.5 lb; two selected items reordered in the confirmation
  sheet; and a unique Today row with 5 sets / 45 sec / 17 seconds rest / 7.5 kg.
  Every phone request is compared with confirmation controls and the exact
  durable Wear Ready request, including full order/identity/source metadata.
  All requests are cancelled through the WebView's actual Cancel control before
  continuing; both UI acknowledgement wording and watch package removal pass.
- Temporary setup isolates only the backed-up playlist draft and a uniquely
  named synthetic Today row. Cleanup restores the draft, deletes only that
  owned row, compares all IndexedDB store records exactly, and restores the
  native schedule cache through the production schedule bridge. Synthetic
  cancelled request records remain only in the copies. No workout starts.
- Initial attempts failed in the fixture before any offer: reload can discard
  a pending WebView evaluation without invoking its callback. Short bounded
  evaluation waits now return to the overall page-readiness poll. Read helpers
  are reinstalled after each reload, and primary failure diagnostics are kept
  when the observer also reports an incomplete run. Final passing evidence
  supersedes these initial fixture failures; no production defect was found.
- Added fixture reproduction and scope to `docs/DEVICE_ACCEPTANCE.md`.
  DOM-driven emulator acceptance is distinct from physical touch, TalkBack,
  audio/haptics/routing/battery/Play observations.

Validation:

- Both Android test APK builds pass; current test APKs are installed only on
  the copies. Final paired entry run passes **OK (1 test) on phone and Wear**,
  in `iteration-57-entry-{phone,wear}-reload.log` under ignored emulator output.
  All four exact packages, Ready/Cancel reconciliation, restored IndexedDB/
  native schedule cache and unchanged legacy entries pass.
- Ordinary instrumentation passes **OK (5 tests) per peer**, skipping mutation
  fixtures without flags. Production/PWA files are unchanged; Iteration 56's
  304 JVM/both-APK baseline and previous browser evidence remain applicable.
- Pulled and visually reviewed phone confirmation/Ready screens for edited
  playlist, reordered selection and Today, plus watch Ready list prompts.
  Final timestamped folders are phone `1791037912727` and Wear `1791037896018`
  under `iteration-57-{phone,wear}-artifacts`; earlier folders retain initial
  failure diagnostics. Exact request JSON and baseline IndexedDB snapshot are
  retained locally. These prompts show title/count/Start; exact per-item
  prescription evidence comes from confirmation controls and durable package
  equality, not a claim that the watch list displays all prescriptions.

Final evidence update:

- Final run adds explicit Today load/unit assertions and before/after
  restoration artifacts, and again passes **OK (1 test) on both peers** in
  `iteration-57-entry-{phone,wear}-final.log`. Final phone ordinary run passes
  **OK (5 tests)**; Wear's five-test ordinary run remains unchanged.
- Latest artifact folders are phone `1791038329518` and Wear `1791038312723`
  under `iteration-57-{phone,wear}-artifacts-final`. Before/after IndexedDB
  and native schedule JSON file hashes independently match exactly, confirming
  the passing restoration assertions. Earlier successful folders remain
  historical evidence.
- Inventory `20261003T143535698Z-iteration-57-final` identifies both copied
  emulators and app 1.0/code 1, with installed/local production base APK hashes
  matching for both peers. Stopped only the verified copied AVDs; all **16
  original source disk hashes remain unchanged** after shutdown. Original active
  workout/history are preserved.

Completed: the four connected phone entry flows through the installed WebView
and real native bridge, exact Ready package comparisons, UI cancellation and
repeatable restoration evidence. Both test APK builds, paired/ordinary native
checks, visual review and `git diff --check` pass. Code/emulator validation is
closed for this iteration; no physical touch/audio/haptic/battery/Play evidence
is claimed. Final Git audit confirms `d6b4153` as HEAD and only Iteration 57
test/documentation changes uncommitted on `PST01`.

Remaining/next action: continue emulator Start acknowledgement in open/reopened phone UI and
disconnected/active/duplicate/expired entry behavior. Stage 19 remains 70/97;
physical Phase 4 remains 0/27.

## Iteration 58 — 2026-10-03 — Phone Start reconciliation and request-state acceptance

Status: **Validated — paired request states and both evidenced UI corrections pass;
physical acceptance remains open.**

Starting checkpoint: `0d48324 PST01: Validate paired phone WebView Quick Start
entry`. Git is clean after the requested commit. Continue the user's isolated
emulator direction with Start acknowledgement in open/reopened phone UI,
duplicate taps, active-workout rejection, real-deadline expiry and actual
transport disconnection. Preserve original AVD disks, legacy entries and
existing phone records; synthetic terminal history stays only in the copies.
Physical Phase 4 remains separate at 0/27.

Findings and changes (pending notes below are superseded by final validation):

- The first paired state run passed duplicate-tap reservation and the open-phone
  Started UI, then found a production rejection mismatch: another request while
  a durable Quick Start is STARTING was reported as PENDING_REQUEST. The active
  runtime was protected, but the phone's guidance described an unsent offer.
  Map the existing STARTING package to ACTIVE_SESSION; READY retains
  PENDING_REQUEST. Add a JVM regression for exact package preservation, durable
  refusal replay after recreation and unchanged same-request replay semantics.
- All 199 Wear JVM tests and the Wear production/test APK builds pass after
  the correction. Full paired UI, actual deadline and offline checks remain
  pending. Initial failed-run artifacts remain historical diagnostics; cleanup
  ended only that run's owned synthetic workout through the normal engine.
- The corrected paired run reaches both completions, exact active refusal,
  reopen reconciliation and actual expiry: the stale watch Start control opens
  "Could not start / Quick Start is no longer ready", with no runtime. Phone
  renders expiry. The fixture incorrectly tried to cancel after expiry, then
  waited for an Expired receipt that this action cannot produce. Native code
  correctly refuses expired cancellation; remove that invalid expectation and
  skip expired requests during failure cleanup. Retain this failed fixture run
  as historical evidence; rerun the full preservation/closure assertions.
- Airplane mode plus removal of the ADB mappings alone left native discovery
  reporting a connected peer for 45 seconds in this run. Mapping/airplane
  state was restored in finally. Stopping only the verified copied Wear AVD
  closed the actual transport; disconnected phone UI then passes OK (1 test),
  with actionable wording, disabled Send and unchanged native request records.
  Restart the same copy and verify no stale package/runtime before the final
  paired state run. This supersedes socket-removal-only assumptions here.
- Final paired state run passes OK (1 test) on each peer, with two receipts,
  exact active runtime preservation, all prior native/IndexedDB records retained,
  deadline expiry and idle watch. Ordinary instrumentation passes seven tests
  per peer. Visual review then finds an expired Ready offer still displays
  Cancel request even though native cancellation correctly rejects expiry.
  Hide that invalid control and guard the hook with the same expiry predicate
  used for status wording. Preserve live Ready cancellation and Started status.
  Add browser deadline-boundary/render checks and opt-in native restoration
  verification against the already-expired real request. PWA cache version is
  advanced so installed browser shells receive the correction. Closure awaits
  browser/native verification and final original-disk audit.

Final validation and closure:

- Full paired state run passes OK (1 test) on both peers in
  `iteration-58-state-{phone,wear}-final.log`. Two actual Start/Complete flows
  produce exactly two immutable receipts; duplicate Send creates one offer,
  active refusal preserves the exact runtime, every prior native/IndexedDB
  record is retained and watch legacy entries remain equal throughout.
- Expiry uses the actual five-minute deadline plus thirty-second skew. The
  stale Wear Start action renders "Could not start / Quick Start is no longer
  ready", with no runtime/result. Phone expiry is derived from the deadline
  while its original Ready acknowledgement may remain durable. The fixture's
  invalid post-expiry cancellation expectation is superseded by this pass.
- Actual copied-watch shutdown passes disconnected phone UI OK (1 test) in
  `iteration-58-disconnected-phone-watch-stopped.log`; restart/reconnection
  passes read-only idle package/runtime verification OK (1 test) in
  `iteration-58-reconnected-wear.log`. Airplane/mappings were restored.
- TypeScript, production PWA build/Capacitor sync, three Quick Start Node
  tests, and all 22 Quick Start browser checks pass. Headless Edge uses a new
  workspace-only profile and the local fixture; it confirms live cancellation,
  expired wording and absent expired cancellation. Earlier sandbox subprocess
  failures are superseded by the approved build/browser runs. Both production
  APKs and current Android test APKs build; all 199 Wear JVM tests pass. Shared
  78/phone 28 JVM baselines are unchanged, not newly rerun here.
- The updated phone APK passes native restoration of the actual expired offer,
  with no Cancel control and exactly unchanged native records, OK (1 test) in
  `iteration-58-expired-phone-final.log`. This targeted test validates the
  later PWA fix; the full paired run preceded that display-only correction.
  Final ordinary instrumentation passes OK (8 tests) on phone and OK (7 tests)
  on Wear, with mutation fixtures skipped when flags are absent.
- Final paired captures are phone `1791040580075` and Wear `1791040570138`
  under `iteration-58-{phone,wear}-artifacts-final`. Reviewed phone Started,
  active refusal, reopened Started and expiry screens, and Wear expiry rejection.
  Updated phone expiry capture `1791041413129` under
  `iteration-58-expired-artifacts` visibly has no Cancel button. Disconnected
  capture `1791040476872` shows actionable wording and disabled Send. Earlier
  failed fixture captures/logs remain historical evidence in ignored output.
- Inventory `20261003T153052529Z-iteration-58-expiry-final` identifies API 35/
  API 37 emulator models and app 1.0/code 1; installed/local production APK
  hashes match on both peers. Stopped only the verified copied AVDs and the
  owned local fixture server. All 16 original source disk hashes match exactly
  in `iteration-58-source-disk-audit.json`, preserving original workout/history.

Completed: emulator phone Start/request-state acceptance, actionable active
rejection and expiry cancellation correction, native/browser regression and
preservation audits. Relevant checks and git diff --check pass; this iteration's
code/emulator validation is closed. Physical touch/audio/haptic/routing/TalkBack/
battery/Play acceptance remains open at 0/27; Stage 19 stays 70/97 (72%). Final
Git audit confirms 0d48324 as HEAD; only Iteration 58 code/test/docs remain
uncommitted on PST01.

Remaining/next action: continue isolated emulator short-rest cue sequences,
success recreation and ambient/screen-off recovery, comparing durable cue
ledger and session progress. Keep physical audio quality/routing, battery and
Play evidence separate. Use the opted-in fixtures in DEVICE_ACCEPTANCE.md.

## Iteration 59 — 2026-10-04 — Short-rest cues and lifecycle recovery

Status: **Validated — short-rest cues, success recreation and actual ambient
rest recovery pass; physical acceptance remains open.**

Starting checkpoint: `b9b8431 PST01: Validate Quick Start states and fix expiry
controls`. Iteration 58 was committed at the user's request; the working tree
was clean afterward. Continue the isolated emulator direction with short-rest
cue sequences, success recreation and ambient/screen-off recovery. Preserve
source AVD disks, unrelated workouts and phone records. Physical Phase 4 stays
0/27; Stage 19 remains 70/97.

Next action: extend the opted-in paired UI fixture to compare durable cue ledger
and progress across actual short-rest and lifecycle transitions, then run the
relevant native/build checks and source-disk preservation audit. Code completion
and emulator acceptance remain pending until those checks pass.

Findings/validation in progress:

- Both test APK builds pass, the exact isolated AVDs boot and connect, and all
  16 original source disk hashes match before launch.
- Added an eight-exercise fixture with 0/3/5/6/8/10/12/20-second rests, durable
  ordered cue assertions, exercise/workout-success recreation and actual
  sleep/wake diagnostics. Voice preferences are restored and legacy entries
  remain guarded. The initial run found a test-only null assumption: initial
  SessionState has no progress projection yet, while the authoritative runtime
  outcomes already contain zero completed sets. Compare those outcomes instead.
  Only the owned interrupted fixture was ended through the production engine;
  the phone probe passes preservation assertions. Full acceptance is pending.
- Corrected run passes all seven short-rest cue sequences and exercise-success
  recreation, then exposes a production ambient visibility gap. Actual power
  state is Dozing and the activity's real ambient callback sets ambient=true,
  but RESUMED lifecycle alone leaves the rest timer running. It reserves Five
  seconds/Go and advances rest while dozing; outcomes/progress are unchanged.
  SessionScreen now gates foreground visibility with both lifecycle and the
  existing presentation interaction policy. Ambient transitions cancel output
  and suspend redraws without pausing the session or shifting the deadline.
  Wake catches up through the existing deadline engine. Rerun/build/JVM checks
  are pending; initial failure artifacts are retained as historical evidence.

Final validation and closure:

- Final paired run passes **OK (1 test) on phone and Wear** in
  `iteration-59-recovery-{phone,wear}-final.log`. Seven first-set rest durations
  (0/3/5/6/8/10/12 seconds) produce the exact ordered production cue keys.
  Activity recreation while paused preserves exact runtime/ledger; the
  exercise-success header returns after Resume. Completing all eight exercises
  produces exactly 16 completed sets and one immutable phone result/receipt.
  Every prior native phone request record remains exact, with only one new
  fixture added; all watch legacy entries remain equal.
- Actual sleep enters **Dozing** and the production ambient callback sets
  **ambient=true**. The reviewed screenshot shows the static Pasingot ambient
  rest display. Runtime and cue ledger remain exact past the 20-second deadline;
  independently compared before/hidden artifact SHA-256 hashes match. Wake
  catches up to set 2 with unchanged progress/outcomes and exactly one new Go
  reservation, without the obsolete Five seconds warning. This supersedes the
  corrected run's evidenced ambient visibility failure after the production fix.
- Workout-success Activity recreation retains **Workout complete / Saved on
  watch**, the exact phone result and the unchanged compact acknowledged cue
  tombstone after runtime pruning. It emits no additional completion reservation.
  This is Activity recreation, not a claim of terminal process-death recovery.
- All **199 Wear JVM tests** pass, including the strengthened hidden-rest
  regression (exact resting state, output cancellation, no hidden warning and
  one catch-up Go). Wear production and both Android test APKs build. Ordinary
  instrumentation passes **OK (8 tests) per peer**, skipping mutation fixtures
  without flags. Crash buffers are empty and `git diff --check` passes. Phone
  production/PWA/shared code is unchanged; their prior baseline remains current,
  rather than claiming a fresh browser or phone JVM run.
- Final artifacts are `recovery-1791075079768` under ignored
  `iteration-59-ui-artifacts-final`; reviewed exercise-success, actual ambient,
  recovered active set and recreated final summary screenshots. Inventory
  `20261004T005319467Z-iteration-59-final` identifies API 35/API 37 and app
  1.0/code 1, with installed/local production base APK hashes matching per peer.
  Initial fixture/ambient failure artifacts remain historical diagnostics.
- Stopped only the exact verified copied AVDs. All **16 original source disk
  hashes** remain unchanged in `iteration-59-source-disk-after.json`, matching
  the pre-launch audit. ADB shows no remaining device. Original workout/history
  and source profiles are preserved; synthetic history remains only in copies.

Completed: short-rest durable cue acceptance, success Activity recreation,
actual ambient rest/deadline recovery and the evidenced foreground visibility
correction. Relevant build/JVM/native/visual/preservation checks pass, closing
this iteration's code/emulator validation. Stage 19 remains **70/97 (72%)**;
physical Phase 4 remains **0/27**. Final Git audit confirms `b9b8431` as HEAD;
Iteration 59 code/test/docs are validated and uncommitted on `PST01`.

Remaining/next action: continue isolated emulator countdown ambient
cancellation/retry and active-set screen-off recovery, then process-death
success recovery before the phone receipt. Audible speech overlap, voice
intelligibility, TalkBack/route changes, physical haptics, battery and Play
acceptance still require separate evidence. The cue fixture deliberately uses
voice disabled, restores original preferences and claims durable reservation
and visual recovery only.

## Iteration 60 — 2026-10-04 — Countdown ambient and process-death success recovery

Status: **Validated — countdown/active ambient, staged success process recovery
and exact reconnection receipt pass; physical acceptance remains open.**

Starting checkpoint: `573ad28 PST01: Fix ambient session visibility and validate
cue recovery`. Iteration 59 was committed at the user's request and Git was
clean afterward. Continue on the exact dedicated AVD copies; preserve source
disks, legacy entries and original phone records. Stage 19 remains 70/97 and
physical Phase 4 stays 0/27.

Next action: add staged opt-in UI checks for actual countdown ambient
cancellation/retry and active-set sleep/wake, then force-stop between stages
to verify exercise-success and offline final-success recovery. Reconnect for
an exact final receipt/preservation check. Code and emulator acceptance remain
pending until build/native/visual/source-disk checks pass.

Findings/validation in progress:

- Added staged opt-in methods to the paired UI/phone probe classes. Exact AVD
  gates, prior-record/legacy equality, changed process-ID assertions and
  request-specific durable runtime/cue witnesses prevent conflating Activity
  recreation with process death. Transitions use real UI controls; only the
  caller force-stops the exact copied app between stages. Voice preferences
  are restored per stage.
- Both updated Android test APKs build; source-disk pre-launch audit passes
  all 16 hashes. Exact copied AVDs boot and the bridge is restored. Countdown/
  active-ambient paired preparation is running; later offline/reconnect stages
  and closure remain pending.
- Paired preparation passes OK (1 test) on both peers: actual ambient cancels
  countdown before zero, retry reserves one Briefing/Five seconds/Go sequence,
  and active ambient sleep/wake preserves exact runtime/cues. An owned paused
  exercise-success witness is retained for the next phase. The first offline
  stage stops at its empty-connected-nodes precondition: Wear discovery still
  claims the stopped copied phone after 45 seconds. No session action occurs.
  Retain that environment failure; enable airplane mode only on the copied
  watch, remove its bridge mapping and cold-restart it before retry. Restore
  its recorded airplane setting and bridge for final receipt acceptance.
- After copied-watch airplane mode/mapping isolation and reboot, the offline
  exercise stage passes OK (1 test): a different process reads the exact paused
  witness, restores exercise-success through real Resume and adds no success
  reservation. It completes two sets offline and saves one final result/cue.
  A subsequent confirmed force-stop kills a live copied app process; the final
  recovery stage passes OK (1 test) in another process with exact runtime/cues
  and real Workout complete / Saved on watch / Waiting to sync UI. Reconnecting
  the same copied phone for final receipt/preservation is pending.

Final validation and closure:

- Paired preparation passes **OK (1 test) per peer** in
  `iteration-60-preparation-{phone,wear}.log`. Actual ambient entry cancels
  countdown before zero with READY/no runtime/no Go; retry reserves one new
  Briefing/Five seconds/Go sequence and the phone acknowledges Started. Actual
  active-set ambient sleep/wake preserves exact runtime and cue ledger.
- Offline exercise recovery passes **OK (1 test)** in
  `iteration-60-offline-exercise-isolated.log`. Cold-restarted copied Wear has
  no native connected nodes and reads the exact paused exercise-success witness
  in process **2113**, after preparation process **2889**. Real Resume restores
  the success header without a new reservation, and normal Complete set saves
  two completed sets and one final result while the phone is stopped.
- A live copied app process **2236** is confirmed killed by force-stop, with
  no remaining PID. Final-success recovery then passes **OK (1 test)** in
  `iteration-60-offline-final.log`, process **2301**, preserving the exact
  final runtime/cues and rendered Saved on watch / Waiting to sync summary.
  Independent SHA-256 comparisons of before/after runtime and cue witnesses
  match exactly. Exercise-phase live-process force-stop evidence (PID 3066)
  is retained too; the later reboot clears stale native connectivity. This
  establishes real fresh-process recovery, separately from Activity recreation.
- Restoring the same phone copy, original airplane setting **0** and bridge
  passes **OK (1 test) per peer** in `iteration-60-receipt-{phone,wear}.log`.
  The phone receives the exact offline final result, independently compared
  as full JSON, and has exactly one new request/result receipt. Every prior
  native record stays exact. Wear prunes runtime/package/transient cues,
  retains the acknowledged success tombstone and original preferences, and
  preserves every legacy workout entry. No duplicate completion is created.
- Both test APK builds pass. Ordinary instrumentation passes **OK (9 tests)**
  on phone and **OK (12 tests)** on Wear, skipping mutation fixtures without
  their explicit flags. Crash buffers are empty and `git diff --check` passes.
  Production/PWA/JVM code is unchanged; Iteration 59's 199 Wear JVM tests and
  existing shared/phone/browser baselines remain applicable, not freshly rerun.
  No additional production defect is established by this iteration.
- Artifacts are `lifecycle-81d99292-af13-4464-bb9b-35557f5de63d` under ignored
  `iteration-60-ui-artifacts-final`, with phone before/after records under
  `iteration-60-phone-artifacts-final`. Reviewed cancelled ambient placeholder,
  active ambient, recovered exercise-success and offline final-summary captures.
  Initial stale-discovery timeout remains historical environment evidence.
- Inventory `20261004T012816837Z-iteration-60-final` records API 35/API 37,
  app 1.0/code 1 and matching installed/local production APK hashes per peer.
  Airplane setting restoration is checked before shutdown. Stop only exact
  verified copies; all **16 source disk hashes** remain unchanged in
  `iteration-60-source-disk-after.json`. ADB reports no remaining devices.

Completed: repeatable actual countdown/active ambient acceptance and staged
exercise/final-success fresh-process recovery, offline completion and exact
reconnection receipt/pruning. Relevant build/native/visual/preservation checks
pass, closing code/emulator validation for Iteration 60. Stage 19 stays
**70/97 (72%)** and physical Phase 4 stays **0/27**. Final Git audit confirms
`573ad28` as HEAD; only Iteration 60 test/docs are validated and uncommitted.

Remaining/next action: continue emulator foreground voice availability and
category/TalkBack suppression, plus reduced-motion rest/success presentation.
Keep speech intelligibility/overlap, physical speaker/Bluetooth routing,
tactile delivery, battery and Play acceptance separate. This iteration uses
voice disabled for driven transitions and restores preferences per stage.

## Iteration 61 — 2026-10-04 — Foreground voice and reduced-motion acceptance

Status: **Validated — native voice/TalkBack/fallback and paired reduced-motion
UI pass; physical acceptance remains open.**

Starting checkpoint: `965becd PST01: Validate ambient and offline process
recovery`, committed at the user's request with a clean working tree.
Continue on the exact isolated AVD copies. Stage 19 stays 70/97; physical
Phase 4 stays 0/27.

Next action: exercise actual Android TTS availability and native accessibility
suppression, category gating and duplicate prevention; run the existing real
paired session/settings UI flow with system animator scale zero and verify
rest/success presentation and unchanged session timing. Preserve source disks,
existing workout data and system preferences. Record emulator limitations
separately from physical speech, haptics, routing and battery acceptance.

Findings in progress:

- Source pre-launch audit passes all 16 hashes. Native foreground and actual
  Google TalkBack runs each pass one test: six enabled cues report SPOKEN;
  category/voice suppression retains native haptic calls and duplicate
  controller/store recreation emits nothing. These initial runs precede the
  production fix below and will be repeated on the final APK.
- Disabling only the copied image's Google TTS package exposes a real defect:
  availability stays CHECKING and the native test times out. Installed SDK
  TextToSpeech source confirms missing-engine failure can call onInit inside
  construction. The old callback reads an unassigned tts field and returns;
  the later init block reports CHECKING. Move CHECKING before construction and
  defer callback processing to the main handler, ignoring closed owners.
  Relevant builds/JVM/native/UI checks remain pending. Original package,
  accessibility and animator settings will be restored before shutdown.
- Fixed APK passes the missing-engine regression (one native test) and all
  199 Wear JVM tests, with production/test APK builds passing. Reduced-motion
  UI attempts exposed harness resets of system animator scale across automation/
  lifecycle changes and legitimate cleanup of the earlier owned synthetic
  fixture. Retain these failures. Apply actual scale zero at each validation
  point, wait for the Activity observer, use a stable automation connection,
  and baseline phone preservation only after exact checks of unrelated records
  and normal owned-fixture cancellation/ended-result receipt. Final paired UI
  and final-APK available-engine/TalkBack runs remain pending.

Final validation and closure:

- Final production APK passes **OK (1 test)** in each of
  `iteration-61-native-{foreground-fixed,talkback-fixed,unavailable-fixed}.log`.
  Available TTS reports AVAILABLE and all six enabled cue kinds return SPOKEN.
  Category-disabled and voice-disabled kinds make no speech calls; all 18
  events reserve before native haptic output. Each duplicate is rejected after
  controller/store recreation without speech or haptic output. Real Google
  TalkBack with native touch exploration makes zero speech calls for all 18
  events while retaining haptic calls. Missing-engine discovery reports
  SERVICE_UNAVAILABLE; initialization promptly reports INITIALIZATION_FAILED,
  and all requested speech falls back. Isolated serialized persistence and
  short Go utterances cover dispatch, not full-script intelligibility or
  physical tactile quality. No production preferences/session state is changed
  by the native controller fixture.
- Final paired presentation passes **OK (1 test) per peer** in
  `iteration-61-diagnostic-{phone,wear}.log` (final successful diagnostic run).
  Actual zero-scale Activity policy suppresses decorative progress at rest,
  exercise/final success and recreated settings validation points. Exact
  +5/+10/+30 deadlines, final-five extension lock, paused progress, complete
  result, receipt and pruning still pass with voice enabled and TTS unavailable.
  Real settings display No system voice service and visual/haptic fallback.
  Category/voice preferences persist across recreation and are restored.
  Earlier scale-reset timeouts and owned-fixture cleanup failures remain
  historical harness evidence, superseded by stable connection, actual setting
  application, asynchronous policy observation and scoped cleanup baselining.
- Phone full-JSON comparison independently confirms all **43 prior records**
  exact, **44 after**, receipts **20 -> 21**, for final request
  `d7c46fae-e2fd-49f0-a410-a153c87d48de`. Only owned interrupted synthetic
  fixtures are normally ended/cancelled before the preservation baseline.
  Wear legacy entries remain exact. Retained artifacts are
  `iteration-61-ui-artifacts`, `iteration-61-phone-artifacts` and
  `iteration-61-native-final-artifacts`; reviewed static rest, success/saved
  summary and unavailable-voice guidance. Exercise-success heading semantics
  pass; that capture is scrolled to the next exercise/rest controls.
- Production Wear and both final test APK builds pass. All **199 Wear JVM
  tests** pass with zero failures/errors. Ordinary instrumentation passes
  **9 phone / 13 Wear tests**, mutation fixtures skipped without flags. Crash
  buffers are empty. PWA/shared/phone production code is unchanged; their prior
  baselines remain applicable rather than freshly rerun.
- Inventory `20261004T015904012Z-iteration-61-final` records API 35/API 37,
  app 1.0/code 1, matching installed/local production APK hashes. Original
  Google TTS package state is restored to default (enabled=0), accessibility
  service setting to absent, accessibility/touch exploration to 0, animator
  scale to 1.0 and airplane mode to 0. Only exact verified copied AVDs are
  stopped; all **16 source disk hashes** remain unchanged in
  `iteration-61-source-disk-after.json` and ADB reports no remaining devices.

Completed: synchronous TTS initialization correction and repeatable native
availability/category/voice/TalkBack/duplicate/fallback acceptance, plus paired
reduced-motion rest/success/settings and exact record preservation. Relevant
build/JVM/native/visual/settings/preservation checks pass, closing Iteration 61
code/emulator validation. Stage 19 stays **70/97 (72%)**, physical Phase 4
stays **0/27**. Final Git audit keeps `965becd` as HEAD; Iteration 61 changes
are validated and uncommitted.

Remaining/next action: continue emulator native speech cancellation on pause,
Start now, navigation and end, followed by audio-focus/language fallback.
Full-script speech intelligibility/overlap, physical speaker/Bluetooth routing,
tactile delivery, battery and Play acceptance retain separate physical evidence.

## Iteration 62 — 2026-10-04 — Native speech interruption and language fallback

Status: **Validated — native interruption/focus/language and durable action
wiring checks pass; actual voice-enabled UI and physical acceptance remain open.**

Starting checkpoint: `eb2be9e PST01: Fix TTS initialization and validate native
voice cues`, committed at the user's request with a clean working tree.
Continue on the isolated Wear AVD copy; preserve source disks and all durable
session/preferences/history. Stage 19 remains 70/97; physical Phase 4 is 0/27.

Next action: exercise actual in-flight TTS cancellation for pause, Start now,
navigation and end, priority replacement, native focus loss/denial where the
platform permits a repeatable fixture, and unsupported-language fallback.
Use native playback/focus observations and an isolated cue store; distinguish
output/controller acceptance from real UI action wiring and physical audio.
Record findings, changes, checks, completed/remaining work and next action.

Findings and validation in progress:

- All 16 source hashes match before launch; only the exact Wear copy is booted.
  Initial native cancellation/replacement/focus-loss and zz-ZZ language tests
  pass (two executed methods, locked-focus method skipped). Android isSpeaking
  is observed before interruption; prompt fallback, stopped playback, duplicate
  suppression and a fresh SPOKEN cue are verified. This establishes native
  output/controller behavior, separately from actual UI action acceptance.
- A copied-emulator-only locked-focus fixture passes one explicit test. It
  adopts shell identity and uses the framework's internal focus-for-call API
  with hidden-API checks disabled only for that instrumentation invocation.
  This requests a native focus lock without placing a telephone call. Native
  output reports AUDIO_FOCUS_UNAVAILABLE/HAPTIC_ONLY, reserves before fallback,
  rejects the duplicate and returns SPOKEN for a fresh event after release.
  Cleanup drops shell identity and releases only its own fixture focus.
- Add final exact cue/runtime/legacy JSON witnesses and focus-stack cleanup
  assertions; add meaningful JVM pause/end tests that hold the durable commit
  and verify no early cancellation, plus failed-write preservation. Final
  build/JVM/native/ordinary/preservation checks remain pending. No new
  production defect is established by the initial native checks.

Final validation and closure:

- Final guarded harness passes **OK (3 tests)**, all methods executed, in
  `iteration-62-native-validated.log`. Actual Android isSpeaking precedes
  PAUSE/START_NOW/NAVIGATION/END cancellation. Each pending emit returns
  HAPTIC_ONLY within 1.5 seconds, native playback stops, duplicates stay silent
  and a fresh cue returns SPOKEN. Reported cancel-plus-recovery times are
  651/678/656/680 ms; these include the subsequent short Go utterance.
  Priority replacement returns old HAPTIC_ONLY/new SPOKEN and suppresses replay.
- A real competing transient focus request stops the active utterance, and
  release permits a fresh SPOKEN cue. The explicit native focus lock establishes
  AUDIO_FOCUS_UNAVAILABLE/HAPTIC_ONLY, duplicate suppression and SPOKEN recovery
  after unlock. The copied-emulator system fixture makes no telephone call;
  adopted shell identity/hidden-API allowance are scoped to instrumentation,
  and its lock is released. Final focus-stack assertions and dumps establish
  no remaining native cue or fixture owner after close.
- zz-ZZ process locale establishes LANGUAGE_UNAVAILABLE/HAPTIC_ONLY with no
  playback or replay. Restoring en_US creates a fresh AVAILABLE/SPOKEN owner;
  the original process locale is asserted in cleanup. No persistent system
  language setting is changed. These are output/controller tests, separately
  from actual UI cancellation or physical speech/routing quality.
- Each method's full cue/runtime/legacy JSON before/after is exactly equal.
  Independent SHA-256 comparisons match all three in
  `iteration-62-preservation-validated.json`. Native evidence is retained in
  `iteration-62-validated-artifacts`, including focus during playback, the
  real lock, final release and six JSON witnesses. Fixtures use isolated cue
  persistence and reject existing active/paused workouts and native focus
  owners; existing production preferences/history stay untouched.
- Final Wear test APK build passes. All **201 Wear JVM tests** pass with zero
  failures/errors, including two new tests that hold pause/end commit gates
  and fail writes: speech cancellation waits for successful persistence, and
  failed actions preserve active state/history without cancellation. Existing
  Start now/navigation wiring checks remain green. Ordinary instrumentation
  passes **OK (16 tests)** with mutation flags absent. Crash buffer is empty
  and `git diff --check` passes. No production source/APK change is needed;
  prior production/shared/phone/PWA baselines remain applicable, not rerun.
- Watch-only inventory `20261004T042851279Z-iteration-62-final` records API 37,
  matching installed/local Wear production APK hash and app 1.0/code 1. The
  phone is intentionally not booted; the collector's pair-selection readiness
  does not establish paired acceptance for this native-only iteration.
  Accessibility remains absent/0, touch exploration 0, animator scale 1.0,
  airplane mode 0. Only the verified Wear copy is stopped; all **16 source
  disk hashes** remain unchanged in `iteration-62-source-disk-after.json`.
  ADB reports no remaining devices.

Completed: repeatable native speech interruption/replacement, focus-loss/denial
and language fallback/restoration acceptance, plus durable pause/end action
wiring regression tests and exact preservation witnesses. Relevant build/JVM/
native/settings/focus/source checks pass, closing Iteration 62 code/emulator
validation. No new production defect is established. Stage 19 stays
**70/97 (72%)** and physical Phase 4 stays **0/27**. Final Git audit confirms
`eb2be9e` as HEAD; Iteration 62 test/docs are validated and uncommitted.

Remaining/next action: actual voice-enabled Wear UI cancellation on pause,
Start now, back/navigation and end, with native playback/focus observations
and exact cue/history checks. Full-script intelligibility/overlap, physical
speaker/Bluetooth/call routing, tactile delivery, battery and Play acceptance
remain separate physical evidence.

## Iteration 63 — 2026-10-04 — Voice-enabled UI interruption acceptance

Status: **Complete — code/emulator validation passed; physical Phase 4 remains open.**

Starting checkpoint: `f14785e PST01: Validate native speech interruption and
fallback`, committed at the user's request with a clean working tree.
Continue on exact copied phone/Wear AVDs. Stage 19 remains 70/97; physical
Phase 4 remains 0/27. Preserve source disks, prior native records, downloaded
entries and cue preferences.

Next action: drive Pause, Start now, Back/navigation and End through real Wear
controls while production rest speech is in flight. Read native playback and
focus state without replacing the output; verify exact runtime/ledger changes,
partial ended result/receipt, preservation and preference restoration. Actual
speaker/Bluetooth quality, tactile delivery, battery and Play remain separate.

Implementation findings: the first paired run proves live native Pause and
Start now, but fails an incorrect harness expectation that Back pauses RESTING.
Production intentionally preserves the exact running rest/deadline on Back;
foreground recovery uses that deadline. Failure snapshots retain two completed
sets, exact ledger/progress, and the Workouts Resume action. Correct the test to
require exact saved rest, native silence/focus release and no cue replay when
reopened. No production defect is established. First logs/artifacts remain
historical evidence; the owned failed fixture is normally ended with a receipt,
and prior preferences restored. Final acceptance is still pending.

Paired/native validation completed:

- Final phone and Wear runs pass **OK (1 test)** each, in
  `iteration-63-phone-speech-ui.log` and `iteration-63-wear-speech-ui.log`.
  Request `73da43bf-3a30-436e-90d4-9979b2c7c78d` uses the real production
  SessionViewModel/controller/AndroidTtsCueOutput, observed read-only through
  the existing navigation ViewModelStore. No speech output, listener, rate,
  engine or session transition is replaced/injected by the UI driver.
- Four actual native isSpeaking/focus witnesses precede real UI actions:
  Pause, Start now, Back, and Pause before End. Pause cancels and preserves
  progress/ledger; resume reserves a fresh rest deadline; Start now reserves
  exactly one new GO and the native queue/focus returns idle. Back stops speech
  and preserves the exact RESTING runtime/deadline with two completed sets.
  Reopening through Workouts Resume neither reserves nor replays that rest.
  Native playback/focus idle checks use 1.5-second bounds, with 3 seconds for
  the legitimate short Go replacement. These bounds begin at the post-action
  durable-state observation, not an end-to-end action-latency measurement.
- End is available only on PAUSED: actual Pause/Resume creates a fresh spoken
  rest, Pause cancels it, and the real End confirmation leaves the paused
  runtime/ledger exact until confirmation. The resulting endedSummary contains
  **2/6 completed sets**, summary is absent, a phone receipt arrives and runtime/
  package prune. Native output/focus remains idle, with no workout-success
  replay. This verifies the actual safe End UI path, separately from Iteration
  62's direct in-flight native END cancellation.
- Independent preservation audit `iteration-63-preservation-validated.json`
  proves **45/45 prior phone records exact**, records **45 -> 46**, receipts
  **22 -> 23**, exact legacy-entry JSON and exact restored preference JSON.
  The failed first synthetic fixture's ended result is retained in that
  baseline; no app data/history is reset. Eleven audio-stack witnesses match
  the four speaking owners and seven released/idle points.
- Both Android test APK builds pass. Ordinary instrumentation with opt-in flags
  absent reports **OK (9 phone tests)** and **OK (17 Wear tests)**; guarded
  mutation methods skip normally. Both crash buffers are empty. No production
  defect/change is established, so the prior 78 shared/28 phone/201 Wear JVM,
  production APK and PWA baselines remain applicable, not rerun this iteration.
- Final artifacts are retained in `iteration-63-validated-wear-artifacts` and
  `iteration-63-validated-phone-artifacts`; reviewed round screenshots show
  Pause, Workouts Resume, End confirmation and Workout ended clearly. Paired
  inventory `20261004T045645887Z-iteration-63-final` records phone API 35/Wear
  API 37, app 1.0/code 1 and installed production hashes matching both local
  APKs. Inventory readiness alone is not pairing acceptance; the explicit
  two-peer tests above establish that evidence. Wear accessibility is absent/0,
  touch exploration 0, animator scale 1.0; native focus is released.

Final source-disk audit is pending after stopping only the two verified copies.
Relevant acceptance/build/checks pass; iteration closure awaits that audit and
final Git check. Next: actual voice-enabled short-rest/exercise-transition UI
matrix with native playback/countdown ordering and exact history checks.
Physical speech intelligibility/overlap, speaker/Bluetooth/call routing, tactile
delivery, battery and Play remain separate.

Final closure supersedes the pending audit above: all **16 source disk hashes**
match the original baseline in `iteration-63-source-disk-after.json`, after
stopping only Pasingot_Matrix_Wear/Phone. ADB reports no remaining devices.
`git diff --check` passes. Final Git audit confirms `f14785e` as HEAD; only the
Iteration 63 test/docs changes remain, validated and uncommitted. Completed:
real voice-enabled UI interruption/replacement, exact Back/rest recovery and
safe partial End/receipt acceptance, with native focus and preservation
witnesses. Stage 19 remains **70/97 (72%)**, physical Phase 4 **0/27**.

## Iteration 64 — 2026-10-04 — Voice-enabled short-rest and transition acceptance

Status: **Complete — code/emulator validation passed; physical Phase 4 remains open.**

Starting checkpoint: `02ee2f7 PST01: Validate voice-enabled Wear UI interruption`,
committed at the user's request with a clean working tree. Continue on the
exact copied AVD pair; Stage 19 remains 70/97, physical Phase 4 remains 0/27.

Next action: enable all production cue categories and drive 0/3/5/6/8/10/12/20-
second rests and exercise transitions through the actual Wear UI. Observe the
existing native TTS owner/controller without replacing output or scripts;
verify durable cue/countdown order, unchanged deadlines/progress, terminal
receipt and no success replay after recreation. Preserve prior records,
legacy entries/preferences and original source disks. Native playback ordering
is emulator evidence, separately from physical intelligibility/overlap,
routing, tactile, battery and Play acceptance.

Implementation: the guarded phone fixture creates eight two-set exercises
with 0/3/5/6/8/10/12/20-second rests. Wear uses real controls and natural
countdown expiry, with every voice category enabled. A read-only 20-ms sampler
records native isSpeaking and the production controller's current cue key;
these are correlated observations, not physical overlap/quality measurements.
The harness checks exact cue sequences/deadlines, native warning/Go/rest
playback, naturally preempted short success, full result/receipt, terminal
recreation silence and preservation. The first compile exposed a nullable
sampler reference; it is corrected in test code, with rebuild pending.
All 16 original source disks match before launch; copied AVD identities,
boot completion, bridge and Wear accessibility/animator baselines are verified.

First paired attempt: durable Started arrived while the countdown Go overlay
was still opening the session screen, so the read-only observer found no
SessionViewModel yet. Add the actual Complete set readiness wait before native
observation; this is a harness synchronization correction, not a production
defect. The owned fixture is normally ended with a receipt and preferences
restored; first logs/artifacts are retained as superseded evidence. Native
matrix acceptance remains pending.

Production finding: second paired run passes rests 0/3 and same-exercise 5,
but the five-second exercise transition reserves its warning while the native
queue remains speaking with controllerKey=null. The timeline retains the
old success -> null/speaking -> idle -> Go sequence. Android QUEUE_FLUSH can
deliver an old stop callback to the newly installed listener; the current
adapter finishes/unfocuses on any stop/error and even treats old Done as failure.
This is a production callback ownership defect, not a script/ledger assertion
to relax. Fix every callback to match its utterance and finish once, and guard
native stop/focus release with a unique speech lease under one lock so old
completion/cancellation cannot affect a replacement. Add JVM regression cases
for stale/absent IDs and completion/cancellation duplicates. Rebuild production
Wear and test APKs, rerun Wear JVM/native interruption and the paired matrix.
The owned failed fixture is normally ended/receipted; earlier logs/artifacts
and original preferences/history remain preserved. Acceptance stays pending.

Fix validation: the production Wear APK and both test APK builds pass;
all **204 Wear JVM tests** pass with zero failures/errors (three added callback
regression cases). Native adapter callbacks now match their utterance, complete
once, and only the current unique lease releases focus. Explicit cancellation
finishes the owned coroutine without relying on delivery to a replaced
listener; old completion/timeout cannot stop a newer queue. Focus-loss/route
cancellation uses the same ownership path. The production APK and Wear test
APK are installed only on the verified copy. Native regression and final
paired matrix acceptance are pending; earlier failed artifacts remain retained.

Final acceptance/checks:

- Final paired runs pass **OK (1 test)** on both peers in
  `iteration-64-phone-voice-matrix.log` and `iteration-64-wear-voice-matrix.log`.
  Request `adcc7833-1c57-4924-818c-eb7668fcc894` completes all eight exercises,
  **16/16 sets**, a completed summary and the exact phone result receipt.
  Runtime/package/ledger prune and terminal success does not replay through
  actual Activity recreation, with native output/focus idle.
- **15 phase checks** cover eight same-exercise rests and seven natural
  exercise transitions. Same-exercise <=10 seconds omit REST; 12/20 require
  REST/FIVE_SECONDS/GO. Transitions reserve EXERCISE_SUCCESS/FIVE_SECONDS/GO
  (zero rest has only success), allowing short success preemption at <=5.
  All required native playback samples occur with the corresponding production
  controller key. The previously failing five-second transition now observes
  the real warning through playback and then Go. Final native observations
  cover **36 distinct cue keys**, including workout success. Independent
  `iteration-64-native-thresholds.json` validates all **26 warning/Go samples**
  against their thresholds across both rest and transition phases. Sampling
  correlates native state/controller identity; it does not measure physical
  sound intelligibility, acoustic overlap or latency.
- Independent `iteration-64-preservation-validated.json` proves **48/48 prior
  phone records exact**, records **48 -> 49**, receipts **25 -> 26**, exact
  original legacy-entry JSON and exact restored preference JSON. The two
  normally ended failed synthetic fixtures remain in that baseline/history;
  data is never cleared. Wear/phone evidence is retained in
  `iteration-64-validated-wear-artifacts` / `iteration-64-validated-phone-artifacts`.
- Native regression passes **OK (3 tests)** with both explicit flags after
  installing the fix. Pause/Start now/navigation/end stop actual speech and
  allow fresh SPOKEN output; priority replacement returns old HAPTIC_ONLY/new
  SPOKEN, rejecting duplicates. Real focus loss/locked denial and language
  fallback/recovery pass. All three cue/runtime/legacy before/after witnesses
  remain exact in `iteration-64-native-preservation.json`; native focus lock
  and shell identity are released. No call is made. Reported cancel-plus-short-
  recovery times are 675/678/657/657 ms, separately from UI action timing.
- Final production Wear/test builds and all **204 Wear JVM tests** pass;
  three new cases cover stale IDs, null IDs and duplicate/cancellation
  completion. Ordinary checks with flags absent pass **9 phone / 18 Wear**;
  guarded mutation methods skip normally. Both crash buffers are empty,
  screenshots show readable exercise success and 16/16 saved completion,
  and `git diff --check` passes. Shared 78/phone 28 JVM and PWA baselines remain
  applicable, not rerun because this production change is confined to Wear TTS.
- Paired inventory `20261004T053503122Z-iteration-64-final` records phone API 35/
  Wear API 37, app 1.0/code 1 and matching installed/local production APKs.
  Wear hash is now `46F8C23A388D37E144C7DA2E2FC5B58E3E0977A5D472D6CCF8F73C8E3DF12C27`;
  phone hash remains `21F9C264A1311BED0BD0FD94FD3BA52DAE730E4158CE90140B2DE02A0C6DF0C0`.
  Wear accessibility remains absent/0, touch exploration 0, animator scale 1.0,
  and final native focus has no production owner. Only the verified copied
  AVD pair is stopped. Final source-disk audit is pending.

Completed code/emulator checks: native callback/ownership fix, stale-callback
regressions, natural voice-enabled rest/transition/result/recreation acceptance
and preservation witnesses. Closure awaits the disk audit/final Git check;
Stage 19 stays **70/97 (72%)**, physical Phase 4 **0/27**. Next: voice-enabled
ambient/background cancellation and deadline recovery with actual native
playback/silence, exact saved progress/ledger and result checks. Physical
intelligibility/overlap, speaker/Bluetooth/call routing, tactile, battery and
Play acceptance remain separate.

Final closure supersedes the pending disk audit above: all **16 original disk
hashes** match the baseline after stopping only the verified copies, in
`iteration-64-source-disk-after.json`. ADB reports no remaining devices.
Final Git audit confirms `02ee2f7` as HEAD; only Iteration 64 source/tests/docs
remain, validated and uncommitted. All relevant build/JVM/native/paired/UI/
preservation/crash/settings/focus/source checks pass, closing this iteration.

## Iteration 65 — 2026-10-04 — Voice-enabled ambient/background recovery

Status: **Validated on the isolated paired emulators — test/docs uncommitted;
physical acceptance remains separate.**

Starting checkpoint: `c7bdf5a PST01: Fix TTS callback ownership and validate rest
speech`, committed at the user's request with a clean working tree. Continue
only on the exact copied AVD pair; Stage 19 remains 70/97, physical Phase 4 0/27.

Next action: actual Sleep/ambient and Home/background during native rest and
exercise-success speech, hidden deadline passage without playback/new keys,
foreground-only Go catch-up and durable active-set pause on Home. Read the
on-screen production controller/TTS and native focus without replacing output;
use real controls and shell key events. Preserve exact saved progress/deadlines,
prior phone records, legacy entries, preferences and all original source disks.
Physical intelligibility/overlap, routing, tactile, battery and Play stay separate.

Implementation: the guarded phone fixture has four Lifecycle sets followed
by one Final exercise, with 12/0-second rest and all cue categories enabled.
Wear observes the existing production controller/TTS/native focus during
actual Sleep and Home events. It polls exact runtime/ledger and native silence
past three hidden rest deadlines; foreground catch-up must add only Go.
Home during spoken catch-up Go must durably pause set 3 and reopening/resuming
must not replay. Actual exercise-success speech is also interrupted by ambient
entry, then all five sets complete with a receipt. No production change is
planned unless acceptance establishes a defect. Original 16-disk audit passes
before launch; copied AVD identities are verified. Test builds are pending.

First-run findings: test APK builds pass. Actual ambient-rest and Home-rest
interruption/recovery pass with 125/122 hidden exact-state/silence observations;
Home during Go durably pauses set 3 and reopening/resuming does not replay.
The later success observation times out because the harness accepted transient
idle immediately after Start now's durable ACTIVE commit, before asynchronous Go
dispatch. It tapped Complete set while the higher-priority Go was becoming
active; success was correctly rejected by cue priority. Retain the failed run
and its normally ended/receipted owned fixture. Require actual Start now Go
playback before waiting for idle and advancing. No production defect is
established; acceptance remains pending.

Final acceptance/checks:

- Corrected paired runs pass **OK (1 test)** on each peer in
  `iteration-65-phone-voice-lifecycle.log` and
  `iteration-65-wear-voice-lifecycle.log`. Request
  `edd7224c-7ae6-4ae3-8bb9-c18a7d96c9d9` completes **5/5 sets**, with a completed
  summary, exact phone receipt and runtime/package/ledger pruning.
- Actual Sleep during REST, Home during REST and Sleep during EXERCISE_SUCCESS
  each target observed native playback/current controller kind/focus. Output
  and focus become idle within the 1.5-second post-transition observation bound.
  **119/117/125** hidden observations retain exact runtime/progress/outcomes,
  deadlines and cue ledger while native output remains silent past each deadline
  plus 1.5 seconds. Wake/actual same-Activity reorder adds exactly one deadline-
  bound Go per recovery, without a late warning or success replay. Native Go
  playback is observed on each return. Bound/sampling evidence does not measure
  acoustic silence or end-to-end UI action latency.
- Actual Home during live catch-up Go durably pauses set 3 with exact progress
  and outcomes, stops speech and releases focus. Reopening stays PAUSED;
  real Resume returns ACTIVE with the same ledger and no Go replay. Start now's
  observed Go completes before advancing to exercise success. Screenshots show
  paused set 3, ambient rest, exercise completion and 5/5 Saved on watch.
- Independent `iteration-65-preservation-validated.json` proves all **50/50 prior
  phone records exact**, records **50 -> 51**, receipts **27 -> 28**, byte-exact
  legacy-entry/preference JSON, exact three hidden-state/ledger witnesses and
  only deadline-bound Go additions. The normally ended first failed fixture
  remains in that baseline/history. Final Wear/phone artifacts are retained in
  `iteration-65-validated-wear-artifacts` / `iteration-65-validated-phone-artifacts`;
  first-run logs/artifacts remain separately preserved.
- Both test APK builds and corrected Wear test rebuild pass. Ordinary checks
  with explicit mutation flags absent pass **9 phone / 19 Wear**, with guarded
  methods skipped normally. Crash buffers are empty; accessibility is absent/0,
  touch exploration 0, animator scale 1.0 and final current native focus has no
  production owner. Shared 78/phone 28/Wear 204 JVM and PWA baselines remain
  applicable and are not rerun because only test harness/docs change.
- Inventory `20261004T061220351Z-iteration-65-final` records phone API 35/
  Wear API 37, app 1.0/code 1 and matching installed/local production APK hashes.
  Wear remains `46F8C23A388D37E144C7DA2E2FC5B58E3E0977A5D472D6CCF8F73C8E3DF12C27`;
  phone remains `21F9C264A1311BED0BD0FD94FD3BA52DAE730E4158CE90140B2DE02A0C6DF0C0`.
  Only the verified copied pair is stopped; final original-disk audit is pending.

Completed code/emulator checks: guarded voice-enabled lifecycle harness, real
native interruption/deadline/pause/receipt acceptance and preservation witnesses.
No production change is needed. Closure awaits source audit and final Git check.
Stage 19 remains **70/97 (72%)**, physical Phase 4 **0/27**. Next: voice-enabled
fresh-process recovery around exercise/final success, exact offline runtime/cues,
no replay and one receipt. Physical intelligibility/overlap, routing, tactile,
battery and Play acceptance remain separate.

Final closure supersedes the pending source audit/Git check above: all **16
original source-disk hashes** match their baseline after stopping only verified
copies (`iteration-65-source-disk-after.json`); ADB reports no remaining devices.
Independent `iteration-65-focus-validated.json` proves current native focus
present before and absent after all four interruptions. Installed/local APK
hashes match and both crash buffers are empty. `git diff --check` passes;
final Git audit confirms `c7bdf5a` as HEAD with only these Iteration 65 test/docs
changes, validated and uncommitted. All relevant checks pass, closing this
iteration. The earlier pending labels remain historical evidence.

## Iteration 66 — 2026-10-04 — Voice-enabled fresh-process success recovery

Status: **Validated on the isolated paired emulators — test/docs uncommitted;
physical acceptance remains separate.**

Starting checkpoint: `f02974d PST01: Validate voice-enabled ambient and background
recovery`, committed at the user's request with a clean working tree.
Continue the documented emulator lane on only the exact copied AVD pair.
Stage 19 stays 70/97, physical Phase 4 0/27.

Next action: stage actual voice-enabled exercise success and offline final
success, terminate the app process between stages, then verify fresh PID,
exact saved runtime/cues/result, native silence/no replay and one receipt after
reconnection. Preserve every prior phone record, legacy workout, preference,
source AVD disk and failed-run evidence. Production changes require evidence
of a defect; physical speech quality/routing/tactile/battery/Play remain separate.

Implementation: a new exact-copy/peer opt-in creates an owned two-set Process
A/B fixture and records original preferences/legacy entries. Preparation
observes production EXERCISE_SUCCESS speech/focus then real Pause, saving exact
paused runtime/cues with voice still enabled. Existing staged process methods
accept the new explicit mode while preserving their historical voice-off path.
Fresh initialized production output must remain idle for 20 observations on
paused reopen/Resume and offline final reopen, with exact ledger/no replay.
Offline final completion must first reach native WORKOUT_SUCCESS playback.
The final exact-result receipt stage restores original preferences and compares
every prior phone record. No production change is planned. Original 16-disk
audit and verified copy boot pass; both test APK builds are pending.

Preparation checks: both test APK builds and final Wear build pass. Both paired
preparation methods pass OK (1 test). Request
`927613b4-043b-40e4-936a-ac1913948f3b` reaches native EXERCISE_SUCCESS/focus,
then actual Pause saves exact progress/cues with enabled voice and no replay.
The instrumentation runner leaves no live app PID at exit; explicitly relaunch
only the copied app's Home (no Resume), confirm a live PID and force-stop that
process. This is a harness/process boundary finding, not a production defect.
Retain that proof separately from the preparation PID, then isolate/reboot the
copied watch to clear cached peer discovery. Offline recovery remains pending.

First offline attempt: native peer isolation and initial exact paused runtime/
cue reads pass, but a delayed cold-boot `ChargingComposeActivity` opens over
the app and changes its lifecycle to STOPPED during UI recovery. The method
times out waiting for its control; no fresh-output/recovery checks run. Logs,
power/Activity dumps and the first artifact set are preserved. Dismiss the
observed system overlay with Home/Wake and retry the same unchanged owned
paused witness. This environment failure is separate from production behavior.

Offline acceptance: corrected exercise recovery passes OK (1 test), process
2268 after preparation 3000. Exact paused runtime/cues survive; initialized
native output stays silent through paused reopen and real Resume with no new
keys. Actual WORKOUT_SUCCESS speech/focus occurs before 2/2 offline completion.
The runner again removes its process; only copied Home is relaunched and live
PID 2521 is confirmed force-stopped with no remaining PID. Final-success
recovery passes OK (1 test) in another process, with exact saved final runtime/
cues and native silence while voice remains enabled. Original airplane mode 0
is restored and the same phone copy is being relaunched for exact receipt checks.

Final acceptance/checks:

- All six explicit stages pass **OK (1 test)**: paired preparation on phone/
  Wear, offline exercise recovery, offline final recovery and receipt checks
  on both peers. Logs are `iteration-66-preparation-{phone,wear}.log`,
  `iteration-66-offline-exercise.log`, `iteration-66-offline-final.log` and
  `iteration-66-receipt-{phone,wear}.log`. The interrupted charging-overlay
  attempt remains preserved in `iteration-66-offline-exercise-first.log` and
  `iteration-66-offline-first-wear-artifacts` with logcat/power/Activity witnesses.
- Voice stays enabled in all saved recovery witnesses. Preparation process
  **3000** and exercise-recovery process **2268** differ; final recovery uses
  **2589**. Relaunched Home processes **3143/2521** are separately confirmed
  force-stopped with empty remaining PID. This establishes actual fresh-process
  recovery after durable success and normal Activity teardown, not killing
  speech in flight. No app data or runtime is reset.
- Read-only native observations verify live EXERCISE_SUCCESS and offline
  WORKOUT_SUCCESS with their production controller kind/current focus.
  Six silence phases each sample initialized output/controller **20 times at
  100 ms**. Fresh paused reopening, real Resume and fresh offline final reopening
  add no cue keys or replay; current native focus is released. Exact paused
  runtime/cues and exact final runtime/cues survive across processes. Native
  state sampling does not establish physical acoustic silence/intelligibility.
- Request `927613b4-043b-40e4-936a-ac1913948f3b` completes **2/2 sets** offline
  with one final-success key, saved summary and Waiting to sync. Reconnection
  produces the exact full saved final result and one receipt; Wear prunes
  runtime/package/ledger, retains the acknowledged success tombstone and restores
  original preferences. Independent `iteration-66-preservation-validated.json`
  proves **51/51 prior phone records exact**, records **51 -> 52**, receipts
  **28 -> 29**, byte-exact original legacy entries/preferences, exact fresh
  recovery witnesses/Resume ledger and full offline final-result equality.
  Final Wear/phone artifacts are in `iteration-66-validated-wear-artifacts` /
  `iteration-66-validated-phone-artifacts`. Reviewed paused Process B,
  recovered Process A success and offline 2/2 saved-summary screenshots.
- Both test APK builds/final Wear build pass. Ordinary instrumentation without
  mutation flags passes **9 phone / 20 Wear**; guarded methods skip normally.
  Crash buffers are empty, native focus has no production owner, airplane mode
  is restored to **0**, accessibility absent/0, touch exploration 0 and animator
  scale 1.0. Production/PWA code is unchanged, so shared 78/phone 28/Wear 204
  JVM and PWA baselines remain applicable and are not rerun.
- Inventory `20261004T065701444Z-iteration-66-final` records phone API 35/
  Wear API 37, app 1.0/code 1 and matching installed/local production APKs.
  Wear remains `46F8C23A388D37E144C7DA2E2FC5B58E3E0977A5D472D6CCF8F73C8E3DF12C27`;
  phone remains `21F9C264A1311BED0BD0FD94FD3BA52DAE730E4158CE90140B2DE02A0C6DF0C0`.
  Only verified copies are stopped; final original-disk audit is pending.

Completed code/emulator checks: guarded voice-enabled staged success recovery,
fresh native no-replay observations, offline exact completion and receipt/
preservation acceptance. No production defect is established. Closure awaits
source-disk audit and final Git check. Stage 19 stays **70/97 (72%)**, physical
Phase 4 **0/27**. Next: voice-enabled fresh-process rest/deadline recovery,
including no hidden advancement and exactly-once foreground catch-up. Physical
speech quality/overlap, routing, tactile, battery and Play remain separate.

Final closure supersedes the pending disk audit/Git check above: all **16
original source-disk hashes** match the baseline after stopping only verified
copies (`iteration-66-source-disk-after.json`); ADB reports no remaining devices.
`git diff --check` passes. Final Git audit confirms `f02974d` as HEAD with only
these Iteration 66 test/docs changes, validated and uncommitted. All relevant
build/native/staged/UI/preservation/crash/settings/focus/inventory/source checks
pass, closing this iteration. Earlier pending labels and the charging-overlay
failure remain historical evidence.

## Iteration 67 — 2026-10-04 — Voice-enabled fresh-process rest recovery

Status: **Validated on the isolated paired emulators — test/docs uncommitted;
physical acceptance remains separate.**

Starting checkpoint: `7b2fdc3 PST01: Validate voice-enabled fresh-process success
recovery`, committed at the user's request with a clean working tree.
Continue only on the exact copied AVD pair. Stage 19 stays 70/97, physical
Phase 4 0/27.

Next action: stage actual native rest speech and Home exit, preserve exact
RESTING state/deadline/cues beyond the hidden deadline, then terminate/reboot
and verify exact expired-rest preservation in a fresh offline process. Only
actual foreground Resume may catch up with one deadline-bound Go; cold native
availability is observed without replacing output. Complete normally offline,
verify exact result/receipt/pruning and restore original preferences/settings.
Preserve prior phone records, legacy entries and every original AVD disk.

Implementation: new exact-copy/peer opt-in creates one two-set Rest recovery
exercise with 12 seconds rest. The shared preparation helper keeps the prior
success mode unchanged, while the new rest mode observes native REST/focus,
uses actual Home and polls exact runtime/ledger/native silence beyond the
deadline. Fresh offline recovery first launches only Home and verifies the
expired RESTING witness remains exact with no session owner/focus. Real Resume
must advance once with only deadline-bound Go and exact progress/outcomes/set.
A read-only 20 ms timeline records cold engine initialization/controller/native
state; speech need not occur before native initialization. After initialized
idle, actual offline workout-success speech and 2/2 saved completion are required.
Existing exact receipt/pruning stages accept the new explicit mode and restore
original preferences. Original 16-disk audit and verified boot pass; test APK
builds are pending. No production change is planned without defect evidence.

Preparation acceptance: both test APK builds pass and both paired methods pass
OK (1 test). Request `01614eea-ac26-469f-b44f-9d68aeed2847` reaches actual native
REST/focus, then Home cancels output. Exact hidden state/cues persist beyond
the deadline. The owned expired-rest artifacts remain retained. Runner exit
removes the app PID; relaunch only copied Home (no Resume), confirm live PID
3119 and force-stop it with no remaining PID. Remove only copied bridge mappings,
stop the verified phone copy and enable/reboot only the copied watch's airplane
mode to clear native peer discovery. Offline fresh-process acceptance is pending.

Offline acceptance passes OK (1 test), process **2130** after preparation
**2930**. Post-boot charging overlay is observed/dismissed before launch; no
failed attempt is needed. The expired RESTING witness stays exact through
fresh Home, with no session controller/focus. Only actual Resume commits ACTIVE
once, with unchanged progress/outcomes/set and exactly one deadline-bound Go.
The **219-sample** cold native timeline contains one Checking sample and **61
GO/native-speaking samples**, followed by initialized idle/no replay. Actual
WORKOUT_SUCCESS playback and 2/2 offline saved completion pass. Preparation
retains **101** hidden exact-state/ledger/silence observations beyond its
deadline. Original airplane mode 0 is restored; the same phone copy is being
relaunched for exact result/receipt/pruning and preservation checks.

Reconnection environment: the copied phone boot exceeds the initial 50-second
host wait. Its retained log reports successful boot in 52,627 ms; subsequent
ADB identity/boot checks pass. Re-establish the same copied bridge without
another restart. This supersedes the host wait timeout; no acceptance method
has failed and no production defect is established.

Final acceptance/checks:

- All **five explicit stages** pass OK (1 test): paired preparation on both
  peers, offline rest recovery and receipt/pruning on both peers. Logs are
  `iteration-67-preparation-{phone,wear}.log`, `iteration-67-offline-rest.log`
  and `iteration-67-receipt-{phone,wear}.log`. No failed acceptance attempt occurs;
  the delayed phone boot and dismissed charging overlay remain environment
  evidence, separately from acceptance.
- Preparation process **2930** and fresh offline process **2130** differ.
  Relaunched copied Home process **3119** is separately confirmed force-stopped
  with empty remaining PID. This is fresh recovery after durable rest/Home and
  normal Activity teardown, not termination of speech in flight. Voice remains
  enabled across saved witnesses. **101** hidden observations retain exact
  runtime/progress/outcomes/deadline/cues and native silence beyond deadline.
- Fresh Home retains the exact expired RESTING witness for ten observations,
  with no session controller/current native focus. Actual Resume alone commits
  ACTIVE with exactly one new deadline-bound Go and one runtime revision;
  progress/outcomes/current set stay exact. The **219-sample** 20 ms native
  timeline contains one Checking sample, **61 Go/controller/native-speaking
  samples**, then initialized idle without replay. The accepted cold path
  permits initialization fallback, but this run observes actual Go playback.
  Normal offline completion also reaches native WORKOUT_SUCCESS/focus and saves
  **2/2 sets**, one final-success key and Waiting to sync. Native observations
  are separate from physical acoustic silence/intelligibility.
- Request `01614eea-ac26-469f-b44f-9d68aeed2847` receives one exact full offline
  final result/receipt after reconnection; Wear prunes runtime/package/ledger
  and retains the acknowledged success tombstone. Independent
  `iteration-67-preservation-validated.json` proves **52/52 prior phone records
  exact**, records **52 -> 53**, receipts **29 -> 30**, exact hidden/fresh Home
  witnesses, only deadline-bound Go, exact progress/outcomes/full offline final
  result and byte-exact original legacy entries/restored preferences. Evidence
  is in `iteration-67-validated-wear-artifacts` /
  `iteration-67-validated-phone-artifacts`. Reviewed fresh Home Resume, active
  set 2/2 and offline 2/2 saved-summary screenshots.
- Both test APK builds pass. Ordinary instrumentation without mutation flags
  passes **9 phone / 22 Wear**, guarded methods skipped normally. Crash buffers
  are empty and native focus released. Airplane mode is restored to 0,
  accessibility absent/0, touch exploration 0 and animator scale 1.0. Production/
  PWA code is unchanged; shared 78/phone 28/Wear 204 JVM and PWA baselines remain
  applicable and are not rerun.
- Inventory `20261004T080315686Z-iteration-67-final` records phone API 35/
  Wear API 37, app 1.0/code 1 and matching installed/local production APKs.
  Wear remains `46F8C23A388D37E144C7DA2E2FC5B58E3E0977A5D472D6CCF8F73C8E3DF12C27`;
  phone remains `21F9C264A1311BED0BD0FD94FD3BA52DAE730E4158CE90140B2DE02A0C6DF0C0`.
  Only verified copies are stopped; final original-disk audit is pending.

Completed code/emulator checks: guarded rest process harness, native Home
cancellation, hidden exact rest preservation, cold foreground-only Go catch-up,
offline completion and exact receipt/preservation acceptance. No production
defect is established. Closure awaits source audit and final Git check.
Stage 19 remains **70/97 (72%)**, physical Phase 4 **0/27**. Next: voice-enabled
fresh-process recovery before a rest deadline, preserving the same deadline
without repeating REST and observing native warning/Go at their thresholds.
Physical speech quality/overlap, routing, tactile, battery and Play stay separate.

Final closure supersedes the pending disk audit/Git check above: all **16
original source-disk hashes** match the baseline after stopping only verified
copies (`iteration-67-source-disk-after.json`); ADB reports no remaining devices.
`git diff --check` passes. Final Git audit confirms `7b2fdc3` as HEAD with only
these Iteration 67 test/docs changes, validated and uncommitted. All relevant
build/native/staged/UI/preservation/crash/settings/focus/inventory/source checks
pass, closing this iteration. Earlier pending labels remain historical evidence.

## Iteration 68 — 2026-10-04 — Voice-enabled pending-rest process recovery

Status: **Validated on the isolated paired emulators — test/docs uncommitted;
physical acceptance remains separate.**

Starting checkpoint: `c9d5d1f PST01: Validate voice-enabled expired-rest process
recovery`, committed at the user's request with a clean working tree. This
supersedes Iteration 67's uncommitted label. Stage 19 stays 70/97, physical
Phase 4 0/27.

Scope: an explicit exact-copy opt-in stages one two-set exercise with 120-second
rest, actual native REST speech and Home cancellation. The longer natural rest
allows copied-watch reboot/offline recovery before the final-five boundary;
the saved deadline is never rewritten. Fresh Home must retain exact runtime/
cues without a session owner. Actual Resume must stay RESTING with the same
deadline, progress, outcomes and ledger, initialized silence and no REST replay.
Read-only native sampling and persisted witnesses must show no early warning/
Go, then one native FIVE_SECONDS and GO at their respective deadline boundaries.
Complete 2/2 offline, require exact final result/receipt/pruning, preserve every
prior phone record/legacy entry/preference and all original source disks.

Implementation: shared process preparation/recovery helpers retain expired-
rest behavior and add pending-rest mode. New preparation/recovery methods and
phone transport/receipt opt-ins are guarded on exact copied identities/peers.
No production change is planned without a demonstrated defect. Next: compile
both test APKs, audit original disks, boot only copied AVDs and run staged native
acceptance; then ordinary instrumentation, preservation and final audits.

First attempt: both preparation methods pass OK (1 test), request
`b08547b5-3de7-4af8-8f98-d11250727f46`. Fresh Home preserves exact state/cues and
native initialization is observed, but the offline method times out at its
rest-label assertion. Inspection identifies two harness/environment issues:
the test incorrectly expected `RESTING` while the real UI says `REST · SET 2 / 2`,
and delayed ChargingComposeActivity appears after the initial 15-second host
settle wait. Correct the label and extend the bounded observed-overlay wait to
40 seconds. Retain first-attempt logs/artifacts. No production defect is found.
The original natural deadline has now expired, so a separate guarded
`completeExpiredPendingRestFixtureOffline` method completes only this owned
fixture through actual UI; its expired-mode result cannot count as pending-rest
acceptance. Reconnect for exact receipt/pruning/preference restoration before
creating a new request and repeating the fresh timing test.

Owned cleanup passes OK (1 test), with exact expired witness and 2/2 normal
offline completion; Wear receipt/pruning/restored preferences also pass. The
phone receipt stage initially finds its retained `before-records.json` empty
after copied-phone shutdown, despite successful preparation. This is an
acceptance-artifact durability issue, not evidence of lost app history. Retain
that failure and empty diagnostic. Reconstruct only the test artifact from
Iteration 67's independently validated exact 53-record final checkpoint;
the phone receipt retry passes, proving all 53 originals exact plus the cleanup
record and receipt. No production data is rewritten. Add FileDescriptor.sync
to the new mode's prior-record witness before shutdown, then rebuild for a new
pending-rest request. The first failed timing attempt remains unaccepted.

Retry preparation passes on both peers for request
`30009af4-c1df-4a1a-8ed6-1ea01118dc7e`. Confirmed copied Home PID 3597 is
force-stopped with empty remaining PID. Reboot only the isolated offline watch,
use the longer post-boot settle window, then actual Home/Resume retains exact
RESTING state/deadline/progress/outcomes/ledger. Initialized output/controller
silence is observed without REST replay. Natural warning/Go acceptance is
running; original deadline and system clock remain unchanged.

Fresh pending-rest recovery passes OK (1 test), preparation PID **3467** and
fresh offline PID **2387**. Resume occurs **68,194 ms** before the same deadline.
Native warning is observed **4,883 ms** before it, native Go **197 ms** after it.
**19** hidden and **1,069** foreground observations retain exact state/ledger
and native silence before the warning. The **3,130-sample** 20 ms timeline shows
**79** FIVE_SECONDS and **62** Go native-speaking samples, no REST replay/early
cue, then initialized idle. No Checking-false sample is captured in this run;
engine initialization is confirmed independently. Actual WORKOUT_SUCCESS and
2/2 offline completion pass; original airplane mode 0 is restored.

Reconnection: Wear receipt/pruning/preferences pass. The copied phone initially
cannot launch instrumentation after shutdown (`INSTRUMENTATION_FAILED` and
empty `pm list instrumentation`, retained package diagnostics). Reinstall only
the same test APK, preserving production app/data. Phone receipt retry passes
OK (1 test); the synced **71,671-byte** before-record artifact survives intact,
without reconstruction for this accepted request. Independent
`iteration-68-preservation-validated.json` proves all **54/54** prior records
exact, records **54 -> 55**, receipts **31 -> 32**, exact full offline final
result, runtime/ledger/deadline/progress/outcomes, original legacy entries and
restored preferences, one warning/Go each and pruning/tombstone. Reviewed
resumed rest, final-five and offline saved 2/2 screenshots. Ordinary checks,
crash/settings/focus/APK inventory and final source-disk audit remain pending.

Final checks and closure supersede the pending labels above:

- All **five accepted stages** pass OK (1 test):
  `iteration-68-preparation-{phone,wear}.log`, `iteration-68-offline-rest.log`,
  `iteration-68-receipt-wear.log` and `iteration-68-receipt-phone-retry.log`.
  Earlier failed timing/cleanup-witness/test-runner attempts remain retained
  and do not count as acceptance. The separately guarded expired cleanup and
  its receipt/pruning pass, preserving all 53 earlier originals before the
  accepted request adds one further result. The accepted 54-record snapshot
  requires no reconstruction.
- Both test APKs build; final repaired/synced build is
  `iteration-68-final-test-build.log`. Ordinary instrumentation without flags
  passes **9 phone / 25 Wear**, with guarded mutation stages skipped normally.
  Production/PWA code is unchanged; shared 78/phone 28/Wear 204 JVM and PWA
  baselines remain applicable and are not rerun.
- Independent preservation checks pass (`validate-iteration-68.ps1` and
  `iteration-68-preservation-validated.json`), with 62 top-level Wear and three
  phone accepted artifacts retained. Runtime/package/cue ledger prune and the
  acknowledged workout-success tombstone persists. Original preferences and
  legacy entries are byte-exact. No production defect is established.
- Crash buffers are empty, current native focus released, original airplane
  mode 0/accessibility absent and disabled/touch exploration 0/animator 1.0
  restored. Inventory `20261004T082954071Z-iteration-68-final` records phone
  API 35/Wear API 37, app 1.0/code 1 and installed/local production APK equality.
  Wear stays `46F8C23A388D37E144C7DA2E2FC5B58E3E0977A5D472D6CCF8F73C8E3DF12C27`;
  phone stays `21F9C264A1311BED0BD0FD94FD3BA52DAE730E4158CE90140B2DE02A0C6DF0C0`.
- Sync and stop only the verified copied pair. All **16 original source-disk
  hashes** match before and after (`iteration-68-source-disk-{before,after}.json`);
  final ADB inventory is empty. Physical acoustic silence/intelligibility,
  routing, tactile, battery and Play acceptance remain separate from native
  emulator observations.

Completed: guarded pending-rest process harness, exact hidden/fresh Home/
foreground preservation, natural native warning/Go timing, offline 2/2 result,
receipt/pruning, durability/owned-cleanup acceptance support and preservation
audits. Stage 19 stays **70/97 (72%)**, physical Phase 4 **0/27**. Next:
voice-enabled paused-rest fresh-process recovery, retaining exact saved remaining
time without replay before actual Resume, then verifying normal deadline/cue
behavior and exact offline result/receipt. Final Git check follows doc closure.

Final Git audit confirms `c9d5d1f` remains HEAD with only the seven Iteration 68
test/docs files modified; unrelated changes are absent. `git diff --check`
passes. All relevant checks above pass, closing this iteration as validated
and uncommitted. Historical failures/pending observations remain retained and
are superseded only by the explicitly described successful checks.

## Iteration 69 — 2026-10-04 — Voice-enabled paused-rest process recovery

Status: **Validated on the isolated paired emulators — test/docs uncommitted;
physical acceptance remains separate.**

Starting checkpoint: `ed4ca54 PST01: Validate voice-enabled pending-rest process
recovery`, committed at the user's request with a clean working tree. This
supersedes Iteration 68's uncommitted label. Stage 19 stays 70/97, physical
Phase 4 0/27.

Scope/findings: real Pause freezes remaining rest seconds, clears its deadline
and retains the rest interval. Opening Home's Resume enters the paused session
without resuming its timer. Only the session Resume action commits RESTING,
creating a new deadline from the saved remaining seconds; production intentionally
emits one REST cue bound to that new deadline. This differs from replay of the
old deadline's cue. Validate exact paused state/remaining time/ledger through
Home, process termination, offline reboot and opening the paused screen after
the original deadline. Require initialized silence until actual session Resume,
then one native resumed REST and natural warning/Go bound to the new deadline.
Complete 2/2 offline with exact result/receipt/pruning and preservation.

Implementation: a new exact-copy/peer opt-in stages one two-set 20-second rest,
observes native REST and uses actual Pause/Home. Shared preparation/recovery
helpers add paused mode, with distinct evidence folders and explicit preparation/
offline methods. Fresh paused witnesses remain exact; resumed deadline must
equal saved remaining seconds plus an observed actual Resume commit time, with
exact progress/outcomes/interval and one new REST key. Existing natural boundary
sampling/checks use that committed rest witness. Phone receipt/pruning restore
preferences and compare all prior records. Prior-record evidence is synced;
host shutdown will sync only copied devices. No production change is planned
without defect evidence. Next: test APK builds, original source audit and staged
native acceptance, followed by ordinary/UI/preservation/inventory/final audits.

First preparation attempt: both test builds and the 16-disk pre-audit pass.
Both exact copied identities/boot/install checks pass, but Wear's immediate
native peer assertion sees an empty list just after bridge setup and fails
before any fixture/preference mutation. The phone waiter is stopped only on
its verified copy; no owned request exists. Retain first-attempt logs. Add a
bounded 30-second exact sole-peer readiness wait to the new paused opt-in on
both peers, then retain the same strict identity assertions before mutation.
This addresses bridge discovery timing without relaxing peer requirements.
No production defect is established; rebuild/retry are next.

Preparation retry passes OK (1 test) on both peers for request
`e076add6-a001-481f-9aae-cfb37daad403`. Actual Pause cancels observed native REST,
clears the deadline and freezes remaining time without progress/outcome/ledger
change. Home retains the exact paused witness and initialized silence. The
runner exits; relaunch only copied Home, confirm PID **3019**, force-stop it and
verify empty remaining PID. Sync/stop only the verified phone copy, remove the
copied bridge, save airplane mode 0 and sync/reboot only the watch copy offline.
The charging overlay is observed/dismissed before acceptance. Fresh paused
screen/native Resume/new-deadline acceptance is running. Earlier pre-mutation
discovery failure remains retained, superseded by this successful preparation.

Offline acceptance passes OK (1 test), preparation PID **2852** and fresh PID
**2347**. The exact paused runtime/20 seconds remaining/ledger survive reboot,
fresh Home and opening PAUSED UI after the original deadline; first Home Resume
occurs **35,705 ms** beyond it. Native initialization and silence are required
before session Resume. Actual session Resume alone commits a new deadline from
the saved 20 seconds, with exact progress/outcomes/interval and one new REST
key/native playback. Natural warning is observed **4,813 ms** before the new
deadline and Go **242 ms** after it, one key/revision each. **19** hidden and
**182** foreground observations stay exact/silent before the warning. The
**1,156-sample** 20 ms timeline captures one Checking-false sample, **74** REST,
**75** warning and **63** Go native-speaking samples, then initialized idle.
Actual native WORKOUT_SUCCESS and 2/2 offline Saved/Waiting to sync pass.
Reviewed paused remaining/resumed rest/final-five/offline summary screenshots.
Original airplane mode 0 is restored; the same copied phone is reconnected and
its runner remains installed after synced shutdown. Receipt/pruning and final
preservation/ordinary/inventory/settings/source audits are next.

Final checks supersede the pending labels above:

- All **five accepted stages** pass OK (1 test):
  `iteration-69-preparation-{phone,wear}.log`, `iteration-69-offline-rest.log`
  and `iteration-69-receipt-{phone,wear}.log`. The earlier pre-mutation native
  discovery failure and stopped waiter remain retained; no failed owned fixture
  is created and no acceptance assertion is weakened.
- Independent `validate-iteration-69.ps1` /
  `iteration-69-preservation-validated.json` prove **55/55** prior records exact,
  records **55 -> 56**, receipts **32 -> 33**, exact full offline final result,
  hidden/fresh Home/opened paused runtime/ledger/remaining time, new deadline
  from real Resume, one new REST/warning/Go bound to that deadline, no pre-
  Resume or early cue, pruning and acknowledged success tombstone. Original
  legacy entries/restored preferences are byte-exact. Accepted artifacts are
  `iteration-69-validated-wear-artifacts` (**80 files**) and
  `iteration-69-validated-phone-artifacts` (**three files**).
- Both test APKs build (`iteration-69-final-test-build.log`). Ordinary runs
  without mutation flags pass **9 phone / 27 Wear**; guarded staged methods
  skip normally. Production/PWA code is unchanged; shared 78/phone 28/Wear 204
  JVM and PWA baselines remain applicable and are not rerun.
- Crash buffers are empty and current native focus released. Original airplane
  mode 0/accessibility absent and disabled/touch exploration 0/animator 1.0 are
  restored. Inventory `20261004T091754510Z-iteration-69-final` verifies phone
  API 35/Wear API 37, app 1.0/code 1 and installed/local production APK equality.
  Wear stays `46F8C23A388D37E144C7DA2E2FC5B58E3E0977A5D472D6CCF8F73C8E3DF12C27`;
  phone stays `21F9C264A1311BED0BD0FD94FD3BA52DAE730E4158CE90140B2DE02A0C6DF0C0`.
- Sync and stop only verified copies; all **16 original source-disk hashes**
  match before and after (`iteration-69-source-disk-{before,after}.json`). Final
  ADB inventory is empty. Synced shutdown retains the copied phone's runner/
  prior-record witness, superseding the previous iteration's repair need for
  this run. Physical acoustic/routing/tactile/battery/Play remain separate.

Completed: guarded paused-rest process preparation/recovery, native Pause
cancellation, exact frozen state through Home/reboot/opening PAUSED UI, actual
Resume/new REST/deadline, natural warning/Go, offline 2/2 result, exact receipt/
pruning and preservation audits. No production defect is established. Stage 19
stays **70/97 (72%)**, physical Phase 4 **0/27**. Next: voice-enabled paused-rest
fresh-process recovery after the final-five latch, preserving its warning/lock
and short remaining time without replay, then exactly-once resumed Go and exact
offline result/receipt. Closure awaits final Git/doc check.

Final closure: `git diff --check` passes and Git still reports `ed4ca54` as HEAD,
with only the seven Iteration 69 test/docs files modified and no unrelated
changes. All relevant checks above pass; this iteration is validated and
uncommitted. Earlier pending statements and the pre-mutation discovery failure
remain historical evidence, superseded by the explicit successful checks.

## Iteration 70 — 2026-10-04 — Voice-enabled final-five paused-rest recovery

Status: **Code/emulator acceptance validated; physical acceptance separate.**

Starting checkpoint: `8a3c765 PST01: Validate voice-enabled paused-rest process
recovery`, committed at the user's request with a clean working tree. This
supersedes Iteration 69's uncommitted label. Stage 19 stays 70/97, physical 0/27.

Scope/findings: Pause after the natural FIVE_SECONDS cue retains the durable
final-countdown latch and short remaining time. On explicit session Resume,
the normal engine rebases its deadline from saved seconds; rest scripts of at
most five seconds are suppressed and the retained latch prevents another
warning. Validate actual native warning/Pause cancellation, exact paused state/
ledger/lock through Home, termination, offline reboot and opening PAUSED UI;
then locked extension controls, native silence/no REST or warning replay until
one deadline-bound Go. Finish 2/2 offline and require exact receipt/pruning.

Implementation: a new exact-copy/peer opt-in stages one two-set 12-second rest.
The shared helpers wait for actual native FIVE_SECONDS, save its original
deadline-bound key, and Pause while speech is active. Frozen remaining time
must be 1–5 seconds with its latch set. Fresh recovery keeps exact paused state
and initializes silently before session Resume. The new deadline must match
saved seconds plus the observed Resume time; extensions stay disabled, no cue/
runtime change occurs until Go, and only Go adds one revision/key. Existing
voice/paused/pending modes retain their assertions. Synced prior-record evidence
and copied-device shutdown preserve originals. No production change is planned
without defect evidence. Next: test builds, source-disk audit and staged native
acceptance, then ordinary/preservation/UI/inventory/settings/final audits.

Builds pass (`iteration-70-test-build.log` and the later diagnostic build), and
all 16 original disk hashes match before launch. The first preparation fails
before creating an owned fixture: the phone retains 56 records, and the final
count assertion masks the original setup exception. Retain the first logs;
the shared preparation now preserves its original exception and suppresses a
secondary cleanup error, while phone probes log their original failures. No
preservation assertion is weakened. A retry with these diagnostic-only changes
passes both preparation stages for `fad676b6-8dbe-4fe8-9b16-f73c3c4f4366`.
The initial root cause remains unconfirmed; do not infer a production defect
from the masked error. Natural native warning/Pause and hidden frozen state
pass. The host confirms process termination, syncs/stops the exact phone copy,
removes its bridge and reboots the exact watch offline. Fresh recovery is next.

Final evidence supersedes those pending labels:

- All five stages pass OK (1 test): `iteration-70-preparation-{phone,wear}.log`,
  `iteration-70-offline-rest.log`, `iteration-70-receipt-{phone,wear}.log`.
  Fresh PID **2338** differs from preparation **2904**; host termination of PID
  **3096** is confirmed. Exact paused state/ledger and **5 seconds** survive
  beyond the old deadline (opening occurs **51,429 ms** after it). Original
  native warning occurs **4,848 ms** before its deadline. Explicit Resume alone
  rebases the deadline; +5/+10/+30 stay disabled. **164** foreground and **19**
  hidden observations pass. Go occurs **202 ms** after the new deadline.
- `validate-iteration-70.ps1` / `iteration-70-preservation-validated.json`
  independently verify **56/56** prior records exact, records **56 -> 57**,
  receipts **33 -> 34**, full offline 2/2 result equality, no new REST/warning,
  one new deadline-bound Go, pruning, byte-exact legacy entries/preferences.
  The first audit wrongly counted the separate initial workout countdown as a
  replayed rest warning; binding that assertion to the original rest deadline
  fixes the audit without changing acceptance data. The **511-sample** native
  timeline contains **67** Go-speaking samples, one Checking sample and zero
  REST/warning samples. Accepted Wear/phone folders contain **70/3 files**.
- Both test APK builds and ordinary **9 phone / 29 Wear** tests pass. Production
  code is unchanged; existing shared 78/phone 28/Wear 204 JVM and PWA baselines
  remain applicable. Paused/rest/offline screenshots were visually inspected.
  Crash buffers are empty, current focus released, and original airplane 0,
  absent/disabled accessibility, touch exploration 0 and animator 1.0 restored.
  Inventory `20261004T135422221Z-iteration-70-final` verifies API 35/API 37,
  app 1.0/code 1 and unchanged installed/local production APK equality.
- Synced shutdown stops only verified copies; all **16** original disk hashes
  match before/after; final ADB inventory is empty. The earlier approval-review
  usage-limit failure executed no command; a subsequent authorized retry
  succeeds. Original setup failure remains historical evidence with an
  unconfirmed cause, superseded by passing preparation/diagnostic improvements.

Completed: latched paused-rest process recovery, native cancellation/silence,
locked controls, exactly-once resumed Go, offline result/receipt/pruning and
preservation. Stage 19 stays **70/97 (72%)**, physical **0/27**. Next: extend a
voice-enabled rest using +30 before Pause, preserve its extended remaining time
through fresh offline recovery, then validate the rebased deadline/cue sequence
and exact result/receipt. Final Git/doc closure is recorded before committing.

Final closure: `git diff --check` passes; HEAD remains `8a3c765` and only the
seven expected test/docs files are modified. All relevant emulator checks pass;
this iteration is ready for the requested commit, with physical 0/27 separate.

## Iteration 71 — 2026-10-04 — Voice-enabled extended paused-rest recovery

Status: **Code/emulator acceptance validated; uncommitted. Physical acceptance
remains separate.**

Starting checkpoint: `ceaf53a PST01: Validate voice-enabled locked-rest process
recovery`, committed at the user's request with a clean tree. This supersedes
Iteration 70's ready-to-commit label. Stage 19 stays 70/97, physical 0/27.

Scope: extend a 20-second voiced rest using the actual +30 control before Pause.
Require exactly +30,000 ms/one runtime revision, unchanged progress/outcomes/
rest interval and cue ledger, with no rest announcement replay. Pause retains
21–50 seconds with no warning latch. Preserve this exact state through Home,
process termination and offline reboot. Opening PAUSED UI is silent; explicit
Resume alone rebases from saved extended seconds. Require one intentional REST,
then natural warning/Go on the new deadline, offline 2/2 result, exact receipt/
pruning, prior records and original preferences/entries/disks preserved. Reuse
guarded shared helpers without relaxing existing modes. Next: implement, build
and run the five staged checks plus ordinary/preservation/inventory audits.

Implementation adds two guarded Wear methods and a phone offer/receipt mode.
Actual +30 compares the entire runtime against a copy changing only deadline
and revision; the ledger stays exact before Pause. Extended recovery waits
beyond the saved extended deadline while PAUSED, preserves all frozen state,
and reuses native resumed REST/warning/Go and exact offline receipt assertions.
Both test APKs build (`iteration-71-test-build.log`, 1m 7s); all 16 original
source disk hashes match before launch. Only verified AVD copies are running,
with installed test APKs and the bounded charging-overlay observation. Staged
acceptance is running; code completion is distinct from pending emulator checks.

First preparation fails before any owned request/folder exists: retained logs
show a 30-second probe timeout with the expected-count cleanup assertion now
suppressed. The phone keeps 57 records. A fixed two-second host delay does not
establish listener readiness during native peer/store initialization. Add an
instrumentation stream marker only after the phone message listener is
registered, and require it in the host protocol before Wear sends its offer.
Keep all first-attempt logs, preserve the diagnostic improvement and rebuild;
no acceptance assertion or existing record is relaxed/cleared.

Readiness build passes (`iteration-71-ready-test-build.log`, 1m). The phone
stream marker is observed before Wear starts; both preparation stages pass
OK (1 test) for `eecfa58d-f483-4c97-aaf2-935f48c52696`. Actual +30/one revision,
exact unchanged ledger and paused/Home state pass. The host confirms copied
process termination, syncs/stops only the phone copy and reboots the watch
offline. Earlier pre-mutation failure remains retained; no failed fixture is
created. Fresh extended-deadline/Resume/native sequence acceptance is running.

Offline recovery passes OK (1 test), fresh PID **2427** versus preparation
**2873**, confirmed host-killed PID **3080**. The +30 screen shows 50 seconds;
Pause freezes **46 seconds**, retained exactly beyond the extended deadline
through fresh Home and opening PAUSED UI. Actual session Resume rebases from
those 46 seconds and intentionally plays one REST. **19** hidden and **605**
foreground observations preserve runtime/ledger/native silence before natural
warning/Go. Native final success and offline 2/2 Saved/Waiting to sync pass.
Extended/paused/resumed/offline screenshots are visually inspected. Airplane
mode is restored and the same copied phone is restarting for exact receipt/
pruning; independent preservation and final audits remain pending.

Final evidence supersedes those pending labels:

- All **five stages** pass OK (1 test):
  `iteration-71-preparation-{phone,wear}.log`, `iteration-71-offline-rest.log`
  and `iteration-71-receipt-{phone,wear}.log`. The retained first pre-mutation
  timeout is superseded by an explicit registered-listener stream marker and
  successful retry; no failed owned fixture exists and no prior record changes.
- Independent `validate-iteration-71.ps1` /
  `iteration-71-preservation-validated.json` prove the entire extended runtime
  differs only by **+30,000 ms/one revision**, with the exact original ledger.
  Frozen **46 seconds** survive **25,093 ms** beyond the extended deadline
  before opening. Only session Resume commits the new deadline/one REST;
  native warning occurs **4,760 ms** before it and Go **334 ms** after it.
  **19** hidden / **605** foreground observations are exact and silent.
  The **2,205-sample** native timeline includes one Checking sample, **89**
  REST, **77** warning and **60** Go-speaking samples, with no pre-Resume cue
  or early warning/Go. All cue keys bind to their correct deadlines.
- Exact full offline final result and 2/2 completion pass; all **57/57** prior
  records stay exact, records **57 -> 58**, receipts **34 -> 35**, runtime/cue
  pruning and acknowledged success tombstone pass. Original legacy entries and
  restored preferences are byte-exact. Accepted Wear/phone artifacts contain
  **85/3 files**. Paused/extended/resumed/offline captures are visually reviewed.
- Both test APK builds pass; ordinary tests without mutation flags pass
  **9 phone / 31 Wear**. Production/PWA code is unchanged, so the shared
  78/phone 28/Wear 204 JVM and existing PWA baselines remain applicable.
  Crash buffers are empty and current native focus released. Airplane 0,
  absent/disabled accessibility, touch exploration 0 and animator 1.0 are
  restored. Inventory `20261004T141255893Z-iteration-71-final` verifies phone
  API 35/Wear API 37, app 1.0/code 1 and installed/local production APK equality:
  Wear `46F8C23A388D37E144C7DA2E2FC5B58E3E0977A5D472D6CCF8F73C8E3DF12C27`,
  phone `21F9C264A1311BED0BD0FD94FD3BA52DAE730E4158CE90140B2DE02A0C6DF0C0`.
- Synced shutdown stops only verified copies. All **16** original source disk
  hashes match before/after, and final ADB inventory is empty. Physical acoustic,
  speaker/Bluetooth, tactile, battery and Play observations remain open.

Completed: actual extension, exact paused extended-state process recovery,
silent opening, intentional Resume/rest/new deadline, natural warning/Go,
offline result/receipt/pruning, synchronized phone readiness and preservation.
No production defect is established. Stage 19 remains **70/97 (72%)**, physical
Phase 4 **0/27**. Next: voice-enabled Start now after offline paused-rest
recovery, exactly one immediate Go and no late warning/Go at the abandoned rest
deadline, then exact offline result/receipt. Closure awaits final Git/doc check.

Final closure: `git diff --check` passes. Git is rechecked at HEAD `ceaf53a`,
with only the seven expected Iteration 71 test/docs files modified and no
unrelated changes. All relevant code/emulator checks above pass; this iteration
is validated and uncommitted. Earlier pending labels and first-attempt failure
remain historical evidence, superseded by the explicit successful results.

## Iteration 72 — 2026-10-04 — Voice-enabled early rest exit after recovery

Status: **Code/emulator acceptance validated; committed on 2026-10-05. Physical acceptance
remains separate.**

Starting checkpoint: `9764243 PST01: Validate voice-enabled extended-rest
process recovery`, committed at the user's request with a clean tree. This
supersedes Iteration 71's uncommitted label. Stage 19 stays 70/97, physical 0/27.

Scope: preserve a voiced 20-second paused rest through process termination and
offline reboot; explicit Resume alone rebases its deadline and plays one REST.
Use the real Start now control before the final-five threshold. Require one
immediate native Go, one revision and exact progress/outcomes, with no warning
or extra Go through the abandoned deadline. Observe active runtime/ledger/native
silence beyond that boundary before offline 2/2 completion, exact result/receipt
and pruning. Preserve all prior records/preferences/entries/original AVD disks.
Next: guarded implementation/build, five staged checks, ordinary/preservation/
UI/inventory/settings/final audits. Physical acceptance remains separate.

Implementation adds guarded early-rest offer/receipt and preparation/recovery
methods. It compares the entire Start now runtime to the resumed copy changing
only revision/status and clearing rest fields, requires native Go before the
final-five boundary, then polls exact active state/ledger and initialized native
silence through deadline +1,500 ms. A 20 ms native timeline and independent
artifact validator check early Go/no warning/no delayed cues alongside exact
receipt/pruning/preservation. Existing recovery modes keep their assertions.
Both test APKs build (`iteration-72-test-build.log`, 59s); all 16 original disk
hashes match before launch. Only verified copied AVDs are used. Next: staged
acceptance with explicit phone listener readiness and charging-overlay checks.

Both preparation stages pass on the first attempt for
`a138c9cb-a5b4-4e84-a88b-2bd94f5f442c`, with the phone's registered-listener
marker observed before Wear starts. Actual Pause/native cancellation and hidden
frozen state pass. The host relaunches only copied Home after runner exit,
confirms live PID **2929**, force-stops it with no remaining PID, syncs/stops
only the verified phone and removes the copied bridge. Airplane-mode offline
watch reboot and bounded charging-overlay observation precede fresh recovery.
No unrelated records/data or system settings are cleared. Offline acceptance
is running; exact receipt/pruning and final preservation checks are next.

First offline recovery passes OK (1 test), PID **2596 -> 2327**, preserving
**20 seconds** and observing **224** exact/silent active-state samples beyond
the abandoned deadline. The immediate-active/offline screenshots look correct,
but the beyond-deadline screenshot is black from display idle. Retain this
first attempt; strengthen the late-cue check with repeated Wake events, explicit
Awake power evidence and non-ambient presentation during polling, then repeat
with a new owned fixture. This prevents display-idle cancellation masking a
late-cue defect and does not change session state or system settings.

Initial phone receipt check separately fails before inspecting records because
the native peer list is still empty after bridge restoration. Add a bounded
30-second exact-peer wait for this mode before retaining the same identity
assertion. Complete and preserve the first fixture's exact receipt/pruning
before staging another; no history or pending result is discarded. Build and
retry those checks, then perform the stricter awake acceptance.

The first fixture's receipt/pruning and independent state/native/preservation
checks pass after peer discovery settles: **58/58** prior records exact,
records **58 -> 59**, receipts **35 -> 36**, exact 2/2 result and original
preferences/entries. Its initial receipt-discovery failure and complete logs/
artifacts are retained under `iteration-72-first-*`. Go is observed **217 ms**
after Start now and **10,218 ms** before the final-five threshold, with no late
cue observed; the display-idle limitation remains explicit and this attempt
does not establish the stricter awake result. Awake build passes (1m 50s);
final source/peer-readiness test build passes (32s). Both fresh test APKs are
installed. The new fixture rerun follows first receipt/pruning; all prior
history, including the first result, remains preserved.

Both stricter preparation stages pass for
`3c68d00c-0718-468a-acb4-9875e7907a4f`, with first result/history retained.
The same guarded offline force-stop/reboot protocol is running. Final accepted
logs/artifacts will refer to this awake rerun; the first fixture's successful
but display-limited evidence and receipt discovery failure remain historical.

The stricter offline stage passes OK (1 test), fresh PID **3447 -> 2359** and
confirmed terminated PID **3617**. Frozen **20 seconds** remain exact through
fresh Home/opened PAUSED UI. After intentional Resume/REST, actual Start now
adds one Go/revision and only clears rest fields. **223** observations keep the
entire active runtime/ledger/native silence exact beyond the abandoned deadline,
with repeated Awake/non-ambient assertions and saved power evidence. The
beyond-boundary screenshot now visibly shows SET 2/2; it and the offline summary
are reviewed. The immediate-active capture has a black frame, retained as a
capture limitation; full transition/native Go evidence and the later visible
active screen independently establish this case. Exact offline 2/2 completion
passes. Original airplane mode is restored; final receipt/preservation checks
are running on the same copied phone/bridge.

Final evidence supersedes those pending labels:

- All **five accepted stages** pass OK (1 test) for the awake rerun:
  `iteration-72-preparation-{phone,wear}.log`, `iteration-72-offline-rest.log`
  and `iteration-72-receipt-{phone,wear}.log`. Retained first-stage evidence
  remains display-limited; the initial receipt peer-discovery failure is
  superseded by bounded exact-peer readiness. No result/history is discarded.
- Independent `validate-iteration-72.ps1` /
  `iteration-72-preservation-validated.json` prove exact paused state/ledger/
  **20 seconds** beyond the original deadline (opening **36,162 ms** later),
  a new deadline/one REST only on explicit Resume, and the complete Start now
  runtime changing only revision/status/rest fields. One native Go is observed
  **159 ms** after Start now and **10,295 ms** before the final-five threshold.
  All **223** foreground and **19** hidden observations pass. Awake/non-ambient
  polling and power evidence cover **1,514 ms** beyond the abandoned deadline,
  with exact active runtime/ledger and no warning or additional Go. The
  **1,170-sample** native timeline includes one Checking sample, **74** REST,
  **64** Go-speaking samples and zero warning samples, with no delayed cue.
- Full offline 2/2 result equality, one new receipt, pruning and success
  tombstone pass. All **59/59** prior records (including the first fixture) stay
  exact: records **59 -> 60**, receipts **36 -> 37**. Original legacy entries/
  restored preferences are byte-exact. Accepted Wear/phone artifacts contain
  **86/3 files**. Awake beyond-boundary and offline screens are reviewed; the
  immediate-active black capture is retained without substituting it for full
  runtime/native evidence. Copied request history now holds 60/64 records.
- Final test APK build passes (`iteration-72-final-test-build.log`, 32s);
  earlier build/awake build logs remain retained. Ordinary mutation-free tests
  pass **9 phone / 33 Wear**. Production/PWA code is unchanged; existing shared
  78/phone 28/Wear 204 JVM and PWA baselines remain applicable. Crash buffers
  are empty and current native focus released. Original airplane 0, absent/
  disabled accessibility, touch exploration 0 and animator 1.0 are restored.
  Inventory `20261004T154013950Z-iteration-72-final` verifies API 35/API 37,
  app 1.0/code 1 and installed/local production APK equality; Wear remains
  `46F8C23A388D37E144C7DA2E2FC5B58E3E0977A5D472D6CCF8F73C8E3DF12C27`,
  phone `21F9C264A1311BED0BD0FD94FD3BA52DAE730E4158CE90140B2DE02A0C6DF0C0`.
- Sync/stop only verified copies. All **16** original disk hashes match
  before/after; final ADB inventory is empty. Physical acoustic/routing/tactile/
  battery/Play observations remain open and are not established by this run.

Completed: guarded early rest exit after fresh offline paused-rest recovery,
exact transition/one native Go, no delayed cues through an awake abandoned
deadline, offline result/receipt/pruning, peer readiness and preservation. No
production defect is established. Stage 19 stays **70/97 (72%)**, physical
**0/27**. Next: Start now while resumed REST speech is still active, verifying
native Go supersedes that speech without duplicate/late cues and preserving
the exact offline result/receipt. Closure awaits final Git/doc check.

Final closure: `git diff --check` passes; Git is rechecked at HEAD `9764243`,
with only the seven expected Iteration 72 test/docs files modified and no
unrelated changes. All relevant code/emulator checks above pass; this iteration
is validated and uncommitted. Earlier pending statements, initial receipt
discovery failure and display-limited first fixture remain historical evidence,
superseded by explicit awake acceptance and preservation results.

Commit checkpoint — 2026-10-05: the user requests only the commit. Re-read the
plan/latest iteration, confirm the seven expected files and passing diff check,
and commit as `PST01: Validate voice-enabled early-rest process recovery`.
This supersedes Iteration 72's earlier uncommitted labels. The validated source
has not changed since acceptance; only checkpoint documentation is updated.
The next implementation remains planned, not started by this commit request.


## Iteration 73 — 2026-10-05 — Start now during recovered REST speech

Status: **Implementation in progress; paired/device acceptance pending.**

Starting checkpoint: `7dc85f8`, clean tree. Iteration 72 is committed; this
supersedes its historical uncommitted closure. Stage 19 remains 70/97 (72%),
physical Phase 4 0/27. The current Mac has no connected ADB devices; the
Windows copied-pair evidence remains historical and is not reproducible here
without preparing a new isolated pair. Do not mutate the original Mac AVDs.

Scope: extend the existing guarded early-rest recovery fixture with a distinct
recovery method that clicks Start now while native resumed REST is demonstrably
in flight. Retain exact transition, one deadline-bound Go, awake abandoned-
deadline silence, offline completion and receipt/preservation assertions.
Five requested iterations will cover this fixture, controller replacement,
native callback ownership, recovered runtime boundaries and final integration
review. Code/JVM/build results will be separated from pending paired execution.
Next: implement the in-flight recovery method and build the test APKs.

Validation/closure: `:app:assembleDebugAndroidTest
:wear:assembleDebugAndroidTest --offline --no-daemon` passes (1m 30s). The
baseline Wear JVM suite passes (54s). The new opt-in
`recoverSpeakingVoiceRestAndCompleteOffline` shares early-rest preparation and
receipt stages, requires `quickStartVoiceInterruptRestSpeech=true` alongside
the existing early-rest flag, excludes the idle recovery method, and records
the native REST witness immediately before the accessibility click. It skips
slow resumed screenshots in this mode and retains all subsequent exact Go,
awake late-cue, result and preservation assertions. Production code is unchanged.
Code/build checkpoint complete; paired execution is **pending**, not closed.
Next: deterministic controller replacement regression coverage.

## Iteration 74 — 2026-10-05 — Delayed REST completion during Go

Status: **Implementation complete; focused JVM validation running.**

Findings: native callbacks can finish the old REST after Go has become active.
Add two controlled-completion tests covering priority replacement and explicit
START_NOW cancellation. Require the old completion to leave Go pending, lower
priority cues refused without ledger/haptic changes, duplicates suppressed and
the replacement completing normally. No production defect is assumed.
Next: run the controller suite and retain its result before closure.

Validation/closure: focused `WatchCueControllerTest` passes (8 tests, 23s),
including both new delayed-completion schedules. No controller defect is
established and no production change is needed. Code/JVM checkpoint complete;
this does not establish native playback or device acceptance. Next: exercise
the real session cue emitter and recovered ledger across replacement.

## Iteration 75 — 2026-10-05 — Session emitter replacement and ledger recovery

Status: **Implementation in progress; JVM validation pending.**

Scope: connect the production session emitter/controller/store to a controlled
speech output. Keep REST outstanding while real emitter Go cancels it, then
deliver the old completion after Go admission. Verify exact prescribed scripts,
deadline-bound keys, haptics, cancellation, no active-session warning and
recreation replay suppression. The fake output only controls callback timing;
it supplies no evidence of native TTS/focus/acoustics. Next: focused JVM check.

Validation/closure: `SessionCueRecoveryTest` passes (1 integration test, 18s).
The production emitter emits exactly `Rest for 20 seconds.` and `Go. Set 2.`,
retains the two exact deadline-bound keys, cancels via START_NOW, ignores
rest/warning calls carrying ACTIVE state, and suppresses both replays with a
new store/controller. No production defect is established. Code/JVM checkpoint
complete; native/device validation remains pending. Next: recovered session
timer, duplicate action and persistence checks through the abandoned deadline.

## Iteration 76 — 2026-10-05 — Recreated session and abandoned deadline

Status: **Implementation in progress; focused JVM validation pending.**

Scope: recreate the real session ViewModel from a frozen paused-rest repository
after the old deadline, require silent exact recovery, then explicit Resume and
two immediate Start now taps. Observe the whole ACTIVE state and persistence/
cue counts beyond the new abandoned deadline, then recreate ACTIVE state again.
Also inject a rejected Start now commit: no Go or runtime change may escape;
a later successful retry must transition once. Next: run SessionViewModel tests.

First focused run: 26/27 pass; the new recreation test incorrectly expected
zero writes at legacy start-gate admission. Inspection shows the existing gate
re-persists the identical recovered session once; the complete session equality
assertion already passes. Correct the write-count expectations for both opens
while retaining exact state, one Start now commit, no abandoned-deadline writes
and no cue replay. This is a fixture expectation error, not a production defect.
Next: rerun the focused suite before closing this iteration.

Validation/closure: corrected focused `SessionViewModelTest` passes **27/27**
(31s), including both new cases. Whole paused/active state, exact rebased
deadline, one duplicate-safe exit write, no timer writes/cues through deadline
+1,500 ms and silent reopened ACTIVE state pass. Rejected commit emits nothing;
retry succeeds once. Existing legacy admission's identical write is explicitly
accounted for. Code/JVM checkpoint complete; physical/native checks stay open.
Next: strengthen the native fixture's own evidence validation, run combined
suites/builds and review the exact commit diff.

## Iteration 77 — 2026-10-05 — Native evidence checks and commit-readiness audit

Status: **Implementation/review in progress; integration validation pending.**

Scope: make the opt-in speech-interruption fixture validate its own native
timeline and bound replacement Go latency below the controller speech timeout.
Document explicit preparation/recovery/receipt methods and isolation guards.
Reconcile stale latest-checkpoint/next-action summaries while preserving prior
evidence. Run all three JVM suites, production/test APK builds and whitespace
review; inspect final Git again. Stage 19 counts remain unchanged because these
headless improvements cannot close physical acceptance. Next: final validation.

Final validation/closure — supersedes the pending code-check labels above:

- Combined Gradle command passes in **1m 20s**:
  `:shared:test :app:testDebugUnitTest :wear:testDebugUnitTest
  :app:assembleDebug :wear:assembleDebug :app:assembleDebugAndroidTest
  :wear:assembleDebugAndroidTest --offline --no-daemon --console=plain`.
  XML reports prove **78 shared / 28 phone / 209 Wear**, **315 total**, zero
  failures/errors/skips. Five new meaningful JVM cases cover two controller
  schedules, emitter/ledger recovery and two session persistence/timer cases.
- Phone/Wear production and instrumentation APKs build successfully. Existing
  AGP deprecation and two accessibility-node warnings remain; no build failure.
  PWA/production sources are unchanged, so no new browser/native behavior is
  claimed and no redundant PWA test cycle is needed for this test/docs slice.
- New native recovery method validates observed REST within 250 ms before the
  click, Go within two seconds, sampled REST/Go ordering, no REST/warning return
  and existing exact ledger/awake late-cue checks. It is **built, not executed**.
  The host has no connected ADB devices; original Mac AVDs and user data are
  untouched. Native speech/focus, five-stage result/receipt preservation, disk
  audits and physical acceptance remain pending for this new mode.
- Review corrects the documented phone receipt method name and includes its
  required exact `lifecycleResultId`. Roadmaps identify `7dc85f8` as the latest
  commit, preserve historical evidence and use the actual next action.
  Checklist recount remains **70 checked / 27 open / 97 total** (72%),
  Phases 0–3 complete, physical Phase 4 **0/27**. Stages 17/18 also retain their
  separate device-acceptance work. There is no defensible whole-plan ETA from
  these counts; the physical battery item alone needs a 60–90 minute run.

Completed: five requested code/review iterations (73–77), guarded planned native
fixture, five JVM regressions, self-validating native evidence and actionable
paired protocol, full tests/APK builds, plan reconciliation and commit review.
No production defect is established. **Code/test/docs checkpoint complete and
uncommitted; new paired/device acceptance is not complete.**

Next: execute all five explicit resumed-REST interruption stages on verified
isolated copies, with complete native/result/receipt/preservation evidence;
then continue the 27 physical items. Suggested test/docs commit:
`PST01: Add recovered rest speech interruption coverage`.
Final Git and whitespace verification follow this documentation update.

Final Git audit: HEAD remains `7dc85f8` on `PST01`; `git diff --check`
passes. Exactly **nine expected files** are changed (four Wear test sources,
including the new untracked emitter integration test, and five documentation
files). No unrelated changes or production sources are modified. Review finds
no commit-blocking code/test issue. Ready for the suggested **test/docs** commit;
no commit is created because the request is to check changes for commit.
Paired execution stays explicitly pending.


## Iteration 78 — 2026-10-05 — Timed exercise circular countdown on Wear

Status: **Code/native round-UI validated; committed on 2026-10-05 in the audited
timer/recovery checkpoint. Paired and physical acceptance pending.**

User promotes timed-exercise UX ahead of the pending paired speech fixture:
Wear first, a circular dynamic countdown, and automatic set completion/rest
at zero. Preserve all nine uncommitted Iterations 73–77 files. Samsung's
official workout guide confirms countdown/workout-timer and routine flows;
public examples support timer-focused watch layouts, but the exact modern
timed-set circular treatment is not established by official screenshots.
Implement the user's requested interaction using Pasingot's design language.

Plan: recognize explicit unambiguous duration prescriptions; add backward-
compatible durable timed-set deadline/frozen-millisecond fields; initialize/reset
each timed set, freeze on Pause, resume exact time, and auto-complete through the
existing atomic set/history/outcome path only while foreground/interactive.
Ambient stops ticking; wake catches up once rather than advancing unseen through
multiple sets. Rep prescriptions remain manual. Render a prominent ring/time,
final-five state and paused remaining time, respecting reduced motion/ambient.
Validate parser/recovery/duplicate/failure/boundary behavior and full JVM/APK
builds. Native/round-layout and physical battery/cue acceptance stay separate.
Next: implement durable timer and focused watch UI.

Implementation: additive deadline/frozen-millisecond fields in shared SessionState,
strict duration recognition (`30 sec`, `2 min`, `1 min 30 sec`, fractions),
atomic timer initialization/reset/pause/resume in the shared Wear session engine,
250 ms foreground UI ticks without persistence writes, existing durable
completion/rest/cue path and retry after a failed zero-boundary commit. Native
Quick Start validation rejects illegal timer field/status combinations. Timed
UI adds perimeter progress, large mm:ss, final-five color/state and paused time;
rep UI stays manual. Seven focused JVM cases and a detached native round-UI
fixture are added; the new blank `Pasingot_Timed_UI` AVD uses only its own disks.

Initial combined build finds duplicate generated `... 2.dex` artifacts in Wear
build intermediates, not duplicate source declarations. Retain the failure;
use Gradle `:wear:clean` and rebuild all affected tests/APKs. No user data or
source files are removed. Validation is running; no acceptance claim yet.

Clean rebuild additionally finds a cross-module Kotlin smart-cast/type-range
error in the newly added Quick Start timer validation. Correct it using local
nullable values and explicit Long ranges. Native fixture constructor and sender
seams are corrected before acceptance. Eight focused JVM cases now cover parser,
old-record decoding, timer reducer, auto-rest/reset, exact paused recreation,
hidden one-set catch-up, failed-save retry and manual/expiry race. The default
native run skips this detached fixture unless explicitly opted in, and requires
only the new `Pasingot_Timed_UI` profile. Next: finish clean builds and native UI.

Clean combined validation passes (6m 50s) with `--max-workers=2`: shared **78**,
phone **28**, Wear **217**, total **323**, zero failures/errors/skips. Both
production and both instrumentation APKs build. Generated duplicate dex/report
artifacts and initial compiler errors are superseded by the clean pass; no
acceptance assertion is relaxed. Original non-timer suites, prior five regression
cases and eight new timer cases pass. Test APK is installing on the verified
disposable 480x480/API 37 Wear AVD for the explicit detached UI fixture.
Next: inspect native screenshots and exact UI pause/auto-rest/reps assertions.

First native fixture retains all five captures but ends with a label lookup
timeout on rep-mode Pause, whose control is below the visible success header.
Timed Pause/Resume, exact frozen state, one automatic set/rest and manual rep
mode already pass. Visual inspection confirms the large ring/countdown and
paused remainder, but the final-five capture shows a stale six-second green
frame rather than proving the intended warning state. Keep the failed log and
first captures. Improve the fixture with cache-cleared current accessibility
trees, scrolling for off-screen controls and an explicit visible `Finishing`
label before capturing the final-five state. Do not change production behavior
or relax any timer assertion. Rebuild the test APK and repeat acceptance.

Final review adds a visibility re-check inside serialized automatic completion:
a slow set-adjustment save can hold the transition mutex while timer expiry is
queued and the screen subsequently hides. The queued expiry must not complete
unseen after that write releases. Manual completion keeps its existing path;
wake catches up normally. A ninth focused JVM case holds a real fake-store
commit, queues expiry, hides, releases, and checks exact unchanged progress
until wake. Rebuild/check the final production/test APKs before native rerun.

Final evidence — supersedes pending validation labels for Iteration 78:

- Final Wear JVM/production/test APK command passes in **1m 17s** after the
  visibility guard: **218 Wear** tests, zero failures/errors/skips. Earlier
  clean shared **78**/phone **28** results and both phone APK builds remain
  applicable because subsequent source changes are Wear-only. Total **324**
  JVM tests pass; all four APK builds pass. Nine new focused timer cases cover
  durable clock and concurrency boundaries. `git diff --check` passes.
- Native accepted log `output/emulator-validation/iteration-78-ui-accepted.log`
  reports **OK (1 test)** in **38.25s** on the verified fresh blank
  `Pasingot_Timed_UI` API 37/480x480 profile. Current production/test APKs are
  installed. UI taps exercise timed Pause/Resume and rep Pause; exact frozen
  state, one automatic completion/rest, and manual rep mode pass. Six artifacts
  under `iteration-78-accepted` include five visually reviewed screenshots and
  `checks.txt`: full-size active ring/time/Pause; paused 0:19; warning-colored
  0:04 `Finishing` ring; automatic 0:08 rest/success; manual 8 reps/Complete set.
  The retained first failure and stale capture are superseded by cache-cleared
  lookups, scrolling and explicit rendered-state observation.
- Detached native fixture uses the real screen/session engine with an in-memory
  store and NoOp cues; it does **not** establish native TTS/haptics, DataStore
  process-death/reboot, paired timed Quick Start result/receipt or physical
  battery/ambient/acoustic acceptance. JVM tests establish additive old-record
  decoding and paused recreation. Original AVD disks/profile settings and
  workout/history data are never opened for mutation; only the new profile
  receives installs/UI work. Exact AVD name is rechecked before owned shutdown.
- Existing Iterations 73–77 uncommitted changes remain intact. PWA is untouched
  per the user's Wear-first choice. Stage 19's existing checklist remains
  **70/97**, physical **0/27**; this shared-session timer enhancement has its
  own follow-up device checks and does not close a physical item.

Completed: user-requested watch countdown/ring, final-five treatment, automatic
set/rest through atomic outcomes, per-set reset, exact Pause/Resume/recovery,
foreground/queued-expiry safety, error retry, rep/manual distinction, nine JVM
regressions, native round-UI acceptance and all APK builds. **Code/emulator UI
checkpoint validated, uncommitted; paired and physical timer acceptance pending.**
Next: test timed sets on a physical watch and with the paired production
Quick Start store/result/receipt path, then return to resumed-REST speech
interruption acceptance. Suggested feature commit:
`PST01: Add Wear timed-set circular countdown and automatic rest`.
Final Git/AVD shutdown audit follows the documentation update.

Final closure: rechecked Git at HEAD `7dc85f8`, with 17 expected source/test/docs
files changed across preserved Iterations 73–77 and new Iteration 78; no
unrelated edits are reverted and no commit is made. `git diff --check` passes.
Owned `Pasingot_Timed_UI` shutdown succeeds and final ADB inventory is empty.
Iteration 78 code/emulator UI validation is complete; the listed paired/
physical follow-ups remain open.


Commit audit — 2026-10-05

The user authorizes committing the existing changes after audit. Inspect all
17 expected source/test/docs files across Iterations 73–78, including the four
new files. Review additive timer persistence and validation, exact pause/reset,
foreground/queued-expiry protection, atomic completion/history ordering,
duplicate/failed-write coverage, round UI, guarded native fixtures and roadmap
accuracy. No commit-blocking finding or unrelated change is found. Production
source is unchanged since the accepted timer/native checkpoint.

Combined shared/phone/Wear JVM plus both production/test APK validation passes
in **18s**, all tasks up-to-date against the audited sources. XML reports remain
**78 shared / 28 phone / 218 Wear**, **324 total**, zero failures/errors/skips.
Retained exact native timer UI evidence is **OK (1 test)**, 38.25s; no repeat
is needed because visible production source is unchanged. `git diff --check`
passes. Existing AGP warnings remain non-fatal. Paired speech interruption and
real timed-result/receipt, process/reboot and physical acceptance stay open;
Stage 19 remains 70/97, physical 0/27.

Commit the full audited checkpoint as
`PST01: Add Wear timed-set countdown and recovery coverage`. This supersedes
Iterations 73–78's historical uncommitted/no-commit labels; prior evidence and
failed attempts remain preserved. No additional implementation iteration is
started by this commit request. Next: paired/physical timed-device acceptance,
then resumed-REST speech acceptance. Recheck staged content and Git after commit.


## Iteration 79 — 2026-10-06 — Durable first timed Quick Start deadline

Status: **Code/JVM validated; committed as `faea1df`. Device acceptance pending.**

Progress audit: clean PST01 at `39a3835`, the committed Iterations 73–78
checkpoint. Stage 19 remains 70/97; physical acceptance remains 0/27. ADB
inventory confirms no connected devices. Original AVD profiles are untouched.

Finding: Quick Start initialized ACTIVE elapsed time before receipt transport
and Go playback, but deferred first-set timer creation until SessionViewModel
opened. Slow transport/speech or process interruption could start the timer
late. Save the explicit-duration deadline with the initial runtime. Existing
runtime retry preserves its original deadline; repetition prescriptions remain
manual. Add delayed-receipt/offline-retry and mixed-prescription regressions.
Next: run Wear JVM checks and production/test APK builds, audit, then commit.

Closure: Wear JVM suite passes **220 tests**, zero failures/errors/skips;
production and instrumentation APK builds pass (1m 23s). `git diff --check`
passes. Audit confirms the deadline is created atomically with ACTIVE, only
for the first explicit-duration prescription; retries return existing runtime
without another write. Two new regressions pass. No UI/PWA changes or device
acceptance claims. Iteration 79 code/checkpoint validated and ready to commit.
Next: real-file active/paused timer and final-result/receipt recovery coverage.


## Iteration 80 — 2026-10-06 — Real-file timed Quick Start recovery

Status: **Code/JVM validated; committed as `61bc734`. Device acceptance pending.**

Iteration 79 is committed as `faea1df`. Add two real Preferences DataStore
regressions, closing/joining each writer scope before reopening the same
TemporaryFolder file. Cover active deadline, exact 28,750 ms pause, delayed
resume, unchanged Started receipt/outcomes, atomic two-set completion,
stale duplicate rejection, offline final-result transport, wrong-revision
receipt rejection, exact cleanup, and non-resurrection after another reopen.
Use the production runtime store and session adapter. This is JVM file/storage
evidence, not native process death, reboot, paired transport or physical proof.
Next: run Wear JVM suite, audit file-scope isolation and commit.

Closure: all **222 Wear JVM tests** pass, zero failures/errors/skips (1m 12s).
Both new file-scope cases pass. Review verifies private temporary files, joined
DataStore scopes, exact complete runtime comparisons, immutable Started/result
identity and mismatch-safe cleanup. Production sources/APKs are unchanged from
validated Iteration 79. `git diff --check` passes. Iteration 80 is code/storage
validated and ready to commit; native/paired/physical acceptance remains open.
Next: run the actual SessionViewModel timer through QuickStartSessionStore,
covering hidden fresh-instance recovery and failed expiry persistence/retry.


## Iteration 81 — 2026-10-06 — Production timer engine/Quick Start adapter integration

Status: **Code/JVM/build validated; committed in the timer-integration checkpoint. Device acceptance pending.**

Iteration 80 is committed as `61bc734`. Exercise the real SessionViewModel,
QuickStartSessionStore and serialized QuickStartRuntimeStore together. Add
three cases: expired hidden fresh-instance recovery admits one set only when
foreground; subsecond paused runtime recreates/resumes exactly; failed expiry
write emits no result/success, retries once with durable offline final result,
and completed recreation/manual duplicate cannot replay success. Private
in-memory serialized persistence and a controlled clock make these headless
integration checks, separate from Iteration 80 real-file evidence.
Next: combined shared/phone/Wear JVM checks and all four APK builds, audit and
commit; refresh paired acceptance protocol and current roadmap evidence.

Closure/audit: combined Gradle validation passes in **51s** using offline
existing caches and two workers. XML reports prove **78 shared / 28 phone /
225 Wear = 331 JVM tests**, zero failures/errors/skips. All four production/
instrumentation APK build targets pass. Seven meaningful regressions are added
across Iterations 79–81. `git diff --check` passes. Existing AGP deprecation
warnings remain non-fatal. No PWA changes require browser validation.

Review verifies atomic initial deadline before receipt/Go, unchanged runtime
on retry, exact DataStore scope teardown, outcome/result identity, stale write
and wrong-receipt refusal, visibility admission and exactly-once retry/success.
No further commit-blocking defect is found. Plans distinguish current 331-JVM
proof from historical Iteration 78 native UI acceptance; updated native,
paired, reboot and physical acceptance remains pending. Current device protocol
records the required first-deadline/pause/hidden/receipt evidence.

Completed: Iterations 79–81 code/storage/integration checkpoint and audit;
79 is committed in `faea1df`, 80 in `61bc734`, 81 ready to commit. This run
closes at three iterations within the user's maximum of five, with the next
plan action at device acceptance. No original AVDs, settings, workout data or
history are mutated; no device execution is claimed. Stage 19 stays 70/97,
physical 0/27. Next: current-APK native/paired timed process/reboot/result/
receipt validation, then physical timer acceptance and resumed-REST speech
interruption. Final Git verification follows commit.

Commit closure — 2026-10-06: audited Iterations 79–81 are committed under
`PST01: Persist timed Quick Start deadline before receipt and Go`,
`PST01: Verify timed Quick Start recovery across DataStore scopes`, and
`PST01: Validate timed Quick Start session integration and recovery`.
This supersedes their historical pending/ready-to-commit labels. Relevant
checks pass as recorded above; no additional implementation iteration is
started by commit closure. Paired and physical acceptance stays open.


## Iteration 82 — 2026-10-07 — Progress audit and isolated native readiness

Status: **Validated; uncommitted. Actual paired/native-cue/physical acceptance pending.**

Clean baseline `5c4df77`; Iterations 79–81 are committed. Stage 19 is
70/97 (72%), physical 0/27. The plan has 2,027 lines/13,573 words and the
progress log 5,320 lines/42,797 words before this run; these include historical
evidence, not remaining task counts. No defensible elapsed-time estimate exists.
ADB inventory is empty after starting its local bridge with sandbox approval.
Only the two original profiles are registered; neither is booted or modified.
Create a fresh workspace-owned `Pasingot_Timed_UI` profile from read-only
hardware configuration, with new disks, for current-APK round UI acceptance.
Five iterations in this run: readiness/current UI, native paused fixture,
fresh-process paused recovery, hidden expiry/result/receipt recovery, final
combined validation and commit review. Private native fixtures use production
DataStore/engine with controlled time; paired transport/native cues/physical
acceptance remain separate. Next: boot the disposable profile and build APKs.

Iteration 82 retained failure: cached Wear dex intermediates contain duplicate
`QuickStartStartCoordinator$1 2.dex`; production source is not duplicated.
Run `:wear:clean` before rebuilding. Disposable AVD boots and exact name is
verified. New fixture source is being prepared for Iterations 83–85; no native
acceptance is claimed until current APKs build and explicit stages pass.

Retained first current-UI attempt times out after 17.608s: `active.png` shows
the fresh-install POST_NOTIFICATIONS system prompt covering the screen. Grant
that permission only on the owned disposable AVD and retry; preserve the log
and capture. Early install attempt before the clean test APK existed failed
without starting instrumentation; retry uses the completed 1m46s build.

## Iteration 83 — 2026-10-07 — Native durable paused runtime fixture

Status: **Validated with full fixture audit; uncommitted. Actual paired/native-cue/physical acceptance pending.**

Add opt-in `TimedQuickStartRecoveryTest` with exact blank-AVD guard, private
Preferences DataStore file, production engine/adapter, full runtime snapshots
and boot/PID markers. Preparation refuses to overwrite prior owned evidence.
No normal runtime store, legacy history, Activity or phone transport is used.
First explicit `preparePausedRuntime` passes **OK (1 test), 0.494s**: durable
30-second active deadline, exact Pause at +1,250 ms leaves 28,750 ms, unchanged
Started identity and zero outcomes/results/success. Clock/cues/initialization
are controlled seams; no native speech or real phone Start claim.
Next: reboot the owned AVD and compare exact paused runtime before explicit
Resume, then save hidden active runtime for another reboot.

Iteration 82 closure (supersedes initial failed/pending UI labels): current
Wear production/test APKs build after generated-artifact clean. Explicit
round-UI retry passes **OK (1 test), 33.102s**; six accepted artifacts retained
under `output/emulator-validation/iteration-82-ui-accepted`. Screenshots are
reviewed for active, paused, final-five, rest and manual-rep layout. Permission
prompt failure/capture remains in `iteration-82-ui`; only disposable AVD
permission is changed. Current UI/readiness checkpoint validated.

## Iteration 84 — 2026-10-07 — Native paused recovery after actual reboot

Status: **Validated; uncommitted. Actual paired/native-cue/physical acceptance pending.**

Execute only the owned `Pasingot_Timed_UI` reboot. Host and fixture independently
require changed Android boot ID; each method also records process ID. Compare
full paused runtime from the private file, not only countdown text. Controlled
clock +90,000 ms represents being beyond the abandoned deadline. Explicit
Resume must derive +118,750 ms from the saved 28,750 ms, retain Started identity,
and remain without outcomes while hidden. Next: inspect explicit native result,
then reboot again for hidden-expiry/final-result/receipt acceptance.

Iteration 84 closure: explicit stage passes **OK (1 test), 1.511s** after
actual reboot (boot ID `c228b99d…` -> `97c071ef…`). Exact paused snapshot
survives; visible opening leaves it unchanged, Resume derives saved remainder,
Started identity stays exact, no completion/success/transport occurs. Hidden
runtime snapshot is retained. Native controlled-clock reboot checkpoint
validated; actual paired/native-cue/physical acceptance remains open.

## Iteration 85 — 2026-10-07 — Native hidden expiry, offline result and receipt reboot

Status: **Validated; uncommitted. Actual paired/native-cue/physical acceptance pending.**

Reboot the owned AVD again, open beyond the timed deadline with controlled
clock, require exact unchanged hidden runtime and zero outcomes. Foreground
may complete one set and initialize the next duration; final expiry must save
one exact offline result/success, reject duplicate completion/replay and wrong
receipt revision. Exact synthetic receipt clears only the private runtime.
Reboot a third time and require the receipt tombstone/no resurrection.
Next: retain exact native snapshots and explicit logs, ordinary instrumentation,
APK identity and final source/docs/commit review.

Iteration 85 first explicit stage passes **OK (1 test), 2.677s** after changed
boot ID `97c071ef…` -> `84367cc7…`. Full hidden snapshot remains exact until
foreground, which commits one set/revision and resets the next deadline.
Final expiry durably saves 2/2 and one offline result/success; duplicate
completion and recreated engine preserve the entire terminal runtime without
replay. Wrong receipt revision preserves it, exact synthetic receipt clears it.
Third reboot/tombstone stage is running; closure waits for that explicit pass.

Iteration 85 closure: tombstone stage passes **OK (1 test), 0.541s** after
third reboot (`84367cc7…` -> `26376e61…`). Runtime stays absent; exact receipt
is already cleared and replay initialization returns the retained receipt.
All four explicit private-store native stages pass across three actual reboots.
Full native snapshots, receipt and boot/PID files are retained in
`output/emulator-validation/iteration-85-native-evidence.tar` and extracted
owned evidence directory. This validates native storage/engine controlled-clock
reboot behavior; real paired delivery/package/cue pruning, natural wall-clock
timing, native acoustics and physical checks remain pending.

## Iteration 86 — 2026-10-07 — Combined validation and commit-readiness audit

Status: **Validated; uncommitted. Actual paired/native-cue/physical acceptance pending.**

Review the new guarded fixture and all docs, requiring private file ownership,
joined DataStore scopes, main-thread model cleanup, immutable full snapshots,
changed boot identity, exact foreground/outcome/result/receipt comparisons and
accurate evidence boundaries. Production/PWA source is unchanged.
Combined Gradle validation passes **30s**, **78 shared / 28 phone / 225 Wear
= 331 JVM tests**, zero failures/errors/skips; all four APK targets pass.
Retain the initial command failure (`:shared:testDebugUnitTest` does not exist
in this plain Kotlin module); corrected `:shared:test` command passes.
Current round-UI and four explicit native stages pass as recorded above.
Ordinary mutation-free instrumentation is running; final installed APK/hash,
crash, evidence, whitespace and Git review follows. No commit requested/made;
prepare reviewable changes and a suggested commit message. Next after closure:
paired actual timed Ready/Start and result/receipt/package/cue pruning, native
wall-clock/cue checks, then resumed-REST speech and 27 physical acceptance items.

Iteration 86 validation/audit closure: ordinary Wear instrumentation reports
**OK (39 tests), 0.333s** with opt-in mutation cases skipped; it is guard/discovery
evidence, not 39 newly executed device behaviors. Both installed/current Wear
APK SHA-256 comparisons pass (recorded in `iteration-86-audit.txt`), and
post-final-reboot crash buffer has no FATAL EXCEPTION. All five accepted UI
screens are visually reviewed; active Pause, paused Resume/remainder, warning
ring/Finishing, automatic rest and manual rep controls are visible.

Review finds no commit-blocking issue. Production source and PWA are unchanged;
new code is the four-stage guarded native fixture. Private DataStore scopes
join after main-thread ViewModel cleanup; atomic clock and volatile observations
support exact assertions. Baseline/full runtime comparisons preserve outcome/
Started/result identity; recovery requires actual changed boot ID and receipt
revision matching. No actual native acoustic silence/playback is claimed from
NoOp output. Plans/acceptance docs now distinguish this completed private native
slice from real paired Start/transport, natural timing, cues and physical work.
`git diff --check` passes. Iterations **82–86 (five)** are validated, uncommitted,
with prior failure/pending labels preserved and superseded by the accepted
results above. Suggested commit: `PST01: Validate native timed runtime recovery across reboots`.
Next: actual paired timed Ready/Start and result/receipt/package/cue pruning,
native timing/cues, resumed-REST speech interruption and physical acceptance.
Stage 19 remains 70/97 (72%), physical 0/27. Final Git, private-file guard and
owned AVD permission/shutdown audit follows; no commit is made by this request.

Final closure: private runtime file SHA-256 is unchanged by ordinary no-flag
instrumentation, confirming the guarded fixture did not mutate its retained
tombstone. Temporary notification permission is revoked on the disposable
AVD; exact `Pasingot_Timed_UI` identity is rechecked before owned shutdown.
Current Git remains HEAD `5c4df77`, with six expected files (one new native
test and five docs), no unrelated tracked changes and no commit. Original
AVDs/settings/workout histories are not opened for mutation. Whitespace and
final Git/device inventory checks follow this log update. All five iterations
are complete within their stated native/code evidence boundaries.

Final device inventory is empty after owned shutdown. Final tracked diff and
new native-file whitespace checks pass; Git HEAD and the six-file scope remain
unchanged. Checkpoint is ready for commit, with no commit-blocking findings.


Commit closure — 2026-10-07: user authorizes committing Iterations 82–86.
The six expected files match the validated/audited scope; retained 331-JVM,
four-APK, current round-UI/four-stage native reboot and installed-hash evidence
remains applicable. No source changed after that validation. Commit as
`PST01: Validate native timed runtime recovery across reboots`. This supersedes
historical uncommitted/no-commit labels; actual paired/native-cue/physical
acceptance remains pending. Five further iterations begin after this commit.


## Iteration 87 — 2026-10-07 — Committed checkpoint and native timing readiness

Status: **Validated; uncommitted. Actual paired/speech-interruption/physical acceptance pending.**

User-authorized Iterations 82–86 commit is `4da1e87`; Git is clean afterward.
Stage 19 remains 70/97, physical 0/27; documents are roughly 2,045 plan /
5,490 progress lines, mostly retained history. No reliable finish-time estimate
is established. Original phone/watch AVDs remain untouched. Boot only the
existing workspace-owned blank `Pasingot_Timed_UI` to discover actual connected
peers/native TTS readiness. Paired acceptance requires a connected phone and
cannot be replaced by private fixtures. Advance the independently available
native timer/cue path while keeping the actual paired protocol pending.
Five iterations: native readiness, natural deadlines with native TTS, hidden
Activity expiry, actual-clock paused reboot/recovery, combined commit audit.
Next: add guarded private production-store/gate/engine/emitter native fixture.

Iteration 87 closure: native readiness passes **OK (1 test), 5.247s**;
current test APK builds in 21s. Actual TTS is AVAILABLE, `Go.` is observed
with native isSpeaking and completes. Connected-peer inventory is retained;
paired acceptance remains pending without an exact connected phone. Private
production-store/gate/engine/emitter fixture and reproducible staged protocol
are added. Initial non-escalated build could not write Gradle's wrapper cache;
normal approved build succeeds. Native readiness checkpoint validated.

## Iteration 88 — 2026-10-07 — Natural timer/rest deadlines with native speech

Status: **Validated; uncommitted. Actual paired/speech-interruption/physical acceptance pending.**

Run two actual eight-second timed sets with twelve-second rest. Production
Ready/Start coordinator saves the first deadline before a 750 ms delayed,
intentionally offline Started client. Real SessionScreen/engine entry must not
rebase it. Verify first expiry/rest commit, next-set duration, native REST /
five-second / Go / workout-success ordering and bounded actual timestamps.
Private package/runtime/cues share owned DataStore; no normal data, phone
transport, physical haptics/acoustics or battery result is claimed.
Next: inspect native timeline and exact 2/2 offline result; then hidden Activity
expiry without state/cue changes and terminal speech non-replay.

Iteration 88 initial run passes **OK (1 test), 33.225s** with native observed/
completed REST, warning, Go and success. Review tightens timestamp evidence:
record first native isSpeaking time separately from speak-admission time and
assert warning/Go thresholds against actual observed playback. Retain the first
pass; rebuild and rerun in a new owned run directory without overwriting it.
This strengthens measurement, not a production change or relaxed assertion.

Iteration 88 closure: strengthened playback-timestamp run passes
**OK (1 test), 34.276s** in retained `playback/natural` directory. Actual
native isSpeaking timestamps satisfy warning (3,000–5,500 ms before rest
deadline) and Go (0–2,000 ms after) bounds. All four cues are observed and
complete, with one native haptic call each, exact 2/2 offline result and
first/new-set deadline checks. Both earlier and final passes are retained.
No production defect is found; natural native timer/cue checkpoint validated.

## Iteration 89 — 2026-10-07 — Hidden Activity expiry and native success non-replay

Status: **Validated; uncommitted. Actual paired/speech-interruption/physical acceptance pending.**

Use actual SessionScreen lifecycle: move Activity to STARTED (ON_PAUSE without
ON_STOP save), preserve full runtime and private cue ledger beyond the real
first deadline, require no haptic/speech output. RESUMED must admit one set
only and initialize the next duration. Final native success must occur once;
duplicate completion and recreated engine cannot replay speech/haptics/result.
This validates real Activity lifecycle, not actual ambient/sleep. Next: native
paused runtime through an actual reboot and abandoned real deadline.

Iteration 89 closure: **OK (1 test), 24.056s**. Actual Activity STARTED
preserves full runtime/cue ledger beyond the real timed deadline with no
output; RESUMED commits one set/revision, initializes the next timer and
naturally completes 2/2 offline. One native workout-success speech/haptic is
observed/completed; duplicate completion and new engine/native output instance
retain exact terminal state with no replay or extra result send. Validated.

## Iteration 90 — 2026-10-07 — Actual-clock paused timer reboot with native cues

Status: **Validated; uncommitted. Actual paired/speech-interruption/physical acceptance pending.**

Start one actual twenty-second timed set, Pause through the real engine and
retain full private runtime, exact frozen milliseconds, cue state and Android
boot ID. Reboot only the owned AVD. Fresh SessionScreen/native output must
leave state/cues exact and silent beyond the abandoned actual deadline.
Explicit Resume derives its deadline from saved milliseconds; natural expiry
saves one exact offline final result and one observed/completed native success.
No mocked clock is used. Next: inspect both explicit stages/boot identity and
retained evidence; combined validation, ordinary guard/APK/focus/crash audit.

Iteration 90 preparation passes **OK (1 test), 15.347s**. Exact paused
runtime/frozen milliseconds/private cue state and boot marker are durable;
no native output occurs. Actual reboot recovery is running and must pass
before this iteration closes. Initial timer deadlines use real epoch time,
independent of Iterations 83–85's controlled-clock evidence.

Iteration 90 retained failed recovery (18.501s): paused full state/cues and
silence survive reboot, but fixture's `delta <= 500 ms` compares deadline
origin to the caller's pre-queue wall time. Actual main-thread scheduling
latency is 1,194 ms. That assertion does not establish a duration error.
Repair measurement with a recording clock callback that returns unmodified
System.currentTimeMillis; require deadline minus exact frozen milliseconds
to equal a real transition clock read, plus one revision/unchanged outcomes/
Started identity and cleared frozen field. No duration assertion is weakened,
no clock is mocked, and no production source changes. Retain failed runtime/
logs; prepare and reboot a fresh `retry` run directory rather than overwriting.

Retained accepted natural timeline: first observed native warning is
**4,699 ms before** the rest deadline; observed Go is **287 ms after**.
All retained focus-after files show no remaining Pasingot audio focus owner.
The failed paused run's frozen duration is 12,938 ms; it is retained intact
as first-attempt evidence, not used to seed the fresh retry.

Iteration 90 fresh `retry` preparation passes **OK (1 test), 11.55s**;
repaired test APK builds successfully. Final combined Gradle command passes
in **27s**: all 331 JVM tests and four APK targets remain validated against
current source. Fresh owned reboot/recovery is running with exact recorded
real-clock assertions; failed first-run archive is preserved separately.

Iteration 90 second recovery retained failure (29.857s): exact recorded-clock
Resume assertions pass; completion times out. Evidence identifies actual
charging-overlay foreground ownership (`ChargingComposeActivity`) and the
private engine durably PAUSED with `app_closed`, 6,171 ms remaining and zero
outcomes/output. Production correctly handles foreground loss; no production
fix is justified. Preserve `iteration-90-retry-evidence.tar` and logs. Record
owned battery baseline, temporarily use `cmd battery unplug` and wake/Home
only on the disposable AVD to dismiss the known post-boot overlay. Repeat
preparation/reboot in fresh `awake` directory, resetting battery simulation
at closure. Timing/state/cue assertions remain unchanged.

Iteration 90 fresh awake preparation passes **OK (1 test), 5.141s**.
Post-boot charging-overlay removal/wake is included in the owned reboot
protocol; final strict recovery is running against the unchanged repaired
source and installed test APK. No visibility override is used.

Iteration 90 final closure: awake recovery passes **OK (1 test), 24.434s**,
with changed boot ID `b406e2e4…` -> `8b27175b…`. Full paused runtime/cues
stay exact without output beyond original real deadline. Resume deadline
minus saved frozen milliseconds exactly matches a recorded real transition
clock read; one revision/unchanged outcomes/Started identity pass. Natural
expiry saves one offline final result and observed/completed native success.
Both earlier failures and archives remain retained and are superseded only
by this successful awake fixture. No production change or relaxed native
visibility/state/duration assertion is needed. Native real-clock reboot slice
is validated; paired/physical/speech-interruption acceptance remains open.

## Iteration 91 — 2026-10-07 — Final native evidence and commit-readiness audit

Status: **Validated; uncommitted. Actual paired/speech-interruption/physical acceptance pending.**

All five explicit native stages pass within Iterations 87–90; measured natural
warning/Go times, Activity hidden-state/success non-replay and real paused
reboot recovery are retained. Production/PWA code is unchanged. Final combined
Gradle command passes in 27s, all 331 JVM reports remain zero failures/errors/
skips and all four APK targets pass. Export all private native fixture evidence
into `iteration-91-native-evidence.tar` and extracted directory, preserving
first/retry failures. Audit exact resumed duration/native success threshold,
audio-focus release, ordinary guard/discovery, installed APK hashes, crash
buffer and every changed source/docs file. Next: restore owned battery/
notification permission, verified shutdown and final Git/whitespace review.
Suggested commit: `PST01: Validate native timed cues and wall-clock recovery`.
Actual paired timed Ready/Start/result/receipt/package/cue pruning, resumed-
REST speech interruption and physical 0/27 remain next.

Iteration 91 closure: exact real paused fixture freezes **16,916 ms**; resumed
deadline origin equals a recorded real clock read. Native success is observed
**249 ms after** resumed deadline. Ordinary runner reports **OK (44 tests),
0.353s**, with opt-in cases skipped; all six retained private DataStore files
match their pre-run SHA-256. Both installed Wear APK hashes match current
builds; all retained stages release Pasingot audio focus, final boot crash
buffer has no FATAL EXCEPTION. Audit files and full fixture snapshots are
retained in ignored validation output. Source review finds no commit blocker:
exact AVD/run guards, refusal to overwrite, joined DataStore/model teardown,
real clock/volatile observations, durable-before-output checks and evidence
boundaries are intact. Production/PWA source remains unchanged.

Five iterations **87–91** are complete within stated native boundaries.
Prior Iterations 82–86 are committed in `4da1e87`; this new checkpoint is
validated and uncommitted. Plans and acceptance protocol record current native
real-time/speech proof separately from pending paired transport, resumed-REST
speech and physical acceptance. Stage 19 remains 70/97 (72%), physical 0/27;
no reliable finish-time estimate is inferred from that ratio. Suggested commit:
`PST01: Validate native timed cues and wall-clock recovery`. Next: actual paired
timed Ready/Start/result/receipt/package/cue pruning, resumed-REST speech
interruption and physical validation. Restore owned battery/notification
permission and verify shutdown/Git after the final doc/whitespace update.

Final closure: owned battery simulation is reset (updates are no longer
stopped), temporary POST_NOTIFICATIONS permission is revoked, and exact
`Pasingot_Timed_UI` identity is checked before shutdown. No original AVD or
workout history is opened for mutation. Git still has the six expected new
checkpoint files at HEAD `4da1e87`; no unrelated edits are reverted and no
second commit is made. New test and tracked diff whitespace checks pass.
Final inventory and Git recheck follow; checkpoint is ready for commit.

Final ADB inventory is empty. Git/whitespace recheck confirms unchanged
HEAD `4da1e87` and the six expected uncommitted files. Five new iterations
are validated and ready for commit; no commit-blocking finding remains.


Commit closure — 2026-10-07: user authorizes committing Iterations 87–91.
Six expected files match the validated/audited scope. Retained 331 JVM tests,
four APK builds, native real-clock/speech/hidden/paused-reboot evidence and
installed/private-file/focus/crash audits remain applicable; source has not
changed since final validation. Commit as
`PST01: Validate native timed cues and wall-clock recovery`. This supersedes
historical uncommitted labels; actual paired, resumed-REST speech and physical
acceptance remain pending. Begin five further iterations after commit.


## Iteration 92 — 2026-10-07 — Committed checkpoint and resumed-REST native readiness

Status: **Validated; uncommitted. Actual paired UI/transport/physical acceptance pending.**

Iterations 87–91 are committed in `6296b4f`; Git is clean afterward.
Stage 19 is 70/97 (72%), physical 0/27. Plan/progress are roughly
2,063/5,700 lines, mostly retained history; no reliable finish-time estimate.
Five new iterations: owned native readiness, paused REST preparation, actual
reboot/in-flight REST Start now with no late cues, fresh offline final result/
synthetic receipt/runtime-package-cue pruning, final combined commit audit.
Continue pending resumed-REST native interruption without substituting this
private slice for actual paired phone/UI/transport or physical acceptance.
Boot only existing workspace-owned `Pasingot_Timed_UI`; original profiles/
workout data remain untouched. Next: native readiness and staged fixture code.

Iteration 92 retained test-build failure: FinalQuickStartResult has no
watchNodeId property; obtain the exact target from its durable session package
when constructing receipt envelopes. Correct fixture reference; production
schema/source is unchanged. Record owned battery baseline and temporarily
unplug/wake/Home to avoid the known charging overlay; notification permission
is granted only on the owned AVD and will be restored with battery state.

Iteration 92 closure: accepted test APK builds in **29s** after the retained
fixture reference repair. Native readiness passes **OK (1 test), 5.23s**;
actual TTS is available, observed and completes. Connected-phone inventory
remains empty; no paired acceptance is claimed. Staged private resumed-REST/
receipt-retry code and protocol are ready for explicit native validation.

## Iteration 93 — 2026-10-07 — Paused REST preparation with actual speech cancellation

Status: **Validated; uncommitted. Actual paired UI/transport/physical acceptance pending.**

Start two actual six-second timed sets with thirty-second rest via private
production gate/coordinator/store. First automatic set creates rest; native
REST must be observed in-flight immediately before Pause. Pause must retain
one completed set, exact frozen rest seconds, full runtime/cue ledger, old
deadline and boot ID, with cancelled REST and no final result. Next: archive
this stage before generic speech files are reused, reboot only the owned AVD,
then exact paused recovery and Start now during resumed native REST.

Iteration 93 closure: **OK (1 test), 8.717s**. First timed set completes
automatically; actual in-flight REST is cancelled by Pause, one REST haptic
and reserved cue remain, one completed set/full paused rest state and no final
result are retained. Preparation archive `iteration-93-native-evidence.tar`
preserves native cancellation evidence before next-stage generic files reuse.

## Iteration 94 — 2026-10-07 — Rebooted resumed REST and native Start now interruption

Status: **Validated; uncommitted. Actual paired UI/transport/physical acceptance pending.**

Actual changed-boot recovery compares full paused runtime/cues beyond the
original real deadline with initialized native silence. Resume uses frozen
seconds/new real deadline, one intentional REST; Start now must target native
in-flight REST and replace it with one observed/completed Go within two
seconds. Finish 2/2 offline with one native success; keep foreground through
the abandoned resumed-rest deadline and require exact terminal runtime/ledger
and no late warning/REST/Go. Next: archive native timeline, then fresh final
result/receipt validation and interrupted cue-pruning recovery.

Iteration 94 closure: **OK (1 test), 38.073s** after actual changed boot
`9ed5f94c…` -> `e8b30a0f…`. Exact paused runtime/cues and initialized
silence survive beyond the original rest deadline. Resume emits intentional
REST; Start now interrupts actual in-flight REST, replaces it with native
observed/completed Go within two seconds, naturally completes 2/2 offline
with one success/result send. Foreground silence/exact terminal runtime and
ledger hold through the abandoned resumed-rest deadline; no warning/returning
REST/late Go occurs. Archive `iteration-94-native-evidence.tar` pins timeline
before later-stage generic evidence reuse. Private native slice validated;
actual paired UI/transport/speech and physical acceptance remain pending.

## Iteration 95 — 2026-10-07 — Fresh offline result and interrupted receipt pruning

Status: **Validated; uncommitted. Actual paired UI/transport/physical acceptance pending.**

Reboot owned AVD, retain exact final runtime/cue ledger and native silence
while opening completed state. Wrong receipt sender/revision/path must preserve
all three private stores. Exact synthetic receipt uses production payload
coordinator; inject cue-pruning failure only after durable runtime/package
tombstones. Reboot again and replay exact receipt to finish cue pruning,
reject all obsolete/success native cues and request/runtime resurrection.
Next: archive each stage and require both explicit passes before final audit.

Retained Iteration 94 native timeline: Go is first observed **975 ms after**
actual Start now. Resumed REST is observed then cancelled (success=false);
Go/workout success are observed and complete. Ledger has four durable keys
(including original preparation REST); no late cue alters them.

Iteration 95 first stage passes **OK (1 test), 9.132s** after another actual
reboot (`e8b30a0f…` -> `dfa89656…`). Exact final runtime/cues survive and
opening is silent/no result resend. Wrong sender/revision/path preserve
all stores. Exact receipt durably clears runtime/package before intentional
cue-pruning failure; full prior cue state remains exact. Partial archive
`iteration-95-partial-evidence.tar` is retained before final reboot/retry.
Final pruning/replay stage is running; iteration closure waits for its pass.

Iteration 95 closure: final pruning retry passes **OK (1 test), 12.907s**
after changed boot `dfa89656…` -> `0e41463f…`. Runtime/package tombstones
survive actual reboot while cue ledger remains exact until receipt replay.
Production coordinator replay finishes cue pruning; acknowledged success
tombstone suppresses all six cue kinds on actual native output, with no
speech/haptics. Runtime/request reinitialization is refused, exact receipt
replay is idempotent and stores remain pruned. Both stages pass; private
synthetic receipt recovery is validated. Actual paired receipt/Data Layer
cleanup remains pending.

## Iteration 96 — 2026-10-07 — Final speech/receipt evidence and commit audit

Status: **Validated; uncommitted. Actual paired UI/transport/physical acceptance pending.**

Five explicit native stages pass across Iterations 92–95 and three actual
reboots. Retained native REST/Go/success timeline, exact paused/final/cue
snapshots, identity, receipt and partial/pruned tombstones establish the private
slice. Export final evidence to `iteration-96-native-evidence.tar` and extracted
directory, retaining per-stage archives before generic files reuse. Combined
Gradle check passes **27s**, all four APK targets and **331 JVM tests** remain
validated; production/PWA source is unchanged. Audit new staged code, docs,
ordinary no-flag guard/discovery, current installed hashes/private file bytes,
all retained focus release and final crash buffer. Next: update roadmap, restore
owned battery/notification permission, verify owned shutdown and final Git.
Suggested commit: `PST01: Validate resumed REST speech and receipt cleanup recovery`.
Actual paired timed Start/result/receipt and resumed-REST UI/transport, then
physical 0/27 remain pending; Stage 19 remains 70/97 (72%).

Iteration 96 closure/audit: ordinary Wear runner reports **OK (48 tests),
0.341s**, with opt-in cases skipped; this is guard/discovery evidence, not 48
new device behaviors. All seven retained private DataStore files remain
SHA-256 exact. Both installed/current Wear APK hashes match, accepted speech/
final stages release focus and final boot crash buffer has no FATAL EXCEPTION.
Current XML reports are **78 shared / 28 phone / 225 Wear = 331**, zero
failures/errors/skips; all four APK builds pass in the 27s combined command.

Review finds no commit-blocking issue: scoped opt-in/AVD/run guards, refusal to
overwrite preparation, joined DataStore/model teardown, true native in-flight
observations, exact runtime/outcomes/deadlines, durable-before-output identity,
wrong-receipt preservation, deliberate cleanup failure/reboot retry and
idempotent cue/package/runtime tombstones remain intact. Source changes only
add four staged native test methods; production/PWA code is unchanged.
Roadmap/acceptance docs distinguish private native evidence from actual paired
UI/transport and physical acceptance. Earlier compile failure and historical
next/pending labels remain retained/superseded by accepted results.

Five iterations **92–96** are complete and validated, uncommitted. Previous
checkpoint is committed as `6296b4f`. Stage 19 stays 70/97 (72%), physical
0/27; actual connected-phone inventory is empty. Next actual paired timed
Ready/Start/result/receipt and resumed-REST UI/transport require a connected
pair, followed by physical checks. No reliable finish-time estimate is inferred
from checklist ratio/history length. Suggested commit:
`PST01: Validate resumed REST speech and receipt cleanup recovery`.
Restore owned battery/notification permission, verify identity/shutdown and
recheck Git/whitespace before reporting this ready-for-commit checkpoint.

Final closure: owned battery simulation is reset (updates no longer stopped),
temporary notification permission is revoked and exact `Pasingot_Timed_UI`
identity is verified before shutdown. Original profiles/history are untouched.
Git remains HEAD `6296b4f` with six expected modified source/docs files;
no unrelated edit is reverted and no new commit is made. Whitespace checks
pass. Final device inventory and Git recheck follow this log update.

Final ADB inventory is empty. Git/whitespace recheck confirms HEAD
`6296b4f` and the six expected uncommitted files. Five iterations 92–96
are validated and ready for commit; no commit-blocking findings remain.


Commit closure — 2026-10-07: user authorizes committing Iterations 92–96.
Six files match accepted native speech/receipt scope; retained 331 JVM/four
APK, five native stages, per-stage archives and installed/private-file/focus/
crash audits remain applicable. No source changed after validation. Commit as
`PST01: Validate resumed REST speech and receipt cleanup recovery`. This
supersedes historical uncommitted labels; actual paired UI/transport and
physical acceptance remain open. Begin five paired-readiness iterations next.


## Iteration 97 — 2026-10-07 — Committed checkpoint and isolated paired environment

Status: **Environment audited; paired acceptance pending companion setup.**

Iterations 92–96 commit is `f2658ad`; Git is clean afterward. Stage 19 remains
70/97 (72%), physical 0/27. Plan/progress are 2,081/5,858 lines before commit
closure, primarily historical evidence; no reliable time-to-finish estimate.
Next actual paired acceptance needs exact connected phone/watch nodes and
matching installed signatures, not another private runtime fixture. Prepare a
fresh workspace-owned `Pasingot_Pair_Phone` (API 37.1 Play Store, 1 GB RAM)
and reuse only owned `Pasingot_Timed_UI`. Original profiles/disks/history remain
stopped and untouched; source config is read solely for hardware/image paths.
Official Android pairing guide requires companion app; inspect installed
phone package/SDK/IDE prerequisites before claiming connectivity. Five
iterations: environment, native readiness inventory, owned bridge/connectivity
attempt, repeatable preflight tooling, validation/commit audit. Device acceptance
may remain blocked by external setup; keep code/tooling and paired proof separate.
Next: boot the owned phone/watch and inspect actual prerequisites.

Iteration 97 closure: both owned profiles boot and exact names are verified;
new phone disks are workspace-owned, shared system-image resources remain
read-only. Phone has Google Play Store/GMS but no Pixel Watch or legacy Wear
companion. Android Studio's installed primary implementation queries GMS
EMULATOR get-pairing-status; owned Wear reports local `f81011dd` with null
peer, phone broadcast reports no pairing status. No actual pairing is claimed.
Official guide requires companion installation/setup; no bundled companion
APK exists in this installed IDE. Environment inspection is complete, with
external companion setup still pending. Next: native application API/signature
inventory on both owned peers, then exact bridge/readiness diagnosis.

## Iteration 98 — 2026-10-07 — Native paired readiness inventory

Status: **Inventory validated; actual connectivity unavailable.**

Add opt-in read-only instrumentation probes on phone/watch, bound to exact
owned AVD names. Record actual local/connected nodes, Wearable API failures,
API level, app/debug signature digests and companion installation into owned
external evidence without creating requests or modifying runtime/history.
Do not equate inventory OK with a connected pair. Current phone APK installs
only on fresh owned phone. Next: build/install probes and inspect native API
results on both peers.

Iteration 98 closure: both new test APKs build in **54s**. Phone inventory
passes **OK (1 test), 3.679s**; watch **OK (1 test), 1.444s**. Inventories
record matching app signing SHA-256 `f49623d0…`; watch local `f81011dd` and
zero peers, phone local null/zero peers with actual **Wearable.API unavailable
(ApiException 17 / API_UNAVAILABLE)**. Inventory execution passes; connectivity
acceptance does not. No user request/runtime/history is mutated by probes.
Evidence is retained in `iteration-98-{phone,wear}.json` and explicit logs.

## Iteration 99 — 2026-10-07 — Owned bridge and actual connection diagnosis

Status: **Experiment validated; connectivity blocked.**

Capture existing forwards/reverses, verify exact owned AVD identities, create
only vacant 5602/5601 endpoints with no-rebind, then use installed Studio's
refresh-emulator-connection operation and rerun both native read-only probes.
Always remove only endpoints created by this attempt and compare original
bridge state. Missing companion/phone Wearable API is an external prerequisite;
bridge alone must not be claimed as pairing or trigger synthetic acceptance.
Next: inspect actual results and build repeatable fail-closed preflight tooling.

Iteration 99 closure: explicit owned bridge/refresh produces the same native
result: phone Wearable API unavailable, watch has zero peers. Before/after
forward/reverse inventories are identical after removing only newly created
endpoints. No original mappings or device data are changed. Connectivity
experiment is complete; actual pairing remains blocked by companion setup.
Next: provide a repeatable preflight that fails closed on these prerequisites.

## Iteration 100 — 2026-10-07 — Repeatable paired preflight

Status: **Tooling validated; paired acceptance pending.**

Add standard-library `scripts/paired_preflight.py` with exact owned-AVD guards,
explicit native inventory probes, schema/package/signature checks, reciprocal
sole nearby peers and installed/current APK hashes. Refuse existing evidence
folders. Exit 0 means transport prerequisites ready, exit 2 means blocked,
exit 1 means capture failure; acceptanceValidated remains false in all cases.
Add meaningful tests for original-profile/evidence preservation and false-ready
conditions. Initial test-path attempt failed because root tests directory did
not exist; use scripts/tests and retain that setup failure as superseded.
Next: execute decision/safety tests and capture actual owned preflight.

Iteration 100 closure: **10 decision/safety tests pass**, including rejection
of original profiles before probe/evidence creation and preserving existing
evidence. Actual preflight exits **2**, correctly recording transportReady
false/acceptanceValidated false, current installed APK hashes and matching
native signatures. Blockers: phone API unavailable/local node missing, no
reciprocal peers, companion absent. `iteration-100-preflight` retains reports
and logs. Protocol explains setup and exit semantics; no acceptance item closes.

## Iteration 101 — 2026-10-07 — Final checks and commit review

Status: **Validated; ready for commit. Actual pairing blocked.**

Run all JVM suites and four APK targets, ordinary no-flag instrumentation
(discovery/guard only), review owned changes and preserve native blocker
reports. Confirm exact owned devices before shutdown, update current roadmap
status without replacing historical evidence, then recheck Git. Next actual
acceptance still requires external companion setup and native reciprocal peers.

Iteration 101 closure: combined offline Gradle verification **BUILD SUCCESSFUL
in 19s**, all four APK targets current. Retained JVM reports total **331 tests
(78 shared / 28 phone / 225 wear), zero failures/errors/skips**. No-flag phone
runner **OK (10 tests), 0.105s** and Wear **OK (49 tests), 0.41s** validate
ordinary discovery/guards; opt-in fixtures skipped, not behavior acceptance.
Review adds missing identity/digest rejection and checks native observation
against the device-clock capture window so stale markers cannot claim ready.
Expanded Python suite **11 tests passes**; final native preflight exits **2**
with current APK hashes, matching signing and the same real pairing blockers.
Evidence: `iteration-101-gradle.log`, both no-flag logs and
`iteration-101-preflight-final`. Whitespace/source review passes; no production
source or PWA changes. No commit-blocking code findings remain.

Only exact owned phone/watch profiles are stopped after identity verification;
bridge mappings were already restored. Workspace-owned profiles/evidence are
retained for companion setup, original AVDs/data remain untouched. Five
iterations 97–101 complete tooling/environment checks; actual paired and
physical acceptance remain open. Stage 19 stays 70/97 (72%), physical 0/27.
Next: install/setup official companion on the isolated phone, rerun preflight,
then actual paired timed Start/result/receipt and resumed-REST UI/transport.
This checkpoint is validated and uncommitted, following prior commit f2658ad.

## Iteration 102 — 2026-10-07 — Physical paired readiness

Status: **In progress; physical behavior acceptance remains open.**

User supplies physical Galaxy S25/Watch7 and authorizes continuing checks.
Watch debugging is rediscovered via mDNS and reconnects at its advertised
endpoint; phone remains authorized. Read-only inventory compares models/OS,
installed versions/APK hashes/signatures and actual reciprocal nearby nodes.
Existing emulator-only fixture guards remain intact: do not run mutation
fixtures on personal hardware or treat skipped tests as device acceptance.
Next: verify current installed builds and guide actual UI/physical observations.

Iteration 102 findings: physical S25 SM-S931B and Watch7 SM-L300 both run
Android 16/API 36. Native service inventories confirm phone local 1a3ea2de
and watch local 6b45442a as reciprocal nearby peers with active Bluetooth
connections. This supersedes the isolated-emulator companion/API blocker for
this physical pair only; emulator findings remain historical evidence.
Installed APKs date from September 10 and hashes differ from validated current
builds. Begin in-place `install -r` updates (phone also -t), preserving app data;
never uninstall/reset if signing or installation rejects. Initial read-only APK
pull timed out after 40s; device-side SHA-256 succeeds instead. Partial host APK
is not validation evidence. No physical behavior checklist closes on connection
inventory alone. Await active-session preservation answer before test workout.

Iteration 102 update: user confirms no active workout and ready to test.
Phone in-place update returns Success; app launch intent succeeds, but native
UI inventory shows the phone lock screen. Await user unlock; no lock bypass.
Watch update remains transferring over wireless ADB; both devices are online.
No installation reset/uninstall or workout mutation is performed. All current
behavior rows remain open pending normal UI observations and completed update.

Iteration 102 latest: watch in-place install also returns Success. Device-side
SHA-256 on both installed base APKs exactly matches current validated local
builds, whose apksigner certificates match f49623d0… on both components.
`updated-builds.json` retains proof. Both app launch intents succeed. Readiness
checks pass; physical behavior acceptance still pending user unlock and actual
Library → Ready prescription observation. Iteration stays open until that check
passes. Next: send one exercise (2 sets / 10 reps / 15s rest), compare exact
watch Ready before Start, then continue Started/result/receipt observations.

## Iteration 103 — 2026-10-07 — Physical completion button clipping

Status: **Code/build and physical clipping fix validated; uncommitted.**

User reports completing a 5/5 workout and a chopped Back to workout(s) button.
This is user-reported completion evidence, not a captured full acceptance run.
Terminal views use shared WatchAction, whose exact 44dp height can constrain
wrapped text on small round screens/font scales. Replace fixed height with a
52dp minimum allowing content to grow. Actual watch capture currently shows
the charging overlay despite wake/app-launch, not the reported button;
request normal completion-screen visibility before claiming visual validation.
Preserve completed workout/history and prior readiness edits. Initial edit
command used android cwd with repository-relative paths and failed without
changing source; corrected at repository root. Next: Wear build/JVM checks,
in-place update and physical completion-screen visual check.

Iteration 103 physical reproduction: waking/reopening after charging overlay
clears captures the actual completion view. `foreground-before.png/xml` shows
5/5 completed, Completed 5 / Skipped 0 / Pending 0, Sets 9/9, Saved on watch.
Back to workouts wraps onto two lines; lower line clips inside fixed-height
chip. Installed AndroidX 1.4.0 Chip source documents automatic content growth,
52dp default minimum and intrinsic sizing; our exact height overrides that.
Current fix restores unconstrained growth with minimum 52dp. Wear build/test
APK targets pass (45s); 225 Wear JVM tests, zero failures/errors/skips.
In-place physical fix installation underway; retain before image/history.
Next: verify installed hash, reopen completed view and inspect label fit.

Iteration 103 closure: in-place Wear install returns Success. Installed APK
SHA-256 f760fb2d… exactly matches the fixed local build. Physical Watch7
font_scale=1.0; completed Foundation A survives update (5/5, 9/9 sets,
Elapsed 1:24, Saved on watch). Reopen the saved completed card and scroll to
the terminal action: `fixed-button-final.png` visually confirms both text
lines fully inside the button, including complete lower-line glyphs and bottom
padding. `foreground-before.png` retains original cropped lower line.
Intermediate asleep/charging screenshots and a null-root UI dump are not
layout validation; dump failure left stale XML, explicitly superseded by fresh
awake screenshots after navigation. No synthetic fixture, new workout, reset,
uninstall or history clearing is used. 225 JVM tests and both Wear APK targets
pass; Git whitespace check passes. Code and observed physical defect fix are
complete; broader paired/physical checklist remains open, counts unchanged.
Next: continue actual Ready/Started/result/receipt and audio/haptic observations.

Persistence/visibility diagnosis — 2026-10-07, after Iteration 103:
User clarifies workout countdown reopens paused after app is replaced by watch
face; no claim of lost progress. Read-only durable Preferences store confirms
five completed exercise statuses, completedSets [1,2,2,2,2], resultSaved=true.
Watch is currently not charging; system watch UI is foreground while app PID
9343 remains alive. Earlier charging overlay is captured, but no lifecycle trace
of the original interruption establishes charging as its sole trigger.
SessionScreen ON_STOP/disposal calls saveOnExitIfActive; active sessions persist
PAUSED with APP_CLOSED reason. Ambient-only visibility handling does not pause
that state; resting sessions retain deadlines. Existing unit tests explicitly
cover active exit pause and hidden-rest recovery. No OngoingActivity integration
exists in Wear source/dependencies. Official Android always-on guidance states
watch-face timeout can hide apps, and Ongoing Activity keeps workout UI visible
through that timeout (https://developer.android.com/training/wearables/always-on).
Diagnosis: saved-data persistence works for captured completion; current exit
policy explains reported pause, with missing ongoing-workout visibility support.
Next: add session-scoped Ongoing Activity and validate natural timeout/ambient
on physical Watch7, preserving deliberate exit pause; no new runtime-policy or
personal settings change made during this diagnostic check.


Next-plan audit — 2026-10-07: device availability resolved; inventory evidence
from Iterations 102–103 satisfies Phase 4 item 26 (actual models/OS/app versions).
Close only that exit item: physical 1/27, Stage 19 71/97 (73%), remaining 26.
Historical 0/27 checkpoints remain preserved and explicitly superseded. No
other checkbox closes from partial 5/5 completion or layout evidence.
Next implementation is workout-scoped Ongoing Activity visibility, followed
by physical timeout/ambient proof. Then collect paired entries/Started/result/
receipt, offline/reboot/duplicate/expiry/nonreplacement regression, physical
speech/haptics/TalkBack/routing/fallback, 60–90 minute battery and Play internal
install/update/mixed compatibility evidence. Code completion and complete
physical acceptance must still close separately. No new runtime change in this
audit; uncommitted readiness/layout work remains preserved.

## Iteration 104 — 2026-10-07 — Completed watch history leaves phone Continue blocked

Status: **Code/browser and physical stale-session fix validated; uncommitted.**

Physical S25 screenshot shows 100% handled / five completed and Completed
watch snapshot, yet header Continue remains. Read-only verified WebView capture
records a separate active phone cursor for row IDs 1–5 at exercise 0/set 1;
player exists below the watch panel. Header only switches to Today, so tapping
while already on Today leaves viewport unchanged. Retain full before IndexedDB
and screenshot under phone-continue-20261007; no personal history reset.
Promote this observed defect ahead of planned Ongoing Activity work.
Add a pure fully-handled-history closure scoped to session date/exact row IDs,
serialized hook reconciliation on restore and incoming log changes, no duplicate
logs/events/health writes. Continue requests screen-owned player scroll/focus.
Next: partial/date/row/terminal preservation and real hook/browser checks,
PWA required build checks, then physical in-place update and evidence comparison.

Iteration 104 validation update: TypeScript and production Vite build pass;
Capacitor copy and phone Gradle unit/build target pass (15s, retained reports
28 phone tests, zero failures/errors/skips). Browser integrity harness now
passes 27 checks including same-date/exact-row reconciliation, partial/wrong
row/date/empty cursor rejection, retained terminal summary, startup/live closure
and no duplicate history/events/health writes. Full-app mobile smoke fixture
uses a unique disposable DB: Continue moves player top from 537.25px to 96.25px
and focuses its container. Completed fixture shows no Continue/player, complete
training and disabled Start. Screenshot retained under output/playwright.
Playwright wrapper was not executable and npx resolution stalled; installed
cached CLI works directly. Initial sandbox server bind fails; approved localhost
server succeeds. These setup failures do not replace accepted browser evidence.
Phone in-place update underway; preserve before IndexedDB archive for comparison.

Iteration 104 closure: phone install -r -t returns Success. Installed APK hash
115855bd… equals validated fixed build. Verified physical WebView records show
active cursor removed, no Continue/player, disabled Start for completed plan.
Exact before/after comparison confirms workouts, all 11 logs, session events,
set logs and every other appState record unchanged. Only stale active cursor is
removed. `preservation.json` retains assertions. Initial after screenshot is
asleep/black; `after-awake.png` supersedes it and visually confirms 100% handled,
five completed, Completed watch snapshot and no stale Continue. Scoped TCP9227
WebView forward is removed, task browser closed, devices remain available.
Composition review passes: App wires routing/display focus request, Today owns
scroll/focus, hook owns serialized persistence, pure helper owns row/date match.
TypeScript/build, 27 integrity checks, full-app mobile smoke, 28 phone JVM tests,
phone APK build, physical preservation/visual and whitespace checks pass.
Continue focus is browser-validated; no real unfinished personal workout is
created just to repeat that check. No full acceptance item closes from this
fix alone; Stage 19 remains 71/97, physical 1/27. Next: Ongoing Activity and
natural timeout/ambient test, then remaining paired/physical acceptance.

## Iteration 105 — 2026-10-07 — Ongoing workout visibility

Status: **Code/build and physical startup checks validated; timed acceptance pending.**

User authorizes continuing the plan. Add session-scoped native Ongoing Activity,
observing durable legacy/Quick Start changes from Application scope; no polling,
wake lock, foreground engine or background speech. One unambiguous active/rest/
paused target gets a silent ongoing workout notification; completion/end/receipt
cleanup removes it. Notification touch intent revalidates exact saved identity
before navigating directly to its session; stale/invalid targets cannot start
workouts. Keep deliberate lifecycle exit save/pause semantics. Next: pure target
regressions, native builds, actual notification/lifecycle checks, in-place
Watch7 update and user-driven natural timeout/ambient observations.

Iteration 105 first build: dependency resolves; compile fails because Quick
Start titles are nullable. Retain failed log and use a Workout display fallback
in notification and return-target validation; no workout payload is changed.
Rerun relevant checks before marking implementation complete.

Iteration 105 final-review build catches a Kotlin package qualifier shadowed by
local app Context. Replace it with an explicit schema-version import; retain
failed final log. Added permission-refresh on Activity resume (grant can occur
without repository changes) and fail-closed unknown entry schemas/read errors.
Rerun final native gates on this exact source; no device update yet.

Iteration 105 code checkpoint: final combined offline Gradle build passes in
28s, all four APK targets accepted. JVM total 335 = 78 shared / 28 phone /
229 Wear; zero failures/errors/skips. Four new target-policy tests cover
unfinished legacy/Quick Start identity, terminal removal, missing/invalid/future
records and conflicting targets. Permission grant refresh and saved identity/
schema revalidation are included in the exact accepted source. No runtime
schema/workout engine/lifecycle pause policy or PWA source changed this iteration.
Private-store baselines retained before in-place Watch7 update. Code validation
complete; physical timeout/ambient/one-tap return still pending user timed test.

Iteration 105 physical startup checkpoint: install -r succeeds; installed APK
5d5500e0… matches validated build. App launches, no WorkoutOngoing errors.
Exact private-store comparison shows workout downloads, log queue, session-event
queue, watch cues and Wear settings all unchanged. Completed workout publishes
no active notification ID 3. `ongoing-workout-105/install-readiness.json` retains
proof. Existing screen_off_timeout is 15000ms; doze_enabled query returns null
(no inference of actual AOD state), neither setting is modified.
No unfinished personal workout is created or restarted. Physical timed-session
indicator, natural timeout/ambient behavior, one-tap routing and terminal cleanup
after a new test remain unexecuted pending user readiness. Iteration stays open
for these device observations; code completion is separate. Proposed test: normal
phone Library Quick Start, one set / 3 min / 0s rest, Start on watch, leave it
off charger untouched >15s, wake and inspect running state, then verify final
completion/receipt and indicator removal. Preserve deliberate Home/back exit
pause policy and record any device-specific stop event before changing it.
Stage 19 stays 71/97, physical 1/27; current changes remain uncommitted.

## Iteration 106 — 2026-10-07 — Footer blocks Library Quick Start

Status: **Code/browser and physical WebView hit tests validated; uncommitted.**

User cannot tap Quick Start behind phone footer, blocking physical timed test.
Read-only physical WebView hit test reproduces selected action at y684–729,
footer y689–753; action center returns a footer tab, not the action. No dialog
is open: this is Library selection, not modal stacking. Capture geometry while
initial screenshot is asleep; keep screenshot limitation explicit. Mobile shell
gets a bounded scroll viewport above reserved footer area; desktop retains its
existing document layout. Move selected Quick Start above nested catalog so it
is available beside selection controls rather than beneath the scrolling list.
Preserve all workout/offer state. Next: mobile/desktop/modal hit-target smoke,
required PWA checks, in-place phone update and physical inspection.

Iteration 106 closure: TypeScript, production Vite build, Capacitor copy and
phone APK build pass (12s). Real-component browser fixture checks mobile
360x780/native-safe-area layout: main bottom 684px < footer top 703.61px,
selected-action center hits itself; modal Send also hits itself and isolated
handler fires once. Desktop 1024x768 retains static navigation/visible main
layout. In-place physical phone install succeeds; installed APK bc1c70ed…
exactly matches build. Normal Library select → Quick Start selected opens real
confirmation sheet. Physical geometry: selected action y270–315, main bottom
684, footer top703.60; center hit true. Send y710–755, enabled, center hit true
above footer's stacking layer. `physical-layout.json` retains accepted proof.
No Send/Start is invoked on the personal pair, no offer/workout/history is
created/reset; confirmation left open for user. Initial screenshot asleep and
later lock-screen capture are not UI evidence; unrelated lock capture discarded.
Scoped TCP9227 debug forward removed, task regression browser closed. Review
keeps layout in styles/screen components and no feature logic in App. Whitespace
and final Git recheck pass; prior unrelated readiness/runtime/layout edits are
preserved. Counts stay 71/97, physical1/27. Next: user unlocks phone and configures
one set / 3 min / 0s rest in the open sheet for Iteration105 timeout acceptance.


Commit closure — 2026-10-07: user authorizes commit/reinstall. Review all 28
owned files, retained 335 native JVM/four APK, 27 browser integrity/real-component
mobile/desktop/modal, 11 Python preflight and physical layout/preservation
checks. No production source changed since its relevant accepted checks. All
changes belong to Iterations 97–106; preserve prior history/evidence. Commit
as PST01: Fix physical workout controls and ongoing activity. Timed-session
visibility remains physical acceptance pending, not inferred from code tests.

## Iteration 107 — 2026-10-07 — Committed physical reinstall audit

Status: **Reinstall/startup/data and physical hit checks validated.**

Reinstall both committed production APKs with -r (phone also -t), preserving
personal app data. Verify exact physical device models, installed APK hashes,
signing correspondence, startup/ongoing error logs and actual reciprocal peers.
Capture durable baselines first; avoid starting or replacing a workout. Finish
with a Git recheck and record actual retained-data and UI readiness results.
Next: reinstall and inspect the exact checkpoint on physical S25/Watch7.

Iteration 107 closure: implementation checkpoint is 5014ec8 (28 owned files).
Phone and Wear in-place installs both return Success. Initial Wear attempt
reports transient device offline; it recovers, exact SM-L300/RFAY11LZ4SR identity
is verified, and retry succeeds. No uninstall, data clear or personal setting
change is performed. Phone APK embeds current validated PWA JS/CSS.
Installed SHA-256 matches current builds: phone bc1c70ed…, Wear 5d5500e0…;
both certificates match f49623d0…. Both apps start, no observed process-filtered
AndroidRuntime/WorkoutOngoing errors; native services confirm exact nearby
phone 1a3ea2de ↔ watch 6b45442a. Phone before/after full workouts/logs/session-
events/set-logs/appState records are identical (12 rows, 11 logs). Watch workout
downloads/log queue/session-event queue/watch cues/Wear settings match byte-for-
byte. Retained evidence: reinstall-107/{phone-preservation,verification}.json.

Reinstalled physical phone normal Library select → Quick Start selected → sheet
checks pass: main bottom669 < footer top688.60, selection action y270–315 hits
itself; Send y710–755 is enabled and hit-testable. No Send/Start is invoked.
Scoped ephemeral debug bridges are removed after verifying task-owned mapping;
devices remain connected. Whitespace/Git rechecks pass; no source change or new
test execution is needed for identical accepted artifacts. Commit this audit
separately so completed reinstall evidence is durable and worktree is clean.
Timed Ongoing Activity/ambient acceptance remains open in Iteration105; no
physical checklist item is inferred from reinstall. Counts stay71/97,1/27.
Next: actual one-set / 3min / 0s-rest test off charger and natural timeout/wake,
then result/receipt/indicator cleanup and remaining physical checks.

## Iteration 108 — 2026-10-08 — Current plan and device readiness audit

Status: **Documentation audit validated; device execution blocked.**

Read the plan, latest Iteration 107 and React instructions before work. Initial
Git status is clean; HEAD is 857ebe6 (reinstall evidence), production checkpoint
5014ec8. Sandbox ADB startup first fails to bind its listener; approved inventory
then succeeds and reports zero devices. This is not a pairing failure diagnosis
and does not invalidate yesterday's retained physical evidence. Correct the
stale delivery board from 70/97 and 0/27 to 71/97 and 1/27; prepend an explicit
current audit to plan, roadmap, Quick Start plan and acceptance protocol while
preserving history. 26 physical checks remain; no finish-date estimate is
supported. Relevant whitespace/count/document checks pass. Next: a portable,
read-only physical collector for this Mac; timed acceptance stays pending.

## Iteration 109 — 2026-10-08 — Portable physical inventory collector

Status: **CLI/missing-device checks pass; connected collection not executed.**

Existing physical collector requires PowerShell. Add Python standard-library
collector with explicit phone/watch serials, physical role/emulator guards,
selected-device-only property/package/battery reads, optional validated base
APK hashing and split-install limitations. Never starts workouts, installs,
resets, changes settings or runs instrumentation. Inventory readiness is
separate from transport/signing/acceptance; existing evidence is not overwritten.
CLI help passes. Initial py_compile cannot write the host cache under sandbox;
rerun with /private/tmp/pasingot-pycache passes. Actual approved collector exits
2, retaining zero-device evidence under iteration-109-no-devices; JSON assertions
confirm unavailable, no collected devices, unknown transport and unevaluated
acceptance. No personal device is mutated. Next: regression tests for wrong
selection, partial/failing collection, emulator refusal and artifact equality.

## Iteration 110 — 2026-10-08 — Physical collector regression coverage

Status: **Automated collection/preservation gates pass; connected execution pending.**

Add 13 collector tests using a strict fake command dispatcher that rejects any
unexpected/mutating operation and any unselected device probe. Cover unavailable,
offline/unauthorized/no-permission selections, invalid/equal serials, existing
evidence preservation, emulator/wrong-role/TV/unknown identity refusal, timeout
failure artifacts, missing app, malformed remote paths/hash, split-install
limitations and exact/stale APK comparison. Successful inventory never asserts
transport or acceptance. All 24 Python checks pass (13 new, 11 existing paired
preflight), with no failures; whitespace check passes. Connected reads and
physical timing remain unexecuted. Next: document the explicit timed physical
protocol and evidence boundaries, then final checkpoint review.

## Iteration 111 — 2026-10-08 — Physical timed acceptance procedure

Status: **Protocol/source consistency checks pass; physical test pending.**

Add explicit normal-UI one-set/3min/0s-rest procedure and portable collector
usage to DEVICE_ACCEPTANCE. Record readiness/installed-build limitations,
reciprocal peers, baseline preservation, Started identity, natural untouched
timeout/wake, deliberate-exit distinction, exact Ongoing Activity return,
natural expiry/final result/actual receipt cleanup and post-run comparison.
Do not substitute a synthetic receipt, active UI polling or an awake screenshot
for the required observations. Source checks confirm notification ID 3 and
runtime/package/result DataStore locations; CLI flags match implemented help.
Whitespace/source-reference checks pass. No physical checklist box closes;
26 remain open. Next: final review, fresh tooling gates and authorized commit.

## Iteration 112 — 2026-10-08 — Final review and tooling/docs checkpoint

Status: **Five preparatory iterations validated for commit; device acceptance open.**

Review all seven owned files from Iterations 108–112. Catch and fix physical
watch role validation returning before model/API completeness checks; extend
existing refusal test with missing watch model/API. Final 24 Python tests pass
(13 collector/11 paired preflight), both Python files compile with explicit
sandbox-writable cache, documentation/source/CLI references and whitespace
checks pass. Reviewed dispatcher permits only selected-device read commands;
APK paths are validated before shell hashing, existing evidence is preserved,
and inventory cannot assert pairing/acceptance. Actual final collector exits 2
with no devices and retains iteration-112-final/inventory.json. This does not
validate connected collection or any physical behavior. No Android/PWA app
source changes or installs; prior 335 JVM/four-APK evidence is historical,
not a new run. Git HEAD remains 857ebe6 before commit; only seven owned files
are changed, with no unrelated edits. Commit the validated tooling/docs stage
as PST01: Prepare portable physical acceptance validation. All five iterations
108–112 are complete within preparatory scope; Iteration 105 timed acceptance
stays open, Stage 19 remains 71/97 and physical 1/27. Next: reconnect S25/Watch7,
verify current inventory/peers, execute DEVICE_ACCEPTANCE timed protocol,
then complete remaining paired, audio/accessibility, battery and Play checks.

## Iteration 113 — 2026-10-08 — Physical timed acceptance in progress

Status: **Physical Library Ready/Started validated; failed timeout retained and superseded by Iteration 114 fix.**

User requests starting physical checks. Current checkpoint 44decb0/worktree
clean. After sandbox listener failure, approved ADB discovers physical S25
RFCY21CLYDR and Watch7 RFAY11LZ4SR over wireless debug. Portable collector
executes on the connected pair and reports physical_inventory_ready; both
installed base APKs match local accepted builds. Watch battery 45%, off all
chargers; notification permission granted, screen timeout 15000ms. Retain
inventory, native service peers, current screens and all five existing private
DataStore files under physical-113-before. Initial phone files/datastore lookup
fails because phone has no such directory; corrected per-device files listing
passes. Watch has no Quick Start runtime/package/result files. Legacy yesterday
entry is completed; today's downloaded entry has no session state. No unrelated
unfinished workout observed in watch baseline; phone baseline still required.
Wake/open normal apps for UI readiness; do not replace today's schedule.
Next: phone baseline, normal Library one-set/3min/0s-rest send and exact Ready/
Started observations, untouched timeout/wake and return, then result/receipt.

Iteration 113 readiness checkpoint: phone baseline captures all appState,
workouts, logs, sessionEvents and setLogs (12 rows, 11 logs); no active phone
workout. Exact protobuf map/value decoding passes for all five watch stores.
Both installed hashes match historical accepted production artifacts, and
native services report phone 1a3ea2de ↔ watch 6b45442a nearby. Normal apps are
opened; screenshots before wake are asleep and do not prove UI rendering.
Phone keyguard shows secure=true/showing=true/mTrusted=false, confirmed by a
later read. User unlock/wear/wake readiness requested; no test Send/Start or
private-store mutation performed. Connected collector baseline is validated;
physical timed behavior remains blocked on the locked phone. Retain complete
baseline and readiness-summary.json under physical-113-before. Verified task
TCP50340 debug bridge removed, no other mapping changed. Counts remain 71/97,
physical 1/27; iteration stays open. Next: unlock S25 with Pasingot visible,
wear/wake off-charger Watch7, then normal one-set/3min/0s-rest UI test.

Iteration 113 physical execution: user confirms ready, keyguard false verified.
Normal Library search Walking → Quick Start on watch → one set / 3min / 0s rest /
no load; native Send tap hits verified modal center. Actual request
45359db9-ed46-4308-b340-2ee08cdd6b87 arrives unchanged on Watch7, Ready phone/watch
screens captured. Native watch Start tap enters five-second gate, durable ACTIVE
and Started acknowledgement reaches the still-open phone. Ongoing ID3 is present.
Bixby Listening overlay temporarily takes window focus at 1791423049289,
returns at 1791423061566; saved timer stays active with deadline1791423200738.
After no further input, natural system screen_timeout=60 and sleep stop occur
at 1791423121579–1695. Read-only before-wake capture proves Dozing, PAUSED,
app_closed, exact79054ms remaining; no timeout acceptance pass. Effective
PowerManager screen timeout is60000ms despite earlier settings query15000ms;
no setting changed. Preserve failed run and full event/runtime/notification
trace under physical-113-{ready,timeout}. Close only Phase4 single Library Ready
and open-phone Started checks: physical3/27, Stage19 73/97 (75%),24 remaining.
Next: fix evidenced natural sleep being treated as deliberate exit; repeat
physical test after native regression/build and in-place state-preserving update.

## Iteration 114 — 2026-10-08 — Natural screen sleep must not pause exercise

Status: **Code/build, natural screen sleep, exact return and actual paired cleanup validated; broader ambient/audio acceptance pending.**

Promote the reproduced defect from Iteration113. ON_STOP always saves active
session as APP_CLOSED, including AOD-off natural system sleep. Android always-on
reference separates power-state transition from returning to watch face:
https://developer.android.com/training/wearables/always-on . Keep existing
foreground-only timer/output gate and deliberate Home/back/disposal exit pause;
on lifecycle stop, use actual PowerManager interactive state to distinguish
natural screen-off from interactive app exit. Preserve durable timer/deadline
while asleep, catch up one set only on foreground wake, with no wake lock,
background engine or polling. Next: real engine regressions/build, install -r
with exact saved-runtime preservation, then same-session physical timeout/wake.

Iteration114 implementation: add onScreenStopped(deviceInteractive) to session
ViewModel and wire lifecycle ON_STOP to actual PowerManager.isInteractive,
falling back to deliberate pause when service is unavailable. Visibility/output
always stop; natural screen sleep preserves deadline without a write, interactive
exit keeps pause. Back/disposal remain explicit exits. Two real-engine tests
cover no hidden write/cue/outcome beyond expiry, one-set foreground catch-up,
and exact179250ms frozen remainder on duplicate interactive stop. First Gradle
attempt is sandbox-cache denied; approved offline Wear suite/APK targets run.
Preinstall snapshot confirms original legacy/settings exact, test remainder79054ms.

Iteration114 build recovery: Gradle stalls reading stale generated values2.xml
while snapshotting merge resources. Preserve thread/file diagnostics, terminate
only verified owned client15611 and busy daemon10905, use temporary Wear build
outputs via ignored init script, no source/build-config change. Fresh compile/
production APK succeeds;231 tests run with one new-test failure: expected set
completion to emit an exercise-level log before both sets completed. Correct
that expectation to retained progress/one transition/no duplicate persistence;
production code unchanged by this correction. Retain failed build log and rerun
all Wear gates before any device install.

Iteration114 closure: final temporary-output build succeeds in52s,231 Wear
JVM tests zero failures/errors/skips and production/test APK targets pass. APK
output paths remain in repo despite temporary intermediates; initial copy-path
assertion fails (no copy occurs), then actual output metadata/mtime and DEX
onScreenStopped presence verify the validated source. Install-r succeeds with
ab72fb12348708874266fffbe369cd464b2341d0382b5e8512bc89bad22e1464 matching installed
base APK. All seven watch DataStore files compare byte-exact across install.

Physical watch-face dumbbell indicator one tap returns exact paused Walking
request, preserving79054ms/no new Start. Resume then intentional Home confirms
PAUSED/app_closed with77047ms, so deliberate exit behavior remains intact.
Return via indicator, Resume at1791423876143 yields deadline1791423953190.
Leave untouched: natural Dozing/sleep STOP is captured; runtime remains ACTIVE
with identical deadline,0 completed sets. After deadline+35489ms, hidden state
still has0 sets/no unseen advancement. KEYCODE_WAKEUP only (no launch/navigation)
returns session and completes1/1 once. Watch completed screen and actual phone
native result/resultReceipt are exact request/result/revision/peer matches.
Watch runtime/package tombstones and acknowledged cue state prove actual receipt
pruning; active notification ID3 is absent. Recorded times use their own device
clock domains; phone/watch timestamps are not assumed synchronized. These are
physical screen-sleep/wake observations, not proof of the AOD setting or rendered
ambient UI; earlier AOD-off diagnosis labels mean the observed STOP pathway,
not a verified user setting. Full ambient/TalkBack/audio/routing stays open.

Final phone IndexedDB compares all12 workout rows,11 logs,2 session events and
0 set logs exactly. All appState except playlistDraft are exact. Draft level
changed beginner→advanced and optional absent loads became explicit null between
Started and final capture; cause not established. Preserve current draft, do
not restore or claim all appState byte-exact. Legacy watch workouts/settings
remain byte-exact and queues are empty. Native phone retains exactly one test
request/result/receipt; no personal schedule/history reset. Initial final CDP
capture times out while suspended; phone wake read succeeds, normal reopen read
agrees. Ignored evidence physical-114-{preinstall,postinstall,deliberate-exit,
resumed,natural-sleep,wake-completion,final,after} and final assertion JSON retained.

Code/source review and whitespace checks pass; only eight owned source/docs files
changed, HEAD44decb0 before commit. Stage19 now73/97(75%),physical3/27,24 remaining;
close only the two complete Iteration113 Ready/Started boxes, no broader speech/
ambient/reboot/battery box. Iteration105 natural screen sleep/return/cleanup is
now physically validated in this pathway; ambient UI acceptance remains pending.
Next: physical playlist/reordered selection/Today prescription and reopened-phone
Started, offline/reboot/nonreplacement cases, audio/accessibility/routing,
60–90min measured battery and Play/mixed-version checks. Commit validated fix
and evidence documentation as PST01: Preserve workout timer through physical
screen sleep; no PWA source change, no new claim for prior JVM/phone APK results.

Final checkpoint review: verified taskTCP50429 bridge removed without touching
other mappings. Final Git recheck stillHEAD44decb0 with exactly eight owned
files; native tests/build evidence corresponds to reviewed source. Final physical
inventory reports both current installed hashes match local builds. Whitespace,
checklist-count and exact paired cleanup/scoped preservation assertions pass.

## Iteration 115 — 2026-10-08 — Physical playlist delivery in progress

Status: **Current physical Library playlist exact delivery/Ready/cancellation validated.**

User requests continuation after committed timer fix1c2c2a1; initial worktree
clean. Read plan/latest progress/React agreements. Phone remains unlocked;
watch ADB initially absent/mDNS unavailable. Reconnect previously verified
192.168.6.11:33451 succeeds without settings or pairing mutation; verify exact
physical identity before use. Capture fresh phone baseline/current playlist;
preserve today's schedule, existing results and user draft. Next: normal current
Library playlist → Ready exact order/prescriptions, Cancel owned offer; then
normal reordered selection and Today row/reopened-phone Started checks.

Iteration115 closure: physical exact Watch7 RFAY11LZ4SR identity, both installed
hashes and no unrelated runtime/Ready offer verified. Current five-item user
playlist Run→Dumbbell Curls→Bird Dog→Assisted chin-ups→Barbell Curls sent through
normal confirmation/native hit-tested Send. Request2ba41d8b-c569-41f3-96ea-f19a1e5ada14
phone/watch equality and every ID/name/sets/target/rest/load match current draft;
phone Ready and watch five-exercise Start entry pass. Previous completed UI
initially remains visible; normal Back reveals new Ready entry, captured apart.
Normal hit-tested Cancel receives actual cancelled acknowledgement, removes
owned package and creates no runtime/result. Baseline/delivery/cancellation
manifests retained under physical-115*. Relevant exact-package/cancel assertions
pass. Close physical current-playlist item only: physical4/27,Stage19 74/97.
Next: explicit reordered selection, then Today and reopened-phone Started.

## Iteration 116 — 2026-10-08 — Physical reordered Library selection

Status: **Corrected dialog reorder/delivery/cancel and exact baseline restoration validated.**

Preserve user's current five-item playlist; select Walking and Bird Dog through
Library checkboxes, open selected Quick Start and move Bird Dog above Walking
through sheet's normal Move control. Compare full ordered phone/watch request
and unchanged targets, then cancel only owned Ready offer. Next: selected entry
and reorder UI, actual delivery/Ready/cancel evidence.

Iteration116 retained first-attempt failure: unscoped Move Bird Dog up label
matches saved playlist control before modal control. This moves user's draft
Bird Dog above Dumbbell Curls; modal selection remains Walking→Bird Dog, so do
not close reordered-selection acceptance. Native Ready2-exercise offer is owned
request21d82c51-83c5-4641-8380-5fd2b03697ad; Cancel it normally. Restore draft through
its normal Move Bird Dog down control; complete phone data equality to current
iteration baseline passes. No workout started/history reset. Correct automation
selector to role=dialog and repeat while preserving this failed evidence.
First stage manifest captures watch package before delivery and phone ack after
it; it is not an atomic snapshot, so retain as preliminary and collect settled
matching package after actual Ready rather than diagnosing a store mismatch.

Iteration116 closure: corrected role=dialog selector visually reorders Bird Dog
above Walking before native Send. Actual request0c7f7861-b8ae-4cdd-9393-8728f73d467d
phone/watch package equality, source library_selection, ordered targets
Bird Dog4×6–10/rest90 and Walking1×20–30min/rest60, actual Ready acknowledgement
pass. Full phone data equals current baseline including draft. Cancel normal
owned offer; actual cancelled acknowledgement passes, no workout started.
Retain successful physical-116-reordered-ready separately from first failed
attempt. Its watch screenshot is asleep and not rendered-UI evidence; exact
native package/Ready ack and visible phone reorder establish delivery/order.
Close reordered-selection item only: physical5/27,Stage19 75/97,22 remaining.
Next: Today exact row prescription and separate short reopened-phone Started.

## Iteration 117 — 2026-10-08 — Physical Today row prescription

Status: **Physical Today row/date/prescription Ready and owned cancellation validated.**

Open Today Run row's normal Quick Start (2sets/10min/120s/5kg), compare full
sourceDate/sourceWorkoutRowId/target/sets/rest/load binding with unchanged
persisted row and actual watch package, then cancel only owned test offer.
Next: visible Today row/sheet, actual Ready and exact full package assertions.

Iteration117 closure: actual Today Run row11 binds sourceDate2026-10-08 and
sourceWorkoutRowId11. Phone/watch request6a4b63d2-64c9-4af7-877d-a67e56df2252 matches
exact source exercise ID and2sets/10min/120s/5kg; actual Ready acknowledgement
and watch Run Start screen pass. Normal Cancel receives actual cancelled;
no personal Run is started or schedule changed. Retain row-assertion plus
physical-117-today-ready manifest/screens. Close Today prescription item only:
physical6/27,Stage19 76/97,21 remaining. Next: short separate Walking test with
phone backgrounded before watch Start, then reopen and verify exact Started.

## Iteration 118 — 2026-10-08 — Physical reopened-phone Started

Status: **Physical background Started/reopened UI and short completion/receipt cleanup validated.**

Create explicit separate one-set/45sec/0s-rest Library Walking test, leaving
saved playlist/Today rows unchanged. Background phone via normal Home before
watch Start; require native Started for the exact request while phone activity
is stopped, reopen normally and verify visible UI reconciliation. Finish this
owned short test and verify actual result/receipt cleanup, preserving records.
Next: normal short confirmation/send, phone Home → watch Start → actual native
Started → phone reopen/UI proof, completion/cleanup and final audit.

Iteration118 closure: exact one-set45sec/0s-rest Walking request
7ed6df75-c1bc-485c-94a4-8891f72f2128 reaches Ready. Initial screenshot is asleep/
partial and wake-only UI dump is watch face after idle; no Start inference.
Normal watch app reopen plus fresh accessibility Start bounds verified, TTL
146263ms remains. Phone Home occurs before watch native Start; activity archive
shows Launcher foreground. Actual native phone Started is captured while
backgrounded and watch runtime ACTIVE. Normal phone reopen visibly renders
Workout started on watch for exact request, screenshot/DOM retained. Short
session completes1/1 with one actual result/receipt, runtime/package pruning,
terminal screen and no unfinished test. Close reopened-phone Started item only:
physical7/27,Stage19 77/97(79%),20 remaining. Evidence physical-118* retained.
Next: final owned-data/checklist/build inventory audit and authorized commit.

## Iteration 119 — 2026-10-08 — Physical acceptance checkpoint audit

Status: **Five physical acceptance iterations reviewed and validated for commit.**

Review Iterations115–118 evidence, retaining mis-scoped selection attempt and
non-atomic/asleep captures separately from accepted evidence. Full phone
workouts/logs/events/setLogs/appState equals current iteration baseline, including
user's Advanced five-item playlist. Prior native completed request is exact;
five added owned requests are four normally cancelled offers (including failed
selection attempt) and one completed short test. No personal schedule/history
reset. Watch legacy workouts/settings byte-exact; runtime/package pruned by
actual matching receipt. No new APK/source change this batch; inherited native
checks are historical and installed matching artifacts are verified separately.
Next: final installed inventory, notification/cue/queue cleanup and bridge/XML
cleanup, source/docs review, whitespace and Git recheck, then commit docs.

Iteration119 closure: final inventory both installed APK hashes match accepted
builds; no code/APK mutation during batch. Native indicatorID3 absent, both
legacy queues empty, cue preferences exact and owned completed ledger pruned.
Full phone baseline equality (12rows/11logs/2sessionEvents/0setLogs/allappState,
including Advanced playlist), prior native result exact,4owned cancelled offers
and1owned completed short test pass. Final watch legacy/settings byte-exact.
Remove only byte-compared taskXML files and verified task debug bridge; other
mappings/settings untouched. Exact per-case assertions, checklist7closed/20open,
source/docs review and whitespace pass. No production source changed, so no
new native/PWA build/tests are claimed; historical validated artifacts remain.
Git recheck before commit shows only five owned docs files and HEAD1c2c2a1.
Commit physical delivery/reopen evidence checkpoint; physical7/27 and Stage19
77/97(79%). Next: disconnected wording, nonreplacement, duplicate/expiry/pending
restart and unsynced reboot, then physical cue/audio/accessibility/ambient,
60–90min measured battery and Play/mixed-version acceptance. Broader acceptance
remains open; failed first selection attempt is retained and not counted.

## Iteration 120 — 2026-10-08 — Physical disconnected send behavior

Status: **Physical execution deferred: user removed watch connectivity to conserve battery; no acceptance closed.**

User continues after committed physical entry checkpoint7f6a54c; worktree initially
clean. Read plan/latest progress/React instructions. Both Watch7 IP and mDNS ADB
transports refer to same physical device; use explicit verified serial throughout.
Capture current phone/watch private baseline and exact original watch Bluetooth/
WiFi settings. Temporarily disable watch Bluetooth only (WiFi debug retained),
open normal Library confirmation and require unavailable actionable wording,
disabled Send and unchanged request/runtime/package stores. Restore original
Bluetooth in finally, verify readiness returns. Next: disconnected observation,
radio restoration/preservation, then duplicate send/active nonreplacement.

Iteration120 retained setup failure: explicit watch IP transport becomes offline
before remote radio command; radio-restore.log reports adb:device offline.
Disconnected UI still shows enabled Send, so no acceptance inferred. Device
setting read/restore over same stale transport fails; reconnect freshly advertised
exact Watch7 endpoint and verify original Bluetooth before retry. No Send action
was performed; retain this probe separately from a successful radio test.

Iteration120 current blocker: fresh Watch7 connect attempt cannot obtain a live
transport; both exact serials are unavailable. Ask user to wake watch/retain
wireless debugging, because debug absence is not Data Layer disconnection.
Phone native request file remains byte-exact after failed probe; no Send/no new
request. Preserve current native Wearable readiness separately and require
verified live watch/original radio read before any retry or passing claim.
Remaining physical cases are not executed while device inspection is blocked.

Iteration120 handoff — user clarifies they deliberately removed watch
connectivity because battery is low and no charger is available. This supersedes
the pending wake/ready request and debug-reconnect diagnosis. Stop physical
work and all watch reconnection/radio attempts. Remote radio probe log reports
adb:device offline before it could establish a shell; no successful radio
mutation or test Send is recorded. Phone native requests remain byte-exact,
current phone Data Layer peer observation is partial readiness, not a passed
disconnected UI check. Remove only verified phone-side task debug bridge;
watch is not contacted for cleanup. Retain failed/setup evidence and current
baseline. Stage19 remains77/97(79%),physical7/27,20 open. Iteration120 remains
physically unvalidated; five-iteration batch120–124 is not completed or counted.
Next when charged/reconnected: verify actual watch battery/connectivity/original
radio settings and no active personal session, then retry disconnected case
before duplicate/nonreplacement/expiry/restart/reboot checks. Docs/whitespace
and Git review can pass independently; no new app source/APK change made.


## Iteration 121 — 2026-10-08 — Independent phone/PWA feature scope

Status: **Scope review complete.** Starting checkpoint: `5b7e759`, clean tree.
Read plan, latest progress and React instructions. User authorizes continuing
phone/PWA development while Watch7 remains disconnected for low battery.
Optional feature choice offered; proceed with recommended plate calculator
without a reply. This promotes the previously deferred calculator independently
of Stage19; no watch contact or physical acceptance claim. Reviewed boundaries:
Library owns UI, pure calculations in lib, no durable schema or workout writes.
Next: implement and verify equal-side allocations.

## Iteration 122 — 2026-10-08 — Plate allocation calculation

Status: **Calculation and regression checks complete.** Added integer-hundredth
allocation: closest reachable weight at/below target, then minimum plate count
per side. Supports custom positive sizes, duplicate normalization and fractional
weights; bounded inputs prevent excessive work. Assumes unlimited pairs.
Five Node test groups pass: kg/lb, noncanonical exact solutions/minimum count,
non-overload/fractional targets, validation limits, duplicate/order stability.
Initial test incorrectly rejected an equally minimal allocation; changed fixture
to distinguish an exact solution from greedy failure. No production algorithm
change needed. Next: Library component and phone viewport validation.

## Iteration 123 — 2026-10-08 — Library calculator UI

Status: **UI implementation and static checks complete.** Added collapsed
Library calculator with target/bar/plate-size drafts, kg/lb presets, explicit
unit-reset wording, equal-side result, total and shortfall/error messages.
Component-local drafts reset on screen remount; no saved preference/inventory,
no persistence, import/export or native contract changes. App.tsx unchanged.
`npx tsc --noEmit` and `npm run build` pass. Next: browser/offline verification.

## Iteration 124 — 2026-10-08 — Mobile and offline calculator checks

Status: **Browser checks complete.** Playwright uses existing real Library/
AppShell harness `tests/quick-start-footer.html`, without personal device data.
390×844 screenshot reviewed; 320×740 document width equals viewport (320).
Default60kg gives20kg each side; custom20/5 sizes at61kg give60kg total and1kg
shortfall. Below-bar validation and lb preset135/45/45 each-side work. Browser
offline136lb calculates135lb/1lb shortfall; blank target rejects instead of zero.
First offline assertion hit two unnamed statuses (shell connectivity and result);
added accessible result name Plate calculation and reran successfully.
Only harness favicon404 console error, no application exception observed.
Screenshot: `output/playwright/plate121-mobile.png` (ignored evidence).
TypeScript/build rerun after accessible-name change pass. Next: Android package
build and final source/docs/Git review. Watch remains untouched.

## Iteration 125 — 2026-10-08 — Phone packaging and commit review

Status: **Five phone/PWA iterations complete; validated for commit.** Copied updated production assets with
`npx cap copy android`. Initial Gradle target `:phone:assembleDebug` does not exist;
repository phone module is `:app`, corrected build pending. Review source,
roadmap and whitespace; recheck Git before authorized commit. Stage19 stays
77/97, physical7/27,20 open. No physical installation or device acceptance is
claimed. Next: finish packaging check, commit if clean, then choose next phone
feature or resume physical checks once watch is charged.

Iteration125 closure: corrected `./gradlew :app:assembleDebug --offline` passes
(110 tasks); APK bundled JS/CSS byte-match production assets. Five domain test
groups, TypeScript, production build, Capacitor copy, mobile/offline browser
checks and whitespace pass. Source review confirms pure bounded allocation,
local form state, no App composition/domain or durable/native schema changes.
Rechecked Git: seven owned source/test/docs paths only, HEAD5b7e759. Calculator
code/browser/package complete; physical phone install and actual device checks
pending. Commit authorized checkpoint; physical7/27 and Stage19 77/97 unchanged.
Next: independent RPE/RIR or phone usability work, or resume physical protocol
when watch is charged. No reliable overall finish ETA follows checklist ratios.


## Iteration 126 — 2026-10-08 — Phone set-effort scope and compatibility

Status: **Implementation in progress.** Starting checkpoint `c185fe8`, clean tree.
Read plan/latest progress/React instructions. Continue user-authorized phone/PWA
work with optional RPE/RIR from the roadmap. Scope: phone set input, recovery,
atomic completion, History display and backup/restore. RPE1–10 in0.5 steps;
RIR0–10 whole numbers. Independent fields, no inferred conversion, required
rating or watch/HealthConnect contract changes. Additive optional fields retain
legacy unrated records. Watch remains disconnected and uncontacted. Next: verify
validation, durable edits, skipped/duplicate completions and backup compatibility.


Iteration126 closure: optional durable types live in types.ts; pure validation
in set-effort.ts, local UI in player/history components, existing hook retains
atomic cursor/history boundary. Legacy input/log/backup records remain accepted;
no DB version or native schema change required. Static review and TypeScript pass.
Next: validate UI and exact per-set persistence.

## Iteration 127 — 2026-10-08 — Optional player and History effort UI

Status: **Code and mobile browser checks complete.** Added collapsed optional
RPE/RIR inputs, visible invalid-rating alert, and range-filtered History section
for latest20 rated sets. Zero reserve renders explicitly; next set starts blank.
Dedicated two-column effort layout avoids inherited three-column load styling.
Real-component mobile entry8.5/0 persists exact numeric values and displays
History rating. Screenshots reviewed390×844;320×740 has no horizontal overflow.
Initial stale-ref click after harness reset failed without mutating a record;
fresh snapshot used afterward. User requested headless tests: closed headed
browser, reran checks headlessly; all further browser tests use headless mode.
Next: backup/recovery and invalid completion evidence.

## Iteration 128 — 2026-10-08 — Durable effort and backup regression

Status: **22 disposable-database checks pass headlessly.** New harness
`pwa/tests/set-effort.html` exercises actual hook, pure transition, DB transactions,
backup parse/restore, player and History components. Checks valid half-step/zero,
invalid bounds/precision, exact pause→restore→resume drafts, active backup,
duplicate completion, next-set blank defaults, invalid complete cursor/history
preservation and message, invalid draft backup, blank completion, exact backup
round trip, invalid restore with unchanged history, legacy backup compatibility,
and skipped-set absence. Invalid stored effort is rejected before replacement;
uncommitted string drafts may remain invalid until corrected. No personal DB
or watch accessed. Next: existing session/integrity regression suite.

## Iteration 129 — 2026-10-08 — Existing phone workflow regression

Status: **68 existing checks pass headlessly.** Session-integrity27,
workout-session6 and data-integrity35 pass in isolated browser databases.
Together with22 new effort checks:90 browser assertions pass. UI smoke after
layout/wording refinement repeats mobile entry, actual stored values, History
output and320px width. Harness favicon404 is unrelated; no app exception observed.
Evidence screenshots in ignored output/playwright/effort127-*.png. No changes to
watch transfer, HealthConnect payloads, strength calculations or App.tsx.
Next: production assets, phone packaging and final review.

## Iteration 130 — 2026-10-08 — Phone packaging and reviewed checkpoint

Status: **Five development iterations complete; validated for commit.**
TypeScript, production build, Capacitor copy and whitespace pass. Final cleanup
briefly removed WeightUnit import still used by select cast; compiler caught it,
restored import and TypeScript rerun passes. Production build unaffected by type
import; phone`:app:assembleDebug --offline` succeeds (110 tasks), bundled JS/CSS
byte-match production assets. No physical install/acceptance claimed.
Source review confirms ratings commit with existing set cursor/history transaction;
invalid completion cannot advance, old backups still restore, History uses current
range and caps display20, App composition unchanged. Recheck Git and stage only
owned files for authorized commit. Stage19 remains77/97,physical7/27,20 open;
watch remains disconnected/uncontacted. Next: choose independent phone planning
work (date-specific scheduling or supersets), or resume device acceptance when
watch charged. Headless preference retained for future tests. No overall ETA.


## Iteration 131 — 2026-10-08 — Recurring weekly move scope

Status: **Implementation in progress.** Start9808505, clean tree. Read plan/latest
progress/React instructions. Continue independent phone/PWA planning: move a
whole recurring day/time session, preserving row IDs/prescriptions/history.
Date-specific exceptions/deletion/supersets remain separate. Quest sessions use
Quests; reject occupied target slots and stale groups. Durable phone session and
observed unfinished watch snapshot block moves. No watch contact. Implement
transactional DB move, named hook, focused form, optional Today/WeeklyPlan wiring;
headless regression/mobile checks next. Stage19 stays77/97,physical7/27,20open.


Iteration131 closure: recurring move scope implemented independently from
Stage19. Row IDs/load/prescriptions retained; no schema/native payload change.
TypeScript/build and source review pass. Next: atomic guard/regression evidence.

## Iteration 132 — 2026-10-08 — Atomic weekly session moves

Status: **18 isolated-database checks pass headlessly.** New schedule-editing
module validates whole source groups, weekday/time, quest protection and occupied
slots; reads schedule and durable phone cursor in one transaction, writes only
selected rows. Stale source cannot overwrite a newer group; concurrent moves
using same source admit one winner. Active phone cursor rejects without mutation.
IDs/order/prescriptions/unrelated rows/history/preferences remain unchanged apart
from selected day/time. Backup round trip preserves moved schedule. Named hook
blocks observed unfinished workout, suppresses concurrent local submissions and
refreshes schedule after commit. Native update failure keeps committed local move
and displays a distinct failure message; bridge now returns boolean success,
existing callers retain historical caught/logged behavior. No actual watch contact.
Next: mobile form/cancel/collision/offline checks.

## Iteration 133 — 2026-10-08 — Weekly plan edit controls

Status: **Headless mobile UI checks complete.** Focused editor offers weekday/time,
explicit every-week scope, Save and Cancel; no-op Save disabled. Quest-owned
blocks display Quests guidance. Today optionally wires editor; App only composes
hook/callback/guard props. Collision error and Cancel preserve exact schedule.
Normal390×844 UI moves two test rows toFriday08:15 while offline, retains third
session and selects destination day; screenshot reviewed.320×740 width320/content320.
Evidence output/playwright/move132-{form,saved}.png is ignored. Native/paired
acceptance not inferred; browser has no real native watch bridge.
Next: existing planning/import/library/session/sync regression suite.

## Iteration 134 — 2026-10-08 — Planning and sync regressions

Status: **147 headless browser assertions pass.** New move18; existing Today12,
import15,Library18,session-integrity27,data-integrity35,watch-sync22. Tests use
isolated databases/mocked native bridge; preserve user headless preference.
Expected mocked native failure is logged; harness favicon404 is unrelated.
TypeScript, production build and whitespace pass. Review confirms daily overview
and notification hooks receive updated workouts; history is never rewritten.
Date-specific exceptions/deletion and hardware validation remain separate.
Next: phone package/asset identity and final Git review.

## Iteration 135 — 2026-10-08 — Reviewed phone planning checkpoint

Status: **Five phone/PWA iterations complete; validated for commit.** Capacitor
copy and `:app:assembleDebug --offline` pass; phone APK JS/CSS exactly match
production artifacts. No physical installation claimed. Composition review:
App connects named editing hook, Today/WeeklyPlan own JSX, editor owns string
form drafts, lib owns validation/atomic writes, no DB version/native schema change.
Git recheck shows only owned source/test/docs paths, HEAD9808505. Stage19 remains
77/97,physical7/27,20open; watch disconnected/uncontacted. Authorised commit next.
Remaining: date-specific scheduling/deletion, supersets and physical phone/watch
acceptance. Latest committed effort checkpoint9808505 supersedes prior uncommitted
labels. Overall ETA remains unknown; checklist ratios are not elapsed-time estimates.


## Iteration 136 — 2026-10-08 — Recurring removal scope and safeguards

Status: **Implementation in progress.** Startingc6e9f9b, clean tree. Read current
plan/latest progress/React instructions. Extend weekly editor with confirmed
whole-session removal. Preserve history/playlist/unrelated sessions, retain
quest ownership and active/paused/resting cursor guards, reject stale/partial
groups. User is authorizing feature code; no personal schedule is removed during
development. Native schedule cache refresh shares existing editing hook. Watch
untouched; headless tests use disposable DB. Next: durable deletion and regressions.


Iteration136 closure: recurring removal added to existing move workflow, with
explicit confirmation and captured source snapshot. TypeScript and review pass;
no new DB/native schema, no personal data changed. Next: durable deletion checks.

## Iteration 137 — 2026-10-08 — Removal transaction and player reconciliation

Status: **22 new isolated-DB checks pass headlessly.** Shared transaction validates
whole current group and unfinished durable phone cursor, deletes only selected
schedule rows, preserves unrelated rows/history/preferences, and rejects partial,
quest-owned, duplicate/stale and remove/move race attempts. Deleted IDs are not
reused by new additions; backup round trip retains historical references.
Review found affected terminal player could lose its rows but retain unusable
End action. Clear only intersecting completed/ended cursor in same transaction;
keep all history and unrelated terminal cursors. Then await normal session restore
in App's schedule-changed callback, so UI and DB clear together. Two added hook
integration checks verify visible finished cursor then null after removal. Native
cache failure keeps local removal and reports distinct partial-success wording.
Next: confirmation/cancel/offline/mobile review.

## Iteration 138 — 2026-10-08 — Confirmed mobile removal UI

Status: **Headless mobile workflow checks pass.** Confirmation shows exact exercise
count, time, weekday and recurring scope, preserves source snapshot until confirm,
and offers Keep session. Cancel preserves exact schedule. Offline normal UI removes
only chosen two rows, keeps unrelated row and rated history; subsequent last-session
removal shows rest-day empty preview.390×844 screenshot reviewed;320×740 content
width equals320. Evidence output/playwright/remove138-{confirm,saved}.png ignored.
Busy/blocked guards apply to move/removal; Quests owns quest sessions. Watch and
personal phone data untouched. Next: inherited planning/session/sync regressions.

## Iteration 139 — 2026-10-08 — Existing editing and phone regressions

Status: **169 headless browser assertions pass.** Removal22 plus existing move18,
Today12,import15,Library18,session-integrity27,data-integrity35,watch-sync22.
Expected fixture bridge rejection logged; harness favicon404 unrelated. Source
review keeps App composition/cross-feature restore callback, focused editor JSX,
named mutation hook, pure group validation and transactional DB edits. No watch
payload, strength analytics, history mutation or required effort field added.
TypeScript, production build, Capacitor copy and whitespace pass. Next: APK
identity/final Git review and authorised commit.

## Iteration 140 — 2026-10-08 — Phone removal checkpoint

Status: **Five development iterations complete; validated for commit.** Android
`:app:assembleDebug --offline` passes (110tasks); bundled JS/CSS byte-match current
production artifacts. Physical phone install and device acceptance pending.
Recheck Git: owned source/test/docs changes only, HEADc6e9f9b. Weekly move/removal
code/browser/package complete; date-specific exceptions and supersets remain
future work. Stage19 remains77/97,physical7/27,20open; watch disconnected/uncontacted.
Latest move checkpointc6e9f9b supersedes previous uncommitted labels. Commit next;
then independent phone work or charged-device acceptance. No reliable overall ETA.


## Iteration 141 — 2026-10-08 — Remembered calculator equipment scope

Status: **Implementation in progress.** Startinge0619c8, clean tree. Read current
plan/latest progress/React instructions. Add explicit saved bar/plate-size presets
per kg/lb to Library calculator and full JSON backup. Target remains temporary;
unit switch loads saved equipment and example target. Explicit Save and default
reset avoid automatic draft writes. Additive appState record, no DB version or
native/watch schema change. Hook owns load/save; component JSX and pure validation
remain separate. No watch contact, tests headless/disposable DB. Next: reload,
per-unit, invalid setup, storage failure and backup compatibility checks.


Iteration141 closure: additive equipment record/types, explicit persistence hook,
pure setup normalization/backup guard and focused calculator controls implemented.
TypeScript/build/source boundaries pass; App unchanged. Next: persistence evidence.

## Iteration 142 — 2026-10-08 — Equipment validation and compatibility

Status: **Setup validation and calculation checks pass.** Same bounded calculator
weight/precision rules validate bar and positive sizes independently of target.
Saved sizes deduplicate/sort; kg/lb presets remain independent. Backup validator
checks both unit presets and rejects malformed records before replacement; legacy
backups without equipment remain valid. Five calculation test groups pass; no
allocation algorithm change. Defaults/invalid setup checks also pass in new
headless harness. Next: durable hook, error paths and backup round trip.

## Iteration 143 — 2026-10-08 — Durable equipment workflow

Status: **27 headless disposable-DB checks pass.** Save canonicalizes equipment,
keeps other unit preset and current target, remembers saved unit, restores on
hook remount with example temporary target. Explicit default reset edits draft
only until Save. Invalid saves and injected write failures preserve previous
record; read failures/malformed saved record show defaults with error without
silently overwriting evidence. JSON backup round trip preserves both presets;
invalid restore keeps prior data; legacy restore retains unrelated preferences.
Save works even when target blank. All form edits guarded while loading/saving;
editing clears stale save message. No automatic keystroke writes. Next: mobile/
offline UI and existing regression suite.

## Iteration 144 — 2026-10-08 — Mobile and inherited backup/Library regression

Status: **107 headless browser checks and mobile smoke pass.** Equipment27,
data-integrity35,backup-export11,data-hydration16,Library18. Real Library/AppShell
integration renders calculator controls.390×844 screenshot reviewed;320×740
content width320. UI saves kg40bar/10,5plates offline, retains80target on Save,
loads separate lb45 default then restores savedkg40; target remains absent from
stored record. Unit switch explicitly resets target to an example. Evidence:
output/playwright/equipment143-mobile.png (ignored). No app exception observed;
harness favicon404 unrelated. Final source review adds missing loading/busy guard
to plate-size field and clears saved status on draft changes;27 checks rerun pass.
Next: updated phone package and Git review.

## Iteration 145 — 2026-10-08 — Equipment preference checkpoint

Status: **Five development iterations complete; validated for commit.** TypeScript,
production build, Capacitor copy and whitespace pass; final phone`:app:assembleDebug
--offline` build succeeds, bundled JS/CSS byte-match final production assets.
No DB version/native/watch schema change, no App domain code added. Full backup
continues to include appState equipment record; temporary target and unsaved
form edits stay out of saved preset. Git recheck owned source/test/docs only,
HEADe0619c8. Physical install pending; Stage19 stays77/97,physical7/27,20open,
watch disconnected/uncontacted. Latest removal checkpointe0619c8 supersedes old
uncommitted labels. Commit reviewed checkpoint next; date-specific planning and
supersets remain separate future candidates, actual device acceptance deferred.


## Iteration 146 — 2026-10-08 — Schedule edit recovery audit

Status: **Implementation in progress.** Start560aa0d, clean tree. Read plan/latest
progress/React instructions. Review finds committed weekly edits can be reported
as failed if UI/player refresh callback throws; native-cache failure otherwise
requires app reopen. Separate durable commit from post-commit publish, keep true
edit result after saved data, expose retry using current DB rows, never replay
move/removal. Retry warning remains through form cancellation. No new durable
schema or actual watch contact; headless tests next for callback/native/read
failure, current-data retry and overlapping actions. Physical20checks stay open.


Iteration146 closure: separate saved mutation from view/cache publishing in named
hook; failure now preserves successful edit result and offers retry. Static checks
and source review pass. Next: failure/retry regression evidence.

## Iteration 147 — 2026-10-08 — Current-data schedule retry regression

Status: **19 new headless isolated-DB checks pass.** Injected view failure after
move returns committed success, preserves rows, makes no premature cache call,
and retains warning through clear/cancel. Repeated publish failure keeps retry;
newer edited/restored DB rows supersede failed snapshot when retry succeeds.
Success clears pending retry and redundant retry is suppressed. Removal cache
failure keeps committed removal; DB read failure remains actionable. Deferred
retry suppresses overlapping retry/edit. Retry works while unfinished workout
blocks new edits, without mutating cursor/schedule/history. Mocked watch Send
counter stays0: updating local phone cache never sends/starts watch workout.
Retry pending state belongs to current app hook; full restart retains existing
startup cache-refresh behavior, no durable retry queue added. Next: mobile UI.

## Iteration 148 — 2026-10-08 — Mobile recovery affordance

Status: **Headless UI smoke passes.** WeeklyPlan shows Retry schedule update only
for post-commit recovery, disables while busy; row form opening/Cancel retains
warning. Normal retry clears warning/button and shows success, exact saved rows
and history preserved.390×844 screenshot reviewed;320×740 width/content320.
Evidence output/playwright/recovery148-{warning,success}.png ignored. Optional
editor props preserve old harnesses/screens; App wiring unchanged. No watch
contact/personal DB mutation. Next: existing edit/removal/session/bridge checks.

## Iteration 149 — 2026-10-08 — Regression and composition review

Status: **120 headless browser assertions pass.** Recovery19 plus existing
move18,removal22,session-integrity27,Today12,watch-sync22. Expected injected
native failures log separately; harness favicon404 unrelated. Hook handles DB
mutation, async publishing/retry and concurrency; WeeklyPlan owns button/display.
Pure transaction still protects active sessions and history; retry only reads
current schedule and publishes it. No native payload/schema/backup changes or
new background loop. TypeScript, production build, Capacitor copy and whitespace
pass. Next: phone APK identity/Git recheck and authorised commit.

## Iteration 150 — 2026-10-08 — Schedule recovery checkpoint

Status: **Five implementation/review iterations complete; validated for commit.**
Phone`:app:assembleDebug --offline` passes (110tasks), APK JS/CSS byte-match final
production artifacts. No physical installation claimed. Recheck Git only owned
source/test/docs paths, HEAD560aa0d. Save success distinguished from later callback/
cache failure; retry never replays original edit or pushes stale captured rows.
Physical acceptance remains deferred: Stage19 77/97,physical7/27,20open,watch
uncontacted. Latest equipment checkpoint560aa0d supersedes old uncommitted labels.
Commit next; independent planning features and real-device acceptance remain
separate. No reliable overall completion ETA; headless preference retained.


## Iteration 151 — 2026-10-08 — Weekly editor state audit/reproduction

Status: **Implementation in progress.** Start2f5968c, clean tree. Read plan/latest
progress/React instructions. Reproduce in headless isolated DB: open move on
currentday07:00, switch to another day with same07:00; old form survives due to
parent time-only key. Test fails at this assertion before changes, no mutation.
Also review move form using live rows after opening, unlike captured removal
confirmation: concurrent prescription/group changes could expand move scope.
Use day/time key and captured move source, guard day picker during pending edit;
retain normal DB stale validation. No native/watch schema change or contact.
Next: corrected day-switch/stale-prescription/busy-picker regression.


Iteration151 closure: retained pre-fix failure proves real cross-day form reuse;
reproduction touches only disposable DB. Next: source identity and scope fix.

## Iteration 152 — 2026-10-08 — Source-scoped planning form fix

Status: **Implementation and static review complete.** Weekly session key now
includes selected weekday/time. Move form captures row snapshot on open, uses
captured source label/count/no-op check, and submits that snapshot to existing
atomic stale-group validator. Day picker disabled during pending editing/publish;
reenables afterward. Removal snapshot and retry behavior preserved. Corrected
singular exercise accessibility count. Component-local display state only; no
App/hook/schema/native contract change. TypeScript/whitespace pass. Next: full
state regression including pending-day navigation.

## Iteration 153 — 2026-10-08 — Editor state regression and mobile smoke

Status: **15 new headless checks pass.** Cross-day same07:00 move form closes;
other day opens its own source/default; cross-day removal confirmation closes;
browsing/cancellation does not mutate schedule. Changed prescription after form
opening rejects stale move without altering new data. Reopening moves exact
current group and leaves other day unchanged. Deferred cache update makes picker
busy/disabled, clicking cannot redirect pending editor, then re-enables normally.
Normal Playwright mobile day switching confirms both forms close correctly;
390×844 screenshot reviewed;320×740 width/content320. Evidence:
output/playwright/editor153-mobile.png ignored. Pre-fix test failure retained
separately from passing evidence. Next: inherited planning/session/sync suite.

## Iteration 154 — 2026-10-08 — Planning regression and packaging

Status: **135 headless assertions pass.** New editor15 plus existingmove18,
removal22,recovery19,session-integrity27,Today12,watch-sync22. Expected fixture
native failures remain test-only; no watch/device contact. TypeScript, production
build, Capacitor copy and whitespace pass. Phone`:app:assembleDebug --offline`
succeeds; bundled JS/CSS match final production assets. No personal schedule
mutation or physical installation claimed. Next: source/docs/Git final review.

## Iteration 155 — 2026-10-08 — Reviewed weekly editor checkpoint

Status: **Five review/implementation iterations complete; validated for commit.**
Source review confirms per-source component identity, captured move/removal scope,
atomic stale rejection and pending-navigation guard; no durable data shape changes.
App composition unchanged, existing mutation/retry workflow reused. Recheck Git
only owned source/test/docs paths, HEAD2f5968c. Physical acceptance still deferred:
Stage19 77/97,physical7/27,20open,watch untouched. Recovery checkpoint2f5968c
supersedes historical uncommitted labels. Commit next; remaining independent
feature choices and physical installation/charged-watch acceptance remain open.
No reliable overall ETA; preserve headless test preference.


## Iteration 156 — 2026-10-08 — Inspectable set history scope

Status: **Implementation in progress.** Start4547abd, clean tree. Read plan/latest
progress/React instructions. History currently exposes only latest20 rated sets;
unrated/duration sets cannot be inspected individually and older rated sets stop
at20. Replace effort-only section with all recorded set details, exercise search,
rated filter and20-row pages. Respect existing History date range and raw actual/
planned/load/effort values; no writes/schema/native changes. Pure filtering/sorting
module, focused presentation component; App unchanged. Headless regression next.


Iteration156 closure: all-set inspection implemented separately from analytics,
read-only raw fields, bounded20-row pages and existing date-range integration.
App/data schema unchanged. TypeScript and initial domain checks pass.
Next: sorting/filter behavior and pagination/range regression.

## Iteration 157 — 2026-10-08 — Set filtering and timestamp ordering

Status: **Three domain test groups pass.** Pure filter includes unrated/duration
sets by default, optional rated-only predicate preservesRIR0, search trims/case
normalizes exercise names. Date.parse sort orders actual instants across timezone
offsets, IDs break timestamp ties; input array remains exact. Raw actual/planned
strings, load0 and absent load are preserved for display. No grouping/inference,
volume/PR calculation or history record mutation. Next: real UI/page/range checks.

## Iteration 158 — 2026-10-08 — Bounded set inspection UI

Status: **15 new headless fixture checks pass.** First/second/last page20/20/5
for45records, navigation bounds, zero load/reserve, duration actual/planned,
search+rating filtering, no-match/empty state, shrinking input and exact unchanged
records verified. Real HistoryView all-time includes older month; month excludes
it and resets page, all-time restores it. Renamed effort-only component to
SetHistory and updated actual History/harness imports. Details retain RPE/RIR
alongside unrated records; active date range remains parent-owned.
Next: normal mobile/offline UI and existing session/data checks.

## Iteration 159 — 2026-10-08 — Mobile and existing history integrity regression

Status: **99 headless browser assertions pass, plus3 domain groups.** NewHistory15,
set-effort22,session-integrity27,data-integrity35. Normal mobile searchWalking+
rated-only yields7matches; offline next/next reaches records41–46.390×844
screenshot reviewed;320×740 content width320. Evidence output/playwright/
history158-mobile.png ignored. Only harness favicon404 unrelated; no app exception.
Existing set completion/effort persistence and data integrity remain valid.
TypeScript/build/Capacitor copy/whitespace pass; phoneAPK build succeeds.
Next: final source/docs/Git and packaged asset identity review.

## Iteration 160 — 2026-10-08 — Reviewed set history checkpoint

Status: **Five development iterations complete; validated for commit.** Phone
`:app:assembleDebug --offline` passes(110tasks); APK JS/CSS byte-match production.
Source review: pure immutable filtering, local presentation state, bounded pages,
clamped page when rows shrink, reset on range/data update, long text wraps,
recorded values unchanged. No stale old-component imports remain. App and durable
schema unchanged; no DB writes introduced. Recheck Git only owned source/test/docs,
HEAD4547abd. Physical installation pending,Stage19 77/97,physical7/27,20open,
watch disconnected/uncontacted. Latest editor checkpoint4547abd supersedes old
uncommitted labels. Commit next; independent planning features and charged-device
acceptance remain separate. Headless preference retained; no overall ETA.


## Iteration 161 — 2026-10-08 — Concurrent equipment save audit/reproduction

Status: **Implementation in progress.** Start0653f71, clean tree. Read plan/latest
progress/React instructions. Two calculator hooks loaded from same settings each
save whole cached record; concurrent kg/lb saves can lose earlier unit edit.
Headless disposable-DB reproduction fails at both-presets assertion before fix.
Merge only selected unit against current durable record using existing atomic
updateRecord helper. Preserve explicit Save, invalid/failure handling, temporary
target and backup schema. No user/device data touched; watch remains disconnected.
Next: concurrent/stale/restored data merge and inherited equipment regression.


Iteration161 closure: retained pre-fix failed assertion proves actual lost preset
with two independent views. No personal DB used. Next: atomic selected-unit merge.

## Iteration 162 — 2026-10-08 — Current-record equipment merge

Status: **Code and static checks complete.** Save uses existing updateRecord
read/write transaction, validates current durable settings or uses legacy defaults,
then changes only chosen unit preset and saved unit. Other-unit record no longer
comes from stale component snapshot. Returned committed record refreshes saving
view's preset cache. Existing Save/loading/busy/error/target behavior preserved.
TypeScript and whitespace pass; no App/schema/native contract change.
Next: concurrent/stale/restored-record and rollback checks.

## Iteration 163 — 2026-10-08 — Concurrent and aborted save regression

Status: **14 new headless disposable-DB checks pass.** Independent calculator
hooks loaded from same record concurrently savekg/lb; both bars/plate lists stay
exact. Stale kg view preserves newer lb save; current restored presets merge
rather than old snapshot. Saving view sees merged other-unit preset afterward;
last explicit saved unit and temporary target retained. Invalid edits, injected
storage failure and write-stage transaction abort preserve both presets. Unrelated
preference exact; merged settings survive backup round trip and remount. Pre-fix
failure retained separately. Next: inherited equipment/backup/hydration checks.

## Iteration 164 — 2026-10-08 — Existing equipment and data regression

Status: **121 headless browser assertions pass.** Concurrency14,equipment27,
data-integrity35,backup-export11,data-hydration16,Library18. Existing equipment
mobile controls unchanged; explicit save/default reset, unit switching, invalid/
load failures, legacy backups and malformed-record recovery remain valid.
Only harness favicon404 unrelated; expected failures injected only into disposable
storage. TypeScript, production build, Capacitor copy and whitespace pass.
No watch contact or personal data mutation. Next: package identity/final review.

## Iteration 165 — 2026-10-08 — Reviewed equipment integrity checkpoint

Status: **Five audit/implementation iterations complete; validated for commit.**
Phone`:app:assembleDebug --offline` passes(110tasks); APK JS/CSS byte-match current
production assets. Source review confirms atomic selected-unit merge using current
record, no cached other-unit overwrite or target persistence, unchanged form/API
and backup shape. Git recheck owned hook/test/docs only, HEAD0653f71. Latest set-
history checkpoint0653f71 supersedes historical uncommitted labels. Physical
installation pending; Stage19 remains77/97,physical7/27,20open,watch untouched.
Commit next; future independent planning work and device acceptance stay separate.
