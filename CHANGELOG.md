# Changelog

## 1.0.0 (2026-10-28)

First public release.

* 44 triggers, 40 constraints and 101 actions in 16 categories, all from one declarative catalog.
* Two apps: **OpenMacro** (no network permission at all) and **OpenMacro Online** (HTTP,
  UDP, an on-device webhook server, downloads, weather).
* Control flow (If / Else-if / Else, loops, break/continue), local and global variables,
  magic text with arithmetic, per-action constraints.
* Opt-in integrations: accessibility (UI automation), notification access, Shizuku, root.
* Developer actions: hashes, Base64/URL encoding, UUID/random, JSON parsing, date formatting, shell.
* On-device sunrise/sunset, geofencing without Google Play Services, background photo capture.
* JSON backup and restore that re-links macro-to-macro references. Unknown items from other
  versions load as placeholders instead of crashing.
* No ads, accounts, analytics or cloud backup.
