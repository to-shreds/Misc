# TorBox Drop 2.1.0

[Open the web client](https://to-shreds.github.io/Misc/TorboxDrop/) · [Download the standalone HTML](TorBox-Drop.html) · [Download Android APK](https://github.com/to-shreds/torbox/raw/refs/heads/main/release/TorBox-Drop-v2.1.0.apk)

The existing [to-shreds/torbox repository](https://github.com/to-shreds/torbox) remains the source and APK home. This folder publishes the web client through the existing Misc GitHub Pages site.

The web client has passed browser, download, privacy and large-library tests. Sign-in is intentionally disabled until its stateless API relay is activated. TorBox blocks direct browser API requests from GitHub Pages. The Render connector requires Jon to confirm My Workspace before creating the free relay. No API key is needed in the deployment configuration.

Once activated, use your TorBox API key as the password. It stays in memory only and is cleared on reload/sign-out. No account or file data loads before authentication. For full native Drive routing and background monitoring, use the Android app.

Generated from torbox/web/index.html. Edit source there and synchronize both HTML copies after regeneration. Never insert credentials in these files.
