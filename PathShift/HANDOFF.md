# PathShift handoff

Goal: A real sideloadable Android APK supporting batch regex rename and moves (including full relative path and directory names), metadata-only scanning, full preview and explicit Save.

Source: `to-shreds/Misc/PathShift`, Java Android framework with no external libraries; `RenameCore` pure JVM planner, `BatchCommit` file operations, `MainActivity` native UI, `build.sh` Android SDK-only build.

Design commitments: no file contents read, no network permission, do not modify before Save + confirmation, no overwrite of unchanged files, validate every planned destination and detect duplicates, stage changed nodes deepest-first and restore parents first, error rather than silently proceeding on stale scan. Working paths must remain under selected root. Android 11+ broad file-management permission is prompted, never silently granted.

Verification: Run `bash tests/run-tests.sh` locally; build Android APK in GitHub Actions `.github/workflows/pathshift-android.yml`. Check GitHub CI result and signed APK before calling it ready. Real-device testing is still outstanding unless otherwise recorded.

Known limitations: All-files permission is broad; storage provider/volume restrictions apply. Filesystem move batches are best-effort with staged rollback, not atomic. Preserve private signing key/ password to update installation. A failed rollback leaves `.pathshift-stage-*` recovery folder. Empty original source folders are retained by default.

Next: Build and archive v0.1.1 privately; replace the v0.1.0 public-CI-signed preview-only artifact in the user Library, preserve private signing identity, and test on actual Android device. Public CI must never publish a signing key or password. After meaningful work, update `ProjectStatus/projects/pathshift/STATUS.md` last.

Preview v0.1.1 includes a button to view all affected before-and-after paths, including children inherited from a renamed folder.

Android emulator smoke script `tests/android_smoke.py` installs the signed APK, grants All files access via appops, browses to a disposable folder, enters a regex, checks Preview leaves the file unchanged, then confirms Save renames it. CI `android-emulator-smoke` uploads screenshots and UI hierarchy. Do not claim success until its GitHub run completes.
