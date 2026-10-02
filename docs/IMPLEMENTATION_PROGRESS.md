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
