# Privacy

OpenMacro is built so that your automations, and the data they touch, stay on your device.

## What OpenMacro does not do

* No analytics, telemetry, crash reporting, advertising or attribution SDKs.
* No accounts, sign-in, licence checks or remote configuration.
* No Google Play Services or Firebase dependency.
* No cloud backup: `allowBackup="false"`, and the data-extraction rules exclude all app data
  from cloud backup and device-to-device transfer.

## Where your data lives

| Data | Location |
|---|---|
| Macros, triggers, constraints, actions | Local Room database inside the app's private storage |
| Global variables | Same database |
| Engine log | Android logcat (`OpenMacro` tag), on the device only |
| Photos taken by macros, files written by macros | The location you choose in the action |
| Backups | A JSON file you export through the system file picker, to wherever you save it |

## Network access

| Flavor | Network |
|---|---|
| **OpenMacro** (`dev.openmacro`) | **None.** The `INTERNET` permission is removed from the manifest, so Android blocks all sockets. |
| **OpenMacro Online** (`dev.openmacro.online`) | Only what your macros do: the URLs in HTTP, download and UDP actions; `api.open-meteo.com` for the weather action; and the local webhook server, which listens on port 8585 only while a webhook trigger is enabled. |

The webhook server is authenticated only by the token in its URL. Treat that token like a
password, and use the server only on networks you trust. Optional shared-secret
authentication is planned (see the [roadmap](v2-roadmap.md)).

## Permissions

OpenMacro declares the permissions its features need, but requests each one **only when you
add an item that uses it**. The powerful integrations are off until you turn them on in
system settings:

* **Accessibility service:** reads the screen and performs taps only for macros that use UI
  automation, the foreground-app trigger or the text-on-screen constraint.
* **Notification access:** reads notifications only to evaluate your notification triggers
  and constraints.
* **Shizuku / root / `WRITE_SECURE_SETTINGS`:** used only to run the system actions you
  configure.
* **Location, camera, SMS, phone, microphone-related audio:** used only by the matching
  triggers and actions.

`QUERY_ALL_PACKAGES` is used to list installed apps in the app picker and to detect app
install/uninstall triggers. The list never leaves the device.
