# Quick Start device acceptance

User direction for Iteration 56: **use emulator**. Continue platform acceptance
on the dedicated copied phone/watch AVDs. Record emulator results separately
from Phase 4's physical-device exit checks. Audio intelligibility, physical
haptics, Bluetooth routing, battery consumption and Play delivery require their
own evidence; a native fixture or inventory does not establish those results.

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
