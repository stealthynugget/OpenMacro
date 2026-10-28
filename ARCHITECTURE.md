# OpenMacro Architecture

OpenMacro is an on-device automation engine in the *trigger → constraint → action*
paradigm. This document describes how it is built.

> **Clean-room note.** The domain model was informed only by structural metadata
> (manifest, class and field names) of an existing automation app. No code, resources or
> assets were copied; every line of OpenMacro is original.

---

## 1. Module graph

```
:app ───────────────► :core:engine ─────► :core:database ─────► :core:model
 Compose M3 UI,        engine, FGS,         Room entities,         pure Kotlin:
 ViewModels,           event sources,       DAO, converters,       sealed specs, catalog,
 navigation,           performers,          repository,            JSON codec, magic text,
 flavor resources      privileged backends  global variables       solar math, export
```

* `:core:model` is JVM-only Kotlin with no Android imports, so the catalog, interpreter
  structure, serialization and text engine are unit-tested on the JVM.
* `:core:database` holds `OpenMacroDatabase` (Room, schema v2, exported to `schemas/`),
  `MacroDao` and `MacroRepository`.
* `:core:engine` holds everything that runs without UI.
* `:app` holds `MainActivity`, the dashboard, the editor, the catalog pickers and the
  variables screen.

There is no DI framework. `OpenMacroApp` owns a hand-written container, and services,
receivers and workers reach it through the `Application`.

---

## 2. Domain model

```
Macro
 ├─ id, name, description, enabled, constraintMode (ALL | ANY), runCount, lastRunAt
 ├─ triggers:    List<TriggerItem>     (TriggerSpec + enabled)
 ├─ constraints: List<ConstraintItem>  (ConstraintSpec + enabled)
 ├─ actions:     List<ActionItem>      (ActionSpec + enabled + per-action constraints)
 └─ localVariables
```

`TriggerSpec`, `ConstraintSpec` and `ActionSpec` are sealed interfaces with one
`@Serializable` data class per item. Control flow is **not a tree**. If / Else-if / Else /
End-if and Loop / End-loop are ordinary actions in a flat list, and `BlockStructure`
resolves their nesting.

### Serialization contract

* One codec: `SpecJson` (kotlinx-serialization, class discriminator `"type"`,
  `ignoreUnknownKeys`, `encodeDefaults`).
* `@SerialName` values are a **storage contract** and never change. A property literally
  named `type` would collide with the discriminator, so such properties are renamed with
  `@SerialName("x_type")`.
* **Lenient decoding.** An unreadable or removed type decodes to
  `Unsupported(originalType, raw)` instead of throwing. Placeholders never match and never
  pass, and they fail with a clear message when run. The editor shows them so the user can
  remove them. Constraint lists decode element by element, so one bad entry keeps its
  siblings.

---

## 3. Catalog

Every item is registered once as an `ItemDef` in `core/model/.../catalog`:

```
ItemDef { category, emoji, title, description, create(), summarize(spec),
          fields: List<Field<S>>, requirements(spec), needsNetwork }
  TriggerDef    + source(spec): SourceKey, matches(spec, event), nextOccurrence(spec, now)
  ConstraintDef + evaluate(spec, EvalContext)
  ActionDef     + block: BlockRole
```

* `Field<S>` is a declarative editor descriptor: choice, multi-choice, int, slider, text
  with magic text, bool, time, variable, macro, app, location, nested conditions, info.
  `ui/editor/FieldEditors.kt` renders any field generically, so **a new item needs no UI
  code**. Nested conditions recurse, which is how Logic groups and If/Loop conditions are
  edited.
* `Catalog` is the registry: lookup by spec class, grouping by category, and filtering out
  network items on the offline flavor. A startup check and `CatalogTest` assert that every
  item resolves and round-trips through `SpecJson`.
* v1 size: 44 triggers, 40 constraints, 101 actions in 16 categories.

---

## 4. Persistence

```
macros (MacroEntity)
  id PK · name · description · enabled · constraintMode · runCount · lastRunAt · timestamps
   ├──< triggers    (TriggerEntity)    macroId FK CASCADE · position · enabled · spec JSON
   ├──< actions     (ActionEntity)     macroId FK CASCADE · position · enabled · spec JSON
   │                                   · constraints JSON (per-action gating)
   └──< constraints (ConstraintEntity) macroId FK CASCADE · position · enabled · spec JSON
global_variables  (GlobalVariableEntity) name · type · value
```

* `MacroWithItems` (`@Embedded` + `@Relation`s) is the read model. Saving is one
  `@Transaction`: upsert the parent, then replace its children in order.
* Adding a spec type is a new `@SerialName`, never a schema migration.
* Preferences (the master engine switch) live in DataStore.
* `allowBackup="false"` and `data_extraction_rules.xml` keep data out of cloud backup and
  device transfer.

### Export / import

`MacroBackup` is plain JSON. Each exported macro carries its `sourceId`.
`MacroRepository.import` works in two passes. First it inserts every macro and maps
`sourceId` (falling back to name) to the new id. Then `MacroRefs.remap` rewrites every
macro reference: Run, Enable and Cancel macro, the Macro-finished and Macro-enabled
triggers, and the Macro-enabled and Macro-running constraints, including references inside
nested Logic/If/Loop conditions and per-action constraints.

---

## 5. Runtime engine

```
 ┌──────────────── EngineService (foreground service, specialUse) ─────────────────┐
 │ EventSourceRegistry: opens only the SourceKeys enabled macros need              │
 │   broadcast receivers · NetworkCallback · ContentObservers · FileObserver ·      │
 │   SensorManager · LocationManager · WebhookServer · ComponentCallbacks           │
 └──────────────┬──────────────────────────────────────────────────────────────────┘
                │ TriggerEvent      ▲ pushed: AccessibilityService, NotificationListener,
                ▼                   │ QuickTileService, AlarmReceiver, MacroExecutor (internal)
        MacroEngine.events (SharedFlow)
                │
        dispatch(event): for each enabled macro
            any enabled trigger Catalog.matches(spec, event)?
            macro constraints   Catalog.evaluate(…) combined ALL | ANY?
                │ yes
                ▼
        MacroExecutor.execute(macro, trigger context)
            InvocationRateLimiter: at most 10 runs/s per macro (loop guard)
            per-macro Mutex: re-triggers queue FIFO
            interpreter over the flat action list + BlockStructure jump table
               action constraints fail → SKIPPED
               AndroidActionDispatcher.perform(spec) → Success | Failed(reason)
            invocations map → cancel(macroId) cancels the running coroutine
            recordRun · emits MacroFinished
```

### 5.1 Event sources

Each trigger maps to a `SourceKey(source, param)`. `EventSourceRegistry.update(required)`
diffs the required set against the open subscriptions and opens or closes receivers by set
difference, so the engine listens only to what is in use. Edge-detecting sources (battery,
Wi-Fi, airplane mode and similar) seed their state when opened, so the initial sticky
broadcast never fires a macro.

Notable implementations:

| Source | Mechanism |
|---|---|
| Wi-Fi | `ConnectivityManager.NetworkCallback` (SSID requires location permission) |
| Airplane mode | ContentObserver + exported broadcast receiver + 2 s poll (some system paths notify neither) |
| Dark theme | `ComponentCallbacks.onConfigurationChanged` |
| NFC, custom intents | `RECEIVER_EXPORTED`, because the sender is another UID |
| Packages | `PACKAGE_ADDED/REMOVED` (needs `QUERY_ALL_PACKAGES` for visibility) |
| Files | `FileObserver` on the nearest existing ancestor; re-arms after delete/move; 700 ms debounce |
| Sensors | accelerometer (shake, flip), proximity, light, step counter |
| Geofence | `LocationManager` updates, no Play Services |
| Webhook | embedded HTTP server on port 8585 (online flavor) |

### 5.2 Time

`AlarmScheduler` keeps **one** exact alarm: the earliest next occurrence across all time
triggers (time of day, regular interval, sunrise/sunset). `SolarTime` computes sunrise and
sunset with standard solar-position equations, fully offline. Alarms are re-armed on boot, package
replace, time or zone change, a change in exact-alarm permission, and by a periodic
`EngineMaintenanceWorker`.

### 5.3 Foreground service

The engine service uses type `specialUse`, because it is an always-on event listener and
neither `dataSync` nor `connectedDevice` fits. The activity starts it while the master
switch is on, and `SystemEventReceiver` starts it on `BOOT_COMPLETED` /
`MY_PACKAGE_REPLACED`. Camera capture runs in a separate short-lived `camera`-type
foreground service, so photos work from the background.

### 5.4 Action performers

`AndroidActionDispatcher` routes each spec through `DeviceActions`, `AppActions`,
`DataActions`, `NetworkActions`, `AccessibilityActions` and `SystemActions`. The first one
that handles the spec wins. An unhandled spec returns `Failed`, never a silent success.

### 5.5 Privileged backends

`PrivilegedExecutor.shell(cmd)` tries **Shizuku** (`ShizukuBackend`: reflection on
`Shizuku.newProcess`, degrading to null if unavailable), then **root** (`su -c`).
`writeSetting` first tries a framework write, which needs `WRITE_SECURE_SETTINGS` for
Secure/Global (`Settings.System` accepts only platform keys), then falls back to the shell.
On Android 11+ Wi-Fi, mobile data, airplane and NFC toggles need the shell path. Without it
the action fails with a message saying so.

### 5.6 Variables and magic text

Local variables live on the macro. Global variables live in a Room table, are cached, and
emit changes that fire variable-changed triggers. `MagicText` resolves tokens such as
`{battery}`, `{time}`, `{trigger}`, `{lv=x}`, `{v=x}`, `{notification_text}` and
`{webhook_body}`. A `{` followed by another `{` before its `}` is literal, which keeps JSON
bodies intact while `{"x":{battery}}` still resolves. `Expression` evaluates arithmetic for
numeric assignments.

---

## 6. Flavors and permissions

* `offline` (`dev.openmacro`): `src/offline/AndroidManifest.xml` removes `INTERNET` with
  `tools:node="remove"`, so no dependency can reintroduce it.
* `online` (`dev.openmacro.online`): `src/online` adds `INTERNET`, plus its own app name and
  launcher colour.
* `BuildConfig.NETWORK` hides catalog items with `needsNetwork` on the offline flavor.

Runtime and special permissions are requested only when a macro uses an item that declares
them (`Requirement`s on each `ItemDef`). Dangerous capabilities (accessibility, notification
access, overlay, exact alarms, secure settings, Shizuku, root) are all opt-in.
