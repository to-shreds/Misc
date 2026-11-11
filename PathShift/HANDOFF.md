# PathShift handoff

## Purpose and controlling source
PathShift is a sideloadable native Android app for finding and regex-renaming files or whole relative paths, including folders, under a user-selected working directory. The authoritative source is `to-shreds/Misc/PathShift`, with package `com.pathshift.app`. Its readiness record is `to-shreds/ProjectStatus/projects/pathshift/STATUS.md`.

## Non-negotiable behavior
- Never inspect file contents, thumbnails, or file bytes. Scan names and metadata using `Files.walkFileTree`.
- Read-only **Find** mode is entirely separate from **Rename**. Find must never offer Save or call file-move operations.
- Rename plans stay in memory until **Save changes** plus **Save now** confirmation. Before Save, preview selected old/new paths and **all affected paths**, including descendants of renamed folders.
- On Save, reject stale scans, invalid paths, collisions and overwrites. Stage moves in a hidden same-root folder, perform best-effort automatic rollback after failure, and preserve unresolved stage files for recovery. A multi-file batch cannot be guaranteed atomic against device crashes.
- No internet permission, analytics, cloud processing, third-party SDKs or runtime libraries. Android 11+ All files access is user-granted, and platform-protected directories remain restricted.

## Architecture
- `app/src/main/java/com/pathshift/app/MainActivity.java`: Native Find/Rename UI, working-folder picker, live result filtering, path copy/browse action, regex transfer to Rename, preview and explicit Save flow.
- `app/src/main/java/com/pathshift/app/RenameCore.java`: Pure JVM metadata scanner, regex/literal Find matching and replacement planning, validation and collision checks.
- `app/src/main/java/com/pathshift/app/BatchCommit.java`: Pre-save stale validation, staged filesystem moves and best-effort rollback.
- `tests/RenameCoreTest.java`: Pure Java regression tests. `tests/android_smoke.py`: device Find/Preview/Save UI smoke test.
- `build.sh`: Direct SDK JDK17 build without Gradle/Maven; release signing identity is external to the public repository.

## Latest release: 0.1.4 (2026-11-11)
Find tab provides regex or literal matching; filename or full relative path; recursive, include-files/folders and ignore-case switches; match-count list, in-results substring filtering, full-path copy, change working folder to the found item's location, and `Use in Rename` to transfer search pattern/options. The new Find feature does not require a replacement and has no Save control. Rename remains backward-compatible, including `View all affected paths` and explicit Save confirmation.

Validated 49 pure JVM test assertions on local source and successful Android SDK build, APK version 0.1.4 (versionCode 5), sign and package checks in private GitHub Actions run `42978452858`. Signed APK SHA-256: `4b5d8fd3e002d6e97b062db17fc584892cdc3842ec068ecdb28c0b4fa198fecb`. Verified the exact private signing keystore matches v0.1.3, so 0.1.4 upgrades from 0.1.3 and 0.1.2. Earlier 0.1.0 used a different signer and cannot be updated in place. Source revision for the v0.1.4 Android release: `to-shreds/Misc` commit `edd608fe649ff4b7b966d3c157bb5eb2b12016b7`. Android 15 window-insets handling was added to keep Find, Rename, Preview and Save controls clear of the status/navigation bars and keyboard.

APK and complete source ZIP should be uploaded to ChatGPT File Library `/PathShift/` and privately archived under `to-shreds/ProjectStatus/projects/pathshift/artifacts/`. Private signing backup should remain under `/PathShift/private-signing/`, never in the public Misc repository or a public build artifact.

## Remaining verification / next steps
Android 15 emulator CI run `42978410563` **PASSED** the full read-only Find -> transfer expression into Rename -> preview -> confirmed Save workflow. The fixture file remained unchanged during Find and Preview, and was renamed only after Save. The test also asserted that Find results do not show Save, captured screenshots and checked AndroidRuntime for fatal exceptions. The 0.1.4 system-bar insets fix was visibly validated. No physical-phone test has yet been performed; Android vendor/device storage restrictions and interrupted-batch recovery remain areas to verify with disposable test folders. Do not describe a successful emulator or physical-device test without confirming its actual output. Test on disposable folders before important files. Some Android removable-storage or restricted directory operations may fail; staged rollback is best-effort.

After this release, update the project STATUS.md as the final persistence step, with the verified CI run, Library artifacts, remaining device QA and plain-English status.