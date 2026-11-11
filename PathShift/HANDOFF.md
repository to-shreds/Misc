# PathShift handoff

Goal: A real sideloadable Android APK supporting batch regex rename and moves (including full relative path and directory names), metadata-only scanning, full preview and explicit Save.

Source: `to-shreds/Misc/PathShift` (once pushed), Java Android framework with no external libraries; `RenameCore` pure JVM planner, `BatchCommit` file operations, `MainActivity` native UI, `build.sh` Android SDK-only build.

Design commitments: no file contents read, no network permission, do not modify before Save + confirmation, no overwrite of unchanged files, validate every planned destination and detect duplicates, stage changed nodes deepest-first and restore parents first, error rather than silently proceeding on stale scan. Working paths must remain under selected root. Android 11+ broad file-management permission is prompted, never silently granted.

Verification: Run `bash tests/run-tests.sh` locally; build Android APK in GitHub Actions `.github/workflows/pathshift-android.yml`. Check GitHub CI result and signed APK before calling it ready. Real-device testing is still outstanding unless otherwise recorded.

Known limitations: All-files permission is broad; storage provider/volume restrictions apply. Filesystem move batches are best-effort with staged rollback, not atomic. Preserve private signing key/ password to update installation. A failed rollback leaves `.pathshift-stage-*` recovery folder. Empty original source folders are retained by default.

Next: Run CI and fix build issues, retrieve signed APK, preserve key and store APK in user Library, then test on actual Android device. After meaningful work, update `ProjectStatus/projects/pathshift/STATUS.md` last.
