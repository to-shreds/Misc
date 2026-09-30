# DAZN / NHL UK

This folder is the durable handoff for the NHL UK Android project.

The goal is a one-tap, spoiler-safe Buffalo Sabres replay client that keeps NordVPN as the phone's existing always-on VPN, automatically switches Nord to the UK, reuses a DAZN web login, resolves the selected NHL game without exposing DAZN's sports UI, and plays from the beginning or a saved elapsed position.

## Current state

The project is BLOCKED. No build has successfully completed protected playback on the real phone.

The latest device test used version 3.1-multi-search-test. The app progressed farther than any prior build: it resolved a selected game far enough to show "Prepare beginning." When Prepare beginning was tapped, the guard stopped with "Unexpected position change." No video or audio was revealed.

That result is the current debugging target. It must not be described as a nearly finished player.

Read these files in order before changing code:

1. STATUS.md
2. HANDOFF.md
3. NO_SPOILERS.md
4. ARCHITECTURE.md
5. FAILURE_HISTORY.md
6. TESTING.md
7. SOURCE_MAP.md

Historical build reports are preserved under history/.

## Source

The current private source/build archive is NHL-UK-3.1-private-backup-fresh.zip, SHA-256 be3772613c6eb4606dd83c4dff87444aa56c97189acd804225284502188a74dc.

That archive contains the private APK signing key and must not be committed to this public repository. SOURCE_MAP.md records the source layout and signing identity without publishing the key.

## Repository rule

This folder controls the durable project handoff. ProjectStatus controls readiness. Device evidence outranks synthetic tests when they conflict.

Do not make another broad architectural rewrite merely because a protected step failed. Identify the exact stage and preserve everything already proven on-device.
