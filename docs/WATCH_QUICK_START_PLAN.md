# Watch Quick Start Plan

Status: **Phases 0–3 complete; Phase 4 physical-device acceptance pending**
Active stage: **Stage 19 — Phone-selected Watch Quick Start**
Planning checkpoint: `23dd15c PST01: Finalize code`
Latest checkpoint: **Iteration 64 — TTS callback ownership fix and voice-enabled rest/transition validation complete (uncommitted)**
Previous checkpoint: `02ee2f7 PST01: Validate voice-enabled Wear UI interruption`
Last updated: **2026-10-04**

**Current closure:** **70/97 (72%)**, Phase 3 **12/12 (100%)**. Windows paired
emulators verify Ready/process restart/Start, Cancel/Dismiss, offline queued
completion through reboot, exact receipt and runtime/package/cue pruning.
Receipt-aware final presentation and race-safe ledger cleanup are fixed.
Phone startup/resume cleanup passes native acceptance; interrupted-result
recovery passes JVM checks. Historical replay/capability and legacy native
transport checks pass on both peers; 53 relevant browser checks pass.
Iteration 54's historical mode also passes acknowledgement-path and invalid
result binding/receipt checks on both peers. Iteration 55 passes the full fresh
completion/Ready/Cancel/replay matrix and real phone launch/resume cleanup on
isolated AVD copies; 16 unchanged source disk hashes prove original workout
preservation. The active-session blocker is superseded for this isolated setup.
Current suites pass 78 shared,
28 phone and 204 Wear tests; Iteration 64 updates the installed production Wear APK.
Iteration 56 fixes a real countdown
navigation crash during lifecycle dispatch and extends acceptance to actual
Wear UI taps, deadline locks, rest recovery, terminal presentation and settings.
User chose emulator validation; paired UI and ordinary instrumentation pass,
with settled screenshots confirming rendered cue settings. All 16 original
source AVD disk hashes remain unchanged after the UI run and copied-AVD shutdown.
Iteration 57 passes installed phone WebView Library single/playlist/reordered
selection and Today delivery with exact watch package equality and UI
Ready/Cancel reconciliation, preserving original IndexedDB records, native
schedule cache and watch legacy entries. All 16 original source disk hashes
remain unchanged after shutting down the copied AVDs. No production change was needed.
Physical Phase 4 remains 0/27, separately from emulator acceptance.
Iteration 57 is committed in `0d48324`. Iteration 58 validates Start in open/
reopened phone UI, duplicate clicks, exact active runtime preservation, real
expiry and disconnected Send/reconnection. It fixes active-workout rejection
guidance and removes invalid expired cancellation. Both paired state peers,
native expiry restoration, 22 browser checks and eight phone/seven Wear ordinary
tests pass. All 16 original source AVD disk hashes remain unchanged.
Iteration 58 is committed in `b9b8431`. Iteration 59 validates exact cue ledger
sequences for seven short rests, exercise/workout-success Activity recreation,
and actual Dozing/ambient rest recovery. It fixes the lifecycle-only foreground
timer gate, preserving exact runtime/cues while ambient and catching up once
on wake. Both paired peers, 199 Wear JVM tests, APK builds and eight ordinary
tests per peer pass; all 16 original source disk hashes remain unchanged.
Iteration 59 is committed in `573ad28`. Iteration 60 validates actual countdown
ambient cancellation/retry and active-set sleep/wake, plus staged exercise/
final-success fresh-process recovery before receipt. Exact offline runtime/
cues and final phone result survive; reconnection produces one receipt and
prunes watch state. Every explicit stage, both test APK builds, nine phone/
twelve Wear ordinary checks and all 16 original disk hashes pass. A stopped-
phone stale-discovery timeout is superseded by airplane/mapping isolation and
copied-watch reboot; original settings are restored. No production change is
needed. Physical Phase 4 remains 0/27.

Iteration 60 is committed in `965becd`. Iteration 61 fixes synchronous missing-
engine initialization leaving voice status at Checking. Final native tests pass
actual TTS completion, category/voice suppression, real Google TalkBack touch-
exploration suppression and unavailable-engine fallback, with duplicate output
prevented after controller/store recreation. Paired UI passes actual zero-scale
policy and static rest/success, unchanged deadline locks, saved summary and
unavailable-voice guidance; 43 prior phone records stay exact and one new result/
receipt is added. Both test builds, production Wear build, all 199 Wear JVM
tests, nine phone/thirteen Wear ordinary checks, visual review, settings restore
and 16 original source disk hashes pass. Historical failed harness attempts are
retained; acceptance applies actual scale at validation points because automation/
lifecycle transitions can reset it. Physical Phase 4 remains 0/27.

Iteration 61 is committed in `eb2be9e`. Iteration 62 validates actual native
in-flight cancellation for pause/Start now/navigation/end, priority replacement,
transient focus loss and locked native focus denial, plus unsupported-language
fallback/restoration. Exact cue/runtime/legacy witnesses stay unchanged;
focus owners and adopted shell identity are released. All three explicit
methods, the final test build, all 201 Wear JVM tests, 16 ordinary Wear checks
and all 16 original disk hashes pass. Added JVM checks prove pause/end speech
cancellation follows durable commits and failed writes preserve active state.
No new production defect is established. These output/controller observations
remain separate from actual voice-enabled UI action acceptance; physical
Phase 4 remains 0/27. Only the Wear copy is booted for this iteration.

Iteration 62 is committed in `f14785e`. Iteration 63 closes real voice-enabled
Pause/Start now/Back and safe paused End UI acceptance on the exact copied pair.
Read-only observations use the on-screen production AndroidTtsCueOutput; no
output/listener or session transition is replaced. Native speech/focus is active
before each interruption and idle/released afterward. Back preserves exact
RESTING/deadline/progress; reopening neither reserves nor replays that rest.
End confirms from paused state, saves 2/6 partial sets and a phone receipt, then
prunes runtime/package without workout-success replay. All 45 prior phone
records, legacy entries and preferences stay exact; one result/receipt is added.
Paired tests, both test builds, nine phone/seventeen ordinary Wear checks,
visual/crash/inventory checks and all 16 source disk hashes pass. The first
incorrect Back-pause harness assertion is preserved as superseded evidence;
no production defect is established. Physical Phase 4 remains 0/27.

Iteration 63 is committed in `02ee2f7`. Iteration 64 fixes a native TTS callback
ownership defect exposed by a five-second exercise transition: a stale stop
callback could complete/unfocus the replacement warning while it still played.
Callback IDs now match the owned utterance and finish once; a unique token
guards native stop/focus release, with explicit old-operation cancellation.
The final real voice-enabled matrix passes 0/3/5/6/8/10/12/20-second natural
rests and seven exercise transitions, with 36 cue keys observed in native
playback and all 26 warning/Go samples at valid thresholds. All 16 sets save,
receive a phone receipt and prune; terminal success stays silent after Activity
recreation. All 48 prior phone records and original legacy entries/preferences
stay exact. Production/test builds, 204 Wear JVM tests, all three native
interruption/focus/language regressions, nine phone/eighteen ordinary Wear
checks, visual/crash/inventory checks and all 16 original disks pass. Historical
readiness/transition failures remain evidence. Physical Phase 4 stays 0/27.

**Historical closure (Iteration 35, superseded by Iteration 48):** Iteration 35 passes 78 shared, 16 phone, and 191 Wear JVM
tests plus both debug APK builds, TypeScript, Capacitor sync, the production
PWA build, and three Node checks. Iteration 34's 109 browser checks remain the
latest real-browser evidence. The checklist is
65/97 (67%); Phase 2 has serialized Start/Dismiss/Cancel,
replay-safe native final-result transport and receipt cleanup, schema-1
capability publication on both peers, and the isolated cue/TTS/persistence
foundation plus foreground start, durable rest cues, persisted exercise/
workout success presentations, notification-independent Ready fallback,
persisted voice settings, runtime TTS/locale/audio-route reporting, and explicit
ambient/reduced-motion presentation policy. The round Wear OS 7 AVD passed the
Ready, countdown, rest, exercise-success, final-success, and ambient layout
review; countdown-to-ambient returned to the durable Ready offer. Phase 3 now
records the already-wired acknowledgement event and recreation/resume paths
plus its automated exit checks. Paired delivery/recovery and physical
accessibility/audio behavior remain open. The paired-emulator attempt confirmed
the documented ADB bridge, but Android Studio's official assistant reports that
the cold Pixel 8 AVD lacks Google Pixel Watch; its Play Store is signed out, so
the companion cannot be restored without user-owned account action. See
`IMPLEMENTATION_PROGRESS.md` for evidence and next actions.

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

### Shared result and phone receipt wire decision — 2026-09-26

| Message | Required envelope | Data Layer path |
|---|---|---|
| Final result | `schemaVersion: 1`, `watchNodeId`, `result` | `/quick-start/result/{requestId}/{resultId}` |
| Import receipt | `schemaVersion: 1`, `watchNodeId`, `status: "persisted"`, `receipt` | `/quick-start/result-receipt/{requestId}/{resultId}` |

The shared result retains request/result IDs, final outcome revision, expected
phone node and exactly one completion/ended summary. The receipt echoes those
IDs/revision/phone node plus `receivedAtMillis`. The phone must atomically import
the result and save its receipt before publishing `persisted`, then resend that
same receipt and timestamp on retries. Codec acceptance is not proof of import.

The phone decoder compares the actual Data Layer sender to its saved request's
watch node, the envelope watch node, the receiving phone, session/request
identity, ordered exercise plan and explicit title. A null request title allows
a validated local display title. Offer expiry does not discard an offline final
result. The request receiver preserves the originating phone identity from
the observed offer sender. The runtime store uses it in its frozen final record;
Iteration 25 wires native final-result delivery and permanent phone import.

The Wear decoder binds the observed phone sender, local watch node, exact path,
result identity and revision before any receipt write. After compaction it
requires the exact saved receipt, including its timestamp. Wire request IDs are
canonical UUIDs; result IDs are 1–128 ASCII letters/digits/underscores/hyphens.
Both codecs reject malformed/oversized payloads (131,072-character bound), missing
required version/status, unknown schemas and unknown receipt statuses, while
allowing additive optional fields. Existing stored result JSON remains readable.

At the Iteration 15 checkpoint, the request listener and origin-node persistence
were implemented while native final-result/receipt listeners, permanent phone
import, Data Item removal, capability publication and paired delivery remained
open. **Superseded by Iteration 25:** the native transport/import/cleanup and
capability publication are now implemented; paired delivery remains unverified.
Transport receipt paths must use the guarded payload entry point; direct typed
receipt operations are for previously validated records and headless fixtures.

#### Ownership, cancellation and cleanup decision — 2026-09-26

The watch stores the observed offer sender as `sourcePhoneNodeId` alongside the
independent transient package. A duplicate from a different phone node cannot
claim that package. Finalization must address the saved phone owner when one
was recorded. Older ownerless local records remain readable; native delivery
must always provide the observed node. The Android phone retains the permanent
request/import record, while the watch retains one active package/result until
its exact persisted-import receipt; no date-keyed download entry is created.

Request, watch acknowledgement and cancellation use the request-scoped paths
`/quick-start/request/{requestId}`, `/quick-start/ack/{requestId}`, and
`/quick-start/cancel/{requestId}`. Cancel/Dismiss uses request revision + 1.
The watch serializes it with Start: a READY cancellation writes a terminal
tombstone; STARTING wins against cancellation and cannot be erased. Same-ID
retries return the terminal decision. Recent dismissed/cancelled requests are
kept through the request's possible replay window even after a newer offer;
new requests are refused if the 64-record history is full. An expired READY
offer can be cleared, but STARTING remains for explicit recovery and results.
The phone may remove its request Data Item after the matching terminal watch
acknowledgement has been persisted locally; the watch/phone may remove a result
Data Item only after the phone persists import and the watch stores its exact
receipt. Neither transport acceptance nor local timeout proves import.

Native delivery must bind every inbound message to the observed Data Layer
node, the selected node/capability and its exact request path. Schema 1 is
supported now. Future schema 2 peers advertise it only after both codecs and
stored-record migration exist; 1+2 peers fall back to 1 with a 1-only peer,
and 2-only/1-only peers refuse. No older app is assumed compatible from its
package ID alone. Headless rules and migration fixtures are present. Iteration
25 supplies native publication, listeners, and guarded Data Item deletion;
paired-device proof remains open.

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

#### Phase 0 cue contract — 2026-09-26

The persisted preference defaults to voice off. When the user opts in, all four
categories default to on and can then be disabled separately. System TTS voice
and language are used; if that locale, engine, output route, or audio focus is
unavailable, skip speech while retaining the visual state and haptic. TalkBack
owns speech when active, so the cue adapter suppresses simultaneous TTS and
provides the same concise accessibility label. The adapter must discover TTS
services, initialize asynchronously, cancel obsolete utterances on route or
focus change, and shut down on leaving the foreground session. These Android
adapter behaviors remain Phase 2 implementation/device checks.

`WatchCueScripts` fixes the first-version wording: a sanitized, 140-character
maximum briefing of **“<exercise>. <sets> set(s) of <prescription> [at <load>].”**;
**“Rest for <duration>. [Up next: <exercise>, <target>.]”** above ten seconds;
**“Next: <exercise>.”** at six through ten seconds only if its estimated speech
fits before the final five; no rest/preview speech at zero through five;
**“Starting in five seconds.”**; **“Go.”**, **“Go. Set <n>.”**, or
**“Go. <exercise>.”**; **“Exercise complete.”**; and
**“Workout complete. Great work.”** Control characters and invisible format
characters are removed. Unknown user prescriptions remain bounded plain text;
the adapter uses the system language and does not invent coaching instructions.

Cue IDs include the session, transition revision, exercise/set index, kind and
deadline threshold. Terminal success outranks Go, Go outranks the warning, and
informational speech is lowest. The cue controller owns one active utterance;
pause, start now, restart, end, skip and navigation cancel obsolete speech.
The success cue ledger records a key with the session transition before speech
and retains it through recovery, then prunes only after result acknowledgement.
The pure ledger and priority rules are tested; runtime atomic persistence and
TTS wiring remain Phase 2 work.

`RestCountdownLock` stores an interval ID, deadline and irreversible
`finalCountdownStarted` flag. A new rest creates a new unlocked interval. At
more than 5,000 ms left, an extension may move the deadline. At 5,000 ms or
less the lock latches before a warning; no later extension changes the
deadline. Pause/resume retains the lock, including after serialization and
recovery. Start now and natural zero cancel the warning and finish once.
The pure boundary/race fixture passes; `SessionViewModel` and UI enforcement
remain in Phase 2's explicit checklist item.

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

### Progress audit — 2026-10-04, Iteration 64

Checklist items are counted equally for a reproducible completed/total view;
the ratio is not an engineering-effort estimate. The working tree currently
stands at **70/97 items (72%) overall**:

| Phase | Completed/total | Status |
|---|---:|---|
| Phase 0 — foundations | **31/31 (100%)** | Code and headless contract decisions complete; native runtime and paired-device checks remain in later phases |
| Phase 1 — phone feature | **10/10 (100%)** | Code, headless checks, and browser UI fixture pass; paired-device delivery belongs to later phases |
| Phase 2 — watch feature | **17/17 (100%)** | Code, headless checks, and the round-emulator presentation exit check pass; physical accessibility/audio behavior remains Phase 4 device evidence |
| Phase 3 — integration/recovery | **12/12 (100%)** | Paired restart/Start, Cancel/Dismiss, offline reboot, exact receipt/cue pruning, legacy sync and the full fresh native replay/binding/capability/cleanup matrix pass; physical acceptance stays Phase 4 |
| Phase 4 — device acceptance | **0/27 (0%) physical** | Phone states, ambient/process recovery, native TTS/TalkBack/fallback, reduced-motion rest/success and voice-enabled UI interruption/rest/transition/recreation pass on emulators; physical audio, routing, tactile and battery evidence remain open |

The earlier committed checkpoint, `7596256`, represents **22/96 items (23%)**.
Iteration 14's earlier uncommitted status is superseded by that commit.
Iteration 15's shared result/receipt wire contract is committed in `61edf55`.
Iteration 16 is committed in `085e39e` and closes eight Phase 0 decisions with cue/rest boundary fixtures,
observed phone ownership, and terminal replay history. Phase 0's
100% must not be reported as feature
completion: there is no usable phone-to-watch Quick Start path yet.
Iteration 17's native phone bridge is committed in `e178b74`. Iteration 18
adds the React sheet and entry points with headless status fixtures; its visible
browser check is open because the in-app browser blocked the local Vite URL.
Iteration 19 audits recovery: the phone now exposes its latest durable offer,
React restores it before enabling Quick Start entry points, pending/Ready offers
block a second send, and conflicting equal-revision acknowledgements are stale.
Its 74 shared and eight phone tests, React build and debug APK pass. The visible
fixture passes 18 checks on the configured Vite port; Library and a scheduled
Today row show no browser Quick Start send at 390px, with no horizontal overflow.
This supersedes Iteration 18's local-browser block. Phase 1's two remaining
browser checks close; watch/paired-device acceptance remains open.

Preparation order for the next parts:

1. Build the isolated Phase 1 phone bridge and React sheet.
2. Build the Phase 2 watch receiver, ready prompt, cue controller, and success
   UI using the persisted reducer output.
3. Complete Phase 3 recovery/regression wiring before Phase 4 physical-device,
   Play internal-track, audio-routing, and battery acceptance.

### Phase 0 — Domain, persistence, decisions, and contract fixtures

Status: **Headless code/contract foundations complete; native wiring and device proof belong to later phases**

- [x] Confirm entry points: single exercise, current Library playlist, explicit
      Library multi-select, and Today row.
- [x] Confirm the existing 24-item maximum and all-or-nothing rejection policy.
- [x] Confirm pending-offer policy: reject a second request with
      `pending_request` in the first release.
- [x] Define `WatchSessionPackage`, phone ownership, transient watch retention,
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
- [x] Confirm voice defaults: explicit opt-in, all four cue categories enabled
      after opt-in, and system TTS language.
- [x] Confirm the exact start/rest/exercise-success/workout-success scripts and
      maximum spoken-brief length.
- [x] Define cue-event keys, priority, cancellation, and recovery behavior.
- [x] Confirm the final-countdown lock: extensions allowed above five seconds,
      atomically disabled at five seconds, lock retained through pause/recovery.
- [x] Define request/ack models and non-regressing revision-based state ordering.
- [x] Define cancellation races, revisions, node binding, Data Item paths and
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
      TalkBack text. Runtime session wiring is complete; dedicated success UI
      rendering remains open.
- [x] Persist ordered reducer outcomes and revision atomically in an isolated
      Wear store; verify restart/replay, invalid-state preservation, identity/plan
      conflicts, concurrent transitions, and storage failures. A real
      Preferences DataStore file recovery test passes. Runtime wiring and
      native final-result/receipt integration are complete; device recovery
      validation remains open.
- [x] Define a shared v1 capability envelope and pure schema negotiation with
      reachable-node/role binding. The current helper includes only implemented
      request schema 1. Missing, malformed, unsupported, and mixed-version
      fixtures pass; both peers now publish it, while paired mixed-version
      validation remains open.
- [x] Persist completed and ended Quick Start summaries until an exact phone
      receipt; prune package/outcome/result in a restart-safe sequence and
      retain bounded replay tombstones. Headless real-file and write-failure
      fixtures pass. Native transport is wired; paired recovery remains open.
- [x] Share final-result and persisted-receipt models/codecs with exact observed
      sender, target node, request/plan, path and revision validation. Preserve
      legacy disk records and immutable compacted receipt replay; native phone
      import and final-result transport wiring are complete. The request
      receiver persists the observed originating phone node.
- [x] Define short-rest cue scripts, TalkBack/audio-output behavior, TTS
      lifecycle, manifest discovery, and deterministic spoken formatting.
- [x] Record the battery/runtime budget and headless-versus-device test split.
- [x] Add the first shared request/acknowledgement validation, transient-package
      conversion, ordering, serialization, and status-transition tests.

Exit checks:

- [x] `:shared:test` passes (77 tests at Iteration 25).
- [x] Empty, oversized, partially invalid, reordered, expired, clock-skewed,
      duplicate, cancelled, out-of-order, and unsupported-schema fixtures pass.
- [x] Shared capability negotiation and mixed phone/watch-version fixtures
      pass, including schema-1 fallback and incompatible schema refusal.
- [x] Contract fields and chosen decisions are recorded in this document.

### Phase 1 — Phone bridge and React feature shell

Status: **Code, headless checks, and browser UI checks complete; paired-device delivery remains in later phases**

- [x] Create the isolated React feature directory.
- [x] Add the Quick Start sheet with one-or-many item editing, ordering, and
      prescription validation.
- [x] Create the separate Capacitor plugin/client/receipt store. The phone
      persists requests/watch acknowledgements and now atomically imports final
      results with their immutable receipt; paired recovery remains in Phase 3.
- [x] Add platform/plugin gating without changing browser-to-watch claims.
- [x] Add mocked bridge tests for every phone-visible status.
- [x] Integrate Library and Today through narrow props/callbacks.

Exit checks:

- [x] `npx tsc --noEmit` passes.
- [x] Focused browser fixture passes (19 checks on 2026-09-27, including the
      explicit pending/Ready cancellation action).
- [x] Browser UI never offers a non-functional send action (Library and a
      scheduled Today row inspected at 390px).
- [x] `App.tsx` does not acquire feature-specific effects or handlers.

### Phase 2 — Watch receiver and ready prompt

Status: **Complete — code, headless checks, and round-emulator presentation exit check pass; physical behavior remains Phase 4 evidence**

- [x] Implement listener, coordinator, receipt client, and prompt.
- [x] Persist accepted requests through the transient package store, leaving the
      permanent date-keyed download store unchanged.
- [x] Handle active-session, invalid, expired, and duplicate requests.
- [x] Add Start, Dismiss, and Cancel acknowledgements and serialized races.
- [x] Add the isolated cue controller, TTS adapter, preferences, and cue ledger.
- [x] Add the foreground-only five-second start state and cancellation path.
- [x] Add rest-duration, five-second, and Go cue events without changing the
      persisted rest deadline.
- [x] Add the persisted final-countdown lock and enforce it in both UI and
      `SessionViewModel.onAddRestSeconds`.
- [x] Add exercise-success and final workout-success presentations.
- [x] Add notification permission/fallback behavior without making notification
      permission a requirement for opening the in-app ready prompt.
- [x] Add immutable final summary and await durable local result enqueue before
      rendering **Saved on watch**.
- [x] Add TTS discovery/lifecycle, audio-output/focus, TalkBack, ambient, and
      reduced-motion handling.

Iteration 32 completes the TTS discovery/lifecycle and runtime locale,
audio-output, and focus-reporting portion. Iteration 33 completes explicit
ambient/reduced-motion handling and retains TalkBack semantics/TTS suppression.
Device TalkBack/audio/ambient behavior remains acceptance evidence, not code
closure.

Exit checks:

- [x] Wear JVM tests cover all receipt outcomes and duplicate delivery.
- [x] Cue tests cover exact scripts, threshold crossing, priority, cancellation,
      pause/resume, TTS failure, process recovery, and exactly-once completion.
- [x] Rest-boundary tests cover extension accepted at six seconds, rejected at
      five seconds, simultaneous tap/threshold ordering, Start now cancellation,
      and no warning/Go speech overlap.
- [x] Existing session/repository tests still pass.
- [x] Round-screen preview/emulator shows readable Start/Dismiss, five-second
      countdown, exercise-success, rest, and final-success states.

### Phase 3 — End-to-end integration and recovery

Status: **Complete — automated gates, legacy sync and full fresh paired-emulator matrix pass; physical evidence stays Phase 4**

- [x] Wire acknowledgement events to the phone plugin and React hook.
- [x] Reconcile status after phone process death/app resume.
- [x] Verify watch process death between ready and Start.
- [x] Verify phone cancellation/dismissal behavior.
- [x] Verify request/ack Data Item cleanup, stale replay, node binding, and
      mixed-version capability gating.
- [x] Verify completion stays queued through watch reboot until phone ACK, then
      transient package/cue data is safely pruned.
- [x] Preserve **Send today to watch**, scheduled downloads, log sync, and live
      watch session status without contract regression.

Exit checks:

- [x] Browser regression fixtures all pass.
- [x] Shared, phone, and Wear JVM tests pass.
- [x] `npm run cap:sync` passes.
- [x] Clean phone and Wear debug APK builds pass.
- [x] `git diff --check` passes.

### Phase 4 — Paired-device acceptance

Status: **Emulator acceptance underway at user request; physical 0/27 remains open**

Track emulator UI observations separately in
[Device acceptance](DEVICE_ACCEPTANCE.md) and the latest implementation iteration.
These physical checklist boxes are not closed by emulator-only evidence.

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

**Current checkpoint:** Phase 0 is complete with headless evidence. The Android
phone now has a registered Quick Start Capacitor bridge, capability-gated Data
Layer sender, and durable request/watch-acknowledgement store. Iteration 19 adds
latest-offer recovery, pending-offer enforcement and strict revision replay.
Its eight phone tests and debug APK build pass. React has an isolated editing
sheet, Library single/playlist/selection and Today-row entry points, plugin
gating, mocked status fixtures, and a restored Today status. TypeScript, Vite,
Node fixtures, Capacitor sync and the Android debug APK pass. The browser
fixture passes 18 checks on the configured Vite port; Library and a scheduled
Today row show no browser send control at phone width. Iteration 20 registers an
observed-sender request listener and receipt client. It checks path, sender,
target, schema and expiry; the existing transient DataStore persists a package
before Ready is sent. Eight new coordinator fixtures pass (108 Wear tests total)
and the Wear debug APK builds. Capability publication remains gated until the
watch has a usable Start/Dismiss prompt. Session integration, native
final-result acknowledgement, and cues remain open.
Iteration 21 wires both offer acceptance and downloaded-workout initialization
through the process-wide gate. Invalid offers are classified before the active
session check; active or paused legacy sessions block offers, including duplicate
Ready replay from an older inconsistent state. The existing session screen now
shows a block reason instead of starting a downloaded workout while Quick Start
owns the slot. Concurrent offer/start, blocker, and existing session tests pass:
113 Wear tests and the debug APK build. The gate does not yet start a Quick Start
runtime session; that remains with the prompt and Start path.
The global start gate is wired into production offer admission and downloaded
session creation. Iteration 24 adds the durable Ready prompt, wires Start and
Dismiss receipts, and adapts the existing session engine to the transient
runtime without copying the offer into the date-keyed download store. Explicit
session exit now awaits its pause write. Iteration 25 adds revisioned phone
Cancel, native final-result transport/import/receipt cleanup, retry after
ambiguous transport, and phone/watch capability publication.
The shared progress/completion summary now includes explicit pending counts.
A pure Wear reducer produces it and derives compact **completed/total** text,
and Iteration 11 persists its ordered outcomes/revision atomically with tested
recovery and failure behavior. Iteration 24 connects those outcomes to the
runtime session engine; Iteration 29 adds the recoverable exercise-success and
final workout-success presentations. Completed
and ended outcomes are frozen and retained, and Iteration 25 wires their native
sender/receiver, permanent phone import, exact receipt, and guarded cleanup.
Iteration 12 adds a shared capability envelope and schema-1 negotiation with
mixed-version fixtures; Iteration 25 publishes it from both peers. Iteration 28
wires the rest transition cues and durable final-five lock. Iteration 29 adds
persisted success presentations, completion cues, and the durable **Saved on
watch** boundary. Iteration 30 adds the permission-aware Ready notification and
durable in-app fallback. Iteration 31 adds persisted voice-cue preferences, a
one-time opt-in prompt, per-category settings, non-blocking TTS-service
availability reporting, failed-write rollback, and process-wide cue-store write
serialization. The latest clean suites have 78 shared, 12 phone, and 183
Wear passing tests plus both debug APK builds. Iteration 32 adds runtime TTS
initialization, locale, audio-route, and focus reporting with route-listener
lifecycle cleanup. Its latest clean suites have 78 shared, 12 phone, and 187
Wear passing tests plus both debug APK builds. Iteration 33 adds the shared
ambient/reduced-motion policy, static low-power layouts, burn-in offsets, and
foreground countdown cancellation. Its clean suites have 78 shared, 12 phone,
and 191 Wear passing tests plus both debug APK builds. The
454x454 emulator renders the saved-workout list and a persisted Ready prompt;
the Started path is covered by runtime adapter tests, but paired Quick Start
delivery and physical-device acceptance remain open. No emulator was opened for
Iteration 33. Iteration 34 closes the round-emulator presentation gate:
Ready/Start/Dismiss, the five-second countdown, rest, exercise-success,
final-success, active ambient, final ambient, and countdown cancellation on
ambient entry were readable on the round Wear OS 7 AVD. It also reconciles
Phase 3 with code already delivered in Iterations 19 and 25: native
acknowledgement events reach the React hook, and durable status restores and
reconciles after recreation and resume. The clean recount is 78 shared, 11
phone, and 191 Wear tests; the earlier 12-phone summary is a documentation
count superseded here, not rewritten historical evidence. The full automated
Phase 3 baseline passes, but paired Quick Start delivery, process/reboot
recovery, cancellation races, cleanup, and physical-device acceptance remain
open.
**Current phase:** Phase 4 — Paired-device acceptance.
**Exact next action:** continue on the isolated phone/watch emulators as directed
by the user. Validate voice-enabled ambient/background cancellation and deadline
recovery with actual native playback/silence and exact saved progress/ledger.
Iteration 64 closes natural voice-enabled short-rest/exercise-transition
ordering, native terminal playback and recreation without replay, fixing stale
TTS callback ownership. Iteration
63 closes actual Pause/Start now/Back cancellation, exact saved-rest recovery
without replay and safe paused End/partial receipt acceptance. Iteration 62 closes native output/controller
interruption, replacement, focus-loss/denial and language fallback/restoration.
Iteration 61 closes actual
foreground availability, category/voice/TalkBack suppression, unavailable-
engine fallback and reduced-motion rest/success presentation. Iteration 60 closes
actual countdown ambient cancellation/retry, active-set screen-off and staged
exercise/final-success fresh-process recovery before the phone receipt.
Iteration 59 closes short-rest durable cue sequences, success Activity
recreation and actual ambient rest recovery, correcting the foreground timer
gate. Iteration 58 closes
open/reopened phone Start and disconnected/active/duplicate/expired entry
behavior, including the evidenced rejection and expired Cancel corrections.
Iteration 57 closes
connected Library single/playlist/reordered-selection and Today entry with
exact durable phone/watch prescription comparisons and UI cancellation.
Use [Device acceptance](DEVICE_ACCEPTANCE.md) for inventory and UI reproduction;
continue emulator lifecycle/cue cases while recording physical limits separately.
Iteration 55 closes the
fresh native matrix using isolated AVD copies without ending the original
active workout; Iteration 53 covers legacy sync and Iteration 54 covers native
binding rejection. No physical-device evidence is claimed. Reproduction
commands are in [Paired emulator validation](PAIRED_EMULATOR_VALIDATION.md).
Offline isolation requires airplane mode plus closed adb bridge sockets;
ordinary Wi-Fi disable is automatically reversed by this Wear runtime.
The Windows companion is already installed;
Iteration 35's missing-companion blocker remains historical Mac evidence.
Audit TalkBack ordering and audio routing on a physical device when available.
Keep Send gated until native discovery confirms a reachable, compatible watch;
transport acceptance cannot display Ready on watch.

At the end of every future session, update this marker, the relevant phase
checkboxes, and `IMPLEMENTATION_PROGRESS.md` with commands/results and the exact
next file or test. Never mark a phase complete from code presence alone.
