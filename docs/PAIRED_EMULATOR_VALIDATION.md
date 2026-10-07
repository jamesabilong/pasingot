# Paired emulator transport checks

These opt-in Android instrumentation checks use the production Data Layer
services. They require two running, connected emulators with matching debug
app signatures and application ID `app.personal.workouttracker`.

## Owned-pair readiness before acceptance — Iterations 97–101

The isolated phone `Pasingot_Pair_Phone` and watch `Pasingot_Timed_UI` have
matching debug signatures and current installed APK hashes. Native inventory
currently reports phone **Wearable.API unavailable (API_UNAVAILABLE)** and no
local node; watch local `f81011dd` has no connected peers. Creating/removing
only owned vacant bridge endpoints and refreshing the emulator connection did
not resolve this. The phone has no watch companion. Complete installation and
setup using the [official Android pairing guide](https://developer.android.com/training/wearables/get-started/connect-phone),
then rerun native discovery. Original AVDs and their data remain untouched.

Build/install the current production and instrumentation APKs on the exact
owned profiles before running from the repository root (verify serials):

```sh
python3 scripts/paired_preflight.py --phone-serial emulator-5582 --wear-serial emulator-5580 --output output/emulator-validation/paired-preflight-new
```

The output directory must be new. The tool refuses original profiles before
running probes, records native inventories and instrumentation logs, compares
installed production APK hashes and native signing certificates, rejects stale
native markers using the device-clock capture window, and requires
exact reciprocal sole nearby peers. It creates no bridges or workout requests.
Exit 0 means transport prerequisites ready; exit 2 means blocked prerequisites;
exit 1 means capture failure. `acceptanceValidated` always remains false.
Inventory OK is evidence that the probe executed, not proof that pairing or
Ready/Start/result/receipt behavior passed. Keep no-flag discovery runs separate
from explicit opt-in inventory. Actual paired timed and resumed-REST acceptance
and all physical checks remain open.

## Quick Start matrix

The phone must have a completed Quick Start with its exact phone receipt. Use
the most recently acknowledged request, before creating another offer: its
watch replay tombstone is the baseline. The harness creates and cancels one
additional offer, retaining that terminal fixture in normal local history.
It does not start a workout. Complete a fresh fixture before repeating a run
whose earlier replay tombstone has expired or been replaced.

The default full matrix requires no active legacy workout. Preserve an existing
active workout until the operator decides to finish it; test fixtures must not
silently replace or end it. In Iteration 52, the cached October 2 workout has
one completed set and blocks fresh Ready/Cancel acceptance.

Two additional watch-driver modes are available (use one at a time):

- `-e quickStartFreshCompletedFixture true` creates a fresh one-set Quick Start
  through the phone probe, uses native start/runtime transition/result APIs to
  complete it, then runs the full matrix. It retains synthetic completion and
  cancellation history. The supplied phone `completedRequestId` seeds its
  exercise prescription. This mode is built but its completion runtime path
  remains unverified in Iteration 52 because an active legacy workout blocks it.
  Iteration 55 validates this mode on isolated AVD copies after ending only the
  copied legacy session. It does not establish countdown, UI, cue or
  physical-device acceptance; the Iteration 52 runtime limitation is historical.
- `-e quickStartTerminalReplayOnly true` runs capability/binding and historical
  completed/wrong-target replay while preserving active workouts; skips fresh
  Ready/Cancel creation. Require both probes to report `OK (1 test)`. It checks
  exact phone/runtime and unrelated package/history state. An expired fixture
  may add its own refusal to terminal history, which this mode permits.
  This partial mode cannot close the full Phase 3 matrix item.

Build from `android`:

```powershell
.\gradlew.bat :app:assembleDebugAndroidTest :wear:assembleDebugAndroidTest --no-daemon
```

Install the phone `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`
and watch `wear/build/outputs/apk/androidTest/debug/wear-debug-androidTest.apk`
on their respective emulators with `adb -s SERIAL install -r APK`.
Confirm each serial with `adb -s SERIAL emu avd name`; ports can swap after a
restart. The emulator bridge uses a phone forward `tcp:5602 tcp:5601` and watch
reverse `tcp:5601 tcp:5602`. Node IDs come from native Data Layer discovery;
they are distinct from adb serials.

Run the phone probe first, then the watch driver in a second terminal within
three minutes. Substitute the verified serials, node IDs and completed UUID:

As of Iteration 54, the phone's `completedRequestId` argument is optional: when
omitted it selects the newest durable completion receipt bound to the exact
connected watch. Supply the argument to replay a specific retained fixture.
The probe fails before registering if no matching completion exists.

```powershell
adb -s PHONE_SERIAL shell am instrument -w -e class app.personal.workouttracker.quickstart.PairedQuickStartTransportTest -e quickStartPairedValidation true -e peerNodeId WATCH_NODE -e completedRequestId COMPLETED_UUID app.personal.workouttracker.test/androidx.test.runner.AndroidJUnitRunner
adb -s WATCH_SERIAL shell am instrument -w -e class app.personal.workouttracker.wear.quickstart.PairedQuickStartTransportTest -e quickStartPairedValidation true -e peerNodeId PHONE_NODE app.personal.workouttracker.test/androidx.test.runner.AndroidJUnitRunner
```

Require `OK (1 test)` from both explicit runs. Check the instrumentation
output, since adb may exit zero even when JUnit reports failure. Without the
opt-in argument the paired tests skip; ordinary phone instrumentation still
checks the installed application package.

Coverage:

- Actual watch-owned capability Data Items: missing, malformed, future
  envelope, unsupported request version, wrong node, wrong role, compatible.
- A watch-sent acknowledgement claiming another node cannot alter the phone.
- A valid acknowledgement under another request's URI cannot alter the phone.
- Actual result Data Items with a wrong watch, wrong importing phone, future
  envelope schema or mismatched URI are retained without a persisted receipt;
  phone records and watch runtime remain exact. Injection refuses to overwrite
  an existing Data Item at the fixture path; only injected items are removed.
- Known completed replay and wrong-target requests cannot revive watch state.
- A fresh Ready offer becomes Cancelled; request/cancel/ack items disappear.
  Replaying the cancelled request preserves its exact terminal record and
  cleans transport items again.
- Durable runtime and package state remain unchanged during replay checks.
- Before replay, actual MainActivity launch/background/resume exercises phone
  terminal transport recovery and removes any retained exact orphan receipt.
  Cleanup acceptance includes this lifecycle retry boundary.

The original capability is restored and injected transport items are removed
in cleanup. These fixtures establish native emulator integration, not physical
Bluetooth, mixed installed app releases, audio, battery or Play acceptance.

## Isolated full fresh matrix

Iteration 55 passes the full matrix without ending the original active workout.
Use dedicated disk copies when an original session must be preserved:

1. Stop the source phone/Wear emulator processes normally and verify no source
   disk is being written. Capture SHA-256 hashes of all source `.img` and
   `.qcow2` files, retaining the source workout baseline separately.
2. Create `Pasingot_Matrix_Phone.avd` and `Pasingot_Matrix_Wear.avd` directories
   under ignored workspace validation output. Copy each original profile's
   complete `.img`/`.qcow2` chains, `config.ini`, `qemu-version.txt`, and
   `version_num.cache`. The version metadata is required: omitting it caused
   the emulator to recreate the copied overlay chain in the initial attempt.
3. Update each copied `config.ini` `AvdId` and create corresponding `.ini` files
   pointing to those directories, with the correct Android target. Disk
   backing names in this validated setup are relative; verify any backing
   references and writable disk paths resolve to the copies. System images
   and SDK skins may remain shared read-only resources.
4. Scope `ANDROID_AVD_HOME` to the copied-profile directory in the launcher
   process. Cold boot the copies with `-no-snapshot -no-window -no-boot-anim
   -gpu host -feature -Vulkan`, distinct verified ports, and disabled phone
   cameras. Use `Start-Process -WindowStyle Hidden` on Windows. Confirm actual
   AVD names and app installations; verify the copied workout JSON exactly
   matches the baseline before preparation. Re-establish the adb bridge.
5. Build/install current test APKs, then run the explicit preparation below.
   It requires exact boot name `Pasingot_Matrix_Wear`, ends only the selected
   copied session through the normal engine, preserves completed sets and
   cached exercises, and requires an idle copied watch afterward.

```powershell
adb -s WATCH_SERIAL shell am instrument -w -e class 'app.personal.workouttracker.wear.quickstart.PairedQuickStartTransportTest#prepareIsolatedLegacySession' -e quickStartIsolatedLegacyPreparation true -e legacyEntryId 2026-10-02 app.personal.workouttracker.test/androidx.test.runner.AndroidJUnitRunner
```

Require `OK (1 test)`. Run the phone probe, then the watch driver with
`quickStartFreshCompletedFixture=true` as above (select the
`#capabilityBindingAndTerminalReplay` method explicitly if desired). Require
`OK (1 test)` from both peers. The fresh fixture retains synthetic completion
and cancellation history only in the copied runtime.

Stop only the copied AVDs and compare every original disk hash afterward.
Iteration 55 verifies **16 unchanged source disk images** before and after
shutdown. Original emulator profiles remain stopped and unchanged; copied
profiles/history are retained only in ignored local validation output. This
closes the Phase 3 native matrix; physical Phase 4 remains separate.

## Legacy sync regression

Iteration 53 passes these probes on both Windows emulators with the active
October 2 legacy workout preserved. Build/install the same Android test APKs
described above. Require an empty Wear pending log/event/snapshot queue, fewer
than three cached workouts, and no existing cache entry for today's date. The
driver fails these preconditions instead of draining user history or evicting
an existing workout. It can run while a different cached legacy workout is
active. Use the verified connected node IDs and start the phone first:

```powershell
adb -s PHONE_SERIAL shell am instrument -w -e class app.personal.workouttracker.weardata.PairedLegacySyncTest -e legacyPairedValidation true -e peerNodeId WATCH_NODE app.personal.workouttracker.test/androidx.test.runner.AndroidJUnitRunner
adb -s WATCH_SERIAL shell am instrument -w -e class app.personal.workouttracker.wear.data.PairedLegacySyncTest -e legacyPairedValidation true -e peerNodeId PHONE_NODE app.personal.workouttracker.test/androidx.test.runner.AndroidJUnitRunner
```

Require **OK (1 test) from both peers**, inspecting JUnit output even if adb
exits zero. Ordinary instrumentation skips both fixtures. Coverage includes
actual phone Send today transport with full exercise/quest prescriptions,
manual download, the actual scheduled-download worker, duplicate-safe log and
session-event staging, and live snapshot delivery. Existing cache/session
entries and Quick Start package/runtime are asserted unchanged.

Cleanup restores original raw phone schedule/live-status preferences and its
owned workout Data Item, restores the watch-owned live snapshot Data Item,
removes only marked synthetic phone history, and deletes only the downloaded
fixture whose exercises still match and which has no session state. It cancels
only its immediate worker ID. The production worker re-arms the ordinary
scheduled-download job using existing settings, and normal log retry jobs may
remain. Do not clear app data to repeat this test.

Pair native evidence with `/tests/watch-sync.html`, `/tests/watch-updates.html`
and `/tests/quick-start.html` in the local PWA test server: Iteration 53 passes
22 + 12 + 19 browser checks. These use real isolated IndexedDB with mock native
bridges, separately from actual native Data Layer transport. This checkpoint
does not validate combined Android WebView interaction, scheduled wall-clock
alarm delivery, countdown/cues, physical Bluetooth/audio/battery or the full
fresh Quick Start matrix.

Phone launch/resume cleanup acceptance is a separate opt-in fixture. It requires
the exact connected watch and two existing completed phone records with no
retained watch results. It injects invalid payloads at those terminal offer
paths, one exact orphan receipt and one timestamp conflict; uses real Data Items
and an injected partial deletion failure; then launches/backgrounds/resumes
MainActivity to exercise production plugin recovery. Phone durable history must
remain exact. Owned transport fixtures are removed in `finally`.

```powershell
adb -s PHONE_SERIAL shell am instrument -w -e class app.personal.workouttracker.quickstart.QuickStartTransportRecoveryInstrumentedTest -e quickStartRecoveryValidation true -e peerNodeId WATCH_NODE app.personal.workouttracker.test/androidx.test.runner.AndroidJUnitRunner
```

Require `OK (1 test)`; without opt-in it skips. This fixture does not exercise
legacy transfer or the full fresh Quick Start matrix. Iteration 52 verifies this
fixture and the historical replay-only mode on Windows API 35/API 37 emulators.

On this Windows phone AVD, the Google Pixel Watch companion crashed with
`SecurityException: BLUETOOTH_CONNECT` until the user approved granting its
Nearby devices permissions. This was a companion setup failure; Pasingot had
no fatal crash or system ANR in that boot. Grant permissions only when the
operator authorizes that emulator setup change.


## Resumed REST speech interruption — Iterations 73–77

**Code/build and headless checks only; paired execution pending on this Mac.**
The existing early-rest fixture now supports an explicit in-flight speech
recovery case. Use a new owned request on the verified isolated copied pair,
not original AVDs or a historical completed request. Do not run the whole test
class with mutation flags. Preserve source-disk hashes, legacy entries, prior
phone records and preferences, and keep physical acceptance separate.

Use `quickStartVoiceEarlyRestProcessUiValidation=true` on all five stages.
Start the phone `PairedQuickStartTransportTest#capabilityBindingAndTerminalReplay`
probe first with its exact `peerNodeId`, and wait for its registered-listener
stream marker. Run Wear
`PairedQuickStartUiTest#prepareVoiceEnabledEarlyRestProcessRecovery` with the
phone node ID. Preserve the returned request UUID and its artifact folder.
Perform the existing exact-copy force-stop/offline reboot protocol, requiring
a genuinely fresh process and original rest deadline elapsed while paused.

Run Wear `PairedQuickStartUiTest#recoverSpeakingVoiceRestAndCompleteOffline`
with `lifecycleRequestId=UUID` and **both** the early-rest flag and
`quickStartVoiceInterruptRestSpeech=true`. It opens the paused session silently,
explicitly resumes, verifies native in-flight REST immediately before clicking
Start now, and requires one Go within two seconds. It checks exact transition/
ledger, native REST/Go timeline ordering, no returning REST/warning, awake
silence through the abandoned deadline and offline 2/2 completion. The idle
`recoverEarlyVoiceRestAndCompleteOffline` method skips when the speech flag is
set, preventing accidental coverage substitution.

Restore only the copied bridge/airplane baseline, then run phone
`PairedQuickStartTransportTest#verifyLifecycleReceiptAfterReconnect` and Wear
`PairedQuickStartUiTest#verifyLifecyclePrunedAfterReceipt` with `lifecycleRequestId=UUID`,
the early-rest flag and exact peer IDs. Supply the phone receipt stage
`lifecycleResultId` from `finalResult.resultId` in the retained Wear
`before-final-process-death-runtime.json`; never guess the result identity. All five stages must report `OK (1 test)`; inspect
JUnit output, not merely adb exit codes. Retain the native timeline,
`start-now-rest-speaking-wall.txt`, `start-now-wall.txt`, `go-wall.txt` and
`speech-interruption-validated.txt`, plus the full original preservation/
result/receipt/pruning artifacts. Re-run ordinary mutation-free tests, audit
settings/focus/APK identity and compare source disks after stopping only copies.
No paired or physical item closes until these stages actually pass.


## Timed-set round UI — Iteration 78

Use only the disposable blank `Pasingot_Timed_UI` AVD for the detached fixture:
`TimedExerciseUiTest#countdownPauseResumeAutoRestAndRepLayout` with
`timedExerciseUiValidation=true`. Build/install current Wear production and
Android-test APKs. It injects a detached in-memory workout into the real
SessionScreen/ViewModel, captures active/paused/final-five/rest/repetition
screens and asserts exact paused state, automatic one-set completion and
return to manual repetition controls. It does not send transport or mutate
normal workout history/cue preferences, and does not establish native TTS,
paired Quick Start result/receipt, physical acoustics or battery acceptance.
Artifacts are under the app's external files `timed-ui` folder. Ordinary
instrumentation skips without the explicit flag; original AVDs must remain
untouched. Retain failure and accepted-run logs separately.


## Timed Quick Start persistence and receipt — Iterations 79–81

Headless evidence: the Started coordinator saves the first explicit-duration
deadline before receipt transport/Go. Real-file DataStore scopes preserve
active/paused/resumed runtime and final-result/receipt tombstones. Production
engine/adapter integration covers foreground expiry, exact pause and failed
writes. These checks do not execute paired transport, reboot, native cues or
physical acceptance. Iteration 78's detached UI evidence remains historical;
rerun with current APKs before claiming updated native acceptance.

Next device run (pending; no new native fixture or completed run is claimed):

1. Use verified isolated phone/watch copies, recording exact AVD names, source
   disk hashes, installed APK hashes, legacy entries, phone history and cue/
   device settings before mutation. Never install or write fixtures to original
   user profiles. Record the exact timed request and request/result identity.
2. Send a timed two-set workout through the actual phone selection/Ready/Start
   flow. Capture persisted ACTIVE, Started acknowledgement and first timed
   deadline at zero. Confirm the deadline starts with runtime, survives delayed
   receipt/Go and is not rebased by screen entry or reopening.
3. Pause mid-set; retain exact frozen milliseconds. Disconnect both transport
   peers, close bridge sockets and reboot the isolated watch beyond the old
   deadline. Fresh Home and paused Resume UI must retain exact runtime/outcomes
   and silence until explicit session Resume; then derive deadline from saved
   milliseconds. Retain screenshots and exact runtime snapshots.
4. Separately hide an active timed set beyond its deadline; verify no hidden
   outcome changes. Foreground admits exactly one completion/rest. Verify each
   subsequent timed set gets its own duration and a repetition exercise retains
   manual completion. Preserve exact cue ledger and native playback evidence.
5. Complete offline, retain exact final result and prior phone history, then
   reconnect. Require one matching phone record/receipt and runtime/package/cue
   pruning; mismatched or stale receipts must not clear the result. Re-run
   ordinary instrumentation, audit original history/settings, shut down only
   verified copies and compare source disk hashes. Native timing/cue, transport
   and physical items remain open until this evidence passes.


## Isolated native timed runtime reboot fixture — Iterations 83–85

Use `TimedQuickStartRecoveryTest` only on a fresh, workspace-owned blank
`Pasingot_Timed_UI` AVD. It never opens the normal runtime DataStore, starts
an Activity, calls a phone client or writes legacy history/preferences. Its
production Preferences DataStore lives under the private owned directory
`files/timed-recovery-validation`; `preparePausedRuntime` refuses to overwrite
existing evidence. Native session engine and Quick Start adapter are real;
clock, cue emitter, request initialization and offline result client are
controlled seams. This proves native file/engine recovery across actual reboot,
not actual phone Ready/Start, Started delivery, natural wall-clock waiting,
Data Layer receipt/package/cue pruning, native playback, or physical acceptance.

Build and install current production/test APKs, verify the exact AVD name with
`adb -s SERIAL emu avd name`, and select each method explicitly. Never run the
whole class with the mutation flag. Each run uses:

```sh
adb -s SERIAL shell am instrument -w \
  -e class 'app.personal.workouttracker.wear.quickstart.TimedQuickStartRecoveryTest#METHOD' \
  -e timedQuickStartRecoveryValidation true \
  app.personal.workouttracker.test/androidx.test.runner.AndroidJUnitRunner
```

Run these stages in order, with an actual `adb -s SERIAL reboot` between each
and a bounded wait for `sys.boot_completed=1` plus exact AVD-name recheck:

1. `preparePausedRuntime`: private two-set 30-second runtime, durable active
   deadline/Started identity, production Pause at +1,250 ms, exact 28,750 ms
   remainder and no outcomes/result/success. Saves full active/paused snapshots.
2. `recoverPausedAndPrepareHiddenRuntime`: at controlled +90,000 ms, compare
   full paused runtime, remain paused on opening, explicitly Resume to
   the exact +118,750 ms deadline, hide, retain full hidden snapshot.
3. `recoverHiddenCompleteOfflineAndReceipt`: at controlled +200,000 ms, hidden
   opening preserves full runtime without outcomes; foreground completes one
   set only and gives the next its own 30-second duration. Final expiry saves
   exactly 2/2 and one durable offline result/success. Duplicate completion and
   recreated engine do not replay; wrong revision preserves result, exact
   synthetic receipt clears the private runtime.
4. `verifyReceiptAfterFreshProcess`: no runtime resurrection after reboot;
   exact receipt is already cleared and reinitialization returns the tombstone.

Require **OK (1 test)** for each stage, not merely adb success. The fixture
records Android boot IDs and process IDs; recovery stages assert that the prior
boot ID changed. Retain logs, full snapshots and receipt/boot markers using
`adb shell run-as app.personal.workouttracker` against the owned directory.
Do not clear app data to repeat; use a new disposable AVD/run directory while
preserving prior failure evidence. Also run current timed round-UI acceptance
and ordinary instrumentation without mutation flags. Shut down only the owned
AVD after identity verification. This supplements the pending paired protocol
above; Stage 19/physical item counts do not change from private fixture evidence.


## Native wall-clock timed sets and speech — Iterations 87–91

`TimedQuickStartNativeCueTest` uses the actual system clock, private Preferences
DataStore for package/runtime/cue records, production Ready/Start gate,
coordinator, SessionViewModel, SessionScreen and ControllerSessionCueEmitter.
AndroidTtsCueOutput is wrapped to retain native `isSpeaking`, completion,
haptic calls and wall-clock timestamps. Only the workspace-owned disposable
`Pasingot_Timed_UI` is allowed. No actual phone Start/receipt transport or
physical acoustics/haptics/battery is established by this fixture.

Build/install current Wear test APK, verify the exact AVD identity and use:

```sh
adb -s SERIAL shell am instrument -w \
  -e class 'app.personal.workouttracker.wear.quickstart.TimedQuickStartNativeCueTest#METHOD' \
  -e timedNativeCueValidation true -e timedNativeRunId RUN_ID \
  app.personal.workouttracker.test/androidx.test.runner.AndroidJUnitRunner
```

Select methods explicitly; never run the class with mutation flags. RUN_ID is
an alphanumeric/hyphen identifier; default is `initial`. Each stage refuses to
overwrite its own directory, so retain failed attempts and use a new RUN_ID for
retry. Preparation and paused recovery must use the same RUN_ID. Owned evidence
is under `files/native-timed-validation/RUN_ID/STAGE`, retrievable with run-as.

- `nativeReadiness`: retain real connected phone-node inventory; require native
  TTS availability and observed/completed `Go.`. Empty node inventory means
  paired delivery remains pending even when native speech passes.
- `naturalTimedSetsRestAndNativeCues`: two actual eight-second sets and a
  twelve-second rest. Started seam delays 750 ms after verifying durable first
  deadline; opening cannot rebase it. One set/rest commit, next duration,
  native REST/five-second/Go/workout-success ordering, bounded warning/Go
  timestamps, 2/2 durable offline result and one success.
- `hiddenActivityExpiryAndTerminalNonReplay`: Activity STARTED pauses foreground
  ticking without ON_STOP save; exact private runtime/cues stay unchanged beyond
  the old real deadline. RESUMED admits one set and a fresh next duration. Final
  native success occurs once; duplicate completion/recreated engine stay silent.
  This is Activity lifecycle evidence, separate from actual Wear ambient mode.
- `prepareNativePausedRuntime`: start actual twenty-second set, explicitly Pause,
  retain exact frozen milliseconds/full runtime/cues and Android boot ID.
- Reboot only the verified owned AVD. Observe/dismiss the post-boot charging
  overlay and wake the display before `recoverNativePausedRuntime`. When the
  overlay keeps reclaiming foreground, record owned battery state, temporarily
  use `cmd battery unplug` plus wake/Home only on the disposable AVD, then
  restore simulation with `cmd battery reset` at closure. Do not substitute
  visibility overrides or remove the native lifecycle assertions.
  Then run `recoverNativePausedRuntime`:
  changed boot ID is mandatory. Opening beyond the abandoned real deadline
  retains exact paused state/cues without native output. Explicit Resume derives
  its deadline from exact frozen milliseconds and completes with one native
  success and durable offline result. A recording clock callback returns actual
  System.currentTimeMillis; deadline minus frozen milliseconds must equal a
  recorded transition clock read, independent of caller scheduling latency.

Require OK (1 test) from each stage; native speech must actually be observed and
complete, not just be requested. Retain speech/haptic/focus files and full
runtime/cue snapshots. Run ordinary instrumentation without flags, compare
installed APK hashes, restore owned notification permission, audit focus/crash
state and shut down only the verified disposable AVD. Current pass/failure
status belongs in IMPLEMENTATION_PROGRESS.md; this protocol alone is not proof.


## Private native resumed-REST speech and interrupted receipt — Iterations 92–96

Continue `TimedQuickStartNativeCueTest` on the verified owned `Pasingot_Timed_UI`
with `timedNativeCueValidation=true` and one fresh `timedNativeRunId` shared by
all stages. Select each method explicitly. Do not run the class with mutation
flags. This private production-store/gate/engine/emitter/Android-TTS path uses
actual time and programmatic session actions; it does not establish actual
phone selection/Ready/Start, Wear UI taps, Data Layer transport or physical
acoustics/haptics/battery. The paired speech protocol above remains pending.

Use the invocation from the real-clock section, with these method names:

1. `nativeReadiness`: actual TTS observation/completion and connected phone-node
   inventory. Empty peer inventory keeps all paired acceptance open.
2. `prepareSpeakingRestRecovery`: two six-second sets with thirty-second rest;
   Pause interrupts observed in-flight native REST after first automatic set.
   Exact paused rest/runtime/cues, old deadline and boot identity are retained.
3. Reboot only the owned AVD, dismiss the charging overlay using recorded/
   reversible owned battery simulation plus wake/Home, then run
   `recoverSpeakingRestAndStartNow`. Changed boot ID, exact paused state/cues
   and silence beyond the original real deadline are mandatory. Explicit Resume
   derives a new deadline from frozen seconds and emits native REST. Start now
   must target in-flight REST, replace it with one observed/completed Go within
   two seconds and finish 2/2 offline. Native REST is cancelled; no five-second,
   returning REST or late Go is admitted through the abandoned new deadline.
4. Reboot again, then `prepareInterruptedReceiptCleanup`: exact offline final
   runtime/cue ledger survives fresh process and opening without output/replay.
   Wrong sender/revision/path preserve runtime/package/cues. Exact synthetic
   receipt passes production payload coordinator, durably clearing runtime and
   package before an injected cue-pruning failure. Receipt and partial-cleanup
   boot identity are retained; private cue ledger remains until retry.
5. Reboot again, then `recoverInterruptedReceiptCleanup`: exact receipt replay
   retries cue pruning from runtime/package tombstones. All obsolete cues and
   success remain duplicate/silent on actual native output; original request/
   runtime cannot revive and exact replay remains idempotent.

Require OK (1 test) for every explicit stage; do not count skipped default runs
as native behavior. Capture a private-file archive **after each stage and before
running the next**: generic speech/haptic/focus filenames describe the latest
stage and are reused. Full stage snapshots have distinct names. Keep native
speech timeline and paused/final/receipt/boot identities in separate retained
archives. No app data is cleared and a prior run directory is never overwritten
by preparation. Retain failed builds/attempts separately, with new run IDs for
fresh retries. Run ordinary no-flag instrumentation, compare installed/current
APK and private-file hashes, audit focus/crash state, reset owned battery and
notification permission, verify AVD identity before shutdown. Current progress
and actual pass/failure evidence belongs in IMPLEMENTATION_PROGRESS.md.
