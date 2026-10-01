# TorBox Drop 2.1.0

[Open the web client](https://to-shreds.github.io/Misc/TorboxDrop/) · [Download the standalone HTML](TorBox-Drop.html) · [Download Android APK](https://github.com/to-shreds/torbox/raw/refs/heads/main/release/TorBox-Drop-v2.1.0.apk)

The existing [to-shreds/torbox repository](https://github.com/to-shreds/torbox) remains the source and APK home. This folder publishes the web client through the existing Misc GitHub Pages site.

The web client is live and has passed browser, download, privacy and large-library tests. Its verified free stateless relay is https://torbox-drop-api.onrender.com. TorBox blocks direct browser API requests from GitHub Pages, so only API requests use that relay; file bytes download directly from TorBox. No API key is saved in the deployment configuration.

Use your TorBox API key as the password. It stays in memory only and is cleared on reload/sign-out. No account or file data loads before authentication. For full native Drive routing and background monitoring, use the Android app.

Generated from torbox/web/index.html. Edit source there and synchronize both HTML copies after regeneration. Never insert credentials in these files.
