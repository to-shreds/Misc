# PathShift

Native Android batch regex renamer, with a metadata-only scan and a separate preview/commit step.

**Capabilities:** selectable working folder, recursive filename search, full relative-path regex replacements, capture groups, replace first/all, case-insensitive patterns, files/folders filters, per-item destination overrides, batch selection, collision/traversal checks, stale-preview rejection, safe within-volume directory moves, automatic creation of destination folders, stage-before-commit, and best-effort restoration on failure.

**Privacy:** No internet permission, analytics, cloud service, thumbnail decoding, document opening, or content reads. `Files.walkFileTree` only lists names and filesystem metadata. Saving uses in-volume `Files.move` operations, not content copies. The Android **All files access** permission is needed because the app can rename/move arbitrary paths, including folders. Android may still restrict protected system/application directories.

**User workflow:** Grant file access, browse for a working folder, enter a Java regex and a replacement (e.g. `^IMG_(\\d+)\\.jpg$` → `Photo_$1.jpg`), choose Filename or Full relative path, and tap **Preview changes**. All matches are selected by default. Uncheck any, or tap **Edit** to override the destination relative to your chosen folder. The app validates all final paths, including untouched items and descendants of renamed folders. **Save changes** and confirm to apply. No files change during searching, preview, or manual edits.

**How full-path matching works:** Paths are relative to the selected root, with `/` separators. For example, `^Camera/(.*)\\.jpg$` → `Sorted/$1.jpeg` moves matching files to `Sorted/` under the selected root. Enable **Include folders** to rename directories themselves. Parent folder changes are inherited by descendants automatically, without renaming them twice. Empty source folders are retained unless they were explicitly renamed. When multiple source paths map to the same destination, Save is disabled.

**Important limitations:** Use on user-accessible local/shared storage. The app cannot cross storage volumes or bypass Android's restricted directories. A batch is not a filesystem transaction: if Android or another app interrupts it, partial changes may require recovery. The app stages sources in a hidden `.pathshift-stage-*` folder inside the selected root, attempts to restore originals automatically if an operation fails, and keeps any unresolved stage directory for manual recovery. Never force-stop during Save. Android removable-storage/provider behavior can vary. No claims of device-tested behavior until actual device/emulator testing.

## Build

The app has no third-party dependencies and does not require Gradle. With JDK 17, Python 3, Android SDK platform 35, and build-tools 35.0.0 installed:

```sh
bash tests/run-tests.sh
bash build.sh
```

The APK is signed with an app-specific key saved outside the repository by default. Preserve that keystore and password if you want to install future updates without uninstalling the existing app. The GitHub Actions workflow signs in a temporary runner environment and uploads a private signing backup artifact, but that backup expires after 90 days. Copy it to private durable storage. Do not publish it. For subsequent builds, set `PATHSHIFT_KEYSTORE` and `PATHSHIFT_KEYPASS_FILE` to the original key/password. Do not check secrets into this public repository.

## Test matrix

`tests/RenameCoreTest.java` covers regex capture groups, case flags, recursive parent mappings, duplicate destinations, traversal rejection, real file swaps, directory renames, and stale-preview rejection. Source code for the local test run never reads file contents; the test harness reads its own fixture files only to check results. The APK build and package-signature verification run in GitHub Actions. Device permission and behavior still require real-device testing.
