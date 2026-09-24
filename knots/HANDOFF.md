# Knotbook handoff

## Current state
Version 1.1.0 is published at `https://to-shreds.github.io/Misc/knots.html`. The new `#originals` route shows the first 20 original animated tutorials. The complete guide still contains 100 tutorials and 500 written steps. The remaining 80 tutorials retain their external demonstrations. All 100 source-page and reference-video links remain available.

The current requested batch of 20 is delivered. Conversion of the remaining 80 is not done. Read this directory on `main` for substance and `to-shreds/ProjectStatus/projects/knotbook/STATUS.md` for project readiness; do not restart from an older ZIP.

Tested application commit: `5b75e012b830fc256c62d30702fea39c89348962`. Documentation and live-check improvements followed. Standalone `knots.html` SHA256: `aed4170851b3339c208bbca5606fbb57acb29bbf9818ee3dddc52bf104434478` (216526 bytes).

## Original batch 1
`overhand`, `double-overhand`, `figure-eight`, `stevedore`, `slip-knot`, `square`, `sheet-bend`, `bowline`, `clove-hitch`, `cow-hitch`, `half-hitch`, `round-turn-two-half-hitches`, `constrictor`, `strangle`, `timber-hitch`, `flat-overhand-bend`, `surgeons-join`, `surgeons-loop`, `water-knot`, `figure-eight-bend`.

`originals.js` contains hand-authored rope waypoints, per-stage cuts, depth-defined crossings, original captions and the native player. Geometry is sampled once and crossing bridges show the nearer strand. `KnotOriginals.ids` is the converted-knot inventory. `originals.css` styles native thumbnails, controls, enlargement and printed diagram panels. No publisher photographs or animation frames were traced or vendored.

Native controls: play/pause, next/previous move, replay current move, whole-sequence scrubber, three speeds, repeat, mirror and enlarged view. Five animation stages stay synchronized with the existing written reader. Native drawings remain visible when external previews are disabled. Print renders all five diagram panels. Playback pauses when the document becomes hidden and is cleaned up on navigation. Escape closes enlarged view before leaving a tutorial.

## Accuracy and media boundaries
These are expanded 2D rope-path schematics, not rope-physics, tension or tightening simulations. Some dressing and inspection stages hold the loose completed diagram while the text describes the action. The moving dot indicates the working tip; pale dashes show the current route; dotted sections behind a practice bar indicate depth. Do not describe these as photorealistic demonstrations or independently safety-certified instructions.

Related knots legitimately share operations, but crossing order, strand identity and bight versus tail roles matter. Do not replace knot-specific geometry with generic loops. The slip knot folds the short end, not the standing part. The surgeon's join has opposed line roles; the surgeon's loop is one folded line. Retraced knots follow the earlier strand backward. Mirror changes the drawing, not left/right wording in the written catalog. Structural tests verify bounds and explicit depth, not real-world topology or competence.

The 20 native animations need no network requests after the page has loaded. This does not guarantee offline reload or persistence of a browser cache. Remaining YouTube pictures and videos need internet; external player loading remains tap-to-play. Native tutorials retain direct external reference links for comparison. Keep publisher credit for external media, and do not claim affiliation or that every external video was played in testing.

Preserve knot-specific cautions, especially binding versus joining, fixed versus sliding loops, material compatibility, tail requirements, and practice versus climbing/rescue/rigging qualifications. Decorative rope balls must remain soft and unweighted.

## Architecture and source of truth
`catalog-a.txt` through `catalog-d.txt` are the 100 reviewed plaintext records, with five titled steps, use, materials, limitations, inspection check and common mistake. They were not changed by batch 1. `media.json` retains 100 distinct matched reference-video IDs. `template.html`, `style.css` and `app.js` define the surrounding guide. `tools/build.py` compiles these and the native animation assets into root `knots.html`, with no runtime dependencies or API keys.

Routine verification:
```
python knots/tools/build.py
python knots/tools/build.py --check
node --check knots/app.js
node --check knots/originals.js
python knots/tools/browser_test.py
python knots/tools/originals_test.py
```

`tools/finalize_catalog.py` and `tools/install_originals_v1.py` are archived one-time migrations. Do not run them as part of routine builds or future batches. The batch-1 staging workflow is also an archived assembly path. Edit the installed canonical source directly. `tools/research.py` is optional research tooling; do not commit research excerpts or downloaded publisher material. Proof screenshots and caches remain excluded by `.gitignore`.

## State and do-not-break constraints
Local storage remains `misc-knotbook-v1`. Preserve saved and practiced IDs, larger-text, external-picture and all-steps preferences, and export/import merge behavior. Storage failure must leave instructions usable.

Preserve routes `#knot/<id>`, `#basics`, `#saved`, `#essentials` and `#originals`. Activity/type/difficulty/search filters combine with the native-only filter. The eight essentials remain Overhand, Figure Eight, Square, Sheet Bend, Bowline, Round Turn and Two Half Hitches, Clove Hitch and Trucker's Hitch.

Root `index.html`, its Knotbook card and automatic discovery, Zork and the root Zork handoff were not changed. Do not replace the Misc launcher or root handoff with Knotbook-specific files. No service worker is installed.

## Verification and publishing
Successful staging run `36003859619` passed 329 general Chromium assertions and 286 native-animation assertions, 615 total. The tests cover all 100 routes, all 20 native tutorials and their five moves, reader synchronization, controls, print, saved progress, exports/imports, storage failure, and widths 1440/768/390/320. Native playback made zero network requests after initial display. Screenshots include all 20 diagrams and desktop/tablet/phone player layouts; a contact sheet and phone screenshots were visually reviewed. The downloaded successful build matches the locally reviewed app byte for byte.

Main run `36004664282` subsequently passed both browser suites and the strengthened live HTTP check. That check compares the public page SHA256 against the exact local compiled HTML, so an old page with 100 tutorials cannot pass. Pages run `36004661678` successfully deployed the revision containing this application. The main verification revision is `1e078463142b76044351da187b3699c4c7b37744`.

Container browser navigation and direct public network access are restricted in this environment. Local `--local` tests use set_content; full tests and the exact public HTTP check ran in normal GitHub Actions environments. Do not conflate local fixture checks with hosted-page proof.

The standard Knotbook workflow builds and runs both suites. It commits generated `knots.html` when needed. Build-bot commits may not trigger branch-based Pages, so confirm the actual deployment and exact live HTML after future updates. Initial staging publication failed because the build token attempted a workflow edit; the connector applied that workflow change directly and subsequent staging and main runs succeeded. There is no outstanding publication blocker.

## Next action
Build batch 2 for 20 unconverted tutorials. Preserve these 20 and all existing guide behavior. Add genuine knot-specific geometry and captions, update converted-count UI and test expectations, rebuild, run both suites, inspect diagrams and phone layouts, verify the exact live page, then update this handoff and ProjectStatus. Do not describe the remaining 80 as converted before their animations exist.
