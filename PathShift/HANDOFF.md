# PathShift handoff

Goal: A real sideloadable Android APK supporting batch regex rename and moves (including full relative path and directory names), metadata-only scanning, full preview and explicit Save.

Source: `to-shreds/Misc/PathShift`, Java Android framework with no external libraries; `RenameCore` pure JVM planner, `BatchCommit` file operations, `MainActivity` native UI, `build.sh` Android SDK-only build.

Design commitments: no file contents read, no network permission, do not modify before Save + confirmation, no overwrite of unchanged files, validate every planned destination and detect duplicates, stage changed nodes deepest-first and restore parents first, error rather than silently proceeding on stale scan. Working paths must remain under selected root. Android 11+ broad file-management permission is prompted, never silently granted.

Verification: Run `bash tests/run-tests.sh` locally; build Android APK in GitHub Actions `.github/workflows/pathshift-android.yml`. Check GitHub CI result and signed APK before calling it ready. Real-device testing is still outstanding unless otherwise recorded.

Known limitations: All-files permission is broad; storage provider/volume restrictions apply. Filesystem move batches are best-effort with staged rollback, not atomic. Preserve private signing key/ password to update installation. A failed rollback leaves `.pathshift-stage-*` recovery folder. Empty original source folders are retained by default.

Latest verified release: v0.1.1, built from source commit `bfcf49f029693a2ae87f52dc74b1252008ef35b5` in private ProjectStatus Actions run `42973306864`. Its 38 JVM assertions pass, private APK signature verification passes, and the signed APK, current source ZIP, and private signing backup are available in ChatGPT File Library `/PathShift`. The app includes a complete all-affected-paths preview. The v0.1.0 public-CI build is superseded; its original signing identity must not be used for future releases. Public CI must never publish a signing key or password.

Device QA: Android 15 emulator runs `42971369014` and `42973646895` failed because ADB/Pixel Launcher or the disposable test fixture failed before the application was exercised. A new AOSP emulator smoke test `42974111620` was pending at this handoff; consult its actual result. No physical-device behavior has been verified.

Next: Verify launch, folder permissions, preview and Save on a disposable folder on a real Android device. Reuse the private v0.1.1 signing backup from the Library for future upgrades. Update `ProjectStatus/projects/pathshift/STATUS.md` last.

Preview v0.1.1 includes a button to view all affected before-and-after paths, including children inherited from a renamed folder.

Android emulator smoke script `tests/android_smoke.py` installs the signed APK, grants All files access via appops, browses to a disposable folder, enters a regex, checks Preview leaves the file unchanged, then confirms Save renames it. CI `android-emulator-smoke` uploads screenshots and UI hierarchy. Do not claim success until its GitHub run completes.


Android emulator discovery: 0.1.1 folder picker combined AlertDialog `setMessage` with `setItems`, causing the directory list to be hidden. Removed the message in 0.1.2, and put the path into the dialog title so the list can render. Treat 0.1.1 as superseded. Latest runtime QA requires the 0.1.2 emulator run; earlier run `42974111620` exposed this bug. Reuse the private 0.1.1 release signer so 0.1.2 upgrades cleanly.


Final 0.1.2 delivery: Private signing run `42974761100` succeeded with 38 core assertions and verified APK signing. The v0.1.2 APK SHA-256 is `a3de81eb77efe9dcbe3d2ef07503611a9a04ba563f41bd48d1ab75394a9f10f2`; released APK, source ZIP and latest private signer backup are stored and verified in ChatGPT File Library `/PathShift`. The v0.1.2 signer is byte-identical to v0.1.1, so upgrading from 0.1.1 should preserve installation identity; v0.1.0 uses a different key and must be uninstalled before upgrading. The Android 15 emulator run `42974686005` demonstrated folder navigation after the picker fix, but stopped at a case-sensitive test-button label; run `42974843308` failed because the emulator's Quickstep launcher was unresponsive before the app launched. Thus end-to-end device Preview/Save is unverified, not a verified failure. Before broader use, test preview, folder moves, file swaps, Save and recovery using a disposable folder on a real device. Do not publish any signing keys in the public repo. Update ProjectStatus STATUS.md last.
