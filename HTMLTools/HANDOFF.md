# HTMLTools Handoff

## Current state
The current working build is `html_toolbox_v24_force_extract.html`, produced 2026-09-30.

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

## Current artifact
Local generated artifact: `html_toolbox_v24_force_extract.html`
SHA-256: `7b2024a244d48de498322aad224b2c3580285e9b99daa271b287c873a0914c5c`
Size: 61,942 bytes.

## Next action
Persist the exact v24 HTML artifact into this folder when a GitHub write path capable of ingesting the generated local file bytes is available.