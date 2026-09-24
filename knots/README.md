# Knotbook

A phone-friendly visual field guide with **100 tutorials and 500 written steps**. Version 1.1 adds the first **20 original, local rope-path animations**. The other 80 tutorials keep their existing reference videos. The collection includes everyday knots, camping and boating hitches, fishing connections, decorative work, neckties, and related rope-care and lashing techniques.

**Open the original batch:** https://to-shreds.github.io/Misc/knots.html#originals  
**Whole guide:** https://to-shreds.github.io/Misc/knots.html  
**Misc launcher:** https://to-shreds.github.io/Misc/

## Using the guide

Search by name, alternate name or use. Combine activity, knot type and difficulty filters. The **Original animations** filter shows the new batch. Start with the **essential 8** for a short beginner course. Each tutorial has a demonstration, a picture, five written steps, a materials list, a finished-knot check and a common mistake to avoid.

Original animations have Play/Pause, previous and next moves, replay-current-move, a whole-sequence scrubber, three speeds, repeat, mirror and an enlarged view. Their moves and the written reader stay on the same numbered step. The moving dot identifies the working tip; pale dashes preview the current route. Dotted sections on a practice bar indicate rope behind it. Diagrams stay expanded so crossings can be inspected. They illustrate rope paths, not physical tightening, tension or collision simulation. Some final dressing and inspection stages intentionally hold the completed loose diagram while the text explains the action.

The other 80 tutorials still load the reference video when Play is pressed. Those written steps are separate companion instructions, not synchronized video frames. Every tutorial retains direct reference-video and illustrated-source links, including the 20 with original animations.

Use Previous/Next, the step markers, or Show all steps. On a keyboard, left/right arrows change reader steps; `/` focuses search; Escape closes an enlarged animation before returning from a tutorial. Mirroring changes the drawing, not the left/right words in the companion text.

Star a knot to save it. Mark a knot as practiced to track repetition. These marks do not certify skill. Preferences include larger instructions, external preview pictures, and showing every step at once. Progress stays in this browser's local storage. Export/import JSON transfers saved knots and practice marks; import merges existing progress and ignores unknown IDs. The existing storage key and schema were preserved. No account is needed.

Print from a tutorial to get all five written steps, checks and the reference URL. Original tutorials also print five diagram panels.

## First original batch

Overhand; Double Overhand; Figure Eight; Stevedore; Slip Knot; Square Knot; Sheet Bend; Bowline; Clove Hitch; Cow Hitch; Half Hitch; Round Turn and Two Half Hitches; Constrictor; Strangle; Timber Hitch; Flat Overhand Bend; Surgeon's Join; Surgeon's Loop; Water Knot; Figure Eight Bend.

These are twenty distinct tutorials, not twenty unrelated knot families. Related knots intentionally share the corresponding geometric operation, such as a paired overhand or a retraced figure eight. Their rope roles, passages and instructions differ.

## Internet and media

The interface, catalog, CSS, JavaScript, original drawings and native player are contained in `../knots.html`. The app requires no backend, framework, account or API key. After the page has loaded, the first 20 animations and all written instructions need no further connection. This does not imply that a browser will retain the page across an offline reload. Browser storage restrictions may prevent progress persistence, but do not prevent practice.

The other 80 preview pictures and reference videos require internet. Preview images come from YouTube. Pressing their Play button loads YouTube's privacy-enhanced embedded player. The direct YouTube video and original illustrated tutorial remain linked because content filters, provider restrictions or connectivity can block an embed. The external-preview preference affects only external pictures, not the original SVG drawings.

The 100 IDs in `media.json` remain pinned to the named demonstrations on Animated Knots by Grog's public channel or corresponding publisher page. Availability and embedding permission remain under the publisher's control.

## Safety

This is an educational practice reference, not climbing, rescue, animal-handling, lifting, structural design or cargo-securement qualification. Knot performance depends on material, diameter, condition, correct dressing, tails, loading direction and the entire system. Follow equipment specifications and qualified instruction for consequential uses. Each tutorial adds knot-specific limitations. Do not copy illustrative spacing or tail lengths into critical applications.

The guide distinguishes a binding Square Knot from a rope-joining bend, fixed eyes from tightening loops, and practice records from competence. Weighted decorative knots are not recommended; use soft unweighted material. Structural and browser tests do not independently certify the drawings' real-world safety or make the learner competent.

## Sources and rights

Animated Knots by Grog: https://www.animatedknots.com/complete-knot-list  
Publisher safety information: https://www.animatedknots.com/safety  
Publisher terms: https://www.animatedknots.com/copyright-and-privacy-policy

Every tutorial includes its specific publisher URL. The original batch was checked against the corresponding published tying descriptions and reference illustrations. Its SVG waypoints, rendering code, captions and interface were authored for Knotbook, not traced or copied from animation frames. This is not an independent expert certification of the drawings.

Publisher animation frames, videos and photographs have not been redistributed by this repository. Remaining external media is delivered through the provider's own player and preview endpoints. External demonstrations remain copyright their respective owner. No affiliation or endorsement is claimed.

## Editing and rebuilding

The source of truth is this directory on `main`, not a downloaded ZIP.

- `catalog-a.txt` through `catalog-d.txt`: 100 reviewed, human-readable tutorial records, unchanged by the first animation batch.
- `media.json`: one matching reference-video ID per tutorial.
- `originals.js`: original geometry, depth-defined crossings, move cuts, captions and player. `KnotOriginals.ids` identifies the converted tutorials.
- `originals.css`: native player, thumbnail, responsive and print styling.
- `template.html`, `style.css`, `app.js`: the surrounding guide and integration.
- `tools/build.py`: deterministic standard-library compiler and catalog validation. It embeds native CSS/JS into the standalone page.
- `tools/browser_test.py`: all 100 tutorial routes, filters, progress, imports/exports, viewport widths, print and blocked storage.
- `tools/originals_test.py`: every native tutorial and move, controls, geometry bounds, explicit crossing depth, reader synchronization, repeat, mirror, enlargement, print and network-independent playback. `--local` uses set_content where managed browsers block navigation; normal CI uses a routed HTTP origin.
- `tools/finalize_catalog.py` and `tools/install_originals_v1.py`: archived one-time migrations, not routine build steps. Edit canonical source directly for subsequent batches.
- `tools/research.py`: optional research helper; fetched research excerpts must not be committed or published.

From the repository root:

```sh
python knots/tools/build.py
python knots/tools/build.py --check
node --check knots/app.js
node --check knots/originals.js
pip install playwright
python knots/tools/browser_test.py
python knots/tools/originals_test.py
```

The browser tests use installed Chromium or Google Chrome. With neither installed, install Playwright's Chromium first. `--online-pictures` on the general browser test enables optional external-preview screenshot capture; assertions do not claim to test provider-controlled video playback.

The normal Knotbook workflow rebuilds, runs both suites and commits the generated `knots.html` on `main`. The batch-1 staging workflow is an archived integration path, not the workflow for future batches. GitHub Pages serves the root page. No service worker is installed. Confirm live deployment after publishing rather than assuming a build-bot commit triggered Pages.
