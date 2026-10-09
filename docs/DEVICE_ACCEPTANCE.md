# Quick Start device acceptance

Physical continuation — 2026-10-09, Iterations 171–184: Stage 19 is
**87/97 (90%)**; physical acceptance is **17/27**, with **10 checks open**.
Four newly completed criteria: exact start briefing, one prescribed rest
announcement despite extensions, final-five extension lock, and one warning/Go
at the transition. User confirms every cue worked perfectly through TRUEFREE O1
with YouTube left playing; no repeats, overlaps or lock sounds. Actual phone
focus loss/gain, six completed speech callbacks, YouTube PLAYING afterward,
service teardown, owned 2/2 result/receipt/pruning and preserved data
corroborate that report. Keep prior masked speech and missed-timing attempts as
failures.

Paired Quick Start now routes speech through a bounded phone media-playback
service armed by foreground Send with phone headphones; the watch owns timers,
haptics and local fallback. The final cancellation/focus cleanup build (345
native tests) and in-place installation pass. Scripted 0/5/8/12-second
short-rest cases complete with expected callbacks, but human no-overlap reports
are pending. Iteration 184's offline result-durability test is open: the watch
debug endpoint must be reconnected before offline captures, reboot retention
and phone acknowledgement/pruning can be checked. Recovery, fallback,
short-rest, composite routing/ambient, battery, regression and Play checks
remain open. This supersedes previous current routing/count labels. Play setup:
[internal testing](PLAY_INTERNAL_TESTING.md).

Superseded checkpoint — 2026-10-09, Iterations 166–170 (historical): Stage 19
was **81/97 (84%)**, physical **11/27**, **16 open**. Pending offer recovery
after process death, leaving the countdown without an unseen Start,
voice/category preference persistence and natural request expiry passed. The
short radio-off probe retained a cloud route and stayed inconclusive. That
iteration's locked-phone/watch-debugging blocker and pending remote cleanup were
resolved in Iteration 171. This supersedes the battery deferral below;
historical findings below remain evidence.

Physical validation deferred — 2026-10-08, Iteration 120: user removed Watch7
connectivity to conserve low battery and has no charger. Physical tests and
watch reconnection/radio attempts stop until charged/reconnected. Latest debug
probe failed before a remote shell; no successful radio change or test Send is
recorded, and phone native requests remain exact. No new acceptance item closes:
Stage19 **77/97 (79%)**, physical **7/27**, **20 checks remain**. This supersedes
the pending wake/readiness request. Resume with fresh battery/radio/session
inventory, then disconnected behavior and remaining physical acceptance.


Physical entry checkpoint — 2026-10-08, Iterations 115–119: current five-item
Library playlist arrives with exact order/prescriptions; explicit selected
Bird Dog→Walking reorder and Today Run row11/date/ID/2sets/10min/120s/5kg pass.
Phone Home before watch Start records native Started while UI is backgrounded;
normal reopen visibly reconciles the exact short Walking request. All owned
Ready offers cancelled; short test completes1/1 with actual result/receipt and
pruning. Saved phone database/playlist and watch legacy/settings compare exact.
Mis-scoped first selection click is corrected through normal UI with exact
baseline restoration; failed/asleep/non-atomic captures stay documented.
Stage19 **77/97 (79%)**, physical **7/27**, **20 checks remain**. No app source
or APK change in this batch. Earlier counts/next labels are superseded here.
Next: disconnected wording, active nonreplacement, duplicate/expiry/pending
restart and unsynced reboot; physical cue/audio/accessibility/ambient, measured
60–90min battery and Play/mixed-version acceptance remain open.


Physical checkpoint — 2026-10-08, Iterations 113–114: normal Library Walking
Ready and open-phone Started pass on S25/Watch7. Natural screen sleep reproduces
an unwanted APP_CLOSED pause; corrected ON_STOP distinguishes noninteractive
screen sleep from deliberate exit. 231 Wear tests and both Wear APK targets
pass; in-place update preserves all seven watch stores. Real Dozing retains
exact running deadline, Home still pauses, indicator returns to exact session,
and wake completes1/1 with actual phone result/receipt, pruning and ID3 removal.
Phone rows/logs/events/set logs and watch legacy/settings remain exact; changed
playlist draft is retained and documented separately. Stage19 **73/97 (75%)**,
physical **3/27**, **24 checks remain**. This supersedes earlier locked-phone/
no-device and timeout-pending labels for the validated screen-sleep pathway;
ambient UI/audio/TalkBack/routing, recovery/battery/Play checks remain pending.
Next: physical playlist/reordered selection/Today and reopened-phone Started.


Physical readiness — 2026-10-08, Iteration 113: S25/Watch7 are now connected
through wireless ADB; both installed APK hashes match validated builds and
reciprocal nearby peers pass. Watch is off charger at 45%, notifications granted,
15s screen timeout. Phone/watch baselines retained; no unfinished session found.
The S25 is securely locked, so visible timed UI acceptance awaits user unlock
and watch wear/wake readiness. No test workout sent/started; counts remain
71/97 overall and 1/27 physical. This supersedes the no-device readiness below.


Current progress audit — 2026-10-08, Iteration 108: committed production
checkpoint `5014ec8` and reinstall evidence `857ebe6`; worktree initially clean.
Stage 19 remains **71/97 (73%)**, physical **1/27**, with **26 checks open**.
Today's successful ADB inventory reports no attached devices; yesterday's
connected-pair evidence remains historical, not current readiness. Next device
work is Iteration 105's one-set / 3min / 0s-rest natural timeout/ambient test,
followed by paired result/receipt/indicator cleanup. No reliable completion ETA:
remaining acceptance includes a 60–90 minute battery run, audio/accessibility,
recovery/regression and Play internal installation/update checks. Item ratios
do not estimate remaining time. Iterations 108–112 validate portable collection,
regression coverage and the physical protocol for this tooling/docs checkpoint;
device execution remains blocked. Earlier current/next/uncommitted labels below are
historical and superseded by this audit where they conflict.

User direction for Iteration 56: **use emulator**. Continue platform acceptance
on the dedicated copied phone/watch AVDs. Record emulator results separately
from Phase 4's physical-device exit checks. Audio intelligibility, physical
haptics, Bluetooth routing, battery consumption and Play delivery require their
own evidence; a native fixture or inventory does not establish those results.

## Physical timeout and Ongoing Activity — Iteration 111

Status: **Procedure reviewed; execution pending connected physical devices.**
This is the next device action for Iteration 105. The collector below is a
portable alternative to the PowerShell inventory command. Use explicit current
serials from `adb devices -l`, not yesterday's values. Use a fresh output path
for each capture; an existing path is refused without overwriting evidence.

```sh
python3 scripts/collect_physical_validation.py \
  --adb "$ANDROID_HOME/platform-tools/adb" \
  --phone-serial "$PHONE_SERIAL" --watch-serial "$WATCH_SERIAL" \
  --include-installed-hash \
  --output output/device-validation/physical-timeout-before
```

Exit 0 means physical inventory/app presence, 2 means selected devices or apps
are unavailable, and 1 means failed/refused collection. The report does not
prove signing compatibility or reciprocal Data Layer peers. Inspect the
optional installed/local hashes for both devices; absent local APKs, mismatch
or split installation require separate installation evidence before asserting
the tested build. No install, permission grant, setting change, workout action
or native mutation probe runs in this collector. Connected-device collection
has not yet been executed for the new Python tool.

1. Record current checkpoint, physical model/OS/build/hash evidence, actual
   reciprocal nearby peers, notification permission, AOD/screen timeout and
   battery/charger state. Preserve phone records and watch runtime/package/
   result/cues/legacy stores before any workout action. Retain source data and
   decoded identity/state, not just hashes, for the expected workout changes.
   Refuse replacement if an unrelated unfinished workout or Ready offer exists.
2. Through normal phone Library UI, send one selected timed exercise with
   **one set / 3 minutes / 0 seconds rest**. Record its exact request ID,
   prescription/order and Ready state on both peers. Tap Start on watch and
   observe the five-second gate, active timer and exact Started acknowledgement
   on the phone. Do not inject a synthetic receipt or initialize private stores.
3. With watch off charger, leave it untouched longer than the observed natural
   timeout (previously 15 seconds). Record wall-clock boundaries and system
   lifecycle/ambient evidence. Avoid UI dumps/taps while waiting because those
   may wake or perturb the watch. Wake normally and inspect the same identity,
   saved deadline and running/paused state. If it pauses, preserve the actual
   stop/lifecycle trace before changing runtime policy. An awake screenshot
   alone does not prove ambient behavior.
4. Observe notification ID **3**/Ongoing Activity for this exact unfinished
   workout. If the system exposes an indicator on the watch face, tap it once
   and verify return to the same saved session without a duplicate Start, new
   request or deadline reset. Deliberate Home/back exit may pause by current
   policy; record that separately from natural timeout. Notification denial
   fallback is a separate case, not evidence that an indicator appeared.
5. Observe natural timer expiry in foreground (or its existing one-set wake
   catch-up), one completed set and durable final result. Verify the phone
   retains exactly one matching result, an actual matching receipt reaches the
   watch, runtime/package/cues are pruned as specified, and ID 3 disappears.
   Reopen to check saved summary/no cue replay. Compare all unrelated baseline
   records and settings exactly; retain failed and successful captures apart.

Collect a second inventory in a fresh `physical-timeout-after` directory.
Attach per-stage timestamps, awake screenshots, lifecycle/notification evidence,
exact IDs/results/receipt and preservation assertions. Update only checklist
items whose complete observations pass. Timeout/indicator startup or one
successful workout does not close the 60–90 minute battery, physical audio/
TalkBack/routing, reboot/offline, mixed-version or Play delivery checks.
Stage 19 remains **71/97**, physical **1/27** until execution supplies evidence.

## Current private speech/receipt checkpoint — 2026-10-07

Iterations 92–96 pass five explicit native stages across three actual reboots
on owned `Pasingot_Timed_UI`. Exact paused REST recovery/silence, cancellation
of observed resumed REST, Go after 975 ms, 2/2 offline completion and no late
cues through the abandoned deadline pass. Fresh final state remains exact;
wrong sender/revision/path refuse pruning. Exact synthetic receipt durably
clears runtime/package before injected cue failure; another reboot/exact replay
clears cues, suppresses all six native kinds and prevents resurrection.

Per-stage archives retain speech/haptic/focus and full runtime/cue/receipt/boot
evidence before generic filenames are reused. Final evidence is in
`iteration-96-native-evidence.tar`. All 331 JVM tests/four APK targets, installed
hashes and focus/crash/whitespace checks pass. Ordinary OK (48 tests) verifies
guards with mutation cases skipped; all seven private DataStore files remain
byte-identical under that run. See the [private speech/receipt protocol](PAIRED_EMULATOR_VALIDATION.md#private-native-resumed-rest-speech-and-interrupted-receipt--iterations-9296).

Programmatic actions/private stores/synthetic receipt leave actual phone/Wear
UI, Data Layer cleanup and physical acoustics/haptics/battery acceptance open.
Connected phone inventory is empty; next actual paired timed Start/result/
receipt and resumed-REST UI/transport need a connected pair. Stage 19 remains
70/97, physical 0/27. Original AVDs/workout history are untouched.

## Prior real-clock native checkpoint — 2026-10-07

Iterations 87–91 pass five explicit native stages on the owned disposable
`Pasingot_Timed_UI`: actual TTS availability, natural timed sets/rest cues,
hidden Activity expiry/success non-replay, paused preparation and actual reboot
recovery. Warning/Go native observations are -4,699/+287 ms relative to the
rest deadline; resumed exact 16,916 ms yields native success +249 ms after
expiry. Full private runtime/cues, real-clock reads, native speech/haptic/focus
and changed-boot evidence are retained in `iteration-91-native-evidence.tar`.
Caller-time and charging-overlay failures are preserved; exact transition
measurement and awake retry pass without production changes.

All 331 JVM tests/four APK targets and installed hashes pass; ordinary runner
OK (44 tests) verifies guard/discovery with mutation cases skipped and all
private file hashes preserved. Native output releases focus; final boot has no
fatal crash. Paired phone inventory is empty. Actual paired timed Start/result/
receipt/package/cue pruning, resumed-REST speech interruption and physical
acoustics/haptics/battery remain open. Stage 19 stays 70/97, physical 0/27.
See the [native real-clock protocol](PAIRED_EMULATOR_VALIDATION.md#native-wall-clock-timed-sets-and-speech--iterations-8791).

## Prior controlled-clock native checkpoint — 2026-10-07

Iterations 82–86 pass current timed round-UI acceptance and four private native
runtime/engine stages across three actual reboots on a fresh workspace-owned
`Pasingot_Timed_UI` AVD. Full paused/hidden/outcome/result snapshots, receipt
and boot/PID markers are retained in ignored validation output. Both installed
Wear APK hashes match current builds; 331 JVM checks and all four APK targets
pass. Ordinary Wear instrumentation reports OK (39 tests); guarded opt-in
mutation cases skip without flags. See the [explicit staged protocol](PAIRED_EMULATOR_VALIDATION.md#isolated-native-timed-runtime-reboot-fixture--iterations-8385).

Controlled clock, NoOp cue output, synthetic initialization/receipt and private
DataStore keep this evidence separate from actual paired Ready/Start, natural
wall-clock timing, native speech, package/cue pruning and physical acceptance.
Stage 19 remains 70/97, physical 0/27. Actual paired timed transport and native
cue/timing validation, then resumed-REST speech and physical checks remain next.
Original AVDs/workout history are not opened for mutation.

## Record the tested installation

Run from the repository root with the Android SDK's adb available. The collector
reads device properties, package versions and battery snapshots. It writes only
ignored local evidence; it never installs, resets, reboots or grants permission.

```powershell
.\scripts\collect-device-validation.ps1 -AllowEmulators -PhoneSerial emulator-5556 -WatchSerial emulator-5554 -IncludeInstalledApk -Label emulator-baseline
```

Omit `-AllowEmulators` for physical inventory. Supply both serials when discovery
is ambiguous. `inventory.json` records Git HEAD/working-tree state, model, OS,
API, emulator identity, app versions and local debug APK hashes. The optional
installed-base APK hash can be compared with the corresponding local build.
Split APK details remain in `package.txt`; base hashing alone does not compare
all splits. Inventory readiness does not establish Data Layer connectivity or
acceptance. Capture another inventory when the tested installation changes.

## Isolated paired UI regression

Use the copied AVD setup in [Paired emulator validation](PAIRED_EMULATOR_VALIDATION.md).
The UI driver requires `Pasingot_Matrix_Wear`, emulator hardware, explicit
opt-in and the sole exact connected peer. It preserves the original source AVD
disk chains and legacy workout entries. It may end a prior interrupted
**Emulator UI acceptance** fixture through the normal session engine and cancel
its own prior Ready offer. It rejects unrelated runtime or pending state;
it never clears app data. Synthetic completed/ended phone history is retained.

Build both Android test APKs and the current production APKs, then install them
only on these copies. Run the phone waiter first and the Wear driver second in
separate terminals; replace serials/node IDs with the verified current pair.

```powershell
adb -s emulator-5556 shell am instrument -w -e class app.personal.workouttracker.quickstart.PairedQuickStartTransportTest -e quickStartPairedValidation true -e quickStartUiPairedValidation true -e peerNodeId cc1f21d2 app.personal.workouttracker.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5554 shell am instrument -w -e class app.personal.workouttracker.wear.quickstart.PairedQuickStartUiTest -e quickStartUiPairedValidation true -e peerNodeId 3710eec app.personal.workouttracker.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5554 pull /sdcard/Android/data/app.personal.workouttracker/files/ui-acceptance output/emulator-validation/ui-acceptance
```

Require **OK (1 test)** on both peers, regardless of adb exit code. Screenshots
and accessibility snapshots cover Ready, countdown, active session, rest,
final-five-second extension lock, exercise advancement, resume, saved summary
and persisted cue settings. The driver clears the accessibility observation
cache so live countdown semantics are read freshly.

The phone probe creates a two-exercise native transport fixture: A has two sets,
8–10 reps, 12 seconds rest and 2.5 kg; B has one set, 20 seconds and no rest/load.
Actual Wear UI taps cancel a backgrounded countdown, retry Start, complete
sets, extend rest by 5/10/30 seconds, attempt disabled final-countdown controls,
pause/resume, finish and change settings. Assertions require exact deadlines,
no unseen start, phone Started/completion receipts, runtime pruning, retained
summary and unchanged legacy entries. Cue preferences are restored in `finally`.
The phone Library/Today WebView entry workflow and audible cue quality are
outside this fixture's evidence.

## Phase 4 evidence protocol

### Native voice and reduced-motion emulator checks

Iteration 61 adds `NativeVoiceCueAcceptanceTest`, gated by
`nativeVoiceCueValidation=true`, emulator hardware and `Pasingot_Matrix_Wear`.
It wraps the actual Android output to count speech/haptic calls, using isolated
serialized cue persistence rather than changing production session/preferences.
Each of six cue kinds is emitted enabled, category-disabled and voice-disabled;
controller/store recreation must reject each duplicate. Short `Go.` utterances
exercise native completion; they do not establish prescription intelligibility.

```powershell
adb -s emulator-5554 shell am instrument -w -e class app.personal.workouttracker.wear.cues.NativeVoiceCueAcceptanceTest -e nativeVoiceCueValidation true -e expectTalkBack false app.personal.workouttracker.test/androidx.test.runner.AndroidJUnitRunner
```

For actual TalkBack suppression, record original accessibility settings, enable
the installed TalkBack service only on the verified copy, then pass
`expectTalkBack=true`. The test uses
`UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES` and requires an actual
enabled TalkBack package plus native touch exploration. Restore settings before
the normal UI flow. Record the installed TTS package's original enabled state;
temporarily disable it only on the copy and pass `expectUnavailableVoice=true`
to verify SERVICE_UNAVAILABLE discovery, initialization failure and fallback.
Restore the exact original package state afterward. Evidence is written under
external files `voice-acceptance/{foreground,talkback,unavailable}.txt`.

Run the existing paired `serveProductionTransportProbe` and
`countdownRestAndRecoveryThroughRealUi` methods with their usual UI/peer flags
and add `reducedMotionValidation=true` on both peers. With TTS disabled, also
pass `unavailableVoiceUiValidation=true` on Wear. The driver enables voice for
the fallback run, restores preferences, sets actual system animator scale zero
at validation points, verifies Activity policy/decorative-progress suppression,
and restores the original scale. Automation/lifecycle transitions can reset
the scale, so assertions wait for the real observer rather than injecting policy.
Rest extension deadlines, final-five locks, exercise/final success, receipts,
legacy entries and category preference recreation must still pass. The phone
checks exact prior records and one new result/receipt; only an explicitly owned
interrupted UI fixture may be normally ended/cancelled before the new baseline.
Before/after records are retained under `presentation-acceptance/REQUEST_UUID`.

Require **OK (1 test)** for each explicit run. Ordinary instrumentation skips
these mutation checks without flags. Retain failures as historical evidence;
review rest/success/fallback screenshots and audit source disks after shutdown.
Physical speech quality, tactile delivery, routing and battery remain separate.

### Actual voice-enabled interruption UI

Iteration 63 adds `voiceEnabledCancellationThroughRealUi`, guarded by
`quickStartSpeechUiPairedValidation=true`, emulator hardware and exact copied
AVD names on both peers. It requires the sole exact peer, rejects unrelated
runtime/Ready/downloaded active workouts, and may normally end/cancel only a
prior **Emulator UI speech** fixture before taking a fresh baseline. No app
data/history reset is permitted. Existing production APKs need no reinstall
when their inventory hashes still match; build/install both current test APKs
on the copies. Start the phone waiter before Wear:

```powershell
adb -s emulator-5556 shell am instrument -w -e class app.personal.workouttracker.quickstart.PairedQuickStartTransportTest#serveProductionTransportProbe -e quickStartPairedValidation true -e quickStartSpeechUiPairedValidation true -e peerNodeId cc1f21d2 app.personal.workouttracker.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5554 shell am instrument -w -e class app.personal.workouttracker.wear.quickstart.PairedQuickStartUiTest#voiceEnabledCancellationThroughRealUi -e quickStartSpeechUiPairedValidation true -e peerNodeId 3710eec app.personal.workouttracker.test/androidx.test.runner.AndroidJUnitRunner
```

Require **OK (1 test)** on each peer. The synthetic two-exercise fixture has
5/1 sets, 60/0 seconds rest and no load. Actual Start/countdown, Complete set,
Pause, Resume, Start now, Android Back, Workouts Resume and End confirmation
drive all transitions. Read-only reflection follows the existing navigation
ViewModelStore to the on-screen production native TTS owner; it neither injects
an output/listener nor adjusts speech rate/scripts. Native isSpeaking and
production focus must still be active immediately before interruption. Pause/
Back require stopped output and released focus; Start now adds exactly one GO
reservation then returns idle. Back preserves exact running rest/deadline and
reopening stays silent without a new reservation. End exists only while paused:
Pause cancels the fresh spoken rest, confirmation preserves the paused state,
and End saves 2/6 sets as endedSummary with no completed summary/success replay.
The phone receipt must prune runtime/package; prior records/legacy entries
and restored cue preferences must match exactly.

Pull Wear `ui-acceptance/speech-REQUEST_UUID` and phone
`speech-ui-acceptance/REQUEST_UUID` from their external app files. Retain
native focus/JSON witnesses and round screenshots, review paused/list/End
presentation, run ordinary instrumentation with flags absent, inventory the
installation and audit all 16 original source disks after copied-AVD shutdown.
Playback/focus assertions establish emulator behavior; measured action latency,
physical intelligibility/overlap, routing, tactile, battery and Play remain
separate evidence. Iteration 62's direct native END interruption remains the
in-flight native End evidence; this method validates the actual paused UI path.

### Voice-enabled natural rest and exercise transitions

Iteration 64 adds `voiceEnabledShortRestsAndTransitionsThroughRealUi`, guarded
by `quickStartVoiceMatrixUiValidation=true`, emulator hardware, exact copied
AVD names and the sole exact connected peer. Build/install the current production
Wear APK and both test APKs on the copies. Existing phone production installation
may remain when its inventory hash matches. The driver rejects unrelated
runtime/Ready/downloaded active workouts; it may normally end/cancel only its
prior **Emulator voice matrix** fixture before establishing a fresh baseline.
Never reset data/history. Start the phone waiter before Wear:

```powershell
adb -s emulator-5556 shell am instrument -w -e class 'app.personal.workouttracker.quickstart.PairedQuickStartTransportTest#serveProductionTransportProbe' -e quickStartPairedValidation true -e quickStartVoiceMatrixUiValidation true -e peerNodeId cc1f21d2 app.personal.workouttracker.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5554 shell am instrument -w -e class 'app.personal.workouttracker.wear.quickstart.PairedQuickStartUiTest#voiceEnabledShortRestsAndTransitionsThroughRealUi' -e quickStartVoiceMatrixUiValidation true -e peerNodeId 3710eec app.personal.workouttracker.test/androidx.test.runner.AndroidJUnitRunner
```

Require **OK (1 test)** on each peer. The fixture has eight two-set exercises
with 0/3/5/6/8/10/12/20-second rests and all voice categories enabled. Real
Start and Complete set controls drive the session; each countdown expires
naturally. Same-exercise rests <=10 seconds omit REST; longer rests require
REST/FIVE_SECONDS/GO. Exercise transitions reserve EXERCISE_SUCCESS before
warning/Go, with brief success allowed to be preempted at <=5-second rests.
Read-only sampling correlates the production controller's active key with
native TTS isSpeaking every 20 ms, retaining changes in `native-timeline.tsv`.
It never replaces output/listener/scripts or changes speech rate. Require
native playback for expected rest/warning/Go, permitted success playback,
deadline-bound keys and no warning/Go before their thresholds. Native queue
and focus must become idle between phases. Final success must reach playback,
save all 16 sets and a phone receipt, prune runtime/package/ledger, and remain
silent with exact terminal state after Activity recreation.

Pull Wear `ui-acceptance/voice-matrix-REQUEST_UUID` and phone
`voice-matrix-acceptance/REQUEST_UUID` from external app files. Preserve
`checks.txt`, native timeline, result/receipt, runtime/cue snapshots and round
screenshots. Compare every prior phone record and legacy-entry/preference JSON;
require exactly one new result/receipt. Run ordinary instrumentation without
mutation flags, inventory installed APKs, and audit all 16 original source disks
after copied-AVD shutdown. Retain failed runs as historical evidence.

Iteration 64's first observed five-second transition exposed an old success
stop callback completing/unfocusing the replacement warning. The production
adapter now matches callback IDs, completes once, and uses a unique ownership
token so old completion/cancellation cannot stop or unfocus a newer cue.
Cancellation explicitly finishes the old operation because its listener may
already have been replaced. JVM stale-ID/duplicate checks and the native
interruption/focus/language suite accompany the natural paired matrix. Timeline
samples establish emulator playback/controller ordering, separately from
physical intelligibility/overlap, routing, tactile, battery and Play acceptance.

### Voice-enabled ambient and background recovery

Iteration 65 adds `voiceEnabledAmbientAndBackgroundRecoveryThroughRealUi`,
guarded by `quickStartVoiceLifecycleUiValidation=true`, emulator hardware,
the exact copied AVD names and sole exact connected peer. Use the current
production APKs and both test APKs on those copies. Only the owned **Emulator
voice lifecycle** fixture may be normally ended/cancelled before a fresh
baseline; preserve unrelated runtime, all history and original AVD disks.
Start the phone waiter before Wear:

```powershell
adb -s emulator-5556 shell am instrument -w -e class 'app.personal.workouttracker.quickstart.PairedQuickStartTransportTest#serveProductionTransportProbe' -e quickStartPairedValidation true -e quickStartVoiceLifecycleUiValidation true -e peerNodeId cc1f21d2 app.personal.workouttracker.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5554 shell am instrument -w -e class 'app.personal.workouttracker.wear.quickstart.PairedQuickStartUiTest#voiceEnabledAmbientAndBackgroundRecoveryThroughRealUi' -e quickStartVoiceLifecycleUiValidation true -e peerNodeId 3710eec app.personal.workouttracker.test/androidx.test.runner.AndroidJUnitRunner
```

Require **OK (1 test)** on both peers. Four Lifecycle sets and one Final exercise
use 12/0-second rests with all cue categories enabled. Actual Sleep interrupts
native REST and EXERCISE_SUCCESS; actual Home interrupts REST and catch-up GO.
Read the existing on-screen production controller/TTS and current native focus;
never replace output, listener, scripts or session transitions. After each
interruption, require native/controller idle and released focus within the
1.5-second post-transition observation bound. This is not end-to-end action
latency or an acoustic measurement. Poll exact saved runtime/ledger and native
silence every 100 ms until each hidden deadline plus 1.5 seconds. Wake or
reorder the same Activity to the foreground; require exactly one deadline-bound
Go and unchanged progress/outcomes/set. Home during live Go must durably pause
set 3; reopening and real Resume must not reserve/replay it.

Observe Start now's actual Go before awaiting idle and completing the next
set: ACTIVE persistence precedes asynchronous cue dispatch, so a transient idle
sample alone does not establish completion. Observe final native success,
5/5 saved sets, one receipt and runtime/package/ledger pruning. Pull Wear
`ui-acceptance/voice-lifecycle-REQUEST_UUID` and phone
`voice-lifecycle-acceptance/REQUEST_UUID`. Retain native focus dumps, hidden
power/Activity witnesses, snapshots, screenshots and `checks.txt`; compare
every prior phone record and exact original legacy-entry/preference JSON.
Run ordinary instrumentation with flags absent, inventory installed APKs and
audit original source disks after stopping only verified copies. Preserve
failed-run evidence. Physical speech quality, routing, tactile, battery and
Play acceptance remain separate.

### Voice-enabled fresh-process success recovery

Iteration 66 adds `prepareVoiceEnabledProcessSuccessRecovery` and a voice mode
for the existing three staged lifecycle recovery methods. Every stage requires
`quickStartVoiceProcessUiValidation=true`, emulator hardware and exact copied
AVD identity. Connected stages require the sole exact peer; offline stages
require no native connected nodes. Never reset app data/history or original
AVDs. Preparation rejects existing runtime, pending packages and active legacy
workouts. Its owned **Emulator voice process** fixture has Process A/B, one set
each and zero rest. Run the phone waiter before Wear preparation:

```powershell
adb -s emulator-5556 shell am instrument -w -e class 'app.personal.workouttracker.quickstart.PairedQuickStartTransportTest#serveProductionTransportProbe' -e quickStartPairedValidation true -e quickStartVoiceProcessUiValidation true -e peerNodeId cc1f21d2 app.personal.workouttracker.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5554 shell am instrument -w -e class 'app.personal.workouttracker.wear.quickstart.PairedQuickStartUiTest#prepareVoiceEnabledProcessSuccessRecovery' -e quickStartVoiceProcessUiValidation true -e peerNodeId 3710eec app.personal.workouttracker.test/androidx.test.runner.AndroidJUnitRunner
```

Require OK on each peer. Preparation observes production native exercise-success
speech/current controller kind/focus, then real Pause stops output and saves
the exact paused runtime/cues. Original preferences are retained in the artifact;
voice remains explicitly enabled through all fresh-process stages. Close the
Activity normally, confirm and force-stop a live copied app process, remove
bridge mappings and stop only the verified phone copy. Record the watch's
original airplane setting, enable airplane mode only on its copy and cold
reboot it to clear cached Data Layer discovery. This tests recovery after
durable success and normal Activity teardown, separately from killing speech
in flight. Require no native connected nodes before offline acceptance.
If runner exit already removed its process, launch only the copied app Home
without Resume before confirming/force-stopping a live PID. After reboot,
check for a delayed charging overlay and dismiss it with Home/Wake before UI
acceptance; retain any interrupted attempt and verify its exact saved witness.

Extract REQUEST_UUID from the retained `voice-process-REQUEST_UUID` directory,
then run each method separately; force-stop a confirmed live copied app process
again between the two offline stages:

```powershell
adb -s emulator-5554 shell am instrument -w -e class 'app.personal.workouttracker.wear.quickstart.PairedQuickStartUiTest#recoverExerciseSuccessAndCompleteOffline' -e quickStartVoiceProcessUiValidation true -e lifecycleRequestId REQUEST_UUID app.personal.workouttracker.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5554 shell am instrument -w -e class 'app.personal.workouttracker.wear.quickstart.PairedQuickStartUiTest#recoverOfflineFinalSuccessAfterProcessDeath' -e quickStartVoiceProcessUiValidation true -e lifecycleRequestId REQUEST_UUID app.personal.workouttracker.test/androidx.test.runner.AndroidJUnitRunner
```

Require fresh process IDs and exact persisted runtime/cues at each entry. Real
Resume opens paused success, then resumes the session without replay. Read the
existing production output after actual TTS initialization; require 20 idle
observations at 100 ms, no current controller cue/native speech and no native
focus. Offline completion must first reach actual workout-success speech,
then save 2/2 sets with Waiting to sync and one final-success key. A further
fresh process must reopen that exact saved final result/ledger silently.

Restore the watch's original airplane setting, relaunch only the same phone
copy and re-establish the bridge. Use RESULT_ID from the offline final witness:

```powershell
adb -s emulator-5556 shell am instrument -w -e class 'app.personal.workouttracker.quickstart.PairedQuickStartTransportTest#verifyLifecycleReceiptAfterReconnect' -e quickStartVoiceProcessUiValidation true -e lifecycleRequestId REQUEST_UUID -e lifecycleResultId RESULT_ID -e peerNodeId cc1f21d2 app.personal.workouttracker.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5554 shell am instrument -w -e class 'app.personal.workouttracker.wear.quickstart.PairedQuickStartUiTest#verifyLifecyclePrunedAfterReceipt' -e quickStartVoiceProcessUiValidation true -e lifecycleRequestId REQUEST_UUID -e peerNodeId 3710eec app.personal.workouttracker.test/androidx.test.runner.AndroidJUnitRunner
```

Require OK per stage, exact offline result on the phone, exactly one new record/
receipt and every prior phone record unchanged. Wear must prune runtime/package/
ledger, retain the acknowledged success tombstone and restore original
preferences; legacy entries stay exact. Pull Wear
`ui-acceptance/voice-process-REQUEST_UUID` and phone
`voice-process-acceptance/REQUEST_UUID`. Retain PID/force-stop proof, native
focus dumps, snapshots, screenshots and `checks.txt`. Run ordinary tests without
flags, check crashes/settings/installed APKs and audit original disks after
copied-AVD shutdown. Native sampling does not establish physical acoustic
silence, intelligibility, routing, tactile, battery or Play acceptance.

### Voice-enabled expired-rest process recovery

Iteration 67 adds `prepareVoiceEnabledRestProcessRecovery` and
`recoverExpiredVoiceRestAndCompleteOffline`, explicitly guarded by
`quickStartVoiceRestProcessUiValidation=true`, emulator hardware and exact
copied AVD identity. Preparation requires the sole exact peer and no existing
runtime/package/active legacy workout. Its owned **Emulator voice rest process**
fixture has one two-set exercise with 12 seconds rest. Start the phone waiter:

```powershell
adb -s emulator-5556 shell am instrument -w -e class 'app.personal.workouttracker.quickstart.PairedQuickStartTransportTest#serveProductionTransportProbe' -e quickStartPairedValidation true -e quickStartVoiceRestProcessUiValidation true -e peerNodeId cc1f21d2 app.personal.workouttracker.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5554 shell am instrument -w -e class 'app.personal.workouttracker.wear.quickstart.PairedQuickStartUiTest#prepareVoiceEnabledRestProcessRecovery' -e quickStartVoiceRestProcessUiValidation true -e peerNodeId 3710eec app.personal.workouttracker.test/androidx.test.runner.AndroidJUnitRunner
```

Require OK on both peers. Actual Home targets observed native REST speech;
output/focus releases and exact runtime/ledger/native silence are polled beyond
the hidden deadline. Voice remains enabled. Close the Activity normally,
confirm/force-stop a live copied app process (launch only Home if runner exit
already removed its PID), remove bridge mappings and stop only the copied phone.
Record original watch airplane mode, enable it only on the copy and reboot to
clear cached native peer discovery. Dismiss a known post-boot charging overlay
with Home/Wake before testing. Never reset app data/history or original disks.
Use the UUID from `voice-rest-process-REQUEST_UUID`:

```powershell
adb -s emulator-5554 shell am instrument -w -e class 'app.personal.workouttracker.wear.quickstart.PairedQuickStartUiTest#recoverExpiredVoiceRestAndCompleteOffline' -e quickStartVoiceRestProcessUiValidation true -e lifecycleRequestId REQUEST_UUID app.personal.workouttracker.test/androidx.test.runner.AndroidJUnitRunner
```

Require no native peers, a fresh PID and exact expired RESTING state/cues at
entry. Home must retain that witness without creating a session controller or
owning focus. Only actual Resume opens the session and catches up once: one
deadline-bound Go, no late warning/rest, exact progress/outcomes/set and one
runtime revision. `cold-native-timeline.tsv` samples existing production native
initialization/controller/speech every 20 ms around Resume. The adapter permits
haptic fallback while TTS initializes; cold Go playback is observed, not
required. After native initialization, output/controller must settle idle
without replay. Normal completion must then reach native WORKOUT_SUCCESS and
save 2/2 sets offline with Waiting to sync and one final-success key.

Restore the original airplane setting, same phone copy and bridge. Use RESULT_ID
from the full offline final witness and run the existing receipt methods with
the new explicit flag:

```powershell
adb -s emulator-5556 shell am instrument -w -e class 'app.personal.workouttracker.quickstart.PairedQuickStartTransportTest#verifyLifecycleReceiptAfterReconnect' -e quickStartVoiceRestProcessUiValidation true -e lifecycleRequestId REQUEST_UUID -e lifecycleResultId RESULT_ID -e peerNodeId cc1f21d2 app.personal.workouttracker.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5554 shell am instrument -w -e class 'app.personal.workouttracker.wear.quickstart.PairedQuickStartUiTest#verifyLifecyclePrunedAfterReceipt' -e quickStartVoiceRestProcessUiValidation true -e lifecycleRequestId REQUEST_UUID -e peerNodeId 3710eec app.personal.workouttracker.test/androidx.test.runner.AndroidJUnitRunner
```

Require OK per stage, exact full offline result, exactly one new result/receipt,
every prior phone record unchanged, pruning/tombstone and original preference/
legacy-entry equality. Pull Wear `ui-acceptance/voice-rest-process-REQUEST_UUID`
and phone `voice-rest-process-acceptance/REQUEST_UUID`. Retain PID/force-stop
proof, timeline, snapshots, screenshots and native focus checks. Ordinary tests
run without mutation flags; inventory APKs, restore settings and audit original
disks after copied-AVD shutdown. These emulator observations remain separate
from physical speech quality/routing/tactile/battery/Play acceptance.

### Voice-enabled pending-rest process recovery

Iteration 68 extends the same exact-copy staging/receipt protocol with
`quickStartVoicePendingRestProcessUiValidation=true`. The owned **Emulator
voice pending rest process** fixture has one two-set exercise and 120-second
natural rest, leaving time to reboot the copied watch before its deadline.
Run `serveProductionTransportProbe` on the phone with `quickStartPairedValidation`
and the new flag; run `prepareVoiceEnabledPendingRestProcessRecovery` on Wear
with the new flag and the exact phone peer. Both must report OK (1 test).

Actual REST playback, Home cancellation and initialized native silence are
required. Preparation retains voice enabled and an exact nonexpired RESTING
runtime/ledger. Confirm/force-stop a live copied Home process, remove only the
copied bridge mappings, stop only the copied phone, save/enable airplane mode
on the watch copy and reboot. Dismiss the known charging overlay with Home/Wake.
Complete these steps promptly without changing the saved deadline or clock.

```powershell
adb -s emulator-5554 shell am instrument -w -e class 'app.personal.workouttracker.wear.quickstart.PairedQuickStartUiTest#recoverPendingVoiceRestAndCompleteOffline' -e quickStartVoicePendingRestProcessUiValidation true -e lifecycleRequestId REQUEST_UUID app.personal.workouttracker.test/androidx.test.runner.AndroidJUnitRunner
```

This method requires no native peers, a fresh PID and at least 15 seconds before
the original deadline. Home retains exact runtime/cues and has no session
controller/current focus. Real Resume stays RESTING with no revision, deadline,
progress, outcome or cue change. Initialized native output must remain idle
without REST replay. Poll exact state/ledger/native silence until the final-five
boundary, then require native FIVE_SECONDS between deadline minus five seconds
and the deadline, and native Go at or after the deadline. Persisted revisions
advance once for warning and once for Go, with only those two deadline-bound
keys. A 20 ms read-only native timeline and wall timestamps retain the observed
boundaries. No replacement TTS output or artificial transitions are used.

Complete normally offline, observe native WORKOUT_SUCCESS and 2/2 saved summary
with Waiting to sync. Restore original airplane mode and the same copied phone/
bridge, then run `verifyLifecycleReceiptAfterReconnect` and
`verifyLifecyclePrunedAfterReceipt` with the new flag, request/result IDs and
exact peer IDs. Require exact full offline result, one new result/receipt,
pruning/tombstone, every prior phone record unchanged and exact legacy entries/
restored preferences. Pull Wear `ui-acceptance/voice-pending-rest-process-REQUEST_UUID`
and phone `voice-pending-rest-process-acceptance/REQUEST_UUID`. Retain native
timeline/focus/PID evidence, ordinary checks, inventory and final disk audit.
Emulator native timing remains separate from physical acoustic, Bluetooth,
tactile, battery and Play acceptance.

If a failed attempt leaves this owned fixture until its deadline expires, retain
its diagnostics and use the separately guarded
`completeExpiredPendingRestFixtureOffline` method with the same flag/request ID.
It requires the exact original saved RESTING witness and an expired deadline,
then completes via actual Resume/Complete set. Reconnect for the same exact
receipt/pruning/preference restoration before staging a new request. This
cleanup does not satisfy before-deadline acceptance. The copied-watch charging
overlay may appear more than 15 seconds after boot readiness; use a bounded
40-second observed-overlay wait before Home/Wake, without changing device clock,
charging settings, battery simulation or persisted deadline.

Iteration 68's accepted request `30009af4-c1df-4a1a-8ed6-1ea01118dc7e` passes
all five staged methods. Resume occurs 68,194 ms before the original deadline,
native warning 4,883 ms before it and native Go 197 ms after it. The 3,130-sample
timeline and exact state/ledger checks show no REST replay/early cue. All 2/2
sets save offline; all 54 prior phone records stay exact, records 54 -> 55 and
receipts 31 -> 32, with exact full result, pruning and restored preferences/
legacy entries. Nine phone/twenty-five ordinary Wear checks, builds, screenshots,
empty crash buffers, restored settings/released focus, matching installed APKs
and all 16 original disk hashes pass. Earlier failed attempt and owned cleanup
remain documented in the progress log. The accepted phone's synced prior-record
artifact survives unchanged; its test runner requires reinstalling the same
test APK after copied-phone shutdown before receipt verification. Check
`pm list instrumentation` and repair only the test APK if unavailable. Sync
the copied device before stopping it. Inventory is
`20261004T082954071Z-iteration-68-final`; physical Phase 4 remains 0/27.

### Voice-enabled paused-rest process recovery

Iteration 69 extends the exact-copy staged protocol with
`quickStartVoicePausedRestProcessUiValidation=true`. The owned **Emulator voice
paused rest process** fixture has one two-set exercise with 20-second rest.
Run `serveProductionTransportProbe` on the phone with `quickStartPairedValidation`,
the new flag and exact watch peer; run
`prepareVoiceEnabledPausedRestProcessRecovery` on Wear with the flag/exact phone
peer. Require OK (1 test) on both. Real Pause targets native REST speech and
must cancel output/focus, freeze positive remaining seconds, clear the deadline,
retain rest interval/progress/outcomes and add no cue. Real Home then preserves
that exact paused runtime/ledger with voice still enabled.

Confirm/force-stop a live copied Home process, remove only the copied bridge,
sync/stop only the copied phone, save/enable watch airplane mode, sync/reboot
only the watch copy and dismiss the observed charging overlay after a bounded
40-second wait. Original rest deadline must pass while the fixture stays paused.
Never clear app data or rewrite the clock/deadline/remaining time.

```powershell
adb -s emulator-5554 shell am instrument -w -e class 'app.personal.workouttracker.wear.quickstart.PairedQuickStartUiTest#recoverPausedVoiceRestAndCompleteOffline' -e quickStartVoicePausedRestProcessUiValidation true -e lifecycleRequestId REQUEST_UUID app.personal.workouttracker.test/androidx.test.runner.AndroidJUnitRunner
```

Require no native peers, a fresh PID and exact original PAUSED state/cues.
Fresh Home has no session owner/focus. Its Resume opens PAUSED UI; after native
initialization, state/remaining time/ledger remain exact and output silent.
Only the session Resume commits RESTING once. The new deadline must equal the
saved seconds plus an observed actual Resume time between pre-click and commit
timestamps. Progress/outcomes/interval remain exact. Production intentionally
announces this resumed rest once, bound to the new deadline; the old deadline's
REST key must not replay. Retain `paused-rest-opened`, `paused-rest-resumed`,
foreground/warning/Go witnesses and the 20 ms native timeline. Natural warning
and Go must each add one key/revision bound to the new deadline, with no early
cue and initialized idle after speech. Complete 2/2 normally offline with native
WORKOUT_SUCCESS, Saved on watch and Waiting to sync.

Restore original airplane mode and the same phone/bridge, then use
`verifyLifecycleReceiptAfterReconnect` and `verifyLifecyclePrunedAfterReceipt`
with the new flag, request/result IDs and exact peers. Require exact full offline
result, one new record/receipt, all prior records unchanged, pruning/tombstone,
restored original preferences and exact legacy entries. Pull Wear
`ui-acceptance/voice-paused-rest-process-REQUEST_UUID` and phone
`voice-paused-rest-process-acceptance/REQUEST_UUID`; verify test runner availability
after copied-phone boot, repair only its test APK if necessary. Run ordinary
instrumentation without flags, inventory installed production APKs, inspect
screenshots/crashes/settings/current focus, then sync/stop verified copies and
audit all original source disks. Physical acoustic/routing/tactile/battery/Play
acceptance remains separate.

Iteration 69 request `e076add6-a001-481f-9aae-cfb37daad403` passes all five
stages, with exact 20 seconds frozen through reboot and opening PAUSED UI
35,705 ms after the original deadline. Actual session Resume creates a new
deadline and one intentional native REST; native warning occurs 4,813 ms before
it and Go 242 ms after it. All 2/2 sets save offline with exact result/receipt/
pruning, all 55 prior records unchanged, records 55 -> 56 and receipts 32 -> 33.
Nine phone/twenty-seven ordinary Wear checks, test builds, screenshots, empty
crashes, restored settings/released focus, matching production APKs and all 16
original source-disk hashes pass. The initial empty-peer discovery failure
occurs before mutation; new preparation opt-ins wait up to 30 seconds for the
sole exact native peer, retaining strict assertions before fixture creation.
Synced copied-phone shutdown retains the runner and history witness without
repair. Inventory is `20261004T091754510Z-iteration-69-final`; physical 0/27.

### Voice-enabled final-five paused-rest recovery

Iteration 70 passes all five stages and 9 phone/29 Wear ordinary checks on the
copies. Exact 5 seconds/latch survive; controls stay locked with no REST/warning
replay, one native Go at the new deadline and exact result/receipt/pruning.
All 56 prior records and original disk/settings/preferences/legacy data remain
unchanged. See implementation progress for retained failures and final audits.

Iteration 70 extends the staged exact-copy protocol with
`quickStartVoiceLockedRestProcessUiValidation=true`. The owned **Emulator voice
locked rest process** fixture has one two-set exercise and 12-second rest.
Run phone `serveProductionTransportProbe` with `quickStartPairedValidation`, the
new flag and exact watch peer; run Wear
`prepareVoiceEnabledLockedRestProcessRecovery` with the flag/exact phone peer.
Both wait for the sole exact native peer before mutation and must report
OK (1 test). Actual native REST is followed by the natural FIVE_SECONDS latch,
one key bound to the original deadline and native warning/focus. Pause while
that speech is active, then require released focus/silence, 1–5 frozen seconds,
retained interval/final-countdown latch and exact progress/outcomes/ledger.
Home and normal Activity teardown retain the exact paused witness.

Confirm/force-stop a live copied Home process, remove only the copied bridge,
sync/stop only the copied phone, save/enable watch airplane mode and sync/reboot
only the watch copy. Dismiss the observed charging overlay after a bounded
40-second wait. The original deadline must pass while paused. Never rewrite
clock/deadline/remaining time or clear app data/history.

```powershell
adb -s emulator-5554 shell am instrument -w -e class 'app.personal.workouttracker.wear.quickstart.PairedQuickStartUiTest#recoverLockedVoiceRestAndCompleteOffline' -e quickStartVoiceLockedRestProcessUiValidation true -e lifecycleRequestId REQUEST_UUID app.personal.workouttracker.test/androidx.test.runner.AndroidJUnitRunner
```

Require no connected native peers, fresh PID and exact PAUSED state/cues.
Home has no session owner/focus; opening PAUSED UI initializes speech but stays
silent with exact remaining time/latch/ledger. Only the session Resume commits
a new deadline from the saved seconds and retains the latch/interval. No REST
or warning key is added. Verify +5/+10/+30 controls disabled, and exact runtime/
ledger/native silence up to the new deadline. Natural native Go must occur at
or after it, add exactly one deadline-bound key/revision, and settle idle.
The original warning stays recorded without replay. Finish normally offline
with native WORKOUT_SUCCESS, 2/2 Saved on watch and Waiting to sync.

Restore airplane mode and the same copied phone/bridge. Run
`verifyLifecycleReceiptAfterReconnect` and `verifyLifecyclePrunedAfterReceipt`
with the new flag, request/result IDs and exact peers. Require exact full offline
result, one new record/receipt, all prior records unchanged, pruning/tombstone,
restored original preferences and exact legacy entries. Pull Wear
`ui-acceptance/voice-locked-rest-process-REQUEST_UUID` and phone
`voice-locked-rest-process-acceptance/REQUEST_UUID`, retaining initial warning,
paused/resumed/cue/PID/native timeline/focus evidence. Run ordinary tests without
flags, inventory installed production APKs, inspect screenshots/crashes/settings/
focus, then sync/stop verified copies and audit original disks. Physical acoustic,
routing, tactile, battery and Play acceptance remain separate.

### Voice-enabled extended paused-rest recovery

Iteration 71 passes all five stages plus 9 phone/31 Wear ordinary tests. Actual
+30 changes only deadline/revision; 46 frozen seconds survive offline reboot
beyond the extended deadline, with no cue before session Resume. One new REST/
warning/Go sequence and exact 2/2 offline result/receipt/pruning pass. All 57
prior records and original disk/settings/preferences/legacy data are preserved.
The initial pre-mutation probe timeout is retained; explicit registered-listener
readiness enables successful retry. Final artifacts/audits are recorded in
implementation progress.

Iteration 71 uses `quickStartVoiceExtendedRestProcessUiValidation=true` on the
same exact AVD pair. The phone probe stages **Emulator voice extended rest
process**, one two-set exercise with 20-second rest. Run the phone probe with
`quickStartPairedValidation` and exact peer, then Wear
`prepareVoiceEnabledExtendedRestProcessRecovery`. Wait for the phone runner's
`Transport probe ready` stream marker before starting Wear; peer discovery
alone does not prove its message listener has registered. During native REST, tap the
actual +30s control: require +30,000 ms and one revision, exact session copy
except deadline, unchanged outcomes/progress/interval/ledger and native idle.
Pause retains 21–50 frozen seconds with no warning latch; Home stays exact.

Use the same synced force-stop/offline reboot protocol. Run Wear
`recoverExtendedVoiceRestAndCompleteOffline` with the new flag and
`lifecycleRequestId`. Wait beyond the extended deadline while still PAUSED;
fresh Home and opened PAUSED UI remain exact and silent. Only session Resume
rebases from saved seconds, adds one intentional native REST and retains the
interval/progress. Poll exact native silence until natural final-five warning,
then native Go; each key binds to the new deadline. Finish 2/2 offline.

Restore airplane mode/phone/bridge and run the shared receipt/pruning methods
with the new flag, request/result IDs and exact peers. Preserve every prior
record and compare the full offline result, one new receipt, original entries/
preferences and original AVD disks. Artifacts live under Wear
`ui-acceptance/voice-extended-rest-process-REQUEST_UUID` and phone
`voice-extended-rest-process-acceptance/REQUEST_UUID`. Require all five stages,
test APK builds, ordinary tests, screenshots/crash/focus/settings/APK audits.
Physical acoustic/routing/tactile/battery/Play checks remain separate.

### Voice-enabled early rest exit after recovery

Iteration 72 passes five accepted stages, 9 phone/33 Wear ordinary checks and
preservation/inventory audits. Exact 20 seconds survive; Start now produces one
Go 159 ms after the action, with no delayed warning/Go through the abandoned
deadline while Awake/non-ambient. Full 2/2 result/receipt/pruning and all 59 prior
records remain exact. The display-limited first result is also preserved; its
initial receipt discovery failure is superseded by bounded peer readiness.
See implementation progress for capture limits and final artifact evidence.

Iteration 72 uses `quickStartVoiceEarlyRestProcessUiValidation=true` on the
same exact copies. Phone `serveProductionTransportProbe` also requires
`quickStartPairedValidation` and the exact watch peer. Wait for its
`Transport probe ready` stream marker before running Wear
`prepareVoiceEnabledEarlyRestProcessRecovery` with the flag/exact phone peer.
The owned **Emulator voice early rest process** fixture has one two-set exercise
and 20-second rest. Pause during native REST, preserving 6–20 frozen seconds,
interval/progress/outcomes/ledger, then Home/native idle and exact hidden state.

Follow the same synced live-process force-stop, phone disconnection/shutdown,
watch airplane-mode/offline reboot and observed charging-overlay protocol.
Run Wear `recoverEarlyVoiceRestAndCompleteOffline` with the new flag and
`lifecycleRequestId`. Require fresh PID, no native peers, exact silent Home and
opened PAUSED UI beyond the old deadline. Explicit session Resume alone creates
the saved-seconds deadline and one intentional native REST. Let it settle, tap
actual Start now before the final-five threshold, then require exactly one
native Go and one revision, with only rest fields cleared and all progress/
outcomes unchanged. The Go key binds to that now-abandoned deadline.

Keep the active screen open through deadline +1,500 ms, using repeated Wake
events plus Awake power/non-ambient assertions so display-idle cancellation
cannot mask delayed cues. Compare the complete runtime/ledger and native
controller/speech on every observation. No warning,
extra Go or state change is allowed. Finish 2/2 offline with native final
success. Restore original airplane mode/phone/bridge and run the shared
receipt/pruning methods with the new flag and exact IDs/peers. Phone receipt
waits up to 30 seconds for the sole exact native peer before its assertion.
Preserve all
prior records and compare the full offline result, one receipt, restored
preferences/entries and original disk hashes. Wear artifacts are under
`ui-acceptance/voice-early-rest-process-REQUEST_UUID`; phone artifacts under
`voice-early-rest-process-acceptance/REQUEST_UUID`. Require five staged checks,
test APK builds, ordinary tests, screenshots/crash/focus/settings/APK audits.
Physical acoustic/routing/tactile/battery/Play checks remain separate.

### Native speech interruption, focus and language checks

Iteration 62 adds `NativeSpeechInterruptionTest`, gated by
`nativeSpeechInterruptionValidation=true`, emulator hardware and the exact
`Pasingot_Matrix_Wear` copy. It rejects active/paused Quick Start or downloaded
workouts and existing native focus owners. Only the Wear copy is required.
Production cue/runtime/legacy JSON must be exactly unchanged before/after
each method; synthetic cue storage is isolated. Run after baseline voice
availability is verified and TalkBack/system settings are restored.

```powershell
adb -s emulator-5554 shell am instrument -w -e class app.personal.workouttracker.wear.cues.NativeSpeechInterruptionTest -e nativeSpeechInterruptionValidation true app.personal.workouttracker.test/androidx.test.runner.AndroidJUnitRunner
```

This executes interruption/replacement/focus-loss and unsupported-language
methods, skipping the additional locked-focus method. Each cancellation reason
(Pause, Start now, navigation, end) targets actual Android `isSpeaking`, requires
HAPTIC_ONLY within 1.5 seconds, stopped playback, duplicate suppression and a
fresh SPOKEN cue. Higher-priority Go replaces rest; a real competing transient
focus request stops speech and allows recovery after release. Temporary process
locale zz-ZZ must report LANGUAGE_UNAVAILABLE and restore to the prior locale
with AVAILABLE/SPOKEN. These are output/controller checks; actual UI action
wiring retains separate acceptance. JVM checks hold/fail pause/end persistence
to ensure cancellation follows a successful durable transition.

For focus denial, the extra explicit fixture uses adopted shell identity and
the internal framework focus-for-call API only on the verified idle copy. It
creates a native focus lock without making a telephone call. It requires
AUDIO_FOCUS_UNAVAILABLE/HAPTIC_ONLY, no duplicate output, successful recovery
after unlock, and final removal of its own lock/shell identity. Hidden-API
checks are disabled only for this instrumentation invocation:

```powershell
adb -s emulator-5554 shell am instrument --no-hidden-api-checks -w -e class app.personal.workouttracker.wear.cues.NativeSpeechInterruptionTest -e nativeSpeechInterruptionValidation true -e nativeLockedFocusValidation true app.personal.workouttracker.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5554 pull /sdcard/Android/data/app.personal.workouttracker/files/speech-interruption output/emulator-validation/speech-interruption
```

Require **OK (3 tests)** with both flags (all three methods execute). Retain
native playback/focus evidence and exact before/after JSON hashes. Ordinary
instrumentation skips all three methods without opt-in. Focus loss/denial
establish framework behavior; physical calls, routing, intelligibility, tactile
delivery and battery acceptance remain open.

### Staged countdown, active ambient and process-death recovery

Iteration 60 extends the existing paired UI/probe classes with explicit staged
methods. They require `quickStartLifecycleUiPairedValidation=true`, emulator
hardware and the exact copied AVD names. Build/install both test APKs. Run the
phone probe first and the watch preparation second:

```powershell
adb -s emulator-5556 shell am instrument -w -e class 'app.personal.workouttracker.quickstart.PairedQuickStartTransportTest#serveProductionTransportProbe' -e quickStartPairedValidation true -e quickStartLifecycleUiPairedValidation true -e peerNodeId cc1f21d2 app.personal.workouttracker.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5554 shell am instrument -w -e class 'app.personal.workouttracker.wear.quickstart.PairedQuickStartUiTest#prepareCountdownAmbientAndActiveSleepRecovery' -e quickStartLifecycleUiPairedValidation true -e peerNodeId 3710eec app.personal.workouttracker.test/androidx.test.runner.AndroidJUnitRunner
```

Require **OK (1 test) on both peers** and pull the timestamped/request-specific
artifacts under `ui-acceptance/lifecycle-REQUEST_UUID`. This preparation cancels
the actual countdown by entering ambient, retries Start, preserves an active
set through actual ambient sleep/wake, and pauses after the first exercise's
success. It leaves only its owned synthetic runtime for the next stage.

Verify exact AVD identities again, stop only the copied phone, and force-stop
only Pasingot on the copied watch. Run these Wear methods individually with
`-e quickStartLifecycleUiPairedValidation true -e lifecycleRequestId REQUEST_UUID`:

1. `#recoverExerciseSuccessAndCompleteOffline`: assert empty native connected
   nodes, exact paused runtime/cues and a different process ID; reopen with real
   Resume controls and recover exercise success without a new cue; complete
   offline and preserve the final result and exactly one success reservation.
2. Force-stop Pasingot again, then `#recoverOfflineFinalSuccessAfterProcessDeath`:
   require another process ID and exact runtime/cues; reopen the real saved
   summary with Waiting to sync. This stage does not reconnect the phone.
3. Restart the same copied phone and re-establish the ADB bridge. Run phone
   `PairedQuickStartTransportTest#verifyLifecycleReceiptAfterReconnect` with
   `-e quickStartLifecycleReceiptValidation true -e peerNodeId WATCH_NODE
   -e lifecycleRequestId REQUEST_UUID -e lifecycleResultId RESULT_ID`.
   Run Wear `#verifyLifecyclePrunedAfterReceipt` with the lifecycle flag,
   request ID and `-e peerNodeId PHONE_NODE`. Require **OK (1 test)** from both.

The receipt phase verifies the same two completed sets, exactly one new phone
record/receipt, exact prior native records, pruned runtime/package/cues and
unchanged legacy watch entries. Each stage restores original cue preferences;
voice is disabled only during driven transitions. Process IDs and immutable
JSON witnesses are retained per request. Iteration 60 passes every explicit
stage **OK (1 test)**, ordinary phone **OK (9 tests)** and Wear **OK (12 tests)**,
both test APK builds, visual review and all 16 original disk hashes.
Preparation and recovered process IDs are 2889/2113/2301; live-process
force-stop evidence is also retained. Exact final runtime/cue hashes and full
phone result JSON match. Final artifacts are
`lifecycle-81d99292-af13-4464-bb9b-35557f5de63d` under
`iteration-60-ui-artifacts-final`, with phone records in
`iteration-60-phone-artifacts-final`. The original airplane setting 0 is
restored before shutdown. Physical Phase 4 remains separate at 0/27.

Iteration 60's initial stopped-phone attempt left Wear discovery claiming a
connected node for 45 seconds, so its offline phase did not run. For this copied
setup, record the watch's existing airplane setting, enable airplane mode,
remove its reverse mapping and reboot only that verified copied watch. The
offline methods still require native empty connected nodes; do not bypass that
gate. Restore the recorded airplane setting and mappings before the receipt
phase. This changes only copied runtime setup and preserves source profiles.

### Short-rest cue ledger and screen-off recovery fixture

Iteration 59 adds a separate opt-in method to the existing paired UI classes.
Use only the dedicated copied AVDs; both peers verify their exact isolated boot
name. The watch requires idle runtime/offer and preserves legacy entries. The
phone retains its prior completion receipts exactly and adds only its synthetic
fixture. Start the phone waiter first, then the watch driver:

```powershell
adb -s emulator-5556 shell am instrument -w -e class app.personal.workouttracker.quickstart.PairedQuickStartTransportTest -e quickStartPairedValidation true -e quickStartRecoveryUiPairedValidation true -e peerNodeId cc1f21d2 app.personal.workouttracker.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5554 shell am instrument -w -e class 'app.personal.workouttracker.wear.quickstart.PairedQuickStartUiTest#shortRestCuesAndScreenOffRecoveryThroughRealUi' -e quickStartRecoveryUiPairedValidation true -e peerNodeId 3710eec app.personal.workouttracker.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5554 pull /sdcard/Android/data/app.personal.workouttracker/files/ui-acceptance output/emulator-validation/iteration-59-ui-artifacts
```

Require **OK (1 test)** on both peers. The fixture drives actual Start/Complete
set/Pause/Resume/Start now controls. Rest durations 0/3/5/6/8/10/12 seconds are
compared with durable ordered cue keys: zero has no rest cue, positive short
rests have Five seconds/Go, and a 12-second same-exercise rest also announces
rest. Voice is temporarily disabled and restored in `finally`; ledger evidence
establishes production cue reservation, not audible intelligibility, spoken
overlap, or physical haptic delivery. Exercise success is recreated while
paused with exact runtime/ledger equality. A final 20-second rest spans actual
sleep/wake key events; power/activity/presentation diagnostics distinguish
screen-off from any observed ambient callback. Terminal recreation compares
the exact phone result and compact acknowledged cue tombstone.

Iteration 59 passes **OK (1 test) per peer** in
`iteration-59-recovery-{phone,wear}-final.log`, plus ordinary **OK (8 tests) per
peer** and 199 Wear JVM tests. Actual Dozing/ambient=true and the rendered static
Pasingot rest screen are observed; exact before/hidden runtime and cue hashes
match past the deadline, then wake reserves only Go. This found and corrected
SessionScreen's lifecycle-only visibility gate: Wear can stay RESUMED while
ambient. Final captures are `recovery-1791075079768` under
`iteration-59-ui-artifacts-final`. All 16 original disk hashes remain unchanged.
No injected ambient state is used. Success validation is Activity recreation;
process-death success recovery remains separate. Physical Phase 4 stays 0/27.

### Start and request-state fixture

Iteration 58 extends the entry test classes with separate opted-in methods.
The Wear driver taps real Start/Complete set controls; the phone drives the
installed WebView and closes/relaunches MainActivity to verify reconciliation.
It checks rapid duplicate Send clicks, a new offer rejected during an active
workout with byte-for-byte equivalent runtime JSON, and expiry against the
actual five-minute request deadline plus allowed clock skew. It never changes
device time or substitutes an injected expiry timestamp.

```powershell
adb -s emulator-5554 shell am instrument -w -e class 'app.personal.workouttracker.wear.quickstart.PairedQuickStartEntryUiTest#serveStartAndRequestStates' -e quickStartStateUiPairedValidation true -e peerNodeId 3710eec app.personal.workouttracker.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5556 shell am instrument -w -e class 'app.personal.workouttracker.quickstart.PairedQuickStartEntryUiTest#startAndRequestStatesThroughInstalledWebView' -e quickStartStateUiPairedValidation true -e peerNodeId cc1f21d2 app.personal.workouttracker.test/androidx.test.runner.AndroidJUnitRunner
```

Start Wear first; require **OK (1 test)** on both peers. The real expiry wait
adds about five and a half minutes. Artifacts are in timestamped
`files/state-ui-acceptance/` folders. The driver retains two synthetic completion
receipts plus active-rejected/time-expired records, asserts pre-existing native phone
records and IndexedDB records are preserved, and requires an idle watch at the
end. Failure cleanup can end only a runtime owned by requests observed in that
run, through the normal session engine. Synthetic imported history stays in
the copied phone profile; it is not removed by broad database clearing.
The expired phone record may retain its earlier Ready acknowledgement; the UI
derives expiry from the real deadline. A watch Start after expiry must create no
runtime or result. This fixture does not require an unsolicited Expired
acknowledgement or attempt to cancel an already expired request.

For disconnection, record the copied watch's airplane mode and bridge mapping
state, remove only this session's phone `tcp:5602` forward and watch `tcp:5601`
reverse, and enable watch airplane mode. Confirm native discovery has no peer;
Wi-Fi disable alone is automatically reversed by this Wear runtime. In Iteration
58, removal of mappings and airplane mode still left a connected peer for 45
seconds. Stop only the verified copied Wear AVD if native discovery still
reports it; never stop an original or unrelated device. Run while that copy is
stopped, then cold boot the same copy and restore its bridge mappings:

```powershell
adb -s emulator-5556 shell am instrument -w -e class 'app.personal.workouttracker.quickstart.PairedQuickStartEntryUiTest#disconnectedWatchBlocksInstalledWebViewSend' -e quickStartDisconnectedUiValidation true app.personal.workouttracker.test/androidx.test.runner.AndroidJUnitRunner
```

Require **OK (1 test)**: actionable connection wording, disabled Send, ignored
disabled click and exactly unchanged native request records. Restore prior
airplane mode and owned bridge mappings afterward, including after failure.
Then run the read-only Wear check to require the verified peer and no stale
package/runtime after reconnection:

```powershell
adb -s emulator-5554 shell am instrument -w -e class 'app.personal.workouttracker.wear.quickstart.PairedQuickStartEntryUiTest#verifyIdleAfterTransportReconnect' -e quickStartDisconnectedUiValidation true -e peerNodeId 3710eec app.personal.workouttracker.test/androidx.test.runner.AndroidJUnitRunner
```

This proves emulator transport behavior, not physical Bluetooth routing.

Iteration 58 also corrects the expired offer's Cancel control. After the full
state run leaves an actual time-expired Ready record as the phone's latest
request, build/sync/install the current phone APK and test APK, then run:

```powershell
adb -s emulator-5556 shell am instrument -w -e class 'app.personal.workouttracker.quickstart.PairedQuickStartEntryUiTest#expiredOfferRestoresWithoutCancellation' -e quickStartExpiredUiValidation true app.personal.workouttracker.test/androidx.test.runner.AndroidJUnitRunner
```

This method requires that real expired record, preserves all native records
and checks restored expiry wording, disabled Send and absent Cancel in the
installed WebView. It does not create an artificial expiry timestamp. The
paired state run, actual disconnected/reconnected checks and this restoration
check pass **OK (1 test)** each; final ordinary runs pass eight phone/seven
Wear tests with mutation flags absent. All 199 Wear JVM and 22 Quick Start
browser checks pass. Final logs/captures and unchanged original disk hashes
are recorded in Iteration 58's progress log. Physical Phase 4 stays 0/27.

### Installed phone WebView entry fixture

Iteration 57 adds `PairedQuickStartEntryUiTest` on phone and Wear. This fixture
uses the installed production WebView's rendered DOM buttons, controlled inputs
and React handlers with the real Capacitor/Data Layer bridge. It does not invoke
native offer creation to substitute for Library/Today actions. These are DOM
interactions inside Android instrumentation, not physical touch/accessibility
acceptance. Both participants require explicit opt-in, emulator hardware, the
exact isolated AVD name and sole verified paired node.

Build/install both current Android test APKs. Start the Wear observer first,
then the phone driver in another terminal:

```powershell
adb -s emulator-5554 shell am instrument -w -e class 'app.personal.workouttracker.wear.quickstart.PairedQuickStartEntryUiTest#observePhoneEntryPackages' -e quickStartEntryUiPairedValidation true -e peerNodeId 3710eec app.personal.workouttracker.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5556 shell am instrument -w -e class 'app.personal.workouttracker.quickstart.PairedQuickStartEntryUiTest#libraryAndTodayThroughInstalledWebView' -e quickStartEntryUiPairedValidation true -e peerNodeId cc1f21d2 app.personal.workouttracker.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5556 pull /sdcard/Android/data/app.personal.workouttracker/files/entry-ui-acceptance output/emulator-validation/phone-entry-ui
adb -s emulator-5554 pull /sdcard/Android/data/app.personal.workouttracker/files/entry-ui-acceptance output/emulator-validation/wear-entry-ui
```

Require **OK (1 test)** from both peers. Four offers cover Library single,
playlist with editable duration/rest/load/unit, selected items reordered in
the confirmation sheet, and a synthetic Today row with exact source date/ID.
Each native phone request must match the confirmation controls and be exactly
equal to the durable watch Ready package. Ready and Cancelled wording is
observed through the WebView; every offer is cancelled through its actual
control before the next case. No workout is started.

The driver backs up the persisted playlist draft and all IndexedDB records
after ordinary startup reconciliation, writes only a temporary empty draft and
one uniquely named Today row, and restores those records in cleanup. It
compares all original IndexedDB records and restores the native schedule cache
through the production schedule bridge. Native synthetic cancelled request
history remains only on the copies. The Wear observer requires idle package/
runtime, compares legacy entries throughout, and never creates or starts an
offer. Screenshots, WebView text/input snapshots, before-store records and
exact request JSON are stored in separate timestamped folders on each device.

Iteration 57's paired entry checks pass on API 35/API 37 copies, with five
ordinary instrumentation tests per peer skipping mutation fixtures without
opt-in. Phone confirmation/Ready screenshots and Wear list prompts were
visually inspected. Final log/artifact names and preservation evidence are
recorded in the latest implementation progress iteration. The four connected
entry cases are validated on emulators; physical Phase 4 stays distinct.

For each row record device inventory, request ID/prescription, observations,
artifacts, result and limitations. Failed or unexecuted rows remain open. The
numbers below follow the roadmap's 25 behavior checks and two exit checks.

| # | Check and required observation |
|---|---|
| 1 | Send one exercise through phone Library UI; compare the watch Ready prescription. |
| 2 | Send current playlist through phone UI; compare every item and order. |
| 3 | Reorder an explicit selection before sending; compare resulting watch order. |
| 4 | Send Today row; compare exact sets/reps/rest/load. |
| 5 | Start on watch with phone open; capture Started reconciliation on phone. |
| 6 | Start while phone app is closed; reopen and capture reconciled status. |
| 7 | Disconnect the actual transport; capture actionable phone wording and absent stale package. |
| 8 | Attempt sending during an active workout; verify unchanged runtime/progress. |
| 9 | Duplicate send/tap; verify one package, runtime and terminal result. |
| 10 | Let an offer expire; verify Start rejection and absence of runtime. |
| 11 | Kill/restart watch app with Ready offer; verify safe pending recovery. |
| 12 | Complete offline; kill/reboot watch, compare retained result, reconnect and require exact phone receipt before pruning. |
| 13 | Toggle voice master and each category; recreate/reopen and compare stored settings. |
| 14 | Hear start briefing and compare exercise, sets, reps/duration and load with the request. |
| 15 | Leave during five-second countdown; wait beyond expiry and verify no unseen session; retry normally. |
| 16 | Hear configured rest once; extend it and verify no original announcement replay. |
| 17 | Use +5/+10/+30; compare deadline increments, then disabled controls, unchanged deadline and no added cue during final lock. |
| 18 | Observe/hear exactly one five-second and Go cue at the corresponding transition. |
| 19 | Recompose/restart/navigate from exercise/final success; verify no repeated speech/haptics. |
| 20 | Make TTS unavailable/fail; verify complete visible prescription/countdown and physical haptic fallback. |
| 21 | Compare rests of 0–5, 6–10 and >10 seconds against specified cue sequence without overlap. |
| 22 | Change speaker/Bluetooth route, enable TalkBack, enter ambient/screen-off; recover without duplicate speech or altered progress. |
| 23 | Run comparable 60–90 minutes on physical watch; record initial/final battery, duration/settings and relevant power diagnostics. |
| 24 | Install/update from Play internal track; compare installed versions/signatures and compatible/mixed capability gating. |
| 25 | Repeat full-day send, manual/scheduled download, logs/live status and offline retry; preserve prior history. |
| 26 | Evidence identifies actual phone/watch models, OS and app versions; label emulator inventory explicitly. |
| 27 | Close code completion and paired physical acceptance separately only after their relevant checks pass. |

Existing native transport and recovery evidence is linked from the paired
validation guide. It supports emulator platform confidence but does not replace
phone UI entry, physical cue, battery or store-install observations.

Iteration 56's final paired UI logs are
`output/emulator-validation/iteration-56-ui-phone-verified.log` and
`iteration-56-ui-wear-verified.log`: both **OK (1 test)**. Final 01–10 PNG/text
captures in `iteration-56-ui-artifacts-verified` were visually inspected;
prior-run `failure.*` diagnostics retain their older timestamps. Ordinary runs
pass four tests per peer with mutation flags absent. The emulator inventory
`20261003T140730409Z-iteration-56-final` verifies API 35/API 37, app 1.0/code 1
and installed/local base APK equality. These are local ignored artifacts;
the checked-in fixture and commands reproduce them.


Physical inventory exit check — 2026-10-07 (item 26 complete): Galaxy S25
SM-S931B and Galaxy Watch7 SM-L300, Android 16/API 36, Pasingot 1.0/versionCode
1. Iteration 102 readiness artifacts retain models/OS/versions and reciprocal
nearby nodes; post-update hashes match validated APKs with matching certificates.
Iteration 103 installed-fixed-sha256.txt retains the updated Watch7 layout APK.
Physical behavior evidence currently includes saved 5/5, 9/9 completion and
visually fixed return button. These observations do not close full transport,
recovery, cue, battery or Play acceptance cases. Physical count is 1/27.


Ongoing workout timeout test — Iteration 105, 2026-10-07 (pending): installed
Watch7 build adds a silent session-scoped Ongoing Activity. Physical startup
preserves all captured stores and clears notification for the completed workout.
Use a fresh normal timed Quick Start (one set, 3 min, 0s rest); record exact
request/session, notification metadata and tap target, leave screen untouched
past current 15s timeout off charger, wake and compare active state/deadline.
Differentiate normal ambient from deliberate Home/back and charging overlay;
do not alter personal timeout/AOD/notification settings. Finish normally, verify
phone result/receipt and no stale ongoing indicator. No row closes until actual
observations pass; timed acceptance is not inferred from code/startup checks.
