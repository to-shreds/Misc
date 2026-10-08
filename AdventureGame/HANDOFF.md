# Adventure Game handoff to Arcade

## Current package

- Source: `to-shreds/Misc`, `AdventureGame/Logan_and_Jenkins_Operation_Giggle.html`.
- Project description and operating notes: [README.md](README.md).
- Uploaded 2026-10-08, unchanged from the latest saved standalone game.
- 350,806 bytes; SHA-256 `4ba4942761c1cc88ea615a0de16a19fd0b21adbc7ba79fa7f261e41da42fe027`.
- Single offline HTML; 124 reachable scenes, eight happy endings, 30 story decisions per route.
- Structural validation and JavaScript syntax checks passed during this handoff. No new visual/device or Arcade-integration tests were run.

## What Jon wants next

Include this game in the Arcade project. This upload only provides the game and documentation; it does not change, release, or deploy Arcade. Read Arcade's own current README, handoff, and controlling project status before implementing there.

## Preserve

- Reading intended for ages 7-8, understandable household humor, and a warm family story. Avoid replacing clear events with random nonsense.
- Logan chooses; Jenkins, Scarlett, Mom, and Dad remain meaningful characters.
- Small controls, no large title, most screen space devoted to readable text.
- Measured text pagination, no routine full-page scrolling, and choices only on the final text page.
- Exact Back snapshots, deterministic wording within a run, and a fresh recipe on Restart.
- The directly-openable, self-contained HTML distribution.
- Invisible recipe history, not an in-progress save system. Its current storage key is `logan.operationGiggle.director.v2`.

## Integration checklist

1. Add an Arcade catalog entry using its existing conventions. Choose the appropriate activity mode from Arcade's actual architecture rather than assuming this standalone game already supports online play.
2. Package the HTML locally and include it in Arcade's offline manifest/cache and release artifacts. Do not rely on a remote GitHub URL for gameplay or introduce external libraries/assets.
3. Connect Arcade exit/navigation and ensure narration stops when leaving or suspending the game. Avoid stacking a large Arcade header above this game's compact reading layout.
4. For shared-control play, implement an explicit bridge: one accepted recipe and authoritative story state, controller-only choices, observer rendering, Back/Restart transitions, and text-page synchronization/recovery. The debug API is a starting point, not an implemented transport adapter. Keep local speech playback separate from shared story decisions.
5. Verify short/tall phone and landscape viewports, safe-area/shell insets, all choices, resized pagination, Back, Restart, narration cancellation, and offline reload. Check SpeechSynthesis on the actual target browser/WebView; reading must work without it.
6. Run Arcade's relevant regression/release checks and update its own handoff/status only after that integration is verified.

## Known boundaries

The content is finite. Run recipes and stored history discourage repeats; they cannot make every scene forever unique, and clearing/changing browser storage resets history. The prose targets young readers but has no independently certified reading-level score. No Arcade integration or physical-device compatibility is claimed by this package.

Next action: the Arcade project consumes this folder and implements its catalog/lifecycle integration.
