# HANDOFF: NHL UK Firefox

Read `CODEX_FIREFOX_HANDOFF.md`, then this file and `STATUS.md`. The controlling architecture has not changed. The substantive repo is `to-shreds/Misc/DAZN`; readiness is also recorded in `to-shreds/ProjectStatus/projects/nhl-uk/STATUS.md`.

## Current state

Firefox development version `0.1.0` is implemented under `firefox-extension/`. This is no longer an architecture-only handoff. It is not accepted for watching unwatched games, and real DAZN playback has not been proven. The old WebView application remains untouched.

## Completed

- Shared Firefox MV3 desktop/Android manifest with narrow hosts, local storage and no cookie permission.
- Document-start user-origin cover, one responsive shell, recognized DAZN sign-in form exposure and synchronous SPA return cover.
- Official NHL schedule sanitation; exact team/date matching; actual DAZN search parameter/envelope and event/asset browser route evidence.
- New bounded, generation-based player preparation, decoded-frame readiness, separate Play verification, source/position fail-closed handling and elapsed-only resume.
- Native-control suppression, neutral Media Session metadata, covered system actions, disabled embedded frames/plugins and PiP.
- Reproducible Node tests, Mozilla lint/build, public-data smoke check and actual installed-extension Firefox synthetic tests. See the dated reports for counts and limitations.

## Source and evidence

Implementation details: `FIREFOX_ARCHITECTURE.md` and `firefox-extension/README.md`.

Provider evidence: `reports/CATALOGUE-EVIDENCE-2026-10-02.md` and `reports/DATA-SMOKE-2026-10-02.md`. Current search is `searchTerm`, not the misleading `searchParam` mentioned in provider validation errors. Parse all `Results[].Tiles` groups. The provider's own route table supports `/en-GB/home/<EventId>/<AssetId>`; do not return to guessed bare fixture paths.

Test/build evidence: `reports/FIREFOX-IMPLEMENTATION-2026-10-02.md`. The synthetic browser harness installs a temporary extension using web-ext, runs production code in actual Firefox, and adds a test-only isolated probe. It is not a real DAZN login, Widevine or physical Android test.

## Do not break

- No scores, results, total length, progress percentage, thumbnails, provider headlines or raw diagnostics.
- No live-edge fallback. Live variants remain excluded. Playoff rows remain hidden pending series-watched support.
- `shield.cover()` is an intentional operation and must not notify the invalidation handler. Unexpected source/navigation/DOM faults do notify it. Confusing these cancels the player's own preparation.
- Firefox can retain an old `currentSrc` while loading a new `src`. Track both and discard decoded evidence when source/metadata changes.
- Hidden `visibility:hidden` video DID produce decoded callbacks in the verified fixture. A container decoder-process failure initially looked like a player failure; do not weaken cover CSS to solve that environment issue.
- Store only authorized elapsed playback positions. Different variants do not automatically share a timeline.
- MAIN-world code is page-visible and is not a security boundary against hostile provider JavaScript.
- No secrets or signing material in this public repository.

## Unresolved acceptance and limits

No authenticated DAZN session, Windows device or attached Android phone was used. Real sign-in, entitlement/DRM playback, first revealed broadcast frame/audio, background behavior, fullscreen and system duration/progress surfaces remain unverified. Embedded auth/player frames are deliberately blocked; inspect any required CAPTCHA or external auth flow before adapting it.

The Android Nord launcher is intentionally not created because the controlling handoff requires proven extension replay playback first. Mozilla signing and persistent distribution also follow that gate.

## Next action

Use `firefox-extension/tests/REAL-DEVICE-ACCEPTANCE.md`. Test a previously watched completed replay in Windows Firefox, then the same source on physical Firefox Android with `web-ext run`. Record exact neutral failure stages and platform observations. Fix observed provider integration issues without weakening the cover. Only after both pass, proceed to still-running DVR and the thin Nord launcher.
