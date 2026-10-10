# Emergency web controller verification

Date: October 10, 2026. Revision: public GitHub Pages frontend plus Free Render backend with a separate password checked for every command. No real Hyundai credentials or vehicle were used.

## Current revision passed

- 78 Python tests: 22 confirmed-client fixtures, 23 application regressions, 16 independent security checks, two actual local HTTP tests, and 15 cross-domain web API tests.
- A read session cannot prepare, execute or resolve a command without the separate password. Both web and legacy cookie endpoints enforce the check, including duplicate/replay lookup.
- Cookie-free signed read sessions expire after 30 minutes and invalidate across process epochs. Exact-Origin CORS, preflight headers, forged/expired tokens, owner-bound signed guards, expired preparations and global failed-password rate limits are covered.
- Single-use preparation is delivered before execution. The backend keeps one shared lock, bounded polling, no automatic control retries and conservative startup acknowledgment. The confirmed Hyundai client is unchanged.
- 13 DOM scenarios execute the actual frontend source with synthetic fetch replies. They cover public load, account authorization, saved/manual account connection, fresh passwords for successive commands, wrong-password/rate-limit handling, cancel/Escape clearing, guard storage before send, lost preparation/execution, bounded polling, expired sessions and pending/manual recovery.
- Passwords, PIN and read tokens stay out of WebStorage; only a signed guard and nonsecret recovery marker are stored. The real configured password is absent from every published source/test file.
- JavaScript syntax, Gunicorn configuration and generated index/template agreement pass. The Blueprint parses and adds only the exact GitHub frontend Origin to the existing Free service.
- The optional Playwright fixture now serves public frontend assets locally and rewrites the backend URL to its synthetic server, with no external requests permitted.

## Hosted deployment passed

Verified October 10, 2026.

The revised frontend is live at https://to-shreds.github.io/Misc/SantaFe/web/. GitHub Pages run 38080938981 completed successfully on code commit 55ff968cbc37e1d38a757fd424772216333ee713. Render deployment dep-db59aqcs728c73ca3tv0 is live on that same commit, still on the Free plan in Virginia with automatic deployments disabled.

20 hosted HTTPS checks passed: public page/assets without embedded secrets, minimal public backend information, exact GitHub CORS/preflight, foreign-Origin rejection, blocked unauthenticated state, incorrect-password rejection, correct configured-password authorization without cookies, authorized cached state, read-token-only denial of prepare/command/resolve, incorrect fresh-command-password rejection, retained read access after that error, and refusal to execute an unprepared command. These checks sent no Hyundai request and no vehicle command.

An independent public review confirmed page/asset availability, absence of embedded credentials, minimal setup information, CORS, and both web/legacy unauthorized state rejection. A cloud browser showed the public GitHub controls with readable styling, private status hidden, and connection available after Render woke.

The backend reports saved_account_available:false and connected:false. Hyundai email/password/PIN still need one-time private configuration or authorized runtime entry.

## Not verified

No Render CLI/API Blueprint validation was performed. Full authenticated Chromium integration could not run in this container because no browser executable is installed and its prior download was unusable. DOM tests do not claim browser layout verification.

Real Render-to-Hyundai authentication, enrollment/status and physical control outcomes remain account-holder acceptance checks. Configure account credentials in Render Environment settings or enter them inside the authorized page. Verify vehicle and cached status before an individually requested car command.
