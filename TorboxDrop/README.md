# TorBox Drop 2.1.1

[Open the web client](https://to-shreds.github.io/Misc/TorboxDrop/) · [Standalone HTML](TorBox-Drop.html) · [Android APK](https://github.com/to-shreds/torbox/raw/refs/heads/main/release/TorBox-Drop-v2.1.1.apk)

Added and Cached have their own aligned columns on every screen size. Swipe sideways for more columns; Name stays visible in the web table. The APK uses the same explorer layout and retains its native Google Drive and background-monitoring features.

Enter your TorBox API key as the password. It stays in tab memory and clears on reload or sign-out. No account information loads before authentication. The free stateless relay is live; downloads go directly from TorBox to your device.

The existing [to-shreds/torbox repository](https://github.com/to-shreds/torbox) remains the source and APK home. This folder publishes the standalone web client on GitHub Pages. Edit torbox/web, regenerate with `node web/build.mjs`, and synchronize both HTML copies. Never insert credentials in these files.
