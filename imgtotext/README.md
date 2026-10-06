# Image to Text

A phone-friendly, single-page image OCR tool. Main app: `index.html`.

**Page:** https://to-shreds.github.io/Misc/imgtotext/

The repository-root `imgtotext.html` redirects here and makes the app appear in the Misc homepage's existing root-HTML discovery. No other projects or homepage code were changed.

## Use

Add images, drop them anywhere on the page, paste a screenshot, or use **Take photo**. English OCR starts automatically. Use **Try a sample** to generate a sample image and run it through the real OCR engine.

Select an image to edit its **Clean paragraphs** text. **Original OCR** is preserved separately; **Line list** reflects your edits. **All images** combines completed results in the displayed order, with source filenames. Combined text is read-only; edit individual images instead.

**Key details** groups and deduplicates emails, links, North American-style phone numbers, common date patterns, currency amounts, and labeled fields. These are pattern matches, not AI summaries or semantic interpretation. They can miss values or produce false positives. Corrections to an image's text flow into its details and combined exports.

Copy text or individual details. Export the selected image or all images as TXT, Markdown, full JSON, or details CSV. TXT/Markdown follow the selected view. JSON includes original OCR, edited text, source metadata and details. CSV includes type, value and source filenames, with formula-prefix escaping.

Under **Image preview & options**, rotate and re-scan, choose automatic/single-block/scattered-text layout, reorder, or remove an image. Stop cancels current processing; Resume processes stopped images. Existing recognized/edited text is replaced by a successful re-scan after confirmation.

## Privacy and limits

- OCR runs in a browser worker using Tesseract.js **7.0.0**, loaded on demand from jsDelivr. Its core and English language assets are also downloaded from public CDNs. There is no image/text upload endpoint, API key, account, analytics, AI service, or application backend.
- Internet access is needed to download uncached engine/language assets. This is **not** a self-contained offline package. CDN requests reveal normal connection metadata, but the app does not send image contents or extracted text with those requests.
- Images and results are held in tab memory, not application persistent storage. Save exports before closing or reloading. The OCR library/browser can cache engine and language assets. This is not a claim of certified secure erasure or a compliance certification.
- English only. PNG, JPG, WebP, BMP and GIF (first frame). HEIC and PDF are not supported; convert them to supported images first.
- Up to 40 images, 25 MB per image, and 100 MB per session. Processing is sequential with a reused worker. The recognition canvas is capped at 12 megapixels / 4,200 pixels on the long edge, with limited upscaling for small images. Very long screenshots should be split into smaller images for better results.
- OCR can misread handwriting, names, numbers, rotated text, and complex/multicolumn layouts. Confidence measures the engine's original output, not the correctness of later manual edits.
- Prefer the hosted HTTPS page. File previews or local-file browsers can restrict workers, clipboard, or downloads.

## Verification on 2026-10-06

The committed `index.html` exactly matches the locally checked file (Git blob SHA `697b13b92c96eb2326ac4fe0e1062a30af427819`). JavaScript syntax checking passed. **40 deterministic Chromium interface checks passed** using a stubbed OCR worker: automatic queueing, worker reuse, formatting, preserved originals, editing, six detail groups, deduplication, CSV/JSON exports, reordering, rotation canvas, layout selection, stop/resume, empty/error cases, unsupported formats, clearing and reuse, HTML-safe text rendering, reset, and no horizontal overflow at 360/390/720/1200px. No browser JavaScript errors were recorded. Light/dark and mobile/desktop layouts were visually inspected.

**These checks do not prove OCR accuracy or live CDN loading.** The testing environment could not retrieve the external OCR assets; an actual engine end-to-end scan was not completed there. The built-in sample uses the real engine when opened in a network-enabled browser.

## Maintenance

No build step or framework. HTML, CSS and application JavaScript are in `index.html`. The Tesseract worker API was checked against its official documentation: https://github.com/naptha/tesseract.js/blob/master/docs/api.md. Keep the library and worker URL versions aligned. Never inject recognized text as HTML. Preserve existing original-text/edited-text separation and stop/error handling when changing the UI.
