# Public data smoke check

Run `python3 scripts/check-public-data.py --season 20262027` from `DAZN/firefox-extension/`. This uses Python's standard-library HTTP client to read one official NHL season response and one neutral Buffalo DAZN search response, then passes them in memory to the actual extension JavaScript sanitizers and matcher. No raw response is written to disk. Standard output contains only aggregate neutral game, matched game and compatible variant counts; failures use a fixed error string.

The 2026-10-02 live data check sanitized **88** scheduled games and resolved **3** games into **5** compatible variants. Provider catalogue availability can change, so future counts may differ. This establishes that the current public response schemas work with the matcher. It does not establish Firefox page-origin transport, authentication, subscription entitlement, DRM, or safe playback on either desktop or Android.
