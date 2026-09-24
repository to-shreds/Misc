# Validation and limits

## Automated engine regressions

`node --test tests/engine.cjs` runs against the committed, unchanged interpreter bundle and game binary. It checks original bytes/edition/checksum; opening and object-table integrity; mailbox/read/inventory; closed opaque sack; concealed trapdoor; darkness and lamp; zero RAM/PC/state changes from GUI refresh; exact command-prompt snapshot round trip; original SAVE/RESTORE Quetzal flow; cancelled file prompts; invalid resume rejection; native quit confirmation; and unknown-word rejection. All 13 passed in development.

## Browser regressions

`tests/browser.py` serves the actual canonical files over local HTTP, then uses Chromium/Playwright. It checks desktop rendering; visible-object clicking; original move accounting; panel/notes/builder memory invariance; native manual saves; reload recovery; notes persistence; portable save export/import; malformed import protection; paper theme; hidden trapdoor and dark-room visibility; no external runtime requests; 393 px and 360 px touch layouts; landscape containment; and the standalone build. It writes screenshots and a JSON report in `test-results/`. GitHub Actions artifacts are the authoritative record of which browser checks passed for a particular commit.

## Explicit limits

No claim of a complete end-to-end 350-point walkthrough, exhaustive testing of every puzzle, or formal verification of the third-party Z-machine. Using the exact original story avoids approximate reimplementations but does not make an interpreter/UI bug impossible. Safari/iOS and Firefox have not been separately verified. Quetzal saves are tied to release 119, not every Zork edition.

Item cards use conservative visibility and original source-derived nouns. Unusual parser contexts, post-death states, or dynamically described objects may need the full text-command field. Cards are not an exhaustive list of valid actions, and unavailable cards must not be treated as proof that an action is impossible. No automap or hidden-exit discovery is implemented.

Reload recovery requires browser storage. It is not a substitute for exported manual saves, and a page closed while a native save dialog is open can resume the preceding command prompt. Save slots and notes are local to the browser/origin. The hosted and directly-opened standalone editions therefore do not automatically share saves; use export/import.

The website needs its assets to load initially. The standalone `zork.html` embeds everything needed for gameplay; the split-file website is not advertised as a fully offline-installable PWA. Decorative headers do not depict a literal navigable world. Full typing remains available at all times the original game requests input.
