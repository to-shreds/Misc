# Current source map

Current private source archive:

NHL-UK-3.1-private-backup-fresh.zip

SHA-256:

be3772613c6eb4606dd83c4dff87444aa56c97189acd804225284502188a74dc

Package:

app.nhluk

Version:

3.1-multi-search-test

versionCode:

13

Signing certificate SHA-256:

ab3541f1f7b9bf41ab26f5951d4f2672cd71f7ca89678257250a6a9fd54d013a

The private signing key is intentionally not in this public repository.

## Source tree inside the private archive

- AndroidManifest.xml
- build.py
- native.py
- requirements.txt
- assets/catalogue.js
- assets/controller.js
- assets/home.html
- assets/icon.png
- assets/player-ui.js
- assets/player.css
- assets/player.html
- assets/shield.js
- assets/style.css
- assets/ui.js
- tests/automation_static_test.py
- tests/catalogue.test.js
- tests/controller.test.js
- tests/handset-acceptance.md
- tests/lifecycle_test.py
- tests/native_types.py
- tests/replay_ui_test.py
- tests/ui_test.py
- tests/verify_package.py
- tools/android_package.py
- tools/dex.py
- signing/nhl-uk-certificate.pem
- signing/nhl-uk-private.pem
- build outputs and test reports

## Important implementation files

assets/shield.js

Controls remote-player cover/mute, media hooks, timeline verification, prepare, Play, skip, source-change handling, and fixed remote state.

assets/player-ui.js

Controls the local protected-player UI and stage/error messages.

assets/catalogue.js

Normalizes NHL/DAZN game identity, search terms, exact matches, routes, feed variants, and bookmark compatibility.

assets/controller.js

Controls VPN state and transition logic.

native.py

Defines the Android application and activities in the custom explicit DEX build system, including Main, Auth, Browser, discovery, storage, diagnostics, and Nord accessibility.

build.py and tools/

Build and sign the APK without Android SDK build tools.

## Public-repo policy

Do not publish the private signing key. If Work needs a buildable repo, copy the non-secret source files from the private archive into a new source subtree and keep signing material outside git.
