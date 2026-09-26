# Watch Quick Start Plan

Status: **Phase 0 in progress — foundations implemented; end-to-end flow not started**
Active stage: **Stage 19 — Phone-selected Watch Quick Start**
Planning checkpoint: `23dd15c PST01: Finalize code`
Latest committed checkpoint: `bffe396 PST01: Retain quick start results until phone receipt`
Last updated: **2026-09-26**

## Goal

Let a user choose one exercise, the current Library playlist, or an ordered
selection of exercises on the Android phone; confirm the prescriptions; send
the resulting workout to the paired Wear OS app; and start it with one
deliberate tap on the watch.

The phone action is **Send to watch**, not an invisible remote start. The phone
shows **Ready on watch** only after the watch has validated and stored the
request. The watch then presents a large **Start** action. This prevents an
accidental timer/session and remains understandable when the watch is not worn,
busy, or disconnected.

## Scope

### In scope

- Four phone entry paths that converge on one workflow:
  - one Library exercise;
  - one Today row;
  - the current ordered Library playlist;
  - an ordered multi-selection from the Library, up to the existing 24-item
    playlist limit.
- A compact phone sheet showing the selected workout and editable per-exercise
  sets, reps or duration, rest, optional load, and load unit.
- A separate phone-to-watch request and watch-to-phone acknowledgement contract.
- A one-or-many-exercise transient session package on the watch, preserving
  selection order without adding it to the permanent/date-keyed download store.
- A watch-ready surface with Start and Dismiss actions.
- A detailed per-exercise success transition and final workout-success screen.
- Optional Wear OS text-to-speech (TTS) cues for the exercise briefing,
  five-second start warning, prescribed rest duration, next exercise, and final
  completion. Visual and haptic cues remain complete without speech.
- Active-session conflict handling, duplicate safety, expiry, retry, and clear
  connection/error states.
- Browser capability gating: browser/PWA users see that this action requires the
  installed Android app; no browser-to-watch transport is implied.
- Focused browser/JVM tests, clean builds, emulator review, and paired-device
  acceptance kept as separate evidence.

## Storage ownership and retention

The Android phone is the source of truth and owns the permanent workout
library, playlists, schedule, workout history, and received completion records.
Quick Start must not turn the watch into a second workout library.

The watch keeps only the minimum crash-safe state needed to finish and sync:

- at most one pending `WatchSessionPackage`, keyed by `requestId` rather than
  date;
- at most one active session derived from that package;
- persisted progress, rest/countdown state, cue ledger, and immutable final
  summary for that session;
- completed logs/results that have not yet been acknowledged by the phone.

The existing three-entry, date-keyed downloaded-workout store remains for the
existing scheduled download workflow and is not reused for Quick Start. A
pending package may be dismissed or expire without creating history. A
completed package may be pruned only after the phone acknowledges its results;
unsynced results survive app/process death, reboot, and temporary disconnection.
Retention is bounded by one pending/active package plus the existing durable
unsynced queue, with age-based cleanup only for acknowledged terminal data.

### Final-result retention decision — 2026-09-26

- A completed or ended Quick Start result is saved as an immutable validated summary
  with stable request/result IDs, the final outcome revision, and the expected
  phone node. This separate durable result record is the source for future
  resend; success from Wear message transport is never a phone import receipt.
- The phone must send a receipt after it has persisted the exact result. The
  watch compares request ID, result ID, outcome revision, and the observed Data
  Layer sender node with its saved record. Only the matching receipt is saved.
  **Synced to phone** depends on that saved receipt, not transport acceptance.
- Cleanup is replayable in this order: persist phone receipt, remove the matching
  outcome snapshot with a durable cleared-session tombstone, compact the bulky
  result to its receipt, then release the `STARTING` package to an acknowledged
  tombstone. This supersedes the pre-audit package-first ordering, which allowed
  a new offer to strand cleanup. A crash or write failure leaves the result
  or receipt available for retry. A conflicting outcome revision blocks its
  removal. Both completed and ended-before-completion results use this policy.
- Ended results preserve unfinished exercises as pending, including sessions
  with zero completed sets. Exactly one completion or ended summary is present.
  The outcome store freezes the exact final metadata atomically before result
  enqueue, rejects later set/skip transitions, and can recover the original
  result after a failed enqueue. Outcome schema 2 reads legacy schema 1 and
  upgrades on write; unreadable records are retained for recovery, not deleted.
  Cleanup can resume entirely from the saved receipt while disconnected.
- The watch keeps up to 64 recent acknowledged request tombstones through each
  request's local expiry plus two clock-skew allowances. New offers are refused
  rather than evicting unexpired replay protection when that bound is full.
  Expired tombstones are pruned during the next acceptance. This policy is
  distinct from the legacy history queue, which drops entries after message
  transport acceptance and cannot prove phone import.
- On navigation away from the foreground-only pre-start countdown, the offer
  remains `READY` until its local expiry. The start gate changes it to
  `STARTING` only at countdown zero. After process death/reboot, `READY` is
  shown again if unexpired; `STARTING` requires explicit foreground recovery
  of that same session and never replays the countdown or starts unseen.
  Activity navigation, sender/receiver wiring, and device recovery checks are
  still pending.

Today-row requests retain their source row identity so the phone can reconcile
schedule/quest progress. Ad-hoc exercise and playlist requests receive a new
session identity and appear in phone History under their transmitted title and
source after result import.

### Out of scope for the first release

- Starting a workout silently without confirmation on the watch.
- Arbitrary remote control of an active watch session from the phone.
- Creating a second saved-playlist database. Stage 19 sends the current Library
  playlist or transient ordered selection; reusable custom quests and the weekly
  schedule remain the existing persistence mechanisms.
- Browser/PWA-to-watch delivery, accounts, or a cloud relay.
- Multiple paired watches. The existing single-watch assumption remains.
- Replacing or interrupting an active watch workout.
- Background voice execution after the user leaves the active session screen.
  The first release does not add a foreground workout service; countdowns catch
  up safely on return without speaking stale cues.
- Long form-coaching scripts. The initial spoken brief is the exercise name and
  prescription. Reviewed technique instructions would require a separate
  catalog field and content-review scope.

## User experience

### Phone flow

1. The user chooses one of these actions:
   - **Quick start** on an exercise;
   - **Send playlist to watch** in the Library playlist panel;
   - **Select exercises**, then **Send selected to watch**.
2. A bottom sheet opens with an optional workout title, estimated duration, and
   an ordered list of exercises. Each row exposes sets, reps/min/sec, rest
   seconds, planned load, and unit. The current playlist order is preserved;
   the selected-list flow allows reordering before send.
3. The primary action is **Send to watch**.
4. The phone progresses through explicit states:
   - **Connecting to watch…**
   - **Sending…**
   - **Waiting for watch…**
   - **Ready on watch**
   - **Started on watch**
5. Failure states provide an action:
   - **Watch not connected — reconnect and retry**
   - **Pasingot for Wear OS needs an update**
   - **A workout is already active — continue on your watch**
   - **Request expired — send again**
   - **Watch rejected this exercise — review the prescription**

The Library card's existing **Add** action stays primary for workout planning.
**Quick start** is a secondary action so the common playlist workflow is not
made ambiguous. The playlist panel receives **Send playlist to watch** only
when it contains at least one valid item. Multi-select is an explicit mode so
normal browsing does not gain permanent selection controls. Today's queue may
use **Send to watch** directly because its prescription is already known; the
existing **Send today to watch** remains the full scheduled-day action.

### Watch flow

1. The watch validates and stores the incoming request.
2. If no session is active, it shows a ready card or notification:
   - workout title or **Quick workout**;
   - exercise count and estimated duration;
   - the first exercise and prescription, with the remaining ordered list
     available by scrolling;
   - large **Start** action;
   - **Dismiss** secondary action.
3. Start opens the existing session UI with the complete ordered selection.
4. Dismiss removes the pending offer without creating workout history.
5. If a session is active, the existing session is never replaced. The watch
   rejects the request with an acknowledgement that lets the phone show
   **A workout is already active**.

## Watch session, success, and TTS design

The success and voice design applies to all watch workouts: Quick Start,
**Send today to watch**, and watch-requested downloads. It is not coupled to the
new transfer path.

### Voice settings and fallback

- Add a one-time watch prompt: **Use voice workout cues?** with **Enable** and
  **Not now**. Voice remains off until the user opts in.
- Add watch settings for:
  - **Voice cues** master toggle;
  - **Start briefing**;
  - **Rest announcements**;
  - **Countdown cue**;
  - **Completion cue**.
- Use the system TTS engine and system-selected language/voice. Do not bundle a
  cloud speech service or upload workout data.
- Detect initialization/language/output failure. Continue with the full visual
  UI plus distinct haptics; show a non-blocking **Voice unavailable** status in
  Settings rather than interrupting the workout.
- Keep speech concise and sanitize user-entered exercise names. Cap the optional
  spoken brief so a cue cannot monopolize the session.
- TTS is never required to start, rest, complete, persist, or sync a workout.

### Start sequence

The user still taps **Start** on the watch. Starting does not immediately enter
the active set:

1. Show a full-screen five-second countdown with a green perimeter ring, the
   exercise name, and its target.
2. Speak a concise briefing once, for example:
   - **“Assisted chin-ups. Two sets of eight to ten reps.”**
   - **“Plank. One set of thirty seconds.”**
   - **“Goblet squat. Three sets of ten reps at twelve kilograms.”**
3. Speak **“Starting in five seconds.”** and display `5`, `4`, `3`, `2`, `1`.
4. At zero, use a short start haptic and speak **“Go.”** Then create/enter the
   active session.

The cue controller sequences the briefing and countdown warning so speech does
not overlap. If TTS initialization or speech exceeds its bounded timeout, the
visual countdown proceeds and the workout remains usable. Leaving the screen
during pre-start cancels back to Ready; it must never begin unseen in the
background.

### Rest sequence

When a set completes:

1. Give a short success haptic and show a compact check transition.
2. Start the existing deadline-based rest timer immediately; animation or
   speech must not add hidden seconds.
3. Speak the prescribed duration once, for example **“Rest for sixty seconds.”**
4. If the next target is a new exercise, append a concise preview, for example
   **“Up next: push-ups, twelve reps.”**
5. At five seconds remaining, speak **“Starting in five seconds.”**
6. At zero, use a start haptic and speak **“Go. Set two.”** or
   **“Go. Push-ups.”**

The `+5`, `+10`, and `+30` controls update the visual deadline only before the
final-countdown lock. They do not repeat the whole rest announcement;
optionally speak only **“Thirty seconds added”** after an accepted action.
Pause cancels pending speech/countdown cues. Resume announces the remaining
rest only when more than five seconds remain.

Short rests use reduced scripts so the announcement cannot consume or overlap
the final countdown:

- `0–5` seconds: omit the rest/next-target speech and use the countdown/Go cue;
- `6–10` seconds: use only a bounded abbreviated next-target cue when it can
  finish before the final-five threshold, otherwise omit it;
- more than `10` seconds: use the full rest-duration and next-target cue.

The visual timer always starts immediately and never waits for speech. The cue
controller owns a single utterance queue, cancels obsolete speech, and anchors
the warning to the persisted deadline rather than to TTS completion.

#### Final five-second lock

Rest extension and the spoken start cue must never race:

1. While more than five whole seconds remain, `+5`, `+10`, and `+30` are enabled.
2. When the timer first crosses to five seconds or fewer, the session atomically
   latches `finalCountdownStarted` for that rest interval **before** emitting
   **“Starting in five seconds.”**
3. Once latched, all extension buttons are disabled and visually muted. Show
   **Starting soon** in their place or provide the disabled accessibility label
   **Rest extension unavailable during final countdown**.
4. `SessionViewModel.onAddRestSeconds` enforces the same guard. UI disablement is
   not considered sufficient protection.
5. If an extension tap and threshold crossing occur together, the serialized
   state transition decides the result:
   - extension committed while the prior remaining time is greater than five:
     accept it, move the deadline, and emit one warning at the new five-second
     crossing;
   - final-countdown lock committed first: reject the extension without changing
     the deadline or speaking an “added” confirmation.
6. The lock persists through recomposition, screen-off/on, and process recovery
   for that rest interval. A new set/rest interval starts unlocked.
7. **Start now** remains enabled. It cancels any queued/in-progress five-second
   warning, advances once, and emits only the appropriate **Go** cue/haptic.
8. At natural zero, the cue controller cancels any unfinished warning utterance
   before the **Go** cue so the two announcements cannot overlap.
9. Pausing during the locked phase retains the lock. Resuming at five seconds or
   fewer does not re-enable extensions or replay the warning.

The persisted session representation should carry the lock explicitly (or an
equivalent persisted transition revision); deriving it only from a Compose
timer is not reliable enough for process recovery.

### Exercise-success transition

After the final set of a non-final exercise:

- show a high-contrast green check and **Exercise complete**;
- show the completed exercise name and compact **completed/total** progress,
  for example **2/5 completed**; expose **2 of 5 exercises completed** to
  TalkBack;
- show explicit compact counts for **Completed**, **Skipped**, and **Pending**;
  pending is derived from the persisted per-exercise outcomes rather than a
  separate UI-only counter;
- show the next exercise and target below it;
- speak **“Exercise complete.”** once;
- if rest follows, merge into the rest sequence:
  **“Exercise complete. Rest for sixty seconds. Up next: rows, ten reps.”**;
- keep **Pause** and **Start now** reachable; the success treatment must not
  trap the user in a non-interactive animation.

This state is a presentation of the persisted transition, not a new history
event. Recomposition or process recovery must not log or announce completion a
second time.

### Final workout-success screen

After the final exercise, replace the current minimal completion page with:

- a large animated check/ring and **Workout complete**;
- a compact summary using already persisted session data:
  - exercises completed and skipped;
  - exercises pending (normally `0` on a valid completed workout);
  - sets completed;
  - elapsed active time;
  - estimated time, when available;
- **Saved on watch** only after the local result queue is durably persisted;
- **Synced to phone** only after a phone acknowledgement, otherwise a quiet
  **Waiting to sync** state that does not block Done;
- primary **Done** / **Back to workouts** action;
- no destructive action on the success surface.

Use a distinct double success haptic and speak once:
**“Workout complete. Great work.”** The screen may remain until dismissed; TTS
must not replay after recomposition, navigation back, or process recovery.

### Cue priority and exactly-once behavior

- Emit semantic cue events from session transitions, never from Compose
  recomposition or raw one-second redraws.
- Give terminal completion highest priority, then start/go, rest warning, and
  informational cues.
- Stop or replace obsolete queued speech on pause, restart, end, skip, or
  navigation away.
- Give every cue a deterministic key based on session package, session revision,
  exercise index, set, transition, and threshold.
- Persist a small cue ledger atomically with the session transition so process
  recreation cannot replay an exercise or workout success announcement. Prune
  it with the transient session package only after result acknowledgement.
- Countdown speech fires only when crossing a threshold from above; extending
  rest while still unlocked moves the threshold and allows one five-second
  warning for the new deadline. After the lock, extension is rejected and
  ordinary redraws never replay the warning.

## Feature-module boundaries

Quick Start must not be added as another branch inside the existing schedule
sync code. It gets an explicit feature boundary on each platform.

### Shared Kotlin contract

New directory:

```text
android/shared/src/main/kotlin/app/personal/workouttracker/shared/quickstart/
  QuickStartContract.kt
  QuickStartValidation.kt
```

Responsibilities:

- request and acknowledgement models;
- path constants owned by this feature;
- schema/version constants;
- prescription validation;
- expiry and state-transition helpers;
- all-or-nothing list validation and preservation of item order;
- pure conversion from a validated request to a transient
  `WatchSessionPackage`.

Suggested request fields:

```text
requestId
schemaVersion
revision
createdAtMillis
expiresAtMillis
targetNodeId
title?
source: single | library_playlist | library_selection | today_row
exercises[]:
  itemId
  exerciseId
  exerciseName
  sets
  prescription
  restSeconds
  loadWeight?
  loadUnit?
  sourceDate?
  sourceWorkoutRowId?
```

Suggested acknowledgement fields:

```text
requestId
revision
targetNodeId
status: ready | started | dismissed | cancelled | rejected | expired
reason?: active_session | pending_request | invalid_payload |
  unsupported_schema | storage_error
watchUpdatedAtMillis
```

Use stable request and item IDs. Never use the exercise display name as
identity. Revision, not wall-clock time, orders updates for a request. Expiry is
evaluated as a received-time TTL with a documented clock-skew allowance;
timestamps remain diagnostic metadata. Requests with zero items, more than 24
items, duplicate item IDs, unknown schema versions, or any malformed
prescription are rejected as a whole; never store a partial workout.

New fields use backward-compatible defaults where safe. Phase 0 must define a
feature-specific schema migration and a capability handshake for phone-new /
watch-old and watch-new / phone-old combinations before either UI exposes Send.

#### Capability and schema migration decision — 2026-09-26

- The shared v1 capability envelope carries the publishing Data Layer node ID,
  `phone` or `watch` role, envelope version, and an explicit list of implemented
  request schema versions. The current helper constructs an advertisement for
  request schema 1; native publication is still pending.
- The sender checks the selected node is currently reachable and that the
  advertised node ID matches it. Peers must have opposite roles. A missing
  advertisement means the older app has no Quick Start capability; malformed or
  unknown envelope versions fail closed. A cached Data Item alone does not
  prove the peer is connected or that it has accepted a request.
- Choose the highest schema in the intersection of the peers' advertised lists.
  A future phone or watch may advertise schemas 1 and 2 only when it implements
  both codecs. Mixed-version peers then use schema 1. If a peer supports only
  schema 2 while the other supports only 1, no request is sent and the user
  needs an app update. Current request validation still accepts only schema 1.
- Optional fields within the v1 capability envelope are ignored for compatible
  additions. A change that alters required meaning needs a new envelope
  version and explicit support; unknown envelopes are rejected. No old app is
  assumed to support Quick Start simply because it runs the same Android app ID.
- These are shared contract rules and headless fixtures. Native discovery,
  capability publication, request/ack delivery, user-facing update wording,
  and migration of any future persisted schema remain pending.

### Android phone native module

New directory:

```text
android/app/src/main/java/app/personal/workouttracker/quickstart/
  WatchQuickStartPlugin.kt
  WatchQuickStartClient.kt
  QuickStartAckListenerService.kt
  QuickStartReceiptStore.kt
```

Responsibilities:

- expose a small Capacitor API to the React layer;
- check Android platform/plugin/watch capability;
- send one validated request through the Wear Data Layer;
- listen for watch acknowledgements;
- persist the latest request/receipt so app recreation does not lose status;
- serialize sends and ignore stale/duplicate acknowledgements.

Do not put Quick Start methods in `ScheduleSyncPlugin`. Schedule sync remains
responsible for the weekly schedule and **Send today to watch**.

Proposed Capacitor API:

```text
sendQuickStart(request) -> { requestId, transportAcceptedAtMillis }
getQuickStartStatus(requestId) -> acknowledgement or pending
cancelQuickStart(requestId) -> local cancellation plus watch dismissal request
addListener("quickStartStatus", acknowledgement)
```

Transport acceptance is not a delivery receipt. The plugin must not translate a
successful `putDataItem` call into **Ready on watch**.

### React/PWA module

New files:

```text
pwa/src/features/watch-quick-start/
  WatchQuickStartSheet.tsx
  useWatchQuickStart.ts
  watch-quick-start.ts
  types.ts
```

Responsibilities:

- form state and validation;
- capability-aware Android/browser wording;
- request/acknowledgement UI state machine;
- listener setup/cleanup and app-resume refresh;
- focused presentation with no IndexedDB or native calls in the component.

Integration points should stay thin:

- `LibraryView.tsx` passes one exercise, the current playlist, or an ordered
  selection into the same feature entry point.
- `TodayView.tsx` passes an existing scheduled row.
- `App.tsx` owns no Quick Start state machine; it wires the feature hook and
  sheet into the composition root only.
- `pwa/src/lib/native-bridge.ts` may expose capability discovery, but feature
  behavior belongs in `pwa/src/features/watch-quick-start/`.

### Wear OS module

New directory:

```text
android/wear/src/main/kotlin/app/personal/workouttracker/wear/quickstart/
  QuickStartListenerService.kt
  QuickStartCoordinator.kt
  QuickStartReceiptClient.kt
  WatchSessionPackageStore.kt
  QuickStartPrompt.kt
```

Responsibilities:

- decode, validate, and deduplicate incoming requests;
- reject expired requests and active-session conflicts;
- persist the complete accepted request in the separate transient
  `WatchSessionPackageStore`, preserving item order and rejecting partial
  conversion;
- expose a ready prompt/notification without forcing an Activity to the front;
- expose the same pending card in-app when notification permission is denied;
- emit ready/started/dismissed/cancelled/rejected acknowledgements;
- create the existing session engine's runtime input only after explicit Start.

The coordinator may call a narrow, serialized session-start method. It must not
duplicate the session engine, insert Quick Start into the permanent date-keyed
download store, or keep a second long-term workout repository.

### Wear OS cue and success module

New directory:

```text
android/wear/src/main/kotlin/app/personal/workouttracker/wear/cues/
  WatchCueController.kt
  WatchTtsEngine.kt
  WatchCuePreferences.kt
  WatchCueLedger.kt
  WatchCueEvent.kt
```

Session integration files remain under `wear/session/`, but they emit semantic
events into this module. `SessionViewModel` owns workout state transitions; the
cue controller owns TTS sequencing, haptic intent, cancellation, priority, and
exactly-once keys. Compose screens render countdown/success state and never call
`TextToSpeech.speak` directly.

The first version generates the spoken brief from the sanitized display name
and prescription using shared deterministic formatting; a separate
`spokenBrief` transport field is deferred unless reviewed coaching content is
added. Formatting must define ranges, seconds/minutes, load units, maximum
length, locale fallback, and control-character removal.

The Wear manifest declares TTS service discovery where required. The TTS
adapter initializes asynchronously, verifies language/output availability,
handles audio focus and speaker/Bluetooth changes, and calls `shutdown()` when
the session/cue scope ends. TalkBack must not receive duplicate simultaneous
speech; visual and haptic behavior remains complete when voice is suppressed.

## Protocol and reliability rules

1. Every request has a UUID `requestId`, revision, target node, and short expiry
   (initial proposal: five minutes).
2. The phone chooses one reachable compatible node and binds the request and
   receipts to that node. Multiple-watch broadcast behavior is out of scope.
   The first version does not queue a surprise workout for later delivery.
3. The phone sends a Data Item containing the request and reports only
   **Waiting for watch** after transport acceptance.
4. The watch validates capability/schema, received-time expiry with clock-skew
   tolerance, list size, identity, every prescription, bounds, order, and the
   global pending/active-session invariant before storage.
5. The watch durably stores the transient package before sending `ready`.
6. Repeated delivery of the same request is idempotent and returns the previous
   outcome without creating another transient package or session.
7. The first release rejects a different request while an offer is pending with
   `pending_request`; it does not silently replace or dismiss the prior offer.
8. At the foreground countdown's zero, Start atomically rechecks that no
   blocking session exists and persists `STARTING`. The `started` receipt must
   follow durable runtime-session creation, including recovery after a process
   interruption. Blocking states are `READY`,
   `STARTING`, `ACTIVE`, `RESTING`, and `PAUSED` across every watch entry path.
9. Cancellation is a revisioned protocol event. It can dismiss `READY`; once
   `STARTING` or `started` wins the serialized transition, cancellation cannot
   stop or erase that session. Duplicate cancel/dismiss is idempotent.
10. Phone listener/app-resume races reconcile by request ID and revision using
    an explicit transition table. Wall-clock timestamps never determine order.
11. Request, acknowledgement, and cancellation Data Items use request-scoped
    paths. Terminal items are deleted only after both sides have persisted the
    outcome; terminal receipts remain long enough for replay-safe recovery.
12. Critical session transitions and queue writes are serialized. Countdown
    lock, completion summary, result enqueue, and cue-ledger updates are awaited
    before dependent UI, speech, or acknowledgements are emitted.

## Battery and runtime budget

Stage 19 must not regress the watch's existing deadline-based, lifecycle-aware
session behavior:

- transfer the complete package once; do not continuously synchronize progress;
- sync only meaningful transitions or final queued results, using callbacks and
  bounded retry/backoff rather than phone/node polling;
- derive elapsed/rest/countdown values from persisted deadlines and a monotonic
  time source; visible text may update once per second only while resumed;
- do not write elapsed time to storage every second and do not hold a manual
  wake lock or force the display to remain on;
- allow ambient/screen-off operation and catch up from persisted state;
- initialize/reuse TTS only for the active cue scope, then release it; keep
  haptics and animations brief and respect reduced-motion/ambient presentation;
- if sensors are added later, register only while required, use the lowest
  acceptable sampling rate, and unregister on pause/end.

Physical acceptance records a baseline and a comparable 60–90 minute session
on a supported watch. Code completion cannot claim a battery percentage target
until measured hardware evidence establishes a defensible budget.

## Release and installation compatibility

The phone and Wear components ship from the same Play Store application listing
and should first be exercised through an internal-testing track. The phone shows
**Send to watch** only after the installed native plugin discovers a reachable
Wear component with a compatible Quick Start schema/capability. Sideloaded debug
builds remain a development path, not the reliable end-user distribution plan.

## Delivery phases and resumable checklist

Each phase is a separate reviewable checkpoint. Do not start the next phase
until the phase's listed automated checks pass. Physical paired-device evidence
is recorded separately from code completion.

### Progress audit — 2026-09-26, Iteration 14

Checklist items are counted equally for a reproducible completed/total view;
the ratio is not an engineering-effort estimate. The working tree currently
stands at **22/96 items (23%) overall**:

| Phase | Completed/total | Status |
|---|---:|---|
| Phase 0 — foundations | **22/30 (73%)** | In progress; contracts, transient storage, arbitration, cancellation, summaries, persisted reducer outcomes, capability negotiation, completed-result receipts, recovery decisions, and focused fixtures exist |
| Phase 1 — phone feature | **0/10 (0%)** | Not started |
| Phase 2 — watch feature | **0/17 (0%)** | Not started |
| Phase 3 — integration/recovery | **0/12 (0%)** | Not started |
| Phase 4 — device acceptance | **0/27 (0%)** | Not started |

The latest committed checkpoint, `bffe396`, represents **21/96 items (22%)**.
Iteration 13's earlier uncommitted status is superseded by that commit.
Iteration 14 adds ended-result retention and closes the persisted final-outcome
decision in this audited checkpoint. Phase 0's
73% must not be reported as feature
completion: there is no usable phone-to-watch Quick Start path yet.

Preparation order for the next parts:

1. Define the shared final-result/phone-receipt wire payload and sender-node
   validation before native transport wiring.
2. Close the remaining Phase 0 cue decisions and fixtures, including the
   final-countdown lock.
3. Build the isolated Phase 1 phone bridge and React sheet.
4. Build the Phase 2 watch receiver, ready prompt, cue controller, and success
   UI using the persisted reducer output.
5. Complete Phase 3 recovery/regression wiring before Phase 4 physical-device,
   Play internal-track, audio-routing, and battery acceptance.

### Phase 0 — Domain, persistence, decisions, and contract fixtures

Status: **In progress — contract, store, gate, cancellation, summary, reducer, outcome persistence, capability, and completed-result retention slices passed**

- [x] Confirm entry points: single exercise, current Library playlist, explicit
      Library multi-select, and Today row.
- [x] Confirm the existing 24-item maximum and all-or-nothing rejection policy.
- [x] Confirm pending-offer policy: reject a second request with
      `pending_request` in the first release.
- [ ] Define `WatchSessionPackage`, phone ownership, transient watch retention,
      result acknowledgement, and removal independently of the date-keyed store.
- [x] Implement isolated one-package Wear persistence with local received-time
      expiry, malformed/stale cleanup, duplicate idempotency, and pending-request
      rejection; result acknowledgement/removal remains open.
- [x] Define the global blocking-session invariant: null/completed/ended legacy
      states do not block; active/resting/paused/unknown states and Quick Start
      `READY`/`STARTING` do block competing entry points.
- [x] Implement the pure process-wide start gate and repository snapshot adapter
      with atomic Quick Start/legacy arbitration; production entry-point wiring
      remains open.
- [x] Define `READY`/`STARTING` persistence and back/navigation/reboot behavior:
      countdown navigation leaves `READY`; the gate marks `STARTING` at zero;
      recovery resumes the same session explicitly without unseen auto-start.
      Production navigation/reboot acceptance remains open.
- [x] Persist and serialize the package's atomic `READY` to `STARTING`
      transition across store recreation; navigation behavior remains open.
- [x] Confirm five-minute request expiry with 30 seconds of clock-skew tolerance.
- [ ] Confirm voice defaults: explicit opt-in, all four cue categories enabled
      after opt-in, and system TTS language.
- [ ] Confirm the exact start/rest/exercise-success/workout-success scripts and
      maximum spoken-brief length.
- [ ] Define cue-event keys, priority, cancellation, and recovery behavior.
- [ ] Confirm the final-countdown lock: extensions allowed above five seconds,
      atomically disabled at five seconds, lock retained through pause/recovery.
- [x] Define request/ack models and non-regressing revision-based state ordering.
- [ ] Define cancellation races, revisions, node binding, Data Item paths and
      cleanup, clock-skew tolerance, capability handshake, and schema migration.
- [x] Implement revisioned local Dismiss/Cancel persistence: `READY` becomes a
      replay-safe terminal tombstone, `STARTING` wins over termination, and
      simultaneous Start/Cancel has exactly one outcome. Transport cleanup,
      capability, and migration work remain open.
- [x] Define persisted exercise/set outcomes and immutable success summary;
      local **saved** and phone **synced** states must remain distinct.
- [x] Add the shared immutable progress/completion contract with validated
      per-exercise outcomes, completed/skipped/pending counts, completed/planned
      sets, elapsed/estimated time, and stable serialization. Immutable final
      result persistence now exists; UI saved/synced integration remains open.
- [x] Add the pure Wear outcome reducer and derived compact
      **completed/total** presentation (for example `2/5`) with descriptive
      TalkBack text. Runtime session wiring and UI rendering remain open.
- [x] Persist ordered reducer outcomes and revision atomically in an isolated
      Wear store; verify restart/replay, invalid-state preservation, identity/plan
      conflicts, concurrent transitions, and storage failures. A real
      Preferences DataStore file recovery test passes. Runtime wiring and
      native final-result/receipt integration remains open.
- [x] Define a shared v1 capability envelope and pure schema negotiation with
      reachable-node/role binding. The current helper includes only implemented
      request schema 1. Missing, malformed, unsupported, and mixed-version
      fixtures pass; publication and UI gating remain open.
- [x] Persist completed and ended Quick Start summaries until an exact phone
      receipt; prune package/outcome/result in a restart-safe sequence and
      retain bounded replay tombstones. Headless real-file and write-failure
      fixtures pass. Native transport remains open.
- [ ] Define short-rest cue scripts, TalkBack/audio-output behavior, TTS
      lifecycle, manifest discovery, and deterministic spoken formatting.
- [x] Record the battery/runtime budget and headless-versus-device test split.
- [x] Add the first shared request/acknowledgement validation, transient-package
      conversion, ordering, serialization, and status-transition tests.

Exit checks:

- [x] `:shared:test` passes (54 tests: 15 Quick Start, 10 capability, 13 terminal/progress summary, and
      16 existing).
- [x] Empty, oversized, partially invalid, reordered, expired, clock-skewed,
      duplicate, cancelled, out-of-order, and unsupported-schema fixtures pass.
- [x] Shared capability negotiation and mixed phone/watch-version fixtures
      pass, including schema-1 fallback and incompatible schema refusal.
- [ ] Contract fields and chosen decisions are recorded in this document.

### Phase 1 — Phone bridge and React feature shell

Status: **Not started**

- [ ] Create the isolated React feature directory.
- [ ] Add the Quick Start sheet with one-or-many item editing, ordering, and
      prescription validation.
- [ ] Create the separate Capacitor plugin/client/receipt store.
- [ ] Add platform/plugin gating without changing browser-to-watch claims.
- [ ] Add mocked bridge tests for every phone-visible status.
- [ ] Integrate Library and Today through narrow props/callbacks.

Exit checks:

- [ ] `npx tsc --noEmit` passes.
- [ ] Focused browser fixture passes.
- [ ] Browser UI never offers a non-functional send action.
- [ ] `App.tsx` does not acquire feature-specific effects or handlers.

### Phase 2 — Watch receiver and ready prompt

Status: **Not started**

- [ ] Implement listener, coordinator, receipt client, and prompt.
- [ ] Persist accepted requests through the transient package store, leaving the
      permanent date-keyed download store unchanged.
- [ ] Handle active-session, invalid, expired, and duplicate requests.
- [ ] Add Start, Dismiss, and Cancel acknowledgements and serialized races.
- [ ] Add the isolated cue controller, TTS adapter, preferences, and cue ledger.
- [ ] Add the foreground-only five-second start state and cancellation path.
- [ ] Add rest-duration, five-second, and Go cue events without changing the
      persisted rest deadline.
- [ ] Add the persisted final-countdown lock and enforce it in both UI and
      `SessionViewModel.onAddRestSeconds`.
- [ ] Add exercise-success and final workout-success presentations.
- [ ] Add notification permission/fallback behavior without making notification
      permission a requirement for opening the in-app ready prompt.
- [ ] Add immutable final summary and await durable local result enqueue before
      rendering **Saved on watch**.
- [ ] Add TTS discovery/lifecycle, audio-output/focus, TalkBack, ambient, and
      reduced-motion handling.

Exit checks:

- [ ] Wear JVM tests cover all receipt outcomes and duplicate delivery.
- [ ] Cue tests cover exact scripts, threshold crossing, priority, cancellation,
      pause/resume, TTS failure, process recovery, and exactly-once completion.
- [ ] Rest-boundary tests cover extension accepted at six seconds, rejected at
      five seconds, simultaneous tap/threshold ordering, Start now cancellation,
      and no warning/Go speech overlap.
- [ ] Existing session/repository tests still pass.
- [ ] Round-screen preview/emulator shows readable Start/Dismiss, five-second
      countdown, exercise-success, rest, and final-success states.

### Phase 3 — End-to-end integration and recovery

Status: **Not started**

- [ ] Wire acknowledgement events to the phone plugin and React hook.
- [ ] Reconcile status after phone process death/app resume.
- [ ] Verify watch process death between ready and Start.
- [ ] Verify phone cancellation/dismissal behavior.
- [ ] Verify request/ack Data Item cleanup, stale replay, node binding, and
      mixed-version capability gating.
- [ ] Verify completion stays queued through watch reboot until phone ACK, then
      transient package/cue data is safely pruned.
- [ ] Preserve **Send today to watch**, scheduled downloads, log sync, and live
      watch session status without contract regression.

Exit checks:

- [ ] Browser regression fixtures all pass.
- [ ] Shared, phone, and Wear JVM tests pass.
- [ ] `npm run cap:sync` passes.
- [ ] Clean phone and Wear debug APK builds pass.
- [ ] `git diff --check` passes.

### Phase 4 — Paired-device acceptance

Status: **Not started; cannot be closed by emulator-only evidence**

- [ ] Connected: one Library exercise reaches the watch and shows Ready.
- [ ] Connected: the current Library playlist arrives in the same order with
      exact per-item prescriptions.
- [ ] Connected: an explicit multi-selection can be reordered and arrives in
      that order.
- [ ] Connected: Today row uses its exact sets/reps/rest/load prescription.
- [ ] Start acknowledgement reaches the phone while it is open.
- [ ] Start acknowledgement reconciles after the phone app is reopened.
- [ ] Disconnected watch produces actionable phone wording and no stale workout.
- [ ] Active watch workout is never replaced.
- [ ] Duplicate send/tap produces one transient package and one session.
- [ ] Expired request cannot be started.
- [ ] Watch process restart retains or safely dismisses the pending offer.
- [ ] Unsynced completed results survive watch process death and reboot and are
      removed only after phone acknowledgement.
- [ ] Voice opt-in/out persists and every cue category can be disabled.
- [ ] Start briefing states the correct exercise, sets, reps/duration, and load.
- [ ] Start never begins unseen after leaving during the five-second countdown.
- [ ] Rest announces the configured seconds once; extensions do not replay the
      original announcement.
- [ ] `+5`, `+10`, and `+30` disable at the final five-second lock; no accepted
      extension or “added” cue occurs after the lock.
- [ ] Five-second and Go cues fire once at the correct transition.
- [ ] Exercise-success and final-success speech/haptics do not replay after
      recomposition, app restart, or navigation.
- [ ] Missing/failed TTS falls back to complete visual and haptic behavior.
- [ ] Short rests follow the `0–5`, `6–10`, and `>10` cue rules without overlap.
- [ ] Speaker/Bluetooth changes, TalkBack, ambient mode, and screen-off recovery
      preserve correct session behavior without duplicate speech.
- [ ] A comparable 60–90 minute physical-watch run records battery evidence,
      with no manual wake lock, continuous polling, or per-second persistence.
- [ ] Play Store internal-test installation/update and mixed compatible versions
      expose Quick Start only when both components support it.
- [ ] Existing full-day send, watch-requested sync, completed logs, live status,
      and offline history retry still work.

Exit checks:

- [ ] Evidence identifies phone/watch models and OS versions.
- [ ] Code completion and paired-device acceptance are both explicitly closed.

## Test matrix

| Layer | Required coverage |
|---|---|
| Shared | list bounds, all-or-nothing validation, order, duration/reps parsing, TTL/clock skew, schema migration, request conversion, transition table, revision ordering |
| React | single/playlist/selection entry, ordering, per-item validation, capability gating, status wording, listener cleanup, resume reconciliation, active-session error |
| Phone native | node selection, capability/version gating, transport accepted, acknowledgement persistence, cancel race, duplicate/out-of-order receipts, cleanup, plugin lifecycle |
| Wear | transient package storage, global session invariant, invalid/expired/duplicate request, dismiss/cancel/start races, pre-start recovery, durable summary/result queue |
| Watch cues | opt-in, generated/short-rest scripts, TTS init/language/output failure, audio focus/TalkBack, sequencing, priority, cancellation, atomic lock, Start now, pause/resume, exactly-once ledger |
| Regression | full-day send, scheduled watch request, session-state sync, completed log/session queues |
| Device | connected/disconnected/reconnected, process death/reboot, duplicate taps, notification denied, round-screen/ambient layout, audio routing, internal-track install, measured battery |

All contract, state-machine, persistence, cue-scheduling, and bridge tests run
headlessly in JVM/browser fixtures. Emulator and physical-device checks are
reserved for platform behavior that headless tests cannot prove: Data Layer
delivery, TTS intelligibility/routing, haptics, ambient mode, Play delivery, and
measured battery use.

## Risks and safeguards

- **Misleading success:** Data Layer acceptance is not watch receipt. Keep
  `sent`, `ready`, and `started` distinct.
- **Accidental session:** require an explicit watch Start tap.
- **Active workout loss:** reject Quick Start while any watch session is active;
  never reset or replace it.
- **Stale delayed start:** use a short expiry and reject disconnected immediate
  sends in the first release.
- **Duplicate delivery:** deduplicate by request ID before repository writes.
- **Permanent-storage collision:** keep Quick Start out of the date-keyed
  download store; retain only one transient pending/active package plus unsynced
  results.
- **Partial playlist corruption:** validate every item before writing anything;
  one invalid item rejects the complete request with an actionable phone error.
- **Unexpected order:** preserve explicit array order through conversion,
  persistence, watch display, and session start; cover it at every layer.
- **Speech overlap or stale cues:** sequence through one controller with priority
  and cancellation; never speak directly from UI rendering.
- **Rest extension/start-cue race:** latch the final-countdown state before cue
  emission, disable every extension control, and enforce the guard again in the
  ViewModel using serialized state transitions.
- **Repeated praise/cues:** use deterministic event keys and a persisted ledger,
  especially for exercise and workout completion.
- **TTS unavailable/no speaker:** voice remains optional; visual targets,
  countdowns, and distinct haptics must carry the complete experience.
- **Battery drain:** avoid wake locks, polling, per-second persistence, and
  continuous sync; use lifecycle-aware deadline calculation and bounded retry.
- **False saved/synced claim:** await the durable watch queue before **Saved on
  watch** and require a phone receipt before **Synced to phone**.
- **Hidden auto-start:** pre-start is foreground-only and cancels to Ready when
  the user leaves; no new foreground service is introduced in this scope.
- **Composition-root growth:** isolate the React workflow in its feature hook;
  do not expand the existing `App.tsx` maintenance debt.
- **Protocol drift:** keep request/ack models and validation in the shared module,
  with phone and watch using the same contract.
- **Browser confusion:** keep the existing message that browser/PWA has no Wear
  bridge; Quick Start appears only when the Android plugin is available.

## Definition of done

The feature is done only when:

- phone users can send one exercise, the current playlist, or an ordered
  selection after confirming every prescription;
- the phone distinguishes transport acceptance from watch readiness;
- the watch presents a deliberate Start action and starts exactly one session;
- opted-in users hear the correct briefing, five-second warning, rest duration,
  Go cue, and success cues without overlap or replay;
- no rest extension is accepted after the final five-second warning begins, and
  Start now/natural zero cannot overlap the warning with the Go cue;
- users without working TTS receive a complete visual/haptic countdown, rest,
  exercise-success, and workout-success experience;
- active, expired, invalid, duplicate, disconnected, and interrupted cases have
  verified outcomes;
- the phone remains the permanent source of truth while the watch survives
  interruption with only its pending/active package and unsynced results;
- cancellation, version mismatch, multiple-node selection, clock skew, result
  acknowledgement, and stale Data Item cleanup have verified outcomes;
- a physical-watch run records battery evidence without wake locks, polling, or
  per-second persistence;
- existing schedule/watch sync behavior has regression coverage;
- automated checks and clean APK builds pass;
- paired physical-device acceptance is recorded separately and passes.

## Resume marker

**Current checkpoint:** Phase 0 shared contract slice implemented and verified;
the isolated Wear transient store is also implemented and verified. Phone,
transport, UI, session integration, native result acknowledgement, and cues are
unchanged. The pure global start gate is implemented but not yet wired into
production navigation/session creation. Revisioned Dismiss/Cancel and terminal
replay protection are implemented in the store but not wired to transport/UI.
The shared progress/completion summary now includes explicit pending counts.
A pure Wear reducer produces it and derives compact **completed/total** text,
and Iteration 11 persists its ordered outcomes/revision atomically with tested
recovery and failure behavior. It is not yet connected to the runtime session
engine or rendered by UI. Completed and ended outcomes are frozen and retained;
final records, exact phone receipts, offline recovery, and guarded cleanup exist,
but native sender/receiver and UI do not. Iteration 12 adds a shared capability
envelope and schema-1 negotiation with mixed-version fixtures. All 54 shared
and 81 Wear tests and the Wear debug build pass. Physical-device acceptance
remains open.
**Current phase:** Phase 0 — Domain, persistence, decisions, and contract
fixtures.
**Exact next action:** specify the shared final-result and phone-receipt wire
payloads with exact identity, path and observed sender-node validation. Cover
both terminal types and invalid peers before native transport or UI.
**Do not start with UI code:** stabilize the shared request/acknowledgement
contract and state ordering first.

At the end of every future session, update this marker, the relevant phase
checkboxes, and `IMPLEMENTATION_PROGRESS.md` with commands/results and the exact
next file or test. Never mark a phase complete from code presence alone.
