# PathShift v0.2.0

Native, offline Android 11+ file finder and batch renamer. The app only scans file/folder names and metadata, never file contents. It has no network permission, analytics or third-party runtime dependencies. Android's user-granted All files access is required for selected locations.

## Screens

**Find:** Search file/folder names or complete relative paths, with literal or Java regex matches. Filter results on-screen, copy their full paths, or switch the selected working folder. Find has no Save control.

**Rename:** Enter a Java regex and replacement, choose filename or full relative path, and choose whether the replacement changes *only the matched text* or sets the *complete resulting destination*. Replacement helpers insert `${filename}` (entire original filename, including extension), `${basename}` (original filename without the final extension), `${ext}` (extension without the dot), `${folder}` (original parent path relative to the selected working folder) and `${path}` (original path relative to the selected working folder). Standard Java replacement references `$0` (entire regex match), `$1`, `$2`, etc. still work. When matching filenames, the new path is relative to each file's current parent. When matching full relative paths, the new destination is relative to the selected working folder. For example, searching `mag` in Filename mode, choosing **Set complete resulting destination**, and specifying `Mag/${filename}` will move `Images/photo_mag_005.jpg` to `Images/Mag/photo_mag_005.jpg` even though `mag` is only a partial match. Leaving the default **Replace matched text** mode preserves traditional Java regex substitution semantics.

**Presets:** Eight guided actions, all preview-first:
1. **Move into a new folder**: choose the destination *parent folder* using the in-app folder browser, enter a new folder name, optionally filter filenames (literal or regex), include subfolders and preserve their relative layout.
2. **Sort by file extension**: choose the destination parent and new container folder; files are grouped into JPG, PNG, PDF and similar subfolders. No extension is grouped under `No extension`.
3. **Add prefix** to filenames.
4. **Add suffix** before the extension.
5. **Number filenames** using a configurable starting number and padding width.
6. **Find and replace plain text** in filenames, no regex required.
7. **Lowercase filenames**.
8. **Convert spaces to underscores**.

Presets operate on files, not complete directory trees; the advanced Rename mode still supports folder renaming. Move/Sort presets can target a location *outside the scanned working folder* as long as Android permits both locations and they are on the **same storage volume**. Content copying, cross-volume moves, and bypassing Android's restricted system directories are deliberately unsupported. The chosen *new folder* must not already exist, to avoid merging files into an unknown directory. With recursive processing, choose whether to preserve original subfolders. If flattening would produce duplicate names, the preview blocks Save and offers **Number duplicates**.

## Safety and review

Nothing on disk changes during choosing a preset, searching, editing proposed names, changing selection, or previewing. Every change is staged and listed before **Save changes** and the separate **Save now** confirmation. Preset previews show original and absolute target paths. Both advanced Rename and Preset previews group duplicate destinations, disallow overwriting existing files, and offer numbering for conflicting filenames. Invalid destinations, paths outside the allowed working scope (except explicitly chosen preset output folders), and stale metadata block Save. A same-volume temporary staging directory is created *only after Save*; failures trigger best-effort rollback. A multi-file operation is not a true atomic transaction across a power failure or device restart. Test with disposable files before moving important data, and avoid force-closing while a batch is saving. Empty source folders can remain after moving their files.

## Build & validation

Run `bash tests/run-tests.sh` for JVM rename and preset regression checks. With Android SDK API 35 and build tools 35.0.0 and JDK 17 installed, run `bash build.sh`. The public GitHub workflow builds and tests with an ephemeral key that is **not uploaded**; private release signing is managed in `to-shreds/ProjectStatus`, using the existing v0.1.4 keystore to preserve upgrade compatibility. Do not put private signing keys or passwords in a public repository.

GitHub Actions builds a separate APK and runs the emulator UI smoke test, which checks Find, Preview/Save of a regex change, and a move-to-new-folder preset selecting a destination outside the source folder. Actual test results, not test existence, determine whether an APK is ready. Real-phone testing is recommended for manufacturer-specific file permissions and SD cards.
