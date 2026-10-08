> **Historical import.** Arcade is now the sole maintained source for this game. Use [the Arcade activity](https://to-shreds.github.io/arcade/?game=adventure) and edit [arcade/adventure/index.html](https://github.com/to-shreds/arcade/blob/main/adventure/index.html). This folder remains an archived import; the notes below describe the pre-integration version.

# Logan and Jenkins: Operation Giggle

A complete, standalone choose-your-own-adventure game for Logan, intended for a reader around age 7 or 8. This folder is the source package for a future addition to [Jon's Arcade](https://github.com/to-shreds/arcade). Arcade integration has not been performed.

## Play

Download [Logan_and_Jenkins_Operation_Giggle.html](Logan_and_Jenkins_Operation_Giggle.html) using GitHub's **Download raw file** button, then open the downloaded file in a modern browser. JavaScript must be enabled. There is no build step, installation, account, server, API key, library, external asset, or internet requirement for gameplay.

All markup, styling, code, story content, and branching connections are inside that one HTML file. Keep this directly-openable version available even if Arcade later adds an integrated version.

## Story and audience

An ordinary day at home turns into a secret family mission. Logan finds clues that Jenkins is making a surprise for Scarlett. Logan becomes his partner, chooses what to make, gathers supplies, solves problems, keeps the surprise hidden, and arranges the reveal.

Logan is the decision-maker. Jenkins is a friendly, excitable monster whose overconfident plans often need a little help. Scarlett asks questions, nearly finds the project, and sometimes helps without realizing it. Mom and Dad can interrupt, become helpers, or rescue a plan. They remain loving and capable.

The intended tone is warm, funny, adventurous, and safe. The latest prose revision uses simpler sentences and more recognizable household situations, with fewer random jokes and non sequiturs. The target is a 7- or 8-year-old reader, not a formally certified reading grade. Preserve clear causes and consequences when expanding the writing.

## Adventure structure

The current map has **124 reachable story nodes, eight positive endings, and 30 story decisions per complete route**. Advancing a text page is not a story decision. Non-ending scenes normally provide three or four approaches, such as investigating, asking for help, being honest, keeping a secret, or improvising.

The adventure progresses through the mystery, discovering Jenkins's plan, choosing a surprise, gathering materials, building it, keeping the secret, solving a larger problem, preparing the reveal, and celebrating with Scarlett.

Four project branches have different materials and construction problems:

- A tennis-ball crown.
- A pancake prize/trophy.
- A monster picture.
- A small treasure hunt and puppet show.

Investigation routes, hiding places, helpers, and reveal locations branch again. Separate routes can reconnect, but earlier decisions affect later scenes. Internal state tracks items such as a cookie, tape, an acorn, and a sock puppet; Dad's shoes; who knows the plan; Jenkins's cleanup promise; suspicion; and earlier fixes. Later dialogue and endings use those choices as callbacks. Endings are different happy outcomes, not a harsh win/lose screen.

## Replay director and storage

The internal connections layer selects one of four three-decision side adventures before the project and one of four after construction. A seeded recipe also changes the mission name, recurring details, scene variations, and callbacks.

Rendering does not reroll the story. Back restores the exact previous decision state, and Restart requests a fresh recipe. An invisible run-history record uses the browser storage key `logan.operationGiggle.director.v2`, preferring localStorage and falling back to sessionStorage or memory if storage is unavailable. It is not a save/load system: refreshing does not resume a partially completed adventure.

This is a finite, hand-authored story, not an AI story generator. Recipes and history reduce repeats, but individual scenes and phrases can recur. Clearing browser data, changing browser/profile/origin, or losing fallback storage resets that history. Do not promise infinitely many entirely unique stories.

## Reading interface and controls

Most of the screen is reserved for story text. The large decorative heading is removed. A small scene title and compact controls sit above the text; there is no inventory panel, stats display, progress meter, or mission dashboard.

Story text is divided into pages by measuring the available rendered space, not by using a fixed character limit. Page breaks preserve words and adapt to viewport size, font metrics, and choice-button space. Longer scenes use Previous/Next buttons instead of requiring the reader to scroll. Choices become available on the final text page. Resize recalculates the pages and stops narration.

- **Back** undoes one story decision and restores its state snapshot.
- **Restart** begins a new adventure.
- **Read / Stop** is a single small narration toggle using browser SpeechSynthesis.
- **1-4** selects a choice once the final text page is visible.
- **Left/right arrows** change text pages; **Escape** stops narration.

Narration advances through text pages. Speech support and voices depend on the browser/device, and some voices may require a connection. The story remains playable without speech. The compact layout uses responsive choice grids and reduced-motion support.

## Implementation and maintenance

The canonical editable source is the HTML file in this folder. There is no separate generated bundle or connections file.

The central `STORY` map contains scene titles, text or text functions, choices, conditions, effects, and destinations. The engine maintains a game-state object and decision snapshots. `STORY_CONNECTIONS` and the director choose side-adventure links. Reading-level text/title/choice overrides are applied after the original story data; edit the effective override when changing a scene that has one.

Diagnostic interfaces exposed on the page are `globalThis.__LOGAN_ADVENTURE__` and `globalThis.__LOGAN_CYOA_DEBUG__`. The former includes `validateStory()` and story/director helpers; the latter exposes transitions, state snapshots, and pagination diagnostics. These are not yet an Arcade multiplayer adapter or a stable external integration protocol.

## This upload and verification

The saved HTML was uploaded unchanged on 2026-10-08. Its inline JavaScript parses successfully. The built-in structural validator reports 124/124 nodes reachable, eight endings, minimum and maximum path lengths of 30 decisions, and no errors or warnings. There is one inline script and no external script reference.

File size: **350,806 bytes**.

SHA-256: `4ba4942761c1cc88ea615a0de16a19fd0b21adbc7ba79fa7f261e41da42fe027`.

This was an archive/documentation handoff, not another rewrite or a fresh visual/device QA pass. Actual Arcade embedding, shared-control synchronization, offline packaging, device speech, and viewport behavior within the Arcade shell still need testing by that project.

## Arcade handoff

Start with [HANDOFF.md](HANDOFF.md). Add the game through Arcade's existing catalog and lifecycle conventions. Preserve the simple reading-first interface, offline single-file copy, deterministic replay, and exact Back behavior. If Arcade wants synchronized shared play, add an explicit adapter for the active controller, accepted story state, recipe, decisions, and text-page position. Independently starting the file on two devices does not synchronize their stories.

Do not treat this upload as an Arcade release, deployment, or multiplayer implementation.

