# Emergency web controller verification

Date: October 10, 2026. Source: `SantaFe/web`, deployed from commit `2d302454235f40a14b14ec95c355462606e34507`. No Hyundai account credentials were configured and no real Hyundai request was sent. The website password and signing secret are configured only in protected Render settings.

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

## Hosted deployment passed

- Jon confirmed Render's My Workspace. Service `srv-db591kt9fdbs73c1fdcg` is live at https://santa-fe-emergency.onrender.com/ on the **Free** Python plan in Virginia. Automatic deployments are disabled. No database, disk, worker or paid service was created.
- Deployment `dep-db591ld9fdbs73c1ffh0` reports `live` on the source commit above. Dependencies installed and Gunicorn started successfully.
- **17 real HTTPS checks passed**: login-only public HTML; matching login CSS nonce/CSP; blocked public controller assets/state; incorrect password rejection; configured password acceptance; Secure/HttpOnly/SameSite=Strict cookie; authenticated controller/assets/state; initially disconnected account; conservative startup guard; missing-CSRF and foreign-Origin rejection; logout and renewed API denial.
- An independent unauthenticated review confirmed the public gate, blocked assets/API, no-store headers and `/healthz` with `configured: true`.
- A cloud browser displayed the live sign-in form with readable styling. This checks the public login layout only, not the authenticated browser command flow.
- Zero Hyundai requests and zero vehicle commands were sent by hosted checks.

## Not verified

- Server-to-Hyundai authentication, vehicle enrollment/status and physical control outcomes still need hosted acceptance testing. Previous physical confirmation of the original API recipes is not a new cloud verification.
- Full authenticated Chromium integration could not run in this container: no browser executable was installed and the browser download returned an unusable archive. The Playwright test fixture is included for the next available browser environment. The cloud browser check covered only the public sign-in layout.
- No Render CLI/API Blueprint validation was performed. YAML parsing and documented field inspection are distinct from platform acceptance.

## Next verification

Sign in to the live service, connect to the real Hyundai account inside the protected page, and verify enrollment and cached status before any individually requested vehicle command. The site can accept runtime account details, so no Hyundai credentials need to be supplied in this conversation or placed in the repository.
