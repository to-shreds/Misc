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

## Deployment and acceptance

The prior same-origin Render build passed 17 hosted HTTPS checks and a public sign-in browser inspection. Those checks covered the earlier page-password gate and do not establish this revised cross-domain command flow. The revised frontend/backend now need their hosted checks after publication.

No Render CLI/API Blueprint validation was performed. Full authenticated Chromium integration could not run in this container because no browser executable is installed and its prior download was unusable. DOM tests do not claim browser layout verification.

Real Render-to-Hyundai authentication, enrollment/status and physical control outcomes remain account-holder acceptance checks. Configure account credentials in Render Environment settings or enter them inside the authorized page. Verify vehicle and cached status before an individually requested car command.
