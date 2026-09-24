# Flowcraft

A browser-only Power Automate builder written for nontechnical users. The normal experience is: choose what you want to happen, read the boxes from top to bottom, answer plain-English questions, and download the ZIP. Power Automate terms such as variables, JSON, expressions, scopes, and connector details stay hidden unless the user deliberately opens technical options.

## Open it

The hosted entry is `PowerAutomate/index.html`. A root `power-automate.html` entry also links here so the Misc launcher can discover it. No npm installation, account, API key, server-side application, or third-party JavaScript is required.

Start with **Hello, automation**, click **Email me the result**, enter your work email, and choose **Export ZIP**. Import that ZIP in **Power Automate > My flows > Import > Import Package (Legacy)**, choose **Create as new**, and map your Outlook connection. Inspect the imported flow and test the manual trigger before relying on it.

This application generates configuration. It does not run the automation in your browser or deploy to your tenant.

## Included

Version 1.2 uses a slate-blue and white interface and rewrites the normal workflow in everyday language. The page is explicitly numbered: **1. What do you want to happen? 2. Put it together. 3. Download ZIP.** Common actions read like “Send an email,” “Do this for every item,” and “Make a yes or no decision.” Less common and technical actions stay behind **Show more step types**.

Nine recipes: a first email, email/AI/email, attachment saving, a scheduled inbox digest, filtered records, AI per item, conditional notification, try/catch with email, and a blank canvas.

Twenty-nine step types cover data operations, five variable operations, loops and conditions, groups, waiting, termination, Outlook email and attachment operations, supported Outlook Graph requests, HTML conversion, Teams, OneDrive, SharePoint, a saved AI Builder prompt, HTTP requests, and HTTP responses. Four triggers cover a manual button, incoming Outlook email, a schedule, and an HTTP request.

The reference picker shows preceding output values, initialized variables, and the current loop item. **Need more options?** exposes expressions, raw action JSON, and run-after dependencies only when needed. Conditions use a plain rule builder instead of showing raw condition JSON, and common validation messages are translated into ordinary language. One-click loop/collect and try/catch patterns generate multiple steps. The app also includes undo/redo, a JSON-schema starter, a sandboxed static email preview, project JSON save/load, optional browser persistence, and a review panel.

## Export contract

**Version 1 supports legacy single-flow packages, not Power Platform Solutions.** Solution ZIPs are rejected without replacing the current canvas. It does not synthesize AI model components, custom connectors, desktop flows, or tenant authentication. Existing saved AI prompts need their actual model ID and exact input names in the target environment. Prompt names alone do not create prompts.

The workflow definition is the source of truth. Known actions get friendly controls; unfamiliar action properties, metadata, nested branches, and explicit dependencies remain in the definition. Nonlinear and failure-handling graphs are not silently flattened for reordering. New exports create fresh package and flow resource IDs and ask the importer to map connections; they do not reuse connection credentials. Package-level export provenance and obsolete unused connections are not round-tripped.

A ZIP contains the five legacy package members: root manifest, flow-assets manifest, definition, API map, and connection map. Export uses standard STORE ZIP entries; import supports STORE and DEFLATE with CRC and size/path checks. Import is limited to 25 MB and 1,000 ZIP entries. ZIP64, encrypted archives, multivolume archives, binary members, and multiflow packages are not supported.

The checker catches many missing fields, broken references, dependency cycles, invalid initialization, unsafe concurrent variable writes, and malformed expression delimiters. It is **not Microsoft's validator**, a complete WDL interpreter, or proof of successful import. Actual connector schemas, dynamic data, service permissions, licensing, AI capacity, and prompt existence need a tenant test. A saved prompt's output may still need parsing or source verification. The inbox recipe is explicitly bounded, not an exhaustive mailbox scan.

## Privacy

No analytics, external libraries, remote fonts, tenant login, or data-upload requests. The hosted page loads its own static files; project processing and ZIP generation stay in the browser. Remember-in-browser is off by default. Enabling it saves unencrypted data in the current profile and origin, accessible to other scripts on that origin. Exported files are not encrypted either. Do not use this as a secrets vault. After import, your actual automation runs in Microsoft and is governed by your organization's policies.

## Source and offline build

`index.html`, `style.css`, `core.js`, `zip.js`, and `app.js` are canonical source. Run `python build.py` to create the self-contained `standalone.html`. The build uses only Python's standard library. Open that generated file directly for offline use. Do not hand-edit generated distributions.

Run `node --test tests/core.test.cjs` and `python tests/browser.py`. The latter requires Playwright and Chromium; its environment settings are described in TESTING.md. No development dependencies are loaded by the application itself.

See HANDOFF.md for current readiness and do-not-break constraints. The application is independent and is not a Microsoft product.

## Official references

- [Legacy package export/import](https://learn.microsoft.com/en-us/power-automate/export-import-flow-non-solution)
- [Workflow actions and triggers](https://learn.microsoft.com/en-us/azure/logic-apps/logic-apps-workflow-actions-triggers)
- [Variables](https://learn.microsoft.com/en-us/power-automate/create-variable-store-values)
- [Office 365 Outlook connector](https://learn.microsoft.com/en-us/connectors/office365/)
- [Run a saved AI prompt](https://learn.microsoft.com/en-us/ai-builder/use-a-custom-prompt-in-flow)
