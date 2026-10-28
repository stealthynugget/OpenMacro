# Releasing

1. Bump `versionCode` (+1) and `versionName` in `app/build.gradle.kts`.
2. Build the signed APKs (the keystore and its properties file are never committed):
   ```bash
   ./gradlew :core:model:test :core:engine:testDebugUnitTest
   ./gradlew assembleOfflineRelease assembleOnlineRelease -PopenmacroKeystore=/secure/path/keystore.properties
   ```
3. Copy them into `releases/vX.Y.Z/` as `OpenMacro-vX.Y.Z-offline.apk` and
   `OpenMacro-vX.Y.Z-online.apk`, then generate checksums:
   ```bash
   cd releases/vX.Y.Z && sha256sum *.apk > SHA256SUMS.txt
   ```
4. Verify the signature and permissions:
   ```bash
   apksigner verify --print-certs OpenMacro-vX.Y.Z-offline.apk
   aapt2 dump permissions OpenMacro-vX.Y.Z-offline.apk | grep INTERNET   # must print nothing
   ```
5. Update the Download table in `README.md`, then commit and tag `vX.Y.Z`.
6. Optionally attach the same APKs to a GitHub Release:
   ```bash
   gh release create vX.Y.Z releases/vX.Y.Z/*.apk releases/vX.Y.Z/SHA256SUMS.txt --title "OpenMacro X.Y.Z" --notes-file CHANGELOG.md
   ```

**Always sign with the same key.** Android refuses to update an app signed with a different
key, so users would have to uninstall and lose their macros. Back up the keystore and its
passwords somewhere safe; if they are lost, no further update can be published to existing
installs.
