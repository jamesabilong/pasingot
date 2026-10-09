# Pasingot Play internal testing

Prepared 2026-10-09 against checkpoint `c98fd59`. This guide prepares the actual
Play installation/update check; no app has been uploaded or published.

## Project preparation

The phone module is `android/app`; the watch module is `android/wear`. Both use
`app.personal.workouttracker`, currently `versionCode 1` and `versionName 1.0`.
Their release build types currently have no signing configuration. Current
debug APKs are for ADB acceptance and are not ready for a new Play upload.

1. Choose distinct release version codes, for example phone `1001` and watch
   `2001`, in the modules' `defaultConfig` blocks. Increase each module's code
   for its next upload. These are suggested values, not edits already made.
2. Keep the shared application ID. The watch manifest already declares the
   required watch feature and standalone setting. Recheck whether the final
   product's core watch functionality qualifies as standalone before release.
   [Wear packaging guidance](https://developer.android.com/training/wearables/packaging)
3. Open the `android` directory in Android Studio. Use **Build → Generate
   Signed Bundle / APK → Android App Bundle**. Build `app` and `wear` separately
   with the release variant and the same upload keystore. Back up the keystore
   securely; keep passwords and keys out of Git. Enable Play App Signing when
   creating the first release. The upload key signs submitted bundles; Play's
   app signing key signs the installed apps.
   [Android signing guide](https://developer.android.com/studio/publish/app-signing)
4. In the repository root, run `npm run cap:sync` before creating the phone
   bundle so it includes current web assets. Run the required TypeScript,
   PWA/native tests and both release builds. Record the source commit, versions,
   bundle paths, hashes and certificates. Inspect Gradle's actual output paths;
   the wizard normally puts bundles under each module's `build/outputs/`.

## Play Console setup

1. Sign in to [Play Console](https://play.google.com/console/) with your
   developer account. Create the Pasingot app if it does not exist.
2. Under **Test and release → Advanced settings → Form factors**, add **Wear
   OS** and supply the requested watch listing/screenshots. Use the same app
   listing for phone and watch; their delivered signing identity must match.
   [Wear distribution guidance](https://developer.android.com/training/wearables/packaging)
3. Under **Monitor and improve → App content**, complete the foreground service
   declaration for the phone's `mediaPlayback` service. Describe the explicitly
   started watch workout voice cues, their impact if delayed/interrupted, and
   provide a demo video showing phone **Send**, watch **Start**, audible cues
   while the phone app is in the background, the visible **Watch voice cues**
   notification and **Stop voice cues**, and teardown after completion. Select
   the matching use case or describe it manually if the provided choices do not
   fit. The declaration does not itself establish Play approval.
   [Play foreground service declaration](https://support.google.com/googleplay/android-developer/answer/13392821?hl=en)
4. Open **Test and release → Testing → Internal testing**. In **Testers**,
   create/select an email list containing the Google account used by your S25
   and Watch7. Save it and provide a feedback contact.
5. Create an internal release for the phone form factor and upload its signed
   AAB. Create the corresponding Wear OS internal release and upload its AAB.
   Check the selected form factor and artifacts before rolling out each release.
6. Resolve the Console's release validation messages, then roll out to internal
   testing. In **Testers**, copy the opt-in link. Open it while signed in as the
   tester, join the test, then use its Play Store link. Initial link availability
   can take time; an unpublished draft does not supply a usable opt-in link.
   [Internal testing instructions](https://support.google.com/googleplay/android-developer/answer/9845334?hl=en)

## Preserve current data before installing

The current physical installs are debug-signed. A Play-signed build generally
cannot update them in place because the signing certificate differs. Export
**Import → Export full backup** from the phone, retain it separately and verify
its contents before planning any uninstall. Watch local history/unfinished or
unsynced results need their own preservation: do not remove the watch app while
an owned or personal session/result is unfinished. A signing-conflict uninstall
is a separate destructive step and has not been authorized by this guide.
[Update signing requirements](https://developer.android.com/studio/publish/app-signing)

## Acceptance after both installations

Install from Play on the S25 and Watch7. Capture version, installer, signing
certificate and reciprocal peer evidence. Test one short Quick Start to actual
Ready/Started/completion/receipt cleanup. Publish higher version codes to the
same internal tracks, update both through Play, and verify retained data.
Also test compatible older/newer phone/watch combinations and the unsupported
capability-gated path. Close the Play/mixed-version checklist only after these
observations pass. Debug ADB installs or a successful upload do not close it.
