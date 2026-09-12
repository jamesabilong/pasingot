# Website/PWA → Wear OS feasibility

Reviewed 2026-09-12. **Possible with a new transport; not implemented in the
current app.** Installing the browser PWA does not grant Android native APIs.
This assessment concerns Wear OS, not Apple Watch, Garmin, or other platforms.

## Current implementation

The Android phone app wraps the PWA with Capacitor. `ScheduleSync` copies the
schedule into native storage, and `WearSyncClient` sends today's exercises over
Google Play services' Wearable Data Layer. The watch requests downloads through
the same transport, saves them locally, and queues completion records for the
phone. A browser has no `ScheduleSync` plugin. No browser relay endpoint or
custom BLE GATT service is implemented.

Google documents Data Layer access for Wear OS watches and paired Android
devices only, with matching package names and signing certificates. It is not
a browser API, even when Data Layer itself routes through Google's cloud.
See [Data Layer overview](https://developer.android.com/training/wearables/data/overview).

## Feasible routes

| Route | Pasingot Android phone app needed? | Implementation status |
|---|---|---|
| Browser backup → Android app restore → paired watch | Yes | Existing manual workflow; not automatic cross-device sync |
| Website/PWA → authenticated HTTPS service → Wear OS app | No, for this app's sync route | Feasible; needs a backend, pairing/authentication, and new web/watch clients |
| Browser → nearby custom BLE service on watch | Potentially not | Experimental candidate only; requires watch hardware/API proof, a new protocol, and compatible browsers |

For a browser-first product, the HTTPS route is the proposed direction, not an
approved stage. Wear OS apps can use network APIs directly; when no phone is
available, Wi-Fi or cellular availability depends on the watch hardware. A
Pasingot watch app is still required. Device setup/pairing requirements are
separate from whether this app's synchronization needs a phone companion.
See [Wear OS network communication](https://developer.android.com/training/wearables/data/network-communication).

Web Bluetooth can connect a supported browser to a BLE GATT server, with HTTPS
and an explicit user gesture/device chooser. It does not speak Wear Data Layer
or expose our watch app's existing storage. A custom watch service, secure
pairing, payload framing, retries and acknowledgements would all be new work.
Browser/hardware coverage and lifecycle limits must be proven on target devices;
it must not be promised as a universal PWA feature. Google's Data Layer guidance
also cautions against alternative low-level phone/watch communication channels.
See [Chrome Web Bluetooth guidance](https://developer.chrome.com/docs/capabilities/bluetooth)
and the Data Layer overview above. This is a possible investigation, not a
validated or recommended production implementation.

## Use the existing route safely

1. In the source browser, choose **Import → Export full backup**.
2. Transfer the file to the Android phone yourself.
3. Export a backup of the Android app's existing data if you need to retain it.
   **Restore replaces local data; it does not merge databases.**
4. In the Pasingot Android app, use **Import → Restore backup**.
5. Install Pasingot on the paired Wear OS watch, then use **Today → Send today
   to watch** in the Android app. A Library draft must first become a schedule.
6. Confirm receipt on the watch. Browser data and Android data remain separate;
   logs returning from the watch go to the Android app, not the source browser.

## Gaps before automatic browser sync can be called complete

- Select transport, supported devices/browsers, and hosting/privacy scope.
- Pair devices securely; authenticate and authorize each transfer, with token
  expiry/revocation. Do not publish workout data at guessable URLs.
- Define stable cross-device IDs (local database row IDs are not global),
  schema/version compatibility, date/time-zone handling, and conflict rules.
- Reuse workout payload semantics, validate inbound data, preserve active watch
  progress, and distinguish queued delivery from acknowledged persistence.
- Persist retry queues on both ends and deduplicate logs/receipts across
  reconnects without requiring a browser tab to remain open in the background.
- Keep local workouts usable offline. Handle watch background/battery limits.
- Verify pairing, offline/reconnect, duplicate delivery, stale data, revocation,
  malformed payloads, and interrupted sessions on a real watch. Emulators/mock
  tests help with development but do not establish Bluetooth/battery acceptance.

No new network service, cloud account, Bluetooth permission, or transfer of
personal workout data was introduced by this feasibility/capability correction.
