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
