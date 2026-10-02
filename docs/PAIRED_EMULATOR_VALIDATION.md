# Paired emulator transport checks

These opt-in Android instrumentation checks use the production Data Layer
services. They require two running, connected emulators with matching debug
app signatures and application ID `app.personal.workouttracker`.

The phone must have a completed Quick Start with its exact phone receipt. Use
the most recently acknowledged request, before creating another offer: its
watch replay tombstone is the baseline. The harness creates and cancels one
additional offer, retaining that terminal fixture in normal local history.
It does not start a workout. Complete a fresh fixture before repeating a run
whose earlier replay tombstone has expired or been replaced.

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

On this Windows phone AVD, the Google Pixel Watch companion crashed with
`SecurityException: BLUETOOTH_CONNECT` until the user approved granting its
Nearby devices permissions. This was a companion setup failure; Pasingot had
no fatal crash or system ANR in that boot. Grant permissions only when the
operator authorizes that emulator setup change.
