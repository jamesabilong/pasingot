# Grouped Watch Workout Implementation

Stage 22 follows the committed phone grouping (`4d93252`) and grouped quest
checkpoint (`f154e5c`). Physical execution remains deferred. Stage 19 remains
87/97 overall, physical 17/27; this stage adds no accepted device criterion.

## Phase 0 — Transfer contract and receiver protection

Code/native-build complete in Iteration 191: 24 new shared contract/receiver
regressions, 387 current shared/phone/Wear JVM tests and both APK builds pass.
Phone packaged shell assets remain identical to production. The checkpoint is
committed with this documentation at the user’s request on 2026-10-15; validation/closure is in the progress log. This completes only the
foundation, with runtime and publication still pending.

- Separate `pasingot-grouped-workout` envelope, explicit version 2, and
  `/grouped-workout-set-v2` path. Existing native schema-5 sends remain unchanged; group envelope version 2 is
  independent of that legacy native schema.
- Each item retains the original `WorkoutExercise` prescription and stable
  scheduled row ID, plus `sessionTime` and optional circuit metadata. Session
  times isolate repeated group IDs in a full-day projection. Date overrides
  resolve before transfer; per-slot order and quest identity remain attached.
- Complete 2–24-member groups, equal 1–100 rounds, 0–3600 seconds shared rest,
  at most 240 exercises/24,000 compiled steps. Non-grouped members use bounded
  1–100 sets and 0–3600 seconds rest. Missing/duplicate logging row IDs reject.
  Transfer JSON is bounded to 65,536 UTF-8 bytes and characters; a structurally
  valid large plan may still be too large to encode and must not be published.
- Pure compilation yields A1/B1/A2/B2, zero intra-round rest, shared round/block
  rest and no final rest, while retaining mixed straight blocks.
- Future watch capability requires a valid version-1 capability envelope,
  watch role, exact selected reachable node and explicit workout version 2.
  No current runtime publishes this advertisement or the new grouped path.
- The actual legacy receiver now rejects future versions or raw grouped
  execution fields before permissive decoding or download-store mutation.
  Current grouped full-day/Quick Start sends continue rejecting as before.

## Phase 1 — Durable watch executor (next)

Implement a separately versioned grouped download/session representation so
older apps mark it unsupported and cannot play it as straight sets. Keep old
flat downloads and sessions usable; preserve duplicate-date/explicit re-download
and active-session nonreplacement rules.

Snapshot the compiled sequence and durable cursor with set progress. Complete,
skip, pause, resume, restart and timed-set expiry must commit cursor, logs,
exercise progress and terminal effects together. Skipping one member removes
only its future sets; round rest must survive skipping the last member. Recover
without replaying sets or inferring completed members from a monotonic exercise
index, since grouped execution returns to earlier members each round.

Keep per-exercise planned loads/targets and scheduled identity. Existing watch
history is exercise-level; do not claim phone-style actual-load/effort set history
without adding an explicit native model/result contract. Integrate existing cues
with real round/block rest, backward index transitions, exactly-once completion,
final-five locking, ambient/timed-set behavior and resource cleanup. Per-member
set adjustment must not break equal rounds; use group settings or disable that
control for grouped entries. Show round/member position on the watch.

Validation: meaningful reducer/persistence/CAS/recovery/skip/timed/cue tests,
existing flat and Quick Start regressions, both native builds, and isolated
round-watch UI inspection. Physical installation/audio/haptics/battery remain
separate.

## Phase 2 — Capability publication and grouped delivery

Only after Phase 1 passes, advertise the implemented grouped watch capability.
Add phone foreground full-day send and watch-requested grouped downloads using
this separate path and a freshly reachable, exact-node capability decision.
Retain accurate unavailable/update-required messages, existing flat sends and
old-watch behavior. A missing/incompatible capability must publish no partial
or flattened workout. Recheck capability after reconnect; cached advertisements
alone are insufficient. Update phone UI guidance only for enabled paths.

Quick Start grouping requires its own request/package/result schema and durable
runtime changes. It remains separately scoped and guarded until those changes
are implemented and validated. This transfer contract does not extend the
existing Quick Start request schema.

## Device acceptance

Use the physical validation module when the user requests execution. Check
legacy malformed/new-version rejection and retained downloads first. After the
executor and delivery phases, verify actual paired exact round/timing/history,
process death, offline result retry, cue routing/ambient, mixed versions and
retained data. Automated contracts/builds cannot close physical acceptance.
