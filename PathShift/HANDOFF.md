# PathShift handoff

## Objective and controlling sources
PathShift is an offline, native Android file finder, batch renamer and folder organizer. Authoritative code is `to-shreds/Misc/PathShift`, current validated release source commit `7e89add688334b1431baebd197ac989823c8dd71` (v0.2.0, versionCode 6). Project readiness is recorded in `to-shreds/ProjectStatus/projects/pathshift/STATUS.md`; the private release build workflow is `to-shreds/ProjectStatus/.github/workflows/pathshift-private-release.yml`.

## Non-negotiables
- Only scan names and filesystem metadata; never open, decode or copy file contents.
- Find is read-only. Rename and Presets do not touch disk until Preview, **Save changes**, and separate **Save now** confirmation.
- Check duplicate destinations, existing files, invalid/escaping paths, stale scans, restricted paths, folder inheritance, source/destination volume compatibility, and unexpected concurrent mutations before moving files.
- Moves use same-volume staging and best-effort restoration. Multi-file operations are **not atomic** against power failures, forced shutdowns or storage corruption. Never promise guaranteed rollback.
- Android 11+ user-granted All files access, no internet permission or external dependencies, no private keys in the public repository.
- Preserve the private signing certificate to allow app updates without uninstalling. v0.1.0 used a different signing identity, but v0.1.2 through v0.2.0 use the same certificate.

## Release v0.2.0 (2026-11-11)
The app has tabs for Find, Rename and Presets. Find supports literal/regex matching, filenames or relative paths, filters and copying paths. Rename supports partial-text replacement or **Set complete resulting destination**, `$0`, `$1`, etc., plus `${filename}`, `${basename}`, `${ext}`, `${folder}`, `${path}` variables. Rename preview shows all impacted paths, grouped destination collisions, and a numbering option for duplicates.

Eight presets: Move into a new folder (choose parent destination folder with an actual folder browser, name the new folder), Sort by extension, Add prefix, Add suffix, Number files, Replace plain text, Lowercase, and Convert spaces to underscores. Optional recursive scope, filter, and preserve/flatten subfolders. Presets only move files, not whole folder trees. External destinations work on the **same storage volume**; no cross-volume content copying. Unmatched files are preserved, conflicts block Save, and new output folders are not created before Save.

An emulator-detected bug was corrected: Android's `Files.getFileStore` can throw an `IOException("getFileStore")` for ordinary shared storage, before the prior fallback ran. `PresetCore.sameVolume` now compares recognizable Android storage-mount prefixes **before** trying NIO, and fails closed on unknown volumes. Regression tests cover same vs different Android shared-storage mounts.

## Verified build and tests
- Public GitHub Actions run `43090208897` succeeded. JVM tests: **49 rename assertions + 44 preset/template assertions = 93**. Android APK SDK build, signing and Android 15 emulator UI test passed.
- Emulator confirmed read-only Find, regex preview with no modifications, explicit Save, and the **Move into a new folder** preset selecting a destination outside the source root. It checked the source remained untouched during preset preview, then was moved only after Save, and checked for AndroidRuntime fatal errors.
- Private signing build `43090886013` passed. Signed APK package `com.pathshift.app`, versionName 0.2.0 / versionCode 6, APK v3 signature verified. Signing certificate SHA-256: `63093b28d3c664e20eff48788dc3ad4e923064fe530073d66fb108d6450e30e0`, matching v0.1.4.
- Final signed APK SHA-256: `2937a4da419836c9842a373b81576e6e4f51ff8491b47be96d6d08fa671b764f`, 66,037 bytes. Stored and verified in ChatGPT File Library `/PathShift/PathShift-0.2.0.apk`, with private mirror `to-shreds/ProjectStatus/projects/pathshift/artifacts/PathShift-0.2.0.apk`. Source is in the public Misc repository.

## Outstanding
No successful physical-phone test or test on every manufacturer's removable/virtual storage. Do a controlled pilot on disposable local files before any critical collection; verify permissions, full-path moves, collision numbering, external chosen folder and interrupted-save recovery. Test performance and selection on very large batches (10,000+ files). Preserve unrelated completed behavior.

Next continuation: verify Android device behavior, inspect any reproducible errors first, make targeted fixes, retest through actual emulator and release with the **existing private signing identity**. Update HANDOFF first and ProjectStatus STATUS last after each meaningful milestone.
