<div align="center">

# ⚡ OpenMacro

**Open-source Android automation that runs on your device and stays there.**

*When this happens → if these conditions hold → do that.*

44 triggers · 40 constraints · 101 actions · no ads · no account · no telemetry

![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)
![Telemetry](https://img.shields.io/badge/telemetry-none-success)
[![License: GPL v3](https://img.shields.io/badge/license-GPLv3-blue)](LICENSE)

</div>

---

## 📥 Download

| | Version | APK | Network |
|---|---|---|---|
| 🔒 **OpenMacro** (recommended) | 1.0.0 | [**OpenMacro-v1.0.0-offline.apk**](releases/v1.0.0/OpenMacro-v1.0.0-offline.apk?raw=1) | none: cannot access the internet |
| 🌐 **OpenMacro Online** | 1.0.0 | [**OpenMacro-v1.0.0-online.apk**](releases/v1.0.0/OpenMacro-v1.0.0-online.apk?raw=1) | HTTP, webhooks, weather, downloads |

Requires **Android 8.0+**. The two apps install side by side; pick one, or use both.

**How to install**
1. Download the APK on your phone (or copy it over).
2. Open it. If Android asks, allow *Install unknown apps* for your browser or file manager.
3. Open OpenMacro, tap **New macro**, and grant permissions only when an item asks for them.

**Verify your download** (optional): compare against
[`SHA256SUMS.txt`](releases/v1.0.0/SHA256SUMS.txt). The APKs are signed with this
certificate (SHA-256):
`33:e5:63:e3:f1:1c:0a:a2:b3:3e:2f:13:ca:f9:e6:f0:8a:e2:b4:10:d6:1b:5a:7b:a4:f3:21:04:c6:64:e1:56`

```bash
apksigner verify --print-certs OpenMacro-v1.0.0-offline.apk
```

> Updates are signed with the same key, so a new version installs over the old one and keeps
> your macros. If you built a debug version yourself, uninstall it first, because the
> signatures differ.

---

## Why OpenMacro?

Most automation apps on Android are closed-source. Many have ads, a subscription or an
account, and they ask for network access even when you only want your phone to turn on the
torch in the dark. OpenMacro takes a different approach:

### 🔒 A build that *physically cannot* go online
The default `offline` flavor strips the `INTERNET` permission at the manifest level with
`tools:node="remove"`. No library can add it back. That's not a promise in a privacy policy:
**Android itself blocks every socket.** You can check it yourself:

```bash
aapt2 dump permissions app-offline-release.apk | grep INTERNET   # → nothing
```

Need HTTP requests or webhooks? Install the separate `online` flavor ("OpenMacro Online",
orange icon). It has its own data and permissions and still has zero telemetry.

### 🌐 A webhook server on your phone, with no cloud relay
The online flavor runs a small HTTP server on the device (port 8585). Any device on your
network can trigger a macro with `curl http://<phone>:8585/<token>`. The request body and
method become magic-text variables. No third-party relay server and no account are involved.
The server runs only while an enabled macro uses a webhook trigger.

### 🧑‍💻 Built-in developer tools
Hashes (MD5/SHA-1/SHA-256), Base64 and URL encoding, UUID and random values, **JSON parsing
into variables**, date formatting, arithmetic expressions, clipboard, shell commands and
HTTP requests are all native actions. Chain them into small on-device pipelines without
plugins.

### 📍 Location features without Google Play Services
Geofences use the platform `LocationManager`, so they work on de-Googled ROMs. **Sunrise and
sunset triggers are computed on-device** with solar equations: no weather API, no network.
Unit tests check the results against reference data to within 5 minutes.

### 🔓 Power features without root
Privileged actions (Wi-Fi, mobile data, airplane mode, NFC, key events, force stop, system
settings) use the best backend available: **[Shizuku](https://shizuku.rikka.app/)** (ADB
level, no root) → root → a one-time `adb pm grant`. Each integration is opt-in and off by
default.

### ✅ No dummy features
Every item in the catalog does something real. When an action can't run, for example
because it needs Shizuku, root or a permission you haven't granted, it **fails loudly and
says why**. It never pretends to succeed. Features the platform no longer honours have been
removed rather than left in as placebo.

### 🛡️ Storage that survives updates
Macros are stored as typed JSON. If an update removes an item, old macros don't crash the
app: the item becomes a visible "unsupported" placeholder that you can edit out. Export
and import are plain, human-readable JSON. **Macro-to-macro links are re-wired on import**,
so a backup restores as a working set rather than a pile of broken references.

### 🔋 Light on battery by design
The engine subscribes only to the system events your enabled macros actually use. A macro
set that only reacts to charging never wakes up for Wi-Fi changes.

---

## Features

<details open>
<summary><b>Triggers (44)</b>: when a macro runs</summary>

| Category | Triggers |
|---|---|
| Battery & power | Battery level, temperature, battery full, battery saver, power connected/disconnected, USB, dock |
| Connectivity | Wi-Fi (incl. a specific SSID), Bluetooth (adapter, device), airplane mode, NFC, SIM state, location services |
| Device | Screen on/off, unlocked, dark theme, headphones, doze, ringer mode, boot, low storage, time-zone change |
| Date & time | Time of day (weekdays), regular interval, sunrise/sunset (with offset) |
| Apps & web | App installed/removed, app in foreground, custom broadcast intent, **webhook** |
| Phone & notifications | Notification posted (with filters), SMS received, call states |
| Sensors & location | Shake, flip, proximity, light level, step count, geofence enter/exit |
| OpenMacro | Quick Settings tile, manual (no trigger), macro finished, macro enabled, variable changed, file changed |

</details>

<details>
<summary><b>Constraints (40)</b>: only run if…</summary>

Time window (wraps midnight), weekday, day of month, month, daytime/night · device locked,
screen, orientation · battery level and temperature, charging and charging method, battery
saver, doze · Wi-Fi, Bluetooth, airplane, roaming, VPN, NFC, location · headphones,
auto-rotate, brightness, auto-brightness, dark theme, torch · ringer mode, volume, music
playing, call state · foreground app, **text visible on screen**, notification present ·
macro enabled/running, last run, time since boot, variable comparison (incl. regex) ·
nested **AND / OR / NOT / XOR** groups. Constraints can gate the whole macro *or individual
actions*.

</details>

<details>
<summary><b>Actions (101)</b>: what a macro does</summary>

| Category | Actions |
|---|---|
| Logic | If / Else-if / Else, count & while loops, break, continue, stop, wait (fixed or random) |
| Variables & data | Set/clear/increment/append variables, text manipulation, encode/decode, hash, UUID/random/timestamp, parse JSON, format date, clipboard, log |
| Device | Torch & strobe, vibrate patterns, **take photo** (front or back, also from the background), ringtone, keep awake, car mode |
| Audio | Volume, volume step, ringer mode, Do Not Disturb, media control, play sound, text-to-speech, say time, mute mic, speakerphone |
| Display | Brightness, screen timeout, auto-rotate, rotation, font scale, wallpaper, overlay text |
| Apps | Launch app, open URL/maps/dialer/assistant/camera/settings, send intent, share text, set alarm/timer, app info, uninstall, force stop, kill background apps |
| Phone | Call, SMS, answer/end call |
| Files | Write, append, read, copy, move, rename, delete |
| Web (online) | HTTP request (any method, headers, body → variable), UDP, connectivity check, download file, weather (Open-Meteo, no API key), get location |
| UI automation | Press Back/Home/Recents/screenshot, tap X/Y, **tap on text**, swipe, type text, read screen into a variable |
| System | Shell (optionally root), Wi-Fi, mobile data, airplane, NFC, location mode, reboot, key events, write any system/secure/global setting |
| OpenMacro | Run/enable/disable/cancel macros, notification, toast, clear notifications, engine on/off |

</details>

**Also included:** local and global variables · `{magic text}` such as `{battery}`, `{time}`,
`{notification_text}`, `{lv=myVar}`, with arithmetic · per-macro FIFO execution · a loop
guard (at most 10 runs per second per macro) · a "Test actions" button in the editor · a
global-variable manager · JSON backup and restore through the system file picker.

---

## Opt-in integrations

Everything below is **off until you enable it** in system settings. The in-app "Grant"
button takes you to the right screen.

| Integration | Unlocks |
|---|---|
| Accessibility service | UI automation actions, "app in foreground" trigger, "text on screen" constraint |
| Notification access | Notification trigger and constraint, clear notifications, notification magic text |
| Shizuku *or* root | Wi-Fi / mobile data / airplane / NFC toggles, key events, force stop, root shell |
| `WRITE_SECURE_SETTINGS` (one-time adb grant) | Secure/Global settings writes, location mode |

```bash
adb shell pm grant dev.openmacro android.permission.WRITE_SECURE_SETTINGS
```

---

## Install & build

**Requirements:** JDK 17+ · Android SDK platform 36 · a device running Android 8.0+ (API 26).

```bash
git clone https://github.com/stealthynugget/OpenMacro.git && cd OpenMacro
echo "sdk.dir=/path/to/Android/Sdk" > local.properties   # use forward slashes on Windows

./gradlew :app:installOfflineDebug     # network-free build  (dev.openmacro)
./gradlew :app:installOnlineDebug      # network build       (dev.openmacro.online)
```

| Flavor | Package | `INTERNET` | Name |
|---|---|---|---|
| `offline` | `dev.openmacro` | removed | OpenMacro |
| `online` | `dev.openmacro.online` | yes | OpenMacro Online |

Both can be installed side by side.

### Signed release build

Release signing reads a `keystore.properties` file that lives **outside** the repository:

```properties
storeFile=/secure/path/openmacro-release.jks
storePassword=…
keyAlias=openmacro
keyPassword=…
```

```bash
./gradlew assembleOfflineRelease assembleOnlineRelease -PopenmacroKeystore=/secure/path/keystore.properties
# or: export OPENMACRO_KEYSTORE=/secure/path/keystore.properties
```

Without it, release APKs are built unsigned. See [docs/RELEASING.md](docs/RELEASING.md).

### Tests

```bash
./gradlew :core:model:test                 # catalog, serialization, interpreter, magic text, solar math
./gradlew :core:engine:testDebugUnitTest   # engine
./gradlew :app:lintOnlineDebug             # Android lint
```

### Trying it from adb

```bash
adb logcat -s OpenMacro                                  # engine log (stays on the device)
adb shell dumpsys battery set ac 1                       # fake "power connected"
adb shell dumpsys battery reset
adb shell am broadcast -a my.custom.ACTION --es k hello  # custom-intent trigger
adb forward tcp:8585 tcp:8585 && curl -d 'hi' http://127.0.0.1:8585/mytoken   # webhook (online)
```

---

## Architecture at a glance

```
:app  ──►  :core:engine  ──►  :core:database  ──►  :core:model
Compose    foreground service,  Room (v2),          pure Kotlin: sealed specs,
M3 UI      event sources,       repository,         declarative catalog, JSON codec,
           performers,          global variables    magic text, solar math
           Shizuku/root
```

Every trigger, constraint and action is **one declarative catalog entry**: metadata, editor
fields, matching and evaluation. The editor renders all of them generically, so adding an
item needs no UI code. Full details are in [ARCHITECTURE.md](ARCHITECTURE.md).

**Stack:** Kotlin 2.2 · Jetpack Compose + Material 3 · Room · DataStore · WorkManager ·
kotlinx-serialization · coroutines · Shizuku API. No DI framework, no Google Play Services,
no Firebase, no analytics SDKs.

---

## Privacy

* No analytics, crash reporting, ads, accounts or licence checks.
* Macros and variables live in a local Room database. Cloud backup and device transfer are
  disabled (`allowBackup="false"` plus data-extraction rules).
* The offline flavor has no network permission at all.
* The online flavor contacts only the URLs you put in your macros (plus Open-Meteo, if you
  use the weather action).

See [docs/PRIVACY.md](docs/PRIVACY.md).

---

## Roadmap

v1 is feature-complete and was verified on a Pixel 10a running Android 17. Next up: an
end-to-end Shizuku test pass, webhook authentication, a run-history screen, macro folders
and search, and more triggers (calendar, media buttons, widgets). See
[docs/v2-roadmap.md](docs/v2-roadmap.md).

## Contributing

Issues and pull requests are welcome. Read [CONTRIBUTING.md](CONTRIBUTING.md) first. It
covers the catalog workflow and the one unbreakable rule: **never rename a serial name.**

## License

OpenMacro is free software, licensed under the [GNU General Public License v3.0](LICENSE).
You may use, study, share and modify it. If you distribute a modified version, you must
release its source code under the same license. Nobody can ship a closed-source copy of
OpenMacro.

Copyright © 2026 stealthynugget and contributors.

## Acknowledgements

Inspired by the trigger → constraint → action model popularised by existing Android
automation apps. OpenMacro is an independent clean-room implementation and is not
affiliated with any of them.
