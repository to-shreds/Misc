# Emergency web controller verification

Date: October 10, 2026. Source: the new `SantaFe/web` implementation. No personal account credentials were configured and no real Hyundai request was sent.

## Passed

- **54 Python tests**: 22 Hyundai-client fixture checks, 14 app checks, 16 independent adversarial security checks, and two tests using an actual local HTTP server.
- Hyundai requests match the existing host/endpoints, vehicle identity mapping, temperature presets and `REMOTE_POLL` result checks. Intercepted replies cover successful/rejected/unknown commands, read reauthentication, malformed responses, redirects, invalid identities and unresolved-command guards.
- Public login HTML contains no controller script or control markup. The server rejects unauthenticated controller styles/scripts and all account/control APIs. Incorrect passwords, missing CSRF, foreign/null Origin, spoofed forwarded IPs and oversized/malformed bodies are rejected.
- Session cookies have Secure/HttpOnly/SameSite=Strict attributes in production settings, carry no Hyundai credentials or tokens, and expire/invalidate after restart. Login CSS receives the matching CSP nonce; private output uses no-store headers.
- An actual HTTP test signs in, receives protected assets, connects to a fake Hyundai account, receives the prepared-command cookie before any control transmission, submits one control and checks successful completion. A second HTTP test rejects an unauthenticated cross-origin control.
- Duplicate execution, second-browser interference, lost replies, logout/relogin, marker tampering and fresh process epochs do not silently replay a control. Reconnection is blocked while a command is pending; runtime Hyundai secrets stay out of cookies and API state.
- JavaScript syntax checks pass for the control page and optional Playwright test.
- Gunicorn validates its configuration: one worker, four threads, public port binding and bounded worker timeout.
- The Blueprint YAML parses and describes one Free web service, with no database, disk or paid dependency. Server setup fails closed when required website secrets are absent or too short.

## Not verified

- No Render service has been created yet. Render requires confirmation of the destination workspace; the account exposes `My Workspace`.
- Server-to-Hyundai authentication, vehicle enrollment/status and physical control outcomes still need hosted acceptance testing. Previous physical confirmation of the original API recipes is not a new cloud verification.
- Chromium integration could not run in this container: no browser executable was installed and the browser download returned an unusable archive. The Playwright test fixture is included for the next available browser environment. No browser-layout or screenshot pass is claimed.
- No Render CLI/API Blueprint validation was performed. YAML parsing and documented field inspection are distinct from platform acceptance.

## Next verification

Create the free service after workspace confirmation, set a unique website password and session secret outside git, and verify the hosted password gate. Then connect to the real Hyundai account inside that protected page and verify cached status before any individually requested vehicle command. The site can accept runtime account details, so no Hyundai credentials need to be supplied in this conversation or placed in the repository.
