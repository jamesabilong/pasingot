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
