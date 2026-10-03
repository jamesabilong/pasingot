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
  It does not establish countdown, UI, cue or physical-device acceptance.
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
- Known completed replay and wrong-target requests cannot revive watch state.
- A fresh Ready offer becomes Cancelled; request/cancel/ack items disappear.
  Replaying the cancelled request preserves its exact terminal record and
  cleans transport items again.
- Durable runtime and package state remain unchanged during replay checks.

The original capability is restored and injected transport items are removed
in cleanup. These fixtures establish native emulator integration, not physical
Bluetooth, mixed installed app releases, audio, battery or Play acceptance.

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
