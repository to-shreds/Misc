# Codex bootstrap

This repository is `to-shreds/Misc`.

The active Codex project is the DAZN / NHL UK Firefox rebuild.

## Start here

Read and execute:

`DAZN/CODEX_FIREFOX_HANDOFF.md`

Then read the supporting project files it names.

Do not ask the user to restate project history that is already in `DAZN/`.

## Important bootstrap rule

The Firefox rebuild does **not** require the private legacy 3.1 archive to begin.

Everything needed to start the new Firefox implementation is documented in this repository.

The private legacy archive is optional reference material only if a later task specifically needs:

- exact legacy NordVPN launcher implementation details;
- exact legacy pure schedule/catalogue code that cannot be cleanly reimplemented from the documented behavior;
- Android signing continuity when the thin Android launcher is eventually rebuilt.

If that archive is unavailable, continue with the Firefox extension work. Do not stop the task merely to request it.

## Repository workflow

Work directly in this repository.

Create the implementation under:

`DAZN/firefox-extension/`

Later, after Firefox playback is proven, create:

`DAZN/android-launcher/`

Run the tests described in the handoff, commit meaningful working changes, and push them to `to-shreds/Misc`.

After meaningful progress, update the DAZN handoff/status files and then update `to-shreds/ProjectStatus/projects/nhl-uk/STATUS.md` as the final persistence step.

## Non-negotiable constraint

No spoilers. Ever.

Read `DAZN/NO_SPOILERS.md` before implementing any DAZN UI or player behavior.
