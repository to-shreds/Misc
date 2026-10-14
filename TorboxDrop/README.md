# TorBox Drop web 2.1.4 · Android stable 2.1.1

[Open the web client](https://to-shreds.github.io/Misc/TorboxDrop/) · [Standalone HTML](TorBox-Drop.html) · [Stable Android APK](https://github.com/to-shreds/torbox/raw/refs/heads/main/release/TorBox-Drop-v2.1.1.apk)

## YouTube downloads

Choose **YouTube**, paste a single-video link, and leave **Prepare and download this YouTube video** enabled. The client normalizes watch, share, Shorts, live-video and embed links, starts the TorBox web download immediately, and follows the returned job until the file is actually present. Then choose **Download video** to save it through the browser. Keep the tab open while preparing. Failed or restricted videos show TorBox's error; **Check status** checks the existing item without adding another copy. Playlist-only and channel links are rejected rather than silently submitted.

YouTube uses TorBox's paid-plan web-download service. Accepting a URL is not treated as completing a download. Public GitHub Pages hosts only the client; the existing stateless relay forwards API calls and video bytes go directly from TorBox to your device. Availability still depends on TorBox and the particular video. Verification used controlled API responses and real fixture-byte browser downloads, not an authenticated live YouTube download.

Use **List spacing** beside Sort to choose **Compact · one line**, **Cozy**, or **Detailed**. Compact is the default for more information on screen; Cozy retains familiar spacing, and Detailed adds room for wrapped names and supporting text. The choice applies to library, queue and file browsing, preserves your place and selection, and lasts for the tab session.

A prominent Account & quotas panel shows plan and expiry, active slots including seeding, AirLock storage usage and the published plan allowance, rolling 30-day bandwidth with TorBox’s dynamic fair-use baseline, and queued torrent/web items. Usage refreshes while browsing folders, stays independent of filters, and clears with the session. Missing values remain Not reported; failed refreshes retain visibly stale values.

Added and Cached have their own aligned columns on every screen size. Swipe sideways for more columns; Name stays visible in the web table. The APK uses the same explorer layout and retains its native Google Drive and background-monitoring features.

Enter your TorBox API key as the password. It stays in tab memory and clears on reload or sign-out. No account information loads before authentication. The free stateless relay is live; downloads go directly from TorBox to your device.

The existing [to-shreds/torbox repository](https://github.com/to-shreds/torbox) remains the source and APK home. This folder publishes the standalone web client on GitHub Pages. Edit torbox/web, regenerate with `node web/build.mjs`, and synchronize both HTML copies. Never insert credentials in these files.

Web verification: 43 core/relay tests and 31 Chromium browser scenarios passed in [the repair workflow](https://github.com/to-shreds/torbox/actions/runs/38654410657). The existing Android stable download above is intentionally not mislabeled as the new YouTube build. See the source repository's HANDOFF for the separately packaged Android preview and production-signing requirements.
