# OpenMacro v2 Roadmap

## What v1 achieved

* **Catalog:** 44 triggers, 40 constraints and 101 actions in 16 categories, all declarative
  and rendered by one generic editor. The reference APK had 143 / 89 / 230. v1 covers the
  everyday core plus extras the reference lacks (dev-data actions, webhook server, JSON parsing).
* **Engine:** foreground-service daemon, demand-driven event sources, block-structured
  interpreter (If / Else-if / Else, loops, break/continue), per-macro FIFO, loop guard,
  per-action constraints, local and global variables, magic text with expressions, real
  cancellation.
* **Integrations:** accessibility (UI automation), notification listener, Shizuku and root
  backends, camera capture via a foreground service, overlay, TTS, geofencing without Play
  Services, sunrise/sunset from on-device solar math.
* **Two flavors:** a provably network-free `offline` build and an `online` build. They have
  distinct names and icons.
* **Robustness:** lenient decoding with `Unsupported` placeholders (no crash on unknown stored
  types), and an export/import that re-links macro references.
* **Verified on a Pixel 10a (Android 17):** a live sweep of the webhook, data, network, file,
  logic, macro-chaining, constraint, battery, power/USB, Wi-Fi, Bluetooth, location, NFC,
  airplane, dark theme, package, file-watch, notification, intent, Quick Settings tile,
  camera, overlay, audio and display items. 14 bugs found in the sweep were fixed, and every
  dummy feature was either implemented or removed.

## Known limitations

| Limitation | Cause | Notes |
|---|---|---|
| Wi-Fi, mobile data, airplane, NFC toggles, key events, force stop fail without Shizuku/root | Android 11+ platform restriction | Clear failure message; Shizuku path not yet device-tested |
| Root actions untested | Test device not rooted | Code path exists (`su -c`) |
| Accessibility features untested in the online flavor | Service must be toggled by the user | Verified earlier in the offline flavor |
| Sensor triggers (flip, proximity, light, steps) mostly untested | Need physical interaction | Shake verified |
| Geofence enter/exit untested | Needs movement or a mock location provider | Fetching the current location is verified |
| Photo with the screen off untested | Locking the screen stops ADB UI driving | Screen-on capture verified |
| `Settings.System` custom keys can't be written | Platform allow-list | By design |
| Webhook server authenticates only by URL token | v1 scope | See P1 |
| SMS / call actions not live-tested | Real-world side effects deliberately avoided | |

## Deferred features

* Remaining reference-catalog items: calendar events, cell tower / mobile signal, media
  button and volume-button triggers, Wi-Fi SSID scan list, app shortcut / home-screen widget
  invocation, music-playing details, screen recording, keyboard/IME actions, Bluetooth
  device-specific actions, email (online).
* Macro categories/folders, favourites, search, and a per-macro run log viewer in the UI.
* Templates / example macros on first launch.
* Action and trigger enable/disable toggles inside a macro; comments.
* OCR / screen-text from images. Skipped because ML Kit downloads models, which conflicts
  with the offline promise. Needs an on-device-only alternative.

## Priorities for v2

**P0: reliability and safety**
1. Test the Shizuku path end to end (install Shizuku and run the privileged actions).
2. Instrumented tests for `EventSourceRegistry` and performers. Make the logcat-marker test
   suite a repeatable script.
3. A Room migration test, and schema history checked in.
4. Release build: R8/minify with keep rules for kotlinx-serialization and Shizuku, plus signing config.

**P1: security and privacy**
1. Optional shared secret (header or query parameter) for the webhook server; bind to
   localhost by default with an opt-in LAN mode.
2. A per-macro permission summary in the editor (which grants a macro needs and why).
3. An audit of exported components (`RECEIVER_EXPORTED` receivers, the tile service, providers).

**P2: features and UX**
1. Run log / history screen built on the existing `EngineLog` events.
2. Macro categories, search and favourites. Duplicate macro.
3. The deferred catalog items above, starting with the most-used: calendar, volume/media
   buttons, widgets and shortcuts.
4. Example macros and onboarding that explains the four opt-in integrations.

**P3: maintainability**
1. Lightweight DI (or split `MacroEngine` wiring into factories).
2. Split the large performers (`SystemActions`, `DeviceActions`) by concern.
3. Add ktlint or detekt to CI.
