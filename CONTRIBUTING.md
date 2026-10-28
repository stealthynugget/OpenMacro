# Contributing to OpenMacro

Thanks for helping! OpenMacro has a few firm principles. Please read this before opening a PR.

## Principles

1. **Privacy first.** No analytics, crash reporters, ads, accounts or remote config. Nothing
   that contacts a server the user didn't configure.
2. **The offline flavor stays offline.** Never add a dependency or feature that needs
   `INTERNET` to the offline build. Network items set `needsNetwork = true`.
3. **No dummy features.** An item must work, or fail with a clear reason (`ActionOutcome.Failed`).
   If the platform stops honouring something, remove it instead of leaving a no-op.
4. **Opt-in power.** Accessibility, notification access, Shizuku, root and secure settings are
   never required for the basics, and are never enabled without the user's action.
5. **Clean room.** Never copy code, resources or assets from other automation apps.

## Getting started

```bash
echo "sdk.dir=/path/to/Android/Sdk" > local.properties
./gradlew :core:model:test :core:engine:testDebugUnitTest
./gradlew :app:installOfflineDebug
adb logcat -s OpenMacro
```

JDK 17+ is required. CI builds both flavors and runs the unit tests on every push.

## Adding a trigger, constraint or action

1. Add a `@Serializable @SerialName("snake_case")` data class to `TriggerSpec`,
   `ConstraintSpec` or `ActionSpec` in `core/model`. **Give every property a default.**
2. Register it in `TriggerCatalog`, `ConstraintCatalog` or `ActionCatalog`: category, emoji,
   title, description, `create`, `summary`, `fields`, requirements, and `needsNetwork` if it
   uses the network.
3. Implement it:
   * **Trigger:** emit a `TriggerEvent` from a source in `EventSourceRegistry` (or push one
     from a service), and implement `matches`.
   * **Constraint:** implement the evaluator. Extend `DeviceProbe` if you need new device
     state, and update the fake in `CatalogTest`.
   * **Action:** handle the spec in the right `core/engine/.../perform/*Actions.kt`.
4. Run `./gradlew :core:model:test`. `CatalogTest` round-trips every item through JSON.
5. Verify on a device and describe how you tested it in the PR.

No UI code is needed: the editor renders `fields` generically.

## The unbreakable rules

* **Never rename or reuse an `@SerialName`.** Serial names are the storage format of every
  user's macros. To retire an item, delete it; stored copies degrade to "unsupported"
  placeholders by themselves.
* **Don't name a spec property `type`.** It collides with the JSON discriminator. Use
  `@SerialName("x_type")` on the property.
* **Database changes** need a Room version bump, a migration, and the exported schema in
  `core/database/schemas/`.

## Style

* Kotlin official code style, 4-space indent. Match the surrounding code.
* Keep comments sparse, and explain *why*, not *what*.
* User-facing failure messages say what is missing and how to fix it.
* Compose: show feedback inline inside dialogs, never in snackbars (they render behind dialogs).

## License of contributions

OpenMacro is licensed under [GPL-3.0](LICENSE). By submitting a pull request you agree that
your contribution is licensed under the same terms. Only contribute code you wrote yourself
or that comes from a GPL-3.0-compatible source, and say where it came from.

## Pull requests

* One focused change per PR, with tests where the logic is in `core/model`.
* Update `README.md` counts and feature tables when you add catalog items.
* No new permissions without explaining in the PR why they're needed.
