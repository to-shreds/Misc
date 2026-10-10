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

Code commit c5e05939d1d086179346e24070a52227f6e469e6 is published. GitHub Pages run 38085464261 succeeded; Free Render deployment dep-db5ab9jbc2fs73es64og is live on the same commit. All 22 local source-file Git blob hashes match the published tree. Render environment values, plan, process count and confirmed Hyundai client were not changed.

29 hosted HTTPS checks passed. An anonymous request and a GitHub-Origin request retrieved actual cached status using the saved account, including observation/read timestamps, without a website password or browser cookie. Public response shape/types, exact CORS, foreign-Origin and invalid-preflight rejection, no-store headers, private web/legacy state gating, anonymous command/prepare/resolve denial, initial Loading HTML, and exact live asset bytes all passed. These checks performed the explicitly requested read-only Hyundai status query and sent zero vehicle commands.

A cloud browser loaded the live GitHub page without authentication, visibly showed Loading, then displayed the car name and returned Doors, Engine, Fuel, Odometer and cached observation time. Desktop layout was readable and all anonymous command buttons remained disabled. DOM tests separately verify every reload and public Refresh status.

Jon reports the emergency web controls work. Saved account settings are now configured, superseding the prior missing-account setup note. Credential values were not inspected or altered. His report is user acceptance, not an independently observed physical control outcome.

## Verification limits

Full authenticated Chromium integration is unavailable in this container because no browser executable is installed. DOM tests do not establish browser layout or a physical vehicle result. Actual vehicle commands are never sent by this verification. The page displays Hyundai's cached observation with its timestamp, not a guarantee of a freshly sampled car.
