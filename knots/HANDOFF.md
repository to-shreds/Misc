# Knotbook handoff

## Request and delivery
The user requested a comprehensive, friendly visual guide to about 100 knots, including animation, pictures and instructions, published in `to-shreds/Misc`.

The canonical public page is `https://to-shreds.github.io/Misc/knots.html`. Root `index.html` now includes a permanent Knotbook card alongside Zork and still discovers future root HTML pages automatically. Preserve the existing Zork project and root Zork handoff.

Version 1.0.0 contains exactly 100 named tutorials, 500 original written steps, and 100 distinct, matched Animated Knots by Grog video IDs. A few entries cover closely related lashing, whipping and decorative techniques. Sources on `main` are authoritative; do not use an older chat ZIP as the starting point.

## Architecture and source of truth
`catalog-a.txt` through `catalog-d.txt` are the reviewed plaintext records. Each has five titled steps, use, materials, limitations, inspection check and common mistake. `media.json` maps each record ID to its own demonstration. `template.html`, `style.css` and `app.js` define the responsive interface. `tools/build.py` validates the catalog and compiles everything into root `knots.html` with no application dependencies or API keys. Use `--check` to detect a stale generated page.

`tools/finalize_catalog.py` is an archived one-time editorial migration that already ran. It must not be part of future routine builds: edit the reviewed catalogs directly. `tools/research.py` is an optional research utility, not application runtime. Its excerpts and downloaded page content must never be committed or published; `.gitignore` excludes research, screenshot proof and Python caches.

## Media and accuracy boundaries
Do not replace the actual demonstrations with generic animated loops or assume similar knot names share the same crossings. All 100 source URLs were checked against the publisher's canonical index. Ninety-nine selected IDs were matched to the publisher's public channel metadata; Bottle Sling uses the exact video ID embedded in its own publisher page. No publisher video or animation frames are vendored.

YouTube preview pictures require internet. The privacy-enhanced YouTube player loads only after a tap. Written steps are independent read-alongs, not synchronized video frames. Keep source-page and direct-video fallback links. Do not claim video playback is offline or that every third-party video was actually played in testing. Do not remove publisher credit or imply affiliation.

Keep distinctions between binding and joining, fixed and sliding loops, and practice records and competence. Specialist entries explicitly are not climbing/rescue/rigging qualifications. Rope/material compatibility and tail requirements cannot be inferred solely from a neat diagram. Keep decorative rope balls soft and unweighted.

## State and UI
Browser storage key: `misc-knotbook-v1`. Saves, practiced marks, large text, picture loading and all-steps preferences are local. Export/import transfers progress; importing merges valid known IDs. Storage failure must leave the full guide usable.

Routes: `#knot/<id>`, `#basics`, `#saved`, `#essentials`. The eight essentials are Overhand, Figure-Eight Stopper, Square, Sheet Bend, Bowline, Round Turn & Two Half Hitches, Clove Hitch, and Trucker's Hitch. Type/activity/difficulty filters combine. Search includes aliases and uses. No service worker or stale offline cache. Print includes all steps regardless of current reader position.

## Verified testing
GitHub Actions run `35959717916` passed **328 real Chromium assertions** on September 24, 2026. This covered all 100 routes and matching sources/media IDs; search, filters, beginner course, progress persistence, JSON export/import, reader controls, keyboard navigation, lazy player creation/removal, settings, unavailable storage, print, and horizontal-overflow checks at 1440, 768, 390 and 320 pixels.

The generated browser screenshots were downloaded and visually inspected. Real preview pictures loaded on the desktop and phone screenshots. Actual third-party video playback was intentionally not asserted. `tools/check_live.py` separately checks HTTP 200, the 100/500/100 embedded catalog and both launcher links on GitHub Pages.

The local development container prohibits browser URL navigation; `set_content` checks there are not hosted-browser proof. Full tests run in ordinary Chromium on GitHub Actions.

## Publishing
The workflow builds, tests and commits the root generated page. Existing GitHub Pages serves `main` / root. GitHub Actions bot commits may not themselves trigger branch-based Pages builds: after future generated-page commits, confirm the Pages deployment actually contains the new revision rather than merely assuming a green application build means a live update. Preserve the live HTTP check and inspect the deployment status. No paid hosting or backend is required.
