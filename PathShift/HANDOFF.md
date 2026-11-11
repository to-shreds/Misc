# PathShift handoff

Goal: A real sideloadable Android APK supporting batch regex rename and moves (including full relative path and directory names), metadata-only scanning, full preview and explicit Save.

Source: `to-shreds/Misc/PathShift`, Java Android framework with no external libraries; `RenameCore` pure JVM planner, `BatchCommit` file operations, `MainActivity` native UI, `build.sh` Android SDK-only build.

Design commitments: no file contents read, no network permission, do not modify before Save + confirmation, no overwrite of unchanged files, validate every planned destination and detect duplicates, stage changed nodes deepest-first and restore parents first, error rather than silently proceeding on stale scan. Working paths must remain under selected root. Android 11+ broad file-management permission is prompted, never silently granted.

Verification: Run `bash tests/run-tests.sh` locally; build Android APK in GitHub Actions `.github/workflows/pathshift-android.yml`. Check GitHub CI result and signed APK before calling it ready. Real-device testing is still outstanding unless otherwise recorded.

Known limitations: All-files permission is broad; storage provider/volume restrictions apply. Filesystem move batches are best-effort with staged rollback, not atomic. Preserve private signing key/ password to update installation. A failed rollback leaves `.pathshift-stage-*` recovery folder. Empty original source folders are retained by default.

Latest verified release: v0.1.1, built from source commit `bfcf49f029693a2ae87f52dc74b1252008ef35b5` in private ProjectStatus Actions run `42973306864`. Its 38 JVM assertions pass, private APK signature verification passes, and the signed APK, current source ZIP, and private signing backup are available in ChatGPT File Library `/PathShift`. The app includes a complete all-affected-paths preview. The v0.1.0 public-CI build is superseded; its original signing identity must not be used for future releases. Public CI must never publish a signing key or password.\n\nDevice QA: Android 15 emulator runs `42971369014` and `42973646895` failed because ADB/Pixel Launcher or the disposable test fixture failed before the application was exercised. A new AOSP emulator smoke test `42974111620` was pending at this handoff; consult its actual result. No physical-device behavior has been verified.\n\nNext: Verify launch, folder permissions, preview and Save on a disposable folder on a real Android device. Reuse the private v0.1.1 signing backup from the Library for future upgrades. Update `ProjectStatus/projects/pathshift/STATUS.md` last.

Preview v0.1.1 includes a button to view all affected before-and-after paths, including children inherited from a renamed folder.

Android emulator smoke script `tests/android_smoke.py` installs the signed APK, grants All files access via appops, browses to a disposable folder, enters a regex, checks Preview leaves the file unchanged, then confirms Save renames it. CI `android-emulator-smoke` uploads screenshots and UI hierarchy. Do not claim success until its GitHub run completes.
