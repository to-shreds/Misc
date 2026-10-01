# HTMLTools Handoff

## Current state
The current verified build is `html_toolbox_v24_force_extract.html`, persisted in `to-shreds/Misc/HTMLTools/` on 2026-09-30.

## What works
- Mobile-first tabbed toolbox.
- Web URL loading with direct fetch and proxy fallback.
- Asset table with sorting/filtering and per-row view/download actions.
- Forced URL extraction from the actual loaded text when structural HTML scanning returns no assets. This is the first approach verified by Jon to work against https://www.johnkewpickleball.com/paddle-database when the proxy returns Jina Markdown rather than HTML.
- HTML comment scanning includes HTML comments plus best-effort JavaScript line/block comments inside script blocks.
- HTML regex find/replace and download.
- ZIP filename sanitation/repackaging.
- Basic image preview/conversion.

## Do not break
- Preserve the v24 forced URL extraction fallback. Earlier v13-v23 iterations repeatedly loaded the target page but reported zero assets because the proxy response was Markdown/reader text rather than source HTML.
- Keep URL loading in Web only. HTML is for editing loaded/uploaded HTML.
- Keep mobile UX simple: collapsible sections, question-mark help buttons, and collapse the load panel after success.
- Do not require a local server, bookmarklet, browser extension, or headless-worker setup for the normal workflow.
- Direct fetch should fall back automatically rather than requiring the user to understand proxy behavior.

## Verification
Jon explicitly confirmed v24 successfully listed assets from the John Kew Pickleball paddle database after prior builds failed.
The repository copy was fetched back after upload and compared character-for-character with the generated v24 artifact; the contents matched exactly.

## Current artifact
- Repository path: `HTMLTools/html_toolbox_v24_force_extract.html`
- Git blob SHA: `bbe6cf58b72b1fd39c7f914e0784410390f0f076`
- Generated-file SHA-256 previously recorded: `7b2024a244d48de498322aad224b2c3580285e9b99daa271b287c873a0914c5c`
- UTF-8 text characters: 61,852

## Next action
Use v24 as the baseline for future HTMLTools changes. Preserve the forced extraction fallback and mobile workflow when adding features.