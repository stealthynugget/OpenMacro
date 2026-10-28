# CLAUDE.md: OpenMacro

## Overview

OpenMacro is a privacy-hardened, open-source device-automation app for Android
(trigger → constraint → action), built clean-room from the *structure* of a reference APK.
**Never copy code, resources or assets from the reference APK.** Only structural metadata
(manifest, class and field names) informed the domain model. All logic is original.

v1 is complete: 44 triggers, 40 constraints, 101 actions, 16 categories, two flavors.
The reference test device is a Pixel 10a (Android 17 / SDK 37), not rooted, without Shizuku.

**Tech stack:** Kotlin 2.2.21 · Jetpack Compose + Material 3 (BOM 2025.10.01) · Room 2.8.3
(KSP) · DataStore · WorkManager · kotlinx-serialization 1.9 · coroutines · Shizuku API 13.1.5.
Gradle 8.14.3 with Kotlin DSL and a version catalog (`gradle/libs.versions.toml`), AGP 8.13.2,
JDK 17 target (builds on JDK 24). minSdk 26, target/compile 36. No DI framework: dependencies
are wired by hand in `OpenMacroApp` / `MacroEngine`.

## Commands

```bash
./gradlew :core:model:test                # main test suite (pure JVM, fast)
./gradlew :core:engine:testDebugUnitTest  # engine unit tests
./gradlew assembleOnlineDebug assembleOfflineDebug
./gradlew :app:installOnlineDebug         # install on the connected device
./gradlew :app:lintOnlineDebug            # Android lint (no ktlint/detekt configured)
adb shell am start -n dev.openmacro.online/dev.openmacro.MainActivity
adb logcat -s OpenMacro                   # engine log tag (EngineLog)
./gradlew assembleOfflineRelease assembleOnlineRelease -PopenmacroKeystore=<path>/keystore.properties
```

Release APKs live in `releases/vX.Y.Z/` with `SHA256SUMS.txt`; follow `docs/RELEASING.md`.
The signing keystore and its properties are kept **outside** the repo and must never be
committed (`*.jks`, `*.keystore` and `keystore.properties` are gitignored). Every release
must use the same key, or updates won't install over existing copies.

Run `:core:model:test` after **any** spec or catalog change. `CatalogTest` round-trips every
catalog item through JSON and catches most mistakes.

Windows notes: from Git Bash, set `export MSYS_NO_PATHCONV=1` before passing device paths to
`adb shell`, and use a Windows path (or PowerShell) for the *local* argument of `adb push`.
`local.properties` must use forward slashes.

## File organization

```
core/model/      pure Kotlin, no Android imports
  TriggerSpec.kt / ConstraintSpec.kt / ActionSpec.kt   sealed interfaces, one data class per item
  TriggerEvent.kt          runtime events + EventSource enum + SourceKey
  catalog/                 Definitions (ItemDef, trigger()/constraint()/action() builders),
                           Fields (editor descriptors), *Catalog.kt (one entry per spec),
                           Catalog (registry), BlockStructure (If/Loop nesting)
  SpecJson.kt              the only JSON codec for persisted specs
  text/                    MagicText ({tokens}), Expression (arithmetic)
  MacroExport.kt, MacroRefs.kt, SolarTime.kt, EvalContext.kt (DeviceProbe interface)
core/database/   Room entities, DAO, MacroRepository (incl. two-pass import)
core/engine/
  MacroEngine.kt, MacroExecutor.kt     dispatch, per-macro mutex, cancellation, loop guard
  service/       EngineService (FGS), EventSourceRegistry + *EventSource, WebhookServer,
                 QuickTileService, CameraCaptureService
  perform/       AndroidActionDispatcher → Device/App/Data/Network/Accessibility/SystemActions,
                 PrivilegedExecutor (Shizuku → root → framework), ShizukuBackend
  platform/      thin Android wrappers (torch, TTS, sound, camera, overlay, location, probe)
  runtime/       EvalContext impl, magic-text resolver, variables, trigger context
  accessibility/, notifications/, alarm/, permissions/, work/
app/             Compose UI: ui/dashboard, ui/editor, ui/catalog, ui/variables, ui/theme
  src/offline/   manifest that removes INTERNET
  src/online/    INTERNET, app_name "OpenMacro Online", orange launcher background
```

## Adding a trigger, constraint or action

1. Add a `@Serializable @SerialName("snake_case") data class` (or `data object`) to the sealed
   spec in `core/model`. **Every property needs a default.**
2. Register it in the matching `*Catalog.kt` with `trigger<…>` / `constraint<…>` / `action<…>`:
   category, emoji, title, description, `create`, `summary`, `fields`. Triggers also need
   `source` and `matches`; constraints need an evaluator; set `needsNetwork` / requirements.
3. Triggers: emit a `TriggerEvent` from an event source and map the `EventSource` in
   `EventSourceRegistry.subscribe` (or push it from a service).
4. Actions: handle the spec in the right `perform/*Actions.kt`. **A catalog item without a
   performer is a dummy.** v1 removed or fixed every one, so don't add new ones.
5. If `DeviceProbe` grows, update the fake in `CatalogTest.kt`.
6. Run `:core:model:test`, install, and verify on the device through logcat.

## Code style

* Kotlin official style (`kotlin.code.style=official`), 4-space indent. Match surrounding code.
* Comment density is low: KDoc on public types and non-obvious rules only. Explain *why*.
* Specs are named for what they configure (`SetVolume`, `BatteryLevel`). Serial names are snake_case.
* Files that touch many specs use the alias imports `ActionSpec as A`, `TriggerSpec as T`,
  `ConstraintSpec as C`, `TriggerEvent as E`.
* Action performers return `ActionOutcome.Success` or `ActionOutcome.Failed("human-readable
  reason")`. Failures must say what is missing ("needs Shizuku or root…"), never silently no-op.
* UI: Compose M3. Show feedback inline inside config dialogs, never in a snackbar, because the
  snackbar renders behind the modal dialog.

## Critical architectural decisions

* **Serial names are a storage contract.** Never rename or reuse an `@SerialName`. The
  discriminator is `"type"`, so a spec property literally named `type` crashes serialization.
  Use `@SerialName("x_type")` (already done for `var_type` and `sound_type`).
* **Lenient decoding.** `SpecJson.decode*` turns unreadable or removed types into
  `Unsupported(originalType, raw)` placeholders. They never match, never pass, and fail when run
  with a clear message. This exists because a single unknown stored type once crash-looped
  the app at startup. To remove an item, delete it; old rows degrade to placeholders on their own.
* **Declarative catalog.** The engine and UI never `when` over concrete specs for metadata;
  they look up `Catalog.trigger/constraint/action(spec)`.
* **Flat action list + `BlockStructure`** for If/ElseIf/Else/Loop rather than a tree, as in the
  reference model. The executor interprets the block roles.
* **Event sources are demand-driven.** `EventSourceRegistry.update()` opens only the
  `SourceKey`s that enabled macros need. Edge-detecting sources seed their state on open so a
  sticky broadcast never fires a macro. ALARM, INTERNAL, ACCESSIBILITY and NOTIFICATIONS are
  pushed by their own components.
* **Import re-links references.** Exported macros carry `sourceId`. `MacroRepository.import`
  inserts everything, then `MacroRefs.remap` rewrites macro references (nested conditions and
  per-action constraints too).
* **Privileged cascade.** `PrivilegedExecutor.shell()` tries Shizuku (reflection
  `newProcess`) and then `su`. `writeSetting` tries a framework write first.
* **Offline guarantee.** The offline manifest removes INTERNET with `tools:node="remove"`.
  Network items are hidden through `needsNetwork` + `BuildConfig.NETWORK`.

## Known quirks

* **Airplane trigger** combines a ContentObserver, an exported broadcast receiver and a 2 s
  poll, because `cmd connectivity airplane-mode` can change the setting without notifying
  either. To test it, use `settings put global airplane_mode_on 1/0`.
* On Android 11+ apps cannot toggle airplane, Wi-Fi, mobile data or NFC without Shizuku or root.
  `svc nfc` is a silent no-op on Pixel, so use `cmd nfc enable-nfc`.
* `Settings.System` writes only accept the platform's public key list. Custom keys fail by design.
* Receivers for broadcasts sent by other UIDs (NFC, custom intents) must be `RECEIVER_EXPORTED`.
  The dark-theme trigger uses `onConfigurationChanged`, not a broadcast.
* Package triggers and the app picker need `QUERY_ALL_PACKAGES`, because of package visibility.
* `FileEventSource` watches the nearest existing ancestor until the target exists, re-arms
  after delete/move, and debounces bursts for 700 ms.
* `MagicText` has no `{{` escape: a `{` followed by another `{` before its `}` is literal,
  which keeps JSON bodies intact.
* **Webhook server** (online, port 8585) authenticates only by its URL token.
* The Claude Code safety classifier blocks enabling accessibility services through
  `adb shell settings put secure enabled_accessibility_services`. Ask the user to toggle the
  service instead of working around it.

## Device-testing etiquette

* Never modify or delete the user's own macros. Delete only test macros you created.
* Record device settings before changing them and restore them afterwards (brightness, timeout,
  rotation, ringer, DND, airplane, location, NFC, Bluetooth, Wi-Fi, battery simulation).
* Don't send real SMS or place real calls, and don't browse the user's personal files in the
  system picker. Use its search box for your own test file.
* ADB UI driving: the dashboard FAB (~850,2245) overlaps the editor's Save button (~797,2266).
  Confirm which screen is showing before tapping coordinates. Locking the screen
  (`KEYCODE_SLEEP`) ends UI driving, because the device has a PIN.

## Technical debt

* No DI framework; hand wiring in `MacroEngine` is getting large.
* Room schemas are exported to `core/database/schemas/`, but migrations are hand-written and
  untested (DB v2, `MIGRATION_1_2`). Bump the version and add a migration for any entity change.
* Release builds aren't minified or signed for distribution.
* No instrumented/UI tests. Device verification is manual, through logcat markers.
* Some long performers (`SystemActions`, `DeviceActions`) would benefit from splitting.
* See `docs/v2-roadmap.md` for deferred features. Contributor workflow: `CONTRIBUTING.md`.
