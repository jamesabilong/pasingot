# Paired emulator transport checks

These opt-in Android instrumentation checks use the production Data Layer
services. They require two running, connected emulators with matching debug
app signatures and application ID `app.personal.workouttracker`.

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
