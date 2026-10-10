# Emergency web controller verification

Date: October 10, 2026. Revision: automatic password-free status loading on GitHub Pages with command passwords enforced by the Free Render backend.

## Local verification passed

- 92 Python tests: 22 confirmed-client fixtures, 23 application regressions, 21 independent security checks, two actual local HTTP tests, and 24 cross-domain web API tests.
- Public status connects with the saved account on cold start and uses the selected existing client thereafter. Anonymous reads cannot prepare, execute or resolve commands, clear unknown outcomes, change vehicle selection, or expose VIN, location, account details, PIN, tokens or internal command state.
- Typed, nested whitelisting rejects extra secrets, markup and invalid data. Concurrent cold requests share a single connection/read. Read results and failures coalesce for five seconds; prepared/pending commands return the last cached observation without any Hyundai call.
- 22 DOM scenarios / 909 checks execute the actual frontend source with synthetic replies. Loading is visible before any response, and status arrives independently of access information or password entry. Information failures retain readable status; errors/unavailability allow retry; each new page load queries again; Refresh status needs no password. Late public replies preserve newer private status, and forgetting/expiring access retains public metrics while commands stay disabled.
- Prior command flows remain covered: fresh password for each command, wrong-password/rate-limit handling, input clearing, signed guard before send, lost replies, no resend, bounded polling and checked-car recovery. Both web and legacy cookie APIs retain their command password gates.
- Public reads send no password, session token, command guard or browser cookies. Only signed guards and nonsecret recovery markers enter sessionStorage. The configured website password is absent from published source and tests.
- JavaScript syntax, Gunicorn configuration and generated index/template agreement pass. Assets use version 3. Optional Playwright fixtures are updated for public status/refresh/reload and remain restricted to a synthetic local backend.
- The confirmed Hyundai client and the prior phone/Tasker/API Lab artifacts are unchanged. Local tests send no real Hyundai request or vehicle command.

## Hosted verification

Publishing and live verification of this revision are pending. Jon reports the preceding emergency web controls work, and the live backend now reports that saved account settings are available. Credential values were not inspected or altered.

## Verification limits

Full authenticated Chromium integration is unavailable in this container because no browser executable is installed. DOM tests do not establish browser layout or a physical vehicle result. Actual vehicle commands are never sent by this verification. The page displays Hyundai's cached observation with its timestamp, not a guarantee of a freshly sampled car.
