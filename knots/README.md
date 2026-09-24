# Knotbook

A phone-friendly visual field guide with **100 tutorials, 500 written steps and 100 distinct animated tying demonstrations**. The collection includes everyday knots, camping and boating hitches, fishing connections, decorative work, neckties, and a small number of related rope-care and lashing techniques.

**Open:** https://to-shreds.github.io/Misc/knots.html  
**Misc launcher:** https://to-shreds.github.io/Misc/

## Using the guide

Search by name, alternate name or use. Combine activity, knot type and difficulty filters. Start with the **essential 8** for a short beginner course. Each tutorial has a real tying demonstration, a preview picture, five read-along steps, a materials list, a finished-knot check and a common mistake to avoid.

Press the large play button to load the demonstration. The player's settings menu can change playback speed. Written steps are a separate companion reader: they are **not** claimed to be synchronized to individual video frames. Use Previous/Next, the step markers, or Show all steps. On a keyboard, left/right arrows change steps; `/` focuses search; Escape returns from a tutorial.

Star a knot to save it. Mark a knot as practiced to track repetition. These marks do not certify skill. Preferences include larger instructions, whether to load preview pictures, and whether to show every step at once. Progress stays in this browser's local storage. Export/import JSON transfers saved knots and practice marks; import merges existing progress and ignores unknown IDs. No account is needed.

Print from a tutorial to get all five written steps, the checks and the reference URL, regardless of the current screen step.

## Internet and media

The page's interface, catalog, CSS and JavaScript are all contained in `../knots.html`; it requires no application backend, framework, account or API key. The words remain usable without external media. Browser storage restrictions may prevent progress persistence, in which case the guide explains the limitation and still works.

**Pictures and animated demonstrations require internet access.** Preview images come from YouTube. Pressing play loads YouTube's privacy-enhanced embedded player. The original illustrated tutorial and the direct YouTube video are also linked, because content filters, provider restrictions or connectivity can block an embed. Knotbook does not bypass those restrictions or claim offline video playback.

The 100 IDs in `media.json` were matched to the named demonstrations on Animated Knots by Grog's public channel or the corresponding publisher page. IDs are pinned so a search result cannot silently substitute an unrelated video. Availability and embedding permission remain under the publisher's control.

## Safety

This is an educational practice reference, not climbing, rescue, animal-handling, lifting, structural design or cargo-securement qualification. Knot performance depends on material, diameter, condition, correct dressing, tails, loading direction and the entire system. Follow equipment specifications and qualified instruction for consequential uses. Each tutorial adds knot-specific limitations. The source demonstrations sometimes use short illustrative tails: do not copy those tail lengths into critical applications.

The guide deliberately distinguishes a binding Square Knot from a rope-joining bend, fixed eyes from tightening loops, and practice records from competence. Weighted decorative knots are not recommended; use soft unweighted material.

## Sources and rights

Animated Knots by Grog: https://www.animatedknots.com/complete-knot-list  
Publisher safety information: https://www.animatedknots.com/safety  
Publisher terms: https://www.animatedknots.com/copyright-and-privacy-policy

Every tutorial includes its specific publisher URL. Companion instructions and interface are original. Publisher animation frames, videos, and photographs have **not** been downloaded into or redistributed by this repository. Media is delivered through the provider's own player and preview endpoints. Demonstrations remain copyright their respective owner. No affiliation or endorsement is claimed.

## Editing and rebuilding

The source of truth is this directory on `main`, not a downloaded ZIP.

- `catalog-a.txt` through `catalog-d.txt`: the 100 reviewed, human-readable tutorial records.
- `media.json`: one matching YouTube ID per tutorial.
- `template.html`, `style.css`, `app.js`: the original interface.
- `tools/build.py`: deterministic, standard-library-only compiler and catalog validation.
- `tools/browser_test.py`: real Chromium interface tests, including all 100 tutorial routes, filters, local progress, imports/exports, multiple viewport widths, print, and unavailable storage.
- `tools/finalize_catalog.py`: archived, idempotent initial editorial migration. Do not rerun it to apply future edits; edit the reviewed catalogs directly.
- `tools/research.py`: optional developer research helper. It is not part of the app or normal build; fetched research excerpts must not be committed or published.

From the repository root:

```sh
python knots/tools/build.py
python knots/tools/build.py --check
node --check knots/app.js
pip install playwright
python knots/tools/browser_test.py
```

The browser test uses an installed Chromium or Google Chrome executable. With neither installed, install Playwright's Chromium first. `--online-pictures` is an optional visual-proof mode; normal assertions deliberately avoid relying on external video playback. A browser managed to prohibit all navigation may require running the tests on a normal development machine or GitHub Actions.

The Knotbook workflow rebuilds and tests source changes, then commits the generated `knots.html` on `main`. The existing GitHub Pages configuration serves the root page. No service worker is installed, so there is no stale offline cache to clear during updates.
