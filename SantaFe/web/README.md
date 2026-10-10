# Santa Fe emergency web controls

A password-gated web controller backed by a free Render Python service. It sends the existing confirmed Hyundai USA API requests directly, without a phone, Join or Tasker. The old phone projects and API Lab remain separate.

The service is live at [santa-fe-emergency.onrender.com](https://santa-fe-emergency.onrender.com/) on Render's Free plan. The hosted website password gate, protected assets/APIs, secure session cookies, CSRF/Origin checks and logout have been verified over HTTPS. Authentication with a real Hyundai account still needs the account holder's first check; local Hyundai verification uses synthetic accounts and intercepted replies only.

## Access

The public entry page is a password form. Controller HTML, JavaScript, styles and every account/control API require a valid server session. The site password must be at least 12 characters; a long unique passphrase is recommended. A four-digit Bluelink PIN is not accepted as the website password.

The password and session-signing secret live in Render environment settings, never in the repository. Unconfigured or short secrets leave the controller closed. Browser sessions are Secure, HttpOnly and SameSite=Strict, expire after 30 minutes, and become invalid after a server restart. Responses are not cached. Site-password attempts are globally limited to eight in five minutes.

After signing in, use the optional server-configured Hyundai account or enter your MyHyundai email/password in the protected page. Hyundai details and tokens stay in the running server's memory. The page clears the submitted password and PIN fields and does not save them to browser storage. You can enter a PIN while connecting, or enter it separately with each command. Whether Hyundai accepts account/status reads with the PIN omitted needs the first live check.

## Deploy on free Render

Repository: `https://github.com/to-shreds/Misc`, branch `main`. Create a new **web service**, Python runtime, **Free** compute, Virginia region. Use these commands if configuring through the direct service API without a root-directory field:

```text
Build: pip install -r SantaFe/web/requirements.txt
Start: cd SantaFe/web && gunicorn -c gunicorn.conf.py app:app
Health check: /healthz
```

The equivalent Blueprint is `SantaFe/web/render.yaml`; its `rootDir` is `SantaFe/web`. Use manual deployments so unrelated updates in Misc do not restart an emergency controller. No database, disk, worker, cron job or paid resource is required.

Required environment settings:

| Name | Value |
| --- | --- |
| `WEBSITE_PASSWORD` | Your unique site password, at least 12 characters |
| `SESSION_SECRET` | Random secret of at least 32 characters |

Optional settings to avoid entering your Hyundai account on each visit:

| Name | Value |
| --- | --- |
| `HYUNDAI_EMAIL` | MyHyundai email |
| `HYUNDAI_PASSWORD` | MyHyundai password |
| `HYUNDAI_PIN` | Bluelink four-digit PIN, or leave unset to enter it with each command |

Do not send credentials in URLs. Render serves HTTPS and the app refuses ordinary HTTP traffic. `RENDER_EXTERNAL_HOSTNAME` restricts allowed Host headers in production. The single-worker Gunicorn configuration is required because the service has one shared account and command guard.

## Operation and restart handling

Free Render sleeps after 15 minutes without inbound traffic and takes about a minute to wake. Waking the page does not operate the vehicle. After a process restart, sign in again, connect to Hyundai, and acknowledge the current car condition before sending a command.

Commands are Lock, Unlock, Regular start (72 F), Cool cabin (62 F), Warm cabin (81 F with defrost), and Stop. Starts last ten minutes, use no seat/steering heat, and require outdoor confirmation. The confirmed generation-3 non-EV remote-start recipe sends registration ID in the start body and VIN in vehicle headers.

The page shows cached status with its upstream observation time. Reading it does not prove the observation is fresh. Command acceptance and completion are shown separately; the server checks Hyundai's transaction result. No command is automatically resent.

Before operating the car, the browser first prepares an intent and receives a signed HttpOnly pending-marker cookie. Only a second explicit request can execute that single-use intent. The running server serializes commands across tabs/browsers. A lost response, unresolved transaction, expired intent or restart cannot silently cause a command replay. An unresolved outcome requires checking the car before continuing.

Free Render has no persistent local disk. The pending cookie and startup acknowledgment are conservative safeguards, not a globally durable audit ledger across browsers. No claim is made that a signed cookie can revoke a copied older cookie. Always keep one Gunicorn process; adding more workers would defeat the shared lock. The browser saves only a nonsecret action/time/request marker in sessionStorage, with no credentials, PIN or Hyundai token.

## Verification

```sh
pip install -r requirements.txt pytest
python -m pytest -q
node --check static/app.js
```

The optional Playwright integration check uses a local fake Hyundai server and exercises the actual login, protected assets, account connection, startup guard, two-step command and completion flow:

```sh
PYTHONPATH=.:tests node tests/browser_smoke.cjs
```

Playwright and Chromium must already be installed. `tests/browser_server.py` is a test fixture and is never imported by `app.py` or the deployment.

First live acceptance: sign in, connect, verify vehicle enrollment and cached status from Render. Only then perform an individually requested real-car control and verify its physical outcome. No real account or vehicle was used by the automated tests.

## Controlling sources

- Existing API implementation: `SantaFe/diagnostic.js` and `SantaFe/tasker/direct/api.java`.
- Confirmed recipes: `SantaFe/docs/verification/confirmed-api-recipes.json`.
- Project handoff: `SantaFe/HANDOFF.md`.
- Readiness: `to-shreds/ProjectStatus/projects/santa-fe-control-center/STATUS.md`.
- [Render web services](https://render.com/docs/web-services), [free-service limits](https://render.com/docs/free), [Blueprint specification](https://render.com/docs/blueprint-spec).
- [Flask security documentation](https://flask.palletsprojects.com/en/stable/web-security/).

This is an implementation of previously confirmed private Hyundai request recipes, not an official Hyundai integration. Hosted authentication still needs verification.
